package kim.biryeong.semionTd.gametest;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semionTd.config.AttackKind;
import kim.biryeong.semionTd.config.CurrencyType;
import kim.biryeong.semionTd.config.EconomyConfig;
import kim.biryeong.semionTd.config.SemionConfigLoader;
import kim.biryeong.semionTd.config.SummonConfig;
import kim.biryeong.semionTd.config.SummonMonsterEntry;
import kim.biryeong.semionTd.config.WaveMonsterEntry;
import kim.biryeong.semionTd.entity.boss.SemionBossEntity;
import kim.biryeong.semionTd.entity.monster.KillSourceKind;
import kim.biryeong.semionTd.test.entity.SemionTestTowerEntity;
import kim.biryeong.semionTd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semionTd.config.WaveConfig;
import kim.biryeong.semionTd.game.AssignedParticipant;
import kim.biryeong.semionTd.game.EconomyService;
import kim.biryeong.semionTd.game.MatchMode;
import kim.biryeong.semionTd.game.ParticipantSelectionPlan;
import kim.biryeong.semionTd.game.ParticipantSelectionService;
import kim.biryeong.semionTd.game.RoundPhase;
import kim.biryeong.semionTd.game.SemionGame;
import kim.biryeong.semionTd.game.PlayerLane;
import kim.biryeong.semionTd.game.StartPlacement;
import kim.biryeong.semionTd.game.StartCandidate;
import kim.biryeong.semionTd.game.TeamId;
import kim.biryeong.semionTd.game.TowerPlacementResult;
import kim.biryeong.semionTd.game.TowerUpgradeResult;
import kim.biryeong.semionTd.game.VanillaTeamBridge;
import kim.biryeong.semionTd.test.TestTowerService;
import kim.biryeong.semionTd.test.tower.TestTower;
import kim.biryeong.semionTd.tower.TowerCategory;
import kim.biryeong.semionTd.tower.TowerType;
import kim.biryeong.semionTd.test.tower.TestTowerTypes;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.slf4j.LoggerFactory;

