package kim.biryeong.semiontd.tower.plant;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.PlantTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class PlantTowerCatalogs {
    private PlantTowerCatalogs() {
    }

    public static void register() {
        // 테라포밍 타워 - 지형 확보용. 티어가 오를수록 반경이 넓어집니다.
        registerTower(PlantTowers.T1_OAK_SEED_TOWER, PlantTerraformTower::new, 1);
        registerTower(PlantTowers.T2_OAK_SEED_TOWER, PlantTerraformTower::new, 2);
        registerTower(PlantTowers.T3_OAK_SEED_TOWER, PlantTerraformTower::new, 3);
        registerTower(PlantTowers.T1_MUSHROOM_SPORE_TOWER, PlantTerraformTower::new, 1);
        registerTower(PlantTowers.T2_MUSHROOM_SPORE_TOWER, PlantTerraformTower::new, 2);
        registerTower(PlantTowers.T3_MUSHROOM_SPORE_TOWER, PlantTerraformTower::new, 3);
        registerTower(PlantTowers.T1_DRY_GRASS_SEED_TOWER, PlantTerraformTower::new, 1);
        registerTower(PlantTowers.T2_DRY_GRASS_SEED_TOWER, PlantTerraformTower::new, 2);
        registerTower(PlantTowers.T3_DRY_GRASS_SEED_TOWER, PlantTerraformTower::new, 3);
        registerTower(PlantTowers.T1_SPRUCE_SEED_TOWER, PlantTerraformTower::new, 1);
        registerTower(PlantTowers.T2_SPRUCE_SEED_TOWER, PlantTerraformTower::new, 2);
        registerTower(PlantTowers.T3_SPRUCE_SEED_TOWER, PlantTerraformTower::new, 3);

        // 전투 타워 - 자기 계열 지형 위에만 설치할 수 있습니다.
        registerTower(PlantTowers.T1_MEADOW_TOWER, PlantCombatTower::new, 1);
        registerTower(PlantTowers.T2_MEADOW_TOWER, PlantCombatTower::new, 2);
        registerTower(PlantTowers.T3_MEADOW_TOWER, PlantCombatTower::new, 3);
        registerTower(PlantTowers.T1_MEADOW_NOVA_TOWER, PlantCombatTower::new, 1);
        registerTower(PlantTowers.T2_MEADOW_NOVA_TOWER, PlantCombatTower::new, 2);
        registerTower(PlantTowers.T3_MEADOW_NOVA_TOWER, PlantCombatTower::new, 3);
        registerTower(PlantTowers.T1_MYCELIUM_TOWER, PlantMineTower::new, 1);
        registerTower(PlantTowers.T2_MYCELIUM_TOWER, PlantMineTower::new, 2);
        registerTower(PlantTowers.T3_MYCELIUM_TOWER, PlantMineTower::new, 3);
        registerTower(PlantTowers.T1_DESERT_TOWER, PlantCombatTower::new, 1);
        registerTower(PlantTowers.T2_DESERT_TOWER, PlantCombatTower::new, 2);
        registerTower(PlantTowers.T3_DESERT_TOWER, PlantCombatTower::new, 3);
        registerTower(PlantTowers.T1_PODZOL_TOWER, PlantCombatTower::new, 1);
        registerTower(PlantTowers.T2_PODZOL_TOWER, PlantCombatTower::new, 2);
        registerTower(PlantTowers.T3_PODZOL_LILAC_TOWER, PlantCombatTower::new, 3);
        registerTower(PlantTowers.T3_PODZOL_ROSE_TOWER, PlantCombatTower::new, 3);
        registerTower(PlantTowers.T3_PODZOL_PITCHER_TOWER, PlantCombatTower::new, 3);

        link(PlantTowers.T1_OAK_SEED_TOWER, PlantTowers.T2_OAK_SEED_TOWER);
        link(PlantTowers.T2_OAK_SEED_TOWER, PlantTowers.T3_OAK_SEED_TOWER);
        link(PlantTowers.T1_MUSHROOM_SPORE_TOWER, PlantTowers.T2_MUSHROOM_SPORE_TOWER);
        link(PlantTowers.T2_MUSHROOM_SPORE_TOWER, PlantTowers.T3_MUSHROOM_SPORE_TOWER);
        link(PlantTowers.T1_DRY_GRASS_SEED_TOWER, PlantTowers.T2_DRY_GRASS_SEED_TOWER);
        link(PlantTowers.T2_DRY_GRASS_SEED_TOWER, PlantTowers.T3_DRY_GRASS_SEED_TOWER);
        link(PlantTowers.T1_SPRUCE_SEED_TOWER, PlantTowers.T2_SPRUCE_SEED_TOWER);
        link(PlantTowers.T2_SPRUCE_SEED_TOWER, PlantTowers.T3_SPRUCE_SEED_TOWER);

        link(PlantTowers.T1_MEADOW_TOWER, PlantTowers.T2_MEADOW_TOWER);
        link(PlantTowers.T2_MEADOW_TOWER, PlantTowers.T3_MEADOW_TOWER);
        link(PlantTowers.T1_MEADOW_NOVA_TOWER, PlantTowers.T2_MEADOW_NOVA_TOWER);
        link(PlantTowers.T2_MEADOW_NOVA_TOWER, PlantTowers.T3_MEADOW_NOVA_TOWER);
        link(PlantTowers.T1_MYCELIUM_TOWER, PlantTowers.T2_MYCELIUM_TOWER);
        link(PlantTowers.T2_MYCELIUM_TOWER, PlantTowers.T3_MYCELIUM_TOWER);
        link(PlantTowers.T1_DESERT_TOWER, PlantTowers.T2_DESERT_TOWER);
        link(PlantTowers.T2_DESERT_TOWER, PlantTowers.T3_DESERT_TOWER);

        // 회백토만 T2 에서 세 갈래로 갈라집니다.
        link(PlantTowers.T1_PODZOL_TOWER, PlantTowers.T2_PODZOL_TOWER);
        link(PlantTowers.T2_PODZOL_TOWER, PlantTowers.T3_PODZOL_LILAC_TOWER);
        link(PlantTowers.T2_PODZOL_TOWER, PlantTowers.T3_PODZOL_ROSE_TOWER);
        link(PlantTowers.T2_PODZOL_TOWER, PlantTowers.T3_PODZOL_PITCHER_TOWER);

        JobRegistry.registerIfAbsent(new PlantTowerJob());
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

    /** Upgrade id is the target tower id, so a branch just links the same source twice. */
    private static void link(TowerType from, TowerType to) {
        if (ProductionTowerCatalog.upgrade(from, to.id()).isPresent()) {
            return;
        }
        TowerType targetType = ProductionTowerCatalog.find(to.id())
                .map(ProductionTowerCatalog.CatalogEntry::type)
                .orElse(to);
        ProductionTowerCatalog.linkUpgrade(
                from,
                to.id(),
                to.displayName(),
                targetType,
                TowerBalanceRuntime.upgradeCost(from, to.id())
        );
    }
}
