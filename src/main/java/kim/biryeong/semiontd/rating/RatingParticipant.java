package kim.biryeong.semiontd.rating;

import java.util.Objects;
import java.util.UUID;
import kim.biryeong.semiontd.game.PlayerMatchStatsSnapshot;
import kim.biryeong.semiontd.game.TeamId;

public record RatingParticipant(
        UUID playerId,
        String playerName,
        TeamId teamId,
        boolean winner,
        int placement,
        double placementWeight,
        PlayerRatingProfile currentProfile,
        PlayerMatchStatsSnapshot stats
) {
    public RatingParticipant(
            UUID playerId,
            String playerName,
            TeamId teamId,
            boolean winner,
            PlayerRatingProfile currentProfile
    ) {
        this(playerId, playerName, teamId, winner, winner ? 1 : 2, winner ? 1.0 : 0.0, currentProfile, PlayerMatchStatsSnapshot.empty());
    }

    public RatingParticipant(
            UUID playerId,
            String playerName,
            TeamId teamId,
            boolean winner,
            PlayerRatingProfile currentProfile,
            PlayerMatchStatsSnapshot stats
    ) {
        this(playerId, playerName, teamId, winner, winner ? 1 : 2, winner ? 1.0 : 0.0, currentProfile, stats);
    }

    public RatingParticipant(
            UUID playerId,
            String playerName,
            TeamId teamId,
            boolean winner,
            int placement,
            double placementWeight,
            PlayerRatingProfile currentProfile
    ) {
        this(playerId, playerName, teamId, winner, placement, placementWeight, currentProfile, PlayerMatchStatsSnapshot.empty());
    }

    public RatingParticipant {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(currentProfile, "currentProfile");
        if (placement <= 0) {
            throw new IllegalArgumentException("placement must be positive");
        }
        if (!Double.isFinite(placementWeight) || placementWeight < 0.0) {
            throw new IllegalArgumentException("placementWeight must be finite and non-negative");
        }
        stats = stats == null ? PlayerMatchStatsSnapshot.empty() : stats;
    }
}
