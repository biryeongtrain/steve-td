package kim.biryeong.semiontd.gametest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentEconomyService;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.buildguide.BuildAction;
import kim.biryeong.semiontd.buildguide.BuildActionType;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.MapConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.MonsterSupportMetrics;
import kim.biryeong.semiontd.entity.monster.WaveHealingState;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.PlayerRoundMetricsSnapshot;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerRoundMetricsSnapshot;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.adversary.AdversaryRivalTower;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;

/** Real R1 economy replay of recorded actions, not a replay of missing opponents or player decisions. */
public final class SeasonThreeRecordedOperationGameTest {
    private static final String FILTER = "semion-td-gametest:season_three_recorded_operation_game_test_recorded_operations";
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private static final Set<Integer> CHECKPOINTS = Set.of(5, 15, 16, 25);
    private static final int WAVE_OBSERVATION_LIMIT = 6_000;
    private static final List<String> LIMITS = List.of("INTERNAL_RECORD_REPLAY_NOT_LIVE_BALANCE",
            "FOUR_CORRELATED_MIRRORS_NOT_FOUR_SOURCE_PLAYERS", "SOURCE_OPPONENTS_NOT_REPRODUCED",
            "NO_EXACT_ACTION_TICKS_PREPARATION_ORDER_REPLAY", "NO_SOURCE_TRAITS_APPLIED",
            "RECORDED_ACTIONS_PLUS_DISCLOSED_MISSING_CONTROLS", "UNSEEDED_ENTITY_AI_AND_RANDOM_RESULTS",
            "CURRENT_BUNDLED_MAP_NOT_CONFIRMED_SOURCE_MAP",
            "SPAWN_RECORD_ONLY_TERMINAL_KILLS_HAVE_NO_DIRECT_POSITION_OBSERVATION",
            "CONFIRMED_VANILLA_CRAMMING_DEATHS_ARE_NOT_PLAYER_OR_BOSS_KILLS",
            "OWNED_ADVERSARY_PROXY_DEATHS_ARE_NOT_REGULAR_REWARDED_KILLS",
            "NO_CLIENT_OR_NETWORK_EVIDENCE", "NO_FUTURE_SURVIVAL_OR_EARNINGS_INJECTION",
            "OPTIONAL_VARIANTS_ONE_R5_GOLD_CARD_NOT_THREE_AUGMENT_BUILDS");

    @GameTest(maxTicks = 20_000_000)
    public void recordedOperations(GameTestHelper context) {
        if (!Boolean.getBoolean("semiontd.baseline.operation")) {
            SemionTd.LOGGER.info("SEASON3_RECORDED_OPERATION_NOT_RUN: opt-in property absent.");
            context.succeed();
            return;
        }
        if (!FILTER.equals(System.getProperty("fabric-api.gametest.filter"))
                || !(context.getLevel().getServer() instanceof GameTestServer)) {
            context.fail(Component.literal("Recorded operation fixture requires its exact isolated filter and headless GameTestServer."));
            return;
        }
        try {
            List<Scenario> scenarios = new ArrayList<>();
            Group group = Group.valueOf(System.getProperty("semiontd.baseline.operation.group", "NONE"));
            for (var sample : SeasonThreeReplaySupport.samples()) {
                for (Stage stage : Stage.values()) { scenarios.add(new Scenario(sample, stage, group)); }
            }
            require(!scenarios.isEmpty(), "No source records match the supplied filter.");
            new Run(context, scenarios, 0, null).start();
        } catch (RuntimeException | AssertionError failure) {
            context.fail(Component.literal("Cannot initialize recorded operation fixture: " + failure));
        }
    }

    @GameTest(maxTicks = 1)
    public void sourceEconomyComparisonPreservesRecordedSemantics(GameTestHelper context) {
        var exact = new SeasonThreeReplaySupport.ActionResult("SUCCESS", -100, 0, 0);
        BuildAction upgrade = BuildAction.towerUpgradeRelative(5, "upgrade", new GridPosition(1, 0, 1), 100);
        require(sourceEconomyDivergence(upgrade, exact, 0) == null, "An equal actual cost is not an economic divergence.");
        require("SOURCE_DIAMOND_COST_CHANGED".equals(sourceEconomyDivergence(upgrade,
                new SeasonThreeReplaySupport.ActionResult("SUCCESS", -90, 0, 0), 0)), "A successful discounted upgrade still differs from its source cost.");
        BuildAction sale = BuildAction.towerSellRelative(5, "tower", new GridPosition(1, 0, 1), 80);
        require("SOURCE_SALE_REFUND_CHANGED".equals(sourceEconomyDivergence(sale,
                new SeasonThreeReplaySupport.ActionResult("SUCCESS", 75, 0, 80), 0)), "Sale incomeGain is a diamond refund, not regular income.");
        BuildAction production = BuildAction.emeraldProductionUpgrade(1, 1, 30, 1);
        require(sourceEconomyDivergence(production, new SeasonThreeReplaySupport.ActionResult("SUCCESS", 0, -30, 0), 1) == null,
                "Source production currency is unknown; equal paid amount and EPS are comparable.");
        require("SOURCE_PRODUCTION_RATE_CHANGED".equals(sourceEconomyDivergence(production,
                new SeasonThreeReplaySupport.ActionResult("SUCCESS", -30, 0, 1), 0)), "Production incomeGain is EPS, not regular income.");
        BuildAction summon = BuildAction.summon(5, "zombie", 40, 2, 5, "BLUE", 1);
        require("SOURCE_SUMMON_INCOME_CHANGED".equals(sourceEconomyDivergence(summon,
                new SeasonThreeReplaySupport.ActionResult("SUCCESS", 60, -40, 0), 0)), "Cash diamonds do not reproduce recorded regular summon income.");
        Monster dead = Monster.fromWaveEntry(WaveConfig.defaultConfig().configForRound(6).orElseThrow()
                .entriesForLane("lane_1").getFirst(), TeamId.RED, 1, MonsterOrigin.NATURAL_WAVE);
        dead.syncHealth(0);
        require(!confirmedCrammingDeath(dead, null), "Unknown deaths without actual entity evidence remain unexplained.");
        require(confirmedCrammingDeath(dead, new VanillaDeath("cramming", false)), "A dead uncredited monster with actual cramming evidence is environmental.");
        require(!confirmedCrammingDeath(dead, new VanillaDeath("cramming", true)), "An attributed hit must not become environmental damage.");
        dead.recordBossHit();
        require(!confirmedCrammingDeath(dead, new VanillaDeath("cramming", false)), "Boss kills must not also become environmental kills.");
        context.succeed();
    }

