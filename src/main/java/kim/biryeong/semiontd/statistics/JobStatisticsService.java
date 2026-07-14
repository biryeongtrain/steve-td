package kim.biryeong.semiontd.statistics;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.persistence.SQLiteJobStatisticsStore;

public final class JobStatisticsService {
    private static final int QUEUE_CAPACITY = 256;
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private final Object executorLock = new Object();
    private final AtomicReference<JobStatisticsSnapshot> snapshot =
            new AtomicReference<>(JobStatisticsSnapshot.empty());
    private final AtomicReference<JobStatisticsState> state =
            new AtomicReference<>(JobStatisticsState.LOADING);
    private final AtomicReference<String> lastFailure = new AtomicReference<>();
    private final AtomicReference<Configuration> configuration = new AtomicReference<>();
    private final AtomicBoolean resyncRequested = new AtomicBoolean();

    private volatile ThreadPoolExecutor executor;
    private volatile boolean shutdown;

    public void configure(Path sqliteHistoryPath, Path fileHistoryPath, Path statisticsPath) {
        if (shutdown) {
            return;
        }
        if (statisticsPath == null) {
            configuration.set(null);
            state.set(JobStatisticsState.FAILED);
            lastFailure.set("Job statistics path is not configured.");
            return;
        }

        Configuration next = new Configuration(
                normalize(sqliteHistoryPath),
                normalize(fileHistoryPath),
                normalize(statisticsPath)
        );
        Configuration previous = configuration.getAndSet(next);
        if (next.equals(previous) && executor != null && state.get() != JobStatisticsState.FAILED) {
            return;
        }

        state.set(JobStatisticsState.LOADING);
        lastFailure.set(null);
        submit(next, () -> rebuild(next));
    }

    public void record(MatchResult matchResult) {
        Objects.requireNonNull(matchResult, "matchResult");
        Configuration current = configuration.get();
        if (current == null || shutdown) {
            return;
        }
        submit(current, () -> publish(current, store(current).ingest(matchResult)));
    }

    public JobStatisticsSnapshot snapshot() {
        return snapshot.get();
    }

    public JobStatisticsState state() {
        return state.get();
    }

    public Optional<String> lastFailure() {
        return Optional.ofNullable(lastFailure.get());
    }

    public void shutdown() {
        shutdown = true;
        ThreadPoolExecutor current;
        synchronized (executorLock) {
            current = executor;
            executor = null;
        }
        if (current == null) {
            return;
        }
        current.shutdown();
        try {
            if (!current.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                current.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            current.shutdownNow();
        }
    }

    private void submit(Configuration taskConfiguration, Runnable task) {
        ThreadPoolExecutor current = ensureExecutor();
        if (current == null) {
            return;
        }
        try {
            current.execute(() -> runTask(taskConfiguration, task));
        } catch (RejectedExecutionException exception) {
            resyncRequested.set(true);
            SemionTd.LOGGER.warn("Semion TD job-statistics queue is full; scheduling a history rescan.");
        }
    }

    private void runTask(Configuration taskConfiguration, Runnable task) {
        try {
            if (!taskConfiguration.equals(configuration.get())) {
                return;
            }
            task.run();
        } catch (RuntimeException exception) {
            resyncRequested.set(true);
            fail(taskConfiguration, exception);
        } finally {
            runRequestedResync();
        }
    }

    private void runRequestedResync() {
        if (!resyncRequested.compareAndSet(true, false)) {
            return;
        }
        Configuration latest = configuration.get();
        if (latest == null || shutdown) {
            return;
        }
        try {
            rebuild(latest);
        } catch (RuntimeException exception) {
            fail(latest, exception);
        }
    }

    private void rebuild(Configuration current) {
        JobStatisticsSnapshot rebuilt = store(current).rebuildFromHistory(
                current.sqliteHistoryPath(),
                current.fileHistoryPath()
        );
        publish(current, rebuilt);
    }

    private void publish(Configuration current, JobStatisticsSnapshot updated) {
        if (!current.equals(configuration.get())) {
            return;
        }
        snapshot.set(updated);
        lastFailure.set(null);
        state.set(JobStatisticsState.READY);
    }

    private void fail(Configuration current, RuntimeException exception) {
        if (!current.equals(configuration.get())) {
            return;
        }
        lastFailure.set(exception.getMessage());
        state.set(JobStatisticsState.FAILED);
        SemionTd.LOGGER.warn("Semion TD job-statistics processing failed.", exception);
    }

    private static SQLiteJobStatisticsStore store(Configuration configuration) {
        return new SQLiteJobStatisticsStore(configuration.statisticsPath());
    }

    private ThreadPoolExecutor ensureExecutor() {
        synchronized (executorLock) {
            if (shutdown) {
                return null;
            }
            if (executor != null) {
                return executor;
            }
            executor = new ThreadPoolExecutor(
                    1,
                    1,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                    runnable -> {
                        Thread thread = new Thread(runnable, "semion-td-job-statistics");
                        thread.setDaemon(true);
                        thread.setUncaughtExceptionHandler((ignored, exception) ->
                                SemionTd.LOGGER.error("Uncaught Semion TD job-statistics worker failure.", exception));
                        return thread;
                    },
                    new ThreadPoolExecutor.AbortPolicy()
            );
            return executor;
        }
    }

    private static Path normalize(Path path) {
        return path == null ? null : path.toAbsolutePath().normalize();
    }

    private record Configuration(Path sqliteHistoryPath, Path fileHistoryPath, Path statisticsPath) {
        private Configuration {
            Objects.requireNonNull(statisticsPath, "statisticsPath");
        }
    }
}
