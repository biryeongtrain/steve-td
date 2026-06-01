package kim.biryeong.semiontd.rating;

import java.util.Objects;
import java.util.UUID;
import kim.biryeong.semiontd.game.MatchId;

public record PlayerRatingProfile(
        UUID playerId,
        String lastKnownName,
        RatingSystemId ratingSystemId,
        int ratingVersion,
        int gamesPlayed,
        int wins,
        int losses,
        double mu,
        double sigma,
        int displayElo,
        MatchId lastUpdatedMatchId,
        long updatedAtEpochMillis
) {
    public static final int INITIAL_DISPLAY_ELO = 1500;
    public static final double INITIAL_MU = 1500.0;
    public static final double INITIAL_SIGMA = 350.0;

    public PlayerRatingProfile {
        Objects.requireNonNull(playerId, "playerId");
        ratingSystemId = ratingSystemId == null ? RatingSystemId.ELO : ratingSystemId;
        lastKnownName = lastKnownName == null ? "" : lastKnownName;
        if (ratingVersion < 0 || gamesPlayed < 0 || wins < 0 || losses < 0 || updatedAtEpochMillis < 0) {
            throw new IllegalArgumentException("Rating profile counters cannot be negative");
        }
        if (wins + losses > gamesPlayed) {
            throw new IllegalArgumentException("Rating wins and losses cannot exceed games played");
        }
        if (!Double.isFinite(mu) || !Double.isFinite(sigma) || sigma < 0.0) {
            throw new IllegalArgumentException("Rating profile values must be finite");
        }
    }

    public static PlayerRatingProfile initial(UUID playerId, String playerName) {
        return new PlayerRatingProfile(
                playerId,
                playerName,
                RatingSystemId.ELO,
                0,
                0,
                0,
                0,
                INITIAL_MU,
                INITIAL_SIGMA,
                INITIAL_DISPLAY_ELO,
                null,
                0L
        );
    }
}
