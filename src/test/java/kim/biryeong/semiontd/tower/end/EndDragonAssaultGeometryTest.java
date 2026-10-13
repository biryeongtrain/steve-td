package kim.biryeong.semiontd.tower.end;

import java.util.List;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;
import static org.junit.jupiter.api.Assertions.*;

class EndDragonAssaultGeometryTest {
    @Test
    void templateLaneIncludesSeparateSevenBySevenSpawnAndIgnoresSharedWaypoints() {
        var layout = new LaneRegionLayout(1, new Vec3(-50.5, 66, 50.5),
                bounds(-54, 47, -48, 53), List.of(new Vec3(-.5, 66, 50.5), new Vec3(-.5, 66, .5)),
                new Vec3(49.5, 66, .5), bounds(-47, 47, -5, 53), List.of(), 1);
        var geometry = EndDragonAssaultGeometry.from(layout, 66);
        assertEquals(new Vec3(-4, 67, 50.5), geometry.rear());
        assertEquals(new Vec3(-54, 67, 50.5), geometry.front());
        assertEquals(50, geometry.length());
        assertEquals(7, geometry.width());
        assertEquals(new Vec3(1, 76, 50.5), geometry.airborneRear(66, 10));
        assertEquals(new Vec3(-59, 67, 50.5), geometry.point(geometry.length() + 5));
        for (int x = -54; x <= -5; x++) {
            for (int z = 47; z <= 53; z++) {
                Vec3 point = new Vec3(x + .5, 66, z + .5);
                assertTrue(geometry.swept(point, 0, 50), point.toString());
                assertTrue(java.util.stream.IntStream.range(0, 60)
                        .anyMatch(tick -> geometry.swept(point, 50.0 * tick / 60, 50.0 * (tick + 1) / 60)));
            }
        }
        assertFalse(geometry.swept(new Vec3(-20, 66, 46.999), 0, 50));
        assertFalse(geometry.swept(new Vec3(-20, 66, 54), 0, 50));
        assertFalse(geometry.swept(new Vec3(-54.001, 66, 50), 0, 50));
        assertFalse(geometry.swept(new Vec3(-3.999, 66, 50), 0, 50));
    }

    @Test
    void arbitraryLengthsAndBothAxesHaveNoGapsBetweenTickSweeps() {
        for (int length : new int[]{19, 50, 73, 137}) {
            for (boolean x : new boolean[]{true, false}) {
                for (int sign : new int[]{-1, 1}) {
                    var area = bounds(0, 0, x ? length - 1 : 6, x ? 6 : length - 1);
                    Vec3 front = x ? new Vec3(sign < 0 ? .5 : length - .5, 17, 3.5)
                            : new Vec3(3.5, 17, sign < 0 ? .5 : length - .5);
                    Vec3 rear = x ? new Vec3(sign < 0 ? length - .5 : .5, 17, 3.5)
                            : new Vec3(3.5, 17, sign < 0 ? length - .5 : .5);
                    var layout = new LaneRegionLayout(1, front, List.of(rear), rear, area, List.of());
                    var geometry = EndDragonAssaultGeometry.from(layout, 16);
                    assertEquals(length, geometry.length());
                    assertEquals(7, geometry.width());
                    Vec3 side = new Vec3(-geometry.direction().z, 0, geometry.direction().x);
                    for (int cell = 0; cell < length; cell++) {
                        for (double edge : new double[]{-3.49, 0, 3.49}) {
                            Vec3 point = geometry.point(cell + .5).add(side.scale(edge));
                            assertTrue(java.util.stream.IntStream.range(0, 60).anyMatch(tick ->
                                    geometry.swept(point, length * tick / 60.0, length * (tick + 1) / 60.0)));
                        }
                    }
                }
            }
        }
    }

    private static BlockBounds bounds(int minX, int minZ, int maxX, int maxZ) {
        return BlockBounds.of(new BlockPos(minX, 65, minZ), new BlockPos(maxX, 65, maxZ));
    }
}
