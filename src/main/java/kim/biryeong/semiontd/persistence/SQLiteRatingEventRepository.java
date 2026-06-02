package kim.biryeong.semiontd.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.rating.RatingMatchResult;

public final class SQLiteRatingEventRepository implements RatingEventRepository {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Path path;

    public SQLiteRatingEventRepository(Path path) {
        this.path = path;
        initialize();
    }

    @Override
    public synchronized void saveMatchResult(RatingMatchResult ratingMatchResult) {
        try (Connection connection = SQLiteSupport.connect(path);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR IGNORE INTO rating_events (match_id, rating_system_id, rating_version, applied_at_epoch_millis, payload) VALUES (?, ?, ?, ?, ?)"
             )) {
            statement.setLong(1, ratingMatchResult.matchId().value());
            statement.setString(2, ratingMatchResult.ratingSystemId().name());
            statement.setInt(3, ratingMatchResult.ratingVersion());
            statement.setLong(4, ratingMatchResult.appliedAtEpochMillis());
            statement.setString(5, GSON.toJson(ratingMatchResult));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to save rating event to SQLite " + path, exception);
        }
    }

    @Override
    public synchronized Optional<RatingMatchResult> findMatchResult(MatchId matchId) {
        try (Connection connection = SQLiteSupport.connect(path);
             PreparedStatement statement = connection.prepareStatement("SELECT payload FROM rating_events WHERE match_id = ? LIMIT 1")) {
            statement.setLong(1, matchId.value());
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return Optional.empty();
                }
                return Optional.of(GSON.fromJson(results.getString(1), RatingMatchResult.class));
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load rating event from SQLite " + path, exception);
        }
    }

    @Override
    public synchronized Map<MatchId, RatingMatchResult> findAllMatchResults() {
        try (Connection connection = SQLiteSupport.connect(path);
             PreparedStatement statement = connection.prepareStatement("SELECT match_id, payload FROM rating_events ORDER BY match_id")) {
            Map<MatchId, RatingMatchResult> results = new LinkedHashMap<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    results.put(new MatchId(rows.getLong(1)), GSON.fromJson(rows.getString(2), RatingMatchResult.class));
                }
            }
            return Map.copyOf(results);
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load rating events from SQLite " + path, exception);
        }
    }

    private void initialize() {
        try (Connection connection = SQLiteSupport.connect(path);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS rating_events (
                        match_id INTEGER PRIMARY KEY,
                        rating_system_id TEXT NOT NULL,
                        rating_version INTEGER NOT NULL,
                        applied_at_epoch_millis INTEGER NOT NULL,
                        payload TEXT NOT NULL
                    )
                    """);
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to initialize SQLite rating event store " + path, exception);
        }
    }

}
