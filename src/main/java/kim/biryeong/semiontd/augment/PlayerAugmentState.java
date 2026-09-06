package kim.biryeong.semiontd.augment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Pure per-match state. The server controller owns identity, phase, target and resource validation. */
public final class PlayerAugmentState {
    public enum Outcome { SELECTED, SKIPPED }
    public enum SkipReason { EXPLICIT, TIMEOUT }
    public enum Status {
        SUCCESS, UNCHANGED, INVALID_REQUEST, STALE_REVISION, EXPIRED, ALREADY_RESOLVED,
        INELIGIBLE, NO_REPLACEMENT, REROLL_SPENT, CONFIGURED_THIS_ROUND, COMMIT_REJECTED
    }
    public record ActionResult(Status status) {
        public boolean successful() {return status == Status.SUCCESS || status == Status.UNCHANGED;}
    }
    public record Selection(int milestoneRound, AugmentRarity rarity, String augmentId, Outcome outcome,
                            SkipReason skipReason, AugmentChoice choice) {
        public Selection {
            Objects.requireNonNull(rarity, "rarity");
            Objects.requireNonNull(outcome, "outcome");
            if (outcome == Outcome.SELECTED) {
                augmentId = AugmentCatalog.normalizeId(augmentId);
                if (skipReason != null) {throw new IllegalArgumentException("Selected augment has no skip reason.");}
            } else {
                if (augmentId != null || skipReason == null) {throw new IllegalArgumentException("Skipped augment requires only a reason.");}
            }
            choice = choice == null ? AugmentChoice.none() : choice;
        }
    }
    public record Draft(String cardId, AugmentChoice choice) {}
    public record Offer(int milestoneRound, int offeredRound, AugmentRarity rarity, List<String> cardIds,
                        long revision, long deadlineTickExclusive, long draftRevision, Draft draft) {
        public Offer {cardIds = List.copyOf(cardIds);}
        public long inputAllowedTick() {return AugmentService.inputAllowedTick(deadlineTickExclusive);}
    }
    public record ConfigurationDraft(int selectedMilestone, int currentRound, long revision, AugmentChoice choice) {}
    public record OfferEvent(int milestoneRound, AugmentRarity rarity, String eventType, long offerRevision,
                             List<String> before, List<String> after) {
        public OfferEvent {
            Objects.requireNonNull(rarity, "rarity");
            if (!"INITIAL".equals(eventType) && !"REROLLED".equals(eventType)) {
                throw new IllegalArgumentException("Unknown augment offer event type.");
            }
            before = List.copyOf(before);
            after = List.copyOf(after);
        }
    }

    /** Return false without changing runtime state; true must atomically apply the whole effect. */
    @FunctionalInterface
    public interface Commit {
        boolean tryApply(AugmentDefinition definition, AugmentChoice choice);
    }

    private record Receipt(String fingerprint, ActionResult result) {}
    private static final Set<String> CALLS = Set.of("semiontd:barrier_core_call", "semiontd:giant_hunter_call",
            "semiontd:starlight_cocoon_call", "semiontd:ordnance_factory_call");
    private static final Set<String> CONFIGURABLE = Set.of("semiontd:tactical_designation_1", "semiontd:tactical_designation_2",
            "semiontd:tactical_designation_3", "semiontd:engagement_plan", "semiontd:overheat_core",
            "semiontd:frontline_specialization", "semiontd:biased_armor");
    private UUID playerId;
    private long seed;
    private AugmentConfig config = AugmentConfig.defaults();
    private List<AugmentRarity> schedule = List.of();
    private final Map<Integer, Selection> selections = new LinkedHashMap<>();
    private final Map<Integer, Offer> offers = new LinkedHashMap<>();
    private final List<OfferEvent> offerEvents = new ArrayList<>();
    private final Map<Integer, Set<String>> seen = new HashMap<>();
    private final Map<UUID, Receipt> receipts = new HashMap<>();
    private final Set<String> consumedCalls = new HashSet<>();
    private final Map<Integer, Integer> lastConfiguredRounds = new HashMap<>();
    private Integer currentMilestone;
    private long nextOfferRevision;
    private boolean rerollSpent;
    private boolean committing;
    private int preparedRound = -1;
    private long configurationRevision;
    private ConfigurationDraft configurationDraft;

    public PlayerAugmentState() {}
    public PlayerAugmentState(UUID playerId) {this.playerId = Objects.requireNonNull(playerId);}

