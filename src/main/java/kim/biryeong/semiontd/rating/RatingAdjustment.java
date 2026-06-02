package kim.biryeong.semiontd.rating;

import java.util.Objects;
import java.util.UUID;
import kim.biryeong.semiontd.game.TeamId;

public record RatingAdjustment(
        UUID playerId,
        String playerName,
        TeamId teamId,
        boolean winner,
        PlayerRatingProfile before,
        PlayerRatingProfile after,
        double muDelta,
        int displayEloDelta,
        RatingContributionBreakdown contribution
) {
    public RatingAdjustment(
            UUID playerId,
            String playerName,
            TeamId teamId,
            boolean winner,
            PlayerRatingProfile before,
            PlayerRatingProfile after,
            double muDelta,
            int displayEloDelta
    ) {
        this(
                playerId,
                playerName,
                teamId,
                winner,
                before,
                after,
                muDelta,
                displayEloDelta,
                RatingContributionBreakdown.neutral()
        );
    }

    public RatingAdjustment {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        contribution = contribution == null ? RatingContributionBreakdown.neutral() : contribution;
        if (!Double.isFinite(muDelta)) {
            throw new IllegalArgumentException("Rating delta must be finite");
        }
    }
}
