package kim.biryeong.semiontd.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kim.biryeong.semiontd.config.SemionConfigLoader.LoadedConfigs;
import kim.biryeong.semiontd.rating.RatingConfig;
import kim.biryeong.semiontd.tower.army.ArmyBalance;
import kim.biryeong.semiontd.tower.demonlord.DemonLordTowers;
import kim.biryeong.semiontd.tower.end.EndTowers;
import kim.biryeong.semiontd.tower.hero.HeroWeapon;
import kim.biryeong.semiontd.tower.illager.IllagerRaidStates;
import kim.biryeong.semiontd.tower.illager.IllagerTowers;
import kim.biryeong.semiontd.tower.legion.LegionTowers;
import kim.biryeong.semiontd.tower.warlock.WarlockTowers;
import kim.biryeong.semiontd.trait.TraitSelectionConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

final class SemionConfigLoaderTest {
    private static final List<String> BUNDLED_BALANCE_FILES = List.of(
            "economy.json",
            "income_lane_routing.json",
            "leader_targeting.json",
            "monster_scaling.json",
            "progression.json",
            "rating.json",
            "summons.json",
            "tower_balance.json",
            "trait_balance.json",
            "traits.json",
            "wave.json"
    );

    @TempDir
    Path tempDir;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void bundledBalanceFilesSeedRuntimeDefaults() throws Exception {
        SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        for (String fileName : BUNDLED_BALANCE_FILES) {
            try (InputStream input = BundledBalanceDefaults.class.getResourceAsStream(
                    "/semiontd/balance-defaults/" + fileName
            )) {
                assertNotNull(input, fileName);
                JsonElement bundled = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                JsonElement written = JsonParser.parseString(Files.readString(tempDir.resolve(fileName)));
                assertEquals(bundled, written, fileName);
            }
        }

        Path cosmetics = tempDir.resolve("cosmetics.json");
        BundledBalanceDefaults.copyIfMissing("cosmetics.json", cosmetics);
        assertTrue(Files.size(cosmetics) > 0L);
    }

    @Test
    void webIntegrationIsDisabledByDefault() {
        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertTrue(Files.exists(tempDir.resolve("web_integration.json")));
        assertFalse(configs.webIntegration().enabled());
    }

