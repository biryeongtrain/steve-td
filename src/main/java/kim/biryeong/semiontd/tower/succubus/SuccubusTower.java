package kim.biryeong.semiontd.tower.succubus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaTargetMode;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.hero.FakePlayerTowerVisuals;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;

public final class SuccubusTower extends ProductionTower {
    private static final ResourceLocation LULLABY_MONSTERS = ResourceLocation.fromNamespaceAndPath("semion-td", "succubus_lullaby_monsters");
    private static final ResourceLocation LULLABY_TOWERS = ResourceLocation.fromNamespaceAndPath("semion-td", "succubus_lullaby_towers");
    private final Map<UUID, Long> counterReadyAt = new HashMap<>();
    private PlayerLane lane;
    private int attackCount;
    private double damageTowardSelfDream;

    public SuccubusTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId,
                       GridPosition originalPosition, GridPosition currentPosition) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        this.lane = lane;
        if (role() == SuccubusRole.SUCCUBUS) syncHealth(currentMaxHealth());
        super.onPlaced(lane);
    }

    @Override
    public double currentMaxHealth() {
        return role() == SuccubusRole.SUCCUBUS
                ? applyTraitMaxHealth(maxHealth() + SuccubusAbsorption.health(ownerPlayer()))
                : super.currentMaxHealth();
    }

    @Override
    protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) {
        if (role() == SuccubusRole.SUCCUBUS) {
            entity.setCustomNameVisible(false);
            FakePlayerTowerVisuals.attach(entity, this);
        }
    }

    @Override
    public void onStateChanged(PlayerLane lane) {
        super.onStateChanged(lane);
        if (role() == SuccubusRole.SUCCUBUS) FakePlayerTowerVisuals.refresh(this);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        if (role() == SuccubusRole.SUCCUBUS) FakePlayerTowerVisuals.remove(this);
        super.onRemoved(lane);
        if (this.lane == lane) this.lane = null;
    }

    @Override
    public void onDeath(PlayerLane lane) {
        if (role() == SuccubusRole.SUCCUBUS) FakePlayerTowerVisuals.remove(this);
    }

    @Override
    public void tick(PlayerLane lane) {
        super.tick(lane);
        if (role() == SuccubusRole.SUCCUBUS) FakePlayerTowerVisuals.tick(this);
    }

    @Override
    public DamageResult damageTargetResult(SemionTowerEntity source, SemionMonsterEntity target, double baseDamage) {
        return super.damageTargetResult(source, target, baseDamage, DamageType.MAGIC);
    }

    @Override
    public Optional<SemionMonsterEntity> selectAttackTarget(SemionTowerEntity source, List<SemionMonsterEntity> candidates) {
        if (candidates == null || candidates.isEmpty()) return Optional.empty();
        Comparator<SemionMonsterEntity> comparator = role() == SuccubusRole.SUCCUBUS
                ? Comparator.comparingInt((SemionMonsterEntity monster) -> SuccubusDreams.sleepCount(monster)).reversed()
                    .thenComparing(Comparator.comparingInt((SemionMonsterEntity monster) -> SuccubusDreams.stacks(monster)).reversed())
                    .thenComparingDouble(source::distanceToSqr)
                : Comparator.comparingInt((SemionMonsterEntity monster) -> SuccubusDreams.stacks(monster)).reversed()
                    .thenComparingDouble(source::distanceToSqr);
        return candidates.stream().min(comparator);
    }

    @Override
    public double modifyResolvedAttackDamage(SemionTowerEntity source, SemionMonsterEntity target, double damage) {
        if (role() != SuccubusRole.NIGHTMARE || !SuccubusDreams.isAsleep(target)) return damage;
        return damage * (1.0 + SuccubusBalance.ability(type().id(), "sleepingDamageBonus", 0.0));
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity source, SemionMonsterEntity target, double damage) {
        return role() == SuccubusRole.SUCCUBUS ? damage + SuccubusAbsorption.attack(ownerPlayer()) : damage;
    }

    @Override
    public void onKill(SemionTowerEntity source, SemionMonsterEntity target, double damageAmount) {
        if (role() == SuccubusRole.SUCCUBUS && lane != null) {
            SuccubusAbsorption.absorb(this, source, target, lane);
        }
    }

    @Override
    public void onAttackResolved(SemionTowerEntity source, SemionMonsterEntity target, double attempted,
                                 double outgoing, double dealt, boolean killed) {
        if (target == null || dealt <= 0.0 || killed || lane == null) return;
        switch (role()) {
            case DREAM_DUST -> {
                int every = Math.max(1, SuccubusBalance.abilityInt(type().id(), "stackEvery", 3));
                if (++attackCount % every == 0) SuccubusDreams.add(target, lane, this, 1);
            }
            case NIGHTMARE -> {
                int minimum = SuccubusBalance.abilityInt(type().id(), "minimumStacks", 5);
                if (SuccubusDreams.stacks(target) >= minimum) SuccubusDreams.add(target, lane, this, 1);
            }
            case SUCCUBUS -> {
                SuccubusDreams.add(target, lane, this, 1);
                FakePlayerTowerVisuals.playAttack(this);
            }
            default -> {
            }
        }
    }

    @Override
    public double modifyIncomingDamage(SemionTowerEntity towerEntity, DamageSource source, double damage) {
        if (role() != SuccubusRole.SLEEPWALKER || !(source.getEntity() instanceof SemionMonsterEntity monster)
                || SuccubusDreams.stacks(monster) <= 0) return damage;
        double reduction = SuccubusBalance.ability(type().id(), "dreamDamageReduction", 0.0);
        return damage * (1.0 - Math.max(0.0, Math.min(0.95, reduction)));
    }

    @Override
    public void onDamaged(SemionTowerEntity towerEntity, DamageSource source, double amount,
                          double previousHealth, double currentHealth) {
        if (role() != SuccubusRole.SLEEPWALKER || lane == null || SuccubusDreams.isAsleep(this)
                || !(source.getEntity() instanceof SemionMonsterEntity monster)) return;
        long now = towerEntity.level().getGameTime();
        long ready = counterReadyAt.getOrDefault(monster.getUUID(), 0L);
        if (now >= ready) {
            int stacks = Math.max(1, SuccubusBalance.abilityInt(type().id(), "counterStacks", 1));
            SuccubusDreams.add(monster, lane, this, stacks);
            int cooldown = Math.max(1, SuccubusBalance.abilityInt(type().id(), "counterCooldownTicks", 60));
            counterReadyAt.put(monster.getUUID(), now + cooldown);
        }
        damageTowardSelfDream += Math.max(0.0, previousHealth - currentHealth);
        double threshold = Math.max(1.0, currentMaxHealth() * 0.10);
        while (damageTowardSelfDream + 1.0e-9 >= threshold) {
            damageTowardSelfDream -= threshold;
            if (!SuccubusDreams.add(this, lane, this, 1)) break;
        }
    }

    @Override
    protected boolean execute(PlayerLane lane) {
        return role() == SuccubusRole.LULLABY && pulse(lane);
    }

    @Override
    protected int cooldownTicksAfterExecute(PlayerLane lane) {
        return role() == SuccubusRole.LULLABY
                ? Math.max(1, SuccubusBalance.abilityInt(type().id(), "pulseIntervalTicks", type().attackIntervalTicks()))
                : super.cooldownTicksAfterExecute(lane);
    }

    private boolean pulse(PlayerLane lane) {
        SemionTowerEntity source = entity(lane);
        if (source == null || !source.isAlive()) return false;
        double radius = SuccubusBalance.ability(type().id(), "radius", 4.5);
        int legacyLimit = Math.max(1, SuccubusBalance.abilityInt(type().id(), "maxTargets", 2));
        int enemyLimit = Math.max(1, SuccubusBalance.abilityInt(type().id(), "enemyMaxTargets", legacyLimit));
        int allyLimit = Math.max(1, SuccubusBalance.abilityInt(type().id(), "allyMaxTargets", legacyLimit));

        Set<UUID> monsters = lane.activeMonsters().stream().filter(monster -> monster.minecraftEntityId() >= 0)
                .map(monster -> lane.arenaWorld().getEntity(monster.minecraftEntityId()))
                .filter(SemionMonsterEntity.class::isInstance).map(SemionMonsterEntity.class::cast)
                .filter(monster -> monster.isAlive() && monster.position().distanceToSqr(source.position()) <= radius * radius)
                .sorted(Comparator.comparingInt((SemionMonsterEntity monster) -> SuccubusDreams.stacks(monster)).reversed())
                .limit(enemyLimit).map(SemionMonsterEntity::getUUID).collect(java.util.stream.Collectors.toSet());
        MonsterAreaEffectRequest monsterRequest = MonsterAreaEffectRequest.aroundTower(
                LULLABY_MONSTERS, source, radius, AreaVfxSpec.onChange(AreaVfxStyles.DEBUFF))
                .withFilter(target -> monsters.contains(target.getUUID()));
        SemionTdApi.areaEffects().applyToMonsters(monsterRequest, target ->
                SuccubusDreams.add(target, lane, this, 1) ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED).appliedCount();

        Set<Tower> towers = lane.towers().stream().filter(tower -> tower.health() > 0.0)
                .filter(tower -> !SuccubusTowers.isSuccubus(tower.type()))
                .filter(tower -> distanceSqr(tower, this) <= radius * radius)
                .sorted(Comparator.comparingInt(SuccubusDreams::stacks)).limit(allyLimit).collect(java.util.stream.Collectors.toSet());
        TowerAreaEffectRequest towerRequest = new TowerAreaEffectRequest(
                LULLABY_TOWERS, source, source.position(), radius, TowerAreaTargetMode.REGISTERED,
                true, target -> towers.contains(target.tower()), AreaVfxSpec.onChange(AreaVfxStyles.BUFF));
        SemionTdApi.areaEffects().applyToTowers(towerRequest, target ->
                SuccubusDreams.addFromLullaby(target.tower(), lane, this, 1)
                        ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED).appliedCount();
        return true;
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (!(previousTower instanceof SuccubusTower previous)) return;
        attackCount = previous.attackCount;
        damageTowardSelfDream = previous.damageTowardSelfDream;
        counterReadyAt.clear();
        counterReadyAt.putAll(previous.counterReadyAt);
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        super.resetForRound(lane);
        attackCount = 0;
        damageTowardSelfDream = 0.0;
        counterReadyAt.clear();
    }

    @Override
    public List<String> runtimeDetailLines() {
        ArrayList<String> lines = new ArrayList<>();
        if (role() == SuccubusRole.SLEEPWALKER) {
            lines.add("꿈 보유 공격자 피해 감소: " + percent(SuccubusBalance.ability(type().id(), "dreamDamageReduction", 0.0)));
        } else if (role() == SuccubusRole.SUCCUBUS) {
            lines.add("꿈 효과 증폭: " + percent(SuccubusBalance.amplification()));
            lines.add("처형 조건: 같은 적 수면 " + SuccubusBalance.executionSleepCount() + "회");
            lines.add("흡수 처치: " + SuccubusAbsorption.kills(ownerPlayer()) + "회");
            lines.add("흡수 공격력: +" + oneDecimal(SuccubusAbsorption.attack(ownerPlayer())));
            lines.add("흡수 최대 체력: +" + oneDecimal(SuccubusAbsorption.health(ownerPlayer())));
        }
        return List.copyOf(lines);
    }

    private SuccubusRole role() {return SuccubusTowers.roleOf(type());}

    SemionTowerEntity entity(PlayerLane lane) {
        if (lane == null || entityId().isEmpty()) return null;
        return lane.arenaWorld().getEntity(entityId().getAsInt()) instanceof SemionTowerEntity entity ? entity : null;
    }

    private static double distanceSqr(Tower a, Tower b) {
        double dx = a.position().x() - b.position().x();
        double dy = a.position().y() - b.position().y();
        double dz = a.position().z() - b.position().z();
        return dx * dx + dy * dy + dz * dz;
    }

}
