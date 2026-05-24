package kim.biryeong.semiontd.tower.warlock;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.WarlockTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class WarlockTowerCatalogs {
    private WarlockTowerCatalogs() {
    }

    public static void register() {
        registerTower(WarlockTowers.BASE_WARLOCK_TOWER, WarlockTower::new, 1);
        registerTower(WarlockTowers.RANGED_WARLOCK_TOWER, WarlockTower::new, 2);
        registerTower(WarlockTowers.MELEE_WARLOCK_TOWER, WarlockTower::new, 2);

        registerTower(WarlockTowers.T1_SLAVE, WarlockSacrificeTower::new, 1);
        registerTower(WarlockTowers.T2_SLAVE, WarlockSacrificeTower::new, 2);
        registerTower(WarlockTowers.T3_SLAVE, WarlockSacrificeTower::new, 3);
        registerTower(WarlockTowers.T1_RANGED_SLAVE, WarlockSacrificeTower::new, 1);
        registerTower(WarlockTowers.T2_RANGED_SLAVE, WarlockSacrificeTower::new, 2);
        registerTower(WarlockTowers.T3_RANGED_SLAVE, WarlockSacrificeTower::new, 3);

        link(WarlockTowers.BASE_WARLOCK_TOWER, "ranged_warlock_tower", "원거리 흑마법사 타워", WarlockTowers.RANGED_WARLOCK_TOWER);
        link(WarlockTowers.BASE_WARLOCK_TOWER, "melee_warlock_tower", "근접 흑마법사 타워", WarlockTowers.MELEE_WARLOCK_TOWER);
        link(WarlockTowers.T1_SLAVE, "t2_slave", "희생\"양\"", WarlockTowers.T2_SLAVE);
        link(WarlockTowers.T2_SLAVE, "t3_slave", "희생\"양\"", WarlockTowers.T3_SLAVE);
        link(WarlockTowers.T1_RANGED_SLAVE, "t2_ranged_slave", "애완 개구리", WarlockTowers.T2_RANGED_SLAVE);
        link(WarlockTowers.T2_RANGED_SLAVE, "t3_ranged_slave", "애완 개구리", WarlockTowers.T3_RANGED_SLAVE);

        JobRegistry.registerIfAbsent(new WarlockTowerJob());
    }

    private static void registerTower(TowerType type, ProductionTowerCatalog.TowerFactory factory, int tier) {
        if (ProductionTowerCatalog.find(type.id()).isPresent()) {
            return;
        }
        TowerType resolvedType = TowerBalanceRuntime.resolve(type);
        if (tier == 1) {
            ProductionTowerCatalog.registerStarter(resolvedType, factory);
            return;
        }
        ProductionTowerCatalog.register(resolvedType, factory, tier);
    }

    private static void link(TowerType from, String id, String displayName, TowerType to) {
        if (ProductionTowerCatalog.upgrade(from, id).isPresent()) {
            return;
        }
        TowerType targetType = ProductionTowerCatalog.find(to.id()).map(ProductionTowerCatalog.CatalogEntry::type).orElse(to);
        ProductionTowerCatalog.linkUpgrade(from, id, displayName, targetType, TowerBalanceRuntime.upgradeCost(from, id));
    }
}
