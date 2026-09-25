package kim.biryeong.semiontd.persistence;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.UUID;
import kim.biryeong.semiontd.report.BugReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SQLiteBugReportRepositoryTest {
    @TempDir Path directory;

    @Test void persistsImmutableEvidenceOrdersPagesAndRejectsOverwrite() {
        var snapshot = new JsonObject();
        snapshot.addProperty("schemaVersion", 1);
        snapshot.addProperty("attack", 123.5);
        var report = new BugReport(UUID.randomUUID(), 1, UUID.randomUUID(), "신고자", "<script> SQL ' 버프 오류", snapshot);
        snapshot.addProperty("attack", 0);
        report.snapshot().addProperty("attack", 999);
        var store = new SQLiteBugReportRepository(directory.resolve("reports.db"));
        store.save(report);
        for (int i = 2; i <= 12; i++) store.save(new BugReport(UUID.randomUUID(), i, report.playerId(), "다른 이름", "내용", snapshot));
        var reopened = new SQLiteBugReportRepository(directory.resolve("reports.db"));
        assertEquals(10, reopened.list(1).size());
        assertEquals(2, reopened.list(2).size());
        assertEquals(12, reopened.list(1).getFirst().createdAtEpochMillis());
        var restored = reopened.find(report.id()).orElseThrow();
        assertEquals(report.content(), restored.content());
        assertEquals(123.5, restored.snapshot().get("attack").getAsDouble());
        assertEquals(report.playerId(), restored.playerId());
        assertThrows(PersistenceException.class, () -> reopened.save(report));
        assertEquals(2, reopened.list(2).size());
        assertTrue(reopened.find(UUID.randomUUID()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> reopened.list(0));
    }

    @Test void invalidTextAndOversizedSnapshotsCannotBeAcknowledgedAsSaved() {
        for (String input : new String[]{" ", "x".repeat(1001), "hello\nworld", "§c공지"}) {
            assertThrows(IllegalArgumentException.class, () -> BugReport.validateContent(input));
        }
        assertEquals("공격력이 이상해요", BugReport.validateContent(" 공격력이 이상해요 "));
        var snapshot = new JsonObject(); snapshot.addProperty("data", "x".repeat(4 * 1024 * 1024));
        var report = new BugReport(UUID.randomUUID(), 1, UUID.randomUUID(), "name", "내용", snapshot);
        var repository = new SQLiteBugReportRepository(directory.resolve("reports.db"));
        assertThrows(IllegalArgumentException.class, () -> repository.save(report));
        assertTrue(repository.list(1).isEmpty());
        assertThrows(PersistenceException.class, () -> new SQLiteBugReportRepository(directory));
    }
}