    public synchronized void initialize(UUID playerId, long seed, AugmentConfig config, List<AugmentRarity> schedule) {
        if (!this.schedule.isEmpty()) {throw new IllegalStateException("Augment match state is already initialized.");}
        if (schedule.size() != 3 || schedule.stream().anyMatch(Objects::isNull)) {throw new IllegalArgumentException("Three rarities are required.");}
        this.playerId = Objects.requireNonNull(playerId);
        this.seed = seed;
        this.config = Objects.requireNonNull(config);
        this.schedule = List.copyOf(schedule);
    }

    public synchronized void initialize(long seed, AugmentConfig config, List<AugmentRarity> schedule) {
        initialize(Objects.requireNonNull(playerId, "playerId"), seed, config, schedule);
    }

    public synchronized boolean initialized() {return !schedule.isEmpty();}
    public synchronized AugmentConfig config() {return config;}
    public synchronized List<AugmentRarity> raritySchedule() {return schedule;}
    public synchronized boolean rerollSpent() {return rerollSpent;}
    public synchronized List<Selection> selections() {return List.copyOf(selections.values());}
    public synchronized List<OfferEvent> offerEvents() {return List.copyOf(offerEvents);}
    public synchronized AugmentSnapshot snapshot() {return new AugmentSnapshot(config, selections());}
    public synchronized Optional<Offer> currentOffer() {
        return currentMilestone == null || selections.containsKey(currentMilestone)
                ? Optional.empty() : Optional.ofNullable(offers.get(currentMilestone));
    }
    public synchronized boolean hasSelected(String id) {
        String normalized = AugmentCatalog.normalizeId(id);
        return selections.values().stream().anyMatch(selection -> normalized.equals(selection.augmentId()));
    }
    public synchronized boolean canUseTowerCall(String id) {
        String normalized = AugmentCatalog.normalizeId(id);
        return CALLS.contains(normalized) && hasSelected(normalized) && !consumedCalls.contains(normalized);
    }
    public synchronized boolean markTowerCallConsumed(String id) {
        if (!canUseTowerCall(id)) {return false;}
        return consumedCalls.add(AugmentCatalog.normalizeId(id));
    }

    /** Repeated prepare callbacks do not clear a reservation made during that same prepare. */
    public synchronized void beginPrepare(int round) {
        if (round <= preparedRound || committing) {return;}
        preparedRound = round;
        configurationDraft = null;
        configurationRevision++;
        selections.replaceAll((milestone, selection) -> "semiontd:overheat_core".equals(selection.augmentId())
                ? new Selection(milestone, selection.rarity(), selection.augmentId(), selection.outcome(), null, AugmentChoice.none())
                : selection);
    }

    public synchronized void clearConfigurationDraft() {
        if (configurationDraft != null && !committing) {configurationDraft = null; configurationRevision++;}
    }

    public synchronized Offer offer(int milestoneRound, int offeredRound, long deadlineTickExclusive,
                                    Predicate<AugmentDefinition> eligible) {
        requireMilestone(milestoneRound);
        Offer existing = offers.get(milestoneRound);
        if (existing != null) {return existing;}
        if (committing || currentOffer().isPresent() || selections.containsKey(milestoneRound)) {
            throw new IllegalStateException("Another augment offer is active or this milestone is resolved.");
        }
        AugmentRarity rarity = schedule.get(AugmentCatalog.MILESTONES.indexOf(milestoneRound));
        List<AugmentDefinition> candidates = normalCandidates(milestoneRound, rarity, eligible);
        shuffle(candidates, milestoneRound, 0);
        List<AugmentDefinition> selected = new ArrayList<>();
        // Reserve diamonds are mandatory when no currently eligible normal SAFE card exists.
        Optional<AugmentDefinition> safe = candidates.stream().filter(AugmentDefinition::safe).findFirst();
        selected.add(safe.orElseGet(() -> diamondReserve(rarity)));
        while (selected.size() < 3) {
            List<AugmentDefinition> remaining = candidates.stream().filter(card -> fits(card, selected)).toList();
            Optional<AugmentDefinition> next = remaining.stream().filter(card -> selected.stream()
                    .noneMatch(previous -> previous.category() == card.category())).findFirst();
            if (next.isEmpty()) {next = remaining.stream().findFirst();}
            if (next.isPresent()) {selected.add(next.get());}
            else {
                selected.add(AugmentCatalog.reserveDefinitions().stream().filter(card -> card.rarity() == rarity)
                        .filter(card -> fits(card, selected)).findFirst().orElseThrow());
            }
        }
        shuffle(selected, milestoneRound, 1);
        return storeOffer(milestoneRound, offeredRound, deadlineTickExclusive, rarity,
                selected.stream().map(AugmentDefinition::id).toList());
    }

