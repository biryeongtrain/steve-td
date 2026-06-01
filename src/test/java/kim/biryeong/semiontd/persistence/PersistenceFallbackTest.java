package kim.biryeong.semiontd.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.ProgressionConfig;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.PlayerMatchStatsSnapshot;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.progression.MatchProgressionReward;
import kim.biryeong.semiontd.progression.ProgressionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PersistenceFallbackTest {
    @TempDir
    Path tempDir;

    @Test
    void defaultPersistenceConfigUsesSqlite() {
        assertEquals(SemionPersistenceBackendType.SQLITE, SemionPersistenceConfig.defaultConfig().backend());
        assertEquals(SemionPersistenceBackendType.SQLITE, new SemionPersistenceConfig(null, null, null, null, false).backend());
    }

    @Test
    void sqliteMatchResultRepositorySavesAndLoadsRoundTrip() {
        Path database = tempDir.resolve("semiontd.db");
        MatchResult result = sampleResult();

        SQLiteMatchResultRepository repository = new SQLiteMatchResultRepository(database);
        repository.saveMatchResult(result);

        Optional<MatchResult> loaded = repository.findMatchResult(result.matchId());
        assertTrue(loaded.isPresent());
        assertEquals(result.matchId(), loaded.orElseThrow().matchId());
        assertEquals(result.participants().size(), loaded.orElseThrow().participants().size());
    }

    @Test
    void cascadingMatchResultRepositoryFallsBackFromSqliteToFile() {
        MatchResult result = sampleResult();
        RecordingMatchResultRepository sqlite = new RecordingMatchResultRepository(true);
        RecordingMatchResultRepository file = new RecordingMatchResultRepository(false);
        RecordingMatchResultRepository log = new RecordingMatchResultRepository(false);

        CascadingMatchResultRepository repository = new CascadingMatchResultRepository(sqlite, file, log);
        repository.saveMatchResult(result);

        assertEquals(List.of(result.matchId()), sqlite.saved);
        assertEquals(List.of(result.matchId()), file.saved);
        assertTrue(log.saved.isEmpty());
        assertTrue(repository.findMatchResult(result.matchId()).isPresent());
    }

    @Test
    void cascadingMatchResultRepositoryFallsBackToLoggingWhenFileFails() {
        MatchResult result = sampleResult();
        RecordingMatchResultRepository sqlite = new RecordingMatchResultRepository(true);
        RecordingMatchResultRepository file = new RecordingMatchResultRepository(true);
        RecordingMatchResultRepository log = new RecordingMatchResultRepository(false);

        CascadingMatchResultRepository repository = new CascadingMatchResultRepository(sqlite, file, log);
        repository.saveMatchResult(result);

        assertEquals(List.of(result.matchId()), sqlite.saved);
        assertEquals(List.of(result.matchId()), file.saved);
        assertEquals(List.of(result.matchId()), log.saved);
        assertTrue(repository.findMatchResult(result.matchId()).isPresent());
    }

    @Test
    void fileRepositoryPropagatesWriteFailureSoFallbackCanPreserveData() throws Exception {
        Path directoryPath = Files.createDirectory(tempDir.resolve("not-a-json-file"));
        FileMatchResultRepository repository = new FileMatchResultRepository(directoryPath);

        assertThrowsPersistenceFailure(() -> repository.saveMatchResult(sampleResult()));
    }

    @Test
    void loggingMatchResultRepositoryWritesPayloadToFallbackLog() throws Exception {
        MatchResult result = sampleResult();
        Path log = tempDir.resolve("match-results-fallback.log");

        new LoggingMatchResultRepository(log).saveMatchResult(result);

        String content = Files.readString(log);
        assertTrue(content.contains("match_result"));
        assertTrue(content.contains(result.matchId().toString()));
        assertTrue(content.contains("participants"));
    }

    @Test
    void sqliteAppliedMatchRepositorySavesAndPreventsDuplicates() {
        SQLiteAppliedMatchRepository repository = new SQLiteAppliedMatchRepository(tempDir.resolve("applied.db"));
        MatchId matchId = MatchId.newId();

        assertFalse(repository.hasApplied(matchId, "progression"));
        assertTrue(repository.markApplied(matchId, "progression", 123L));
        assertTrue(repository.hasApplied(matchId, "progression"));
        assertFalse(repository.markApplied(matchId, "progression", 456L));
    }

    @Test
    void cascadingAppliedMatchRepositoryFallsBackToLoggingWhenFileFails() {
        MatchId matchId = MatchId.newId();
        RecordingAppliedMatchRepository sqlite = new RecordingAppliedMatchRepository(true);
        RecordingAppliedMatchRepository file = new RecordingAppliedMatchRepository(true);
        RecordingAppliedMatchRepository log = new RecordingAppliedMatchRepository(false);

        CascadingAppliedMatchRepository repository = new CascadingAppliedMatchRepository(sqlite, file, log);

        assertTrue(repository.markApplied(matchId, "progression", 123L));
        assertEquals(List.of(matchId), sqlite.saved);
        assertEquals(List.of(matchId), file.saved);
        assertEquals(List.of(matchId), log.saved);
        assertTrue(repository.hasApplied(matchId, "progression"));
    }

    @Test
    void progressionDoesNotMarkAppliedWhenProfileSaveOnlyReachesFallbackLog() throws Exception {
        Path profilePathAsDirectory = Files.createDirectory(tempDir.resolve("profiles.json"));
        RecordingAppliedMatchRepository appliedMatches = new RecordingAppliedMatchRepository(false);
        ProgressionService service = new ProgressionService(
                ProgressionConfig.defaultConfig(),
                profilePathAsDirectory,
                appliedMatches
        );

        Map<UUID, MatchProgressionReward> rewards = service.applyMatchResult(null, sampleResult());

        assertTrue(rewards.isEmpty());
        assertTrue(appliedMatches.saved.isEmpty());
        assertTrue(Files.exists(tempDir.resolve("progression-fallback.log")));
    }


    @Test
    void sqliteSchemaUsesOnlyNonUniqueIndexesAndNoDomainConstraints() throws Exception {
        Path database = tempDir.resolve("schema.db");
        new SQLiteMatchResultRepository(database).saveMatchResult(sampleResult());
        new SQLiteAppliedMatchRepository(database).markApplied(MatchId.newId(), "progression", 1L);

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             ResultSet tables = statement.executeQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")) {
            while (tables.next()) {
                String sql = tables.getString(1).toUpperCase(java.util.Locale.ROOT);
                assertFalse(sql.contains("NOT NULL"), sql);
                assertFalse(sql.contains("PRIMARY KEY"), sql);
                assertFalse(sql.contains("FOREIGN KEY"), sql);
                assertFalse(sql.contains("UNIQUE"), sql);
                assertFalse(sql.contains("CHECK"), sql);
            }
        }

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             ResultSet indexes = statement.executeQuery("SELECT sql FROM sqlite_master WHERE type = 'index'")) {
            while (indexes.next()) {
                String sql = indexes.getString(1).toUpperCase(java.util.Locale.ROOT);
                assertFalse(sql.contains("UNIQUE"), sql);
            }
        }
    }

    private static void assertThrowsPersistenceFailure(Runnable runnable) {
        try {
            runnable.run();
        } catch (PersistenceException expected) {
            return;
        }
        throw new AssertionError("Expected PersistenceException");
    }

    private static MatchResult sampleResult() {
        UUID playerId = UUID.nameUUIDFromBytes("persistence-player".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new MatchResult(
                MatchId.newId(),
                10L,
                20L,
                List.of(new MatchParticipantResult(playerId, "player", TeamId.RED, true, PlayerMatchStatsSnapshot.empty())),
                Set.of(),
                Set.of(TeamId.RED),
                List.of(),
                3
        );
    }

    private static final class RecordingMatchResultRepository implements MatchResultRepository {
        private final boolean fail;
        private final List<MatchId> saved = new ArrayList<>();
        private MatchResult result;

        private RecordingMatchResultRepository(boolean fail) {
            this.fail = fail;
        }

        @Override
        public void saveMatchResult(MatchResult matchResult) {
            saved.add(matchResult.matchId());
            if (fail) {
                throw new PersistenceException("forced failure");
            }
            result = matchResult;
        }

        @Override
        public Optional<MatchResult> findMatchResult(MatchId matchId) {
            return result != null && result.matchId().equals(matchId) ? Optional.of(result) : Optional.empty();
        }
    }

    private static final class RecordingAppliedMatchRepository implements AppliedMatchRepository {
        private final boolean fail;
        private final List<MatchId> saved = new ArrayList<>();
        private boolean applied;

        private RecordingAppliedMatchRepository(boolean fail) {
            this.fail = fail;
        }

        @Override
        public boolean hasApplied(MatchId matchId, String subsystem) {
            return applied;
        }

        @Override
        public boolean markApplied(MatchId matchId, String subsystem, long appliedAtEpochMillis) {
            saved.add(matchId);
            if (fail) {
                throw new PersistenceException("forced failure");
            }
            if (applied) {
                return false;
            }
            applied = true;
            return true;
        }
    }
}
