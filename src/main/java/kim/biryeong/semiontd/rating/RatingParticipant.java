package kim.biryeong.semiontd.rating;

import java.util.Objects;
import java.util.UUID;
import kim.biryeong.semiontd.game.TeamId;

public record RatingParticipant(
        UUID playerId,
        String playerName,
        TeamId teamId,
        boolean winner,
        PlayerRatingProfile currentProfile
) {
    public RatingParticipant {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(currentProfile, "currentProfile");
    }
}
