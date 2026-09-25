package kim.biryeong.semiontd.tower.end;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import net.minecraft.resources.ResourceLocation;

final class EndTransferController {
    private static final int HEALING_INTERVAL_TICKS = 20;
    private static final TowerDataKey<Double> PROGRESS = TowerDataKey.of(ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "end_transfer_progress"), Double.class);

    private final EndTransferState state = new EndTransferState();
    private final EndTransferFactory progressFactory;
    private EndTransferStacks stacks = EndTransferStacks.EMPTY;

    EndTransferController(EndConfig config) {
        progressFactory = new EndTransferFactory(Objects.requireNonNull(config, "config"));
    }

    TickResult tick(EndTower core, PlayerLane lane) {
        if (lane == null) {
            return TickResult.NONE;
        }
        captureTargets(core, lane);
        return advanceTransfers(core, lane);
    }

    private void captureTargets(EndTower core, PlayerLane lane) {
        state.beginSnapshot();
        for (Tower tower : lane.towers()) {
            state.markPresent(tower);
            if (isEligibleTarget(core, tower)) {
                state.ensureProgress(tower, source -> progressFactory.create(source, core.transferDurationMultiplier()));
            }
        }
    }

    private TickResult advanceTransfers(EndTower core, PlayerLane lane) {
        TransferTick tick = new TransferTick();
        advanceActiveTransfers(tick);
        resolveCompletions(core, lane, tick);
        return tick.result();
    }

    private void advanceActiveTransfers(TransferTick tick) {
        var iterator = state.progressEntries().iterator();
        while (iterator.hasNext()) {
            advanceTransfer(iterator, tick);
        }
    }

    private void advanceTransfer(
            Iterator<Map.Entry<Tower, EndTransferState.Progress>> iterator,
            TransferTick tick
    ) {
        Map.Entry<Tower, EndTransferState.Progress> entry = iterator.next();
        Tower source = entry.getKey();
        EndTransferState.Progress progress = entry.getValue();
        if (isInterrupted(source)) {
            interruptTransfer(iterator, source, progress, tick);
            return;
        }

        advanceProgress(source, progress, tick);
        if (progress.isComplete()) {
            collectCompletion(iterator, source, progress, tick);
        } else {
            recordActiveTransfer(source, progress, tick);
        }
    }

    private void advanceProgress(
            Tower source,
            EndTransferState.Progress progress,
            TransferTick tick
    ) {
        progress.advance();
        tick.markStatsChanged(state.apply(progress));
        source.setData(PROGRESS, progress.appliedRatio);
    }

    private static void recordActiveTransfer(
            Tower source,
            EndTransferState.Progress progress,
            TransferTick tick
    ) {
        if (progress.elapsedTicks % HEALING_INTERVAL_TICKS == 0) {
            tick.addTransferHealing(progress.periodicHealingPerSecond);
        }
        if (shouldEmitParticles(source, progress.elapsedTicks)) {
            tick.addParticleSource(source);
        }
    }

    private void interruptTransfer(
            Iterator<Map.Entry<Tower, EndTransferState.Progress>> iterator,
            Tower source,
            EndTransferState.Progress progress,
            TransferTick tick
    ) {
        removeTransfer(iterator, source);
        rollback(progress, tick);
    }

    private void collectCompletion(
            Iterator<Map.Entry<Tower, EndTransferState.Progress>> iterator,
            Tower source,
            EndTransferState.Progress progress,
            TransferTick tick
    ) {
        removeTransfer(iterator, source);
        if (isInterrupted(source)) {
            rollback(progress, tick);
        } else {
            tick.collect(new Completion(source, progress));
        }
    }

    private static void removeTransfer(
            Iterator<Map.Entry<Tower, EndTransferState.Progress>> iterator,
            Tower source
    ) {
        iterator.remove();
        source.removeData(PROGRESS);
    }

    private void rollback(EndTransferState.Progress progress, TransferTick tick) {
        tick.markStatsChanged(state.rollback(progress));
    }

    private void resolveCompletions(EndTower core, PlayerLane lane, TransferTick tick) {
        if (tick.completions().isEmpty()) {
            return;
        }
        Set<Tower> killed = Collections.newSetFromMap(new IdentityHashMap<>());
        killed.addAll(lane.killTowers(tick.completionSources()));
        for (Completion completion : tick.completions()) {
            if (killed.contains(completion.source())) {
                registerCompletion(completion, tick);
                core.onTransferCompleted(lane, completion.source(), completion.progress().sourceMaxHealth);
            } else {
                rollback(completion.progress(), tick);
            }
        }
    }

    private void registerCompletion(Completion completion, TransferTick tick) {
        tick.addCompletionHealing(completion.progress().completionHealing);
        stacks = stacks.recordCompletion(completion.source().type());
        tick.markCountsChanged();
    }

    private boolean isInterrupted(Tower source) {
        return !state.isPresent(source) || source.health() <= 0.0;
    }

    private boolean isEligibleTarget(EndTower core, Tower tower) {
        return tower != null
                && tower != core
                && !tower.isTemporaryCopy()
                && Objects.equals(tower.ownerPlayer(), core.ownerPlayer())
                && tower.health() > 0.0
                && EndTowers.isTransferableTower(tower.type());
    }

    boolean rollbackIncomplete() {
        boolean changed = false;
        for (Map.Entry<Tower, EndTransferState.Progress> entry : state.progressEntries()) {
            entry.getKey().removeData(PROGRESS);
            changed |= state.rollback(entry.getValue());
        }
        state.clearProgress();
        return changed;
    }

    void resetRound() {
        stacks = stacks.resetRound();
        state.resetRoundContributions();
    }

    void copyCommittedFrom(EndTransferController source) {
        state.restore(source.state.committedSnapshot(source.stacks));
        stacks = source.stacks;
    }

    EndTransferSnapshot progressionSnapshot() {
        return state.snapshot(stacks);
    }

    static double progress(Tower tower) {
        return Math.clamp(tower.getDataOrDefault(PROGRESS, 0.0), 0.0, 1.0);
    }

    static void clearProgress(Tower tower) {
        tower.removeData(PROGRESS);
    }

    static boolean shouldEmitParticles(Tower source, int elapsedTicks) {
        int stableOffset = source.ownerPlayer().hashCode();
        stableOffset = 31 * stableOffset + source.type().id().hashCode();
        stableOffset = 31 * stableOffset + source.originalPosition().hashCode();
        return Math.floorMod(elapsedTicks + stableOffset, 5) == 0;
    }

    record TickResult(
            boolean statsChanged,
            boolean countsChanged,
            double completionHealing,
            double transferHealing,
            List<Tower> particleSources
    ) {
        private static final TickResult NONE = new TickResult(false, false, 0.0, 0.0, List.of());

        TickResult {
            particleSources = List.copyOf(particleSources);
        }
    }

    private record Completion(Tower source, EndTransferState.Progress progress) {
    }

    private static final class TransferTick {
        private final List<Completion> completions = new ArrayList<>();
        private boolean statsChanged;
        private boolean countsChanged;
        private double completionHealing;
        private double transferHealing;
        private final List<Tower> particleSources = new ArrayList<>();

        private void markStatsChanged(boolean changed) {
            statsChanged |= changed;
        }

        private void addTransferHealing(double healing) {
            transferHealing += healing;
        }

        private void collect(Completion completion) {
            completions.add(completion);
        }

        private List<Completion> completions() {
            return completions;
        }

        private List<Tower> completionSources() {
            return completions.stream().map(Completion::source).toList();
        }

        private void addCompletionHealing(double healing) {
            completionHealing += healing;
        }

        private void addParticleSource(Tower source) {
            particleSources.add(source);
        }

        private void markCountsChanged() {
            countsChanged = true;
        }

        private TickResult result() {
            return new TickResult(
                    statsChanged,
                    countsChanged,
                    completionHealing,
                    transferHealing,
                    particleSources
            );
        }
    }
}