    @GameTest(maxTicks = 1)
    public void unrewardedProxyDeathsRequireAnObservedOwnedRival(GameTestHelper context) {
        var entry = WaveConfig.defaultConfig().configForRound(6).orElseThrow().entriesForLane("lane_1").getFirst();
        Monster proxy = Monster.fromWaveEntry(entry, TeamId.RED, 1, MonsterOrigin.BUILDER_PROXY);
        proxy.recordLastHit(UUID.randomUUID(), KillSourceKind.TOWER);
        require(!confirmedRivalDeath(proxy, true), "A living rival is not a proxy death.");
        proxy.syncHealth(0);
        require(!confirmedRivalDeath(proxy, false), "An unverified builder proxy must remain unexplained.");
        require(confirmedRivalDeath(proxy, true), "A verified dead rival is separate from regular rewarded kills.");
        Monster natural = Monster.fromWaveEntry(entry, TeamId.RED, 1, MonsterOrigin.NATURAL_WAVE);
        natural.syncHealth(0);
        natural.recordLastHit(UUID.randomUUID(), KillSourceKind.TOWER);
        require(!confirmedRivalDeath(natural, true), "An uncredited natural death must never become a rival death.");
        proxy.markRewardGranted();
        require(!confirmedRivalDeath(proxy, true), "A rewarded monster must not also count as an unrewarded rival death.");
        context.succeed();
    }

    private enum Stage { BASE, HEALER_ONLY, COUNT_AND_HEALER }
    private enum Group { NONE, ATTACK, DEFENSE, INCOME }
    private record Scenario(SeasonThreeReplaySupport.Sample sample, Stage stage, Group group) {}
    private record Balance(long diamonds, long emeralds, long income, long emeraldPerSecond,
            int productionUpgrades, int towerLimitPurchases) {}
    private record ActionObservation(int sourceIndex, int round, BuildActionType type, String subjectId,
            GridPosition translatedPosition, GridPosition resolvedPosition,
            String status, String divergenceReason, int attempts, long firstAttemptTick, long lastAttemptTick,
            long sourceCost, long sourceIncomeGain, String sourceCostCurrency,
            long diamondsDelta, long emeraldsDelta, long incomeDelta, long emeraldProductionDelta) {}
    private record TowerState(int towerRef, String typeId, GridPosition relativePosition, double health,
            double maxHealth, long paidDiamonds, List<String> runtimeDetails) {}
    private record Checkpoint(int round, String phase, Balance balance, List<TowerState> towers,
            long tick, String towerStateTiming, long towerStateTick) {}
    private record Payout(int round, long tick, Balance before, Balance after, long actualDiamondDelta) {}
    private record Automation(int round, long tick, String policy, Integer targetRef) {}
    /** Captured only while the real LivingEntity has zero health, before its last damage source expires. */
    private record VanillaDeath(String damageType, boolean hasAttacker) {}
    private record MonsterDiagnostic(String typeId, MonsterOrigin origin, UUID verifiedRivalId,
            String state, double health, KillSourceKind lastHitSource,
            boolean leaked, Double x, Double y, Double z, String removalReason, VanillaDeath vanillaDeath) {}
    private record Accounting(int observed, int entityConfirmed, int terminalSpawnRecordOnly, int playerKilled, int bossKilled,
            int environmentalDeaths, int builderProxiesObserved, int builderProxyDeaths,
            int leakedNotKilled, int active, int endpointCleanup, int unexplained, int belowFloor, int outsideMap, int unforcedChunk,
            List<MonsterDiagnostic> diagnostics) {
        boolean valid() { return observed == entityConfirmed + terminalSpawnRecordOnly && unexplained == 0
                && observed == playerKilled + bossKilled + environmentalDeaths + builderProxyDeaths + leakedNotKilled + active + endpointCleanup + unexplained
                && belowFloor == 0 && outsideMap == 0 && unforcedChunk == 0; }
    }
    private record Battle(int round, String status, int observedWaveTicks, int naturalSpawnsExpected,
            int naturalSpawnsObserved, int paidSpawnsObserved, double naturalStartingHealth, int leaks,
            double leakedThreat, boolean laneCleared, boolean defenseBroken, double bossHealth,
            long playerRewardedKills, List<TowerRoundMetricsSnapshot> towers, WaveHealingState.Snapshot healing,
            MonsterSupportMetrics.Snapshot naturalSupport, MonsterSupportMetrics.Snapshot utilitySupport,
            Accounting accounting) {}
    private record SubjectReport(TeamId team, int lane, String status, ActionObservation firstDivergence,
            int actionsNotReached, List<ActionObservation> actions, List<Checkpoint> checkpoints,
            List<Battle> battles, List<Payout> payouts, Balance finalBalance,
            List<PlayerAugmentState.Selection> augmentSelections, String augmentStatus, List<Automation> automation,
            List<SeasonThreeReplayControls.Activation> controlActions) {}
    private record Report(String status, String reason, String builderId, String matchId, String playerRef,
            String sourceCatalogVersion, JsonElement sourceTraits, JsonElement sourceMap,
            List<Integer> sourceAttemptedRounds, int sourceFinalRound, int replayEndRound,
            Stage stage, Group group, String towerBalanceSha256, String economySha256, String waveSha256,
            String augmentSha256, String sourceActionsSha256, String replayMapTemplateId, String replayMapConfigSha256,
            String replayMapTemplateSha256,
            long observedTicks, int arenaReadyWaitTicks, double wallSeconds,
            List<String> limits, List<SubjectReport> subjects) {}

