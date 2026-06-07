package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.CurrencyType;
import kim.biryeong.semiontd.config.EconomyConfig;
import org.junit.jupiter.api.Test;

final class EmeraldIncomeBoostTest {
    @Test
    void defaultBoostActivatesFromRoundTwentyFive() {
        EconomyConfig.EmeraldIncomeBoostConfig config = EconomyConfig.defaultConfig().emeraldIncomeBoost();

        assertTrue(config.enabled());
        assertEquals(25, config.startRound());
        assertFalse(config.activeForRound(24));
        assertTrue(config.activeForRound(25));
    }

    @Test
    void tickEmeraldDoublesPerSecondProductionFromConfiguredRound() {
        EconomyConfig config = economyWithBoost(new EconomyConfig.EmeraldIncomeBoostConfig(true, 3));
        EconomyService service = new EconomyService(config);
        SemionTeam team = new SemionTeam(TeamId.BLUE);
        team.activate();
        Map<TeamId, SemionTeam> teams = Map.of(TeamId.BLUE, team);
        SemionPlayer player = player(config, 5);

        service.tickEmerald(List.of(player), teams, 2);
        assertEquals(5, player.economy().emerald());

        service.tickEmerald(List.of(player), teams, 3);
        assertEquals(15, player.economy().emerald());
    }

    @Test
    void disabledBoostKeepsNormalPerSecondProductionAfterThreshold() {
        EconomyConfig config = economyWithBoost(new EconomyConfig.EmeraldIncomeBoostConfig(false, 3));
        EconomyService service = new EconomyService(config);
        SemionTeam team = new SemionTeam(TeamId.BLUE);
        team.activate();
        SemionPlayer player = player(config, 5);

        service.tickEmerald(List.of(player), Map.of(TeamId.BLUE, team), 3);

        assertEquals(5, player.economy().emerald());
    }

    private static EconomyConfig economyWithBoost(EconomyConfig.EmeraldIncomeBoostConfig boostConfig) {
        return new EconomyConfig(
                200,
                50,
                0,
                EconomyConfig.GasCapConfig.defaultConfig(),
                new EconomyConfig.GasProductionConfig(5, 20, 50, 25, 1, CurrencyType.DIAMOND),
                EconomyConfig.TowerLimitConfig.defaultConfig(),
                EconomyConfig.KillRewardConfig.defaultConfig(),
                EconomyConfig.TeamTransferConfig.defaultConfig(),
                boostConfig
        );
    }

    private static SemionPlayer player(EconomyConfig config, long emeraldPerSec) {
        PlayerEconomy economy = new PlayerEconomy(config);
        economy.overrideStartingValues(0, 0, 0, emeraldPerSec);
        return new SemionPlayer(UUID.nameUUIDFromBytes("emerald-boost".getBytes()), "player", TeamId.BLUE, 1, economy);
    }
}
