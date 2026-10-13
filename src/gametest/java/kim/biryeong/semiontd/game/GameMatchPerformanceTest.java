package kim.biryeong.semiontd.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.gametest.SeasonThreeReplayArena;
import kim.biryeong.semiontd.summon.IncomeSummons;
import kim.biryeong.semiontd.summon.SummonContext;
import kim.biryeong.semiontd.summon.SummonMonsterType;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.tower.income.IncomeTowerBalance;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class GameMatchPerformanceTest {
    private static final String FILTER = "semion-td-gametest:game_match_performance_test_measures_match_combat";
    private static final Gson JSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private static final List<String> BUILDERS = List.of("villager_towers", "animal_towers", "undead_towers",
            "warlock_towers", "legion_towers", "nether", "end_towers", "ocean", "ancient_city", "mage_towers");
    private static final String WAVE_TEMPLATE = "overworld_assault";
    private static final List<List<PlayerAugmentState.Selection>> AUGMENTS = List.of(
            List.of(selection(5, AugmentRarity.SILVER, "finishing_fire_1", ""),
                    selection(15, AugmentRarity.GOLD, "winning_barrage", "")),
            List.of(selection(5, AugmentRarity.SILVER, "triangle_formation", ""),
                    selection(15, AugmentRarity.GOLD, "biased_armor", "PHYSICAL")),
            List.of(selection(5, AugmentRarity.SILVER, "additional_payload", ""),
                    selection(15, AugmentRarity.GOLD, "support_performance", "")));
    private static final int WARMUP_ROUNDS = 2;

    @GameTest(maxTicks = 2_000_000)
    public void measuresMatchCombat(GameTestHelper context) {
        if (!Boolean.getBoolean("semiontd.matchProfile")) {
            SemionTd.LOGGER.info("MATCH_PROFILE_NOT_RUN: opt-in match load profile; no performance evidence.");
            context.succeed();
            return;
        }
        if (!FILTER.equals(System.getProperty("fabric-api.gametest.filter"))
                || !(context.getLevel().getServer() instanceof GameTestServer)) {
            context.fail(Component.literal("Match profile requires its exact isolated filter and GameTestServer."));
            return;
        }
        new Run(context).start();
    }

    private static final class Run {
        final GameTestHelper context;
        final ServerLevel level;
        final MinecraftServer server;
        final com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        final String variant = System.getProperty("semiontd.profileVariant", "unspecified")
                .replaceAll("[^A-Za-z0-9._-]", "_");
        final String sparkMode = System.getProperty("semiontd.matchSpark", "off");
        final int summonsPerLane = Integer.getInteger("semiontd.matchSummons", 0);
        final int measuredRound = Integer.getInteger("semiontd.matchRound", 16);
        final int measuredTicks = Integer.getInteger("semiontd.matchTicks", 600);
        final List<String> builders = System.getProperty("semiontd.matchBuilders") == null ? BUILDERS
                : List.of(System.getProperty("semiontd.matchBuilders").split(","));
        final List<UUID> owners = new ArrayList<>();
        final List<Object> boards = new ArrayList<>();
        final List<Object> samples = new ArrayList<>();
        final long[] tickNanos = new long[measuredTicks];
        SeasonThreeReplayArena.ArenaFixture arena;
        SemionGame game;
        boolean measuring;
        int arenaWaitTicks;
        int waveTick;
        int waveEndedAtTick = -1;
        int enteredRound;
        int queuedSummons;
        long cpuBefore;
        long allocatedBefore;
        long gcCountBefore;
        long gcMillisBefore;
        long startedNanos;

        Run(GameTestHelper context) {
            this.context = context;
            level = context.getLevel();
            server = level.getServer();
        }

        void start() {
            safely(() -> {
                require(builders.size() == SemionTeam.MAX_PLAYERS * 2, "Match profile needs ten builders.");
                require(sparkMode.equals("off") || server.getCommands().getDispatcher().getRoot().getChild("spark") != null,
                        "Requested spark runtime must be loaded.");
                arena = SeasonThreeReplayArena.createArena(context, new BlockPos(1024, 2, 1024), SemionTeam.MAX_PLAYERS);
                awaitArena();
            });
        }

        void awaitArena() {
            if (!arena.entitiesReady()) {
                require(++arenaWaitTicks <= 10_000, "Match arena chunks did not become entity-ticking ready.");
                next(this::awaitArena);
                return;
            }
            server.tickRateManager().setTickRate(20.0F);
            ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
            IncomeSummons.reloadBuiltIns(SummonConfig.defaultConfig());
            game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), arena.gameArena());
            AugmentConfig augments = AugmentConfig.defaults();
            game.configureAugments(new AugmentConfig(false, false, augments.rarityWeights(), augments.parameters(),
                    augments.disabledIds()));
            List<AssignedParticipant> participants = new ArrayList<>();
            for (TeamId team : List.of(TeamId.RED, TeamId.BLUE)) {
                for (int laneId = 1; laneId <= SemionTeam.MAX_PLAYERS; laneId++) {
                    int index = owners.size();
                    UUID owner = UUID.nameUUIDFromBytes(("match-profile-" + index).getBytes(StandardCharsets.UTF_8));
                    owners.add(owner);
                    participants.add(new AssignedParticipant(owner, "Profile" + index, team, laneId));
                    require(game.selectJob(owner, Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, builders.get(index))),
                            "Unknown builder: " + builders.get(index));
                }
            }
            require(game.start(server, new ParticipantSelectionPlan(MatchMode.NORMAL, participants, Set.of(), 2)),
                    "Match profile game must start.");
            write("waveConfig", WaveConfig.defaultConfig().withSeason3Stages(true, true, Set.of()));
            for (TeamId team : List.of(TeamId.RED, TeamId.BLUE)) {
                game.teams().get(team).laneGroup().bossEntity().orElseThrow().setPermanentlyInvulnerable(true);
            }
            enterRound(measuredRound - WARMUP_ROUNDS);
            owners.forEach(this::buildBoard);
            AugmentConfig selected = new AugmentConfig(true, true, augments.rarityWeights(), augments.parameters(),
                    augments.disabledIds());
            for (int index = 0; index < owners.size(); index++) {
                game.playerLane(owners.get(index)).orElseThrow().assignAugmentSnapshot(new AugmentSnapshot(selected,
                        AUGMENTS.get(index % AUGMENTS.size())));
            }
            startWave();
        }

        void enterRound(int round) {
            for (SemionTeam team : game.teams().values()) {
                if (team.active() && !team.eliminated()) {
                    team.laneGroup().disableMonsters();
                }
            }
            enteredRound = round;
            write("currentRound", round - 1);
            write("phase", RoundPhase.ROUND_PAYOUT);
            game.tick(server);
            require(game.currentRound() == round && game.phase() == RoundPhase.PREPARE_AND_SUMMON,
                    "The ordinary round transition must enter round " + round + ".");
            write("selectedRoundWave", game.waveConfig().candidatesForRound(round).stream()
                    .filter(candidate -> round < game.waveConfig().infiniteFromRound()
                            || WAVE_TEMPLATE.equals(candidate.templateId())).findFirst().orElseThrow());
        }

        void buildBoard(UUID owner) {
            SemionPlayer player = game.players().get(owner);
            PlayerLane lane = game.playerLane(owner).orElseThrow();
            player.economy().overrideStartingValues(100_000_000L, 100_000_000L, 0, 0);
            int limitPurchases = 0;
            while (game.purchaseTowerLimit(owner)) {
                limitPurchases++;
            }
            List<String> starters = ProductionTowerService.availableTowers(game, owner).stream()
                    .map(entry -> entry.type().id()).sorted().toList();
            require(!starters.isEmpty(), "No starter towers for " + player.name());
            BlockPos min = lane.laneLayout().laneArea().min();
            BlockPos max = lane.laneLayout().laneArea().max();
            double centerX = (min.getX() + max.getX() + 1) / 2.0;
            double centerZ = (min.getZ() + max.getZ() + 1) / 2.0;
            List<BlockPos> cells = new ArrayList<>();
            for (int x = min.getX(); x <= max.getX(); x += 2) {
                for (int z = min.getZ(); z <= max.getZ(); z += 2) {
                    for (int y = max.getY() + 1; y >= min.getY() - 4; y--) {
                        BlockPos cell = new BlockPos(x, y, z);
                        if (!level.getBlockState(cell).isAir()) {
                            if (level.getBlockState(cell.above()).isAir()) {
                                cells.add(cell);
                            }
                            break;
                        }
                    }
                }
            }
            cells.sort(Comparator.comparingDouble((BlockPos cell) -> square(cell.getX() + 0.5 - centerX)
                    + square(cell.getZ() + 0.5 - centerZ)).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
            int next = 0;
            int rejected = 0;
            Map<TowerPlacementResult, Integer> results = new TreeMap<>();
            for (BlockPos cell : cells) {
                if (rejected >= starters.size()) {
                    break;
                }
                TowerPlacementResult result = ProductionTowerService.placeTower(game, owner, cell,
                        starters.get(next % starters.size()));
                results.merge(result, 1, Integer::sum);
                if (result == TowerPlacementResult.SUCCESS) {
                    next++;
                    rejected = 0;
                } else if (result != TowerPlacementResult.OCCUPIED && result != TowerPlacementResult.OUTSIDE_LANE_AREA) {
                    next++;
                    rejected++;
                }
            }
            int index = 0;
            int upgrades = 0;
            for (Tower tower : List.copyOf(lane.towers())) {
                GridPosition position = tower.position();
                for (int step = 0; step < 8; step++) {
                    List<TowerUpgradeOption> options = ProductionTowerService.availableUpgrades(game, owner, position);
                    boolean upgraded = false;
                    for (int offset = 0; offset < options.size() && !upgraded; offset++) {
                        upgraded = ProductionTowerService.upgradeTower(game, owner, position,
                                options.get((index + step + offset) % options.size()).id()) == TowerUpgradeResult.SUCCESS;
                    }
                    if (!upgraded) {
                        break;
                    }
                    upgrades++;
                }
                index++;
            }
            Map<String, Integer> composition = new TreeMap<>();
            for (Tower tower : lane.towers()) {
                composition.merge(tower.type().id(), 1, Integer::sum);
            }
            require(!composition.isEmpty(), "No tower was placed for " + player.name() + ": cells=" + cells.size()
                    + " area=" + min + ".." + max + " starters=" + starters + " results=" + results);
            boards.add(map("player", player.name(), "team", player.teamId().name(), "lane", lane.laneId(),
                    "builder", builders.get(owners.indexOf(owner)), "towerLimitPurchases", limitPurchases,
                    "capacityUsed", game.towerCapacityUsed(owner), "capacity", game.towerLimitForPlayer(owner),
                    "towers", lane.towers().size(), "upgrades", upgrades, "placement", results.toString(),
                    "composition", composition));
        }

        void startWave() {
            int round = game.currentRound();
            List<SummonMonsterType> types = game.summonShop().all().stream()
                    .filter(type -> !IncomeTowerBalance.isUnit(type.id()))
                    .sorted(Comparator.comparing(SummonMonsterType::id)).toList();
            require(!types.isEmpty(), "No income summon is registered.");
            queuedSummons = 0;
            for (int index = 0; index < owners.size(); index++) {
                SemionPlayer target = game.players().get(owners.get(index));
                SemionPlayer sender = game.players().get(owners.get((index + SemionTeam.MAX_PLAYERS) % owners.size()));
                PlayerLane lane = game.playerLane(target.uuid()).orElseThrow();
                for (int count = 0; count < summonsPerLane; count++) {
                    Monster monster = types.get((index + count) % types.size()).createMonster(
                            new SummonContext(game, sender), target.teamId(), lane.laneId(), round);
                    monster.setSenderName(sender.name());
                    monster.setOrigin(MonsterOrigin.NORMAL_PAID);
                    lane.enqueueSummonedMonster(monster);
                    queuedSummons++;
                }
            }
            write("phaseTicks", (int) read("currentPrepareDurationTicks") - 1);
            game.tick(server);
            require(game.phase() == RoundPhase.LANE_WAVE && game.currentRound() == round,
                    "Preparation must transition to real lane combat.");
            waveTick = 0;
            waveEndedAtTick = -1;
            measuring = round == measuredRound;
            next(this::tickWave);
        }

        void tickWave() {
            if (measuring && waveTick >= 1) {
                long[] times = server.getTickTimesNanos();
                tickNanos[waveTick - 1] = times[Math.floorMod(server.getTickCount() - 1, times.length)];
            }
            if (waveTick == measuredTicks) {
                if (measuring) {
                    finish();
                } else {
                    enterRound(enteredRound + 1);
                    startWave();
                }
                return;
            }
            if (measuring && waveTick == 0) {
                if (sparkMode.equals("profile")) {
                    command("spark profiler start --force-java-sampler --interval 2");
                }
                gcCountBefore = gc(false);
                gcMillisBefore = gc(true);
                allocatedBefore = threads.getThreadAllocatedBytes(Thread.currentThread().threadId());
                cpuBefore = threads.getCurrentThreadCpuTime();
                startedNanos = System.nanoTime();
            }
            game.tick(server);
            waveTick++;
            if (waveEndedAtTick < 0 && game.phase() != RoundPhase.LANE_WAVE) {
                waveEndedAtTick = waveTick;
            }
            if (measuring && (waveTick == 1 || waveTick % 50 == 0)) {
                samples.add(sample());
            }
            next(this::tickWave);
        }

        Object sample() {
            long monsters = 0;
            long livingTowers = 0;
            long towers = 0;
            long brokenLanes = 0;
            for (UUID owner : owners) {
                PlayerLane lane = game.playerLane(owner).orElseThrow();
                monsters += lane.activeMonsters().stream().filter(Monster::isAlive).count();
                towers += lane.towers().size();
                livingTowers += lane.towers().stream().filter(tower -> tower.health() > 0.0).count();
                brokenLanes += lane.laneDefenseBroken() ? 1 : 0;
            }
            return map("tick", waveTick, "phase", game.phase().name(), "aliveMonsters", monsters, "towers", towers,
                    "livingTowers", livingTowers, "brokenLanes", brokenLanes);
        }

        void finish() {
            long cpu = threads.getCurrentThreadCpuTime() - cpuBefore;
            long allocated = threads.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocatedBefore;
            double wallSeconds = (System.nanoTime() - startedNanos) / 1_000_000_000.0;
            if (!sparkMode.equals("off")) {
                command("spark tps");
            }
            if (sparkMode.equals("profile")) {
                command("spark profiler stop --save-to-file");
            }
            Map<String, Object> report = map("variant", variant, "spark", sparkMode, "builders", builders,
                    "warmupRounds", WARMUP_ROUNDS, "measuredRound", measuredRound, "waveTemplate", WAVE_TEMPLATE,
                    "measuredTicks", measuredTicks, "summonsPerLane", summonsPerLane, "queuedSummons", queuedSummons,
                    "waveEndedAtTick", waveEndedAtTick, "tickRate", server.tickRateManager().tickrate(),
                    "msptAll", stats(tickNanos, 0, measuredTicks),
                    "msptFirstHalf", stats(tickNanos, 0, measuredTicks / 2),
                    "msptSecondHalf", stats(tickNanos, measuredTicks / 2, measuredTicks),
                    "serverThreadCpuMillisPerTick", cpu / 1_000_000.0 / measuredTicks,
                    "serverThreadAllocatedBytesPerTick", allocated / (double) measuredTicks,
                    "gcCount", gc(false) - gcCountBefore, "gcMillis", gc(true) - gcMillisBefore,
                    "wallSeconds", wallSeconds, "java", System.getProperty("java.runtime.version"),
                    "maxHeapBytes", Runtime.getRuntime().maxMemory(),
                    "processors", Runtime.getRuntime().availableProcessors(), "arenaWaitTicks", arenaWaitTicks,
                    "boards", boards, "samples", samples,
                    "limits", "FIXED_FACTORY_PURCHASED_BOARDS; DIRECT_SUMMON_QUEUE; FIXED_AUGMENT_SNAPSHOTS; NO_CLIENTS; "
                            + "INVULNERABLE_BOSSES; UNSEEDED_ENTITY_AI; ONE_TEMPLATE; NOT_PRODUCTION_TRAFFIC");
            String json = JSON.toJson(report);
            SemionTd.LOGGER.info("MATCH_PROFILE {}", json);
            try {
                Files.writeString(Path.of("match-profile-" + variant + ".json"), json, StandardCharsets.UTF_8);
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
            cleanup();
            if (sparkMode.equals("profile")) {
                awaitProfile(System.nanoTime() + 120_000_000_000L);
            } else {
                context.succeed();
            }
        }

        void awaitProfile(long deadlineNanos) {
            Path directory = Path.of("config/spark");
            if (Files.isDirectory(directory)) {
                try (var files = Files.list(directory)) {
                    if (files.anyMatch(path -> path.toString().endsWith(".sparkprofile"))) {
                        context.succeed();
                        return;
                    }
                } catch (IOException failure) {
                    throw new IllegalStateException(failure);
                }
            }
            if (System.nanoTime() > deadlineNanos) {
                context.fail(Component.literal("Spark profile was not saved."));
                return;
            }
            context.runAfterDelay(1, () -> awaitProfile(deadlineNanos));
        }

        void command(String command) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        }

        void next(Runnable action) {
            context.runAfterDelay(1, () -> safely(action));
        }

        void safely(Runnable action) {
            try {
                action.run();
            } catch (RuntimeException | AssertionError failure) {
                SemionTd.LOGGER.error("Match profile fixture failed", failure);
                cleanup();
                context.fail(Component.literal("Match profile fixture failed: " + failure));
            }
        }

        void cleanup() {
            if (game != null) {
                game.close();
                game = null;
            }
            if (arena != null) {
                arena.close();
                arena = null;
            }
        }

        void write(String name, Object value) {
            try {
                Field field = SemionGame.class.getDeclaredField(name);
                field.setAccessible(true);
                field.set(game, value);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        }

        Object read(String name) {
            try {
                Field field = SemionGame.class.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(game);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        }
    }

    private static PlayerAugmentState.Selection selection(int round, AugmentRarity rarity, String id, String mode) {
        return new PlayerAugmentState.Selection(round, rarity, "semiontd:" + id, PlayerAugmentState.Outcome.SELECTED,
                null, new AugmentChoice(null, null, mode));
    }

    private static Map<String, Object> stats(long[] nanos, int from, int to) {
        long[] sorted = Arrays.copyOfRange(nanos, from, to);
        Arrays.sort(sorted);
        double sum = 0.0;
        for (long value : sorted) {
            sum += value;
        }
        return map("ticks", sorted.length, "mean", sum / sorted.length / 1_000_000.0,
                "median", percentile(sorted, 0.50), "p95", percentile(sorted, 0.95),
                "p99", percentile(sorted, 0.99), "max", percentile(sorted, 1.0));
    }

    private static double percentile(long[] sorted, double fraction) {
        return sorted[Math.max(0, (int) Math.ceil(sorted.length * fraction) - 1)] / 1_000_000.0;
    }

    private static long gc(boolean millis) {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(bean -> millis ? bean.getCollectionTime() : bean.getCollectionCount())
                .filter(value -> value >= 0).sum();
    }

    private static double square(double value) {
        return value * value;
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
