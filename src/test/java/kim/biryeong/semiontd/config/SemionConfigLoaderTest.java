package kim.biryeong.semiontd.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import kim.biryeong.semiontd.config.SemionConfigLoader.LoadedConfigs;
import kim.biryeong.semiontd.rating.RatingConfig;
import kim.biryeong.semiontd.tower.end.EndTowers;
import kim.biryeong.semiontd.tower.illager.IllagerRaidStates;
import kim.biryeong.semiontd.tower.illager.IllagerTowers;
import kim.biryeong.semiontd.tower.legion.LegionTowers;
import kim.biryeong.semiontd.trait.TraitSelectionConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

final class SemionConfigLoaderTest {
    @TempDir
    Path tempDir;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void loadCreatesTraitConfigFileWithEnabledDefaults() {
        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertTrue(Files.exists(tempDir.resolve("traits.json")));
        assertEquals(TraitSelectionConfig.defaultConfig(), configs.traits());
    }

    @Test
    void loadReadsTraitConfigOverrides() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("traits.json"), """
                {
                  "enabled": false,
                  "selectionDurationSeconds": 30
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertEquals(false, configs.traits().enabled());
        assertEquals(30, configs.traits().selectionDurationSeconds());
    }

    @Test
    void loadCreatesTraitBalanceConfigWithDefaults() {
        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertTrue(Files.exists(tempDir.resolve("trait_balance.json")));
        assertEquals(0.15, configs.traitBalance().value("opening_salvo", "attackSpeedBonus", -1.0));
        assertEquals(15.0, configs.traitBalance().value("opening_salvo", "durationSeconds", -1.0));
    }

    @Test
    void loadBackfillsTraitBalanceDefaultsWithoutReplacingOverrides() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("trait_balance.json"), """
                {
                  "traits": {
                    "opening_salvo": {
                      "attackSpeedBonus": 0.12
                    }
                  }
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertEquals(0.12, configs.traitBalance().value("opening_salvo", "attackSpeedBonus", -1.0));
        assertEquals(15.0, configs.traitBalance().value("opening_salvo", "durationSeconds", -1.0));
        assertEquals(150.0, configs.traitBalance().value("mobilization_grant", "startingDiamond", -1.0));
        String written = Files.readString(tempDir.resolve("trait_balance.json"));
        assertTrue(written.contains("durationSeconds"));
        assertTrue(written.contains("mobilization_grant"));
    }

    @Test
    void loadCreatesRatingConfigFileWithDefaults() {
        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertTrue(Files.exists(tempDir.resolve("rating.json")));
        assertEquals(RatingConfig.defaultConfig(), configs.rating());
    }

    @Test
    void loadReadsRatingConfigOverrides() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("rating.json"), """
                {
                  "enabled": true,
                  "teamEloMatchmakingEnabled": false,
                  "eloKFactor": 48.0,
                  "initialDisplayElo": 1200,
                  "initialMu": 1200.0,
                  "initialSigma": 250.0,
                  "leaderboardLimit": 25,
                  "minimumParticipants": 2,
                  "excludeSpectators": true,
                  "contributionWeightingEnabled": false,
                  "contributionMultiplierMin": 0.9,
                  "contributionMultiplierMax": 1.1,
                  "defenseContributionWeight": 0.25,
                  "pressureContributionWeight": 0.25,
                  "economyContributionWeight": 0.25,
                  "assistContributionWeight": 0.25
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertEquals(48.0, configs.rating().eloKFactor());
        assertEquals(1200, configs.rating().initialDisplayElo());
        assertEquals(25, configs.rating().leaderboardLimit());
        assertEquals(false, configs.rating().teamEloMatchmakingEnabled());
        assertEquals(false, configs.rating().contributionWeightingEnabled());
    }

    @Test
    void loadBackfillsRatingTeamEloMatchmakingDefault() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("rating.json"), """
                {
                  "enabled": true,
                  "eloKFactor": 32.0,
                  "initialDisplayElo": 1500,
                  "initialMu": 1500.0,
                  "initialSigma": 350.0,
                  "leaderboardLimit": 10,
                  "minimumParticipants": 2,
                  "excludeSpectators": true,
                  "contributionWeightingEnabled": true,
                  "contributionMultiplierMin": 0.85,
                  "contributionMultiplierMax": 1.15,
                  "defenseContributionWeight": 0.4,
                  "pressureContributionWeight": 0.25,
                  "economyContributionWeight": 0.2,
                  "assistContributionWeight": 0.15
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertEquals(true, configs.rating().teamEloMatchmakingEnabled());
        String written = Files.readString(tempDir.resolve("rating.json"));
        assertTrue(written.contains("teamEloMatchmakingEnabled"));
    }

