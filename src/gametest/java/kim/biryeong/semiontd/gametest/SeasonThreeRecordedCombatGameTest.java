package kim.biryeong.semiontd.gametest;

import static kim.biryeong.semiontd.gametest.SeasonThreeBuilderBalanceGameTest.require;
import static kim.biryeong.semiontd.gametest.SeasonThreeBuilderBalanceGameTest.setField;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.buildguide.BuildAction;
import kim.biryeong.semiontd.buildguide.BuildActionType;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.MapConfig;
import kim.biryeong.semiontd.config.MonsterScalingConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.EconomyService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.SeasonThreeBuilderBalanceGameTest.Board;
import kim.biryeong.semiontd.gametest.SeasonThreeBuilderBalanceGameTest.LaneResult;
import kim.biryeong.semiontd.gametest.SeasonThreeBuilderBalanceGameTest.LaneRun;
import kim.biryeong.semiontd.gametest.SeasonThreeBuilderBalanceGameTest.Purchase;
import kim.biryeong.semiontd.gametest.SeasonThreeReplaySupport.ActionResult;
import kim.biryeong.semiontd.gametest.SeasonThreeReplaySupport.Sample;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Recorded purchases, not reconstructed historical matches; only this test's budget is injected. */
final class SeasonThreeRecordedCombatGameTest {
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private static final int[] ROUNDS = {5, 15, 16, 25};
    private static final long[] BUDGETS = {270, 1740, 1740, 3000};
    private static final List<String> LIMITS = List.of(
            "RECORDED_PURCHASE_ORDER_AND_LANE_RELATIVE_COORDINATES",
            "CURRENT_CATALOG_NOT_HISTORICAL_PATCH_REPLAY", "SOURCE_TRAITS_NOT_APPLIED",
            "CURRENT_MAP_GEOMETRY_NOT_VERIFIED_SOURCE_MAP", "ZERO_PRIOR_COMBAT_GROWTH_OR_ECONOMY_HISTORY",
            "INJECTED_EQUAL_BUDGET_NOT_EQUAL_SPENDING_OR_ROUND_AFFORDABILITY",
            "NO_SYNTHETIC_PURCHASES_TO_FILL_BUDGET", "ECONOMY_ACTIONS_EXCLUDED_FROM_COMBAT_TRACK",
            "THREE_PAIRED_REPETITIONS_NOT_SIX_PLAYER_SAMPLES", "INERT_INVULNERABLE_BOSS_ENDPOINTS",
            "NONE_AUGMENT_COMPARISON_NOT_REQUIRED_TO_CLEAR", "NO_FULL_MATCH_OR_TEAM_FINAL_DEFENSE",
            "UNSEEDED_ENTITY_AI", "NO_CLIENT_OR_GUI_EVIDENCE");

    private SeasonThreeRecordedCombatGameTest() {}

    static void start(GameTestHelper context) {
        new Run(context).start();
    }

    private record Trial(Sample source, int round, long budget, int repeat) {}
    private record ActionObservation(int sourceIndex, BuildAction action, GridPosition requestedPosition, GridPosition resolvedPosition,
            String status, String lastFailure, String divergenceReason, int attempts,
            int preparationTick, long diamondsDelta, long emeraldsDelta, long incomeDelta) {}
    private record ReplayResult(TeamId team, int sourceActions, int attemptedActions, int successfulActions,
            Integer firstDeviationSourceIndex, List<ActionObservation> actions, int automaticActivations, LaneResult combat) {}
    private record Report(String status, boolean measured, String reason, String builderId, String matchId,
            String playerRef, String sourceCatalogVersion, JsonElement sourceTraits, JsonElement sourceMap,
            int sourceFinalRound, List<Integer> sourceAttemptedRounds, int round, long budget, int slotLimit,
            int repetition, String stage, String augmentGroup, int priorHistoryRounds, int observedTicks,
            int arenaReadyWaitTicks,
            String towerBalanceSha256, String waveConfigSha256, String economyConfigSha256,
            String fixtureLayoutSha256, String fixtureTemplateId, String fixtureTemplateSha256,
            List<String> limits, JsonElement automaticActions, List<ReplayResult> lanes) {}

