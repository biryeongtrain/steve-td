package kim.biryeong.semiontd.tower.thunder;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.ThunderTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class ThunderTowerCatalogs {
    private ThunderTowerCatalogs() {
    }

    public static void register() {
        register(ThunderTowers.ROD_T1, 1);
        register(ThunderTowers.ROD_COPPER, 2);
        register(ThunderTowers.ROD_STORM, 2);

        register(ThunderTowers.ARMADILLO_T1, 1);
        register(ThunderTowers.ARMADILLO_INSULATED, 2);
        register(ThunderTowers.ARMADILLO_RUBBER, 3);
        register(ThunderTowers.ARMADILLO_GROUNDED, 2);
        register(ThunderTowers.ARMADILLO_EARTH, 3);

        register(ThunderTowers.SQUIRREL_T1, 1);
        register(ThunderTowers.SQUIRREL_T2, 2);
        register(ThunderTowers.SQUIRREL_T3, 3);
        register(ThunderTowers.SURGE_T2, 2);
        register(ThunderTowers.SURGE_T3, 3);

        // Every branch happens at T1, matching every existing builder in the repository.
        link(ThunderTowers.ROD_T1, ThunderTowers.ROD_COPPER);
        link(ThunderTowers.ROD_T1, ThunderTowers.ROD_STORM);

        link(ThunderTowers.ARMADILLO_T1, ThunderTowers.ARMADILLO_INSULATED);
        link(ThunderTowers.ARMADILLO_T1, ThunderTowers.ARMADILLO_GROUNDED);
        link(ThunderTowers.ARMADILLO_INSULATED, ThunderTowers.ARMADILLO_RUBBER);
        link(ThunderTowers.ARMADILLO_GROUNDED, ThunderTowers.ARMADILLO_EARTH);

        link(ThunderTowers.SQUIRREL_T1, ThunderTowers.SQUIRREL_T2);
        link(ThunderTowers.SQUIRREL_T1, ThunderTowers.SURGE_T2);
        link(ThunderTowers.SQUIRREL_T2, ThunderTowers.SQUIRREL_T3);
        link(ThunderTowers.SURGE_T2, ThunderTowers.SURGE_T3);

        if (JobRegistry.find(ThunderTowerJob.ID).isEmpty()) {
            JobRegistry.registerIfAbsent(new ThunderTowerJob());
        }
    }

    private static void register(TowerType type, int tier) {
        if (ProductionTowerCatalog.find(type.id()).isPresent()) {
            return;
        }
        TowerType resolved = TowerBalanceRuntime.resolve(type);
        if (tier == 1) {
            ProductionTowerCatalog.registerStarter(resolved, ThunderTower::new);
        } else {
            ProductionTowerCatalog.register(resolved, ThunderTower::new, tier);
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
