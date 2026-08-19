package kim.biryeong.semiontd.tower.warlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WarlockStateTest {
    @Test
    void absorptionSeparatesPermanentAndRoundState() {
        WarlockState state = new WarlockState();

        state.absorbForRound(100.0, 20.0, 0.40);
        assertEquals(40.0, state.roundHealthBonus(), 0.0001);
        state.absorbPermanently(100.0, 20.0, 0.025, 0.05);
        state.absorbAttackInterval(20, 12, 15.0);

        assertEquals(40.0, state.roundHealthBonus(), 0.0001);
        assertEquals(8.0, state.roundDamageBonus(), 0.0001);
        assertEquals(2.5, state.permanentHealthBonus(), 0.0001);
        assertEquals(1.0, state.permanentDamageBonus(), 0.0001);
        assertEquals(8.0, state.roundIntervalReduction(), 0.0001);
        assertEquals(1, state.totalSacrificeCount());
        assertEquals(1, state.roundSacrificeCount());

        state.resetRound();

        assertEquals(0.0, state.roundHealthBonus(), 0.0001);
        assertEquals(0.0, state.roundDamageBonus(), 0.0001);
        assertEquals(0.0, state.roundIntervalReduction(), 0.0001);
        assertEquals(0, state.roundSacrificeCount());
        assertEquals(1, state.totalSacrificeCount());
        assertEquals(2.5, state.permanentHealthBonus(), 0.0001);
        assertEquals(1.0, state.permanentDamageBonus(), 0.0001);
    }

    @Test
    void attackIntervalAbsorptionHonorsCapAndOnlyFasterTargets() {
        WarlockState state = new WarlockState();

        state.absorbAttackInterval(20, 10, 15.0);
        state.absorbAttackInterval(20, 12, 15.0);
        state.absorbAttackInterval(20, 20, 15.0);

        assertEquals(15.0, state.roundIntervalReduction(), 0.0001);
    }

    @Test
    void copiedStateDoesNotShareFutureMutations() {
        WarlockState source = new WarlockState();
        source.absorbForRound(100.0, 20.0, 0.60);
        source.absorbPermanently(100.0, 20.0, 0.05, 0.025);

        WarlockState copy = new WarlockState();
        copy.copyFrom(source);
        source.resetRound();

        assertEquals(60.0, copy.roundHealthBonus(), 0.0001);
        assertEquals(12.0, copy.roundDamageBonus(), 0.0001);
        assertEquals(5.0, copy.permanentHealthBonus(), 0.0001);
        assertEquals(0.5, copy.permanentDamageBonus(), 0.0001);
        assertEquals(1, copy.totalSacrificeCount());
        assertEquals(1, copy.roundSacrificeCount());
    }

    @Test
    void awakeningCanOccurOncePerRoundAndResetsWithRoundState() {
        WarlockState state = new WarlockState();

        assertTrue(state.awaken());
        assertFalse(state.awaken());
        assertTrue(state.awakenedThisRound());

        state.resetRound();

        assertFalse(state.awakenedThisRound());
        assertTrue(state.awaken());
    }

    @Test
    void awakeningRequiresUnlockLowHealthAndLastSurvivorTogether() {
        assertTrue(WarlockTower.meetsAwakeningConditions(true, 0.40, 0.40, true));
        assertFalse(WarlockTower.meetsAwakeningConditions(false, 0.40, 0.40, true));
        assertFalse(WarlockTower.meetsAwakeningConditions(true, 0.41, 0.40, true));
        assertFalse(WarlockTower.meetsAwakeningConditions(true, 0.40, 0.40, false));
        assertFalse(WarlockTower.meetsAwakeningConditions(true, 0.0, 0.40, true));
        assertFalse(WarlockTower.meetsAwakeningConditions(true, Double.NaN, 0.40, true));
    }
}
