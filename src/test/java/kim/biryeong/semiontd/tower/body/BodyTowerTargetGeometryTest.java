package kim.biryeong.semiontd.tower.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

class BodyTowerTargetGeometryTest {
    private static final Vec3 ORIGIN = new Vec3(0.0, 64.0, 0.0);
    private static final Vec3 TOWARD_SPAWN = new Vec3(1.0, 0.0, 0.0);

    @Test
    void eyeHitsOnlyInsideItsFixedForwardCorridor() {
        assertTrue(BodyTowerTargetGeometry.insideEyeRay(
                ORIGIN, new Vec3(8.0, 64.0, 0.5), TOWARD_SPAWN, 12.0, 1.25
        ));
        assertFalse(BodyTowerTargetGeometry.insideEyeRay(
                ORIGIN, new Vec3(-1.0, 64.0, 0.0), TOWARD_SPAWN, 12.0, 1.25
        ));
        assertFalse(BodyTowerTargetGeometry.insideEyeRay(
                ORIGIN, new Vec3(8.0, 64.0, 2.0), TOWARD_SPAWN, 12.0, 1.25
        ));
        assertFalse(BodyTowerTargetGeometry.insideEyeRay(
                ORIGIN, new Vec3(13.0, 64.0, 0.0), TOWARD_SPAWN, 12.0, 1.25
        ));
    }

    @Test
    void eyeFacesAgainstTheLaneTravelDirection() {
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                new Vec3(0.0, 64.0, 0.0),
                List.of(new Vec3(10.0, 64.0, 0.0)),
                new Vec3(10.0, 64.0, 10.0),
                BlockBounds.of(new BlockPos(0, 63, 0), new BlockPos(10, 66, 10)),
                List.of(new GridPosition(9, 64, 9))
        );

        assertEquals(new Vec3(-1.0, 0.0, 0.0), BodyTowerTargetGeometry.eyeDirection(layout));
        assertEquals(new Vec3(0.0, 0.0, -1.0), BodyTowerTargetGeometry.eyeDirection(layout, true));
    }

    @Test
    void eyeCorridorIncludesExactHorizontalBoundariesWithoutHeightFiltering() {
        assertTrue(BodyTowerTargetGeometry.insideEyeRay(
                ORIGIN, new Vec3(12.0, 120.0, 1.25), TOWARD_SPAWN, 12.0, 1.25
        ));
        assertTrue(BodyTowerTargetGeometry.insideEyeRay(
                ORIGIN, new Vec3(0.0, 0.0, -1.25), TOWARD_SPAWN, 12.0, 1.25
        ));
        assertFalse(BodyTowerTargetGeometry.insideEyeRay(
                ORIGIN, new Vec3(12.0001, 64.0, 0.0), TOWARD_SPAWN, 12.0, 1.25
        ));
    }

}
