package kim.biryeong.semiontd.entity.monster;

import static org.junit.jupiter.api.Assertions.*;

import kim.biryeong.semiontd.config.WaveHealingConfig;
import org.junit.jupiter.api.Test;

final class WaveHealingStateTest {
    private static final WaveHealingConfig CONFIG = new WaveHealingConfig(6, 80, 3, 160, 20, 2);

    @Test
    void failuresDoNotConsumeCastsAndIncapacitationDoesNotFreezeCooldown() {
        WaveHealingState state = new WaveHealingState();
        assertEquals(120, CONFIG.minimumInjury());
        assertTrue(state.tick(CONFIG, false));
        state.recordAttempt(CONFIG, 1, 119, 0, 0, WaveHealingState.Failure.INSUFFICIENT_INJURY);
        for (int i = 0; i < 20; i++) {assertFalse(state.tick(CONFIG, true));}
        assertEquals(0, state.remainingCooldownTicks());
        assertFalse(state.tick(CONFIG, true));
        assertTrue(state.tick(CONFIG, false));
        state.recordAttempt(CONFIG, 3, 120, 240, 120, WaveHealingState.Failure.NONE);
        for (int i = 0; i < 160; i++) {assertFalse(state.tick(CONFIG, true));}
        assertTrue(state.tick(CONFIG, false));
        state.recordAttempt(CONFIG, 3, 160, 240, 160, WaveHealingState.Failure.NONE);
        for (int i = 0; i < 1000; i++) {assertFalse(state.tick(CONFIG, false));}
        assertEquals(2, state.successfulCasts());
        assertEquals(1, state.snapshot().insufficientInjury());
        assertEquals(280, state.snapshot().effectiveHealing());
        assertEquals(200, state.snapshot().overhealing());
    }

    @Test
    void zeroEffectiveCastRetriesAndSnapshotsAggregate() {
        WaveHealingState state = new WaveHealingState();
        state.recordAttempt(CONFIG, 0, 0, 0, 0, WaveHealingState.Failure.NO_ELIGIBLE_TARGET);
        state.recordAttempt(CONFIG, 3, 240, 240, 0, WaveHealingState.Failure.ZERO_EFFECTIVE_HEALING);
        assertEquals(0, state.successfulCasts());
        assertEquals(20, state.remainingCooldownTicks());
        assertEquals(1, state.snapshot().noEligibleTarget());
        assertEquals(1, state.snapshot().zeroEffectiveHealing());
        assertEquals(state.snapshot(), WaveHealingState.Snapshot.empty().plus(state.snapshot()));
    }
}
