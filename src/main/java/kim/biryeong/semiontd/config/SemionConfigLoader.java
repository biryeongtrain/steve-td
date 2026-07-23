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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import kim.biryeong.semiontd.persistence.SemionPersistenceConfig;
import kim.biryeong.semiontd.rating.RatingConfig;
import kim.biryeong.semiontd.tower.end.EndTower;
import kim.biryeong.semiontd.tower.end.EndTowers;
import kim.biryeong.semiontd.trait.TraitSelectionConfig;
import org.slf4j.Logger;

public final class SemionConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private SemionConfigLoader() {
    }

    public static LoadedConfigs load(Path configDir, Logger logger) {
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
                    TowerBalanceConfig.defaultConfig(),
                    SummonConfig.defaultConfig(),
                    LeaderTargetingConfig.defaultConfig(),
                    IncomeLaneRoutingConfig.defaultConfig(),
                    MonsterScalingConfig.defaultConfig(),
                    VfxConfig.defaultConfig(),
                    TipConfig.defaultConfig(),
                    TraitSelectionConfig.defaultConfig(),
                    TraitBalanceConfig.defaultConfig()
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
        TowerBalanceConfig towerBalance = loadOrCreateTowerBalance(
                configDir.resolve("tower_balance.json"),
                TowerBalanceConfig.defaultConfig(),
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
        return new LoadedConfigs(economy, waves, map, progression, rating, persistence, towerBalance, summons, leaderTargeting, incomeLaneRouting, monsterScaling, vfx, tips, traits, traitBalance);
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
            if (teamEloMatchmakingMissing) {
                value = value.withTeamEloMatchmakingEnabled(defaults.teamEloMatchmakingEnabled());
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
            boolean killRewardMissing = !hasObjectProperty(json, "killReward");
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
            if (towerLimitMissing || towerLimitPurchaseMissing || killRewardMissing || teamTransferMissing
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
            Logger logger
    ) {
        if (Files.notExists(path)) {
            write(path, defaults, logger);
            return defaults;
        }

        try {
            String json = Files.readString(path);
            String migratedJson = migrateLegacyVillagerAdvBuffs(json, defaults);
            migratedJson = migrateLegacyEndUpgradeCosts(migratedJson, defaults);
            migratedJson = migrateLegacyEndBalanceDefaults(migratedJson, defaults);
            migratedJson = migrateLegacyEndDragonDamage(migratedJson, defaults);
            migratedJson = migrateLegacyEndDragonAttackInterval(migratedJson, defaults);
            migratedJson = migrateLegacyEndShulkerDamageReduction(migratedJson, defaults);
            TowerBalanceConfig value = GSON.fromJson(migratedJson, TowerBalanceConfig.class);
            TowerBalanceConfig loaded = value == null ? defaults : value;
            TowerBalanceConfig merged = loaded.withMissingDefaults(defaults);
            boolean illusionCloneQueueMissing = !hasObjectProperty(migratedJson, "illusionCloneQueue");
            boolean villagerAdvMissing = !hasObjectProperty(migratedJson, "villagerAdv");
            if (!migratedJson.equals(json) || illusionCloneQueueMissing || villagerAdvMissing || !merged.equals(loaded)) {
                write(path, merged, logger);
            }
            return merged;
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            logger.warn("Failed to load config {}; using defaults.", path, exception);
            return defaults;
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

        try (Reader reader = Files.newBufferedReader(path)) {
            TraitBalanceConfig value = GSON.fromJson(reader, TraitBalanceConfig.class);
            TraitBalanceConfig loaded = value == null ? defaults : value;
            TraitBalanceConfig merged = loaded.withMissingDefaults(defaults);
            if (!merged.equals(loaded)) {
                write(path, merged, logger);
            }
            return merged;
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            logger.warn("Failed to load config {}; using defaults.", path, exception);
            return defaults;
        }
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

    private static String migrateLegacyEndUpgradeCosts(String json, TowerBalanceConfig defaults) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            return json;
        }
        JsonObject object = root.getAsJsonObject();
        if (!object.has("upgradeCosts") || !object.get("upgradeCosts").isJsonObject()) {
            return json;
        }
        JsonObject upgradeCosts = object.getAsJsonObject("upgradeCosts");
        boolean changed = migrateLegacyUpgradeCost(
                upgradeCosts,
                TowerBalanceConfig.upgradeKey(EndTowers.T1_ENDERMITE_TOWER.id(), EndTowers.T2_ENDERMAN_TOWER.id()),
                defaults,
                75L,
                100L,
                125L
        );
        changed |= migrateLegacyUpgradeCost(
                upgradeCosts,
                TowerBalanceConfig.upgradeKey(EndTowers.T2_ENDERMAN_TOWER.id(), EndTowers.T3_END_CRYSTAL_TOWER.id()),
                defaults,
                75L,
                150L,
                200L
        );
        changed |= migrateLegacyUpgradeCost(
                upgradeCosts,
                TowerBalanceConfig.upgradeKey(EndTowers.T1_SHULKER_TOWER.id(), EndTowers.T2_SHULKER_TOWER.id()),
                defaults,
                75L,
                100L,
                125L
        );
        changed |= migrateLegacyUpgradeCost(
                upgradeCosts,
                TowerBalanceConfig.upgradeKey(EndTowers.T2_SHULKER_TOWER.id(), EndTowers.T3_SHULKER_TOWER.id()),
                defaults,
                75L,
                150L,
                200L
        );
        if (object.has("abilities") && object.get("abilities").isJsonObject()) {
            JsonObject abilities = object.getAsJsonObject("abilities");
            if (abilities.has(EndTower.CONFIG_ID) && abilities.get(EndTower.CONFIG_ID).isJsonObject()) {
                JsonObject endAbilities = abilities.getAsJsonObject(EndTower.CONFIG_ID);
                changed |= migrateLegacyAbilityKey(
                        endAbilities,
                        "shulkerSplashEvery",
                        "endCrystalSplashEvery"
                );
                changed |= migrateLegacyAbilityKey(
                        endAbilities,
                        "shulkerAttackRangeEvery",
                        "endCrystalSplashEvery"
                );
                changed |= migrateLegacyAbilityKey(
                        endAbilities,
                        "attackRangePerStep",
                        "splashRadiusPerStep"
                );
                if (endAbilities.remove("endermanAttackIntervalEvery") != null) {
                    changed = true;
                }
                if (endAbilities.remove("endermanLifeStealEvery") != null) {
                    changed = true;
                }
            }
        }
        return changed ? GSON.toJson(object) : json;
    }

    private static String migrateLegacyEndDragonDamage(String json, TowerBalanceConfig defaults) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            return json;
        }
        JsonObject object = root.getAsJsonObject();
        if (!object.has("towers") || !object.get("towers").isJsonObject()) {
            return json;
        }
        JsonObject towers = object.getAsJsonObject("towers");
        String towerId = EndTowers.BASE_END_TOWER.id();
        if (!towers.has(towerId) || !towers.get(towerId).isJsonObject()) {
            return json;
        }
        JsonObject enderDragon = towers.getAsJsonObject(towerId);
        JsonElement configuredDamage = enderDragon.get("damage");
        if (configuredDamage == null
                || !configuredDamage.isJsonPrimitive()
                || !configuredDamage.getAsJsonPrimitive().isNumber()
                || Math.abs(configuredDamage.getAsDouble() - 5.0) > 1.0E-9) {
            return json;
        }
        TowerBalanceConfig.TowerStats defaultStats = defaults.towers().get(towerId);
        if (defaultStats == null || defaultStats.damage() == null) {
            return json;
        }
        enderDragon.addProperty("damage", defaultStats.damage());
        return GSON.toJson(object);
    }

    private static String migrateLegacyEndDragonAttackInterval(String json, TowerBalanceConfig defaults) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            return json;
        }
        JsonObject object = root.getAsJsonObject();
        if (!object.has("towers") || !object.get("towers").isJsonObject()) {
            return json;
        }
        JsonObject towers = object.getAsJsonObject("towers");
        String towerId = EndTowers.BASE_END_TOWER.id();
        if (!towers.has(towerId) || !towers.get(towerId).isJsonObject()) {
            return json;
        }
        JsonObject endDragon = towers.getAsJsonObject(towerId);
        JsonElement configuredInterval = endDragon.get("attackIntervalTicks");
        if (configuredInterval == null
                || !configuredInterval.isJsonPrimitive()
                || !configuredInterval.getAsJsonPrimitive().isNumber()
                || configuredInterval.getAsInt() != 20) {
            return json;
        }
        TowerBalanceConfig.TowerStats defaultStats = defaults.towers().get(towerId);
        if (defaultStats == null || defaultStats.attackIntervalTicks() == null) {
            return json;
        }
        endDragon.addProperty("attackIntervalTicks", defaultStats.attackIntervalTicks());
        return GSON.toJson(object);
    }

    private static String migrateLegacyEndBalanceDefaults(String json, TowerBalanceConfig defaults) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            return json;
        }
        JsonObject object = root.getAsJsonObject();
        if (!object.has("abilities") || !object.get("abilities").isJsonObject()) {
            return json;
        }
        JsonObject abilities = object.getAsJsonObject("abilities");
        if (!abilities.has(EndTower.CONFIG_ID) || !abilities.get(EndTower.CONFIG_ID).isJsonObject()) {
            return json;
        }
        JsonObject endAbilities = abilities.getAsJsonObject(EndTower.CONFIG_ID);
        boolean changed = migrateLegacyAbilityDefault(endAbilities, defaults, "absorptionDurationTicks", 400.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "roundAbsorptionAttackIntervalEvery", 2.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "endCrystalAttackIntervalEvery", 20.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "maxAttackIntervalReductionTicks", 15.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "shulkerReductionEvery", 20.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "splashRadiusPerStep", 0.25);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "splashDamageRatio", 1.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "lifeStealCap", 0.30);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "lifeStealCap", 0.20);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "endCrystalAttackIntervalEvery", 15.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "endCrystalAttackRangeEvery", 40.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "endCrystalAttackRangeEvery", 20.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "attackRangePerStep", 1.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "attackRangeCap", 5.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "shulkerLifeStealEvery", 10.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "shulkerLifeStealEvery", 15.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "regenerationPerStep", 5.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "regenerationCap", 50.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "regenerationCap", 15.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "endCrystalSplashEvery", 10.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "splashRadiusPerStep", 0.5);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "splashRadiusCap", 5.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "shulkerReductionEvery", 15.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "damageReductionPerStep", 0.025);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "damageReductionCap", 0.25);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "dragonIncomeDebuffResistance", 0.25);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "dragonFinalDamageBonus", 0.25);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "dragonFinalDamageBonus", 0.30);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "dragonFinalDamageBonus", 0.15);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "dragonDamageBonus", 0.20);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "dragonDamageBonus", 0.25);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "dragonDamageBonus", 0.15);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "dragonIncomeDebuffResistance", 0.05);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "roundHealthRatio", 1.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "roundDamageRatio", 1.0);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "roundHealthRatio", 0.50);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "roundDamageRatio", 0.50);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "roundHealthRatio", 0.40);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "roundDamageRatio", 0.40);
        changed |= migrateLegacyAbilityDefault(endAbilities, defaults, "absorptionHealAmount", 50.0);
        if (endAbilities.remove("endCrystalSplashEvery") != null) {
            changed = true;
        }
        if (endAbilities.remove("splashRadiusPerStep") != null) {
            changed = true;
        }
        if (endAbilities.remove("roundStatBonusCapRatio") != null) {
            changed = true;
        }
        return changed ? GSON.toJson(object) : json;
    }

    private static boolean migrateLegacyAbilityDefault(
            JsonObject abilities,
            TowerBalanceConfig defaults,
            String key,
            double legacyValue
    ) {
        JsonElement configured = abilities.get(key);
        if (configured == null
                || !configured.isJsonPrimitive()
                || !configured.getAsJsonPrimitive().isNumber()
                || Double.compare(configured.getAsDouble(), legacyValue) != 0) {
            return false;
        }
        double defaultValue = defaults.ability(EndTower.CONFIG_ID, key, legacyValue);
        if (Double.compare(defaultValue, legacyValue) == 0) {
            return false;
        }
        abilities.addProperty(key, defaultValue);
        return true;
    }

    private static String migrateLegacyEndShulkerDamageReduction(String json, TowerBalanceConfig defaults) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            return json;
        }
        JsonObject object = root.getAsJsonObject();
        if (!object.has("abilities") || !object.get("abilities").isJsonObject()) {
            return json;
        }
        JsonObject abilities = object.getAsJsonObject("abilities");
        boolean changed = false;
        if (abilities.has(EndTower.CONFIG_ID) && abilities.get(EndTower.CONFIG_ID).isJsonObject()) {
            JsonObject endAbilities = abilities.getAsJsonObject(EndTower.CONFIG_ID);
            if (endAbilities.remove("hatchDelayTicks") != null) {
                changed = true;
            }
        }
        changed |= migrateLegacyTowerAbilityValue(
                abilities,
                EndTowers.T2_SHULKER_TOWER.id(),
                "damageReduction",
                0.15,
                defaults
        );
        changed |= migrateLegacyTowerAbilityValue(
                abilities,
                EndTowers.T3_SHULKER_TOWER.id(),
                "damageReduction",
                0.20,
                defaults
        );
        return changed ? GSON.toJson(object) : json;
    }

    private static boolean migrateLegacyTowerAbilityValue(
            JsonObject abilities,
            String towerId,
            String key,
            double legacyValue,
            TowerBalanceConfig defaults
    ) {
        if (!abilities.has(towerId) || !abilities.get(towerId).isJsonObject()) {
            return false;
        }
        JsonObject towerAbilities = abilities.getAsJsonObject(towerId);
        JsonElement configured = towerAbilities.get(key);
        if (configured == null
                || !configured.isJsonPrimitive()
                || !configured.getAsJsonPrimitive().isNumber()
                || Math.abs(configured.getAsDouble() - legacyValue) > 1.0E-9) {
            return false;
        }
        Double defaultValue = defaults.abilities().getOrDefault(towerId, Map.of()).get(key);
        if (defaultValue == null) {
            return false;
        }
        towerAbilities.addProperty(key, defaultValue);
        return true;
    }

    private static boolean migrateLegacyUpgradeCost(
            JsonObject upgradeCosts,
            String upgradeKey,
            TowerBalanceConfig defaults,
            long... legacyCosts
    ) {
        JsonElement configured = upgradeCosts.get(upgradeKey);
        if (configured == null
                || !configured.isJsonPrimitive()
                || !configured.getAsJsonPrimitive().isNumber()) {
            return false;
        }
        long configuredCost = configured.getAsLong();
        boolean legacyCost = false;
        for (long candidate : legacyCosts) {
            if (configuredCost == candidate) {
                legacyCost = true;
                break;
            }
        }
        if (!legacyCost) {
            return false;
        }
        Long defaultCost = defaults.upgradeCosts().get(upgradeKey);
        if (defaultCost == null || defaultCost == configuredCost) {
            return false;
        }
        upgradeCosts.addProperty(upgradeKey, defaultCost);
        return true;
    }

    private static boolean migrateLegacyAbilityKey(
            JsonObject abilities,
            String legacyKey,
            String replacementKey
    ) {
        JsonElement legacyValue = abilities.remove(legacyKey);
        if (legacyValue == null) {
            return false;
        }
        if (!abilities.has(replacementKey)) {
            abilities.add(replacementKey, legacyValue);
        }
        return true;
    }

    private static SummonConfig loadOrCreateSummons(Path path, SummonConfig defaults, Logger logger) {
        if (Files.notExists(path)) {
            write(path, defaults, logger);
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            SummonConfig value = GSON.fromJson(reader, SummonConfig.class);
            SummonConfig loaded = value == null ? defaults : value;
            SummonConfig merged = loaded.withMissingDefaults(defaults);
            if (!merged.equals(loaded)) {
                write(path, merged, logger);
            }
            return merged;
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            logger.warn("Failed to load config {}; using defaults.", path, exception);
            return defaults;
        }
    }

    private static VfxConfig loadOrCreateVfx(Path path, VfxConfig defaults, Logger logger) {
        if (Files.notExists(path)) {
            write(path, defaults, logger);
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            VfxConfig loaded = GSON.fromJson(reader, VfxConfig.class);
            VfxConfig value = (loaded == null ? defaults : loaded).normalized();
            if (loaded == null || !value.equals(loaded)) {
                logger.warn("Normalized invalid or missing VFX config values in {}.", path);
                write(path, value, logger);
            }
            return value;
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            logger.warn("Failed to load config {}; using defaults.", path, exception);
            write(path, defaults, logger);
            return defaults;
        }
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

    private static void write(Path path, Object value, Logger logger) {
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(value, writer);
        } catch (IOException exception) {
            logger.warn("Failed to write default config {}.", path, exception);
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
            TowerBalanceConfig towerBalance,
            SummonConfig summons,
            LeaderTargetingConfig leaderTargeting,
            IncomeLaneRoutingConfig incomeLaneRouting,
            MonsterScalingConfig monsterScaling,
            VfxConfig vfx,
            TipConfig tips,
            TraitSelectionConfig traits,
            TraitBalanceConfig traitBalance
    ) {
    }
}