    @Test
    void loadBackfillsTowerBalanceDefaultsWithoutReplacingOverrides() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("tower_balance.json"), """
                {
                  "towers": {
                    "t1_goat_tower": {
                      "mineralCost": 99
                    }
                  },
                  "upgradeCosts": {
                  },
                  "abilities": {
                    "t3_extreme_goat_tower": {
                      "maxStacks": 2.0
                    }
                  }
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        TowerBalanceConfig towerBalance = configs.towerBalance();
        assertEquals(99, towerBalance.towers().get(LegionTowers.T1_GOAT_TOWER.id()).mineralCost());
        assertEquals(70.0, towerBalance.towers().get(LegionTowers.T1_GOAT_TOWER.id()).maxHealth());
        assertTrue(towerBalance.towers().containsKey(LegionTowers.T2_STRONG_GOAT_TOWER.id()));
        assertEquals(150, towerBalance.upgradeCost(
                LegionTowers.T1_GOAT_TOWER.id(),
                LegionTowers.T2_STRONG_GOAT_TOWER.id(),
                0
        ));
        assertEquals(2.0, towerBalance.abilities().get(LegionTowers.T3_EXTREME_GOAT_TOWER.id()).get("maxStacks"));
        assertEquals(0.065, towerBalance.abilities().get(LegionTowers.T3_EXTREME_GOAT_TOWER.id()).get("cloneDamageBonus"));
        String written = Files.readString(tempDir.resolve("tower_balance.json"));
        assertTrue(written.contains("t2_strong_goat_tower"));
        assertTrue(written.contains("cloneDamageBonus"));
    }

    @Test
    void loadMigratesLegacyEndUpgradePricesToTargetTowerMineralCosts() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("tower_balance.json"), """
                {
                  "towers": {
                    "base_ender_dragon": {
                      "damage": 5.0,
                      "attackIntervalTicks": 20
                    }
                  },
                  "upgradeCosts": {
                    "t1_endermite_tower->t2_enderman_tower": 125,
                    "t2_enderman_tower->t3_end_crystal_tower": 200,
                    "t1_shulker_tower->t2_shulker_tower": 125,
                    "t2_shulker_tower->t3_shulker_tower": 200,
                    "t2_enderman_tower->t3_enderman_tower": 75
                  },
                  "abilities": {
                    "t2_shulker_tower": {
                      "damageReduction": 0.15
                    },
                    "t3_shulker_tower": {
                      "damageReduction": 0.20
                    },
                    "ender_global": {
                      "hatchDelayTicks": 200.0,
                      "absorptionDurationTicks": 400.0,
                      "roundAbsorptionAttackIntervalEvery": 2.0,
                      "endCrystalAttackIntervalEvery": 20.0,
                      "shulkerReductionEvery": 20.0,
                      "dragonFinalDamageBonus": 0.25,
                      "dragonIncomeDebuffResistance": 0.05,
                      "endermanAttackIntervalEvery": 10.0,
                      "endermanLifeStealEvery": 20.0,
                      "shulkerSplashEvery": 12.0,
                      "shulkerAttackRangeEvery": 14.0,
                      "attackRangePerStep": 0.5
                    }
                  }
                }
                """);

        TowerBalanceConfig balance = SemionConfigLoader.load(
                tempDir,
                LoggerFactory.getLogger("test")
        ).towerBalance();

        assertEquals(10.0, balance.towers().get(EndTowers.BASE_END_TOWER.id()).damage(), 0.0001);
        assertEquals(15, balance.towers().get(EndTowers.BASE_END_TOWER.id()).attackIntervalTicks());
        assertEquals(80, balance.upgradeCost(
                EndTowers.T1_ENDERMITE_TOWER.id(),
                EndTowers.T2_ENDERMAN_TOWER.id(),
                -1
        ));
        assertEquals(130, balance.upgradeCost(
                EndTowers.T2_ENDERMAN_TOWER.id(),
                EndTowers.T3_END_CRYSTAL_TOWER.id(),
                -1
        ));
        assertEquals(80, balance.upgradeCost(
                EndTowers.T1_SHULKER_TOWER.id(),
                EndTowers.T2_SHULKER_TOWER.id(),
                -1
        ));
        assertEquals(130, balance.upgradeCost(
                EndTowers.T2_SHULKER_TOWER.id(),
                EndTowers.T3_SHULKER_TOWER.id(),
                -1
        ));
        assertEquals(-1.0, balance.ability("ender_global", "endCrystalSplashEvery", -1.0), 0.0001);
        assertEquals(15.0, balance.ability("ender_global", "endCrystalSplashThreshold1", -1.0), 0.0001);
        assertEquals(60.0, balance.ability("ender_global", "endCrystalSplashThreshold2", -1.0), 0.0001);
        assertEquals(150.0, balance.ability("ender_global", "endCrystalSplashThreshold3", -1.0), 0.0001);
        assertEquals(300.0, balance.ability("ender_global", "endCrystalSplashThreshold4", -1.0), 0.0001);
        assertEquals(200.0, balance.ability("ender_global", "absorptionDurationTicks", -1.0), 0.0001);
        assertEquals(1.0, balance.ability("ender_global", "roundAbsorptionAttackIntervalEvery", -1.0), 0.0001);
        assertEquals(30.0, balance.ability("ender_global", "endCrystalAttackIntervalEvery", -1.0), 0.0001);
        assertEquals(0.30, balance.ability("ender_global", "dragonFinalDamageBonus", -1.0), 0.0001);
        assertEquals(0.10, balance.ability("ender_global", "dragonIncomeDebuffResistance", -1.0), 0.0001);
        assertEquals(60.0, balance.ability("ender_global", "shulkerReductionEvery", -1.0), 0.0001);
        assertEquals(0.5, balance.ability("ender_global", "attackRangePerStep", -1.0), 0.0001);
        assertEquals(15.0, balance.ability("ender_global", "shulkerLifeStealEvery", -1.0), 0.0001);
        assertEquals(-1.0, balance.ability("ender_global", "splashRadiusPerStep", -1.0), 0.0001);
        assertEquals(0.30, balance.ability(EndTowers.T2_SHULKER_TOWER.id(), "damageReduction", -1.0), 0.0001);
        assertEquals(0.50, balance.ability(EndTowers.T3_SHULKER_TOWER.id(), "damageReduction", -1.0), 0.0001);
        String written = Files.readString(tempDir.resolve("tower_balance.json"));
        assertTrue(written.contains("\"damage\": 10.0"));
        assertTrue(!written.contains("t2_enderman_tower->t3_enderman_tower"));
        assertTrue(!written.contains("endermanAttackIntervalEvery"));
        assertTrue(!written.contains("endermanLifeStealEvery"));
        assertTrue(!written.contains("hatchDelayTicks"));
        assertTrue(!written.contains("shulkerSplashEvery"));
        assertTrue(!written.contains("shulkerAttackRangeEvery"));
        assertTrue(!written.contains("endCrystalSplashEvery"));
        assertTrue(written.contains("attackRangePerStep"));
    }

    @Test
    void loadMigratesPreviousEndSplashAndLifeStealDefaults() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("tower_balance.json"), """
                {
                  "abilities": {
                    "ender_global": {
                      "splashRadiusPerStep": 0.25,
                      "splashDamageRatio": 1.0,
                      "lifeStealCap": 0.30
                    }
                  }
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        TowerBalanceConfig balance = configs.towerBalance();
        assertEquals(15.0, balance.ability("ender_global", "endCrystalSplashThreshold1", -1.0), 0.0001);
        assertEquals(60.0, balance.ability("ender_global", "endCrystalSplashThreshold2", -1.0), 0.0001);
        assertEquals(150.0, balance.ability("ender_global", "endCrystalSplashThreshold3", -1.0), 0.0001);
        assertEquals(300.0, balance.ability("ender_global", "endCrystalSplashThreshold4", -1.0), 0.0001);
        assertEquals(-1.0, balance.ability("ender_global", "splashRadiusPerStep", -1.0), 0.0001);
        assertEquals(0.60, balance.ability("ender_global", "splashDamageRatio", -1.0), 0.0001);
        assertEquals(0.20, balance.ability("ender_global", "lifeStealCap", -1.0), 0.0001);
    }

