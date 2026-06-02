package kim.biryeong.semiontd.rating;

public record RatingConfig(
        boolean enabled,
        double eloKFactor,
        int initialDisplayElo,
        double initialMu,
        double initialSigma,
        int leaderboardLimit,
        int minimumParticipants,
        boolean excludeSpectators,
        boolean contributionWeightingEnabled,
        double contributionMultiplierMin,
        double contributionMultiplierMax,
        double defenseContributionWeight,
        double pressureContributionWeight,
        double economyContributionWeight,
        double assistContributionWeight
) {
    public RatingConfig(
            boolean enabled,
            double eloKFactor,
            int initialDisplayElo,
            double initialMu,
            double initialSigma,
            int leaderboardLimit,
            int minimumParticipants,
            boolean excludeSpectators
    ) {
        this(
                enabled,
                eloKFactor,
                initialDisplayElo,
                initialMu,
                initialSigma,
                leaderboardLimit,
                minimumParticipants,
                excludeSpectators,
                true,
                0.85,
                1.15,
                0.40,
                0.25,
                0.20,
                0.15
        );
    }

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
        if (!Double.isFinite(contributionMultiplierMin)
                || !Double.isFinite(contributionMultiplierMax)
                || contributionMultiplierMin <= 0.0
                || contributionMultiplierMax < contributionMultiplierMin) {
            throw new IllegalArgumentException("Contribution multiplier bounds are invalid");
        }
        double weightSum = defenseContributionWeight
                + pressureContributionWeight
                + economyContributionWeight
                + assistContributionWeight;
        if (!Double.isFinite(defenseContributionWeight)
                || !Double.isFinite(pressureContributionWeight)
                || !Double.isFinite(economyContributionWeight)
                || !Double.isFinite(assistContributionWeight)
                || defenseContributionWeight < 0.0
                || pressureContributionWeight < 0.0
                || economyContributionWeight < 0.0
                || assistContributionWeight < 0.0
                || Math.abs(weightSum - 1.0) > 0.000001) {
            throw new IllegalArgumentException("Contribution weights must be non-negative and sum to 1.0");
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
