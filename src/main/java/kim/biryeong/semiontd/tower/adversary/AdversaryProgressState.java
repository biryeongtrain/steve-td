package kim.biryeong.semiontd.tower.adversary;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AdversaryProgressState {
    private static final List<FoxRoute> ROUTE_ORDER = List.of(
            FoxRoute.RAPID,
            FoxRoute.TEAM_CONTROL,
            FoxRoute.TARGET_SPECIALIST,
            FoxRoute.HIGH_CEILING
    );

    private final Map<UUID, RivalContribution> contributionByRival = new LinkedHashMap<>();
    private final EnumMap<RivalKind, Integer> scores = emptyScores();

    private FoxForm currentForm = FoxForm.BASE;
    private FoxRoute lockedRoute;
    private FoxForm lockedFinalForm;
    private FoxForm pendingForm;
    private boolean intermediateWaveCompleted;
    private RivalKind lastScoringKind;
    private boolean explicitScoringKindPending;

    public synchronized FoxForm currentForm() {
        return currentForm;
    }

    public synchronized void setCurrentForm(FoxForm form) {
        FoxForm next = Objects.requireNonNull(form, "form");
        currentForm = next;
        pendingForm = null;
        next.route().ifPresent(route -> lockedRoute = route);
        if (next.isFinal()) {
            lockedFinalForm = next;
        }
    }

    public synchronized Optional<FoxForm> pendingForm() {
        return Optional.ofNullable(pendingForm);
    }

    public synchronized Optional<FoxRoute> lockedRoute() {
        return Optional.ofNullable(lockedRoute);
    }

    public synchronized Optional<FoxForm> lockedFinalForm() {
        return Optional.ofNullable(lockedFinalForm);
    }

    public synchronized Optional<RivalKind> lastScoringKind() {
        return Optional.ofNullable(lastScoringKind);
    }

    public synchronized boolean intermediateWaveCompleted() {
        return intermediateWaveCompleted;
    }

    public synchronized int score(RivalKind kind) {
        return kind == null ? 0 : scores.getOrDefault(kind, 0);
    }

    public synchronized Map<RivalKind, Integer> scores() {
        return Collections.unmodifiableMap(new EnumMap<>(scores));
    }

    public synchronized int contribution(UUID rivalId) {
        RivalContribution contribution = contributionByRival.get(rivalId);
        return contribution == null ? 0 : contribution.score();
    }

    public synchronized Optional<RivalContribution> rivalContribution(UUID rivalId) {
        return Optional.ofNullable(contributionByRival.get(rivalId));
    }

    public synchronized Collection<RivalContribution> rivalContributions() {
        return List.copyOf(contributionByRival.values());
    }

    public synchronized void noteScoringKind(RivalKind kind) {
        if (kind != null) {
            lastScoringKind = kind;
            explicitScoringKindPending = true;
        }
    }

    synchronized void registerRival(UUID rivalId, RivalKind kind) {
        if (rivalId == null || kind == null || contributionByRival.containsKey(rivalId)) {
            return;
        }
        contributionByRival.put(rivalId, new RivalContribution(rivalId, kind, 0));
    }

    /**
     * Adds one kill to an instance ledger. Runtime towers normally persist the
     * score themselves and call {@link #reconcileRivals(Collection)}; this method
     * is also available for direct integrations and tests.
     */
    synchronized int recordRivalKill(UUID rivalId, RivalKind kind, boolean enhanced) {
        if (rivalId == null || kind == null) {
            return score(kind);
        }
        RivalContribution previous = contributionByRival.get(rivalId);
        int previousScore = previous != null && previous.kind() == kind ? previous.score() : 0;
        RivalContribution next = new RivalContribution(
                rivalId,
                kind,
                previousScore + kind.scorePerKill(enhanced)
        );
        contributionByRival.put(rivalId, next);
        rebuildScores();
        lastScoringKind = kind;
        queueAvailableEvolution();
        return score(kind);
    }

    public synchronized int removeRival(UUID rivalId) {
        RivalContribution removed = contributionByRival.remove(rivalId);
        if (removed == null) {
            return 0;
        }
        rebuildScores();
        reconcileDemotion();
        validatePendingForm();
        return removed.score();
    }

    public synchronized void transferRival(UUID previousRivalId, UUID nextRivalId) {
        if (previousRivalId == null || nextRivalId == null || previousRivalId.equals(nextRivalId)) {
            return;
        }
        RivalContribution contribution = contributionByRival.remove(previousRivalId);
        if (contribution != null) {
            contributionByRival.put(
                    nextRivalId,
                    new RivalContribution(nextRivalId, contribution.kind(), contribution.score())
            );
        }
    }

    /** Replaces the ledger with the currently installed rival tower snapshots. */
    public synchronized void reconcileRivals(Collection<RivalContribution> snapshots) {
        EnumMap<RivalKind, Integer> previousScores = new EnumMap<>(scores);
        LinkedHashMap<UUID, RivalContribution> next = new LinkedHashMap<>();
        if (snapshots != null) {
            for (RivalContribution snapshot : snapshots) {
                if (snapshot != null) {
                    next.put(snapshot.rivalId(), snapshot);
                }
            }
        }

        contributionByRival.clear();
        contributionByRival.putAll(next);
        rebuildScores();

        RivalKind detectedScoringKind = largestPositiveDelta(previousScores);
        if (detectedScoringKind != null && !explicitScoringKindPending) {
            lastScoringKind = detectedScoringKind;
        }
        explicitScoringKindPending = false;
        reconcileDemotion();
        validatePendingForm();
        if (detectedScoringKind != null) {
            queueAvailableEvolution();
        } else if (pendingForm == null) {
            queueAvailableEvolution();
        }
    }

    public synchronized void recordCompletedWave() {
        if (currentForm.isIntermediate()) {
            intermediateWaveCompleted = true;
            queueAvailableEvolution();
        }
    }

    /** Applies no more than one queued evolution during a preparation phase. */
    public synchronized Optional<FormTransition> applyPreparationTransition() {
        validatePendingForm();
        if (pendingForm == null) {
            queueAvailableEvolution();
        }
        if (pendingForm == null || pendingForm == currentForm) {
            pendingForm = null;
            return Optional.empty();
        }

        FoxForm previous = currentForm;
        FoxForm next = pendingForm;
        pendingForm = null;

        currentForm = next;
        next.route().ifPresent(route -> lockedRoute = route);
        if (next.isFinal()) {
            lockedFinalForm = next;
        }
        return Optional.of(new FormTransition(previous, next));
    }

    private void queueAvailableEvolution() {
        if (pendingForm != null || currentForm.isFinal()) {
            return;
        }
        if (currentForm == FoxForm.BASE) {
            FoxForm intermediate = chooseIntermediate();
            if (intermediate != null) {
                lockedRoute = intermediate.route().orElseThrow();
                pendingForm = intermediate;
            }
            return;
        }
        if (!currentForm.isIntermediate() || !intermediateWaveCompleted) {
            return;
        }
        FoxForm finalForm = chooseFinal(currentForm.route().orElseThrow());
        if (finalForm != null) {
            lockedFinalForm = finalForm;
            pendingForm = finalForm;
        }
    }

    private FoxForm chooseIntermediate() {
        if (lockedRoute != null) {
            FoxForm form = FoxForm.intermediateFor(lockedRoute);
            return recipeSatisfied(form) ? form : null;
        }
        for (FoxRoute route : ROUTE_ORDER) {
            FoxForm form = FoxForm.intermediateFor(route);
            if (recipeSatisfied(form)
                    && (lastScoringKind == null || form.recipe().orElseThrow().requires(lastScoringKind))) {
                return form;
            }
        }
        for (FoxRoute route : ROUTE_ORDER) {
            FoxForm form = FoxForm.intermediateFor(route);
            if (recipeSatisfied(form)) {
                return form;
            }
        }
        return null;
    }

    private FoxForm chooseFinal(FoxRoute route) {
        if (lockedFinalForm != null) {
            return recipeSatisfied(lockedFinalForm) ? lockedFinalForm : null;
        }
        List<FoxForm> eligible = new ArrayList<>();
        for (FoxForm form : FoxForm.finalsFor(route)) {
            if (recipeSatisfied(form)) {
                eligible.add(form);
            }
        }
        if (eligible.isEmpty()) {
            return null;
        }
        if (eligible.size() == 1 || lastScoringKind == null) {
            return eligible.getFirst();
        }
        for (FoxForm form : eligible) {
            boolean uniqueAffinity = form.recipe().orElseThrow().requires(lastScoringKind)
                    && eligible.stream()
                    .filter(other -> other != form)
                    .noneMatch(other -> other.recipe().orElseThrow().requires(lastScoringKind));
            if (uniqueAffinity) {
                return form;
            }
        }
        return eligible.getFirst();
    }

    private boolean recipeSatisfied(FoxForm form) {
        return form.recipe().map(recipe -> recipe.satisfiedBy(this::score)).orElse(form == FoxForm.BASE);
    }

    private void reconcileDemotion() {
        FoxForm next = currentForm;
        while (next != FoxForm.BASE && !recipeSatisfied(next)) {
            next = next.parentForm();
        }
        if (next != currentForm) {
            currentForm = next;
            pendingForm = null;
        }
    }

    private void validatePendingForm() {
        if (pendingForm == null || recipeSatisfied(pendingForm)) {
            return;
        }
        pendingForm = null;
    }

    private void rebuildScores() {
        scores.clear();
        scores.putAll(emptyScores());
        for (RivalContribution contribution : contributionByRival.values()) {
            scores.merge(contribution.kind(), contribution.score(), Integer::sum);
        }
    }

    private RivalKind largestPositiveDelta(Map<RivalKind, Integer> previousScores) {
        RivalKind detected = null;
        int largestDelta = 0;
        for (RivalKind kind : RivalKind.values()) {
            int delta = score(kind) - previousScores.getOrDefault(kind, 0);
            if (delta > largestDelta) {
                detected = kind;
                largestDelta = delta;
            }
        }
        return detected;
    }

    private static EnumMap<RivalKind, Integer> emptyScores() {
        EnumMap<RivalKind, Integer> scores = new EnumMap<>(RivalKind.class);
        for (RivalKind kind : RivalKind.values()) {
            scores.put(kind, 0);
        }
        return scores;
    }

    public record FormTransition(FoxForm previous, FoxForm current) {
        public FormTransition {
            Objects.requireNonNull(previous, "previous");
            Objects.requireNonNull(current, "current");
        }
    }
}
