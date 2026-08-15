package kim.biryeong.semiontd.tower.queen;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;

public final class QueenTowerCatalogs {
    private QueenTowerCatalogs() {}

    public static void register() {
        ProductionTowerCatalog.registerStarter(TowerBalanceRuntime.resolve(QueenTowers.QUEEN), QueenTower::new);
        ProductionTowerCatalog.registerStarter(TowerBalanceRuntime.resolve(QueenTowers.RANDOM_CARD_SOLDIER), QueenCardTower::new);
    }
}
