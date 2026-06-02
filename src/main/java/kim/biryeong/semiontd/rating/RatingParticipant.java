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
        PlayerMatchStatsSnapshot stats
) {
    public RatingParticipant(
            UUID playerId,
            String playerName,
            TeamId teamId,
            boolean winner,
            PlayerRatingProfile currentProfile
    ) {
        this(playerId, playerName, teamId, winner, currentProfile, PlayerMatchStatsSnapshot.empty());
    }

    public RatingParticipant {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(currentProfile, "currentProfile");
        stats = stats == null ? PlayerMatchStatsSnapshot.empty() : stats;
    }
}
