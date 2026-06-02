package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class SemionTeamLeaderTest {
    @Test
    void setLeaderCreatesTargetingState() {
        SemionTeam team = new SemionTeam(TeamId.RED);
        team.activate();
        UUID leaderId = UUID.nameUUIDFromBytes("red-leader".getBytes());

        team.setLeader(leaderId);

        assertEquals(Optional.of(leaderId), team.leaderPlayerId());
        assertTrue(team.leaderTargeting().isPresent());
        assertEquals(leaderId, team.leaderTargeting().orElseThrow().leaderPlayerId());
    }

    @Test
    void deactivateClearsLeaderAndTargetState() {
        SemionTeam team = new SemionTeam(TeamId.BLUE);
        team.activate();
        UUID leaderId = UUID.nameUUIDFromBytes("blue-leader".getBytes());
        team.setLeader(leaderId);
        team.leaderTargeting().orElseThrow().use(TeamId.RED, 3, 3);

        team.deactivate();

        assertEquals(Optional.empty(), team.leaderPlayerId());
        assertEquals(Optional.empty(), team.leaderTargeting());
    }

    @Test
    void eliminatedTeamCannotUseLeaderState() {
        SemionTeam team = new SemionTeam(TeamId.GREEN);
        team.activate();
        team.setLeader(UUID.nameUUIDFromBytes("green-leader".getBytes()));

        assertTrue(team.eliminate());

        assertEquals(Optional.empty(), team.leaderTargeting());
        assertFalse(team.hasLeader(UUID.nameUUIDFromBytes("green-leader".getBytes())));
    }
}