    /** Internal test injection only; the calling command must enforce its admin/test-mode boundary. */
    public synchronized Offer forceOffer(int milestoneRound, int offeredRound, long deadlineTickExclusive, List<String> ids) {
        requireMilestone(milestoneRound);
        if (committing || offers.containsKey(milestoneRound) || currentOffer().isPresent() || selections.containsKey(milestoneRound)) {
            throw new IllegalStateException("Cannot replace an existing augment offer.");
        }
        List<AugmentDefinition> cards = ids.stream().map(id -> AugmentCatalog.find(id).orElseThrow()).toList();
        AugmentRarity rarity = schedule.get(AugmentCatalog.MILESTONES.indexOf(milestoneRound));
        List<AugmentDefinition> checked = new ArrayList<>();
        for (AugmentDefinition card : cards) {
            if (card.rarity() != rarity || !isEligible(card, milestoneRound, ignored -> true) || !fits(card, checked)) {
                throw new IllegalArgumentException("Forced offer violates card eligibility.");
            }
            checked.add(card);
        }
        if (cards.size() != 3 || cards.stream().noneMatch(AugmentDefinition::safe)) {
            throw new IllegalArgumentException("Forced offer requires three cards and one SAFE card.");
        }
        return storeOffer(milestoneRound, offeredRound, deadlineTickExclusive, rarity, cards.stream().map(AugmentDefinition::id).toList());
    }

    private Offer storeOffer(int milestone, int offeredRound, long deadline, AugmentRarity rarity, List<String> ids) {
        Offer offer = new Offer(milestone, offeredRound, rarity, ids, ++nextOfferRevision, deadline, 0, null);
        offers.put(milestone, offer);
        offerEvents.add(new OfferEvent(milestone, rarity, "INITIAL", offer.revision(), List.of(), ids));
        seen.put(milestone, new HashSet<>(ids));
        currentMilestone = milestone;
        return offer;
    }

    public synchronized boolean canReroll(int milestone, int slot, Predicate<AugmentDefinition> eligible) {
        Offer offer = offers.get(milestone);
        return !rerollSpent && !selections.containsKey(milestone) && offer != null && slot >= 0 && slot < 3
                && !replacements(offer, slot, eligible).isEmpty();
    }

    public synchronized ActionResult reroll(int milestone, int slot, long offerRevision, UUID requestId,
                                            long now, Predicate<AugmentDefinition> eligible) {
        String fingerprint = "reroll:" + milestone + ":" + slot + ":" + offerRevision;
        ActionResult replay = replay(requestId, fingerprint);
        if (replay != null) {return replay;}
        Status invalid = validateOffer(milestone, offerRevision, now);
        if (invalid != null) {return result(requestId, fingerprint, invalid);}
        if (slot < 0 || slot >= 3) {return result(requestId, fingerprint, Status.INVALID_REQUEST);}
        if (rerollSpent) {return result(requestId, fingerprint, Status.REROLL_SPENT);}
        Offer offer = offers.get(milestone);
        List<AugmentDefinition> replacements = replacements(offer, slot, eligible);
        if (replacements.isEmpty()) {return result(requestId, fingerprint, Status.NO_REPLACEMENT);}
        shuffle(replacements, milestone, 100 + slot);
        List<String> ids = new ArrayList<>(offer.cardIds());
        String replacement = replacements.getFirst().id();
        ids.set(slot, replacement);
        long revision = ++nextOfferRevision;
        offers.put(milestone, new Offer(milestone, offer.offeredRound(), offer.rarity(), ids, revision,
                offer.deadlineTickExclusive(), offer.draftRevision() + 1, null));
        offerEvents.add(new OfferEvent(milestone, offer.rarity(), "REROLLED", revision, offer.cardIds(), ids));
        seen.get(milestone).add(replacement);
        rerollSpent = true;
        return result(requestId, fingerprint, Status.SUCCESS);
    }

