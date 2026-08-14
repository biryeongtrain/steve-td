package kim.biryeong.semiontd.tower.hero;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;

public final class HeroCompanionTower extends HeroPartyTower {
    private static final ResourceLocation MAGE_SPLASH = ResourceLocation.fromNamespaceAndPath("semion-td", "hero_party_mage_splash");
    private static final ResourceLocation BARD_AURA = ResourceLocation.fromNamespaceAndPath("semion-td", "hero_party_bard_aura");
    private static final double[] KNIGHT_REDUCTION = {0.0, 0.07, 0.13, 0.20};
    private static final double[] ARCHER_BOSS_BONUS = {0.0, 0.12, 0.23, 0.35};
    private static final double[] MAGE_SPLASH_RATIO = {0.30, 0.40, 0.50, 0.60};
    private static final double[] MAGE_SPLASH_RADIUS = {2.0, 2.3, 2.6, 3.0};
    private static final double[] PRIEST_HEAL = {14.0, 21.0, 31.0, 45.0};
    private static final int[] PRIEST_INTERVAL = {40, 38, 34, 30};
    private static final double[] PRIEST_SECOND = {0.0, 0.0, 0.50, 1.0};
    private static final double[] ROGUE_EXECUTE = {0.25, 0.35, 0.47, 0.60};
    private static final double[] BARD_SPEED = {0.08, 0.11, 0.14, 0.18};
    private static final double[] BARD_DAMAGE = {0.0, 0.03, 0.06, 0.10};
    private static final double[] BARD_RADIUS = {8.0, 9.0, 10.0, 12.0};

    private int supportCooldown;
    private boolean executeAttackPending;

