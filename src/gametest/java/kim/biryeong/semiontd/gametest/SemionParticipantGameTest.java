package kim.biryeong.semiontd.gametest;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.command.SemionCommands;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.CurrencyType;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.MapConfig;
import kim.biryeong.semiontd.config.ProgressionConfig;
import kim.biryeong.semiontd.config.SemionConfigLoader;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
import kim.biryeong.semiontd.effect.TimedEffectSet;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.boss.SemionBossEntity;
import kim.biryeong.semiontd.entity.boss.BossMonster;
import kim.biryeong.semiontd.entity.boss.goal.BossAttackLaneMonsterGoal;
import kim.biryeong.semiontd.entity.defender.DefenderEntity;
import kim.biryeong.semiontd.entity.defender.DefenderEntityState;
import kim.biryeong.semiontd.entity.goal.AreaAllyHealGoal;
import kim.biryeong.semiontd.entity.goal.ApplyTowerTimedEffectGoal;
import kim.biryeong.semiontd.entity.goal.SiegeTrueDamageGoal;
import kim.biryeong.semiontd.entity.goal.SingleAllyHealGoal;
import kim.biryeong.semiontd.entity.model.SemionBilModelCache;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.EconomyService;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.ParticipantSelectionService;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.SemionTeam;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.StartPlacement;
import kim.biryeong.semiontd.game.StartCandidate;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.game.VanillaTeamBridge;
import kim.biryeong.semiontd.map.ArenaLayout;
import kim.biryeong.semiontd.test.TestTowerService;
import kim.biryeong.semiontd.test.entity.SemionTestTowerEntity;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.summon.SummonAbilityActivation;
import kim.biryeong.semiontd.summon.SummonBalancePolicy;
import kim.biryeong.semiontd.summon.SummonContext;
import kim.biryeong.semiontd.summon.SummonDisplayNames;
import kim.biryeong.semiontd.summon.SummonMonsterType;
import kim.biryeong.semiontd.summon.SummonRegistry;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.summon.SummonTier;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerFaction;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.test.tower.TestTowerTypes;
import kim.biryeong.semiontd.ui.SemionDisplayHudService;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.slf4j.LoggerFactory;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;

