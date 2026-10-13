package kim.biryeong.semiontd.persistence;

import kim.biryeong.semiontd.game.MatchId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.rating.PlayerRatingProfile;
import kim.biryeong.semiontd.rating.RatingMatchResult;
import kim.biryeong.semiontd.rating.RatingSystemId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PersistenceStoresTest {
    @TempDir
    Path tempDir;

    @Test
    void requiredSqliteMatchResultInitializationFailureFailsFastInsteadOfFallingBack() throws Exception {
        Path sqlitePathAsDirectory = Files.createDirectory(tempDir.resolve("required-match-results.db"));
        SemionPersistenceConfig requiredSqlite = requiredSqlite(sqlitePathAsDirectory);

        assertThrows(PersistenceException.class, () -> PersistenceRepositoryFactory.createMatchResultRepository(
                requiredSqlite,
                sqlitePathAsDirectory,
                tempDir.resolve("match-results.json"),
                tempDir
        ));
    }

    @Test
    void requiredSqliteAppliedMatchInitializationFailureFailsFastInsteadOfFallingBack() throws Exception {
        Path sqlitePathAsDirectory = Files.createDirectory(tempDir.resolve("required-applied-matches.db"));
        SemionPersistenceConfig requiredSqlite = requiredSqlite(sqlitePathAsDirectory);

        assertThrows(PersistenceException.class, () -> PersistenceRepositoryFactory.createAppliedMatchRepository(
                requiredSqlite,
                sqlitePathAsDirectory,
                tempDir.resolve("progression-applied-matches.json"),
                tempDir
        ));
    }

    @Test
    void requiredSqliteRatingRepositoryInitializationFailureFailsFastInsteadOfFallingBack() throws Exception {
        Path sqlitePathAsDirectory = Files.createDirectory(tempDir.resolve("required-rating.db"));
        SemionPersistenceConfig requiredSqlite = requiredSqlite(sqlitePathAsDirectory);

        assertThrows(PersistenceException.class, () -> PersistenceRepositoryFactory.createRatingRepository(
                requiredSqlite,
                sqlitePathAsDirectory,
                tempDir.resolve("ratings.json")
        ));
    }

    @Test
    void requiredSqliteRatingEventRepositoryInitializationFailureFailsFastInsteadOfFallingBack() throws Exception {
        Path sqlitePathAsDirectory = Files.createDirectory(tempDir.resolve("required-rating-events.db"));
        SemionPersistenceConfig requiredSqlite = requiredSqlite(sqlitePathAsDirectory);

        assertThrows(PersistenceException.class, () -> PersistenceRepositoryFactory.createRatingEventRepository(
                requiredSqlite,
                sqlitePathAsDirectory,
                tempDir.resolve("rating-events.json")
        ));
    }

    @Test
    void recoveredSqliteRatingRepositoryImportsNewerFileFallbackProfiles() {
        Path sqlitePath = tempDir.resolve("ratings.db");
        Path filePath = tempDir.resolve("ratings.json");
        UUID fallbackOnlyId = UUID.nameUUIDFromBytes("fallback-only".getBytes());
        UUID conflictId = UUID.nameUUIDFromBytes("fallback-conflict".getBytes());
        FileRatingRepository fallback = new FileRatingRepository(filePath);
        fallback.saveProfile(fallbackOnlyId, profile(fallbackOnlyId, "fallbackOnly", 1510, 10L));
        fallback.saveProfile(conflictId, profile(conflictId, "fallbackNewer", 1600, 20L));

        RatingRepository initialSqlite = PersistenceRepositoryFactory.createRatingRepository(
                new SemionPersistenceConfig(SemionPersistenceBackendType.SQLITE, sqlitePath.toString(), "", "semiontd", false),
                sqlitePath,
                tempDir.resolve("empty-ratings.json")
        );
        initialSqlite.saveProfile(conflictId, profile(conflictId, "sqliteOlder", 1400, 5L));

        RatingRepository recovered = PersistenceRepositoryFactory.createRatingRepository(
                new SemionPersistenceConfig(SemionPersistenceBackendType.SQLITE, sqlitePath.toString(), "", "semiontd", false),
                sqlitePath,
                filePath
        );

        assertEquals(1510, recovered.findProfile(fallbackOnlyId).orElseThrow().displayElo());
        assertEquals(1600, recovered.findProfile(conflictId).orElseThrow().displayElo());
    }

    @Test
    void recoveredSqliteRatingEventRepositoryImportsFileFallbackEvents() {
        Path sqlitePath = tempDir.resolve("rating-events.db");
        Path filePath = tempDir.resolve("rating-events.json");
        MatchId fallbackOnlyId = new MatchId(41L);
        MatchId conflictId = new MatchId(42L);
        FileRatingEventRepository fallback = new FileRatingEventRepository(filePath);
        fallback.saveMatchResult(ratingResult(fallbackOnlyId, 100L));
        fallback.saveMatchResult(ratingResult(conflictId, 200L));

        RatingEventRepository initialSqlite = PersistenceRepositoryFactory.createRatingEventRepository(
                new SemionPersistenceConfig(SemionPersistenceBackendType.SQLITE, sqlitePath.toString(), "", "semiontd", false),
                sqlitePath,
                tempDir.resolve("empty-rating-events.json")
        );
        initialSqlite.saveMatchResult(ratingResult(conflictId, 150L));

        RatingEventRepository recovered = PersistenceRepositoryFactory.createRatingEventRepository(
                new SemionPersistenceConfig(SemionPersistenceBackendType.SQLITE, sqlitePath.toString(), "", "semiontd", false),
                sqlitePath,
                filePath
        );

        assertEquals(100L, recovered.findMatchResult(fallbackOnlyId).orElseThrow().appliedAtEpochMillis());
        assertEquals(150L, recovered.findMatchResult(conflictId).orElseThrow().appliedAtEpochMillis());
    }

    @Test
    void softResetRatingsBacksUpAndClearsRatingDataButPreservesAppliedMarkers() throws Exception {
        Path database = tempDir.resolve("semiontd.db");
        Path profileFile = tempDir.resolve("ratings.json");
        Path eventFile = tempDir.resolve("rating-events.json");
        UUID sqlitePlayerId = UUID.nameUUIDFromBytes("soft-reset-sqlite".getBytes());
        UUID fallbackPlayerId = UUID.nameUUIDFromBytes("soft-reset-fallback".getBytes());
        MatchId ratingMatchId = new MatchId(91L);

        new SQLiteRatingRepository(database).saveProfile(sqlitePlayerId, profile(sqlitePlayerId, "sqlite", 1510, 10L));
        new SQLiteRatingEventRepository(database).saveMatchResult(ratingResult(ratingMatchId, 10L));
        new SQLiteAppliedMatchRepository(database).markApplied(ratingMatchId, "rating", 20L);
        new FileRatingRepository(profileFile).saveProfile(fallbackPlayerId, profile(fallbackPlayerId, "fallback", 1600, 20L));
        new FileRatingEventRepository(eventFile).saveMatchResult(ratingResult(new MatchId(92L), 20L));

        Path backupPath = PersistenceRatingBackup.reset(
                tempDir,
                new SemionPersistenceConfig(SemionPersistenceBackendType.SQLITE, database.toString(), "", "semiontd", false)
        );

        assertTrue(Files.exists(backupPath.resolve("semiontd.db")));
        assertTrue(Files.exists(backupPath.resolve("ratings.json")));
        assertTrue(Files.exists(backupPath.resolve("rating-events.json")));
        assertFalse(Files.exists(profileFile));
        assertFalse(Files.exists(eventFile));
        assertEquals(0, countRows(database, "rating_profiles"));
        assertEquals(0, countRows(database, "rating_events"));
        assertEquals(1, countRows(database, "applied_matches"));
        Path backupDatabase = backupPath.resolve("semiontd.db");
        assertEquals(profile(sqlitePlayerId, "sqlite", 1510, 10L),
                new SQLiteRatingRepository(backupDatabase).findProfile(sqlitePlayerId).orElseThrow());
        assertEquals(ratingResult(ratingMatchId, 10L),
                new SQLiteRatingEventRepository(backupDatabase).findMatchResult(ratingMatchId).orElseThrow());
        assertEquals(1, countRows(backupDatabase, "applied_matches"));
        assertEquals(profile(fallbackPlayerId, "fallback", 1600, 20L),
                new FileRatingRepository(backupPath.resolve("ratings.json"))
                        .findProfile(fallbackPlayerId).orElseThrow());
        assertEquals(ratingResult(new MatchId(92L), 20L),
                new FileRatingEventRepository(backupPath.resolve("rating-events.json"))
                        .findMatchResult(new MatchId(92L)).orElseThrow());
    }

    @Test
    void fileOnlySoftResetPreservesRecoverableProfilesAndEvents() {
        UUID playerId = UUID.nameUUIDFromBytes("file-reset".getBytes());
        PlayerRatingProfile profile = profile(playerId, "file", 1520, 30L);
        RatingMatchResult result = ratingResult(new MatchId(93L), 30L);
        Path profileFile = tempDir.resolve("ratings.json");
        Path eventFile = tempDir.resolve("rating-events.json");
        new FileRatingRepository(profileFile).saveProfile(playerId, profile);
        new FileRatingEventRepository(eventFile).saveMatchResult(result);

        Path backup = PersistenceRatingBackup.reset(tempDir,
                new SemionPersistenceConfig(SemionPersistenceBackendType.FILE, "unused.db", "", "semiontd", false));

        assertFalse(Files.exists(profileFile));
        assertFalse(Files.exists(eventFile));
        assertFalse(Files.exists(tempDir.resolve("unused.db")));
        assertEquals(profile, new FileRatingRepository(backup.resolve("ratings.json"))
                .findProfile(playerId).orElseThrow());
        assertEquals(result, new FileRatingEventRepository(backup.resolve("rating-events.json"))
                .findMatchResult(result.matchId()).orElseThrow());
    }

    @Test
    void backupDirectoryFailureLeavesLiveRatingDataIntact() throws Exception {
        UUID playerId = UUID.nameUUIDFromBytes("backup-failure".getBytes());
        PlayerRatingProfile profile = profile(playerId, "preserved", 1530, 40L);
        RatingMatchResult result = ratingResult(new MatchId(94L), 40L);
        Path database = tempDir.resolve("semiontd.db");
        Path profileFile = tempDir.resolve("ratings.json");
        Path eventFile = tempDir.resolve("rating-events.json");
        new SQLiteRatingRepository(database).saveProfile(playerId, profile);
        new SQLiteRatingEventRepository(database).saveMatchResult(result);
        new SQLiteAppliedMatchRepository(database).markApplied(result.matchId(), "rating", 40L);
        new FileRatingRepository(profileFile).saveProfile(playerId, profile);
        new FileRatingEventRepository(eventFile).saveMatchResult(result);
        Files.writeString(tempDir.resolve("rating-backups"), "occupied");

        assertThrows(PersistenceException.class, () -> PersistenceRatingBackup.reset(
                tempDir, SemionPersistenceConfig.defaultConfig()));

        assertEquals(profile, new SQLiteRatingRepository(database).findProfile(playerId).orElseThrow());
        assertEquals(result, new SQLiteRatingEventRepository(database).findMatchResult(result.matchId()).orElseThrow());
        assertEquals(1, countRows(database, "applied_matches"));
        assertEquals(profile, new FileRatingRepository(profileFile).findProfile(playerId).orElseThrow());
        assertEquals(result, new FileRatingEventRepository(eventFile).findMatchResult(result.matchId()).orElseThrow());
    }

    private static PlayerRatingProfile profile(UUID playerId, String name, int elo, long updatedAtEpochMillis) {
        return new PlayerRatingProfile(
                playerId,
                name,
                RatingSystemId.ELO,
                1,
                1,
                1,
                0,
                elo,
                350.0,
                elo,
                new MatchId(updatedAtEpochMillis),
                updatedAtEpochMillis
        );
    }

    private static RatingMatchResult ratingResult(MatchId matchId, long endedAtEpochMillis) {
        return new RatingMatchResult(matchId, RatingSystemId.ELO, 1, endedAtEpochMillis, List.of());
    }

    private static int countRows(Path database, String table) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var results = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertTrue(results.next());
            return results.getInt(1);
        }
    }

    private static SemionPersistenceConfig requiredSqlite(Path sqlitePath) {
        return new SemionPersistenceConfig(
                SemionPersistenceBackendType.SQLITE,
                sqlitePath.toString(),
                "",
                "semiontd",
                true
        );
    }
}
