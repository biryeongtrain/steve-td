package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.LeaderTargetingConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.map.GameArena;
import org.junit.jupiter.api.Test;

final class SemionGameTeamLeaderTest {
    @Test
    void lowestLaneParticipantBecomesTeamLeader() {
        SemionGame game = newGame();
        UUID laneTwo = UUID.nameUUIDFromBytes("red-lane-2".getBytes());
        UUID laneOne = UUID.nameUUIDFromBytes("red-lane-1".getBytes());
        game.teams().get(TeamId.RED).activate();
        game.players().put(laneTwo, player(laneTwo, TeamId.RED, 2));
        game.players().put(laneOne, player(laneOne, TeamId.RED, 1));

        game.assignTeamLeadersFromParticipants(List.of(
                new AssignedParticipant(laneTwo, "red2", TeamId.RED, 2),
                new AssignedParticipant(laneOne, "red1", TeamId.RED, 1)
        ));

        assertEquals(laneOne, game.teams().get(TeamId.RED).leaderPlayerId().orElseThrow());
    }

    @Test
    void assignsOneLeaderForEachActiveTeam() {
        SemionGame game = newGame();
        UUID red = UUID.nameUUIDFromBytes("red-leader".getBytes());
        UUID blue = UUID.nameUUIDFromBytes("blue-leader".getBytes());
        game.teams().get(TeamId.RED).activate();
        game.teams().get(TeamId.BLUE).activate();
        game.players().put(red, player(red, TeamId.RED, 1));
        game.players().put(blue, player(blue, TeamId.BLUE, 1));

        game.assignTeamLeadersFromParticipants(List.of(
                new AssignedParticipant(red, "red", TeamId.RED, 1),
                new AssignedParticipant(blue, "blue", TeamId.BLUE, 1)
        ));

        assertEquals(red, game.teams().get(TeamId.RED).leaderPlayerId().orElseThrow());
        assertEquals(blue, game.teams().get(TeamId.BLUE).leaderPlayerId().orElseThrow());
        assertTrue(game.teams().values().stream().filter(team -> team.leaderTargeting().isPresent()).count() == 2);
    }

    @Test
    void spectatorsAreNotAssignedAsTeamLeaders() {
        SemionGame game = newGame();
        UUID active = UUID.nameUUIDFromBytes("active-leader".getBytes());
        UUID spectator = UUID.nameUUIDFromBytes("spectator".getBytes());
        game.teams().get(TeamId.GREEN).activate();
        game.players().put(active, player(active, TeamId.GREEN, 2));

        game.assignTeamLeadersFromParticipants(List.of(
                new AssignedParticipant(spectator, "spectator", TeamId.GREEN, 1),
                new AssignedParticipant(active, "active", TeamId.GREEN, 2)
        ));

        assertEquals(active, game.teams().get(TeamId.GREEN).leaderPlayerId().orElseThrow());
    }

    @Test
    void teamLeaderAnnouncementUsesWhiteTextAndTeamColoredNickname() {
        assertEquals(
                "<white>RED 팀장: </white><red>frosti</red>",
                SemionGame.teamLeaderAnnouncementMarkup(TeamId.RED, "frosti")
        );
        assertEquals(
                "<white>PURPLE 팀장: </white><light_purple>purpleLeader</light_purple>",
                SemionGame.teamLeaderAnnouncementMarkup(TeamId.PURPLE, "purpleLeader")
        );
    }

    @Test
    void onlyTwoLeaderTargetsMayPointAtTheSameTeam() {
        SemionGame game = newGame();
        UUID red = addLeader(game, TeamId.RED);
        UUID blue = addLeader(game, TeamId.BLUE);
        UUID green = addLeader(game, TeamId.GREEN);
        addLeader(game, TeamId.YELLOW);

        assertEquals(LeaderTargetResult.SUCCESS, game.setLeaderTarget(red, TeamId.YELLOW));
        assertEquals(LeaderTargetResult.SUCCESS, game.setLeaderTarget(blue, TeamId.YELLOW));
        assertEquals(LeaderTargetResult.TARGET_TEAM_ALREADY_DESIGNATED, game.setLeaderTarget(green, TeamId.YELLOW));
        assertEquals("해당 라인을 지정할 수 없습니다.", LeaderTargetResult.TARGET_TEAM_ALREADY_DESIGNATED.message());
    }

    @Test
    void forcedIncomeTargetExpiresAfterTwoRounds() {
        SemionGame game = newGame();
        UUID red = addLeader(game, TeamId.RED);
        addLeader(game, TeamId.BLUE);
        addLeader(game, TeamId.GREEN);

        assertEquals(LeaderTargetResult.SUCCESS, game.setLeaderTarget(red, TeamId.BLUE));
        assertEquals(Optional.of(TeamId.BLUE), game.targetTeamForSummon(TeamId.RED).map(SemionTeam::id));

        game.tickLeaderCooldowns();
        assertEquals(Optional.of(TeamId.BLUE), game.targetTeamForSummon(TeamId.RED).map(SemionTeam::id));

        game.tickLeaderCooldowns();
        assertTrue(game.teams().get(TeamId.RED).leaderTargeting().orElseThrow().targetTeamId().isEmpty());
    }

    @Test
    void leaderTargetCapsAndDurationUseConfig() {
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                LeaderTargetingConfig.defaultConfig().withMaxTargetingTeams(1).withActiveTargetRounds(3),
                new GameArena(Map.of())
        );
        UUID red = addLeader(game, TeamId.RED);
        UUID blue = addLeader(game, TeamId.BLUE);
        addLeader(game, TeamId.YELLOW);

        assertEquals(LeaderTargetResult.SUCCESS, game.setLeaderTarget(red, TeamId.YELLOW));
        assertEquals(LeaderTargetResult.TARGET_TEAM_ALREADY_DESIGNATED, game.setLeaderTarget(blue, TeamId.YELLOW));

        game.tickLeaderCooldowns();
        game.tickLeaderCooldowns();
        assertEquals(Optional.of(TeamId.YELLOW), game.targetTeamForSummon(TeamId.RED).map(SemionTeam::id));

        game.tickLeaderCooldowns();
        assertTrue(game.teams().get(TeamId.RED).leaderTargeting().orElseThrow().targetTeamId().isEmpty());
    }

    private static SemionGame newGame() {
        return new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), new GameArena(Map.of()));
    }

    private static UUID addLeader(SemionGame game, TeamId teamId) {
        UUID uuid = UUID.nameUUIDFromBytes((teamId.name().toLowerCase() + "-target-leader").getBytes());
        game.teams().get(teamId).activate();
        game.players().put(uuid, player(uuid, teamId, 1));
        game.teams().get(teamId).setLeader(uuid);
        return uuid;
    }

    private static SemionPlayer player(UUID uuid, TeamId teamId, int laneId) {
        return new SemionPlayer(uuid, uuid.toString(), teamId, laneId, new PlayerEconomy(EconomyConfig.defaultConfig()));
    }
}
