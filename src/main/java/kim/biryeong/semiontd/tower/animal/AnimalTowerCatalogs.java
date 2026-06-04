package kim.biryeong.semiontd.tower.animal;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.AnimalTowerJob;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class AnimalTowerCatalogs {
    private AnimalTowerCatalogs() {
    }

    public static void register() {
        registerTower(AnimalTowers.T1_PIG_TOWER, PigTower::new, 1);
        registerTower(AnimalTowers.T2_PIG_TOWER, PigTower::new, 2);
        registerTower(AnimalTowers.T3_PIG_TOWER, PigTower::new, 3);

        registerTower(AnimalTowers.T1_WOLF_TOWER, WolfTower::new, 1);
        registerTower(AnimalTowers.T2_WOLF_DPS_TOWER, WolfTower::new, 2);
        registerTower(AnimalTowers.T3_WOLF_DPS_TOWER, WolfTower::new, 3);

        registerTower(AnimalTowers.T1_RABBIT_TOWER, RabbitTower::new, 1);
        registerTower(AnimalTowers.T2_RABBIT_TOWER, RabbitTower::new, 2);
        registerTower(AnimalTowers.T3_RABBIT_TOWER, RabbitTower::new, 3);

        registerTower(AnimalTowers.T1_FOX_TOWER, FoxTower::new, 1);
        registerTower(AnimalTowers.T2_FOX_TOWER, FoxTower::new, 2);
        registerTower(AnimalTowers.T3_FOX_TOWER, FoxTower::new, 3);

        link(AnimalTowers.T1_PIG_TOWER, "t2_pig_tower", "돼지 타워", AnimalTowers.T2_PIG_TOWER);
        link(AnimalTowers.T2_PIG_TOWER, "t3_pig_tower", "돼지 타워", AnimalTowers.T3_PIG_TOWER);
        link(AnimalTowers.T1_WOLF_TOWER, "t2_wolf_dps_tower", "재빠른 늑구 타워", AnimalTowers.T2_WOLF_DPS_TOWER);
        link(AnimalTowers.T2_WOLF_DPS_TOWER, "t3_wolf_dps_tower", "개빠른 늑구 타워", AnimalTowers.T3_WOLF_DPS_TOWER);
        link(AnimalTowers.T1_RABBIT_TOWER, "t2_rabbit_tower", "토끼 타워", AnimalTowers.T2_RABBIT_TOWER);
        link(AnimalTowers.T2_RABBIT_TOWER, "t3_rabbit_tower", "토끼 타워", AnimalTowers.T3_RABBIT_TOWER);
        link(AnimalTowers.T1_FOX_TOWER, "t2_fox_tower", "붉은 여우 타워", AnimalTowers.T2_FOX_TOWER);
        link(AnimalTowers.T2_FOX_TOWER, "t3_fox_tower", "설원 여우 타워", AnimalTowers.T3_FOX_TOWER);

        JobRegistry.registerIfAbsent(new AnimalTowerJob());
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
