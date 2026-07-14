package kim.biryeong.semiontd.persistence;

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
import java.sql.Types;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.PlayerMatchStatsSnapshot;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamMatchResult;
import kim.biryeong.semiontd.statistics.JobStatisticsEntry;
import kim.biryeong.semiontd.statistics.JobStatisticsSnapshot;
import kim.biryeong.semiontd.statistics.JobStatisticsTotals;
import net.minecraft.resources.ResourceLocation;

public final class SQLiteJobStatisticsStore {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type FILE_HISTORY_TYPE = new TypeToken<Map<String, MatchResult>>() {
    }.getType();
    private static final String FACT_COLUMNS = """
            match_id, player_id, job_id, team_id, won, placement, final_round, cleared_round,
            started_at_epoch_millis, ended_at_epoch_millis,
            monster_kills, kill_minerals, summoned_monsters, final_income,
            own_lane_incoming_threat, own_lane_leaked_threat,
            sent_income_threat, income_attack_success_threat,
            own_lane_diamond_gain, assist_clear_diamond_gain, income_generated,
            assist_clear_threat, incoming_income_threat
            """;
    private static final String AGGREGATE_COLUMNS = """
            job_id, appearances, wins, placement_samples, placement_sum, final_round_sum,
            monster_kills, kill_minerals, summoned_monsters, final_income,
            own_lane_incoming_threat, own_lane_leaked_threat,
            sent_income_threat, income_attack_success_threat,
            own_lane_diamond_gain, assist_clear_diamond_gain, income_generated,
            assist_clear_threat, incoming_income_threat,
            first_match_at_epoch_millis, last_match_at_epoch_millis, updated_at_epoch_millis
            """;
    private static final String INSERT_FACT = "INSERT OR IGNORE INTO job_stat_participant_facts ("
            + FACT_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPSERT_HISTORY_FACT = "INSERT INTO job_stat_participant_facts ("
            + FACT_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT(match_id, player_id) DO UPDATE SET cleared_round = excluded.cleared_round";
    private static final String UPSERT_AGGREGATE = """
            INSERT INTO job_statistics (
            """ + AGGREGATE_COLUMNS + """
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(job_id) DO UPDATE SET
                appearances = job_statistics.appearances + excluded.appearances,
                wins = job_statistics.wins + excluded.wins,
                placement_samples = job_statistics.placement_samples + excluded.placement_samples,
                placement_sum = job_statistics.placement_sum + excluded.placement_sum,
                final_round_sum = job_statistics.final_round_sum + excluded.final_round_sum,
                monster_kills = job_statistics.monster_kills + excluded.monster_kills,
                kill_minerals = job_statistics.kill_minerals + excluded.kill_minerals,
                summoned_monsters = job_statistics.summoned_monsters + excluded.summoned_monsters,
                final_income = job_statistics.final_income + excluded.final_income,
                own_lane_incoming_threat = job_statistics.own_lane_incoming_threat + excluded.own_lane_incoming_threat,
                own_lane_leaked_threat = job_statistics.own_lane_leaked_threat + excluded.own_lane_leaked_threat,
                sent_income_threat = job_statistics.sent_income_threat + excluded.sent_income_threat,
                income_attack_success_threat = job_statistics.income_attack_success_threat + excluded.income_attack_success_threat,
                own_lane_diamond_gain = job_statistics.own_lane_diamond_gain + excluded.own_lane_diamond_gain,
                assist_clear_diamond_gain = job_statistics.assist_clear_diamond_gain + excluded.assist_clear_diamond_gain,
                income_generated = job_statistics.income_generated + excluded.income_generated,
                assist_clear_threat = job_statistics.assist_clear_threat + excluded.assist_clear_threat,
                incoming_income_threat = job_statistics.incoming_income_threat + excluded.incoming_income_threat,
                first_match_at_epoch_millis = MIN(job_statistics.first_match_at_epoch_millis, excluded.first_match_at_epoch_millis),
                last_match_at_epoch_millis = MAX(job_statistics.last_match_at_epoch_millis, excluded.last_match_at_epoch_millis),
                updated_at_epoch_millis = excluded.updated_at_epoch_millis
            """;
    private static final String UPSERT_ROUND_AGGREGATE = """
            INSERT INTO job_round_statistics (job_id, round_number, attempt_count, cleared_count)
            VALUES (?, ?, 1, ?)
            ON CONFLICT(job_id, round_number) DO UPDATE SET
                attempt_count = job_round_statistics.attempt_count + excluded.attempt_count,
                cleared_count = job_round_statistics.cleared_count + excluded.cleared_count
            """;
    private static final String INSERT_PARTICIPANT_ROUND = """
            INSERT OR REPLACE INTO job_stat_participant_rounds (
                match_id, player_id, round_number, cleared
            ) VALUES (?, ?, ?, ?)
            """;
    private static final String DELETE_PARTICIPANT_ROUNDS = """
            DELETE FROM job_stat_participant_rounds
            WHERE match_id = ? AND player_id = ?
            """;