    @Test
    void loadBackfillsIllagerTowerBalanceDefaultsIntoExistingConfigFile() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("tower_balance.json"), """
                {
                  "towers": {
                    "t1_goat_tower": {
                      "mineralCost": 99,
                      "maxHealth": 70.0,
                      "range": 3.0,
                      "damage": 8.0,
                      "attackIntervalTicks": 18,
                      "aggroPriority": 35
                    }
                  },
                  "upgradeCosts": {
                  },
                  "abilities": {
                    "t3_extreme_goat_tower": {
                      "maxStacks": 2.0,
                      "cloneDamageBonus": 0.065
                    }
                  },
                  "illusionCloneQueue": {
                    "spreadTicks": 40,
                    "maxSpawnsPerTick": 8
                  }
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        TowerBalanceConfig towerBalance = configs.towerBalance();
        assertTrue(towerBalance.towers().containsKey(IllagerTowers.T1_VINDICATOR.id()));
        assertEquals(170, towerBalance.upgradeCost(
                IllagerTowers.T1_VINDICATOR.id(),
                IllagerTowers.T2_VINDICATOR_CAPTAIN.id(),
                0
        ));
        assertEquals(100.0, towerBalance.ability(IllagerRaidStates.RAID_CONFIG_ID, "gaugeMax", -1), 0.0001);
        assertEquals(0.10, towerBalance.ability(IllagerTowers.T1_VINDICATOR.id(), "raidDamageReduction", -1), 0.0001);
        String written = Files.readString(tempDir.resolve("tower_balance.json"));
        assertTrue(written.contains("illager_vindicator_t1"));
        assertTrue(written.contains("illager_vindicator_t1->illager_vindicator_captain_t2"));
        assertTrue(written.contains("illager_raid"));
        assertTrue(written.contains("raidDamageReduction"));
    }

    @Test
    void defaultEconomyConfigEnablesTeamTransferEveryThreeRoundsWithThirtyPerRound() {
        EconomyConfig.TeamTransferConfig config = EconomyConfig.defaultConfig().teamTransfer();

        assertTrue(config.enabled());
        assertEquals(3, config.receiveCooldownRounds());
        assertEquals(30, config.maxDiamondPerRound());
        assertEquals(90, config.maxRequestDiamond(3));
    }

    @Test
    void loadBackfillsTeamTransferConfigDefaults() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("economy.json"), """
                {
                  "startingDiamond": 200,
                  "startingEmerald": 50,
                  "startingIncome": 0,
                  "teamTransfer": {
                    "receiveCooldownRounds": 5
                  }
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertTrue(configs.economy().teamTransfer().enabled());
        assertEquals(5, configs.economy().teamTransfer().receiveCooldownRounds());
        assertEquals(30, configs.economy().teamTransfer().maxDiamondPerRound());
        String written = Files.readString(tempDir.resolve("economy.json"));
        assertTrue(written.contains("enabled"));
        assertTrue(written.contains("maxDiamondPerRound"));
    }

