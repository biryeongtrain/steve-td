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

    @GameTest
    public void onlyTwoTeamsCanDesignateTheSameLeaderTarget(GameTestHelper context) {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), new GameArena(Map.of()));
        UUID redLeader = addLeader(game, TeamId.RED);
        UUID blueLeader = addLeader(game, TeamId.BLUE);
        UUID greenLeader = addLeader(game, TeamId.GREEN);
        addLeader(game, TeamId.YELLOW);

        if (game.setLeaderTarget(redLeader, TeamId.YELLOW) != LeaderTargetResult.SUCCESS) {
            context.fail(Component.literal("First leader target designation should succeed."));
            return;
        }
        if (game.setLeaderTarget(blueLeader, TeamId.YELLOW) != LeaderTargetResult.SUCCESS) {
            context.fail(Component.literal("Second leader target designation should succeed."));
            return;
        }
        LeaderTargetResult blocked = game.setLeaderTarget(greenLeader, TeamId.YELLOW);
        if (blocked != LeaderTargetResult.TARGET_TEAM_ALREADY_DESIGNATED) {
            context.fail(Component.literal("Third leader target designation should be blocked when two teams already target the line."));
            return;
        }
        if (!"해당 라인을 지정할 수 없습니다.".equals(blocked.message())) {
            context.fail(Component.literal("Blocked leader target message should match the requested red warning text."));
            return;
        }
        context.succeed();
    }

    private static UUID addLeader(SemionGame game, TeamId teamId) {
        UUID leaderId = UUID.nameUUIDFromBytes(("gametest-" + teamId.name().toLowerCase() + "-leader").getBytes());
        SemionTeam team = game.teams().get(teamId);
        team.activate();
        game.players().put(leaderId, new SemionPlayer(
                leaderId,
                teamId.name().toLowerCase() + "Leader",
                teamId,
                1,
                new PlayerEconomy(EconomyConfig.defaultConfig())
        ));
        team.setLeader(leaderId);
        return leaderId;
    }
}