public final class SemionParticipantGameTest implements CustomTestMethodInvoker {
    private static kim.biryeong.semionTd.map.GameArena testArena(GameTestHelper context) {
        return SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(net.minecraft.core.BlockPos.ZERO));
    }

    @GameTest
    public void testModeSelectsOneVersusOne(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.select(List.of(
                candidate("alpha", TeamId.RED),
                candidate("beta", TeamId.BLUE),
                candidate("gamma", TeamId.GREEN),
                candidate("delta", TeamId.YELLOW),
                candidate("epsilon")
        ), MatchMode.TEST);

        if (!assertPresent(context, plan, "Expected a selection plan for test mode with 5 players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertEquals(context, 2, value.activePlayerCount(), "Test mode should select exactly 2 active players.")) {
            return;
        }
        if (!assertEquals(context, 2, value.activeTeamCount(), "Test mode should activate exactly 2 teams.")) {
            return;
        }
        if (!assertEquals(context, 3, value.spectatorCount(), "Remaining players should be spectators in test mode.")) {
            return;
        }
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 1, TeamId.BLUE, 1))) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void normalModeSelectsFiveVersusFourWithoutSpectator(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.select(List.of(
                candidate("p1"),
                candidate("p2"),
                candidate("p3"),
                candidate("p4"),
                candidate("p5"),
                candidate("p6"),
                candidate("p7"),
                candidate("p8"),
                candidate("p9")
        ), MatchMode.NORMAL);

        if (!assertPresent(context, plan, "Expected a selection plan for 9 players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertEquals(context, 9, value.activePlayerCount(), "9 players should use all 9 active players.")) {
            return;
        }
        if (!assertEquals(context, 2, value.activeTeamCount(), "9 players should produce 2 active teams.")) {
            return;
        }
        if (!assertEquals(context, 0, value.spectatorCount(), "9 players should not leave a spectator.")) {
            return;
        }
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 5, TeamId.BLUE, 4))) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void normalModeSelectsThreeBalancedTeamsFromTwelve(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.select(List.of(
                candidate("p1"),
                candidate("p2"),
                candidate("p3"),
                candidate("p4"),
                candidate("p5"),
                candidate("p6"),
                candidate("p7"),
                candidate("p8"),
                candidate("p9"),
                candidate("p10"),
                candidate("p11"),
                candidate("p12")
        ), MatchMode.NORMAL);

        if (!assertPresent(context, plan, "Expected a selection plan for 12 players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertEquals(context, 12, value.activePlayerCount(), "12 players should use all 12 active players.")) {
            return;
        }
        if (!assertEquals(context, 3, value.activeTeamCount(), "12 players should produce 3 active teams.")) {
            return;
        }
        if (!assertEquals(context, 0, value.spectatorCount(), "12 players should not leave a spectator.")) {
            return;
        }
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 4, TeamId.BLUE, 4, TeamId.GREEN, 4))) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void normalModeSelectsFourBalancedTeamsFromSixteen(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.select(List.of(
                candidate("p1"),
                candidate("p2"),
                candidate("p3"),
                candidate("p4"),
                candidate("p5"),
                candidate("p6"),
                candidate("p7"),
                candidate("p8"),
                candidate("p9"),
                candidate("p10"),
                candidate("p11"),
                candidate("p12"),
                candidate("p13"),
                candidate("p14"),
                candidate("p15"),
                candidate("p16")
        ), MatchMode.NORMAL);

        if (!assertPresent(context, plan, "Expected a selection plan for 16 players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertEquals(context, 16, value.activePlayerCount(), "16 players should use all 16 active players.")) {
            return;
        }
        if (!assertEquals(context, 4, value.activeTeamCount(), "16 players should produce 4 active teams.")) {
            return;
        }
        if (!assertEquals(context, 0, value.spectatorCount(), "16 players should not leave a spectator.")) {
            return;
        }
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 4, TeamId.BLUE, 4, TeamId.GREEN, 4, TeamId.YELLOW, 4))) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void normalModeSelectsFourVersusFourFromEight(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.select(List.of(
                candidate("p1"),
                candidate("p2"),
                candidate("p3"),
                candidate("p4"),
                candidate("p5"),
                candidate("p6"),
                candidate("p7"),
                candidate("p8")
        ), MatchMode.NORMAL);

        if (!assertPresent(context, plan, "Expected a selection plan for 8 players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 4, TeamId.BLUE, 4))) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void normalModeSelectsThreeVersusThreeFromSix(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.select(List.of(
                candidate("p1"),
                candidate("p2"),
                candidate("p3"),
                candidate("p4"),
                candidate("p5"),
                candidate("p6")
        ), MatchMode.NORMAL);

        if (!assertPresent(context, plan, "Expected a selection plan for 6 players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 3, TeamId.BLUE, 3))) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void normalModeFallsBackToTwoVersusTwoFromFour(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.select(List.of(
                candidate("p1"),
                candidate("p2"),
                candidate("p3"),
                candidate("p4")
        ), MatchMode.NORMAL);

        if (!assertPresent(context, plan, "Expected a selection plan for 4 players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 2, TeamId.BLUE, 2))) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void normalModeRejectsOneVersusOneStyleRoster(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.select(List.of(
                candidate("p1"),
                candidate("p2"),
                candidate("p3")
        ), MatchMode.NORMAL);

        if (!assertTrue(context, plan.isEmpty(), "Normal mode should reject rosters that force a one-player team.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void preferredTeamsAreRespectedWhenCapacityExists(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.select(List.of(
                candidate("red-1", TeamId.RED),
                candidate("red-2", TeamId.RED),
                candidate("blue-1", TeamId.BLUE),
                candidate("blue-2", TeamId.BLUE)
        ), MatchMode.NORMAL);

        if (!assertPresent(context, plan, "Expected a selection plan for 4 players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertAssignedTeam(context, value, "red-1", TeamId.RED)) {
            return;
        }
        if (!assertAssignedTeam(context, value, "red-2", TeamId.RED)) {
            return;
        }
        if (!assertAssignedTeam(context, value, "blue-1", TeamId.BLUE)) {
            return;
        }
        if (!assertAssignedTeam(context, value, "blue-2", TeamId.BLUE)) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void scoreboardTeamsAreCreated(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        VanillaTeamBridge.ensureTeams(server);

        if (!assertScoreboardTeam(context, server, "semion_red")) {
            return;
        }
        if (!assertScoreboardTeam(context, server, "semion_blue")) {
            return;
        }
        if (!assertScoreboardTeam(context, server, "semion_green")) {
            return;
        }
        if (!assertScoreboardTeam(context, server, "semion_yellow")) {
            return;
        }
        if (!assertScoreboardTeam(context, server, "semion_spectator")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void startPlacementOffsetsPlayersByLane(GameTestHelper context) {
        Vec3 teamSpawn = testArena(context).teamArena(TeamId.RED)
                .orElseThrow()
                .layout()
                .teamSpawn();

        Vec3 laneOne = StartPlacement.activePlayerSpawn(
                testArena(context).teamArena(TeamId.RED).orElseThrow().layout(),
                1
        );
        Vec3 laneFive = StartPlacement.activePlayerSpawn(
                testArena(context).teamArena(TeamId.RED).orElseThrow().layout(),
                5
        );

        if (!assertEquals(context, teamSpawn.add(-2.5, 0.0, -2.5), laneOne, "Lane 1 start offset is incorrect.")) {
            return;
        }
        if (!assertEquals(context, teamSpawn.add(2.5, 0.0, 2.5), laneFive, "Lane 5 start offset is incorrect.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void spectatorPlacementFloatsAboveTeamSpawn(GameTestHelper context) {
        var layout = testArena(context).teamArena(TeamId.RED).orElseThrow().layout();
        Vec3 spectatorZero = StartPlacement.spectatorSpawn(layout, 0);
        Vec3 spectatorThree = StartPlacement.spectatorSpawn(layout, 3);

        if (!assertEquals(
                context,
                layout.teamSpawn().add(-5.0, 8.0, 0.0),
                spectatorZero,
                "Spectator base spawn is incorrect."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                layout.teamSpawn().add(2.5, 8.0, 0.0),
                spectatorThree,
                "Spectator spread offset is incorrect."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void syntheticArenaProvidesSevenBySevenFinalDefenseSlots(GameTestHelper context) {
        PlayerLane lane = redLane(startedSinglePlayerGame(context, stableUuid("slot-owner"), TeamId.RED), 1);

        if (!assertEquals(context, 49, lane.laneLayout().finalDefenseTowerSlots().size(), "Lane should expose 49 final defense slots from its 7x7 region.")) {
            return;
        }
        if (!assertEquals(
                context,
                lane.laneLayout().finalDefenseTowerSlots().getFirst(),
                lane.laneLayout().finalDefenseTowerSlots().stream()
                        .min(java.util.Comparator.comparingDouble(slot -> lane.laneLayout().bossPosition().distanceTo(
                                new Vec3(slot.x() + 0.5, slot.y(), slot.z() + 0.5)
                        )))
                        .orElseThrow(),
                "Final defense slots should be ordered by distance to boss."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void startSpawnsBossEntitiesForActiveTeams(GameTestHelper context) {
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(new AssignedParticipant(stableUuid("red-boss-owner"), "red-boss-owner", TeamId.RED, 1)),
                java.util.Set.of(),
                1
        );

        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                testArena(context)
        );

        if (!assertTrue(
                context,
                game.start(context.getLevel().getServer(), plan),
                "Game should start with a valid participant plan."
        )) {
            return;
        }

        context.runAfterDelay(1, () -> {
            if (!assertTrue(context, game.teams().get(TeamId.RED).laneGroup().hasBossEntity(), "RED boss entity should be tracked.")) {
                return;
            }
            if (!assertEquals(context, 1, countTrackedBossEntities(game), "One active team should track one boss entity.")) {
                return;
            }
            if (!assertTrue(
                    context,
                    game.teams().get(TeamId.RED).laneGroup().bossEntity().filter(entity -> !entity.isRemoved()).isPresent(),
                    "RED boss entity reference should be alive."
            )) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void killingBossRemovesBossEntity(GameTestHelper context) {
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(new AssignedParticipant(stableUuid("red-boss-owner"), "red-boss-owner", TeamId.RED, 1)),
                java.util.Set.of(),
                1
        );

        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                testArena(context)
        );

        if (!assertTrue(
                context,
                game.start(context.getLevel().getServer(), plan),
                "Game should start with a valid participant plan."
        )) {
            return;
        }

        if (!assertTrue(context, game.killBoss(TeamId.RED), "RED boss kill should succeed.")) {
            return;
        }

        context.runAfterDelay(1, () -> {
            if (!assertTrue(context, !game.teams().get(TeamId.RED).laneGroup().hasBossEntity(), "RED boss entity should be cleared.")) {
                return;
            }
            if (!assertEquals(context, 0, countTrackedBossEntities(game), "No boss entity should remain tracked after killing RED boss.")) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void startLocksRosterAndActivatesOnlySelectedPlayers(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.select(List.of(
                candidate("p1"),
                candidate("p2"),
                candidate("p3"),
                candidate("p4"),
                candidate("p5"),
                candidate("p6"),
                candidate("p7"),
                candidate("p8"),
                candidate("p9")
        ), MatchMode.NORMAL);

        if (!assertPresent(context, plan, "Expected a selection plan for game start.")) {
            return;
        }

        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                testArena(context)
        );

        if (!assertTrue(
                context,
                game.start(context.getLevel().getServer(), plan.get()),
                "Game should start with a valid participant plan."
        )) {
            return;
        }
        if (!assertTrue(context, game.rosterLocked(), "Game roster should be locked after start.")) {
            return;
        }
        if (!assertTrue(context, !game.canConfigureRoster(), "Roster configuration should be blocked after start.")) {
            return;
        }
        if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Game should enter prepare phase.")) {
            return;
        }
        if (!assertEquals(context, 9, game.players().size(), "Only active players should be registered in the game.")) {
            return;
        }
        if (!assertEquals(context, 0, game.spectatorCount(), "Spectator count should match the selection plan.")) {
            return;
        }
        if (!assertTrue(context, game.teams().get(TeamId.RED).active(), "RED should be active.")) {
            return;
        }
        if (!assertTrue(context, game.teams().get(TeamId.BLUE).active(), "BLUE should be active.")) {
            return;
        }
        if (!assertTrue(context, !game.teams().get(TeamId.GREEN).active(), "GREEN should be inactive.")) {
            return;
        }
        if (!assertTrue(context, !game.teams().get(TeamId.YELLOW).active(), "YELLOW should be inactive.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void placingTestTowerConsumesMineralAndSpawnsEntity(GameTestHelper context) {
        UUID playerId = stableUuid("red-tower-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);

        TowerPlacementResult result = TestTowerService.placeTestTower(game, playerId, towerPos);

        if (!assertEquals(context, TowerPlacementResult.SUCCESS, result, "Test tower placement should succeed.")) {
            return;
        }
        if (!assertEquals(
                context,
                EconomyConfig.defaultConfig().startingMineral() - TestTowerTypes.TEST_DIRECT.mineralCost(),
                game.players().get(playerId).economy().mineral(),
                "Test tower should consume its mineral cost."
        )) {
            return;
        }
        if (!assertEquals(context, 1, lane.towers().size(), "Lane should contain one placed tower.")) {
            return;
        }
        if (!assertTrue(context, lane.towers().getFirst() instanceof TestTower, "Placed tower should be a TestTower.")) {
            return;
        }

        TestTower tower = (TestTower) lane.towers().getFirst();
        if (!assertTrue(context, tower.entityId().isPresent(), "Placed tower should spawn a tracked entity.")) {
            return;
        }
        if (!assertTrue(
                context,
                lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTestTowerEntity,
                "Placed tower should spawn a SemionTestTowerEntity."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void placingTestTowerOutsideLanePathIsRejected(GameTestHelper context) {
        UUID playerId = stableUuid("red-outside-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);

        TowerPlacementResult result = TestTowerService.placeTestTower(game, playerId, towerPlacementPos(lane).offset(20, 0, 20));

        if (!assertEquals(
                context,
                TowerPlacementResult.OUTSIDE_LANE_AREA,
                result,
                "Tower placement outside lane_path should be rejected."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void testTowerCanEvolveIntoAnotherTowerType(GameTestHelper context) {
        UUID playerId = stableUuid("red-upgrade-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);

        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                TestTowerService.placeTestTower(game, playerId, towerPos),
                "Test tower placement should succeed before upgrade."
        )) {
            return;
        }

        game.players().get(playerId).economy().addMineral(100);
        TestTower placedTower = (TestTower) lane.towers().getFirst();
        int previousEntityId = placedTower.entityId().orElse(-1);

        if (!assertEquals(
                context,
                2,
                TestTowerService.availableUpgrades(game, playerId, towerPos).size(),
                "Base test tower should expose two evolution choices."
        )) {
            return;
        }

        if (!assertEquals(
                context,
                TowerUpgradeResult.SUCCESS,
                TestTowerService.upgradeTestTower(game, playerId, towerPos, "guard"),
                "Test tower should evolve into the selected target type."
        )) {
            return;
        }

        if (!assertTrue(context, lane.towers().getFirst() instanceof TestTower, "Upgraded tower should still be a TestTower runtime object.")) {
            return;
        }

        TestTower evolvedTower = (TestTower) lane.towers().getFirst();
        if (!assertEquals(context, "test_guard", evolvedTower.type().id(), "Tower should evolve into the guard type.")) {
            return;
        }
        if (!assertTrue(context, evolvedTower.entityId().isPresent(), "Evolved tower should spawn a replacement entity.")) {
            return;
        }
        if (!assertTrue(
                context,
                evolvedTower.entityId().getAsInt() != previousEntityId,
                "Tower evolution should replace the old live entity."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                60L,
                game.players().get(playerId).economy().mineral(),
                "Tower evolution should spend the configured mineral cost."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void testTowerRejectsUnknownEvolutionId(GameTestHelper context) {
        UUID playerId = stableUuid("red-upgrade-reject-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);

        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                TestTowerService.placeTestTower(game, playerId, towerPos),
                "Test tower placement should succeed before invalid upgrade."
        )) {
            return;
        }

        if (!assertEquals(
                context,
                TowerUpgradeResult.UNKNOWN_UPGRADE,
                TestTowerService.upgradeTestTower(game, playerId, towerPos, "missing"),
                "Unknown evolution ids should be rejected."
        )) {
            return;
        }
        context.succeed();
    }
    @GameTest
    public void evolvedSniperTowerCanEvolveIntoDeadeye(GameTestHelper context) {
        UUID playerId = stableUuid("red-sniper-upgrade-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);

        if (!assertEquals(context, TowerPlacementResult.SUCCESS, TestTowerService.placeTestTower(game, playerId, towerPos), "Base test tower placement should succeed before chained upgrade.")) {
            return;
        }

        game.players().get(playerId).economy().addMineral(300);
        if (!assertEquals(context, TowerUpgradeResult.SUCCESS, TestTowerService.upgradeTestTower(game, playerId, towerPos, "sniper"), "Base tower should evolve into sniper.")) {
            return;
        }
        if (!assertEquals(context, 1, TestTowerService.availableUpgrades(game, playerId, towerPos).size(), "Sniper should expose exactly one follow-up evolution.")) {
            return;
        }
        if (!assertEquals(context, TowerUpgradeResult.SUCCESS, TestTowerService.upgradeTestTower(game, playerId, towerPos, "deadeye"), "Sniper should evolve into deadeye.")) {
            return;
        }

        TestTower evolvedTower = (TestTower) lane.towers().getFirst();
        if (!assertEquals(context, TestTowerTypes.TEST_DEADEYE.id(), evolvedTower.type().id(), "Sniper evolution should end at deadeye.")) {
            return;
        }
        if (!assertTrue(context, TestTowerService.availableUpgrades(game, playerId, towerPos).isEmpty(), "Deadeye should be a leaf evolution.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void evolvedGuardTowerCanEvolveIntoBastion(GameTestHelper context) {
        UUID playerId = stableUuid("red-guard-upgrade-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);

        if (!assertEquals(context, TowerPlacementResult.SUCCESS, TestTowerService.placeTestTower(game, playerId, towerPos), "Base test tower placement should succeed before guard chain.")) {
            return;
        }

        game.players().get(playerId).economy().addMineral(300);
        if (!assertEquals(context, TowerUpgradeResult.SUCCESS, TestTowerService.upgradeTestTower(game, playerId, towerPos, "guard"), "Base tower should evolve into guard.")) {
            return;
        }
        if (!assertEquals(context, 1, TestTowerService.availableUpgrades(game, playerId, towerPos).size(), "Guard should expose exactly one follow-up evolution.")) {
            return;
        }
        if (!assertEquals(context, TowerUpgradeResult.SUCCESS, TestTowerService.upgradeTestTower(game, playerId, towerPos, "bastion"), "Guard should evolve into bastion.")) {
            return;
        }

        TestTower evolvedTower = (TestTower) lane.towers().getFirst();
        if (!assertEquals(context, TestTowerTypes.TEST_BASTION.id(), evolvedTower.type().id(), "Guard evolution should end at bastion.")) {
            return;
        }
        if (!assertTrue(context, TestTowerService.availableUpgrades(game, playerId, towerPos).isEmpty(), "Bastion should be a leaf evolution.")) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 80)
    public void testTowerEntityDamagesLaneMonster(GameTestHelper context) {
        UUID playerId = stableUuid("red-tower-combat-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        TowerType highRangeType = new TowerType("damage_test", "Damage Test", TowerCategory.DIRECT, 0, 50.0, 30.0, 20.0, 5, 0);
        lane.addTower(new TestTower(highRangeType, playerId, TeamId.RED, 1, new kim.biryeong.semionTd.game.GridPosition(
                towerPos.getX(),
                towerPos.getY(),
                towerPos.getZ()
        )));

        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "tower-target",
                40.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                1
        ));
        lane.tick(context.getLevel().getServer());

        if (!assertEquals(context, 1, lane.activeMonsters().size(), "Lane should spawn one monster for the tower test.")) {
            return;
        }

        int monsterEntityId = lane.activeMonsters().getFirst().minecraftEntityId();
        context.runAfterDelay(1, () -> {
            if (!(lane.arenaWorld().getEntity(monsterEntityId) instanceof SemionMonsterEntity monsterEntity)) {
                context.fail(Component.literal("Damage test monster entity should exist."));
                return;
            }
            monsterEntity.setNoAi(true);
        });

        context.runAfterDelay(80, () -> {
            if (!(lane.arenaWorld().getEntity(monsterEntityId) instanceof SemionMonsterEntity monsterEntity)) {
                context.succeed();
                return;
            }

            if (!assertTrue(
                    context,
                    monsterEntity.getHealth() < 40.0F,
                    "Test tower entity should damage the monster through its own attack goal."
            )) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void testTowerMovesTowardOutOfRangeMonster(GameTestHelper context) {
        UUID playerId = stableUuid("red-tower-move-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        TowerType lowRangeType = new TowerType("move_test", "Move Test", TowerCategory.DIRECT, 0, 50.0, 1.0, 12.0, 20, 0);
        lane.addTower(new TestTower(lowRangeType, playerId, TeamId.RED, 1, new kim.biryeong.semionTd.game.GridPosition(
                towerPos.getX(),
                towerPos.getY(),
                towerPos.getZ()
        )));

        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "tower-move-target",
                200.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                1
        ));
        lane.tick(context.getLevel().getServer());

        if (!assertEquals(context, 1, lane.activeMonsters().size(), "Lane should spawn one monster for the movement test.")) {
            return;
        }

        int monsterEntityId = lane.activeMonsters().getFirst().minecraftEntityId();
        context.runAfterDelay(1, () -> {
            if (!(lane.arenaWorld().getEntity(monsterEntityId) instanceof SemionMonsterEntity monsterEntity)) {
                context.fail(Component.literal("Movement test monster entity should exist."));
                return;
            }
            monsterEntity.setNoAi(true);
        });

        context.runAfterDelay(40, () -> {
            if (!assertTrue(
                    context,
                    lane.arenaWorld().getEntity(monsterEntityId) instanceof SemionMonsterEntity,
                    "Movement test monster entity should still exist."
            )) {
                return;
            }

            TestTower tower = (TestTower) lane.towers().getFirst();
            if (!assertTrue(context, tower.entityId().isPresent(), "Tower entity should still exist.")) {
                return;
            }
            if (!assertTrue(
                    context,
                    lane.arenaWorld().getEntity(tower.entityId().getAsInt()) != null,
                    "Tower entity should still be present in the arena world."
            )) {
                return;
            }
            Vec3 currentTowerPos = lane.arenaWorld().getEntity(tower.entityId().getAsInt()).position();
            if (!assertTrue(
                    context,
                    currentTowerPos.distanceTo(new Vec3(towerPos.getX() + 0.5, towerPos.getY(), towerPos.getZ() + 0.5)) > 0.2,
                    "Tower should move away from its original position when the target starts out of range."
            )) {
                return;
            }
            context.succeed();
        });
    }
    @GameTest(maxTicks = 160)
    public void laneMonsterDamagesTestTowerEntity(GameTestHelper context) {
        UUID playerId = stableUuid("red-tower-defense-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);

        BlockPos towerPos = towerPlacementPos(lane);
        TowerType dummyType = new TowerType("defense_dummy", "Defense Dummy", TowerCategory.DIRECT, 0, 50.0, 1.0, 1.0, 40, 100);
        lane.addTower(new TestTower(dummyType, playerId, TeamId.RED, 1, new kim.biryeong.semionTd.game.GridPosition(
                towerPos.getX(),
                towerPos.getY(),
                towerPos.getZ()
        )));

        TestTower tower = (TestTower) lane.towers().getFirst();
        if (!assertTrue(context, tower.entityId().isPresent(), "Tower entity should exist before combat.")) {
            return;
        }

        int towerEntityId = tower.entityId().getAsInt();
        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "tower-breaker",
                120.0,
                0.0,
                14.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                1
        ));
        lane.tick(context.getLevel().getServer());

        context.runAfterDelay(120, () -> {
            if (!assertTrue(
                    context,
                    lane.arenaWorld().getEntity(towerEntityId) instanceof SemionTestTowerEntity,
                    "Tower entity should still exist while checking retaliation."
            )) {
                return;
            }

            SemionTestTowerEntity towerEntity = (SemionTestTowerEntity) lane.arenaWorld().getEntity(towerEntityId);
            if (!assertTrue(
                    context,
                    towerEntity.getHealth() < 50.0F,
                    "Lane monster should target and damage the tower entity."
            )) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void laneMonsterPrefersHigherAggroPriorityTower(GameTestHelper context) {
        UUID playerId = stableUuid("red-priority-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);

        BlockPos lowPriorityPos = towerPlacementPos(lane);
        BlockPos highPriorityPos = lowPriorityPos.offset(2, 0, 0);
        TowerType lowPriorityType = new TowerType("low_priority", "Low Priority", TowerCategory.DIRECT, 0, 50.0, 8.0, 12.0, 20, 0);
        TowerType highPriorityType = new TowerType("high_priority", "High Priority", TowerCategory.DIRECT, 0, 50.0, 8.0, 12.0, 20, 50);

        lane.addTower(new TestTower(lowPriorityType, playerId, TeamId.RED, 1, new kim.biryeong.semionTd.game.GridPosition(
                lowPriorityPos.getX(),
                lowPriorityPos.getY(),
                lowPriorityPos.getZ()
        )));
        lane.addTower(new TestTower(highPriorityType, playerId, TeamId.RED, 1, new kim.biryeong.semionTd.game.GridPosition(
                highPriorityPos.getX(),
                highPriorityPos.getY(),
                highPriorityPos.getZ()
        )));

        TestTower lowPriorityTower = (TestTower) lane.towers().get(0);
        TestTower highPriorityTower = (TestTower) lane.towers().get(1);
        if (!assertTrue(context, lowPriorityTower.entityId().isPresent(), "Low priority tower entity should exist.")) {
            return;
        }
        if (!assertTrue(context, highPriorityTower.entityId().isPresent(), "High priority tower entity should exist.")) {
            return;
        }

        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "priority-breaker",
                120.0,
                0.0,
                12.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                1
        ));
        lane.tick(context.getLevel().getServer());

        int monsterEntityId = lane.activeMonsters().getFirst().minecraftEntityId();
        context.runAfterDelay(20, () -> {
            if (!assertTrue(
                    context,
                    lane.arenaWorld().getEntity(highPriorityTower.entityId().getAsInt()) instanceof SemionTestTowerEntity,
                    "High priority tower entity should still exist."
            )) {
                return;
            }
            if (!assertTrue(
                    context,
                    lane.arenaWorld().getEntity(monsterEntityId) instanceof SemionMonsterEntity,
                    "Priority test monster entity should still exist."
            )) {
                return;
            }

            SemionMonsterEntity monsterEntity = (SemionMonsterEntity) lane.arenaWorld().getEntity(monsterEntityId);
            if (!assertTrue(
                    context,
                    monsterEntity.getTarget() != null
                            && monsterEntity.getTarget().getId() == highPriorityTower.entityId().getAsInt(),
                    "Monster should focus the higher aggro priority tower first."
            )) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void clearedLaneMovesTowerToFinalDefense(GameTestHelper context) {
        UUID playerId = stableUuid("red-final-defense-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);

        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                TestTowerService.placeTestTower(game, playerId, towerPlacementPos(lane)),
                "Test tower placement should succeed before final defense move."
        )) {
            return;
        }
        if (!assertTrue(context, lane.towers().getFirst() instanceof TestTower, "Placed tower should be a TestTower.")) {
            return;
        }

        game.teams().get(TeamId.RED).resetForRound();
        game.teams().get(TeamId.RED).tick(context.getLevel().getServer());

        TestTower tower = (TestTower) lane.towers().getFirst();
        if (!assertTrue(context, tower.deployedAtFinalDefense(), "Tower should be marked as deployed at final defense.")) {
            return;
        }
        if (!assertEquals(
                context,
                new BlockPos(
                        lane.laneLayout().finalDefenseTowerSlots().getFirst().x(),
                        lane.laneLayout().finalDefenseTowerSlots().getFirst().y(),
                        lane.laneLayout().finalDefenseTowerSlots().getFirst().z()
                ),
                BlockPos.containing(tower.position().x(), tower.position().y(), tower.position().z()),
                "Tower should move to the final defense position when the lane is cleared."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void roundResetReturnsTowerToLanePosition(GameTestHelper context) {
        UUID playerId = stableUuid("red-reset-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos originalPosition = towerPlacementPos(lane);

        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                TestTowerService.placeTestTower(game, playerId, originalPosition),
                "Test tower placement should succeed before round reset."
        )) {
            return;
        }

        TestTower tower = (TestTower) lane.towers().getFirst();
        if (!assertTrue(context, tower.entityId().isPresent(), "Tower entity should exist before reset validation.")) {
            return;
        }
        if (!assertTrue(
                context,
                lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTestTowerEntity,
                "Tower entity should be available before reset validation."
        )) {
            return;
        }
        ((SemionTestTowerEntity) lane.arenaWorld().getEntity(tower.entityId().getAsInt())).setHealth(11.0F);
        lane.tick(context.getLevel().getServer());

        game.teams().get(TeamId.RED).resetForRound();
        game.teams().get(TeamId.RED).tick(context.getLevel().getServer());
        game.teams().get(TeamId.RED).resetForRound();

        if (!assertTrue(context, lane.towers().getFirst() instanceof TestTower, "Placed tower should be a TestTower.")) {
            return;
        }

        tower = (TestTower) lane.towers().getFirst();
        if (!assertTrue(context, !tower.deployedAtFinalDefense(), "Tower should leave final defense on round reset.")) {
            return;
        }
        if (!assertEquals(context, tower.maxHealth(), tower.health(), "Tower health should reset to max on round reset.")) {
            return;
        }
        if (!assertEquals(
                context,
                originalPosition,
                BlockPos.containing(tower.position().x(), tower.position().y(), tower.position().z()),
                "Tower should return to its original lane block on round reset."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void playerEconomyStartsWithConfiguredValues(GameTestHelper context) {
        UUID playerId = stableUuid("economy-start-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);

        if (!assertEquals(context, 200L, game.players().get(playerId).economy().mineral(), "Starting mineral should match config default.")) {
            return;
        }
        if (!assertEquals(context, 50L, game.players().get(playerId).economy().gas(), "Starting gas should match config default.")) {
            return;
        }
        if (!assertEquals(context, 0L, game.players().get(playerId).economy().income(), "Starting income should match config default.")) {
            return;
        }
        if (!assertEquals(context, 1L, game.players().get(playerId).economy().gasPerSec(), "Starting gas per second should match config default.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void gasTickIncreasesGasAndRespectsCap(GameTestHelper context) {
        UUID playerId = stableUuid("gas-cap-owner");
        EconomyConfig economyConfig = new EconomyConfig(
                200,
                50,
                0,
                new EconomyConfig.GasCapConfig(55, 0, 0, 0),
                new EconomyConfig.GasProductionConfig(3, 20, 50, 25, 1, CurrencyType.MINERAL)
        );
        SemionGame game = new SemionGame(economyConfig, WaveConfig.defaultConfig(), testArena(context));
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(new AssignedParticipant(playerId, "tester", TeamId.RED, 1)),
                java.util.Set.of(),
                1
        );

        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Game should start for gas tick test.")) {
            return;
        }

        tickGame(game, context.getLevel().getServer(), 40);
        if (!assertEquals(context, 55L, game.players().get(playerId).economy().gas(), "Gas should tick up but stop at the round cap.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void gasUpgradeConsumesMineralAndIncreasesGasPerSecond(GameTestHelper context) {
        UUID playerId = stableUuid("gas-up-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);

        if (!assertTrue(context, game.upgradeGasProduction(playerId), "Gas upgrade should succeed with default starting mineral.")) {
            return;
        }
        if (!assertEquals(context, 150L, game.players().get(playerId).economy().mineral(), "Gas upgrade should consume mineral cost.")) {
            return;
        }
        if (!assertEquals(context, 2L, game.players().get(playerId).economy().gasPerSec(), "Gas upgrade should increase gas per second.")) {
            return;
        }
        if (!assertEquals(context, 1, game.players().get(playerId).economy().gasProductionUpgradeCount(), "Gas upgrade count should increase.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void roundPayoutPaysIncomeToLivingPlayersOnly(GameTestHelper context) {
        UUID redId = stableUuid("payout-red-owner");
        UUID blueId = stableUuid("payout-blue-owner");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);
        game.players().get(redId).economy().addIncome(7);
        game.players().get(blueId).economy().addIncome(9);

        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS + 2);

        if (!assertEquals(context, 207L, game.players().get(redId).economy().mineral(), "Living RED player should receive round payout.")) {
            return;
        }
        if (!assertEquals(context, 209L, game.players().get(blueId).economy().mineral(), "Living BLUE player should receive round payout.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void eliminatedPlayersDoNotReceiveGasTicks(GameTestHelper context) {
        UUID redId = stableUuid("elim-red-owner");
        UUID blueId = stableUuid("elim-blue-owner");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "Blue boss kill should succeed.")) {
            return;
        }
        long redGas = game.players().get(redId).economy().gas();
        long blueGas = game.players().get(blueId).economy().gas();

        tickGame(game, context.getLevel().getServer(), 40);

        if (!assertEquals(context, redGas, game.players().get(redId).economy().gas(), "Ended games should not keep generating gas for RED.")) {
            return;
        }
        if (!assertEquals(context, blueGas, game.players().get(blueId).economy().gas(), "Eliminated BLUE player should not receive gas ticks.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void summonConsumesGasAndAddsIncome(GameTestHelper context) {
        UUID redId = stableUuid("summon-red-owner");
        UUID blueId = stableUuid("summon-blue-owner");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);

        var result = game.summonMonster(redId, "grunt");
        if (!assertEquals(context, kim.biryeong.semionTd.summon.SummonResultType.SUCCESS, result.type(), "Summon should succeed when a target team exists.")) {
            return;
        }
        if (!assertEquals(context, 30L, game.players().get(redId).economy().gas(), "Successful summon should spend gas.")) {
            return;
        }
        if (!assertEquals(context, 2L, game.players().get(redId).economy().income(), "Successful summon should add income.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void summonRefundsGasWhenNoTargetTeamExists(GameTestHelper context) {
        UUID redId = stableUuid("refund-red-owner");
        SemionGame game = startedSinglePlayerGame(context, redId, TeamId.RED);

        var result = game.summonMonster(redId, "grunt");
        if (!assertEquals(context, kim.biryeong.semionTd.summon.SummonResultType.NO_TARGET_TEAM, result.type(), "Summon should fail when there is no target team.")) {
            return;
        }
        if (!assertEquals(context, 50L, game.players().get(redId).economy().gas(), "Failed summon should refund gas.")) {
            return;
        }
        if (!assertEquals(context, 0L, game.players().get(redId).economy().income(), "Failed summon should not add income.")) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120)
    public void waveMonsterKillRewardGoesToTowerOwner(GameTestHelper context) {
        UUID playerId = stableUuid("wave-reward-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        TowerType rewardTowerType = new TowerType("reward_test", "Reward Test", TowerCategory.DIRECT, 0, 50.0, 30.0, 30.0, 5, 0);
        lane.addTower(new TestTower(rewardTowerType, playerId, TeamId.RED, 1, new kim.biryeong.semionTd.game.GridPosition(
                towerPos.getX(),
                towerPos.getY(),
                towerPos.getZ()
        )));

        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "reward-wave",
                20.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                9,
                1
        ));
        lane.tick(context.getLevel().getServer());

        int monsterEntityId = lane.activeMonsters().getFirst().minecraftEntityId();
        context.runAfterDelay(1, () -> {
            if (lane.arenaWorld().getEntity(monsterEntityId) instanceof SemionMonsterEntity monsterEntity) {
                monsterEntity.setNoAi(true);
            }
        });

        context.runAfterDelay(120, () -> {
            lane.tick(context.getLevel().getServer(), new EconomyService(game.economyConfig()), game.players());
            if (!assertEquals(context, 209L, game.players().get(playerId).economy().mineral(), "Tower owner should receive wave monster mineral reward.")) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void defenderLastHitPaysMineralRewardOnce(GameTestHelper context) {
        UUID playerId = stableUuid("defender-reward-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        EconomyService economyService = new EconomyService(game.economyConfig());

        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "defender-reward",
                20.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                11,
                1
        ));
        lane.tick(context.getLevel().getServer());

        var monster = lane.activeMonsters().getFirst();
        monster.recordLastHit(playerId, KillSourceKind.DEFENDER);
        monster.syncHealth(0.0);
        lane.tick(context.getLevel().getServer(), economyService, game.players());
        lane.tick(context.getLevel().getServer(), economyService, game.players());

        if (!assertEquals(context, 211L, game.players().get(playerId).economy().mineral(), "Defender last hit should pay the reward only once.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void bossAndUnknownDeathsDoNotGrantMineralReward(GameTestHelper context) {
        UUID playerId = stableUuid("no-reward-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        EconomyService economyService = new EconomyService(game.economyConfig());

        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "boss-no-reward",
                20.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                13,
                1
        ));
        lane.tick(context.getLevel().getServer());
        var bossKilledMonster = lane.activeMonsters().getFirst();
        bossKilledMonster.recordBossHit();
        bossKilledMonster.syncHealth(0.0);
        lane.tick(context.getLevel().getServer(), economyService, game.players());

        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "unknown-no-reward",
                20.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                17,
                1
        ));
        lane.tick(context.getLevel().getServer());
        var unknownKilledMonster = lane.activeMonsters().getFirst();
        unknownKilledMonster.syncHealth(0.0);
        lane.tick(context.getLevel().getServer(), economyService, game.players());

        if (!assertEquals(context, 200L, game.players().get(playerId).economy().mineral(), "Boss or unknown kills should not pay mineral reward.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void summonConfigLoaderCreatesDefaultSummonsFile(GameTestHelper context) {
        try {
            Path tempDir = Files.createTempDirectory("semion-td-config-test");
            var loaded = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("semion-td-config-test"));
            if (!assertTrue(context, Files.exists(tempDir.resolve("summons.json")), "Summon config file should be created.")) {
                return;
            }
            if (!assertEquals(context, 1, loaded.summons().summons().size(), "Default summon config should expose one summon entry.")) {
                return;
            }
            if (!assertEquals(context, "grunt", loaded.summons().summons().getFirst().id(), "Default summon id should be grunt.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Failed to load summon config: " + exception.getMessage()));
        }
    }

    @GameTest
    public void customSummonConfigIsUsedByGame(GameTestHelper context) {
        UUID redId = stableUuid("custom-summon-red-owner");
        UUID blueId = stableUuid("custom-summon-blue-owner");
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                new SummonConfig(List.of(
                        new SummonMonsterEntry("custom", "Custom", 15, 4, 60, 0, 6, AttackKind.MELEE, "minecraft:husk", 8)
                )),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "blue", TeamId.BLUE, 1)
                ),
                java.util.Set.of(),
                2
        );

        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Game should start with custom summon config.")) {
            return;
        }

        var result = game.summonMonster(redId, "custom");
        if (!assertEquals(context, kim.biryeong.semionTd.summon.SummonResultType.SUCCESS, result.type(), "Custom summon should be registered in the game.")) {
            return;
        }
        if (!assertEquals(context, 35L, game.players().get(redId).economy().gas(), "Custom summon should spend configured gas cost.")) {
            return;
        }
        if (!assertEquals(context, 4L, game.players().get(redId).economy().income(), "Custom summon should grant configured income.")) {
            return;
        }
        context.succeed();
    }
    @Override
    public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
        context.setBlock(0, 0, 0, Blocks.AIR);
        method.invoke(this, context);
    }

    private static StartCandidate candidate(String name) {
        return new StartCandidate(stableUuid(name), name, Optional.empty());
    }

    private static StartCandidate candidate(String name, TeamId preferredTeam) {
        return new StartCandidate(stableUuid(name), name, Optional.of(preferredTeam));
    }

    private static SemionGame startedSinglePlayerGame(GameTestHelper context, UUID playerId, TeamId teamId) {
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(new AssignedParticipant(playerId, "tester", teamId, 1)),
                java.util.Set.of(),
                1
        );
        if (!game.start(context.getLevel().getServer(), plan)) {
            throw new IllegalStateException("Failed to start single-player Semion test game.");
        }
        return game;
    }

    private static SemionGame startedTwoPlayerGame(GameTestHelper context, UUID redId, UUID blueId) {
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "blue", TeamId.BLUE, 1)
                ),
                java.util.Set.of(),
                2
        );
        if (!game.start(context.getLevel().getServer(), plan)) {
            throw new IllegalStateException("Failed to start two-player Semion test game.");
        }
        return game;
    }

    private static void tickGame(SemionGame game, MinecraftServer server, int ticks) {
        for (int i = 0; i < ticks; i++) {
            game.tick(server);
        }
    }

    private static PlayerLane redLane(SemionGame game, int laneId) {
        return game.teams().get(TeamId.RED).laneGroup().lane(laneId).orElseThrow();
    }

    private static BlockPos towerPlacementPos(PlayerLane lane) {
        return BlockPos.containing(lane.laneLayout().positionAt(0.35));
    }

    private static UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean assertScoreboardTeam(GameTestHelper context, MinecraftServer server, String teamName) {
        PlayerTeam team = server.getScoreboard().getPlayerTeam(teamName);
        return assertTrue(context, team != null, "Missing scoreboard team " + teamName + ".");
    }

    private static int countTrackedBossEntities(SemionGame game) {
        int count = 0;
        for (TeamId teamId : TeamId.values()) {
            if (game.teams().get(teamId).laneGroup().hasBossEntity()) {
                count++;
            }
        }
        return count;
    }


    private static boolean assertAssignedTeam(
            GameTestHelper context,
            ParticipantSelectionPlan plan,
            String candidateName,
            TeamId expectedTeam
    ) {
        UUID candidateId = stableUuid(candidateName);
        for (AssignedParticipant participant : plan.activeParticipants()) {
            if (participant.uuid().equals(candidateId)) {
                return assertEquals(
                        context,
                        expectedTeam,
                        participant.teamId(),
                        "Expected " + candidateName + " to stay on " + expectedTeam + "."
                );
            }
        }
        context.fail(Component.literal("Missing active participant " + candidateName + "."));
        return false;
    }

    private static boolean assertTeamSizes(
            GameTestHelper context,
            ParticipantSelectionPlan plan,
            Map<TeamId, Integer> expectedSizes
    ) {
        Map<TeamId, Integer> actualSizes = new EnumMap<>(TeamId.class);
        for (AssignedParticipant participant : plan.activeParticipants()) {
            actualSizes.merge(participant.teamId(), 1, Integer::sum);
        }
        return assertEquals(context, expectedSizes, actualSizes, "Unexpected team size distribution.");
    }

    private static boolean assertPresent(
            GameTestHelper context,
            Optional<?> optional,
            String message
    ) {
        return assertTrue(context, optional.isPresent(), message);
    }

    private static boolean assertTrue(GameTestHelper context, boolean condition, String message) {
        if (!condition) {
            context.fail(Component.literal(message));
            return false;
        }
        return true;
    }

    private static boolean assertEquals(GameTestHelper context, Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            context.fail(Component.literal(message + " Expected=" + expected + ", actual=" + actual));
            return false;
        }
        return true;
    }
}









