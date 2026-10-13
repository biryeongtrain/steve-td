package kim.biryeong.semiontd.augment;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.undead.UndeadTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class AugmentControllerGameTest extends AugmentControllerFixture {
    @GameTest
    public void closeReopenAndReconnectPreserveRerolledOfferWithoutSkippingOrResetting(GameTestHelper context) {
        var online = context.makeMockServerPlayerInLevel();
        var game = prepare(context, online, "GGG");
        var originalConnection = online.connection;
        try {
            force(game, online, "beneficial_effect_2 reserve_income_gold reserve_production_gold");
            advance(game, online, 20);
            var player = game.players().get(online.getUUID());
            var state = player.augments();
            var before = state.currentOffer().orElseThrow();
            String reroll = "reroll " + before.revision() + " " + UUID.randomUUID();
            require(handle(game, online, reroll) == 1, "Fixture must spend one reroll before close.");
            var kept = state.currentOffer().orElseThrow();
            var events = List.copyOf(state.offerEvents());
            var packets = new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();
            online.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(online.level().getServer(),
                    new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), online,
                    net.minecraft.server.network.CommonListenerCookie.createInitial(online.getGameProfile(), false)) {
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { packets.add(packet); }
            };
            var screen = game.augmentService().offerScreen(game, player, kept, true);
            require(screen.buttons().getLast().label().equals("닫기"), "The action must describe closing, not skipping.");
            require(game.augmentService().handle(game, online,
                    screen.buttons().getLast().command().substring("/semiontd augment ".length()), false) == 1,
                    "The actual navigation command must close successfully.");
            require(packets.stream().anyMatch(net.minecraft.network.protocol.common.ClientboundClearDialogPacket.class::isInstance),
                    "Closing must send the native clear-dialog packet.");
            for (String route : List.of("ui current", "ui close", "ui skip", "ui current")) {
                require(handle(game, online, route) == 1, "Current and legacy close views remain navigable.");
            }
            require(game.restorePlayerPlacement(online.level().getServer(), online), "Reconnect must restore the existing participant.");
            game.augmentService().reopen(game, online);
            require(state.currentOffer().orElseThrow().equals(kept) && state.offerEvents().equals(events)
                            && state.rerollsRemainingBySlot().equals(List.of(4, 4, 4)) && state.selections().isEmpty(),
                    "Close, command reopen and reconnect preserve cards, deadline, revision, draft and shared rerolls.");
            require(handle(game, online, reroll) == 0, "Closing cannot revive a previously consumed reroll nonce.");
            require(handle(game, online, "draft " + before.revision() + " 0 " + before.cardIds().getFirst() + " " + UUID.randomUUID()) == 0,
                    "Closing cannot revive a replaced card.");
            require(handle(game, online, "skip " + kept.revision() + " " + UUID.randomUUID()) == 0,
                    "An old consuming skip command must no longer discard an offer.");
            require(state.currentOffer().orElseThrow().equals(kept) && state.selections().isEmpty(), "Rejected old commands preserve the offer.");
            String select = "draft " + kept.revision() + " 2 " + kept.cardIds().get(2) + " " + UUID.randomUUID();
            require(handle(game, online, select) == 1, "A still-current card remains selectable after reopening.");
            handle(game, online, "ui close");
            handle(game, online, "ui current");
            require(handle(game, online, select) == 0 && state.selections().size() == 1 && state.currentOffer().isEmpty(),
                    "Closing and reopening after selection cannot recreate or duplicate an augment.");
            var config = game.augmentConfig();
            setField(game, "augmentConfig", new AugmentConfig(config.enabled(), true, config.rarityWeights(), config.parameters(), config.disabledIds()));
            setField(game, "currentRound", 14);
            setField(game, "phase", RoundPhase.ROUND_PAYOUT);
            game.tick(online.level().getServer());
            var next = state.currentOffer().orElseThrow();
            require(next.milestoneRound() == 15 && state.rerollsRemainingBySlot().equals(List.of(4, 4, 4)),
                    "A later milestone preserves the budgets rather than reopening the old offer.");
            require(handle(game, online, select) == 0 && state.currentOffer().orElseThrow().equals(next),
                    "An old selection callback cannot alter the new round offer.");
        } finally { online.connection = originalConnection; game.close(); }
        context.succeed();
    }

    @GameTest
    public void closeDoesNotPauseExpirationAndReopenCannotGrantTwice(GameTestHelper context) {
        var online = context.makeMockServerPlayerInLevel();
        var game = prepare(context, online);
        try {
            force(game, online, "beneficial_effect_1 reserve_income_silver reserve_production_silver");
            advance(game, online, 20);
            var state = game.players().get(online.getUUID()).augments();
            var offer = state.currentOffer().orElseThrow();
            handle(game, online, "ui close");
            setField(game, "tickCounter", offer.deadlineTickExclusive());
            require(handle(game, online, "ui current") == 1, "The reopen command remains available at the deadline.");
            require(state.currentOffer().isEmpty() && state.selections().size() == 1
                            && state.selections().getFirst().outcome() == PlayerAugmentState.Outcome.SELECTED,
                    "Reopen at expiration must use the existing random-selection rule, not skip or extend time.");
            var selections = List.copyOf(state.selections());
            game.augmentService().reopen(game, online);
            handle(game, online, "ui close");
            handle(game, online, "ui current");
            require(state.selections().equals(selections) && state.rerollsRemainingBySlot().equals(List.of(5, 5, 5)),
                    "Repeated reopen after expiry neither grants again nor spends or replenishes rerolls.");
        } finally { game.close(); }
        context.succeed();
    }

    @GameTest
    public void beneficialCardsAreAcquiredInOneClickWithoutATargetAndSurviveReopen(GameTestHelper context) {
        for (int tier = 1; tier <= 3; tier++) {
            String id = "beneficial_effect_" + tier;
            String schedule = tier == 1 ? "SSS" : tier == 2 ? "GGG" : "PPP";
            String rarity = tier == 1 ? "silver" : tier == 2 ? "gold" : "prismatic";
            ServerPlayer online = context.makeMockServerPlayerInLevel();
            SemionGame game = prepare(context, online, schedule);
            try {
                Tower tower = addTarget(game, online);
                double health = tower.currentMaxHealth();
                force(game, online, id + " reserve_income_" + rarity + " reserve_production_" + rarity);
                advance(game, online, 20);
                var player = game.players().get(online.getUUID());
                String command = "draft " + player.augments().currentOffer().orElseThrow().revision() + " 0 " + UUID.randomUUID();
                require(handle(game, online, command) == 1, "The new card must be granted in one click.");
                handle(game, online, command);
                require(player.augments().selections().size() == 1 && toolCount(online) == 0,
                        "A passive stat card must not issue a target tool or duplicate grant.");
                require(Math.abs(tower.currentMaxHealth() - health * (1 + tier / 20.0)) < .00001,
                        "Selection must immediately reach the placed tower.");
                game.augmentService().reopen(game, online);
                require(Math.abs(AugmentCombat.damageBonus(tower, null) - tier / 20.0) < .00001,
                        "Reopening must retain the passive effect without stacking it again.");
            } finally {game.close();}
        }
        context.succeed();
    }

    @GameTest
    public void specialBuilderBodiesAcceptDesignationsAndApplyActualCover(GameTestHelper context) {
        warmPlayerSpawn(context);
        context.runAfterDelay(10, () -> checkSpecialBuilderDesignations(context));
    }

    private static void checkSpecialBuilderDesignations(GameTestHelper context) {
        for (var type : List.of(kim.biryeong.semiontd.tower.warlock.WarlockTowers.BASE_WARLOCK_TOWER,
                kim.biryeong.semiontd.tower.end.EndTowers.BASE_END_TOWER,
                kim.biryeong.semiontd.tower.hero.HeroPartyTowers.HERO,
                kim.biryeong.semiontd.tower.queen.QueenTowers.QUEEN,
                kim.biryeong.semiontd.tower.queen.QueenTowers.RANDOM_CARD_SOLDIER,
                kim.biryeong.semiontd.tower.queen.QueenTowers.JOKER)) {
            ServerPlayer online = context.makeMockServerPlayerInLevel();
            SemionGame game = prepare(context, online);
            try {
                var player = game.players().get(online.getUUID());
                player.assignJob(type == kim.biryeong.semiontd.tower.end.EndTowers.BASE_END_TOWER
                        ? new kim.biryeong.semiontd.job.EndTowerJob()
                        : type == kim.biryeong.semiontd.tower.hero.HeroPartyTowers.HERO
                        ? new kim.biryeong.semiontd.job.HeroPartyTowerJob()
                        : kim.biryeong.semiontd.tower.queen.QueenTowers.all().contains(type)
                        ? new kim.biryeong.semiontd.job.QueenTowerJob() : new kim.biryeong.semiontd.job.WarlockTowerJob());
                var lane = game.playerLane(online.getUUID()).orElseThrow();
                Tower tower = ProductionTowerCatalog.entry(type).orElseThrow().create(online.getUUID(), TeamId.RED, 1,
                        lane.laneLayout().finalDefenseTowerSlots().getFirst());
                lane.addTower(tower);
                for (var card : AugmentCatalog.normalDefinitions().stream()
                        .filter(card -> card.requiredJobId() == null && AugmentService.targetCount(card.id()) > 0).toList()) {
                    require(game.augmentService().eligibleTargets(game, player, card.id()).contains(tower),
                            type.id() + " must allow " + card.id());
                }
                var entity = ((EntityBackedTower) tower).runtimeEntity(lane).orElseThrow();
                double health = tower.health();
                entity.hurt(entity.damageSources().generic(), 10);
                double baseline = health - tower.health();
                require(baseline > 0, "The unaugmented body must receive damage.");
                entity.damageCooldownTime = 0;
                force(game, online, "tactical_designation_1_cover reserve_income_silver reserve_production_silver");
                advance(game, online, 20);
                handle(game, online, "draft " + player.augments().currentOffer().orElseThrow().revision() + " 0 " + UUID.randomUUID());
                holdTargetTool(online);
                var manager = new kim.biryeong.semiontd.game.SemionGameManager();
                setField(manager, "activeGame", game);
                kim.biryeong.semiontd.ui.SemionTowerInteractionService.handleUse(manager, online, online.level(),
                        net.minecraft.world.InteractionHand.MAIN_HAND, entity, new net.minecraft.world.phys.EntityHitResult(entity));
                require(tower.logicalId().equals(player.augments().snapshot().choice("tactical_designation_1").primaryTargetId()),
                        "Physical clicks must commit a target, including an unhatched End egg.");
                lane.markWaveStarted(5);
                health = tower.health();
                entity.hurt(entity.damageSources().generic(), 10);
                double coverMultiplier = kim.biryeong.semiontd.tower.queen.QueenTowers.all().contains(type) ? .8 : .96;
                require(Math.abs(health - tower.health() - baseline * coverMultiplier) < .0001,
                        type.id() + " must apply cover, keeping hypercarry efficiency at 20%.");
                game.augmentService().reopen(game, online);
                require(tower.logicalId().equals(player.augments().snapshot().choice("tactical_designation_1").primaryTargetId()),
                        "Reopening cannot discard a special-body designation.");
            } finally {game.close();}
        }
        context.succeed();
    }

    @GameTest
    public void sharedRerollRejectsLegacyCommandsAndPreservesBudgetOnReconnectAndNextRound(GameTestHelper context) {
        ServerPlayer online = context.makeMockServerPlayerInLevel();
        SemionGame game = prepare(context, online, "GGG");
        try {
            force(game, online, "tactical_designation_2_assault frontline_specialization battlefield_mastery");
            advance(game, online, 20);
            var player = game.players().get(online.getUUID());
            var state = player.augments();
            var before = state.currentOffer().orElseThrow();
            UUID request = UUID.randomUUID();
            String command = "reroll " + before.revision() + " " + request;
            require(game.augmentService().handle(game, online, command, false) == 0, "Reroll requires the match session token.");
            require(handle(game, online, "reroll " + before.revision() + " 0 " + before.cardIds().getFirst() + " " + UUID.randomUUID()) == 0,
                    "The legacy per-slot reroll command must be rejected.");
            require(handle(game, online, "reroll " + before.revision() + " 0 " + before.cardIds().get(1) + " " + UUID.randomUUID()) == 0,
                    "A callback naming another card in the slot must be rejected.");
            require(handle(game, online, command) == 1, "One reroll replaces all three cards.");
            var after = state.currentOffer().orElseThrow();
            require(java.util.Collections.disjoint(before.cardIds(), after.cardIds()),
                    "All three previous cards must be replaced.");
            require(state.rerollsRemainingBySlot().equals(List.of(4, 4, 4)) && AugmentService.offerHeader(state).contains("4/5"),
                    "The server and visible header must show the shared remaining budget.");
            require(handle(game, online, command) == 0, "A repeated callback must be rejected without another charge.");
            require(handle(game, online, "reroll " + before.revision() + " " + UUID.randomUUID()) == 0,
                    "An old revision cannot reroll the new three-card offer.");
            require(handle(game, online, "reroll " + after.revision() + " " + request) == 0,
                    "A request ID cannot be reused for another revision.");
            require(handle(game, online, "ui reroll") == 1, "The shared reroll dialog must render.");
            require(game.restorePlayerPlacement(online.level().getServer(), online), "The real reconnect placement path must find the participant.");
            game.augmentService().reopen(game, online);
            require(game.players().get(online.getUUID()) == player && player.augments() == state
                            && state.currentOffer().orElseThrow().equals(after) && state.rerollsRemainingBySlot().equals(List.of(4, 4, 4)),
                    "Reconnect must preserve the same match state, offer and the shared balance.");
            require(state.offerEvents().stream().filter(event -> event.eventType().equals("REROLLED")).count() == 1,
                    "Only one successful replacement is recorded.");
            var event = player.augmentTelemetry().snapshot().guiEvents().stream()
                    .filter(row -> row.eventType().equals("REROLL")).findFirst().orElseThrow();
            require(event.slot() == null && event.augmentId() == null,
                    "A shared reroll observation has no single slot or card identity.");
            require(handle(game, online, "draft " + after.revision() + " 2 " + after.cardIds().get(2) + " " + UUID.randomUUID()) == 1,
                    "A replacement card remains immediately selectable before assigning its target.");
            require(state.currentOffer().isEmpty(), "One selection resolves the current offer.");
            var config = game.augmentConfig();
            setField(game, "augmentConfig", new AugmentConfig(config.enabled(), true,
                    config.rarityWeights(), config.parameters(), config.disabledIds()));
            setField(game, "currentRound", 14);
            setField(game, "phase", RoundPhase.ROUND_PAYOUT);
            game.tick(online.level().getServer());
            require(game.currentRound() == 15 && game.phase() == RoundPhase.PREPARE_AND_SUMMON,
                    "The real payout hook must advance into the next augment preparation.");
            require(state.currentOffer().orElseThrow().milestoneRound() == 15
                            && state.rerollsRemainingBySlot().equals(List.of(4, 4, 4)),
                    "The next milestone must carry the shared balance without refilling it.");
        } finally {game.close();}
        context.succeed();
    }

    @GameTest
    public void everyCommonTargetCardAcceptsPhysicalTowerClicksWithoutOpeningHistory(GameTestHelper context) {
        for (var card : AugmentCatalog.normalDefinitions().stream()
                .filter(card -> card.requiredJobId() == null && AugmentService.targetCount(card.id()) > 0).toList()) {
            ServerPlayer online = context.makeMockServerPlayerInLevel();
            String schedule = switch (card.rarity()) {case SILVER -> "SSS"; case GOLD -> "GGG"; case PRISMATIC -> "PPP";};
            SemionGame game = prepare(context, online, schedule);
            var originalConnection = online.connection;
            try {
                var manager = new kim.biryeong.semiontd.game.SemionGameManager();
                setField(manager, "activeGame", game);
                Tower target = addTarget(game, online, AugmentService.shortId(card.id()).equals("battlefield_mastery")
                        ? kim.biryeong.semiontd.tower.legion.LegionTowers.T1_PENGUIN : UndeadTowers.T1_ZOMBIE_TOWER);
                var lane = game.playerLane(online.getUUID()).orElseThrow();
                var entity = ((EntityBackedTower) target).runtimeEntity(lane).orElseThrow();
                String rarity = card.rarity().name().toLowerCase(java.util.Locale.ROOT);
                force(game, online, card.id() + " reserve_income_" + rarity + " reserve_production_" + rarity);
                advance(game, online, 20);
                var dialogs = new java.util.concurrent.atomic.AtomicInteger();
                online.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(online.level().getServer(),
                        new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), online,
                        net.minecraft.server.network.CommonListenerCookie.createInitial(online.getGameProfile(), false)) {
                    @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                        if (packet instanceof net.minecraft.network.protocol.common.ClientboundShowDialogPacket) {dialogs.incrementAndGet();}
                    }
                };
                var state = game.players().get(online.getUUID()).augments();
                require(handle(game, online, "draft " + state.currentOffer().orElseThrow().revision() + " 0 " + UUID.randomUUID()) == 1,
                        card.id() + " must be acquired.");
                require(dialogs.get() == 0, "Acquiring " + card.id() + " must not reopen the augment list.");
                online.connection = originalConnection;
                holdTargetTool(online);
                require(kim.biryeong.semiontd.ui.SemionTowerInteractionService.handleUse(manager, online, online.level(),
                        net.minecraft.world.InteractionHand.MAIN_HAND, entity, new net.minecraft.world.phys.EntityHitResult(entity))
                        == net.minecraft.world.InteractionResult.SUCCESS, "The physical right click must be consumed.");
                UUID selected = AugmentService.targetCount(card.id()) == 2
                        ? state.snapshot().choice(card.id()).secondaryTargetId() : state.snapshot().choice(card.id()).primaryTargetId();
                require(target.logicalId().equals(selected), card.id() + " must accept an eligible tower right click.");
                game.augmentService().handleTargetToolInput(game, online, null, false, true);
                require(state.snapshot().choice(card.id()).equals(target.augmentSnapshot().choice(card.id())),
                        "Fallback packets must not clear the assigned target.");
            } finally {
                online.connection = originalConnection;
                game.close();
            }
        }
        context.succeed();
    }

    @GameTest
    public void jobCardsRecheckOwnerOnOfferClickReconnectAndTimeout(GameTestHelper context) {
        for (String id : List.of("job_illager_towers_s", "job_adversary_towers_g1", "job_engineer_towers_g2")) {
            var card = AugmentCatalog.find(id).orElseThrow();
            ServerPlayer online = context.makeMockServerPlayerInLevel();
            SemionGame game = prepare(context, online, card.rarity() == AugmentRarity.SILVER ? "SSS" : "GGG");
            try {
                var player = game.players().get(online.getUUID());
                var onlyJobs = new AugmentConfig(true, true, player.augments().config().rarityWeights(), Map.of(),
                        AugmentCatalog.normalDefinitions().stream().filter(candidate -> !candidate.id().equals(card.id()))
                                .map(AugmentDefinition::id).collect(java.util.stream.Collectors.toSet()));
                var predicate = (java.util.function.Predicate<AugmentDefinition>) candidate ->
                        game.augmentService().isEligible(game, player, candidate.id());
                var schedule = List.of(card.rarity(), card.rarity(), card.rarity());
                var blocked = new PlayerAugmentState(online.getUUID());
                blocked.initialize(1, onlyJobs, schedule);
                var blockedOffer = blocked.offer(5, 5, 1200, predicate);
                require(blockedOffer.cardIds().stream().allMatch(candidate -> AugmentCatalog.find(candidate).orElseThrow().reserve()),
                        "Initial offer must use three reserves when all jobs mismatch.");
                require(blocked.reroll(5, blockedOffer.revision(), UUID.randomUUID(), 20, predicate).status()
                                == PlayerAugmentState.Status.NO_REPLACEMENT && blocked.rerollsRemaining(0) == 5,
                        "A reroll cannot introduce another job or spend a charge without a replacement.");
                require(!game.augmentService().isEligible(game, player, id), "Other jobs cannot acquire " + id);
                player.assignJob(kim.biryeong.semiontd.job.JobRegistry.find(
                        net.minecraft.resources.Identifier.parse(card.requiredJobId())).orElseThrow());
                require(game.augmentService().isEligible(game, player, id), "Matching job must allow " + id);
                String rarity = card.rarity().name().toLowerCase(java.util.Locale.ROOT);
                var allowed = new PlayerAugmentState(online.getUUID());
                allowed.initialize(1, onlyJobs, schedule);
                var allowedOffer = allowed.offer(5, 5, 1200, predicate);
                require(allowedOffer.cardIds().contains(card.id()), "Matching job appears in the normal candidate pool.");
                var rerolled = new PlayerAugmentState(online.getUUID());
                rerolled.initialize(1, onlyJobs, schedule);
                var reserveOffer = rerolled.forceOffer(5, 5, 1200, List.of("reserve_diamonds_" + rarity,
                        "reserve_income_" + rarity, "reserve_production_" + rarity));
                require(rerolled.reroll(5, reserveOffer.revision(), UUID.randomUUID(), 20, predicate).status()
                                == PlayerAugmentState.Status.NO_REPLACEMENT
                                && rerolled.currentOffer().orElseThrow().equals(reserveOffer)
                                && rerolled.rerollsRemaining() == 5,
                        "One eligible job card cannot partially replace three reserves or spend a charge.");
                addTarget(game, online);
                int tier = card.rarity().ordinal() + 1;
                var replacementIds = java.util.Set.of(card.id(), AugmentCatalog.normalizeId("finishing_fire_" + tier),
                        AugmentCatalog.normalizeId("beneficial_effect_" + tier));
                var viableConfig = new AugmentConfig(true, true, onlyJobs.rarityWeights(), Map.of(),
                        AugmentCatalog.normalDefinitions().stream().filter(candidate -> !replacementIds.contains(candidate.id()))
                                .map(AugmentDefinition::id).collect(java.util.stream.Collectors.toSet()));
                var viable = new PlayerAugmentState(online.getUUID());
                viable.initialize(1, viableConfig, schedule);
                var viableOffer = viable.forceOffer(5, 5, 1200, reserveOffer.cardIds());
                require(viable.reroll(5, viableOffer.revision(), UUID.randomUUID(), 20, predicate).successful()
                                && new java.util.HashSet<>(viable.currentOffer().orElseThrow().cardIds()).equals(replacementIds)
                                && viable.rerollsRemaining() == 4,
                        "A complete replacement admits the matching job and two eligible common cards.");
                force(game, online, id + " reserve_income_" + rarity + " reserve_production_" + rarity);
                advance(game, online, 20);
                var offer = player.augments().currentOffer().orElseThrow();
                game.augmentService().reopen(game, online);
                require(offer.equals(player.augments().currentOffer().orElseThrow()), "Reconnect preserves candidate revision.");
                player.assignJob(kim.biryeong.semiontd.job.JobRegistry.defaultJob());
                require(handle(game, online, "draft " + offer.revision() + " 0 " + UUID.randomUUID()) == 0,
                        "A stale job offer must be rejected at click.");
                require(!player.augments().hasSelected(id), "Rejected card has no effect.");
                game.augmentService().tick(game, online.level().getServer(), offer.deadlineTickExclusive());
                require(!player.augments().hasSelected(id), "Timeout cannot award another job's card.");
                require(player.augments().selections().size() == 1, "Timeout still awards one safe replacement.");
            } finally {game.close();}
        }
        context.succeed();
    }

    @GameTest
    public void jobCardClickCommitsOnceAndSynchronizesTowerSnapshot(GameTestHelper context) {
        ServerPlayer online = context.makeMockServerPlayerInLevel();
        SemionGame game = prepare(context, online);
        try {
            var player = game.players().get(online.getUUID());
            player.assignJob(new kim.biryeong.semiontd.job.IllagerTowerJob());
            var target = addTarget(game, online);
            force(game, online, "job_illager_towers_s reserve_income_silver reserve_production_silver");
            advance(game, online, 20);
            var offer = player.augments().currentOffer().orElseThrow();
            String click = "draft " + offer.revision() + " 0 " + UUID.randomUUID();
            require(handle(game, online, click) == 1, "First click must grant the job augment.");
            handle(game, online, click);
            require(player.augments().selections().size() == 1, "Duplicate input cannot grant twice.");
            require(target.augmentSnapshot().has("job_illager_towers_s"), "Existing towers receive the committed snapshot.");
        } finally {game.close();}
        context.succeed();
    }

    @GameTest
    public void allOfferSummariesStayCompactAndKeepTradeoffs(GameTestHelper context) {
        try {
            for (AugmentDefinition card : AugmentCatalog.definitions()) {
                String summary = AugmentService.offerSummary(card, AugmentConfig.defaults());
                require(!summary.isBlank() && !summary.contains("…")
                                && summary.equals(AugmentDescriptions.describe(card, AugmentConfig.defaults())),
                        "Card and detail must share the complete compact description: " + card.id());
            }
            require(AugmentService.offerSummary(AugmentCatalog.find("overheat_core").orElseThrow(), AugmentConfig.defaults()).contains("피해 -6% 영구 누적"),
                    "Compact overheat text must retain the permanent damage penalty.");
            require(AugmentService.offerSummary(AugmentCatalog.find("one_man_show").orElseThrow(), AugmentConfig.defaults()).contains("나머지 피해 -"),
                    "Compact one-man-show text must retain the penalty to the remaining towers.");
            require(AugmentService.offerSummary(AugmentCatalog.find("wartime_economy").orElseThrow(), AugmentConfig.defaults()).contains("정기 다이아 지급 영구"),
                    "Compact wartime text must retain its permanent economic cost.");
        } catch (AssertionError error) {
            context.fail(Component.literal(error.getMessage()));
        }
        context.succeed();
    }

    @GameTest
    public void singleClickSelectionChecksSessionRevealAndDuplicateRequests(GameTestHelper context) {
        ServerPlayer online = context.makeMockServerPlayerInLevel();
        SemionGame game = prepare(context, online);
        var originalConnection = online.connection;
        try {
            force(game, online, "reserve_diamonds_silver reserve_income_silver reserve_production_silver");
            SemionPlayer player = game.players().get(online.getUUID());
            PlayerAugmentState state = player.augments();
            long revision = state.currentOffer().orElseThrow().revision();
            String draft = "draft " + revision + " 1 " + UUID.randomUUID();
            require(handle(game, online, draft) == 0 && state.currentOffer().orElseThrow().draft() == null,
                    "The current session cannot draft before the twenty-tick reveal lock ends.");
            advance(game, online, 20);
            require(game.augmentService().handle(game, online, draft, false) == 0,
                    "Mutation without the match token must be rejected.");
            require(game.augmentService().handle(game, online, "session " + UUID.randomUUID() + " " + draft, false) == 0,
                    "An old match token must not mutate an otherwise current offer.");
            var pending = state.currentOffer().orElseThrow();
            game.augmentService().reopen(game, online);
            require(player.augmentTelemetry().snapshot().guiEvents().stream()
                            .noneMatch(event -> event.eventType().equals("SHOWN")),
                    "An unavailable renderer must not record an offer as displayed.");
            var packets = collectPackets(online);
            game.augmentService().reopen(game, online);
            require(showPackets(packets) == 1, "A client with the card pack must receive the actual offer dialog.");
            require(handle(game, online, "ui offer back") == 1, "Returning to candidates must remain a read-only dialog action.");
            packets.clear();
            game.augmentService().reopen(game, online);
            require(showPackets(packets) == 1, "Restoring a client with the card pack must show the actual offer again.");
            require(pending.equals(state.currentOffer().orElseThrow()), "Reopening the offer dialog must not change its stored offer or draft.");
            long income = player.economy().income();
            require(handle(game, online, draft) == 1 && state.currentOffer().isEmpty(),
                    "A card with no settings must be granted by the first click, without a confirmation step.");
            require(player.economy().income() == income + 15, "The click must immediately apply the selected reward.");
            handle(game, online, draft);
            handle(game, online, "draft " + revision + " 0 " + UUID.randomUUID());
            require(player.economy().income() == income + 15 && state.selections().size() == 1,
                    "Replaying the exact button must neither apply income twice nor create a second selection.");
            require(player.augments().snapshot().has("reserve_income_silver"), "The receipt must identify the selected reserve.");
            var events = player.augmentTelemetry().snapshot().guiEvents();
            require(events.stream().filter(event -> event.eventType().equals("SHOWN")).count() == 1,
                    "Returning and restoring must not create a second first-display observation.");
            require(events.stream().filter(event -> event.eventType().equals("CONFIRMED")).count() == 1,
                    "A duplicate button must not create a second confirmed telemetry observation.");
            var confirmed = events.stream().filter(event -> event.eventType().equals("CONFIRMED")).findFirst().orElseThrow();
            require(confirmed.elapsedTicks() != null && confirmed.inputCount() >= 1 && confirmed.backCount() == 1,
                    "The confirmation observation must carry measured display elapsed time and actual input/back counts.");
            require(events.stream().anyMatch(event -> event.eventType().equals("RESTORED")), "Reconnect restoration must have its own route observation.");
        } catch (AssertionError error) {
            context.fail(Component.literal(error.getMessage()));
        } finally {
            online.connection = originalConnection;
            game.close();
        }
        context.succeed();
    }

    @GameTest
    public void heldToolHighlightsOnlyOwnerAndClearsOnLowerRemovalAndClose(GameTestHelper context) {
        ServerPlayer online = context.makeMockServerPlayerInLevel();
        SemionGame game = prepare(context, online);
        try {
            Tower target = addTarget(game, online);
            var lane = game.playerLane(online.getUUID()).orElseThrow();
            var entity = ((EntityBackedTower) target).runtimeEntity(lane).orElseThrow();
            force(game, online, "tactical_designation_1_assault reserve_income_silver reserve_production_silver");
            advance(game, online, 20);
            var state = game.players().get(online.getUUID()).augments();
            require(handle(game, online, "draft " + state.currentOffer().orElseThrow().revision() + " 0 " + UUID.randomUUID()) == 1,
                    "A targeted card must be owned immediately, before selecting any tower.");
            require(state.currentOffer().isEmpty() && state.snapshot().choice("tactical_designation_1").primaryTargetId() == null,
                    "Acquisition must not choose a tower automatically.");
            holdTargetTool(online);
            require(game.augmentService().handleTargetToolInput(game, online, target, false, false), "The held tool must consume target use.");
            game.augmentService().handleTargetToolInput(game, online, null, false, true);
            require(target.logicalId().equals(state.snapshot().choice("tactical_designation_1").primaryTargetId()),
                    "Fallback item-use from the same right click must not clear the target just assigned.");
            require(targetPreviewCount(game) == 1 && !entity.isCurrentlyGlowing(), "Only the owner's metadata overlay may glow.");
            var packet = entity.selectionGlowPackets(true).getFirst();
            require(packet.id() == entity.getId() && ((Byte) packet.packedItems().getFirst().value() & 0x40) != 0,
                    "The overlay addresses the actual tower's glow bit.");
            entity.setGlowingTag(true);
            require(((Byte) entity.selectionGlowPackets(false).getFirst().packedItems().getFirst().value() & 0x40) != 0,
                    "Clearing the overlay preserves gameplay glow.");
            entity.setGlowingTag(false);
            setField(game, "phase", RoundPhase.LANE_WAVE);
            game.augmentService().tick(game, online.level().getServer(), game.currentTick() + 100);
            require(targetPreviewCount(game) == 1, "Held glow must continue during combat and beyond three seconds.");
            Tower other = addTarget(game, online);
            game.augmentService().useTargetTool(game, online, other, false, false);
            require(target.logicalId().equals(state.snapshot().choice("tactical_designation_1").primaryTargetId()),
                    "Combat clicks cannot change targets.");
            online.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            game.augmentService().tick(game, online.level().getServer(), game.currentTick() + 101);
            require(targetPreviewCount(game) == 0, "Lowering the item removes the private overlay.");
            game.augmentService().reopen(game, online);
            holdTargetTool(online);
            setField(game, "phase", RoundPhase.PREPARE_AND_SUMMON);
            game.augmentService().useTargetTool(game, online, target, true, false);
            require(targetPreviewCount(game) == 1, "The restored tool can highlight the same target again.");
            lane.removeTower(target);
            game.augmentService().tick(game, online.level().getServer(), game.currentTick() + 102);
            require(state.snapshot().choice("tactical_designation_1").primaryTargetId() == null && targetPreviewCount(game) == 0,
                    "Permanent removal clears the binding and overlay without assigning a replacement.");
            game.close();
            require(targetPreviewCount(game) == 0, "Match cleanup clears every preview.");
        } catch (AssertionError error) {
            context.fail(Component.literal(error.getMessage()));
        } finally {
            game.close();
        }
        context.succeed();
    }

    @GameTest
    public void fixedStanceCardsCommitWithoutModeInputAndCannotSwitchStance(GameTestHelper context) {
        for (AugmentDefinition card : AugmentCatalog.normalDefinitions()) {
            String mode = AugmentCatalog.fixedMode(card.id());
            if (mode.isEmpty()) {continue;}
            ServerPlayer online = context.makeMockServerPlayerInLevel();
            String schedule = switch (card.rarity()) {case SILVER -> "SSS"; case GOLD -> "GGG"; case PRISMATIC -> "PPP";};
            SemionGame game = prepare(context, online, schedule);
            try {
                Tower target = addTarget(game, online);
                String rarity = card.rarity().name().toLowerCase(java.util.Locale.ROOT);
                force(game, online, card.id() + " reserve_income_" + rarity + " reserve_production_" + rarity);
                advance(game, online, 20);
                var state = game.players().get(online.getUUID()).augments();
                long revision = state.currentOffer().orElseThrow().revision();
                String click = "draft " + revision + " 0 " + UUID.randomUUID();
                require(handle(game, online, click) == 1, "The fixed card must accept its first click: " + card.id());
                boolean targeted = AugmentService.targetCount(card.id()) > 0;
                if (targeted) {
                    require(state.selections().size() == 1, "Tactical cards must be granted without a target.");
                    holdTargetTool(online);
                    game.augmentService().useTargetTool(game, online, target, false, false);
                }
                require(state.currentOffer().isEmpty() && state.selections().size() == 1, "No mode or confirmation step may remain.");
                require(state.selections().getFirst().augmentId().equals(card.id()), "The record must identify the separate card.");
                require(state.snapshot().choice(AugmentCatalog.effectId(card.id())).mode().equals(mode), "Combat must receive the fixed stance.");
                handle(game, online, click);
                require(state.selections().size() == 1, "Duplicate clicks must not grant another augment.");
                setField(game, "currentRound", 6);
                state.beginPrepare(6);
                require(handle(game, online, "configure 0 " + UUID.randomUUID()) == (targeted ? 1 : 0),
                        "Only tactical cards may change their target in later preparation.");
                if (targeted) {
                    var pending = state.configurationDraft().orElseThrow();
                    require(handle(game, online, "configure-mode " + state.configurationRevision() + " " + mode) == 0,
                            "Later preparation cannot change the fixed stance.");
                    require(pending.equals(state.configurationDraft().orElseThrow()), "Rejected reconfiguration must preserve the draft.");
                    Tower replacement = addTarget(game, online);
                    require(handle(game, online, "configure-target " + state.configurationRevision() + " " + replacement.logicalId()) == 1,
                            "The fixed tactical card must accept a new target.");
                    require(handle(game, online, "configure-confirm " + state.configurationRevision() + " " + UUID.randomUUID()) == 1,
                            "The replacement target must pass the existing reconfiguration checks.");
                    var choice = state.snapshot().choice(card.id());
                    require(replacement.logicalId().equals(choice.primaryTargetId()) && choice.mode().equals(mode),
                            "Retargeting must preserve the chosen card's stance.");
                }
            } catch (AssertionError error) {
                context.fail(Component.literal(error.getMessage()));
            } finally {
                game.close();
            }
        }
        context.succeed();
    }

    @GameTest
    public void targetToolRejectsInvalidTargetsPreservesHealthAndRegrantsWithoutOverwrite(GameTestHelper context) {
        ServerPlayer online = context.makeMockServerPlayerInLevel();
        SemionGame game = prepare(context, online, "PPP");
        try {
            var player = game.players().get(online.getUUID());
            var state = player.augments();
            force(game, online, "one_man_show reserve_income_prismatic reserve_production_prismatic");
            advance(game, online, 20);
            require(handle(game, online, "draft " + state.currentOffer().orElseThrow().revision() + " 0 " + UUID.randomUUID()) == 1,
                    "No eligible tower is required to own a targeted augment.");
            holdTargetTool(online);
            Tower first = addTarget(game, online);
            Tower second = addTarget(game, online);
            first.syncHealth(first.currentMaxHealth() * .4);
            double ratio = first.health() / first.currentMaxHealth();
            game.augmentService().useTargetTool(game, online, first, false, false);
            var previous = state.snapshot().choice("one_man_show");
            game.augmentService().useTargetTool(game, online, null, true, false);
            require(previous.equals(state.snapshot().choice("one_man_show")), "Invalid block/entity clicks preserve the target.");
            game.playerLane(online.getUUID()).orElseThrow().removeTower(second);
            game.augmentService().useTargetTool(game, online, second, false, false);
            require(previous.equals(state.snapshot().choice("one_man_show")), "A removed tower cannot be assigned.");
            game.augmentService().useTargetTool(game, online, null, false, true);
            require(state.snapshot().choice("one_man_show").primaryTargetId() == null, "Air use clears the target.");
            game.augmentService().useTargetTool(game, online, first, true, false);
            require(Math.abs(first.health() / first.currentMaxHealth() - ratio) < 1e-6, "Repeated targeting must not heal.");
            game.augmentService().reopen(game, online);
            require(toolCount(online) == 1, "Reconnect/regrant keeps exactly one tool.");
            AugmentTargetTool.clear(online);
            for (int index = 0; index < 36; index++) {
                online.getInventory().setItem(index, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 64));
            }
            game.augmentService().reopen(game, online);
            require(toolCount(online) == 0 && online.getInventory().getItem(0).getCount() == 64,
                    "A full inventory must neither overwrite items nor drop a tool.");
            online.getInventory().setItem(8, net.minecraft.world.item.ItemStack.EMPTY);
            require(handle(game, online, "ui target-tool") == 1 && toolCount(online) == 1, "The existing augment UI regrants into a free slot.");
            var followUps = player.augmentTelemetry().snapshot().guiEvents().stream().filter(event -> event.route().equals("TARGET_TOOL")).toList();
            require(!followUps.isEmpty(), "Target changes use the existing telemetry path.");
        } catch (AssertionError error) {
            context.fail(Component.literal(error.getMessage()));
        } finally {
            game.close();
        }
        context.succeed();
    }

    @GameTest
    public void twoRoleToolAcceptsEitherOrderAndRejectsCollisions(GameTestHelper context) {
        ServerPlayer online = context.makeMockServerPlayerInLevel();
        SemionGame game = prepare(context, online, "GGG");
        try {
            var state = game.players().get(online.getUUID()).augments();
            force(game, online, "frontline_specialization reserve_income_gold reserve_production_gold");
            advance(game, online, 20);
            require(handle(game, online, "draft " + state.currentOffer().orElseThrow().revision() + " 0 " + UUID.randomUUID()) == 1,
                    "A two-target augment is owned before its targets are assigned.");
            holdTargetTool(online);
            Tower first = addTarget(game, online);
            Tower second = addTarget(game, online);
            game.augmentService().useTargetTool(game, online, second, false, false);
            require(state.snapshot().choice("frontline_specialization").primaryTargetId() == null
                            && second.logicalId().equals(state.snapshot().choice("frontline_specialization").secondaryTargetId()),
                    "Right click may assign artillery first without inventing a vanguard.");
            game.augmentService().useTargetTool(game, online, second, true, false);
            require(state.snapshot().choice("frontline_specialization").primaryTargetId() == null, "Role collision preserves the partial selection.");
            game.augmentService().useTargetTool(game, online, first, true, false);
            var choice = state.snapshot().choice("frontline_specialization");
            require(first.logicalId().equals(choice.primaryTargetId()) && second.logicalId().equals(choice.secondaryTargetId()),
                    "Left and right roles retain their independent assignments.");
            game.augmentService().useTargetTool(game, online, null, false, true);
            require(state.snapshot().choice("frontline_specialization").equals(AugmentChoice.none()), "Air use clears both roles together.");
            require(state.selections().size() == 1, "Target edits must not acquire another augment.");
        } catch (AssertionError error) {
            context.fail(Component.literal(error.getMessage()));
        } finally {
            game.close();
        }
        context.succeed();
    }

    @GameTest
    public void timeoutSelectsOnceButCombatAndClosedGamesNeverGrant(GameTestHelper context) {
        for (boolean deadline : List.of(true, false)) {
            ServerPlayer online = context.makeMockServerPlayerInLevel();
            SemionGame game = prepare(context, online);
            try {
                force(game, online, "reserve_diamonds_silver reserve_income_silver reserve_production_silver");
                advance(game, online, 20);
                SemionPlayer player = game.players().get(online.getUUID());
                PlayerAugmentState state = player.augments();
                var offer = state.currentOffer().orElseThrow();
                long diamonds = player.economy().diamond();
                if (deadline) {
                    setField(game, "tickCounter", offer.deadlineTickExclusive());
                } else {
                    setField(game, "phase", RoundPhase.LANE_WAVE);
                }
                require(handle(game, online, "draft " + offer.revision() + " 0 " + UUID.randomUUID()) == 0,
                        "Neither the exclusive deadline nor a combat phase accepts a card click.");
                if (!deadline) {
                    require(player.economy().diamond() == diamonds && state.selections().isEmpty(), "Combat cannot grant a pending selection.");
                }
                setField(game, "tickCounter", offer.deadlineTickExclusive());
                game.augmentService().expire(game);
                game.augmentService().expire(game);
                if (!deadline) {
                    require(state.selections().isEmpty(), "Expiry must not grant after combat has begun.");
                    game.close();
                    game.augmentService().expire(game);
                    require(state.selections().isEmpty(), "Closed games must not grant.");
                    continue;
                }
                require(state.selections().size() == 1 && state.selections().getFirst().outcome() == PlayerAugmentState.Outcome.SELECTED,
                        "Expiry must record one random selection.");
                String selected = state.selections().getFirst().augmentId();
                require(offer.cardIds().contains(selected), "A valid offered candidate must be selected before any fallback.");
                require(player.economy().diamond() == diamonds + (selected.contains("diamonds") ? 150 : 0),
                        "Random settlement must apply only the actual selected reward once.");
                require(game.playerLane(player.uuid()).orElseThrow().augmentSnapshot().has(selected), "Auto selection must update the lane immediately.");
                require(player.augmentTelemetry().snapshot().guiEvents().stream()
                                .filter(event -> event.eventType().equals("AUTO_SELECTED") && "TIMEOUT".equals(event.reason())).count() == 1,
                        "Repeated expiry must write exactly one timeout observation.");
            } catch (AssertionError error) {
                context.fail(Component.literal(error.getMessage()));
            } finally {
                game.close();
            }
        }
        context.succeed();
    }

    @GameTest
    public void timeoutMayGrantTargetedAugmentButNeverAssignsItsTowers(GameTestHelper context) {
        ServerPlayer online = context.makeMockServerPlayerInLevel();
        SemionGame game = prepare(context, online, "GGG");
        try {
            addTarget(game, online);
            addTarget(game, online);
            force(game, online, "tactical_designation_2_assault frontline_specialization battlefield_mastery");
            var state = game.players().get(online.getUUID()).augments();
            setField(game, "tickCounter", state.currentOffer().orElseThrow().deadlineTickExclusive());
            game.augmentService().expire(game);
            game.augmentService().expire(game);
            require(state.selections().size() == 1, "Timeout must grant exactly one augment.");
            var selected = state.selections().getFirst();
            require(AugmentService.targetCount(selected.augmentId()) > 0 && selected.choice().equals(AugmentChoice.none()),
                    "A targeted candidate is valid at timeout but available towers must not be chosen automatically.");
            game.augmentService().reopen(game, online);
            require(toolCount(online) == 1, "Reconnection restores the tool for a timeout award.");
        } catch (AssertionError error) {
            context.fail(Component.literal(error.getMessage()));
        } finally {
            game.close();
        }
        context.succeed();
    }

    @GameTest
    public void masteryRetargetClearsStacksButSameTargetAndUpgradeKeepThem(GameTestHelper context) {
        ServerPlayer online = context.makeMockServerPlayerInLevel();
        SemionGame game = prepare(context, online, "GGG");
        try {
            var state = game.players().get(online.getUUID()).augments();
            var lane = game.playerLane(online.getUUID()).orElseThrow();
            Tower first = addTarget(game, online, kim.biryeong.semiontd.tower.legion.LegionTowers.T1_PENGUIN);
            Tower second = addTarget(game, online);
            var stacks = kim.biryeong.semiontd.tower.TowerDataKey.of(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("semiontd", "augment_mastery"), Integer.class);
            force(game, online, "battlefield_mastery reserve_income_gold reserve_production_gold");
            advance(game, online, 20);
            handle(game, online, "draft " + state.currentOffer().orElseThrow().revision() + " 0 " + UUID.randomUUID());
            holdTargetTool(online);
            game.augmentService().useTargetTool(game, online, first, true, false);
            require(first.logicalId().equals(state.snapshot().choice("battlefield_mastery").primaryTargetId()),
                    "A newly placed penguin must be designatable without a previous damage/survival record.");
            require(AugmentCombat.masteryStacks(first) == 0, "Designation alone must not grant mastery.");
            first.setData(stacks, 2);
            first.syncHealth(first.currentMaxHealth() * .4);
            game.augmentService().useTargetTool(game, online, first, false, false);
            require(AugmentCombat.masteryStacks(first) == 2, "Clicking the same target preserves mastery.");
            Tower upgraded = ProductionTowerCatalog.entry(kim.biryeong.semiontd.tower.legion.LegionTowers.T2_PENGUIN).orElseThrow()
                    .create(online.getUUID(), TeamId.RED, 1, first.originalPosition());
            upgraded.copyFrom(first, 50);
            require(lane.replaceTower(first, upgraded), "A replacement must use the existing upgrade path.");
            game.augmentService().tick(game, online.level().getServer(), game.currentTick() + 1);
            require(AugmentCombat.masteryStacks(upgraded) == 2 && upgraded.logicalId().equals(state.snapshot().choice("battlefield_mastery").primaryTargetId()),
                    "Normal upgrade keeps logical binding and mastery.");
            upgraded.syncHealth(upgraded.currentMaxHealth() * .4);
            game.augmentService().useTargetTool(game, online, second, false, false);
            require(AugmentCombat.masteryStacks(upgraded) == 0, "Switching to B removes A's mastery.");
            require(Math.abs(upgraded.health() / upgraded.currentMaxHealth() - .4) < 1e-6, "Losing mastery preserves the HP ratio.");
            second.setData(stacks, 1);
            game.augmentService().useTargetTool(game, online, upgraded, false, false);
            require(AugmentCombat.masteryStacks(upgraded) == 0 && AugmentCombat.masteryStacks(second) == 0,
                    "A to B to A never restores old stacks or transfers B's stacks.");
            upgraded.setData(stacks, 1);
            game.augmentService().useTargetTool(game, online, null, false, true);
            require(AugmentCombat.masteryStacks(upgraded) == 0 && state.snapshot().choice("battlefield_mastery").primaryTargetId() == null,
                    "Air clear also forfeits mastery.");
        } catch (AssertionError error) {
            context.fail(Component.literal(error.getMessage()));
        } finally {
            game.close();
        }
        context.succeed();
    }

    private static int targetPreviewCount(SemionGame game) {
        try {
            var field = AugmentService.class.getDeclaredField("targetPreviews");
            field.setAccessible(true);
            return ((Map<?, ?>) field.get(game.augmentService())).size();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
