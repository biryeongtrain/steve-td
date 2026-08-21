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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import kim.biryeong.semiontd.persistence.SemionPersistenceConfig;
import kim.biryeong.semiontd.rating.RatingConfig;
import kim.biryeong.semiontd.trait.TraitSelectionConfig;
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
                    WaveConfig.defaultConfig(),
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
                    CombatSpeedConfig.defaultConfig()
            );
        }

        EconomyConfig economy = loadOrCreateEconomy(
                configDir.resolve("economy.json"),
                EconomyConfig.defaultConfig(),
                logger
        );
        WaveConfig waves = loadOrCreateWithLegacy(
                configDir.resolve("wave.json"),
                configDir.resolve("waves.json"),
                WaveConfig.defaultConfig(),
                WaveConfig.class,
                logger
        );
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
        return new LoadedConfigs(economy, waves, map, progression, rating, persistence, jobAvailability, towerBalance, summons, leaderTargeting, incomeLaneRouting, monsterScaling, vfx, tips, traits, traitBalance, webIntegration, combatSpeed);
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

    private static <T> T loadOrCreateWithLegacy(Path preferred, Path legacy, T defaults, Class<T> type, Logger logger) {
        if (Files.exists(preferred)) {
            return loadOrCreate(preferred, defaults, type, logger);
        }
        if (Files.exists(legacy)) {
            return loadOrCreate(legacy, defaults, type, logger);
        }
        write(preferred, defaults, logger);
        return defaults;
    }

    private static RatingConfig loadOrCreateRating(Path path, RatingConfig defaults, Logger logger) {
        if (Files.notExists(path)) {
            write(path, defaults, logger);
            return defaults;
        }

        try {
            String json = Files.readString(path);
            RatingConfig loaded = GSON.fromJson(json, RatingConfig.class);
            RatingConfig value = loaded == null ? defaults : loaded;
            boolean teamEloMatchmakingMissing = !hasObjectProperty(json, "teamEloMatchmakingEnabled");
            boolean perfectDefenseLossMultiplierMissing = !hasObjectProperty(json, "perfectDefenseLossMultiplier");
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
            EconomyConfig loaded = GSON.fromJson(json, EconomyConfig.class);
            EconomyConfig value = loaded == null ? defaults : loaded;
            boolean towerLimitMissing = !hasObjectProperty(json, "towerLimit");
            boolean towerLimitPurchaseMissing = !towerLimitMissing
                    && !hasAllNestedObjectProperties(
                    json,
                    "towerLimit",
                    "initialPurchaseDiamondCost",
                    "purchaseDiamondCostIncrease",
                    "initialPurchaseEmeraldCost",
                    "purchaseEmeraldCostIncrease"
            );
            boolean teamTransferMissing = !hasObjectProperty(json, "teamTransfer");
            boolean teamTransferEnabledMissing = teamTransferMissing
                    || !hasNestedObjectProperty(json, "teamTransfer", "enabled");
            boolean teamTransferCooldownMissing = teamTransferMissing
                    || !hasNestedObjectProperty(json, "teamTransfer", "receiveCooldownRounds");
            boolean teamTransferMaxMissing = teamTransferMissing
                    || !hasNestedObjectProperty(json, "teamTransfer", "maxDiamondPerRound");
            boolean emeraldIncomeBoostMissing = !hasObjectProperty(json, "emeraldIncomeBoost");
            boolean emeraldIncomeBoostEnabledMissing = emeraldIncomeBoostMissing
                    || !hasNestedObjectProperty(json, "emeraldIncomeBoost", "enabled");
            boolean emeraldIncomeBoostStartRoundMissing = emeraldIncomeBoostMissing
                    || !hasNestedObjectProperty(json, "emeraldIncomeBoost", "startRound");
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
            IncomeLaneRoutingConfig loaded = GSON.fromJson(json, IncomeLaneRoutingConfig.class);
            IncomeLaneRoutingConfig value = loaded == null ? defaults : loaded;
            boolean enabledMissing = !hasObjectProperty(json, "enabled");
            boolean modeMissing = !hasObjectProperty(json, "mode");
            boolean queuedThreatWeightMissing = !hasObjectProperty(json, "queuedThreatWeight");
            boolean nextRoundQueuedThreatWeightMissing = !hasObjectProperty(json, "nextRoundQueuedThreatWeight");
            boolean tieBreakModeMissing = !hasObjectProperty(json, "tieBreakMode");
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
            MonsterScalingConfig loaded = GSON.fromJson(json, MonsterScalingConfig.class);
            MonsterScalingConfig value = loaded == null ? defaults : loaded;
            boolean enabledMissing = !hasObjectProperty(json, "enabled");
            boolean survivalDelayMissing = !hasObjectProperty(json, "survivalDelayTicks");
            boolean laneBreachDelayMissing = !hasObjectProperty(json, "laneBreachDelayTicks");
            boolean intervalMissing = !hasObjectProperty(json, "intervalTicks");
            boolean healthGrowthMissing = !hasObjectProperty(json, "healthGrowthPercentPerInterval");
            boolean attackGrowthMissing = !hasObjectProperty(json, "attackDamageGrowthPercentPerInterval");
            boolean waveMissing = !hasObjectProperty(json, "scaleWaveMonsters");
            boolean incomeMissing = !hasObjectProperty(json, "scaleIncomeMonsters");
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
            String migratedJson = migrateLegacyVillagerAdvBuffs(json, defaults);
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
            boolean schemaVersionMissing = !hasObjectProperty(json, "schemaVersion");
            boolean illusionCloneQueueMissing = !hasObjectProperty(migratedJson, "illusionCloneQueue");
            boolean villagerAdvMissing = !hasObjectProperty(migratedJson, "villagerAdv");
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
            TipConfig loaded = GSON.fromJson(json, TipConfig.class);
            TipConfig safeLoaded = loaded == null ? defaults : loaded;
            boolean enabledMissing = !hasObjectProperty(json, "enabled");
            boolean joinEnabledMissing = !hasObjectProperty(json, "joinEnabled");
            boolean joinMessageMissing = !hasObjectProperty(json, "joinMessage");
            boolean intervalMissing = !hasObjectProperty(json, "intervalSeconds");
            boolean messagesMissing = !hasObjectProperty(json, "messages");
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

    private static boolean hasObjectProperty(String json, String key) {
        try {
            if (!(JsonParser.parseString(json) instanceof JsonObject object)) {
                return false;
            }
            return object.has(key) && !object.get(key).isJsonNull();
        } catch (JsonParseException exception) {
            return false;
        }
    }

    private static boolean hasNestedObjectProperty(String json, String parentKey, String childKey) {
        try {
            if (!(JsonParser.parseString(json) instanceof JsonObject object)) {
                return false;
            }
            if (!object.has(parentKey) || !object.get(parentKey).isJsonObject()) {
                return false;
            }
            JsonObject parent = object.getAsJsonObject(parentKey);
            return parent.has(childKey) && !parent.get(childKey).isJsonNull();
        } catch (JsonParseException exception) {
            return false;
        }
    }

    private static boolean hasAllNestedObjectProperties(String json, String parentKey, String... childKeys) {
        try {
            if (!(JsonParser.parseString(json) instanceof JsonObject object)) {
                return false;
            }
            if (!object.has(parentKey) || !object.get(parentKey).isJsonObject()) {
                return false;
            }
            JsonObject parent = object.getAsJsonObject(parentKey);
            for (String childKey : childKeys) {
                if (!parent.has(childKey) || parent.get(childKey).isJsonNull()) {
                    return false;
                }
            }
            return true;
        } catch (JsonParseException exception) {
            return false;
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
            CombatSpeedConfig combatSpeed
    ) {
    }
}
