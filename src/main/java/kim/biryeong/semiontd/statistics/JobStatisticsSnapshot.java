package kim.biryeong.semiontd.statistics;

import java.util.List;
import java.util.OptionalDouble;

public record JobStatisticsSnapshot(
        long generatedAtEpochMillis,
        long eligibleMatchCount,
        long participantAppearances,
        long firstMatchAtEpochMillis,
        long lastMatchAtEpochMillis,
        List<JobStatisticsEntry> jobs,
        List<TraitCombinationStatisticsEntry> traitCombinations
) {
    private static final JobStatisticsSnapshot EMPTY =
            new JobStatisticsSnapshot(0L, 0L, 0L, 0L, 0L, List.of(), List.of());

    public JobStatisticsSnapshot(
            long generatedAtEpochMillis,
            long eligibleMatchCount,
            long participantAppearances,
            long firstMatchAtEpochMillis,
            long lastMatchAtEpochMillis,
            List<JobStatisticsEntry> jobs
    ) {
        this(generatedAtEpochMillis, eligibleMatchCount, participantAppearances,
                firstMatchAtEpochMillis, lastMatchAtEpochMillis, jobs, List.of());
    }

    public JobStatisticsSnapshot {
        jobs = List.copyOf(jobs);
        traitCombinations = traitCombinations == null ? List.of() : List.copyOf(traitCombinations);
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

    public List<TraitCombinationStatisticsEntry> traitCombinationsForJob(String jobId) {
        return traitCombinations.stream()
                .filter(entry -> entry.jobId().equals(jobId))
                .toList();
    }
}
