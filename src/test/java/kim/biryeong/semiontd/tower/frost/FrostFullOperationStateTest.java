package kim.biryeong.semiontd.tower.frost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kim.biryeong.semiontd.tower.frost.FrostFullOperationService.TriggerFamily;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

final class FrostFullOperationStateTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void familyTickDeduplicationAndWaveResetStayIndependent() {
        FrostFullOperationState state = new FrostFullOperationState();
        assertFalse(state.record(TriggerFamily.DONGTAE, 10));
        state.beginWave();
        assertTrue(state.record(TriggerFamily.DONGTAE, 10));
        assertFalse(state.record(TriggerFamily.DONGTAE, 10));
        assertTrue(state.record(TriggerFamily.ICEBOX, 10));
        assertEquals(2, state.totalActivations());
        assertEquals(1, state.familyActivations(TriggerFamily.DONGTAE));
        state.endWave();
        assertFalse(state.record(TriggerFamily.DONGTAE, 11));
        state.beginWave();
        assertEquals(0, state.totalActivations());
        assertTrue(state.record(TriggerFamily.DONGTAE, 10));
    }

    @Test
    void activationExpiryAndRoundResetDoNotGrantAnotherActivation() {
        FrostFullOperationState state = new FrostFullOperationState();
        state.beginWave();
        state.markReady();
        state.activate(100);
        assertTrue(state.active());
        assertTrue(state.usedThisWave());
        assertFalse(state.ready());
        assertEquals(100, state.nextChillPulseTick());
        state.scheduleChillPulse(120);
        state.expire();
        assertFalse(state.active());
        assertTrue(state.usedThisWave());
        state.beginWave();
        assertFalse(state.usedThisWave());
        assertEquals(0, state.nextChillPulseTick());
        assertEquals(0, state.activeUntilTick());
    }
}
