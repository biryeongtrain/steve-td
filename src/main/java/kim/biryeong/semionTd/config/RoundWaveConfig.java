package kim.biryeong.semionTd.config;

import java.util.List;
import java.util.Map;

public record RoundWaveConfig(int round, Map<String, List<WaveMonsterEntry>> lanes) {
    public static final String DEFAULT_LANE_KEY = "default";

    public RoundWaveConfig {
        if (round < 1) {
            throw new IllegalArgumentException("Round must be positive.");
        }
        lanes = lanes == null ? Map.of() : Map.copyOf(lanes);
    }

    public List<WaveMonsterEntry> entriesForLane(String laneKey) {
        List<WaveMonsterEntry> specific = lanes.get(laneKey);
        if (specific != null && !specific.isEmpty()) {
            return specific;
        }
        return lanes.getOrDefault(DEFAULT_LANE_KEY, List.of());
    }
}
