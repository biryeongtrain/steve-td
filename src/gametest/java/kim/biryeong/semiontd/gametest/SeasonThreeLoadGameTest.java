package kim.biryeong.semiontd.gametest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.MonsterScalingConfig;
import kim.biryeong.semiontd.config.RoundWaveConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterSupportMetrics;
import kim.biryeong.semiontd.entity.monster.WaveHealingState;
import kim.biryeong.semiontd.game.EconomyService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerRoundMetricsSnapshot;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

/**
 * Opt-in headless lane observation, not a release balance or 30-client acceptance gate.
 * Run only this test with JAVA_TOOL_OPTIONS containing both -Dsemiontd.loadAcceptance=true
 * and -Dfabric-api.gametest.filter=semion-td-gametest:season_three_load_game_test_staged_waves.
 * Each SEASON3_WAVE_COMPARISON_REPORT log line is a complete JSON checkpoint/stage report.
 * Eight logical lanes compare two existing boards and four augment profiles, not client load.
 * Maximum measured duration is 16 * 1800 game ticks, plus setup and stage warmup.
 * Fixed factory-built boards omit purchase history, prior survival stacks and job progression.
 * Replicas share a server and are not independent matches. Entity AI is real, not seeded.
 */
public final class SeasonThreeLoadGameTest {
    private static final String FILTER = "semion-td-gametest:season_three_load_game_test_staged_waves";
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private static final int LANES = 8;
    private static final int STAGE_TICKS = 1800;
    private static final List<Integer> ROUNDS = List.of(16, 19, 20, 25);
    private static final String INFINITE_TEMPLATE = "overworld_assault";
    private static final List<String> LIMITS = List.of(
            "NO_INCOME_PURCHASE_OR_REINVESTMENT; INCOME_PROFILE_HAS_DIRECT_COMBAT_EFFECT_ONLY_AT_R25",
            "NO_NETWORK_CLIENTS", "NO_GUI_OR_PURCHASE_TRANSACTIONS", "ONLY_OVERWORLD_ASSAULT_INFINITE_TEMPLATE",
            "NO_FULL_MATCH_OR_TEAM_FINAL_DEFENSE", "NO_PRIOR_ROUND_STACK_OR_ECONOMY_HISTORY",
            "FIXED_BOARDS_NOT_EQUAL_COST_OR_BALANCE_CONTROLS", "CORRELATED_LANES_NOT_MATCH_SAMPLES");
    private static final List<Board> BOARDS = List.of(
            new Board("villager", "semion-td:villager_towers", List.of("t3_golem_tower", "t3_golem_tower",
                    "villager_splash_t3", "villager_splash_t3", "villager_splash_t3", "t2_allay_tower")),
            new Board("animal", "semion-td:animal_towers", List.of("t3_pig_tower", "t3_pig_tower",
                    "t3_pig_tower", "t3_wolf_dps_tower", "t3_wolf_dps_tower", "t3_wolf_dps_tower")));

    @GameTest(maxTicks = 31_000)
    public void stagedWaves(GameTestHelper context) {
        if (!Boolean.getBoolean("semiontd.loadAcceptance")) {
            SemionTd.LOGGER.info("SEASON3_LOAD_NOT_RUN: opt-in property absent; no load evidence collected.");
            context.succeed();
            return;
        }
        if (!FILTER.equals(System.getProperty("fabric-api.gametest.filter"))
                || !(context.getLevel().getServer() instanceof GameTestServer)) {
            context.fail(Component.literal("Load fixture requires the exact isolated filter and headless GameTestServer."));
            return;
        }
        new Run(context).start();
    }

