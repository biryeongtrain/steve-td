package kim.biryeong.semiontd.game;

import java.util.ArrayList;
import java.util.List;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
import kim.biryeong.semiontd.config.WaveSpawnMode;

final class GameLaneWaveOrder {
    private GameLaneWaveOrder() {}

    static List<WaveMonsterEntry> expandWaveEntries(List<WaveMonsterEntry> entries, WaveSpawnMode spawnMode) {
        return expandWaveEntries(entries, spawnMode, null);
    }

    static List<WaveMonsterEntry> expandWaveEntries(List<WaveMonsterEntry> entries, WaveSpawnMode spawnMode, Long rewardBudget) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<WaveMonsterEntry> expanded = new ArrayList<>();
        List<WaveMonsterEntry> healers = entries.stream().filter(entry -> entry.healing() != null && entry.count() > 0).toList();
        if (healers.size() > 1 || healers.stream().mapToInt(WaveMonsterEntry::count).sum() > 5) {
            throw new IllegalArgumentException("A natural wave supports one healer type with at most five units.");
        }
        entries = entries.stream().filter(entry -> entry.healing() == null).toList();
        if (spawnMode != WaveSpawnMode.ROUND_ROBIN) {
            for (WaveMonsterEntry entry : entries) {
                for (int i = 0; i < entry.count(); i++) {
                    expanded.add(entry);
                }
            }
        } else {
            int maxCount = entries.stream().mapToInt(WaveMonsterEntry::count).max().orElse(0);
            for (int index = 0; index < maxCount; index++) {
                for (WaveMonsterEntry entry : entries) {
                    if (index < entry.count()) {
                        expanded.add(entry);
                    }
                }
            }
        }
        if (!healers.isEmpty()) {
            WaveMonsterEntry healer = healers.getFirst();
            int combatCount = expanded.size();
            int intervals = Math.max(1, healer.count() - 1);
            for (int i = 0; i < healer.count(); i++) {
                int combatIndex = (int) ((long) combatCount * (55L * intervals + 30L * i) / (100L * intervals));
                expanded.add(combatIndex + i, healer);
            }
        }
        if (rewardBudget != null) {
            if (rewardBudget < 0) {throw new IllegalArgumentException("Wave reward budget cannot be negative.");}
            int size = expanded.size();
            for (int i = 0; i < size; i++) {
                // Quotient/remainder form avoids overflowing i * rewardBudget.
                long reward = rewardBudget / size + ((long) (i + 1) * (rewardBudget % size) / size)
                        - ((long) i * (rewardBudget % size) / size);
                expanded.set(i, expanded.get(i).withMineralReward(reward));
            }
        }
        return expanded;
    }

}
