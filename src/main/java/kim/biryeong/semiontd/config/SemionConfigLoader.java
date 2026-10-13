package kim.biryeong.semiontd.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import kim.biryeong.semiontd.persistence.SemionPersistenceConfig;
import kim.biryeong.semiontd.rating.RatingConfig;
import kim.biryeong.semiontd.trait.TraitSelectionConfig;
import kim.biryeong.semiontd.augment.AugmentConfig;
import org.slf4j.Logger;

public final class SemionConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private SemionConfigLoader() {
    }

    public static LoadedConfigs load(Path configDir, Logger logger) {
        return load(
                configDir,
                logger,
                TowerBalanceConfig.defaultConfig(),
                JobAvailabilityConfig.defaultConfig()
        );
    }

    public static LoadedConfigs load(
            Path configDir,
            Logger logger,
            TowerBalanceConfig lastKnownGoodTowerBalance
    ) {
        return load(
                configDir,
                logger,
                lastKnownGoodTowerBalance,
                JobAvailabilityConfig.defaultConfig()
        );
    }

    public static LoadedConfigs load(
            Path configDir,
            Logger logger,
            TowerBalanceConfig lastKnownGoodTowerBalance,
            JobAvailabilityConfig lastKnownGoodJobAvailability
    ) {
        return load(configDir, logger, lastKnownGoodTowerBalance, lastKnownGoodJobAvailability, AugmentConfig.defaults());
    }

    public static LoadedConfigs load(
            Path configDir,
            Logger logger,
            TowerBalanceConfig lastKnownGoodTowerBalance,
            JobAvailabilityConfig lastKnownGoodJobAvailability,
            AugmentConfig lastKnownGoodAugments
    ) {
        return load(configDir, logger, lastKnownGoodTowerBalance, lastKnownGoodJobAvailability, lastKnownGoodAugments, null);
    }

    public static LoadedConfigs load(
            Path configDir,
            Logger logger,
            TowerBalanceConfig lastKnownGoodTowerBalance,
            JobAvailabilityConfig lastKnownGoodJobAvailability,
            AugmentConfig lastKnownGoodAugments,
            WaveConfig lastKnownGoodWaves
    ) {
        AugmentConfig augmentFallback = lastKnownGoodAugments == null ? AugmentConfig.defaults() : lastKnownGoodAugments;
        WaveConfig waveFallback = lastKnownGoodWaves == null ? WaveConfig.defaultConfig() : lastKnownGoodWaves;
        TowerBalanceConfig towerBalanceFallback = lastKnownGoodTowerBalance == null
                ? TowerBalanceConfig.defaultConfig()
                : lastKnownGoodTowerBalance;
        JobAvailabilityConfig jobAvailabilityFallback = lastKnownGoodJobAvailability == null
                ? JobAvailabilityConfig.defaultConfig()
                : lastKnownGoodJobAvailability;
        towerBalanceFallback.validateForRuntime();
        try {
            Files.createDirectories(configDir);
        } catch (IOException exception) {
            logger.warn("Failed to create config directory {}; using defaults.", configDir, exception);
            return new LoadedConfigs(
                    EconomyConfig.defaultConfig(),
                    waveFallback,
                    MapConfig.defaultConfig(),
                    ProgressionConfig.defaultConfig(),
                    RatingConfig.defaultConfig(),
                    SemionPersistenceConfig.defaultConfig(),
                    jobAvailabilityFallback,
                    towerBalanceFallback,
                    SummonConfig.defaultConfig(),
                    LeaderTargetingConfig.defaultConfig(),
                    IncomeLaneRoutingConfig.defaultConfig(),
                    MonsterScalingConfig.defaultConfig(),
                    VfxConfig.defaultConfig(),
                    TipConfig.defaultConfig(),
                    TraitSelectionConfig.defaultConfig(),
                    TraitBalanceConfig.defaultConfig(),
                    WebIntegrationConfig.defaultConfig(),
                    CombatSpeedConfig.defaultConfig(),
                    augmentFallback
            );
        }

        EconomyConfig economy = loadOrCreateEconomy(
                configDir.resolve("economy.json"),
                EconomyConfig.defaultConfig(),
                logger
        );
        WaveConfig waves = loadWaves(configDir, lastKnownGoodWaves, logger);
        MapConfig map = loadOrCreate(
                configDir.resolve("map.json"),
                MapConfig.defaultConfig(),
                MapConfig.class,
                logger
        );
        ProgressionConfig progression = loadOrCreate(
                configDir.resolve("progression.json"),
                ProgressionConfig.defaultConfig(),
                ProgressionConfig.class,
                logger
        );
        RatingConfig rating = loadOrCreateRating(
                configDir.resolve("rating.json"),
                RatingConfig.defaultConfig(),
                logger
        );
        SemionPersistenceConfig persistence = loadOrCreate(
                configDir.resolve("persistence.json"),
                SemionPersistenceConfig.defaultConfig(),
                SemionPersistenceConfig.class,
                logger
        );
        JobAvailabilityConfig jobAvailability = loadOrCreateJobAvailability(
                configDir.resolve("jobs.json"),
                JobAvailabilityConfig.defaultConfig(),
                jobAvailabilityFallback,
                logger
        );
        TowerBalanceConfig towerBalance = loadOrCreateTowerBalance(
                configDir.resolve("tower_balance.json"),
                TowerBalanceConfig.defaultConfig(),
                towerBalanceFallback,
                logger
        );
        SummonConfig summons = loadOrCreateSummons(
                configDir.resolve("summons.json"),
                SummonConfig.defaultConfig(),
                logger
        );
        LeaderTargetingConfig leaderTargeting = loadOrCreate(
                configDir.resolve("leader_targeting.json"),
                LeaderTargetingConfig.defaultConfig(),
                LeaderTargetingConfig.class,
                logger
        );
        IncomeLaneRoutingConfig incomeLaneRouting = loadOrCreateIncomeLaneRouting(
                configDir.resolve("income_lane_routing.json"),
                IncomeLaneRoutingConfig.defaultConfig(),
                logger
        );
        MonsterScalingConfig monsterScaling = loadOrCreateMonsterScaling(
                configDir.resolve("monster_scaling.json"),
                MonsterScalingConfig.defaultConfig(),
                logger
        );
        VfxConfig vfx = loadOrCreateVfx(
                configDir.resolve("vfx.json"),
                VfxConfig.defaultConfig(),
                logger
        );
        TipConfig tips = loadOrCreateTips(
                configDir.resolve("tips.json"),
                TipConfig.defaultConfig(),
                logger
        );
        TraitSelectionConfig traits = loadOrCreate(
                configDir.resolve("traits.json"),
                TraitSelectionConfig.defaultConfig(),
                TraitSelectionConfig.class,
                logger
        );
        TraitBalanceConfig traitBalance = loadOrCreateTraitBalance(
                configDir.resolve("trait_balance.json"),
                TraitBalanceConfig.defaultConfig(),
                logger
        );
        WebIntegrationConfig webIntegration = loadOrCreate(
                configDir.resolve("web_integration.json"),
                WebIntegrationConfig.defaultConfig(),
                WebIntegrationConfig.class,
                logger
        );
        CombatSpeedConfig combatSpeed = loadOrCreate(
                configDir.resolve("combat_speed.json"),
                CombatSpeedConfig.defaultConfig(),
                CombatSpeedConfig.class,
                logger
        );
        AugmentConfig augments = loadOrCreateAugments(configDir.resolve("augment_balance.json"), augmentFallback, logger);
        return new LoadedConfigs(economy, waves, map, progression, rating, persistence, jobAvailability, towerBalance, summons, leaderTargeting, incomeLaneRouting, monsterScaling, vfx, tips, traits, traitBalance, webIntegration, combatSpeed, augments);
    }

    static AugmentConfig loadOrCreateAugments(Path path, AugmentConfig lastKnownGood, Logger logger) {
        if (Files.notExists(path)) {
            AugmentConfig defaults = AugmentConfig.defaults();
            write(path, defaults.toJson(), logger);
            return defaults;
        }
        try {
            JsonElement json;
            try (Reader reader = Files.newBufferedReader(path)) {
                json = JsonParser.parseReader(reader);
            }
            if (!json.isJsonObject()) {
                throw new IllegalArgumentException("Augment config must be an object");
            }
            MagicSchoolBalanceMigration.migrateAugments(json.getAsJsonObject());
            AugmentConfig value = AugmentConfig.fromJson(json.getAsJsonObject());
            // Close the source before replacing it atomically on Windows.
            write(path, value.toJson(), logger);
            return value;
        } catch (IOException | RuntimeException exception) {
            logger.error("Failed to load config {}; retaining last-known-good augments.", path, exception);
            return lastKnownGood;
        }
    }

    private static JobAvailabilityConfig loadOrCreateJobAvailability(
            Path path,
            JobAvailabilityConfig defaults,
            JobAvailabilityConfig lastKnownGood,
            Logger logger
    ) {
        if (Files.notExists(path)) {
            write(path, defaults, logger);
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JobAvailabilityConfig value = GSON.fromJson(reader, JobAvailabilityConfig.class);
            return value == null ? defaults : value;
        } catch (IOException | RuntimeException exception) {
            logger.error("Failed to load config {}; retaining last-known-good job availability.", path, exception);
            return lastKnownGood;
        }
    }

    public static boolean saveJobAvailability(
            Path configDir,
            JobAvailabilityConfig config,
            Logger logger
    ) {
        if (configDir == null || config == null) {
            return false;
        }
        return write(configDir.resolve("jobs.json"), config, logger);
    }

    private static <T> T loadOrCreate(Path path, T defaults, Class<T> type, Logger logger) {
        if (Files.notExists(path)) {
            write(path, defaults, logger);
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            T value = GSON.fromJson(reader, type);
            return value == null ? defaults : value;
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            logger.warn("Failed to load config {}; using defaults.", path, exception);
            return defaults;
        }
    }

    static WaveConfig loadWaves(Path configDir, WaveConfig lastKnownGood, Logger logger) {
        Path preferred = configDir.resolve("wave.json");
        Path legacy = configDir.resolve("waves.json");
        Path path = Files.exists(preferred) ? preferred : legacy;
        if (Files.notExists(path) && lastKnownGood == null) {
            WaveConfig defaults = WaveConfig.defaultConfig();
            defaults.validate();
            write(preferred, defaults, logger);
            return defaults;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            WaveConfig value = GSON.fromJson(reader, WaveConfig.class);
            if (value == null) {throw new IllegalArgumentException("Wave config must be an object.");}
            value.validate();
            return value;
        } catch (IOException | RuntimeException exception) {
            logger.error("Failed to load wave config {}; retaining last-known-good waves.", path, exception);
            return lastKnownGood == null ? WaveConfig.defaultConfig() : lastKnownGood;
        }
    }

    private static RatingConfig loadOrCreateRating(Path path, RatingConfig defaults, Logger logger) {
        if (Files.notExists(path)) {
            write(path, defaults, logger);
            return defaults;
        }

        try {
            String json = Files.readString(path);
            ConfigJsonProperties properties = ConfigJsonProperties.parse(json);
            RatingConfig loaded = GSON.fromJson(json, RatingConfig.class);
            RatingConfig value = loaded == null ? defaults : loaded;
            boolean teamEloMatchmakingMissing = !properties.has("teamEloMatchmakingEnabled");
            boolean perfectDefenseLossMultiplierMissing = !properties.has("perfectDefenseLossMultiplier");
            if (teamEloMatchmakingMissing) {
                value = value.withTeamEloMatchmakingEnabled(defaults.teamEloMatchmakingEnabled());
            }
            if (perfectDefenseLossMultiplierMissing) {
                value = value.withPerfectDefenseLossMultiplier(defaults.perfectDefenseLossMultiplier());
            }
            if (teamEloMatchmakingMissing || perfectDefenseLossMultiplierMissing) {
                write(path, value, logger);
            }
            return value;
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            logger.warn("Failed to load config {}; using defaults.", path, exception);
            return defaults;
        }
    }

    private static EconomyConfig loadOrCreateEconomy(Path path, EconomyConfig defaults, Logger logger) {
        if (Files.notExists(path)) {
            write(path, defaults, logger);
            return defaults;
        }

        try {
            String json = Files.readString(path);
            ConfigJsonProperties properties = ConfigJsonProperties.parse(json);
            EconomyConfig loaded = GSON.fromJson(json, EconomyConfig.class);
            EconomyConfig value = loaded == null ? defaults : loaded;
            boolean towerLimitMissing = !properties.has("towerLimit");
            boolean towerLimitPurchaseMissing = !towerLimitMissing
                    && !properties.hasAllNested(
                    "towerLimit",
                    "initialPurchaseDiamondCost",
                    "purchaseDiamondCostIncrease",
                    "initialPurchaseEmeraldCost",
                    "purchaseEmeraldCostIncrease"
            );
            boolean teamTransferMissing = !properties.has("teamTransfer");
            boolean teamTransferEnabledMissing = teamTransferMissing
                    || !properties.hasNested("teamTransfer", "enabled");
            boolean teamTransferCooldownMissing = teamTransferMissing
                    || !properties.hasNested("teamTransfer", "receiveCooldownRounds");
            boolean teamTransferMaxMissing = teamTransferMissing
                    || !properties.hasNested("teamTransfer", "maxDiamondPerRound");
            boolean emeraldIncomeBoostMissing = !properties.has("emeraldIncomeBoost");
            boolean emeraldIncomeBoostEnabledMissing = emeraldIncomeBoostMissing
                    || !properties.hasNested("emeraldIncomeBoost", "enabled");
            boolean emeraldIncomeBoostStartRoundMissing = emeraldIncomeBoostMissing
                    || !properties.hasNested("emeraldIncomeBoost", "startRound");
            EconomyConfig.TeamTransferConfig teamTransfer = mergedTeamTransfer(
                    value.teamTransfer(),
                    defaults.teamTransfer(),
                    teamTransferEnabledMissing,
                    teamTransferCooldownMissing,
                    teamTransferMaxMissing
            );
            EconomyConfig.EmeraldIncomeBoostConfig emeraldIncomeBoost = mergedEmeraldIncomeBoost(
                    value.emeraldIncomeBoost(),
                    defaults.emeraldIncomeBoost(),
                    emeraldIncomeBoostEnabledMissing,
                    emeraldIncomeBoostStartRoundMissing
            );
            if (towerLimitPurchaseMissing || teamTransferMissing || teamTransferEnabledMissing
                    || teamTransferCooldownMissing || teamTransferMaxMissing || emeraldIncomeBoostMissing
                    || emeraldIncomeBoostEnabledMissing || emeraldIncomeBoostStartRoundMissing) {
                value = new EconomyConfig(
                        value.startingDiamond(),
                        value.startingEmerald(),
                        value.startingIncome(),
                        value.emeraldCap(),
                        value.emeraldProduction(),
                        towerLimitPurchaseMissing ? value.towerLimit().withDefaultPurchaseSettings() : value.towerLimit(),
                        value.killReward(),
                        teamTransfer,
                        emeraldIncomeBoost
                );
            }
            if (towerLimitMissing || towerLimitPurchaseMissing || teamTransferMissing
                    || teamTransferEnabledMissing || teamTransferCooldownMissing || teamTransferMaxMissing
                    || emeraldIncomeBoostMissing || emeraldIncomeBoostEnabledMissing || emeraldIncomeBoostStartRoundMissing) {
                write(path, value, logger);
            }
            return value;
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            logger.warn("Failed to load config {}; using defaults.", path, exception);
            return defaults;
        }
    }

    private static IncomeLaneRoutingConfig loadOrCreateIncomeLaneRouting(
            Path path,
            IncomeLaneRoutingConfig defaults,
            Logger logger
    ) {
        if (Files.notExists(path)) {
            write(path, defaults, logger);
            return defaults;
        }

        try {
            String json = Files.readString(path);
            ConfigJsonProperties properties = ConfigJsonProperties.parse(json);
            IncomeLaneRoutingConfig loaded = GSON.fromJson(json, IncomeLaneRoutingConfig.class);
            IncomeLaneRoutingConfig value = loaded == null ? defaults : loaded;
            boolean enabledMissing = !properties.has("enabled");
            boolean modeMissing = !properties.has("mode");
            boolean queuedThreatWeightMissing = !properties.has("queuedThreatWeight");
            boolean nextRoundQueuedThreatWeightMissing = !properties.has("nextRoundQueuedThreatWeight");
            boolean tieBreakModeMissing = !properties.has("tieBreakMode");
            if (enabledMissing || modeMissing || queuedThreatWeightMissing || nextRoundQueuedThreatWeightMissing || tieBreakModeMissing) {
                value = new IncomeLaneRoutingConfig(
                        enabledMissing ? defaults.enabled() : value.enabled(),
                        modeMissing ? defaults.mode() : value.mode(),
                        queuedThreatWeightMissing ? defaults.queuedThreatWeight() : value.queuedThreatWeight(),
                        nextRoundQueuedThreatWeightMissing ? defaults.nextRoundQueuedThreatWeight() : value.nextRoundQueuedThreatWeight(),
                        tieBreakModeMissing ? defaults.tieBreakMode() : value.tieBreakMode()
                );
                write(path, value, logger);
            }
            return value;
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            logger.warn("Failed to load config {}; using defaults.", path, exception);
            write(path, defaults, logger);
            return defaults;
        }
    }

    private static MonsterScalingConfig loadOrCreateMonsterScaling(
            Path path,
            MonsterScalingConfig defaults,
            Logger logger
    ) {
        if (Files.notExists(path)) {
            write(path, defaults, logger);
            return defaults;
        }

        try {
            String json = Files.readString(path);
            ConfigJsonProperties properties = ConfigJsonProperties.parse(json);
            MonsterScalingConfig loaded = GSON.fromJson(json, MonsterScalingConfig.class);
            MonsterScalingConfig value = loaded == null ? defaults : loaded;
            boolean enabledMissing = !properties.has("enabled");
            boolean survivalDelayMissing = !properties.has("survivalDelayTicks");
            boolean laneBreachDelayMissing = !properties.has("laneBreachDelayTicks");
            boolean intervalMissing = !properties.has("intervalTicks");
            boolean healthGrowthMissing = !properties.has("healthGrowthPercentPerInterval");
            boolean attackGrowthMissing = !properties.has("attackDamageGrowthPercentPerInterval");
            boolean waveMissing = !properties.has("scaleWaveMonsters");
            boolean incomeMissing = !properties.has("scaleIncomeMonsters");
            if (enabledMissing || survivalDelayMissing || laneBreachDelayMissing || intervalMissing
                    || healthGrowthMissing || attackGrowthMissing || waveMissing || incomeMissing) {
                value = new MonsterScalingConfig(
                        enabledMissing ? defaults.enabled() : value.enabled(),
                        survivalDelayMissing ? defaults.survivalDelayTicks() : value.survivalDelayTicks(),
                        laneBreachDelayMissing ? defaults.laneBreachDelayTicks() : value.laneBreachDelayTicks(),
                        intervalMissing ? defaults.intervalTicks() : value.intervalTicks(),
                        healthGrowthMissing ? defaults.healthGrowthPercentPerInterval() : value.healthGrowthPercentPerInterval(),
                        attackGrowthMissing ? defaults.attackDamageGrowthPercentPerInterval() : value.attackDamageGrowthPercentPerInterval(),
                        waveMissing ? defaults.scaleWaveMonsters() : value.scaleWaveMonsters(),
                        incomeMissing ? defaults.scaleIncomeMonsters() : value.scaleIncomeMonsters()
                );
                write(path, value, logger);
            }
            return value;
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            logger.warn("Failed to load config {}; using defaults.", path, exception);
            write(path, defaults, logger);
            return defaults;
        }
    }

    private static EconomyConfig.TeamTransferConfig mergedTeamTransfer(
            EconomyConfig.TeamTransferConfig loaded,
            EconomyConfig.TeamTransferConfig defaults,
            boolean enabledMissing,
            boolean cooldownMissing,
            boolean maxMissing
    ) {
        EconomyConfig.TeamTransferConfig safeLoaded = loaded == null ? defaults : loaded;
        return new EconomyConfig.TeamTransferConfig(
                enabledMissing ? defaults.enabled() : safeLoaded.enabled(),
                cooldownMissing ? defaults.receiveCooldownRounds() : safeLoaded.receiveCooldownRounds(),
                maxMissing ? defaults.maxDiamondPerRound() : safeLoaded.maxDiamondPerRound()
        );
    }

    private static EconomyConfig.EmeraldIncomeBoostConfig mergedEmeraldIncomeBoost(
            EconomyConfig.EmeraldIncomeBoostConfig loaded,
            EconomyConfig.EmeraldIncomeBoostConfig defaults,
            boolean enabledMissing,
            boolean startRoundMissing
    ) {
        EconomyConfig.EmeraldIncomeBoostConfig safeLoaded = loaded == null ? defaults : loaded;
        return new EconomyConfig.EmeraldIncomeBoostConfig(
                enabledMissing ? defaults.enabled() : safeLoaded.enabled(),
                startRoundMissing ? defaults.startRound() : safeLoaded.startRound()
        );
    }

    private static TowerBalanceConfig loadOrCreateTowerBalance(
            Path path,
            TowerBalanceConfig defaults,
            TowerBalanceConfig lastKnownGood,
            Logger logger
    ) {
        if (Files.notExists(path)) {
            write(path, defaults, logger);
            return defaults;
        }

        try {
            String json = Files.readString(path);
            ConfigJsonProperties properties = ConfigJsonProperties.parse(json);
            String migratedJson = migrateLegacyVillagerAdvBuffs(json, defaults);
            migratedJson = migrateLegacyHogwartsHealth(migratedJson);
            migratedJson = migrateLegacyMagicSchoolStudents(migratedJson);
            migratedJson = migrateMagicSchoolBaseStats(migratedJson);
            migratedJson = migrateMagicSchoolProficiency(migratedJson);
            migratedJson = migrateFreeHogwarts(migratedJson);
            migratedJson = migrateMagicSchoolCurriculumBalance(migratedJson);
            migratedJson = migrateMagicSchoolCombatBalance(migratedJson, defaults);
            migratedJson = migrateMagicSchoolImperio(migratedJson);
            migratedJson = MagicSchoolBalanceMigration.migrate(migratedJson, defaults);
            migratedJson = MagicSchoolBalanceMigration.migrateProtection(migratedJson);
            ConfigJsonProperties migratedProperties = migratedJson.equals(json)
                    ? properties
                    : ConfigJsonProperties.parse(migratedJson);
            TowerBalanceConfig value = GSON.fromJson(migratedJson, TowerBalanceConfig.class);
            TowerBalanceConfig loaded = value == null ? defaults : value;
            TowerBalanceConfig merged = loaded.withMissingDefaults(defaults);
            if (merged.schemaVersion() > TowerBalanceConfig.CURRENT_SCHEMA_VERSION) {
                throw new IllegalArgumentException(
                        "Unsupported tower balance schema version: " + merged.schemaVersion()
                );
            }
            TowerBalanceConfig fallback = (lastKnownGood == null ? defaults : lastKnownGood)
                    .withMissingDefaults(defaults);
            TowerBalanceConfig repaired = merged.withInvalidNumericValuesFrom(fallback);
            if (!repaired.equals(merged)) {
                logger.warn(
                        "Ignored invalid negative or non-finite values in {}; using last-known-good values for those fields.",
                        path
                );
                merged = repaired;
            }
            merged.validateForRuntime();
            boolean schemaVersionMissing = !properties.has("schemaVersion");
            boolean illusionCloneQueueMissing = !migratedProperties.has("illusionCloneQueue");
            boolean villagerAdvMissing = !migratedProperties.has("villagerAdv");
            if (!migratedJson.equals(json)
                    || schemaVersionMissing
                    || illusionCloneQueueMissing
                    || villagerAdvMissing
                    || !merged.equals(loaded)) {
                write(path, merged, logger);
            }
            return merged;
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            logger.error("Failed to load config {}; retaining last-known-good tower balance.", path, exception);
            return lastKnownGood;
        }
    }

    private static TraitBalanceConfig loadOrCreateTraitBalance(
            Path path,
            TraitBalanceConfig defaults,
            Logger logger
    ) {
        if (Files.notExists(path)) {
            write(path, defaults, logger);
            return defaults;
        }

        TraitBalanceConfig loaded;
        try (Reader reader = Files.newBufferedReader(path)) {
            TraitBalanceConfig value = GSON.fromJson(reader, TraitBalanceConfig.class);
            loaded = value == null ? defaults : value;
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            logger.warn("Failed to load config {}; using defaults.", path, exception);
            return defaults;
        }
        TraitBalanceConfig merged = loaded.withMissingDefaults(defaults);
        if (!merged.equals(loaded)) {
            write(path, merged, logger);
        }
        return merged;
    }

    private static String migrateLegacyMagicSchoolStudents(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject() || !root.getAsJsonObject().has("towers")
                || !root.getAsJsonObject().get("towers").isJsonObject()) return json;
        JsonObject towers = root.getAsJsonObject().getAsJsonObject("towers");
        if (java.util.List.of("gryffindor", "hufflepuff", "ravenclaw", "slytherin").stream()
                .anyMatch(house -> towers.has("magic_school_" + house + "_t2"))
                || !towers.has("magic_school_freshman_t1")
                || !towers.get("magic_school_freshman_t1").isJsonObject()) return json;
        JsonObject student = towers.getAsJsonObject("magic_school_freshman_t1");
        boolean changed = false;
        for (String key : java.util.List.of("maxHealth", "damage", "attackIntervalTicks")) {
            double previous = switch (key) { case "maxHealth" -> 80; case "damage" -> 12; default -> 20; };
            JsonElement value = student.get(key);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                    && value.getAsDouble() == previous) {
                student.addProperty(key, key.equals("maxHealth") ? 200 : 30);
                changed = true;
            }
        }
        return changed ? GSON.toJson(root) : json;
    }

    private static String migrateMagicSchoolBaseStats(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return json;
        JsonObject object = root.getAsJsonObject();
        JsonObject abilities = legacySchoolObject(object, "abilities");
        JsonObject global = legacySchoolObject(abilities, "magic_school_global");
        if (global.has("baseStatsVersion")) return json;
        JsonObject towers = legacySchoolObject(object, "towers");
        for (String house : java.util.List.of("freshman", "gryffindor", "hufflepuff", "ravenclaw", "slytherin")) {
            boolean freshman = house.equals("freshman");
            String id = "magic_school_" + house + (freshman ? "_t1" : "_t2");
            if (!towers.has(id) && !abilities.has(id)) continue;
            JsonObject stats = legacySchoolObject(towers, id);
            JsonObject values = legacySchoolObject(abilities, id);
            double health = legacySchoolNumber(stats, "maxHealth", freshman ? 200 : 350);
            double damage = legacySchoolNumber(stats, "damage", freshman ? 30 : 50);
            double interval = legacySchoolNumber(stats, "attackIntervalTicks", freshman ? 30 : 18);
            double healthBonus = legacySchoolNumber(values, "houseHealthBonus", house.equals("hufflepuff") ? .10 : 0);
            double damageBonus = legacySchoolNumber(values, "houseDamageBonus", house.equals("slytherin") ? .10 : 0);
            double reduction = legacySchoolNumber(values, "houseAttackIntervalReduction", house.equals("gryffindor") ? 1 : 0);
            if (healthBonus > 1 || damageBonus > 1 || reduction != Math.rint(reduction) || interval != Math.rint(interval)) {
                throw new IllegalArgumentException("Invalid legacy Magic School house stats: " + id);
            }
            if (freshman && interval == 30) interval = 24;
            stats.addProperty("maxHealth", BigDecimal.valueOf(health).multiply(BigDecimal.ONE.add(BigDecimal.valueOf(healthBonus))));
            stats.addProperty("damage", BigDecimal.valueOf(damage).multiply(BigDecimal.ONE.add(BigDecimal.valueOf(damageBonus))));
            stats.addProperty("attackIntervalTicks", Math.max(1, (int) Math.round(interval - reduction)));
            values.remove("houseHealthBonus");
            values.remove("houseDamageBonus");
            values.remove("houseAttackIntervalReduction");
            towers.add(id, stats);
            abilities.add(id, values);
        }
        global.addProperty("baseStatsVersion", 1);
        abilities.add("magic_school_global", global);
        object.add("abilities", abilities);
        object.add("towers", towers);
        return GSON.toJson(object);
    }

    private static String migrateMagicSchoolProficiency(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return json;
        JsonObject object = root.getAsJsonObject();
        JsonObject abilities = legacySchoolObject(object, "abilities");
        JsonObject global = legacySchoolObject(abilities, "magic_school_global");
        double version = legacySchoolNumber(global, "proficiencyVersion", 0);
        if (version >= 2) return json;
        for (String house : java.util.List.of("gryffindor", "hufflepuff", "ravenclaw", "slytherin")) {
            for (int tier : java.util.List.of(2, 3)) {
                String id = "magic_school_" + house + "_t" + tier;
                if (!abilities.has(id)) continue;
                JsonObject values = legacySchoolObject(abilities, id);
                double cap = legacySchoolNumber(values, "maxProficiency", -1);
                if (cap == 300 || tier == 2 && version == 0 && cap == 200) {
                    values.addProperty("maxProficiency", tier == 2 ? 250 : 400);
                }
            }
        }
        for (String key : java.util.List.of("proficiencyDamagePerPoint", "proficiencyHealthPerPoint")) {
            if (legacySchoolNumber(global, key, -1) == .005) global.addProperty(key, .0015);
        }
        if (abilities.has("magic_school_spell_episkey")) {
            JsonObject episkey = legacySchoolObject(abilities, "magic_school_spell_episkey");
            if (legacySchoolNumber(episkey, "healingMultiplier", -1) == 2) episkey.addProperty("healingMultiplier", 1.5);
        }
        global.addProperty("proficiencyVersion", 2);
        abilities.add("magic_school_global", global);
        object.add("abilities", abilities);
        return GSON.toJson(object);
    }

    private static String migrateFreeHogwarts(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return json;
        JsonObject object = root.getAsJsonObject();
        JsonObject abilities = legacySchoolObject(object, "abilities");
        JsonObject global = legacySchoolObject(abilities, "magic_school_global");
        if (global.has("hogwartsVersion")) return json;
        JsonObject towers = legacySchoolObject(object, "towers");
        if (towers.has("magic_school_hogwarts_t1")) {
            JsonObject school = legacySchoolObject(towers, "magic_school_hogwarts_t1");
            if (legacySchoolNumber(school, "mineralCost", 0) == 300) school.addProperty("mineralCost", 0);
        }
        JsonObject costs = legacySchoolObject(object, "upgradeCosts");
        for (String id : java.util.List.of("magic_school_hogwarts_t2", "magic_school_hogwarts_t3")) {
            towers.remove(id);
            abilities.remove(id);
            costs.remove(id);
        }
        costs.remove("magic_school_hogwarts_t1->magic_school_hogwarts_t2");
        costs.remove("magic_school_hogwarts_t2->magic_school_hogwarts_t3");
        global.addProperty("hogwartsVersion", 1);
        abilities.add("magic_school_global", global);
        object.add("abilities", abilities);
        object.add("towers", towers);
        object.add("upgradeCosts", costs);
        return GSON.toJson(object);
    }

    private static String migrateMagicSchoolCurriculumBalance(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return json;
        JsonObject object = root.getAsJsonObject();
        JsonObject abilities = legacySchoolObject(object, "abilities");
        JsonObject global = legacySchoolObject(abilities, "magic_school_global");
        if (legacySchoolNumber(global, "curriculumBalanceVersion", 0) >= 2) return json;
        Map<String, Double> formerDefaults = Map.of(
                "spellPowerPerLevel", .10, "darkArtsDefensePerLevel", .10, "magicHistoryPerLevel", .25,
                "magicHistoryMaxLevel", 4.0, "sortingHatCost", 150.0, "deathEaterCost", 100.0,
                "duelingPracticeCost", 250.0);
        var defaults = kim.biryeong.semiontd.tower.magicschool.MagicSchoolCurriculum.defaultAbilities();
        formerDefaults.forEach((key, former) -> {
            if (!global.has("curriculumBalanceVersion") && global.has(key) && legacySchoolNumber(global, key, -1) == former) {
                global.addProperty(key, defaults.get(key));
            }
        });
        Map.of("spellPowerPerLevel", .08, "darkArtsDefensePerLevel", .08,
                "spellPowerMaxLevel", 5.0, "darkArtsDefenseMaxLevel", 5.0).forEach((key, former) -> {
            if (global.has(key) && legacySchoolNumber(global, key, -1) == former) {
                global.addProperty(key, defaults.get(key));
            }
        });
        global.addProperty("curriculumBalanceVersion", 2);
        abilities.add("magic_school_global", global);
        object.add("abilities", abilities);
        return GSON.toJson(object);
    }

    private static String migrateMagicSchoolCombatBalance(String json, TowerBalanceConfig defaults) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return json;
        JsonObject object = root.getAsJsonObject();
        JsonObject abilities = legacySchoolObject(object, "abilities");
        JsonObject global = legacySchoolObject(abilities, "magic_school_global");
        if (legacySchoolNumber(global, "combatBalanceVersion", 0) >= 1) return json;
        Map.of("spellPowerCost", 100.0, "spellPowerCostIncrease", 75.0, "darkArtsDefenseCost", 90.0,
                "spellTier2Cost", 200.0, "spellTier3Cost", 400.0, "spellTier4Cost", 700.0,
                "spellTier5Cost", 1000.0).forEach((key, former) -> {
            if (legacySchoolNumber(global, key, -1) == former) {
                global.addProperty(key, defaults.ability("magic_school_global", key, former));
            }
        });
        JsonObject towers = legacySchoolObject(object, "towers");
        if (towers.has("magic_school_freshman_t1")) {
            JsonObject student = legacySchoolObject(towers, "magic_school_freshman_t1");
            if (legacySchoolNumber(student, "attackIntervalTicks", -1) == 24) {
                student.addProperty("attackIntervalTicks", defaults.towers().get("magic_school_freshman_t1").attackIntervalTicks());
            }
        }
        for (String house : java.util.List.of("gryffindor", "hufflepuff", "ravenclaw", "slytherin")) {
            for (int tier : java.util.List.of(2, 3)) {
                String id = "magic_school_" + house + "_t" + tier;
                if (!towers.has(id)) continue;
                JsonObject stats = legacySchoolObject(towers, id);
                double formerHealth = tier == 2 ? (house.equals("hufflepuff") ? 385 : 350)
                        : (house.equals("hufflepuff") ? 770 : 700);
                double formerDamage = tier == 2 ? (house.equals("slytherin") ? 55 : 50)
                        : (house.equals("slytherin") ? 132 : 120);
                if (legacySchoolNumber(stats, "maxHealth", -1) == formerHealth) {
                    stats.addProperty("maxHealth", defaults.towers().get(id).maxHealth());
                }
                if (legacySchoolNumber(stats, "damage", -1) == formerDamage) {
                    stats.addProperty("damage", defaults.towers().get(id).damage());
                }
            }
        }
        String bombardaId = "magic_school_spell_bombarda";
        if (abilities.has(bombardaId)) {
            JsonObject spell = legacySchoolObject(abilities, bombardaId);
            Map.of("damageMultiplier", .8, "radius", 1.5, "secondaryMultiplier", .5).forEach((key, former) -> {
                if (legacySchoolNumber(spell, key, -1) == former) {
                    spell.addProperty(key, defaults.ability(bombardaId, key, former));
                }
            });
        }
        global.addProperty("combatBalanceVersion", 1);
        abilities.add("magic_school_global", global);
        object.add("abilities", abilities);
        object.add("towers", towers);
        return GSON.toJson(object);
    }

    private static String migrateMagicSchoolImperio(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return json;
        JsonObject object = root.getAsJsonObject();
        JsonObject abilities = legacySchoolObject(object, "abilities");
        JsonObject spell = legacySchoolObject(abilities, "magic_school_spell_imperio");
        if (legacySchoolNumber(spell, "controlVersion", 0) >= 1) return json;
        if (legacySchoolNumber(spell, "controlTicks", -1) == 60) spell.addProperty("controlTicks", 40);
        spell.remove("cooldownTicks");
        spell.addProperty("controlVersion", 1);
        abilities.add("magic_school_spell_imperio", spell);
        object.add("abilities", abilities);
        return GSON.toJson(object);
    }

    private static JsonObject legacySchoolObject(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return new JsonObject();
        if (!value.isJsonObject()) throw new IllegalArgumentException("Invalid legacy Magic School object: " + key);
        return value.getAsJsonObject();
    }

    private static double legacySchoolNumber(JsonObject object, String key, double fallback) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return fallback;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Invalid legacy Magic School number: " + key);
        }
        double number = value.getAsDouble();
        if (!Double.isFinite(number) || number < 0) {
            throw new IllegalArgumentException("Invalid legacy Magic School number: " + key);
        }
        return number;
    }

    private static String migrateLegacyHogwartsHealth(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject() || !root.getAsJsonObject().has("towers")
                || !root.getAsJsonObject().get("towers").isJsonObject()) {
            return json;
        }
        JsonObject towers = root.getAsJsonObject().getAsJsonObject("towers");
        if (towers.has("magic_school_hogwarts_t2") || towers.has("magic_school_hogwarts_t3")
                || !towers.has("magic_school_hogwarts_t1")
                || !towers.get("magic_school_hogwarts_t1").isJsonObject()) {
            return json;
        }
        JsonObject school = towers.getAsJsonObject("magic_school_hogwarts_t1");
        JsonElement health = school.get("maxHealth");
        if (health == null || !health.isJsonPrimitive() || !health.getAsJsonPrimitive().isNumber()
                || health.getAsDouble() != 300.0) {
            return json;
        }
        school.addProperty("maxHealth", 1.0);
        return GSON.toJson(root);
    }

    private static String migrateLegacyVillagerAdvBuffs(String json, TowerBalanceConfig defaults) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            return json;
        }
        JsonObject object = root.getAsJsonObject();
        if (!object.has("villagerAdv") || !object.get("villagerAdv").isJsonObject()) {
            return json;
        }
        JsonObject villagerAdv = object.getAsJsonObject("villagerAdv");
        if (!villagerAdv.has("buffs") || !villagerAdv.get("buffs").isJsonObject()) {
            return json;
        }
        JsonObject buffs = villagerAdv.getAsJsonObject("buffs");
        boolean legacyFlatBuffs = buffs.entrySet().stream().anyMatch(entry -> !entry.getValue().isJsonObject());
        if (!legacyFlatBuffs) {
            return json;
        }
        villagerAdv.add("buffs", GSON.toJsonTree(defaults.villagerAdv().buffs()));
        return GSON.toJson(object);
    }

    private static SummonConfig loadOrCreateSummons(Path path, SummonConfig defaults, Logger logger) {
        if (Files.notExists(path)) {
            write(path, defaults, logger);
            return defaults;
        }

        SummonConfig loaded;
        try (Reader reader = Files.newBufferedReader(path)) {
            SummonConfig value = GSON.fromJson(reader, SummonConfig.class);
            loaded = value == null ? defaults : value;
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            logger.warn("Failed to load config {}; using defaults.", path, exception);
            return defaults;
        }
        SummonConfig merged = loaded.withMissingDefaults(defaults);
        if (!merged.equals(loaded)) {
            write(path, merged, logger);
        }
        return merged;
    }

    private static VfxConfig loadOrCreateVfx(Path path, VfxConfig defaults, Logger logger) {
        if (Files.notExists(path)) {
            write(path, defaults, logger);
            return defaults;
        }

        VfxConfig loaded;
        try (Reader reader = Files.newBufferedReader(path)) {
            loaded = GSON.fromJson(reader, VfxConfig.class);
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            logger.warn("Failed to load config {}; using defaults.", path, exception);
            write(path, defaults, logger);
            return defaults;
        }
        VfxConfig value = (loaded == null ? defaults : loaded).normalized();
        if (loaded == null || !value.equals(loaded)) {
            logger.warn("Normalized invalid or missing VFX config values in {}.", path);
            write(path, value, logger);
        }
        return value;
    }

    private static TipConfig loadOrCreateTips(Path path, TipConfig defaults, Logger logger) {
        if (Files.notExists(path)) {
            write(path, defaults, logger);
            return defaults;
        }

        try {
            String json = Files.readString(path);
            ConfigJsonProperties properties = ConfigJsonProperties.parse(json);
            TipConfig loaded = GSON.fromJson(json, TipConfig.class);
            TipConfig safeLoaded = loaded == null ? defaults : loaded;
            boolean enabledMissing = !properties.has("enabled");
            boolean joinEnabledMissing = !properties.has("joinEnabled");
            boolean joinMessageMissing = !properties.has("joinMessage");
            boolean intervalMissing = !properties.has("intervalSeconds");
            boolean messagesMissing = !properties.has("messages");
            TipConfig value = new TipConfig(
                    enabledMissing ? defaults.enabled() : safeLoaded.enabled(),
                    joinEnabledMissing ? defaults.joinEnabled() : safeLoaded.joinEnabled(),
                    joinMessageMissing ? defaults.joinMessage() : safeLoaded.joinMessage(),
                    intervalMissing ? defaults.intervalSeconds() : safeLoaded.intervalSeconds(),
                    messagesMissing ? defaults.messages() : safeLoaded.messages()
            );
            if (loaded == null || enabledMissing || joinEnabledMissing || joinMessageMissing
                    || intervalMissing || messagesMissing || !value.equals(loaded)) {
                write(path, value, logger);
            }
            return value;
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            logger.warn("Failed to load config {}; using defaults.", path, exception);
            write(path, defaults, logger);
            return defaults;
        }
    }

    private static boolean write(Path path, Object value, Logger logger) {
        Path temporary = null;
        try {
            Path absolute = path.toAbsolutePath();
            Path parent = absolute.getParent();
            if (parent == null) {
                throw new IOException("Config path has no parent directory: " + path);
            }
            temporary = Files.createTempFile(
                    parent,
                    absolute.getFileName().toString(),
                    ".tmp"
            );
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(value, writer);
            }
            try {
                Files.move(
                        temporary,
                        absolute,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temporary,
                        absolute,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
            return true;
        } catch (IOException exception) {
            logger.warn("Failed to write config {}.", path, exception);
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException exception) {
                    logger.warn("Failed to remove temporary config file {}.", temporary, exception);
                }
            }
        }
    }

    public record LoadedConfigs(
            EconomyConfig economy,
            WaveConfig waves,
            MapConfig map,
            ProgressionConfig progression,
            RatingConfig rating,
            SemionPersistenceConfig persistence,
            JobAvailabilityConfig jobAvailability,
            TowerBalanceConfig towerBalance,
            SummonConfig summons,
            LeaderTargetingConfig leaderTargeting,
            IncomeLaneRoutingConfig incomeLaneRouting,
            MonsterScalingConfig monsterScaling,
            VfxConfig vfx,
            TipConfig tips,
            TraitSelectionConfig traits,
            TraitBalanceConfig traitBalance,
            WebIntegrationConfig webIntegration,
            CombatSpeedConfig combatSpeed,
            AugmentConfig augments
    ) {
    }
}
