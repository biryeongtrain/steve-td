package kim.biryeong.semiontd.tower.magicschool;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class MagicSchoolTowerCatalogs {
    private MagicSchoolTowerCatalogs() {
    }

    public static void register() {
        ProductionTowerCatalog.registerStarter(TowerBalanceRuntime.resolve(MagicSchoolTowers.HOGWARTS), HogwartsTower::new);
        ProductionTowerCatalog.registerStarter(TowerBalanceRuntime.resolve(MagicSchoolTowers.FRESHMAN), FreshmanTower::new);
        for (TowerType type : MagicSchoolTowers.houseWizards()) {
            ProductionTowerCatalog.register(TowerBalanceRuntime.resolve(type), HouseWizardTower::new, 2);
        }
        for (TowerType type : MagicSchoolTowers.archWizards()) {
            ProductionTowerCatalog.register(TowerBalanceRuntime.resolve(type), HouseWizardTower::new, 3);
        }
        MagicSchoolTowers.houseWizards().forEach(type -> link(MagicSchoolTowers.FRESHMAN, type));
        MagicSchoolTowers.houseWizards().forEach(type -> link(type, MagicSchoolTowers.archWizardFor(type)));
    }

    private static void link(TowerType from, TowerType to) {
        TowerType target = ProductionTowerCatalog.find(to.id()).orElseThrow().type();
        ProductionTowerCatalog.linkUpgrade(from, to.id(), to.displayName(), target,
                TowerBalanceRuntime.upgradeCost(from, to.id()));
    }
}
