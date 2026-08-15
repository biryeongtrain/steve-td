package kim.biryeong.semiontd.tower.futureagency;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class FutureAgencyTowerCatalogs {
    private FutureAgencyTowerCatalogs() {}

    public static void register() {
        registerStarter(FutureAgencyTowers.ESCAPEE, FutureAgencyLeaderTower::new);
        register(FutureAgencyTowers.REBUILDER, 2, FutureAgencyLeaderTower::new);
        register(FutureAgencyTowers.COMMANDER, 3, FutureAgencyLeaderTower::new);
        for (FutureAgencyRole role : FutureAgencyRole.values()) {
            for (int grade = 5; grade >= 1; grade--) {
                TowerType type = FutureAgencyTowers.agent(role, grade);
                if (grade == 5) registerStarter(type, FutureAgencyAgentTower::new);
                else register(type, 6 - grade, FutureAgencyAgentTower::new);
            }
        }

        link(FutureAgencyTowers.ESCAPEE, FutureAgencyLeaderTower.RECONSTRUCT,
                "미래기관 재건", FutureAgencyTowers.REBUILDER);
        link(FutureAgencyTowers.REBUILDER, FutureAgencyLeaderTower.SAVE_WORLD,
                "…이제 세계를 구한다.", FutureAgencyTowers.REBUILDER);
        link(FutureAgencyTowers.COMMANDER, FutureAgencyLeaderTower.SAVE_WORLD,
                "…이제 세계를 구한다.", FutureAgencyTowers.COMMANDER);
        for (TowerType leader : java.util.List.of(FutureAgencyTowers.REBUILDER, FutureAgencyTowers.COMMANDER)) {
            for (FutureAgencyPolicy policy : FutureAgencyPolicy.values()) {
                link(leader, policy.upgradeId(), policy.displayName(), leader);
            }
        }
        link(FutureAgencyTowers.REBUILDER, FutureAgencyLeaderTower.PROMOTE_COMMANDER,
                "기관 최고 지휘자", FutureAgencyTowers.COMMANDER);
        for (FutureAgencyRole role : FutureAgencyRole.values()) {
            for (int grade = 5; grade > 1; grade--) {
                TowerType from = FutureAgencyTowers.agent(role, grade);
                TowerType to = FutureAgencyTowers.agent(role, grade - 1);
                link(from, to.id(), (grade - 1) + "급 요원 강화", to);
            }
        }
    }

    private static void registerStarter(TowerType type, ProductionTowerCatalog.TowerFactory factory) {
        ProductionTowerCatalog.registerStarter(TowerBalanceRuntime.resolve(type), factory);
    }

    private static void register(TowerType type, int tier, ProductionTowerCatalog.TowerFactory factory) {
        ProductionTowerCatalog.register(TowerBalanceRuntime.resolve(type), factory, tier);
    }

    private static void link(TowerType fromDefaults, String id, String name, TowerType toDefaults) {
        TowerType from = ProductionTowerCatalog.find(fromDefaults.id()).orElseThrow().type();
        TowerType to = ProductionTowerCatalog.find(toDefaults.id()).orElseThrow().type();
        ProductionTowerCatalog.linkUpgrade(from, id, name, to,
                TowerBalanceRuntime.upgradeCost(fromDefaults, id));
    }
}
