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
        PlayerRatingProfile currentProfile,
        double placementScore,
        PlayerMatchStatsSnapshot stats
) {
    public RatingParticipant(
            UUID playerId,
            String playerName,
            TeamId teamId,
            boolean winner,
            PlayerRatingProfile currentProfile
    ) {
        this(playerId, playerName, teamId, winner, currentProfile, winner ? 1.0 : 0.0, PlayerMatchStatsSnapshot.empty());
    }

    public RatingParticipant(
            UUID playerId,
            String playerName,
            TeamId teamId,
            boolean winner,
            PlayerRatingProfile currentProfile,
            double placementScore
    ) {
        this(playerId, playerName, teamId, winner, currentProfile, placementScore, PlayerMatchStatsSnapshot.empty());
    }

    public RatingParticipant(
            UUID playerId,
            String playerName,
            TeamId teamId,
            boolean winner,
            PlayerRatingProfile currentProfile,
            PlayerMatchStatsSnapshot stats
    ) {
        this(playerId, playerName, teamId, winner, currentProfile, winner ? 1.0 : 0.0, stats);
    }

    public RatingParticipant {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(currentProfile, "currentProfile");
        if (!Double.isFinite(placementScore) || placementScore < 0.0 || placementScore > 1.0) {
            throw new IllegalArgumentException("placementScore must be between 0.0 and 1.0");
        }
        stats = stats == null ? PlayerMatchStatsSnapshot.empty() : stats;
    }
}