    private static final class Subject {
        final LaneRun combat;
        final List<ActionObservation> actions = new ArrayList<>();
        int sourceIndex;
        int attempts;
        String lastFailure;
        Integer firstDeviation;

        Subject(LaneRun combat) {this.combat = combat;}

        ReplayResult snapshot(int sourceActions, int activations) {
            return new ReplayResult(combat.player.teamId(), sourceActions,
                    (int) actions.stream().filter(value -> value.attempts() > 0).count(),
                    (int) actions.stream().filter(value -> "SUCCESS".equals(value.status())).count(),
                    firstDeviation, List.copyOf(actions), activations, combat.result);
        }
    }

    private static final class Run {
        final GameTestHelper context;
        final ServerLevel level;
        final EconomyConfig economyConfig = EconomyConfig.defaultConfig();
        final WaveConfig waves = WaveConfig.defaultConfig().withSeason3Stages(true, true, Set.of());
        final String towerHash = SeasonThreeReplaySupport.hash(TowerBalanceRuntime.current());
        final List<Trial> trials = new ArrayList<>();
        final List<Subject> subjects = new ArrayList<>();
        SeasonThreeReplayArena.ArenaFixture arena;
        SeasonThreeReplayControls controls;
        SemionGame game;
        EconomyService economy;
        Trial trial;
        int trialIndex;
        int preparationTick;
        int combatTick;
        int measuredTrials;
        int arenaWaitTicks;

        Run(GameTestHelper context) {
            this.context = context;
            this.level = context.getLevel();
        }

        void start() {
            safely(() -> {
                List<Sample> samples = SeasonThreeReplaySupport.samples();
                String selected = System.getProperty("semiontd.baseline.builder");
                for (Sample sample : samples) {
                    if (selected != null && !selected.equals(sample.builderId())) {continue;}
                    String excluded = SeasonThreeReplaySupport.exclusion(sample.builderId()).orElse(null);
                    for (int checkpoint = 0; checkpoint < ROUNDS.length; checkpoint++) {
                        trial = new Trial(sample, ROUNDS[checkpoint], BUDGETS[checkpoint], 0);
                        if (excluded != null) {
                            report("EXCLUDED", false, excluded);
                        } else {
                            for (int repeat = 1; repeat <= 3; repeat++) {
                                trials.add(new Trial(sample, trial.round(), trial.budget(), repeat));
                            }
                        }
                    }
                }
                trial = null;
                if (trials.isEmpty()) {
                    SemionTd.LOGGER.info("SEASON3_RECORDED_COMBAT_NOT_MEASURED: no eligible sample/checkpoint; no balance evidence collected.");
                    context.succeed();
                    return;
                }
                arena = SeasonThreeReplayArena.createArena(context, new BlockPos(1024, 2, 1024), 1);
                awaitArenaReady();
            });
        }

        private void awaitArenaReady() {
            if (arena.entitiesReady()) {
                startTrial();
                return;
            }
            require(++arenaWaitTicks <= 10_000, "Recorded arena chunks did not become entity-ticking ready.");
            context.runAfterDelay(1, () -> safely(this::awaitArenaReady));
        }

