package kim.biryeong.semiontd.tower.atlantis;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.AtlantisTowerJob;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class AtlantisTowerCatalogs {
    private AtlantisTowerCatalogs() {
    }

    public static void register() {
        register(AtlantisTowers.TURTLE_T1, 1);
        register(AtlantisTowers.TURTLE_T2, 2);
        register(AtlantisTowers.TURTLE_T3, 3);
        register(AtlantisTowers.DOLPHIN_T1, 1);
        register(AtlantisTowers.DOLPHIN_T2, 2);
        register(AtlantisTowers.DOLPHIN_T3, 3);
        register(AtlantisTowers.AXOLOTL_T1, 1);
        register(AtlantisTowers.AXOLOTL_T2, 2);
        register(AtlantisTowers.AXOLOTL_T3, 3);
        register(AtlantisTowers.CONDUIT_T1, 1);
        register(AtlantisTowers.CONDUIT_T2, 2);
        register(AtlantisTowers.CONDUIT_T3, 3);

        link(AtlantisTowers.TURTLE_T1, AtlantisTowers.TURTLE_T2);
        link(AtlantisTowers.TURTLE_T2, AtlantisTowers.TURTLE_T3);
        link(AtlantisTowers.DOLPHIN_T1, AtlantisTowers.DOLPHIN_T2);
        link(AtlantisTowers.DOLPHIN_T2, AtlantisTowers.DOLPHIN_T3);
        link(AtlantisTowers.AXOLOTL_T1, AtlantisTowers.AXOLOTL_T2);
        link(AtlantisTowers.AXOLOTL_T2, AtlantisTowers.AXOLOTL_T3);
        link(AtlantisTowers.CONDUIT_T1, AtlantisTowers.CONDUIT_T2);
        link(AtlantisTowers.CONDUIT_T2, AtlantisTowers.CONDUIT_T3);

        if (JobRegistry.find(AtlantisTowerJob.ID).isEmpty()) {
            JobRegistry.registerIfAbsent(new AtlantisTowerJob());
        }
    }

    private static void register(TowerType type, int tier) {
        if (ProductionTowerCatalog.find(type.id()).isPresent()) {
            return;
        }
        TowerType resolved = TowerBalanceRuntime.resolve(type);
        if (tier == 1) {
            ProductionTowerCatalog.registerStarter(resolved, AtlantisTower::new);
        } else {
            ProductionTowerCatalog.register(resolved, AtlantisTower::new, tier);
        }
    }

    private static void link(TowerType from, TowerType to) {
        if (ProductionTowerCatalog.upgrade(from, to.id()).isPresent()) {
            return;
        }
        TowerType target = ProductionTowerCatalog.find(to.id())
                .map(ProductionTowerCatalog.CatalogEntry::type)
                .orElse(to);
        ProductionTowerCatalog.linkUpgrade(
                from,
                to.id(),
                to.displayName(),
                target,
                TowerBalanceRuntime.upgradeCost(from, to.id())
        );
    }
}
