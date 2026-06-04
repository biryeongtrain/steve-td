package kim.biryeong.semiontd.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import kim.biryeong.semiontd.game.GridPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

class LaneRegionLayoutTest {
    @Test
    void legacyConstructorUsesSingleCellSpawnAreaFallback() {
        Vec3 spawn = new Vec3(10.5, 64.0, 20.5);
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                spawn,
                List.of(new Vec3(10.5, 64.0, 22.5)),
                new Vec3(10.5, 64.0, 30.5),
                BlockBounds.of(new BlockPos(8, 63, 18), new BlockPos(12, 66, 32)),
                List.of(new GridPosition(10, 63, 30))
        );

        assertEquals(BlockPos.containing(spawn), layout.spawnArea().min());
        assertEquals(BlockPos.containing(spawn), layout.spawnArea().max());
    }

    @Test
    void explicitSpawnAreaIsPreservedSeparatelyFromSpawnCenter() {
        Vec3 spawn = new Vec3(10.5, 64.0, 20.5);
        BlockBounds spawnArea = BlockBounds.of(new BlockPos(9, 64, 20), new BlockPos(11, 64, 20));
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                spawn,
                spawnArea,
                List.of(new Vec3(10.5, 64.0, 22.5)),
                new Vec3(10.5, 64.0, 30.5),
                BlockBounds.of(new BlockPos(8, 63, 18), new BlockPos(12, 66, 32)),
                List.of(new GridPosition(10, 63, 30))
        );

        assertEquals(spawnArea.min(), layout.spawnArea().min());
        assertEquals(spawnArea.max(), layout.spawnArea().max());
        assertEquals(spawn, layout.spawn());
    }
}
