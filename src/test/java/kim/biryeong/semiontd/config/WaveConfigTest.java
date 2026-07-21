package kim.biryeong.semiontd.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
        WaveConfig config = WaveConfig.defaultConfig();
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

        for (RoundWaveConfig template : config.infiniteTemplates()) {
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
                config.infiniteTemplates().stream()
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

    private static void assertCombatStats(WaveMonsterEntry entry, double health, double armor, double attackDamage) {
        assertEquals(health, entry.health(), 0.0001, entry.id() + " health");
        assertEquals(armor, entry.armor(), 0.0001, entry.id() + " armor");
        assertEquals(attackDamage, entry.attackDamage(), 0.0001, entry.id() + " attack");
    }

    private static int totalCount(List<WaveMonsterEntry> entries) {
        return entries.stream().mapToInt(WaveMonsterEntry::count).sum();
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
