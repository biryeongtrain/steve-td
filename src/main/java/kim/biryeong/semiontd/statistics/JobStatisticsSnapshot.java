package kim.biryeong.semiontd.statistics;

import java.util.List;
import java.util.OptionalDouble;

public record JobStatisticsSnapshot(
        long generatedAtEpochMillis,
        long eligibleMatchCount,
        long participantAppearances,
        long firstMatchAtEpochMillis,
        long lastMatchAtEpochMillis,
        List<JobStatisticsEntry> jobs
) {
    private static final JobStatisticsSnapshot EMPTY = new JobStatisticsSnapshot(0L, 0L, 0L, 0L, 0L, List.of());

    public JobStatisticsSnapshot {
        jobs = List.copyOf(jobs);
    }

    public static JobStatisticsSnapshot empty() {
        return EMPTY;
    }

    public OptionalDouble selectionRate(JobStatisticsEntry entry) {
        if (entry == null || participantAppearances <= 0L) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((double) entry.appearances() / participantAppearances);
    }
}
