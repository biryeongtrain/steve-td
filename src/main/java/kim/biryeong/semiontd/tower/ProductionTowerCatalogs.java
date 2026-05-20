package kim.biryeong.semiontd.tower;

import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.undead.UndeadTowerCatalogs;
import kim.biryeong.semiontd.tower.villager.VillagerTowerCatalogs;

public final class ProductionTowerCatalogs {
    private ProductionTowerCatalogs() {
    }

    public static void reloadBuiltIns(TowerBalanceConfig config) {
        TowerBalanceRuntime.apply(config);
        ProductionTowerCatalog.clear();
        VillagerTowerCatalogs.register();
        UndeadTowerCatalogs.register();
    }
}
