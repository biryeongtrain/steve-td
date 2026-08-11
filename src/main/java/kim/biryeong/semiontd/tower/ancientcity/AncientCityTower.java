package kim.biryeong.semiontd.tower.ancientcity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.world.damagesource.DamageSource;

public final class AncientCityTower extends EntityBackedTower {
    private int retaliationCooldownTicks;

    public AncientCityTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public AncientCityTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    public AncientCityRole role() {
        return AncientCityTowers.roleOf(type());
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        super.onPlaced(lane);
        AncientCityStates.ensureSeeded(this, lane);
    }

    @Override
    public void onStateChanged(PlayerLane lane) {
        super.onStateChanged(lane);
        AncientCityStates.ensureFinalDefenseSeeded(this, lane);
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        AncientCityStates.onWaveStarted(this, lane, currentRound);
    }

    @Override
    public void tick(PlayerLane lane) {
        if (retaliationCooldownTicks > 0) {
            retaliationCooldownTicks--;
        }
        super.tick(lane);
    }

    @Override
    public double modifyIncomingDamage(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount) {
        if (role() != AncientCityRole.CATALYST || !AncientCityStates.resonanceActive(this)) {
            return damageAmount;
        }
        return damageAmount * Math.max(0.0, 1.0 - ability("sculkDamageReduction"));
    }

