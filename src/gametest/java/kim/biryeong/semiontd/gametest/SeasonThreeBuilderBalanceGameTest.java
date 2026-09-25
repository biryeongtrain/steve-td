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
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.MonsterScalingConfig;
import kim.biryeong.semiontd.config.RoundWaveConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.MonsterSupportMetrics;
import kim.biryeong.semiontd.entity.monster.WaveHealingState;
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
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.game.TowerRoundMetricsSnapshot;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.map.ArenaLayout;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.map.TeamArena;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCapacity;
import kim.biryeong.semiontd.tower.adversary.AdversaryRivalTower;
import kim.biryeong.semiontd.tower.engineer.EngineerCircuitTower;
import kim.biryeong.semiontd.tower.engineer.EngineerGolemTower;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

/**
 * Opt-in, equal-investment observations of three fixed official/creative board pairs.
 * Use -Dsemiontd.builderBalanceAcceptance=true together with the exact filter below.
 * Purchases/upgrades use NORMAL runtime checks; only the starting budget and skipped-round
 * clock are injected. Combat uses real entity AI and the existing lane tick/metrics path.
 * No prior combat/growth history is replayed, no augments are selected, and the observations
 * neither estimate an official-builder median nor establish a release balance threshold.
 * Bosses are inert, invulnerable path endpoints: boss assistance/final-defense damage is excluded.
 */
public final class SeasonThreeBuilderBalanceGameTest {
    private static final String FILTER = "semion-td-gametest:season_three_builder_balance_game_test_equal_budget_boards";
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    static final int OBSERVATION_TICKS = 1800;
    private static final int FIXTURE_REVISION = 2;
    private static final List<String> LIMITS = List.of(
            "ZERO_PRIOR_COMBAT_GROWTH_OR_ECONOMY_HISTORY", "INJECTED_STARTING_BUDGET_NOT_PROVEN_ROUND_AFFORDABILITY",
            "ONE_FIXED_COMPOSITION_PER_BUILDER_AND_ROUND", "NOT_AN_OFFICIAL_BUILDER_MEDIAN",
            "NONE_AUGMENT_BASELINE_ONLY", "INCOME_AND_REINVESTMENT_UNMEASURED",
            "NO_FULL_MATCH_OR_TEAM_FINAL_DEFENSE", "NO_NETWORK_CLIENTS_OR_GUI",
            "INERT_INVULNERABLE_BOSS_ENDPOINTS", "PREVIOUS_FIXTURE_REVISION_NOT_COMPARABLE",
            "UNSEEDED_ENTITY_AI_SINGLE_OBSERVATION_NOT_MATCH_SAMPLES");
    private static final Board R16_FROST = new Board("creative_frost", "semion-td:frost", List.of(
            path("frost_ice_vanguard", "frost_sturdy_ice_vanguard"),
            path("frost_ice_vanguard", "frost_sturdy_ice_vanguard", "frost_dongtae"),
            path("frost_ice_vanguard", "frost_sturdy_ice_vanguard", "frost_dongtae"),
            path("frost_ice_vanguard", "frost_sturdy_ice_vanguard", "frost_dongtae"),
            path("frost_ice_breaker_t1", "frost_ice_breaker_t2", "frost_ice_breaker_t3"),
            path("frost_ice_breaker_t1", "frost_ice_breaker_t2", "frost_ice_breaker_t3")));
    private static final List<Pair> PAIRS = List.of(
            new Pair(5, 270,
                    new Board("official_animal", "semion-td:animal_towers", List.of(
                            path("t1_pig_tower"), path("t1_pig_tower"), path("t1_pig_tower"),
                            path("t1_wolf_tower"), path("t1_wolf_tower"), path("t1_wolf_tower"))),
                    new Board("creative_frost", "semion-td:frost", List.of(
                            path("frost_ice_vanguard"), path("frost_ice_vanguard"), path("frost_ice_vanguard"),
                            path("frost_ice_vanguard"), path("frost_ice_breaker_t1"), path("frost_frozen_dumpling_t1")))),
            new Pair(16, 1740,
                    new Board("official_villager", "semion-td:villager_towers", List.of(
                            path("t1_golem_tower", "t2_golem_tower", "t3_golem_tower"),
                            path("t1_golem_tower", "t2_golem_tower", "t3_golem_tower"),
                            path("t1_golem_tower", "t2_golem_tower", "t3_golem_tower"),
                            path("villager_splash_t1", "villager_splash_t2"),
                            path("villager_splash_t1", "villager_splash_t2", "villager_splash_t3"),
                            path("villager_splash_t1", "villager_splash_t2", "villager_splash_t3"))),
                    R16_FROST),
            // 2 * (40+95+150) + 3 * (50+90+180) + (60+150) = 1740.
            // Two front-flank pigs protect a central fox and three rear wolves. No leader/history requirement.
            new Pair(16, 1740,
                    new Board("official_animal", "semion-td:animal_towers", List.of(
                            path("t1_pig_tower", "t2_pig_tower", "t3_pig_tower"),
                            path("t1_fox_tower", "t2_fox_tower"),
                            path("t1_pig_tower", "t2_pig_tower", "t3_pig_tower"),
                            path("t1_wolf_tower", "t2_wolf_dps_tower", "t3_wolf_dps_tower"),
                            path("t1_wolf_tower", "t2_wolf_dps_tower", "t3_wolf_dps_tower"),
                            path("t1_wolf_tower", "t2_wolf_dps_tower", "t3_wolf_dps_tower"))),
                    R16_FROST));