    @Test
    void jobAvailabilityIsCreatedSavedAndReloaded() {
        LoadedConfigs defaults = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));
        ResourceLocation disabledJob = ResourceLocation.fromNamespaceAndPath("semion-td", "nether");
        JobAvailabilityConfig updated = defaults.jobAvailability().withEnabled(disabledJob, false);

        assertTrue(Files.exists(tempDir.resolve("jobs.json")));
        assertTrue(SemionConfigLoader.saveJobAvailability(tempDir, updated, LoggerFactory.getLogger("test")));

        LoadedConfigs reloaded = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));
        assertEquals(updated, reloaded.jobAvailability());
        assertFalse(reloaded.jobAvailability().isEnabled(disabledJob));
    }

    @Test
    void invalidJobAvailabilityRetainsLastKnownGood() throws Exception {
        ResourceLocation disabledJob = ResourceLocation.fromNamespaceAndPath("semion-td", "nether");
        JobAvailabilityConfig lastKnownGood = JobAvailabilityConfig.defaultConfig()
                .withEnabled(disabledJob, false);
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("jobs.json"), """
                {
                  "disabledJobs": ["invalid job id"]
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(
                tempDir,
                LoggerFactory.getLogger("test"),
                TowerBalanceConfig.defaultConfig(),
                lastKnownGood
        );

        assertEquals(lastKnownGood, configs.jobAvailability());
    }

    @Test
    void jobAvailabilityPreservesUnknownValidIds() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("jobs.json"), """
                {
                  "disabledJobs": ["removed-mod:old_job"]
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertEquals(Set.of("removed-mod:old_job"), configs.jobAvailability().disabledJobs());
    }

    @Test
    void failedJobAvailabilitySaveReturnsFalse() throws Exception {
        Path fileInsteadOfDirectory = tempDir.resolve("not-a-directory");
        Files.writeString(fileInsteadOfDirectory, "occupied");

        assertFalse(SemionConfigLoader.saveJobAvailability(
                fileInsteadOfDirectory,
                JobAvailabilityConfig.defaultConfig(),
                LoggerFactory.getLogger("test")
        ));
    }

    @Test
    void webIntegrationCanBeEnabled() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("web_integration.json"), """
                {
                  "enabled": true
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertTrue(configs.webIntegration().enabled());
    }

    @Test
    void combatSpeedIsDisabledByDefault() {
        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertTrue(Files.exists(tempDir.resolve("combat_speed.json")));
        assertFalse(configs.combatSpeed().enabled());
        assertEquals(40.0F, configs.combatSpeed().combatTickRate());
        assertEquals(25.0, configs.combatSpeed().maxAverageTickTimeMillis());
    }

    @Test
    void combatSpeedCanBeConfiguredByAdministrator() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("combat_speed.json"), """
                {
                  "enabled": true,
                  "combatTickRate": 60.0,
                  "maxAverageTickTimeMillis": 20.0
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertTrue(configs.combatSpeed().enabled());
        assertEquals(60.0F, configs.combatSpeed().combatTickRate());
        assertEquals(20.0, configs.combatSpeed().maxAverageTickTimeMillis());
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
        assertEquals(0.25, configs.traitBalance().value("opening_salvo", "attackSpeedBonus", -1.0));
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
        assertEquals(120.0, configs.traitBalance().value("mobilization_grant", "startingDiamond", -1.0));
        assertEquals(150.0, configs.traitBalance().value("weekly_holiday_pay", "intervalSeconds", -1.0));
        assertEquals(0.01, configs.traitBalance().value("ignite", "attackDamageRatioPerRound", -1.0));
        assertEquals(0.16, configs.traitBalance().value("performance_bonus", "teamIncomeRatio", -1.0));
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
                  "assistContributionWeight": 0.25,
                  "perfectDefenseLossMultiplier": 0.6
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertEquals(48.0, configs.rating().eloKFactor());
        assertEquals(1200, configs.rating().initialDisplayElo());
        assertEquals(25, configs.rating().leaderboardLimit());
        assertEquals(false, configs.rating().teamEloMatchmakingEnabled());
        assertEquals(false, configs.rating().contributionWeightingEnabled());
        assertEquals(0.6, configs.rating().perfectDefenseLossMultiplier());
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

        assertEquals(false, configs.rating().teamEloMatchmakingEnabled());
        assertEquals(0.75, configs.rating().perfectDefenseLossMultiplier());
        String written = Files.readString(tempDir.resolve("rating.json"));
        assertTrue(written.contains("teamEloMatchmakingEnabled"));
        assertTrue(written.contains("perfectDefenseLossMultiplier"));
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
        assertEquals(0.10, towerBalance.abilities().get(LegionTowers.T3_EXTREME_GOAT_TOWER.id()).get("cloneDamageBonus"));
        assertEquals(1_000.0, towerBalance.ability("ocean_global", "waterSoftCap", -1.0));
        assertEquals(2_500.0, towerBalance.ability("ocean_global", "waterSupplyStopThreshold", -1.0));
        assertEquals(0.60, towerBalance.ability("ocean_global", "waterSupplyStackDecay", -1.0));
        String written = Files.readString(tempDir.resolve("tower_balance.json"));
        assertTrue(written.contains("t2_strong_goat_tower"));
        assertTrue(written.contains("cloneDamageBonus"));
        assertTrue(written.contains("\"schemaVersion\": 2"));
        assertTrue(written.contains("waterSupplyStopThreshold"));
    }

    @Test
    void invalidArmyThresholdOrderRetainsLastKnownGoodBalance() throws Exception {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> army = new LinkedHashMap<>(abilities.get(ArmyBalance.CONFIG_ID));
        army.put("corporalAttackMultiplier", 0.70);
        abilities.put(ArmyBalance.CONFIG_ID, army);
        TowerBalanceConfig lastKnownGood = new TowerBalanceConfig(
                defaults.towers(), defaults.upgradeCosts(), abilities
        );
        lastKnownGood.validateForRuntime();

        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("tower_balance.json"), """
                {
                  "schemaVersion": 2,
                  "towers": {},
                  "upgradeCosts": {},
                  "abilities": {
                    "army_global": {
                      "corporalService": 6,
                      "sergeantService": 5
                    }
                  }
                }
                """);

        TowerBalanceConfig loaded = SemionConfigLoader.load(
                tempDir, LoggerFactory.getLogger("test"), lastKnownGood
        ).towerBalance();
        assertEquals(ArmyBalance.CORPORAL_SERVICE,
                loaded.ability(ArmyBalance.CONFIG_ID, "corporalService", -1.0));
        assertEquals(0.70, loaded.ability(ArmyBalance.CONFIG_ID, "corporalAttackMultiplier", -1.0));
    }

    @Test
    void invalidDemonLordBalanceRetainsLastKnownGoodBalance() throws Exception {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> global = new LinkedHashMap<>(
                abilities.get(DemonLordTowers.GLOBAL_CONFIG_ID));
        global.put("baseMaxHealth", 475.0);
        abilities.put(DemonLordTowers.GLOBAL_CONFIG_ID, global);
        TowerBalanceConfig lastKnownGood = new TowerBalanceConfig(
                defaults.towers(), defaults.upgradeCosts(), abilities);
        lastKnownGood.validateForRuntime();

        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("tower_balance.json"), """
                {
                  "schemaVersion": 2,
                  "towers": {},
                  "upgradeCosts": {},
                  "abilities": {
                    "demon_lord_global": {
                      "bladeAttackIntervalTicks": 1.5
                    }
                  }
                }
                """);

        TowerBalanceConfig loaded = SemionConfigLoader.load(
                tempDir, LoggerFactory.getLogger("test"), lastKnownGood
        ).towerBalance();
        assertEquals(475.0,
                loaded.ability(DemonLordTowers.GLOBAL_CONFIG_ID, "baseMaxHealth", -1.0));
        assertEquals(12.0,
                loaded.ability(DemonLordTowers.GLOBAL_CONFIG_ID, "bladeAttackIntervalTicks", -1.0));
    }

    @Test
    void loadBackfillsMissingWarlockAbilitiesWithoutOverwritingConfiguredValues() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("tower_balance.json"), """
                {
                  "towers": {},
                  "upgradeCosts": {},
                  "abilities": {
                    "warlock_global": {
                      "sacrificeRadius": 25.0,
                      "minInterval": 5.0,
                      "speedCap": 15.0,
                      "awakeningThreshold": 0.4,
                      "damageSoftCap": 180.0
                    },
                    "ranged_warlock_tower": {
                      "petHealth": 0.05,
                      "petHealthCap": 0.25,
                      "petDamage": 0.15,
                      "petDamageCap": 0.75
                    },
                    "melee_warlock_tower": {
                      "petHealth": 0.15,
                      "petHealthCap": 0.75,
                      "petDamage": 0.05,
                      "petDamageCap": 0.25
                    }
                  },
                  "schemaVersion": 2
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        Map<String, Double> global = configs.towerBalance().abilities().get(WarlockTowers.CONFIG_ID);
        assertFalse(global.containsKey("damageThreshold"));
        assertFalse(global.containsKey("damageScale"));
        assertFalse(global.containsKey("healthThreshold"));
        assertFalse(global.containsKey("healthScale"));
        assertEquals(180.0, global.get("damageSoftCap"));
        assertEquals(1350.0, global.get("awakeningKills"));
        assertFalse(global.containsKey("awakeningAbsorptions"));
        assertEquals(145.0, configs.towerBalance().ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "damageThreshold", -1.0));
        assertEquals(20.0, configs.towerBalance().ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "damageScale", -1.0));
        assertEquals(2000.0, configs.towerBalance().ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "healthThreshold", -1.0));
        assertEquals(500.0, configs.towerBalance().ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "healthScale", -1.0));
        assertEquals(2.0, configs.towerBalance().ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "splashEvery", -1.0));
        assertEquals(200.0, configs.towerBalance().ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "damageThreshold", -1.0));
        assertEquals(20.0, configs.towerBalance().ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "damageScale", -1.0));
        assertEquals(3500.0, configs.towerBalance().ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "healthThreshold", -1.0));
        assertEquals(500.0, configs.towerBalance().ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "healthScale", -1.0));
        assertEquals(0.25, configs.towerBalance().ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "petHealthCap", -1.0));
        assertEquals(0.25, configs.towerBalance().ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "petDamageCap", -1.0));
        assertEquals(600.0, configs.towerBalance().ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "awakeningHeal", -1.0));

        String written = Files.readString(tempDir.resolve("tower_balance.json"));
        var writtenGlobal = JsonParser.parseString(written)
                .getAsJsonObject()
                .getAsJsonObject("abilities")
                .getAsJsonObject(WarlockTowers.CONFIG_ID);
        assertFalse(writtenGlobal.has("damageThreshold"));
        assertFalse(writtenGlobal.has("damageScale"));
        assertFalse(writtenGlobal.has("healthThreshold"));
        assertFalse(writtenGlobal.has("healthScale"));
        assertTrue(written.contains("\"damageThreshold\": 145.0"));
        assertTrue(written.contains("\"damageThreshold\": 200.0"));
        assertTrue(written.contains("\"healthThreshold\": 3000.0"));
        assertTrue(written.contains("\"awakeningKills\": 1350.0"));
        assertTrue(written.contains("\"awakeningHeal\": 600.0"));
        assertTrue(written.contains("\"splashEvery\": 2.0"));
        assertTrue(written.contains("\"damageSoftCap\": 180.0"));
        assertTrue(written.contains("\"petHealthCap\": 0.25"));
        assertTrue(written.contains("\"petDamageCap\": 0.25"));
    }

    @Test
    void loadRejectsFutureTowerBalanceSchemaAndRetainsLastKnownGood() throws Exception {
        Files.createDirectories(tempDir);
        Path towerBalancePath = tempDir.resolve("tower_balance.json");
        Files.writeString(towerBalancePath, """
                {
                  "schemaVersion": 3,
                  "towers": {
                    "t1_goat_tower": {
                      "mineralCost": 99
                    }
                  }
                }
                """);

        TowerBalanceConfig lastKnownGood = TowerBalanceConfig.defaultConfig();
        TowerBalanceConfig balance = SemionConfigLoader.load(
                tempDir,
                LoggerFactory.getLogger("test"),
                lastKnownGood
        ).towerBalance();

        assertEquals(lastKnownGood, balance);
        String unchanged = Files.readString(towerBalancePath);
        assertTrue(unchanged.contains("\"schemaVersion\": 3"));
        assertTrue(unchanged.contains("\"mineralCost\": 99"));
    }

    @Test
    void loadPreservesExplicitEndValues() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("tower_balance.json"), """
        {
          "schemaVersion": 1,
          "towers": {
            "base_ender_dragon": {
              "damage": 5.0,
              "attackIntervalTicks": 20
            }
          },
          "upgradeCosts": {
            "t1_endermite_tower->t2_enderman_tower": 75
          },
          "abilities": {
            "t2_shulker_tower": {
              "damageReduction": 0.15
            },
            "end_global": {
              "damageReductionStep": 0.025,
              "lifeStealCap": 0.20
            }
          }
        }
        """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));
        TowerBalanceConfig balance = configs.towerBalance();
        assertEquals(1, balance.schemaVersion());
        assertEquals(5.0, balance.towers().get(EndTowers.BASE_END_TOWER.id()).damage(), 0.0001);
        assertEquals(20, balance.towers().get(EndTowers.BASE_END_TOWER.id()).attackIntervalTicks());
        assertEquals(75, balance.upgradeCost(
                EndTowers.T1_ENDERMITE_TOWER.id(),
                EndTowers.T2_ENDERMAN_TOWER.id(),
                -1
        ));
        assertEquals(0.15, balance.ability(EndTowers.T2_SHULKER_TOWER.id(), "damageReduction", -1.0), 0.0001);
        assertEquals(0.025, balance.ability("end_global", "damageReductionStep", -1.0), 0.0001);
        assertEquals(0.20, balance.ability("end_global", "lifeStealCap", -1.0), 0.0001);
    }

    @Test
    void loadRepairsOnlyInvalidAbilityAndKeepsOtherOverrides() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("tower_balance.json"), """
            {
              "towers": {
                "t1_goat_tower": {
                  "mineralCost": 99
                }
              },
              "abilities": {
                "end_global": {
                  "transferTicks": -1.0,
                  "roundDamageRatio": 1.75
                }
              }
            }
            """);

        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, TowerBalanceConfig.TowerStats> lastKnownTowers =
                new LinkedHashMap<>(defaults.towers());
        TowerBalanceConfig.TowerStats goat =
                lastKnownTowers.get(LegionTowers.T1_GOAT_TOWER.id());
        lastKnownTowers.put(
                LegionTowers.T1_GOAT_TOWER.id(),
                new TowerBalanceConfig.TowerStats(
                        999L,
                        goat.maxHealth(),
                        goat.range(),
                        goat.damage(),
                        goat.attackIntervalTicks(),
                        goat.aggroPriority()
                )
        );
        TowerBalanceConfig lastKnownGood = new TowerBalanceConfig(
                lastKnownTowers,
                defaults.upgradeCosts(),
                defaults.abilities(),
                defaults.illusionCloneQueue(),
                defaults.villagerAdv()
        );

        TowerBalanceConfig balance = SemionConfigLoader.load(
                tempDir,
                LoggerFactory.getLogger("test"),
                lastKnownGood
        ).towerBalance();

        assertEquals(99L, balance.towers().get(LegionTowers.T1_GOAT_TOWER.id()).mineralCost());
        assertEquals(
                lastKnownGood.ability("end_global", "transferTicks", -1.0),
                balance.ability("end_global", "transferTicks", -1.0),
                0.0001
        );
        assertEquals(1.75, balance.ability("end_global", "roundDamageRatio", -1.0), 0.0001);
        String repaired = Files.readString(tempDir.resolve("tower_balance.json"));
        assertFalse(repaired.contains("\"transferTicks\": -1.0"));
        assertTrue(repaired.contains("\"roundDamageRatio\": 1.75"));
    }

    @Test
    void loadPreservesSignedHeroWeaponAggro() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("tower_balance.json"), """
            {
              "abilities": {
                "hero_party_weapon_tome": {
                  "aggroPriority": -25.0
                }
              }
            }
            """);

        TowerBalanceConfig balance = SemionConfigLoader.load(
                tempDir,
                LoggerFactory.getLogger("test"),
                TowerBalanceConfig.defaultConfig()
        ).towerBalance();

        assertEquals(-25.0, balance.ability(HeroWeapon.TOME.configId(), "aggroPriority", 0.0), 0.0001);
        assertTrue(Files.readString(tempDir.resolve("tower_balance.json"))
                .contains("\"aggroPriority\": -25.0"));
    }

    @Test
    void loadRepairsOnlyInvalidUpgradeCostAndKeepsOtherOverrides() throws Exception {
        Files.createDirectories(tempDir);
        String endUpgradeKey = TowerBalanceConfig.upgradeKey(
                EndTowers.T1_ENDERMITE_TOWER.id(),
                EndTowers.T2_ENDERMAN_TOWER.id()
        );
        Files.writeString(tempDir.resolve("tower_balance.json"), """
            {
              "schemaVersion": 2,
              "towers": {
                "t1_goat_tower": {
                  "mineralCost": 99
                }
              },
              "upgradeCosts": {
                "%s": -50
              }
            }
            """.formatted(endUpgradeKey));

        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceConfig balance = SemionConfigLoader.load(
                tempDir,
                LoggerFactory.getLogger("test")
        ).towerBalance();

        assertEquals(99L, balance.towers().get(LegionTowers.T1_GOAT_TOWER.id()).mineralCost());
        assertEquals(defaults.upgradeCosts().get(endUpgradeKey), balance.upgradeCosts().get(endUpgradeKey));
        JsonElement repaired = JsonParser.parseString(Files.readString(tempDir.resolve("tower_balance.json")));
        assertEquals(
                defaults.upgradeCosts().get(endUpgradeKey),
                repaired.getAsJsonObject()
                        .getAsJsonObject("upgradeCosts")
                        .get(endUpgradeKey)
                        .getAsLong()
        );
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
        assertEquals(130, towerBalance.upgradeCost(
                IllagerTowers.T1_VINDICATOR.id(),
                IllagerTowers.T2_VINDICATOR_CAPTAIN.id(),
                0
        ));
        assertEquals(100.0, towerBalance.ability(IllagerRaidStates.RAID_CONFIG_ID, "gaugeMax", -1), 0.0001);
        assertEquals(0.15, towerBalance.ability(IllagerTowers.T1_VINDICATOR.id(), "raidDamageReduction", -1), 0.0001);
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
        assertEquals(1.0, configs.economy().killReward().crossLaneOwnerShare(), 0.0001);
        assertTrue(Files.readString(tempDir.resolve("economy.json")).contains("\"crossLaneOwnerShare\": 1.0"));
    }

    @Test
    void loadPreservesKillRewardOwnerShareOverride() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("economy.json"), """
                {
                  "killReward": {
                    "crossLaneWaveReductionEnabled": true,
                    "crossLaneFinalDefenseWaveMultiplier": 0.25,
                    "finalDefenseProgressThreshold": 0.9,
                    "applyToIncomeUnits": false,
                    "crossLaneOwnerShare": 0.35
                  }
                }
                """);

        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertEquals(0.35, configs.economy().killReward().crossLaneOwnerShare(), 0.0001);
    }

    @Test
    void loadCreatesLeaderTargetingConfigFileWithDefaults() {
        LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"));

        assertTrue(Files.exists(tempDir.resolve("leader_targeting.json")));
        assertEquals(1, configs.leaderTargeting().maxTargetingTeamsPerTarget());
        assertEquals(1, configs.leaderTargeting().activeTargetRounds());
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