    @Test
    void loadPreservesExplicitTeamTransferDisabled() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("economy.json"), """
                {
                  "startingDiamond": 200,
                  "startingEmerald": 50,
                  "startingIncome": 0,
                  "teamTransfer": {
                    "enabled": false,
                    "receiveCooldownRounds": 2,
                    "maxDiamondPerRound": 10
                  }
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertEquals(false, configs.economy().teamTransfer().enabled());
        assertEquals(2, configs.economy().teamTransfer().receiveCooldownRounds());
        assertEquals(10, configs.economy().teamTransfer().maxDiamondPerRound());
    }

    @Test
    void defaultEconomyConfigEnablesEmeraldIncomeBoostFromRoundTwentyFive() {
        EconomyConfig.EmeraldIncomeBoostConfig config = EconomyConfig.defaultConfig().emeraldIncomeBoost();

        assertTrue(config.enabled());
        assertEquals(25, config.startRound());
        assertTrue(config.activeForRound(25));
    }

    @Test
    void loadBackfillsEmeraldIncomeBoostDefaults() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("economy.json"), """
                {
                  "startingDiamond": 200,
                  "startingEmerald": 50,
                  "startingIncome": 0,
                  "emeraldIncomeBoost": {
                    "startRound": 30
                  }
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertTrue(configs.economy().emeraldIncomeBoost().enabled());
        assertEquals(30, configs.economy().emeraldIncomeBoost().startRound());
        String written = Files.readString(tempDir.resolve("economy.json"));
        assertTrue(written.contains("emeraldIncomeBoost"));
        assertTrue(written.contains("enabled"));
    }