    private static final class Subject {
        final SemionPlayer player;
        final PlayerLane lane;
        final List<ActionObservation> actions = new ArrayList<>();
        final List<Checkpoint> checkpoints = new ArrayList<>();
        final List<Battle> battles = new ArrayList<>();
        final List<Payout> payouts = new ArrayList<>();
        final List<Automation> automation = new ArrayList<>();
        final Map<UUID, Integer> towerRefs = new LinkedHashMap<>();
        final Map<UUID, Monster> spawned = new LinkedHashMap<>();
        final Map<UUID, Entity> entities = new LinkedHashMap<>();
        final Map<UUID, VanillaDeath> vanillaDeaths = new LinkedHashMap<>();
        final Map<UUID, UUID> rivalProxySources = new LinkedHashMap<>();
        final Set<UUID> belowFloor = new HashSet<>();
        final Set<UUID> outsideMap = new HashSet<>();
        final Set<UUID> unforcedChunk = new HashSet<>();
        Set<UUID> activeBeforeTick = Set.of();
        int actionIndex;
        int attemptCount;
        long firstAttemptTick;
        long lastAttemptTick;
        long nextAttemptTick;
        long killedAtWaveStart;
        int naturalSpawnsExpected;
        double naturalStartingHealth;
        long augmentOfferTick = -1;
        int cashContractRound;
        String augmentStatus = "NOT_REACHED";
        SeasonThreeReplaySupport.ActionResult lastAttempt;
        long lastProductionDelta;
        List<TowerState> preTickTowers = List.of();
        long preTickTowerStateTick = -1;

        Subject(SemionPlayer player, PlayerLane lane) { this.player = player; this.lane = lane; }
    }

    private static final class Run {
        final GameTestHelper context;
        final ServerLevel level;
        final List<Scenario> scenarios;
        final int scenarioIndex;
        final Scenario scenario;
        final SeasonThreeReplaySupport.Sample sample;
        final List<Subject> subjects = new ArrayList<>();
        final WaveConfig waves;
        final AugmentConfig augments;
        final String towerHash = SeasonThreeReplaySupport.hash(TowerBalanceRuntime.current());
        final int replayEndRound;
        SemionGame game;
        SeasonThreeReplayArena.ArenaFixture arena;
        SeasonThreeReplayControls controls;
        long started;
        long observedTicks;
        int preparedRound;
        int waveTicks;
        int arenaReadyWaitTicks;

        Run(GameTestHelper context, List<Scenario> scenarios, int scenarioIndex, SeasonThreeReplayArena.ArenaFixture arena) {
            this.context = context;
            this.scenarios = scenarios;
            this.scenarioIndex = scenarioIndex;
            this.arena = arena;
            scenario = scenarios.get(scenarioIndex);
            sample = scenario.sample();
            level = context.getLevel();
            waves = WaveConfig.defaultConfig().withSeason3Stages(scenario.stage() == Stage.COUNT_AND_HEALER,
                    scenario.stage() != Stage.BASE, Set.of());
            var defaults = AugmentConfig.defaults();
            Map<String, Integer> weights = new LinkedHashMap<>();
            defaults.rarityWeights().keySet().forEach(key -> weights.put(key, key.equals("GGG") ? 100 : 0));
            augments = new AugmentConfig(scenario.group() != Group.NONE, false, weights, defaults.parameters(), defaults.disabledIds());
            int recordedEnd = sample.attemptedRounds() == null || sample.attemptedRounds().isEmpty()
                    ? sample.actions().stream().mapToInt(BuildAction::round).max().orElse(0)
                    : sample.attemptedRounds().stream().mapToInt(Integer::intValue).max().orElseThrow();
            replayEndRound = Math.min(25, recordedEnd);
        }

        void start() {
            safely(() -> {
                String exclusion = SeasonThreeReplaySupport.exclusion(sample.builderId()).orElse(null);
                if (exclusion != null) { finish("EXCLUDED_UNRECORDED_CHOICES", exclusion); return; }
                if (sample.attemptedRounds() != null && !sample.attemptedRounds().isEmpty() && !sample.attemptedRounds().contains(1)) {
                    finish("PARTIAL_REPLAY", "NO_RECORDED_R1_PARTICIPATION");
                    return;
                }
                if (replayEndRound < 1) { finish("UNREPRODUCIBLE", "SOURCE_ENDPOINT_MISSING"); return; }
                if (arena == null) { arena = SeasonThreeReplayArena.createArena(context, BlockPos.ZERO.offset(4096, 0, 1024), 2); }
                game = new SemionGame(EconomyConfig.defaultConfig(), waves, arena.gameArena());
                game.configureAugments(augments);
                context.runAfterDelay(1, () -> safely(this::awaitArenaReady));
            });
        }

        private void awaitArenaReady() {
            if (arena.entitiesReady()) { startMatch(); return; }
            require(++arenaReadyWaitTicks <= 10_000, "Replay arena chunks did not become entity-ticking before match start.");
            context.runAfterDelay(1, () -> safely(this::awaitArenaReady));
        }

