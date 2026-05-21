package kim.biryeong.semiontd.tower;

import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.UndeadTowerJob;
import kim.biryeong.semiontd.job.VillagerTowerJob;
import kim.biryeong.semiontd.tower.undead.UndeadTowerCatalogs;
import kim.biryeong.semiontd.tower.villager.VillagerTowerCatalogs;

public final class ProductionTowerCatalogs {
    private ProductionTowerCatalogs() {
    }

    public static void reloadBuiltIns(TowerBalanceConfig config) {
        TowerBalanceRuntime.apply(config);
        ProductionTowerCatalog.clear();
        JobRegistry.registerIfAbsent(new VillagerTowerJob());
        JobRegistry.registerIfAbsent(new UndeadTowerJob());
        VillagerTowerCatalogs.register();
        UndeadTowerCatalogs.register();
    }
}