    @Test
    void loadPreservesExplicitEmeraldIncomeBoostDisabled() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("economy.json"), """
                {
                  "startingDiamond": 200,
                  "startingEmerald": 50,
                  "startingIncome": 0,
                  "emeraldIncomeBoost": {
                    "enabled": false,
                    "startRound": 30
                  }
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertEquals(false, configs.economy().emeraldIncomeBoost().enabled());
        assertEquals(30, configs.economy().emeraldIncomeBoost().startRound());
    }

    @Test
    void loadBackfillsTowerLimitPurchaseDefaults() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("economy.json"), """
                {
                  "startingDiamond": 200,
                  "startingEmerald": 50,
                  "startingIncome": 0,
                  "emeraldCap": {
                    "base": 1500,
                    "roundOffsetMultiplier": 6,
                    "roundOffsetStep": 20,
                    "flatBonus": 30
                  },
                  "emeraldProduction": {
                    "initialEmeraldPerSec": 1,
                    "maxUpgradeCount": 20,
                    "initialUpgradeCost": 50,
                    "upgradeCostIncrease": 25,
                    "emeraldPerSecIncrease": 1,
                    "upgradeCurrency": "DIAMOND"
                  },
                  "towerLimit": {
                    "initialLimit": 5,
                    "increaseStartRound": 5,
                    "increaseEveryRounds": 5,
                    "increaseAmount": 3,
                    "maxLimit": 11
                  }
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertEquals(EconomyConfig.TowerLimitConfig.defaultConfig().initialPurchaseDiamondCost(), configs.economy().towerLimit().initialPurchaseDiamondCost());
        assertEquals(EconomyConfig.TowerLimitConfig.defaultConfig().initialPurchaseEmeraldCost(), configs.economy().towerLimit().initialPurchaseEmeraldCost());
        assertEquals(EconomyConfig.KillRewardConfig.defaultConfig(), configs.economy().killReward());
        String written = Files.readString(tempDir.resolve("economy.json"));
        assertTrue(written.contains("initialPurchaseDiamondCost"));
        assertTrue(written.contains("initialPurchaseEmeraldCost"));
        assertTrue(written.contains("killReward"));
    }