        private void startMatch() {
            List<AssignedParticipant> participants = new ArrayList<>();
            for (TeamId team : List.of(TeamId.RED, TeamId.BLUE)) {
                for (int lane = 1; lane <= 2; lane++) {
                    UUID id = UUID.nameUUIDFromBytes(("s3-operation-" + scenarioIndex + "-" + team + "-" + lane).getBytes(StandardCharsets.UTF_8));
                    participants.add(new AssignedParticipant(id, "operation-" + team + "-" + lane, team, lane));
                    if (!game.selectJob(id, ResourceLocation.parse(sample.builderId()))) {
                        finish("UNREPRODUCIBLE", "SOURCE_BUILDER_UNAVAILABLE");
                        return;
                    }
                }
            }
            require(game.start(level.getServer(), new ParticipantSelectionPlan(MatchMode.NORMAL, participants, Set.of(), 2)),
                    "Actual NORMAL match did not start.");
            require(game.currentRound() == 1 && game.phase() == RoundPhase.PREPARE_AND_SUMMON,
                    "Operation replay must start at R1 without jumping checkpoints.");
            for (AssignedParticipant participant : participants) {
                SemionPlayer player = game.players().get(participant.uuid());
                subjects.add(new Subject(player, game.playerLane(player.uuid()).orElseThrow()));
            }
            if (sample.builderId().equals("semion-td:frost")) {
                Map<UUID, String> names = new LinkedHashMap<>();
                participants.forEach(participant -> names.put(participant.uuid(), participant.name()));
                controls = SeasonThreeReplayControls.attach(context, game, names);
            }
            started = System.nanoTime();
            context.runAfterDelay(1, () -> safely(this::tick));
        }

