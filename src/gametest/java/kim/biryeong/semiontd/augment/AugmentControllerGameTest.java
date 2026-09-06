package kim.biryeong.semiontd.augment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.undead.UndeadTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Exercises the real server controller and native dialog construction, not client rendering. */
public final class AugmentControllerGameTest {
    @GameTest
    public void allOfferSummariesStayCompactAndKeepTradeoffs(GameTestHelper context) {
        try {
            for (AugmentDefinition card : AugmentCatalog.definitions()) {
                String summary = AugmentService.offerSummary(card, AugmentConfig.defaults());
                require(!summary.isBlank() && summary.length() <= 105 && !summary.contains("\n"),
                        "Offer summaries must fit one compact item description, with full details on first click: " + card.id());
            }
            require(AugmentService.offerSummary(AugmentCatalog.find("overheat_core").orElseThrow(), AugmentConfig.defaults()).contains("영구 피해"),
                    "Compact overheat text must retain the permanent damage penalty.");
            require(AugmentService.offerSummary(AugmentCatalog.find("one_man_show").orElseThrow(), AugmentConfig.defaults()).contains("나머지 피해 -"),
                    "Compact one-man-show text must retain the penalty to the remaining towers.");
            require(AugmentService.offerSummary(AugmentCatalog.find("wartime_economy").orElseThrow(), AugmentConfig.defaults()).contains("정기 지급은 영구"),
                    "Compact wartime text must retain its permanent economic cost.");
        } catch (AssertionError error) {
            context.fail(Component.literal(error.getMessage()));
        }
        context.succeed();
    }