    private final Path path;

    public SQLiteJobStatisticsStore(Path path) {
        this.path = path;
    }

    public JobStatisticsSnapshot rebuildFromHistory(Path sqliteHistoryPath, Path fileHistoryPath) {
        initialize();
        List<MatchResult> history = loadHistory(sqliteHistoryPath, fileHistoryPath);
        try (Connection connection = SQLiteSupport.connect(path)) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(UPSERT_HISTORY_FACT);
                 PreparedStatement deleteRounds = connection.prepareStatement(DELETE_PARTICIPANT_ROUNDS);
                 PreparedStatement insertRound = connection.prepareStatement(INSERT_PARTICIPANT_ROUND)) {
                for (MatchResult matchResult : history) {
                    for (ParticipantFact fact : participantFacts(matchResult)) {
                        bindFact(insert, fact);
                        insert.executeUpdate();
                        replaceParticipantRounds(deleteRounds, insertRound, fact);
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to backfill job statistics into SQLite " + path, exception);
        }
        rebuildAggregates();
        return loadSnapshot();
    }

    public JobStatisticsSnapshot ingest(MatchResult matchResult) {
        initialize();
        List<ParticipantFact> facts = participantFacts(matchResult);
        if (facts.isEmpty()) {
            return loadSnapshot();
        }

        long updatedAt = System.currentTimeMillis();
        try (Connection connection = SQLiteSupport.connect(path)) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(INSERT_FACT);
                 PreparedStatement aggregate = connection.prepareStatement(UPSERT_AGGREGATE);
                 PreparedStatement insertRound = connection.prepareStatement(INSERT_PARTICIPANT_ROUND);
                 PreparedStatement roundAggregate = connection.prepareStatement(UPSERT_ROUND_AGGREGATE)) {
                for (ParticipantFact fact : facts) {
                    bindFact(insert, fact);
                    if (insert.executeUpdate() == 0) {
                        continue;
                    }
                    bindAggregate(aggregate, fact, updatedAt);
                    aggregate.executeUpdate();
                    insertParticipantRounds(insertRound, fact);
                    incrementRoundAggregates(roundAggregate, fact);
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to update job statistics in SQLite " + path, exception);
        }
        return loadSnapshot();
    }

    public JobStatisticsSnapshot loadSnapshot() {
        initialize();
        try (Connection connection = SQLiteSupport.connect(path)) {
            long eligibleMatchCount;
            long participantAppearances;
            long firstMatchAt;
            long lastMatchAt;
            try (Statement statement = connection.createStatement();
                 ResultSet results = statement.executeQuery("""
                         SELECT COUNT(DISTINCT match_id), COUNT(*),
                                COALESCE(MIN(started_at_epoch_millis), 0),
                                COALESCE(MAX(ended_at_epoch_millis), 0)
                         FROM job_stat_participant_facts
                         """)) {
                results.next();
                eligibleMatchCount = results.getLong(1);
                participantAppearances = results.getLong(2);
                firstMatchAt = results.getLong(3);
                lastMatchAt = results.getLong(4);
            }

            Map<String, RoundCounts> roundCounts = loadRoundCounts(connection);
            ArrayList<JobStatisticsEntry> jobs = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet results = statement.executeQuery("SELECT " + AGGREGATE_COLUMNS
                         + " FROM job_statistics ORDER BY job_id")) {
                while (results.next()) {
                    jobs.add(readEntry(results, roundCounts.get(results.getString(1))));
                }
            }
            return new JobStatisticsSnapshot(
                    System.currentTimeMillis(),
                    eligibleMatchCount,
                    participantAppearances,
                    firstMatchAt,
                    lastMatchAt,
                    jobs
            );
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load job statistics from SQLite " + path, exception);
        }
    }

