package kim.biryeong.semiontd.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void personalWaypointsExcludeSharedFinalPath() {
        Vec3 personal = new Vec3(10.5, 64.0, 22.5);
        Vec3 shared = new Vec3(10.5, 64.0, 26.5);
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                new Vec3(10.5, 64.0, 20.5),
                BlockBounds.of(new BlockPos(10, 64, 20), new BlockPos(10, 64, 20)),
                List.of(personal, shared),
                new Vec3(10.5, 64.0, 30.5),
                BlockBounds.of(new BlockPos(8, 63, 18), new BlockPos(12, 66, 32)),
                List.of(new GridPosition(10, 63, 30)),
                1
        );

        assertEquals(List.of(personal), layout.personalWaypoints());
        assertEquals(List.of(personal, shared), layout.waypoints());
    }

    @Test
    void finalDefenseTowerAreaUsesHorizontalBoundsAndPreservesHeightWhenClamped() {
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                new Vec3(10.5, 64.0, 20.5),
                List.of(new Vec3(10.5, 64.0, 22.5)),
                new Vec3(10.5, 64.0, 30.5),
                BlockBounds.of(new BlockPos(8, 63, 18), new BlockPos(12, 66, 32)),
                List.of(new GridPosition(10, 63, 30))
        );

        assertTrue(layout.isInsideFinalDefenseTowerArea(new Vec3(10.5, 62.75, 30.5)));
        assertTrue(layout.isInsideFinalDefenseTowerArea(new Vec3(10.5, 65.0, 30.5)));
        assertFalse(layout.isInsideFinalDefenseTowerArea(new Vec3(11.0, 64.0, 30.5)));

        Vec3 clamped = layout.clampToFinalDefenseTowerArea(new Vec3(20.0, 65.0, 40.0));
        assertEquals(65.0, clamped.y);
        assertTrue(layout.isInsideFinalDefenseTowerArea(clamped));
    }
}