    public synchronized ActionResult draft(int milestone, int slot, long offerRevision, long draftRevision,
                                           AugmentChoice choice, UUID requestId, long now,
                                           Predicate<AugmentDefinition> eligible) {
        choice = choice == null ? AugmentChoice.none() : choice;
        String fingerprint = "draft:" + milestone + ":" + slot + ":" + offerRevision + ":" + draftRevision + ":" + choice;
        ActionResult replay = replay(requestId, fingerprint);
        if (replay != null) {return replay;}
        Status invalid = validateOffer(milestone, offerRevision, now);
        if (invalid != null) {return result(requestId, fingerprint, invalid);}
        Offer offer = offers.get(milestone);
        if (draftRevision != offer.draftRevision()) {return result(requestId, fingerprint, Status.STALE_REVISION);}
        if (slot < 0 || slot >= 3) {return result(requestId, fingerprint, Status.INVALID_REQUEST);}
        AugmentDefinition card = AugmentCatalog.find(offer.cardIds().get(slot)).orElseThrow();
        if (!isEligible(card, milestone, eligible)) {return result(requestId, fingerprint, Status.INELIGIBLE);}
        Draft next = new Draft(card.id(), choice);
        if (next.equals(offer.draft())) {return result(requestId, fingerprint, Status.UNCHANGED);}
        offers.put(milestone, new Offer(milestone, offer.offeredRound(), offer.rarity(), offer.cardIds(), offer.revision(),
                offer.deadlineTickExclusive(), offer.draftRevision() + 1, next));
        return result(requestId, fingerprint, Status.SUCCESS);
    }

    public synchronized ActionResult confirm(int milestone, long offerRevision, long draftRevision, UUID requestId,
                                             long now, Predicate<AugmentDefinition> eligible, Commit commit) {
        String fingerprint = "confirm:" + milestone + ":" + offerRevision + ":" + draftRevision;
        ActionResult replay = replay(requestId, fingerprint);
        if (replay != null) {return replay;}
        Status invalid = validateOffer(milestone, offerRevision, now);
        if (invalid != null) {return result(requestId, fingerprint, invalid);}
        Offer offer = offers.get(milestone);
        if (draftRevision != offer.draftRevision()) {return result(requestId, fingerprint, Status.STALE_REVISION);}
        if (offer.draft() == null) {return result(requestId, fingerprint, Status.INVALID_REQUEST);}
        AugmentDefinition card = AugmentCatalog.find(offer.draft().cardId()).orElseThrow();
        if (!isEligible(card, milestone, eligible)) {return result(requestId, fingerprint, Status.INELIGIBLE);}
        if (!apply(commit, card, offer.draft().choice())) {return result(requestId, fingerprint, Status.COMMIT_REJECTED);}
        selections.put(milestone, new Selection(milestone, offer.rarity(), card.id(), Outcome.SELECTED, null, offer.draft().choice()));
        lastConfiguredRounds.put(milestone, offer.offeredRound());
        currentMilestone = null;
        return result(requestId, fingerprint, Status.SUCCESS);
    }

    public synchronized ActionResult skip(int milestone, long offerRevision, UUID requestId, long now, SkipReason reason) {
        String fingerprint = "skip:" + milestone + ":" + offerRevision + ":" + reason;
        ActionResult replay = replay(requestId, fingerprint);
        if (replay != null) {return replay;}
        Status invalid = validateOffer(milestone, offerRevision, now);
        if (invalid != null) {return result(requestId, fingerprint, invalid);}
        if (reason != SkipReason.EXPLICIT) {return result(requestId, fingerprint, Status.INVALID_REQUEST);}
        resolveSkipped(offers.get(milestone), reason);
        return result(requestId, fingerprint, Status.SUCCESS);
    }

    public synchronized void expire(long now) {
        if (committing) {return;}
        currentOffer().filter(offer -> now >= offer.deadlineTickExclusive()).ifPresent(offer -> resolveSkipped(offer, SkipReason.TIMEOUT));
    }

    public synchronized long configurationRevision() {return configurationRevision;}
    public synchronized Optional<ConfigurationDraft> configurationDraft() {return Optional.ofNullable(configurationDraft);}
    public synchronized int lastConfiguredRound(int selectedMilestone) {return lastConfiguredRounds.getOrDefault(selectedMilestone, -1);}

