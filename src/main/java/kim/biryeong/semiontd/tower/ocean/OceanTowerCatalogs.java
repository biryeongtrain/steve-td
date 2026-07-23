package kim.biryeong.semiontd.tower.ocean;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.OceanTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class OceanTowerCatalogs {
    private OceanTowerCatalogs() {
    }

    public static void register() {
        registerWater(OceanTowers.T1_WATER, 1);
        registerWater(OceanTowers.T2_SPRING_WATER, 2);
        registerWater(OceanTowers.T3_CURRENT, 3);
        registerCombat(OceanTowers.T1_PUFFERFISH, 1);
        registerCombat(OceanTowers.T2_GUARDIAN, 2);
        registerCombat(OceanTowers.T3_ELDER_GUARDIAN, 3);
        registerCombat(OceanTowers.T1_TROPICAL_FISH, 1);
        registerCombat(OceanTowers.T2_LARGE_TROPICAL_FISH, 2);
        registerCombat(OceanTowers.T3_GIANT_TROPICAL_FISH, 3);
        registerCombat(OceanTowers.T1_SQUID, 1);
        registerCombat(OceanTowers.T2_GLOW_SQUID, 2);
        registerCombat(OceanTowers.T3_DOLPHIN, 3);
        registerCombat(OceanTowers.T1_SALMON, 1);
        registerCombat(OceanTowers.T2_LARGE_SALMON, 2);
        registerCombat(OceanTowers.T3_GIANT_SALMON, 3);
        registerCombat(OceanTowers.T1_COD, 1);
        registerCombat(OceanTowers.T2_LARGE_COD, 2);
        registerCombat(OceanTowers.T3_GIANT_COD, 3);

        link(OceanTowers.T1_WATER, OceanTowers.T2_SPRING_WATER);
        link(OceanTowers.T2_SPRING_WATER, OceanTowers.T3_CURRENT);
        link(OceanTowers.T1_PUFFERFISH, OceanTowers.T2_GUARDIAN);
        link(OceanTowers.T2_GUARDIAN, OceanTowers.T3_ELDER_GUARDIAN);
        link(OceanTowers.T1_TROPICAL_FISH, OceanTowers.T2_LARGE_TROPICAL_FISH);
        link(OceanTowers.T2_LARGE_TROPICAL_FISH, OceanTowers.T3_GIANT_TROPICAL_FISH);
        link(OceanTowers.T1_SQUID, OceanTowers.T2_GLOW_SQUID);
        link(OceanTowers.T2_GLOW_SQUID, OceanTowers.T3_DOLPHIN);
        link(OceanTowers.T1_SALMON, OceanTowers.T2_LARGE_SALMON);
        link(OceanTowers.T2_LARGE_SALMON, OceanTowers.T3_GIANT_SALMON);
        link(OceanTowers.T1_COD, OceanTowers.T2_LARGE_COD);
        link(OceanTowers.T2_LARGE_COD, OceanTowers.T3_GIANT_COD);

        if (JobRegistry.find(OceanTowerJob.ID).isEmpty()) {
            JobRegistry.registerIfAbsent(new OceanTowerJob());
        }
    }

    private static void registerWater(TowerType type, int tier) {
        register(type, OceanWaterTower::new, tier);
    }

    private static void registerCombat(TowerType type, int tier) {
        register(type, OceanTower::new, tier);
    }

    private static void register(TowerType type, ProductionTowerCatalog.TowerFactory factory, int tier) {
        if (ProductionTowerCatalog.find(type.id()).isPresent()) {
            return;
        }
        TowerType resolved = TowerBalanceRuntime.resolve(type);
        if (tier == 1) {
            ProductionTowerCatalog.registerStarter(resolved, factory);
        } else {
            ProductionTowerCatalog.register(resolved, factory, tier);
        }
    }

    private static void link(TowerType from, TowerType to) {
        if (ProductionTowerCatalog.upgrade(from, to.id()).isPresent()) {
            return;
        }
        TowerType target = ProductionTowerCatalog.find(to.id()).map(ProductionTowerCatalog.CatalogEntry::type).orElse(to);
        ProductionTowerCatalog.linkUpgrade(from, to.id(), to.displayName(), target, TowerBalanceRuntime.upgradeCost(from, to.id()));
    }
}
