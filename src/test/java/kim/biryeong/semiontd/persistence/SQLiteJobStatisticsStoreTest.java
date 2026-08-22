package kim.biryeong.semiontd.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.MatchResultGroup;
import kim.biryeong.semiontd.game.PlayerMatchStatsSnapshot;
import kim.biryeong.semiontd.game.PlayerRoundMetricsSnapshot;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamMatchResult;
import kim.biryeong.semiontd.game.TowerCompositionEntry;
import kim.biryeong.semiontd.game.TowerRoundMetricsSnapshot;
import kim.biryeong.semiontd.statistics.JobStatisticsEntry;
import kim.biryeong.semiontd.statistics.JobStatisticsSnapshot;
import kim.biryeong.semiontd.statistics.TraitCombinationStatisticsEntry;
import kim.biryeong.semiontd.statistics.TraitTowerStatisticsEntry;
import kim.biryeong.semiontd.trait.TraitLoadoutSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SQLiteJobStatisticsStoreTest {
    private static final String VILLAGER = "semion-td:villager";
    private static final String NETHER = "semion-td:nether";

    @TempDir
    Path tempDir;

    @Test
    void aggregatesNormalMatchesAndIgnoresDuplicateParticipants() {
        SQLiteJobStatisticsStore store = new SQLiteJobStatisticsStore(tempDir.resolve("job-statistics.db"));
        MatchResult first = twoPlayerResult(
                1L,
                10,
                participant("villager-one", TeamId.RED, true, VILLAGER, statsA(), rounds(1, 10), rounds(1, 10)),
                participant("nether-one", TeamId.BLUE, false, NETHER, PlayerMatchStatsSnapshot.empty()),
                MatchMode.NORMAL
        );
        MatchResult second = twoPlayerResult(
                2L,
                20,
                participant("nether-two", TeamId.RED, true, NETHER, PlayerMatchStatsSnapshot.empty()),
                participant("villager-two", TeamId.BLUE, false, VILLAGER, statsB(), rounds(1, 20), rounds(1, 19)),
                MatchMode.NORMAL
        );

        store.ingest(first);
        JobStatisticsSnapshot snapshot = store.ingest(second);
        snapshot = store.ingest(first);

        assertEquals(2L, snapshot.eligibleMatchCount());
        assertEquals(4L, snapshot.participantAppearances());
        JobStatisticsEntry villager = entry(snapshot, VILLAGER);
        assertEquals(2L, villager.appearances());
        assertEquals(1L, villager.wins());
        assertEquals(3L, villager.placementSum());
        assertEquals(40L, villager.totals().monsterKills());
        assertEquals(60L, villager.totals().killMinerals());
        assertEquals(8L, villager.totals().summonedMonsters());
        assertEquals(300L, villager.totals().finalIncome());
        assertEquals(20.0, villager.averageValue(villager.totals().monsterKills()).orElseThrow(), 0.0001);
        assertEquals(0.5, villager.winRate().orElseThrow(), 0.0001);
        assertEquals(1.5, villager.averagePlacement().orElseThrow(), 0.0001);
        assertEquals(15.0, villager.averageFinalRound().orElseThrow(), 0.0001);
        assertEquals(0.75, villager.defenseSuccessRate().orElseThrow(), 0.0001);
        assertEquals(0.35, villager.incomeAttackSuccessRate().orElseThrow(), 0.0001);
        assertEquals(0.5, snapshot.selectionRate(villager).orElseThrow(), 0.0001);
        assertEquals(2L, villager.roundPassCount(10));
        assertEquals(1L, villager.roundPassCount(19));
        assertEquals(0L, villager.roundPassCount(20));
        assertEquals(2L, villager.roundAttemptCount(10));
        assertEquals(1L, villager.roundAttemptCount(19));
        assertEquals(1L, villager.roundAttemptCount(20));
        assertEquals(1.0, villager.roundPassRate(10).orElseThrow(), 0.0001);
        assertEquals(1.0, villager.roundPassRate(19).orElseThrow(), 0.0001);
        assertEquals(0.0, villager.roundPassRate(20).orElseThrow(), 0.0001);
    }

    @Test
    void countsRoundPassesPerBuilderInsteadOfTeamSurvival() {
        SQLiteJobStatisticsStore store = new SQLiteJobStatisticsStore(tempDir.resolve("job-statistics.db"));
        MatchParticipantResult clearedBuilder = participant(
                "same-team-cleared",
                TeamId.RED,
                true,
                VILLAGER,
                PlayerMatchStatsSnapshot.empty(),
                List.of(1, 2),
                List.of(1, 2)
        );
        MatchParticipantResult brokenBuilder = participant(
                "same-team-broken",
                TeamId.RED,
                true,
                VILLAGER,
                PlayerMatchStatsSnapshot.empty(),
                List.of(1, 2),
                List.of(1)
        );
        MatchResult result = new MatchResult(
                new MatchId(3L),
                3_000L,
                3_500L,
                List.of(clearedBuilder, brokenBuilder),
                Set.of(),
                Set.of(TeamId.RED),
                List.of(teamResult(TeamId.RED, 1, true, -1)),
                2,
                MatchMode.NORMAL
        );

        JobStatisticsEntry villager = entry(store.ingest(result), VILLAGER);

        assertEquals(2L, villager.roundAttemptCount(2));
        assertEquals(1L, villager.roundPassCount(2));
        assertEquals(0.5, villager.roundPassRate(2).orElseThrow(), 0.0001);
        assertTrue(villager.roundPassRate(3).isEmpty());
    }

    @Test
    void tracksTraitCombinationRoundsAndFinalTowerCompositionOnce() {
        SQLiteJobStatisticsStore store = new SQLiteJobStatisticsStore(tempDir.resolve("job-statistics.db"));
        TraitLoadoutSnapshot loadout = new TraitLoadoutSnapshot(
                "semion-td:test_economy",
                2,
                "semion-td:test_defense",
                1
        );
        MatchParticipantResult villager = new MatchParticipantResult(
                UUID.nameUUIDFromBytes("trait-villager".getBytes(StandardCharsets.UTF_8)),
                "trait-villager",
                TeamId.RED,
                true,
                PlayerMatchStatsSnapshot.empty(),
                VILLAGER,
                rounds(1, 20),
                rounds(1, 19),
                loadout,
                List.of(
                        new TowerCompositionEntry("semion-td:goat", 1, 2),
                        new TowerCompositionEntry("semion-td:archer", 2, 1)
                )
        );
        MatchResult result = twoPlayerResult(
                4L,
                20,
                villager,
                participant("trait-nether", TeamId.BLUE, false, NETHER, PlayerMatchStatsSnapshot.empty()),
                MatchMode.NORMAL
        );

        store.ingest(result);
        JobStatisticsSnapshot snapshot = store.ingest(result);

        TraitCombinationStatisticsEntry combination = snapshot.traitCombinationsForJob(VILLAGER).getFirst();
        assertEquals(1, snapshot.traitCombinationsForJob(VILLAGER).size());
        assertEquals("semion-td:test_economy", combination.primaryTraitId());
        assertEquals(2, combination.primaryTraitVersion());
        assertEquals("semion-td:test_defense", combination.secondaryTraitId());
        assertEquals(1, combination.secondaryTraitVersion());
        assertEquals(1L, combination.appearances());
        assertEquals(1L, combination.wins());
        assertEquals(1.0, combination.selectionRate(1L).orElseThrow(), 0.0001);
        assertEquals(1.0, combination.averagePlacement().orElseThrow(), 0.0001);
        assertEquals(20.0, combination.averageFinalRound().orElseThrow(), 0.0001);
        assertEquals(1.0, combination.roundPassRate(19).orElseThrow(), 0.0001);
        assertEquals(0.0, combination.roundPassRate(20).orElseThrow(), 0.0001);

        TraitTowerStatisticsEntry goat = combination.finalTowers().stream()
                .filter(tower -> tower.towerTypeId().equals("semion-td:goat"))
                .findFirst()
                .orElseThrow();
        assertEquals(1, goat.tier());
        assertEquals(1L, goat.participantAppearances());
        assertEquals(2L, goat.totalCount());
        assertEquals(2.0, goat.averageCount(combination.appearances()).orElseThrow(), 0.0001);
    }

    @Test
    void excludesLegacyAndTestMatchesAndSkipsOnlyInvalidNormalParticipants() {
        SQLiteJobStatisticsStore store = new SQLiteJobStatisticsStore(tempDir.resolve("job-statistics.db"));
        store.ingest(singlePlayerResult(10L, MatchMode.TEST, VILLAGER));
        store.ingest(singlePlayerResult(11L, null, VILLAGER));

        MatchResult mixed = twoPlayerResult(
                12L,
                8,
                participant("invalid", TeamId.RED, true, "INVALID JOB ID", PlayerMatchStatsSnapshot.empty()),
                participant(
                        "removed",
                        TeamId.BLUE,
                        false,
                        "external:removed",
                        PlayerMatchStatsSnapshot.empty(),
                        rounds(1, 8),
                        rounds(1, 7)
                ),
                MatchMode.NORMAL
        );
        JobStatisticsSnapshot snapshot = store.ingest(mixed);

        assertEquals(1L, snapshot.eligibleMatchCount());
        assertEquals(1L, snapshot.participantAppearances());
        JobStatisticsEntry removed = entry(snapshot, "external:removed");
        assertEquals(1L, removed.appearances());
        assertEquals(1L, removed.roundPassCount(7));
        assertEquals(0L, removed.roundPassCount(8));
    }

    @Test
    void rebuildMergesPrimaryAndFallbackHistoryAndRepairsAggregateTable() throws Exception {
        Path sourceDatabase = tempDir.resolve("semiontd.db");
        Path sourceFile = tempDir.resolve("match-results.json");
        MatchResult primary = singlePlayerResult(20L, MatchMode.NORMAL, VILLAGER);
        MatchResult fallback = singlePlayerResult(21L, MatchMode.NORMAL, NETHER);
        MatchResult legacy = singlePlayerResult(22L, null, VILLAGER);

        SQLiteMatchResultRepository sqlite = new SQLiteMatchResultRepository(sourceDatabase);
        sqlite.saveMatchResult(primary);
        sqlite.saveMatchResult(legacy);
        FileMatchResultRepository file = new FileMatchResultRepository(sourceFile);
        file.saveMatchResult(primary);
        file.saveMatchResult(fallback);

        Path statisticsDatabase = tempDir.resolve("job-statistics.db");
        SQLiteJobStatisticsStore store = new SQLiteJobStatisticsStore(statisticsDatabase);
        JobStatisticsSnapshot snapshot = store.rebuildFromHistory(sourceDatabase, sourceFile);
        assertEquals(2L, snapshot.eligibleMatchCount());
        assertEquals(2L, snapshot.participantAppearances());

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + statisticsDatabase.toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM job_statistics");
        }
        JobStatisticsSnapshot repaired = store.rebuildFromHistory(null, null);
        assertEquals(2L, repaired.jobs().size());
        assertEquals(1L, entry(repaired, VILLAGER).appearances());
        assertEquals(1L, entry(repaired, NETHER).appearances());
    }

    @Test
    void migratesExistingFactSchemaAndRebuildsRoundStatistics() throws Exception {
        Path sourceDatabase = tempDir.resolve("semiontd.db");
        MatchResult matchResult = singlePlayerResult(25L, MatchMode.NORMAL, VILLAGER, 45);
        new SQLiteMatchResultRepository(sourceDatabase).saveMatchResult(matchResult);

        Path statisticsDatabase = tempDir.resolve("job-statistics.db");
        SQLiteJobStatisticsStore store = new SQLiteJobStatisticsStore(statisticsDatabase);
        store.ingest(matchResult);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + statisticsDatabase.toAbsolutePath());
             var statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE job_round_statistics DROP COLUMN attempt_count");
            statement.executeUpdate("ALTER TABLE job_stat_participant_facts DROP COLUMN cleared_round");
        }

        JobStatisticsSnapshot migrated = store.rebuildFromHistory(sourceDatabase, null);
        JobStatisticsEntry villager = entry(migrated, VILLAGER);
        assertEquals(1L, villager.roundPassCount(40));
        assertEquals(1L, villager.roundAttemptCount(40));
        assertEquals(1.0, villager.roundPassRate(40).orElseThrow(), 0.0001);

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + statisticsDatabase.toAbsolutePath());
             var statement = connection.createStatement();
             var columns = statement.executeQuery("PRAGMA table_info(job_stat_participant_facts)")) {
            boolean clearedRoundPresent = false;
            while (columns.next()) {
                clearedRoundPresent |= "cleared_round".equals(columns.getString("name"));
            }
            assertTrue(clearedRoundPresent);
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + statisticsDatabase.toAbsolutePath());
             var statement = connection.createStatement();
             var columns = statement.executeQuery("PRAGMA table_info(job_round_statistics)")) {
            boolean attemptCountPresent = false;
            while (columns.next()) {
                attemptCountPresent |= "attempt_count".equals(columns.getString("name"));
            }
            assertTrue(attemptCountPresent);
        }
    }

    @Test
    void storesCatalogVersionOnParticipantFacts() throws Exception {
        MatchResult base = singlePlayerResult(26L, MatchMode.NORMAL, VILLAGER);
        String version = "a".repeat(64);
        MatchResult versioned = new MatchResult(
                base.matchId(),
                base.startedAtEpochMillis(),
                base.endedAtEpochMillis(),
                base.participants(),
                base.spectatorIds(),
                base.winningTeams(),
                base.teamResults(),
                base.finalRound(),
                base.matchMode(),
                version
        );
        Path database = tempDir.resolve("catalog-version-statistics.db");

        new SQLiteJobStatisticsStore(database).ingest(versioned);

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT catalog_version FROM job_stat_participant_facts")) {
            assertTrue(result.next());
            assertEquals(version, result.getString(1));
        }
    }

    @Test
    void oldJsonWithoutJobAndModeLoadsButDoesNotEnterStatistics() {
        MatchResult current = singlePlayerResult(30L, MatchMode.NORMAL, VILLAGER);
        Gson gson = new Gson();
        JsonObject json = JsonParser.parseString(gson.toJson(current)).getAsJsonObject();
        json.remove("matchMode");
        json.getAsJsonArray("participants").get(0).getAsJsonObject().remove("jobId");
        json.getAsJsonArray("participants").get(0).getAsJsonObject().remove("attemptedRounds");
        json.getAsJsonArray("participants").get(0).getAsJsonObject().remove("clearedRounds");
        json.getAsJsonArray("participants").get(0).getAsJsonObject().remove("traitLoadout");
        json.getAsJsonArray("participants").get(0).getAsJsonObject().remove("finalTowerComposition");
        json.getAsJsonArray("participants").get(0).getAsJsonObject().remove("buildActions");
        json.getAsJsonArray("participants").get(0).getAsJsonObject().remove("roundMetrics");
        json.remove("catalogVersion");

        MatchResult legacy = gson.fromJson(json, MatchResult.class);
        assertNull(legacy.matchMode());
        assertNull(legacy.participants().getFirst().jobId());
        assertEquals(List.of(), legacy.participants().getFirst().attemptedRounds());
        assertEquals(List.of(), legacy.participants().getFirst().clearedRounds());
        assertEquals(TraitLoadoutSnapshot.none(), legacy.participants().getFirst().traitLoadout());
        assertEquals(List.of(), legacy.participants().getFirst().finalTowerComposition());
        assertEquals(List.of(), legacy.participants().getFirst().buildActions());
        assertEquals(List.of(), legacy.participants().getFirst().roundMetrics());
        assertNull(legacy.catalogVersion());

        JobStatisticsSnapshot snapshot = new SQLiteJobStatisticsStore(tempDir.resolve("job-statistics.db"))
                .ingest(legacy);
        assertEquals(0L, snapshot.eligibleMatchCount());
        assertEquals(0L, snapshot.participantAppearances());
        assertTrue(snapshot.jobs().isEmpty());
    }

    @Test
    void storesRoundCombatMetricsAndReplacesThemDuringRescan() throws Exception {
        Path history = tempDir.resolve("round-metrics-history.json");
        Path database = tempDir.resolve("round-metrics-statistics.db");
        FileMatchResultRepository repository = new FileMatchResultRepository(history);
        SQLiteJobStatisticsStore store = new SQLiteJobStatisticsStore(database);

        repository.saveMatchResult(singlePlayerMetricsResult(31L, MatchMode.NORMAL, 120.0));
        store.rebuildFromHistory(null, history);
        repository.saveMatchResult(singlePlayerMetricsResult(31L, MatchMode.NORMAL, 240.0));
        store.rebuildFromHistory(null, history);

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("""
                    SELECT wave_duration_ticks, combat_ticks, tower_count_start, tower_count_end,
                           emerald_production_upgrade_count, emerald_per_second, income,
                           emerald_balance, diamond_balance, tower_limit_purchase_count, monster_kills
                    FROM job_stat_participant_round_metrics
                    """)) {
                assertTrue(result.next());
                assertEquals(200, result.getInt(1));
                assertEquals(21, result.getInt(2));
                assertEquals(2, result.getInt(3));
                assertEquals(1, result.getInt(4));
                assertEquals(3, result.getInt(5));
                assertEquals(8L, result.getLong(6));
                assertEquals(90L, result.getLong(7));
                assertEquals(40L, result.getLong(8));
                assertEquals(600L, result.getLong(9));
                assertEquals(2, result.getInt(10));
                assertEquals(7L, result.getLong(11));
                assertTrue(!result.next());
            }
            try (var result = statement.executeQuery("""
                    SELECT sample_count, start_count, end_alive_count, death_count,
                           physical_damage_dealt, magic_damage_dealt, damage_taken,
                           healing_done, kill_count, first_combat_tick, last_combat_tick, survival_ticks
                    FROM job_stat_participant_round_tower_metrics
                    """)) {
                assertTrue(result.next());
                assertEquals(2, result.getInt(1));
                assertEquals(2, result.getInt(2));
                assertEquals(1, result.getInt(3));
                assertEquals(1, result.getInt(4));
                assertEquals(240.0, result.getDouble(5), 0.0001);
                assertEquals(30.0, result.getDouble(6), 0.0001);
                assertEquals(45.0, result.getDouble(7), 0.0001);
                assertEquals(20.0, result.getDouble(8), 0.0001);
                assertEquals(4L, result.getLong(9));
                assertEquals(10, result.getInt(10));
                assertEquals(30, result.getInt(11));
                assertEquals(300L, result.getLong(12));
                assertTrue(!result.next());
            }
        }

        new SQLiteJobStatisticsStore(database).ingest(singlePlayerMetricsResult(32L, MatchMode.TEST, 999.0));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM job_stat_participant_round_metrics")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
    }

    private static MatchResult singlePlayerResult(long id, MatchMode mode, String jobId) {
        return singlePlayerResult(id, mode, jobId, 10);
    }

    private static MatchResult singlePlayerResult(long id, MatchMode mode, String jobId, int finalRound) {
        MatchParticipantResult participant = participant(
                "single-" + id,
                TeamId.RED,
                true,
                jobId,
                PlayerMatchStatsSnapshot.empty(),
                rounds(1, finalRound),
                rounds(1, finalRound)
        );
        return new MatchResult(
                new MatchId(id),
                id * 1_000L,
                id * 1_000L + 500L,
                List.of(participant),
                Set.of(),
                Set.of(TeamId.RED),
                List.of(teamResult(TeamId.RED, 1, true, -1)),
                finalRound,
                mode
        );
    }

    private static MatchResult singlePlayerMetricsResult(long id, MatchMode mode, double physicalDamage) {
        MatchParticipantResult base = participant(
                "metrics-" + id,
                TeamId.RED,
                true,
                VILLAGER,
                PlayerMatchStatsSnapshot.empty(),
                List.of(1),
                List.of(1)
        );
        TowerRoundMetricsSnapshot tower = new TowerRoundMetricsSnapshot(
                "semion-td:test_tower",
                2,
                2,
                1,
                1,
                physicalDamage,
                30.0,
                45.0,
                20.0,
                4,
                10,
                30,
                300
        );
        PlayerRoundMetricsSnapshot round = new PlayerRoundMetricsSnapshot(
                1, 200, 21, 2, 1, 1, 3, 8, 90, 40, 600, 2, 7, List.of(tower)
        );
        MatchParticipantResult participant = new MatchParticipantResult(
                base.playerId(),
                base.playerName(),
                base.teamId(),
                base.winner(),
                base.stats(),
                base.jobId(),
                base.attemptedRounds(),
                base.clearedRounds(),
                base.traitLoadout(),
                base.finalTowerComposition(),
                base.buildActions(),
                List.of(round)
        );
        return new MatchResult(
                new MatchId(id),
                id * 1_000L,
                id * 1_000L + 500L,
                List.of(participant),
                Set.of(),
                Set.of(TeamId.RED),
                List.of(teamResult(TeamId.RED, 1, true, -1)),
                1,
                mode
        );
    }

    private static MatchResult twoPlayerResult(
            long id,
            int finalRound,
            MatchParticipantResult red,
            MatchParticipantResult blue,
            MatchMode mode
    ) {
        return new MatchResult(
                new MatchId(id),
                id * 1_000L,
                id * 1_000L + 500L,
                List.of(red, blue),
                Set.of(),
                Set.of(TeamId.RED),
                List.of(
                        teamResult(TeamId.RED, 1, true, -1),
                        teamResult(TeamId.BLUE, 2, false, finalRound)
                ),
                finalRound,
                mode
        );
    }

    private static TeamMatchResult teamResult(TeamId teamId, int placement, boolean winner, int eliminatedRound) {
        return new TeamMatchResult(
                teamId,
                placement,
                winner ? MatchResultGroup.WIN_GROUP : MatchResultGroup.LOSS_GROUP,
                winner ? 1.0 : 0.0,
                eliminatedRound,
                -1L,
                0.0
        );
    }

    private static MatchParticipantResult participant(
            String name,
            TeamId teamId,
            boolean winner,
            String jobId,
            PlayerMatchStatsSnapshot stats
    ) {
        return participant(name, teamId, winner, jobId, stats, List.of(), List.of());
    }

    private static MatchParticipantResult participant(
            String name,
            TeamId teamId,
            boolean winner,
            String jobId,
            PlayerMatchStatsSnapshot stats,
            List<Integer> attemptedRounds,
            List<Integer> clearedRounds
    ) {
        return new MatchParticipantResult(
                UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)),
                name,
                teamId,
                winner,
                stats,
                jobId,
                attemptedRounds,
                clearedRounds
        );
    }

    private static List<Integer> rounds(int firstRound, int lastRound) {
        return IntStream.rangeClosed(firstRound, lastRound).boxed().toList();
    }

    private static PlayerMatchStatsSnapshot statsA() {
        return new PlayerMatchStatsSnapshot(
                10L, 20L, 3L, 100L,
                200.0, 50.0, 300.0, 120.0,
                5L, 7L, 40L, 60.0, 90.0
        );
    }

    private static PlayerMatchStatsSnapshot statsB() {
        return new PlayerMatchStatsSnapshot(
                30L, 40L, 5L, 200L,
                100.0, 25.0, 100.0, 20.0,
                3L, 4L, 60L, 20.0, 50.0
        );
    }

    private static JobStatisticsEntry entry(JobStatisticsSnapshot snapshot, String jobId) {
        return snapshot.jobs().stream()
                .filter(entry -> entry.jobId().equals(jobId))
                .findFirst()
                .orElseThrow();
    }
}