    private enum Group { NONE, ATTACK, DEFENSE, INCOME }
    private enum Stage { BASE, HEALER_ONLY, COUNT_ONLY, COUNT_AND_HEALER }
    private record Board(String id, String job, List<String> towers) {}
    private record TickTimes(int samples, Double p50, Double p95, Double p99, Double maximum) {}
    private record LaneResult(String board, Group group, int replica, List<PlayerAugmentState.Selection> selections,
            String templateId, int expectedSpawns, int observedSpawns, Integer spawnCompleteTick,
            double initialNaturalHealth, long observedHealers, long aliveMonsters, int leaks, double leakedThreat, long rewardedKills,
            boolean laneCleared, boolean perfectClear, boolean defenseBroken, int towerDeaths, Integer clearTick,
            int endpointTick, boolean censored, int combatTicksObserved, List<TowerRoundMetricsSnapshot> towers,
            WaveHealingState.Snapshot healer, MonsterSupportMetrics.Snapshot naturalSupport,
            String healingRatioStatus, Double healingRatio) {}
    private record StageReport(String status, int round, Stage stage, int logicalParticipants, int initialRealTowers, int forcedArenaChunks,
            int endpointTick, double wallSeconds, String towerBalanceSha256, String augmentConfigSha256,
            String waveDefinitionSha256, String definitions, List<String> limits, TickTimes serverTickMilliseconds,
            List<LaneResult> lanes) {}

    private static final class LaneRun {
        final PlayerLane lane;
        final SemionPlayer player;
        final Board board;
        final Group group;
        final int replica;
        final Map<UUID, Monster> spawned = new LinkedHashMap<>();
        Integer spawnCompleteTick;
        LaneResult result;

        LaneRun(PlayerLane lane, SemionPlayer player, Board board, Group group, int replica) {
            this.lane = lane;
            this.player = player;
            this.board = board;
            this.group = group;
            this.replica = replica;
        }

        void capture(int tick) {
            for (Monster monster : lane.activeMonsters()) {
                spawned.putIfAbsent(monster.logicalId(), monster);
            }
            if (spawnCompleteTick == null && spawned.size() == lane.naturalWaveCount()) {
                spawnCompleteTick = tick;
            }
            if (result == null && (lane.clearedThisRound() || tick == STAGE_TICKS)) {
                List<TowerRoundMetricsSnapshot> towers = lane.roundTowerMetrics();
                int first = towers.stream().mapToInt(TowerRoundMetricsSnapshot::firstCombatTick)
                        .filter(value -> value >= 0).min().orElse(-1);
                int last = towers.stream().mapToInt(TowerRoundMetricsSnapshot::lastCombatTick).max().orElse(-1);
                long healers = spawned.values().stream().filter(monster -> monster.waveHealing() != null).count();
                WaveHealingState.Snapshot healing = lane.waveSupportMetrics();
                result = new LaneResult(board.id(), group, replica, lane.augmentSnapshot().selections(),
                        lane.waveTemplateId(), lane.naturalWaveCount(), spawned.size(), spawnCompleteTick,
                        lane.naturalWaveStartingHealth(), healers,
                        lane.activeMonsters().stream().filter(Monster::isAlive).count(),
                        lane.leakedCountThisRound(), lane.leakedThreatThisRound(),
                        player.matchStats().monsterKills(), lane.clearedThisRound(),
                        lane.clearedThisRound() && !lane.leakedThisRound() && !lane.laneDefenseBroken(),
                        lane.laneDefenseBroken(), towers.stream().mapToInt(TowerRoundMetricsSnapshot::deathCount).sum(),
                        lane.clearedThisRound() ? tick : null, tick,
                        !lane.clearedThisRound(), first < 0 || last < first ? 0 : last - first + 1,
                        towers, healing, lane.naturalWaveSupportMetrics(), healers == 0 ? "N/A" : "MEASURED",
                        healers == 0 ? null : healing.effectiveHealing() / lane.naturalWaveStartingHealth());
            }
        }
    }

