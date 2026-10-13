package kim.biryeong.semiontd.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class PersistenceJobStatisticsSchemaTest {
    @Test
    void initializesIdempotentlyWithoutChangingStoredRowsAndReadsEachMigrationTableOnce() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            AtomicInteger schemaReads = new AtomicInteger();
            Connection counted = countingConnection(connection, schemaReads);
            PersistenceJobStatisticsSchema.initialize(counted);
            assertEquals(4, schemaReads.get());
            List<String> before = schema(connection);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO job_round_statistics VALUES ('semion-td:villager', 1, 3, 2)");
            }
            schemaReads.set(0);
            PersistenceJobStatisticsSchema.initialize(counted);
            assertEquals(5, schemaReads.get());
            assertEquals(before, schema(connection));
            try (Statement statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT attempt_count, cleared_count FROM job_round_statistics")) {
                assertTrue(rows.next());
                assertEquals(3, rows.getInt(1));
                assertEquals(2, rows.getInt(2));
                assertFalse(rows.next());
            }
        }
    }

    @Test
    void rechecksExternalSchemaChangesAndMigratesLegacyRoundCounts() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement statement = connection.createStatement()) {
            PersistenceJobStatisticsSchema.initialize(connection);
            statement.executeUpdate("ALTER TABLE job_stat_participant_facts DROP COLUMN augment_telemetry");
            statement.executeUpdate("ALTER TABLE job_stat_participant_round_metrics DROP COLUMN augment_economy_metrics");
            statement.executeUpdate("DROP TABLE job_round_statistics");
            statement.executeUpdate("CREATE TABLE job_round_statistics (job_id TEXT NOT NULL, round_number INTEGER NOT NULL, "
                    + "cleared_count INTEGER NOT NULL, PRIMARY KEY (job_id, round_number))");
            statement.executeUpdate("INSERT INTO job_round_statistics VALUES ('semion-td:villager', 1, 2)");
            PersistenceJobStatisticsSchema.initialize(connection);
            try (var results = statement.executeQuery("SELECT augment_telemetry FROM job_stat_participant_facts")) {
                assertFalse(results.next());
            }
            try (var results = statement.executeQuery("SELECT augment_economy_metrics FROM job_stat_participant_round_metrics")) {
                assertFalse(results.next());
            }
            try (var results = statement.executeQuery("SELECT attempt_count FROM job_round_statistics")) {
                assertFalse(results.next());
            }
            statement.executeUpdate("INSERT INTO job_round_statistics (job_id, round_number, cleared_count) "
                    + "VALUES ('semion-td:villager', 1, 2)");
            try (var results = statement.executeQuery("SELECT attempt_count FROM job_round_statistics")) {
                assertTrue(results.next());
                assertEquals(0, results.getInt(1));
            }
        }
    }

    @Test
    void migrationFailurePropagatesWithoutDeletingExistingRoundCounts() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement statement = connection.createStatement()) {
            PersistenceJobStatisticsSchema.initialize(connection);
            statement.executeUpdate("INSERT INTO job_round_statistics VALUES ('semion-td:villager', 1, 3, 2)");
            statement.executeUpdate("ALTER TABLE job_stat_participant_facts DROP COLUMN augment_telemetry");
            statement.executeUpdate("PRAGMA query_only = ON");
            assertThrows(SQLException.class, () -> PersistenceJobStatisticsSchema.initialize(connection));
            try (var results = statement.executeQuery("SELECT attempt_count, cleared_count FROM job_round_statistics")) {
                assertTrue(results.next());
                assertEquals(3, results.getInt(1));
                assertEquals(2, results.getInt(2));
            }
        }
    }

    private static List<String> schema(Connection connection) throws SQLException {
        List<String> entries = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             var results = statement.executeQuery("SELECT type, name, sql FROM sqlite_master ORDER BY type, name")) {
            while (results.next()) {
                entries.add(results.getString(1) + "|" + results.getString(2) + "|" + results.getString(3));
            }
        }
        return entries;
    }

    private static Connection countingConnection(Connection delegate, AtomicInteger schemaReads) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(delegate, args);
                        if (!method.getName().equals("createStatement")) {
                            return result;
                        }
                        Statement statement = (Statement) result;
                        return Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[]{Statement.class},
                                (statementProxy, statementMethod, statementArgs) -> {
                                    if (statementMethod.getName().equals("executeQuery") && statementArgs != null
                                            && statementArgs[0] instanceof String sql && sql.startsWith("PRAGMA table_info")) {
                                        schemaReads.incrementAndGet();
                                    }
                                    try {
                                        return statementMethod.invoke(statement, statementArgs);
                                    } catch (InvocationTargetException exception) {
                                        throw exception.getCause();
                                    }
                                });
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }
}