        private void startTrial() {
            require(towerHash.equals(SeasonThreeReplaySupport.hash(TowerBalanceRuntime.current())),
                    "Tower balance changed between recorded observations.");
            trial = trials.get(trialIndex);
            preparationTick = 0;
            combatTick = 0;
            List<AssignedParticipant> participants = new ArrayList<>();
            Map<UUID, String> controlNames = new LinkedHashMap<>();
            for (int side = 0; side < 2; side++) {
                TeamId team = side == 0 ? TeamId.RED : TeamId.BLUE;
                UUID owner = UUID.nameUUIDFromBytes(("s3-record-combat-" + trial.source().playerRef() + "-" + team).getBytes(StandardCharsets.UTF_8));
                participants.add(new AssignedParticipant(owner, "Recorded" + side, team, 1));
                if (trial.source().builderId().equals("semion-td:frost")) {controlNames.put(owner, "S3Frost" + side);}
            }
            game = new SemionGame(economyConfig, waves, arena.gameArena());
            controls = SeasonThreeReplayControls.attach(context, game, controlNames);
            economy = new EconomyService(economyConfig, game);
            for (AssignedParticipant participant : participants) {
                require(game.selectJob(participant.uuid(), ResourceLocation.parse(trial.source().builderId())),
                        "Unknown current builder: " + trial.source().builderId());
            }
            require(game.start(level.getServer(), new ParticipantSelectionPlan(MatchMode.NORMAL, participants, Set.of(), 2)),
                    "Recorded combat NORMAL fixture must start.");
            for (AssignedParticipant participant : participants) {
                var boss = game.teams().get(participant.teamId()).laneGroup().bossEntity().orElseThrow();
                boss.setNoAi(true);
                boss.setInvulnerable(true);
            }
            setField(game, "currentRound", trial.round() - 1);
            setField(game, "phase", RoundPhase.ROUND_PAYOUT);
            game.tick(level.getServer());
            require(game.currentRound() == trial.round() && game.phase() == RoundPhase.PREPARE_AND_SUMMON,
                    "The ordinary round transition must enter this checkpoint.");
            for (AssignedParticipant participant : participants) {
                SemionPlayer player = game.players().get(participant.uuid());
                PlayerLane lane = game.playerLane(player.uuid()).orElseThrow();
                player.economy().overrideStartingValues(trial.budget(), 0, 0, 0);
                subjects.add(new Subject(new LaneRun(new Board(trial.source().playerRef(), trial.source().builderId(), List.of()),
                        player, lane, trial.budget(), arena.bounds(player.teamId()))));
            }
            tickPreparation();
        }

        private void replay(Subject subject, boolean deadline) {
            List<BuildAction> source = trial.source().actions();
            while (subject.sourceIndex < source.size()) {
                BuildAction action = source.get(subject.sourceIndex);
                if (action.round() > trial.round()) {subject.sourceIndex++; continue;}
                if (action.type() == BuildActionType.SUMMON || action.type() == BuildActionType.EMERALD_PRODUCTION_UPGRADE) {
                    observe(subject, action, "ECONOMY_ACTION_EXCLUDED_FROM_COMBAT", 0, 0, 0);
                    continue;
                }
                if (deadline) {
                    observe(subject, action, subject.attempts > 0 ? "PREPARATION_EXPIRED" : "EARLIER_ACTION_BLOCKED", 0, 0, 0);
                    continue;
                }
                ActionResult result = SeasonThreeReplaySupport.execute(game, subject.combat.player, action, 6);
                subject.attempts++;
                if (!result.success()) {subject.lastFailure = result.status();}
                if (!result.success() && result.retryable()) {return;}
                if (result.success()) {
                    subject.combat.purchases.add(new Purchase(subject.sourceIndex, action.type().name(), null,
                            action.subjectId(), -result.diamondsDelta()));
                }
                observe(subject, action, result.status(), result.diamondsDelta(), result.emeraldsDelta(), result.incomeDelta());
            }
        }

        private void observe(Subject subject, BuildAction action, String status, long diamonds, long emeralds, long income) {
            String divergence = "SUCCESS".equals(status)
                    ? SeasonThreeRecordedOperationGameTest.sourceEconomyDivergence(action,
                            new ActionResult(status, diamonds, emeralds, income), 0)
                    : null;
            if ((divergence != null || !"SUCCESS".equals(status) && !"ECONOMY_ACTION_EXCLUDED_FROM_COMBAT".equals(status))
                    && subject.firstDeviation == null) {subject.firstDeviation = subject.sourceIndex;}
            subject.actions.add(new ActionObservation(subject.sourceIndex, action,
                    SeasonThreeReplaySupport.position(subject.combat.lane, action),
                    SeasonThreeReplaySupport.resolvedPosition(subject.combat.lane, action), status, subject.lastFailure,
                    divergence, subject.attempts,
                    preparationTick, diamonds, emeralds, income));
            subject.sourceIndex++;
            subject.attempts = 0;
            subject.lastFailure = null;
        }

