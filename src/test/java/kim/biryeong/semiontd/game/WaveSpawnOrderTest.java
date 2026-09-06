package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void healerAppearsOnceAfterFiftyFivePercentAndRewardBudgetIsExact() {
        for (boolean counts : List.of(false, true)) {
            var config = WaveConfig.defaultConfig().withSeason3Stages(counts, true, Set.of("overworld_assault"));
            for (int round : new int[] {16, 20, 25}) {
                var wave = config.candidatesForRound(round).stream()
                        .filter(candidate -> round == 16 || "overworld_assault".equals(candidate.templateId())).findFirst().orElseThrow();
                var expanded = PlayerLane.expandWaveEntries(wave.entriesForLane("lane_1"), wave.spawnMode(), wave.rewardBudgetForLane("lane_1"));
                int expected = (int) Math.floor((expanded.size() - 1) * 0.55);
                assertEquals(1, expanded.stream().filter(entry -> entry.healing() != null).count());
                assertEquals(expected, java.util.stream.IntStream.range(0, expanded.size()).filter(i -> expanded.get(i).healing() != null).findFirst().orElseThrow());
                assertEquals(wave.rewardBudgetForLane("lane_1"), expanded.stream().mapToLong(WaveMonsterEntry::mineralReward).sum());
                if (round == 16) {assertEquals(counts ? 39 : 32, expected);}
                if (round >= 20 && counts) {assertEquals(expanded.size() - 55, expanded.stream().filter(entry -> entry.mineralReward() == 0).count());}
            }
        }
    }

    @Test
    void budgetDistributionDoesNotOverflowLongMultiplication() {
        var expanded = PlayerLane.expandWaveEntries(List.of(TANK, RANGED), WaveSpawnMode.ROUND_ROBIN, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, expanded.stream().mapToLong(WaveMonsterEntry::mineralReward).sum());
    }

    private static WaveMonsterEntry entry(String id, int count) {
        return new WaveMonsterEntry(id, 10.0, 0.0, 1.0, AttackKind.MELEE, "minecraft:zombie", null, count);
    }
}
