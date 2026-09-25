package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class AugmentTelemetryTest {
    @Test
    void snapshotsRemainImmutableAndDoNotExportLogicalUuids() {
        AugmentTelemetry telemetry = new AugmentTelemetry();
        UUID towerId = UUID.randomUUID();
        int towerRef = telemetry.towerRef(towerId);
        assertEquals(towerRef, telemetry.towerRef(towerId));
        assertEquals(towerRef + 1, telemetry.towerRef(UUID.randomUUID()));
        ArrayList<Integer> targets = new ArrayList<>(List.of(towerRef));
        var event = new AugmentTelemetrySnapshot.GuiEvent(5, 100, "CONFIRMED", 2,
                0, "tactical_designation_1", "TARGET", "APPLIED", null, "ASSAULT", targets, 40L, 3, 1);
        telemetry.recordGui(event);
        AugmentTelemetrySnapshot snapshot = telemetry.snapshot();
        targets.clear();
        telemetry.recordGui(event);
        assertEquals(List.of(towerRef), snapshot.guiEvents().getFirst().targetRefs());
        assertEquals(1, snapshot.guiEvents().size());
        assertEquals(2, telemetry.snapshot().guiEvents().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.guiEvents().clear());
        assertThrows(UnsupportedOperationException.class, () -> event.targetRefs().clear());
        assertFalse(new Gson().toJson(snapshot).contains(towerId.toString()));
    }

    @Test
    void clockIsActualOrUnmeasuredAndClearReleasesPreviousMatch() {
        AugmentTelemetry telemetry = new AugmentTelemetry();
        assertNull(telemetry.currentTick());
        assertNull(telemetry.currentRound());
        AtomicLong tick = new AtomicLong(120);
        AtomicInteger round = new AtomicInteger(5);
        telemetry.bindClock(tick::get, round::get);
        assertEquals(120L, telemetry.currentTick());
        assertEquals(5, telemetry.currentRound());
        tick.set(150);
        round.set(6);
        assertEquals(150L, telemetry.currentTick());
        assertEquals(6, telemetry.currentRound());
        telemetry.recordLane(new AugmentTelemetrySnapshot.LaneRoundSample(5, true, 12.5, 1));
        AugmentTelemetrySnapshot saved = telemetry.snapshot();
        telemetry.clear();
        assertEquals(1, saved.laneRounds().size());
        assertEquals(List.of(), telemetry.snapshot().laneRounds());
        assertEquals(1, telemetry.towerRef(UUID.randomUUID()));
        assertNull(telemetry.currentTick());
        assertNull(telemetry.currentRound());
    }

    @Test
    void typedPayloadRoundTripsActualValuesWithoutInventingMissingMeasurements() {
        AugmentTelemetry telemetry = new AugmentTelemetry();
        telemetry.recordEconomy(new AugmentTelemetrySnapshot.EconomyEvent(15, null,
                "forecast_offensive", "FORECAST_RESERVED", null, 100L, 110L, null,
                null, null, null, null, 1, List.of("forecast_offensive"), 12L));
        telemetry.recordCombat(new AugmentTelemetrySnapshot.CombatRoundSample(16, 1, "semion-td:villager",
                "END", new AugmentTelemetrySnapshot.CombatState(100.0, 45.0, null, 2, true,
                "QUALIFIED", null, false, 0, 0.0, "PHYSICAL", 45.0, 0.0, 10.0, 0.0,
                null, null, null, 40.0)));
        telemetry.recordTower(new AugmentTelemetrySnapshot.TowerSample(16, 320, 2,
                "semion-td:augment_barrier_core", "ROUND_END", 1,
                35.0, null, null, null, null, null, null, null));
        telemetry.recordLane(new AugmentTelemetrySnapshot.LaneRoundSample(16, false, 0.0, 0));
        Gson gson = new Gson();
        AugmentTelemetrySnapshot restored = gson.fromJson(gson.toJson(telemetry.snapshot()),
                AugmentTelemetrySnapshot.class);
        assertEquals(telemetry.snapshot(), restored);
        assertNull(restored.economyEvents().getFirst().tick());
        assertNull(restored.economyEvents().getFirst().incomeGranted());
        assertEquals(12L, restored.economyEvents().getFirst().incomeDeferred());
        assertNull(restored.towerSamples().getFirst().ordnanceShellsFired());
        assertEquals(0, restored.laneRounds().getFirst().leakedCount());
        var partial = gson.fromJson("{\"guiEvents\":[]}", AugmentTelemetrySnapshot.class);
        assertEquals(List.of(), partial.guiEvents());
        assertNull(partial.economyEvents());
    }

    @Test
    void jointEconomyModifiersRetainOneActualDeltaAndImmutableCardList() {
        ArrayList<String> cards = new ArrayList<>(List.of("advanced_blueprint", "wartime_economy"));
        var event = new AugmentTelemetrySnapshot.EconomyEvent(16, 400L, null, "PERIODIC_PAYOUT",
                null, null, null, null, 60L, null, null, 40L, 1, cards);
        cards.clear();
        assertEquals(List.of("advanced_blueprint", "wartime_economy"), event.relatedAugmentIds());
        assertEquals(40L, event.payoutWithheld());
        assertNull(event.augmentId());
        assertNull(event.incomeDeferred());
        assertThrows(UnsupportedOperationException.class, () -> event.relatedAugmentIds().clear());
    }
}
