package kim.biryeong.semiontd.tower.illager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IllagerRaidStateTest {
    @Test
    void startsEachRoundEmptyAndSnapshotsTowerCount() {
        IllagerRaidState state = new IllagerRaidState();

        state.resetForRound(4);

        assertEquals(0, state.gauge());
        assertFalse(state.active());
        assertEquals(4, state.roundStartTowerCount());
        assertFalse(state.pendingActivationSound());
    }

    @Test
    void activatesAtGaugeMaximumAndQueuesSoundOnce() {
        IllagerRaidState state = new IllagerRaidState();
        state.resetForRound(3);

        assertFalse(state.addGauge(40, 100));
        assertEquals(40, state.gauge());
        assertTrue(state.addGauge(60, 100));
        assertTrue(state.active());
        assertTrue(state.pendingActivationSound());
        assertTrue(state.consumePendingActivationSound());
        assertFalse(state.consumePendingActivationSound());
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
