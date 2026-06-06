package kim.biryeong.semiontd.gametest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.LeaderTargetResult;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionTeam;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.tower.resonance.ResonanceService;
import kim.biryeong.semiontd.tower.resonance.ResonanceTower;
import kim.biryeong.semiontd.tower.resonance.ResonanceTowers;
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
    public void towerRuntimeDetailsAreAvailableInRuntimeDialogPath(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        UUID owner = UUID.nameUUIDFromBytes("gametest-runtime-detail-owner".getBytes());
        ResonanceTower focus = resonanceTower(ResonanceTowers.FOCUS_CORE, owner, new GridPosition(0, 0, 0));
        ResonanceService.refresh(List.of(
                focus,
                resonanceTower(ResonanceTowers.WAVE_CRYSTAL, owner, new GridPosition(1, 0, 0)),
                resonanceTower(ResonanceTowers.FROST_CRYSTAL, owner, new GridPosition(-1, 0, 0)),
                resonanceTower(ResonanceTowers.AMPLIFY_CRYSTAL, owner, new GridPosition(0, 0, 1)),
                resonanceTower(ResonanceTowers.WAVE_PRISM, owner, new GridPosition(1, 0, -1)),
                resonanceTower(ResonanceTowers.FROST_PRISM, owner, new GridPosition(-1, 0, -1)),
                resonanceTower(ResonanceTowers.AMPLIFY_PRISM, owner, new GridPosition(0, 0, -1))
        ));
        List<String> details = SemionDialogService.towerRuntimeDetailLines(focus);
        if (details.stream().noneMatch(line -> line.contains("무블룸 공명 Lv 3"))) {
            context.fail(Component.literal("Tower detail dialog runtime lines should include the earned moobloom resonance level."));
            return;
        }
        if (details.stream().noneMatch(line -> line.contains("링크 6"))) {
            context.fail(Component.literal("Tower detail dialog runtime lines should include moobloom resonance link count."));
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

    private static ResonanceTower resonanceTower(kim.biryeong.semiontd.tower.TowerType type, UUID owner, GridPosition position) {
        return new ResonanceTower(type, owner, TeamId.RED, 1, position, position);
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