    private static final class Run {
        private final GameTestHelper context;
        private final ServerLevel level;
        private final BlockPos origin;
        private final List<BlockPos> floor = new ArrayList<>();
        private final Set<Long> forcedChunks = new HashSet<>();
        private final List<LaneRun> lanes = new ArrayList<>();
        private final Map<UUID, SemionPlayer> players = new LinkedHashMap<>();
        private final List<Double> tickMillis = new ArrayList<>();
        private final EconomyService economy = new EconomyService(EconomyConfig.defaultConfig());
        private final AugmentConfig augments;
        private final String towerHash = hash(TowerBalanceRuntime.current());
        private RoundWaveConfig wave;
        private int stageIndex;
        private int roundIndex;
        private int tick;
        private long startNanos;

        Run(GameTestHelper context) {
            this.context = context;
            level = context.getLevel();
            origin = context.absolutePos(BlockPos.ZERO).offset(1024, 0, 1024);
            AugmentConfig defaults = AugmentConfig.defaults();
            augments = new AugmentConfig(true, true, defaults.rarityWeights(), defaults.parameters(), defaults.disabledIds());
        }

        void start() {
            safely(() -> {
                buildFloor();
                context.runAfterDelay(40, () -> safely(this::startStage));
            });
        }

        private BlockPos base(int index) {
            return origin.offset((index % 6) * 32, 1, (index / 6) * 40);
        }

        private void buildFloor() {
            for (int index = 0; index < LANES; index++) {
                BlockPos base = base(index);
                for (int x = base.getX() >> 4; x <= (base.getX() + 17) >> 4; x++) {
                    for (int z = base.getZ() >> 4; z <= (base.getZ() + 31) >> 4; z++) {
                        long key = ChunkPos.asLong(x, z);
                        if (!level.getForceLoadedChunks().contains(key)) {
                            level.setChunkForced(x, z, true);
                            forcedChunks.add(key);
                        }
                        level.getChunk(x, z);
                    }
                }
                for (int x = 0; x < 18; x++) {
                    for (int z = 0; z < 32; z++) {
                        BlockPos position = base.offset(x, 0, z);
                        require(level.getBlockState(position).isAir(), "Load arena floor collides with an existing block.");
                        floor.add(position);
                        level.setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
                    }
                }
            }
        }

        private void startStage() {
            require(towerHash.equals(hash(TowerBalanceRuntime.current())), "Tower runtime changed between load stages.");
            Stage stage = Stage.values()[stageIndex];
            int round = ROUNDS.get(roundIndex);
            boolean healers = stage == Stage.HEALER_ONLY || stage == Stage.COUNT_AND_HEALER;
            wave = WaveConfig.defaultConfig().withSeason3Stages(stage == Stage.COUNT_ONLY || stage == Stage.COUNT_AND_HEALER,
                    healers, healers ? Set.of(INFINITE_TEMPLATE) : Set.of()).candidatesForRound(round).stream()
                    .filter(candidate -> round < 20 || INFINITE_TEMPLATE.equals(candidate.templateId()))
                    .findFirst().orElseThrow();
            tick = 0;
            tickMillis.clear();
            for (int index = 0; index < LANES; index++) {
                Board board = BOARDS.get(index % 2);
                Group group = Group.values()[index / 2];
                TeamId team = TeamId.values()[index / 5];
                int laneId = index % 5 + 1;
                UUID owner = UUID.nameUUIDFromBytes(("s3-wave-" + round + "-" + stage + "-" + index).getBytes(StandardCharsets.UTF_8));
                SemionPlayer player = new SemionPlayer(owner, "load-" + index, team, laneId,
                        new PlayerEconomy(EconomyConfig.defaultConfig()));
                player.assignJob(JobRegistry.find(ResourceLocation.parse(board.job())).orElseThrow());
                players.put(owner, player);
                BlockPos base = base(index);
                PlayerLane lane = new PlayerLane(team, laneId, owner, level, layout(base, laneId));
                LaneRun run = new LaneRun(lane, player, board, group, 0);
                lanes.add(run);
                AreaEffectLaneIndex.register(lane);
                for (int slot = 0; slot < board.towers().size(); slot++) {
                    GridPosition position = new GridPosition(base.getX() + 6 + (slot % 3) * 3,
                            base.getY(), base.getZ() + 12 + (slot / 3) * 3);
                    Tower tower = ProductionTowerCatalog.find(board.towers().get(slot)).orElseThrow()
                            .create(owner, team, laneId, position);
                    lane.addTower(tower);
                }
                lane.assignAugmentSnapshot(snapshot(augments, group, lane.towers().getFirst().logicalId(), round));
                lane.markWaveStarted(round);
                String laneKey = "lane_" + laneId;
                lane.enqueueWave(wave.entriesForLane(laneKey), wave.spawnMode(), wave.spawnIntervalTicks(),
                        wave.rewardBudgetForLane(laneKey), wave.templateId());
            }
            startNanos = System.nanoTime();
            context.runAfterDelay(1, () -> safely(this::tickStage));
        }

