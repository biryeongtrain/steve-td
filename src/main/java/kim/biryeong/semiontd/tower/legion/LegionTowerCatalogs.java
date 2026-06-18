package kim.biryeong.semiontd.tower.legion;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.LegionTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class LegionTowerCatalogs {
    private LegionTowerCatalogs() {
    }

    public static void register() {
        registerTower(LegionTowers.T1_CHICKEN, LegionChickenTower::new, 1);
        registerTower(LegionTowers.T2_CHICKEN_TOWER, LegionChickenTower::new, 2);
        registerTower(LegionTowers.T2_DPS_CHICKEN_TOWER, LegionChickenTower::new, 2);

        registerTower(LegionTowers.T1_SLIME_TOWER, LegionSlimeTower::new, 1);
        registerTower(LegionTowers.T2_SLIME_TOWER, LegionSlimeTower::new, 2);

        registerTower(LegionTowers.T1_PENGUIN, LegionPenguinTower::new, 1);
        registerTower(LegionTowers.T2_PENGUIN, LegionPenguinTower::new, 2);

        registerTower(LegionTowers.T1_PARROT_TOWER, LegionParrotTower::new, 1);
        registerTower(LegionTowers.T2_PARROT_TOWER, LegionParrotTower::new, 2);

        registerTower(LegionTowers.T1_GOAT_TOWER, LegionGoatTower::new, 1);
        registerTower(LegionTowers.T2_STRONG_GOAT_TOWER, LegionGoatTower::new, 2);
        registerTower(LegionTowers.T3_EXTREME_GOAT_TOWER, LegionGoatTower::new, 3);

//        registerTower(LegionTowers.T1_BEE_TOWER, BeeTower::new, 1);
//        registerTower(LegionTowers.T2_BEE_TOWER, BeeTower::new, 2);
//        registerTower(LegionTowers.T3_BEE_TOWER, BeeTower::new, 3);

        registerTower(LegionTowers.ILLUSION_TOWER, LegionGlobalIllusionTower::new, 1);

        link(LegionTowers.T1_CHICKEN, LegionTowers.T2_CHICKEN_TOWER.id(), "인싸 닭 타워", LegionTowers.T2_CHICKEN_TOWER);
        link(LegionTowers.T1_CHICKEN, LegionTowers.T2_DPS_CHICKEN_TOWER.id(), "아찐 닭 타워", LegionTowers.T2_DPS_CHICKEN_TOWER);
        link(LegionTowers.T1_SLIME_TOWER, LegionTowers.T2_SLIME_TOWER.id(), "슬라임 타워", LegionTowers.T2_SLIME_TOWER);
        link(LegionTowers.T1_PENGUIN, LegionTowers.T2_PENGUIN.id(), "강화 땡컨타워", LegionTowers.T2_PENGUIN);
        link(LegionTowers.T1_PARROT_TOWER, LegionTowers.T2_PARROT_TOWER.id(), "앵무 타워", LegionTowers.T2_PARROT_TOWER);
        link(LegionTowers.T1_GOAT_TOWER, LegionTowers.T2_STRONG_GOAT_TOWER.id(), "짱쌘 염소 타워", LegionTowers.T2_STRONG_GOAT_TOWER);
        link(LegionTowers.T2_STRONG_GOAT_TOWER, LegionTowers.T3_EXTREME_GOAT_TOWER.id(), "개쌘 염소 타워", LegionTowers.T3_EXTREME_GOAT_TOWER);
//        link(LegionTowers.T1_BEE_TOWER, LegionTowers.T2_BEE_TOWER.id(), "벌떼 타워", LegionTowers.T2_BEE_TOWER);
//        link(LegionTowers.T2_BEE_TOWER, LegionTowers.T3_BEE_TOWER.id(), "여왕벌 타워", LegionTowers.T3_BEE_TOWER);

        JobRegistry.registerIfAbsent(new LegionTowerJob());
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
