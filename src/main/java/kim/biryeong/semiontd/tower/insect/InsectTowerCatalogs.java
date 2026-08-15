package kim.biryeong.semiontd.tower.insect;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class InsectTowerCatalogs {
    private InsectTowerCatalogs() {
    }

    public static void register() {
        registerUnit(InsectTowers.SILVERFISH, 1);
        registerUnit(InsectTowers.ENDERMITE, 2);
        registerUnit(InsectTowers.ENHANCED_ENDERMITE, 3);
        registerUnit(InsectTowers.CAVE_SPIDER, 1);
        registerUnit(InsectTowers.SPIDER, 2);
        registerUnit(InsectTowers.ENHANCED_SPIDER, 3);
        registerUnit(InsectTowers.BEE, 1);
        registerUnit(InsectTowers.ENHANCED_BEE, 2);
        registerUnit(InsectTowers.QUEEN_BEE, 3);
        ProductionTowerCatalog.registerStarter(
                TowerBalanceRuntime.resolve(InsectTowers.SPAWNER), InsectSpawnerTower::new);

        link(InsectTowers.SILVERFISH, InsectTowers.ENDERMITE);
        link(InsectTowers.ENDERMITE, InsectTowers.ENHANCED_ENDERMITE);
        link(InsectTowers.CAVE_SPIDER, InsectTowers.SPIDER);
        link(InsectTowers.SPIDER, InsectTowers.ENHANCED_SPIDER);
        link(InsectTowers.BEE, InsectTowers.ENHANCED_BEE);
        link(InsectTowers.ENHANCED_BEE, InsectTowers.QUEEN_BEE);
    }

    private static void registerUnit(TowerType defaults, int tier) {
        TowerType type = TowerBalanceRuntime.resolve(defaults);
        if (tier == 1) {
            ProductionTowerCatalog.registerStarter(type, InsectUnitTower::new);
        } else {
            ProductionTowerCatalog.register(type, InsectUnitTower::new, tier);
        }
    }

    private static void link(TowerType fromDefaults, TowerType toDefaults) {
        TowerType from = resolved(fromDefaults);
        TowerType to = resolved(toDefaults);
        ProductionTowerCatalog.linkUpgrade(
                from, to.id(), "강화", to, TowerBalanceRuntime.upgradeCost(fromDefaults, to.id()));
    }

    private static TowerType resolved(TowerType defaults) {
        return ProductionTowerCatalog.find(defaults.id())
                .map(ProductionTowerCatalog.CatalogEntry::type)
                .orElse(defaults);
    }
}