    public synchronized ActionResult draftConfiguration(int selectedMilestone, int currentRound, long expectedRevision,
                                                        AugmentChoice choice, UUID requestId,
                                                        Predicate<AugmentDefinition> eligible) {
        choice = choice == null ? AugmentChoice.none() : choice;
        String fingerprint = "configure-draft:" + selectedMilestone + ":" + currentRound + ":" + expectedRevision + ":" + choice;
        ActionResult replay = replay(requestId, fingerprint);
        if (replay != null) {return replay;}
        if (committing) {return result(requestId, fingerprint, Status.INVALID_REQUEST);}
        if (expectedRevision != configurationRevision) {return result(requestId, fingerprint, Status.STALE_REVISION);}
        Selection selection = selections.get(selectedMilestone);
        if (selection == null || selection.outcome() != Outcome.SELECTED
                || !CONFIGURABLE.contains(selection.augmentId()) || currentRound != preparedRound) {
            return result(requestId, fingerprint, Status.INELIGIBLE);
        }
        if (lastConfiguredRound(selectedMilestone) >= currentRound) {return result(requestId, fingerprint, Status.CONFIGURED_THIS_ROUND);}
        AugmentDefinition card = AugmentCatalog.find(selection.augmentId()).orElseThrow();
        if (!eligible.test(card)) {return result(requestId, fingerprint, Status.INELIGIBLE);}
        if (configurationDraft != null && configurationDraft.selectedMilestone() == selectedMilestone
                && configurationDraft.currentRound() == currentRound && configurationDraft.choice().equals(choice)) {
            return result(requestId, fingerprint, Status.UNCHANGED);
        }
        configurationRevision++;
        configurationDraft = new ConfigurationDraft(selectedMilestone, currentRound, configurationRevision, choice);
        return result(requestId, fingerprint, Status.SUCCESS);
    }

    public synchronized ActionResult confirmConfiguration(int currentRound, long expectedRevision, UUID requestId,
                                                          Predicate<AugmentDefinition> eligible, Commit commit) {
        String fingerprint = "configure-confirm:" + currentRound + ":" + expectedRevision;
        ActionResult replay = replay(requestId, fingerprint);
        if (replay != null) {return replay;}
        if (committing) {return result(requestId, fingerprint, Status.INVALID_REQUEST);}
        if (expectedRevision != configurationRevision) {return result(requestId, fingerprint, Status.STALE_REVISION);}
        ConfigurationDraft draft = configurationDraft;
        if (draft == null || draft.currentRound() != currentRound || currentRound != preparedRound) {
            return result(requestId, fingerprint, Status.INVALID_REQUEST);
        }
        if (lastConfiguredRound(draft.selectedMilestone()) >= currentRound) {return result(requestId, fingerprint, Status.CONFIGURED_THIS_ROUND);}
        Selection selection = selections.get(draft.selectedMilestone());
        AugmentDefinition card = AugmentCatalog.find(selection.augmentId()).orElseThrow();
        if (!eligible.test(card)) {return result(requestId, fingerprint, Status.INELIGIBLE);}
        if (!apply(commit, card, draft.choice())) {return result(requestId, fingerprint, Status.COMMIT_REJECTED);}
        selections.put(selection.milestoneRound(), new Selection(selection.milestoneRound(), selection.rarity(), selection.augmentId(),
                selection.outcome(), null, draft.choice()));
        lastConfiguredRounds.put(selection.milestoneRound(), currentRound);
        configurationDraft = null;
        configurationRevision++;
        return result(requestId, fingerprint, Status.SUCCESS);
    }

    private boolean apply(Commit commit, AugmentDefinition card, AugmentChoice choice) {
        committing = true;
        try {return Objects.requireNonNull(commit).tryApply(card, choice);}
        finally {committing = false;}
    }

    private void resolveSkipped(Offer offer, SkipReason reason) {
        selections.put(offer.milestoneRound(), new Selection(offer.milestoneRound(), offer.rarity(), null, Outcome.SKIPPED, reason, AugmentChoice.none()));
        currentMilestone = null;
    }

    private Status validateOffer(int milestone, long revision, long now) {
        if (committing) {return Status.INVALID_REQUEST;}
        if (selections.containsKey(milestone)) {return Status.ALREADY_RESOLVED;}
        Offer offer = offers.get(milestone);
        if (offer == null || !Objects.equals(currentMilestone, milestone)) {return Status.INVALID_REQUEST;}
        if (now >= offer.deadlineTickExclusive()) {resolveSkipped(offer, SkipReason.TIMEOUT); return Status.EXPIRED;}
        if (revision != offer.revision()) {return Status.STALE_REVISION;}
        if (now < offer.inputAllowedTick()) {return Status.INVALID_REQUEST;}
        return null;
    }

