package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class LeaderTargetingStateTest {
    @Test
    void newStateHasLeaderButNoTargetAndNoCooldown() {
        UUID leaderId = UUID.nameUUIDFromBytes("leader-state-new".getBytes());
        LeaderTargetingState state = new LeaderTargetingState(leaderId);

        assertEquals(leaderId, state.leaderPlayerId());
        assertEquals(Optional.empty(), state.targetTeamId());
        assertEquals(0, state.cooldownRemainingRounds());
        assertTrue(state.canUse());
    }

    @Test
    void useStoresTargetRoundAndCooldown() {
        UUID leaderId = UUID.nameUUIDFromBytes("leader-state-use".getBytes());
        LeaderTargetingState state = new LeaderTargetingState(leaderId);

        state.use(TeamId.BLUE, 5, 3);

        assertEquals(Optional.of(TeamId.BLUE), state.targetTeamId());
        assertEquals(5, state.lastUsedRound());
        assertEquals(3, state.cooldownRemainingRounds());
        assertEquals(2, state.activeTargetRemainingRounds());
        assertFalse(state.canUse());
    }

    @Test
    void targetExpiresAfterTwoRoundTicks() {
        LeaderTargetingState state = new LeaderTargetingState(UUID.nameUUIDFromBytes("leader-state-duration".getBytes()));
        state.use(TeamId.BLUE, 1, 3);

        state.tickRoundCooldown();
        assertEquals(Optional.of(TeamId.BLUE), state.targetTeamId());
        assertEquals(1, state.activeTargetRemainingRounds());

        state.tickRoundCooldown();
        assertEquals(Optional.empty(), state.targetTeamId());
        assertEquals(0, state.activeTargetRemainingRounds());
    }

    @Test
    void cooldownReachesReusableAfterThreeRoundTicks() {
        LeaderTargetingState state = new LeaderTargetingState(UUID.nameUUIDFromBytes("leader-state-cooldown".getBytes()));
        state.use(TeamId.GREEN, 2, 3);

        state.tickRoundCooldown();
        state.tickRoundCooldown();
        state.tickRoundCooldown();

        assertEquals(0, state.cooldownRemainingRounds());
        assertTrue(state.canUse());
        assertEquals(Optional.empty(), state.targetTeamId());
    }

    @Test
    void negativeCooldownIsClampedToZero() {
        LeaderTargetingState state = new LeaderTargetingState(UUID.nameUUIDFromBytes("leader-state-negative".getBytes()));

        state.use(TeamId.YELLOW, 4, -1);

        assertEquals(0, state.cooldownRemainingRounds());
        assertTrue(state.canUse());
    }

    @Test
    void clearTargetKeepsCooldown() {
        LeaderTargetingState state = new LeaderTargetingState(UUID.nameUUIDFromBytes("leader-state-clear".getBytes()));
        state.use(TeamId.PURPLE, 7, 3);

        state.clearTarget();

        assertEquals(Optional.empty(), state.targetTeamId());
        assertEquals(3, state.cooldownRemainingRounds());
        assertEquals(0, state.activeTargetRemainingRounds());
    }
}
