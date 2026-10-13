package kim.biryeong.semiontd.tower.nether;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class NetherBloodChargeControllerTest {
    @Test
    void thresholdChangesPreserveExistingChargesAndResidualLoss() {
        NetherBloodChargeController controller = new NetherBloodChargeController();
        controller.recordNaturalLoss(25, 10);
        controller.recordNaturalLoss(35, 20);
        assertEquals(List.of(10.0, 10.0, 20.0, 20.0), controller.snapshot().charges());
        assertEquals(0.0, controller.snapshot().naturalHealthLoss());
        assertEquals(10, controller.consume());
        assertEquals(10, controller.consume());
        assertEquals(20, controller.consume());
        assertEquals(20, controller.consume());
        assertEquals(0, controller.consume());
    }

    @Test
    void snapshotCopyIsImmutableAndIndependentAcrossConsumptionAndRoundReset() {
        NetherBloodChargeController source = new NetherBloodChargeController();
        source.recordNaturalLoss(25, 10);
        NetherBloodChargeSnapshot snapshot = source.snapshot();
        NetherBloodChargeController copy = new NetherBloodChargeController();
        copy.restore(snapshot);
        source.consume();
        source.resetRound();
        assertEquals(2, copy.chargeCount());
        assertEquals(List.of(10.0, 10.0), snapshot.charges());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.charges().clear());
        copy.recordNaturalLoss(5, 10);
        assertEquals(3, copy.chargeCount());
        assertEquals(new NetherBloodChargeSnapshot(0, List.of()), source.snapshot());
    }

    @Test
    void thresholdEpsilonAndDisabledThresholdKeepOriginalAccumulationRules() {
        NetherBloodChargeController controller = new NetherBloodChargeController();
        controller.recordNaturalLoss(9.9999999995, 10);
        assertEquals(10, controller.consume());
        assertEquals(0, controller.snapshot().naturalHealthLoss());
        controller.recordNaturalLoss(5, 0);
        assertEquals(0, controller.chargeCount());
        controller.recordNaturalLoss(5, 10);
        assertEquals(10, controller.consume());
    }
}