    private void initialize() {
        try (Connection connection = SQLiteSupport.connect(path);
             Statement statement = connection.createStatement()) {
            boolean existingRoundStatistics = tableExists(connection, "job_round_statistics");
            boolean legacyRoundStatistics = existingRoundStatistics
                    && !columnExists(connection, "job_round_statistics", "attempt_count");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS job_stat_participant_facts (
                        match_id INTEGER NOT NULL,
                        player_id TEXT NOT NULL,
                        job_id TEXT NOT NULL,
                        team_id TEXT NOT NULL,
                        won INTEGER NOT NULL,
                        placement INTEGER,
                        final_round INTEGER NOT NULL,
                        cleared_round INTEGER NOT NULL DEFAULT 0,
                        started_at_epoch_millis INTEGER NOT NULL,
                        ended_at_epoch_millis INTEGER NOT NULL,
                        monster_kills INTEGER NOT NULL,
                        kill_minerals INTEGER NOT NULL,
                        summoned_monsters INTEGER NOT NULL,
                        final_income INTEGER NOT NULL,
                        own_lane_incoming_threat REAL NOT NULL,
                        own_lane_leaked_threat REAL NOT NULL,
                        sent_income_threat REAL NOT NULL,
                        income_attack_success_threat REAL NOT NULL,
                        own_lane_diamond_gain INTEGER NOT NULL,
                        assist_clear_diamond_gain INTEGER NOT NULL,
                        income_generated INTEGER NOT NULL,
                        assist_clear_threat REAL NOT NULL,
                        incoming_income_threat REAL NOT NULL,
                        PRIMARY KEY (match_id, player_id)
                    )
                    """);
            ensureColumn(
                    connection,
                    "job_stat_participant_facts",
                    "cleared_round",
                    "INTEGER NOT NULL DEFAULT 0"
            );
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_job_stat_facts_job_id "
                    + "ON job_stat_participant_facts (job_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_job_stat_facts_ended_at "
                    + "ON job_stat_participant_facts (ended_at_epoch_millis)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_job_stat_facts_job_ended_at "
                    + "ON job_stat_participant_facts (job_id, ended_at_epoch_millis)");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS job_statistics (
                        job_id TEXT PRIMARY KEY,
                        appearances INTEGER NOT NULL,
                        wins INTEGER NOT NULL,
                        placement_samples INTEGER NOT NULL,
                        placement_sum INTEGER NOT NULL,
                        final_round_sum INTEGER NOT NULL,
                        monster_kills INTEGER NOT NULL,
                        kill_minerals INTEGER NOT NULL,
                        summoned_monsters INTEGER NOT NULL,
                        final_income INTEGER NOT NULL,
                        own_lane_incoming_threat REAL NOT NULL,
                        own_lane_leaked_threat REAL NOT NULL,
                        sent_income_threat REAL NOT NULL,
                        income_attack_success_threat REAL NOT NULL,
                        own_lane_diamond_gain INTEGER NOT NULL,
                        assist_clear_diamond_gain INTEGER NOT NULL,
                        income_generated INTEGER NOT NULL,
                        assist_clear_threat REAL NOT NULL,
                        incoming_income_threat REAL NOT NULL,
                        first_match_at_epoch_millis INTEGER NOT NULL,
                        last_match_at_epoch_millis INTEGER NOT NULL,
                        updated_at_epoch_millis INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS job_stat_participant_rounds (
                        match_id INTEGER NOT NULL,
                        player_id TEXT NOT NULL,
                        round_number INTEGER NOT NULL,
                        cleared INTEGER NOT NULL,
                        PRIMARY KEY (match_id, player_id, round_number),
                        CHECK (round_number BETWEEN 1 AND 40),
                        CHECK (cleared IN (0, 1))
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS job_round_statistics (
                        job_id TEXT NOT NULL,
                        round_number INTEGER NOT NULL,
                        attempt_count INTEGER NOT NULL DEFAULT 0,
                        cleared_count INTEGER NOT NULL,
                        PRIMARY KEY (job_id, round_number),
                        CHECK (round_number BETWEEN 1 AND 40)
                    )
                    """);
            ensureColumn(
                    connection,
                    "job_round_statistics",
                    "attempt_count",
                    "INTEGER NOT NULL DEFAULT 0"
            );
            if (legacyRoundStatistics) {
                statement.executeUpdate("DELETE FROM job_round_statistics");
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to initialize job statistics SQLite " + path, exception);
        }
    }

