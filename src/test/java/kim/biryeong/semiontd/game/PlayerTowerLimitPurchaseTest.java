package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.map.GameArena;
import org.junit.jupiter.api.Test;

final class PlayerTowerLimitPurchaseTest {
    @Test
    void buyingTowerLimitUsesConfiguredCostAndIncreasesPlayerLimit() {
        EconomyConfig config = economyWithTowerLimitPurchases(100, 25, 30, 10, 2, 3);
        SemionGame game = new SemionGame(config, WaveConfig.defaultConfig(), new GameArena(Map.of()));
        UUID playerId = UUID.nameUUIDFromBytes("tower-limit-buyer".getBytes());
        SemionPlayer player = new SemionPlayer(playerId, "buyer", TeamId.RED, 1, new PlayerEconomy(config));
        game.players().put(playerId, player);

        assertEquals(5, game.towerLimitForPlayer(playerId));
        assertEquals(100, game.nextTowerLimitPurchaseDiamondCost(playerId));
        assertEquals(30, game.nextTowerLimitPurchaseEmeraldCost(playerId));

        assertTrue(game.purchaseTowerLimit(playerId));

        assertEquals(100, player.economy().diamond());
        assertEquals(20, player.economy().emerald());
        assertEquals(1, player.economy().towerLimitPurchaseCount());
        assertEquals(7, game.towerLimitForPlayer(playerId));
        assertEquals(125, game.nextTowerLimitPurchaseDiamondCost(playerId));
        assertEquals(40, game.nextTowerLimitPurchaseEmeraldCost(playerId));
    }

    @Test
    void towerLimitPurchaseFailsWithoutBothCurrenciesAndAtConfiguredCap() {
        EconomyConfig config = economyWithTowerLimitPurchases(100, 50, 20, 5, 1, 1);
        PlayerEconomy economy = new PlayerEconomy(config);

        assertTrue(economy.purchaseTowerLimit(config.towerLimit()));
        assertFalse(economy.purchaseTowerLimit(config.towerLimit()));
        assertEquals(-1, config.towerLimit().purchaseDiamondCost(economy.towerLimitPurchaseCount()));
        assertEquals(-1, config.towerLimit().purchaseEmeraldCost(economy.towerLimitPurchaseCount()));

        EconomyConfig expensiveDiamondConfig = economyWithTowerLimitPurchases(500, 0, 10, 0, 1, 3);
        PlayerEconomy poorDiamondEconomy = new PlayerEconomy(expensiveDiamondConfig);
        assertFalse(poorDiamondEconomy.purchaseTowerLimit(expensiveDiamondConfig.towerLimit()));
        assertEquals(0, poorDiamondEconomy.towerLimitPurchaseCount());

        EconomyConfig expensiveEmeraldConfig = economyWithTowerLimitPurchases(100, 0, 500, 0, 1, 3);
        PlayerEconomy poorEmeraldEconomy = new PlayerEconomy(expensiveEmeraldConfig);
        assertFalse(poorEmeraldEconomy.purchaseTowerLimit(expensiveEmeraldConfig.towerLimit()));
        assertEquals(0, poorEmeraldEconomy.towerLimitPurchaseCount());
    }

    private static EconomyConfig economyWithTowerLimitPurchases(
            long initialDiamondCost,
            long diamondCostIncrease,
            long initialEmeraldCost,
            long emeraldCostIncrease,
            int increaseAmount,
            int maxPurchaseCount
    ) {
        return new EconomyConfig(
                200,
                50,
                0,
                EconomyConfig.GasCapConfig.defaultConfig(),
                EconomyConfig.GasProductionConfig.defaultConfig(),
                new EconomyConfig.TowerLimitConfig(
                        5,
                        5,
                        5,
                        3,
                        11,
                        increaseAmount,
                        maxPurchaseCount,
                        initialDiamondCost,
                        diamondCostIncrease,
                        initialEmeraldCost,
                        emeraldCostIncrease
                )
        );
    }
}
