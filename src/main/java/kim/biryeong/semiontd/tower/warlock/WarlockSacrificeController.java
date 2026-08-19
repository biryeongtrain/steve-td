package kim.biryeong.semiontd.tower.warlock;

import static kim.biryeong.semiontd.tower.warlock.WarlockConfig.Ability.*;

import java.util.Comparator;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import net.minecraft.world.phys.Vec3;

final class WarlockSacrificeController {
    private final WarlockConfig config;
    private final WarlockState state;

    WarlockSacrificeController(WarlockConfig config, WarlockState state) {
        this.config = config;
        this.state = state;
    }

    boolean sacrifice(
            WarlockTower warlock,
            SemionTowerEntity towerEntity,
            PlayerLane lane,
            double radius,
            Comparator<Tower> priority
    ) {
        if (warlock == null
                || towerEntity == null
                || towerEntity.runtimeTower() != warlock
                || lane == null
                || priority == null) {
            return false;
        }
        Tower target = lane.towers().stream()
                .filter(tower -> isEligibleTarget(warlock, tower, radius))
                .min(priority)
                .orElse(null);
        if (target == null) {
            return false;
        }

        double sacrificedHealth = target.currentMaxHealth();
        double sacrificedDamage = target.modifyAttackDamage(null, null, target.type().damage());
        int sacrificedInterval = target.type().attackIntervalTicks();
        Vec3 center = sacrificedCenter(lane, target);
        if (!lane.killTower(target)) {
            return false;
        }

        TowerVfxService.showWarlockSacrifice(towerEntity, center);
        double previousMaxHealth = warlock.currentMaxHealth();
        absorbStats(warlock, sacrificedHealth, sacrificedDamage, sacrificedInterval);
        double increasedMaxHealth = Math.max(0.0, warlock.currentMaxHealth() - previousMaxHealth);
        double healAmount = increasedMaxHealth + Math.max(0.0, config.value(ABSORPTION_HEAL));
        warlock.refreshAfterSacrifice(lane, towerEntity, healAmount);
        return true;
    }

    double passiveHealthBonus(WarlockTower warlock, PlayerLane lane) {
        if (warlock.is(WarlockTowers.RANGED_WARLOCK_TOWER)) {
            return passiveBonus(warlock, lane, RANGED_PET_HEALTH, RANGED_PET_HEALTH_CAP);
        }
        if (warlock.is(WarlockTowers.MELEE_WARLOCK_TOWER)) {
            return passiveBonus(warlock, lane, MELEE_PET_HEALTH, MELEE_PET_HEALTH_CAP);
        }
        return 0.0;
    }

    double passiveDamageBonus(WarlockTower warlock, PlayerLane lane) {
        if (warlock.is(WarlockTowers.RANGED_WARLOCK_TOWER)) {
            return passiveBonus(warlock, lane, RANGED_PET_DAMAGE, RANGED_PET_DAMAGE_CAP);
        }
        if (warlock.is(WarlockTowers.MELEE_WARLOCK_TOWER)) {
            return passiveBonus(warlock, lane, MELEE_PET_DAMAGE, MELEE_PET_DAMAGE_CAP);
        }
        return 0.0;
    }

    double damageReduction(WarlockTower warlock) {
        if (warlock.is(WarlockTowers.RANGED_WARLOCK_TOWER)
                && state.roundSacrificeCount() > config.integer(RANGED_DEFENSE_THRESHOLD)) {
            return Math.max(0.0, config.value(RANGED_DEFENSE));
        }
        if (warlock.is(WarlockTowers.MELEE_WARLOCK_TOWER)) {
            int every = config.integer(MELEE_DEFENSE_EVERY);
            if (every <= 0) {
                return 0.0;
            }
            return Math.min(
                    Math.max(0.0, config.value(MELEE_DEFENSE_CAP)),
                    (state.totalSacrificeCount() / every) * Math.max(0.0, config.value(MELEE_DEFENSE_STEP))
            );
        }
        return 0.0;
    }

    double maximumDamageReduction(WarlockTower warlock) {
        if (warlock.is(WarlockTowers.RANGED_WARLOCK_TOWER)) {
            return Math.max(0.0, config.value(RANGED_DEFENSE));
        }
        if (warlock.is(WarlockTowers.MELEE_WARLOCK_TOWER)) {
            return Math.max(0.0, config.value(MELEE_DEFENSE_CAP));
        }
        return 0.0;
    }