        private void tickStage() {
            tick++;
            // The current server tick has not finished; sample only the previous completed tick.
            if (tick > 1) {
                long[] times = level.getServer().getTickTimesNanos();
                long nanos = times[Math.floorMod(level.getServer().getTickCount() - 1, times.length)];
                if (nanos > 0) {tickMillis.add(nanos / 1_000_000.0);}
            }
            for (LaneRun run : lanes) {
                if (run.result == null) {
                    run.lane.tick(level.getServer(), economy, players, MonsterScalingConfig.defaultConfig(), tick);
                    run.capture(tick);
                }
            }
            if (tick < STAGE_TICKS && lanes.stream().anyMatch(run -> run.result == null)) {
                context.runAfterDelay(1, () -> safely(this::tickStage));
                return;
            }
            report("INTERNAL_OBSERVATION_ONLY");
            // These validate the fixture, not whether a board is strong enough to clear.
            require(lanes.stream().allMatch(run -> run.spawnCompleteTick != null), "Not every queued natural monster was observed spawning.");
            long expectedHealers = wave.entriesForLane("lane_1").stream().filter(entry -> entry.healing() != null)
                    .mapToLong(entry -> entry.count()).sum();
            require(lanes.stream().allMatch(run -> run.result.observedHealers() == expectedHealers),
                    "Every lane must spawn the checkpoint's configured healer count.");
            require(lanes.stream().flatMap(run -> run.spawned.values().stream()).allMatch(monster ->
                    monster.waveHealing() == null || monster.waveHealingState().successfulCasts()
                            <= monster.waveHealing().maxSuccessfulCasts()), "A natural healer exceeded its cast limit.");
            require(lanes.stream().flatMap(run -> run.result.towers().stream())
                    .mapToDouble(value -> value.physicalDamageDealt() + value.magicDamageDealt()).sum() > 0,
                    "No actual tower damage: this run provides no combat evidence.");
            cleanupStage();
            if (++stageIndex == Stage.values().length) {
                stageIndex = 0;
                if (++roundIndex == ROUNDS.size()) {
                    cleanupArena();
                    context.succeed();
                    return;
                }
            }
            context.runAfterDelay(40, () -> safely(this::startStage));
        }

        private void report(String status) {
            SemionTd.LOGGER.info("SEASON3_WAVE_COMPARISON_REPORT {}", JSON.toJson(new StageReport(status,
                    ROUNDS.get(roundIndex), Stage.values()[stageIndex], LANES, LANES * 6, forcedChunks.size(), tick,
                    startNanos == 0 ? 0 : (System.nanoTime() - startNanos) / 1_000_000_000.0,
                    towerHash, hash(augments), hash(wave), "BUNDLED_WAVE_AND_AUGMENT_H0; CURRENT_TOWER_RUNTIME",
                    LIMITS, tickTimes(tickMillis), lanes.stream().map(run -> run.result).toList())));
        }

