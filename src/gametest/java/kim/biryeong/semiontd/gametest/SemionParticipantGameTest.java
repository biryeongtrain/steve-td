package kim.biryeong.semiontd.gametest;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.CurrencyType;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.SemionConfigLoader;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.boss.SemionBossEntity;
import kim.biryeong.semiontd.entity.defender.DefenderEntity;
import kim.biryeong.semiontd.entity.defender.DefenderEntityState;
import kim.biryeong.semiontd.entity.goal.AreaAllyHealGoal;
import kim.biryeong.semiontd.entity.goal.SingleAllyHealGoal;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.EconomyService;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.ParticipantSelectionService;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.StartPlacement;
import kim.biryeong.semiontd.game.StartCandidate;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.game.VanillaTeamBridge;
import kim.biryeong.semiontd.test.TestTowerService;
import kim.biryeong.semiontd.test.entity.SemionTestTowerEntity;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.summon.SummonAbilityActivation;
import kim.biryeong.semiontd.summon.SummonBalancePolicy;
import kim.biryeong.semiontd.summon.SummonContext;
import kim.biryeong.semiontd.summon.SummonMonsterType;
import kim.biryeong.semiontd.summon.SummonRegistry;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.summon.SummonTier;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.test.tower.TestTowerTypes;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.slf4j.LoggerFactory;

