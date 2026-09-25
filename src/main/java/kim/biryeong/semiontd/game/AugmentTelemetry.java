package kim.biryeong.semiontd.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/** Server-thread-only participant observations; storage remains in the existing match result path. */
public final class AugmentTelemetry {
    private final ArrayList<AugmentTelemetrySnapshot.GuiEvent> guiEvents = new ArrayList<>();
    private final ArrayList<AugmentTelemetrySnapshot.EconomyEvent> economyEvents = new ArrayList<>();
    private final ArrayList<AugmentTelemetrySnapshot.CombatRoundSample> combatRounds = new ArrayList<>();
    private final ArrayList<AugmentTelemetrySnapshot.TowerSample> towerSamples = new ArrayList<>();
    private final ArrayList<AugmentTelemetrySnapshot.LaneRoundSample> laneRounds = new ArrayList<>();
    private final Map<UUID, Integer> towerReferences = new HashMap<>();
    private LongSupplier tickSource;
    private IntSupplier roundSource;

    public void bindClock(LongSupplier tickSource, IntSupplier roundSource) {
        this.tickSource = Objects.requireNonNull(tickSource, "tickSource");
        this.roundSource = Objects.requireNonNull(roundSource, "roundSource");
    }

    public Long currentTick() {
        return tickSource == null ? null : tickSource.getAsLong();
    }

    public Integer currentRound() {
        return roundSource == null ? null : roundSource.getAsInt();
    }

    public int towerRef(UUID logicalTowerId) {
        Objects.requireNonNull(logicalTowerId, "logicalTowerId");
        return towerReferences.computeIfAbsent(logicalTowerId, ignored -> towerReferences.size() + 1);
    }

    public void recordGui(AugmentTelemetrySnapshot.GuiEvent event) {
        guiEvents.add(Objects.requireNonNull(event, "event"));
    }

    public void recordEconomy(AugmentTelemetrySnapshot.EconomyEvent event) {
        economyEvents.add(Objects.requireNonNull(event, "event"));
    }

    public void recordCombat(AugmentTelemetrySnapshot.CombatRoundSample sample) {
        combatRounds.add(Objects.requireNonNull(sample, "sample"));
    }

    public void recordTower(AugmentTelemetrySnapshot.TowerSample sample) {
        towerSamples.add(Objects.requireNonNull(sample, "sample"));
    }

    public void recordLane(AugmentTelemetrySnapshot.LaneRoundSample sample) {
        laneRounds.add(Objects.requireNonNull(sample, "sample"));
    }

    public AugmentTelemetrySnapshot snapshot() {
        return new AugmentTelemetrySnapshot(guiEvents, economyEvents, combatRounds, towerSamples, laneRounds);
    }

    public void clear() {
        guiEvents.clear();
        economyEvents.clear();
        combatRounds.clear();
        towerSamples.clear();
        laneRounds.clear();
        towerReferences.clear();
        tickSource = null;
        roundSource = null;
    }
}