public final class SemionParticipantGameTest implements CustomTestMethodInvoker {
    private static kim.biryeong.semiontd.map.GameArena testArena(GameTestHelper context) {
        return SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(net.minecraft.core.BlockPos.ZERO));
    }

    @GameTest
    public void testModeSelectsOneVersusOne(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.select(List.of(
                candidate("alpha"),
                candidate("beta"),
                candidate("gamma"),
                candidate("delta"),
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
    public void participantSelectionOnlyCountsReadyPlayers(GameTestHelper context) {
        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.selectReady(
                List.of(
                        candidate("ready-1"),
                        candidate("ready-2"),
                        candidate("ready-fill-1"),
                        candidate("ready-fill-2"),
                        candidate("not-ready")
                ),
                Set.of(
                        stableUuid("ready-1"),
                        stableUuid("ready-2"),
                        stableUuid("ready-fill-1"),
                        stableUuid("ready-fill-2")
                ),
                MatchMode.NORMAL
        );

        if (!assertPresent(context, plan, "Expected normal mode to start from the four ready players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertEquals(context, 4, value.activePlayerCount(), "Only ready players should be counted as active candidates.")) {
            return;
        }
        if (!assertEquals(context, 0, value.spectatorCount(), "Not-ready online players should not become match spectators.")) {
            return;
        }
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 2, TeamId.BLUE, 2))) {
            return;
        }
        if (!assertTrue(context, value.activeParticipants().stream().noneMatch(participant -> participant.uuid().equals(stableUuid("not-ready"))), "Not-ready players should not be assigned to an active lane.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void lateSpectatorsDoNotPolluteResultSpectators(GameTestHelper context) {
        UUID redId = stableUuid("late-spectator-red");
        UUID blueId = stableUuid("late-spectator-blue");
        UUID lateSpectatorId = stableUuid("late-spectator-waiting");
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), testArena(context));
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "late-spectator-red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "late-spectator-blue", TeamId.BLUE, 1)
                ),
                Set.of(),
                2
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Game should start for late spectator test.")) {
            return;
        }
        if (!assertTrue(context, !game.addLateSpectator(redId), "Active participants should not become late spectators.")) {
            return;
        }
        if (!assertTrue(context, game.addLateSpectator(lateSpectatorId), "Late joiners should be able to register as match spectators.")) {
            return;
        }
        if (!assertEquals(context, 1, game.spectatorCount(), "Late spectator should count in the active match spectator set.")) {
            return;
        }
        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "Blue boss should die to finish the late spectator test.")) {
            return;
        }

        Optional<MatchResult> result = game.matchResult();
        if (!assertPresent(context, result, "Ended game should expose a match result.")) {
            return;
        }
        if (!assertTrue(context, !result.get().spectatorIds().contains(lateSpectatorId), "Late spectators should not be stored as initial match spectators.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void teamSelectedLateSpectatorValidatesTargetTeam(GameTestHelper context) {
        UUID redId = stableUuid("team-spectate-red");
        UUID blueId = stableUuid("team-spectate-blue");
        UUID greenId = stableUuid("team-spectate-green");
        UUID blueSpectatorId = stableUuid("team-spectate-blue-viewer");
        UUID greenSpectatorId = stableUuid("team-spectate-green-viewer");
        SemionGame game = startedThreePlayerGame(context, redId, blueId, greenId);

        if (!assertTrue(context, game.canSpectateTeam(TeamId.BLUE), "BLUE should be a valid selected spectate target while active.")) {
            return;
        }
        if (!assertTrue(context, game.addLateSpectator(blueSpectatorId, TeamId.BLUE), "Late joiner should be able to select BLUE for spectating.")) {
            return;
        }
        if (!assertEquals(context, 1, game.spectatorCount(), "Selected late spectator should be tracked.")) {
            return;
        }
        if (!assertTrue(context, !game.addLateSpectator(redId, TeamId.BLUE), "Active participants should not switch to selected spectating.")) {
            return;
        }
        if (!assertTrue(context, !game.addLateSpectator(greenSpectatorId, TeamId.YELLOW), "Inactive teams should not be selected for spectating.")) {
            return;
        }

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "BLUE boss kill should eliminate selected target team.")) {
            return;
        }
        if (!assertTrue(context, !game.canSpectateTeam(TeamId.BLUE), "Eliminated teams should not remain selected spectate targets.")) {
            return;
        }
        if (!assertTrue(context, game.addLateSpectator(greenSpectatorId, TeamId.GREEN), "Late joiner should be able to select another active team.")) {
            return;
        }
        if (!assertEquals(context, 3, game.spectatorCount(), "Late spectators plus the eliminated BLUE participant should be tracked.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void matchHudTextSplitsActiveSpectatorAndEliminatedRoles(GameTestHelper context) {
        UUID redId = stableUuid("hud-role-red");
        UUID blueId = stableUuid("hud-role-blue");
        UUID greenId = stableUuid("hud-role-green");
        UUID spectatorId = stableUuid("hud-role-spectator");
        SemionGame game = startedThreePlayerGame(context, redId, blueId, greenId);

        String activeText = SemionDisplayHudService.matchMarkupFor(
                redId,
                Optional.of(game.teams().get(TeamId.RED)),
                game,
                MatchMode.NORMAL
        );
        if (!assertTrue(context, activeText.contains("팀/라인"), "Active HUD should show team and lane.")) {
            return;
        }
        if (!assertTrue(context, activeText.contains("다이아"), "Active HUD should show diamond economy.")) {
            return;
        }
        if (!assertTrue(context, activeText.contains("전체 팀 보스"), "Active HUD should keep the full team boss summary.")) {
            return;
        }

        if (!assertTrue(context, game.addLateSpectator(spectatorId, TeamId.GREEN), "Late spectator should register before HUD rendering.")) {
            return;
        }
        String spectatorText = SemionDisplayHudService.matchMarkupFor(
                spectatorId,
                Optional.of(game.teams().get(TeamId.GREEN)),
                game,
                MatchMode.NORMAL
        );
        if (!assertTrue(context, spectatorText.contains("관전 중"), "Spectator HUD should show spectator status.")) {
            return;
        }
        if (!assertTrue(context, spectatorText.contains("관전 팀 보스"), "Spectator HUD should show the viewed team's boss.")) {
            return;
        }
        if (!assertTrue(context, !spectatorText.contains("전체 팀 보스"), "Spectator HUD should not show the full team boss summary.")) {
            return;
        }

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "BLUE boss kill should eliminate BLUE for HUD role test.")) {
            return;
        }
        String eliminatedText = SemionDisplayHudService.matchMarkupFor(
                blueId,
                Optional.of(game.teams().get(TeamId.RED)),
                game,
                MatchMode.NORMAL
        );
        if (!assertTrue(context, eliminatedText.contains("탈락 후 관전 중"), "Eliminated HUD should distinguish eliminated spectators.")) {
            return;
        }
        if (!assertTrue(context, eliminatedText.contains("소속 팀"), "Eliminated HUD should show the original team.")) {
            return;
        }
        if (!assertTrue(context, eliminatedText.contains("관전 팀"), "Eliminated HUD should show the currently viewed team.")) {
            return;
        }
        if (!assertTrue(context, !eliminatedText.contains("다이아"), "Eliminated HUD should omit active economy lines.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void statusReportSummarizesOperationalState(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        SemionGameManager manager = new SemionGameManager();

        List<String> noGameLines = SemionCommands.statusLines(manager);
        if (!assertTrue(context, noGameLines.stream().anyMatch(line -> line.contains("activeGame=false")), "Status should report no active game.")) {
            return;
        }
        if (!assertTrue(context, noGameLines.stream().anyMatch(line -> line.contains("lobbyLoaded=false")), "Status should report unloaded lobby before create.")) {
            return;
        }

        try {
            SemionGame game = manager.createGame(server);
            UUID redId = stableUuid("status-red");
            UUID blueId = stableUuid("status-blue");
            UUID spectatorId = stableUuid("status-spectator");
            ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                    MatchMode.NORMAL,
                    List.of(
                            new AssignedParticipant(redId, "status-red", TeamId.RED, 1),
                            new AssignedParticipant(blueId, "status-blue", TeamId.BLUE, 1)
                    ),
                    Set.of(spectatorId),
                    2
            );
            if (!assertTrue(context, game.start(server, plan), "Status test game should start.")) {
                return;
            }

            List<String> statusLines = SemionCommands.statusLines(manager);
            if (!assertTrue(context, statusLines.stream().anyMatch(line -> line.contains("activeGame=true")), "Status should report active game.")) {
                return;
            }
            if (!assertTrue(context, statusLines.stream().anyMatch(line -> line.contains("phase=PREPARE_AND_SUMMON")), "Status should report current phase.")) {
                return;
            }
            if (!assertTrue(context, statusLines.stream().anyMatch(line -> line.contains("activeParticipants=2")), "Status should report active participants.")) {
                return;
            }
            if (!assertTrue(context, statusLines.stream().anyMatch(line -> line.contains("spectators=1")), "Status should report spectators.")) {
                return;
            }
            if (!assertTrue(context, statusLines.stream().anyMatch(line -> line.contains("lobbyLoaded=true")), "Status should report loaded lobby.")) {
                return;
            }
            if (!assertTrue(context, statusLines.stream().anyMatch(line -> line.contains("arenaLoaded=4/4")), "Status should report loaded arenas.")) {
                return;
            }

            List<String> teamLines = SemionCommands.teamStatusLines(game);
            if (!assertTrue(context, teamLines.stream().anyMatch(line -> line.contains("팀 RED active=true")), "Team status should include active RED.")) {
                return;
            }
            if (!assertTrue(context, teamLines.stream().anyMatch(line -> line.contains("팀 GREEN active=false")), "Team status should include inactive GREEN.")) {
                return;
            }
            if (!assertTrue(context, teamLines.stream().anyMatch(line -> line.contains("boss=")), "Team status should include boss health.")) {
                return;
            }

            List<String> laneLines = SemionCommands.laneStatusLines(game);
            if (!assertTrue(context, laneLines.stream().anyMatch(line -> line.contains("라인 RED#1")), "Lane status should include active RED lane.")) {
                return;
            }
            if (!assertTrue(context, laneLines.stream().anyMatch(line -> line.contains("towerSample=")), "Lane status should include a tower placement sample.")) {
                return;
            }
            if (!assertTrue(context, laneLines.stream().anyMatch(line -> line.contains("laneArea=")), "Lane status should include the lane area bounds.")) {
                return;
            }

            List<String> playerLines = SemionCommands.playerStatusLines(game);
            if (!assertTrue(context, playerLines.stream().anyMatch(line -> line.contains("참가자 status-red")), "Player status should list active participants.")) {
                return;
            }
            if (!assertTrue(context, playerLines.stream().anyMatch(line -> line.contains("관전자 uuid=" + spectatorId)), "Player status should list spectators.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Status report should summarize operational state: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void managerStartSpectateAndResetFlowWorks(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        SemionGameManager manager = new SemionGameManager();
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-manager-reset-flow").resolve("profiles.json");
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
            if (!assertTrue(context, manager.lobbyWorld().isPresent(), "Create should load lobby.")) {
                return;
            }

            UUID redId = stableUuid("manager-reset-red");
            UUID blueId = stableUuid("manager-reset-blue");
            UUID lateSpectatorId = stableUuid("manager-reset-late-spectator");
            if (!assertTrue(context, game.markReady(redId), "Red player should ready before admin start.")) {
                return;
            }
            if (!assertTrue(context, game.markReady(blueId), "Blue player should ready before admin start.")) {
                return;
            }

            ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                    MatchMode.NORMAL,
                    List.of(
                            new AssignedParticipant(redId, "manager-reset-red", TeamId.RED, 1),
                            new AssignedParticipant(blueId, "manager-reset-blue", TeamId.BLUE, 1)
                    ),
                    Set.of(),
                    2
            );
            if (!assertTrue(context, game.start(server, plan), "Game should start from the admin flow.")) {
                return;
            }
            if (!assertTrue(context, manager.activeGame().isPresent(), "Manager should retain the active game after start.")) {
                return;
            }
            if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Started game should enter prepare.")) {
                return;
            }
            if (!assertEquals(
                    context,
                    TeamId.RED,
                    game.teamForWorld(game.arena().teamArena(TeamId.RED).orElseThrow().world()).map(SemionTeam::id).orElse(null),
                    "RED runtime world should map back to RED for spectator HUD."
            )) {
                return;
            }
            if (!assertEquals(
                    context,
                    TeamId.BLUE,
                    game.teamForWorld(game.arena().teamArena(TeamId.BLUE).orElseThrow().world()).map(SemionTeam::id).orElse(null),
                    "BLUE runtime world should map back to BLUE for spectator HUD."
            )) {
                return;
            }
            if (!assertTrue(context, game.addLateSpectator(lateSpectatorId), "Late joiner should be able to spectate an active match.")) {
                return;
            }
            if (!assertEquals(context, 1, game.spectatorCount(), "Late spectator should be tracked in the active match.")) {
                return;
            }

            if (!assertTrue(context, manager.resetToLobby(server), "Reset should report an active game was closed.")) {
                return;
            }
            if (!assertTrue(context, manager.activeGame().isEmpty(), "Reset should clear the active game.")) {
                return;
            }
            if (!assertTrue(context, manager.lobbyWorld().isPresent(), "Reset should keep or load the lobby world.")) {
                return;
            }
            if (!assertTrue(context, manager.lastMatchResult().isEmpty(), "Reset should clear stale match results.")) {
                return;
            }
            if (!assertTrue(context, !manager.resetToLobby(server), "Second reset should report no active game.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Manager start/spectate/reset flow should work: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
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
        var layout = testArena(context).teamArena(TeamId.RED)
                .orElseThrow()
                .layout();

        Vec3 laneOne = StartPlacement.activePlayerSpawn(
                layout,
                1
        );
        Vec3 laneFive = StartPlacement.activePlayerSpawn(
                layout,
                5
        );

        if (!assertEquals(context, layout.lane(1).orElseThrow().spawn(), laneOne, "Lane 1 should spawn at its assigned lane spawn.")) {
            return;
        }
        if (!assertEquals(context, layout.lane(5).orElseThrow().spawn(), laneFive, "Lane 5 should spawn at its assigned lane spawn.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void defaultWaveConfigCoversPreInfiniteRounds(GameTestHelper context) {
        WaveConfig config = WaveConfig.defaultConfig();

        for (int round = 1; round <= 20; round++) {
            if (!assertPresent(context, config.configForRound(round), "Default wave config should define round " + round + ".")) {
                return;
            }
        }
        if (!assertTrue(
                context,
                !config.configForRound(2).orElseThrow().entriesForLane("lane_1").isEmpty(),
                "Round 2 should enqueue default lane monsters."
        )) {
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
    public void mapConfigUsesPlainRegionMarkerNames(GameTestHelper context) {
        MapConfig.RegionMarkers markers = MapConfig.defaultConfig().regions();

        if (!assertEquals(context, "team_spawn", markers.teamSpawn(), "Team spawn marker should not require a namespace.")) {
            return;
        }
        if (!assertEquals(context, "lane_spawn", markers.laneSpawn(), "Lane spawn marker should not require a namespace.")) {
            return;
        }
        if (!assertEquals(context, "lane_path", markers.lanePath(), "Lane path marker should not require a namespace.")) {
            return;
        }
        if (!assertEquals(context, "final_waypoint", markers.finalWaypoint(), "Final waypoint marker should not require a namespace.")) {
            return;
        }
        if (!assertEquals(context, "boss_spawn", markers.bossSpawn(), "Boss spawn marker should not require a namespace.")) {
            return;
        }
        if (!assertEquals(context, "final_defense_lane", markers.finalDefenseTower(), "Final defense marker should not require a namespace.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void arenaLayoutUsesSharedFinalDefenseRegionForEveryLane(GameTestHelper context) {
        MapTemplate template = MapTemplate.createEmpty();
        template.getMetadata().addRegion("team_spawn", BlockBounds.ofBlock(new BlockPos(0, 64, 0)));
        template.getMetadata().addRegion("boss_spawn", BlockBounds.ofBlock(new BlockPos(20, 64, 0)));
        template.getMetadata().addRegion("final_defense_lane", BlockBounds.of(10, 64, -1, 12, 64, 1));
        template.getMetadata().addRegion("final_waypoint", BlockBounds.ofBlock(new BlockPos(8, 64, 0)), orderData(1));
        template.getMetadata().addRegion("final_waypoint", BlockBounds.ofBlock(new BlockPos(6, 64, 0)), orderData(0));
        for (int laneId = 1; laneId <= 5; laneId++) {
            template.getMetadata().addRegion(
                    "lane_spawn",
                    BlockBounds.ofBlock(new BlockPos(-10, 64, laneId)),
                    laneData(laneId)
            );
            template.getMetadata().addRegion(
                    "lane_path",
                    BlockBounds.of(-10, 64, laneId, 20, 64, laneId),
                    laneData(laneId)
            );
        }
        template.getMetadata().addRegion(
                "lane_waypoint",
                BlockBounds.ofBlock(new BlockPos(-5, 64, 1)),
                laneData(1, 0)
        );

        try {
            ArenaLayout layout = ArenaLayout.fromTemplate(
                    template,
                    BlockPos.ZERO,
                    MapConfig.RegionMarkers.defaultMarkers()
            );
            List<?> laneOneSlots = layout.lane(1).orElseThrow().finalDefenseTowerSlots();
            List<?> laneFiveSlots = layout.lane(5).orElseThrow().finalDefenseTowerSlots();
            if (!assertEquals(context, 9, laneOneSlots.size(), "Shared final defense region should expose all slots.")) {
                return;
            }
            if (!assertEquals(context, laneOneSlots, laneFiveSlots, "Every lane should share unlaned final defense slots.")) {
                return;
            }
            List<Vec3> laneOneWaypoints = layout.lane(1).orElseThrow().waypoints();
            List<Vec3> laneFiveWaypoints = layout.lane(5).orElseThrow().waypoints();
            if (!assertEquals(context, List.of(
                    new Vec3(-4.5, 64.0, 1.5),
                    new Vec3(6.5, 64.0, 0.5),
                    new Vec3(8.5, 64.0, 0.5)
            ), laneOneWaypoints, "Lane waypoints should be followed by shared final waypoints.")) {
                return;
            }
            if (!assertEquals(context, List.of(
                    new Vec3(6.5, 64.0, 0.5),
                    new Vec3(8.5, 64.0, 0.5)
            ), laneFiveWaypoints, "Lanes without lane waypoints should still use shared final waypoints.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Shared final defense region should be accepted: " + exception.getMessage()));
        }
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
    public void productionTowerCatalogRegistersFactionJobsAndSplashTowers(GameTestHelper context) {
        if (!assertTrue(context, kim.biryeong.semiontd.job.JobRegistry.find(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("semion-td", "villager_engineer")).isPresent(), "Villager job should be registered.")) {
            return;
        }
        if (!assertTrue(context, kim.biryeong.semiontd.job.JobRegistry.find(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("semion-td", "undead_necromancer")).isPresent(), "Undead job should be registered.")) {
            return;
        }
        if (!assertTrue(context, kim.biryeong.semiontd.job.JobRegistry.find(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("semion-td", "beast_tamer")).isPresent(), "Beast job should be registered.")) {
            return;
        }
        if (!assertEquals(context, 9, ProductionTowerCatalog.all().size(), "Production catalog should expose three towers per faction.")) {
            return;
        }
        for (TowerFaction faction : TowerFaction.values()) {
            if (!assertEquals(context, 3, ProductionTowerCatalog.forFaction(faction).size(), "Each faction should expose three production towers.")) {
                return;
            }
        }
        long splashTowerCount = ProductionTowerCatalog.all().stream()
                .filter(entry -> entry.behavior().splashRadius() > 0.0)
                .count();
        if (!assertTrue(context, splashTowerCount >= 8, "Most production towers should have splash coverage for mob packs.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void productionTowerBuildRespectsSelectedFactionJob(GameTestHelper context) {
        UUID playerId = stableUuid("red-production-villager-owner");
        SemionGame game = startedSinglePlayerGame(
                context,
                playerId,
                TeamId.RED,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("semion-td", "villager_engineer")
        );
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);

        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                ProductionTowerService.placeTower(game, playerId, towerPos, ProductionTowerCatalog.VILLAGER_CROSSBOW_POST.id()),
                "Villager job should place villager production towers."
        )) {
            return;
        }
        if (!assertTrue(context, lane.towers().getFirst() instanceof ProductionTower, "Production build should create a ProductionTower runtime object.")) {
            return;
        }
        BlockPos secondPos = towerPos.offset(1, 0, 0);
        if (!assertEquals(
                context,
                TowerPlacementResult.TOWER_NOT_ALLOWED_BY_JOB,
                ProductionTowerService.placeTower(game, playerId, secondPos, ProductionTowerCatalog.UNDEAD_BONE_SPITTER.id()),
                "Villager job should reject undead faction towers."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120)
    public void productionSplashTowerDamagesPackedMonsters(GameTestHelper context) {
        UUID playerId = stableUuid("red-production-splash-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        ProductionTowerCatalog.CatalogEntry entry = ProductionTowerCatalog.find(ProductionTowerCatalog.UNDEAD_GRAVE_BOMBARD.id()).orElseThrow();
        lane.addTower(new ProductionTower(
                entry.type(),
                entry.behavior(),
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(towerPos.getX(), towerPos.getY(), towerPos.getZ())
        ));

        for (int i = 0; i < 3; i++) {
            lane.enqueueWaveMonster(new WaveMonsterEntry(
                    "packed-target-" + i,
                    80.0,
                    0.0,
                    0.0,
                    AttackKind.MELEE,
                    "minecraft:zombie",
                    null,
                    1
            ));
        }
        lane.tick(context.getLevel().getServer());
        lane.tick(context.getLevel().getServer());
        lane.tick(context.getLevel().getServer());

        context.runAfterDelay(100, () -> {
            long damagedCount = lane.activeMonsters().stream()
                    .filter(monster -> monster.health() < 80.0)
                    .count();
            if (!assertTrue(context, damagedCount >= 2, "Splash tower should damage multiple packed monsters.")) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void productionTowerMechanicStacksAfterCombat(GameTestHelper context) {
        UUID playerId = stableUuid("red-production-stack-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        ProductionTowerCatalog.CatalogEntry entry = ProductionTowerCatalog.find(ProductionTowerCatalog.VILLAGER_BELL_MORTAR.id()).orElseThrow();
        ProductionTower tower = new ProductionTower(
                entry.type(),
                entry.behavior(),
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(towerPos.getX(), towerPos.getY(), towerPos.getZ())
        );
        lane.addTower(tower);
        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "stack-target",
                120.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                1
        ));
        lane.tick(context.getLevel().getServer());

        context.runAfterDelay(100, () -> {
            if (!assertTrue(context, tower.mechanicStacks() > 0, "Villager production tower should gain Emerald stacks after combat.")) {
                return;
            }
            if (!assertTrue(context, tower.damageMultiplier() > 1.0, "Emerald stacks should increase tower damage multiplier.")) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void productionTowerCatalogUsesVanillaMobVisuals(GameTestHelper context) {
        if (!assertEquals(
                context,
                "minecraft:villager",
                ProductionTowerCatalog.VILLAGER_CROSSBOW_POST.entityTypeId(),
                "Villager faction towers should use a visible vanilla mob entity."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                "minecraft:pig",
                ProductionTowerCatalog.BEAST_BOAR_CRASHER.entityTypeId(),
                "Beast faction boar tower should use a pig visual instead of armor stand."
        )) {
            return;
        }
        if (!assertTrue(
                context,
                ProductionTowerCatalog.all().stream()
                        .noneMatch(entry -> "minecraft:armor_stand".equals(entry.type().entityTypeId())),
                "Production tower catalog should not render towers as armor stands."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void hudScaleUsesQaMultiplier(GameTestHelper context) {
        if (!assertEquals(
                context,
                2.5F,
                SemionDisplayHudService.HUD_SCALE_MULTIPLIER,
                "Display HUD scale multiplier should match QA decision."
        )) {
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

    @GameTest(maxTicks = 520)
    public void monsterReachingBossFightsBossUntilKilled(GameTestHelper context) {
        UUID playerId = stableUuid("boss-reach-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        double initialBossHealth = game.teams().get(TeamId.RED).laneGroup().boss().health();

        lane.enqueueWaveMonster(new WaveMonsterEntry(
                "boss-reacher",
                60.0,
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

        awaitBossCombatResolution(context, game, TeamId.RED, lane, initialBossHealth, monsterEntityId, 0);
    }

    @GameTest
    public void semionEntitiesIgnoreDirectPlayerDamage(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        Vec3 origin = context.absolutePos(BlockPos.ZERO).getCenter();

        SemionTestTowerEntity tower = spawnTowerEntity(context, TeamId.RED, 1, origin, TestTowerTypes.TEST_DIRECT);
        float towerHealth = tower.getHealth();
        context.hurt(tower, tower.damageSources().playerAttack(player), 20.0F);
        if (!assertEquals(context, towerHealth, tower.getHealth(), "Players should not damage tower entities directly.")) {
            return;
        }

        SemionMonsterEntity monster = spawnSummonEntity(context, "player-immune-monster", TeamId.BLUE, TeamId.RED, 1, origin.add(2.0, 0.0, 0.0), 100.0, 0.0);
        float monsterHealth = monster.getHealth();
        context.hurt(monster, monster.damageSources().playerAttack(player), 20.0F);
        if (!assertEquals(context, monsterHealth, monster.getHealth(), "Players should not damage wave or summon entities directly.")) {
            return;
        }

        BossMonster runtimeBoss = BossMonster.defaultBoss(TeamId.RED);
        SemionBossEntity boss = new SemionBossEntity(SemionEntityTypes.BOSS, context.getLevel());
        boss.configure(TeamId.RED, runtimeBoss);
        boss.setPos(origin.add(4.0, 0.0, 0.0));
        context.getLevel().addFreshEntity(boss);
        float bossHealth = boss.getHealth();
        double runtimeBossHealth = runtimeBoss.health();
        context.hurt(boss, boss.damageSources().playerAttack(player), 20.0F);
        if (!assertEquals(context, bossHealth, boss.getHealth(), "Players should not damage boss entities directly.")) {
            return;
        }
        if (!assertEquals(context, runtimeBossHealth, runtimeBoss.health(), "Player boss hits should not affect runtime boss health.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void bossEntityStaysAnchoredAndPullsRangedMonsters(GameTestHelper context) {
        Vec3 anchor = context.absolutePos(BlockPos.ZERO).getCenter().add(4.0, 2.0, 4.0);
        BossMonster runtimeBoss = BossMonster.defaultBoss(TeamId.RED);
        SemionBossEntity boss = new SemionBossEntity(SemionEntityTypes.BOSS, context.getLevel());
        boss.configure(TeamId.RED, runtimeBoss);
        boss.setPos(anchor);
        boss.setAnchorPosition(anchor);
        context.getLevel().addFreshEntity(boss);

        boss.teleportTo(anchor.x + 2.0, anchor.y, anchor.z);
        boss.aiStep();
        if (!assertTrue(context, boss.position().distanceTo(anchor) < 0.01, "Boss entity should stay fixed at its anchor position.")) {
            return;
        }

        Monster rangedMonster = new Monster(
                "boss-pull-ranged",
                TeamId.RED,
                1,
                Optional.empty(),
                Optional.of(TeamId.BLUE),
                100.0,
                0.0,
                4.0,
                AttackKind.RANGED,
                "minecraft:skeleton",
                null,
                DamageType.PHYSICAL,
                0,
                SummonTier.T1,
                List.of(SummonRole.RUSH),
                0
        );
        SemionMonsterEntity rangedEntity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        rangedEntity.configureFrom(rangedMonster, null);
        rangedEntity.setPos(anchor.add(8.0, 0.0, 0.0));
        context.getLevel().addFreshEntity(rangedEntity);

        double before = rangedEntity.distanceToSqr(boss);
        new BossAttackLaneMonsterGoal(boss).tick();
        double after = rangedEntity.distanceToSqr(boss);
        if (!assertTrue(context, after < before, "Boss should pull ranged monsters toward the fixed boss position.")) {
            return;
        }
        context.succeed();
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
    public void twoPlayerGameLifecycleProgressesThroughRoundSummonAndVictory(GameTestHelper context) {
        UUID redId = stableUuid("lifecycle-red-owner");
        UUID blueId = stableUuid("lifecycle-blue-owner");
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

        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Lifecycle game should start with two players.")) {
            return;
        }
        if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Lifecycle game should enter prepare phase after start.")) {
            return;
        }
        if (!assertTrue(context, game.rosterLocked(), "Lifecycle game should lock the roster after start.")) {
            return;
        }
        if (!assertTrue(context, game.teams().get(TeamId.RED).active(), "RED should be active after lifecycle start.")) {
            return;
        }
        if (!assertTrue(context, game.teams().get(TeamId.BLUE).active(), "BLUE should be active after lifecycle start.")) {
            return;
        }
        if (!assertTrue(context, game.teams().get(TeamId.RED).laneGroup().hasBossEntity(), "RED boss entity should exist after lifecycle start.")) {
            return;
        }
        if (!assertTrue(context, game.teams().get(TeamId.BLUE).laneGroup().hasBossEntity(), "BLUE boss entity should exist after lifecycle start.")) {
            return;
        }
        if (!assertEquals(context, kim.biryeong.semiontd.job.JobRegistry.defaultJob().id(), game.selectedJobOrDefault(redId).id(), "RED should use the default job when none is selected.")) {
            return;
        }

        long redGasBeforePrepareTick = game.players().get(redId).economy().gas();
        tickGame(game, context.getLevel().getServer(), 40);
        if (!assertEquals(context, redGasBeforePrepareTick + 2, game.players().get(redId).economy().gas(), "Prepare phase should tick gas production.")) {
            return;
        }

        game.players().get(redId).economy().addIncome(7);
        game.players().get(blueId).economy().addIncome(9);
        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS - 40 + 2);
        if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Empty first wave should resolve into the next prepare phase.")) {
            return;
        }
        if (!assertEquals(context, 2, game.currentRound(), "Lifecycle game should advance to round 2 after first payout.")) {
            return;
        }
        if (!assertEquals(context, 207L, game.players().get(redId).economy().mineral(), "Round payout should pay RED's accumulated income.")) {
            return;
        }
        if (!assertEquals(context, 209L, game.players().get(blueId).economy().mineral(), "Round payout should pay BLUE's accumulated income.")) {
            return;
        }

        long redGasBeforeSummon = game.players().get(redId).economy().gas();
        long redIncomeBeforeSummon = game.players().get(redId).economy().income();
        var summonResult = game.summonMonster(redId, "grunt");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, summonResult.type(), "Round 2 prepare should allow RED to summon.")) {
            return;
        }
        if (!assertEquals(context, TeamId.BLUE, summonResult.targetTeam().orElse(null), "RED summon should target BLUE in a two-player game.")) {
            return;
        }
        if (!assertEquals(context, redGasBeforeSummon - 20, game.players().get(redId).economy().gas(), "Successful lifecycle summon should spend grunt gas cost.")) {
            return;
        }
        if (!assertEquals(context, redIncomeBeforeSummon + 2, game.players().get(redId).economy().income(), "Successful lifecycle summon should add grunt income.")) {
            return;
        }

        if (!assertPresent(context, summonResult.targetLaneId(), "Successful lifecycle summon should report a target lane.")) {
            return;
        }
        PlayerLane blueTargetLane = lane(game, TeamId.BLUE, summonResult.targetLaneId().get());
        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS + 2);
        if (!assertEquals(context, RoundPhase.LANE_WAVE, game.phase(), "Queued lifecycle summon should keep round 2 in wave phase after spawning.")) {
            return;
        }
        if (!assertEquals(context, 1, blueTargetLane.activeMonsters().size(), "BLUE target lane should spawn the queued lifecycle summon.")) {
            return;
        }
        Monster lifecycleSummon = blueTargetLane.activeMonsters().getFirst();
        if (!assertEquals(context, "grunt", lifecycleSummon.id(), "Spawned lifecycle summon should preserve its summon id.")) {
            return;
        }
        if (!assertTrue(context, lifecycleSummon.hasMinecraftEntity(), "Spawned lifecycle summon should have a runtime entity.")) {
            return;
        }

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "Killing BLUE boss should finish the lifecycle game.")) {
            return;
        }
        if (!assertEquals(context, RoundPhase.ENDED, game.phase(), "Lifecycle game should end when only RED remains.")) {
            return;
        }
        if (!assertTrue(context, game.teams().get(TeamId.BLUE).eliminated(), "BLUE should be eliminated after boss death.")) {
            return;
        }
        if (!assertEquals(context, 0, blueTargetLane.activeMonsters().size(), "Eliminated BLUE lane should clear active lifecycle summons.")) {
            return;
        }

        var matchResult = game.matchResult();
        if (!assertPresent(context, matchResult, "Ended lifecycle game should expose a match result.")) {
            return;
        }
        if (!assertTrue(context, matchResult.get().winningTeams().contains(TeamId.RED), "Lifecycle match result should mark RED as winner.")) {
            return;
        }
        if (!assertEquals(context, 1, matchResult.get().winnerCount(), "Lifecycle match should have one winner.")) {
            return;
        }
        if (!assertEquals(context, 1, matchResult.get().loserCount(), "Lifecycle match should have one loser.")) {
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
        var quiltGuard = SummonRegistry.find("quilt_guard");
        var staticBobbin = SummonRegistry.find("static_bobbin");
        var buttonNurse = SummonRegistry.find("button_nurse");
        var popperPod = SummonRegistry.find("popper_pod");
        var tank = SummonRegistry.find("ironclad_tank");
        var wardTank = SummonRegistry.find("ward_tank");
        var disruptor = SummonRegistry.find("static_disruptor");
        var support = SummonRegistry.find("pulse_support");
        var galeFerret = SummonRegistry.find("gale_ferret");
        var bulwarkBison = SummonRegistry.find("bulwark_bison");
        var wizardCat = SummonRegistry.find("wizard_cat");
        var groveAlpaca = SummonRegistry.find("grove_alpaca");
        var stormLynx = SummonRegistry.find("storm_lynx");
        var aegisGolem = SummonRegistry.find("aegis_golem");
        var nullImp = SummonRegistry.find("null_imp");
        var elderSprite = SummonRegistry.find("elder_sprite");
        var bombardToad = SummonRegistry.find("bombard_toad");
        var siege = SummonRegistry.find("siege_breaker");
        var apexWarden = SummonRegistry.find("apex_warden");
        var oraclePhoenix = SummonRegistry.find("oracle_phoenix");
        if (!assertPresent(context, swarm, "Summon registry should provide T1 swarm content.")) {
            return;
        }
        if (!assertPresent(context, quiltGuard, "Summon registry should provide T1 tank content.")) {
            return;
        }
        if (!assertPresent(context, staticBobbin, "Summon registry should provide T1 disruptor content.")) {
            return;
        }
        if (!assertPresent(context, buttonNurse, "Summon registry should provide T1 support content.")) {
            return;
        }
        if (!assertPresent(context, popperPod, "Summon registry should provide T1 siege content.")) {
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
        if (!assertPresent(context, galeFerret, "Summon registry should provide T3 rush content.")) {
            return;
        }
        if (!assertPresent(context, bulwarkBison, "Summon registry should provide T3 tank content.")) {
            return;
        }
        if (!assertPresent(context, wizardCat, "Summon registry should provide T3 disruptor content.")) {
            return;
        }
        if (!assertPresent(context, groveAlpaca, "Summon registry should provide T3 support content.")) {
            return;
        }
        if (!assertPresent(context, stormLynx, "Summon registry should provide T4 rush content.")) {
            return;
        }
        if (!assertPresent(context, aegisGolem, "Summon registry should provide T4 tank content.")) {
            return;
        }
        if (!assertPresent(context, nullImp, "Summon registry should provide T4 disruptor content.")) {
            return;
        }
        if (!assertPresent(context, elderSprite, "Summon registry should provide T4 support content.")) {
            return;
        }
        if (!assertPresent(context, bombardToad, "Summon registry should provide T4 siege content.")) {
            return;
        }
        if (!assertPresent(context, siege, "Summon registry should provide siege content.")) {
            return;
        }
        if (!assertPresent(context, apexWarden, "Summon registry should provide T5 tank/disruptor content.")) {
            return;
        }
        if (!assertPresent(context, oraclePhoenix, "Summon registry should provide T5 support/disruptor content.")) {
            return;
        }
        if (!assertEquals(context, SummonTier.T1, swarm.get().tier(), "Swarm baseline should be a T1 pressure summon.")) {
            return;
        }
        if (!assertEquals(context, SummonTier.T1, quiltGuard.get().tier(), "Quilt guard should be the T1 tank baseline.")) {
            return;
        }
        if (!assertTrue(context, quiltGuard.get().roles().contains(SummonRole.TANK), "Quilt guard should be a tank role summon.")) {
            return;
        }
        if (!assertEquals(context, SummonTier.T1, staticBobbin.get().tier(), "Static bobbin should be the T1 disruptor baseline.")) {
            return;
        }
        if (!assertTrue(context, staticBobbin.get().roles().contains(SummonRole.DISRUPTOR), "Static bobbin should be a disruptor role summon.")) {
            return;
        }
        if (!assertEquals(context, SummonTier.T1, buttonNurse.get().tier(), "Button nurse should be the T1 support baseline.")) {
            return;
        }
        if (!assertTrue(context, buttonNurse.get().abilityActivations().contains(SummonAbilityActivation.COOLDOWN), "Button nurse should use cooldown support abilities.")) {
            return;
        }
        if (!assertEquals(context, SummonTier.T1, popperPod.get().tier(), "Popper pod should be the T1 siege baseline.")) {
            return;
        }
        if (!assertTrue(context, popperPod.get().roles().contains(SummonRole.SIEGE), "Popper pod should be a siege role summon.")) {
            return;
        }
        if (!assertEquals(context, SummonDisplayNames.PINCER_CRAB, popperPod.get().displayName(), "Summon display names should be editable from the central name catalog.")) {
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
        if (!assertEquals(context, SummonTier.T3, galeFerret.get().tier(), "Gale ferret should be the T3 rush baseline.")) {
            return;
        }
        if (!assertTrue(context, galeFerret.get().roles().contains(SummonRole.RUSH), "Gale ferret should be a rush role summon.")) {
            return;
        }
        if (!assertEquals(context, SummonTier.T3, bulwarkBison.get().tier(), "Bulwark bison should be the T3 tank baseline.")) {
            return;
        }
        if (!assertEquals(context, MonsterDimensions.of(1.35, 1.15), bulwarkBison.get().dimensions(), "Bulwark bison should define its larger gameplay hitbox.")) {
            return;
        }
        if (!assertEquals(context, SummonTier.T3, wizardCat.get().tier(), "Wizard cat should be the T3 disruptor baseline.")) {
            return;
        }
        if (!assertTrue(context, wizardCat.get().abilityActivations().contains(SummonAbilityActivation.COOLDOWN), "T3 disruptor should use cooldown abilities.")) {
            return;
        }
        if (!assertEquals(context, SummonTier.T3, groveAlpaca.get().tier(), "Grove alpaca should be the T3 support baseline.")) {
            return;
        }
        if (!assertTrue(context, groveAlpaca.get().abilityActivations().contains(SummonAbilityActivation.COOLDOWN), "T3 support should use cooldown abilities.")) {
            return;
        }
        if (!assertEquals(context, SummonTier.T4, stormLynx.get().tier(), "Storm lynx should be the T4 rush baseline.")) {
            return;
        }
        if (!assertTrue(context, stormLynx.get().blockbenchModelId().isEmpty(), "T4 rush should not require an authored Blockbench model yet.")) {
            return;
        }
        if (!assertEquals(context, SummonTier.T4, aegisGolem.get().tier(), "Aegis golem should be the T4 tank baseline.")) {
            return;
        }
        if (!assertEquals(context, MonsterDimensions.of(1.4, 2.2), aegisGolem.get().dimensions(), "Aegis golem should define a larger vanilla fallback hitbox.")) {
            return;
        }
        if (!assertEquals(context, SummonTier.T4, nullImp.get().tier(), "Null imp should be the T4 disruptor baseline.")) {
            return;
        }
        if (!assertTrue(context, nullImp.get().abilityActivations().contains(SummonAbilityActivation.COOLDOWN), "T4 disruptor should use cooldown abilities.")) {
            return;
        }
        if (!assertEquals(context, SummonTier.T4, elderSprite.get().tier(), "Elder sprite should be the T4 support baseline.")) {
            return;
        }
        if (!assertTrue(context, elderSprite.get().blockbenchModelId().isEmpty(), "T4 support should use vanilla visuals until a model is authored.")) {
            return;
        }
        if (!assertEquals(context, SummonTier.T4, bombardToad.get().tier(), "Bombard toad should be the T4 siege baseline.")) {
            return;
        }
        if (!assertTrue(context, bombardToad.get().roles().contains(SummonRole.SIEGE), "Bombard toad should be a siege role summon.")) {
            return;
        }
        if (!assertEquals(context, SummonTier.T5, siege.get().tier(), "Siege breaker should be promoted to the T5 tank model slot.")) {
            return;
        }
        if (!assertEquals(context, MonsterDimensions.of(2.0, 1.35), siege.get().dimensions(), "Siege baseline should define a larger gameplay hitbox.")) {
            return;
        }
        if (!assertEquals(context, SummonTier.T5, apexWarden.get().tier(), "Apex warden should be the T5 tank/disruptor baseline.")) {
            return;
        }
        if (!assertTrue(context, apexWarden.get().roles().contains(SummonRole.TANK), "Apex warden should keep the tank role.")) {
            return;
        }
        if (!assertTrue(context, apexWarden.get().roles().contains(SummonRole.DISRUPTOR), "Apex warden should keep the disruptor role.")) {
            return;
        }
        if (!assertTrue(context, apexWarden.get().blockbenchModelId().isEmpty(), "New T5 tank/disruptor should not require an authored Blockbench model yet.")) {
            return;
        }
        if (!assertEquals(context, SummonTier.T5, oraclePhoenix.get().tier(), "Oracle phoenix should be the T5 support/disruptor baseline.")) {
            return;
        }
        if (!assertTrue(context, oraclePhoenix.get().roles().contains(SummonRole.SUPPORT), "Oracle phoenix should keep the support role.")) {
            return;
        }
        if (!assertTrue(context, oraclePhoenix.get().roles().contains(SummonRole.DISRUPTOR), "Oracle phoenix should keep the disruptor role.")) {
            return;
        }
        if (!assertTrue(context, oraclePhoenix.get().blockbenchModelId().isEmpty(), "New T5 support/disruptor should not require an authored Blockbench model yet.")) {
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

        if (!assertEquals(context, SemionAnimationState.HEAL, caster.animationState(), "Successful single heal should play the caster heal animation.")) {
            return;
        }
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
    public void timedEffectsKeepStrongestMagnitudeCapAndRefreshDuration(GameTestHelper context) {
        TimedEffectSet effects = new TimedEffectSet();
        effects.apply(TimedEffectType.MONSTER_MOVE_SPEED_BONUS, 0.50, 10);
        if (!assertEquals(context, 0.30, effects.magnitude(TimedEffectType.MONSTER_MOVE_SPEED_BONUS), "Timed effects should clamp to the balance cap.")) {
            return;
        }
        effects.apply(TimedEffectType.MONSTER_MOVE_SPEED_BONUS, 0.20, 50);
        if (!assertEquals(context, 0.30, effects.magnitude(TimedEffectType.MONSTER_MOVE_SPEED_BONUS), "Lower magnitude should not replace a stronger active effect.")) {
            return;
        }
        if (!assertEquals(context, 10, effects.remainingTicks(TimedEffectType.MONSTER_MOVE_SPEED_BONUS), "Lower magnitude should not refresh the stronger effect duration.")) {
            return;
        }
        effects.apply(TimedEffectType.MONSTER_MOVE_SPEED_BONUS, 0.30, 40);
        if (!assertEquals(context, 40, effects.remainingTicks(TimedEffectType.MONSTER_MOVE_SPEED_BONUS), "Equal magnitude should refresh the active effect duration.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void nullImpDebuffsOnlyNearestTargetLaneTower(GameTestHelper context) {
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        SemionMonsterEntity caster = spawnSummonEntity(context, "null_imp", TeamId.RED, TeamId.BLUE, 1, origin, 100.0, 0.0);
        SemionTestTowerEntity wrongTeam = spawnTowerEntity(context, TeamId.RED, 1, origin.add(1.0, 0.0, 0.0), TestTowerTypes.TEST_DIRECT);
        SemionTestTowerEntity wrongLane = spawnTowerEntity(context, TeamId.BLUE, 2, origin.add(2.0, 0.0, 0.0), TestTowerTypes.TEST_DIRECT);
        SemionTestTowerEntity target = spawnTowerEntity(context, TeamId.BLUE, 1, origin.add(3.0, 0.0, 0.0), TestTowerTypes.TEST_DIRECT);
        SemionTestTowerEntity fartherTarget = spawnTowerEntity(context, TeamId.BLUE, 1, origin.add(4.0, 0.0, 0.0), TestTowerTypes.TEST_DIRECT);

        new ApplyTowerTimedEffectGoal(
                caster,
                TimedEffectType.TOWER_RANGE_REDUCTION,
                SummonBalancePolicy.NULL_IMP_RANGE_REDUCTION,
                SummonBalancePolicy.NULL_IMP_RANGE_RADIUS,
                SummonBalancePolicy.NULL_IMP_RANGE_DURATION_TICKS,
                SummonBalancePolicy.NULL_IMP_RANGE_COOLDOWN_TICKS,
                SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS,
                1
        ).tick();

        if (!assertEquals(context, 0.0, wrongTeam.activeTimedEffectMagnitude(TimedEffectType.TOWER_RANGE_REDUCTION), "Null imp should ignore towers from the sender team.")) {
            return;
        }
        if (!assertEquals(context, 0.0, wrongLane.activeTimedEffectMagnitude(TimedEffectType.TOWER_RANGE_REDUCTION), "Null imp should ignore towers on another lane.")) {
            return;
        }
        if (!assertEquals(context, SummonBalancePolicy.NULL_IMP_RANGE_REDUCTION, target.activeTimedEffectMagnitude(TimedEffectType.TOWER_RANGE_REDUCTION), "Null imp should debuff the nearest target-lane enemy tower.")) {
            return;
        }
        if (!assertEquals(context, 0.0, fartherTarget.activeTimedEffectMagnitude(TimedEffectType.TOWER_RANGE_REDUCTION), "Null imp should affect only one tower.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void apexWardenSlowsTowersAndReducesFriendlyTowerDamage(GameTestHelper context) {
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        SemionMonsterEntity caster = spawnSummonEntity(context, "apex_warden", TeamId.RED, TeamId.BLUE, 1, origin, 200.0, 0.0);
        SemionMonsterEntity ally = spawnSummonEntity(context, "apex_ally", TeamId.RED, TeamId.BLUE, 1, origin.add(1.0, 0.0, 0.0), 100.0, 0.0);
        SemionMonsterEntity enemy = spawnSummonEntity(context, "apex_enemy", TeamId.GREEN, TeamId.BLUE, 1, origin.add(2.0, 0.0, 0.0), 100.0, 0.0);
        SemionTestTowerEntity tower = spawnTowerEntity(context, TeamId.BLUE, 1, origin.add(3.0, 0.0, 0.0), TestTowerTypes.TEST_DIRECT);

        for (var goal : SummonRegistry.find("apex_warden").orElseThrow().createAbilityGoals(caster)) {
            goal.tick();
        }

        if (!assertEquals(context, 31, tower.attackIntervalTicks(), "Apex warden should slow tower attack intervals by attack speed reduction.")) {
            return;
        }
        if (!assertEquals(context, SummonBalancePolicy.APEX_WARDEN_DAMAGE_REDUCTION, ally.activeTimedEffectMagnitude(TimedEffectType.MONSTER_DAMAGE_REDUCTION), "Apex warden should protect friendly summons.")) {
            return;
        }
        if (!assertEquals(context, 70.0, ally.towerDamageTaken(100.0), "Apex protection should reduce incoming tower damage.")) {
            return;
        }
        if (!assertEquals(context, 0.0, enemy.activeTimedEffectMagnitude(TimedEffectType.MONSTER_DAMAGE_REDUCTION), "Apex warden should not protect another sender team's summons.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void oraclePhoenixHealsBlessesAlliesAndReducesTowerRange(GameTestHelper context) {
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        SemionMonsterEntity caster = spawnSummonEntity(context, "oracle_phoenix", TeamId.RED, TeamId.BLUE, 1, origin, 200.0, 0.0);
        SemionMonsterEntity ally = spawnSummonEntity(context, "oracle_ally", TeamId.RED, TeamId.BLUE, 1, origin.add(1.0, 0.0, 0.0), 100.0, 20.0);
        SemionMonsterEntity enemy = spawnSummonEntity(context, "oracle_enemy", TeamId.GREEN, TeamId.BLUE, 1, origin.add(2.0, 0.0, 0.0), 100.0, 20.0);
        SemionTestTowerEntity tower = spawnTowerEntity(context, TeamId.BLUE, 1, origin.add(3.0, 0.0, 0.0), TestTowerTypes.TEST_DIRECT);

        for (var goal : SummonRegistry.find("oracle_phoenix").orElseThrow().createAbilityGoals(caster)) {
            goal.tick();
        }

        if (!assertTrue(context, ally.runtimeMonster().health() > 80.0, "Oracle phoenix should keep its existing support healing behavior.")) {
            return;
        }
        if (!assertEquals(context, 80.0, enemy.runtimeMonster().health(), "Oracle phoenix should not heal another sender team's summon.")) {
            return;
        }
        if (!assertEquals(context, 1.25, ally.movementSpeedMultiplier(), "Oracle phoenix should bless friendly summons with move speed.")) {
            return;
        }
        if (!assertEquals(context, 6.0, tower.attackRange(), "Oracle phoenix should reduce target-team tower range.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void siegeSummonsDealConditionalTrueBonusDamage(GameTestHelper context) {
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        SemionMonsterEntity bombard = spawnSummonEntity(context, "bombard_toad", TeamId.RED, TeamId.BLUE, 1, origin, 100.0, 0.0);
        SemionTestTowerEntity tower = spawnTowerEntity(context, TeamId.BLUE, 1, origin.add(1.0, 0.0, 0.0), TestTowerTypes.TEST_DIRECT);
        bombard.setTarget(tower);

        new SiegeTrueDamageGoal(
                bombard,
                SummonBalancePolicy.BOMBARD_TOAD_PROGRESS_THRESHOLD,
                SummonBalancePolicy.BOMBARD_TOAD_TRUE_DAMAGE,
                SummonBalancePolicy.BOMBARD_TOAD_TRUE_DAMAGE_COOLDOWN_TICKS,
                SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS
        ).tick();
        if (!assertEquals(context, 50.0F, tower.getHealth(), "Bombard toad should not bonus-damage towers before its progress condition.")) {
            return;
        }

        bombard.runtimeMonster().syncLaneProgress(SummonBalancePolicy.BOMBARD_TOAD_PROGRESS_THRESHOLD);
        new SiegeTrueDamageGoal(
                bombard,
                SummonBalancePolicy.BOMBARD_TOAD_PROGRESS_THRESHOLD,
                SummonBalancePolicy.BOMBARD_TOAD_TRUE_DAMAGE,
                SummonBalancePolicy.BOMBARD_TOAD_TRUE_DAMAGE_COOLDOWN_TICKS,
                SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS
        ).tick();
        if (!assertEquals(context, 30.0F, tower.getHealth(), "Bombard toad should bonus-damage towers after its progress condition.")) {
            return;
        }

        SemionMonsterEntity siege = spawnSummonEntity(context, "siege_breaker", TeamId.RED, TeamId.BLUE, 1, origin.add(2.0, 0.0, 0.0), 100.0, 0.0);
        SemionBossEntity boss = new SemionBossEntity(SemionEntityTypes.BOSS, context.getLevel());
        boss.configure(TeamId.BLUE, BossMonster.defaultBoss(TeamId.BLUE));
        boss.setPos(origin.add(3.0, 0.0, 0.0));
        context.getLevel().addFreshEntity(boss);
        siege.setTarget(boss);

        new SiegeTrueDamageGoal(
                siege,
                SummonBalancePolicy.SIEGE_BREAKER_PROGRESS_THRESHOLD,
                SummonBalancePolicy.SIEGE_BREAKER_TRUE_DAMAGE,
                SummonBalancePolicy.SIEGE_BREAKER_TRUE_DAMAGE_COOLDOWN_TICKS,
                SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS
        ).tick();
        if (!assertEquals(context, 955.0F, boss.getHealth(), "Siege breaker should bonus-damage boss targets even before lane progress threshold.")) {
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
        if (!assertEquals(context, MonsterDimensions.DEFAULT, monster.dimensions(), "Wave monsters should default to the shared monster hitbox.")) {
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
    public void t5SiegeBlockbenchModelLoadsWithCombatAnimations(GameTestHelper context) {
        var model = SemionBilModelCache.load("semion-td:summon/t5_siege");
        if (!assertPresent(context, model, "T5 siege Blockbench model should load through BIL.")) {
            return;
        }
        if (!assertTrue(context, model.get().animations().containsKey("idle"), "T5 siege model should provide idle animation.")) {
            return;
        }
        if (!assertTrue(context, model.get().animations().containsKey("walk"), "T5 siege model should provide walk animation.")) {
            return;
        }
        if (!assertTrue(context, model.get().animations().containsKey("attack"), "T5 siege model should provide attack animation.")) {
            return;
        }

        Monster monster = new Monster(
                "t5_siege_model",
                TeamId.BLUE,
                1,
                Optional.empty(),
                Optional.of(TeamId.RED),
                70,
                2,
                8,
                AttackKind.MELEE,
                null,
                "semion-td:summon/t5_siege",
                DamageType.PHYSICAL,
                0,
                MonsterDimensions.of(2.0, 1.35),
                SummonTier.T5,
                List.of(SummonRole.SIEGE),
                6
        );
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, null);
        if (!assertTrue(context, entity.hasBilModelHolder(), "T5 siege runtime entity should attach a BIL holder when the model resource exists.")) {
            return;
        }
        entity.playAnimation(SemionAnimationState.ATTACK);
        if (!assertEquals(context, SemionAnimationState.ATTACK, entity.animationState(), "T5 siege entity should expose attack animation state.")) {
            return;
        }
        if (!assertClose(context, 2.0, entity.getBbWidth(), "T5 siege hitbox width should match its authored dimensions.")) {
            return;
        }
        if (!assertClose(context, 1.35, entity.getBbHeight(), "T5 siege hitbox height should match its authored dimensions.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void earlyTierSummonBlockbenchModelsLoadWithCombatAnimations(GameTestHelper context) {
        Map<String, String> summonModels = Map.ofEntries(
                Map.entry("grunt", "semion-td:summon/t1_fox_kit"),
                Map.entry("skitter_swarm", "semion-td:summon/t1_honey_bee"),
                Map.entry("quilt_guard", "semion-td:summon/t1_shell_turtle"),
                Map.entry("static_bobbin", "semion-td:summon/t1_spark_axolotl"),
                Map.entry("button_nurse", "semion-td:summon/t1_medic_duck"),
                Map.entry("popper_pod", "semion-td:summon/t1_pincer_crab"),
                Map.entry("ironclad_tank", "semion-td:summon/t2_ironclad_boar"),
                Map.entry("ward_tank", "semion-td:summon/t2_ward_ram"),
                Map.entry("static_disruptor", "semion-td:summon/t2_static_owl"),
                Map.entry("pulse_support", "semion-td:summon/t2_pulse_fawn"),
                Map.entry("gale_ferret", "semion-td:summon/t3_gale_ferret"),
                Map.entry("bulwark_bison", "semion-td:summon/t3_bulwark_bison"),
                Map.entry("wizard_cat", "semion-td:summon/t3_wizard_cat"),
                Map.entry("grove_alpaca", "semion-td:summon/t3_grove_alpaca")
        );

        for (Map.Entry<String, String> entry : summonModels.entrySet()) {
            SummonMonsterType summon = SummonRegistry.find(entry.getKey()).orElseThrow();
            if (!assertEquals(context, Optional.of(entry.getValue()), summon.blockbenchModelId(), "Summon should point at its Blockbench model.")) {
                return;
            }

            var model = SemionBilModelCache.load(entry.getValue());
            if (!assertPresent(context, model, entry.getValue() + " should load through BIL.")) {
                return;
            }
            if (!assertTrue(context, model.get().animations().containsKey("idle"), entry.getValue() + " should provide idle animation.")) {
                return;
            }
            if (!assertTrue(context, model.get().animations().containsKey("walk"), entry.getValue() + " should provide walk animation.")) {
                return;
            }
            if (!assertTrue(context, model.get().animations().containsKey("attack"), entry.getValue() + " should provide attack animation.")) {
                return;
            }
            boolean healer = "button_nurse".equals(entry.getKey())
                    || "pulse_support".equals(entry.getKey())
                    || "grove_alpaca".equals(entry.getKey());
            if (healer && !assertTrue(context, model.get().animations().containsKey("heal"), entry.getValue() + " should provide heal animation.")) {
                return;
            }
        }
        context.succeed();
    }

    @GameTest
    public void monsterDimensionsAreAuthoredAndAppliedAtRuntime(GameTestHelper context) {
        MonsterDimensions waveDimensions = MonsterDimensions.of(1.25, 0.9);
        WaveMonsterEntry entry = new WaveMonsterEntry(
                "wide_wave",
                25,
                0,
                3,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                waveDimensions,
                1
        );
        Monster waveMonster = Monster.fromWaveEntry(entry, TeamId.RED, 1);
        if (!assertEquals(context, waveDimensions, waveMonster.dimensions(), "Wave monster should keep authored dimensions.")) {
            return;
        }

        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(waveMonster, null);
        if (!assertClose(context, 1.25, entity.getBbWidth(), "Runtime monster width should refresh from authored dimensions.")) {
            return;
        }
        if (!assertClose(context, 0.9, entity.getBbHeight(), "Runtime monster height should refresh from authored dimensions.")) {
            return;
        }
        if (!assertClose(context, 0.9, entity.getBoundingBox().getYsize(), "Runtime monster AABB should refresh from authored dimensions.")) {
            return;
        }

        SummonMonsterType summon = new SummonMonsterType(
                "wide_custom",
                "Wide Custom",
                10,
                1,
                40,
                0,
                4,
                AttackKind.MELEE,
                "minecraft:husk",
                null,
                MonsterDimensions.of(1.7, 1.1),
                DamageType.PHYSICAL,
                0,
                SummonTier.T1,
                List.of(SummonRole.RUSH),
                List.of(SummonAbilityActivation.PASSIVE),
                6
        ) {
        };
        Monster summonMonster = summon.createMonster(
                new SummonContext(
                        new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), testArena(context)),
                        new SemionPlayer(
                                stableUuid("wide-custom-owner"),
                                "owner",
                                TeamId.RED,
                                1,
                                new PlayerEconomy(EconomyConfig.defaultConfig())
                        )
                ),
                TeamId.BLUE,
                1
        );
        if (!assertEquals(context, MonsterDimensions.of(1.7, 1.1), summonMonster.dimensions(), "Summon monster should keep authored dimensions.")) {
            return;
        }
        if (!assertInvalidDimensionsRejected(context)) {
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
        return new StartCandidate(stableUuid(name), name);
    }

    private static SemionGame startedSinglePlayerGame(GameTestHelper context, UUID playerId, TeamId teamId) {
        return startedSinglePlayerGame(context, playerId, teamId, null);
    }

    private static SemionGame startedSinglePlayerGame(GameTestHelper context, UUID playerId, TeamId teamId, net.minecraft.resources.ResourceLocation jobId) {
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                testArena(context)
        );
        if (jobId != null && !game.selectJob(playerId, jobId)) {
            throw new IllegalStateException("Failed to select test job " + jobId);
        }
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

    private static SemionTestTowerEntity spawnTowerEntity(
            GameTestHelper context,
            TeamId teamId,
            int laneId,
            Vec3 position,
            TowerType towerType
    ) {
        TestTower tower = new TestTower(
                towerType,
                stableUuid("tower-" + teamId.name() + "-" + laneId + "-" + position),
                teamId,
                laneId,
                new kim.biryeong.semiontd.game.GridPosition((int) Math.floor(position.x), (int) Math.floor(position.y), (int) Math.floor(position.z))
        );
        SemionTestTowerEntity entity = new SemionTestTowerEntity(SemionEntityTypes.TEST_TOWER, context.getLevel());
        entity.configure(tower, null);
        entity.setPos(position);
        context.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static void awaitBossCombatResolution(
            GameTestHelper context,
            SemionGame game,
            TeamId teamId,
            PlayerLane lane,
            double initialBossHealth,
            int monsterEntityId,
            int elapsedTicks
    ) {
        game.teams().get(teamId).tick(context.getLevel().getServer());
        boolean bossDamaged = game.teams().get(teamId).laneGroup().boss().health() < initialBossHealth;
        boolean monsterCleared = lane.activeMonsters().isEmpty();
        if (bossDamaged && monsterCleared) {
            if (!assertTrue(context, lane.arenaWorld().getEntity(monsterEntityId) == null
                    || lane.arenaWorld().getEntity(monsterEntityId).isRemoved(), "Boss-killed monster entity should be removed.")) {
                return;
            }
            context.succeed();
            return;
        }
        if (elapsedTicks >= 440) {
            if (!assertTrue(context, bossDamaged, "Monster should damage the boss through normal combat.")) {
                return;
            }
            assertEquals(context, 0, lane.activeMonsters().size(), "Boss should be able to kill and clear the reached monster.");
            return;
        }

        context.runAfterDelay(10, () -> awaitBossCombatResolution(
                context,
                game,
                teamId,
                lane,
                initialBossHealth,
                monsterEntityId,
                elapsedTicks + 10
        ));
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

    private static CompoundTag laneData(int laneId) {
        CompoundTag data = new CompoundTag();
        data.putInt("lane", laneId);
        return data;
    }

    private static CompoundTag laneData(int laneId, int order) {
        CompoundTag data = laneData(laneId);
        data.putInt("order", order);
        return data;
    }

    private static CompoundTag orderData(int order) {
        CompoundTag data = new CompoundTag();
        data.putInt("order", order);
        return data;
    }

    private static boolean assertTrue(GameTestHelper context, boolean condition, String message) {
        if (!condition) {
            context.fail(Component.literal(message));
            return false;
        }
        return true;
    }

    private static boolean assertClose(GameTestHelper context, double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.0001) {
            context.fail(Component.literal(message + " Expected=" + expected + ", actual=" + actual));
            return false;
        }
        return true;
    }

    private static boolean assertInvalidDimensionsRejected(GameTestHelper context) {
        try {
            MonsterDimensions.of(0, 1);
            context.fail(Component.literal("Monster dimensions should reject non-positive width."));
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static boolean assertEquals(GameTestHelper context, Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            context.fail(Component.literal(message + " Expected=" + expected + ", actual=" + actual));
            return false;
        }
        return true;
    }
}
