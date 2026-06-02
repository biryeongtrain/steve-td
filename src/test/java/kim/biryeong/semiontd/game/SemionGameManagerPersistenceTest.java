package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.persistence.FileRatingEventRepository;
import kim.biryeong.semiontd.persistence.FileRatingRepository;
import kim.biryeong.semiontd.persistence.PersistenceException;
import kim.biryeong.semiontd.persistence.RatingEventRepository;
import kim.biryeong.semiontd.persistence.RatingRepository;
import kim.biryeong.semiontd.persistence.SemionPersistenceBackendType;
import kim.biryeong.semiontd.persistence.SemionPersistenceConfig;
import kim.biryeong.semiontd.rating.PlayerRatingProfile;
import kim.biryeong.semiontd.rating.RatingMatchResult;
import kim.biryeong.semiontd.rating.RatingSystemId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SemionGameManagerPersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void requiredSqliteMatchResultInitializationFailureFailsFastInsteadOfFallingBack() throws Exception {
        Path sqlitePathAsDirectory = Files.createDirectory(tempDir.resolve("required-match-results.db"));
        SemionPersistenceConfig requiredSqlite = requiredSqlite(sqlitePathAsDirectory);

        assertThrows(PersistenceException.class, () -> SemionGameManager.createMatchResultRepository(
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

        assertThrows(PersistenceException.class, () -> SemionGameManager.createAppliedMatchRepository(
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

        assertThrows(PersistenceException.class, () -> SemionGameManager.createRatingRepository(
                requiredSqlite,
                sqlitePathAsDirectory,
                tempDir.resolve("ratings.json")
        ));
    }

    @Test
    void requiredSqliteRatingEventRepositoryInitializationFailureFailsFastInsteadOfFallingBack() throws Exception {
        Path sqlitePathAsDirectory = Files.createDirectory(tempDir.resolve("required-rating-events.db"));
        SemionPersistenceConfig requiredSqlite = requiredSqlite(sqlitePathAsDirectory);

        assertThrows(PersistenceException.class, () -> SemionGameManager.createRatingEventRepository(
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

        RatingRepository initialSqlite = SemionGameManager.createRatingRepository(
                new SemionPersistenceConfig(SemionPersistenceBackendType.SQLITE, sqlitePath.toString(), "", "semiontd", false),
                sqlitePath,
                tempDir.resolve("empty-ratings.json")
        );
        initialSqlite.saveProfile(conflictId, profile(conflictId, "sqliteOlder", 1400, 5L));

        RatingRepository recovered = SemionGameManager.createRatingRepository(
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

        RatingEventRepository initialSqlite = SemionGameManager.createRatingEventRepository(
                new SemionPersistenceConfig(SemionPersistenceBackendType.SQLITE, sqlitePath.toString(), "", "semiontd", false),
                sqlitePath,
                tempDir.resolve("empty-rating-events.json")
        );
        initialSqlite.saveMatchResult(ratingResult(conflictId, 150L));

        RatingEventRepository recovered = SemionGameManager.createRatingEventRepository(
                new SemionPersistenceConfig(SemionPersistenceBackendType.SQLITE, sqlitePath.toString(), "", "semiontd", false),
                sqlitePath,
                filePath
        );

        assertEquals(100L, recovered.findMatchResult(fallbackOnlyId).orElseThrow().appliedAtEpochMillis());
        assertEquals(150L, recovered.findMatchResult(conflictId).orElseThrow().appliedAtEpochMillis());
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
