package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import kim.biryeong.semiontd.persistence.PersistenceException;
import kim.biryeong.semiontd.persistence.SemionPersistenceBackendType;
import kim.biryeong.semiontd.persistence.SemionPersistenceConfig;
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
