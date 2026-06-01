package kim.biryeong.semiontd.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.game.MatchResult;

public final class SQLiteMatchResultRepository implements MatchResultRepository {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Path path;

    public SQLiteMatchResultRepository(Path path) {
        this.path = path;
        initialize();
    }

    @Override
    public synchronized void saveMatchResult(MatchResult matchResult) {
        try (Connection connection = SQLiteSupport.connect(path)) {
            if (exists(connection, matchResult.matchId())) {
                return;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO match_results (match_id, payload) VALUES (?, ?)"
            )) {
                statement.setLong(1, matchResult.matchId().value());
                statement.setString(2, GSON.toJson(matchResult));
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to save match result to SQLite " + path, exception);
        }
    }

    @Override
    public synchronized Optional<MatchResult> findMatchResult(MatchId matchId) {
        try (Connection connection = SQLiteSupport.connect(path);
             PreparedStatement statement = connection.prepareStatement("SELECT payload FROM match_results WHERE match_id = ? LIMIT 1")) {
            statement.setLong(1, matchId.value());
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return Optional.empty();
                }
                return Optional.of(GSON.fromJson(results.getString(1), MatchResult.class));
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load match result from SQLite " + path, exception);
        }
    }

    private void initialize() {
        try (Connection connection = SQLiteSupport.connect(path);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS match_results (match_id INTEGER, payload TEXT)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_match_results_match_id ON match_results (match_id)");
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to initialize SQLite match result store " + path, exception);
        }
    }

    private boolean exists(Connection connection, MatchId matchId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM match_results WHERE match_id = ? LIMIT 1")) {
            statement.setLong(1, matchId.value());
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }
}
