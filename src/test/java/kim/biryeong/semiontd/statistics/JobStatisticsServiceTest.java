package kim.biryeong.semiontd.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.MatchResultGroup;
import kim.biryeong.semiontd.game.PlayerMatchStatsSnapshot;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamMatchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JobStatisticsServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void recordsOnDedicatedWorkerWithoutBlockingCallerAndRetainsLastSnapshotOnFailure() throws Exception {
        JobStatisticsService service = new JobStatisticsService();
        try {
            Path statisticsPath = tempDir.resolve("job-statistics.db");
            service.configure(null, null, statisticsPath);
            waitFor(() -> service.state() == JobStatisticsState.READY);
            assertTrue(Thread.getAllStackTraces().keySet().stream()
                    .anyMatch(thread -> thread.getName().equals("semion-td-job-statistics")));

            assertTimeout(Duration.ofMillis(200), () -> service.record(sampleResult()));
            waitFor(() -> service.snapshot().participantAppearances() == 1L);
            JobStatisticsSnapshot successful = service.snapshot();
            assertEquals(1L, successful.eligibleMatchCount());

            Path invalidParent = Files.writeString(tempDir.resolve("not-a-directory"), "x");
            service.configure(null, null, invalidParent.resolve("job-statistics.db"));
            waitFor(() -> service.state() == JobStatisticsState.FAILED);
            assertEquals(successful, service.snapshot());
            assertTrue(service.lastFailure().isPresent());
        } finally {
            service.shutdown();
        }
    }

    private static MatchResult sampleResult() {
        MatchParticipantResult participant = new MatchParticipantResult(
                UUID.nameUUIDFromBytes("job-statistics-worker".getBytes(StandardCharsets.UTF_8)),
                "worker",
                TeamId.RED,
                true,
                PlayerMatchStatsSnapshot.empty(),
                "semion-td:villager"
        );
        return new MatchResult(
                new MatchId(100L),
                1_000L,
                2_000L,
                List.of(participant),
                Set.of(),
                Set.of(TeamId.RED),
                List.of(new TeamMatchResult(
                        TeamId.RED,
                        1,
                        MatchResultGroup.WIN_GROUP,
                        1.0,
                        -1,
                        -1L,
                        0.0
                )),
                10,
                MatchMode.NORMAL
        );
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for asynchronous job-statistics work.");
    }
}
