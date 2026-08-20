package kim.biryeong.semiontd.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

final class SemionLaneIndicatorServiceTest {
    @Test
    void directionMarkersFollowTheLanePathTowardTheBoss() {
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                new Vec3(0.0, 0.0, 0.0),
                List.of(new Vec3(10.0, 0.0, 0.0)),
                new Vec3(10.0, 0.0, 10.0),
                BlockBounds.of(BlockPos.ZERO, new BlockPos(10, 1, 10)),
                List.of()
        );

        List<SemionLaneIndicatorService.DirectionArrow> initial = SemionLaneIndicatorService.directionArrows(layout, 0);
        assertPosition(initial.get(0).head(), 0.0, 2.0, 0.0);
        assertPosition(initial.get(0).leftWing(), -2.5, 2.0, 1.75);
        assertPosition(initial.get(0).rightWing(), -2.5, 2.0, -1.75);
        assertPosition(initial.get(1).head(), 5.0, 2.0, 0.0);
        assertPosition(initial.get(2).head(), 10.0, 2.0, 0.0);
        assertPosition(initial.get(3).head(), 10.0, 2.0, 5.0);

        List<SemionLaneIndicatorService.DirectionArrow> advanced = SemionLaneIndicatorService.directionArrows(layout, 10);
        assertPosition(advanced.get(0).head(), 1.0, 2.0, 0.0);
        assertPosition(advanced.get(1).head(), 6.0, 2.0, 0.0);
        assertPosition(advanced.get(2).head(), 10.0, 2.0, 1.0);
        assertPosition(advanced.get(3).head(), 10.0, 2.0, 6.0);

        List<SemionLaneIndicatorService.DirectionArrow> repeated = SemionLaneIndicatorService.directionArrows(layout, 200);
        for (int index = 0; index < initial.size(); index++) {
            assertEquals(initial.get(index), repeated.get(index));
        }
    }

    private static void assertPosition(Vec3 actual, double x, double y, double z) {
        assertEquals(x, actual.x, 0.0001);
        assertEquals(y, actual.y, 0.0001);
        assertEquals(z, actual.z, 0.0001);
    }
}
