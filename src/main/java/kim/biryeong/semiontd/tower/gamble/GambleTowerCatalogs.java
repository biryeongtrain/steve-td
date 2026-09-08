package kim.biryeong.semiontd.tower.gamble;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

public final class GambleTowerCatalogs {
    private GambleTowerCatalogs() {
    }

    public static void register() {
        registerStarter(GambleTowers.DICE_T1, GambleSupportTower::new);
        register(GambleTowers.DICE_T2, 2, GambleSupportTower::new);
        register(GambleTowers.DICE_T3, 3, GambleSupportTower::new);
        registerStarter(GambleTowers.GAMBLER, GamblerTower::new);
        register(GambleTowers.KING, 4, GamblerTower::new);
        register(GambleTowers.DARK_KING, 4, GamblerTower::new);
        registerStarter(GambleTowers.SPECTATOR_T1, GambleSupportTower::new);
        register(GambleTowers.SPECTATOR_T2, 2, GambleSupportTower::new);
        register(GambleTowers.SPECTATOR_T3, 3, GambleSupportTower::new);

        registerStarter(GambleTowers.POKER_TABLE, PokerTableTower::new);
        link(GambleTowers.POKER_TABLE, GamblePoker.UPGRADE_ID, "포커 베팅", GambleTowers.POKER_TABLE);

        link(GambleTowers.DICE_T1, GambleTowers.DICE_T2.id(), "주사위 지원 강화 II", GambleTowers.DICE_T2);
        link(GambleTowers.DICE_T2, GambleTowers.DICE_T3.id(), "주사위 지원 강화 III", GambleTowers.DICE_T3);
        link(GambleTowers.SPECTATOR_T1, GambleTowers.SPECTATOR_T2.id(), "슬롯머신 강화 II", GambleTowers.SPECTATOR_T2);
        link(GambleTowers.SPECTATOR_T2, GambleTowers.SPECTATOR_T3.id(), "슬롯머신 강화 III", GambleTowers.SPECTATOR_T3);
        for (TowerType gambler : java.util.List.of(
                GambleTowers.GAMBLER, GambleTowers.KING, GambleTowers.DARK_KING)) {
            for (GambleBet bet : GambleBet.values()) {
                link(gambler, bet.upgradeId(), bet.displayName(), gambler);
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
