package kim.biryeong.semiontd.persistence;

import com.google.gson.Gson;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.report.BugReport;

/** Append-only reports. No user text is written to fallback/public logs on failure. */
public final class SQLiteBugReportRepository {
    private static final Gson GSON = new Gson();
    private final Path path;

    public record Summary(UUID id, long createdAtEpochMillis, String playerName, String content) {}

    public SQLiteBugReportRepository(Path path) {
        this.path = path;
        try (var connection = SQLiteSupport.connect(path); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS bug_reports (report_id TEXT PRIMARY KEY NOT NULL, "
                    + "created_at INTEGER NOT NULL, player_id TEXT NOT NULL, player_name TEXT NOT NULL, "
                    + "content TEXT NOT NULL, payload TEXT NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_bug_reports_created ON bug_reports(created_at DESC, report_id DESC)");
        } catch (SQLException exception) { throw new PersistenceException("Failed to initialize bug report storage", exception); }
    }

    public void save(BugReport report) {
        String payload = GSON.toJson(report);
        if (payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 4 * 1024 * 1024) {
            throw new IllegalArgumentException("신고 스냅샷이 저장 한도를 초과했습니다. 운영자에게 문의해 주세요.");
        }
        try (var connection = SQLiteSupport.connect(path); var statement = connection.prepareStatement(
                "INSERT INTO bug_reports(report_id, created_at, player_id, player_name, content, payload) VALUES(?,?,?,?,?,?)")) {
            statement.setString(1, report.id().toString());
            statement.setLong(2, report.createdAtEpochMillis());
            statement.setString(3, report.playerId().toString());
            statement.setString(4, report.playerName());
            statement.setString(5, report.content());
            statement.setString(6, payload);
            statement.executeUpdate();
        } catch (SQLException exception) { throw new PersistenceException("Failed to save bug report", exception); }
    }

    public List<Summary> list(int page) {
        if (page < 1 || page > 100_000) throw new IllegalArgumentException("페이지는 1~100,000입니다.");
        try (var connection = SQLiteSupport.connect(path); var statement = connection.prepareStatement(
                "SELECT report_id, created_at, player_name, content FROM bug_reports ORDER BY created_at DESC, report_id DESC LIMIT 10 OFFSET ?")) {
            statement.setInt(1, (page - 1) * 10);
            try (var rows = statement.executeQuery()) {
                var result = new ArrayList<Summary>();
                while (rows.next()) result.add(new Summary(UUID.fromString(rows.getString(1)), rows.getLong(2), rows.getString(3), rows.getString(4)));
                return List.copyOf(result);
            }
        } catch (SQLException exception) { throw new PersistenceException("Failed to list bug reports", exception); }
    }

    public Optional<BugReport> find(UUID id) {
        try (var connection = SQLiteSupport.connect(path); var statement = connection.prepareStatement(
                "SELECT payload FROM bug_reports WHERE report_id = ?")) {
            statement.setString(1, id.toString());
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(GSON.fromJson(rows.getString(1), BugReport.class)) : Optional.empty();
            }
        } catch (SQLException exception) { throw new PersistenceException("Failed to read bug report", exception); }
    }
}
