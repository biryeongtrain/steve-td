package kim.biryeong.semiontd.gametest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentCatalog;
import kim.biryeong.semiontd.augment.AugmentEconomyService;
import kim.biryeong.semiontd.augment.AugmentService;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.IncomeLaneRoutingConfig;
import kim.biryeong.semiontd.config.LeaderTargetingConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.UndeadTowerJob;
import kim.biryeong.semiontd.summon.SummonResultType;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.test.tower.TestTowerTypes;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.trait.BuiltInTraits;
import kim.biryeong.semiontd.trait.TraitLoadout;
import kim.biryeong.semiontd.trait.TraitSelectionConfig;
import kim.biryeong.semiontd.trait.TraitSelectionSnapshot;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Lifecycle/server-command checks, not client rendering or network reconnect proof. */
public final class AugmentLifecycleGameTest {
    @GameTest
    public void naturalVictorySnapshotsSurvivingPlayersForecastCancellationExactlyOnce(GameTestHelper context) {
        SemionGame game = game(context, config(true, false));
        UUID red = UUID.randomUUID();
        try {
            require(game.start(context.getLevel().getServer(), plan(red, MatchMode.NORMAL)), "Synthetic match must start.");
            enterPrepare(game, context.getLevel().getServer(), 5);
            SemionPlayer player = game.players().get(red);
            AugmentEconomyService.onSelected(player, "semiontd:forecast_offensive", 5, Map.of());
            require(AugmentEconomyService.setContract(player, 5, AugmentEconomyService.Contract.FORECAST),
                    "The surviving player must arm an actual forecast purchase.");
            player.economy().addEmerald(1_000);
            long originalIncome = player.economy().income();
            require(game.summonMonster(red, "zombie").type() == SummonResultType.SUCCESS,
                    "The real paid summon path must register the forecast reservation.");
            var pending = player.economyAugments().pendingForecasts();
            require(pending.size() == 1 && pending.getFirst().round() == 6 && pending.getFirst().amount() > 0,
                    "The fixture must contain one unpaid R6 reservation before the R5 victory.");
            long deferredIncome = pending.getFirst().amount();
            tick(game, context.getLevel().getServer(), game.remainingPrepareSeconds() * 20);
            require(game.phase() == RoundPhase.LANE_WAVE, "The fixture must reach actual combat before ending.");
            game.teams().get(TeamId.BLUE).laneGroup().boss().damage(Double.MAX_VALUE);
            game.tick(context.getLevel().getServer());
            require(game.phase() == RoundPhase.ENDED && !game.teams().get(TeamId.RED).eliminated(),
                    "The ordinary combat tick must finish with the reservation owner still alive.");
            var result = game.matchResult().orElseThrow();
            var winner = result.participants().stream().filter(participant -> participant.playerId().equals(red)).findFirst().orElseThrow();
            var events = winner.augmentTelemetry().economyEvents();
            var cancellations = events.stream().filter(event -> event.eventType().equals("FORECAST_CANCELLED")).toList();
            require(winner.winner() && cancellations.size() == 1,
                    "The first final-result snapshot must already contain exactly one surviving-player cancellation, before runtime close.");
            var cancellation = cancellations.getFirst();
            require(cancellation.incomeDeferred() == -deferredIncome && cancellation.incomeGranted() == null
                            && cancellation.round() == 5 && cancellation.tick() != null && cancellation.tick() == game.currentTick(),
                    "Cancellation must record the actual unpaid delta and end tick, without pretending to grant income.");
            require(player.economyAugments().pendingForecasts().isEmpty() && player.economy().income() == originalIncome,
                    "Natural victory must close the reservation without paying its deferred income.");
            for (int i = 0; i < 3; i++) {
                game.tick(context.getLevel().getServer());
                var repeated = game.matchResult().orElseThrow().participants().stream()
                        .filter(participant -> participant.playerId().equals(red)).findFirst().orElseThrow();
                require(repeated.augmentTelemetry().economyEvents().equals(events),
                        "Repeated ended-game ticks and result snapshots must not append another cancellation.");
            }
        } catch (RuntimeException | AssertionError failure) {
            context.fail(net.minecraft.network.chat.Component.literal("Natural-victory economy telemetry failed: " + failure));
        } finally {
            game.close();
        }
        context.succeed();
    }

