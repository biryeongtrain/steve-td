package kim.biryeong.semiontd.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import kim.biryeong.semiontd.game.MatchId;

public final class SQLiteAppliedMatchRepository implements AppliedMatchRepository {
    private final Path path;

    public SQLiteAppliedMatchRepository(Path path) {
        this.path = path;
        initialize();
    }

    @Override
    public synchronized boolean hasApplied(MatchId matchId, String subsystem) {
        try (Connection connection = SQLiteSupport.connect(path);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM applied_matches WHERE match_id = ? AND subsystem = ? LIMIT 1"
             )) {
            statement.setLong(1, matchId.value());
            statement.setString(2, normalizeSubsystem(subsystem));
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load applied match from SQLite " + path, exception);
        }
    }

    @Override
    public synchronized boolean markApplied(MatchId matchId, String subsystem, long appliedAtEpochMillis) {
        String normalizedSubsystem = normalizeSubsystem(subsystem);
        try (Connection connection = SQLiteSupport.connect(path);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR IGNORE INTO applied_matches (match_id, subsystem, applied_at_epoch_millis) VALUES (?, ?, ?)"
             )) {
            statement.setLong(1, matchId.value());
            statement.setString(2, normalizedSubsystem);
            statement.setLong(3, Math.max(0L, appliedAtEpochMillis));
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to save applied match to SQLite " + path, exception);
        }
    }

    private void initialize() {
        try (Connection connection = SQLiteSupport.connect(path);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS applied_matches (match_id INTEGER NOT NULL, subsystem TEXT NOT NULL, applied_at_epoch_millis INTEGER NOT NULL)");
            statement.executeUpdate("DELETE FROM applied_matches WHERE rowid NOT IN (SELECT MIN(rowid) FROM applied_matches GROUP BY match_id, subsystem)");
            statement.executeUpdate("DROP INDEX IF EXISTS idx_applied_matches_match_subsystem");
            statement.executeUpdate("CREATE UNIQUE INDEX idx_applied_matches_match_subsystem ON applied_matches (match_id, subsystem)");
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to initialize SQLite applied match store " + path, exception);
        }
    }

    private static String normalizeSubsystem(String subsystem) {
        String normalized = Objects.requireNonNull(subsystem, "subsystem").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("subsystem cannot be blank");
        }
        return normalized;
    }
}
