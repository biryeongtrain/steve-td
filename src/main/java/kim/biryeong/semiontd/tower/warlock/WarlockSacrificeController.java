package kim.biryeong.semiontd.tower.warlock;

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

    boolean absorbNearest(
            WarlockTower warlock,
            SemionTowerEntity towerEntity,
            PlayerLane lane,
            Comparator<Tower> priority
    ) {
        if (warlock == null
                || towerEntity == null
                || towerEntity.runtimeTower() != warlock
                || lane == null
                || priority == null) {
            return false;
        }
        WarlockRules.SacrificeRule rule = config.path(warlock.path()).sacrifice();
        Tower target = lane.towers().stream()
                .filter(tower -> isEligibleTarget(warlock, tower, rule))
                .min(deterministicPriority(warlock, priority))
                .orElse(null);
        if (target == null) {
            return false;
        }

        WarlockSacrifice.Snapshot snapshot = WarlockSacrifice.snapshot(target);
        Vec3 center = sacrificedCenter(lane, target);
        WarlockSacrifice.Gain gain = sacrificeGain(warlock, snapshot);
        boolean killed = lane.killTower(target);
        if (!killed) {
            return false;
        }

        double previousMaxHealth = warlock.currentMaxHealth();
        if (!WarlockSacrifice.commit(true, state, gain)) {
            return false;
        }
        double increasedMaxHealth = Math.max(0.0, warlock.currentMaxHealth() - previousMaxHealth);
        warlock.refreshAfterSacrifice(lane, towerEntity, increasedMaxHealth + rule.completionHealing());
        TowerVfxService.showWarlockSacrifice(towerEntity, center);
        return true;
    }

    double passiveHealthBonus(WarlockTower warlock, PlayerLane lane) {
        return config.path(warlock.path()).passive().healthBonus(passiveStackCount(warlock, lane));
    }

    double passiveDamageBonus(WarlockTower warlock, PlayerLane lane) {
        return config.path(warlock.path()).passive().damageBonus(passiveStackCount(warlock, lane));
    }

    double damageReduction(WarlockPath path) {
        var progression = WarlockProgressionSnapshot.from(state, null);
        return config.path(path).defense().value(progression.defenseSacrificeCount(path));
    }

    double maximumDamageReduction(WarlockPath path) {
        return config.path(path).defense().maximum();
    }

    private WarlockSacrifice.Gain sacrificeGain(WarlockTower warlock, WarlockSacrifice.Snapshot snapshot) {
        WarlockPath path = warlock.path();
        return WarlockSacrifice.calculate(
                path,
                snapshot,
                config.path(path),
                config.combat(),
                warlock.type().attackIntervalTicks()
        );
    }

    private int passiveStackCount(WarlockTower warlock, PlayerLane lane) {
        if (lane == null || warlock.path() == WarlockPath.BASE) {
            return 0;
        }
        return (int) lane.towers().stream()
                .filter(tower -> tower != warlock)
                .filter(tower -> tower.health() > 0.0)
                .filter(tower -> sameOwner(warlock, tower))
                .filter(tower -> warlock.path().acceptsSacrificeTower(tower.type()))
                .count();
    }

    private static boolean sameOwner(WarlockTower warlock, Tower tower) {
        return tower != null && warlock.ownerPlayer().equals(tower.ownerPlayer());
    }

    static boolean isEligibleTarget(
            WarlockTower warlock,
            Tower tower,
            WarlockRules.SacrificeRule rule
    ) {
        return warlock != null
                && tower != null
                && rule != null
                && tower != warlock
                && !tower.isAugmentTower()
                && tower.health() > 0.0
                && !WarlockTowers.isWarlockCore(tower.type())
                && warlock.path().acceptsSacrificeTower(tower.type())
                && sameOwner(warlock, tower)
                && rule.includes(squaredDistance(warlock, tower));
    }

    private static double squaredDistance(WarlockTower warlock, Tower tower) {
        double dx = tower.position().x() - warlock.position().x();
        double dy = tower.position().y() - warlock.position().y();
        double dz = tower.position().z() - warlock.position().z();
        return dx * dx + dy * dy + dz * dz;
    }

    static Comparator<Tower> deterministicPriority(WarlockTower warlock, Comparator<Tower> priority) {
        return priority
                .thenComparingDouble(tower -> squaredDistance(warlock, tower))
                .thenComparingInt(tower -> tower.position().x())
                .thenComparingInt(tower -> tower.position().y())
                .thenComparingInt(tower -> tower.position().z());
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
