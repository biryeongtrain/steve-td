package kim.biryeong.semiontd.rating;

public record RatingContributionBreakdown(
        double defenseScore,
        double pressureScore,
        double economyScore,
        double assistScore,
        double rawMultiplier,
        double appliedMultiplier
) {
    public static RatingContributionBreakdown neutral() {
        return new RatingContributionBreakdown(1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
    }

    public RatingContributionBreakdown {
        if (!Double.isFinite(defenseScore)
                || !Double.isFinite(pressureScore)
                || !Double.isFinite(economyScore)
                || !Double.isFinite(assistScore)
                || !Double.isFinite(rawMultiplier)
                || !Double.isFinite(appliedMultiplier)) {
            throw new IllegalArgumentException("Contribution scores must be finite");
        }
    }
}