    private void rebuildAggregates() {
        long updatedAt = System.currentTimeMillis();
        try (Connection connection = SQLiteSupport.connect(path)) {
            connection.setAutoCommit(false);
            try (Statement clear = connection.createStatement();
                 PreparedStatement rebuild = connection.prepareStatement("""
                         INSERT INTO job_statistics (
                         """ + AGGREGATE_COLUMNS + """
                         )
                         SELECT job_id,
                                COUNT(*), COALESCE(SUM(won), 0), COUNT(placement), COALESCE(SUM(placement), 0),
                                COALESCE(SUM(final_round), 0),
                                COALESCE(SUM(monster_kills), 0), COALESCE(SUM(kill_minerals), 0),
                                COALESCE(SUM(summoned_monsters), 0), COALESCE(SUM(final_income), 0),
                                COALESCE(SUM(own_lane_incoming_threat), 0.0),
                                COALESCE(SUM(own_lane_leaked_threat), 0.0),
                                COALESCE(SUM(sent_income_threat), 0.0),
                                COALESCE(SUM(income_attack_success_threat), 0.0),
                                COALESCE(SUM(own_lane_diamond_gain), 0),
                                COALESCE(SUM(assist_clear_diamond_gain), 0),
                                COALESCE(SUM(income_generated), 0),
                                COALESCE(SUM(assist_clear_threat), 0.0),
                                COALESCE(SUM(incoming_income_threat), 0.0),
                                MIN(started_at_epoch_millis), MAX(ended_at_epoch_millis), ?
                         FROM job_stat_participant_facts
                         GROUP BY job_id
                         """);
                 PreparedStatement rebuildRounds = connection.prepareStatement("""
                         INSERT INTO job_round_statistics (
                             job_id, round_number, attempt_count, cleared_count
                         )
                         SELECT facts.job_id,
                                outcomes.round_number,
                                COUNT(*),
                                COALESCE(SUM(outcomes.cleared), 0)
                         FROM job_stat_participant_rounds AS outcomes
                         INNER JOIN job_stat_participant_facts AS facts
                                 ON facts.match_id = outcomes.match_id
                                AND facts.player_id = outcomes.player_id
                         GROUP BY facts.job_id, outcomes.round_number
                         """)) {
                clear.executeUpdate("DELETE FROM job_statistics");
                clear.executeUpdate("DELETE FROM job_round_statistics");
                rebuild.setLong(1, updatedAt);
                rebuild.executeUpdate();
                rebuildRounds.executeUpdate();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to rebuild job statistics in SQLite " + path, exception);
        }
    }

    private List<MatchResult> loadHistory(Path sqliteHistoryPath, Path fileHistoryPath) {
        LinkedHashMap<Long, MatchResult> history = new LinkedHashMap<>();
        loadSqliteHistory(sqliteHistoryPath, history);
        loadFileHistory(fileHistoryPath, history);
        return List.copyOf(history.values());
    }

    private void loadSqliteHistory(Path sourcePath, Map<Long, MatchResult> history) {
        if (sourcePath == null || Files.notExists(sourcePath) || sourcePath.equals(path)) {
            return;
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + sourcePath.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA query_only = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            if (!tableExists(connection, "match_results")) {
                return;
            }
            try (ResultSet results = statement.executeQuery("SELECT payload FROM match_results ORDER BY match_id")) {
                while (results.next()) {
                    addHistoryPayload(results.getString(1), history, sourcePath.toString());
                }
            }
        } catch (SQLException exception) {
            SemionTd.LOGGER.warn("Failed to read job-statistics history from SQLite {}.", sourcePath, exception);
        }
    }

    private void loadFileHistory(Path sourcePath, Map<Long, MatchResult> history) {
        if (sourcePath == null || Files.notExists(sourcePath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(sourcePath)) {
            Map<String, MatchResult> raw = GSON.fromJson(reader, FILE_HISTORY_TYPE);
            if (raw == null) {
                return;
            }
            for (MatchResult matchResult : raw.values()) {
                addHistory(matchResult, history);
            }
        } catch (IOException | RuntimeException exception) {
            SemionTd.LOGGER.warn("Failed to read job-statistics history from file {}.", sourcePath, exception);
        }
    }

    private void addHistoryPayload(String payload, Map<Long, MatchResult> history, String source) {
        try {
            addHistory(GSON.fromJson(payload, MatchResult.class), history);
        } catch (RuntimeException exception) {
            SemionTd.LOGGER.warn("Skipping invalid match-result payload from {}.", source, exception);
        }
    }

    private static void addHistory(MatchResult matchResult, Map<Long, MatchResult> history) {
        if (matchResult != null && matchResult.matchId() != null) {
            history.putIfAbsent(matchResult.matchId().value(), matchResult);
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1"
        )) {
            statement.setString(1, tableName);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private static boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (columns.next()) {
                if (columnName.equalsIgnoreCase(columns.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static List<ParticipantFact> participantFacts(MatchResult matchResult) {
        if (matchResult == null || matchResult.matchMode() != MatchMode.NORMAL) {
            return List.of();
        }
        Map<TeamId, TeamMatchResult> teamResults = new EnumMap<>(TeamId.class);
        for (TeamMatchResult teamResult : matchResult.teamResults()) {
            teamResults.putIfAbsent(teamResult.teamId(), teamResult);
        }

        ArrayList<ParticipantFact> facts = new ArrayList<>();
        for (MatchParticipantResult participant : matchResult.participants()) {
            String rawJobId = participant.jobId();
            ResourceLocation jobId = rawJobId == null ? null : ResourceLocation.tryParse(rawJobId);
            if (jobId == null) {
                continue;
            }
            TeamMatchResult teamResult = teamResults.get(participant.teamId());
            List<RoundOutcome> roundOutcomes = roundOutcomes(participant);
            facts.add(new ParticipantFact(
                    matchResult.matchId().value(),
                    participant.playerId().toString(),
                    jobId.toString(),
                    participant.teamId().name(),
                    participant.winner(),
                    teamResult == null ? null : teamResult.placement(),
                    matchResult.finalRound(),
                    lastClearedRound(roundOutcomes),
                    matchResult.startedAtEpochMillis(),
                    matchResult.endedAtEpochMillis(),
                    participant.stats(),
                    roundOutcomes
            ));
        }
        return facts;
    }

    private static List<RoundOutcome> roundOutcomes(MatchParticipantResult participant) {
        Set<Integer> clearedRounds = new HashSet<>(participant.clearedRounds());
        return participant.attemptedRounds().stream()
                .filter(round -> round <= JobStatisticsEntry.MAX_TRACKED_ROUND)
                .map(round -> new RoundOutcome(round, clearedRounds.contains(round)))
                .toList();
    }

    private static int lastClearedRound(List<RoundOutcome> roundOutcomes) {
        return roundOutcomes.stream()
                .filter(RoundOutcome::cleared)
                .mapToInt(RoundOutcome::round)
                .max()
                .orElse(0);
    }

    private static void bindFact(PreparedStatement statement, ParticipantFact fact) throws SQLException {
        PlayerMatchStatsSnapshot stats = fact.stats();
        statement.setLong(1, fact.matchId());
        statement.setString(2, fact.playerId());
        statement.setString(3, fact.jobId());
        statement.setString(4, fact.teamId());
        statement.setInt(5, fact.won() ? 1 : 0);
        if (fact.placement() == null) {
            statement.setNull(6, Types.INTEGER);
        } else {
            statement.setInt(6, fact.placement());
        }
        statement.setInt(7, fact.finalRound());
        statement.setInt(8, fact.clearedRound());
        statement.setLong(9, fact.startedAtEpochMillis());
        statement.setLong(10, fact.endedAtEpochMillis());
        statement.setLong(11, stats.monsterKills());
        statement.setLong(12, stats.killMinerals());
        statement.setLong(13, stats.summonedMonsters());
        statement.setLong(14, stats.finalIncome());
        statement.setDouble(15, stats.ownLaneIncomingThreat());
        statement.setDouble(16, stats.ownLaneLeakedThreat());
        statement.setDouble(17, stats.sentIncomeThreat());
        statement.setDouble(18, stats.incomeAttackSuccessThreat());
        statement.setLong(19, stats.ownLaneDiamondGain());
        statement.setLong(20, stats.assistClearDiamondGain());
        statement.setLong(21, stats.incomeGenerated());
        statement.setDouble(22, stats.assistClearThreat());
        statement.setDouble(23, stats.incomingIncomeThreat());
    }

    private static void bindAggregate(PreparedStatement statement, ParticipantFact fact, long updatedAt) throws SQLException {
        PlayerMatchStatsSnapshot stats = fact.stats();
        statement.setString(1, fact.jobId());
        statement.setLong(2, 1L);
        statement.setLong(3, fact.won() ? 1L : 0L);
        statement.setLong(4, fact.placement() == null ? 0L : 1L);
        statement.setLong(5, fact.placement() == null ? 0L : fact.placement());
        statement.setLong(6, fact.finalRound());
        statement.setLong(7, stats.monsterKills());
        statement.setLong(8, stats.killMinerals());
        statement.setLong(9, stats.summonedMonsters());
        statement.setLong(10, stats.finalIncome());
        statement.setDouble(11, stats.ownLaneIncomingThreat());
        statement.setDouble(12, stats.ownLaneLeakedThreat());
        statement.setDouble(13, stats.sentIncomeThreat());
        statement.setDouble(14, stats.incomeAttackSuccessThreat());
        statement.setLong(15, stats.ownLaneDiamondGain());
        statement.setLong(16, stats.assistClearDiamondGain());
        statement.setLong(17, stats.incomeGenerated());
        statement.setDouble(18, stats.assistClearThreat());
        statement.setDouble(19, stats.incomingIncomeThreat());
        statement.setLong(20, fact.startedAtEpochMillis());
        statement.setLong(21, fact.endedAtEpochMillis());
        statement.setLong(22, updatedAt);
    }

    private static void incrementRoundAggregates(PreparedStatement statement, ParticipantFact fact) throws SQLException {
        statement.clearBatch();
        for (RoundOutcome outcome : fact.roundOutcomes()) {
            statement.setString(1, fact.jobId());
            statement.setInt(2, outcome.round());
            statement.setInt(3, outcome.cleared() ? 1 : 0);
            statement.addBatch();
        }
        if (!fact.roundOutcomes().isEmpty()) {
            statement.executeBatch();
        }
    }

    private static void replaceParticipantRounds(
            PreparedStatement deleteStatement,
            PreparedStatement insertStatement,
            ParticipantFact fact
    ) throws SQLException {
        deleteStatement.setLong(1, fact.matchId());
        deleteStatement.setString(2, fact.playerId());
        deleteStatement.executeUpdate();
        insertParticipantRounds(insertStatement, fact);
    }

    private static void insertParticipantRounds(PreparedStatement statement, ParticipantFact fact) throws SQLException {
        for (RoundOutcome outcome : fact.roundOutcomes()) {
            statement.setLong(1, fact.matchId());
            statement.setString(2, fact.playerId());
            statement.setInt(3, outcome.round());
            statement.setInt(4, outcome.cleared() ? 1 : 0);
            statement.executeUpdate();
        }
    }

    private static Map<String, RoundCounts> loadRoundCounts(Connection connection) throws SQLException {
        LinkedHashMap<String, long[]> passCounts = new LinkedHashMap<>();
        LinkedHashMap<String, long[]> attemptCounts = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("""
                     SELECT job_id, round_number, attempt_count, cleared_count
                     FROM job_round_statistics
                     ORDER BY job_id, round_number
                     """)) {
            while (results.next()) {
                int round = results.getInt(2);
                if (round < 1 || round > JobStatisticsEntry.MAX_TRACKED_ROUND) {
                    continue;
                }
                String jobId = results.getString(1);
                attemptCounts.computeIfAbsent(jobId, ignored -> new long[JobStatisticsEntry.MAX_TRACKED_ROUND])
                        [round - 1] = results.getLong(3);
                passCounts.computeIfAbsent(jobId, ignored -> new long[JobStatisticsEntry.MAX_TRACKED_ROUND])
                        [round - 1] = results.getLong(4);
            }
        }

        LinkedHashMap<String, RoundCounts> counts = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> entry : attemptCounts.entrySet()) {
            counts.put(entry.getKey(), new RoundCounts(
                    asCountList(passCounts.get(entry.getKey())),
                    asCountList(entry.getValue())
            ));
        }
        return counts;
    }

    private static List<Long> asCountList(long[] values) {
        ArrayList<Long> counts = new ArrayList<>(JobStatisticsEntry.MAX_TRACKED_ROUND);
        for (long value : values) {
            counts.add(value);
        }
        return List.copyOf(counts);
    }

    private static JobStatisticsEntry readEntry(ResultSet results, RoundCounts roundCounts) throws SQLException {
        JobStatisticsTotals totals = new JobStatisticsTotals(
                results.getLong(7),
                results.getLong(8),
                results.getLong(9),
                results.getLong(10),
                results.getDouble(11),
                results.getDouble(12),
                results.getDouble(13),
                results.getDouble(14),
                results.getLong(15),
                results.getLong(16),
                results.getLong(17),
                results.getDouble(18),
                results.getDouble(19)
        );
        return new JobStatisticsEntry(
                results.getString(1),
                results.getLong(2),
                results.getLong(3),
                results.getLong(4),
                results.getLong(5),
                results.getLong(6),
                totals,
                results.getLong(20),
                results.getLong(21),
                results.getLong(22),
                roundCounts == null ? null : roundCounts.passCounts(),
                roundCounts == null ? null : roundCounts.attemptCounts()
        );
    }

    private static void ensureColumn(
            Connection connection,
            String tableName,
            String columnName,
            String columnDefinition
    ) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (columns.next()) {
                if (columnName.equalsIgnoreCase(columns.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
        }
    }

    private record ParticipantFact(
            long matchId,
            String playerId,
            String jobId,
            String teamId,
            boolean won,
            Integer placement,
            int finalRound,
            int clearedRound,
            long startedAtEpochMillis,
            long endedAtEpochMillis,
            PlayerMatchStatsSnapshot stats,
            List<RoundOutcome> roundOutcomes
    ) {
    }

    private record RoundOutcome(int round, boolean cleared) {
    }

    private record RoundCounts(
            List<Long> passCounts,
            List<Long> attemptCounts
    ) {
    }
}