    @GameTest(maxTicks = 3_000_000)
    public void equalBudgetBoards(GameTestHelper context) {
        if (!Boolean.getBoolean("semiontd.builderBalanceAcceptance")) {
            SemionTd.LOGGER.info("SEASON3_BUILDER_BALANCE_NOT_RUN: opt-in property absent; no balance evidence collected.");
            context.succeed();
            return;
        }
        if (!FILTER.equals(System.getProperty("fabric-api.gametest.filter"))
                || !(context.getLevel().getServer() instanceof GameTestServer)) {
            context.fail(Component.literal("Builder balance fixture requires the exact isolated filter and headless GameTestServer."));
            return;
        }
        if (System.getProperty("semiontd.baseline.records") != null) {
            SeasonThreeRecordedCombatGameTest.start(context);
        } else {
            new Run(context).start();
        }
    }

    record Board(String id, String jobId, List<List<String>> upgradePaths) {}
    private record Pair(int round, long budget, Board official, Board creative) {}
    record Purchase(int slot, String operation, String fromType, String toType, long paidDiamonds) {}
    private record TowerSurvival(int slot, String typeId, long paidDiamonds, boolean alive, double health, double maxHealth,
            boolean laneDefender, int slotCost) {}
    private record EntityDiagnostic(int entityId, int entityAge, Vec3 lastPosition, String removalReason,
            int maximumConsecutiveUnticked, boolean outsideFloor, boolean belowFloor, boolean unforcedChunk,
            boolean directlyObserved) {}
    private record BlockDiagnostic(String typeId, BlockPos position, boolean presentThroughout, boolean forcedChunk) {}
    private record RivalDiagnostic(String towerTypeId, String proxyTypeId, UUID proxyLogicalId, String state,
            double health, boolean removed, boolean leaked, int contributedScore, EntityDiagnostic entity) {}
    private record MonsterOutcome(UUID logicalId, String typeId, String outcome, String runtimeState,
            double health, boolean rewarded, boolean leaked, String lastHitSource, EntityDiagnostic entity) {}
    record LaneResult(String board, String jobId, TeamId team, int slots, long initialBudget,
            long actualInvestment, List<Purchase> purchases, String templateId, int expectedSpawns, int observedSpawns,
            Integer spawnCompleteTick, long observedHealers, double initialNaturalHealth,
            long aliveMonsters, int leaks, double leakedThreat, long rewardedKills, boolean laneCleared,
            boolean perfectClear, Integer clearTick, int endpointTick, boolean censored, int combatTicksObserved,
            long survivingTowers, double towerHealthRemaining, List<TowerSurvival> towerSurvival,
            List<TowerRoundMetricsSnapshot> combatMetrics, WaveHealingState.Snapshot healing,
            MonsterSupportMetrics.Snapshot naturalSupport, Double healingRatio, long endingDiamonds,
            String endpointReason, Map<String, Long> exclusiveOutcomes, long leakedAndRewarded,
            List<MonsterOutcome> monsterOutcomes, List<EntityDiagnostic> towerEntityDiagnostics,
            long survivingDefenseTowers, double defenseTowerHealthRemaining, long nonCombatSupportCount,
            List<BlockDiagnostic> blockDiagnostics, List<RivalDiagnostic> rivalTransformations) {}
    private record Difference(String direction, int leaks, long survivingTowers, Integer clearTicks) {}
    private record PairReport(String status, int fixtureRevision, int round, String stage, String augmentGroup, long equalBudget,
            int equalSlots, int priorHistoryRounds, int endpointTick, double wallSeconds,
            String towerBalanceSha256, String waveDefinitionSha256, List<String> limits,
            List<LaneResult> lanes, Difference creativeMinusOfficial) {}

