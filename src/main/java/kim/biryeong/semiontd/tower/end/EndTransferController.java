package kim.biryeong.semiontd.tower.end;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.LogarithmicScaling;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.resources.ResourceLocation;

import static kim.biryeong.semiontd.tower.end.EndConfig.Ability.*;

final class EndTransferController {
    private static final TowerDataKey<Double> PROGRESS = TowerDataKey.of(
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "end_transfer_progress"),
            Double.class
    );

    private final EndTransferState state = new EndTransferState();
    private final EndConfig config;
    private int shulkerCount;
    private int endCrystalCount;
    private int roundCompletedCount;

    EndTransferController(EndConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    TickResult tick(
            EndTower core,
            PlayerLane lane,
            BiConsumer<PlayerLane, Tower> particleEmitter
    ) {
        if (lane == null) {
            return TickResult.NONE;
        }
        captureTargets(core, lane);
        return advanceTransfers(lane, particleEmitter);
    }

    private void captureTargets(EndTower core, PlayerLane lane) {
        state.beginSnapshot();
        for (Tower tower : lane.towers()) {
            state.markPresent(tower);
            if (isEligibleTarget(core, tower)) {
                state.ensureProgress(tower, this::newProgress);
            }
        }
    }

    private TickResult advanceTransfers(
            PlayerLane lane,
            BiConsumer<PlayerLane, Tower> particleEmitter
    ) {
        double completionHealing = 0.0;
        double periodicHealingPerSecond = 0.0;
        boolean statsChanged = false;
        boolean countsChanged = false;
        List<Completion> completions = null;
        var iterator = state.progressEntries().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Tower, EndTransferState.Progress> entry = iterator.next();
            Tower source = entry.getKey();
            EndTransferState.Progress progress = entry.getValue();
            if (isInterrupted(source)) {
                iterator.remove();
                source.removeData(PROGRESS);
                statsChanged |= state.rollback(progress);
                continue;
            }

            progress.elapsedTicks++;
            statsChanged |= state.apply(progress);
            source.setData(PROGRESS, progress.appliedRatio);
            if (progress.elapsedTicks < progress.durationTicks) {
                periodicHealingPerSecond += progress.periodicHealingPerSecond;
                if (shouldEmitParticles(source, progress.elapsedTicks)) {
                    particleEmitter.accept(lane, source);
                }
                continue;
            }

            iterator.remove();
            source.removeData(PROGRESS);
            if (isInterrupted(source)) {
                statsChanged |= state.rollback(progress);
                continue;
            }
            if (completions == null) {completions = new ArrayList<>();}
            completions.add(new Completion(source, progress));
        }
        if (completions != null) {
            List<Tower> completionSources = new ArrayList<>(completions.size());
            for (Completion completion : completions) {completionSources.add(completion.source());}
            Set<Tower> killed = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            killed.addAll(lane.killTowers(completionSources));
            for (Completion completion : completions) {
                if (!killed.contains(completion.source())) {
                    statsChanged |= state.rollback(completion.progress());
                    continue;
                }
                completionHealing += completion.progress().completionHealing;
                registerCompleted(completion.source().type());
                countsChanged = true;
            }
        }
        return new TickResult(
                statsChanged,
                countsChanged,
                completionHealing,
                periodicHealingPerSecond
        );
    }

    private boolean isInterrupted(Tower source) {
        return !state.isPresent(source) || source.health() <= 0.0;
    }

    private boolean isEligibleTarget(EndTower core, Tower tower) {
        return tower != null
                && tower != core
                && Objects.equals(tower.ownerPlayer(), core.ownerPlayer())
                && tower.health() > 0.0
                && EndTowers.isTransferableTower(tower.type());
    }

    private EndTransferState.Progress newProgress(Tower tower) {
        int durationTicks = Math.max(1, config.transferTicks());
        boolean shulkerLine = EndTowers.isShulkerLine(tower.type());
        boolean endCrystalLine = EndTowers.isEndCrystalLine(tower.type());
        double maxHealth = tower.type().maxHealth();
        double damage = tower.type().damage();
        tower.setData(PROGRESS, 0.0);
        return new EndTransferState.Progress(
                durationTicks,
                shulkerLine ? maxHealth * nonNegative(config.value(ROUND_HEALTH_RATIO)) : 0.0,
                shulkerLine ? maxHealth * nonNegative(config.value(PERMANENT_HEALTH_RATIO)) : 0.0,
                endCrystalLine ? damage * nonNegative(config.value(ROUND_DAMAGE_RATIO)) : 0.0,
                endCrystalLine ? damage * nonNegative(config.value(PERMANENT_DAMAGE_RATIO)) : 0.0,
                nonNegative(config.value(TRANSFER_HEAL)),
                shulkerLine ? maxHealth * nonNegative(config.value(TRANSFER_HEAL_RATIO)) : 0.0
        );
    }

    private void registerCompleted(TowerType sourceType) {
        int tier = EndTowers.transferTier(sourceType);
        roundCompletedCount = saturatedAdd(roundCompletedCount, 1);
        if (EndTowers.isShulkerLine(sourceType)) {
            shulkerCount = saturatedAdd(shulkerCount, tier);
        } else {
            endCrystalCount = saturatedAdd(endCrystalCount, tier);
        }
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
        roundCompletedCount = 0;
        state.resetRoundContributions();
    }

    void copyFrom(EndTransferController source) {
        state.copyBonusesFrom(source.state);
        shulkerCount = source.shulkerCount;
        endCrystalCount = source.endCrystalCount;
        roundCompletedCount = source.roundCompletedCount;
    }

    int shulkerCount() {
        return shulkerCount;
    }

    int endCrystalCount() {
        return endCrystalCount;
    }

    int roundCompletedCount() {
        return roundCompletedCount;
    }

    double permanentHealthBonus() {
        return scaleHealthBonus(state.permanentHealthBonus());
    }

    double permanentDamageBonus() {
        return scaleDamageBonus(state.permanentDamageBonus());
    }

    double roundHealthBonus() {
        double permanent = state.permanentHealthBonus();
        double total = permanent + state.roundHealthContribution();
        return Math.max(0.0, scaleHealthBonus(total) - scaleHealthBonus(permanent));
    }

    double roundDamageBonus() {
        double permanent = state.permanentDamageBonus();
        double total = permanent + state.roundDamageContribution();
        return Math.max(0.0, scaleDamageBonus(total) - scaleDamageBonus(permanent));
    }

    private double scaleDamageBonus(double raw) {return LogarithmicScaling.logarithmicBonus(raw, config.value(DAMAGE_THRESHOLD), config.value(DAMAGE_SCALE));}

    private double scaleHealthBonus(double raw) {return LogarithmicScaling.logarithmicBonus(raw, config.value(HEALTH_THRESHOLD), config.value(HEALTH_SCALE));}

    static double progress(Tower tower) {
        return Math.clamp(tower.getDataOrDefault(PROGRESS, 0.0), 0.0, 1.0);
    }

    static void clearProgress(Tower tower) {
        tower.removeData(PROGRESS);
    }

    private static double nonNegative(double value) {
        return Math.max(0.0, value);
    }

    private static boolean shouldEmitParticles(Tower source, int elapsedTicks) {
        return Math.floorMod(elapsedTicks + System.identityHashCode(source), 5) == 0;
    }

    private static int saturatedAdd(int value, int increment) {
        if (increment <= 0 || value == Integer.MAX_VALUE) {
            return value;
        }
        return value > Integer.MAX_VALUE - increment ? Integer.MAX_VALUE : value + increment;
    }

    record TickResult(
            boolean statsChanged,
            boolean countsChanged,
            double completionHealing,
            double periodicHealingPerSecond
    ) {
        private static final TickResult NONE = new TickResult(false, false, 0.0, 0.0);
    }

    private record Completion(Tower source, EndTransferState.Progress progress) {
    }
}
