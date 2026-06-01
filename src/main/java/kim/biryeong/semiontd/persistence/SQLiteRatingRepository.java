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
import java.util.UUID;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.rating.PlayerRatingProfile;

public final class SQLiteRatingRepository implements RatingRepository {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Path path;

    public SQLiteRatingRepository(Path path) {
        this.path = path;
        initialize();
    }

    @Override
    public synchronized Optional<PlayerRatingProfile> findProfile(UUID playerId) {
        try (Connection connection = SQLiteSupport.connect(path);
             PreparedStatement statement = connection.prepareStatement("SELECT payload FROM rating_profiles WHERE player_id = ? LIMIT 1")) {
            statement.setString(1, playerId.toString());
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return Optional.empty();
                }
                return Optional.of(GSON.fromJson(results.getString(1), PlayerRatingProfile.class));
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load rating profile from SQLite " + path, exception);
        }
    }

    @Override
    public synchronized PlayerRatingProfile saveProfile(UUID playerId, PlayerRatingProfile profile) {
        try (Connection connection = SQLiteSupport.connect(path);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO rating_profiles (
                         player_id,
                         last_known_name,
                         rating_system_id,
                         rating_version,
                         games_played,
                         wins,
                         losses,
                         mu,
                         sigma,
                         display_elo,
                         last_updated_match_id,
                         updated_at_epoch_millis,
                         payload
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(player_id) DO UPDATE SET
                         last_known_name = excluded.last_known_name,
                         rating_system_id = excluded.rating_system_id,
                         rating_version = excluded.rating_version,
                         games_played = excluded.games_played,
                         wins = excluded.wins,
                         losses = excluded.losses,
                         mu = excluded.mu,
                         sigma = excluded.sigma,
                         display_elo = excluded.display_elo,
                         last_updated_match_id = excluded.last_updated_match_id,
                         updated_at_epoch_millis = excluded.updated_at_epoch_millis,
                         payload = excluded.payload
                     """)) {
            bindProfile(statement, playerId, profile);
            statement.executeUpdate();
            return profile;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to save rating profile to SQLite " + path, exception);
        }
    }

    @Override
    public synchronized Map<UUID, PlayerRatingProfile> findAllProfiles() {
        try (Connection connection = SQLiteSupport.connect(path);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_id, payload FROM rating_profiles ORDER BY display_elo DESC, games_played DESC, updated_at_epoch_millis DESC, last_known_name ASC"
             );
             ResultSet results = statement.executeQuery()) {
            Map<UUID, PlayerRatingProfile> profiles = new LinkedHashMap<>();
            while (results.next()) {
                profiles.put(UUID.fromString(results.getString(1)), GSON.fromJson(results.getString(2), PlayerRatingProfile.class));
            }
            return profiles;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load rating profiles from SQLite " + path, exception);
        }
    }

    private void initialize() {
        try (Connection connection = SQLiteSupport.connect(path);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS rating_profiles (
                        player_id TEXT PRIMARY KEY,
                        last_known_name TEXT NOT NULL,
                        rating_system_id TEXT NOT NULL,
                        rating_version INTEGER NOT NULL,
                        games_played INTEGER NOT NULL,
                        wins INTEGER NOT NULL,
                        losses INTEGER NOT NULL,
                        mu REAL NOT NULL,
                        sigma REAL NOT NULL,
                        display_elo INTEGER NOT NULL,
                        last_updated_match_id INTEGER,
                        updated_at_epoch_millis INTEGER NOT NULL,
                        payload TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_rating_profiles_leaderboard ON rating_profiles (display_elo DESC, games_played DESC, updated_at_epoch_millis DESC, last_known_name ASC)");
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to initialize SQLite rating profile store " + path, exception);
        }
    }

    private static void bindProfile(PreparedStatement statement, UUID playerId, PlayerRatingProfile profile) throws SQLException {
        statement.setString(1, playerId.toString());
        statement.setString(2, profile.lastKnownName());
        statement.setString(3, profile.ratingSystemId().name());
        statement.setInt(4, profile.ratingVersion());
        statement.setInt(5, profile.gamesPlayed());
        statement.setInt(6, profile.wins());
        statement.setInt(7, profile.losses());
        statement.setDouble(8, profile.mu());
        statement.setDouble(9, profile.sigma());
        statement.setInt(10, profile.displayElo());
        MatchId lastUpdatedMatchId = profile.lastUpdatedMatchId();
        if (lastUpdatedMatchId == null) {
            statement.setObject(11, null);
        } else {
            statement.setLong(11, lastUpdatedMatchId.value());
        }
        statement.setLong(12, profile.updatedAtEpochMillis());
        statement.setString(13, GSON.toJson(profile));
    }
}