    // Support knockback and enemies circling the endpoint without changing the playable lane bounds.
    private static final int FLOOR_MARGIN = 8;

    private static final class EntityTrace {
        Entity entity;
        int lastEntityId = -1;
        int lastAge = -1;
        int consecutiveUnticked;
        int maximumUnticked;
        boolean outsideFloor;
        boolean belowFloor;
        boolean unforcedChunk;
        int reportedSpawnId = -1;

        void sample(Entity current, PlayerLane lane, BlockBounds supportedBounds) {
            if (current != null) {entity = current;}
            if (entity == null || entity.isRemoved() || !entity.isAlive()) {return;}
            if (entity.getId() == lastEntityId && entity.tickCount == lastAge) {
                maximumUnticked = Math.max(maximumUnticked, ++consecutiveUnticked);
            } else {
                consecutiveUnticked = 0;
            }
            lastEntityId = entity.getId();
            lastAge = entity.tickCount;
            outsideFloor |= entity.getX() < supportedBounds.min().getX() || entity.getX() >= supportedBounds.max().getX() + 1
                    || entity.getZ() < supportedBounds.min().getZ() || entity.getZ() >= supportedBounds.max().getZ() + 1;
            belowFloor |= entity.getY() < supportedBounds.min().getY() - .5;
            unforcedChunk |= !lane.arenaWorld().getForceLoadedChunks().contains(entity.chunkPosition().toLong());
        }

        EntityDiagnostic snapshot() {
            return new EntityDiagnostic(entity == null ? reportedSpawnId : entity.getId(), entity == null ? -1 : entity.tickCount,
                    entity == null ? null : entity.position(),
                    entity == null || entity.getRemovalReason() == null ? null : entity.getRemovalReason().name(),
                    maximumUnticked, outsideFloor, belowFloor, unforcedChunk, entity != null);
        }
    }

    static final class LaneRun {
        final Board board;
        final SemionPlayer player;
        final PlayerLane lane;
        final long budget;
        final BlockBounds supportedBounds;
        final List<Purchase> purchases = new ArrayList<>();
        final List<Tower> initialTowers = new ArrayList<>();
        final Map<UUID, Monster> spawned = new LinkedHashMap<>();
        final Map<UUID, EntityTrace> monsterEntities = new LinkedHashMap<>();
        final Map<UUID, EntityTrace> towerEntities = new LinkedHashMap<>();
        final Map<UUID, BlockDiagnostic> blockTowers = new LinkedHashMap<>();
        final Map<UUID, Monster> rivalProxies = new LinkedHashMap<>();
        Integer spawnCompleteTick;
        LaneResult result;

        LaneRun(Board board, SemionPlayer player, PlayerLane lane, long budget) {
            this(board, player, lane, budget, BlockBounds.of(
                    lane.laneLayout().laneArea().min().offset(-FLOOR_MARGIN, 0, -FLOOR_MARGIN),
                    lane.laneLayout().laneArea().max().offset(FLOOR_MARGIN, 0, FLOOR_MARGIN)));
        }

        LaneRun(Board board, SemionPlayer player, PlayerLane lane, long budget, BlockBounds supportedBounds) {
            this.board = board;
            this.player = player;
            this.lane = lane;
            this.budget = budget;
            this.supportedBounds = supportedBounds;
        }