public final class SemionParticipantGameTest implements CustomTestMethodInvoker {
    private static kim.biryeong.semiontd.map.GameArena testArena(GameTestHelper context) {
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
    public void eliminatedTeamDisablesActiveAndQueuedLaneMonsters(GameTestHelper context) {
        UUID redId = stableUuid("disable-red-owner");
        UUID blueId = stableUuid("disable-blue-owner");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);
        PlayerLane blueLane = lane(game, TeamId.BLUE, 1);

        blueLane.enqueueWaveMonster(new WaveMonsterEntry(
                "disable-wave",
                20.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                5,
                2
        ));
        blueLane.tick(context.getLevel().getServer());
        if (!assertEquals(context, 1, blueLane.activeMonsters().size(), "Blue lane should have one active monster before elimination.")) {
            return;
        }
        int activeMonsterEntityId = blueLane.activeMonsters().getFirst().minecraftEntityId();

        var summonResult = game.summonMonster(redId, "grunt");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, summonResult.type(), "Summon should queue a monster on the only living enemy team.")) {
            return;
        }

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "Blue boss kill should eliminate the target team.")) {
            return;
        }
        if (!assertTrue(context, game.teams().get(TeamId.BLUE).eliminated(), "Blue team should be eliminated.")) {
            return;
        }
        if (!assertEquals(context, 0, blueLane.activeMonsters().size(), "Eliminated team lane should have no active monsters.")) {
            return;
        }
        if (!assertTrue(context, blueLane.clearedThisRound(), "Eliminated team lane should be marked resolved.")) {
            return;
        }

        blueLane.tick(context.getLevel().getServer());
        if (!assertEquals(context, 0, blueLane.activeMonsters().size(), "Eliminated team lane should not spawn queued wave or summon monsters.")) {
            return;
        }
        if (!assertTrue(context, context.getLevel().getEntity(activeMonsterEntityId) == null
                || context.getLevel().getEntity(activeMonsterEntityId).isRemoved(), "Active monster entity should be discarded.")) {
            return;
        }
        context.succeed();
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
        lane.addTower(new TestTower(highRangeType, playerId, TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(
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
        lane.addTower(new TestTower(lowRangeType, playerId, TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(
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
        lane.addTower(new TestTower(dummyType, playerId, TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(
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

        lane.addTower(new TestTower(lowPriorityType, playerId, TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(
                lowPriorityPos.getX(),
                lowPriorityPos.getY(),
                lowPriorityPos.getZ()
        )));
        lane.addTower(new TestTower(highPriorityType, playerId, TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(
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
    public void towerProducedDefendersResetOnNextRound(GameTestHelper context) {
        UUID playerId = stableUuid("defender-reset-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        DefenderEntity defender = new DefenderEntity(playerId, "producer_test", TeamId.RED, 1, 40.0, 7.0, false);
        lane.addDefenderEntity(defender);

        game.teams().get(TeamId.RED).resetForRound();

        if (!assertTrue(context, lane.defenderEntities().isEmpty(), "Round reset should clear tower-produced lane defenders.")) {
            return;
        }
        if (!assertEquals(context, DefenderEntityState.REMOVED, defender.state(), "Round reset should mark tower-produced defender as removed.")) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120)
    public void monsterReachingBossFightsBossUntilKilled(GameTestHelper context) {
        UUID playerId = stableUuid("boss-reach-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        double initialBossHealth = game.teams().get(TeamId.RED).laneGroup().boss().health();

        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "boss-reacher",
                40.0,
                0.0,
                37.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                0,
                1
        ));
        lane.tick(context.getLevel().getServer());
        int monsterEntityId = lane.activeMonsters().getFirst().minecraftEntityId();
        if (!(lane.arenaWorld().getEntity(monsterEntityId) instanceof SemionMonsterEntity monsterEntity)) {
            context.fail(Component.literal("Boss combat test monster entity should exist."));
            return;
        }
        if (game.teams().get(TeamId.RED).laneGroup().bossEntity().isEmpty()) {
            context.fail(Component.literal("Boss combat test boss entity should exist."));
            return;
        }

        SemionBossEntity bossEntity = game.teams().get(TeamId.RED).laneGroup().bossEntity().get();
        Vec3 bossPosition = lane.laneLayout().bossPosition();
        monsterEntity.teleportTo(bossPosition.x, bossPosition.y, bossPosition.z);
        monsterEntity.setTarget(bossEntity);
        game.teams().get(TeamId.RED).tick(context.getLevel().getServer());

        if (!assertEquals(context, 1, lane.activeMonsters().size(), "Monster reaching boss should stay active for boss combat.")) {
            return;
        }
        if (!assertEquals(context, initialBossHealth, game.teams().get(TeamId.RED).laneGroup().boss().health(), "Monster should not damage the boss through instant lane removal.")) {
            return;
        }

        context.runAfterDelay(80, () -> {
            game.teams().get(TeamId.RED).tick(context.getLevel().getServer());

            if (!assertTrue(context, game.teams().get(TeamId.RED).laneGroup().boss().health() < initialBossHealth, "Monster should damage the boss through normal combat.")) {
                return;
            }
            if (!assertEquals(context, 0, lane.activeMonsters().size(), "Boss should be able to kill and clear the reached monster.")) {
                return;
            }
            if (!assertTrue(context, lane.arenaWorld().getEntity(monsterEntityId) == null
                    || lane.arenaWorld().getEntity(monsterEntityId).isRemoved(), "Boss-killed monster entity should be removed.")) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void waveTimeoutMovesEnemiesAndTowersToFinalDefense(GameTestHelper context) {
        UUID redId = stableUuid("timeout-final-defense-red-owner");
        UUID blueId = stableUuid("timeout-final-defense-blue-owner");
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
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Game should start for wave timeout test.")) {
            return;
        }

        PlayerLane lane = redLane(game, 1);
        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                TestTowerService.placeTestTower(game, redId, towerPlacementPos(lane)),
                "Test tower placement should succeed before wave timeout."
        )) {
            return;
        }
        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "timeout-runner",
                100000.0,
                0.0,
                1.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                0,
                1
        ));

        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS + 1);
        if (lane.arenaWorld().getEntity(lane.activeMonsters().getFirst().minecraftEntityId()) instanceof SemionMonsterEntity monsterEntity) {
            monsterEntity.setNoAi(true);
        }
        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_WAVE_FINAL_DEFENSE_TICKS + 2);

        TestTower tower = (TestTower) lane.towers().getFirst();
        if (!assertTrue(context, tower.deployedAtFinalDefense(), "Wave timeout should move lane tower to final defense.")) {
            return;
        }
        if (!assertEquals(context, 1, lane.activeMonsters().size(), "Wave timeout should keep the enemy active at final defense.")) {
            return;
        }
        if (!(lane.arenaWorld().getEntity(lane.activeMonsters().getFirst().minecraftEntityId()) instanceof SemionMonsterEntity monsterEntity)) {
            context.fail(Component.literal("Wave timeout monster entity should still exist."));
            return;
        }
        if (!assertTrue(context, monsterEntity.position().distanceTo(lane.laneLayout().positionAt(0.9)) < 1.5, "Wave timeout should move enemy toward the final defense side.")) {
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
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, result.type(), "Summon should succeed when a target team exists.")) {
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
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.NO_TARGET_TEAM, result.type(), "Summon should fail when there is no target team.")) {
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

    @GameTest
    public void summonDoesNotTargetEliminatedTeams(GameTestHelper context) {
        UUID redId = stableUuid("summon-living-red-owner");
        UUID blueId = stableUuid("summon-eliminated-blue-owner");
        UUID greenId = stableUuid("summon-living-green-owner");
        SemionGame game = startedThreePlayerGame(context, redId, blueId, greenId);

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "Blue boss kill should eliminate BLUE before summon targeting.")) {
            return;
        }

        var result = game.summonMonster(redId, "grunt");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, result.type(), "Summon should still succeed with another living enemy team.")) {
            return;
        }
        if (!assertEquals(context, TeamId.GREEN, result.targetTeam().orElse(null), "Summon should skip eliminated BLUE and target living GREEN.")) {
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
        lane.addTower(new TestTower(rewardTowerType, playerId, TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(
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
    public void summonRegistryProvidesDefaultGrunt(GameTestHelper context) {
        var grunt = SummonRegistry.find("grunt");
        if (!assertPresent(context, grunt, "Summon registry should provide default grunt summon.")) {
            return;
        }
        if (!assertEquals(context, 20L, grunt.get().gasCost(), "Default grunt should keep the expected gas cost.")) {
            return;
        }
        if (!assertEquals(context, 2L, grunt.get().incomeGain(), "Default grunt should keep the expected income gain.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void summonRegistryProvidesPlannedRoleTierCatalog(GameTestHelper context) {
        var swarm = SummonRegistry.find("skitter_swarm");
        var tank = SummonRegistry.find("ironclad_tank");
        var wardTank = SummonRegistry.find("ward_tank");
        var disruptor = SummonRegistry.find("static_disruptor");
        var support = SummonRegistry.find("pulse_support");
        var siege = SummonRegistry.find("siege_breaker");
        if (!assertPresent(context, swarm, "Summon registry should provide T1 swarm content.")) {
            return;
        }
        if (!assertPresent(context, tank, "Summon registry should provide armor tank content.")) {
            return;
        }
        if (!assertPresent(context, wardTank, "Summon registry should provide resistance tank content.")) {
            return;
        }
        if (!assertPresent(context, disruptor, "Summon registry should provide low-tier disruptor content.")) {
            return;
        }
        if (!assertPresent(context, support, "Summon registry should provide low-tier support content.")) {
            return;
        }
        if (!assertPresent(context, siege, "Summon registry should provide siege content.")) {
            return;
        }
        if (!assertEquals(context, SummonTier.T1, swarm.get().tier(), "Swarm baseline should be a T1 pressure summon.")) {
            return;
        }
        if (!assertTrue(context, tank.get().roles().contains(SummonRole.TANK), "Ironclad should be a tank role summon.")) {
            return;
        }
        if (!assertEquals(context, 8.0, wardTank.get().resistance(), "Ward tank should represent a resistance-specialized tank.")) {
            return;
        }
        if (!assertTrue(context, disruptor.get().abilityActivations().contains(SummonAbilityActivation.COOLDOWN), "Low-tier disruptor should be allowed to use cooldown abilities.")) {
            return;
        }
        if (!assertTrue(context, support.get().abilityActivations().contains(SummonAbilityActivation.COOLDOWN), "Low-tier support should be allowed to use cooldown abilities.")) {
            return;
        }
        if (!assertEquals(context, SummonTier.T3, siege.get().tier(), "Siege baseline should start at T3.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void summonRuntimeCarriesRoleTierAndDamagePolicy(GameTestHelper context) {
        SummonMonsterType summon = SummonRegistry.find("ironclad_tank").orElseThrow();
        Monster monster = summon.createMonster(
                new SummonContext(
                        new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), testArena(context)),
                        new SemionPlayer(
                                stableUuid("ironclad-runtime-owner"),
                                "owner",
                                TeamId.RED,
                                1,
                                new PlayerEconomy(EconomyConfig.defaultConfig())
                        )
                ),
                TeamId.BLUE,
                1
        );
        if (!assertEquals(context, Optional.of(SummonTier.T2), monster.summonTier(), "Runtime summon monster should keep its tier.")) {
            return;
        }
        if (!assertTrue(context, monster.summonRoles().contains(SummonRole.TANK), "Runtime summon monster should keep its role.")) {
            return;
        }
        if (!assertEquals(context, DamageType.PHYSICAL, monster.damageType(), "Ironclad should use physical attack damage.")) {
            return;
        }
        if (!assertEquals(context, 8.0, monster.armor(), "Ironclad should keep armor defense.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void monsterDamageTypesUseArmorResistanceAndTrueDamage(GameTestHelper context) {
        Monster monster = new Monster(
                "damage-policy",
                TeamId.BLUE,
                1,
                Optional.empty(),
                Optional.empty(),
                130,
                8,
                5,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                DamageType.PHYSICAL,
                1,
                SummonTier.T2,
                List.of(SummonRole.TANK),
                0
        );

        monster.damage(10, DamageType.PHYSICAL);
        if (!assertEquals(context, 128.0, monster.health(), "Physical damage should be reduced by armor.")) {
            return;
        }
        monster.damage(10, DamageType.MAGIC);
        if (!assertEquals(context, 119.0, monster.health(), "Magic damage should be reduced by resistance.")) {
            return;
        }
        monster.damage(10, DamageType.TRUE);
        if (!assertEquals(context, 109.0, monster.health(), "True damage should ignore armor and resistance.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void summonTargetPriorityUsesRoleProgressAndSiegeBonus(GameTestHelper context) {
        Monster support = SummonRegistry.find("pulse_support").orElseThrow().createMonster(
                new SummonContext(
                        new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), testArena(context)),
                        new SemionPlayer(stableUuid("support-priority-owner"), "owner", TeamId.RED, 1, new PlayerEconomy(EconomyConfig.defaultConfig()))
                ),
                TeamId.BLUE,
                1
        );
        Monster tank = SummonRegistry.find("ironclad_tank").orElseThrow().createMonster(
                new SummonContext(
                        new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), testArena(context)),
                        new SemionPlayer(stableUuid("tank-priority-owner"), "owner", TeamId.RED, 1, new PlayerEconomy(EconomyConfig.defaultConfig()))
                ),
                TeamId.BLUE,
                1
        );
        support.syncLaneProgress(0.5);
        tank.syncLaneProgress(0.5);
        if (!assertTrue(context, tank.targetPriorityScore() > support.targetPriorityScore(), "Tank should be prioritized over support at the same progress.")) {
            return;
        }

        Monster siege = SummonRegistry.find("siege_breaker").orElseThrow().createMonster(
                new SummonContext(
                        new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), testArena(context)),
                        new SemionPlayer(stableUuid("siege-priority-owner"), "owner", TeamId.RED, 1, new PlayerEconomy(EconomyConfig.defaultConfig()))
                ),
                TeamId.BLUE,
                1
        );
        siege.syncLaneProgress(SummonBalancePolicy.SIEGE_NEAR_BOSS_PROGRESS);
        double expected = (SummonBalancePolicy.SIEGE_NEAR_BOSS_PROGRESS * 100.0)
                + SummonRole.SIEGE.targetPriority()
                + SummonBalancePolicy.SIEGE_NEAR_BOSS_TARGET_BONUS;
        if (!assertEquals(context, expected, siege.targetPriorityScore(), "Siege should gain target priority near the boss line.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void singleAllyHealGoalHealsMostInjuredFriendlySummon(GameTestHelper context) {
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        SemionMonsterEntity caster = spawnSummonEntity(context, "manual_support", TeamId.RED, TeamId.BLUE, 1, origin, 80.0, 0.0);
        SemionMonsterEntity lightInjury = spawnSummonEntity(context, "light_injury", TeamId.RED, TeamId.BLUE, 1, origin.add(1.0, 0.0, 0.0), 100.0, 10.0);
        SemionMonsterEntity heavyInjury = spawnSummonEntity(context, "heavy_injury", TeamId.RED, TeamId.BLUE, 1, origin.add(2.0, 0.0, 0.0), 100.0, 25.0);
        SemionMonsterEntity enemy = spawnSummonEntity(context, "enemy_injury", TeamId.GREEN, TeamId.BLUE, 1, origin.add(3.0, 0.0, 0.0), 100.0, 25.0);

        new SingleAllyHealGoal<>(caster, SemionMonsterEntity.class, 8.0, 12.0, 80, 10).tick();

        if (!assertEquals(context, 90.0, lightInjury.runtimeMonster().health(), "Single heal should not heal the less injured friendly summon.")) {
            return;
        }
        if (!assertEquals(context, 87.0, heavyInjury.runtimeMonster().health(), "Single heal should heal the most injured friendly summon.")) {
            return;
        }
        if (!assertEquals(context, 75.0, enemy.runtimeMonster().health(), "Single heal should not heal another sender team's summon.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void areaAllyHealGoalHealsNearbyFriendliesUpToTargetCap(GameTestHelper context) {
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        SemionMonsterEntity caster = spawnSummonEntity(context, "manual_area_support", TeamId.RED, TeamId.BLUE, 1, origin, 80.0, 0.0);
        SemionMonsterEntity first = spawnSummonEntity(context, "area_first", TeamId.RED, TeamId.BLUE, 1, origin.add(1.0, 0.0, 0.0), 100.0, 10.0);
        SemionMonsterEntity second = spawnSummonEntity(context, "area_second", TeamId.RED, TeamId.BLUE, 1, origin.add(2.0, 0.0, 0.0), 100.0, 10.0);
        SemionMonsterEntity third = spawnSummonEntity(context, "area_third", TeamId.RED, TeamId.BLUE, 1, origin.add(3.0, 0.0, 0.0), 100.0, 10.0);
        SemionMonsterEntity far = spawnSummonEntity(context, "area_far", TeamId.RED, TeamId.BLUE, 1, origin.add(8.0, 0.0, 0.0), 100.0, 10.0);
        SemionMonsterEntity enemy = spawnSummonEntity(context, "area_enemy", TeamId.GREEN, TeamId.BLUE, 1, origin.add(1.0, 0.0, 1.0), 100.0, 10.0);

        new AreaAllyHealGoal<>(caster, SemionMonsterEntity.class, 5.0, 5.0, 2, 100, 10).tick();

        int healedNearbyFriendlies = 0;
        for (SemionMonsterEntity entity : List.of(first, second, third)) {
            if (entity.runtimeMonster().health() == 95.0) {
                healedNearbyFriendlies++;
            }
        }
        if (!assertEquals(context, 2, healedNearbyFriendlies, "Area heal should heal nearby friendlies only up to the target cap.")) {
            return;
        }
        if (!assertEquals(context, 90.0, far.runtimeMonster().health(), "Area heal should ignore friendlies outside radius.")) {
            return;
        }
        if (!assertEquals(context, 90.0, enemy.runtimeMonster().health(), "Area heal should ignore another sender team's summon.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void healGoalsCanTargetTowerEntities(GameTestHelper context) {
        UUID playerId = stableUuid("tower-heal-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        TowerType healerType = new TowerType("healer_test", "Healer Test", TowerCategory.SUPPORT, 0, 80.0, 1.0, 0.0, 20, 0);
        TowerType targetType = new TowerType("heal_target_test", "Heal Target Test", TowerCategory.DIRECT, 0, 80.0, 1.0, 0.0, 20, 0);
        TestTower healerTower = new TestTower(healerType, playerId, TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(base.getX(), base.getY(), base.getZ()));
        TestTower targetTower = new TestTower(targetType, playerId, TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(base.getX() + 2, base.getY(), base.getZ()));
        lane.addTower(healerTower);
        lane.addTower(targetTower);
        targetTower.syncHealth(50.0);

        SemionTestTowerEntity healerEntity = (SemionTestTowerEntity) lane.arenaWorld().getEntity(healerTower.entityId().orElseThrow());
        SemionTestTowerEntity targetEntity = (SemionTestTowerEntity) lane.arenaWorld().getEntity(targetTower.entityId().orElseThrow());
        targetEntity.syncTowerState(targetTower);

        new SingleAllyHealGoal<>(healerEntity, SemionTestTowerEntity.class, 6.0, 15.0, 80, 10).tick();

        if (!assertEquals(context, 65.0, targetTower.health(), "Generic heal goal should update the tower runtime health.")) {
            return;
        }
        if (!assertEquals(context, 65.0F, targetEntity.getHealth(), "Generic heal goal should update the tower entity health.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void configLoaderDoesNotCreateSummonsConfigFile(GameTestHelper context) {
        try {
            Path tempDir = Files.createTempDirectory("semion-td-config-test");
            SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("semion-td-config-test"));
            if (!assertTrue(context, Files.notExists(tempDir.resolve("summons.json")), "Summon definitions should come from classes, not summons.json.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Failed to load configs: " + exception.getMessage()));
        }
    }

    @GameTest
    public void waveMonsterBlockbenchVisualReachesRuntimeEntity(GameTestHelper context) {
        WaveMonsterEntry entry = new WaveMonsterEntry(
                "model_wave",
                25,
                0,
                3,
                AttackKind.MELEE,
                null,
                "semion-td:monster/model_wave",
                1
        );
        Monster monster = Monster.fromWaveEntry(entry, TeamId.RED, 1);
        if (!assertEquals(context, Optional.of("semion-td:monster/model_wave"), monster.blockbenchModelId(), "Wave monster should keep its Blockbench model id.")) {
            return;
        }
        if (!assertEquals(context, "minecraft:zombie", monster.entityTypeId(), "Blockbench-only monsters should keep gameplay fallback entity data separate from BIL rendering.")) {
            return;
        }

        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, null);
        if (!assertEquals(context, EntityType.BLOCK_DISPLAY, entity.getPolymerEntityType(null), "Blockbench monsters should render through BIL's animated entity display type.")) {
            return;
        }
        if (!assertTrue(context, !entity.hasBilModelHolder(), "Missing test model resources should not create a BIL holder.")) {
            return;
        }
        if (!assertEquals(context, SemionAnimationState.IDLE, entity.animationState(), "Configured monster should start in idle animation state.")) {
            return;
        }
        entity.playAnimation(SemionAnimationState.WALK);
        if (!assertEquals(context, SemionAnimationState.WALK, entity.animationState(), "Monster should expose walk animation state.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void customSummonClassIsUsedByGame(GameTestHelper context) {
        UUID redId = stableUuid("custom-class-summon-red-owner");
        UUID blueId = stableUuid("custom-class-summon-blue-owner");
        String summonId = "custom_class";
        if (SummonRegistry.find(summonId).isEmpty()) {
            SummonRegistry.register(new SummonMonsterType(
                    summonId,
                    "Custom Class",
                    15,
                    4,
                    60,
                    0,
                    6,
                    AttackKind.MELEE,
                    "minecraft:husk",
                    8
            ) {
                @Override
                public void onSummoned(SummonContext context, Monster monster) {
                    monster.damage(10);
                }
            });
        }
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
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

        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Game should start with custom summon class registered.")) {
            return;
        }

        var result = game.summonMonster(redId, summonId);
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, result.type(), "Custom summon class should be registered in the game.")) {
            return;
        }
        if (!assertEquals(context, 35L, game.players().get(redId).economy().gas(), "Custom summon class should spend its gas cost.")) {
            return;
        }
        if (!assertEquals(context, 4L, game.players().get(redId).economy().income(), "Custom summon class should grant its income.")) {
            return;
        }
        PlayerLane targetLane = lane(game, result.targetTeam().orElseThrow(), result.targetLaneId().orElseThrow());
        targetLane.tick(context.getLevel().getServer());
        if (!assertEquals(context, 1, targetLane.activeMonsters().size(), "Custom summon class should queue one monster on the target lane.")) {
            return;
        }
        Monster summoned = targetLane.activeMonsters().getFirst();
        if (!assertEquals(context, 50.0, summoned.health(), "Custom summon onSummoned hook should be able to mutate the runtime monster.")) {
            return;
        }
        if (!assertEquals(context, "minecraft:husk", summoned.entityTypeId(), "Custom summon class should control the spawned entity type.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void customSummonClassCanUseBlockbenchModel(GameTestHelper context) {
        String summonId = "custom_model";
        if (SummonRegistry.find(summonId).isEmpty()) {
            SummonRegistry.register(new SummonMonsterType(
                    summonId,
                    "Custom Model",
                    10,
                    1,
                    40,
                    0,
                    4,
                    AttackKind.MELEE,
                    null,
                    "semion-td:summon/custom_model",
                    6
            ) {
            });
        }

        SummonMonsterType summon = SummonRegistry.find(summonId).orElseThrow();
        Monster monster = summon.createMonster(
                new SummonContext(
                        new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), testArena(context)),
                        new SemionPlayer(
                                stableUuid("custom-model-owner"),
                                "owner",
                                TeamId.RED,
                                1,
                                new PlayerEconomy(EconomyConfig.defaultConfig())
                        )
                ),
                TeamId.BLUE,
                1
        );
        if (!assertEquals(context, Optional.of("semion-td:summon/custom_model"), monster.blockbenchModelId(), "Summon monster should keep its Blockbench model id.")) {
            return;
        }
        if (!assertEquals(context, "minecraft:zombie", monster.entityTypeId(), "Blockbench-only summon should keep zombie as gameplay fallback entity data.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void towerTypeProvidesVisualAndAnimationState(GameTestHelper context) {
        TowerType towerType = new TowerType(
                "visual_tower",
                "Visual Tower",
                TowerCategory.DIRECT,
                100,
                50,
                8,
                12,
                20,
                0,
                "minecraft:iron_golem",
                "semion-td:tower/visual",
                List.of()
        );
        TestTower tower = new TestTower(
                towerType,
                stableUuid("visual-tower-owner"),
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)
        );
        SemionTestTowerEntity entity = new SemionTestTowerEntity(SemionEntityTypes.TEST_TOWER, context.getLevel());
        entity.configure(tower, null);
        if (!assertEquals(context, EntityType.BLOCK_DISPLAY, entity.getPolymerEntityType(null), "Modeled towers should render through BIL's animated entity display type.")) {
            return;
        }
        if (!assertEquals(context, "semion-td:tower/visual", entity.blockbenchModelId(), "Tower should keep its Blockbench model id.")) {
            return;
        }
        if (!assertTrue(context, !entity.hasBilModelHolder(), "Missing test model resources should not create a BIL holder.")) {
            return;
        }
        entity.playAnimation(SemionAnimationState.ATTACK);
        if (!assertEquals(context, SemionAnimationState.ATTACK, entity.animationState(), "Tower should expose attack animation state.")) {
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

    private static SemionGame startedThreePlayerGame(GameTestHelper context, UUID redId, UUID blueId, UUID greenId) {
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "blue", TeamId.BLUE, 1),
                        new AssignedParticipant(greenId, "green", TeamId.GREEN, 1)
                ),
                java.util.Set.of(),
                3
        );
        if (!game.start(context.getLevel().getServer(), plan)) {
            throw new IllegalStateException("Failed to start three-player Semion test game.");
        }
        return game;
    }

    private static void tickGame(SemionGame game, MinecraftServer server, int ticks) {
        for (int i = 0; i < ticks; i++) {
            game.tick(server);
        }
    }

    private static PlayerLane redLane(SemionGame game, int laneId) {
        return lane(game, TeamId.RED, laneId);
    }

    private static PlayerLane lane(SemionGame game, TeamId teamId, int laneId) {
        return game.teams().get(teamId).laneGroup().lane(laneId).orElseThrow();
    }

    private static BlockPos towerPlacementPos(PlayerLane lane) {
        return BlockPos.containing(lane.laneLayout().positionAt(0.35));
    }

    private static SemionMonsterEntity spawnSummonEntity(
            GameTestHelper context,
            String id,
            TeamId senderTeam,
            TeamId targetTeam,
            int targetLaneId,
            Vec3 position,
            double maxHealth,
            double damageTaken
    ) {
        Monster monster = new Monster(
                id,
                targetTeam,
                targetLaneId,
                Optional.empty(),
                Optional.ofNullable(senderTeam),
                maxHealth,
                0,
                0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                DamageType.PHYSICAL,
                0,
                SummonTier.T1,
                List.of(SummonRole.RUSH),
                0
        );
        if (damageTaken > 0) {
            monster.damage(damageTaken, DamageType.TRUE);
        }

        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, null);
        entity.setPos(position);
        context.getLevel().addFreshEntity(entity);
        return entity;
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
