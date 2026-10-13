package kim.biryeong.semiontd.tower.engineer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class EngineerTrapSignalControllerTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void reverseTraversalMatchesForwardSearchWithBranchesCyclesAndRepeaters() {
        Random random = new Random(73621);
        Direction[] directions = {null, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (int sample = 0; sample < 200; sample++) {
            Map<BlockPos, EngineerTrapSignalSnapshot> circuits = new HashMap<>();
            BlockPos target = new BlockPos(0, 64, 0);
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    if ((x == 0 && z == 0) || random.nextInt(4) == 0) {
                        continue;
                    }
                    var kind = random.nextInt(3) == 0
                            ? EngineerTowers.PlateKind.values()[random.nextInt(4)] : null;
                    circuits.put(new BlockPos(x, 64, z), new EngineerTrapSignalSnapshot(
                            directions[random.nextInt(directions.length)], kind, random.nextInt(160)));
                }
            }
            var distances = EngineerTrapSignalController.distances(circuits, target);
            for (BlockPos start : circuits.keySet()) {
                assertEquals(forwardDistance(circuits, start, target), distances.getOrDefault(start, -1));
            }
            for (boolean recent : new boolean[]{false, true}) {
                assertEquals(legacySelect(circuits, target, 100, 40, recent),
                        EngineerTrapSignalController.select(circuits, target, 100, 40, recent));
            }
        }
    }

    @Test
    void newestPressThenDistanceThenCoordinatesRemainSelectionPriority() {
        BlockPos target = new BlockPos(0, 64, 0);
        Map<BlockPos, EngineerTrapSignalSnapshot> circuits = new HashMap<>();
        circuits.put(new BlockPos(-1, 64, 0), new EngineerTrapSignalSnapshot(null, EngineerTowers.PlateKind.WOOD, 50));
        circuits.put(new BlockPos(1, 64, 0), new EngineerTrapSignalSnapshot(null, EngineerTowers.PlateKind.GOLD, 50));
        var selected = EngineerTrapSignalController.select(circuits, target, 100, 40, true).orElseThrow();
        assertEquals(EngineerTowers.PlateKind.WOOD, selected.kind());
        circuits.put(new BlockPos(2, 64, 0), new EngineerTrapSignalSnapshot(null, EngineerTowers.PlateKind.IRON, 51));
        selected = EngineerTrapSignalController.select(circuits, target, 100, 40, true).orElseThrow();
        assertEquals(EngineerTowers.PlateKind.IRON, selected.kind());
        assertEquals(2, selected.distance());
        assertTrue(EngineerTrapSignalController.select(circuits, target, 100, 52, true).isEmpty());
        assertEquals(EngineerTowers.PlateKind.IRON,
                EngineerTrapSignalController.select(circuits, target, 40, 0, false).orElseThrow().kind());
    }

    @Test
    void changedCircuitAndPressAreObservedWithoutStaleCachedRoutes() {
        BlockPos target = new BlockPos(0, 64, 0);
        BlockPos repeater = new BlockPos(1, 64, 0);
        BlockPos plate = new BlockPos(2, 64, 0);
        Map<BlockPos, EngineerTrapSignalSnapshot> circuits = new HashMap<>();
        circuits.put(repeater, new EngineerTrapSignalSnapshot(Direction.EAST, null, 0));
        circuits.put(plate, new EngineerTrapSignalSnapshot(null, EngineerTowers.PlateKind.STONE, 10));
        assertTrue(EngineerTrapSignalController.select(circuits, target, 10, 0, true).isEmpty());
        circuits.put(repeater, new EngineerTrapSignalSnapshot(Direction.WEST, null, 0));
        assertEquals(2, EngineerTrapSignalController.select(circuits, target, 10, 0, true).orElseThrow().distance());
        circuits.remove(repeater);
        assertTrue(EngineerTrapSignalController.select(circuits, target, 10, 0, true).isEmpty());
        assertTrue(EngineerTrapSignalController.select(Map.of(), target, 10, 0, true).isEmpty());
    }

    @Test
    void occupiedTargetKeepsLegacyPositiveCycleDistanceWithoutRevisitingTerminal() {
        BlockPos target = new BlockPos(0, 64, 0);
        Map<BlockPos, EngineerTrapSignalSnapshot> circuits = new HashMap<>();
        circuits.put(target, new EngineerTrapSignalSnapshot(null, EngineerTowers.PlateKind.GOLD, 50));
        assertEquals(-1, forwardDistance(circuits, target, target));
        assertTrue(EngineerTrapSignalController.distances(circuits, target).isEmpty());
        circuits.put(target.east(), new EngineerTrapSignalSnapshot(null, null, 0));
        assertEquals(2, forwardDistance(circuits, target, target));
        assertEquals(2, EngineerTrapSignalController.distances(circuits, target).get(target));
        assertEquals(legacySelect(circuits, target, 100, 0, true),
                EngineerTrapSignalController.select(circuits, target, 100, 0, true));
    }

    static Optional<EngineerTrapSignalController.Activation> legacySelect(
            Map<BlockPos, EngineerTrapSignalSnapshot> circuits, BlockPos target,
            long now, long oldest, boolean recent) {
        return circuits.entrySet().stream()
                .filter(entry -> entry.getValue().plateKind() != null)
                .filter(entry -> !recent || entry.getValue().pressedAt() >= oldest && entry.getValue().pressedAt() <= now)
                .map(entry -> new Path(entry.getKey(), entry.getValue(), forwardDistance(circuits, entry.getKey(), target)))
                .filter(path -> path.distance() > 0)
                .sorted(Comparator.<Path>comparingLong(path -> path.signal().pressedAt()).reversed()
                        .thenComparingInt(Path::distance)
                        .thenComparingInt(path -> path.position().getX())
                        .thenComparingInt(path -> path.position().getY())
                        .thenComparingInt(path -> path.position().getZ()))
                .map(path -> new EngineerTrapSignalController.Activation(path.distance(), path.signal().plateKind()))
                .findFirst();
    }

    static int forwardDistance(Map<BlockPos, EngineerTrapSignalSnapshot> circuits, BlockPos start, BlockPos target) {
        ArrayDeque<Step> pending = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        pending.addLast(new Step(start, 1));
        visited.add(start);
        while (!pending.isEmpty()) {
            Step step = pending.removeFirst();
            var current = circuits.get(step.position());
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (current == null || !current.permits(direction)) {
                    continue;
                }
                BlockPos adjacent = step.position().relative(direction);
                if (adjacent.equals(target)) {
                    return step.distance();
                }
                var next = circuits.get(adjacent);
                if (next != null && next.permits(direction) && visited.add(adjacent)) {
                    pending.addLast(new Step(adjacent, step.distance() + 1));
                }
            }
        }
        return -1;
    }

    private record Step(BlockPos position, int distance) {
    }

    private record Path(BlockPos position, EngineerTrapSignalSnapshot signal, int distance) {
    }
}
