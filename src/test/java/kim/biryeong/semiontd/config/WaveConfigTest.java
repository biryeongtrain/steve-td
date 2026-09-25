package kim.biryeong.semiontd.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

final class WaveConfigTest {
    @TempDir
    Path tempDir;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void legacyWaveJsonUsesPreviousCombatAndSpawnDefaults() throws Exception {
        Files.writeString(tempDir.resolve("wave.json"), """
                {
                  "rounds": [
                    {
                      "round": 1,
                      "lanes": {
                        "default": [
                          {
                            "id": "legacy_ranged",
                            "health": 20.0,
                            "armor": 1.0,
                            "attackDamage": 2.0,
                            "attackKind": "RANGED",
                            "entityType": "minecraft:skeleton",
                            "mineralReward": 3,
                            "count": 2
                          }
                        ]
                      }
                    }
                  ],
                  "infiniteFromRound": 20,
                  "infinite": {
                    "round": 20,
                    "lanes": {
                      "default": [
                        {
                          "id": "legacy_infinite",
                          "health": 50.0,
                          "attackKind": "MELEE",
                          "entityType": "minecraft:zombie",
                          "count": 1
                        }
                      ]
                    }
                  }
                }
                """);

        RoundWaveConfig round = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"))
                .waves()
                .configForRound(1)
                .orElseThrow();
        WaveMonsterEntry entry = round.entriesForLane("lane_1").getFirst();

        assertEquals(WaveSpawnMode.SEQUENTIAL, round.spawnMode());
        assertEquals(1, round.spawnIntervalTicks());
        assertEquals(0.0, entry.targetPriority());
        assertEquals(1.0, entry.movementSpeedMultiplier());
        assertEquals(6.0, entry.attackRange());
        assertEquals(13, entry.attackIntervalTicks());
        assertEquals("legacy_infinite", SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("test"))
                .waves()
                .configForRound(20)
                .orElseThrow()
                .entriesForLane("lane_1")
                .getFirst()
                .id());
    }

    @Test
    void infiniteScalingUpdatesDamageAndPreservesStaticSettings() {
        WaveMonsterEntry entry = new WaveMonsterEntry(
                "infinite_tank",
                100.0,
                8.0,
                5.0,
                AttackKind.MELEE,
                "minecraft:husk",
                null,
                MonsterDimensions.DEFAULT,
                2,
                3,
                45.0,
                0.9,
                3.0,
                20
        );
        WaveConfig config = new WaveConfig(
                List.of(),
                20,
                new RoundWaveConfig(20, WaveSpawnMode.ROUND_ROBIN, 4, Map.of("default", List.of(entry)))
        );

        RoundWaveConfig scaled = config.configForRound(21).orElseThrow();
        WaveMonsterEntry scaledEntry = scaled.entriesForLane("lane_1").getFirst();

        assertEquals(140.0, scaledEntry.health(), 0.0001);
        assertEquals(5.15, scaledEntry.attackDamage(), 0.0001);
        assertEquals(WaveSpawnMode.ROUND_ROBIN, scaled.spawnMode());
        assertEquals(4, scaled.spawnIntervalTicks());
        assertEquals(45.0, scaledEntry.targetPriority());
        assertEquals(0.9, scaledEntry.movementSpeedMultiplier());
        assertEquals(3.0, scaledEntry.attackRange());
        assertEquals(20, scaledEntry.attackIntervalTicks());
    }

    @Test
    void infiniteTemplatesAreSelectedAndScaledIndependently() {
        RoundWaveConfig first = new RoundWaveConfig(20, Map.of("default", List.of(entry("first", 100.0))));
        RoundWaveConfig second = new RoundWaveConfig(20, Map.of("default", List.of(entry("second", 150.0))));
        WaveConfig config = new WaveConfig(List.of(), 20, first, List.of(first, second));
        Random chooseSecond = new Random() {
            @Override
            public int nextInt(int bound) {
                assertEquals(2, bound);
                return 1;
            }
        };

        RoundWaveConfig selected = config.selectForRound(21, chooseSecond).orElseThrow();

        assertEquals("second", selected.entriesForLane("lane_1").getFirst().id());
        assertEquals(210.0, selected.entriesForLane("lane_1").getFirst().health(), 0.0001);
        assertEquals(1.03, selected.entriesForLane("lane_1").getFirst().attackDamage(), 0.0001);
        assertEquals(2, config.candidatesForRound(21).size());
    }

    @Test
    void lateRoundDefaultsUseTempoBalanceValues() {
        WaveConfig config = WaveConfig.defaultConfig();

        assertCombatStats(config.configForRound(15).orElseThrow().entriesForLane("lane_1").getFirst(), 1100.0, 10.0, 30.0);

        List<WaveMonsterEntry> round16 = config.configForRound(16).orElseThrow().entriesForLane("lane_1");
        assertCombatStats(round16.get(0), 120.0, 10.0, 4.0);
        assertCombatStats(round16.get(1), 60.0, 3.0, 6.0);
        assertCombatStats(round16.get(2), 45.0, 3.0, 5.0);

        List<WaveMonsterEntry> round18 = config.configForRound(18).orElseThrow().entriesForLane("lane_1");
        assertCombatStats(round18.get(0), 120.0, 14.0, 5.0);
        assertCombatStats(round18.get(1), 45.0, 7.0, 7.0);
        assertCombatStats(round18.get(2), 30.0, 4.0, 9.0);
    }

    @Test
    void infiniteRoundsScaleHealthAndAttackForLateGameTempo() {
        WaveConfig config = WaveConfig.defaultConfig();
        int[] rounds = {20, 30, 33, 40};
        double[] healthMultipliers = {1.0, 5.0, 6.2, 9.0};
        double[] attackMultipliers = {1.0, 1.3, 1.39, 1.6};

        for (int index = 0; index < rounds.length; index++) {
            WaveMonsterEntry entry = config.configForRound(rounds[index]).orElseThrow().entriesForLane("lane_1").getFirst();
            assertEquals(250.0 * healthMultipliers[index], entry.health(), 0.0001, "round " + rounds[index] + " health");
            assertEquals(10.0 * attackMultipliers[index], entry.attackDamage(), 0.0001, "round " + rounds[index] + " attack");
        }
    }

    @Test
    void defaultConfigUsesThemedEntitiesAndExpectedRoundTotals() {
        WaveConfig config = WaveConfig.defaultConfig().withSeason3Stages(false, false, Set.of());
        int[] expectedCounts = {12, 14, 18, 16, 20, 35, 30, 40, 40, 25, 35, 40, 50, 35, 4, 60, 100, 80, 80};
        double[] expectedHealth = {
                120.0, 161.0, 234.0, 232.0, 320.0, 525.0, 570.0, 740.0, 880.0, 881.25,
                1312.5, 1590.0, 2100.0, 1548.75, 4400.0, 4500.0, 6000.0, 4800.0, 9600.0
        };
        long[] expectedRewards = {36, 42, 54, 80, 100, 140, 120, 200, 200, 175, 175, 240, 250, 315, 400, 300, 400, 320, 320};

        for (int round = 1; round <= 19; round++) {
            List<WaveMonsterEntry> entries = config.configForRound(round).orElseThrow().entriesForLane("lane_1");
            assertEquals(expectedCounts[round - 1], totalCount(entries), "round " + round + " count");
            assertEquals(expectedHealth[round - 1], totalHealth(entries), 0.0001, "round " + round + " health");
            assertEquals(expectedRewards[round - 1], totalReward(entries), "round " + round + " reward");
        }

        assertEquals(List.of("minecraft:pig", "minecraft:sheep", "minecraft:cow", "minecraft:wolf", "minecraft:llama"),
                java.util.stream.IntStream.rangeClosed(1, 5)
                        .mapToObj(round -> config.configForRound(round).orElseThrow().entriesForLane("lane_1").getFirst().entityType())
                        .toList());
        assertEquals("minecraft:warden", config.configForRound(15).orElseThrow().entriesForLane("lane_1").getFirst().entityType());

        for (RoundWaveConfig template : config.candidatesForRound(20)) {
            List<WaveMonsterEntry> entries = template.entriesForLane("lane_1");
            assertEquals(55, totalCount(entries));
            assertEquals(10_450.0, totalHealth(entries), 0.1);
            assertEquals(55, totalReward(entries));
            assertTrue(entries.stream()
                    .filter(entry -> entry.attackKind() == AttackKind.RANGED)
                    .allMatch(entry -> entry.movementSpeedMultiplier() == 0.95));
        }
        assertEquals(
                List.of(
                        List.of("minecraft:cow", "minecraft:llama"),
                        List.of("minecraft:husk", "minecraft:spider", "minecraft:pillager"),
                        List.of("minecraft:piglin_brute", "minecraft:zombified_piglin", "minecraft:blaze")
                ),
                config.candidatesForRound(20).stream()
                        .map(template -> template.entriesForLane("lane_1").stream().map(WaveMonsterEntry::entityType).toList())
                        .toList()
        );
        assertFalse(config.infiniteTemplates().isEmpty());
        assertTrue(config.rounds().stream()
                .flatMap(round -> round.lanes().values().stream())
                .flatMap(List::stream)
                .noneMatch(WaveConfigTest::isExcludedLargeEntity));
        assertTrue(config.infiniteTemplates().stream()
                .flatMap(round -> round.lanes().values().stream())
                .flatMap(List::stream)
                .noneMatch(WaveConfigTest::isExcludedLargeEntity));
    }

    private static WaveMonsterEntry entry(String id, double health) {
        return new WaveMonsterEntry(id, health, 0.0, 1.0, AttackKind.MELEE, "minecraft:zombie", null, 1);
    }

    @Test
    void season3StagesPreserveBaselineAndApplyCountsWithoutCompounding() {
        WaveConfig baseline = WaveConfig.defaultConfig().withSeason3Stages(false, false, Set.of());
        baseline.validate();
        WaveConfig canary = baseline.withSeason3Stages(false, true, Set.of());
        WaveConfig h0 = WaveConfig.defaultConfig();
        WaveConfig countsOnly = baseline.withSeason3Stages(true, false, Set.of());
        int[] expected = {12, 14, 18, 16, 22, 39, 33, 44, 44, 28, 39, 44, 55, 39, 4, 72, 120, 96, 96};
        for (int round = 1; round <= 19; round++) {
            RoundWaveConfig actual = h0.configForRound(round).orElseThrow();
            assertEquals(expected[round - 1], totalCount(actual.entriesForLane("lane_1")));
            assertEquals(baseline.configForRound(round).orElseThrow().rewardBudgetForLane("lane_1"), actual.rewardBudgetForLane("lane_1"));
            assertEquals(round >= 16 ? round - 15 : 0, totalHealers(actual.entriesForLane("lane_1")));
            assertEquals(totalHealth(countsOnly.configForRound(round).orElseThrow().entriesForLane("lane_1")),
                    totalHealth(actual.entriesForLane("lane_1")), 0.001, "healers replace equal-health combat units");
        }
        assertEquals(60, totalCount(canary.configForRound(16).orElseThrow().entriesForLane("lane_1")));
        assertEquals(4500, totalHealth(canary.configForRound(16).orElseThrow().entriesForLane("lane_1")), 0.001);
        assertEquals(5400, totalHealth(h0.configForRound(16).orElseThrow().entriesForLane("lane_1")), 0.001);
        assertEquals(66, totalCount(h0.configForRound(20).orElseThrow().entriesForLane("lane_1")));
        assertEquals(72, totalCount(h0.configForRound(25).orElseThrow().entriesForLane("lane_1")));
        assertEquals(55, totalCount(baseline.configForRound(25).orElseThrow().entriesForLane("lane_1")));
        assertEquals(55, h0.configForRound(25).orElseThrow().rewardBudgetForLane("lane_1"));
        for (int round : new int[]{20, 24, 25}) {
            for (RoundWaveConfig actual : h0.candidatesForRound(round)) {
                assertEquals(round < 25 ? 66 : 72, totalCount(actual.entriesForLane("lane_1")));
                assertEquals(5, totalHealers(actual.entriesForLane("lane_1")));
                assertEquals(55, actual.rewardBudgetForLane("lane_1"));
                assertEquals(actual, h0.candidatesForRound(round).stream()
                        .filter(wave -> wave.templateId().equals(actual.templateId())).findFirst().orElseThrow());
            }
        }
        WaveConfig promoted = new WaveConfig(baseline.rounds(), 20, baseline.infinite(), baseline.infiniteTemplates(),
                Map.of("20", 1.2, "25", 1.4), true, true, Set.of("overworld_assault"));
        assertEquals(77, totalCount(promoted.configForRound(25).orElseThrow().entriesForLane("lane_1")));
        assertEquals(77, totalCount(promoted.configForRound(25).orElseThrow().entriesForLane("lane_1")));
    }

    @Test
    void infiniteHealerScalesOnceAndKeepsTemplateAndBudget() {
        WaveConfig config = WaveConfig.defaultConfig().withSeason3Stages(true, true, Set.of("overworld_assault"));
        RoundWaveConfig scaled = config.candidatesForRound(25).stream()
                .filter(wave -> "overworld_assault".equals(wave.templateId())).findFirst().orElseThrow();
        WaveMonsterEntry healer = scaled.entriesForLane("lane_1").stream().filter(e -> e.healing() != null).findFirst().orElseThrow();
        assertEquals(450, healer.health());
        assertEquals(480, healer.healing().amount());
        assertEquals(5, healer.count());
        assertEquals(2.3, healer.attackDamage(), 0.001);
        assertEquals(55, scaled.mineralRewardBudget());
        assertEquals(72, totalCount(scaled.entriesForLane("lane_1")));
        assertEquals(config.selectForRound(27, new Random(12)), config.selectForRound(27, new Random(12)));
    }

    @Test
    void invalidStageAndHealingConfigurationsAreRejected() {
        WaveConfig baseline = WaveConfig.defaultConfig();
        assertThrows(IllegalArgumentException.class, () -> baseline.withSeason3Stages(true, true, Set.of("unknown")).validate());
        assertThrows(IllegalArgumentException.class, () -> new WaveConfig(baseline.rounds(), 20, baseline.infinite(),
                baseline.infiniteTemplates(), Map.of("19", 1.2), true, false, Set.of()).validate());
        assertThrows(IllegalArgumentException.class, () -> new WaveConfig(baseline.rounds(), 20, baseline.infinite(),
                baseline.infiniteTemplates(), Map.of("25", 1.51), true, false, Set.of()).validate());
        assertThrows(IllegalArgumentException.class, () -> new WaveHealingConfig(6, Double.NaN, 3, 160, 20, 2));
        assertThrows(IllegalArgumentException.class, () -> new WaveHealingConfig(6, 80, 0, 160, 20, 2));
    }

    @Test
    void legacyAnonymousTemplatesKeepCustomNumbersAndInvalidReloadKeepsPreviousWave() throws Exception {
        String legacy = """
                {
                  "rounds": [],
                  "infiniteFromRound": 20,
                  "infiniteTemplates": [
                    {"round": 20, "lanes": {"default": [
                      {"id": "custom_legacy", "health": 77, "entityType": "minecraft:husk", "count": 7, "mineralReward": 9}
                    ]}}
                  ]
                }
                """;
        Path path = tempDir.resolve("wave.json");
        Files.writeString(path, legacy);
        WaveConfig original = SemionConfigLoader.loadWaves(tempDir, null, LoggerFactory.getLogger("test"));
        WaveMonsterEntry custom = original.configForRound(20).orElseThrow().entriesForLane("lane_1").getFirst();
        assertEquals("custom_legacy", custom.id());
        assertEquals(77, custom.health());
        assertEquals(7, custom.count());
        assertEquals(63, original.configForRound(20).orElseThrow().rewardBudgetForLane("lane_1"));
        assertEquals(legacy, Files.readString(path));
        assertFalse(original.season3CountsEnabled());
        assertFalse(original.round16HealingEnabled());
        assertTrue(original.healingTemplates().isEmpty());

        String invalid = legacy.replace("\"rounds\": []", "\"round16HealingEnabled\": true, \"rounds\": []");
        Files.writeString(path, invalid);
        assertSame(original, SemionConfigLoader.loadWaves(tempDir, original, LoggerFactory.getLogger("test")));
        assertEquals(invalid, Files.readString(path));
        Files.writeString(path, "null");
        assertSame(original, SemionConfigLoader.loadWaves(tempDir, original, LoggerFactory.getLogger("test")));
        Files.delete(path);
        assertSame(original, SemionConfigLoader.loadWaves(tempDir, original, LoggerFactory.getLogger("test")));
    }

    @Test
    void enabledHealerCannotSilentlyAcceptAnOldTemplateWithoutHealingDefinition() {
        RoundWaveConfig old = new RoundWaveConfig(20, WaveSpawnMode.ROUND_ROBIN, 1,
                Map.of("default", List.of(entry("custom_old", 100))), "overworld_assault", 55L);
        WaveConfig config = new WaveConfig(List.of(), 20, old, List.of(old));
        assertThrows(IllegalArgumentException.class, () -> config.withSeason3Stages(false, false, Set.of("overworld_assault")).validate());
        assertThrows(IllegalArgumentException.class, () -> new WaveConfig(List.of(), 20, old, List.of(old, old)).validate());
    }

    @Test
    void countStagesInferNamedLaneBudgetsAndRejectAmbiguousLegacyLaneBudgets() {
        WaveMonsterEntry entry = new WaveMonsterEntry("named_lane", 20, 0, 1, AttackKind.MELEE, "minecraft:husk", null, 9, 10);
        RoundWaveConfig named = new RoundWaveConfig(5, Map.of("lane_1", List.of(entry)));
        WaveConfig config = new WaveConfig(List.of(named), 20, null).withSeason3Stages(true, false, Set.of());
        config.validate();
        assertEquals(11, totalCount(config.configForRound(5).orElseThrow().entriesForLane("lane_1")));
        assertEquals(90, config.configForRound(5).orElseThrow().rewardBudgetForLane("lane_1"));

        RoundWaveConfig heterogeneous = new RoundWaveConfig(5, Map.of("lane_1", List.of(entry), "lane_2", List.of(entry.withCount(20))));
        WaveConfig legacy = new WaveConfig(List.of(heterogeneous), 20, null);
        legacy.validate();
        assertThrows(IllegalArgumentException.class, () -> legacy.withSeason3Stages(true, false, Set.of()).validate());
    }

    @Test
    void javaAndBundledDefaultsUseTheSameHealerRampAndStageSettings() {
        WaveConfig bundled = WaveConfig.defaultConfig();
        WaveConfig fallback = WaveConfig.fallbackConfig();
        bundled.validate();
        fallback.validate();
        assertTrue(bundled.season3CountsEnabled());
        assertTrue(bundled.round16HealingEnabled());
        assertEquals(fallback.season3CountsEnabled(), bundled.season3CountsEnabled());
        assertEquals(fallback.round16HealingEnabled(), bundled.round16HealingEnabled());
        assertEquals(Set.of("animal_stampede", "overworld_assault", "zombified_legion"), bundled.healingTemplates());
        assertEquals(fallback.healingTemplates(), bundled.healingTemplates());
        assertEquals(fallback.infiniteCountMultipliers(), bundled.infiniteCountMultipliers());
        for (int round = 1; round <= 25; round++) {
            List<RoundWaveConfig> actual = bundled.candidatesForRound(round);
            List<RoundWaveConfig> expected = fallback.candidatesForRound(round);
            assertEquals(expected.size(), actual.size());
            for (int index = 0; index < actual.size(); index++) {
                RoundWaveConfig wave = actual.get(index);
                List<WaveMonsterEntry> entries = wave.entriesForLane("lane_1");
                List<WaveMonsterEntry> expectedEntries = expected.get(index).entriesForLane("lane_1");
                assertEquals(expected.get(index).templateId(), wave.templateId());
                assertEquals(expectedEntries.stream().map(WaveMonsterEntry::id).toList(), entries.stream().map(WaveMonsterEntry::id).toList());
                assertEquals(expectedEntries.stream().map(WaveMonsterEntry::count).toList(), entries.stream().map(WaveMonsterEntry::count).toList());
                assertEquals(expectedEntries.stream().filter(entry -> entry.healing() != null).toList(),
                        entries.stream().filter(entry -> entry.healing() != null).toList());
                assertEquals(expected.get(index).rewardBudgetForLane("lane_1"), wave.rewardBudgetForLane("lane_1"));
                for (WaveMonsterEntry healer : entries.stream().filter(entry -> entry.healing() != null).toList()) {
                    assertEquals(Math.min(5, round - 15), healer.count());
                    assertEquals(round < 20 ? "wave_healer_allay_" + round : "wave_healer_allay_infinite", healer.id());
                    assertEquals(round < 20 ? 80 : 160 * (1 + (round - 20) * 0.4), healer.healing().amount());
                    assertEquals(new WaveHealingConfig(6, healer.healing().amount(), 3, 160, 20, 2), healer.healing());
                    WaveMonsterEntry replacement = entries.get(round < 20 ? 0 : 1);
                    assertEquals(replacement.health(), healer.health());
                    assertEquals(replacement.armor(), healer.armor());
                }
            }
        }
    }

    @Test
    void healerValidationKeepsLegacySingleHealersAndRejectsInvalidCountsAndBosses() {
        WaveConfig defaults = WaveConfig.defaultConfig();
        RoundWaveConfig round16 = defaults.rounds().stream().filter(round -> round.round() == 16).findFirst().orElseThrow();
        WaveConfig legacy = new WaveConfig(List.of(round16), 20, null).withSeason3Stages(false, true, Set.of());
        legacy.validate();
        assertEquals(60, totalCount(legacy.configForRound(16).orElseThrow().entriesForLane("lane_1")));
        assertEquals(1, totalHealers(legacy.configForRound(16).orElseThrow().entriesForLane("lane_1")));
        List<WaveMonsterEntry> entries = round16.entriesForLane("lane_1");
        WaveMonsterEntry healer = entries.getLast();
        for (int invalidCount : new int[]{0, 6}) {
            List<WaveMonsterEntry> invalid = new java.util.ArrayList<>(entries.subList(0, entries.size() - 1));
            invalid.add(healer.withCount(invalidCount));
            assertThrows(IllegalArgumentException.class, () -> new WaveConfig(List.of(new RoundWaveConfig(16,
                    Map.of("default", invalid))), 20, null).validate());
        }
        assertThrows(IllegalArgumentException.class, () -> new WaveConfig(List.of(new RoundWaveConfig(15,
                Map.of("default", entries))), 20, null).validate());
        List<WaveMonsterEntry> bossWave = new java.util.ArrayList<>(entries);
        bossWave.add(entry("warden_boss_15", 1100));
        assertThrows(IllegalArgumentException.class, () -> new WaveConfig(List.of(new RoundWaveConfig(16,
                Map.of("default", bossWave))), 20, null).validate());
        List<WaveMonsterEntry> duplicateType = new java.util.ArrayList<>(entries);
        duplicateType.add(healer);
        assertThrows(IllegalArgumentException.class, () -> new WaveConfig(List.of(new RoundWaveConfig(16,
                Map.of("default", duplicateType))), 20, null).validate());
    }

    @Test
    void explicitDisabledStageConfigIsNotPromotedOrRewrittenOnLoad() throws Exception {
        WaveConfig baseline = WaveConfig.defaultConfig().withSeason3Stages(false, false, Set.of());
        String json = new com.google.gson.Gson().toJson(baseline);
        Path path = tempDir.resolve("wave.json");
        Files.writeString(path, json);
        WaveConfig loaded = SemionConfigLoader.loadWaves(tempDir, null, LoggerFactory.getLogger("test"));
        assertEquals(baseline, loaded);
        assertEquals(80, totalCount(loaded.configForRound(19).orElseThrow().entriesForLane("lane_1")));
        assertEquals(0, totalHealers(loaded.configForRound(19).orElseThrow().entriesForLane("lane_1")));
        assertEquals(json, Files.readString(path));
    }

    @Test
    void healingTemplateSerializationIsSortedAndImmutableRegardlessOfInputOrder() {
        List<String> ids = List.of("animal_stampede", "overworld_assault", "zombified_legion");
        Set<String> reversed = new java.util.LinkedHashSet<>(ids.reversed());
        WaveConfig defaults = WaveConfig.defaultConfig();
        WaveConfig forward = defaults.withSeason3Stages(true, true, new java.util.LinkedHashSet<>(ids));
        WaveConfig backward = defaults.withSeason3Stages(true, true, reversed);
        reversed.clear();
        var gson = new com.google.gson.Gson();
        assertEquals(gson.toJsonTree(ids), gson.toJsonTree(backward).getAsJsonObject().get("healingTemplates"));
        assertEquals(gson.toJson(forward), gson.toJson(backward));
        assertThrows(UnsupportedOperationException.class, () -> backward.healingTemplates().clear());
    }

    private static void assertCombatStats(WaveMonsterEntry entry, double health, double armor, double attackDamage) {
        assertEquals(health, entry.health(), 0.0001, entry.id() + " health");
        assertEquals(armor, entry.armor(), 0.0001, entry.id() + " armor");
        assertEquals(attackDamage, entry.attackDamage(), 0.0001, entry.id() + " attack");
    }

    private static int totalCount(List<WaveMonsterEntry> entries) {
        return entries.stream().mapToInt(WaveMonsterEntry::count).sum();
    }

    private static int totalHealers(List<WaveMonsterEntry> entries) {
        return entries.stream().filter(entry -> entry.healing() != null).mapToInt(WaveMonsterEntry::count).sum();
    }

    private static double totalHealth(List<WaveMonsterEntry> entries) {
        return entries.stream().mapToDouble(entry -> entry.health() * entry.count()).sum();
    }

    private static long totalReward(List<WaveMonsterEntry> entries) {
        return entries.stream().mapToLong(entry -> entry.mineralReward() * entry.count()).sum();
    }

    private static boolean isExcludedLargeEntity(WaveMonsterEntry entry) {
        return "minecraft:ghast".equals(entry.entityType()) || "minecraft:ravager".equals(entry.entityType());
    }
}
