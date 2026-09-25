package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
import kim.biryeong.semiontd.config.WaveSpawnMode;
import org.junit.jupiter.api.Test;

final class WaveSpawnOrderTest {
    private static final WaveMonsterEntry TANK = entry("tank", 2);
    private static final WaveMonsterEntry RANGED = entry("ranged", 3);

    @Test
    void sequentialExpandsEachEntryAsOneBlock() {
        assertEquals(
                List.of("tank", "tank", "ranged", "ranged", "ranged"),
                ids(PlayerLane.expandWaveEntries(List.of(TANK, RANGED), WaveSpawnMode.SEQUENTIAL))
        );
    }

    @Test
    void roundRobinAlternatesUntilEachEntryIsExhausted() {
        assertEquals(
                List.of("tank", "ranged", "tank", "ranged", "ranged"),
                ids(PlayerLane.expandWaveEntries(List.of(TANK, RANGED), WaveSpawnMode.ROUND_ROBIN))
        );
    }

    private static List<String> ids(List<WaveMonsterEntry> entries) {
        return entries.stream().map(WaveMonsterEntry::id).toList();
    }

    @Test
    void healerRampKeepsCombatOrderAndRewardBudgetExact() {
        for (boolean counts : List.of(false, true)) {
            var config = WaveConfig.defaultConfig().withSeason3Stages(counts, true,
                    Set.of("animal_stampede", "overworld_assault", "zombified_legion"));
            for (int round : new int[] {15, 16, 17, 18, 19, 20, 24, 25}) {
                for (var wave : config.candidatesForRound(round)) {
                    var entries = wave.entriesForLane("lane_1");
                    var expanded = PlayerLane.expandWaveEntries(entries, wave.spawnMode(), wave.rewardBudgetForLane("lane_1"));
                    int healerCount = Math.max(0, Math.min(5, round - 15));
                    var positions = healerPositions(expanded);
                    assertEquals(healerCount, positions.size(), "R" + round + ": " + wave.templateId());
                    assertEquals(entries.stream().mapToInt(WaveMonsterEntry::count).sum(), expanded.size());
                    if (healerCount > 0) {
                        int combatCount = expanded.size() - healerCount;
                        assertEquals(combatCount * 55 / 100, positions.getFirst());
                        assertEquals(combatCount * (healerCount == 1 ? 55 : 85) / 100 + healerCount - 1, positions.getLast());
                    }
                    var combat = entries.stream().filter(entry -> entry.healing() == null).toList();
                    assertEquals(ids(PlayerLane.expandWaveEntries(combat, wave.spawnMode())),
                            ids(expanded.stream().filter(entry -> entry.healing() == null).toList()));
                    assertEquals(wave.rewardBudgetForLane("lane_1"), expanded.stream().mapToLong(WaveMonsterEntry::mineralReward).sum());
                    if (round >= 20 && counts) {
                        assertEquals(expanded.size() - 55, expanded.stream().filter(entry -> entry.mineralReward() == 0).count());
                    }
                }
            }
        }
    }

    @Test
    void healersSpreadAcrossCombatQueueWithoutCountingEarlierHealers() {
        var healer = healer();
        List<List<Integer>> expected = List.of(List.of(11), List.of(11, 18), List.of(11, 15, 19),
                List.of(11, 14, 17, 20), List.of(11, 13, 16, 18, 21));
        for (WaveSpawnMode mode : WaveSpawnMode.values()) {
            for (int count = 1; count <= 5; count++) {
                var expanded = PlayerLane.expandWaveEntries(List.of(TANK.withCount(20), healer.withCount(count)), mode);
                assertEquals(expected.get(count - 1), healerPositions(expanded));
            }
            var crowded = PlayerLane.expandWaveEntries(List.of(TANK.withCount(1), healer.withCount(5)), mode);
            assertEquals(List.of(0, 1, 2, 3, 4), healerPositions(crowded));
        }
        assertThrows(IllegalArgumentException.class, () -> PlayerLane.expandWaveEntries(
                List.of(TANK, healer.withCount(6)), WaveSpawnMode.ROUND_ROBIN));
        assertThrows(IllegalArgumentException.class, () -> PlayerLane.expandWaveEntries(
                List.of(TANK, healer, healer), WaveSpawnMode.ROUND_ROBIN));
    }

    @Test
    void budgetDistributionDoesNotOverflowLongMultiplication() {
        var expanded = PlayerLane.expandWaveEntries(List.of(TANK, RANGED), WaveSpawnMode.ROUND_ROBIN, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, expanded.stream().mapToLong(WaveMonsterEntry::mineralReward).sum());
    }

    private static WaveMonsterEntry entry(String id, int count) {
        return new WaveMonsterEntry(id, 10.0, 0.0, 1.0, AttackKind.MELEE, "minecraft:zombie", null, count);
    }

    private static WaveMonsterEntry healer() {
        return WaveConfig.defaultConfig().withSeason3Stages(false, true, Set.of()).configForRound(16).orElseThrow()
                .entriesForLane("lane_1").stream().filter(entry -> entry.healing() != null).findFirst().orElseThrow();
    }

    private static List<Integer> healerPositions(List<WaveMonsterEntry> entries) {
        return java.util.stream.IntStream.range(0, entries.size()).filter(i -> entries.get(i).healing() != null).boxed().toList();
    }
}