    @Test
    void loadReadsKillRewardConfigOverrides() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("economy.json"), """
                {
                  "startingDiamond": 200,
                  "startingEmerald": 50,
                  "startingIncome": 0,
                  "emeraldCap": {
                    "base": 1500,
                    "roundOffsetMultiplier": 6,
                    "roundOffsetStep": 20,
                    "flatBonus": 30
                  },
                  "emeraldProduction": {
                    "initialEmeraldPerSec": 1,
                    "maxUpgradeCount": 20,
                    "initialUpgradeCost": 50,
                    "upgradeCostIncrease": 25,
                    "emeraldPerSecIncrease": 1,
                    "upgradeCurrency": "DIAMOND"
                  },
                  "towerLimit": {
                    "initialLimit": 5,
                    "increaseStartRound": 5,
                    "increaseEveryRounds": 5,
                    "increaseAmount": 3,
                    "maxLimit": 11,
                    "purchaseIncreaseAmount": 1,
                    "maxPurchaseCount": 20,
                    "initialPurchaseDiamondCost": 100,
                    "purchaseDiamondCostIncrease": 50,
                    "initialPurchaseEmeraldCost": 25,
                    "purchaseEmeraldCostIncrease": 10
                  },
                  "killReward": {
                    "crossLaneWaveReductionEnabled": false,
                    "crossLaneFinalDefenseWaveMultiplier": 0.5,
                    "finalDefenseProgressThreshold": 0.95,
                    "applyToIncomeUnits": true
                  }
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertEquals(false, configs.economy().killReward().crossLaneWaveReductionEnabled());
        assertEquals(0.5, configs.economy().killReward().crossLaneFinalDefenseWaveMultiplier(), 0.0001);
        assertEquals(0.95, configs.economy().killReward().finalDefenseProgressThreshold(), 0.0001);
        assertEquals(true, configs.economy().killReward().applyToIncomeUnits());
    }

    @Test
    void loadCreatesLeaderTargetingConfigFileWithDefaults() {
        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertTrue(Files.exists(tempDir.resolve("leader_targeting.json")));
        assertEquals(2, configs.leaderTargeting().maxTargetingTeamsPerTarget());
        assertEquals(2, configs.leaderTargeting().activeTargetRounds());
    }

    @Test
    void loadReadsLeaderTargetingConfigOverrides() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("leader_targeting.json"), """
                {
                  "maxTargetingTeamsPerTarget": 1,
                  "activeTargetRounds": 4
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertEquals(1, configs.leaderTargeting().maxTargetingTeamsPerTarget());
        assertEquals(4, configs.leaderTargeting().activeTargetRounds());
    }

    @Test
    void loadCreatesIncomeLaneRoutingConfigFileWithDefaults() {
        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertTrue(Files.exists(tempDir.resolve("income_lane_routing.json")));
        assertEquals(IncomeLaneRoutingConfig.defaultConfig(), configs.incomeLaneRouting());
        assertEquals(true, configs.incomeLaneRouting().enabled());
        assertEquals(IncomeLaneRoutingConfig.Mode.LEAST_THREAT_PRESSURE, configs.incomeLaneRouting().mode());
    }

    @Test
    void loadCreatesIncomeLaneRoutingConfigFileForExistingLegacyConfigDirectory() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("rating.json"), """
                {
                  "enabled": true,
                  "teamEloMatchmakingEnabled": true,
                  "eloKFactor": 32.0,
                  "initialDisplayElo": 1500,
                  "initialMu": 1500.0,
                  "initialSigma": 350.0,
                  "leaderboardLimit": 10,
                  "minimumParticipants": 2,
                  "excludeSpectators": true,
                  "contributionWeightingEnabled": true,
                  "contributionMultiplierMin": 0.85,
                  "contributionMultiplierMax": 1.15,
                  "defenseContributionWeight": 0.4,
                  "pressureContributionWeight": 0.25,
                  "economyContributionWeight": 0.2,
                  "assistContributionWeight": 0.15
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertTrue(Files.exists(tempDir.resolve("income_lane_routing.json")));
        assertEquals(IncomeLaneRoutingConfig.defaultConfig(), configs.incomeLaneRouting());
    }

    @Test
    void loadBackfillsIncomeLaneRoutingConfigDefaults() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("income_lane_routing.json"), """
                {
                  "mode": "RANDOM"
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertEquals(true, configs.incomeLaneRouting().enabled());
        assertEquals(IncomeLaneRoutingConfig.Mode.RANDOM, configs.incomeLaneRouting().mode());
        assertEquals(IncomeLaneRoutingConfig.defaultConfig().queuedThreatWeight(), configs.incomeLaneRouting().queuedThreatWeight(), 0.0001);
        assertEquals(IncomeLaneRoutingConfig.defaultConfig().nextRoundQueuedThreatWeight(), configs.incomeLaneRouting().nextRoundQueuedThreatWeight(), 0.0001);
        assertEquals(IncomeLaneRoutingConfig.defaultConfig().tieBreakMode(), configs.incomeLaneRouting().tieBreakMode());
        String written = Files.readString(tempDir.resolve("income_lane_routing.json"));
        assertTrue(written.contains("enabled"));
        assertTrue(written.contains("queuedThreatWeight"));
        assertTrue(written.contains("nextRoundQueuedThreatWeight"));
        assertTrue(written.contains("tieBreakMode"));
    }