        void capture(int tick) {
            for (Monster monster : SeasonThreeReplaySupport.observedRoundMonsters(lane)) {
                if (monster.origin() == MonsterOrigin.NATURAL_WAVE) {spawned.putIfAbsent(monster.logicalId(), monster);}
            }
            for (Monster monster : spawned.values()) {
                EntityTrace trace = monsterEntities.computeIfAbsent(monster.logicalId(), ignored -> new EntityTrace());
                trace.reportedSpawnId = monster.minecraftEntityId();
                trace.sample(lane.arenaWorld().getEntity(monster.minecraftEntityId()), lane, supportedBounds);
            }
            for (Tower tower : initialTowers) {
                if (tower instanceof EngineerCircuitTower circuit) {
                    BlockDiagnostic previous = blockTowers.get(tower.logicalId());
                    boolean present = lane.arenaWorld().getBlockState(circuit.circuitPosition()).is(circuitBlock(circuit).getBlock());
                    blockTowers.put(tower.logicalId(), new BlockDiagnostic(tower.type().id(), circuit.circuitPosition(),
                            present && (previous == null || previous.presentThroughout()),
                            lane.arenaWorld().getForceLoadedChunks().contains(new ChunkPos(circuit.circuitPosition()).toLong())));
                } else if (tower instanceof AdversaryRivalTower rival && rival.convertedForWave()) {
                    for (Monster candidate : lane.activeMonsters()) {
                        if (AdversaryRivalTower.isOwnedRival(candidate, tower.ownerPlayer())
                                && AdversaryRivalTower.logicalRivalIdOf(candidate).filter(rival.rivalId()::equals).isPresent()) {
                            Monster previous = rivalProxies.putIfAbsent(tower.logicalId(), candidate);
                            require(previous == null || previous == candidate, "A rival transformed into multiple proxy monsters.");
                        }
                    }
                    Monster proxy = rivalProxies.get(tower.logicalId());
                    require(proxy != null && proxy.hasMinecraftEntity(), "A converted rival has no recorded spawned proxy.");
                    EntityTrace trace = towerEntities.computeIfAbsent(tower.logicalId(), ignored -> new EntityTrace());
                    trace.reportedSpawnId = proxy.minecraftEntityId();
                    trace.sample(lane.arenaWorld().getEntity(proxy.minecraftEntityId()), lane, supportedBounds);
                } else {
                    Entity entity = tower instanceof EntityBackedTower backed ? backed.runtimeEntity(lane).orElse(null)
                            : tower instanceof EngineerGolemTower golem ? golemEntity(golem, lane) : null;
                    towerEntities.computeIfAbsent(tower.logicalId(), ignored -> new EntityTrace()).sample(entity, lane, supportedBounds);
                }
            }
            if (spawnCompleteTick == null && spawned.size() == lane.naturalWaveCount()) {spawnCompleteTick = tick;}
            boolean allResolved = spawnCompleteTick != null && spawned.values().stream()
                    .allMatch(monster -> monster.rewardGranted() || monster.laneLeakRecorded())
                    && rivalProxies.values().stream().allMatch(proxy -> proxy.health() <= 0 || proxy.laneLeakRecorded());
            if (result != null || (!lane.clearedThisRound() && !allResolved && tick < OBSERVATION_TICKS)) {return;}
            List<TowerRoundMetricsSnapshot> metrics = lane.roundTowerMetrics();
            int first = metrics.stream().mapToInt(TowerRoundMetricsSnapshot::firstCombatTick).filter(value -> value >= 0).min().orElse(-1);
            int last = metrics.stream().mapToInt(TowerRoundMetricsSnapshot::lastCombatTick).max().orElse(-1);
            List<TowerSurvival> survival = new ArrayList<>();
            for (int slot = 0; slot < initialTowers.size(); slot++) {
                Tower tower = initialTowers.get(slot);
                boolean alive = tower.health() > 0 && !tower.isDestroyed(lane);
                survival.add(new TowerSurvival(slot, tower.type().id(), tower.paidMineralCost(),
                        alive, alive ? Math.max(0, tower.health()) : 0, tower.currentMaxHealth(),
                        tower.countsForLaneDefense(), TowerCapacity.slotCost(tower)));
            }
            long healers = spawned.values().stream().filter(monster -> monster.waveHealing() != null).count();
            WaveHealingState.Snapshot healing = lane.waveSupportMetrics();
            List<MonsterOutcome> outcomes = spawned.values().stream().map(monster -> new MonsterOutcome(
                    monster.logicalId(), monster.id(), outcome(monster), monster.state().name(), monster.health(),
                    monster.rewardGranted(), monster.laneLeakRecorded(), monster.lastHitSourceKind().name(),
                    monsterEntities.get(monster.logicalId()).snapshot())).toList();
            Map<String, Long> counts = new LinkedHashMap<>();
            outcomes.forEach(value -> counts.merge(value.outcome(), 1L, Long::sum));
            boolean perfect = allResolved && counts.getOrDefault("CREDITED_KILL", 0L).longValue() == lane.naturalWaveCount().longValue()
                    && rivalProxies.values().stream().noneMatch(Monster::laneLeakRecorded);
            result = new LaneResult(board.id(), board.jobId(), player.teamId(),
                    initialTowers.stream().mapToInt(TowerCapacity::slotCost).sum(), budget,
                    purchases.stream().mapToLong(Purchase::paidDiamonds).sum(), List.copyOf(purchases),
                    lane.waveTemplateId(), lane.naturalWaveCount(), spawned.size(), spawnCompleteTick, healers,
                    lane.naturalWaveStartingHealth(), spawned.values().stream().filter(monster -> monster.health() > 0 && !monster.isRemoved()).count(),
                    lane.leakedCountThisRound(), lane.leakedThreatThisRound(), player.matchStats().monsterKills(),
                    lane.clearedThisRound(), perfect, perfect ? tick : null, tick, !allResolved,
                    first < 0 || last < first ? 0 : last - first + 1,
                    survival.stream().filter(TowerSurvival::alive).count(), survival.stream().mapToDouble(TowerSurvival::health).sum(),
                    List.copyOf(survival), metrics, healing, lane.naturalWaveSupportMetrics(),
                    healers == 0 ? null : healing.effectiveHealing() / lane.naturalWaveStartingHealth(), player.economy().diamond(),
                    allResolved ? "ALL_MONSTERS_CREDITED_OR_LEAKED" : lane.clearedThisRound() ? "UNEXPLAINED_EMPTY_LANE" : "TIME_LIMIT",
                    Map.copyOf(counts), outcomes.stream().filter(value -> value.leaked() && value.rewarded()).count(),
                    outcomes, towerEntities.values().stream().map(EntityTrace::snapshot).toList(),
                    survival.stream().filter(value -> value.laneDefender() && value.alive()).count(),
                    survival.stream().filter(TowerSurvival::laneDefender).mapToDouble(TowerSurvival::health).sum(),
                    survival.stream().filter(value -> !value.laneDefender()).count(), List.copyOf(blockTowers.values()),
                    initialTowers.stream().filter(AdversaryRivalTower.class::isInstance).map(AdversaryRivalTower.class::cast)
                            .filter(AdversaryRivalTower::convertedForWave).map(rival -> {
                                Monster proxy = rivalProxies.get(rival.logicalId());
                                return new RivalDiagnostic(rival.type().id(), proxy.id(), proxy.logicalId(), proxy.state().name(),
                                        proxy.health(), proxy.isRemoved(), proxy.laneLeakRecorded(), rival.contributedScore(),
                                        towerEntities.get(rival.logicalId()).snapshot());
                            }).toList());
        }