    public HeroCompanionTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        role().ifPresent(role -> HeroPartyStates.commitCompanion(ownerPlayer(), role));
        super.onPlaced(lane);
    }

    @Override
    public Optional<SemionMonsterEntity> selectAttackTarget(SemionTowerEntity towerEntity, List<SemionMonsterEntity> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        Comparator<SemionMonsterEntity> comparator = switch (role().orElse(HeroCompanionRole.KNIGHT)) {
            case ARCHER -> Comparator
                    .comparing((SemionMonsterEntity target) -> isBoss(target)).reversed()
                    .thenComparing(Comparator.comparingDouble((SemionMonsterEntity target) -> maxHealth(target)).reversed())
                    .thenComparingDouble(target -> target.distanceToSqr(towerEntity));
            case ROGUE -> Comparator
                    .comparingDouble(HeroCompanionTower::healthRatio)
                    .thenComparingDouble(target -> target.distanceToSqr(towerEntity));
            case MAGE -> Comparator
                    .comparingInt((SemionMonsterEntity target) -> nearbyCount(target, candidates)).reversed()
                    .thenComparingDouble(target -> target.distanceToSqr(towerEntity));
            default -> Comparator.comparingDouble(target -> target.distanceToSqr(towerEntity));
        };
        return candidates.stream().min(comparator);
    }

    @Override
    public double modifyResolvedAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        HeroCompanionRole role = role().orElse(null);
        executeAttackPending = false;
        if (role == HeroCompanionRole.ARCHER && isBoss(target)) {
            return damageAmount * (1.0 + value("bossDamageBonus", ARCHER_BOSS_BONUS[index()]));
        }
        if (role == HeroCompanionRole.ROGUE
                && healthRatio(target) <= value("executeThreshold", 0.30)) {
            executeAttackPending = true;
            return damageAmount * (1.0 + value("executeDamageBonus", ROGUE_EXECUTE[index()]));
        }
        return damageAmount;
    }

    @Override
    public double modifyIncomingDamage(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount) {
        if (role().orElse(null) != HeroCompanionRole.KNIGHT) {
            return damageAmount;
        }
        double resolved = damageAmount * (1.0 - value("damageReduction", KNIGHT_REDUCTION[index()]));
        state().recordSpecial(
                HeroQuestKind.KNIGHT_GUARD,
                null,
                Math.max(0.0, damageAmount - resolved),
                onlineOwner(towerEntity)
        );
        return resolved;
    }

    @Override
    public void onAttackResolved(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double attemptedDamage,
            double resolvedOutgoingDamage,
            double dealtDamage,
            boolean killedTarget
    ) {
        HeroPlayerVisuals.playAttack(this);
        HeroCompanionRole role = role().orElse(null);
        state().recordCompanionAttack(role, dealtDamage, killedTarget, isBoss(target), onlineOwner(towerEntity));
        if (role == HeroCompanionRole.ROGUE && executeAttackPending && dealtDamage > 0.0) {
            state().recordSpecial(HeroQuestKind.ROGUE_EXECUTE_HITS, null, 1.0, onlineOwner(towerEntity));
        }
        executeAttackPending = false;
        if (role != HeroCompanionRole.MAGE || target == null) {
            return;
        }
        double ratio = value("splashDamageRatio", MAGE_SPLASH_RATIO[index()]);
        double radius = value("splashRadius", MAGE_SPLASH_RADIUS[index()]);
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                MAGE_SPLASH,
                towerEntity,
                target,
                radius,
                AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)
        );
        TowerAreaDamage.apply(
                this,
                towerEntity,
                request,
                ignored -> attemptedDamage * ratio,
                true,
                (ignored, damage, killed) -> {
                    state().recordCompanionAttack(HeroCompanionRole.MAGE, damage, killed, false, onlineOwner(towerEntity));
                    state().recordSpecial(HeroQuestKind.MAGE_SPLASH_HITS, null, 1.0, onlineOwner(towerEntity));
                },
                DamageType.MAGIC
        );
    }

    @Override
    public void tick(PlayerLane lane) {
        super.tick(lane);
        HeroCompanionRole role = role().orElse(null);
        if (role != HeroCompanionRole.PRIEST && role != HeroCompanionRole.BARD) {
            return;
        }
        if (supportCooldown > 0) {
            supportCooldown--;
            return;
        }
        if (role == HeroCompanionRole.PRIEST) {
            healParty(lane);
            supportCooldown = Math.max(1, HeroPartyBalance.towerInt(type().id(), "healIntervalTicks", PRIEST_INTERVAL[index()]));
        } else {
            applyBardAura(lane);
            supportCooldown = 20;
        }
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof HeroCompanionTower companion) {
            supportCooldown = companion.supportCooldown;
        }
    }

    @Override
    public List<String> runtimeDetailLines() {
        ArrayList<String> lines = new ArrayList<>(super.runtimeDetailLines());
        lines.add("동료: " + role().map(HeroCompanionRole::displayName).orElse("알 수 없음") + " T" + tier());
        return List.copyOf(lines);
    }

    private void healParty(PlayerLane lane) {
        List<Tower> wounded = lane.towers().stream()
                .filter(tower -> tower.ownerPlayer().equals(ownerPlayer()))
                .filter(tower -> HeroPartyTowers.isHeroPartyTower(tower.type()))
                .filter(tower -> tower.health() > 0.0 && tower.health() < tower.currentMaxHealth())
                .sorted(Comparator.comparingDouble(tower -> tower.health() / Math.max(1.0, tower.currentMaxHealth())))
                .toList();
        if (wounded.isEmpty()) {
            return;
        }
        double heal = value("healAmount", PRIEST_HEAL[index()]);
        recordPriestHealing(lane, healTower(lane, wounded.get(0), heal, tier() >= 4 ? 0.10 : 0.0));
        double secondRatio = value("secondTargetRatio", PRIEST_SECOND[index()]);
        if (wounded.size() > 1 && secondRatio > 0.0) {
            recordPriestHealing(lane, healTower(lane, wounded.get(1), heal * secondRatio, tier() >= 4 ? 0.10 : 0.0));
        }
    }

    private double healTower(PlayerLane lane, Tower target, double amount, double reduction) {
        SemionTowerEntity entity = towerEntity(lane, target);
        if (entity != null) {
            double healed = healPartyMember(entity, amount);
            if (reduction > 0.0) {
                entity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION, reduction, 60);
            }
            return healed;
        }
        double previous = target.health();
        target.syncHealth(Math.min(target.currentMaxHealth(), target.health()
                + amount * HeroPartyBalance.partyHealingMultiplier(state().adventurePoints())));
        return Math.max(0.0, target.health() - previous);
    }

    private void recordPriestHealing(PlayerLane lane, double healed) {
        SemionTowerEntity source = towerEntity(lane, this);
        state().recordSpecial(HeroQuestKind.PRIEST_HEALING, null, healed, onlineOwner(source));
    }

    private void applyBardAura(PlayerLane lane) {
        double radius = value("auraRadius", BARD_RADIUS[index()]);
        double radiusSqr = radius * radius;
        double speed = value("attackSpeedBonus", BARD_SPEED[index()]);
        double damage = value("damageBonus", BARD_DAMAGE[index()]);
        for (Tower tower : lane.towers()) {
            if (!tower.ownerPlayer().equals(ownerPlayer())
                    || !HeroPartyTowers.isHeroPartyTower(tower.type())
                    || gridDistanceSqr(tower) > radiusSqr) {
                continue;
            }
            SemionTowerEntity entity = towerEntity(lane, tower);
            if (entity == null) {
                continue;
            }
            entity.applyTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_BONUS, BARD_AURA, speed, 40);
            if (damage > 0.0) {
                entity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, BARD_AURA, damage, 40);
            }
            state().recordSpecial(HeroQuestKind.BARD_AURA_SUPPORT, null, 1.0, onlineOwner(entity));
        }
    }

    private double gridDistanceSqr(Tower tower) {
        double dx = tower.position().x() - position().x();
        double dy = tower.position().y() - position().y();
        double dz = tower.position().z() - position().z();
        return dx * dx + dy * dy + dz * dz;
    }

    private Optional<HeroCompanionRole> role() {
        return HeroPartyTowers.role(type());
    }

    private int tier() {
        return Math.max(1, HeroPartyTowers.tier(type()));
    }

    private int index() {
        return Math.max(0, Math.min(3, tier() - 1));
    }

    private double value(String key, double fallback) {
        return HeroPartyBalance.tower(type().id(), key, fallback);
    }

    private static int nearbyCount(SemionMonsterEntity target, List<SemionMonsterEntity> candidates) {
        if (target == null) {
            return 0;
        }
        return (int) candidates.stream().filter(other -> other != null && other.distanceToSqr(target) <= 9.0).count();
    }

    private static double maxHealth(SemionMonsterEntity target) {
        return target == null || target.runtimeMonster() == null ? 0.0 : target.runtimeMonster().maxHealth();
    }

    private static double healthRatio(SemionMonsterEntity target) {
        if (target == null || target.runtimeMonster() == null) {
            return 1.0;
        }
        return target.runtimeMonster().health() / Math.max(1.0, target.runtimeMonster().maxHealth());
    }

    private static boolean isBoss(SemionMonsterEntity target) {
        return target != null
                && target.runtimeMonster() != null
                && target.runtimeMonster().id().toLowerCase(java.util.Locale.ROOT).contains("boss");
    }
}
