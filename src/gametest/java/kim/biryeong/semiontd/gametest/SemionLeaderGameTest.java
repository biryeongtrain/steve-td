package kim.biryeong.semiontd.gametest;

import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.LeaderTargetResult;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionTeam;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.ui.SemionDialogService;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.GameTestHelper;

public final class SemionLeaderGameTest {
    @GameTest
    public void leaderTargetDomainAndTeamColoredLabelsAreRuntimeSafe(GameTestHelper context) {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), new GameArena(Map.of()));
        SemionTeam redTeam = game.teams().get(TeamId.RED);
        SemionTeam blueTeam = game.teams().get(TeamId.BLUE);
        redTeam.activate();
        blueTeam.activate();

        UUID redLeader = UUID.nameUUIDFromBytes("gametest-red-leader".getBytes());
        game.players().put(redLeader, new SemionPlayer(
                redLeader,
                "redLeader",
                TeamId.RED,
                1,
                new PlayerEconomy(EconomyConfig.defaultConfig())
        ));
        redTeam.setLeader(redLeader);

        if (game.setLeaderTarget(redLeader, TeamId.BLUE) != LeaderTargetResult.SUCCESS) {
            context.fail(Component.literal("Leader target selection should succeed for a living enemy team."));
            return;
        }
        if (redTeam.leaderTargeting().orElseThrow().targetTeamId().orElseThrow() != TeamId.BLUE) {
            context.fail(Component.literal("Leader target state should store BLUE."));
            return;
        }
        if (!"RED".equals(SemionDialogService.teamButtonLabel(TeamId.RED).getString())) {
            context.fail(Component.literal("Team dialog button label should render the team name text."));
            return;
        }
        if (!"PURPLE".equals(SemionDialogService.teamButtonLabel(TeamId.PURPLE).getString())) {
            context.fail(Component.literal("Purple team dialog button label should render the team name text."));
            return;
        }
        context.succeed();
    }
}
