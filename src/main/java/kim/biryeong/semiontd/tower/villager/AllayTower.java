package kim.biryeong.semiontd.tower.villager;

import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.SupportTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.resources.ResourceLocation;

public class AllayTower extends SupportTower {
    private static final int SUPPORT_BLOCK_TICKS = 100;
    private static final int BUFF_DURATION_TICKS = 60;
    private static final double T1_HEAL_AMOUNT = 10.0;
    private static final double T2_HEAL_AMOUNT = 20.0;
    private static final double T3_HEAL_AMOUNT = 25.0;
    private static final double T2_WEAPON_BUFF = 0.10;
    private static final double T3_BUFF = 0.15;
    private static final double T3_DAMAGE_REDUCTION = 0.10;
    private static final double T1_RADIUS = 2.0;
    private static final double T2_RADIUS = 3.0;
    private static final ResourceLocation WEAPON_SMITH_SOURCE = supportId("weapon_smith");
    private static final ResourceLocation ARMORER_SOURCE = supportId("armorer");
    private static final TowerDataKey<Long> HEAL_BLOCKED_UNTIL = TowerDataKey.of(supportId("allay_heal_blocked_until"), Long.class);
    private static final TowerDataKey<Long> WEAPON_SMITH_BLOCKED_UNTIL = TowerDataKey.of(supportId("weapon_smith_blocked_until"), Long.class);
    private static final TowerDataKey<Long> ARMORER_BLOCKED_UNTIL = TowerDataKey.of(supportId("armorer_blocked_until"), Long.class);

    public AllayTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public AllayTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition originalPosition, GridPosition currentPosition) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    protected boolean execute(PlayerLane lane) {
        if (type() == VillagerTowers.T1_ALLAY_TOWER) {
            return applyHeal(lane, T1_RADIUS, T1_HEAL_AMOUNT);
        }
        if (type() == VillagerTowers.T2_ALLAY_TOWER) {
            return applyHeal(lane, T2_RADIUS, T2_HEAL_AMOUNT);
        }
        if (type() == VillagerTowers.T2_WEAPON_SMITH_TOWER) {
            return applyWeaponSmithBuff(lane, T1_RADIUS, T2_WEAPON_BUFF);
        }
        if (type() == VillagerTowers.T3_ARMORER_TOWER) {
            return applyArmorerSupport(lane);
        }
        if (type() == VillagerTowers.T3_WEAPON_SMITH_TOWER) {
            return applyWeaponSmithBuff(lane, T2_RADIUS, T3_BUFF);
        }
        return Boolean.FALSE;
    }

    private boolean applyHeal(PlayerLane lane, double radius, double amount) {
        boolean applied = false;
        for (Tower target : nearbyTowers(lane, radius)) {
            if (!canApply(target, HEAL_BLOCKED_UNTIL, lane.arenaWorld().getGameTime())) {
                continue;
            }
            if (heal(target, lane, amount)) {
                block(target, HEAL_BLOCKED_UNTIL, lane.arenaWorld().getGameTime());
                applied = true;
            }
        }
        return applied;
    }

    private boolean applyWeaponSmithBuff(PlayerLane lane, double radius, double magnitude) {
        boolean applied = false;
        for (Tower target : nearbyTowers(lane, radius)) {
            if (!canApply(target, WEAPON_SMITH_BLOCKED_UNTIL, lane.arenaWorld().getGameTime())) {
                continue;
            }
            Optional<SemionTowerEntity> targetEntity = towerEntity(target, lane);
            if (targetEntity.isEmpty()) {
                continue;
            }
            boolean damageApplied = targetEntity.get().applyTimedEffect(
                    TimedEffectType.TOWER_DAMAGE_BONUS,
                    WEAPON_SMITH_SOURCE,
                    magnitude,
                    BUFF_DURATION_TICKS
            );
            boolean speedApplied = targetEntity.get().applyTimedEffect(
                    TimedEffectType.TOWER_ATTACK_SPEED_BONUS,
                    WEAPON_SMITH_SOURCE,
                    magnitude,
                    BUFF_DURATION_TICKS
            );
            if (damageApplied || speedApplied) {
                block(target, WEAPON_SMITH_BLOCKED_UNTIL, lane.arenaWorld().getGameTime());
                applied = true;
            }
        }
        return applied;
    }

    private boolean applyArmorerSupport(PlayerLane lane) {
        boolean applied = false;
        for (Tower target : nearbyTowers(lane, T2_RADIUS)) {
            if (!canApply(target, ARMORER_BLOCKED_UNTIL, lane.arenaWorld().getGameTime())) {
                continue;
            }
            boolean healed = heal(target, lane, T3_HEAL_AMOUNT);
            Optional<SemionTowerEntity> targetEntity = towerEntity(target, lane);
            boolean reducedDamage = targetEntity
                    .map(entity -> entity.applyTimedEffect(
                            TimedEffectType.TOWER_DAMAGE_REDUCTION,
                            ARMORER_SOURCE,
                            T3_DAMAGE_REDUCTION,
                            BUFF_DURATION_TICKS
                    ))
                    .orElse(false);
            if (healed || reducedDamage) {
                block(target, ARMORER_BLOCKED_UNTIL, lane.arenaWorld().getGameTime());
                applied = true;
            }
        }
        return applied;
    }

    private java.util.List<Tower> nearbyTowers(PlayerLane lane, double radius) {
        double radiusSqr = radius * radius;
        return lane.towers().stream()
                .filter(tower -> tower != this)
                .filter(tower -> distanceSqr(tower.position(), position()) <= radiusSqr)
                .toList();
    }

    private boolean heal(Tower target, PlayerLane lane, double amount) {
        Optional<SemionTowerEntity> targetEntity = towerEntity(target, lane);
        if (targetEntity.isPresent()) {
            boolean healed = targetEntity.get().receiveHealing(amount);
            if (healed) {
                targetEntity.get().playHealingAnimation();
            }
            return healed;
        }
        if (target.health() > 0.0 && target.health() < target.maxHealth()) {
            double before = target.health();
            target.syncHealth(before + amount);
            return target.health() > before;
        }
        return Boolean.FALSE;
    }

    private Optional<SemionTowerEntity> towerEntity(Tower target, PlayerLane lane) {
        if (!(target instanceof EntityBackedTower entityBackedTower) || entityBackedTower.entityId().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(lane.arenaWorld().getEntity(entityBackedTower.entityId().getAsInt()))
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast);
    }

    private boolean canApply(Tower target, TowerDataKey<Long> key, long gameTime) {
        return target.getDataOrDefault(key, 0L) <= gameTime;
    }

    private void block(Tower target, TowerDataKey<Long> key, long gameTime) {
        target.setData(key, gameTime + SUPPORT_BLOCK_TICKS);
    }

    private double distanceSqr(GridPosition first, GridPosition second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static ResourceLocation supportId(String path) {
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "tower_support/" + path);
    }
}