        private void tick() {
            int round = game.currentRound();
            RoundPhase beforePhase = game.phase();
            if (beforePhase == RoundPhase.PREPARE_AND_SUMMON) {
                if (preparedRound != round) {
                    require(towerHash.equals(SeasonThreeReplaySupport.hash(TowerBalanceRuntime.current())),
                            "Tower balance changed during recorded operation replay.");
                    preparedRound = round;
                    waveTicks = 0;
                    for (Subject subject : subjects) {
                        subject.nextAttemptTick = game.currentTick();
                        checkpoint(subject, round, "PREPARE_START");
                    }
                }
                for (Subject subject : subjects) {
                    if (!game.teams().get(subject.player.teamId()).eliminated()) {
                        selectAugment(subject);
                        if (scenario.group() != Group.NONE && round == 5 && subject.augmentStatus.equals("NOT_REACHED")) { continue; }
                        if (scenario.group() == Group.INCOME && subject.augmentStatus.equals("ONE_R5_GOLD_SELECTED")
                                && subject.cashContractRound < round
                                && AugmentEconomyService.setContract(subject.player, round, AugmentEconomyService.Contract.CASH)) {
                            subject.cashContractRound = round;
                            subject.automation.add(new Automation(round, game.currentTick(), "CASH_CONTRACT_NEXT_ELIGIBLE_RECORDED_SUMMON", null));
                        }
                        executeActions(subject);
                    }
                }
            }
            List<Balance> beforePayout = beforePhase == RoundPhase.ROUND_PAYOUT
                    ? subjects.stream().map(subject -> balance(subject.player)).toList() : List.of();
            for (Subject subject : subjects) {
                capture(subject);
                subject.activeBeforeTick = subject.lane.activeMonsters().stream().map(Monster::logicalId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
                if (beforePhase == RoundPhase.LANE_WAVE && CHECKPOINTS.contains(round)) {
                    subject.preTickTowers = towerStates(subject);
                    subject.preTickTowerStateTick = game.currentTick();
                }
            }
            if (controls != null) { controls.automaticActions(game); }
            game.tick(level.getServer());
            observedTicks++;
            subjects.forEach(this::capture);
            if (beforePhase == RoundPhase.PREPARE_AND_SUMMON && game.phase() != beforePhase) {
                for (Subject subject : subjects) {
                    expireActions(subject, round);
                    subject.killedAtWaveStart = subject.player.matchStats().monsterKills();
                    require(subject.lane.naturalWaveCount() != null && subject.lane.naturalWaveStartingHealth() != null,
                            "Actual wave-start natural metrics were not captured.");
                    subject.naturalSpawnsExpected = subject.lane.naturalWaveCount();
                    subject.naturalStartingHealth = subject.lane.naturalWaveStartingHealth();
                    checkpoint(subject, round, "WAVE_START");
                }
            }
            if (beforePhase == RoundPhase.LANE_WAVE) {
                waveTicks++;
                if (game.phase() != beforePhase || waveTicks >= WAVE_OBSERVATION_LIMIT) {
                    recordBattles(round);
                    if (subjects.stream().anyMatch(subject -> !subject.battles.getLast().accounting().valid())) {
                        throw new AssertionError("Monster accounting invalidated this fixture; do not interpret its balance results.");
                    }
                    if (game.phase() == RoundPhase.ENDED) { finish("OBSERVED_MATCH_ENDPOINT", "ACTUAL_GAME_ENDED"); return; }
                    if (game.phase() == RoundPhase.LANE_WAVE && waveTicks >= WAVE_OBSERVATION_LIMIT) {
                        finish("CENSORED", "WAVE_OBSERVATION_TIME_LIMIT"); return;
                    }
                    if (round >= replayEndRound) {
                        finish("OBSERVED_HISTORY_ENDPOINT", round == 25 ? "R25_CHECKPOINT_LIMIT" : "SOURCE_HISTORY_ENDED_WITHOUT_FUTURE_PAYOUT");
                        return;
                    }
                }
            }
            if (beforePhase == RoundPhase.ROUND_PAYOUT) {
                require(game.currentRound() == round + 1 && game.phase() == RoundPhase.PREPARE_AND_SUMMON,
                        "Only actual round payout may advance the round.");
                for (int index = 0; index < subjects.size(); index++) {
                    Subject subject = subjects.get(index);
                    Balance after = balance(subject.player);
                    subject.payouts.add(new Payout(round, game.currentTick(), beforePayout.get(index), after,
                            after.diamonds() - beforePayout.get(index).diamonds()));
                    checkpoint(subject, round, "AFTER_ACTUAL_PAYOUT");
                    subject.spawned.clear(); subject.entities.clear(); subject.belowFloor.clear();
                    subject.vanillaDeaths.clear();
                    subject.rivalProxySources.clear();
                    subject.outsideMap.clear(); subject.unforcedChunk.clear();
                }
            }
            context.runAfterDelay(1, () -> safely(this::tick));
        }

        private void executeActions(Subject subject) {
            if (game.currentTick() < subject.nextAttemptTick) { return; }
            while (subject.actionIndex < sample.actions().size()) {
                BuildAction action = sample.actions().get(subject.actionIndex);
                if (action.round() > game.currentRound()) { return; }
                if (action.round() < game.currentRound()) { recordAction(subject, action, "SOURCE_ORDER_OR_ROUND_NOT_REPRODUCIBLE", null); continue; }
                if (subject.attemptCount == 0) { subject.firstAttemptTick = game.currentTick(); }
                subject.attemptCount++;
                subject.lastAttemptTick = game.currentTick();
                long productionBefore = subject.player.economy().emeraldPerSec();
                subject.lastAttempt = SeasonThreeReplaySupport.execute(game, subject.player, action, Integer.MAX_VALUE);
                subject.lastProductionDelta = subject.player.economy().emeraldPerSec() - productionBefore;
                require(subject.lastAttempt.success() || subject.lastAttempt.diamondsDelta() == 0
                                && subject.lastAttempt.emeraldsDelta() == 0 && subject.lastAttempt.incomeDelta() == 0,
                        "A failed recorded action changed the player's economy.");
                if (subject.lastAttempt.retryable()) { subject.nextAttemptTick = game.currentTick() + 20; return; }
                if (subject.lastAttempt.success() && action.type() == BuildActionType.TOWER_PLACE) {
                    Tower tower = subject.lane.towerAt(SeasonThreeReplaySupport.resolvedPosition(subject.lane, action));
                    if (tower != null) { subject.towerRefs.computeIfAbsent(tower.logicalId(), ignored -> subject.towerRefs.size() + 1); }
                }
                recordAction(subject, action, subject.lastAttempt.status(), subject.lastAttempt);
            }
        }

        private void expireActions(Subject subject, int round) {
            while (subject.actionIndex < sample.actions().size() && sample.actions().get(subject.actionIndex).round() <= round) {
                BuildAction action = sample.actions().get(subject.actionIndex);
                recordAction(subject, action, subject.attemptCount == 0 ? "NOT_ATTEMPTED_BEHIND_EXPIRED_ACTION"
                        : "PREPARATION_EXPIRED_" + subject.lastAttempt.status(), subject.lastAttempt);
            }
        }

        private void recordAction(Subject subject, BuildAction action, String status, SeasonThreeReplaySupport.ActionResult result) {
            subject.actions.add(new ActionObservation(subject.actionIndex, action.round(), action.type(), action.subjectId(),
                    SeasonThreeReplaySupport.position(subject.lane, action), SeasonThreeReplaySupport.resolvedPosition(subject.lane, action), status,
                    "SUCCESS".equals(status) ? sourceEconomyDivergence(action, result, subject.lastProductionDelta) : status,
                    subject.attemptCount, subject.attemptCount == 0 ? -1 : subject.firstAttemptTick,
                    subject.attemptCount == 0 ? -1 : subject.lastAttemptTick, action.cost(), action.incomeGain(),
                    action.type() == BuildActionType.EMERALD_PRODUCTION_UPGRADE ? "UNRECORDED"
                            : action.type() == BuildActionType.SUMMON ? "EMERALD" : "DIAMOND",
                    result == null ? 0 : result.diamondsDelta(), result == null ? 0 : result.emeraldsDelta(),
                    result == null ? 0 : result.incomeDelta(), subject.lastProductionDelta));
            subject.actionIndex++;
            subject.attemptCount = 0;
            subject.lastAttempt = null;
            subject.lastProductionDelta = 0;
            subject.nextAttemptTick = game.currentTick();
        }

        private void selectAugment(Subject subject) {
            if (scenario.group() == Group.NONE || game.currentRound() != 5 || !subject.augmentStatus.equals("NOT_REACHED")) { return; }
            if (subject.augmentOfferTick < 0) {
                subject.augmentOfferTick = game.currentTick();
                subject.player.augments().forceOffer(5, 5, game.currentTick() + kim.biryeong.semiontd.augment.AugmentService.PREPARE_TICKS,
                        List.of("semiontd:cash_settlement", "semiontd:tactical_designation_2", "semiontd:reserve_income_gold"));
                return;
            }
            if (game.currentTick() < subject.augmentOfferTick + 20) { return; }
            boolean income = scenario.group() == Group.INCOME;
            String cardId = "semiontd:" + (income ? "cash_settlement" : "tactical_designation_2");
            Tower target = game.augmentService().eligibleTargets(game, subject.player, cardId).stream()
                    .min(java.util.Comparator.comparingInt(tower -> subject.towerRefs.getOrDefault(tower.logicalId(), Integer.MAX_VALUE))).orElse(null);
            if (!income && target == null) { subject.augmentStatus = "NO_ELIGIBLE_TARGET"; return; }
            if (!game.augmentService().isEligible(game, subject.player, cardId)) { subject.augmentStatus = "CARD_NOT_ELIGIBLE"; return; }
            AugmentChoice choice = income ? AugmentChoice.none() : new AugmentChoice(target.logicalId(), null,
                    scenario.group() == Group.ATTACK ? "ASSAULT" : "COVER");
            var offer = subject.player.augments().currentOffer().orElseThrow();
            require(subject.player.augments().draft(5, income ? 0 : 1, offer.revision(), offer.draftRevision(), choice,
                    UUID.randomUUID(), game.currentTick(), card -> game.augmentService().isEligible(game, subject.player, card.id())).successful(),
                    "Fixed R5 variant draft failed.");
            var draft = subject.player.augments().currentOffer().orElseThrow();
            require(subject.player.augments().confirm(5, draft.revision(), draft.draftRevision(), UUID.randomUUID(), game.currentTick(),
                    card -> game.augmentService().isEligible(game, subject.player, card.id()), (card, checkedChoice) -> {
                        AugmentEconomyService.onSelected(subject.player, card.id(), 5, augments.parameters().getOrDefault(card.id(), Map.of()));
                        return true;
                    }).successful(), "Fixed R5 variant confirmation failed.");
            subject.lane.assignAugmentSnapshot(subject.player.augments().snapshot());
            subject.augmentStatus = "ONE_R5_GOLD_SELECTED";
            subject.automation.add(new Automation(5, game.currentTick(), income ? "FIXED_R5_CASH_SETTLEMENT"
                    : "FIXED_R5_TACTICAL_" + choice.mode() + "_EARLIEST_ELIGIBLE_PLACEMENT",
                    target == null ? null : subject.towerRefs.get(target.logicalId())));
        }

        private void capture(Subject subject) {
            for (Monster monster : SeasonThreeReplaySupport.observedRoundMonsters(subject.lane)) {
                subject.spawned.putIfAbsent(monster.logicalId(), monster);
            }
            // Rivals enter activeMonsters at wave start, before the retained round ledger sees them.
            // Capture that real entity now, and retain the exact logical source through its no-reward death.
            for (Tower tower : subject.lane.towers()) {
                if (!(tower instanceof AdversaryRivalTower rival) || !rival.convertedForWave()) { continue; }
                for (Monster candidate : subject.lane.activeMonsters()) {
                    if (candidate.origin() != MonsterOrigin.BUILDER_PROXY || !candidate.hasMinecraftEntity()
                            || !AdversaryRivalTower.isOwnedRival(candidate, subject.player.uuid())
                            || AdversaryRivalTower.logicalRivalIdOf(candidate).filter(rival.rivalId()::equals).isEmpty()) { continue; }
                    if (!subject.rivalProxySources.containsKey(candidate.logicalId())) {
                        require(!subject.rivalProxySources.containsValue(rival.rivalId()), "A rival transformed into multiple proxies in one wave.");
                        subject.rivalProxySources.put(candidate.logicalId(), rival.rivalId());
                    }
                    subject.spawned.putIfAbsent(candidate.logicalId(), candidate);
                }
            }
            for (Monster monster : subject.spawned.values()) {
                Entity entity = level.getEntity(monster.minecraftEntityId());
                if (entity != null) { subject.entities.put(monster.logicalId(), entity); }
            }
            subject.entities.forEach((id, entity) -> {
                if (entity instanceof LivingEntity living && living.getHealth() <= 0 && living.getLastDamageSource() != null) {
                    var damage = living.getLastDamageSource();
                    subject.vanillaDeaths.putIfAbsent(id, new VanillaDeath(damage.getMsgId(), damage.getEntity() != null));
                }
                if (entity.isRemoved()) { return; }
                var bounds = arena.bounds(subject.player.teamId());
                if (entity.getY() < bounds.min().getY()) { subject.belowFloor.add(id); }
                if (entity.getX() < bounds.min().getX() || entity.getX() >= bounds.max().getX() + 1
                        || entity.getZ() < bounds.min().getZ() || entity.getZ() >= bounds.max().getZ() + 1) {
                    subject.outsideMap.add(id);
                }
                if (!level.getForceLoadedChunks().contains(ChunkPos.asLong(entity.blockPosition().getX() >> 4,
                        entity.blockPosition().getZ() >> 4))) { subject.unforcedChunk.add(id); }
            });
        }

        private Accounting accounting(Subject subject) {
            int playerKilled = 0, bossKilled = 0, environmentalDeaths = 0, proxyDeaths = 0, leaked = 0, active = 0, cleanup = 0, unknown = 0, terminalRecordOnly = 0;
            List<MonsterDiagnostic> diagnostics = new ArrayList<>();
            for (Monster monster : subject.spawned.values()) {
                Entity entity = subject.entities.get(monster.logicalId());
                VanillaDeath vanillaDeath = subject.vanillaDeaths.get(monster.logicalId());
                UUID rivalId = subject.rivalProxySources.get(monster.logicalId());
                boolean rivalDeath = confirmedRivalDeath(monster, rivalId != null);
                boolean environmental = confirmedCrammingDeath(monster, vanillaDeath);
                boolean legalTerminalRecord = entity == null && monster.hasMinecraftEntity()
                        && monster.origin() != MonsterOrigin.BUILDER_PROXY
                        && (monster.rewardGranted() || monster.health() <= 0 && monster.lastHitSourceKind() == KillSourceKind.BOSS);
                if (legalTerminalRecord) { terminalRecordOnly++; }
                boolean unexplained = false;
                if (monster.rewardGranted()) { playerKilled++; }
                else if (rivalDeath) { proxyDeaths++; }
                else if (monster.health() <= 0 && monster.lastHitSourceKind() == KillSourceKind.BOSS) { bossKilled++; }
                else if (environmental) { environmentalDeaths++; }
                else if (monster.isRemoved() && subject.activeBeforeTick.contains(monster.logicalId())
                        && (game.phase() == RoundPhase.ENDED || game.teams().get(subject.player.teamId()).eliminated())) { cleanup++; }
                else if (monster.health() > 0 && monster.laneLeakRecorded()) { leaked++; }
                else if (monster.health() > 0 && !monster.isRemoved() && entity != null && !entity.isRemoved()) { active++; }
                else { unknown++; unexplained = true; }
                if (unexplained || environmental || rivalId != null || entity == null || subject.belowFloor.contains(monster.logicalId())
                        || subject.outsideMap.contains(monster.logicalId()) || subject.unforcedChunk.contains(monster.logicalId())) {
                    diagnostics.add(new MonsterDiagnostic(monster.id(), monster.origin(), rivalId, monster.state().name(), monster.health(), monster.lastHitSourceKind(),
                            monster.laneLeakRecorded(), entity == null ? null : entity.getX(), entity == null ? null : entity.getY(),
                            entity == null ? null : entity.getZ(), entity == null || entity.getRemovalReason() == null ? null : entity.getRemovalReason().name(), vanillaDeath));
                }
            }
            return new Accounting(subject.spawned.size(), subject.entities.size(), terminalRecordOnly, playerKilled, bossKilled, environmentalDeaths,
                    subject.rivalProxySources.size(), proxyDeaths, leaked, active,
                    cleanup, unknown, subject.belowFloor.size(), subject.outsideMap.size(), subject.unforcedChunk.size(), List.copyOf(diagnostics));
        }

        private void recordBattles(int round) {
            for (Subject subject : subjects) {
                var team = game.teams().get(subject.player.teamId());
                PlayerRoundMetricsSnapshot saved = game.matchResult().stream().flatMap(result -> result.participants().stream())
                        .filter(participant -> participant.playerId().equals(subject.player.uuid())).flatMap(participant -> participant.roundMetrics().stream())
                        .filter(metrics -> metrics.round() == round).findFirst().orElse(null);
                if (game.phase() == RoundPhase.ENDED) {
                    require(saved != null && saved.naturalWaveCount() != null && saved.naturalWaveStartingHealth() != null,
                            "Ended match must preserve measured natural-wave metrics before lane cleanup.");
                    require(saved.naturalWaveCount() == subject.naturalSpawnsExpected
                                    && Double.compare(saved.naturalWaveStartingHealth(), subject.naturalStartingHealth) == 0,
                            "Saved terminal wave definition differs from the actual wave-start capture.");
                }
                var utility = game.teams().values().stream().flatMap(value -> value.laneGroup().lanes().stream())
                        .map(lane -> lane.utilitySupportMetrics(subject.player.uuid()))
                        .reduce(MonsterSupportMetrics.Snapshot.empty(), MonsterSupportMetrics.Snapshot::plus);
                subject.battles.add(new Battle(round, team.eliminated() ? "TEAM_ELIMINATED"
                        : game.phase() == RoundPhase.LANE_WAVE ? "CENSORED" : "OBSERVED_ROUND_ENDPOINT", waveTicks,
                        subject.naturalSpawnsExpected, (int) subject.spawned.values().stream().filter(monster -> monster.origin() == MonsterOrigin.NATURAL_WAVE).count(),
                        (int) subject.spawned.values().stream().filter(monster -> monster.origin() == MonsterOrigin.NORMAL_PAID).count(),
                        subject.naturalStartingHealth, subject.lane.leakedCountThisRound(), subject.lane.leakedThreatThisRound(),
                        subject.lane.clearedThisRound() && !team.eliminated(), subject.lane.laneDefenseBroken(), team.laneGroup().boss().health(),
                        subject.player.matchStats().monsterKills() - subject.killedAtWaveStart,
                        saved == null ? subject.lane.roundTowerMetrics() : saved.towerMetrics(),
                        saved == null ? subject.lane.waveSupportMetrics() : saved.naturalWaveMetrics(),
                        saved == null ? subject.lane.naturalWaveSupportMetrics() : saved.waveSupportMetrics(),
                        saved == null ? utility : saved.utilitySupportMetrics(), accounting(subject)));
                checkpoint(subject, round, "COMBAT_ENDPOINT");
            }
        }

        private void checkpoint(Subject subject, int round, String phase) {
            if (!CHECKPOINTS.contains(round)) { return; }
            boolean terminalCleanup = phase.equals("COMBAT_ENDPOINT") && game.teams().get(subject.player.teamId()).eliminated();
            if (terminalCleanup) {
                require(subject.preTickTowerStateTick == game.currentTick() - 1,
                        "Eliminated checkpoint requires its immediately preceding tower/growth observation.");
            }
            subject.checkpoints.add(new Checkpoint(round, phase, balance(subject.player),
                    terminalCleanup ? subject.preTickTowers : towerStates(subject), game.currentTick(),
                    terminalCleanup ? "PRE_TERMINAL_TICK" : "CURRENT_TICK",
                    terminalCleanup ? subject.preTickTowerStateTick : game.currentTick()));
        }

        private List<TowerState> towerStates(Subject subject) {
            BlockPos origin = subject.lane.laneLayout().laneArea().min();
            return subject.lane.towers().stream().map(tower -> new TowerState(
                    subject.towerRefs.computeIfAbsent(tower.logicalId(), ignored -> subject.towerRefs.size() + 1), tower.type().id(),
                    new GridPosition(tower.originalPosition().x() - origin.getX(), tower.originalPosition().y() - origin.getY(), tower.originalPosition().z() - origin.getZ()),
                    tower.health(), tower.currentMaxHealth(), tower.paidMineralCost(), List.copyOf(tower.runtimeDetailLines()))).toList();
        }

        private void report(String status, String reason) {
            List<SubjectReport> reports = subjects.stream().map(subject -> {
                ActionObservation first = subject.actions.stream().filter(action -> action.divergenceReason() != null).findFirst().orElse(null);
                boolean partial = first != null || sample.attemptedRounds() == null || sample.attemptedRounds().isEmpty();
                return new SubjectReport(subject.player.teamId(), subject.player.laneId(), partial ? "PARTIAL_REPLAY" : status,
                        first, sample.actions().size() - subject.actionIndex, List.copyOf(subject.actions), List.copyOf(subject.checkpoints),
                        List.copyOf(subject.battles), List.copyOf(subject.payouts), balance(subject.player),
                        subject.player.augments().selections(), scenario.group() == Group.NONE ? "NONE_CONTROL" : subject.augmentStatus,
                        List.copyOf(subject.automation), controls == null ? List.of() : controls.events().stream()
                                .filter(event -> event.playerId().equals(subject.player.uuid())).toList());
            }).toList();
            SemionTd.LOGGER.info("SEASON3_RECORDED_OPERATION_REPORT {}", JSON.toJson(new Report(status, reason,
                    sample.builderId(), sample.matchId(), sample.playerRef(), sample.catalogVersion(), sample.sourceTraits(), sample.sourceMap(),
                    sample.attemptedRounds(), sample.sourceFinalRound(), replayEndRound, scenario.stage(), scenario.group(), towerHash,
                    SeasonThreeReplaySupport.hash(EconomyConfig.defaultConfig()), SeasonThreeReplaySupport.canonicalHash(waves),
                    SeasonThreeReplaySupport.canonicalHash(augments), SeasonThreeReplaySupport.hash(sample.actions()),
                    MapConfig.defaultConfig().templateId(), SeasonThreeReplaySupport.hash(MapConfig.defaultConfig()),
                    arena == null ? null : arena.templateSha256(), observedTicks, arenaReadyWaitTicks,
                    started == 0 ? 0 : (System.nanoTime() - started) / 1_000_000_000.0, LIMITS, reports)));
        }

        private void finish(String status, String reason) {
            require(towerHash.equals(SeasonThreeReplaySupport.hash(TowerBalanceRuntime.current())),
                    "Tower balance changed before the operation replay endpoint.");
            report(status, reason);
            if (controls != null) { controls.close(); }
            if (game != null) { game.close(); }
            if (scenarioIndex + 1 < scenarios.size()) {
                context.runAfterDelay(1, () -> new Run(context, scenarios, scenarioIndex + 1, arena).start());
            } else {
                if (arena != null) { arena.close(); }
                context.succeed();
            }
        }

        private void safely(Runnable action) {
            try { action.run(); }
            catch (RuntimeException | AssertionError failure) {
                report("FIXTURE_FAILED_PARTIAL", failure.toString());
                cleanup();
                context.fail(Component.literal("Recorded operation fixture failed: " + failure));
            }
        }

        private void cleanup() {
            if (controls != null) { controls.close(); }
            if (game != null) { game.close(); }
            if (arena != null) { arena.close(); }
        }
    }

