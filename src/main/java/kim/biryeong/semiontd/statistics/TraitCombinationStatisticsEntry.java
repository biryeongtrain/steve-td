package kim.biryeong.semiontd.statistics;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

public record TraitCombinationStatisticsEntry(
        String jobId,
        String primaryTraitId,
        int primaryTraitVersion,
        String secondaryTraitId,
        int secondaryTraitVersion,
        long appearances,
        long wins,
        long placementSamples,
        long placementSum,
        long finalRoundSum,
        List<Long> roundPassCounts,
        List<Long> roundAttemptCounts,
        List<TraitTowerStatisticsEntry> finalTowers
) {
    private static final List<Long> EMPTY_ROUND_COUNTS =
            Collections.nCopies(JobStatisticsEntry.MAX_TRACKED_ROUND, 0L);

    public TraitCombinationStatisticsEntry {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(primaryTraitId, "primaryTraitId");
        Objects.requireNonNull(secondaryTraitId, "secondaryTraitId");
        primaryTraitVersion = Math.max(0, primaryTraitVersion);
        secondaryTraitVersion = Math.max(0, secondaryTraitVersion);
        roundPassCounts = roundPassCounts == null ? EMPTY_ROUND_COUNTS : List.copyOf(roundPassCounts);
        roundAttemptCounts = roundAttemptCounts == null ? EMPTY_ROUND_COUNTS : List.copyOf(roundAttemptCounts);
        finalTowers = finalTowers == null ? List.of() : List.copyOf(finalTowers);
        if (roundPassCounts.size() != JobStatisticsEntry.MAX_TRACKED_ROUND
                || roundAttemptCounts.size() != JobStatisticsEntry.MAX_TRACKED_ROUND) {
            throw new IllegalArgumentException("trait round counts must contain exactly "
                    + JobStatisticsEntry.MAX_TRACKED_ROUND + " values");
        }
    }

    public OptionalDouble winRate() {
        return ratio(wins, appearances);
    }

    public OptionalDouble selectionRate(long jobAppearances) {
        return ratio(appearances, jobAppearances);
    }

    public OptionalDouble averagePlacement() {
        return ratio(placementSum, placementSamples);
    }

    public OptionalDouble averageFinalRound() {
        return ratio(finalRoundSum, appearances);
    }

    public OptionalDouble roundPassRate(int round) {
        if (round < 1 || round > JobStatisticsEntry.MAX_TRACKED_ROUND) {
            throw new IllegalArgumentException("round must be between 1 and " + JobStatisticsEntry.MAX_TRACKED_ROUND);
        }
        return ratio(roundPassCounts.get(round - 1), roundAttemptCounts.get(round - 1));
    }

    private static OptionalDouble ratio(double numerator, long denominator) {
        return denominator <= 0L ? OptionalDouble.empty() : OptionalDouble.of(numerator / denominator);
    }
}