    @Test
    void loadReadsIncomeLaneRoutingConfigOverrides() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("income_lane_routing.json"), """
                {
                  "enabled": false,
                  "mode": "RANDOM",
                  "queuedThreatWeight": 2.0,
                  "nextRoundQueuedThreatWeight": 0.25,
                  "tieBreakMode": "RANDOM"
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertEquals(false, configs.incomeLaneRouting().enabled());
        assertEquals(IncomeLaneRoutingConfig.Mode.RANDOM, configs.incomeLaneRouting().mode());
        assertEquals(2.0, configs.incomeLaneRouting().queuedThreatWeight(), 0.0001);
        assertEquals(0.25, configs.incomeLaneRouting().nextRoundQueuedThreatWeight(), 0.0001);
        assertEquals(IncomeLaneRoutingConfig.TieBreakMode.RANDOM, configs.incomeLaneRouting().tieBreakMode());
    }

    @Test
    void loadCreatesMonsterScalingConfigFileWithDefaults() {
        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertTrue(Files.exists(tempDir.resolve("monster_scaling.json")));
        assertEquals(MonsterScalingConfig.defaultConfig(), configs.monsterScaling());
    }

    @Test
    void loadBackfillsMonsterScalingDefaults() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("monster_scaling.json"), """
                {
                  "enabled": false,
                  "survivalDelayTicks": 100
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertEquals(false, configs.monsterScaling().enabled());
        assertEquals(100, configs.monsterScaling().survivalDelayTicks());
        assertEquals(MonsterScalingConfig.defaultConfig().laneBreachDelayTicks(), configs.monsterScaling().laneBreachDelayTicks());
        assertEquals(MonsterScalingConfig.defaultConfig().intervalTicks(), configs.monsterScaling().intervalTicks());
        assertEquals(true, configs.monsterScaling().scaleWaveMonsters());
        assertEquals(true, configs.monsterScaling().scaleIncomeMonsters());
        String written = Files.readString(tempDir.resolve("monster_scaling.json"));
        assertTrue(written.contains("laneBreachDelayTicks"));
        assertTrue(written.contains("scaleIncomeMonsters"));
    }

    @Test
    void loadCreatesTipConfigFileWithDefaults() {
        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertTrue(Files.exists(tempDir.resolve("tips.json")));
        assertEquals(TipConfig.defaultConfig(), configs.tips());
    }

    @Test
    void loadReadsTipConfigMiniMessageOverrides() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("tips.json"), """
                {
                  "enabled": true,
                  "joinEnabled": false,
                  "joinMessage": "<aqua><bold>접속 안내</bold></aqua>",
                  "intervalSeconds": 30,
                  "messages": [
                    "<gradient:#ff0000:#00ff00><bold>테스트 팁</bold></gradient>"
                  ]
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertEquals(false, configs.tips().joinEnabled());
        assertEquals("<aqua><bold>접속 안내</bold></aqua>", configs.tips().joinMessage());
        assertEquals(30, configs.tips().intervalSeconds());
        assertEquals(List.of("<gradient:#ff0000:#00ff00><bold>테스트 팁</bold></gradient>"), configs.tips().messages());
    }

    @Test
    void loadBackfillsMissingTipConfigFields() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("tips.json"), """
                {
                  "messages": ["<yellow>운영 팁</yellow>"]
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertEquals(true, configs.tips().enabled());
        assertEquals(true, configs.tips().joinEnabled());
        assertEquals(TipConfig.defaultConfig().joinMessage(), configs.tips().joinMessage());
        assertEquals(120, configs.tips().intervalSeconds());
        assertEquals(List.of("<yellow>운영 팁</yellow>"), configs.tips().messages());
        String written = Files.readString(tempDir.resolve("tips.json"));
        assertTrue(written.contains("enabled"));
        assertTrue(written.contains("joinEnabled"));
        assertTrue(written.contains("joinMessage"));
        assertTrue(written.contains("intervalSeconds"));
    }
}
