package kim.biryeong.semiontd.gametest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.CurrencyType;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.game.EconomyService;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionTeam;
import kim.biryeong.semiontd.game.TeamId;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class SemionEmeraldIncomeBoostGameTest {
    @GameTest
    public void emeraldIncomeBoostDoublesRuntimeProductionAtConfiguredRound(GameTestHelper context) {
        EconomyConfig config = new EconomyConfig(
                200,
                50,
                0,
                EconomyConfig.GasCapConfig.defaultConfig(),
                new EconomyConfig.GasProductionConfig(5, 20, 50, 25, 1, CurrencyType.DIAMOND),
                EconomyConfig.TowerLimitConfig.defaultConfig(),
                EconomyConfig.KillRewardConfig.defaultConfig(),
                EconomyConfig.TeamTransferConfig.defaultConfig(),
                new EconomyConfig.EmeraldIncomeBoostConfig(true, 3)
        );
        EconomyService service = new EconomyService(config);
        SemionTeam team = new SemionTeam(TeamId.BLUE);
        team.activate();
        PlayerEconomy economy = new PlayerEconomy(config);
        economy.overrideStartingValues(0, 0, 0, 5);
        SemionPlayer player = new SemionPlayer(
                UUID.nameUUIDFromBytes("gametest-emerald-boost".getBytes()),
                "player",
                TeamId.BLUE,
                1,
                economy
        );

        service.tickEmerald(List.of(player), Map.of(TeamId.BLUE, team), 3);

        if (player.economy().emerald() != 10) {
            throw new AssertionError("Expected emerald production to double to 10 at round 3, got " + player.economy().emerald());
        }
        context.succeed();
    }
}
