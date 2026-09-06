package kim.biryeong.semiontd.config;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record RoundWaveConfig(
        int round,
        WaveSpawnMode spawnMode,
        int spawnIntervalTicks,
        Map<String, List<WaveMonsterEntry>> lanes,
        String templateId,
        Long mineralRewardBudget
) {
    public static final String DEFAULT_LANE_KEY = "default";

    public RoundWaveConfig(int round, Map<String, List<WaveMonsterEntry>> lanes) {
        this(round, WaveSpawnMode.SEQUENTIAL, 1, lanes);
    }

    public RoundWaveConfig(int round, WaveSpawnMode spawnMode, int spawnIntervalTicks, Map<String, List<WaveMonsterEntry>> lanes) {
        this(round, spawnMode, spawnIntervalTicks, lanes, null, null);
    }

    public RoundWaveConfig {
        if (round < 1) {
            throw new IllegalArgumentException("Round must be positive.");
        }
        spawnMode = spawnMode == null ? WaveSpawnMode.SEQUENTIAL : spawnMode;
        spawnIntervalTicks = spawnIntervalTicks < 1 ? 1 : spawnIntervalTicks;
        lanes = lanes == null ? Map.of() : lanes.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        if (mineralRewardBudget != null && mineralRewardBudget < 0) {
            throw new IllegalArgumentException("Wave reward budget cannot be negative.");
        }
    }

    public List<WaveMonsterEntry> entriesForLane(String laneKey) {
        List<WaveMonsterEntry> specific = lanes.get(laneKey);
        if (specific != null && !specific.isEmpty()) {
            return specific;
        }
        return lanes.getOrDefault(DEFAULT_LANE_KEY, List.of());
    }

    public long rewardBudgetForLane(String laneKey) {
        if (mineralRewardBudget != null) {
            return mineralRewardBudget;
        }
        return entriesForLane(laneKey).stream()
                .mapToLong(entry -> Math.multiplyExact(entry.mineralReward(), entry.count()))
                .reduce(0, Math::addExact);
    }
}
