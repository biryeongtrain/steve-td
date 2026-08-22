package kim.biryeong.semiontd.buildguide;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.persistence.PersistenceException;
import kim.biryeong.semiontd.trait.TraitLoadoutSnapshot;

public final class BuildGuideStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type RAW_TYPE = new TypeToken<Map<String, BuildGuide>>() {
    }.getType();
    private static final Type ACTIONS_TYPE = new TypeToken<List<BuildAction>>() {
    }.getType();
    private static final String LEGACY_MIGRATION = "build_guides_json_v1";

    private final Path databasePath;
    private final Map<String, BuildGuide> inMemoryGuides = new LinkedHashMap<>();
    private final Map<String, String> inMemoryAutomaticCodes = new LinkedHashMap<>();

    public BuildGuideStore(Path databasePath) {
        this(databasePath, null);
    }

    public BuildGuideStore(Path databasePath, Path legacyJsonPath) {
        this.databasePath = databasePath;
        if (databasePath != null) {
            initialize(legacyJsonPath);
        }
    }

    public synchronized Optional<BuildGuide> find(String code) {
        String normalized = normalizeCode(code);
        if (databasePath == null) {
            return Optional.ofNullable(inMemoryGuides.get(normalized));
        }
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM build_guides WHERE code = ? LIMIT 1"
             )) {
            statement.setString(1, normalized);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(readGuide(results)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw failure("load build guide", exception);
        }
    }

    public synchronized Optional<BuildGuide> findAutomatic(MatchId matchId, UUID authorId) {
        if (matchId == null || authorId == null) {
            return Optional.empty();
        }
        if (databasePath == null) {
            return Optional.ofNullable(inMemoryAutomaticCodes.get(sourceKey(matchId, authorId)))
                    .flatMap(this::find);
        }
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM build_guides WHERE source_match_id = ? AND author_id = ? LIMIT 1"
             )) {
            statement.setLong(1, matchId.value());
            statement.setString(2, authorId.toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(readGuide(results)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw failure("load automatic build guide", exception);
        }
    }

    public synchronized boolean contains(String code) {
        return find(code).isPresent();
    }

    public synchronized BuildGuide put(BuildGuide guide) {
        return put(null, guide);
    }

    public synchronized BuildGuide putAutomatic(MatchId matchId, BuildGuide guide) {
        Optional<BuildGuide> existing = findAutomatic(matchId, guide.authorId());
        if (existing.isPresent()) {
            return existing.get();
        }
        BuildGuide saved = put(matchId, guide);
        if (databasePath == null) {
            inMemoryAutomaticCodes.put(sourceKey(matchId, guide.authorId()), saved.code());
        }
        return saved;
    }

    public synchronized boolean remove(String code) {
        String normalized = normalizeCode(code);
        if (databasePath == null) {
            boolean removed = inMemoryGuides.remove(normalized) != null;
            if (removed) {
                inMemoryAutomaticCodes.values().removeIf(normalized::equals);
            }
            return removed;
        }
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM build_guides WHERE code = ?")) {
            statement.setString(1, normalized);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw failure("delete build guide", exception);
        }
    }

    public synchronized List<BuildGuide> publicGuides() {
        if (databasePath == null) {
            return inMemoryGuides.values().stream()
                    .sorted(Comparator.comparingLong(BuildGuide::publishedAtEpochMillis).reversed())
                    .toList();
        }
        List<BuildGuide> guides = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM build_guides ORDER BY published_at_epoch_millis DESC"
             );
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                guides.add(readGuide(results));
            }
            return List.copyOf(guides);
        } catch (SQLException exception) {
            throw failure("list build guides", exception);
        }
    }

    static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String sourceKey(MatchId matchId, UUID authorId) {
        return matchId + ":" + authorId;
    }

    private BuildGuide put(MatchId matchId, BuildGuide guide) {
        if (databasePath == null) {
            inMemoryGuides.put(normalizeCode(guide.code()), guide);
            return guide;
        }
        try (Connection connection = connect()) {
            insert(connection, matchId, guide, false);
            return guide;
        } catch (SQLException exception) {
            throw failure("save build guide", exception);
        }
    }

    private void initialize(Path legacyJsonPath) {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS build_guides (
                        code TEXT PRIMARY KEY,
                        source_match_id INTEGER,
                        title TEXT NOT NULL,
                        author_id TEXT NOT NULL,
                        author_name TEXT NOT NULL,
                        job_id TEXT NOT NULL,
                        trait_loadout_json TEXT NOT NULL,
                        final_round INTEGER NOT NULL,
                        published_at_epoch_millis INTEGER NOT NULL,
                        visibility TEXT NOT NULL,
                        actions_json TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_build_guides_source
                    ON build_guides (source_match_id, author_id)
                    WHERE source_match_id IS NOT NULL
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS build_guide_migrations (
                        id TEXT PRIMARY KEY,
                        applied_at_epoch_millis INTEGER NOT NULL
                    )
                    """);
            migrateLegacyJson(connection, legacyJsonPath);
        } catch (SQLException exception) {
            throw failure("initialize build guide database", exception);
        }
    }

    private void migrateLegacyJson(Connection connection, Path legacyJsonPath) throws SQLException {
        if (migrationApplied(connection)) {
            return;
        }
        Map<String, BuildGuide> guides;
        try {
            guides = readLegacyGuides(legacyJsonPath);
        } catch (IOException | RuntimeException exception) {
            SemionTd.LOGGER.warn("Failed to migrate legacy build guide store {}.", legacyJsonPath, exception);
            return;
        }

        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (BuildGuide guide : guides.values()) {
                insert(connection, null, guide, true);
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO build_guide_migrations (id, applied_at_epoch_millis) VALUES (?, ?)"
            )) {
                statement.setString(1, LEGACY_MIGRATION);
                statement.setLong(2, System.currentTimeMillis());
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private boolean migrationApplied(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM build_guide_migrations WHERE id = ? LIMIT 1"
        )) {
            statement.setString(1, LEGACY_MIGRATION);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private static Map<String, BuildGuide> readLegacyGuides(Path path) throws IOException {
        if (path == null || Files.notExists(path)) {
            return Map.of();
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            Map<String, BuildGuide> raw = GSON.fromJson(reader, RAW_TYPE);
            if (raw == null || raw.isEmpty()) {
                return Map.of();
            }
            Map<String, BuildGuide> guides = new LinkedHashMap<>();
            for (Map.Entry<String, BuildGuide> entry : raw.entrySet()) {
                String code = normalizeCode(entry.getKey());
                if (!code.isBlank() && entry.getValue() != null) {
                    guides.put(code, entry.getValue());
                }
            }
            return guides;
        }
    }

    private static void insert(Connection connection, MatchId matchId, BuildGuide guide, boolean ignoreExisting)
            throws SQLException {
        String sql = (ignoreExisting ? "INSERT OR IGNORE" : "INSERT") + """
                 INTO build_guides (
                    code, source_match_id, title, author_id, author_name, job_id,
                    trait_loadout_json, final_round, published_at_epoch_millis, visibility, actions_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """ + (ignoreExisting ? "" : """
                 ON CONFLICT(code) DO UPDATE SET
                    source_match_id = coalesce(excluded.source_match_id, build_guides.source_match_id),
                    title = excluded.title,
                    author_id = excluded.author_id,
                    author_name = excluded.author_name,
                    job_id = excluded.job_id,
                    trait_loadout_json = excluded.trait_loadout_json,
                    final_round = excluded.final_round,
                    published_at_epoch_millis = excluded.published_at_epoch_millis,
                    visibility = excluded.visibility,
                    actions_json = excluded.actions_json
                """);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizeCode(guide.code()));
            if (matchId == null) {
                statement.setNull(2, java.sql.Types.BIGINT);
            } else {
                statement.setLong(2, matchId.value());
            }
            statement.setString(3, guide.title());
            statement.setString(4, guide.authorId().toString());
            statement.setString(5, guide.authorName());
            statement.setString(6, guide.jobId());
            statement.setString(7, GSON.toJson(guide.traitLoadout()));
            statement.setInt(8, guide.finalRound());
            statement.setLong(9, guide.publishedAtEpochMillis());
            statement.setString(10, guide.visibility());
            statement.setString(11, GSON.toJson(guide.actions(), ACTIONS_TYPE));
            statement.executeUpdate();
        }
    }

    private static BuildGuide readGuide(ResultSet results) throws SQLException {
        TraitLoadoutSnapshot traitLoadout = GSON.fromJson(
                results.getString("trait_loadout_json"),
                TraitLoadoutSnapshot.class
        );
        List<BuildAction> actions = GSON.fromJson(results.getString("actions_json"), ACTIONS_TYPE);
        return new BuildGuide(
                results.getString("code"),
                results.getString("title"),
                UUID.fromString(results.getString("author_id")),
                results.getString("author_name"),
                results.getString("job_id"),
                traitLoadout,
                results.getInt("final_round"),
                results.getLong("published_at_epoch_millis"),
                results.getString("visibility"),
                actions
        );
    }

    private Connection connect() throws SQLException {
        Path parent = databasePath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException exception) {
                throw new SQLException("failed to create sqlite directory", exception);
            }
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    private PersistenceException failure(String operation, SQLException exception) {
        return new PersistenceException("Failed to " + operation + " in SQLite " + databasePath, exception);
    }
}