        private static Entity golemEntity(EngineerGolemTower tower, PlayerLane lane) {
            try {
                var method = EngineerGolemTower.class.getDeclaredMethod("golem", PlayerLane.class);
                method.setAccessible(true);
                return (Entity) method.invoke(tower, lane);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Engineer golem observer changed", failure);
            }
        }

        private static BlockState circuitBlock(EngineerCircuitTower tower) {
            try {
                var method = EngineerCircuitTower.class.getDeclaredMethod("blockState");
                method.setAccessible(true);
                return (BlockState) method.invoke(tower);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Engineer circuit observer changed", failure);
            }
        }

        private static String outcome(Monster monster) {
            // Leakage and a later kill overlap in production; use this priority for an additive denominator.
            if (monster.laneLeakRecorded()) {return "LEAKED";}
            if (monster.rewardGranted()) {return "CREDITED_KILL";}
            if (monster.health() <= 0) {return "UNCREDITED_DEATH";}
            if (monster.isRemoved()) {return "UNEXPLAINED_REMOVAL";}
            return "ALIVE_UNRESOLVED";
        }
    }

    private static final class Run {
        private final GameTestHelper context;
        private final ServerLevel level;
        private final BlockPos origin;
        private final List<BlockPos> ownedFloor = new ArrayList<>();
        private final Set<Long> forcedChunks = new HashSet<>();
        private final List<LaneRun> lanes = new ArrayList<>();
        private final EconomyConfig economyConfig = EconomyConfig.defaultConfig();
        private final WaveConfig waveConfig = WaveConfig.defaultConfig().withSeason3Stages(true, true, Set.of());
        private final String towerHash = hash(TowerBalanceRuntime.current());
        private SemionGame game;
        private EconomyService economy;
        private RoundWaveConfig wave;
        private int pairIndex;
        private int tick;
        private long startNanos;

        Run(GameTestHelper context) {
            this.context = context;
            level = context.getLevel();
            origin = context.absolutePos(BlockPos.ZERO).offset(1024, 1, 1024);
        }

        void start() {
            safely(() -> {
                buildFloor();
                context.runAfterDelay(40, () -> safely(this::startPair));
            });
        }

        private BlockPos base(int side) {return origin.offset(side * 48, 0, 0);}

        private void buildFloor() {
            for (int side = 0; side < 2; side++) {
                BlockPos base = base(side);
                for (int x = (base.getX() - FLOOR_MARGIN) >> 4; x <= (base.getX() + 17 + FLOOR_MARGIN) >> 4; x++) {
                    for (int z = (base.getZ() - FLOOR_MARGIN) >> 4; z <= (base.getZ() + 31 + FLOOR_MARGIN) >> 4; z++) {
                        long key = ChunkPos.asLong(x, z);
                        if (!level.getForceLoadedChunks().contains(key)) {
                            level.setChunkForced(x, z, true);
                            forcedChunks.add(key);
                        }
                        level.getChunk(x, z);
                    }
                }
                for (int x = -FLOOR_MARGIN; x < 18 + FLOOR_MARGIN; x++) {
                    for (int z = -FLOOR_MARGIN; z < 32 + FLOOR_MARGIN; z++) {
                        BlockPos position = base.offset(x, 0, z);
                        require(level.getBlockState(position).isAir(), "Builder fixture floor collides with an existing block.");
                        ownedFloor.add(position);
                        level.setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
                    }
                }
            }
        }