    private void absorbStats(
            WarlockTower warlock,
            double sacrificedHealth,
            double sacrificedDamage,
            int sacrificedIntervalTicks
    ) {
        if (warlock.is(WarlockTowers.BASE_WARLOCK_TOWER)) {
            state.absorbBasePermanently(
                    sacrificedHealth,
                    sacrificedDamage,
                    config.value(BASE_PERMANENT_HEALTH),
                    config.value(BASE_PERMANENT_DAMAGE)
            );
        }

        if (warlock.is(WarlockTowers.RANGED_WARLOCK_TOWER)) {
            state.absorbForRound(
                    sacrificedHealth,
                    sacrificedDamage,
                    config.value(RANGED_ROUND_STAT)
            );
            state.absorbPermanently(
                    sacrificedHealth,
                    sacrificedDamage,
                    config.value(RANGED_PERMANENT_HEALTH),
                    config.value(RANGED_PERMANENT_DAMAGE)
            );
            state.absorbAttackInterval(
                    warlock.type().attackIntervalTicks(),
                    sacrificedIntervalTicks,
                    config.value(SPEED_CAP)
            );
            return;
        }

        if (warlock.is(WarlockTowers.MELEE_WARLOCK_TOWER)) {
            state.absorbForRound(
                    sacrificedHealth,
                    sacrificedDamage,
                    config.value(MELEE_ROUND_STAT)
            );
            state.absorbPermanently(
                    sacrificedHealth,
                    sacrificedDamage,
                    config.value(MELEE_PERMANENT_HEALTH),
                    config.value(MELEE_PERMANENT_DAMAGE)
            );
            return;
        }
    }

    private double passiveBonus(
            WarlockTower warlock,
            PlayerLane lane,
            WarlockConfig.Ability perStack,
            WarlockConfig.Ability cap
    ) {
        int stacks = passiveStackCount(warlock, lane);
        return Math.min(Math.max(0.0, config.value(cap)), stacks * Math.max(0.0, config.value(perStack)));
    }

    private int passiveStackCount(WarlockTower warlock, PlayerLane lane) {
        if (lane == null) {
            return 0;
        }
        return (int) lane.towers().stream()
                .filter(tower -> tower != warlock)
                .filter(tower -> tower.health() > 0.0)
                .filter(tower -> sameOwner(warlock, tower))
                .filter(tower -> isPassiveStackTower(warlock, tower))
                .count();
    }

    private boolean isPassiveStackTower(WarlockTower warlock, Tower tower) {
        if (warlock.is(WarlockTowers.RANGED_WARLOCK_TOWER)) {
            return WarlockTowers.isRangedSlave(tower.type());
        }
        if (warlock.is(WarlockTowers.MELEE_WARLOCK_TOWER)) {
            return WarlockTowers.isMeleeSlave(tower.type());
        }
        return false;
    }

    private static boolean sameOwner(WarlockTower warlock, Tower tower) {
        return tower != null && warlock.ownerPlayer().equals(tower.ownerPlayer());
    }

    static boolean isEligibleTarget(WarlockTower warlock, Tower tower, double radius) {
        return warlock != null
                && tower != null
                && tower != warlock
                && tower.health() > 0.0
                && !WarlockTowers.isWarlockCore(tower.type())
                && sameOwner(warlock, tower)
                && withinRadius(warlock, tower, radius);
    }

    private static boolean withinRadius(WarlockTower warlock, Tower tower, double radius) {
        if (tower == null || radius <= 0.0) {
            return tower != null;
        }
        double dx = tower.position().x() - warlock.position().x();
        double dy = tower.position().y() - warlock.position().y();
        double dz = tower.position().z() - warlock.position().z();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    private static Vec3 sacrificedCenter(PlayerLane lane, Tower target) {
        if (target instanceof EntityBackedTower entityBacked && entityBacked.entityId().isPresent()) {
            var entity = lane.arenaWorld().getEntity(entityBacked.entityId().getAsInt());
            if (entity instanceof SemionTowerEntity towerEntity) {
                return new Vec3(
                        towerEntity.getX(),
                        towerEntity.getY() + Math.max(0.35, towerEntity.getBbHeight() * 0.65),
                        towerEntity.getZ()
                );
            }
        }
        return new Vec3(
                target.position().x() + 0.5,
                target.position().y() + 1.5,
                target.position().z() + 0.5
        );
    }
}