    private static Balance balance(SemionPlayer player) {
        var economy = player.economy();
        return new Balance(economy.diamond(), economy.emerald(), economy.income(), economy.emeraldPerSec(),
                economy.emeraldProductionUpgradeCount(), economy.towerLimitPurchaseCount());
    }

    static String sourceEconomyDivergence(BuildAction action, SeasonThreeReplaySupport.ActionResult actual, long productionDelta) {
        if (actual == null || !actual.success()) { return actual == null ? "NOT_EXECUTED" : actual.status(); }
        return switch (action.type()) {
            case TOWER_PLACE, TOWER_UPGRADE -> action.cost() != -actual.diamondsDelta() ? "SOURCE_DIAMOND_COST_CHANGED" : null;
            case TOWER_SELL -> action.incomeGain() != actual.diamondsDelta() ? "SOURCE_SALE_REFUND_CHANGED" : null;
            case SUMMON -> action.cost() != -actual.emeraldsDelta() ? "SOURCE_SUMMON_COST_CHANGED"
                    : action.incomeGain() != actual.incomeDelta() ? "SOURCE_SUMMON_INCOME_CHANGED" : null;
            // Historical BuildAction does not encode which currency paid for production.
            case EMERALD_PRODUCTION_UPGRADE -> action.cost() != -actual.diamondsDelta() - actual.emeraldsDelta()
                    ? "SOURCE_PRODUCTION_AMOUNT_CHANGED_CURRENCY_UNRECORDED"
                    : action.incomeGain() != productionDelta ? "SOURCE_PRODUCTION_RATE_CHANGED" : null;
        };
    }

    private static boolean confirmedCrammingDeath(Monster monster, VanillaDeath evidence) {
        return monster.health() <= 0 && !monster.rewardGranted() && monster.lastHitSourceKind() == KillSourceKind.UNKNOWN
                && evidence != null && evidence.damageType().equals("cramming") && !evidence.hasAttacker();
    }

    private static boolean confirmedRivalDeath(Monster monster, boolean matchedOwnedRival) {
        return matchedOwnedRival && monster.origin() == MonsterOrigin.BUILDER_PROXY && monster.health() <= 0
                && !monster.rewardGranted() && (monster.lastHitSourceKind() == KillSourceKind.TOWER
                || monster.lastHitSourceKind() == KillSourceKind.BOSS);
    }

    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