        private void startPair() {
            require(towerHash.equals(hash(TowerBalanceRuntime.current())), "Tower runtime changed between observations.");
            Pair pair = PAIRS.get(pairIndex);
            wave = waveConfig.configForRound(pair.round()).orElseThrow();
            Map<TeamId, TeamArena> arenas = new LinkedHashMap<>();
            List<AssignedParticipant> participants = new ArrayList<>();
            List<Board> boards = List.of(pair.official(), pair.creative());
            for (int side = 0; side < 2; side++) {
                TeamId team = side == 0 ? TeamId.RED : TeamId.BLUE;
                LaneRegionLayout lane = layout(base(side));
                arenas.put(team, new TeamArena(team, () -> {}, level,
                        new ArenaLayout(Vec3.atBottomCenterOf(base(side).offset(2, 1, 2)),
                                Vec3.atBottomCenterOf(base(side).offset(9, 1, 30)), Map.of(1, lane))));
                UUID owner = UUID.nameUUIDFromBytes(("s3-budget-" + pair.round() + "-" + side).getBytes(StandardCharsets.UTF_8));
                participants.add(new AssignedParticipant(owner, boards.get(side).id(), team, 1));
            }
            game = new SemionGame(economyConfig, waveConfig, new GameArena(arenas));
            economy = new EconomyService(economyConfig, game);
            for (int side = 0; side < 2; side++) {
                require(game.selectJob(participants.get(side).uuid(), ResourceLocation.parse(boards.get(side).jobId())),
                        "Fixture job is unavailable: " + boards.get(side).jobId());
            }
            require(game.start(level.getServer(), new ParticipantSelectionPlan(MatchMode.NORMAL, participants, Set.of(), 2)),
                    "The ordinary NORMAL match must start.");
            // World entity AI runs even when this fixture only ticks PlayerLane. An active boss
            // could otherwise kill/pull enemies before the leak checkpoint and falsify clear time.
            for (AssignedParticipant participant : participants) {
                var boss = game.teams().get(participant.teamId()).laneGroup().bossEntity().orElseThrow();
                boss.setNoAi(true);
                boss.setInvulnerable(true);
            }
            // Skip previous rounds, not placement/upgrade eligibility or round-start hooks.
            setField(game, "currentRound", pair.round() - 1);
            setField(game, "phase", RoundPhase.ROUND_PAYOUT);
            game.tick(level.getServer());
            require(game.currentRound() == pair.round() && game.phase() == RoundPhase.PREPARE_AND_SUMMON,
                    "The real payout-to-prepare transition must enter the requested round.");
            for (int side = 0; side < 2; side++) {
                UUID owner = participants.get(side).uuid();
                SemionPlayer player = game.players().get(owner);
                PlayerLane lane = game.playerLane(owner).orElseThrow();
                player.economy().overrideStartingValues(pair.budget(), 0, 0, 0);
                LaneRun run = new LaneRun(boards.get(side), player, lane, pair.budget());
                lanes.add(run);
                purchaseBoard(run, base(side));
                require(game.towerCapacityUsed(owner) == 6 && game.towerLimitForPlayer(owner) >= 6,
                        "Both boards must occupy exactly six legal slots.");
                require(player.economy().diamond() == 0
                                && lane.towers().stream().mapToLong(Tower::paidMineralCost).sum() == pair.budget()
                                && run.purchases.stream().mapToLong(Purchase::paidDiamonds).sum() == pair.budget(),
                        "The proposed equal budget must match actual wallet debits and cumulative paid tower costs.");
                require(lane.augmentSnapshot().selections().isEmpty(), "This is an unaugmented baseline.");
                run.initialTowers.addAll(lane.towers());
            }
            require(lanes.getFirst().purchases.stream().mapToLong(Purchase::paidDiamonds).sum()
                            == lanes.getLast().purchases.stream().mapToLong(Purchase::paidDiamonds).sum(),
                    "Official and creative boards must have equal actual investment.");
            // Shorten only the empty waiting period; the ordinary transition starts/queues combat.
            setField(game, "phaseTicks", SemionGame.DEFAULT_PREPARE_TICKS - 1);
            game.tick(level.getServer());
            require(game.phase() == RoundPhase.LANE_WAVE, "The ordinary prepare tick must start the wave.");
            require(lanes.getFirst().lane.naturalWaveCount().equals(lanes.getLast().lane.naturalWaveCount())
                            && lanes.getFirst().lane.naturalWaveStartingHealth().equals(lanes.getLast().lane.naturalWaveStartingHealth()),
                    "The compared lanes must receive the same wave count and initial health.");
            tick = 0;
            startNanos = System.nanoTime();
            context.runAfterDelay(1, () -> safely(this::tickPair));
        }

