package kim.biryeong.semiontd.game;

import java.util.Objects;

public record TeamMatchResult(
        TeamId teamId,
        int placement,
        MatchResultGroup resultGroup,
        double placementWeight,
        int eliminatedRound,
        long eliminatedTick,
        double bossDamageTaken
) {
    public TeamMatchResult {
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(resultGroup, "resultGroup");
        if (placement <= 0) {
            throw new IllegalArgumentException("placement must be positive");
        }
        if (!Double.isFinite(placementWeight) || placementWeight < 0.0) {
            throw new IllegalArgumentException("placementWeight must be finite and non-negative");
        }
        if (bossDamageTaken < 0.0 || !Double.isFinite(bossDamageTaken)) {
            throw new IllegalArgumentException("bossDamageTaken must be finite and non-negative");
        }
    }
}
