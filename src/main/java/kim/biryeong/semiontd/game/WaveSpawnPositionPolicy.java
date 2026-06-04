package kim.biryeong.semiontd.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

final class WaveSpawnPositionPolicy {
    private final List<Vec3> candidates;
    private int cursor;

    WaveSpawnPositionPolicy(LaneRegionLayout laneLayout) {
        this.candidates = candidatesFor(laneLayout);
    }

    Vec3 next() {
        if (candidates.isEmpty()) {
            throw new IllegalStateException("Wave spawn position candidates must not be empty.");
        }
        Vec3 position = candidates.get(cursor);
        cursor = (cursor + 1) % candidates.size();
        return position;
    }

    private static List<Vec3> candidatesFor(LaneRegionLayout laneLayout) {
        BlockBounds area = laneLayout.spawnArea();
        Vec3 spawn = laneLayout.spawn();
        if (area == null) {
            return List.of(spawn);
        }

        int floorY = area.min().getY();
        List<Vec3> positions = new ArrayList<>();
        for (BlockPos blockPos : area) {
            if (blockPos.getY() != floorY) {
                continue;
            }
            positions.add(new Vec3(blockPos.getX() + 0.5, spawn.y, blockPos.getZ() + 0.5));
        }
        if (positions.isEmpty()) {
            return List.of(spawn);
        }
        positions.sort(Comparator
                .comparingDouble((Vec3 position) -> position.distanceToSqr(spawn))
                .thenComparingDouble(position -> position.x)
                .thenComparingDouble(position -> position.z));
        return List.copyOf(positions);
    }
}
