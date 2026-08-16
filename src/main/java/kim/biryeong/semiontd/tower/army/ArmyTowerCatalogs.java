package kim.biryeong.semiontd.tower.army;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.ArmyTowerJob;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class ArmyTowerCatalogs {
    private ArmyTowerCatalogs() {
    }

    public static void register() {
        register(ArmyTowers.CLERK, 1);
        register(ArmyTowers.DRILL_SERGEANT, 2);
        register(ArmyTowers.QUARTERMASTER, 2);

        register(ArmyTowers.GUARD, 1);
        register(ArmyTowers.MILITARY_POLICE, 2);
        register(ArmyTowers.MP_COMMANDER, 3);
        register(ArmyTowers.GOP_SENTRY, 2);
        register(ArmyTowers.OUTPOST_CHIEF, 3);

        register(ArmyTowers.RECRUIT, 1);
        register(ArmyTowers.SPECIALIST, 2);
        register(ArmyTowers.PLATOON_LEADER, 3);
        register(ArmyTowers.GUNNER, 2);
        register(ArmyTowers.BATTERY_CHIEF, 3);

        // Every branch happens at T1, matching every existing builder in the repository.
        link(ArmyTowers.CLERK, ArmyTowers.DRILL_SERGEANT);
        link(ArmyTowers.CLERK, ArmyTowers.QUARTERMASTER);

        link(ArmyTowers.GUARD, ArmyTowers.MILITARY_POLICE);
        link(ArmyTowers.GUARD, ArmyTowers.GOP_SENTRY);
        link(ArmyTowers.MILITARY_POLICE, ArmyTowers.MP_COMMANDER);
        link(ArmyTowers.GOP_SENTRY, ArmyTowers.OUTPOST_CHIEF);

        link(ArmyTowers.RECRUIT, ArmyTowers.SPECIALIST);
        link(ArmyTowers.RECRUIT, ArmyTowers.GUNNER);
        link(ArmyTowers.SPECIALIST, ArmyTowers.PLATOON_LEADER);
        link(ArmyTowers.GUNNER, ArmyTowers.BATTERY_CHIEF);

        if (JobRegistry.find(ArmyTowerJob.ID).isEmpty()) {
            JobRegistry.registerIfAbsent(new ArmyTowerJob());
        }
    }

    private static void register(TowerType type, int tier) {
        if (ProductionTowerCatalog.find(type.id()).isPresent()) {
            return;
        }
        TowerType resolved = TowerBalanceRuntime.resolve(type);
        if (tier == 1) {
            ProductionTowerCatalog.registerStarter(resolved, ArmyTower::new);
        } else {
            ProductionTowerCatalog.register(resolved, ArmyTower::new, tier);
        }
    }

    private static void link(TowerType from, TowerType to) {
        if (ProductionTowerCatalog.upgrade(from, to.id()).isPresent()) {
            return;
        }
        TowerType target = ProductionTowerCatalog.find(to.id())
                .map(ProductionTowerCatalog.CatalogEntry::type)
                .orElse(to);
        ProductionTowerCatalog.linkUpgrade(
                from,
                to.id(),
                to.displayName(),
                target,
                TowerBalanceRuntime.upgradeCost(from, to.id())
        );
    }
}
