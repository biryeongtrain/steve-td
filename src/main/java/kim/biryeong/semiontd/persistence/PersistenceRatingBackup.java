package kim.biryeong.semiontd.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import static kim.biryeong.semiontd.persistence.PersistenceRepositoryFactory.resolveSqlitePath;

public final class PersistenceRatingBackup {
    private PersistenceRatingBackup() {
    }

    private static final DateTimeFormatter RATING_BACKUP_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    public static Path reset(Path configDir, SemionPersistenceConfig persistenceConfig) {
        if (configDir == null) {
            throw new PersistenceException("Semion TD config directory is not configured.");
        }

        Path ratingProfilePath = configDir.resolve("ratings.json");
        Path ratingEventPath = configDir.resolve("rating-events.json");
        SemionPersistenceConfig safePersistenceConfig = persistenceConfig == null
                ? SemionPersistenceConfig.defaultConfig()
                : persistenceConfig;
        Path sqlitePath = resolveSqlitePath(configDir, safePersistenceConfig);
        Path backupPath = configDir.resolve("rating-backups")
                .resolve("elo-softreset-" + RATING_BACKUP_TIMESTAMP_FORMATTER.format(Instant.now()));

        try {
            Files.createDirectories(backupPath);
            backupFileIfExists(ratingProfilePath, backupPath.resolve("ratings.json"));
            backupFileIfExists(ratingEventPath, backupPath.resolve("rating-events.json"));
            if (sqlitePath != null) {
                backupSqliteRatings(sqlitePath, backupPath.resolve(sqlitePath.getFileName()));
            }
            Files.deleteIfExists(ratingProfilePath);
            Files.deleteIfExists(ratingEventPath);
            if (sqlitePath != null) {
                clearSqliteRatings(sqlitePath);
            }
        } catch (IOException | SQLException exception) {
            throw new PersistenceException("Failed to soft reset ratings with backup " + backupPath, exception);
        }
        return backupPath;
    }

    private static void backupFileIfExists(Path source, Path target) throws IOException {
        if (Files.exists(source)) {
            Files.copy(source, target);
        }
    }

    private static void backupSqliteRatings(Path sqlitePath, Path backupPath) throws IOException, SQLException {
        checkpointSqlite(sqlitePath);
        Files.copy(sqlitePath, backupPath);
    }

    private static void checkpointSqlite(Path sqlitePath) throws SQLException {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath.toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA wal_checkpoint(FULL)");
        }
    }

    private static void clearSqliteRatings(Path sqlitePath) throws SQLException {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath.toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
            connection.setAutoCommit(false);
            try {
                statement.executeUpdate("DELETE FROM rating_profiles");
                statement.executeUpdate("DELETE FROM rating_events");
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }
}
