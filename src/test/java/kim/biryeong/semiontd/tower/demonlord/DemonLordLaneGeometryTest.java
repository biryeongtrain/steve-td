package kim.biryeong.semiontd.tower.demonlord;

import java.util.List;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;
import static org.junit.jupiter.api.Assertions.*;

class DemonLordLaneGeometryTest {
    @Test
    void bentPersonalLaneExcludesSharedBossPathAndUsesTravelDistance() {
        var bounds = new BlockBounds(new BlockPos(0, 0, 0), new BlockPos(10, 10, 10));
        var layout = new LaneRegionLayout(1, new Vec3(1, 3, 1),
                List.of(new Vec3(1, 3, 5), new Vec3(7, 3, 5), new Vec3(20, 3, 5)),
                new Vec3(40, 3, 5), bounds, List.of());
        assertEquals(new Vec3(2, 3, 5), DemonLordLaneGeometry.laneCentre(layout));
    }

    @Test
    void zeroLengthSegmentsAndExactMidpointRemainDeterministic() {
        Vec3 first = new Vec3(1, 4, 1);
        Vec3 corner = new Vec3(1, 4, 5);
        assertEquals(corner, DemonLordLaneGeometry.midpointAlong(List.of(first, first, corner, new Vec3(5, 4, 5))));
        assertEquals(first, DemonLordLaneGeometry.midpointAlong(List.of(first, first, first)));
    }

    @Test
    void horizontalBoundsIgnoreHeightAndKeepUpperEdgeExclusive() {
        var bounds = new BlockBounds(new BlockPos(-2, 0, -4), new BlockPos(2, 3, 4));
        assertTrue(DemonLordLaneGeometry.containsHorizontally(bounds, new Vec3(-2, 500, -4)));
        assertTrue(DemonLordLaneGeometry.containsHorizontally(bounds, new Vec3(2.999, -500, 4.999)));
        assertFalse(DemonLordLaneGeometry.containsHorizontally(bounds, new Vec3(3, 0, 0)));
        assertFalse(DemonLordLaneGeometry.containsHorizontally(bounds, new Vec3(0, 0, 5)));
    }

    @Test
    void absentPathUsesBoundingCentreAtSpawnHeight() {
        var bounds = new BlockBounds(new BlockPos(0, 0, 0), new BlockPos(9, 10, 7));
        var layout = new LaneRegionLayout(1, new Vec3(-10, 7, -10), List.of(),
                new Vec3(20, 7, 20), bounds, List.of());
        assertEquals(new Vec3(5, 7, 4), DemonLordLaneGeometry.laneCentre(layout));
        var one = new LaneRegionLayout(1, new Vec3(1, 7, 2), List.of(),
                new Vec3(20, 7, 20), bounds, List.of());
        assertEquals(one.spawn(), DemonLordLaneGeometry.laneCentre(one));
    }
}