        private void safely(Runnable action) {
            try {
                action.run();
            } catch (RuntimeException | AssertionError failure) {
                if (!lanes.isEmpty()) {report("FIXTURE_FAILED_PARTIAL");}
                cleanupStage();
                cleanupArena();
                context.fail(Component.literal("Season 3 internal load fixture failed: " + failure));
            }
        }

        private void cleanupStage() {
            for (LaneRun run : lanes) {
                run.lane.disableMonsters();
                run.lane.clearTowers();
                run.lane.clearRoundMonsterMetrics();
                AreaEffectLaneIndex.unregister(run.lane);
            }
            lanes.clear();
            players.clear();
        }

        private void cleanupArena() {
            for (BlockPos position : floor) {level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());}
            floor.clear();
            for (long key : forcedChunks) {level.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), false);}
            forcedChunks.clear();
        }
    }

    private static LaneRegionLayout layout(BlockPos base, int laneId) {
        Vec3 spawn = Vec3.atBottomCenterOf(base.offset(9, 1, 2));
        Vec3 boss = Vec3.atBottomCenterOf(base.offset(9, 1, 30));
        return new LaneRegionLayout(laneId, spawn,
                BlockBounds.of(base.offset(7, 1, 1), base.offset(11, 1, 2)),
                List.of(Vec3.atBottomCenterOf(base.offset(9, 1, 14))), boss,
                BlockBounds.of(base.offset(0, 1, 0), base.offset(17, 6, 31)),
                List.of(new GridPosition(base.getX() + 9, base.getY(), base.getZ() + 25)));
    }

    private static AugmentSnapshot snapshot(AugmentConfig config, Group group, UUID coverTarget, int round) {
        List<PlayerAugmentState.Selection> selections = new ArrayList<>(switch (group) {
            case NONE -> List.of();
            case ATTACK -> List.of(selection(5, AugmentRarity.SILVER, "finishing_fire_1", AugmentChoice.none()),
                    selection(15, AugmentRarity.GOLD, "winning_barrage", AugmentChoice.none()));
            case DEFENSE -> List.of(selection(5, AugmentRarity.SILVER, "triangle_formation", AugmentChoice.none()),
                    selection(15, AugmentRarity.GOLD, "biased_armor", new AugmentChoice(null, null, "PHYSICAL")));
            case INCOME -> List.of(selection(5, AugmentRarity.SILVER, "additional_payload", AugmentChoice.none()),
                    selection(15, AugmentRarity.GOLD, "support_performance", AugmentChoice.none()));
        });
        if (round >= 25) {
            switch (group) {
                case ATTACK -> selections.add(selection(25, AugmentRarity.PRISMATIC, "one_man_show", new AugmentChoice(coverTarget, null, null)));
                case DEFENSE -> selections.add(selection(25, AugmentRarity.PRISMATIC, "tactical_designation_3", new AugmentChoice(coverTarget, null, "COVER")));
                case INCOME -> selections.add(selection(25, AugmentRarity.PRISMATIC, "wartime_economy", AugmentChoice.none()));
                case NONE -> { }
            }
        }
        return new AugmentSnapshot(config, selections);
    }

    private static PlayerAugmentState.Selection selection(int round, AugmentRarity rarity, String id, AugmentChoice choice) {
        return new PlayerAugmentState.Selection(round, rarity, "semiontd:" + id,
                PlayerAugmentState.Outcome.SELECTED, null, choice);
    }

    private static TickTimes tickTimes(List<Double> samples) {
        List<Double> sorted = samples.stream().sorted().toList();
        return new TickTimes(sorted.size(), percentile(sorted, .5), percentile(sorted, .95),
                percentile(sorted, .99), percentile(sorted, 1));
    }

    private static Double percentile(List<Double> sorted, double fraction) {
        return sorted.isEmpty() ? null : sorted.get(Math.max(0, (int) Math.ceil(sorted.size() * fraction) - 1));
    }

    private static String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JSON.toJson(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {throw new AssertionError(message);}
    }
}
