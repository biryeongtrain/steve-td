package kim.biryeong.semiontd.tower.ancientcity;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.AncientCityTowerJob;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class AncientCityTowerCatalogs {
    private AncientCityTowerCatalogs() {
    }

    public static void register() {
        register(AncientCityTowers.CATALYST_T1, 1);
        register(AncientCityTowers.CATALYST_T2, 2);
        register(AncientCityTowers.CATALYST_T3, 3);
        register(AncientCityTowers.SENSOR_T1, 1);
        register(AncientCityTowers.SENSOR_T2, 2);
        register(AncientCityTowers.SENSOR_T3, 3);
        register(AncientCityTowers.SHRIEKER_T1, 1);
        register(AncientCityTowers.SHRIEKER_T2, 2);
        register(AncientCityTowers.SHRIEKER_T3, 3);
        register(AncientCityTowers.WARDEN_T1, 1);
        register(AncientCityTowers.WARDEN_T2, 2);
        register(AncientCityTowers.WARDEN_T3, 3);
        register(AncientCityTowers.WARDEN_T4, 4);

        link(AncientCityTowers.CATALYST_T1, AncientCityTowers.CATALYST_T2);
        link(AncientCityTowers.CATALYST_T2, AncientCityTowers.CATALYST_T3);
        link(AncientCityTowers.SENSOR_T1, AncientCityTowers.SENSOR_T2);
        link(AncientCityTowers.SENSOR_T2, AncientCityTowers.SENSOR_T3);
        link(AncientCityTowers.SHRIEKER_T1, AncientCityTowers.SHRIEKER_T2);
        link(AncientCityTowers.SHRIEKER_T2, AncientCityTowers.SHRIEKER_T3);
        link(AncientCityTowers.WARDEN_T1, AncientCityTowers.WARDEN_T2);
        link(AncientCityTowers.WARDEN_T2, AncientCityTowers.WARDEN_T3);
        link(AncientCityTowers.WARDEN_T3, AncientCityTowers.WARDEN_T4);

        if (JobRegistry.find(AncientCityTowerJob.ID).isEmpty()) {
            JobRegistry.registerIfAbsent(new AncientCityTowerJob());
        }
    }

    private static void register(TowerType type, int tier) {
        if (ProductionTowerCatalog.find(type.id()).isPresent()) {
            return;
        }
        TowerType resolved = TowerBalanceRuntime.resolve(type);
        if (tier == 1) {
            ProductionTowerCatalog.registerStarter(resolved, AncientCityTower::new);
        } else {
            ProductionTowerCatalog.register(resolved, AncientCityTower::new, tier);
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