    @GameTest
    public void rejectedPurchasesDoNotConsumeTheRoundRobinLaneAssignment(GameTestHelper context) {
        for (boolean invalidContract : List.of(false, true)) {
            SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(),
                    LeaderTargetingConfig.defaultConfig(), new IncomeLaneRoutingConfig(true,
                    IncomeLaneRoutingConfig.Mode.LEAST_THREAT_PRESSURE, 1, 0.75,
                    IncomeLaneRoutingConfig.TieBreakMode.ROUND_ROBIN),
                    SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO)));
            game.configureAugments(config(true, false));
            UUID red = UUID.randomUUID();
            try {
                ParticipantSelectionPlan participants = new ParticipantSelectionPlan(MatchMode.NORMAL, List.of(
                        new AssignedParticipant(red, "routing-buyer", TeamId.RED, 1),
                        new AssignedParticipant(UUID.randomUUID(), "routing-blue-one", TeamId.BLUE, 1),
                        new AssignedParticipant(UUID.randomUUID(), "routing-blue-two", TeamId.BLUE, 2)), Set.of(), 2);
                require(game.start(context.getLevel().getServer(), participants), "Synthetic routing match must start.");
                enterPrepare(game, context.getLevel().getServer(), 5);
                SemionPlayer buyer = game.players().get(red);
                buyer.economy().spendEmerald(buyer.economy().emerald());
                if (invalidContract) {buyer.economy().addEmerald(1_000);}
                var failed = invalidContract
                        ? game.summonMonster(red, "zombie", AugmentEconomyService.Contract.LOW_PRESSURE)
                        : game.summonMonster(red, "zombie");
                require(failed.type() == (invalidContract ? SummonResultType.AUGMENT_CONTRACT_UNAVAILABLE : SummonResultType.NOT_ENOUGH_GAS),
                        "The fixture must reject the unfunded or unauthorized purchase before routing.");
                require(game.teams().get(TeamId.BLUE).laneGroup().lanes().stream().allMatch(lane -> lane.queuedSummonCount() == 0),
                        "A failed purchase must not enqueue a unit.");
                buyer.economy().addEmerald(1_000);
                var success = game.summonMonster(red, "zombie");
                require(success.type() == SummonResultType.SUCCESS && success.targetLaneId().orElseThrow() == 1,
                        "The first successful purchase must still use round-robin lane one; failed purchases must not advance its cursor.");
            } finally {
                game.close();
            }
        }
        context.succeed();
    }

    @GameTest
    public void onlyEnabledNormalMatchesInitializeAugmentsAndReplaceTraits(GameTestHelper context) {
        for (int scenario = 0; scenario < 5; scenario++) {
            boolean enabled = scenario != 4;
            boolean expected = scenario == 0;
            SemionGame game = game(context, config(enabled, false));
            try {
                if (scenario == 2) {game.enableSandboxMode();}
                if (scenario == 3) {game.enableTutorialMode();}
                UUID red = UUID.randomUUID();
                TraitLoadout loadout = new TraitLoadout(BuiltInTraits.MOBILIZATION_GRANT_ID, BuiltInTraits.NONE_ID);
                require(game.start(context.getLevel().getServer(), plan(red, scenario == 1 ? MatchMode.TEST : MatchMode.NORMAL),
                        new TraitSelectionSnapshot(Map.of(red, loadout))), "Synthetic match must start.");
                SemionPlayer player = game.players().get(red);
                require(game.augmentsEnabled() == expected, "Augments must be restricted to enabled NORMAL matches.");
                require(player.augments().initialized() == expected, "Inactive modes must not initialize a hidden augment schedule.");
                require(player.traitLoadout().equals(expected ? TraitLoadout.none() : loadout), "Only season 3 NORMAL replaces the old traits.");
                require(game.augmentRarities().size() == (expected ? 3 : 0), "Only enabled NORMAL draws three match-wide rarities.");
                AugmentConfig frozen = game.augmentConfig();
                game.configureAugments(config(!enabled, true));
                require(game.augmentConfig() == frozen, "A running match must retain its original immutable augment settings.");
            } finally {
                game.close();
            }
        }
        context.succeed();
    }

    @GameTest
    public void managerRejectsTraitAugmentConflictBeforeOpeningEitherSelection(GameTestHelper context) {
        SemionGame game = game(context, config(true, false));
        SemionGameManager manager = manager(game);
        try {
            manager.configureAugments(config(true, false));
            manager.configureTraits(new TraitSelectionConfig(true, 45));
            var result = manager.scheduleStart(context.getLevel().getServer(), plan(UUID.randomUUID(), MatchMode.NORMAL));
            require(result == SemionGameManager.StartCountdownResult.AUGMENT_TRAIT_CONFLICT,
                    "Enabling both traits and augments must reject the start, not silently choose one.");
            require(!manager.traitSelectionActive() && !manager.startCountdownActive() && !game.rosterLocked(),
                    "A rejected start must not mutate selection/countdown/match state.");
            manager.configureTraits(new TraitSelectionConfig(false, 45));
            require(manager.scheduleStart(context.getLevel().getServer(), plan(UUID.randomUUID(), MatchMode.NORMAL))
                            == SemionGameManager.StartCountdownResult.SCHEDULED,
                    "Disabling traits must permit the augment match countdown.");
            require(!manager.traitSelectionActive() && manager.startCountdownActive(), "Season 3 must skip the trait selection dialog.");
        } finally {
            manager.shutdown();
        }
        context.succeed();
    }

    @GameTest
    public void waveReloadOnlyChangesMatchesWhoseRosterHasNotBeenLocked(GameTestHelper context) {
        WaveConfig baseline = WaveConfig.defaultConfig().withSeason3Stages(false, false, Set.of());
        WaveConfig reloaded = baseline.withSeason3Stages(true, true, Set.of());
        for (boolean reloadBeforeStart : List.of(false, true)) {
            SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), baseline,
                    SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO)));
            game.configureAugments(config(true, false));
            UUID red = UUID.randomUUID();
            try {
                if (reloadBeforeStart) {game.applyConfigs(EconomyConfig.defaultConfig(), reloaded);}
                require(game.start(context.getLevel().getServer(), plan(red, MatchMode.NORMAL)), "Synthetic match must start.");
                if (!reloadBeforeStart) {game.applyConfigs(EconomyConfig.defaultConfig(), reloaded);}
                enterPrepare(game, context.getLevel().getServer(), 16);
                var expected = (reloadBeforeStart ? reloaded : baseline).configForRound(16).orElseThrow().entriesForLane("lane_1");
                var actual = game.upcomingWaveEntries(red);
                require(actual.equals(expected), "Wave reload must preserve the running match composition and apply only before the next roster lock.");
                require(actual.stream().anyMatch(entry -> entry.healing() != null) == reloadBeforeStart,
                        "The R16 healer must appear only in the match started with the reloaded stage enabled.");
            } finally {
                game.close();
            }
        }
        context.succeed();
    }

    @GameTest
    public void eachMilestoneAddsThirtyProductionFreeSecondsBeforeOrdinaryPreparation(GameTestHelper context) {
        SemionGame game = game(context, config(true, true));
        UUID red = UUID.randomUUID();
        try {
            require(game.start(context.getLevel().getServer(), plan(red, MatchMode.NORMAL)), "Synthetic match must start.");
            require(game.remainingPrepareSeconds() == SemionGame.DEFAULT_PREPARE_TICKS / 20, "R1 uses the ordinary preparation duration.");
            require(!game.isAugmentSelectionActive() && game.remainingAugmentSelectionSeconds() == 0,
                    "Ordinary R1 preparation must not pause production.");
            for (SemionPlayer player : game.players().values()) {player.economy().spendEmerald(player.economy().emerald());}
            tick(game, context.getLevel().getServer(), 20);
            for (SemionPlayer player : game.players().values()) {
                require(player.economy().emerald() == player.economy().emeraldPerSec(), "R1 produces emeralds normally.");
            }
            game.players().get(red).economy().addAugmentEmeraldProduction(1);
            int resolved = 0;
            for (int milestone : List.of(5, 15, 25)) {
                enterPrepare(game, context.getLevel().getServer(), milestone);
                PlayerAugmentState state = game.players().get(red).augments();
                var offer = state.currentOffer().orElseThrow();
                require(offer.milestoneRound() == milestone && offer.offeredRound() == milestone, "The correct milestone must open.");
                require(offer.deadlineTickExclusive() == game.currentTick() + AugmentService.PREPARE_TICKS,
                        "The offer ends after the separate 30-second choice period.");
                require(game.remainingPrepareSeconds() == 55 && game.remainingAugmentSelectionSeconds() == 30
                                && game.isAugmentSelectionActive(),
                        "Each milestone adds 30 seconds before the unchanged 25-second preparation.");
                for (SemionPlayer player : game.players().values()) {
                    require(player.augments().currentOffer().orElseThrow().rarity() == offer.rarity(), "Players share the milestone rarity.");
                    player.economy().spendEmerald(player.economy().emerald());
                }
                tick(game, context.getLevel().getServer(), 20);
                for (SemionPlayer player : game.players().values()) {
                    if (player.uuid().equals(red)) {continue;}
                    var otherOffer = player.augments().currentOffer().orElseThrow();
                    require(player.augments().skip(milestone, otherOffer.revision(), UUID.randomUUID(), game.currentTick(),
                            PlayerAugmentState.SkipReason.EXPLICIT).successful(), "The other player must skip early.");
                }
                tick(game, context.getLevel().getServer(), AugmentService.PREPARE_TICKS - 21);
                require(game.phase() == RoundPhase.PREPARE_AND_SUMMON && state.currentOffer().isPresent(),
                        "The offer remains unresolved one tick before the deadline.");
                require(game.isAugmentSelectionActive(), "An early skip must not shorten the shared production pause.");
                tick(game, context.getLevel().getServer(), 1);
                require(game.phase() == RoundPhase.PREPARE_AND_SUMMON && state.currentOffer().isEmpty()
                                && !game.isAugmentSelectionActive() && game.remainingAugmentSelectionSeconds() == 0
                                && game.remainingPrepareSeconds() == 25,
                        "The choice deadline closes offers, then leaves the entire ordinary preparation intact.");
                for (SemionPlayer player : game.players().values()) {
                    require(player.economy().emerald() == 0,
                            "All players, including early skips and production-bonus owners, receive no emeralds during the 600 ticks.");
                }
                game.augmentService().expire(game);
                state.expire(game.currentTick());
                require(state.selections().size() == ++resolved, "Repeated expiry must not add another decision or reward.");
                var selection = state.selections().getLast();
                require(selection.milestoneRound() == milestone && selection.outcome() == PlayerAugmentState.Outcome.SKIPPED
                                && selection.skipReason() == PlayerAugmentState.SkipReason.TIMEOUT,
                        "Unchosen cards must be recorded as a timeout, never automatically selected.");
                tick(game, context.getLevel().getServer(), 20);
                long multiplier = game.economyConfig().emeraldIncomeMultiplierForRound(milestone);
                for (SemionPlayer player : game.players().values()) {
                    require(player.economy().emerald() == player.economy().emeraldPerSec() * multiplier,
                            "Production resumes once per second, including augment bonuses and R25 scaling, without catch-up grants.");
                }
                tick(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS - 21);
                require(game.phase() == RoundPhase.PREPARE_AND_SUMMON, "Combat must not start one tick before the ordinary deadline.");
                tick(game, context.getLevel().getServer(), 1);
                require(game.phase() == RoundPhase.LANE_WAVE, "Combat starts only after all 55 seconds.");
                enterPrepare(game, context.getLevel().getServer(), milestone + 1);
                require(game.remainingPrepareSeconds() == 25 && !game.isAugmentSelectionActive(),
                        "The following ordinary round must not inherit the augment pause.");
                for (SemionPlayer player : game.players().values()) {player.economy().spendEmerald(player.economy().emerald());}
                tick(game, context.getLevel().getServer(), 20);
                for (SemionPlayer player : game.players().values()) {
                    require(player.economy().emerald() == player.economy().emeraldPerSec()
                                    * game.economyConfig().emeraldIncomeMultiplierForRound(milestone + 1),
                            "Ordinary preparation production remains unchanged after an augment round.");
                }
            }
        } finally {
            game.close();
        }
        context.succeed();
    }

    @GameTest
    public void revealLockReconnectAndConfirmedSnapshotUseTheActualLifecycle(GameTestHelper context) {
        ServerPlayer online = context.makeMockServerPlayerInLevel();
        SemionGame game = game(context, config(true, false));
        SemionGameManager manager = manager(game);
        try {
            require(game.start(context.getLevel().getServer(), plan(online.getUUID(), MatchMode.NORMAL)), "Synthetic match must start.");
            enterPrepare(game, context.getLevel().getServer(), 5);
            require(game.augmentService().handle(game, online,
                    "force 5 reserve_diamonds_silver reserve_income_silver reserve_production_silver", true) == 1,
                    "An authorized internal offer must add a 30-second choice period to ordinary preparation.");
            SemionPlayer player = game.players().get(online.getUUID());
            PlayerAugmentState state = player.augments();
            var offer = state.currentOffer().orElseThrow();
            long emeralds = player.economy().emerald();
            require(game.remainingPrepareSeconds() == 55 && game.remainingAugmentSelectionSeconds() == 30
                            && offer.inputAllowedTick() == game.currentTick() + 20,
                    "The force-offer path must preserve the reveal delay and the separate choice period.");
            require(draft(game, online, offer.revision(), 1) == 0, "Input is locked on the reveal tick.");
            tick(game, context.getLevel().getServer(), 19);
            require(draft(game, online, offer.revision(), 1) == 0 && state.currentOffer().orElseThrow().draft() == null,
                    "A click at reveal+19 must not write a draft.");
            tick(game, context.getLevel().getServer(), 1);
            require(draft(game, online, offer.revision(), 1) == 1, "Input unlocks at reveal+20.");
            var savedDraft = state.currentOffer().orElseThrow();
            manager.handlePlayerDisconnect(online);
            require(game.restorePlayerPlacement(context.getLevel().getServer(), online), "The same-process participant must restore.");
            game.augmentService().reopen(game, online);
            require(game.players().get(online.getUUID()) == player && player.augments() == state,
                    "Reconnect must keep the original player/augment state object.");
            require(state.currentOffer().orElseThrow().equals(savedDraft), "Reconnect must preserve candidates, revisions, deadline and draft.");
            require(game.isAugmentSelectionActive() && game.remainingAugmentSelectionSeconds() == 29,
                    "Reconnect must neither reset nor shorten the shared choice period.");

            PlayerLane lane = game.playerLane(player.uuid()).orElseThrow();
            boolean[] sawSnapshotAtWaveStart = {false};
            TestTower probe = new TestTower(TestTowerTypes.TEST_DIRECT, player.uuid(), player.teamId(), player.laneId(),
                    lane.laneLayout().finalDefenseTowerSlots().getFirst()) {
                @Override
                public void onWaveStarted(PlayerLane currentLane, int round) {
                    sawSnapshotAtWaveStart[0] = round == 5 && augmentSnapshot().has("reserve_income_silver");
                    super.onWaveStarted(currentLane, round);
                }
            };
            lane.addTower(probe);
            long income = player.economy().income();
            String confirm = sessionCommand(game, "confirm " + savedDraft.revision() + " " + savedDraft.draftRevision() + " " + UUID.randomUUID());
            require(game.augmentService().handle(game, online, confirm, false) == 1, "Confirmation must apply the selected reserve.");
            require(player.economy().income() == income + 10, "The reserve income applies once before combat.");
            game.augmentService().handle(game, online, confirm, false);
            require(player.economy().income() == income + 10 && state.selections().size() == 1, "A duplicate confirmation must not pay twice.");
            lane.assignAugmentSnapshot(AugmentSnapshot.none());
            require(!probe.augmentSnapshot().has("reserve_income_silver"), "The fixture starts combat with a stale lane snapshot.");
            require(game.isAugmentSelectionActive(), "Early confirmation must not release the shared production pause.");
            tick(game, context.getLevel().getServer(), AugmentService.PREPARE_TICKS - 20);
            require(!game.isAugmentSelectionActive() && game.phase() == RoundPhase.PREPARE_AND_SUMMON
                            && game.remainingPrepareSeconds() == 25 && player.economy().emerald() == emeralds,
                    "Early confirmation and reconnect still preserve all 30 production-free seconds plus ordinary preparation.");
            tick(game, context.getLevel().getServer(), 20);
            require(player.economy().emerald() == emeralds + player.economy().emeraldPerSec(),
                    "Confirmed players resume ordinary production without receiving the skipped 30 seconds.");
            tick(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS - 20);
            require(game.phase() == RoundPhase.LANE_WAVE && sawSnapshotAtWaveStart[0],
                    "The confirmed immutable snapshot must reach towers before onWaveStarted.");
        } catch (RuntimeException | AssertionError failure) {
            context.fail(net.minecraft.network.chat.Component.literal("Augment lifecycle failed: " + failure));
        } finally {
            manager.shutdown();
        }
        context.succeed();
    }

    @GameTest
    public void onlyRoundFiveLateJoinReservationsReceiveDeferredRoundFiveOffer(GameTestHelper context) {
        SemionGame game = game(context, config(true, true));
        try {
            require(game.start(context.getLevel().getServer(), plan(UUID.randomUUID(), MatchMode.NORMAL)), "Synthetic match must start.");
            enterPrepare(game, context.getLevel().getServer(), 5);
            tick(game, context.getLevel().getServer(), AugmentService.PREPARE_TICKS + SemionGame.DEFAULT_PREPARE_TICKS);
            ServerPlayer reservedAtFive = context.makeMockServerPlayerInLevel();
            ServerPlayer reservedAtFour = context.makeMockServerPlayerInLevel();
            addLate(game, context, reservedAtFive, 2, 5);
            addLate(game, context, reservedAtFour, 3, 4);
            SemionPlayer lateFive = game.players().get(reservedAtFive.getUUID());
            SemionPlayer lateFour = game.players().get(reservedAtFour.getUUID());
            require(lateFive.augments().initialized() && lateFive.augments().raritySchedule().equals(game.augmentRarities()),
                    "Late participants must use the existing match rarity schedule.");
            require(lateFive.augments().currentOffer().isEmpty() && lateFour.augments().currentOffer().isEmpty(),
                    "Joining during combat must not open or resolve a card immediately.");
            enterPrepare(game, context.getLevel().getServer(), 6);
            var offer = lateFive.augments().currentOffer().orElseThrow();
            require(offer.milestoneRound() == 5 && offer.offeredRound() == 6,
                    "A request recorded at R5 receives its R5 choice in the next preparation.");
            require(game.remainingPrepareSeconds() == 55 && game.remainingAugmentSelectionSeconds() == 30
                            && game.isAugmentSelectionActive()
                            && offer.deadlineTickExclusive() == game.currentTick() + AugmentService.PREPARE_TICKS,
                    "A deferred offer must also extend the actual next preparation.");
            require(lateFour.augments().currentOffer().isEmpty(), "The special deferred R5 rule must not use requestedRound<=4.");
        } finally {
            game.close();
        }
        context.succeed();
    }

    @GameTest
    public void deferredRoundFiveBlueprintOfferUsesCurrentUpgradeEligibilityInRoundSix(GameTestHelper context) {
        Map<String, Integer> weights = new LinkedHashMap<>();
        AugmentConfig.defaults().rarityWeights().keySet().forEach(key -> weights.put(key, key.equals("PPP") ? 100 : 0));
        Set<String> disabled = AugmentCatalog.definitions().stream()
                .filter(card -> !card.reserve() && !card.id().equals("semiontd:forbidden_blueprint"))
                .map(card -> card.id()).collect(java.util.stream.Collectors.toSet());
        SemionGame game = game(context, new AugmentConfig(true, true, weights, Map.of(), disabled));
        try {
            require(game.start(context.getLevel().getServer(), plan(UUID.randomUUID(), MatchMode.NORMAL)), "Synthetic match must start.");
            enterPrepare(game, context.getLevel().getServer(), 5);
            tick(game, context.getLevel().getServer(), AugmentService.PREPARE_TICKS + SemionGame.DEFAULT_PREPARE_TICKS);
            ServerPlayer late = context.makeMockServerPlayerInLevel();
            require(game.addLateParticipant(context.getLevel().getServer(), late,
                    new AssignedParticipant(late.getUUID(), late.getGameProfile().getName(), TeamId.RED, 2),
                    TraitLoadout.none(), JobRegistry.find(UndeadTowerJob.ID).orElseThrow(), 5), "Late R5 request must activate.");
            SemionPlayer player = game.players().get(late.getUUID());
            PlayerLane lane = game.playerLane(player.uuid()).orElseThrow();
            player.economy().addDiamond(1_000);
            // Establish the eligible board before the deferred offer is generated; placement itself is tested separately.
            var tower = ProductionTowerCatalog.find("t1_skeleton_tower").orElseThrow().create(player.uuid(), player.teamId(),
                    player.laneId(), lane.laneLayout().finalDefenseTowerSlots().getFirst());
            lane.addTower(tower);
            enterPrepare(game, context.getLevel().getServer(), 6);
            var offer = player.augments().currentOffer().orElseThrow();
            int slot = offer.cardIds().indexOf("semiontd:forbidden_blueprint");
            require(offer.milestoneRound() == 5 && offer.offeredRound() == 6 && slot >= 0,
                    "An eligible deferred R5 blueprint must not be rejected merely because its offer is shown in R6.");
            tick(game, context.getLevel().getServer(), 20);
            require(draft(game, late, offer.revision(), slot) == 1, "The deferred blueprint must pass the real draft eligibility check.");
            var drafted = player.augments().currentOffer().orElseThrow();
            require(game.augmentService().handle(game, late, sessionCommand(game, "confirm " + drafted.revision() + " "
                    + drafted.draftRevision() + " " + UUID.randomUUID()), false) == 1,
                    "The deferred blueprint must remain eligible at confirmation.");
            require(player.economyAugments().remainingTickets() == 2, "The confirmed R6 offer grants two current-preparation tickets.");
            var upgrade = ProductionTowerCatalog.upgrades(tower.type()).getFirst();
            require(ProductionTowerService.upgradeTower(game, player.uuid(), tower.position(), upgrade.id(), true) == TowerUpgradeResult.SUCCESS,
                    "The deferred ticket must work on an actually eligible R6 upgrade.");
            require(player.economyAugments().usedTickets() == 1 && player.economyAugments().remainingTickets() == 1,
                    "Only the successful upgrade consumes a ticket.");
            enterPrepare(game, context.getLevel().getServer(), 7);
            require(player.economyAugments().remainingTickets() == 0 && player.economyAugments().usedTickets() == 1,
                    "An unused deferred ticket expires after R6 while its applied payout penalty persists.");
        } finally {
            game.close();
        }
        context.succeed();
    }

    private static void addLate(SemionGame game, GameTestHelper context, ServerPlayer player, int lane, int requestedRound) {
        require(game.addLateParticipant(context.getLevel().getServer(), player,
                new AssignedParticipant(player.getUUID(), player.getGameProfile().getName(), TeamId.RED, lane),
                TraitLoadout.none(), JobRegistry.defaultJob(), requestedRound), "Late participant activation must succeed.");
    }

    private static int draft(SemionGame game, ServerPlayer online, long revision, int slot) {
        return game.augmentService().handle(game, online,
                sessionCommand(game, "draft " + revision + " " + slot + " " + UUID.randomUUID()), false);
    }

    private static String sessionCommand(SemionGame game, String command) {
        try {
            var field = game.augmentService().getClass().getDeclaredField("sessionToken");
            field.setAccessible(true);
            return "session " + field.get(game.augmentService()) + " " + command;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot read the match-scoped augment button token.", exception);
        }
    }

    private static AugmentConfig config(boolean enabled, boolean publicPool) {
        Map<String, Integer> weights = new LinkedHashMap<>();
        AugmentConfig.defaults().rarityWeights().keySet().forEach(key -> weights.put(key, key.equals("SSS") ? 100 : 0));
        return new AugmentConfig(enabled, publicPool, weights, Map.of(), Set.of());
    }

    private static SemionGame game(GameTestHelper context, AugmentConfig config) {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO)));
        game.configureAugments(config);
        return game;
    }

    private static ParticipantSelectionPlan plan(UUID red, MatchMode mode) {
        return new ParticipantSelectionPlan(mode, List.of(
                new AssignedParticipant(red, "augment-red", TeamId.RED, 1),
                new AssignedParticipant(UUID.randomUUID(), "augment-blue", TeamId.BLUE, 1)), Set.of(), 2);
    }

    private static SemionGameManager manager(SemionGame game) {
        SemionGameManager manager = new SemionGameManager();
        setField(manager, "activeGame", game);
        return manager;
    }

    private static void enterPrepare(SemionGame game, MinecraftServer server, int round) {
        // Skip unrelated combat, but exercise the real payout -> prepare lifecycle and its hooks.
        for (var team : game.teams().values()) {team.laneGroup().disableMonsters();}
        setField(game, "currentRound", round - 1);
        setField(game, "phase", RoundPhase.ROUND_PAYOUT);
        game.tick(server);
        require(game.currentRound() == round && game.phase() == RoundPhase.PREPARE_AND_SUMMON, "Payout must enter the requested preparation.");
    }

    private static void tick(SemionGame game, MinecraftServer server, int ticks) {
        for (int i = 0; i < ticks; i++) {game.tick(server);}
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot prepare lifecycle fixture: " + name, exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {throw new AssertionError(message);}
    }
}
