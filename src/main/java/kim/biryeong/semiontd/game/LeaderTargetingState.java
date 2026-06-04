package kim.biryeong.semiontd.game;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class LeaderTargetingState {
    static final int ACTIVE_TARGET_ROUNDS = 2;

    private final UUID leaderPlayerId;
    private TeamId targetTeamId;
    private int cooldownRemainingRounds;
    private int activeTargetRemainingRounds;
    private int lastUsedRound = -1;

    public LeaderTargetingState(UUID leaderPlayerId) {
        this.leaderPlayerId = Objects.requireNonNull(leaderPlayerId, "leaderPlayerId");
    }

    public UUID leaderPlayerId() {
        return leaderPlayerId;
    }

    public Optional<TeamId> targetTeamId() {
        return Optional.ofNullable(targetTeamId);
    }

    public int cooldownRemainingRounds() {
        return cooldownRemainingRounds;
    }

    public int activeTargetRemainingRounds() {
        return activeTargetRemainingRounds;
    }

    public int lastUsedRound() {
        return lastUsedRound;
    }

    public boolean canUse() {
        return cooldownRemainingRounds <= 0;
    }

    public void use(TeamId targetTeamId, int currentRound, int cooldownRounds) {
        use(targetTeamId, currentRound, cooldownRounds, ACTIVE_TARGET_ROUNDS);
    }

    public void use(TeamId targetTeamId, int currentRound, int cooldownRounds, int activeTargetRounds) {
        this.targetTeamId = Objects.requireNonNull(targetTeamId, "targetTeamId");
        this.lastUsedRound = currentRound;
        this.cooldownRemainingRounds = Math.max(0, cooldownRounds);
        this.activeTargetRemainingRounds = Math.max(1, activeTargetRounds);
    }

    public void tickRoundCooldown() {
        if (cooldownRemainingRounds > 0) {
            cooldownRemainingRounds--;
        }
        if (activeTargetRemainingRounds > 0) {
            activeTargetRemainingRounds--;
            if (activeTargetRemainingRounds == 0) {
                clearTarget();
            }
        }
    }

    public void clearTarget() {
        targetTeamId = null;
        activeTargetRemainingRounds = 0;
    }
}
