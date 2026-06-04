package kim.biryeong.semiontd.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import kim.biryeong.semiontd.config.SemionConfigLoader.LoadedConfigs;
import kim.biryeong.semiontd.rating.RatingConfig;
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
        assertEquals(false, configs.rating().contributionWeightingEnabled());
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
}
