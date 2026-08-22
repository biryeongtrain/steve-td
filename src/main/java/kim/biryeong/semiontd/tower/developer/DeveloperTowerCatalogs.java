package kim.biryeong.semiontd.tower.developer;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.DeveloperTowerJob;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

/**
 * Catalog registration and upgrade graph for the 개발자 builder.
 *
 * <p>Both T2 towers reach both T3 towers, and the edge prices are set so the accumulated cost is
 * identical whichever way the player came: 290 for 정식판, 280 for LTS. That matters because the
 * T2 choice (하드 캐리 vs 앞라인 + 패치 오라) and the T3 choice (정식 패치 vs 핫픽스) are meant to be
 * independent decisions, and a cheaper path would silently couple them.
 */
public final class DeveloperTowerCatalogs {
    private DeveloperTowerCatalogs() {
    }

    public static void register() {
        register(DeveloperTowers.ALPHA, 1);
        register(DeveloperTowers.BETA, 2);
        register(DeveloperTowers.TEST_BUILD, 2);
        register(DeveloperTowers.RELEASE, 3);
        register(DeveloperTowers.LTS, 3);

        register(DeveloperTowers.WORKBENCH, 1);
        register(DeveloperTowers.DEPLOY_SERVER, 2);
        register(DeveloperTowers.OPS_CENTER, 3);

        register(DeveloperTowers.TESTER, 1);
        register(DeveloperTowers.DEBUGGER, 2);
        register(DeveloperTowers.DEVELOPER, 3);

        // 프로파일러 has no line of its own; it is bought directly and never upgrades.
        register(DeveloperTowers.PROFILER, 1);

        link(DeveloperTowers.ALPHA, DeveloperTowers.BETA);
        link(DeveloperTowers.ALPHA, DeveloperTowers.TEST_BUILD);
        link(DeveloperTowers.BETA, DeveloperTowers.RELEASE);
        link(DeveloperTowers.BETA, DeveloperTowers.LTS);
        link(DeveloperTowers.TEST_BUILD, DeveloperTowers.RELEASE);
        link(DeveloperTowers.TEST_BUILD, DeveloperTowers.LTS);

        link(DeveloperTowers.WORKBENCH, DeveloperTowers.DEPLOY_SERVER);
        link(DeveloperTowers.DEPLOY_SERVER, DeveloperTowers.OPS_CENTER);

        link(DeveloperTowers.TESTER, DeveloperTowers.DEBUGGER);
        link(DeveloperTowers.DEBUGGER, DeveloperTowers.DEVELOPER);

        if (JobRegistry.find(DeveloperTowerJob.ID).isEmpty()) {
            JobRegistry.registerIfAbsent(new DeveloperTowerJob());
        }
    }

    private static void register(TowerType type, int tier) {
        if (ProductionTowerCatalog.find(type.id()).isPresent()) {
            return;
        }
        TowerType resolved = TowerBalanceRuntime.resolve(type);
        if (tier == 1) {
            ProductionTowerCatalog.registerStarter(resolved, DeveloperTower::new);
        } else {
            ProductionTowerCatalog.register(resolved, DeveloperTower::new, tier);
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