    private ActionResult replay(UUID requestId, String fingerprint) {
        if (requestId == null || committing) {return new ActionResult(Status.INVALID_REQUEST);}
        Receipt receipt = receipts.get(requestId);
        if (receipt == null) {return null;}
        return receipt.fingerprint().equals(fingerprint) ? receipt.result() : new ActionResult(Status.INVALID_REQUEST);
    }

    private ActionResult result(UUID id, String fingerprint, Status status) {
        ActionResult result = new ActionResult(status);
        if (id != null) {receipts.put(id, new Receipt(fingerprint, result));}
        return result;
    }

    private List<AugmentDefinition> normalCandidates(int milestone, AugmentRarity rarity, Predicate<AugmentDefinition> eligible) {
        return new ArrayList<>(AugmentCatalog.normalDefinitions().stream().filter(card -> card.rarity() == rarity)
                .filter(card -> isEligible(card, milestone, eligible)).toList());
    }

    private boolean isEligible(AugmentDefinition card, int milestone, Predicate<AugmentDefinition> eligible) {
        if (!card.milestoneRounds().contains(milestone) || !config.isEnabled(card.id())) {return false;}
        if (card.reserve()) {return true;}
        for (Selection selected : selections.values()) {
            if (selected.outcome() != Outcome.SELECTED) {continue;}
            AugmentDefinition previous = AugmentCatalog.find(selected.augmentId()).orElseThrow();
            if (previous.familyKey().equals(card.familyKey()) || previous.towerAugment() && card.towerAugment()
                    || previous.conflicts().contains(card.id()) || card.conflicts().contains(previous.id())) {return false;}
        }
        return eligible.test(card);
    }

    private List<AugmentDefinition> replacements(Offer offer, int slot, Predicate<AugmentDefinition> eligible) {
        List<AugmentDefinition> fixed = new ArrayList<>();
        for (int i = 0; i < 3; i++) {if (i != slot) {fixed.add(AugmentCatalog.find(offer.cardIds().get(i)).orElseThrow());}}
        boolean needsSafe = fixed.stream().noneMatch(card -> card.safe() && isEligible(card, offer.milestoneRound(), eligible));
        List<AugmentDefinition> eligibleNormal = normalCandidates(offer.milestoneRound(), offer.rarity(), eligible);
        boolean needsDiamond = eligibleNormal.stream().noneMatch(AugmentDefinition::safe)
                && fixed.stream().noneMatch(card -> card.familyKey().equals("RESERVE_DIAMONDS"));
        List<AugmentDefinition> normal = eligibleNormal.stream()
                .filter(card -> !seen.get(offer.milestoneRound()).contains(card.id()) && fits(card, fixed))
                .filter(card -> !needsSafe || card.safe()).filter(card -> !needsDiamond).toList();
        if (!normal.isEmpty()) {return new ArrayList<>(normal);}
        List<AugmentDefinition> reserves = AugmentCatalog.reserveDefinitions().stream()
                .filter(card -> card.rarity() == offer.rarity() && fits(card, fixed))
                .filter(card -> !seen.get(offer.milestoneRound()).contains(card.id())).toList();
        if (needsSafe || needsDiamond) {
            AugmentDefinition diamond = diamondReserve(offer.rarity());
            return reserves.contains(diamond) ? new ArrayList<>(List.of(diamond)) : new ArrayList<>();
        }
        return new ArrayList<>(reserves);
    }

    private static boolean fits(AugmentDefinition card, List<AugmentDefinition> selected) {
        return selected.stream().noneMatch(previous -> previous.id().equals(card.id())
                || previous.familyKey().equals(card.familyKey()) || previous.risky() && card.risky());
    }

    private static AugmentDefinition diamondReserve(AugmentRarity rarity) {
        return AugmentCatalog.find("reserve_diamonds_" + rarity.name().toLowerCase(java.util.Locale.ROOT)).orElseThrow();
    }

    private void shuffle(List<AugmentDefinition> cards, int milestone, int salt) {
        cards.sort(Comparator.comparing(AugmentDefinition::id));
        long value = seed ^ playerId.getMostSignificantBits() ^ Long.rotateLeft(playerId.getLeastSignificantBits(), 17)
                ^ ((long) milestone << 32) ^ (0x9E3779B97F4A7C15L * salt);
        Collections.shuffle(cards, new Random(value));
    }

    private void requireMilestone(int milestone) {
        if (!initialized() || !AugmentCatalog.MILESTONES.contains(milestone)) {
            throw new IllegalArgumentException("Initialized R5/R15/R25 state is required.");
        }
    }
}