        private void purchaseBoard(LaneRun run, BlockPos base) {
            for (int slot = 0; slot < run.board.upgradePaths().size(); slot++) {
                List<String> path = run.board.upgradePaths().get(slot);
                BlockPos position = base.offset(6 + (slot % 3) * 3, 0, 12 + (slot / 3) * 3);
                long before = run.player.economy().diamond();
                TowerPlacementResult placed = ProductionTowerService.placeTower(game, run.player.uuid(), position, path.getFirst());
                require(placed == TowerPlacementResult.SUCCESS, "Actual starter purchase failed: " + path.getFirst() + " -> " + placed);
                run.purchases.add(new Purchase(slot, "PLACE", null, path.getFirst(), before - run.player.economy().diamond()));
                GridPosition grid = GridPosition.from(position);
                for (String target : path.subList(1, path.size())) {
                    Tower current = run.lane.towerAt(grid);
                    require(current != null, "The purchased tower must occupy its requested floor position.");
                    var upgrade = ProductionTowerCatalog.upgrades(current.type()).stream()
                            .filter(option -> option.targetType().id().equals(target)).findFirst().orElseThrow();
                    before = run.player.economy().diamond();
                    TowerUpgradeResult upgraded = ProductionTowerService.upgradeTower(game, run.player.uuid(), grid, upgrade.id());
                    require(upgraded == TowerUpgradeResult.SUCCESS, "Actual paid upgrade failed: " + target + " -> " + upgraded);
                    run.purchases.add(new Purchase(slot, "UPGRADE", current.type().id(), target,
                            before - run.player.economy().diamond()));
                }
                require(run.lane.towerAt(grid) != null && run.lane.towerAt(grid).type().id().equals(path.getLast()),
                        "Actual purchase path must end at the specified tower type.");
            }
            require(run.lane.towers().size() == 6, "The fixed board must contain exactly six purchased towers.");
        }

        private void tickPair() {
            tick++;
            // Observe lanes independently; do not let a team victory or next-round reset erase measurements.
            for (LaneRun run : lanes) {
                if (run.result == null) {
                    run.lane.tick(level.getServer(), economy, game.players(), MonsterScalingConfig.defaultConfig(), tick);
                    run.capture(tick);
                }
            }
            if (tick < OBSERVATION_TICKS && lanes.stream().anyMatch(run -> run.result == null)) {
                context.runAfterDelay(1, () -> safely(this::tickPair));
                return;
            }
            require(towerHash.equals(hash(TowerBalanceRuntime.current())), "Tower runtime changed during combat.");
            // Only fixture validity is asserted. A loss or censored clear time is legitimate evidence.
            require(lanes.stream().allMatch(run -> run.spawnCompleteTick != null), "Every queued natural monster must be observed spawning.");
            require(lanes.stream().allMatch(run -> run.result.observedHealers() == (PAIRS.get(pairIndex).round() == 16 ? 1 : 0)),
                    "R5 must have no healer, and the R16 H0 wave must have one.");
            require(lanes.stream().allMatch(run -> run.result.combatMetrics().stream()
                            .mapToDouble(value -> value.physicalDamageDealt() + value.magicDamageDealt()).sum() > 0),
                    "Each compared board must deal actual combat damage.");
            for (LaneRun run : lanes) {
                validateLane(run);
            }
            report("INTERNAL_OBSERVATION_ONLY");
            cleanupPair();
            if (++pairIndex == PAIRS.size()) {
                cleanupArena();
                context.succeed();
            } else {
                context.runAfterDelay(40, () -> safely(this::startPair));
            }
        }

