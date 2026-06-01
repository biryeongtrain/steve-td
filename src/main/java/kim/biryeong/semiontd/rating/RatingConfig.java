package kim.biryeong.semiontd.rating;

public record RatingConfig(
        boolean enabled,
        double eloKFactor,
        int initialDisplayElo,
        double initialMu,
        double initialSigma,
        int leaderboardLimit,
        int minimumParticipants,
        boolean excludeSpectators
) {
    public RatingConfig {
        if (!Double.isFinite(eloKFactor) || eloKFactor <= 0.0) {
            throw new IllegalArgumentException("eloKFactor must be positive and finite");
        }
        if (initialDisplayElo <= 0) {
            throw new IllegalArgumentException("initialDisplayElo must be positive");
        }
        if (!Double.isFinite(initialMu) || initialMu <= 0.0) {
            throw new IllegalArgumentException("initialMu must be positive and finite");
        }
        if (!Double.isFinite(initialSigma) || initialSigma < 0.0) {
            throw new IllegalArgumentException("initialSigma must be finite and non-negative");
        }
        if (leaderboardLimit <= 0) {
            throw new IllegalArgumentException("leaderboardLimit must be positive");
        }
        if (minimumParticipants < 2) {
            throw new IllegalArgumentException("minimumParticipants must be at least 2");
        }
    }

    public static RatingConfig defaultConfig() {
        return new RatingConfig(
                true,
                EloRatingCalculator.DEFAULT_K_FACTOR,
                PlayerRatingProfile.INITIAL_DISPLAY_ELO,
                PlayerRatingProfile.INITIAL_MU,
                PlayerRatingProfile.INITIAL_SIGMA,
                10,
                2,
                true
        );
    }
}