        private void tickPreparation() {
            if (preparationTick % 20 == 0) {subjects.forEach(subject -> replay(subject, false));}
            game.tick(level.getServer());
            preparationTick++;
            if (game.phase() == RoundPhase.PREPARE_AND_SUMMON) {
                context.runAfterDelay(1, () -> safely(this::tickPreparation));
                return;
            }
            require(game.phase() == RoundPhase.LANE_WAVE, "Preparation must transition to real lane combat.");
            subjects.forEach(subject -> {
                replay(subject, true);
                require(game.towerCapacityUsed(subject.combat.player.uuid()) <= 6,
                        "Recorded board exceeded the common six-slot ceiling.");
                require(game.towerCapacityUsed(subject.combat.player.uuid()) <= game.towerLimitForPlayer(subject.combat.player.uuid()),
                        "Recorded board exceeded the builder's own capacity.");
                require(subject.combat.lane.augmentSnapshot().selections().isEmpty(), "Baseline must have no augment selections.");
                subject.combat.initialTowers.addAll(subject.combat.lane.towers());
                subject.combat.capture(0);
            });
            context.runAfterDelay(1, () -> safely(this::tickCombat));
        }

        private void tickCombat() {
            combatTick++;
            controls.automaticActions(game);
            for (Subject subject : subjects) {
                LaneRun run = subject.combat;
                if (run.result != null) {continue;}
                run.lane.tick(level.getServer(), economy, game.players(), MonsterScalingConfig.defaultConfig(), combatTick);
                run.capture(combatTick);
            }
            if (subjects.stream().anyMatch(subject -> subject.combat.result == null)) {
                context.runAfterDelay(1, () -> safely(this::tickCombat));
                return;
            }
            require(towerHash.equals(SeasonThreeReplaySupport.hash(TowerBalanceRuntime.current())),
                    "Tower balance changed during recorded combat.");
            subjects.forEach(subject -> SeasonThreeBuilderBalanceGameTest.validateLane(subject.combat));
            measuredTrials++;
            report(subjects.stream().anyMatch(subject -> subject.firstDeviation != null) ? "PARTIAL_REPLAY" : "MEASURED", true,
                    trial.source().attemptedRounds().contains(trial.round()) ? null : "SOURCE_HISTORY_ENDED_EARLY");
            cleanupTrial();
            if (++trialIndex == trials.size()) {
                arena.close();
                arena = null;
                SemionTd.LOGGER.info("SEASON3_RECORDED_COMBAT_COMPLETE: {} paired observations, {} lane observations; repetitions are not independent players.",
                        measuredTrials, measuredTrials * 2);
                context.succeed();
            } else {
                context.runAfterDelay(40, () -> safely(this::startTrial));
            }
        }

        private void report(String status, boolean measured, String reason) {
            Sample source = trial.source();
            SemionTd.LOGGER.info("SEASON3_RECORDED_COMBAT_REPORT {}", JSON.toJson(new Report(status, measured, reason,
                    source.builderId(), source.matchId(), source.playerRef(), source.catalogVersion(), source.sourceTraits(), source.sourceMap(),
                    source.sourceFinalRound(), source.attemptedRounds(), trial.round(), trial.budget(), 6, trial.repeat(),
                    "COUNT_AND_HEALER_H0", "NONE", 0, combatTick, arenaWaitTicks, towerHash, SeasonThreeReplaySupport.canonicalHash(waves),
                    SeasonThreeReplaySupport.hash(economyConfig), arena == null ? null : SeasonThreeReplaySupport.hash(
                            List.of(arena.templateId(), arena.templateSha256(), MapConfig.defaultConfig(), List.of(1))),
                    arena == null ? null : arena.templateId(), arena == null ? null : arena.templateSha256(), LIMITS,
                    JSON.toJsonTree(controls == null ? List.of() : controls.events()),
                    subjects.stream().map(subject -> subject.snapshot(source.actions().size(),
                            controls == null ? 0 : controls.activationCount(subject.combat.player.uuid()))).toList())));
        }

        private void safely(Runnable action) {
            try {
                action.run();
            } catch (RuntimeException | AssertionError failure) {
                if (trial != null) {report("FIXTURE_FAILED_PARTIAL", false, failure.toString());}
                cleanupTrial();
                if (arena != null) {arena.close(); arena = null;}
                context.fail(Component.literal("Recorded combat fixture failed: " + failure));
            }
        }

        private void cleanupTrial() {
            if (game != null) {game.close(); game = null;}
            if (controls != null) {controls.close(); controls = null;}
            subjects.clear();
        }
    }
}
