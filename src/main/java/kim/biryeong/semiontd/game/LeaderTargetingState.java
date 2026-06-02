package kim.biryeong.semiontd.game;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class LeaderTargetingState {
    private final UUID leaderPlayerId;
    private TeamId targetTeamId;
    private int cooldownRemainingRounds;
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

    public int lastUsedRound() {
        return lastUsedRound;
    }

    public boolean canUse() {
        return cooldownRemainingRounds <= 0;
    }

    public void use(TeamId targetTeamId, int currentRound, int cooldownRounds) {
        this.targetTeamId = Objects.requireNonNull(targetTeamId, "targetTeamId");
        this.lastUsedRound = currentRound;
        this.cooldownRemainingRounds = Math.max(0, cooldownRounds);
    }

    public void tickRoundCooldown() {
        if (cooldownRemainingRounds > 0) {
            cooldownRemainingRounds--;
        }
    }

    public void clearTarget() {
        targetTeamId = null;
    }
}
