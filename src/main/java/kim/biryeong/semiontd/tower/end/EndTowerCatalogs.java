package kim.biryeong.semiontd.tower.end;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.EndTowerJob;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class EndTowerCatalogs {
    private EndTowerCatalogs() {
    }

    public static void register() {
        registerTower(EndTowers.BASE_END_TOWER, 1);
        registerTower(EndTowers.T1_ENDERMITE_TOWER, 1);
        registerTower(EndTowers.T2_ENDERMAN_TOWER, 2);
        registerTower(EndTowers.T3_END_CRYSTAL_TOWER, 3);
        registerTower(EndTowers.T1_SHULKER_TOWER, 1);
        registerTower(EndTowers.T2_SHULKER_TOWER, 2);
        registerTower(EndTowers.T3_SHULKER_TOWER, 3);

        link(EndTowers.T1_ENDERMITE_TOWER, EndTowers.T2_ENDERMAN_TOWER, "엔더맨");
        link(EndTowers.T2_ENDERMAN_TOWER, EndTowers.T3_END_CRYSTAL_TOWER, "엔드 수정");
        link(EndTowers.T1_SHULKER_TOWER, EndTowers.T2_SHULKER_TOWER, "견고한 셜커");
        link(EndTowers.T2_SHULKER_TOWER, EndTowers.T3_SHULKER_TOWER, "완강한 셜커");

        JobRegistry.registerIfAbsent(new EndTowerJob());
    }

    private static void registerTower(TowerType type, int tier) {
        if (ProductionTowerCatalog.find(type.id()).isPresent()) {
            return;
        }
        TowerType resolvedType = TowerBalanceRuntime.resolve(type);
        if (tier == 1) {
            ProductionTowerCatalog.registerStarter(resolvedType, EndTower::new);
            return;
        }
        ProductionTowerCatalog.register(resolvedType, EndTower::new, tier);
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
