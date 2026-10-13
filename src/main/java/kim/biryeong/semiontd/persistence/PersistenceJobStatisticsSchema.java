package kim.biryeong.semiontd.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class PersistenceJobStatisticsSchema {
    private PersistenceJobStatisticsSchema() {
    }

    static void initialize(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
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
                        primary_trait_id TEXT NOT NULL DEFAULT 'semion-td:none',
                        primary_trait_version INTEGER NOT NULL DEFAULT 0,
                        secondary_trait_id TEXT NOT NULL DEFAULT 'semion-td:none',
                        secondary_trait_version INTEGER NOT NULL DEFAULT 0,
                        catalog_version TEXT,
                        augment_version TEXT,
                        builder_origin TEXT,
                        builder_enabled INTEGER,
                        augment_selections TEXT,
                        augment_offer_events TEXT,
                        augment_telemetry TEXT,
                        PRIMARY KEY (match_id, player_id)
                    )
                    """);
            ensureColumns(connection, "job_stat_participant_facts",
                    new Column("cleared_round", "INTEGER NOT NULL DEFAULT 0"),
                    new Column("primary_trait_id", "TEXT NOT NULL DEFAULT 'semion-td:none'"),
                    new Column("primary_trait_version", "INTEGER NOT NULL DEFAULT 0"),
                    new Column("secondary_trait_id", "TEXT NOT NULL DEFAULT 'semion-td:none'"),
                    new Column("secondary_trait_version", "INTEGER NOT NULL DEFAULT 0"),
                    new Column("catalog_version", "TEXT"),
                    new Column("augment_version", "TEXT"),
                    new Column("builder_origin", "TEXT"),
                    new Column("builder_enabled", "INTEGER"),
                    new Column("augment_selections", "TEXT"),
                    new Column("augment_offer_events", "TEXT"),
                    new Column("augment_telemetry", "TEXT"));
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_job_stat_facts_job_id "
                    + "ON job_stat_participant_facts (job_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_job_stat_facts_ended_at "
                    + "ON job_stat_participant_facts (ended_at_epoch_millis)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_job_stat_facts_job_ended_at "
                    + "ON job_stat_participant_facts (job_id, ended_at_epoch_millis)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_job_stat_facts_traits "
                    + "ON job_stat_participant_facts (job_id, primary_trait_id, primary_trait_version, "
                    + "secondary_trait_id, secondary_trait_version)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_job_stat_facts_catalog_version "
                    + "ON job_stat_participant_facts (catalog_version)");
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
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS job_stat_participant_towers (
                        match_id INTEGER NOT NULL,
                        player_id TEXT NOT NULL,
                        tower_type_id TEXT NOT NULL,
                        tier INTEGER NOT NULL,
                        count INTEGER NOT NULL,
                        PRIMARY KEY (match_id, player_id, tower_type_id, tier),
                        CHECK (tier >= 0),
                        CHECK (count > 0)
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_job_stat_towers_type "
                    + "ON job_stat_participant_towers (tower_type_id, tier)");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS job_stat_participant_round_metrics (
                        match_id INTEGER NOT NULL,
                        player_id TEXT NOT NULL,
                        round_number INTEGER NOT NULL,
                        wave_duration_ticks INTEGER NOT NULL,
                        combat_ticks INTEGER NOT NULL,
                        tower_count_start INTEGER NOT NULL,
                        tower_count_end INTEGER NOT NULL,
                        tower_death_count INTEGER NOT NULL,
                        emerald_production_upgrade_count INTEGER NOT NULL,
                        emerald_per_second INTEGER NOT NULL,
                        income INTEGER NOT NULL,
                        emerald_balance INTEGER NOT NULL,
                        diamond_balance INTEGER NOT NULL,
                        tower_limit_purchase_count INTEGER NOT NULL,
                        monster_kills INTEGER NOT NULL,
                        utility_support_metrics TEXT,
                        wave_support_metrics TEXT,
                        natural_wave_metrics TEXT,
                        wave_template_id TEXT,
                        natural_wave_count INTEGER,
                        natural_wave_starting_health REAL,
                        augment_economy_metrics TEXT,
                        PRIMARY KEY (match_id, player_id, round_number)
                    )
                    """);
            ensureColumns(connection, "job_stat_participant_round_metrics",
                    new Column("utility_support_metrics", "TEXT"),
                    new Column("wave_support_metrics", "TEXT"),
                    new Column("natural_wave_metrics", "TEXT"),
                    new Column("wave_template_id", "TEXT"),
                    new Column("natural_wave_count", "INTEGER"),
                    new Column("natural_wave_starting_health", "REAL"),
                    new Column("augment_economy_metrics", "TEXT"));
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_job_stat_round_metrics_round "
                    + "ON job_stat_participant_round_metrics (round_number)");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS job_stat_participant_round_tower_metrics (
                        match_id INTEGER NOT NULL,
                        player_id TEXT NOT NULL,
                        round_number INTEGER NOT NULL,
                        tower_type_id TEXT NOT NULL,
                        sample_count INTEGER NOT NULL,
                        start_count INTEGER NOT NULL,
                        end_alive_count INTEGER NOT NULL,
                        death_count INTEGER NOT NULL,
                        physical_damage_dealt REAL NOT NULL,
                        magic_damage_dealt REAL NOT NULL,
                        damage_taken REAL NOT NULL,
                        healing_done REAL NOT NULL,
                        kill_count INTEGER NOT NULL,
                        first_combat_tick INTEGER NOT NULL,
                        last_combat_tick INTEGER NOT NULL,
                        survival_ticks INTEGER NOT NULL,
                        wave_start_max_health REAL,
                        enemy_hp_damage REAL,
                        augment_special_damage_dealt REAL,
                        PRIMARY KEY (match_id, player_id, round_number, tower_type_id)
                    )
                    """);
            ensureColumns(connection, "job_stat_participant_round_tower_metrics",
                    new Column("wave_start_max_health", "REAL"),
                    new Column("enemy_hp_damage", "REAL"),
                    new Column("augment_special_damage_dealt", "REAL"));
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_job_stat_round_tower_metrics_type_round "
                    + "ON job_stat_participant_round_tower_metrics (tower_type_id, round_number)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_job_stat_round_tower_metrics_round "
                    + "ON job_stat_participant_round_tower_metrics (round_number)");
            ensureColumns(connection, "job_round_statistics",
                    new Column("attempt_count", "INTEGER NOT NULL DEFAULT 0"));
            if (legacyRoundStatistics) {
                statement.executeUpdate("DELETE FROM job_round_statistics");
            }
        }
    }

    static boolean tableExists(Connection connection, String tableName) throws SQLException {
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

    private static void ensureColumns(Connection connection, String tableName, Column... required) throws SQLException {
        Set<String> existing = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (columns.next()) {
                existing.add(columns.getString("name").toLowerCase(Locale.ROOT));
            }
        }
        for (Column column : required) {
            if (existing.contains(column.name().toLowerCase(Locale.ROOT))) {
                continue;
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN "
                        + column.name() + " " + column.definition());
            }
            existing.add(column.name().toLowerCase(Locale.ROOT));
        }
    }

    private record Column(String name, String definition) {
    }
}
