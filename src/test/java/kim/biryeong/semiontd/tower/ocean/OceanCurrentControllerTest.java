package kim.biryeong.semiontd.tower.ocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class OceanCurrentControllerTest {
    @Test
    void tideStartsAfterFirstPeriodAndEndsAtExactDurationBoundary() {
        OceanCurrentController controller = new OceanCurrentController();
        for (int i = 0; i < 239; i++) {
            controller.tick();
            assertFalse(controller.tideActive(240, 80));
        }
        controller.tick();
        assertTrue(controller.tideActive(240, 80));
        for (int i = 0; i < 79; i++) {
            controller.tick();
        }
        assertTrue(controller.tideActive(240, 80));
        controller.tick();
        assertFalse(controller.tideActive(240, 80));
    }

    @Test
    void spendingCarriesRemaindersAndCopiedStateDoesNotShareProgress() {
        OceanCurrentController source = new OceanCurrentController();
        source.recordSpent(75, 30);
        source.tick();
        OceanCurrentController upgraded = new OceanCurrentController();
        upgraded.restore(source.snapshot());
        assertEquals(new OceanCurrentSnapshot(1, 15, 2), upgraded.snapshot());
        assertTrue(source.consume());
        upgraded.recordSpent(5, 20);
        assertEquals(3, upgraded.charges());
        assertEquals(0, upgraded.waterSpent());
        assertEquals(1, source.charges());
        assertEquals(15, source.waterSpent());
        source.resetRound();
        assertEquals(new OceanCurrentSnapshot(0, 0, 0), source.snapshot());
        assertEquals(3, upgraded.charges());
        assertFalse(source.consume());
    }
}
