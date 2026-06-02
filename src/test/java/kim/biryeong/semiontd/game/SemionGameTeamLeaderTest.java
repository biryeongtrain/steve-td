package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
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

    private static SemionGame newGame() {
        return new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), new GameArena(Map.of()));
    }

    private static SemionPlayer player(UUID uuid, TeamId teamId, int laneId) {
        return new SemionPlayer(uuid, uuid.toString(), teamId, laneId, new PlayerEconomy(EconomyConfig.defaultConfig()));
    }
}
