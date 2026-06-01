package kim.biryeong.semiontd.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.MapConfig;
import kim.biryeong.semiontd.config.ProgressionConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.StartPlacement;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.map.GameArenaLoader;
import kim.biryeong.semiontd.map.LobbyWorld;
import kim.biryeong.semiontd.progression.MatchProgressionReward;
import kim.biryeong.semiontd.progression.ProgressionService;
import kim.biryeong.semiontd.summon.SummonResult;
import kim.biryeong.semiontd.summon.SummonResultType;
import kim.biryeong.semiontd.test.TestTowerService;
import kim.biryeong.semiontd.test.tower.TestTowerTypes;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class SemionLifecycleGameTest implements CustomTestMethodInvoker {
    private static final SemionJob TEST_RICH_JOB = JobRegistry.register(new SemionJob(
            ResourceLocation.fromNamespaceAndPath("semion-td-test", "rich"),
            Component.literal("Rich Test Job"),
            List.of(Component.literal("Adds starting resources for tests."))
    ) {
        @Override
        public long modifyStartingMineral(JobContext context, long baseMineral) {
            return baseMineral + 77;
        }

        @Override
        public long modifyStartingGas(JobContext context, long baseGas) {
            return baseGas + 3;
        }

        @Override
        public long modifyStartingIncome(JobContext context, long baseIncome) {
            return baseIncome + 4;
        }

        @Override
        public long modifyStartingGasPerSec(JobContext context, long baseGasPerSec) {
            return baseGasPerSec + 2;
        }
    });

    @GameTest
    public void defaultArenaMapTemplateLoads(GameTestHelper context) {
        GameArena arena = null;
        try {
            arena = GameArenaLoader.load(context.getLevel().getServer(), MapConfig.defaultConfig());
            if (!assertTrue(context, arena.teamArena(TeamId.RED).isPresent(), "Default arena should create a red arena.")) {
                return;
            }
            if (!assertTrue(context, arena.lane(TeamId.RED, 1).isPresent(), "Default arena should expose lane 1.")) {
                return;
            }
            if (!assertTrue(context, arena.lane(TeamId.RED, 5).isPresent(), "Default arena should expose lane 5.")) {
                return;
            }
            if (!assertEquals(
                    context,
                    49,
                    arena.lane(TeamId.RED, 1).orElseThrow().finalDefenseTowerSlots().size(),
                    "Default arena should expose the shared 7x7 final defense slots."
            )) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Default arena map template should load: " + exception.getMessage()));
        } finally {
            if (arena != null) {
                arena.unload();
            }
        }
    }

    @GameTest(maxTicks = 900)
    public void defaultArenaLaneMonstersConvergeNearBoss(GameTestHelper context) {
        GameArena arena;
        try {
            arena = GameArenaLoader.load(context.getLevel().getServer(), MapConfig.defaultConfig());
        } catch (Exception exception) {
            context.fail(Component.literal("Default arena map template should load for convergence test: " + exception.getMessage()));
            return;
        }

        List<PlayerLane> lanes = new ArrayList<>();
        List<SemionMonsterEntity> monsters = new ArrayList<>();
        try {
            var redArena = arena.teamArena(TeamId.RED).orElseThrow();
            for (int laneId = 1; laneId <= 5; laneId++) {
                PlayerLane lane = new PlayerLane(
                        TeamId.RED,
                        laneId,
                        playerId("arena-converge-" + laneId),
                        redArena.world(),
                        redArena.layout().lane(laneId).orElseThrow()
                );
                lane.enqueueWaveMonster(new WaveMonsterEntry(
                        "arena-converge-" + laneId,
                        1000.0,
                        0.0,
                        0.0,
                        AttackKind.MELEE,
                        "minecraft:zombie",
                        null,
                        0,
                        1
                ));
                lane.tick(context.getLevel().getServer());
                if (!assertEquals(context, 1, lane.activeMonsters().size(), "Lane " + laneId + " should spawn one test monster.")) {
                    arena.unload();
                    return;
                }
                if (!(redArena.world().getEntity(lane.activeMonsters().getFirst().minecraftEntityId()) instanceof SemionMonsterEntity monsterEntity)) {
                    arena.unload();
                    context.fail(Component.literal("Lane " + laneId + " monster entity should exist."));
                    return;
                }
                monsterEntity.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.5);
                lanes.add(lane);
                monsters.add(monsterEntity);
            }

            Vec3 commonFinalWaypoint = null;
            Vec3 commonBossPoint = null;
            for (int i = 0; i < monsters.size(); i++) {
                List<Vec3> path = monsters.get(i).pathPoints();
                if (!assertTrue(context, path.size() >= 2, "Lane " + (i + 1) + " should have final waypoint and boss path points.")) {
                    arena.unload();
                    return;
                }
                Vec3 finalWaypoint = path.get(path.size() - 2);
                Vec3 bossPoint = path.getLast();
                if (commonFinalWaypoint == null) {
                    commonFinalWaypoint = finalWaypoint;
                    commonBossPoint = bossPoint;
                } else {
                    if (!assertEquals(context, commonFinalWaypoint, finalWaypoint, "Every lane should share the same final waypoint.")) {
                        arena.unload();
                        return;
                    }
                    if (!assertEquals(context, commonBossPoint, bossPoint, "Every lane should path to the same boss point.")) {
                        arena.unload();
                        return;
                    }
                }
            }

            Vec3 bossPoint = commonBossPoint;
            context.runAfterDelay(700, () -> {
                try {
                    for (int i = 0; i < lanes.size(); i++) {
                        lanes.get(i).tick(context.getLevel().getServer());
                        SemionMonsterEntity monster = monsters.get(i);
                        if (!assertTrue(context, monster.isAlive(), "Lane " + (i + 1) + " monster should remain alive while converging.")) {
                            return;
                        }
                        if (!assertTrue(
                                context,
                                monster.position().distanceTo(bossPoint) < 8.0,
                                "Lane " + (i + 1) + " monster should converge near the shared boss side."
                        )) {
                            return;
                        }
                    }
                    context.succeed();
                } finally {
                    arena.unload();
                }
            });
        } catch (RuntimeException exception) {
            arena.unload();
            context.fail(Component.literal("Default arena convergence test failed: " + exception.getMessage()));
        }
    }

    @GameTest
    public void gameManagerLoadsLobbyArenaAndStartsActualArena(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        SemionGameManager manager = new SemionGameManager();
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-manager-template-lifecycle").resolve("profiles.json");
        } catch (java.io.IOException exception) {
            context.fail(Component.literal("Failed to create temporary progression store path."));
            return;
        }
        manager.configure(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                MapConfig.defaultConfig(),
                ProgressionConfig.defaultConfig(),
                storePath
        );

        try {
            SemionGame game = manager.createGame(server);
            if (!assertTrue(context, manager.lobbyWorld().isPresent(), "Manager create should load the lobby template.")) {
                return;
            }
            if (!assertTrue(context, manager.activeGame().orElse(null) == game, "Manager create should retain the active game.")) {
                return;
            }
            if (!assertTrue(context, game.arena().lane(TeamId.RED, 1).isPresent(), "Created game should expose RED lane 1 from the arena template.")) {
                return;
            }
            if (!assertTrue(context, game.arena().lane(TeamId.BLUE, 1).isPresent(), "Created game should expose BLUE lane 1 from the arena template.")) {
                return;
            }

            UUID redId = playerId("manager-template-red");
            UUID blueId = playerId("manager-template-blue");
            if (!assertTrue(context, game.markReady(redId), "RED player should be able to ready after admin create.")) {
                return;
            }
            if (!assertTrue(context, game.markReady(blueId), "BLUE player should be able to ready after admin create.")) {
                return;
            }
            ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                    MatchMode.NORMAL,
                    List.of(
                            new AssignedParticipant(redId, "manager-template-red", TeamId.RED, 1),
                            new AssignedParticipant(blueId, "manager-template-blue", TeamId.BLUE, 1)
                    ),
                    Set.of(),
                    2
            );

            if (!assertTrue(context, game.start(server, plan), "Manager-created arena should start with ready participants.")) {
                return;
            }
            if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Started game should enter prepare phase.")) {
                return;
            }
            if (!assertTrue(context, game.rosterLocked(), "Started game should lock the roster.")) {
                return;
            }
            if (!assertEquals(context, 2, game.players().size(), "Started game should register both active participants.")) {
                return;
            }
            if (!assertTrue(context, game.teams().get(TeamId.RED).laneGroup().bossEntity().isPresent(), "RED boss entity should spawn in the actual arena world.")) {
                return;
            }
            if (!assertTrue(context, game.teams().get(TeamId.BLUE).laneGroup().bossEntity().isPresent(), "BLUE boss entity should spawn in the actual arena world.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Manager template lifecycle should work: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void gameManagerLoadsLobbyOnServerStart(GameTestHelper context) {
        SemionGameManager manager = new SemionGameManager();
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-manager-startup-lobby").resolve("profiles.json");
        } catch (java.io.IOException exception) {
            context.fail(Component.literal("Failed to create temporary progression store path."));
            return;
        }
        manager.configure(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                MapConfig.defaultConfig(),
                ProgressionConfig.defaultConfig(),
                storePath
        );

        try {
            manager.scheduleStartupLobbyLoad(context.getLevel().getServer());
            for (int i = 0; i < 25; i++) {
                manager.tickStartupLobbyLoad(context.getLevel().getServer());
            }
            if (!assertTrue(context, manager.lobbyWorld().isPresent(), "Server start should load the lobby template.")) {
                return;
            }
            LobbyWorld firstLobby = manager.lobbyWorld().orElseThrow();

            manager.scheduleStartupLobbyLoad(context.getLevel().getServer());
            for (int i = 0; i < 25; i++) {
                manager.tickStartupLobbyLoad(context.getLevel().getServer());
            }
            if (!assertTrue(context, manager.lobbyWorld().orElse(null) == firstLobby, "Server start lobby load should be idempotent.")) {
                return;
            }
            context.succeed();
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void gameManagerStartsAfterPreloadCountdown(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        UUID redId = playerId("manager-countdown-red");
        UUID blueId = playerId("manager-countdown-blue");
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), SyntheticArenaFactory.create(
                context.getLevel(),
                context.absolutePos(BlockPos.ZERO)
        ));
        SemionGameManager manager = new SemionGameManager();
        setField(manager, "activeGame", game);

        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "manager-countdown-red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "manager-countdown-blue", TeamId.BLUE, 1)
                ),
                Set.of(),
                2
        );

        if (!assertEquals(
                context,
                SemionGameManager.StartCountdownResult.SCHEDULED,
                manager.scheduleStart(server, plan),
                "Manager start should schedule a preload countdown."
        )) {
            return;
        }
        if (!assertTrue(context, manager.startCountdownActive(), "Start countdown should be active before the game starts.")) {
            return;
        }
        if (!assertEquals(context, RoundPhase.WAITING, game.phase(), "Scheduled start should not immediately leave waiting phase.")) {
            return;
        }

        for (int i = 0; i < SemionGameManager.START_COUNTDOWN_TICKS; i++) {
            manager.tick(server);
        }

        if (!assertTrue(context, !manager.startCountdownActive(), "Start countdown should clear after the countdown finishes.")) {
            return;
        }
        if (!assertTrue(context, game.rosterLocked(), "Game should lock the roster when the countdown finishes.")) {
            return;
        }
        if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Game should enter prepare phase after the countdown.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void gameParticipantsBypassServerPlayerLimit(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        UUID redId = playerId("limit-bypass-red");
        UUID blueId = playerId("limit-bypass-blue");
        UUID spectatorId = playerId("limit-bypass-spectator");
        UUID strangerId = playerId("limit-bypass-stranger");
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), SyntheticArenaFactory.create(
                context.getLevel(),
                context.absolutePos(BlockPos.ZERO)
        ));
        SemionGameManager manager = new SemionGameManager();
        setField(manager, "activeGame", game);

        game.markReady(redId);
        if (!assertTrue(context, manager.canBypassPlayerLimit(redId), "Ready players should bypass the server player limit while waiting for a match.")) {
            return;
        }
        if (!assertTrue(context, !manager.canBypassPlayerLimit(strangerId), "Unselected players should not bypass the server player limit.")) {
            return;
        }

        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "limit-bypass-red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "limit-bypass-blue", TeamId.BLUE, 1)
                ),
                Set.of(spectatorId),
                2
        );
        if (!assertEquals(
                context,
                SemionGameManager.StartCountdownResult.SCHEDULED,
                manager.scheduleStart(server, plan),
                "Manager should schedule the selected participant plan."
        )) {
            return;
        }
        if (!assertTrue(context, manager.canBypassPlayerLimit(redId), "Selected active participants should bypass the limit during start countdown.")) {
            return;
        }
        if (!assertTrue(context, manager.canBypassPlayerLimit(spectatorId), "Selected match spectators should bypass the limit during start countdown.")) {
            return;
        }

        for (int i = 0; i < SemionGameManager.START_COUNTDOWN_TICKS; i++) {
            manager.tick(server);
        }
        if (!assertTrue(context, manager.canBypassPlayerLimit(redId), "Active participants should bypass the limit after the roster locks.")) {
            return;
        }
        if (!assertTrue(context, manager.canBypassPlayerLimit(spectatorId), "Match spectators should bypass the limit after the roster locks.")) {
            return;
        }
        if (!assertTrue(context, !manager.canBypassPlayerLimit(strangerId), "Late unrelated players should still respect the server player limit.")) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 700)
    public void actualArenaSupportsMinimumPlayableActionLoop(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        SemionGameManager manager = new SemionGameManager();
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-manager-playable-loop").resolve("profiles.json");
        } catch (java.io.IOException exception) {
            context.fail(Component.literal("Failed to create temporary progression store path."));
            return;
        }
        manager.configure(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                MapConfig.defaultConfig(),
                ProgressionConfig.defaultConfig(),
                storePath
        );

        try {
            SemionGame game = manager.createGame(server);
            UUID redId = playerId("playable-loop-red");
            UUID blueId = playerId("playable-loop-blue");
            game.markReady(redId);
            game.markReady(blueId);

            ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                    MatchMode.NORMAL,
                    List.of(
                            new AssignedParticipant(redId, "playable-loop-red", TeamId.RED, 1),
                            new AssignedParticipant(blueId, "playable-loop-blue", TeamId.BLUE, 1)
                    ),
                    Set.of(),
                    2
            );
            if (!assertTrue(context, game.start(server, plan), "Actual arena game should start from admin-created setup.")) {
                return;
            }

            SemionPlayer red = game.players().get(redId);
            SemionPlayer blue = game.players().get(blueId);
            if (!assertTrue(context, red != null && blue != null, "Started game should register both active players.")) {
                return;
            }
            if (!assertEquals(context, TeamId.RED, red.teamId(), "RED player should keep assigned team.")) {
                return;
            }
            if (!assertEquals(context, 1, red.laneId(), "RED player should keep assigned lane.")) {
                return;
            }
            Vec3 redSpawn = game.arena().teamArena(TeamId.RED)
                    .map(teamArena -> StartPlacement.activePlayerSpawn(teamArena.layout(), red.laneId()))
                    .orElse(null);
            if (!assertTrue(context, redSpawn != null, "RED active player spawn should resolve from the actual arena layout.")) {
                return;
            }

            PlayerLane redLane = game.teams().get(TeamId.RED).laneGroup().lane(1).orElseThrow();
            BlockPos towerPos = redLane.laneLayout().laneArea().min();
            if (!assertEquals(
                    context,
                    TowerPlacementResult.SUCCESS,
                    TestTowerService.placeTestTower(game, redId, towerPos),
                    "Test tower placement should work in the actual arena during prepare."
            )) {
                return;
            }
            PlayerEconomy redEconomy = red.economy();
            if (!assertEquals(
                    context,
                    EconomyConfig.defaultConfig().startingDiamond() - TestTowerTypes.TEST_DIRECT.mineralCost(),
                    redEconomy.diamond(),
                    "Tower placement should spend diamond."
            )) {
                return;
            }
            if (!assertTrue(context, game.upgradeGasProduction(redId), "Emerald production upgrade should work during the playable loop.")) {
                return;
            }
            if (!assertEquals(context, 2L, redEconomy.emeraldPerSec(), "Emerald production upgrade should increase emerald per second.")) {
                return;
            }

            long emeraldBeforeSummon = redEconomy.emerald();
            long incomeBeforeSummon = redEconomy.income();
            SummonResult summon = game.summonMonster(redId, "grunt");
            if (!assertEquals(context, SummonResultType.UNKNOWN_SUMMON, summon.type(), "Removed income summons should not be available during prepare.")) {
                return;
            }
            if (!assertEquals(context, emeraldBeforeSummon, redEconomy.emerald(), "Unknown summon should not spend emerald.")) {
                return;
            }
            if (!assertEquals(context, incomeBeforeSummon, redEconomy.income(), "Unknown summon should not increase income.")) {
                return;
            }

            for (int i = 0; i < SemionGame.DEFAULT_PREPARE_TICKS + 1; i++) {
                manager.tick(server);
            }
            if (!assertEquals(context, RoundPhase.LANE_WAVE, game.phase(), "Prepare should advance into wave phase.")) {
                return;
            }
            manager.tick(server);
            if (!assertEquals(context, RoundPhase.ROUND_PAYOUT, game.phase(), "Cleared wave should advance to payout.")) {
                return;
            }
            long diamondBeforePayout = redEconomy.diamond();
            manager.tick(server);
            if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Payout should advance to next prepare phase.")) {
                return;
            }
            if (!assertEquals(context, 2, game.currentRound(), "Playable loop should reach round 2.")) {
                return;
            }
            if (!assertTrue(context, redEconomy.diamond() > diamondBeforePayout, "Round payout should add diamond income.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Actual arena playable loop should work: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void matchResultRecordsWinnerAndLoser(GameTestHelper context) {
        SemionGame game = startedTwoPlayerGame(context, playerId("life-red"), "life-red", playerId("life-blue"), "life-blue");
        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "Blue boss should die to finish the match.")) {
            return;
        }
        if (!assertEquals(context, RoundPhase.ENDED, game.phase(), "Game should enter ENDED phase after the last team survives.")) {
            return;
        }

        Optional<MatchResult> result = game.matchResult();
        if (!assertTrue(context, result.isPresent(), "Ended game should expose a match result.")) {
            return;
        }
        if (!assertEquals(context, Set.of(TeamId.RED), result.get().winningTeams(), "RED should be the only winning team.")) {
            return;
        }

        Map<String, Boolean> winnersByName = result.get().participants().stream()
                .collect(java.util.stream.Collectors.toMap(MatchParticipantResult::playerName, MatchParticipantResult::winner));
        if (!assertEquals(context, Boolean.TRUE, winnersByName.get("life-red"), "RED participant should be marked as winner.")) {
            return;
        }
        if (!assertEquals(context, Boolean.FALSE, winnersByName.get("life-blue"), "BLUE participant should be marked as loser.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void eliminatedParticipantsBecomeMatchSpectatorsWithoutPollutingResultSpectators(GameTestHelper context) {
        UUID redId = playerId("spectator-red");
        UUID blueId = playerId("spectator-blue");
        UUID spectatorId = playerId("spectator-waiting");
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), SyntheticArenaFactory.create(
                context.getLevel(),
                context.absolutePos(BlockPos.ZERO)
        ));
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "spectator-red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "spectator-blue", TeamId.BLUE, 1)
                ),
                Set.of(spectatorId),
                2
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Game should start for spectator lifecycle test.")) {
            return;
        }
        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "Blue boss should die to finish the spectator lifecycle test.")) {
            return;
        }
        if (!assertEquals(context, 2, game.spectatorCount(), "Initial spectator plus eliminated BLUE player should be match spectators.")) {
            return;
        }

        Optional<MatchResult> result = game.matchResult();
        if (!assertTrue(context, result.isPresent(), "Ended game should expose a match result.")) {
            return;
        }
        if (!assertEquals(context, Set.of(spectatorId), result.get().spectatorIds(), "Only original spectators should be stored as match spectators.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void matchResultCarriesPlayerStatsSnapshot(GameTestHelper context) {
        UUID redId = playerId("stats-red");
        UUID blueId = playerId("stats-blue");
        SemionGame game = startedTwoPlayerGame(context, redId, "stats-red", blueId, "stats-blue");

        game.players().get(redId).matchStats().recordMonsterKill(12);
        game.players().get(redId).matchStats().recordSummonedMonster();
        game.players().get(redId).economy().addIncome(5);

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "Blue boss should die to finish the stat snapshot test.")) {
            return;
        }

        MatchParticipantResult redResult = game.matchResult().orElseThrow().participants().stream()
                .filter(participant -> participant.playerId().equals(redId))
                .findFirst()
                .orElseThrow();
        if (!assertEquals(context, 1L, redResult.stats().monsterKills(), "Match result should preserve player kill count.")) {
            return;
        }
        if (!assertEquals(context, 12L, redResult.stats().killMinerals(), "Match result should preserve kill mineral total.")) {
            return;
        }
        if (!assertEquals(context, 1L, redResult.stats().summonedMonsters(), "Match result should preserve summoned monster count.")) {
            return;
        }
        if (!assertEquals(context, 5L, redResult.stats().finalIncome(), "Match result should preserve final income.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void selectedJobAppliesStartingEconomyModifiers(GameTestHelper context) {
        UUID redId = playerId("job-red");
        UUID blueId = playerId("job-blue");
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), SyntheticArenaFactory.create(
                context.getLevel(),
                context.absolutePos(BlockPos.ZERO)
        ));
        if (!assertTrue(context, game.selectJob(redId, TEST_RICH_JOB.id()), "Job selection should succeed before roster lock.")) {
            return;
        }
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "job-red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "job-blue", TeamId.BLUE, 1)
                ),
                Set.of(),
                2
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Game should start with selected job.")) {
            return;
        }

        var red = game.players().get(redId);
        var blue = game.players().get(blueId);
        if (!assertEquals(context, TEST_RICH_JOB.id(), red.job().orElseThrow().id(), "Selected player should keep the selected job.")) {
            return;
        }
        if (!assertEquals(context, JobRegistry.defaultJob().id(), blue.job().orElseThrow().id(), "Unselected player should receive the default job.")) {
            return;
        }
        if (!assertEquals(context, 277L, red.economy().mineral(), "Job should modify starting mineral.")) {
            return;
        }
        if (!assertEquals(context, 53L, red.economy().gas(), "Job should modify starting gas.")) {
            return;
        }
        if (!assertEquals(context, 4L, red.economy().income(), "Job should modify starting income.")) {
            return;
        }
        if (!assertEquals(context, 3L, red.economy().gasPerSec(), "Job should modify starting gas per second.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void progressionServiceWritesProfilesToDedicatedFile(GameTestHelper context) {
        UUID winnerId = playerId("profile-winner");
        UUID loserId = playerId("profile-loser");
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-progression-test").resolve("profiles.json");
        } catch (java.io.IOException exception) {
            context.fail(Component.literal("Failed to create temporary progression store path."));
            return;
        }
        ProgressionService progressionService = new ProgressionService(new ProgressionConfig(10, 15, 5), storePath);

        MatchResult result = new MatchResult(
                List.of(
                        new MatchParticipantResult(winnerId, "profile-winner", TeamId.RED, true),
                        new MatchParticipantResult(loserId, "profile-loser", TeamId.BLUE, false)
                ),
                Set.of(),
                Set.of(TeamId.RED),
                4
        );
        Map<UUID, MatchProgressionReward> rewards = progressionService.applyMatchResult(context.getLevel().getServer(), result);

        if (!assertEquals(context, 25L, rewards.get(winnerId).currencyAwarded(), "Winner should receive play reward plus win bonus.")) {
            return;
        }
        if (!assertEquals(context, 15L, rewards.get(loserId).currencyAwarded(), "Loser should receive play reward plus loss reward.")) {
            return;
        }
        if (!assertTrue(context, Files.exists(storePath), "Progression store should be written to a dedicated file.")) {
            return;
        }
        Map<UUID, MatchProgressionReward> duplicateRewards = progressionService.applyMatchResult(context.getLevel().getServer(), result);
        if (!assertTrue(context, duplicateRewards.isEmpty(), "Duplicate match application should return no progression rewards.")) {
            return;
        }

        ProgressionService reloaded = new ProgressionService(new ProgressionConfig(10, 15, 5), storePath);
        if (!assertEquals(context, 25L, reloaded.profile(context.getLevel().getServer(), winnerId, "profile-winner").cosmeticCurrency(), "Winner cosmetic currency should survive service reload.")) {
            return;
        }
        if (!assertEquals(context, 15L, reloaded.profile(context.getLevel().getServer(), loserId, "profile-loser").cosmeticCurrency(), "Loser cosmetic currency should survive service reload.")) {
            return;
        }
        if (!assertEquals(context, 1, reloaded.profile(context.getLevel().getServer(), winnerId, "profile-winner").gamesPlayed(), "Duplicate apply should not increment winner games played.")) {
            return;
        }
        if (!assertEquals(context, 1, reloaded.profile(context.getLevel().getServer(), loserId, "profile-loser").gamesPlayed(), "Duplicate apply should not increment loser games played.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void selectedJobPersistsAndAppliesToNewLobby(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        MinecraftServer server = context.getLevel().getServer();
        ResourceLocation jobId = JobRegistry.defaultJob().id();
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-selected-job-test").resolve("profiles.json");
        } catch (java.io.IOException exception) {
            context.fail(Component.literal("Failed to create temporary selected-job store path."));
            return;
        }

        ProgressionService progressionService = new ProgressionService(ProgressionConfig.defaultConfig(), storePath);
        progressionService.saveSelectedJob(server, player.getUUID(), player.getGameProfile().getName(), jobId);
        ProgressionService reloaded = new ProgressionService(ProgressionConfig.defaultConfig(), storePath);
        if (!assertEquals(
                context,
                jobId,
                reloaded.profile(server, player.getUUID(), player.getGameProfile().getName()).selectedJobResource().orElse(null),
                "Selected job should survive progression store reload."
        )) {
            return;
        }

        SemionGameManager manager = new SemionGameManager();
        manager.configure(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                MapConfig.defaultConfig(),
                ProgressionConfig.defaultConfig(),
                storePath
        );
        try {
            SemionGame game = manager.createGame(server);
            if (!assertEquals(
                    context,
                    jobId,
                    game.selectedJobOrDefault(player.getUUID()).id(),
                    "New lobbies should preselect each online player's persisted job."
            )) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Persisted selected job should apply to a new lobby: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void gameManagerFinalizesEndedMatchAndClearsActiveGame(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        UUID redId = playerId("manager-red");
        UUID blueId = playerId("manager-blue");
        SemionGameManager manager = new SemionGameManager();
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-manager-progression-test").resolve("profiles.json");
        } catch (java.io.IOException exception) {
            context.fail(Component.literal("Failed to create temporary manager progression store path."));
            return;
        }
        manager.configure(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                MapConfig.defaultConfig(),
                new ProgressionConfig(10, 15, 5),
                storePath
        );

        SemionGame game = startedTwoPlayerGame(context, redId, "manager-red", blueId, "manager-blue");
        setField(manager, "activeGame", game);
        setField(manager, "lobbyWorld", new LobbyWorld(() -> {
        }, context.getLevel(), new Vec3(0.5, 2.0, 0.5)));

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "Blue boss should die to end the manager test match.")) {
            return;
        }

        manager.tick(server);

        if (!assertTrue(context, manager.activeGame().isPresent(), "Game manager should keep the ended match open while delaying result finalization.")) {
            return;
        }
        if (!assertTrue(context, manager.lastMatchResult().isEmpty(), "Game manager should not publish the match result during the result delay.")) {
            return;
        }

        for (int i = 0; i < SemionGameManager.MATCH_RESULT_DELAY_TICKS + 1; i++) {
            manager.tick(server);
        }

        if (!assertTrue(context, manager.activeGame().isEmpty(), "Game manager should clear the active game after finalization.")) {
            return;
        }
        if (!assertTrue(context, manager.lastMatchResult().isPresent(), "Game manager should retain the last match result after finalization.")) {
            return;
        }
        if (!assertTrue(context, getField(manager, "pendingMatchResultDialog") != null, "Game manager should queue the result dialog after sending players to lobby.")) {
            return;
        }
        if (!assertEquals(
                context,
                SemionGameManager.MATCH_RESULT_DIALOG_AFTER_LOBBY_DELAY_TICKS,
                getField(manager, "pendingMatchResultDialogDelayTicks"),
                "Result dialog should wait after lobby teleport before opening."
        )) {
            return;
        }
        if (!assertEquals(context, Set.of(TeamId.RED), manager.lastMatchResult().orElseThrow().winningTeams(), "Last match result should preserve the winning team.")) {
            return;
        }
        if (!assertEquals(context, 25L, manager.profile(server, redId, "manager-red").cosmeticCurrency(), "Winner reward should be written during finalization.")) {
            return;
        }
        if (!assertEquals(context, 15L, manager.profile(server, blueId, "manager-blue").cosmeticCurrency(), "Loser reward should be written during finalization.")) {
            return;
        }
        if (!assertTrue(context, Files.exists(storePath), "Manager finalization should persist progression in the dedicated file.")) {
            return;
        }
        for (int i = 0; i < SemionGameManager.MATCH_RESULT_DIALOG_AFTER_LOBBY_DELAY_TICKS + 1; i++) {
            manager.tick(server);
        }
        if (!assertTrue(context, getField(manager, "pendingMatchResultDialog") == null, "Game manager should clear the queued result dialog after showing it.")) {
            return;
        }
        context.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
        context.setBlock(0, 0, 0, Blocks.AIR);
        method.invoke(this, context);
    }

    private static SemionGame startedTwoPlayerGame(
            GameTestHelper context,
            UUID redId,
            String redName,
            UUID blueId,
            String blueName
    ) {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), SyntheticArenaFactory.create(
                context.getLevel(),
                context.absolutePos(BlockPos.ZERO)
        ));
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, redName, TeamId.RED, 1),
                        new AssignedParticipant(blueId, blueName, TeamId.BLUE, 1)
                ),
                Set.of(),
                2
        );
        if (!game.start(context.getLevel().getServer(), plan)) {
            throw new IllegalStateException("Failed to start synthetic two-player game for lifecycle test.");
        }
        return game;
    }

    private static UUID playerId(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to set field " + fieldName + ".", exception);
        }
    }

    private static Object getField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to get field " + fieldName + ".", exception);
        }
    }

    private static boolean assertTrue(GameTestHelper context, boolean condition, String message) {
        if (!condition) {
            context.fail(Component.literal(message));
            return false;
        }
        return true;
    }

    private static boolean assertEquals(GameTestHelper context, Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            context.fail(Component.literal(message + " expected=" + expected + " actual=" + actual));
            return false;
        }
        return true;
    }
}
