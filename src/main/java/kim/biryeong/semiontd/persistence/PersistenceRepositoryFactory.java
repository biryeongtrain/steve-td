package kim.biryeong.semiontd.persistence;

import java.nio.file.Path;
import java.util.Optional;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.SemionConfigLoader;
import kim.biryeong.semiontd.rating.PlayerRatingProfile;
import kim.biryeong.semiontd.rating.RatingMatchResult;

public final class PersistenceRepositoryFactory {
    private PersistenceRepositoryFactory() {
    }

    public static Path resolveSqlitePath(Path configDir, SemionPersistenceConfig persistenceConfig) {
        if (configDir == null || persistenceConfig.backend() != SemionPersistenceBackendType.SQLITE) {
            return null;
        }
        return resolveConfiguredSqlitePath(configDir, persistenceConfig);
    }

    public static Path resolveConfiguredSqlitePath(Path configDir, SemionPersistenceConfig persistenceConfig) {
        if (configDir == null) {
            return null;
        }
        Path configured = Path.of(persistenceConfig.sqlitePath());
        return configured.isAbsolute() ? configured : configDir.resolve(configured).normalize();
    }

    public static MatchResultRepository createMatchResultRepository(
            SemionPersistenceConfig persistenceConfig,
            Path sqlitePath,
            Path filePath,
            Path configDir
    ) {
        MatchResultRepository file = new FileMatchResultRepository(filePath);
        MatchResultRepository log = new LoggingMatchResultRepository(fallbackLogPath(configDir, "match-results-fallback.log"));
        if (sqlitePath == null) {
            if (requiresSQLite(persistenceConfig)) {
                throw new PersistenceException("SQLite match-result repository is required but no SQLite path is available.");
            }
            return new CascadingMatchResultRepository(file, log, log);
        }
        try {
            return new CascadingMatchResultRepository(new SQLiteMatchResultRepository(sqlitePath), file, log);
        } catch (RuntimeException exception) {
            if (persistenceConfig.externalDbRequired()) {
                throw new PersistenceException("SQLite match-result repository is required but initialization failed.", exception);
            }
            SemionTd.LOGGER.warn("SQLite match-result repository initialization failed; using file/log fallback.", exception);
            return new CascadingMatchResultRepository(file, log, log);
        }
    }

    public static AppliedMatchRepository createAppliedMatchRepository(
            SemionPersistenceConfig persistenceConfig,
            Path sqlitePath,
            Path filePath,
            Path configDir
    ) {
        AppliedMatchRepository file = new FileAppliedMatchRepository(filePath);
        AppliedMatchRepository log = new LoggingAppliedMatchRepository(fallbackLogPath(configDir, "applied-matches-fallback.log"));
        if (sqlitePath == null) {
            if (requiresSQLite(persistenceConfig)) {
                throw new PersistenceException("SQLite applied-match repository is required but no SQLite path is available.");
            }
            return new CascadingAppliedMatchRepository(file, log, log);
        }
        try {
            return new CascadingAppliedMatchRepository(new SQLiteAppliedMatchRepository(sqlitePath), file, log);
        } catch (RuntimeException exception) {
            if (persistenceConfig.externalDbRequired()) {
                throw new PersistenceException("SQLite applied-match repository is required but initialization failed.", exception);
            }
            SemionTd.LOGGER.warn("SQLite applied-match repository initialization failed; using file/log fallback.", exception);
            return new CascadingAppliedMatchRepository(file, log, log);
        }
    }

    public static RatingRepository createRatingRepository(
            SemionPersistenceConfig persistenceConfig,
            Path sqlitePath,
            Path filePath
    ) {
        RatingRepository file = new FileRatingRepository(filePath);
        if (sqlitePath == null) {
            if (requiresSQLite(persistenceConfig)) {
                throw new PersistenceException("SQLite rating repository is required but no SQLite path is available.");
            }
            return file;
        }
        try {
            RatingRepository sqlite = new SQLiteRatingRepository(sqlitePath);
            migrateFallbackRatingProfiles(file, sqlite);
            return sqlite;
        } catch (RuntimeException exception) {
            if (persistenceConfig.externalDbRequired()) {
                throw new PersistenceException("SQLite rating repository is required but initialization failed.", exception);
            }
            SemionTd.LOGGER.warn("SQLite rating repository initialization failed; using file fallback.", exception);
            return file;
        }
    }

    static void migrateFallbackRatingProfiles(RatingRepository fallback, RatingRepository primary) {
        int migrated = 0;
        for (PlayerRatingProfile fallbackProfile : fallback.findAllProfiles().values()) {
            Optional<PlayerRatingProfile> existing = primary.findProfile(fallbackProfile.playerId());
            if (existing.isPresent()
                    && existing.get().updatedAtEpochMillis() >= fallbackProfile.updatedAtEpochMillis()) {
                continue;
            }
            primary.saveProfile(fallbackProfile.playerId(), fallbackProfile);
            migrated++;
        }
        if (migrated > 0) {
            SemionTd.LOGGER.info("Migrated {} fallback rating profiles into primary rating repository.", migrated);
        }
    }

    static void migrateFallbackRatingEvents(RatingEventRepository fallback, RatingEventRepository primary) {
        int migrated = 0;
        for (RatingMatchResult fallbackResult : fallback.findAllMatchResults().values()) {
            if (primary.findMatchResult(fallbackResult.matchId()).isPresent()) {
                continue;
            }
            primary.saveMatchResult(fallbackResult);
            migrated++;
        }
        if (migrated > 0) {
            SemionTd.LOGGER.info("Migrated {} fallback rating events into primary rating-event repository.", migrated);
        }
    }

    public static RatingEventRepository createRatingEventRepository(
            SemionPersistenceConfig persistenceConfig,
            Path sqlitePath,
            Path filePath
    ) {
        RatingEventRepository file = new FileRatingEventRepository(filePath);
        if (sqlitePath == null) {
            if (requiresSQLite(persistenceConfig)) {
                throw new PersistenceException("SQLite rating-event repository is required but no SQLite path is available.");
            }
            return file;
        }
        try {
            RatingEventRepository sqlite = new SQLiteRatingEventRepository(sqlitePath);
            migrateFallbackRatingEvents(file, sqlite);
            return sqlite;
        } catch (RuntimeException exception) {
            if (persistenceConfig.externalDbRequired()) {
                throw new PersistenceException("SQLite rating-event repository is required but initialization failed.", exception);
            }
            SemionTd.LOGGER.warn("SQLite rating-event repository initialization failed; using file fallback.", exception);
            return file;
        }
    }

    private static boolean requiresSQLite(SemionPersistenceConfig persistenceConfig) {
        return persistenceConfig.backend() == SemionPersistenceBackendType.SQLITE && persistenceConfig.externalDbRequired();
    }

    public static Path fallbackLogPath(Path configDir, String fileName) {
        return configDir == null ? null : configDir.resolve(fileName);
    }
}
