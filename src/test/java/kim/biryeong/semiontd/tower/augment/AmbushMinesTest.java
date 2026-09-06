package kim.biryeong.semiontd.tower.augment;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class AmbushMinesTest {
    @Test void firstHorizontalSegmentWinsAndPlacementDoesNotParticipate() {
        var direction = AmbushMines.entranceDirection(List.of(Vec3.ZERO, Vec3.ZERO,
                new Vec3(0, 4, 0), new Vec3(3, 7, 4), new Vec3(-12, 7, 4))).orElseThrow();
        assertEquals(-.6, direction.x, 1e-9);
        assertEquals(0, direction.y, 1e-9);
        assertEquals(-.8, direction.z, 1e-9);
        assertEquals(1, direction.length(), 1e-9);
        assertEquals(6, direction.scale(6).length(), 1e-9);
    }

    @Test void rotatedPathsFollowTheSameRule() {
        var direction = AmbushMines.entranceDirection(List.of(new Vec3(10, 0, 5), new Vec3(6, 0, 8))).orElseThrow();
        assertEquals(.8, direction.x, 1e-9);
        assertEquals(-.6, direction.z, 1e-9);
    }

    @Test void verticalOnlyOrMissingPathIsIneligible() {
        assertTrue(AmbushMines.entranceDirection(List.of()).isEmpty());
        assertTrue(AmbushMines.entranceDirection(List.of(Vec3.ZERO)).isEmpty());
        assertTrue(AmbushMines.entranceDirection(List.of(Vec3.ZERO, new Vec3(0, 4, 0))).isEmpty());
    }
}
