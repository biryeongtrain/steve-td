package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
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

    private static WaveMonsterEntry entry(String id, int count) {
        return new WaveMonsterEntry(id, 10.0, 0.0, 1.0, AttackKind.MELEE, "minecraft:zombie", null, count);
    }
}
