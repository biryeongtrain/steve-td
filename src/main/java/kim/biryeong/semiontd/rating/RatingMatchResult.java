package kim.biryeong.semiontd.rating;

import java.util.List;
import java.util.Objects;
import kim.biryeong.semiontd.game.MatchId;

public record RatingMatchResult(
        MatchId matchId,
        RatingSystemId ratingSystemId,
        int ratingVersion,
        long appliedAtEpochMillis,
        List<RatingAdjustment> adjustments
) {
    public RatingMatchResult {
        Objects.requireNonNull(matchId, "matchId");
        ratingSystemId = ratingSystemId == null ? RatingSystemId.ELO : ratingSystemId;
        if (ratingVersion < 0 || appliedAtEpochMillis < 0) {
            throw new IllegalArgumentException("Rating result metadata cannot be negative");
        }
        adjustments = List.copyOf(adjustments);
    }

    public static RatingMatchResult empty(MatchId matchId) {
        return new RatingMatchResult(matchId, RatingSystemId.ELO, 0, 0L, List.of());
    }
}