    @GameTest
    public void sessionRevealAndDuplicateConfirmationAreCheckedBeforeApplyingEffects(GameTestHelper context) {
        ServerPlayer online = context.makeMockServerPlayerInLevel();
        SemionGame game = prepare(context, online);
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
            require(handle(game, online, draft) == 1, "The current session may draft after reveal.");
            var pending = state.currentOffer().orElseThrow();
            require(handle(game, online, "ui offer back") == 1, "Returning to candidates must remain a read-only dialog action.");
            game.augmentService().reopen(game, online);
            require(pending.equals(state.currentOffer().orElseThrow()), "Reopening a native dialog must not change its stored offer or draft.");
            long income = player.economy().income();
            String confirm = "confirm " + pending.revision() + " " + pending.draftRevision() + " " + UUID.randomUUID();
            require(handle(game, online, confirm) == 1, "A current confirmation must succeed.");
            handle(game, online, confirm);
            require(player.economy().income() == income + 10 && state.selections().size() == 1,
                    "Replaying the exact button must neither apply income twice nor create a second selection.");
            require(player.augments().snapshot().has("reserve_income_silver"), "The receipt must identify the selected reserve.");
            var events = player.augmentTelemetry().snapshot().guiEvents();
            require(events.stream().filter(event -> event.eventType().equals("SHOWN")).count() == 1,
                    "Returning and restoring must not create a second first-display observation.");
            require(events.stream().filter(event -> event.eventType().equals("CONFIRMED")).count() == 1,
                    "A duplicate button must not create a second confirmed telemetry observation.");
            var confirmed = events.stream().filter(event -> event.eventType().equals("CONFIRMED")).findFirst().orElseThrow();
            require(confirmed.elapsedTicks() != null && confirmed.inputCount() >= 2 && confirmed.backCount() == 1,
                    "The confirmation observation must carry measured display elapsed time and actual input/back counts.");
            require(events.stream().anyMatch(event -> event.eventType().equals("RESTORED")), "Reconnect restoration must have its own route observation.");
        } catch (AssertionError error) {
            context.fail(Component.literal(error.getMessage()));
        } finally {
            game.close();
        }
        context.succeed();
    }

    @GameTest
    public void removedTargetAndStaleDraftAreRejectedWithoutLosingTheOffer(GameTestHelper context) {
        ServerPlayer online = context.makeMockServerPlayerInLevel();
        SemionGame game = prepare(context, online);
        try {
            Tower first = addTarget(game, online);
            Tower replacement = addTarget(game, online);
            force(game, online, "tactical_designation_1 reserve_income_silver reserve_production_silver");
            advance(game, online, 20);
            SemionPlayer player = game.players().get(online.getUUID());
            PlayerAugmentState state = player.augments();
            long revision = state.currentOffer().orElseThrow().revision();
            require(handle(game, online, "draft " + revision + " 0 " + UUID.randomUUID()) == 1, "Tactical card must open a draft.");
            long beforeTarget = state.currentOffer().orElseThrow().draftRevision();
            require(handle(game, online, "target " + revision + " " + beforeTarget + " " + UUID.randomUUID()) == 0,
                    "A fabricated target UUID must fail before changing the draft.");
            require(state.currentOffer().orElseThrow().draftRevision() == beforeTarget, "A rejected target must not advance the draft revision.");
            require(handle(game, online, "target " + revision + " " + beforeTarget + " " + first.logicalId()) == 1,
                    "The first current owned target must be selectable.");
            long beforeMode = state.currentOffer().orElseThrow().draftRevision();
            require(handle(game, online, "mode " + revision + " " + beforeMode + " ASSAULT") == 1, "The server must save the requested single mode.");
            var ready = state.currentOffer().orElseThrow();
            String staleConfirm = "confirm " + revision + " " + ready.draftRevision() + " " + UUID.randomUUID();
            game.playerLane(online.getUUID()).orElseThrow().removeTower(first);
            require(handle(game, online, staleConfirm) == 0, "A target removed after preview must not receive the card on confirmation.");
            require(state.selections().isEmpty() && state.currentOffer().isPresent(), "Failure must preserve the offer without selecting or charging anything.");
            long currentDraft = state.currentOffer().orElseThrow().draftRevision();
            require(handle(game, online, "target " + revision + " " + currentDraft + " " + replacement.logicalId()) == 1,
                    "Another currently eligible target can repair the same draft.");
            var repaired = state.currentOffer().orElseThrow();
            require(handle(game, online, "mode " + revision + " " + beforeMode + " COVER") == 0,
                    "An earlier mode button cannot overwrite a newer target draft.");
            require(repaired.equals(state.currentOffer().orElseThrow()), "A stale button must not alter the repaired target or mode.");
            require(handle(game, online, "confirm " + revision + " " + repaired.draftRevision() + " " + UUID.randomUUID()) == 1,
                    "A fresh confirmation may apply the repaired draft.");
            AugmentChoice result = state.snapshot().choice("tactical_designation_1");
            require(replacement.logicalId().equals(result.primaryTargetId()) && result.mode().equals("ASSAULT"),
                    "Only the repaired target and current mode may be committed.");
            var events = player.augmentTelemetry().snapshot().guiEvents();
            require(events.stream().anyMatch(event -> event.eventType().equals("REJECTED") && "TARGET_UNAVAILABLE".equals(event.reason())),
                    "Rejected target input must retain a stable reason without exporting its raw UUID.");
            var confirmed = events.stream().filter(event -> event.eventType().equals("CONFIRMED")).findFirst().orElseThrow();
            require("ASSAULT".equals(confirmed.mode())
                            && confirmed.targetRefs().equals(List.of(player.augmentTelemetry().towerRef(replacement.logicalId()))),
                    "The final observation must use the committed mode and anonymous stable logical-tower reference.");
        } catch (AssertionError error) {
            context.fail(Component.literal(error.getMessage()));
        } finally {
            game.close();
        }
        context.succeed();
    }

    @GameTest
    public void exclusiveDeadlineAndCombatPhaseNeverApplyPendingConfirmations(GameTestHelper context) {
        for (boolean deadline : List.of(true, false)) {
            ServerPlayer online = context.makeMockServerPlayerInLevel();
            SemionGame game = prepare(context, online);
            try {
                force(game, online, "reserve_diamonds_silver reserve_income_silver reserve_production_silver");
                advance(game, online, 20);
                SemionPlayer player = game.players().get(online.getUUID());
                PlayerAugmentState state = player.augments();
                var offer = state.currentOffer().orElseThrow();
                require(handle(game, online, "draft " + offer.revision() + " 0 " + UUID.randomUUID()) == 1, "Reserve draft must exist before deadline test.");
                var draft = state.currentOffer().orElseThrow();
                long diamonds = player.economy().diamond();
                if (deadline) {
                    setField(game, "tickCounter", draft.deadlineTickExclusive());
                } else {
                    setField(game, "phase", RoundPhase.LANE_WAVE);
                }
                require(handle(game, online, "confirm " + draft.revision() + " " + draft.draftRevision() + " " + UUID.randomUUID()) == 0,
                        "Neither the exclusive deadline nor a combat phase accepts a pending confirmation.");
                require(player.economy().diamond() == diamonds && state.selections().isEmpty(), "Rejected confirmation cannot grant diamonds or write a selection.");
                setField(game, "tickCounter", draft.deadlineTickExclusive());
                game.augmentService().expire(game);
                game.augmentService().expire(game);
                require(state.selections().size() == 1 && state.selections().getFirst().outcome() == PlayerAugmentState.Outcome.SKIPPED,
                        "Expiry must eventually record one SKIPPED outcome without selecting a reserve.");
                require(player.augmentTelemetry().snapshot().guiEvents().stream()
                                .filter(event -> event.eventType().equals("SKIPPED") && "TIMEOUT".equals(event.reason())).count() == 1,
                        "Repeated expiry must write exactly one timeout observation.");
            } catch (AssertionError error) {
                context.fail(Component.literal(error.getMessage()));
            } finally {
                game.close();
            }
        }
        context.succeed();
    }

    private static SemionGame prepare(GameTestHelper context, ServerPlayer online) {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO)));
        Map<String, Integer> weights = new LinkedHashMap<>();
        AugmentConfig.defaults().rarityWeights().keySet().forEach(key -> weights.put(key, key.equals("SSS") ? 100 : 0));
        game.configureAugments(new AugmentConfig(true, false, weights, Map.of(), Set.of()));
        require(game.start(context.getLevel().getServer(), new ParticipantSelectionPlan(MatchMode.NORMAL, List.of(
                        new AssignedParticipant(online.getUUID(), "controller-red", TeamId.RED, 1),
                        new AssignedParticipant(UUID.randomUUID(), "controller-blue", TeamId.BLUE, 1)), Set.of(), 2)),
                "The synthetic NORMAL game must start.");
        for (var team : game.teams().values()) {team.laneGroup().disableMonsters();}
        setField(game, "currentRound", 4);
        setField(game, "phase", RoundPhase.ROUND_PAYOUT);
        game.tick(context.getLevel().getServer());
        require(game.currentRound() == 5 && game.phase() == RoundPhase.PREPARE_AND_SUMMON, "The real payout hook must enter R5 preparation.");
        return game;
    }

    private static Tower addTarget(SemionGame game, ServerPlayer online) {
        var lane = game.playerLane(online.getUUID()).orElseThrow();
        var position = lane.laneLayout().finalDefenseTowerSlots().getFirst();
        Tower tower = ProductionTowerCatalog.entry(UndeadTowers.T1_ZOMBIE_TOWER).orElseThrow()
                .create(online.getUUID(), TeamId.RED, 1, position);
        lane.addTower(tower);
        require(AugmentCombat.isNormalPermanent(tower), "Target fixture must be a registered ordinary owned tower.");
        return tower;
    }

    private static void force(SemionGame game, ServerPlayer online, String cards) {
        require(game.augmentService().handle(game, online, "force 5 " + cards, true) == 1, "An authorized internal force-offer must succeed.");
    }

    private static int handle(SemionGame game, ServerPlayer online, String input) {
        try {
            var token = AugmentService.class.getDeclaredField("sessionToken");
            token.setAccessible(true);
            return game.augmentService().handle(game, online, "session " + token.get(game.augmentService()) + " " + input, false);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot obtain the actual native-button match token.", exception);
        }
    }

    private static void advance(SemionGame game, ServerPlayer online, int ticks) {
        for (int i = 0; i < ticks; i++) {game.tick(online.getServer());}
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot prepare controller fixture: " + name, exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {throw new AssertionError(message);}
    }
}
