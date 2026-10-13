package kim.biryeong.semiontd.tower.engineer;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

final class EngineerTrapSignalController {
    private static final Comparator<PlatePath> ORDER = Comparator.comparingLong(PlatePath::pressedAt).reversed()
            .thenComparingInt(PlatePath::distance)
            .thenComparingInt(path -> path.position().getX())
            .thenComparingInt(path -> path.position().getY())
            .thenComparingInt(path -> path.position().getZ());

    private EngineerTrapSignalController() {
    }

    static Optional<Activation> select(Map<BlockPos, EngineerTrapSignalSnapshot> circuits, BlockPos target,
            long now, long oldestAccepted, boolean requireRecentPress) {
        Map<BlockPos, Integer> distances = distances(circuits, target);
        PlatePath best = null;
        for (var entry : circuits.entrySet()) {
            EngineerTrapSignalSnapshot circuit = entry.getValue();
            if (circuit.plateKind() == null || (requireRecentPress
                    && (circuit.pressedAt() < oldestAccepted || circuit.pressedAt() > now))) {
                continue;
            }
            int distance = distances.getOrDefault(entry.getKey(), -1);
            if (distance <= 0) {
                continue;
            }
            PlatePath candidate = new PlatePath(circuit.pressedAt(), distance, entry.getKey(), circuit.plateKind());
            if (best == null || ORDER.compare(candidate, best) < 0) {
                best = candidate;
            }
        }
        return best == null ? Optional.empty() : Optional.of(new Activation(best.distance(), best.kind()));
    }

    static Map<BlockPos, Integer> distances(Map<BlockPos, EngineerTrapSignalSnapshot> circuits, BlockPos target) {
        Map<BlockPos, Integer> distances = new HashMap<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        pending.addLast(target);
        while (!pending.isEmpty()) {
            BlockPos currentPosition = pending.removeFirst();
            boolean terminal = currentPosition.equals(target);
            EngineerTrapSignalSnapshot current = circuits.get(currentPosition);
            int currentDistance = terminal ? 0 : distances.get(currentPosition);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (!terminal && !current.permits(direction)) {
                    continue;
                }
                BlockPos previousPosition = currentPosition.relative(direction.getOpposite());
                EngineerTrapSignalSnapshot previous = circuits.get(previousPosition);
                if (previous != null && previous.permits(direction) && !distances.containsKey(previousPosition)) {
                    distances.put(previousPosition, currentDistance + 1);
                    if (!previousPosition.equals(target)) {
                        pending.addLast(previousPosition);
                    }
                }
            }
        }
        return distances;
    }

    record Activation(int distance, EngineerTowers.PlateKind kind) {
    }

    private record PlatePath(long pressedAt, int distance, BlockPos position, EngineerTowers.PlateKind kind) {
    }
}
