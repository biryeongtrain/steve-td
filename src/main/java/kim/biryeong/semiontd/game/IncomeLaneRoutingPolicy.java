package kim.biryeong.semiontd.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import kim.biryeong.semiontd.config.IncomeLaneRoutingConfig;

final class IncomeLaneRoutingPolicy {
    private final IncomeLaneRoutingConfig config;
    private final Random random;
    private int roundRobinCursor;

    IncomeLaneRoutingPolicy(IncomeLaneRoutingConfig config, Random random) {
        this.config = config == null ? IncomeLaneRoutingConfig.defaultConfig() : config;
        this.random = random == null ? new Random() : random;
    }

    Optional<PlayerLane> select(List<PlayerLane> lanes) {
        if (lanes == null || lanes.isEmpty()) {
            return Optional.empty();
        }
        if (!config.enabled() || config.mode() == IncomeLaneRoutingConfig.Mode.RANDOM) {
            return Optional.of(lanes.get(random.nextInt(lanes.size())));
        }
        return selectLeastThreatPressure(lanes);
    }

    private Optional<PlayerLane> selectLeastThreatPressure(List<PlayerLane> lanes) {
        double lowestPressure = lanes.stream()
                .mapToDouble(this::pressure)
                .min()
                .orElse(0.0);
        List<PlayerLane> candidates = lanes.stream()
                .filter(lane -> Double.compare(pressure(lane), lowestPressure) == 0)
                .sorted(Comparator.comparingInt(PlayerLane::laneId))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (config.tieBreakMode() == IncomeLaneRoutingConfig.TieBreakMode.RANDOM) {
            return Optional.of(candidates.get(random.nextInt(candidates.size())));
        }
        PlayerLane selected = candidates.get(Math.floorMod(roundRobinCursor, candidates.size()));
        roundRobinCursor++;
        return Optional.of(selected);
    }

    private double pressure(PlayerLane lane) {
        return lane.queuedSummonThreat() * config.queuedThreatWeight()
                + lane.pendingNextRoundSummonThreat() * config.nextRoundQueuedThreatWeight();
    }
}
