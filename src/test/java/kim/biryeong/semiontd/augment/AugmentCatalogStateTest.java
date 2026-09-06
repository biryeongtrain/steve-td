package kim.biryeong.semiontd.augment;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static kim.biryeong.semiontd.augment.AugmentRarity.*;
import static kim.biryeong.semiontd.augment.PlayerAugmentState.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class AugmentCatalogStateTest {
    private static final UUID PLAYER = UUID.fromString("92a8be81-520e-4d34-a5d2-aa424778935b");
    private static final Predicate<AugmentDefinition> ALL = card -> true;

    @Test
    void approvedCatalogHas35NormalNineReserveAndNineTowerCards() {
        assertEquals(44, AugmentCatalog.definitions().size());
        assertEquals(35, AugmentCatalog.normalDefinitions().size());
        assertEquals(9, AugmentCatalog.reserveDefinitions().size());
        assertEquals(9, AugmentCatalog.normalDefinitions().stream().filter(AugmentDefinition::towerAugment).count());
        assertEquals(44, AugmentCatalog.definitions().stream().map(AugmentDefinition::id).distinct().count());
        assertTrue(AugmentCatalog.find("honorable_retirement").isEmpty());
        assertTrue(AugmentCatalog.find("overcapacity_permit").isEmpty());
        assertEquals(List.of(10L, 15L, 10L), List.of(SILVER, GOLD, PRISMATIC).stream()
                .map(rarity -> AugmentCatalog.normalDefinitions().stream().filter(card -> card.rarity() == rarity).count()).toList());
        for (AugmentDefinition card : AugmentCatalog.definitions()) {
            for (String conflict : card.conflicts()) {
                assertTrue(AugmentCatalog.find(conflict).orElseThrow().conflicts().contains(card.id()), card.id());
            }
        }
    }

    @Test
    void defaultsAreDisabledAndHaveCorrectedReserveIncome() {
        AugmentConfig config = AugmentConfig.defaults();
        assertFalse(config.enabled());
        assertFalse(config.publicPoolEnabled());
        assertEquals(10, config.parameter("reserve_income_silver", "amount", -1));
        assertEquals(20, config.parameter("reserve_income_gold", "amount", -1));
        assertEquals(40, config.parameter("reserve_income_prismatic", "amount", -1));
        assertEquals(240, config.parameter("reserve_diamonds_prismatic", "amount", -1));
        assertEquals(3, config.parameter("reserve_production_prismatic", "amount", -1));
    }

    @Test
    void configBackfillsMissingValuesAndIsDeeplyImmutable() {
        Map<String, Double> card = new HashMap<>(Map.of("damageBonus", .27));
        Map<String, Map<String, Double>> parameters = new HashMap<>(Map.of("tactical_designation_1", card));
        AugmentConfig config = new AugmentConfig(true, true, null, parameters, Set.of());
        card.put("damageBonus", .99);
        parameters.clear();
        assertEquals(.27, config.parameter("tactical_designation_1", "damageBonus", 0));
        assertEquals(.12, config.parameter("tactical_designation_1", "damageReduction", 0));
        assertThrows(UnsupportedOperationException.class, () -> config.parameters().clear());
        assertThrows(UnsupportedOperationException.class, () -> config.parameters().get("semiontd:tactical_designation_1").clear());
        assertEquals(config, AugmentConfig.fromJson(config.toJson()));
        assertEquals(config.version(), AugmentConfig.fromJson(config.toJson()).version());
        assertNotEquals(config.version(), AugmentConfig.defaults().version());
    }

    @Test
    void invalidConfigurationIsRejectedInsteadOfChangingLastGood() {
        AugmentConfig previous = AugmentConfig.defaults();
        String version = previous.version();
        for (String json : List.of(
                "{\"enabled\":\"true\"}", "{\"unknown\":1}", "{\"rarityWeights\":{\"SSS\":4}}",
                "{\"parameters\":{\"missing\":{\"amount\":1}}}",
                "{\"parameters\":{\"tactical_designation_1\":{\"typo\":1}}}",
                "{\"parameters\":{\"reserve_income_gold\":{\"amount\":1.5}}}",
                "{\"parameters\":{\"forecast_offensive\":{\"echoRatio\":0}}}",
                "{\"parameters\":{\"overheat_core\":{\"maxStacks\":0}}}",
                "{\"disabledIds\":[\"reserve_diamonds_silver\"]}")) {
            assertThrows(RuntimeException.class, () -> AugmentConfig.fromJson(JsonParser.parseString(json).getAsJsonObject()), json);
        }
        assertThrows(IllegalArgumentException.class, () -> new AugmentConfig(false, false, null,
                Map.of("tactical_designation_1", Map.of("damageBonus", Double.NaN)), Set.of()));
        assertEquals(version, previous.version());
    }

    @Test
    void rarityDrawIsFrozenDeterministicAndOnlyUsesApprovedCombinations() {
        Set<String> multisets = Set.of("SSS", "GSS", "GGS", "GGG", "GPS", "GGP", "GPP", "PPP");
        for (long seed = 0; seed < 1000; seed++) {
            List<AugmentRarity> draw = AugmentCatalog.drawRarities(seed, AugmentConfig.defaults());
            assertEquals(draw, AugmentCatalog.drawRarities(seed, AugmentConfig.defaults()));
            assertEquals(3, draw.size());
            String key = draw.stream().map(rarity -> rarity.name().substring(0, 1)).sorted().reduce("", String::concat);
            assertTrue(multisets.contains(key));
        }
    }

    @Test
    void initializeAcceptsTheImmutableScheduleReturnedByRarityDraw() {
        AugmentConfig config = AugmentConfig.defaults();
        List<AugmentRarity> schedule = AugmentCatalog.drawRarities(42, config);
        PlayerAugmentState state = new PlayerAugmentState(PLAYER);
        assertDoesNotThrow(() -> state.initialize(42, config, schedule));
        assertEquals(schedule, state.raritySchedule());
        assertEquals(schedule.getFirst(), state.offer(5, 5, 600, ALL).rarity());
    }

    @Test
    void samePlayerSeedAndEligibilityGiveTheSameOfferWithoutMutableRandomConsumption() {
        for (long seed = 0; seed < 100; seed++) {
            PlayerAugmentState first = state(seed, GOLD);
            PlayerAugmentState second = state(seed, GOLD);
            var offer = first.offer(5, 5, 600, ALL);
            assertEquals(offer, second.offer(5, 5, 600, ALL));
            assertEquals(offer, first.offer(5, 99, 90000, card -> false));
            assertOffer(offer);
            assertTrue(offer.cardIds().stream().noneMatch(id -> AugmentCatalog.find(id).orElseThrow().reserve()));
        }
    }

    @Test
    void allRepeatedRaritiesRemainThreeCardsWithSafeAfterEverySelection() {
        for (AugmentRarity rarity : AugmentRarity.values()) {
            for (int seed = 0; seed < 60; seed++) {
                PlayerAugmentState state = state(seed, rarity);
                for (int milestone : AugmentCatalog.MILESTONES) {
                    state.beginPrepare(milestone);
                    var offer = state.offer(milestone, milestone, milestone * 1000L + 600, ALL);
                    assertOffer(offer);
                    for (String id : offer.cardIds()) {
                        AugmentDefinition card = AugmentCatalog.find(id).orElseThrow();
                        for (var selected : state.selections()) {
                            AugmentDefinition old = AugmentCatalog.find(selected.augmentId()).orElseThrow();
                            assertFalse(old.towerAugment() && card.towerAugment());
                            assertFalse(old.conflicts().contains(card.id()));
                            if (!card.reserve()) {assertNotEquals(old.familyKey(), card.familyKey());}
                        }
                    }
                    choose(state, offer, seed % 3, AugmentChoice.none(), milestone * 1000L + 20);
                }
                assertEquals(3, state.selections().size());
            }
        }
    }

    @Test
    void fallbackIsUnconditionallyEligibleAndMayRepeatAcrossMilestones() {
        PlayerAugmentState state = state(3, PRISMATIC);
        for (int milestone : AugmentCatalog.MILESTONES) {
            var offer = state.offer(milestone, milestone, 600, card -> false);
            assertOffer(offer);
            assertTrue(offer.cardIds().stream().allMatch(id -> AugmentCatalog.find(id).orElseThrow().reserve()));
            int diamondSlot = offer.cardIds().indexOf("semiontd:reserve_diamonds_prismatic");
            assertTrue(diamondSlot >= 0);
            assertTrue(state.draft(milestone, diamondSlot, offer.revision(), 0, AugmentChoice.none(), UUID.randomUUID(), 20, card -> false).successful());
            var draft = state.currentOffer().orElseThrow();
            assertTrue(state.confirm(milestone, draft.revision(), draft.draftRevision(), UUID.randomUUID(), 20,
                    card -> false, (card, choice) -> true).successful());
        }
        assertEquals(3, state.selections().stream().filter(selection -> "semiontd:reserve_diamonds_prismatic".equals(selection.augmentId())).count());
    }

    @Test
    void noNormalSafeForcesDiamondAndRerollCannotRemoveThatGuarantee() {
        PlayerAugmentState state = state(6, GOLD);
        Predicate<AugmentDefinition> unsafeOnly = card -> !card.safe();
        var offer = state.offer(5, 5, 600, unsafeOnly);
        int diamondSlot = offer.cardIds().indexOf("semiontd:reserve_diamonds_gold");
        assertTrue(diamondSlot >= 0);
        assertFalse(state.canReroll(5, diamondSlot, unsafeOnly));
        assertEquals(NO_REPLACEMENT, state.reroll(5, diamondSlot, offer.revision(), UUID.randomUUID(), 20, unsafeOnly).status());
        assertFalse(state.rerollSpent());
    }

    @Test
    void noReplacementDoesNotSpendRerollOrChangeOffer() {
        PlayerAugmentState state = state(2, SILVER);
        var offer = state.offer(5, 5, 600, card -> false);
        for (int slot = 0; slot < 3; slot++) {
            assertEquals(NO_REPLACEMENT, state.reroll(5, slot, offer.revision(), UUID.randomUUID(), 20, card -> false).status());
        }
        assertEquals(offer, state.currentOffer().orElseThrow());
        assertFalse(state.rerollSpent());
    }

    @Test
    void rerollChangesOnlyOneSlotAndReplayCannotSpendAnother() {
        PlayerAugmentState state = state(2, GOLD);
        var offer = state.offer(5, 5, 600, ALL);
        int slot = 0;
        UUID request = UUID.randomUUID();
        assertEquals(SUCCESS, state.reroll(5, slot, offer.revision(), request, 20, ALL).status());
        var rerolled = state.currentOffer().orElseThrow();
        assertNotEquals(offer.cardIds().get(slot), rerolled.cardIds().get(slot));
        assertEquals(offer.cardIds().subList(1, 3), rerolled.cardIds().subList(1, 3));
        assertOffer(rerolled);
        assertEquals(SUCCESS, state.reroll(5, slot, offer.revision(), request, 20, ALL).status());
        assertEquals(rerolled, state.currentOffer().orElseThrow());
        assertEquals(REROLL_SPENT, state.reroll(5, 1, rerolled.revision(), UUID.randomUUID(), 20, ALL).status());
    }

    @Test
    void staleDraftCannotConfirmAndNoOpDraftPreservesRevision() {
        PlayerAugmentState state = state(2, SILVER);
        var offer = state.offer(5, 5, 600, ALL);
        var choice = new AugmentChoice(UUID.randomUUID(), null, "assault");
        assertTrue(state.draft(5, 0, 1, 0, choice, UUID.randomUUID(), 20, ALL).successful());
        var drafted = state.currentOffer().orElseThrow();
        assertEquals(UNCHANGED, state.draft(5, 0, 1, drafted.draftRevision(), choice, UUID.randomUUID(), 20, ALL).status());
        assertEquals(drafted, state.currentOffer().orElseThrow());
        AtomicInteger grants = new AtomicInteger();
        assertEquals(STALE_REVISION, state.confirm(5, 1, 0, UUID.randomUUID(), 20, ALL,
                (card, selectedChoice) -> {grants.incrementAndGet(); return true;}).status());
        assertEquals(0, grants.get());
    }

    @Test
    void confirmedEffectRunsOnceForDuplicateRequestsAndDifferentRequestIds() {
        PlayerAugmentState state = state(1, SILVER);
        assertEquals("선택한 증강 0/3", AugmentService.selectionCountLabel(state));
        var offer = state.offer(5, 5, 600, ALL);
        state.draft(5, 0, 1, 0, AugmentChoice.none(), UUID.randomUUID(), 20, ALL);
        assertEquals("선택한 증강 0/3", AugmentService.selectionCountLabel(state));
        long draftRevision = state.currentOffer().orElseThrow().draftRevision();
        UUID request = UUID.randomUUID();
        AtomicInteger grants = new AtomicInteger();
        PlayerAugmentState.Commit commit = (card, choice) -> {grants.incrementAndGet(); return true;};
        assertEquals(SUCCESS, state.confirm(5, 1, draftRevision, request, 20, ALL, commit).status());
        assertEquals(SUCCESS, state.confirm(5, 1, draftRevision, request, 900, ALL, commit).status());
        assertEquals(ALREADY_RESOLVED, state.confirm(5, 1, draftRevision, UUID.randomUUID(), 20, ALL, commit).status());
        assertEquals(INVALID_REQUEST, state.skip(5, 1, request, 20, PlayerAugmentState.SkipReason.EXPLICIT).status());
        assertEquals(1, grants.get());
        assertEquals(1, state.selections().size());
        assertEquals("선택한 증강 1/3", AugmentService.selectionCountLabel(state));
        assertTrue(state.currentOffer().isEmpty());
    }

    @Test
    void failedAndReentrantCommitsCannotSelectOrGrantTwice() {
        PlayerAugmentState state = state(1, SILVER);
        state.offer(5, 5, 600, ALL);
        state.draft(5, 0, 1, 0, AugmentChoice.none(), UUID.randomUUID(), 20, ALL);
        long revision = state.currentOffer().orElseThrow().draftRevision();
        assertEquals(COMMIT_REJECTED, state.confirm(5, 1, revision, UUID.randomUUID(), 20, ALL, (card, choice) -> false).status());
        assertTrue(state.selections().isEmpty());
        AtomicInteger grants = new AtomicInteger();
        assertEquals(SUCCESS, state.confirm(5, 1, revision, UUID.randomUUID(), 20, ALL, (card, choice) -> {
            assertEquals(INVALID_REQUEST, state.confirm(5, 1, revision, UUID.randomUUID(), 20, ALL,
                    (nested, nestedChoice) -> {grants.incrementAndGet(); return true;}).status());
            grants.incrementAndGet();
            return true;
        }).status());
        assertEquals(1, grants.get());
    }

    @Test
    void deadlineIsExclusiveAndNeverAutoSelects() {
        PlayerAugmentState state = state(1, SILVER);
        state.offer(5, 5, 600, ALL);
        assertEquals(INVALID_REQUEST, state.draft(5, 0, 1, 0, AugmentChoice.none(), UUID.randomUUID(), 19, ALL).status());
        assertTrue(state.draft(5, 0, 1, 0, AugmentChoice.none(), UUID.randomUUID(), 20, ALL).successful());
        long revision = state.currentOffer().orElseThrow().draftRevision();
        assertEquals(EXPIRED, state.confirm(5, 1, revision, UUID.randomUUID(), 600, ALL,
                (card, choice) -> fail("Expired choice must not grant.")).status());
        assertEquals(PlayerAugmentState.Outcome.SKIPPED, state.selections().getFirst().outcome());
        assertEquals(PlayerAugmentState.SkipReason.TIMEOUT, state.selections().getFirst().skipReason());
        assertNull(state.selections().getFirst().augmentId());
        state.expire(900);
        assertEquals(1, state.selections().size());
        assertEquals("선택한 증강 0/3", AugmentService.selectionCountLabel(state));
    }

    @Test
    void chosenFamilyAndTowerAugmentsAreExcludedButOfferInternalConflictsAreAllowed() {
        PlayerAugmentState state = state(1, GOLD);
        state.forceOffer(5, 5, 600, List.of("tactical_designation_2", "overheat_core", "reserve_income_gold"));
        choose(state, state.currentOffer().orElseThrow(), 0, AugmentChoice.none(), 20);
        var next = state.offer(15, 15, 600, ALL);
        assertFalse(next.cardIds().contains("semiontd:tactical_designation_2"));
        assertFalse(next.cardIds().contains("semiontd:overheat_core"));

        PlayerAugmentState towers = state(1, GOLD);
        towers.forceOffer(5, 5, 600, List.of("capacitor_post_blueprint", "reserve_diamonds_gold", "reserve_income_gold"));
        choose(towers, towers.currentOffer().orElseThrow(), 0, AugmentChoice.none(), 20);
        assertTrue(towers.offer(15, 15, 600, ALL).cardIds().stream().noneMatch(id -> AugmentCatalog.find(id).orElseThrow().towerAugment()));
    }

    @Test
    void oneTimeTowerCallIsConsumedOnlyByExplicitSuccessfulPlacementAcknowledgement() {
        PlayerAugmentState state = state(1, PRISMATIC);
        state.forceOffer(5, 5, 600, List.of("barrier_core_call", "reserve_diamonds_prismatic", "reserve_income_prismatic"));
        choose(state, state.currentOffer().orElseThrow(), 0, AugmentChoice.none(), 20);
        assertTrue(state.canUseTowerCall("barrier_core_call"));
        assertFalse(state.canUseTowerCall("giant_hunter_call"));
        assertTrue(state.markTowerCallConsumed("barrier_core_call"));
        assertFalse(state.markTowerCallConsumed("barrier_core_call"));
        assertFalse(state.canUseTowerCall("barrier_core_call"));
    }

    @Test
    void overheatStartsEachNewPrepareUnusedAndCanConfigureOnlyOnce() {
        PlayerAugmentState state = state(1, GOLD);
        state.beginPrepare(5);
        state.forceOffer(5, 5, 600, List.of("overheat_core", "reserve_diamonds_gold", "reserve_income_gold"));
        AugmentChoice choice = new AugmentChoice(UUID.randomUUID(), null, "");
        choose(state, state.currentOffer().orElseThrow(), 0, choice, 20);
        assertEquals(choice, state.snapshot().choice("overheat_core"));
        state.beginPrepare(5);
        assertEquals(choice, state.snapshot().choice("overheat_core"));
        assertEquals(CONFIGURED_THIS_ROUND, state.draftConfiguration(5, 5, state.configurationRevision(), choice, UUID.randomUUID(), ALL).status());
        state.beginPrepare(6);
        assertEquals(AugmentChoice.none(), state.snapshot().choice("overheat_core"));
        assertTrue(state.hasSelected("overheat_core"));
        assertTrue(state.draftConfiguration(5, 6, state.configurationRevision(), choice, UUID.randomUUID(), ALL).successful());
        long revision = state.configurationRevision();
        UUID request = UUID.randomUUID();
        AtomicInteger effects = new AtomicInteger();
        PlayerAugmentState.Commit commit = (card, value) -> {effects.incrementAndGet(); return true;};
        assertTrue(state.confirmConfiguration(6, revision, request, ALL, commit).successful());
        assertTrue(state.confirmConfiguration(6, revision, request, ALL, commit).successful());
        assertEquals(1, effects.get());
        assertEquals(choice, state.snapshot().choice("overheat_core"));
        assertEquals(CONFIGURED_THIS_ROUND, state.draftConfiguration(5, 6, state.configurationRevision(), choice, UUID.randomUUID(), ALL).status());
        state.beginPrepare(7);
        assertEquals(AugmentChoice.none(), state.snapshot().choice("overheat_core"));
    }

    @Test
    void snapshotsAndInitializationCannotBeMutatedByLaterChanges() {
        PlayerAugmentState state = state(1, SILVER);
        AugmentSnapshot empty = state.snapshot();
        state.offer(5, 5, 600, ALL);
        choose(state, state.currentOffer().orElseThrow(), 0, AugmentChoice.none(), 20);
        assertTrue(empty.selections().isEmpty());
        assertEquals(1, state.snapshot().selections().size());
        assertThrows(IllegalStateException.class, () -> state.initialize(2, AugmentConfig.defaults(), List.of(GOLD, GOLD, GOLD)));
        assertSame(AugmentSnapshot.none(), AugmentSnapshot.none());
    }

    @Test
    void offerHistoryRecordsOnlyInitialAndSuccessfulRerollOnce() {
        PlayerAugmentState state = state(2, GOLD);
        var offer = state.offer(5, 5, 600, ALL);
        List<PlayerAugmentState.OfferEvent> initialHistory = state.offerEvents();
        assertEquals(1, initialHistory.size());
        var initial = initialHistory.getFirst();
        assertEquals("INITIAL", initial.eventType());
        assertEquals(5, initial.milestoneRound());
        assertEquals(GOLD, initial.rarity());
        assertEquals(offer.revision(), initial.offerRevision());
        assertEquals(List.of(), initial.before());
        assertEquals(offer.cardIds(), initial.after());
        state.offer(5, 5, 600, ALL);
        assertEquals(1, state.offerEvents().size());
        assertThrows(UnsupportedOperationException.class, () -> initialHistory.clear());
        assertThrows(UnsupportedOperationException.class, () -> initial.after().clear());
        UUID requestId = UUID.randomUUID();
        assertEquals(SUCCESS, state.reroll(5, 0, offer.revision(), requestId, 20, ALL).status());
        assertEquals(SUCCESS, state.reroll(5, 0, offer.revision(), requestId, 20, ALL).status());
        assertEquals(1, initialHistory.size());
        assertEquals(2, state.offerEvents().size());
        var rerolled = state.offerEvents().getLast();
        assertEquals("REROLLED", rerolled.eventType());
        assertEquals(offer.cardIds(), rerolled.before());
        assertEquals(state.currentOffer().orElseThrow().cardIds(), rerolled.after());
        assertEquals(offer.cardIds(), initial.after());
        assertThrows(UnsupportedOperationException.class, () -> rerolled.before().clear());
        assertThrows(UnsupportedOperationException.class, () -> rerolled.after().clear());
        var current = state.currentOffer().orElseThrow();
        assertEquals(REROLL_SPENT, state.reroll(5, 1, current.revision(), UUID.randomUUID(), 20, ALL).status());
        assertEquals(2, state.offerEvents().size());
        choose(state, current, 0, AugmentChoice.none(), 20);
        state.offer(15, 15, 600, ALL);
        assertEquals(3, state.offerEvents().size());
        assertEquals("INITIAL", state.offerEvents().getLast().eventType());
        assertEquals(15, state.offerEvents().getLast().milestoneRound());
    }

    @Test
    void failedRerollAndReopenedDraftNeverAddExposureEvents() {
        PlayerAugmentState state = state(2, SILVER);
        var offer = state.offer(5, 5, 600, card -> false);
        assertEquals(NO_REPLACEMENT, state.reroll(5, 0, offer.revision(), UUID.randomUUID(), 20, card -> false).status());
        assertTrue(state.draft(5, 0, offer.revision(), 0, AugmentChoice.none(), UUID.randomUUID(), 20, ALL).successful());
        var draft = state.currentOffer().orElseThrow();
        assertEquals(UNCHANGED, state.draft(5, 0, draft.revision(), draft.draftRevision(),
                AugmentChoice.none(), UUID.randomUUID(), 20, ALL).status());
        state.expire(600);
        assertEquals(1, state.offerEvents().size());
    }

    @Test
    void previousMilestoneButtonRevisionCannotMutateTheNextOffer() {
        PlayerAugmentState state = state(1, GOLD);
        var first = state.offer(5, 5, 600, ALL);
        choose(state, first, 0, AugmentChoice.none(), 20);
        var next = state.offer(15, 15, 600, ALL);
        assertTrue(next.revision() > first.revision());
        assertEquals(STALE_REVISION, state.draft(15, 0, first.revision(), 0,
                AugmentChoice.none(), UUID.randomUUID(), 20, ALL).status());
        assertEquals(STALE_REVISION, state.reroll(15, 0, first.revision(), UUID.randomUUID(), 20, ALL).status());
        assertEquals(STALE_REVISION, state.skip(15, first.revision(), UUID.randomUUID(), 20,
                PlayerAugmentState.SkipReason.EXPLICIT).status());
        assertEquals(next, state.currentOffer().orElseThrow());
        assertFalse(state.rerollSpent());
    }

    @Test
    void skippedMilestoneCannotOpenConfigurationAndDoesNotThrowNullPointer() {
        PlayerAugmentState state = state(1, GOLD);
        var offer = state.offer(5, 5, 600, ALL);
        assertTrue(state.skip(5, offer.revision(), UUID.randomUUID(), 20, PlayerAugmentState.SkipReason.EXPLICIT).successful());
        state.beginPrepare(6);
        assertEquals(INELIGIBLE, state.draftConfiguration(5, 6, state.configurationRevision(),
                AugmentChoice.none(), UUID.randomUUID(), ALL).status());
    }

    private static PlayerAugmentState state(long seed, AugmentRarity rarity) {
        PlayerAugmentState state = new PlayerAugmentState(PLAYER);
        state.initialize(seed, AugmentConfig.defaults(), List.of(rarity, rarity, rarity));
        return state;
    }

    private static void choose(PlayerAugmentState state, PlayerAugmentState.Offer offer, int slot, AugmentChoice choice, long tick) {
        assertTrue(state.draft(offer.milestoneRound(), slot, offer.revision(), offer.draftRevision(), choice,
                UUID.randomUUID(), tick, ALL).successful());
        var draft = state.currentOffer().orElseThrow();
        assertTrue(state.confirm(offer.milestoneRound(), draft.revision(), draft.draftRevision(), UUID.randomUUID(), tick,
                ALL, (card, selection) -> true).successful());
    }

    private static void assertOffer(PlayerAugmentState.Offer offer) {
        assertEquals(3, offer.cardIds().size());
        assertEquals(3, new HashSet<>(offer.cardIds()).size());
        List<AugmentDefinition> cards = offer.cardIds().stream().map(id -> AugmentCatalog.find(id).orElseThrow()).toList();
        assertEquals(3, cards.stream().map(AugmentDefinition::familyKey).distinct().count());
        assertTrue(cards.stream().allMatch(card -> card.rarity() == offer.rarity()));
        assertTrue(cards.stream().anyMatch(AugmentDefinition::safe));
        assertTrue(cards.stream().filter(AugmentDefinition::risky).count() <= 1);
    }
}
