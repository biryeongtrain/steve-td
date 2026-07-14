package kim.biryeong.semiontd.statistics;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

public record JobStatisticsEntry(
        String jobId,
        long appearances,
        long wins,
        long placementSamples,
        long placementSum,
        long finalRoundSum,
        JobStatisticsTotals totals,
        long firstMatchAtEpochMillis,
        long lastMatchAtEpochMillis,
        long updatedAtEpochMillis,
        List<Long> roundPassCounts,
        List<Long> roundAttemptCounts
) {
    public static final int MAX_TRACKED_ROUND = 40;
    private static final List<Long> EMPTY_ROUND_PASS_COUNTS = java.util.Collections.nCopies(
            MAX_TRACKED_ROUND,
            0L
    );
    private static final List<Long> EMPTY_ROUND_ATTEMPT_COUNTS = java.util.Collections.nCopies(
            MAX_TRACKED_ROUND,
            0L
    );

    public JobStatisticsEntry(
            String jobId,
            long appearances,
            long wins,
            long placementSamples,
            long placementSum,
            long finalRoundSum,
            JobStatisticsTotals totals,
            long firstMatchAtEpochMillis,
            long lastMatchAtEpochMillis,
            long updatedAtEpochMillis
    ) {
        this(
                jobId,
                appearances,
                wins,
                placementSamples,
                placementSum,
                finalRoundSum,
                totals,
                firstMatchAtEpochMillis,
                lastMatchAtEpochMillis,
                updatedAtEpochMillis,
                EMPTY_ROUND_PASS_COUNTS,
                EMPTY_ROUND_ATTEMPT_COUNTS
        );
    }

    public JobStatisticsEntry(
            String jobId,
            long appearances,
            long wins,
            long placementSamples,
            long placementSum,
            long finalRoundSum,
            JobStatisticsTotals totals,
            long firstMatchAtEpochMillis,
            long lastMatchAtEpochMillis,
            long updatedAtEpochMillis,
            List<Long> roundPassCounts
    ) {
        this(
                jobId,
                appearances,
                wins,
                placementSamples,
                placementSum,
                finalRoundSum,
                totals,
                firstMatchAtEpochMillis,
                lastMatchAtEpochMillis,
                updatedAtEpochMillis,
                roundPassCounts,
                java.util.Collections.nCopies(MAX_TRACKED_ROUND, Math.max(0L, appearances))
        );
    }

    public JobStatisticsEntry {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(totals, "totals");
        roundPassCounts = roundPassCounts == null ? EMPTY_ROUND_PASS_COUNTS : List.copyOf(roundPassCounts);
        roundAttemptCounts = roundAttemptCounts == null ? EMPTY_ROUND_ATTEMPT_COUNTS : List.copyOf(roundAttemptCounts);
        if (roundPassCounts.size() != MAX_TRACKED_ROUND) {
            throw new IllegalArgumentException("roundPassCounts must contain exactly " + MAX_TRACKED_ROUND + " values");
        }
        if (roundAttemptCounts.size() != MAX_TRACKED_ROUND) {
            throw new IllegalArgumentException("roundAttemptCounts must contain exactly " + MAX_TRACKED_ROUND + " values");
        }
    }

    public OptionalDouble winRate() {
        return ratio(wins, appearances);
    }

    public OptionalDouble averagePlacement() {
        return ratio(placementSum, placementSamples);
    }

    public OptionalDouble averageFinalRound() {
        return ratio(finalRoundSum, appearances);
    }

    public OptionalDouble averageValue(double total) {
        return ratio(total, appearances);
    }

    public OptionalDouble defenseSuccessRate() {
        if (totals.ownLaneIncomingThreat() <= 0.0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(1.0 - totals.ownLaneLeakedThreat() / totals.ownLaneIncomingThreat());
    }

    public OptionalDouble incomeAttackSuccessRate() {
        return ratio(totals.incomeAttackSuccessThreat(), totals.sentIncomeThreat());
    }

    public long roundPassCount(int round) {
        validateTrackedRound(round);
        return roundPassCounts.get(round - 1);
    }

    public long roundAttemptCount(int round) {
        validateTrackedRound(round);
        return roundAttemptCounts.get(round - 1);
    }

    public OptionalDouble roundPassRate(int round) {
        return ratio(roundPassCount(round), roundAttemptCount(round));
    }

    private static void validateTrackedRound(int round) {
        if (round < 1 || round > MAX_TRACKED_ROUND) {
            throw new IllegalArgumentException("round must be between 1 and " + MAX_TRACKED_ROUND);
        }
    }

    private static OptionalDouble ratio(double numerator, long denominator) {
        return denominator <= 0 ? OptionalDouble.empty() : OptionalDouble.of(numerator / denominator);
    }

    private static OptionalDouble ratio(double numerator, double denominator) {
        return denominator <= 0.0 ? OptionalDouble.empty() : OptionalDouble.of(numerator / denominator);
    }
}
