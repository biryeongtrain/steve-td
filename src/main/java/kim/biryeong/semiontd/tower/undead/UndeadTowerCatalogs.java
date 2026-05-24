package kim.biryeong.semiontd.tower.undead;

import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.UndeadTowerJob;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class UndeadTowerCatalogs {
    private UndeadTowerCatalogs() {
    }

    public static void register() {
        registerTower(UndeadTowers.T1_ZOMBIE_TOWER, UndeadZombieTower::new, 1);
        registerTower(UndeadTowers.T2_ZOMBIE_TOWER, UndeadHuskTower::new, 2);
        registerTower(UndeadTowers.T3_ZOMBIE_TOWER, UndeadDrownedTower::new, 3);

        registerTower(UndeadTowers.T1_SKELETON_TOWER, ProductionTower::new, 1);
        registerTower(UndeadTowers.T2_RANGED_SKELETON_TOWER, UndeadRangedSkeletonTower::new, 2);
        registerTower(UndeadTowers.T2_MELEE_TOWER, UndeadMeleeSkeletonTower::new, 2);
        registerTower(UndeadTowers.T3_RANGED_SKELETON_TOWER, UndeadRangedSkeletonTower::new, 3);
        registerTower(UndeadTowers.T3_MELEE_TOWER, UndeadMeleeSkeletonTower::new, 3);
        registerTower(UndeadTowers.T1_UNDEAD_ANIMAL_TOWER, UndeadAnimalTower::new, 1);
        registerTower(UndeadTowers.T2_UNDEAD_ANIMAL_TOWER, UndeadAnimalTower::new, 2);

        link(UndeadTowers.T1_ZOMBIE_TOWER, "t2_zombie_tower", "허스크 타워", UndeadTowers.T2_ZOMBIE_TOWER);
        link(UndeadTowers.T2_ZOMBIE_TOWER, "t3_zombie_tower", "드라운드 타워", UndeadTowers.T3_ZOMBIE_TOWER);

        link(UndeadTowers.T1_SKELETON_TOWER, "t2_ranged_skeleton_tower", "보그드 타워", UndeadTowers.T2_RANGED_SKELETON_TOWER);
        link(UndeadTowers.T1_SKELETON_TOWER, "t2_melee_tower", "위더 스켈레톤 타워", UndeadTowers.T2_MELEE_TOWER);
        link(UndeadTowers.T2_RANGED_SKELETON_TOWER, "t3_ranged_skeleton_tower", "스트레이 타워", UndeadTowers.T3_RANGED_SKELETON_TOWER);
        link(UndeadTowers.T2_MELEE_TOWER, "t3_melee_tower", "강화 위더 스켈레톤 타워", UndeadTowers.T3_MELEE_TOWER);
        link(UndeadTowers.T1_UNDEAD_ANIMAL_TOWER, "t2_undead_animal_tower", "스켈 말 타워", UndeadTowers.T2_UNDEAD_ANIMAL_TOWER);

        if (JobRegistry.find(UndeadTowerJob.ID).isEmpty()) {
            JobRegistry.registerIfAbsent(new UndeadTowerJob());
        }
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
