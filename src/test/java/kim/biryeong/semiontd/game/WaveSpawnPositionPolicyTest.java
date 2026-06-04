package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

class WaveSpawnPositionPolicyTest {
    @Test
    void singleCellSpawnAreaKeepsLegacySpawnPosition() {
        Vec3 spawn = new Vec3(10.5, 64.0, 20.5);
        WaveSpawnPositionPolicy policy = new WaveSpawnPositionPolicy(layout(spawn, BlockBounds.of(
                new BlockPos(10, 64, 20),
                new BlockPos(10, 64, 20)
        )));

        assertEquals(spawn, policy.next());
        assertEquals(spawn, policy.next());
    }

    @Test
    void wideSpawnAreaCyclesThroughEveryCellBeforeWrapping() {
        Vec3 spawn = new Vec3(1.5, 64.0, 1.5);
        WaveSpawnPositionPolicy policy = new WaveSpawnPositionPolicy(layout(spawn, BlockBounds.of(
                new BlockPos(0, 64, 0),
                new BlockPos(2, 64, 2)
        )));

        Vec3 first = policy.next();
        Set<String> visited = new HashSet<>();
        visited.add(key(first));
        for (int i = 0; i < 8; i++) {
            Vec3 next = policy.next();
            visited.add(key(next));
            assertTrue(isInsideXZ(next, 0, 2, 0, 2), "candidate must stay inside spawn area: " + next);
        }

        assertEquals(new Vec3(1.5, 64.0, 1.5), first);
        assertEquals(9, visited.size());
        assertEquals(first, policy.next());
    }

    private static LaneRegionLayout layout(Vec3 spawn, BlockBounds spawnArea) {
        return new LaneRegionLayout(
                1,
                spawn,
                spawnArea,
                List.of(new Vec3(spawn.x, spawn.y, spawn.z + 2.0)),
                new Vec3(spawn.x, spawn.y, spawn.z + 10.0),
                BlockBounds.of(new BlockPos(-5, 63, -5), new BlockPos(5, 66, 20)),
                List.of(new GridPosition(0, 63, 10))
        );
    }

    private static boolean isInsideXZ(Vec3 position, int minX, int maxX, int minZ, int maxZ) {
        return position.x >= minX && position.x < maxX + 1.0
                && position.z >= minZ && position.z < maxZ + 1.0;
    }

    private static String key(Vec3 position) {
        return position.x + "," + position.y + "," + position.z;
    }
}
