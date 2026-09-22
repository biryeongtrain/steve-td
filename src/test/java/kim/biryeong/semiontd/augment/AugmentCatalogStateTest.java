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
    void approvedCatalogHas164NormalNineReserveAndNineTowerCards() {
        assertEquals(173, AugmentCatalog.definitions().size());
        assertEquals(164, AugmentCatalog.normalDefinitions().size());
        assertEquals(9, AugmentCatalog.reserveDefinitions().size());
        assertEquals(9, AugmentCatalog.normalDefinitions().stream().filter(AugmentDefinition::towerAugment).count());
        assertEquals(173, AugmentCatalog.definitions().stream().map(AugmentDefinition::id).distinct().count());
        assertTrue(AugmentCatalog.find("honorable_retirement").isEmpty());
        assertTrue(AugmentCatalog.find("overcapacity_permit").isEmpty());
        assertEquals(List.of(43L, 79L, 42L), List.of(SILVER, GOLD, PRISMATIC).stream()
                .map(rarity -> AugmentCatalog.normalDefinitions().stream().filter(card -> card.rarity() == rarity).count()).toList());
        for (AugmentDefinition card : AugmentCatalog.definitions()) {
            for (String conflict : card.conflicts()) {
                assertTrue(AugmentCatalog.find(conflict).orElseThrow().conflicts().contains(card.id()), card.id());
            }
        }
    }

    @Test
    void allJobCardsKeepStableOwnershipAndMergeValidatedDefaults() {
        var jobs = AugmentCatalog.definitions().stream().filter(card -> card.requiredJobId() != null).toList();
        assertEquals(124, jobs.size());
        assertEquals(31, jobs.stream().map(AugmentDefinition::requiredJobId).distinct().count());
        for (String job : jobs.stream().map(AugmentDefinition::requiredJobId).distinct().toList()) {
            List<AugmentDefinition> owned = jobs.stream().filter(card -> job.equals(card.requiredJobId())).toList();
            String key = switch (job) {
                case "semion-td:atlantis" -> "atlantis_towers";
                case "semion-td:developer" -> "developer_towers";
                case "semion-td:gamble_towers" -> "gamble";
                default -> job.substring("semion-td:".length());
            };
            String prefix = "semiontd:job_" + key + "_";
            assertEquals(Set.of(prefix + "s", prefix + "g1", prefix + "g2", prefix + "p"),
                    owned.stream().map(AugmentDefinition::id).collect(java.util.stream.Collectors.toSet()), job);
            assertEquals(List.of(1L, 2L, 1L), List.of(SILVER, GOLD, PRISMATIC).stream()
                    .map(rarity -> owned.stream().filter(card -> card.rarity() == rarity).count()).toList(), job);
        }
        for (var card : jobs) {
            assertEquals(Set.of(5, 15, 25), card.milestoneRounds());
            assertFalse(card.reserve());
        }
        AugmentConfig config = AugmentConfig.fromJson(JsonParser.parseString("{\"parameters\":{\"job_illager_towers_s\":{\"markDamageBonus\":0.35}}}").getAsJsonObject());
        assertEquals(.35, config.parameter("job_illager_towers_s", "markDamageBonus", -1));
        assertEquals(80, config.parameter("job_illager_towers_s", "markDurationTicks", -1));
        assertEquals(2, config.parameter("job_adversary_towers_g1", "scoreMultiplier", -1));
        assertEquals(.8, config.parameter("job_engineer_towers_g2", "repeatDamageRatio", -1));
        assertNotEquals(AugmentConfig.defaults().version(), config.version());
        for (String parameters : List.of("\"job_illager_towers_s\":{\"markDurationTicks\":0}",
                "\"job_adversary_towers_g1\":{\"scoreMultiplier\":1.5}",
                "\"job_adversary_towers_g1\":{\"healRatio\":1.1}",
                "\"job_engineer_towers_g2\":{\"repeatDamageRatio\":1.1}")) {
            assertThrows(IllegalArgumentException.class, () -> AugmentConfig.fromJson(
                    JsonParser.parseString("{\"parameters\":{" + parameters + "}}").getAsJsonObject()));
        }
    }

    @Test
    void fixedStancesKeepLegacySettingsAndExcludeTheOtherStanceAfterSelection() {
        AugmentConfig config = new AugmentConfig(true, true, null,
                Map.of("engagement_plan", Map.of("quickDamageBonus", .41)), Set.of("biased_armor"));
        for (String id : List.of("engagement_plan_quick", "engagement_plan_long")) {
            assertEquals(.41, config.parameter(id, "quickDamageBonus", -1));
            assertEquals(config.parametersFor("engagement_plan"), config.parametersFor(id));
        }
        assertFalse(config.isEnabled("biased_armor_physical"));
        assertFalse(config.isEnabled("biased_armor_magic"));
        AugmentConfig oneDisabled = new AugmentConfig(true, true, null, Map.of(), Set.of("engagement_plan_quick"));
        assertFalse(oneDisabled.isEnabled("engagement_plan_quick"));
        assertTrue(oneDisabled.isEnabled("engagement_plan_long"));
        assertTrue(AugmentCatalog.find("engagement_plan").isPresent());
        assertTrue(AugmentCatalog.normalDefinitions().stream().noneMatch(card ->
                card.id().equals("semiontd:engagement_plan") || card.id().equals("semiontd:biased_armor")
                        || card.id().matches("semiontd:tactical_designation_[123]")));
        PlayerAugmentState state = state(42, SILVER);
        var offer = state.offer(5, 5, 600, card -> card.id().equals("semiontd:engagement_plan_quick"));
        int slot = offer.cardIds().indexOf("semiontd:engagement_plan_quick");
        assertTrue(slot >= 0);
        choose(state, offer, slot, AugmentChoice.none(), 25);
        assertTrue(state.hasSelected("engagement_plan"));
        assertEquals("QUICK", state.snapshot().choice("engagement_plan").mode());
        assertFalse(state.snapshot().has("engagement_plan_long"));
        assertTrue(state.offer(15, 30, 600, ALL).cardIds().stream()
                .noneMatch(id -> AugmentCatalog.effectId(id).equals("semiontd:engagement_plan")));
        assertEquals(config, AugmentConfig.fromJson(config.toJson()));
    }

    @Test
    void defaultsAreDisabledAndHaveApprovedReserveRewards() {
        AugmentConfig config = AugmentConfig.defaults();
        assertFalse(config.enabled());
        assertFalse(config.publicPoolEnabled());
        String[] rarities = {"silver", "gold", "prismatic"};
        int[] diamonds = {150, 300, 600};
        int[] income = {15, 30, 60};
        int[] production = {2, 3, 6};
        for (int tier = 0; tier < rarities.length; tier++) {
            String suffix = rarities[tier];
            assertEquals(diamonds[tier], config.parameter("reserve_diamonds_" + suffix, "amount", -1));
            assertEquals(income[tier], config.parameter("reserve_income_" + suffix, "amount", -1));
            assertEquals(production[tier], config.parameter("reserve_production_" + suffix, "amount", -1));
            assertEquals("즉시 다이아 +" + diamonds[tier] + ".",
                    AugmentCatalog.find("reserve_diamonds_" + suffix).orElseThrow().description());
            assertEquals("정기 인컴 +" + income[tier] + ".",
                    AugmentCatalog.find("reserve_income_" + suffix).orElseThrow().description());
            assertEquals("기본 에메랄드 초당 생산 +" + production[tier] + ".",
                    AugmentCatalog.find("reserve_production_" + suffix).orElseThrow().description());
        }
    }

    @Test
    void betaBuffsMatchBundledDefaultsAndPreserveExplicitOlderSettings() {
        AugmentConfig defaults = AugmentConfig.defaults();
        assertEquals(defaults.parameters(), new AugmentConfig(false, false, null, Map.of(), Set.of()).parameters());
        assertEquals(.35, defaults.parameter("tactical_designation_1", "damageBonus", -1));
        assertEquals(.65, defaults.parameter("tactical_designation_2", "damageBonus", -1));
        assertEquals(1.0, defaults.parameter("tactical_designation_3", "damageBonus", -1));
        assertEquals(.15, defaults.parameter("battlefield_mastery", "bonusPerStack", -1));
        assertEquals(450, defaults.parameter("forbidden_blueprint", "ticketValue", -1));
        assertEquals(200, defaults.parameter("ordnance_factory_call", "shellDamage", -1));
        assertEquals(.65, defaults.parameter("wartime_economy", "payoutMultiplier", -1));
        assertEquals(300, defaults.parameter("emergency_loan", "advanceCap", -1));
        AugmentConfig legacy = AugmentConfig.fromJson(JsonParser.parseString("""
                {"enabled":true,"publicPoolEnabled":true,"parameters":{
                  "tactical_designation_1":{"damageBonus":0.15,"damageReduction":0.12},
                  "reserve_diamonds_silver":{"amount":60},
                  "reserve_income_gold":{"amount":20},
                  "reserve_production_prismatic":{"amount":3},
                  "wartime_economy":{"damageBonus":0.35,"maxHealthBonus":0.20,"payoutMultiplier":0.65}}}
                """).getAsJsonObject());
        assertEquals(.15, legacy.parameter("tactical_designation_1", "damageBonus", -1));
        assertEquals(.12, legacy.parameter("tactical_designation_1", "damageReduction", -1));
        assertEquals(.35, legacy.parameter("wartime_economy", "damageBonus", -1));
        assertEquals(60, legacy.parameter("reserve_diamonds_silver", "amount", -1));
        assertEquals(20, legacy.parameter("reserve_income_gold", "amount", -1));
        assertEquals(3, legacy.parameter("reserve_production_prismatic", "amount", -1));
        assertEquals(legacy, AugmentConfig.fromJson(legacy.toJson()));
        var changed = legacy.toJson();
        changed.getAsJsonObject("parameters").getAsJsonObject("semiontd:tactical_designation_1")
                .addProperty("damageBonus", .35);
        assertNotEquals(legacy.version(), AugmentConfig.fromJson(changed).version());
        assertEquals(.15, legacy.parameter("tactical_designation_1", "damageBonus", -1));
    }

    @Test
    void configBackfillsMissingValuesAndIsDeeplyImmutable() {
        Map<String, Double> card = new HashMap<>(Map.of("damageBonus", .27));
        Map<String, Map<String, Double>> parameters = new HashMap<>(Map.of("tactical_designation_1", card));
        AugmentConfig config = new AugmentConfig(true, true, null, parameters, Set.of());
        card.put("damageBonus", .99);
        parameters.clear();
        assertEquals(.27, config.parameter("tactical_designation_1", "damageBonus", 0));
        assertEquals(.20, config.parameter("tactical_designation_1", "damageReduction", 0));
        assertThrows(UnsupportedOperationException.class, () -> config.parameters().clear());
        assertThrows(UnsupportedOperationException.class, () -> config.parameters().get("semiontd:tactical_designation_1").clear());
        assertEquals(config, AugmentConfig.fromJson(config.toJson()));
        assertEquals(config.version(), AugmentConfig.fromJson(config.toJson()).version());
        assertNotEquals(config.version(), AugmentConfig.defaults().version());
        AugmentConfig legacy = AugmentConfig.fromJson(JsonParser.parseString(
                "{\"parameters\":{\"folding_barricade_blueprint\":{\"healthPerTier\":0.8}}}").getAsJsonObject());
        assertEquals(.8, legacy.parameter("folding_barricade_blueprint", "healthPerTier", 0));
        assertEquals(.5, legacy.parameter("folding_barricade_blueprint", "powerPerTier", 0));
        assertEquals(.15, legacy.parameter("folding_barricade_blueprint", "areaPerTier", 0));
        assertEquals(15, legacy.parameter("folding_barricade_blueprint", "damagePerHitCap", 0));
        assertEquals(legacy, AugmentConfig.fromJson(legacy.toJson()));
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
                "{\"parameters\":{\"folding_barricade_blueprint\":{\"damagePerHitCap\":0}}}",
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
    void deadlineRejectsManualInputThenRandomSettlementCommitsExactlyOnce() {
        PlayerAugmentState state = state(1, SILVER);
        state.offer(5, 5, 1200, ALL);
        assertEquals(INVALID_REQUEST, state.draft(5, 0, 1, 0, AugmentChoice.none(), UUID.randomUUID(), 19, ALL).status());
        assertTrue(state.draft(5, 0, 1, 0, AugmentChoice.none(), UUID.randomUUID(), 20, ALL).successful());
        long revision = state.currentOffer().orElseThrow().draftRevision();
        assertEquals(EXPIRED, state.confirm(5, 1, revision, UUID.randomUUID(), 1200, ALL,
                (card, choice) -> fail("Expired choice must not grant.")).status());
        assertTrue(state.selections().isEmpty(), "Late input must leave resolution to the server timeout path");
        AtomicInteger grants = new AtomicInteger();
        assertTrue(state.expire(1200, ALL, card -> AugmentChoice.none(), (card, choice) -> { grants.incrementAndGet(); return true; }));
        assertEquals(PlayerAugmentState.Outcome.SELECTED, state.selections().getFirst().outcome());
        assertNull(state.selections().getFirst().skipReason());
        assertFalse(state.expire(1500, ALL, card -> AugmentChoice.none(), (card, choice) -> { grants.incrementAndGet(); return true; }));
        assertEquals(1, grants.get());
        assertEquals(1, state.selections().size());
        assertEquals("선택한 증강 1/3", AugmentService.selectionCountLabel(state));
    }

    @Test
    void timeoutFallsBackToSameRarityDiamondsWhenEveryOfferedChoiceFails() {
        for (var rarity : AugmentRarity.values()) {
            PlayerAugmentState state = state(42, rarity);
            state.offer(5, 5, 1200, ALL);
            AtomicInteger grants = new AtomicInteger();
            assertFalse(state.expire(1199, ALL, card -> AugmentChoice.none(), (card, choice) -> fail("Too early")));
            assertTrue(state.expire(1200, ALL, card -> AugmentChoice.none(), (card, choice) -> {
                if (!card.familyKey().equals("RESERVE_DIAMONDS")) {return false;}
                grants.incrementAndGet();
                return true;
            }));
            var selection = state.selections().getFirst();
            assertEquals(rarity, selection.rarity());
            assertEquals("semiontd:reserve_diamonds_" + rarity.name().toLowerCase(java.util.Locale.ROOT), selection.augmentId());
            assertEquals(1, grants.get());
        }
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
    void overheatResetsNextRoundButAllowsRepeatedPrepareTargetingAndDeduplicatesConfirm() {
        PlayerAugmentState state = state(1, GOLD);
        state.beginPrepare(5);
        state.forceOffer(5, 5, 600, List.of("overheat_core", "reserve_diamonds_gold", "reserve_income_gold"));
        AugmentChoice choice = new AugmentChoice(UUID.randomUUID(), null, "");
        choose(state, state.currentOffer().orElseThrow(), 0, choice, 20);
        assertEquals(choice, state.snapshot().choice("overheat_core"));
        state.beginPrepare(5);
        assertEquals(choice, state.snapshot().choice("overheat_core"));
        assertTrue(state.draftConfiguration(5, 5, state.configurationRevision(), choice, UUID.randomUUID(), ALL).successful());
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
        assertTrue(state.draftConfiguration(5, 6, state.configurationRevision(), choice, UUID.randomUUID(), ALL).successful());
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
    void targetToolCyclesAcquisitionOrderAndPrunesOnlyMissingLogicalTowers() {
        PlayerAugmentState state = state(1, GOLD);
        state.beginPrepare(5);
        state.forceOffer(5, 5, 600, List.of("overheat_core", "reserve_diamonds_gold", "reserve_income_gold"));
        UUID first = UUID.randomUUID();
        choose(state, state.currentOffer().orElseThrow(), 0, new AugmentChoice(first, null, ""), 20);
        assertEquals(5, state.targetToolSelection().orElseThrow().milestoneRound());
        state.beginPrepare(15);
        state.forceOffer(15, 15, 1800, List.of("frontline_specialization", "reserve_diamonds_gold", "reserve_income_gold"));
        UUID second = UUID.randomUUID();
        choose(state, state.currentOffer().orElseThrow(), 0, new AugmentChoice(first, second, ""), 620);
        assertEquals(15, state.targetToolSelection().orElseThrow().milestoneRound());
        state.cycleTargetTool();
        assertEquals(5, state.targetToolSelection().orElseThrow().milestoneRound());
        state.cycleTargetTool();
        assertEquals(15, state.targetToolSelection().orElseThrow().milestoneRound());
        assertFalse(state.clearMissingTargets(Set.of(first, second)));
        assertTrue(state.clearMissingTargets(Set.of(second)));
        assertNull(state.snapshot().choice("frontline_specialization").primaryTargetId());
        assertEquals(second, state.snapshot().choice("frontline_specialization").secondaryTargetId());
        assertEquals(15, state.targetToolSelection().orElseThrow().milestoneRound());
        assertTrue(state.clearMissingTargets(Set.of()));
        assertEquals(AugmentChoice.none(), state.snapshot().choice("frontline_specialization"));
        assertEquals(2, state.targetedSelections().size());
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
        state.expire(600, ALL, card -> AugmentChoice.none(), (card, choice) -> true);
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
