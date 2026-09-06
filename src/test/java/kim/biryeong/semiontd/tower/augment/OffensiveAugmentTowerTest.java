package kim.biryeong.semiontd.tower.augment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class OffensiveAugmentTowerTest {
    @Test
    void giantHunterIncludesBothRangeBoundariesAndRejectsDeadZone() {
        assertFalse(OffensiveAugmentTower.inRange(8.999, 3, 9));
        assertTrue(OffensiveAugmentTower.inRange(9, 3, 9));
        assertTrue(OffensiveAugmentTower.inRange(81, 3, 9));
        assertFalse(OffensiveAugmentTower.inRange(81.001, 3, 9));
        assertFalse(OffensiveAugmentTower.inRange(Double.NaN, 3, 9));
    }

    @Test
    void capacitorOnlyCompletesUninterruptedEmptyRangePeriodsAndKeepsCompletedCharges() {
        var state = OffensiveAugmentTower.CapacitorState.EMPTY;
        for (int i = 0; i < 39; i++) state = state.advance(false, 40, 3);
        assertEquals(0, state.charges());
        assertEquals(39, state.idleTicks());
        state = state.advance(true, 40, 3);
        assertEquals(OffensiveAugmentTower.CapacitorState.EMPTY, state);
        for (int i = 0; i < 55; i++) state = state.advance(false, 40, 3);
        assertEquals(1, state.charges());
        assertEquals(15, state.idleTicks());
        state = state.advance(true, 40, 3);
        assertEquals(1, state.charges());
        assertEquals(0, state.idleTicks());
        for (int i = 0; i < 160; i++) state = state.advance(false, 40, 3);
        assertEquals(3, state.charges());
        assertEquals(0, state.idleTicks());
    }

    @Test
    void cocoonPreservesSuccessfulWavesAcrossDestructionAndHatchesOnlyInNextPrepare() {
        var state = OffensiveAugmentTower.CocoonState.EMPTY.start(15).settle(15, true);
        assertEquals(1, state.successes());
        assertSame(state, state.settle(15, true));
        state = state.start(16).destroyed(16).settle(16, true);
        assertEquals(1, state.successes());
        state = state.start(17).settle(17, true);
        assertEquals(2, state.successes());
        assertFalse(state.hatched());
        assertSame(state, state.hatch(17, 2));
        state = state.hatch(18, 2);
        assertTrue(state.hatched());
        assertSame(state, state.hatch(18, 2));
        assertEquals(2, state.start(18).settle(18, true).successes());
    }

    @Test
    void cocoonDoesNotCountUnstartedWavesOrEliminatedParticipants() {
        var state = OffensiveAugmentTower.CocoonState.EMPTY;
        assertSame(state, state.settle(5, true));
        state = state.start(5).settle(5, false);
        assertEquals(0, state.successes());
        assertFalse(state.hatch(6, 2).hatched());
        assertSame(state, state.start(5));
    }

    @Test
    void ordnanceCountsOnlyUniqueSuccessfulPurchasesAndClampsInitialAmmunition() {
        UUID transaction = UUID.randomUUID();
        var state = OffensiveAugmentTower.OrdnanceState.EMPTY;
        assertSame(state, state.purchase(transaction, 250));
        state = state.prepare(15).purchase(transaction, 250);
        assertEquals(250, state.paidEmerald());
        assertSame(state, state.purchase(transaction, 250));
        assertSame(state, state.purchase(null, 250));
        assertSame(state, state.purchase(UUID.randomUUID(), 0));
        var combat = state.startCombat(15, 100, 4);
        assertEquals(2, combat.shells());
        assertSame(combat, combat.purchase(UUID.randomUUID(), 100));
        assertSame(combat, combat.startCombat(15, 100, 4));
        assertEquals(4, state.purchase(UUID.randomUUID(), 800).startCombat(15, 100, 4).shells());
    }

    @Test
    void ordnanceCooldownUsesActualLastShotAndDoesNotCatchUpAfterIdle() {
        var state = OffensiveAugmentTower.OrdnanceState.EMPTY.prepare(15)
                .purchase(UUID.randomUUID(), 400).startCombat(15, 100, 4);
        state = state.fire(20, 100);
        assertEquals(3, state.shells());
        assertSame(state, state.fire(119, 100));
        state = state.fire(120, 100);
        assertEquals(2, state.shells());
        state = state.fire(1000, 100);
        assertEquals(1, state.shells());
        assertSame(state, state.fire(1000, 100));
        assertSame(state, state.fire(1099, 100));
        state = state.fire(1100, 100);
        assertEquals(0, state.shells());
        assertSame(state, state.fire(1200, 100));
    }

    @Test
    void ordnanceDoesNotCarryUnusedShellsOrRemainderAcrossWaves() {
        UUID transaction = UUID.randomUUID();
        var state = OffensiveAugmentTower.OrdnanceState.EMPTY.prepare(15).purchase(transaction, 250);
        assertSame(state, state.prepare(15));
        state = state.startCombat(15, 100, 4).fire(20, 100).endCombat();
        assertEquals(0, state.shells());
        assertEquals(0, state.paidEmerald());
        assertSame(state, state.purchase(transaction, 250));
        state = state.prepare(16).purchase(UUID.randomUUID(), 50).startCombat(16, 100, 4);
        assertEquals(0, state.shells());
        assertEquals(50, state.paidEmerald());
    }

    @Test
    void logicalStateCopiesCannotMutatePreviouslyCapturedTransactions() {
        UUID first = UUID.randomUUID();
        var original = OffensiveAugmentTower.OrdnanceState.EMPTY.prepare(15).purchase(first, 100);
        var next = original.purchase(UUID.randomUUID(), 100);
        assertEquals(1, original.transactions().size());
        assertEquals(2, next.transactions().size());
        assertEquals(100, original.paidEmerald());
    }
}
