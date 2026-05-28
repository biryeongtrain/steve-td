package kim.biryeong.semiontd.tower;

import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.tower.animal.AnimalTowerCatalogs;
import kim.biryeong.semiontd.tower.legion.LegionTowerCatalogs;
import kim.biryeong.semiontd.tower.undead.UndeadTowerCatalogs;
import kim.biryeong.semiontd.tower.villager.VillagerTowerCatalogs;
import kim.biryeong.semiontd.tower.warlock.WarlockTowerCatalogs;

public final class ProductionTowerCatalogs {
    private ProductionTowerCatalogs() {
    }

    public static void reloadBuiltIns(TowerBalanceConfig config) {
        TowerBalanceRuntime.apply(config);
        ProductionTowerCatalog.clear();
        JobRegistry.registerBuiltIns();
        VillagerTowerCatalogs.register();
        UndeadTowerCatalogs.register();
        AnimalTowerCatalogs.register();
        WarlockTowerCatalogs.register();
        LegionTowerCatalogs.register();
    }
}
