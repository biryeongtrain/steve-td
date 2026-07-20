package kim.biryeong.semiontd.tower.ender;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.EnderTowerJob;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class EnderTowerCatalogs {
    private EnderTowerCatalogs() {
    }

    public static void register() {
        registerTower(EnderTowers.BASE_ENDER_TOWER, 1);
        registerTower(EnderTowers.T1_ENDERMITE_TOWER, 1);
        registerTower(EnderTowers.T2_ENDERMAN_TOWER, 2);
        registerTower(EnderTowers.T3_END_CRYSTAL_TOWER, 3);
        registerTower(EnderTowers.T1_SHULKER_TOWER, 1);
        registerTower(EnderTowers.T2_SHULKER_TOWER, 2);
        registerTower(EnderTowers.T3_SHULKER_TOWER, 3);

        link(EnderTowers.T1_ENDERMITE_TOWER, EnderTowers.T2_ENDERMAN_TOWER, "엔더맨");
        link(EnderTowers.T2_ENDERMAN_TOWER, EnderTowers.T3_END_CRYSTAL_TOWER, "엔드 수정");
        link(EnderTowers.T1_SHULKER_TOWER, EnderTowers.T2_SHULKER_TOWER, "견고한 셜커");
        link(EnderTowers.T2_SHULKER_TOWER, EnderTowers.T3_SHULKER_TOWER, "완강한 셜커");

        JobRegistry.registerIfAbsent(new EnderTowerJob());
    }

    private static void registerTower(TowerType type, int tier) {
        if (ProductionTowerCatalog.find(type.id()).isPresent()) {
            return;
        }
        TowerType resolvedType = TowerBalanceRuntime.resolve(type);
        if (tier == 1) {
            ProductionTowerCatalog.registerStarter(resolvedType, EnderTower::new);
            return;
        }
        ProductionTowerCatalog.register(resolvedType, EnderTower::new, tier);
    }

    private static void link(TowerType from, TowerType to, String displayName) {
        if (ProductionTowerCatalog.upgrade(from, to.id()).isPresent()) {
            return;
        }
        TowerType targetType = ProductionTowerCatalog.find(to.id())
                .map(ProductionTowerCatalog.CatalogEntry::type)
                .orElse(to);
        ProductionTowerCatalog.linkUpgrade(
                from,
                to.id(),
                displayName,
                targetType,
                TowerBalanceRuntime.upgradeCost(from, to.id())
        );
    }
}