        private void report(String status) {
            Pair pair = PAIRS.get(pairIndex);
            Difference difference = null;
            if (lanes.size() == 2 && lanes.stream().allMatch(run -> run.result != null)) {
                LaneResult official = lanes.getFirst().result;
                LaneResult creative = lanes.getLast().result;
                difference = new Difference("CREATIVE_MINUS_OFFICIAL", creative.leaks() - official.leaks(),
                        creative.survivingTowers() - official.survivingTowers(),
                        creative.clearTick() == null || official.clearTick() == null ? null : creative.clearTick() - official.clearTick());
            }
            SemionTd.LOGGER.info("SEASON3_BUILDER_BALANCE_REPORT {}", JSON.toJson(new PairReport(status, FIXTURE_REVISION, pair.round(),
                    "COUNT_AND_HEALER_H0", "NONE", pair.budget(), 6, 0, tick,
                    startNanos == 0 ? 0 : (System.nanoTime() - startNanos) / 1_000_000_000.0,
                    towerHash, hash(wave), LIMITS, lanes.stream().map(run -> run.result).toList(), difference)));
        }

        private void safely(Runnable action) {
            try {
                action.run();
            } catch (RuntimeException | AssertionError failure) {
                if (!lanes.isEmpty()) {report("FIXTURE_FAILED_PARTIAL");}
                cleanupPair();
                cleanupArena();
                context.fail(Component.literal("Season 3 equal-budget fixture failed: " + failure));
            }
        }

        private void cleanupPair() {
            if (game != null) {game.close(); game = null;}
            lanes.clear();
        }

        private void cleanupArena() {
            for (BlockPos position : ownedFloor) {level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());}
            ownedFloor.clear();
            for (long key : forcedChunks) {level.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), false);}
            forcedChunks.clear();
        }
    }

    private static LaneRegionLayout layout(BlockPos base) {
        return new LaneRegionLayout(1, Vec3.atBottomCenterOf(base.offset(9, 1, 2)),
                BlockBounds.of(base.offset(7, 1, 1), base.offset(11, 1, 2)),
                List.of(Vec3.atBottomCenterOf(base.offset(9, 1, 14))), Vec3.atBottomCenterOf(base.offset(9, 1, 30)),
                BlockBounds.of(base.offset(0, 1, 0), base.offset(17, 6, 31)),
                List.of(new GridPosition(base.getX() + 9, base.getY(), base.getZ() + 25)));
    }

    private static List<String> path(String... types) {return List.of(types);}

    static void setField(SemionGame game, String name, Object value) {
        try {
            var field = SemionGame.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(game, value);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Fixture clock field changed: " + name, failure);
        }
    }

    private static String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JSON.toJson(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static void validateLane(LaneRun run) {
        LaneResult result = run.result;
        require(run.spawnCompleteTick != null, "Every queued natural monster must be observed spawning.");
        require(result.exclusiveOutcomes().values().stream().mapToLong(Long::longValue).sum() == result.expectedSpawns(),
                "Every natural monster needs exactly one endpoint outcome.");
        require(result.exclusiveOutcomes().getOrDefault("UNCREDITED_DEATH", 0L) == 0
                        && result.exclusiveOutcomes().getOrDefault("UNEXPLAINED_REMOVAL", 0L) == 0,
                "Uncredited death/removal invalidates this tower-only comparison: " + run.board.id());
        require(result.monsterOutcomes().stream().filter(MonsterOutcome::rewarded).count() == result.rewardedKills()
                        && result.monsterOutcomes().stream().filter(MonsterOutcome::leaked).count()
                                + result.rivalTransformations().stream().filter(RivalDiagnostic::leaked).count() == result.leaks(),
                "Logical-monster reward/leak ledger must reconcile with player/lane counters.");
        require(result.monsterOutcomes().stream().noneMatch(value -> "BOSS".equals(value.lastHitSource())),
                "Boss assistance is forbidden in the tower-only fixture.");
        require(result.monsterOutcomes().stream().noneMatch(value -> !value.entity().directlyObserved() && !value.rewarded()),
                "Only a credited immediate kill may finish before direct entity observation.");
        require(result.blockDiagnostics().stream().allMatch(value -> value.presentThroughout() && value.forcedChunk()),
                "A recorded engineer circuit was missing or outside forced chunks.");
        require(result.rivalTransformations().stream().allMatch(value -> value.entity().directlyObserved()
                        && (!value.removed() || value.health() <= 0)),
                "A rival proxy was not directly observed or disappeared alive.");
        List<EntityDiagnostic> entities = new ArrayList<>(result.towerEntityDiagnostics());
        result.monsterOutcomes().forEach(value -> entities.add(value.entity()));
        require(entities.stream().noneMatch(value -> value.outsideFloor() || value.belowFloor() || value.unforcedChunk()),
                "An entity left the supported/forced fixture floor: " + run.board.id());
        require(entities.stream().noneMatch(value -> value.entityId() < 0 || value.maximumConsecutiveUnticked() > 5),
                "An entity was absent or stalled while lane time advanced: " + run.board.id());
    }

    static void require(boolean condition, String message) {
        if (!condition) {throw new AssertionError(message);}
    }
}
