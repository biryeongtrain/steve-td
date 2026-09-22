package kim.biryeong.semiontd.tower.illager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IllagerRaidStateTest {
    @Test
    void grandRaidAccumulatesOnlyAfterActivationConsumesFiftiesAndResets() {
        IllagerRaidState state = new IllagerRaidState();
        state.resetForRound(3);
        state.enableGrandRaid(50);
        state.addExtraGauge(100);
        assertEquals(0, state.consumePendingVolleys());
        state.addGauge(100, 100);
        state.addExtraGauge(49);
        assertEquals(0, state.consumePendingVolleys());
        state.addExtraGauge(102);
        assertEquals(3, state.consumePendingVolleys());
        assertEquals(1, state.extraGauge());
        assertEquals(0, state.consumePendingVolleys());
        assertEquals(100, state.gauge());
        state.addExtraGauge(50);
        state.resetForRound(1);
        assertEquals(0, state.extraGauge());
        assertEquals(0, state.consumePendingVolleys());
        assertEquals(0, state.grandRaidThreshold());
    }

    @Test
    void ordinaryRaidsNeverAcquireGrandRaidCharges() {
        IllagerRaidState state = new IllagerRaidState();
        state.resetForRound(3);
        state.addGauge(100, 100);
        state.addExtraGauge(500);
        assertEquals(0, state.extraGauge());
        assertEquals(0, state.consumePendingVolleys());
    }
    @Test
    void startsEachRoundEmptyAndSnapshotsTowerCount() {
        IllagerRaidState state = new IllagerRaidState();

        state.resetForRound(4);

        assertEquals(0, state.gauge());
        assertFalse(state.active());
        assertEquals(4, state.roundStartTowerCount());
        assertFalse(state.pendingActivationEffects());
    }

    @Test
    void activatesAtGaugeMaximumAndQueuesEffectsOnce() {
        IllagerRaidState state = new IllagerRaidState();
        state.resetForRound(3);

        assertFalse(state.addGauge(40, 100));
        assertEquals(40, state.gauge());
        assertTrue(state.addGauge(60, 100));
        assertTrue(state.active());
        assertTrue(state.pendingActivationEffects());
        assertTrue(state.consumePendingActivationEffects());
        assertFalse(state.consumePendingActivationEffects());
    }

    @Test
    void activeStateStopsAdditionalGaugeGainUntilRoundReset() {
        IllagerRaidState state = new IllagerRaidState();
        state.resetForRound(2);

        state.addGauge(100, 100);
        state.addGauge(50, 100);

        assertEquals(100, state.gauge());
        state.resetForRound(5);
        assertEquals(0, state.gauge());
        assertFalse(state.active());
        assertEquals(5, state.roundStartTowerCount());
    }

    @Test
    void bossBarShowsGaugeProgressAndActiveState() {
        IllagerRaidState state = new IllagerRaidState();
        state.resetForRound(3);
        state.addGauge(25, 100);

        assertEquals(0.25F, IllagerRaidBossBarService.progress(state, 100));
        assertEquals("습격 게이지 - 25/100", IllagerRaidBossBarService.title(state, 100).getString());

        state.addGauge(75, 100);

        assertEquals(1.0F, IllagerRaidBossBarService.progress(state, 100));
        assertEquals("습격 게이지 - 발동 중", IllagerRaidBossBarService.title(state, 100).getString());
    }
}