    @Override
    public void onDamaged(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double damageAmount,
            double previousHealth,
            double currentHealth
    ) {
        if (role() != AncientCityRole.CATALYST
                || towerEntity == null
                || damageAmount <= 0.0
                || currentHealth <= 0.0
                || retaliationCooldownTicks > 0) {
            return;
        }
        retaliationCooldownTicks = Math.max(1, abilityTicks("retaliationCooldownTicks"));
        double radius = ability("retaliationRadius");
        double baseDamage = ability("magicDamage");
        if (radius <= 0.0 || baseDamage <= 0.0) {
            return;
        }
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, "catalyst_retaliation"),
                towerEntity,
                radius,
                AreaVfxSpec.onTrigger(AreaVfxStyles.PULSE)
        );
        TowerAreaDamage.apply(
                this,
                towerEntity,
                request,
                target -> magicDamage(target, baseDamage, true),
                true,
                (target, dealtDamage, killed) -> {},
                DamageType.MAGIC
        );
        AncientCityVfx.showCatalyst(towerEntity, radius);
    }

    @Override
    protected boolean execute(PlayerLane lane) {
        if (role() == AncientCityRole.CATALYST) {
            return false;
        }
        SemionTowerEntity towerEntity = towerEntity(lane).orElse(null);
        if (towerEntity == null) {
            return false;
        }
        List<SemionMonsterEntity> candidates = candidates(towerEntity);
        if (candidates.isEmpty()) {
            return false;
        }
        return switch (role()) {
            case SENSOR -> castSensor(towerEntity, candidates);
            case SHRIEKER -> castShrieker(towerEntity, candidates);
            case WARDEN -> castWarden(towerEntity, candidates);
            case CATALYST -> false;
        };
    }

    @Override
    protected int cooldownTicksAfterExecute(PlayerLane lane) {
        return Math.max(1, abilityTicks("magicCooldownTicks"));
    }

    @Override
    public List<String> runtimeDetailLines() {
        int count = AncientCityStates.territoryCount(ownerPlayer());
        int maxSculk = Math.max(1, globalInt("maxSculk"));
        int fullAt = Math.min(maxSculk, Math.max(1, globalInt("resonanceFullAt")));
        double bonus = AncientCityStates.resonanceBonus(this);
        return List.of(
                "스컬크 영토 " + count + "/" + maxSculk,
                "스컬크 공명 " + Math.min(count, fullAt) + "/" + fullAt + " · +" + percent(bonus)
                        + " (" + (AncientCityStates.resonanceActive(this) ? "활성" : "비활성") + ")"
        );
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof AncientCityTower previous) {
            retaliationCooldownTicks = previous.retaliationCooldownTicks;
        }
    }

    double magicDamage(SemionMonsterEntity target, double baseDamage, boolean includeMark) {
        double bonus = AncientCityStates.resonanceBonus(this);
        Monster monster = target == null ? null : target.runtimeMonster();
        if (includeMark) {
            bonus += AncientCityMarks.damageBonus(monster, ownerPlayer());
        }
        bonus = combinedMagicBonus(bonus);
        return incomeAdjustedMagicDamage(
                Math.max(0.0, baseDamage) * (1.0 + bonus),
                monster != null && monster.senderTeam().isPresent()
        );
    }

    static double combinedMagicBonus(double bonus) {
        return Math.min(Math.max(0.0, global("maxCombinedDamageBonus")), Math.max(0.0, bonus));
    }

    static double incomeAdjustedMagicDamage(double damage, boolean incomeTarget) {
        return incomeTarget ? damage * Math.max(0.0, global("incomeMagicDamageMultiplier")) : damage;
    }

    private boolean castSensor(SemionTowerEntity towerEntity, List<SemionMonsterEntity> candidates) {
        SemionMonsterEntity target = primaryTarget(towerEntity, candidates);
        double baseDamage = ability("magicDamage");
        DamageResult result = damageTargetResult(
                towerEntity,
                target,
                magicDamage(target, baseDamage, true),
                DamageType.MAGIC
        );
        if (result.killed()) {
            onKill(towerEntity, target, baseDamage);
        } else if (result.dealtDamage() > 0.0 && target.isAlive()) {
            int duration = abilityTicks("markDurationTicks");
            AncientCityMarks.apply(
                    target.runtimeMonster(),
                    ownerPlayer(),
                    towerEntity.getUUID(),
                    ability("markDamageBonus"),
                    duration
            );
            target.applyTimedEffect(TimedEffectType.MONSTER_MARKED, 1.0, duration);
        }
        AncientCityVfx.showSensor(towerEntity, target);
        return true;
    }

    private boolean castShrieker(SemionTowerEntity towerEntity, List<SemionMonsterEntity> candidates) {
        SemionMonsterEntity primary = primaryTarget(towerEntity, candidates);
        double radius = ability("magicRadius");
        double baseDamage = ability("magicDamage");
        int slowTicks = abilityTicks("slowDurationTicks");
        double slow = ability("slowMagnitude");
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                AreaEffectIds.tower(this, "shriek"),
                towerEntity,
                primary,
                radius,
                AreaVfxSpec.onTrigger(AreaVfxStyles.PULSE)
        ).including(primary.getUUID());
        TowerAreaDamage.apply(
                this,
                towerEntity,
                request,
                target -> magicDamage(target, baseDamage, true),
                true,
                (target, dealtDamage, killed) -> {
                    if (!killed && target.isAlive() && slow > 0.0 && slowTicks > 0) {
                        target.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, slow, slowTicks);
                    }
                },
                DamageType.MAGIC
        );
        AncientCityVfx.showShrieker(towerEntity, primary, radius);
        return true;
    }

    private boolean castWarden(SemionTowerEntity towerEntity, List<SemionMonsterEntity> candidates) {
        ArrayList<SemionMonsterEntity> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator
                .comparingDouble((SemionMonsterEntity target) -> maxHealth(target)).reversed()
                .thenComparingDouble(target -> target.distanceToSqr(towerEntity))
                .thenComparing(target -> target.getUUID().toString()));
        int targetCount = Math.max(1, abilityInt("targetCount"));
        if (AncientCityStates.resonanceActive(this)) {
            targetCount += Math.max(0, abilityInt("sculkExtraTargets"));
        }
        List<SemionMonsterEntity> targets = ordered.stream().limit(targetCount).toList();
        double baseDamage = ability("magicDamage");
        double secondaryRatio = Math.max(0.0, ability("secondaryDamageRatio"));
        for (int index = 0; index < targets.size(); index++) {
            SemionMonsterEntity target = targets.get(index);
            boolean primary = index == 0;
            double targetBaseDamage = primary ? baseDamage : baseDamage * secondaryRatio;
            DamageResult result = damageTargetResult(
                    towerEntity,
                    target,
                    magicDamage(target, targetBaseDamage, primary),
                    DamageType.MAGIC
            );
            if (result.killed()) {
                onKill(towerEntity, target, targetBaseDamage);
            }
        }
        AncientCityVfx.showWarden(towerEntity, targets);
        return true;
    }

    private SemionMonsterEntity primaryTarget(
            SemionTowerEntity towerEntity,
            List<SemionMonsterEntity> candidates
    ) {
        SemionMonsterEntity current = towerEntity.currentAttackTarget();
        if (current != null && candidates.contains(current)) {
            return current;
        }
        return candidates.stream()
                .min(Comparator.comparingDouble(target -> target.distanceToSqr(towerEntity)))
                .orElseThrow();
    }

    private List<SemionMonsterEntity> candidates(SemionTowerEntity towerEntity) {
        double range = type().range();
        double rangeSqr = range * range;
        return towerEntity.level().getEntities(
                        towerEntity,
                        towerEntity.getBoundingBox().inflate(range),
                        entity -> entity instanceof SemionMonsterEntity target
                                && towerEntity.isValidAttackTarget(target)
                ).stream()
                .map(SemionMonsterEntity.class::cast)
                .filter(target -> target.distanceToSqr(towerEntity) <= rangeSqr)
                .toList();
    }

    private Optional<SemionTowerEntity> towerEntity(PlayerLane lane) {
        if (lane == null || entityId().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(lane.arenaWorld().getEntity(entityId().getAsInt()))
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast);
    }

    private static double maxHealth(SemionMonsterEntity target) {
        Monster monster = target.runtimeMonster();
        return monster == null ? target.getMaxHealth() : monster.maxHealth();
    }

    private double ability(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    private int abilityInt(String key) {
        return TowerBalanceRuntime.abilityInt(type().id(), key);
    }

    private int abilityTicks(String key) {
        return TowerBalanceRuntime.abilityTicks(type().id(), key);
    }

    private static double global(String key) {
        return TowerBalanceRuntime.ability(AncientCityStates.CONFIG_ID, key);
    }

    private static int globalInt(String key) {
        return TowerBalanceRuntime.abilityInt(AncientCityStates.CONFIG_ID, key);
    }
}
