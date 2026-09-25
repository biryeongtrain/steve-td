package kim.biryeong.semiontd.game;

/** Match-to-date amounts at capture time; subtract consecutive snapshots for round deltas. */
public record AugmentEconomyMetricsSnapshot(
        long cumulativeDiamondGranted,
        long cumulativeIncomeGranted,
        long cumulativeIncomeForgone,
        long cumulativePayoutWithheld
) {
    public AugmentEconomyMetricsSnapshot {
        if (cumulativeDiamondGranted < 0 || cumulativeIncomeGranted < 0
                || cumulativeIncomeForgone < 0 || cumulativePayoutWithheld < 0) {
            throw new IllegalArgumentException("Cumulative augment economy metrics cannot be negative");
        }
    }
}
