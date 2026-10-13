package kim.biryeong.semiontd.gametest;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.placeholders.api.ServerPlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaEffectResult;
import kim.biryeong.semiontd.api.area.AreaTowerTarget;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaTargetMode;
import kim.biryeong.semiontd.command.SemionCommands;
import kim.biryeong.semiontd.buildguide.BuildAction;
import kim.biryeong.semiontd.buildguide.BuildActionType;
import kim.biryeong.semiontd.buildguide.BuildGuide;
import kim.biryeong.semiontd.buildguide.BuildGuideIndicatorService;
import kim.biryeong.semiontd.buildguide.BuildGuideService;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.CurrencyType;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.IncomeLaneRoutingConfig;
import kim.biryeong.semiontd.config.JobAvailabilityConfig;
import kim.biryeong.semiontd.config.LeaderTargetingConfig;
import kim.biryeong.semiontd.config.MapConfig;
import kim.biryeong.semiontd.config.MonsterScalingConfig;
import kim.biryeong.semiontd.config.ProgressionConfig;
import kim.biryeong.semiontd.config.SemionConfigLoader;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
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
import kim.biryeong.semiontd.entity.goal.ApplyMonsterTimedEffectGoal;
import kim.biryeong.semiontd.entity.goal.ApplyTowerTimedEffectGoal;
import kim.biryeong.semiontd.entity.goal.SiegeTrueDamageGoal;
import kim.biryeong.semiontd.entity.goal.SingleAllyHealGoal;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.monster.goal.MonsterAttackTargetGoal;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisualApplierRegistry;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.entity.visual.SlimeVisual;
import kim.biryeong.semiontd.mixin.accessor.SlimeAccessor;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.EconomyService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResultGroup;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.ParticipantSelectionService;
import kim.biryeong.semiontd.game.PlayerTeleportTransitions;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.SemionPlayerProtectionService;
import kim.biryeong.semiontd.game.SemionTeam;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.StartPlacement;
import kim.biryeong.semiontd.game.StartCandidate;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamMatchResult;
import kim.biryeong.semiontd.game.TeamSizeBalancePolicy;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.game.TowerSellResult;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.game.VanillaTeamBridge;
import kim.biryeong.semiontd.job.AnimalTowerJob;
import kim.biryeong.semiontd.job.ArmyTowerJob;
import kim.biryeong.semiontd.job.AncientCityTowerJob;
import kim.biryeong.semiontd.job.AdversaryTowerJob;
import kim.biryeong.semiontd.job.AtlantisTowerJob;
import kim.biryeong.semiontd.job.BodyTowerJob;
import kim.biryeong.semiontd.job.DeveloperTowerJob;
import kim.biryeong.semiontd.job.FrostTowerJob;
import kim.biryeong.semiontd.job.EndTowerJob;
import kim.biryeong.semiontd.job.EngineerTowerJob;
import kim.biryeong.semiontd.job.FutureAgencyTowerJob;
import kim.biryeong.semiontd.job.HeroPartyTowerJob;
import kim.biryeong.semiontd.job.IllagerTowerJob;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.LegionTowerJob;
import kim.biryeong.semiontd.job.MageTowerJob;
import kim.biryeong.semiontd.job.NetherTowerJob;
import kim.biryeong.semiontd.job.OceanTowerJob;
import kim.biryeong.semiontd.job.DemonLordTowerJob;
import kim.biryeong.semiontd.job.PlantTowerJob;
import kim.biryeong.semiontd.job.QueenTowerJob;
import kim.biryeong.semiontd.job.ResonanceTowerJob;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.job.ThunderTowerJob;
import kim.biryeong.semiontd.job.UndeadTowerJob;
import kim.biryeong.semiontd.job.InsectTowerJob;
import kim.biryeong.semiontd.job.VillagerTowerJob;
import kim.biryeong.semiontd.job.WarlockTowerJob;
import kim.biryeong.semiontd.map.ArenaLayout;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.music.SemionMusicLibrary;
import kim.biryeong.semiontd.music.SemionMusicResourcePack;
import kim.biryeong.semiontd.music.SemionMusicService;
import kim.biryeong.semiontd.music.SemionMusicTrack;
import kim.biryeong.semiontd.placeholder.SemionPlaceholders;
import kim.biryeong.semiontd.persistence.FileAppliedMatchRepository;
import kim.biryeong.semiontd.persistence.FileMatchResultRepository;
import kim.biryeong.semiontd.persistence.SemionPersistenceBackendType;
import kim.biryeong.semiontd.statistics.JobStatisticsEntry;
import kim.biryeong.semiontd.statistics.JobStatisticsSnapshot;
import kim.biryeong.semiontd.statistics.JobStatisticsState;
import kim.biryeong.semiontd.statistics.JobStatisticsTotals;
import kim.biryeong.semiontd.test.TestTowerService;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.summon.SummonAbilityActivation;
import kim.biryeong.semiontd.summon.SummonBalancePolicy;
import kim.biryeong.semiontd.summon.SummonContext;
import kim.biryeong.semiontd.summon.SummonMonsterType;
import kim.biryeong.semiontd.summon.SummonRegistry;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.summon.SummonTier;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.tower.pirate.PirateTowers;
import kim.biryeong.semiontd.tower.legion.IllusionRuntimeTower;
import kim.biryeong.semiontd.test.tower.TestTowerTypes;
import kim.biryeong.semiontd.trait.BuiltInTraits;
import kim.biryeong.semiontd.trait.TraitEffects;
import kim.biryeong.semiontd.trait.TraitLoadout;
import kim.biryeong.semiontd.trait.TraitSelectionConfig;
import kim.biryeong.semiontd.trait.TraitSelectionSession;
import kim.biryeong.semiontd.trait.TraitSelectionSnapshot;
import kim.biryeong.semiontd.trait.TraitSlot;
import kim.biryeong.semiontd.ui.SemionDialogService;
import kim.biryeong.semiontd.ui.SemionDisplayHudService;
import kim.biryeong.semiontd.ui.SemionHudTextService;
import kim.biryeong.semiontd.ui.SemionSidebarHudService;
import kim.biryeong.semiontd.ui.SemionText;
import kim.biryeong.semiontd.ui.SemionTowerInteractionService;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.slf4j.LoggerFactory;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;

public final class SemionParticipantGameTest extends GameTestParticipantFixture {
    @GameTest(maxTicks = 700)
    public void lateJoinReservationsChooseLeastTeamAndReassignEliminatedTeam(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        ServerPlayer firstLatePlayer = context.makeMockServerPlayerInLevel();
        ServerPlayer secondLatePlayer = context.makeMockServerPlayerInLevel();
        ServerPlayer reassignedPlayer = context.makeMockServerPlayerInLevel();
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
        );
        SemionGameManager manager = new SemionGameManager();
        manager.configureTraits(new TraitSelectionConfig(true, 45));
        setField(manager, "activeGame", game);
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(stableUuid("late-reserve-red-1"), "red-1", TeamId.RED, 1),
                        new AssignedParticipant(stableUuid("late-reserve-red-2"), "red-2", TeamId.RED, 2),
                        new AssignedParticipant(stableUuid("late-reserve-blue"), "blue", TeamId.BLUE, 1),
                        new AssignedParticipant(stableUuid("late-reserve-green"), "green", TeamId.GREEN, 1)
                ),
                Set.of(firstLatePlayer.getUUID(), secondLatePlayer.getUUID(), reassignedPlayer.getUUID()),
                4
        );

        try {
            if (!assertTrue(context, game.start(server, plan), "Game should start for late-join reservation test.")) {
                return;
            }
            manager.saveSelectedJob(server, firstLatePlayer.getUUID(), firstLatePlayer.getGameProfile().name(), NetherTowerJob.ID);
            if (!assertEquals(context, SemionGameManager.LateJoinResult.SELECTION_STARTED, manager.requestLateJoin(server, firstLatePlayer), "First reservation should start trait selection.")) {
                return;
            }
            manager.saveSelectedJob(server, secondLatePlayer.getUUID(), secondLatePlayer.getGameProfile().name(), NetherTowerJob.ID);
            if (!assertEquals(context, SemionGameManager.LateJoinResult.SELECTION_STARTED, manager.requestLateJoin(server, secondLatePlayer), "Second reservation should include the first reservation when balancing teams.")) {
                return;
            }
            selectNoTraits(manager, server, firstLatePlayer.getUUID());
            selectNoTraits(manager, server, secondLatePlayer.getUUID());
            SemionPlayer first = game.players().get(firstLatePlayer.getUUID());
            SemionPlayer second = game.players().get(secondLatePlayer.getUUID());
            if (!assertEquals(context, TeamId.BLUE, first.teamId(), "Team-order tie break should reserve BLUE first.")) {
                return;
            }
            if (!assertEquals(context, 2, first.laneId(), "First reservation should use the lowest empty BLUE lane.")) {
                return;
            }
            if (!assertEquals(context, TeamId.GREEN, second.teamId(), "Concurrent reservations should move the next player to GREEN.")) {
                return;
            }
            if (!assertTrue(context, !game.isMatchSpectator(firstLatePlayer.getUUID()), "A joined spectator should leave the spectator set.")) {
                return;
            }

            manager.saveSelectedJob(server, reassignedPlayer.getUUID(), reassignedPlayer.getGameProfile().name(), NetherTowerJob.ID);
            if (!assertEquals(context, SemionGameManager.LateJoinResult.SELECTION_STARTED, manager.requestLateJoin(server, reassignedPlayer), "Third reservation should start trait selection.")) {
                return;
            }
            if (!assertTrue(context, game.killBoss(TeamId.RED), "Reserved RED team should be eliminable while traits are open.")) {
                return;
            }
            selectNoTraits(manager, server, reassignedPlayer.getUUID());
            if (!assertEquals(context, TeamId.BLUE, game.players().get(reassignedPlayer.getUUID()).teamId(), "An eliminated reserved team should be replaced by the next least-populated living team.")) {
                return;
            }
            context.succeed();
        } finally {
            manager.shutdown();
        }
    }

    @GameTest(maxTicks = 700)
    public void lateJoinTimeoutAndRoundFiveRequestCompleteAfterRoundSix(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        ServerPlayer timeoutPlayer = context.makeMockServerPlayerInLevel();
        ServerPlayer roundFivePlayer = context.makeMockServerPlayerInLevel();
        ServerPlayer roundSixPlayer = context.makeMockServerPlayerInLevel();
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
        );
        SemionGameManager manager = new SemionGameManager();
        manager.configureTraits(new TraitSelectionConfig(true, 45));
        setField(manager, "activeGame", game);
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(stableUuid("late-timeout-red"), "red", TeamId.RED, 1),
                        new AssignedParticipant(stableUuid("late-timeout-blue"), "blue", TeamId.BLUE, 1)
                ),
                Set.of(timeoutPlayer.getUUID(), roundFivePlayer.getUUID(), roundSixPlayer.getUUID()),
                2
        );

        try {
            if (!assertTrue(context, game.start(server, plan), "Game should start for late-join timeout test.")) {
                return;
            }
            manager.saveSelectedJob(server, timeoutPlayer.getUUID(), timeoutPlayer.getGameProfile().name(), NetherTowerJob.ID);
            if (!assertEquals(context, SemionGameManager.LateJoinResult.SELECTION_STARTED, manager.requestLateJoin(server, timeoutPlayer), "Timeout player should start trait selection.")) {
                return;
            }
            if (!assertEquals(context, TraitSelectionSession.SelectionResult.SELECTED, manager.selectTrait(server, timeoutPlayer.getUUID(), TraitSlot.PRIMARY, BuiltInTraits.STRENGTH_IN_NUMBERS_ID), "Partial trait selection should be accepted.")) {
                return;
            }
            for (int tick = 0; tick < 30 * 20; tick++) {
                manager.tick(server);
            }
            TraitLoadout timedOutLoadout = game.players().get(timeoutPlayer.getUUID()).traitLoadout();
            if (!assertEquals(context, BuiltInTraits.STRENGTH_IN_NUMBERS_ID, timedOutLoadout.primaryTraitId(), "Timeout should preserve the selected primary trait.")) {
                return;
            }
            if (!assertEquals(context, BuiltInTraits.NONE_ID, timedOutLoadout.secondaryTraitId(), "Timeout should fill the missing secondary trait with none.")) {
                return;
            }

            setField(game, "currentRound", 5);
            manager.saveSelectedJob(server, roundFivePlayer.getUUID(), roundFivePlayer.getGameProfile().name(), NetherTowerJob.ID);
            if (!assertEquals(context, SemionGameManager.LateJoinResult.SELECTION_STARTED, manager.requestLateJoin(server, roundFivePlayer), "Round five request should be accepted.")) {
                return;
            }
            setField(game, "currentRound", 6);
            selectNoTraits(manager, server, roundFivePlayer.getUUID());
            if (!assertTrue(context, game.isActiveParticipant(roundFivePlayer.getUUID()), "Round five reservation should finish after round six begins.")) {
                return;
            }
            if (!assertEquals(context, SemionGameManager.LateJoinResult.TOO_LATE, manager.requestLateJoin(server, roundSixPlayer), "A new round six request should be rejected.")) {
                return;
            }
            context.succeed();
        } finally {
            manager.shutdown();
        }
    }

    @GameTest(maxTicks = 700)
    public void lateParticipantJoiningAfterWaveStartsWaitsUntilNextRound(GameTestHelper context) {
        EconomyConfig economy = EconomyConfig.defaultConfig();
        WaveConfig waves = WaveConfig.defaultConfig();
        GameArena arena = SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO));
        if (!assertTrue(context, arena.teamArena(TeamId.AQUA).isPresent(), "AQUA should receive a runtime arena.")) {
            return;
        }
        SemionGame game = new SemionGame(economy, waves, arena);
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(stableUuid("late-join-red"), "red", TeamId.RED, 1),
                        new AssignedParticipant(stableUuid("late-join-aqua"), "aqua", TeamId.AQUA, 1)
                ),
                Set.of(),
                2
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan, TraitSelectionSnapshot.empty()), "Game should start.")) {
            return;
        }
        if (!assertTrue(context, game.teams().get(TeamId.AQUA).laneGroup().hasBossEntity(), "AQUA should create its team boss.")) {
            return;
        }
        PlayerTeam aquaScoreboardTeam = context.getLevel().getServer().getScoreboard().getPlayerTeam("semion_aqua");
        if (!assertTrue(context, aquaScoreboardTeam != null && aquaScoreboardTeam.getColor().equals(java.util.Optional.of(net.minecraft.world.scores.TeamColor.AQUA)), "AQUA should create an aqua scoreboard team.")) {
            return;
        }

        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS);
        if (!assertEquals(context, RoundPhase.LANE_WAVE, game.phase(), "Round one wave should start before the late participant joins.")) {
            return;
        }

        ServerPlayer latePlayer = context.makeMockServerPlayerInLevel();
        AssignedParticipant lateParticipant = new AssignedParticipant(
                latePlayer.getUUID(),
                latePlayer.getGameProfile().name(),
                TeamId.RED,
                2
        );
        if (!assertTrue(context, game.addLateParticipant(
                context.getLevel().getServer(),
                latePlayer,
                lateParticipant,
                TraitLoadout.none(),
                JobRegistry.find(NetherTowerJob.ID).orElseThrow(),
                1
        ), "Late participant should join the active game.")) {
            return;
        }

        long roundOneReward = waves.configForRound(1).orElseThrow().entriesForLane("lane_2").stream()
                .mapToLong(entry -> entry.mineralReward() * (long) entry.count())
                .sum();
        if (!assertEquals(
                context,
                economy.startingDiamond() + roundOneReward + economy.startingIncome() * 5L,
                game.players().get(latePlayer.getUUID()).economy().diamond(),
                "Late participant should receive configured catch-up diamond."
        )) {
            return;
        }

        if (!assertTrue(context, !game.hasAttemptedRound(latePlayer.getUUID(), 1), "Requested round wave should be skipped.")) {
            return;
        }
        tickGame(game, context.getLevel().getServer(), 1);
        if (!assertTrue(context, redLane(game, 2).clearedThisRound(), "A late lane added during wave phase should resolve without receiving the current wave.")) {
            return;
        }
        game.teams().values().stream()
                .filter(team -> team.active() && !team.eliminated())
                .forEach(team -> team.laneGroup().disableMonsters());
        tickGame(game, context.getLevel().getServer(), 2);
        if (!assertEquals(context, 2, game.currentRound(), "Late participant should wait through the current payout and enter round two preparation.")) {
            return;
        }
        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS);
        if (!assertTrue(context, game.hasAttemptedRound(latePlayer.getUUID(), 2), "Late participant should receive the next round wave normally.")) {
            return;
        }
        game.close();
        context.succeed();
    }

    @GameTest
    public void teleportTransitionPreservesYawAndPitch(GameTestHelper context) {
        TeleportTransition transition = PlayerTeleportTransitions.preservingRotation(
                context.getLevel(),
                new Vec3(1.0, 2.0, 3.0),
                Vec3.ZERO,
                135.0F,
                -30.0F
        );

        if (!assertEquals(context, 135.0F, transition.yRot(), "Teleport should preserve head yaw as yRot.")) {
            return;
        }
        if (!assertEquals(context, -30.0F, transition.xRot(), "Teleport should preserve pitch as xRot.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void crossLaneFinalDefenseWaveRewardReductionAppliesInRuntimeEconomyService(GameTestHelper context) {
        UUID playerId = UUID.nameUUIDFromBytes("gametest-cross-lane-final-defense-killer".getBytes(StandardCharsets.UTF_8));
        SemionPlayer player = new SemionPlayer(
                playerId,
                "killer",
                TeamId.BLUE,
                1,
                new PlayerEconomy(EconomyConfig.defaultConfig())
        );
        Monster monster = new Monster(
                "gametest-blue-lane-two-final-defense-wave",
                TeamId.BLUE,
                2,
                Optional.empty(),
                Optional.empty(),
                20.0,
                0.0,
                5.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                10L
        );
        monster.setOrigin(kim.biryeong.semiontd.entity.monster.MonsterOrigin.NATURAL_WAVE);
        monster.syncLaneProgress(0.90);
        monster.recordLastHit(playerId, KillSourceKind.TOWER);
        monster.syncHealth(0.0);

        new EconomyService(EconomyConfig.defaultConfig()).awardMonsterKillReward(monster, Map.of(playerId, player));

        var snapshot = player.matchStats().snapshot(player.economy().income());
        if (!assertEquals(context, 153L, player.economy().diamond(), "Cross-lane final-defense wave kill should pay 25% of 10 diamond reward.")) {
            return;
        }
        if (!assertEquals(context, 3L, snapshot.assistClearDiamondGain(), "Assist clear diamond gain should record paid reduced reward.")) {
            return;
        }
        if (!assertEquals(context, 25.0, snapshot.assistClearThreat(), "Assist clear threat should preserve full monster threat.")) {
            return;
        }
        context.succeed();
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
    public void normalModePrefersThreeBalancedTeamsFromNine(GameTestHelper context) {
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
        if (!assertEquals(context, 3, value.activeTeamCount(), "9 players should produce 3 balanced active teams.")) {
            return;
        }
        if (!assertEquals(context, 0, value.spectatorCount(), "9 players should not leave a spectator.")) {
            return;
        }
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 3, TeamId.BLUE, 3, TeamId.GREEN, 3))) {
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
    public void normalModeCreatesFifthTeamAboveTwentyPlayers(GameTestHelper context) {
        List<StartCandidate> candidates = java.util.stream.IntStream.rangeClosed(1, 21)
                .mapToObj(index -> candidate("overflow-" + index))
                .toList();
        Set<UUID> readyPlayerIds = candidates.stream()
                .map(StartCandidate::uuid)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.selectReady(candidates, readyPlayerIds, MatchMode.NORMAL);

        if (!assertPresent(context, plan, "Expected normal mode to select from 21 ready players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertEquals(context, 21, value.activePlayerCount(), "21 players should all enter the match.")) {
            return;
        }
        if (!assertEquals(context, 5, value.activeTeamCount(), "21 players should create a fifth active team.")) {
            return;
        }
        if (!assertEquals(context, 0, value.spectatorCount(), "21 ready players should not become spectators.")) {
            return;
        }
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 5, TeamId.BLUE, 4, TeamId.GREEN, 4, TeamId.YELLOW, 4, TeamId.PURPLE, 4))) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void participantSelectionCapsActivePlayersAtThirty(GameTestHelper context) {
        List<StartCandidate> candidates = java.util.stream.IntStream.rangeClosed(1, 31)
                .mapToObj(index -> candidate("overflow-cap-" + index))
                .toList();
        Set<UUID> readyPlayerIds = candidates.stream()
                .map(StartCandidate::uuid)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.selectReady(candidates, readyPlayerIds, MatchMode.NORMAL);

        if (!assertPresent(context, plan, "Expected normal mode to select from 31 ready players.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertEquals(context, 30, value.activePlayerCount(), "Only 30 players should enter the match.")) {
            return;
        }
        if (!assertEquals(context, 1, value.spectatorCount(), "Ready players above 30 should become spectators.")) {
            return;
        }
        if (!assertTeamSizes(context, value, Map.of(TeamId.RED, 5, TeamId.BLUE, 5, TeamId.GREEN, 5, TeamId.YELLOW, 5, TeamId.PURPLE, 5, TeamId.AQUA, 5))) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void participantSelectionPrioritizesPreviousSpectators(GameTestHelper context) {
        List<StartCandidate> candidates = java.util.stream.IntStream.rangeClosed(1, 35)
                .mapToObj(index -> candidate("priority-overflow-" + index))
                .toList();
        Set<UUID> readyPlayerIds = candidates.stream()
                .map(StartCandidate::uuid)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<UUID> previousSpectatorIds = Set.of(
                stableUuid("priority-overflow-31"),
                stableUuid("priority-overflow-32"),
                stableUuid("priority-overflow-33"),
                stableUuid("priority-overflow-34"),
                stableUuid("priority-overflow-35")
        );

        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.selectReady(
                candidates,
                readyPlayerIds,
                MatchMode.NORMAL,
                previousSpectatorIds
        );

        if (!assertPresent(context, plan, "Expected normal mode to select with previous spectator priority.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertEquals(context, 30, value.activePlayerCount(), "Only 30 players should enter the match.")) {
            return;
        }
        if (!assertEquals(context, 5, value.spectatorCount(), "Ready players above 30 should become spectators.")) {
            return;
        }
        if (!assertTrue(
                context,
                previousSpectatorIds.stream().allMatch(priorityId -> value.activeParticipants().stream()
                        .anyMatch(participant -> participant.uuid().equals(priorityId))),
                "Previous spectators should be selected as active participants first."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void participantSelectionBalancesTeamsByDisplayElo(GameTestHelper context) {
        StartCandidate strongest = candidate("elo-balance-strongest", 2000);
        StartCandidate strong = candidate("elo-balance-strong", 1900);
        StartCandidate weak = candidate("elo-balance-weak", 1000);
        StartCandidate weakest = candidate("elo-balance-weakest", 900);

        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.selectReady(
                List.of(strongest, strong, weak, weakest),
                Set.of(strongest.uuid(), strong.uuid(), weak.uuid(), weakest.uuid()),
                MatchMode.NORMAL
        );
        if (!assertPresent(context, plan, "Expected ELO-balanced participant selection plan.")) {
            return;
        }

        Map<TeamId, Integer> teamElo = plan.get().activeParticipants().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        AssignedParticipant::teamId,
                        java.util.stream.Collectors.summingInt(participant -> eloFor(
                                participant.uuid(),
                                strongest,
                                strong,
                                weak,
                                weakest
                        ))
                ));
        if (!assertEquals(context, 2900, teamElo.get(TeamId.RED), "Red team should combine high and low ELO players.")) {
            return;
        }
        if (!assertEquals(context, 2900, teamElo.get(TeamId.BLUE), "Blue team should combine high and low ELO players.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void participantSelectionCanDisableDisplayEloBalancing(GameTestHelper context) {
        StartCandidate strongest = candidate("elo-disabled-strongest", 2000);
        StartCandidate strong = candidate("elo-disabled-strong", 1900);
        StartCandidate weak = candidate("elo-disabled-weak", 1000);
        StartCandidate weakest = candidate("elo-disabled-weakest", 900);

        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.selectReady(
                List.of(strongest, strong, weak, weakest),
                Set.of(strongest.uuid(), strong.uuid(), weak.uuid(), weakest.uuid()),
                MatchMode.NORMAL,
                Set.of(),
                false,
                new Random(0)
        );
        if (!assertPresent(context, plan, "Expected participant selection plan with ELO matchmaking disabled.")) {
            return;
        }

        Map<TeamId, Integer> teamElo = plan.get().activeParticipants().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        AssignedParticipant::teamId,
                        java.util.stream.Collectors.summingInt(participant -> eloFor(
                                participant.uuid(),
                                strongest,
                                strong,
                                weak,
                                weakest
                        ))
                ));
        if (!assertEquals(context, 2800, teamElo.get(TeamId.RED), "Disabled ELO matchmaking should keep randomized roster order for team assignment.")) {
            return;
        }
        if (!assertEquals(context, 3000, teamElo.get(TeamId.BLUE), "Disabled ELO matchmaking should not rebalance team ELO sums.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void gameReadyRosterAllowsPlayersAboveActiveCap(GameTestHelper context) {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), testArena(context));
        List<StartCandidate> candidates = java.util.stream.IntStream.rangeClosed(1, 35)
                .mapToObj(index -> candidate("ready-roster-over-cap-" + index))
                .toList();

        for (StartCandidate candidate : candidates) {
            if (!assertTrue(context, game.markReady(candidate.uuid()), "Players above the active cap should still be able to ready.")) {
                return;
            }
        }
        if (!assertEquals(context, 35, game.readyPlayerCount(), "Ready roster should keep every ready player, even above active cap.")) {
            return;
        }

        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.selectReady(
                candidates,
                game.readyPlayerIds(),
                MatchMode.NORMAL
        );
        if (!assertPresent(context, plan, "Expected normal mode to select from the over-cap ready roster.")) {
            return;
        }

        ParticipantSelectionPlan value = plan.get();
        if (!assertEquals(context, 30, value.activePlayerCount(), "Only 30 ready players should enter the match as active players.")) {
            return;
        }
        if (!assertEquals(context, 5, value.spectatorCount(), "Ready players above 30 should be assigned as spectators at start selection.")) {
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
    public void eliminatedParticipantsRemainRatedParticipantsNotResultSpectators(GameTestHelper context) {
        UUID redId = stableUuid("eliminated-leave-red");
        UUID blueId = stableUuid("eliminated-leave-blue");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "BLUE boss kill should eliminate BLUE and end the match.")) {
            return;
        }
        if (!assertTrue(context, game.matchSpectatorIds().contains(blueId), "Eliminated players should be runtime spectators so they can leave/rejoin as observers.")) {
            return;
        }

        Optional<MatchResult> result = game.matchResult();
        if (!assertPresent(context, result, "Ended game should expose a match result.")) {
            return;
        }
        MatchResult matchResult = result.get();
        if (!assertTrue(context, !matchResult.spectatorIds().contains(blueId), "Eliminated players must not be stored as initial/result spectators.")) {
            return;
        }
        if (!assertTrue(context, matchResult.participants().stream().anyMatch(participant -> participant.playerId().equals(blueId)), "Eliminated players should remain match participants for loss/rating/progression handling.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void matchResultKeepsStableMatchId(GameTestHelper context) {
        UUID redId = stableUuid("match-id-red");
        UUID blueId = stableUuid("match-id-blue");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "BLUE boss kill should end the match.")) {
            return;
        }

        Optional<MatchResult> first = game.matchResult();
        Optional<MatchResult> second = game.matchResult();
        if (!assertPresent(context, first, "Ended game should expose the first match result.")) {
            return;
        }
        if (!assertPresent(context, second, "Ended game should expose the second match result.")) {
            return;
        }
        if (!assertEquals(context, first.get().matchId(), second.get().matchId(), "Repeated matchResult calls should keep the same matchId.")) {
            return;
        }
        if (!assertTrue(context, first.get().startedAtEpochMillis() > 0, "Match result should expose a start timestamp.")) {
            return;
        }
        if (!assertTrue(context, first.get().endedAtEpochMillis() >= first.get().startedAtEpochMillis(), "End timestamp should not precede start timestamp.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void matchResultCapturesParticipantJobAndMode(GameTestHelper context) {
        UUID redId = stableUuid("job-statistics-result-red");
        UUID blueId = stableUuid("job-statistics-result-blue");
        Identifier netherJobId = Identifier.fromNamespaceAndPath("semion-td", "nether");
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                testArena(context)
        );
        if (!assertTrue(context, game.selectJob(redId, netherJobId), "Nether job should be selectable before match start.")) {
            return;
        }
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "job-statistics-red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "job-statistics-blue", TeamId.BLUE, 1)
                ),
                Set.of(),
                2
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Job-statistics game should start.")) {
            return;
        }
        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "BLUE boss kill should end the job-statistics game.")) {
            return;
        }

        Optional<MatchResult> result = game.matchResult();
        if (!assertPresent(context, result, "Ended game should expose a job-aware match result.")) {
            return;
        }
        MatchResult matchResult = result.get();
        if (!assertEquals(context, MatchMode.NORMAL, matchResult.matchMode(), "Match result should preserve the start mode.")) {
            return;
        }
        Map<UUID, String> jobsByPlayer = matchResult.participants().stream()
                .collect(Collectors.toMap(MatchParticipantResult::playerId, MatchParticipantResult::jobId));
        if (!assertEquals(context, netherJobId.toString(), jobsByPlayer.get(redId), "Selected job id should be captured.")) {
            return;
        }
        if (!assertEquals(
                context,
                JobRegistry.defaultJob().id().toString(),
                jobsByPlayer.get(blueId),
                "Default job id should be captured."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void jobKillSwitchBlocksWaitingSelectionButKeepsStartedJob(GameTestHelper context) {
        UUID waitingPlayerId = stableUuid("job-kill-switch-waiting");
        UUID redId = stableUuid("job-kill-switch-red");
        UUID blueId = stableUuid("job-kill-switch-blue");
        JobAvailabilityConfig disabledNether = JobAvailabilityConfig.defaultConfig()
                .withEnabled(NetherTowerJob.ID, false);
        try {
            JobRegistry.configureAvailability(JobAvailabilityConfig.defaultConfig());
            SemionGame waitingGame = new SemionGame(
                    EconomyConfig.defaultConfig(),
                    new WaveConfig(List.of(), 20, null),
                    testArena(context)
            );
            if (!assertTrue(context, waitingGame.selectJob(waitingPlayerId, NetherTowerJob.ID), "Enabled job should be selectable in a waiting lobby.")) {
                return;
            }

            JobRegistry.configureAvailability(disabledNether);
            if (!assertEquals(context, JobRegistry.defaultJob().id(), waitingGame.selectedJobOrDefault(waitingPlayerId).id(), "Disabled waiting selection should fall back to the default job.")) {
                return;
            }
            if (!assertTrue(context, !waitingGame.selectJob(waitingPlayerId, NetherTowerJob.ID), "Disabled job should reject new waiting-lobby selection.")) {
                return;
            }

            JobRegistry.configureAvailability(JobAvailabilityConfig.defaultConfig());
            SemionGame startedGame = new SemionGame(
                    EconomyConfig.defaultConfig(),
                    new WaveConfig(List.of(), 20, null),
                    testArena(context)
            );
            if (!assertTrue(context, startedGame.selectJob(redId, NetherTowerJob.ID), "Enabled job should be selectable before match start.")) {
                return;
            }
            ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                    MatchMode.NORMAL,
                    List.of(
                            new AssignedParticipant(redId, "job-kill-switch-red", TeamId.RED, 1),
                            new AssignedParticipant(blueId, "job-kill-switch-blue", TeamId.BLUE, 1)
                    ),
                    Set.of(),
                    2
            );
            if (!assertTrue(context, startedGame.start(context.getLevel().getServer(), plan), "Kill-switch match should start.")) {
                return;
            }

            JobRegistry.configureAvailability(disabledNether);
            if (!assertEquals(context, NetherTowerJob.ID, startedGame.players().get(redId).job().orElseThrow().id(), "Disabling a job must not replace an active participant's assigned job.")) {
                return;
            }
            context.succeed();
        } finally {
            JobRegistry.configureAvailability(JobAvailabilityConfig.defaultConfig());
        }
    }

    @GameTest
    public void disabledPersistedJobFallsBackInNewLobby(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        MinecraftServer server = context.getLevel().getServer();
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-disabled-job-profile-test").resolve("profiles.json");
        } catch (java.io.IOException exception) {
            context.fail(Component.literal("Failed to create temporary disabled-job profile path."));
            return;
        }

        SemionGameManager manager = new SemionGameManager();
        try {
            manager.configure(
                    EconomyConfig.defaultConfig(),
                    WaveConfig.defaultConfig(),
                    MapConfig.defaultConfig(),
                    ProgressionConfig.defaultConfig(),
                    storePath
            );
            manager.saveSelectedJob(
                    server,
                    player.getUUID(),
                    player.getGameProfile().name(),
                    NetherTowerJob.ID
            );
            manager.configureJobAvailability(JobAvailabilityConfig.defaultConfig()
                    .withEnabled(NetherTowerJob.ID, false));

            SemionGame game = manager.createGame(server);
            if (!assertEquals(context, JobRegistry.defaultJob().id(), game.selectedJobOrDefault(player.getUUID()).id(), "Disabled persisted job should fall back in a new lobby.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Disabled persisted job should not apply: " + exception.getMessage()));
        } finally {
            manager.shutdown();
            JobRegistry.configureAvailability(JobAvailabilityConfig.defaultConfig());
        }
    }

    @GameTest
    public void winningTeamsRemainBackwardCompatibleAndTeamResultsExposeGroups(GameTestHelper context) {
        UUID redId = stableUuid("team-result-red");
        UUID blueId = stableUuid("team-result-blue");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "BLUE boss kill should end the match.")) {
            return;
        }

        Optional<MatchResult> result = game.matchResult();
        if (!assertPresent(context, result, "Ended game should expose a match result.")) {
            return;
        }
        MatchResult matchResult = result.get();
        if (!assertEquals(context, Set.of(TeamId.RED), matchResult.winningTeams(), "Winning teams should remain backward compatible.")) {
            return;
        }
        if (!assertEquals(context, 1, matchResult.winnerCount(), "Participant winner count should remain compatible.")) {
            return;
        }
        if (!assertEquals(context, 1, matchResult.loserCount(), "Participant loser count should remain compatible.")) {
            return;
        }
        Map<TeamId, TeamMatchResult> byTeam = teamResultsByTeam(matchResult);
        if (!assertEquals(context, MatchResultGroup.WIN_GROUP, byTeam.get(TeamId.RED).resultGroup(), "Winner team should be in the win group.")) {
            return;
        }
        if (!assertEquals(context, MatchResultGroup.LOSS_GROUP, byTeam.get(TeamId.BLUE).resultGroup(), "Eliminated team should be in the loss group.")) {
            return;
        }
        if (!assertEquals(context, 1, byTeam.get(TeamId.RED).placement(), "Winner should keep placement 1.")) {
            return;
        }
        if (!assertEquals(context, 2, byTeam.get(TeamId.BLUE).placement(), "Loser should get placement 2 in a two-team match.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void fiveTeamMatchResultOrdersEliminatedTeams(GameTestHelper context) {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), testArena(context));
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(stableUuid("placement-red"), "placement-red", TeamId.RED, 1),
                        new AssignedParticipant(stableUuid("placement-blue"), "placement-blue", TeamId.BLUE, 1),
                        new AssignedParticipant(stableUuid("placement-green"), "placement-green", TeamId.GREEN, 1),
                        new AssignedParticipant(stableUuid("placement-yellow"), "placement-yellow", TeamId.YELLOW, 1),
                        new AssignedParticipant(stableUuid("placement-purple"), "placement-purple", TeamId.PURPLE, 1)
                ),
                Set.of(),
                5
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Five-team game should start.")) {
            return;
        }

        if (!assertTrue(context, game.killBoss(TeamId.YELLOW), "YELLOW should be eliminated first.")) {
            return;
        }
        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "BLUE should be eliminated second.")) {
            return;
        }
        if (!assertTrue(context, game.killBoss(TeamId.GREEN), "GREEN should be eliminated third.")) {
            return;
        }
        if (!assertTrue(context, game.killBoss(TeamId.PURPLE), "PURPLE should be eliminated fourth and finish the match.")) {
            return;
        }

        Optional<MatchResult> result = game.matchResult();
        if (!assertPresent(context, result, "Finished five-team game should expose a match result.")) {
            return;
        }
        Map<TeamId, TeamMatchResult> byTeam = teamResultsByTeam(result.get());
        if (!assertEquals(context, 1, byTeam.get(TeamId.RED).placement(), "Living RED team should be first.")) {
            return;
        }
        if (!assertEquals(context, 2, byTeam.get(TeamId.PURPLE).placement(), "Last eliminated PURPLE team should be second.")) {
            return;
        }
        if (!assertEquals(context, 3, byTeam.get(TeamId.GREEN).placement(), "Third eliminated GREEN team should be third.")) {
            return;
        }
        if (!assertEquals(context, 4, byTeam.get(TeamId.BLUE).placement(), "Second eliminated BLUE team should be fourth.")) {
            return;
        }
        if (!assertEquals(context, 5, byTeam.get(TeamId.YELLOW).placement(), "First eliminated YELLOW team should be fifth.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void allEliminatedMatchResultIsUnratedDraw(GameTestHelper context) {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), testArena(context));
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(stableUuid("draw-red"), "draw-red", TeamId.RED, 1),
                        new AssignedParticipant(stableUuid("draw-blue"), "draw-blue", TeamId.BLUE, 1)
                ),
                Set.of(),
                2
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Two-team game should start.")) {
            return;
        }

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "BLUE should be eliminated first.")) {
            return;
        }
        if (!assertTrue(context, game.killBoss(TeamId.RED), "RED should also be eliminable after match end.")) {
            return;
        }

        Optional<MatchResult> result = game.matchResult();
        if (!assertPresent(context, result, "All-eliminated game should expose a match result.")) {
            return;
        }
        if (!assertEquals(context, Set.of(), result.get().winningTeams(), "All-eliminated games should have no winner.")) {
            return;
        }
        Map<TeamId, TeamMatchResult> byTeam = teamResultsByTeam(result.get());
        if (!assertEquals(context, MatchResultGroup.DRAW_OR_UNRATED, byTeam.get(TeamId.RED).resultGroup(), "No-winner RED result should be unrated.")) {
            return;
        }
        if (!assertEquals(context, MatchResultGroup.DRAW_OR_UNRATED, byTeam.get(TeamId.BLUE).resultGroup(), "No-winner BLUE result should be unrated.")) {
            return;
        }
        if (!assertEquals(context, 1, byTeam.get(TeamId.RED).placement(), "No-winner RED placement should be tied first.")) {
            return;
        }
        if (!assertEquals(context, 1, byTeam.get(TeamId.BLUE).placement(), "No-winner BLUE placement should be tied first.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void matchResultRepositoryPersistsDetailedResults(GameTestHelper context) {
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-match-result-test").resolve("match-results.json");
        } catch (java.io.IOException exception) {
            context.fail(Component.literal("Failed to create temporary match result store path."));
            return;
        }

        MatchResult matchResult = new MatchResult(
                MatchId.newId(),
                1000L,
                2000L,
                List.of(new MatchParticipantResult(stableUuid("persisted-player"), "persisted-player", TeamId.RED, true)),
                Set.of(stableUuid("persisted-spectator")),
                Set.of(TeamId.RED),
                List.of(new TeamMatchResult(TeamId.RED, 1, MatchResultGroup.WIN_GROUP, 1.0, -1, -1, 12.5)),
                7
        );
        FileMatchResultRepository repository = new FileMatchResultRepository(storePath);
        repository.saveMatchResult(matchResult);
        FileMatchResultRepository reloaded = new FileMatchResultRepository(storePath);
        Optional<MatchResult> loaded = reloaded.findMatchResult(matchResult.matchId());
        if (!assertPresent(context, loaded, "Saved match result should reload by stable matchId.")) {
            return;
        }
        if (!assertEquals(context, 7, loaded.get().finalRound(), "Persisted match result should keep final round.")) {
            return;
        }
        if (!assertEquals(context, 1, loaded.get().participantCount(), "Persisted match result should keep participant details.")) {
            return;
        }
        if (!assertEquals(context, 12.5, loaded.get().teamResults().getFirst().bossDamageTaken(), "Persisted match result should keep team debug statistics.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void teamSizeBalancePolicyDefaultsToNormalization(GameTestHelper context) {
        if (!assertEquals(
                context,
                TeamSizeBalancePolicy.ALLOW_UNEVEN_WITH_SIZE_NORMALIZATION,
                TeamSizeBalancePolicy.defaultPolicy(),
                "Rating prework should keep uneven team rosters allowed with future normalized scoring."
        )) {
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

        String activeText = SemionHudTextService.matchSidebarMarkupFor(
                redId,
                Optional.of(game.teams().get(TeamId.RED)),
                game,
                MatchMode.NORMAL
        );
        if (!assertTrue(context, activeText.contains("팀/라인"), "Active HUD should show team and lane.")) {
            return;
        }
        if (!assertTrue(context, !activeText.contains("다이아"), "Active HUD should move economy lines to actionbar.")) {
            return;
        }
        String actionbarText = SemionHudTextService.actionbarMarkupFor(game.players().get(redId), game);
        if (!assertTrue(context, actionbarText.contains("다이아"), "Active actionbar should show diamond economy.")) {
            return;
        }
        if (!assertTrue(context, actionbarText.contains("타워"), "Active actionbar should show tower limit.")) {
            return;
        }
        game.players().get(redId).economy().addGas(
                game.economyConfig().towerLimit().initialPurchaseEmeraldCost(),
                Long.MAX_VALUE
        );
        if (!assertTrue(context, game.purchaseTowerLimit(redId), "Tower limit purchase should succeed before actionbar rendering.")) {
            return;
        }
        String upgradedActionbarText = SemionHudTextService.actionbarMarkupFor(game.players().get(redId), game);
        String upgradedTowerLimitText = game.towerCount(redId) + "/" + game.towerLimitForPlayer(redId);
        if (!assertTrue(context, upgradedActionbarText.contains(upgradedTowerLimitText), "Active actionbar should reflect purchased tower slots.")) {
            return;
        }
        String scoreboardText = SemionHudTextService.matchSidebarMarkupFor(
                redId,
                Optional.of(game.teams().get(TeamId.RED)),
                game,
                MatchMode.NORMAL
        );
        if (!assertTrue(context, !scoreboardText.contains("다이아"), "Scoreboard HUD should keep diamond economy in actionbar.")) {
            return;
        }
        if (!assertTrue(context, !scoreboardText.contains("타워"), "Scoreboard HUD should keep tower limit in actionbar.")) {
            return;
        }
        if (!assertTrue(context, !scoreboardText.contains(upgradedTowerLimitText), "Scoreboard HUD should not duplicate purchased tower slots from actionbar.")) {
            return;
        }
        if (!assertTrue(context, scoreboardText.contains("다음 웨이브"), "Preparation HUD should replace the team summary with the upcoming wave.")) {
            return;
        }
        if (!assertTrue(context, activeText.contains("정보 없음"), "Preparation HUD should handle rounds without configured monsters.")) {
            return;
        }
        if (!assertTrue(context, !activeText.contains("animal_pig_1"), "Preparation HUD should not expose internal monster ids.")) {
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
    public void damageSidebarAggregatesTowerTypesAndKeepsSeparateTopFiveLists(GameTestHelper context) {
        UUID playerId = stableUuid("damage-sidebar-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        double[] dealt = {600.0, 500.0, 400.0, 300.0, 200.0, 100.0};
        double[] taken = {10.0, 20.0, 30.0, 40.0, 50.0, 60.0};
        for (int index = 0; index < dealt.length; index++) {
            TowerType type = new TowerType(
                    "damage_sidebar_" + (index + 1),
                    "Damage Type " + (index + 1),
                    TowerCategory.DIRECT,
                    0,
                    100.0,
                    5.0,
                    0.0,
                    20,
                    0
            );
            ProductionTower tower = new ProductionTower(type, playerId, TeamId.RED, 1, new GridPosition(index, 0, 0));
            tower.markWaveStarted(1);
            tower.recordDamageDealt(dealt[index]);
            if (index == 0) {
                tower.recordDamageDealt(25.0, DamageType.MAGIC);
            }
            tower.recordDamageTaken(taken[index]);
            lane.addTower(tower);
            if (index == 0) {
                ProductionTower sameType = new ProductionTower(type, playerId, TeamId.RED, 1, new GridPosition(10, 0, 0));
                sameType.markWaveStarted(1);
                sameType.recordDamageDealt(50.0);
                sameType.recordDamageTaken(5.0);
                lane.addTower(sameType);
            }
        }

        String markup = SemionHudTextService.damageSidebarMarkupFor(playerId, game);
        if (!assertTrue(context, markup.contains("R1 시작 전"), "First preparation should show the pre-wave label.")) {
            return;
        }
        if (!assertTrue(context, markup.contains("<gray>준비</gray> <green>25초</green>"), "Damage sidebar should show the remaining preparation time before the wave.")) {
            return;
        }
        if (!assertTrue(context, markup.contains("<aqua>다음</aqua>"), "Damage sidebar should keep a compact upcoming-wave line during preparation.")) {
            return;
        }
        if (!assertTrue(context, markup.contains("Damage Type 1</white> <#ec8d34>🪓 650</#ec8d34> <#796CFF>🔥 25</#796CFF>"),
                "Same tower types should aggregate and split physical and magic damage.")) {
            return;
        }
        if (!assertTrue(context, !markup.contains("Damage Type 6</white> <#ec8d34>🪓"), "The sixth dealt-damage type should be excluded from the dealt top five.")) {
            return;
        }
        int takenHeader = markup.indexOf("받은 피해 TOP 5");
        if (!assertTrue(
                context,
                takenHeader >= 0
                        && markup.indexOf("Damage Type 6</white> <aqua>🛡 60</aqua>", takenHeader) >= 0
                        && markup.indexOf("Damage Type 6", takenHeader) < markup.indexOf("Damage Type 5", takenHeader),
                "Taken damage should use shield icons and its own descending top-five order."
        )) {
            return;
        }
        if (!assertTrue(context, markup.split("\\R").length <= 14, "Damage sidebar should stay within the sidebar line limit.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void damageSidebarCommandParsesAndDisconnectClearsToggle(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        var parsed = dispatcher.parse("피해량보기", context.getLevel().getServer().createCommandSourceStack());
        if (!assertTrue(context, !parsed.getContext().getNodes().isEmpty() && !parsed.getReader().canRead(), "/피해량보기 should parse completely.")) {
            return;
        }

        var player = context.makeMockServerPlayerInLevel();
        SemionSidebarHudService service = new SemionSidebarHudService();
        service.toggleDamageView(player.getUUID());
        service.remove(player);
        if (!assertTrue(context, !service.damageViewEnabled(player.getUUID()), "Disconnect cleanup should remove the damage sidebar toggle.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void towerLimitConfigScalesFromRoundFiveAndCaps(GameTestHelper context) {
        EconomyConfig.TowerLimitConfig config = EconomyConfig.TowerLimitConfig.defaultConfig();
        if (!assertEquals(context, 5, config.limitForRound(1), "Tower limit should start at five.")) {
            return;
        }
        if (!assertEquals(context, 5, config.limitForRound(4), "Tower limit should stay at five before round five.")) {
            return;
        }
        if (!assertEquals(context, 8, config.limitForRound(5), "Tower limit should gain three slots at round five.")) {
            return;
        }
        if (!assertEquals(context, 11, config.limitForRound(10), "Tower limit should gain another three slots at round ten.")) {
            return;
        }
        if (!assertEquals(context, 11, config.limitForRound(50), "Tower limit should cap at eleven by default.")) {
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
            if (!assertTrue(context, statusLines.stream().anyMatch(line -> line.contains("arenaLoaded=6/6")), "Status should report loaded arenas.")) {
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
    public void managerResetThenCreateStartsFreshMatch(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        SemionGameManager manager = new SemionGameManager();
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-manager-recreate-flow").resolve("profiles.json");
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
            UUID redId = stableUuid("manager-recreate-red");
            UUID blueId = stableUuid("manager-recreate-blue");

            SemionGame firstGame = manager.createGame(server);
            ParticipantSelectionPlan firstPlan = new ParticipantSelectionPlan(
                    MatchMode.NORMAL,
                    List.of(
                            new AssignedParticipant(redId, "recreate-red", TeamId.RED, 1),
                            new AssignedParticipant(blueId, "recreate-blue", TeamId.BLUE, 1)
                    ),
                    Set.of(),
                    2
            );
            if (!assertTrue(context, firstGame.start(server, firstPlan), "First game should start before reset.")) {
                return;
            }
            if (!assertTrue(context, manager.resetToLobby(server), "Reset should close the first active game.")) {
                return;
            }
            if (!assertTrue(context, manager.activeGame().isEmpty(), "Reset should clear active game before recreate.")) {
                return;
            }

            SemionGame secondGame = manager.createGame(server);
            if (!assertTrue(context, manager.activeGame().orElse(null) == secondGame, "Create should install a fresh active waiting game.")) {
                return;
            }
            if (!assertEquals(context, RoundPhase.WAITING, secondGame.phase(), "Fresh game should start from WAITING phase.")) {
                return;
            }
            if (!assertTrue(context, secondGame.markReady(redId), "RED should be able to ready in the fresh game.")) {
                return;
            }
            if (!assertTrue(context, secondGame.markReady(blueId), "BLUE should be able to ready in the fresh game.")) {
                return;
            }

            ParticipantSelectionPlan secondPlan = new ParticipantSelectionPlan(
                    MatchMode.NORMAL,
                    List.of(
                            new AssignedParticipant(redId, "recreate-red", TeamId.RED, 1),
                            new AssignedParticipant(blueId, "recreate-blue", TeamId.BLUE, 1)
                    ),
                    Set.of(),
                    2
            );
            if (!assertTrue(context, secondGame.start(server, secondPlan), "Fresh game should start after reset and recreate.")) {
                return;
            }
            if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, secondGame.phase(), "Fresh game should progress into prepare phase.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Reset/create/start sequence should remain usable: " + exception.getMessage()));
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
        var firstWave = config.configForRound(1).orElseThrow().entriesForLane("lane_1").getFirst();
        if (!assertEquals(context, 10.0, firstWave.health(), "Round 1 monster health should be tuned for starter towers.")) {
            return;
        }
        if (!assertEquals(context, 1.0, firstWave.attackDamage(), "Round 1 monster attack should be tuned for starter towers.")) {
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
                    new Vec3(-4.5, 65.0, 1.5),
                    new Vec3(6.5, 65.0, 0.5),
                    new Vec3(8.5, 65.0, 0.5)
            ), laneOneWaypoints, "Lane waypoints should be followed by shared final waypoints.")) {
                return;
            }
            if (!assertEquals(context, List.of(
                    new Vec3(6.5, 65.0, 0.5),
                    new Vec3(8.5, 65.0, 0.5)
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
        if (!assertEquals(context, 0, blueLane.activeMonsters().size(), "Eliminated team lane should not spawn queued wave monsters.")) {
            return;
        }
        if (!assertTrue(context, context.getLevel().getEntity(activeMonsterEntityId) == null
                || context.getLevel().getEntity(activeMonsterEntityId).isRemoved(), "Active monster entity should be discarded.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void survivingWaveMonsterScalesHealthAndAttackThroughLaneTick(GameTestHelper context) {
        UUID redId = stableUuid("monster-scaling-red");
        UUID blueId = stableUuid("monster-scaling-blue");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);
        PlayerLane redLane = lane(game, TeamId.RED, 1);
        MonsterScalingConfig config = new MonsterScalingConfig(true, 0, 600, 1, 3.0, 3.0, true, true);

        redLane.enqueueWaveMonster(new WaveMonsterEntry(
                "scaling-wave",
                100.0,
                0.0,
                10.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                1,
                1
        ));
        redLane.tick(context.getLevel().getServer(), null, Map.of(), config, 0);

        if (!assertEquals(context, 1, redLane.activeMonsters().size(), "Scaling test should spawn one wave monster.")) {
            return;
        }
        Monster monster = redLane.activeMonsters().getFirst();
        if (!assertTrue(context, Math.abs(monster.maxHealth() - 103.0) < 0.0001, "Configured scaling should increase runtime max health by 3%. Actual=" + monster.maxHealth())) {
            return;
        }
        if (!assertTrue(context, Math.abs(monster.attackDamage() - 10.3) < 0.0001, "Configured scaling should increase runtime attack damage by 3%. Actual=" + monster.attackDamage())) {
            return;
        }
        if (!(context.getLevel().getEntity(monster.minecraftEntityId()) instanceof SemionMonsterEntity entity)) {
            context.fail(Component.literal("Scaled monster entity should exist in the world."));
            return;
        }
        if (!assertTrue(context, Math.abs(entity.getAttributeValue(Attributes.MAX_HEALTH) - 103.0) < 0.0001, "Entity max-health attribute should be synced after scaling. Actual=" + entity.getAttributeValue(Attributes.MAX_HEALTH))) {
            return;
        }
        if (!assertTrue(context, Math.abs(entity.getAttributeValue(Attributes.ATTACK_DAMAGE) - 10.3) < 0.0001, "Entity attack-damage attribute should be synced after scaling. Actual=" + entity.getAttributeValue(Attributes.ATTACK_DAMAGE))) {
            return;
        }

        context.succeed();
    }

    @GameTest
    public void waveMonstersSpawnAcrossLaneSpawnArea(GameTestHelper context) {
        UUID redId = stableUuid("wave-spawn-area-red");
        UUID blueId = stableUuid("wave-spawn-area-blue");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);
        PlayerLane redLane = lane(game, TeamId.RED, 1);

        redLane.enqueueWaveMonster(new WaveMonsterEntry(
                "distributed-wave",
                20.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                1,
                3
        ));
        redLane.tick(context.getLevel().getServer());
        redLane.tick(context.getLevel().getServer());
        redLane.tick(context.getLevel().getServer());

        if (!assertEquals(context, 3, redLane.activeMonsters().size(), "Three wave monsters should spawn after three lane ticks.")) {
            return;
        }

        Set<String> spawnCells = redLane.activeMonsters().stream()
                .map(monster -> BlockPos.containing(monster.spawnX(), monster.spawnY(), monster.spawnZ()))
                .map(pos -> pos.getX() + "," + pos.getY() + "," + pos.getZ())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertTrue(context, spawnCells.size() >= 2, "Wave monsters should not all spawn on the same block.")) {
            return;
        }

        BlockBounds spawnArea = redLane.laneLayout().spawnArea();
        for (Monster monster : redLane.activeMonsters()) {
            BlockPos spawnPos = BlockPos.containing(monster.spawnX(), monster.spawnY(), monster.spawnZ());
            if (!assertTrue(context, spawnArea.contains(spawnPos), "Wave monster should spawn inside lane spawn area: " + spawnPos)) {
                return;
            }
            if (!(context.getLevel().getEntity(monster.minecraftEntityId()) instanceof SemionMonsterEntity entity)) {
                context.fail(Component.literal("Wave monster entity should exist in the world."));
                return;
            }
            if (!assertEquals(context, spawnPos, BlockPos.containing(entity.position()), "Runtime entity should be at the recorded spawn cell.")) {
                return;
            }
        }

        context.succeed();
    }

    @GameTest
    public void incomeSummonsKeepSingleSpawnPointWhenWaveSpawnAreaIsDistributed(GameTestHelper context) {
        UUID redId = stableUuid("income-single-spawn-red");
        UUID blueId = stableUuid("income-single-spawn-blue");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);
        PlayerLane redLane = lane(game, TeamId.RED, 1);
        Monster incomeMonster = new Monster(
                "income-single-spawn",
                TeamId.RED,
                2,
                Optional.empty(),
                Optional.of(TeamId.BLUE),
                20.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                1
        );

        redLane.enqueueSummonedMonster(incomeMonster);
        redLane.tick(context.getLevel().getServer());

        if (!assertEquals(context, 1, redLane.activeMonsters().size(), "Income monster should spawn from the summon queue.")) {
            return;
        }
        Monster spawned = redLane.activeMonsters().getFirst();
        BlockPos recordedSpawn = BlockPos.containing(spawned.spawnX(), spawned.spawnY(), spawned.spawnZ());
        BlockPos legacySpawn = BlockPos.containing(redLane.laneLayout().spawn());
        if (!assertEquals(context, legacySpawn, recordedSpawn, "Income summons should keep the existing single lane spawn point in the MVP.")) {
            return;
        }

        context.succeed();
    }

    @GameTest
    public void incomeSummonRoutingUsesThreatPressureInRuntimeGame(GameTestHelper context) {
        UUID redId = stableUuid("income-threat-routing-red");
        UUID blueLaneOneId = stableUuid("income-threat-routing-blue-1");
        UUID blueLaneTwoId = stableUuid("income-threat-routing-blue-2");
        reloadDefaultIncomeSummons();
        EconomyConfig economy = new EconomyConfig(
                200,
                1000,
                0,
                EconomyConfig.GasCapConfig.defaultConfig(),
                EconomyConfig.GasProductionConfig.defaultConfig(),
                EconomyConfig.TowerLimitConfig.defaultConfig(),
                EconomyConfig.KillRewardConfig.defaultConfig()
        );
        SemionGame game = new SemionGame(
                economy,
                new WaveConfig(List.of(), 20, null),
                LeaderTargetingConfig.defaultConfig(),
                IncomeLaneRoutingConfig.defaultConfig(),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "red", TeamId.RED, 1),
                        new AssignedParticipant(blueLaneOneId, "blue-one", TeamId.BLUE, 1),
                        new AssignedParticipant(blueLaneTwoId, "blue-two", TeamId.BLUE, 2)
                ),
                java.util.Set.of(),
                3
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Threat routing game should start.")) {
            return;
        }

        var heavySummon = game.summonMonster(redId, "ravager");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, heavySummon.type(), "Heavy summon should succeed.")) {
            return;
        }
        PlayerLane blueLaneOne = lane(game, TeamId.BLUE, 1);
        PlayerLane blueLaneTwo = lane(game, TeamId.BLUE, 2);
        if (!assertEquals(context, 1, blueLaneOne.queuedSummonCount(), "First equal-pressure summon should use round-robin lane 1.")) {
            return;
        }
        if (!assertEquals(context, 0, blueLaneTwo.queuedSummonCount(), "Lane 2 should still be empty after first summon.")) {
            return;
        }

        var lightSummon = game.summonMonster(redId, "chicken");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, lightSummon.type(), "Light summon should succeed.")) {
            return;
        }
        if (!assertEquals(context, 1, blueLaneTwo.queuedSummonCount(), "Next summon should route to the lane with lower accumulated threat.")) {
            return;
        }
        if (!assertTrue(context, blueLaneOne.queuedSummonThreat() > blueLaneTwo.queuedSummonThreat(), "Ravager lane should remain much higher threat than chicken lane.")) {
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
        if (!assertTrue(context, game.teams().get(TeamId.GREEN).active(), "GREEN should be active.")) {
            return;
        }
        if (!assertTrue(context, !game.teams().get(TeamId.YELLOW).active(), "YELLOW should be inactive.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void startAssignsHighestEloParticipantAsTeamLeader(GameTestHelper context) {
        UUID redLaneOne = stableUuid("leader-elo-red-lane-one");
        UUID redLaneTwo = stableUuid("leader-elo-red-lane-two");
        UUID blue = stableUuid("leader-elo-blue");
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redLaneOne, "red-low", TeamId.RED, 1, 1200),
                        new AssignedParticipant(redLaneTwo, "red-high", TeamId.RED, 2, 1800),
                        new AssignedParticipant(blue, "blue", TeamId.BLUE, 1, 1500)
                ),
                Set.of(),
                2
        );

        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Game should start with a two-player RED team.")) {
            return;
        }
        if (!assertEquals(context, redLaneTwo, game.teams().get(TeamId.RED).leaderPlayerId().orElseThrow(), "Highest ELO RED player should become team leader even outside lane 1.")) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 700)
    public void preparePhaseTeleportsPlayerAndGrantsHotbarTools(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        UUID playerId = player.getUUID();
        UUID blueId = stableUuid("prepare-hotbar-blue");
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(playerId, player.getGameProfile().name(), TeamId.RED, 1),
                        new AssignedParticipant(blueId, "prepare-hotbar-blue", TeamId.BLUE, 1)
                ),
                Set.of(),
                2
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Game should start with the mock player.")) {
            return;
        }

        Vec3 laneSpawn = StartPlacement.activePlayerSpawn(game.arena().teamArena(TeamId.RED).orElseThrow().layout(), 1);
        if (!assertEquals(context, game.arena().teamArena(TeamId.RED).orElseThrow().world(), player.level(), "Active player should be moved to their team runtime world.")) {
            return;
        }
        if (!assertTrue(context, player.position().distanceTo(laneSpawn) < 0.01, "Active player should start at their assigned lane spawn.")) {
            return;
        }
        if (!assertTrue(context, player.getInventory().getItem(0).is(Items.COMPASS), "Tower control item should be granted in hotbar slot 0.")) {
            return;
        }
        if (!assertTrue(context, player.getInventory().getItem(1).is(Items.ECHO_SHARD), "Summon control item should be granted in hotbar slot 1.")) {
            return;
        }

        player.teleportTo(player.getX() + 12.0, player.getY(), player.getZ() + 12.0);
        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS + 3);
        if (!assertEquals(context, 2, game.currentRound(), "Empty first wave should advance into round 2 prepare.")) {
            return;
        }
        if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Game should return to prepare after round payout.")) {
            return;
        }
        if (!assertTrue(context, player.position().distanceTo(laneSpawn) < 0.01, "Round prepare should return the player to their assigned lane spawn.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void playerFacingDialogsOpenForJobsTowersAndSummons(GameTestHelper context) {
        reloadDefaultIncomeSummons();
        var player = context.makeMockServerPlayerInLevel();
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                testArena(context)
        );
        SemionDialogService dialogService = new SemionDialogService();
        JobRegistry.configureAvailability(JobAvailabilityConfig.defaultConfig().withEnabled(NetherTowerJob.ID, false));
        try {
            dialogService.showJobSelection(player, game);
            dialogService.showJobSelection(player, game, true);
            dialogService.showJobSelection(player, game, false);
            dialogService.showJobManagement(player);
            dialogService.showJobManagement(player, true);
            dialogService.showJobManagement(player, false);
        } finally {
            JobRegistry.configureAvailability(JobAvailabilityConfig.defaultConfig());
        }
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(new AssignedParticipant(player.getUUID(), player.getGameProfile().name(), TeamId.RED, 1)),
                Set.of(),
                1
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Dialog test game should start.")) {
            return;
        }
        dialogService.showLeaderTargetControl(player, game);
        dialogService.showTowerControl(player, game);
        dialogService.showSummonShop(player, game);
        dialogService.showSummonShop(player, game, 2);
        dialogService.showDebugSummonShop(player, 2);
        dialogService.showMatchResult(
                player,
                new MatchResult(
                        List.of(new MatchParticipantResult(player.getUUID(), player.getGameProfile().name(), TeamId.RED, true)),
                        Set.of(),
                        Set.of(TeamId.RED),
                        3
                ),
                Map.of()
        );
        context.succeed();
    }

    @GameTest
    public void jobStatisticsDialogKeepsRegistryOrderAndAppendsRemovedJobs(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        JobStatisticsEntry villager = new JobStatisticsEntry(
                VillagerTowerJob.ID.toString(),
                3L,
                2L,
                3L,
                4L,
                30L,
                JobStatisticsTotals.empty(),
                1_000L,
                2_000L,
                3_000L,
                java.util.stream.IntStream.rangeClosed(1, JobStatisticsEntry.MAX_TRACKED_ROUND)
                        .mapToObj(round -> round <= 20 ? 3L : round <= 30 ? 1L : 0L)
                        .toList()
        );
        JobStatisticsEntry removed = new JobStatisticsEntry(
                "external:removed",
                2L,
                1L,
                2L,
                3L,
                20L,
                JobStatisticsTotals.empty(),
                1_000L,
                2_000L,
                3_000L
        );
        JobStatisticsSnapshot snapshot = new JobStatisticsSnapshot(
                3_000L,
                2L,
                5L,
                1_000L,
                2_000L,
                List.of(removed, villager)
        );

        List<SemionDialogService.JobStatisticsRow> rows = SemionDialogService.jobStatisticsRows(snapshot);
        List<String> registryOrder = JobRegistry.all().stream().map(job -> job.id().toString()).toList();
        if (!assertEquals(context, registryOrder, rows.subList(0, registryOrder.size()).stream()
                .map(SemionDialogService.JobStatisticsRow::jobId)
                .toList(), "Statistics rows should keep JobRegistry order.")) {
            return;
        }
        SemionDialogService.JobStatisticsRow last = rows.getLast();
        if (!assertEquals(context, "external:removed", last.jobId(), "Removed job ids should be appended.")) {
            return;
        }
        if (!assertTrue(context, !last.registered(), "Removed job ids should be marked unregistered.")) {
            return;
        }
        if (!assertEquals(context, 3L, villager.roundPassCount(20), "Round 20 pass count should be retained.")) {
            return;
        }
        if (!assertEquals(context, 1L, villager.roundPassCount(30), "Round 30 pass count should be retained.")) {
            return;
        }
        if (!assertEquals(context, 0L, villager.roundPassCount(40), "Round 40 pass count should be retained.")) {
            return;
        }
        SemionDialogService dialogService = new SemionDialogService();
        dialogService.showJobStatistics(player, snapshot, JobStatisticsState.READY);
        dialogService.showJobStatisticsDetail(
                player,
                snapshot,
                JobStatisticsState.READY,
                VillagerTowerJob.ID.toString()
        );
        context.succeed();
    }

    @GameTest
    public void playerStatusDialogRowsUseCurrentEconomyTowerCountAndJob(GameTestHelper context) {
        reloadDefaultIncomeSummons();
        var player = context.makeMockServerPlayerInLevel();
        UUID redId = player.getUUID();
        UUID blueId = stableUuid("status-table-blue");
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                testArena(context)
        );
        if (!assertTrue(context, game.selectJob(redId, NetherTowerJob.ID), "Status table test should select the nether job.")) {
            return;
        }
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(blueId, "status-blue", TeamId.BLUE, 1),
                        new AssignedParticipant(redId, player.getGameProfile().name(), TeamId.RED, 1)
                ),
                Set.of(),
                2
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Status table test game should start.")) {
            return;
        }

        game.players().get(redId).economy().overrideStartingValues(321, 45, 17, 1);
        game.players().get(blueId).economy().overrideStartingValues(111, 22, 9, 1);
        PlayerLane lane = game.playerLane(redId).orElseThrow();
        BlockPos towerPos = towerPlacementPos(lane);
        lane.addTower(new TestTower(redId, TeamId.RED, 1, GridPosition.from(towerPos)));
        lane.addTower(new TestTower(redId, TeamId.RED, 1, GridPosition.from(towerPos.offset(1, 0, 0))));

        List<SemionDialogService.PlayerStatusRow> rows = SemionDialogService.playerStatusRows(game);
        if (!assertEquals(context, 2, rows.size(), "Status table should include every active participant.")) {
            return;
        }
        SemionDialogService.PlayerStatusRow red = rows.getFirst();
        if (!assertEquals(context, redId, red.playerId(), "Status rows should use deterministic team and lane ordering.")) {
            return;
        }
        if (!assertEquals(context, TeamId.RED, red.teamId(), "Status rows should expose the player's team for grouping and name color.")) {
            return;
        }
        if (!assertEquals(context, 321L, red.diamond(), "Status table should show current diamonds.")) {
            return;
        }
        if (!assertEquals(context, 45L, red.emerald(), "Status table should show current emeralds.")) {
            return;
        }
        if (!assertEquals(context, 17L, red.income(), "Status table should show current income.")) {
            return;
        }
        if (!assertEquals(context, 2, red.towerCount(), "Status table should count the player's current towers.")) {
            return;
        }
        if (!assertEquals(context, game.players().get(redId).job().orElseThrow().displayName().getString(), red.jobName(), "Status table should show the active job.")) {
            return;
        }
        if (!assertEquals(context, JobRegistry.defaultJob().displayName().getString(), rows.get(1).jobName(), "Status table should use the default job name when no job was selected.")) {
            return;
        }
        if (!assertEquals(context, TeamId.BLUE, rows.get(1).teamId(), "Status rows should keep players grouped in team order.")) {
            return;
        }

        new SemionDialogService().showGameStatus(player, game);
        context.succeed();
    }

    @GameTest
    public void emptyProductionTowerCatalogRejectsBuildRequests(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        UUID playerId = stableUuid("red-production-villager-owner");
        SemionGame game = startedSinglePlayerGame(
                context,
                playerId,
                TeamId.RED
        );
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);

        if (!assertEquals(
                context,
                TowerPlacementResult.UNKNOWN_TOWER,
                ProductionTowerService.placeTower(game, playerId, towerPos, "missing_manual_tower"),
                "Empty production catalog should reject build requests until a tower is registered."
        )) {
            return;
        }
        if (!assertTrue(context, lane.towers().isEmpty(), "Rejected production build should not add a runtime tower.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void emptyProductionTowerCatalogKeepsBuildListsEmpty(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        UUID unjobbedId = stableUuid("unjobbed-production-owner");
        SemionGame unjobbedGame = startedSinglePlayerGame(context, unjobbedId, TeamId.RED);
        if (!assertEquals(
                context,
                0,
                ProductionTowerService.availableTowers(unjobbedGame, unjobbedId).size(),
                "Unjobbed players should see no production towers while the catalog is empty."
        )) {
            return;
        }

        context.succeed();
    }

    @GameTest
    public void towerBuildButtonLabelsColorUnaffordableTowersRed(GameTestHelper context) {
        ProductionTowerCatalog.CatalogEntry entry = productionFixtureEntry();
        Component affordable = SemionDialogService.towerButtonLabel(entry, true);
        Component unaffordable = SemionDialogService.towerButtonLabel(entry, false);
        Component recommended = SemionDialogService.towerButtonLabel(entry, false, true);

        if (!assertEquals(
                context,
                net.minecraft.network.chat.TextColor.GREEN.getValue(),
                affordable.getStyle().getColor().getValue(),
                "Affordable tower button labels should be green when the UI opens."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                net.minecraft.network.chat.TextColor.RED.getValue(),
                unaffordable.getStyle().getColor().getValue(),
                "Unaffordable tower button labels should be red when the UI opens."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                net.minecraft.network.chat.TextColor.BLUE.getValue(),
                recommended.getStyle().getColor().getValue(),
                "Build-recommended tower button labels should be blue regardless of affordability."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void buildRecommendedUpgradeButtonLabelsUseBlue(GameTestHelper context) {
        TowerUpgradeOption option = new TowerUpgradeOption(
                "manual_upgrade",
                "Manual Upgrade",
                productionFixtureType("manual_fixture_blue_upgrade_target", List.of()),
                100
        );
        Component label = SemionDialogService.upgradeButtonLabel(option, false, true);
        if (!assertEquals(
                context,
                net.minecraft.network.chat.TextColor.BLUE.getValue(),
                label.getStyle().getColor().getValue(),
                "Build-recommended upgrade button labels should be blue."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void buildGuideRecordsSuccessfulActionsAndPersistsPublishedGuide(GameTestHelper context) {
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-build-guide-test").resolve("build_guides.json");
        } catch (java.io.IOException exception) {
            context.fail(Component.literal("Failed to create temporary build guide store path."));
            return;
        }

        BuildGuideService service = new BuildGuideService(storePath);
        UUID redId = stableUuid("build-guide-red-owner");
        UUID blueId = stableUuid("build-guide-blue-owner");
        reloadDefaultIncomeSummons();
        ProductionTowerCatalog.clear();
        TowerType starterType = productionFixtureType("manual_fixture_build_record_starter", List.of());
        TowerType targetType = productionFixtureType("manual_fixture_build_record_target", List.of());
        ProductionTowerCatalog.registerStarter(starterType);
        ProductionTowerCatalog.register(targetType, 2);
        ProductionTowerCatalog.linkUpgrade(starterType, "manual_upgrade", "Manual Upgrade", targetType, 0);
        SemionJob testJob = registerTowerAllowingJob(
                "build_record",
                Set.of(starterType.id(), targetType.id())
        );

        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                testArena(context),
                service
        );
        game.selectJob(redId, testJob.id());
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "blue", TeamId.BLUE, 1)
                ),
                java.util.Set.of(),
                2
        );
        TraitLoadout selectedTraits = new TraitLoadout(
                BuiltInTraits.STRENGTH_IN_NUMBERS_ID,
                BuiltInTraits.SUPPLY_DEPOT_ID
        );
        if (!assertTrue(
                context,
                game.start(
                        context.getLevel().getServer(),
                        plan,
                        new TraitSelectionSnapshot(Map.of(redId, selectedTraits))
                ),
                "Build recording game should start."
        )) {
            return;
        }

        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        game.players().get(redId).economy().addGas(20L, Long.MAX_VALUE);
        ProductionTowerService.placeTower(game, redId, towerPos, "missing_tower");
        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                ProductionTowerService.placeTower(game, redId, towerPos, starterType.id()),
                "Successful tower placement should be accepted for build recording."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerUpgradeResult.SUCCESS,
                ProductionTowerService.upgradeTower(game, redId, towerPos, "manual_upgrade"),
                "Successful tower upgrade should be accepted for build recording."
        )) {
            return;
        }
        ProductionTowerService.SaleResult sale = ProductionTowerService.sellTower(game, redId, towerPos);
        if (!assertEquals(context, TowerSellResult.SUCCESS, sale.result(), "Successful tower sale should be accepted for build recording.")) {
            return;
        }
        var summon = game.summonMonster(redId, "chicken");
        if (!assertEquals(
                context,
                kim.biryeong.semiontd.summon.SummonResultType.SUCCESS,
                summon.type(),
                "Successful summon should be accepted for build recording."
        )) {
            return;
        }
        if (!assertTrue(context, game.upgradeGasProduction(redId), "Successful emerald production upgrade should be accepted for build recording.")) {
            return;
        }

        service.finishMatch(game, 3);
        Optional<BuildGuide> published = service.publishLastRecording(redId, "테스트 빌드");
        if (!assertPresent(context, published, "Finished recording should publish a build guide.")) {
            return;
        }
        BuildGuide guide = published.get();
        if (!assertEquals(context, 5, guide.actions().size(), "Only successful placement, upgrade, sale, summon, and emerald upgrade actions should be recorded.")) {
            return;
        }
        if (!assertTrue(context, guide.isPrivate(), "Newly recorded build guides should be private by default.")) {
            return;
        }
        if (!assertEquals(
                context,
                BuiltInTraits.STRENGTH_IN_NUMBERS_ID.toString(),
                guide.traitLoadout().primaryTraitId(),
                "Published build guide should keep the selected primary trait."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                BuiltInTraits.SUPPLY_DEPOT_ID.toString(),
                guide.traitLoadout().secondaryTraitId(),
                "Published build guide should keep the selected secondary trait."
        )) {
            return;
        }
        String guideCode = guide.code();
        if (!assertTrue(context, service.publicGuides().stream().noneMatch(found -> found.code().equals(guideCode)), "Private guides should not appear in the public build list.")) {
            return;
        }
        if (!assertTrue(context, service.myGuides(redId).stream().anyMatch(found -> found.code().equals(guideCode)), "Owner should see private guides in my build list.")) {
            return;
        }
        if (!assertTrue(context, service.findViewable(guide.code(), blueId).isEmpty(), "Other players should not view private guides.")) {
            return;
        }
        if (!assertTrue(context, !service.track(blueId, guide.code()), "Other players should not track private guides.")) {
            return;
        }
        if (!assertTrue(context, service.setVisibility(blueId, guide.code(), BuildGuide.VISIBILITY_PUBLIC).isEmpty(), "Non-owners should not publish another player's guide.")) {
            return;
        }
        guide = service.setVisibility(redId, guide.code(), BuildGuide.VISIBILITY_PUBLIC).orElseThrow();
        if (!assertTrue(context, guide.isPublic(), "Owner should be able to publish a private guide.")) {
            return;
        }
        if (!assertTrue(context, service.publicGuides().stream().anyMatch(found -> found.code().equals(guideCode)), "Published guides should appear in the public build list.")) {
            return;
        }
        if (!assertTrue(context, guide.actions().stream().anyMatch(action -> action.type() == BuildActionType.TOWER_PLACE), "Published guide should include tower placement.")) {
            return;
        }
        BuildAction placementAction = guide.actions().stream()
                .filter(action -> action.type() == BuildActionType.TOWER_PLACE)
                .findFirst()
                .orElseThrow();
        if (!assertTrue(context, placementAction.hasLaneRelativePosition(), "Recorded tower placement should store a lane-relative position.")) {
            return;
        }
        if (!assertEquals(
                context,
                starterType.displayName(),
                BuildGuideService.subjectDisplayName(placementAction),
                "Build guide tower placement display should use the tower display name instead of the internal id."
        )) {
            return;
        }
        GridPosition redAbsolutePosition = GridPosition.from(towerPos);
        if (!assertEquals(
                context,
                redAbsolutePosition,
                service.resolveActionPosition(game, redId, placementAction).orElse(null),
                "Lane-relative recorded placement should resolve back to the original player lane position."
        )) {
            return;
        }
        service.track(blueId, guide.code());
        GridPosition blueResolvedPosition = service.resolveActionPosition(game, blueId, placementAction).orElse(null);
        if (!assertTrue(context, blueResolvedPosition != null, "Lane-relative recorded placement should resolve for another player lane.")) {
            return;
        }
        if (!assertTrue(
                context,
                service.isRecommendedTower(game, blueId, placementAction.round(), blueResolvedPosition, starterType.id()),
                "Tracked placement recommendations should compare against the current player's lane-relative absolute position."
        )) {
            return;
        }
        if (!assertTrue(context, guide.actions().stream().anyMatch(action -> action.type() == BuildActionType.TOWER_UPGRADE), "Published guide should include tower upgrade.")) {
            return;
        }
        BuildAction upgradeAction = guide.actions().stream()
                .filter(action -> action.type() == BuildActionType.TOWER_UPGRADE)
                .findFirst()
                .orElseThrow();
        if (!assertEquals(
                context,
                "Manual Upgrade",
                BuildGuideService.subjectDisplayName(upgradeAction),
                "Build guide tower upgrade display should use the upgrade display name instead of the internal id."
        )) {
            return;
        }
        if (!assertTrue(context, guide.actions().stream().anyMatch(action -> action.type() == BuildActionType.SUMMON), "Published guide should include summon purchase.")) {
            return;
        }
        BuildAction summonAction = guide.actions().stream()
                .filter(action -> action.type() == BuildActionType.SUMMON)
                .findFirst()
                .orElseThrow();
        if (!assertTrue(
                context,
                !BuildGuideService.subjectDisplayName(summonAction).equals(summonAction.subjectId()),
                "Build guide summon display should use the summon display name instead of the internal id."
        )) {
            return;
        }
        if (!assertTrue(context, guide.actions().stream().anyMatch(action -> action.type() == BuildActionType.EMERALD_PRODUCTION_UPGRADE), "Published guide should include emerald production upgrade.")) {
            return;
        }

        BuildGuideService reloaded = new BuildGuideService(storePath);
        Optional<BuildGuide> reloadedGuide = reloaded.find(guide.code());
        if (!assertPresent(context, reloadedGuide, "Published guide should survive build store reload.")) {
            return;
        }
        if (!assertEquals(
                context,
                guide.traitLoadout(),
                reloadedGuide.get().traitLoadout(),
                "Persisted build guide should keep the selected traits after reload."
        )) {
            return;
        }
        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "Build recording game should finish with a match result.")) {
            return;
        }
        MatchParticipantResult participantResult = game.matchResult().orElseThrow().participants().stream()
                .filter(participant -> participant.playerId().equals(redId))
                .findFirst()
                .orElseThrow();
        if (!assertEquals(
                context,
                List.of(
                        BuildActionType.TOWER_PLACE,
                        BuildActionType.TOWER_UPGRADE,
                        BuildActionType.TOWER_SELL,
                        BuildActionType.SUMMON,
                        BuildActionType.EMERALD_PRODUCTION_UPGRADE
                ),
                participantResult.buildActions().stream().map(BuildAction::type).toList(),
                "Match result should preserve every successful build action in insertion order."
        )) {
            return;
        }
        BuildAction saleAction = participantResult.buildActions().get(2);
        if (!assertEquals(context, sale.refundAmount(), saleAction.incomeGain(), "Match result sale action should preserve the actual refund.")) {
            return;
        }
        if (!assertTrue(context, saleAction.hasLaneRelativePosition(), "Match result sale action should preserve lane-relative coordinates.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void disconnectClearsReadyStateAndCancelsPendingStart(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        var leavingPlayer = context.makeMockServerPlayerInLevel();
        UUID leavingId = leavingPlayer.getUUID();
        UUID stayingId = stableUuid("ready-disconnect-staying");
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), SyntheticArenaFactory.create(
                context.getLevel(),
                context.absolutePos(BlockPos.ZERO)
        ));
        SemionGameManager manager = new SemionGameManager();
        setField(manager, "activeGame", game);
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(leavingId, "ready-disconnect-leaving", TeamId.RED, 1),
                        new AssignedParticipant(stayingId, "ready-disconnect-staying", TeamId.BLUE, 1)
                ),
                Set.of(),
                2
        );

        try {
            game.markReady(stayingId);
            manager.handlePlayerDisconnect(leavingPlayer);
            if (!assertEquals(context, 1, game.readyPlayerCount(), "An unready disconnect should not change the ready count.")) {
                return;
            }

            game.markReady(leavingId);
            manager.handlePlayerDisconnect(leavingPlayer);
            if (!assertTrue(context, !game.isReady(leavingId), "A ready disconnect should remove the leaving player from the ready roster.")) {
                return;
            }
            if (!assertEquals(context, 1, game.readyPlayerCount(), "The remaining ready player should stay ready after another player disconnects.")) {
                return;
            }

            game.markReady(leavingId);
            manager.configureTraits(new TraitSelectionConfig(false, 45));
            if (!assertEquals(context, SemionGameManager.StartCountdownResult.SCHEDULED, manager.scheduleStart(server, plan), "Normal start countdown should be scheduled.")) {
                return;
            }
            manager.handlePlayerDisconnect(leavingPlayer);
            if (!assertTrue(context, !manager.startCountdownActive(), "A selected participant disconnect should cancel the normal start countdown.")) {
                return;
            }

            game.markReady(leavingId);
            manager.configureTraits(new TraitSelectionConfig(true, 45));
            if (!assertEquals(context, SemionGameManager.StartCountdownResult.SCHEDULED, manager.scheduleStart(server, plan), "Trait selection should be scheduled.")) {
                return;
            }
            if (!assertTrue(context, manager.traitSelectionActive(), "Trait selection should be active before the selected participant disconnects.")) {
                return;
            }
            manager.handlePlayerDisconnect(leavingPlayer);
            if (!assertTrue(context, !manager.startCountdownActive(), "A selected participant disconnect should cancel trait selection.")) {
                return;
            }
            context.succeed();
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void buildGuideRemainsPublishableUntilNextCountdownCompletes(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        var player = context.makeMockServerPlayerInLevel();
        SemionGameManager manager = new SemionGameManager();
        manager.configureTraits(new TraitSelectionConfig(false, 45));
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-build-guide-manager-test").resolve("profiles.json");
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
            UUID redId = player.getUUID();
            UUID blueId = stableUuid("build-guide-waiting-blue-owner");
            SemionGame finishedGame = manager.createGame(server);
            ParticipantSelectionPlan firstPlan = new ParticipantSelectionPlan(
                    MatchMode.NORMAL,
                    List.of(
                            new AssignedParticipant(redId, player.getGameProfile().name(), TeamId.RED, 1),
                            new AssignedParticipant(blueId, "build-guide-waiting-blue", TeamId.BLUE, 1)
                    ),
                    Set.of(),
                    2
            );
            if (!assertTrue(context, finishedGame.start(server, firstPlan), "First game should start build recording.")) {
                return;
            }
            PlayerLane lane = redLane(finishedGame, 1);
            finishedGame.recordTowerPlacement(redId, "waiting_publish_tower", GridPosition.from(towerPlacementPos(lane)), 0L);
            if (!assertTrue(context, finishedGame.killBoss(TeamId.BLUE), "First game should end before the next game is created.")) {
                return;
            }

            manager.tick(server);
            for (int tick = 0; tick <= SemionGameManager.MATCH_RESULT_DELAY_TICKS; tick++) {
                manager.tick(server);
            }
            for (int tick = 0; tick <= SemionGameManager.MATCH_RESULT_DIALOG_AFTER_LOBBY_DELAY_TICKS; tick++) {
                manager.tick(server);
            }
            if (!assertPresent(
                    context,
                    manager.publishLastBuild(player, "통계 화면 후 저장"),
                    "The surviving winner should publish after the result dialog is shown."
            )) {
                return;
            }

            SemionGame waitingGame = manager.createGame(server);
            if (!assertTrue(context, manager.lastMatchResult().isEmpty(), "Creating the next waiting game should clear stale match results.")) {
                return;
            }
            if (!assertEquals(context, RoundPhase.WAITING, waitingGame.phase(), "Next game should still be waiting before build publish.")) {
                return;
            }

            ParticipantSelectionPlan secondPlan = new ParticipantSelectionPlan(
                    MatchMode.NORMAL,
                    List.of(
                            new AssignedParticipant(redId, player.getGameProfile().name(), TeamId.RED, 1),
                            new AssignedParticipant(blueId, "build-guide-waiting-blue", TeamId.BLUE, 1)
                    ),
                    Set.of(),
                    2
            );
            if (!assertEquals(
                    context,
                    SemionGameManager.StartCountdownResult.SCHEDULED,
                    manager.scheduleStart(server, secondPlan),
                    "Next game countdown should start."
            )) {
                return;
            }
            for (int tick = 0; tick < SemionGameManager.START_COUNTDOWN_TICKS - 1; tick++) {
                manager.tick(server);
            }
            if (!assertEquals(context, RoundPhase.WAITING, waitingGame.phase(), "Next game should remain waiting until the final countdown tick.")) {
                return;
            }
            Optional<BuildGuide> published = manager.publishLastBuild(player, "카운트다운 중 저장");
            if (!assertPresent(context, published, "Last finished build recording should publish before the countdown completes.")) {
                return;
            }
            if (!assertEquals(context, 1, published.get().actions().size(), "Published waiting-period guide should preserve the previous match action.")) {
                return;
            }

            manager.tick(server);
            if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, waitingGame.phase(), "Final countdown tick should start the next game.")) {
                return;
            }
            if (!assertPresent(
                    context,
                    manager.publishLastBuild(player, "다음 경기 시작 후 저장"),
                    "Previous match recording should remain publishable until the next match finishes."
            )) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Build guide should remain publishable until the next countdown completes: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void buildGuideRoundActionsFilterCurrentRound(GameTestHelper context) {
        BuildGuide guide = new BuildGuide(
                "ABC123",
                "라운드 필터",
                stableUuid("build-round-author"),
                "author",
                "semion-td:default",
                kim.biryeong.semiontd.trait.TraitLoadoutSnapshot.none(),
                4,
                1L,
                BuildGuide.VISIBILITY_PUBLIC,
                List.of(
                        BuildAction.towerPlace(1, "tower_a", new GridPosition(1, 64, 1), 0),
                        BuildAction.towerPlace(2, "tower_b", new GridPosition(2, 64, 2), 0)
                )
        );
        List<BuildAction> roundTwo = BuildGuideService.actionsForRound(guide, 2);
        if (!assertEquals(context, 1, roundTwo.size(), "Round action filtering should include only the requested round.")) {
            return;
        }
        if (!assertEquals(context, "tower_b", roundTwo.getFirst().subjectId(), "Round action filtering should return the current-round action.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void buildGuidePublicListsHideDebugGuides(GameTestHelper context) {
        BuildGuideService service = new BuildGuideService(null);
        UUID authorId = stableUuid("build-debug-filter-author");
        BuildGuide debugGuide = service.saveDebugGuide(
                "DEBUG1",
                "디버그 빌드",
                authorId,
                "debug",
                "semion-td:debug",
                1,
                List.of(BuildAction.towerPlace(1, "debug_tower", new GridPosition(0, 0, 0), 0))
        );
        BuildGuide publicGuide = service.saveDebugGuide(
                "LIVE01",
                "실제 빌드",
                authorId,
                "debug",
                "semion-td:default",
                1,
                List.of(BuildAction.towerPlace(1, "live_tower", new GridPosition(0, 0, 0), 0))
        );

        if (!assertTrue(context, service.find(debugGuide.code()).isPresent(), "Debug guide should remain addressable by code for debug commands.")) {
            return;
        }
        if (!assertTrue(context, service.publicGuides().stream().noneMatch(BuildGuideService::isDebugGuide), "Normal public build list should hide debug guides.")) {
            return;
        }
        if (!assertTrue(context, service.publicGuides().stream().anyMatch(guide -> guide.code().equals(publicGuide.code())), "Normal public build list should still include real guides.")) {
            return;
        }
        if (!assertTrue(
                context,
                service.recentGuides(authorId, List.of(debugGuide.code(), publicGuide.code())).stream().noneMatch(BuildGuideService::isDebugGuide),
                "Normal recent build list should hide debug guides."
        )) {
            return;
        }
        if (!assertTrue(context, service.debugPublicGuides().stream().anyMatch(BuildGuideService::isDebugGuide), "Debug build list should still include debug guides.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void buildGuideOwnerCanToggleVisibilityAndDelete(GameTestHelper context) {
        BuildGuideService service = new BuildGuideService(null);
        UUID ownerId = stableUuid("build-owner-management-owner");
        UUID otherId = stableUuid("build-owner-management-other");
        BuildGuide guide = service.saveDebugGuide(
                "LIVE02",
                "관리 테스트",
                ownerId,
                "owner",
                "semion-td:default",
                2,
                List.of(BuildAction.towerPlace(1, "live_tower", new GridPosition(0, 0, 0), 0))
        );

        if (!assertTrue(context, service.setVisibility(otherId, guide.code(), BuildGuide.VISIBILITY_PRIVATE).isEmpty(), "Non-owner should not change build visibility.")) {
            return;
        }
        guide = service.setVisibility(ownerId, guide.code(), BuildGuide.VISIBILITY_PRIVATE).orElseThrow();
        if (!assertTrue(context, guide.isPrivate(), "Owner should be able to make a build private.")) {
            return;
        }
        if (!assertTrue(context, service.findViewable(guide.code(), otherId).isEmpty(), "Private owner builds should not be viewable by other players.")) {
            return;
        }
        if (!assertTrue(context, !service.delete(otherId, guide.code()), "Non-owner should not delete another player's build.")) {
            return;
        }
        if (!assertTrue(context, service.delete(ownerId, guide.code()), "Owner should be able to delete their build.")) {
            return;
        }
        if (!assertTrue(context, service.find(guide.code()).isEmpty(), "Deleted build should be removed from the store.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void buildGuideIndicatorUsesVanillaForPlayersWithoutGcb(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        if (!assertEquals(
                context,
                BuildGuideIndicatorService.DeliveryPath.VANILLA,
                BuildGuideIndicatorService.deliveryPath(player),
                "Players without the GCB client mod should use the vanilla particle fallback."
        )) {
            return;
        }
        context.succeed();
    }

    private static ProductionTowerCatalog.CatalogEntry productionFixtureEntry() {
        return new ProductionTowerCatalog.CatalogEntry(
                productionFixtureType("manual_fixture_entry", List.of()),
                null,
                1
        );
    }

    @GameTest
    public void productionCatalogFactoryAcceptsNonProductionEntityBackedTower(GameTestHelper context) {
        UUID playerId = stableUuid("red-custom-production-runtime-owner");
        TowerType type = productionFixtureType("manual_fixture_custom_runtime", List.of());
        ProductionTowerCatalog.CatalogEntry entry = new ProductionTowerCatalog.CatalogEntry(
                type,
                FixtureSupportTower::new,
                1
        );

        Tower tower = entry.create(
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(0, 64, 0)
        );
        if (!assertTrue(
                context,
                tower instanceof FixtureSupportTower,
                "Production catalog factories should allow entity-backed tower implementations that do not extend ProductionTower."
        )) {
            return;
        }
        if (!assertEquals(context, type, tower.type(), "Factory-created custom runtime tower should preserve its catalog type.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void towerCopyFromTransfersSaleAndRuntimeState(GameTestHelper context) {
        UUID playerId = stableUuid("red-copy-runtime-state-owner");
        TowerType sourceType = productionFixtureType("manual_fixture_copy_source", List.of());
        TowerType targetType = productionFixtureType("manual_fixture_copy_target", List.of());
        kim.biryeong.semiontd.game.GridPosition position = new kim.biryeong.semiontd.game.GridPosition(0, 64, 0);
        TowerDataKey<Integer> stacksKey = TowerDataKey.of(
                Identifier.fromNamespaceAndPath("semion-td", "test/support_stacks"),
                Integer.class
        );
        FixtureSupportTower sourceTower = new FixtureSupportTower(
                sourceType,
                playerId,
                TeamId.RED,
                1,
                position,
                position
        );
        sourceTower.recordPlacementEconomy(75, 2);
        sourceTower.markWaveStarted(2);
        sourceTower.setPersistentBonus(4);
        sourceTower.setData(stacksKey, 3);

        FixtureSupportTower targetTower = new FixtureSupportTower(
                targetType,
                playerId,
                TeamId.RED,
                1,
                position,
                position
        );
        targetTower.copyFrom(sourceTower, 25);

        if (!assertEquals(context, 100L, targetTower.paidMineralCost(), "copyFrom should carry sale cost plus upgrade cost.")) {
            return;
        }
        if (!assertEquals(context, 2, targetTower.placedRound(), "copyFrom should carry original placement round.")) {
            return;
        }
        if (!assertTrue(context, targetTower.waveStartedAfterPlacement(), "copyFrom should carry wave-start sale state.")) {
            return;
        }
        if (!assertEquals(context, 4, targetTower.persistentBonus(), "copyFrom should call the runtime-state copy hook.")) {
            return;
        }
        if (!assertTrue(context, targetTower.hasData(stacksKey), "copyFrom should carry generic tower data keys.")) {
            return;
        }
        if (!assertEquals(context, 3, targetTower.getDataOrDefault(stacksKey, 0), "copyFrom should carry generic tower data values.")) {
            return;
        }
        targetTower.removeData(stacksKey);
        if (!assertTrue(context, !targetTower.hasData(stacksKey), "removeData should clear generic tower data values.")) {
            return;
        }
        context.succeed();
    }

    private static TowerType productionFixtureType(String id, List<TowerUpgradeOption> upgradeOptions) {
        return new TowerType(
                id,
                "Manual Fixture",
                TowerCategory.DIRECT,
                0,
                80.0,
                8.0,
                8.0,
                20,
                0,
                "minecraft:villager",
                upgradeOptions
        );
    }

    @GameTest
    public void selectedJobPlaceholderShowsActivePlayerJob(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        Identifier jobId = JobRegistry.defaultJob().id();
        SemionGame game = startedSinglePlayerGame(context, player.getUUID(), TeamId.RED, jobId);
        SemionGameManager manager = new SemionGameManager();
        setField(manager, "activeGame", game);
        SemionPlaceholders.register(manager);

        PlaceholderResult display = Placeholders.parseServerPlaceholder(
                Identifier.fromNamespaceAndPath("semion-td", "selected_job"),
                null,
                ServerPlaceholderContext.of(player)
        );
        if (!assertTrue(context, display.isValid(), "Selected job display placeholder should resolve for a player.")) {
            return;
        }
        if (!assertEquals(context, JobRegistry.defaultJob().displayName().getString(), display.component().getString(), "Selected job placeholder should show the chosen job display name.")) {
            return;
        }

        PlaceholderResult id = Placeholders.parseServerPlaceholder(
                Identifier.fromNamespaceAndPath("semion-td", "selected_job_id"),
                null,
                ServerPlaceholderContext.of(player)
        );
        if (!assertTrue(context, id.isValid(), "Selected job id placeholder should resolve for a player.")) {
            return;
        }
        if (!assertEquals(context, jobId.toString(), id.component().getString(), "Selected job id placeholder should show the chosen job id.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void jobSelectionDialogUsesPathCommandButtons(GameTestHelper context) {
        SemionJob job = JobRegistry.defaultJob();
        if (!assertEquals(
                context,
                "/semiontd job select " + job.id().getPath(),
                SemionDialogService.jobSelectionCommand(job),
                "Job selection buttons should send path-only job ids instead of namespaced identifiers."
        )) {
            return;
        }
        if (!assertTrue(
                context,
                !SemionDialogService.jobSelectionCommand(job).contains("semion-td:"),
                "Job selection UI commands should not expose the namespace."
        )) {
            return;
        }
        Component selected = SemionDialogService.jobButtonLabel(job, true);
        if (!assertEquals(
                context,
                net.minecraft.network.chat.TextColor.GREEN.getValue(),
                selected.getStyle().getColor().getValue(),
                "Selected job button labels should be highlighted."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void productionTowerUpgradeRejectsUnregisteredTargetType(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        TowerType upgradeTarget = new TowerType("manual_fixture_t2", "Manual Fixture T2", TowerCategory.DIRECT, 0, 80.0, 8.0, 8.0, 20, 0);
        TowerType starterType = new TowerType(
                "manual_fixture_starter",
                "Manual Fixture Starter",
                TowerCategory.DIRECT,
                0,
                80.0,
                8.0,
                8.0,
                20,
                0
        );
        ProductionTowerCatalog.registerStarter(starterType);
        try {
            ProductionTowerCatalog.linkUpgrade(starterType, "manual_upgrade", "Manual Upgrade", upgradeTarget, 0);
        } catch (IllegalArgumentException expected) {
            context.succeed();
            return;
        }
        context.fail(Component.literal("Production catalog should reject upgrade targets that are not registered before linking."));
    }

    @GameTest
    public void towerUpgradeServiceAcceptsNonProductionEntityBackedTower(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        UUID playerId = stableUuid("red-custom-runtime-upgrade-owner");
        TowerType upgradeTarget = new TowerType("manual_fixture_custom_t2", "Manual Fixture Custom T2", TowerCategory.DIRECT, 0, 80.0, 8.0, 8.0, 20, 0);
        TowerType starterType = new TowerType(
                "manual_fixture_custom_starter",
                "Manual Fixture Custom Starter",
                TowerCategory.DIRECT,
                0,
                80.0,
                8.0,
                8.0,
                20,
                0
        );
        ProductionTowerCatalog.registerStarter(starterType, FixtureSupportTower::new);
        ProductionTowerCatalog.register(upgradeTarget, 2);
        ProductionTowerCatalog.linkUpgrade(starterType, "manual_upgrade", "Manual Upgrade", upgradeTarget, 0);
        SemionJob testJob = registerTowerAllowingJob(
                "custom_runtime_upgrade",
                Set.of(starterType.id(), upgradeTarget.id())
        );
        SemionGame game = startedSinglePlayerGame(
                context,
                playerId,
                TeamId.RED,
                testJob.id()
        );
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        kim.biryeong.semiontd.game.GridPosition gridPosition = new kim.biryeong.semiontd.game.GridPosition(
                towerPos.getX(),
                towerPos.getY(),
                towerPos.getZ()
        );
        lane.addTower(new FixtureSupportTower(
                starterType,
                playerId,
                TeamId.RED,
                1,
                gridPosition,
                gridPosition
        ));

        if (!assertEquals(
                context,
                1,
                ProductionTowerService.availableUpgrades(game, playerId, towerPos).size(),
                "Upgrade service should read upgrade options from generic Tower state, not ProductionTower runtime type."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerUpgradeResult.SUCCESS,
                ProductionTowerService.upgradeTower(game, playerId, towerPos, "manual_upgrade"),
                "Generic entity-backed towers should upgrade through catalog links."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void productionTowerRejectsUnknownUpgradeId(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        UUID playerId = stableUuid("red-production-upgrade-reject");
        SemionGame game = startedSinglePlayerGame(
                context,
                playerId,
                TeamId.RED
        );
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        TowerType targetType = new TowerType("manual_fixture_known_target", "Manual Fixture Known Target", TowerCategory.DIRECT, 0, 80.0, 8.0, 8.0, 20, 0);
        TowerType starterType = new TowerType(
                "manual_fixture_unknown_upgrade",
                "Manual Fixture Unknown Upgrade",
                TowerCategory.DIRECT,
                0,
                80.0,
                8.0,
                8.0,
                20,
                0
        );
        ProductionTowerCatalog.registerStarter(starterType);
        ProductionTowerCatalog.register(targetType, 2);
        ProductionTowerCatalog.linkUpgrade(starterType, "known_upgrade", "Known Upgrade", targetType, 0);

        lane.addTower(new ProductionTower(
                starterType,
                playerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(towerPos.getX(), towerPos.getY(), towerPos.getZ())
        ));
        if (!assertEquals(
                context,
                TowerUpgradeResult.UNKNOWN_UPGRADE,
                ProductionTowerService.upgradeTower(game, playerId, towerPos, "missing_branch"),
                "Unknown production upgrade ids should be rejected."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void productionTowerRejectsNonOwnerUpgrade(GameTestHelper context) {
        UUID playerId = stableUuid("red-production-upgrade-viewer");
        UUID ownerId = stableUuid("red-production-upgrade-real-owner");
        SemionGame game = startedSinglePlayerGame(
                context,
                playerId,
                TeamId.RED
        );
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        lane.addTower(new ProductionTower(
                productionFixtureType("manual_fixture_non_owner", List.of(new TowerUpgradeOption(
                        "manual_upgrade",
                        "Manual Upgrade",
                        new TowerType("manual_fixture_non_owner_target", "Manual Fixture Non Owner Target", TowerCategory.DIRECT, 0, 80.0, 8.0, 8.0, 20, 0),
                        0
                ))),
                ownerId,
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(towerPos.getX(), towerPos.getY(), towerPos.getZ())
        ));
        game.players().get(playerId).economy().addMineral(500);

        if (!assertEquals(
                context,
                TowerUpgradeResult.TOWER_NOT_OWNED,
                ProductionTowerService.upgradeTower(game, playerId, towerPos, "militia_net"),
                "Players should get an explicit not-owned result when upgrading another player's production tower."
        )) {
            return;
        }
        if (!assertTrue(
                context,
                ProductionTowerService.availableUpgrades(game, playerId, towerPos).isEmpty(),
                "Other players should not see upgrade options for a production tower they do not own."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void productionTowerCatalogUsesVanillaMobVisuals(GameTestHelper context) {
        if (!assertTrue(
                context,
                ProductionTowerCatalog.all().stream().noneMatch(entry ->
                        "minecraft:armor_stand".equals(entry.type().entityTypeId())
                                && !PirateTowers.isPlayerVisual(entry.type())),
                "Only intentionally skinned pirate player towers may use armor stands."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void hudScaleUsesQaMultiplier(GameTestHelper context) {
        if (!assertEquals(
                context,
                3.0F,
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
                lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity,
                "Placed tower should spawn a SemionTowerEntity."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void buyingTowerLimitAddsPlayerSpecificTowerSlots(GameTestHelper context) {
        UUID playerId = stableUuid("red-tower-limit-buyer");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        game.players().get(playerId).assignTraitLoadout(new TraitLoadout(
                BuiltInTraits.SUPPLY_DEPOT_ID,
                BuiltInTraits.NONE_ID
        ));
        PlayerEconomy economy = game.players().get(playerId).economy();
        int roundLimit = game.towerLimitForCurrentRound();
        int traitLimit = roundLimit + 4;
        long diamondCost = game.economyConfig().towerLimit().initialPurchaseDiamondCost();
        long emeraldCost = game.economyConfig().towerLimit().initialPurchaseEmeraldCost();
        economy.addGas(emeraldCost, Long.MAX_VALUE);

        if (!assertEquals(context, 9, traitLimit, "Primary Supply Depot should raise the initial tower limit from 5 to 9.")) {
            return;
        }
        if (!assertEquals(context, traitLimit, game.towerLimitForPlayer(playerId), "Supply Depot should add four tower slots.")) {
            return;
        }
        if (!assertTrue(context, game.purchaseTowerLimit(playerId), "Player should be able to buy an extra tower slot.")) {
            return;
        }
        if (!assertEquals(context, traitLimit + game.economyConfig().towerLimit().purchaseIncreaseAmount(), game.towerLimitForPlayer(playerId), "Purchased slots should stack with Supply Depot.")) {
            return;
        }
        if (!assertEquals(context, EconomyConfig.defaultConfig().startingMineral() - diamondCost, economy.mineral(), "Tower slot purchase should spend the configured diamond cost.")) {
            return;
        }
        if (!assertEquals(context, 0L, economy.gas(), "Tower slot purchase should spend the configured emerald cost.")) {
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
    public void placingTestTowerFromAirUsesGroundedLaneBlock(GameTestHelper context) {
        UUID playerId = stableUuid("red-air-placement-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos laneColumn = towerPlacementPos(lane);
        BlockPos floorPos = new BlockPos(
                laneColumn.getX(),
                lane.laneLayout().laneArea().min().getY() - 1,
                laneColumn.getZ()
        );
        BlockPos sourcePos = floorPos.above(8);
        context.getLevel().setBlock(floorPos, Blocks.STONE.defaultBlockState(), 3);

        TowerPlacementResult result = TestTowerService.placeTestTower(game, playerId, sourcePos);

        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                result,
                "Tower placement from air above lane_path should resolve to the ground block."
        )) {
            return;
        }
        TestTower tower = (TestTower) lane.towers().getFirst();
        if (!assertEquals(
                context,
                GridPosition.from(floorPos),
                tower.position(),
                "Placed tower should store the grounded block position instead of the air source."
        )) {
            return;
        }
        SemionTowerEntity entity = (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().orElseThrow());
        if (!assertEquals(
                context,
                floorPos.getY() + 1.0,
                entity.getY(),
                "Placed tower entity should stand on top of the grounded block."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void sellingTowerBeforeWaveRefundsFullCost(GameTestHelper context) {
        UUID playerId = stableUuid("red-tower-sell-full");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        long startingMineral = game.players().get(playerId).economy().mineral();
        long towerCost = TestTowerTypes.TEST_DIRECT.mineralCost();

        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                TestTowerService.placeTestTower(game, playerId, towerPos),
                "Test tower placement should succeed before full refund sale."
        )) {
            return;
        }

        ProductionTowerService.SaleResult sale = ProductionTowerService.sellTower(game, playerId, towerPos);
        if (!assertEquals(context, TowerSellResult.SUCCESS, sale.result(), "Tower sale should succeed before wave starts.")) {
            return;
        }
        if (!assertEquals(context, towerCost, sale.refundAmount(), "Tower sold before its first wave should refund the full paid cost.")) {
            return;
        }
        if (!assertEquals(context, startingMineral, game.players().get(playerId).economy().mineral(), "Full refund should restore the starting mineral balance.")) {
            return;
        }
        if (!assertTrue(context, lane.towers().isEmpty(), "Sold tower should be removed from its lane.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void sellingTowerAfterWaveStartsIsRejected(GameTestHelper context) {
        UUID redId = stableUuid("red-tower-sell-rejected");
        UUID blueId = stableUuid("blue-tower-sell-rejected");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        long towerCost = TestTowerTypes.TEST_DIRECT.mineralCost();
        if (!assertEquals(context, TowerPlacementResult.SUCCESS, TestTowerService.placeTestTower(game, redId, towerPos), "Test tower placement should succeed before rejected sale.")) {
            return;
        }
        long mineralAfterPlacement = EconomyConfig.defaultConfig().startingMineral() - towerCost;
        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS);
        if (!assertEquals(context, RoundPhase.LANE_WAVE, game.phase(), "Game should be in wave phase before rejected sale.")) {
            return;
        }
        ProductionTowerService.SaleResult sale = ProductionTowerService.sellTower(game, redId, towerPos);
        if (!assertEquals(context, TowerSellResult.INVALID_PHASE, sale.result(), "Tower sale should be rejected after wave starts.")) {
            return;
        }
        if (!assertEquals(context, 0L, sale.refundAmount(), "Rejected tower sale should not refund minerals.")) {
            return;
        }
        if (!assertEquals(context, mineralAfterPlacement, game.players().get(redId).economy().mineral(), "Rejected tower sale should not change the player's minerals.")) {
            return;
        }
        if (!assertEquals(context, 1, lane.towers().size(), "Rejected tower sale should leave the tower in the lane.")) {
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
                10L,
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

    @GameTest
    public void rangedDamageTowerUsesArrowAttackSoundCue(GameTestHelper context) {
        UUID playerId = stableUuid("red-tower-sound-owner");
        var position = new kim.biryeong.semiontd.game.GridPosition(1, 2, 3);
        TowerType rangedDamageType = new TowerType("sound_ranged", "Sound Ranged", TowerCategory.DIRECT, 0, 50.0, 8.0, 10.0, 20, 0);
        TowerType closeDamageType = new TowerType("sound_close", "Sound Close", TowerCategory.DIRECT, 0, 50.0, 2.0, 10.0, 20, 0);
        TowerType rangedSupportType = new TowerType("sound_support", "Sound Support", TowerCategory.SUPPORT, 0, 50.0, 8.0, 0.0, 20, 0);

        SemionTowerEntity ranged = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        ranged.configure(new TestTower(rangedDamageType, playerId, TeamId.RED, 1, position), null);
        if (!assertTrue(context, ranged.playsRangedAttackSound(), "Damage-dealing ranged towers should play the arrow attack sound cue.")) {
            return;
        }

        SemionTowerEntity close = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        close.configure(new TestTower(closeDamageType, playerId, TeamId.RED, 1, position), null);
        if (!assertTrue(context, !close.playsRangedAttackSound(), "Close-range towers should not use the ranged arrow sound cue.")) {
            return;
        }

        SemionTowerEntity support = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        support.configure(new TestTower(rangedSupportType, playerId, TeamId.RED, 1, position), null);
        if (!assertTrue(context, !support.playsRangedAttackSound(), "Non-damage support towers should not use the ranged arrow sound cue.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void musicLibraryReadsConfigOggDurations(GameTestHelper context) {
        try {
            Path musicDir = Files.createTempDirectory("semion-music-library");
            Files.write(musicDir.resolve("Opening Theme!.ogg"), syntheticOggVorbis(48_000, 120_000));

            SemionMusicLibrary library = SemionMusicLibrary.load(musicDir, LoggerFactory.getLogger("semion-music-test"));
            if (!assertEquals(context, 1, library.tracks().size(), "Music library should load OGG tracks from the config music directory.")) {
                return;
            }
            SemionMusicTrack track = library.tracks().getFirst();
            if (!assertEquals(context, "opening_theme", track.id(), "Music track ids should be resource-pack safe.")) {
                return;
            }
            if (!assertEquals(context, 50L, track.durationTicks(), "Music library should record OGG playback duration in ticks.")) {
                return;
            }
            if (!assertEquals(context, Identifier.fromNamespaceAndPath("semion-td", "music.opening_theme"), track.eventId(), "Music event ids should use the Semion TD namespace.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Music library test setup failed: " + exception.getMessage()));
        }
    }

    @GameTest
    public void musicResourcePackInjectsOggAssetsAndSoundsJson(GameTestHelper context) {
        try {
            Path musicDir = Files.createTempDirectory("semion-music-pack");
            Files.write(musicDir.resolve("Round One.ogg"), syntheticOggVorbis(44_100, 88_200));
            SemionMusicLibrary library = SemionMusicLibrary.load(musicDir, LoggerFactory.getLogger("semion-music-test"));
            CapturingResourcePackBuilder builder = new CapturingResourcePackBuilder();

            SemionMusicResourcePack.addToResourcePack(library, builder, LoggerFactory.getLogger("semion-music-test"));

            if (!assertTrue(
                    context,
                    builder.data().containsKey("assets/semion-td/sounds/music/round_one.ogg"),
                    "Music resource pack hook should copy config OGG files into generated sound assets."
            )) {
                return;
            }
            String soundsJson = builder.getStringData("assets/semion-td/sounds.json");
            if (!assertTrue(context, soundsJson != null && soundsJson.contains("\"music.round_one\""), "Music resource pack hook should register the sound event.")) {
                return;
            }
            if (!assertTrue(context, soundsJson.contains("\"name\": \"semion-td:music/round_one\""), "sounds.json should point at the copied sound file.")) {
                return;
            }
            if (!assertTrue(context, soundsJson.contains("\"stream\": true"), "Music sounds should be streamed by the client.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Music resource pack test setup failed: " + exception.getMessage()));
        }
    }

    @GameTest
    public void musicPlaybackTimerAvoidsMidTrackRestarts(GameTestHelper context) {
        Path fakeSource = Path.of("music.ogg");
        SemionMusicTrack first = new SemionMusicTrack(
                "first",
                fakeSource,
                Identifier.fromNamespaceAndPath("semion-td", "music.first"),
                Identifier.fromNamespaceAndPath("semion-td", "music/first"),
                40L
        );
        SemionMusicTrack second = new SemionMusicTrack(
                "second",
                fakeSource,
                Identifier.fromNamespaceAndPath("semion-td", "music.second"),
                Identifier.fromNamespaceAndPath("semion-td", "music/second"),
                60L
        );
        SemionMusicTrack third = new SemionMusicTrack(
                "third",
                fakeSource,
                Identifier.fromNamespaceAndPath("semion-td", "music.third"),
                Identifier.fromNamespaceAndPath("semion-td", "music/third"),
                50L
        );
        SemionMusicService service = new SemionMusicService(new SemionMusicLibrary(List.of(first, second, third)), () -> 100L, bound -> 1);
        UUID playerId = stableUuid("music-player");

        SemionMusicService.PlaybackDecision initial = service.decisionFor(playerId, 0L, true);
        if (!assertEquals(context, SemionMusicService.PlaybackAction.START_TRACK, initial.action(), "Music should start when a player's client is at the beginning of a track.")) {
            return;
        }
        SemionMusicService.PlaybackDecision midTrackReconnect = service.decisionFor(playerId, 25L, true);
        if (!assertEquals(context, SemionMusicService.PlaybackAction.WAIT_FOR_NEXT_TRACK, midTrackReconnect.action(), "Reconnects or world changes mid-track should wait for the next track instead of restarting from an impossible offset.")) {
            return;
        }
        SemionMusicService.PlaybackDecision interTrackGap = service.decisionFor(playerId, 40L, true);
        if (!assertEquals(context, SemionMusicService.PlaybackAction.WAIT_FOR_NEXT_TRACK, interTrackGap.action(), "Music should keep a silent gap after a track ends.")) {
            return;
        }
        if (!assertTrue(context, interTrackGap.track() == null, "Inter-track music gaps should not select a sound event.")) {
            return;
        }
        SemionMusicService.PlaybackDecision nextTrack = service.decisionFor(playerId, 140L, true);
        if (!assertEquals(context, SemionMusicService.PlaybackAction.START_TRACK, nextTrack.action(), "Stopped clients should resume when the next track boundary arrives.")) {
            return;
        }
        if (!assertEquals(context, third.eventId(), nextTrack.track().eventId(), "The next music boundary should choose an unplayed randomized track instead of always advancing sequentially.")) {
            return;
        }
        SemionMusicService.PlaybackDecision lastUnplayedTrack = service.decisionFor(playerId, 290L, true);
        if (!assertEquals(context, SemionMusicService.PlaybackAction.START_TRACK, lastUnplayedTrack.action(), "Music should keep picking unplayed tracks before repeating the playlist.")) {
            return;
        }
        if (!assertEquals(context, second.eventId(), lastUnplayedTrack.track().eventId(), "Music should play every configured track once before any track repeats.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void musicInterTrackGapIsRandomizedWithinFiveToTenSeconds(GameTestHelper context) {
        for (int attempt = 0; attempt < 100; attempt++) {
            long gapTicks = SemionMusicService.randomInterTrackGapTicks();
            if (!assertTrue(
                    context,
                    gapTicks >= SemionMusicService.MIN_INTER_TRACK_GAP_TICKS
                            && gapTicks <= SemionMusicService.MAX_INTER_TRACK_GAP_TICKS,
                    "Music inter-track gaps should stay between five and ten seconds."
            )) {
                return;
            }
        }
        context.succeed();
    }

    @GameTest(maxTicks = 100)
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

    @GameTest(maxTicks = 60)
    public void towerEntityRetargetsNearbyMonsterBeforeFarProgressTarget(GameTestHelper context) {
        UUID playerId = stableUuid("red-tower-range-priority-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        TowerType towerType = new TowerType("range_priority_test", "Range Priority Test", TowerCategory.DIRECT, 0, 50.0, 3.5, 10.0, 100, 0);
        lane.addTower(new TestTower(towerType, playerId, TeamId.RED, 1, GridPosition.from(towerPos)));

        TestTower tower = (TestTower) lane.towers().getFirst();
        if (!assertTrue(context, tower.entityId().isPresent(), "Range priority tower entity should exist.")) {
            return;
        }

        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().getAsInt());
        Vec3 towerPosition = towerEntity.position();
        SemionMonsterEntity farProgressTarget = spawnRoleMonsterEntity(
                context,
                "far-progress-target",
                Optional.empty(),
                TeamId.RED,
                1,
                towerPosition.add(8.0, 0.0, 0.0),
                40.0,
                List.of(SummonRole.SIEGE)
        );
        farProgressTarget.runtimeMonster().syncLaneProgress(0.95);
        farProgressTarget.setNoAi(true);

        towerEntity.setNoAi(true);
        TowerAttackMonsterGoal targetingGoal = new TowerAttackMonsterGoal(towerEntity);
        targetingGoal.tick();
        if (!assertTrue(
                context,
                towerEntity.currentAttackTarget() == farProgressTarget,
                "Tower should initially advance toward the only available monster."
        )) {
            return;
        }

        SemionMonsterEntity nearTarget = spawnRoleMonsterEntity(
                context,
                "near-range-target",
                Optional.empty(),
                TeamId.RED,
                1,
                towerEntity.position().add(2.0, 0.0, 0.0),
                40.0,
                List.of(SummonRole.RUSH)
        );
        nearTarget.runtimeMonster().syncLaneProgress(0.1);
        nearTarget.setNoAi(true);

        for (int tick = 0; tick <= 5; tick++) {targetingGoal.tick();}
        if (!assertTrue(
                context,
                towerEntity.currentAttackTarget() == nearTarget,
                "Tower should leave a farther high-priority target for a monster inside encounter range."
        )) {
            return;
        }
        if (!assertTrue(context, nearTarget.getHealth() < 40.0F, "Tower should attack the nearby monster after retargeting.")) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 80)
    public void defaultTowerTargetingSplitsEqualPriorityTargets(GameTestHelper context) {
        UUID playerId = stableUuid("red-target-jitter-owner");
        int testLaneId = 1137;
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        TowerType towerType = new TowerType("target_jitter_test", "Target Jitter Test", TowerCategory.DIRECT, 0, 50.0, 8.0, 0.0, 100, 0);
        List<SemionMonsterEntity> monsters = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            SemionMonsterEntity monster = spawnRoleMonsterEntity(
                    context,
                    "jitter-monster-" + index,
                    Optional.empty(),
                    TeamId.RED,
                    testLaneId,
                    origin.add(index * 1.5, 0.0, 4.0),
                    100.0,
                    List.of(SummonRole.RUSH)
            );
            monster.setUUID(stableUuid("jitter-monster-" + index));
            monster.runtimeMonster().syncLaneProgress(0.5);
            monster.setNoAi(true);
            monsters.add(monster);
        }

        List<SemionTowerEntity> towers = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            Vec3 position = origin.add(index * 1.5, 0.0, 0.0);
            SemionTowerEntity tower = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
            tower.configure(new TestTower(towerType, playerId, TeamId.RED, testLaneId, GridPosition.from(BlockPos.containing(position))), null);
            tower.setUUID(stableUuid("jitter-tower-" + index));
            tower.setPos(position);
            context.getLevel().addFreshEntity(tower);
            towers.add(tower);
        }

        context.runAfterDelay(20, () -> {
            Set<SemionMonsterEntity> selectedTargets = towers.stream()
                    .map(SemionTowerEntity::currentAttackTarget)
                    .filter(target -> target != null)
                    .collect(Collectors.toSet());
            if (!assertTrue(context, selectedTargets.size() >= 2, "Equal-priority targets should be split across default towers.")) {
                return;
            }
            if (!assertTrue(context, monsters.containsAll(selectedTargets), "Default towers should only select valid equal-priority candidates.")) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void defaultTowerPrioritizesHigherThreatInsideEncounterRange(GameTestHelper context) {
        UUID playerId = stableUuid("red-target-lock-owner");
        int testLaneId = 101;
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        TowerType towerType = new TowerType("target_lock_test", "Target Lock Test", TowerCategory.DIRECT, 0, 50.0, 3.5, 0.0, 100, 0);
        lane.addTower(new TestTower(towerType, playerId, TeamId.RED, testLaneId, GridPosition.from(towerPlacementPos(lane))));
        TestTower tower = (TestTower) lane.towers().getFirst();
        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().orElseThrow());
        Vec3 towerPosition = towerEntity.position();
        SemionMonsterEntity farPriorityTarget = spawnRoleMonsterEntity(
                context,
                "target-lock-far-priority",
                Optional.empty(),
                TeamId.RED,
                testLaneId,
                towerPosition.add(8.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.SIEGE)
        );
        farPriorityTarget.setNoAi(true);
        farPriorityTarget.runtimeMonster().syncLaneProgress(0.95);
        SemionMonsterEntity lowerPriorityTarget = spawnRoleMonsterEntity(
                context,
                "target-lock-lower-priority",
                Optional.empty(),
                TeamId.RED,
                testLaneId,
                towerPosition.add(2.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.RUSH)
        );
        lowerPriorityTarget.setNoAi(true);
        lowerPriorityTarget.runtimeMonster().syncLaneProgress(0.1);
        SemionMonsterEntity higherPriorityTarget = spawnRoleMonsterEntity(
                context,
                "target-lock-higher-priority",
                Optional.empty(),
                TeamId.RED,
                testLaneId,
                towerPosition.add(3.0, 0.0, 0.0),
                100.0,
                List.of(SummonRole.SIEGE)
        );
        higherPriorityTarget.setNoAi(true);
        higherPriorityTarget.runtimeMonster().syncLaneProgress(0.95);

        context.runAfterDelay(10, () -> {
            if (!assertTrue(context, towerEntity.currentAttackTarget() == higherPriorityTarget, "Tower should select the higher-priority target among enemies inside encounter range.")) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void towerDamageAppliesTraitsTargetModifiersArmorAndSingleReward(GameTestHelper context) {
        UUID playerId = stableUuid("runtime-damage-owner");
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO)).add(2.0, 2.0, 2.0);
        TestTower tower = new TestTower(
                TestTowerTypes.TEST_DIRECT,
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(BlockPos.containing(origin))
        );
        SemionTowerEntity towerEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        towerEntity.configure(tower, null);
        towerEntity.setPos(origin);
        towerEntity.applyTimedEffect(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS, 0.20, 40);
        context.getLevel().addFreshEntity(towerEntity);

        Monster runtimeMonster = new Monster(
                "runtime-damage-target",
                TeamId.RED,
                1,
                Optional.empty(),
                Optional.empty(),
                100.0,
                20.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                10
        );
        SemionMonsterEntity target = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        target.configureFrom(runtimeMonster, null);
        target.setPos(origin.add(2.0, 0.0, 0.0));
        target.setNoAi(true);
        target.applyTimedEffect(TimedEffectType.MONSTER_DAMAGE_REDUCTION, 0.25, 40);
        context.getLevel().addFreshEntity(target);

        boolean firstHitKilled = tower.damageTarget(towerEntity, target, 100.0);
        if (!assertTrue(context, !firstHitKilled, "First hit should leave the armored target alive.")) {
            return;
        }
        if (!assertClose(context, 25.0, runtimeMonster.health(), "Outgoing bonus, target reduction, then armor should produce 75 damage.")) {
            return;
        }
        if (!assertClose(context, runtimeMonster.health(), target.getHealth(), "Runtime and entity health should stay synchronized.")) {
            return;
        }
        if (!assertTrue(context, runtimeMonster.lastHitPlayerId().filter(playerId::equals).isPresent(), "Tower owner should be recorded as the last hitter.")) {
            return;
        }
        if (!assertTrue(context, runtimeMonster.lastHitSourceKind() == KillSourceKind.TOWER, "Tower damage should record the tower kill source.")) {
            return;
        }

        if (!assertTrue(context, tower.damageTarget(towerEntity, target, 40.0), "Second hit should kill the target.")) {
            return;
        }
        if (!assertClose(context, 100.0, tower.roundDamageDealt(), "Tower damage stats should count actual health removed and exclude overkill.")) {
            return;
        }
        SemionPlayer player = new SemionPlayer(
                playerId,
                "runtime-damage-owner",
                TeamId.RED,
                1,
                new PlayerEconomy(EconomyConfig.defaultConfig())
        );
        EconomyService economyService = new EconomyService(EconomyConfig.defaultConfig());
        economyService.awardMonsterKillReward(runtimeMonster, Map.of(playerId, player));
        economyService.awardMonsterKillReward(runtimeMonster, Map.of(playerId, player));
        if (!assertEquals(context, 160L, player.economy().diamond(), "Monster reward should be granted exactly once.")) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void igniteTicksFourTimesAndKeepsTheStrongestCadence(GameTestHelper context) {
        UUID playerId = stableUuid("ignite-trait-owner");
        GridPosition sourcePosition = GridPosition.from(context.absolutePos(BlockPos.ZERO));
        TestTower sourceTower = new TestTower(TestTowerTypes.TEST_DIRECT, playerId, TeamId.RED, 1, sourcePosition);
        sourceTower.attachToLane(null, new TraitLoadout(BuiltInTraits.IGNITE_ID, BuiltInTraits.NONE_ID));
        sourceTower.markWaveStarted(10);
        SemionTowerEntity sourceEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        sourceEntity.configure(sourceTower, null);
        double igniteDamage = TraitEffects.igniteDamagePerTick(sourceTower.traitLoadout(), 100.0, 10);
        double strongerIgniteDamage = TraitEffects.igniteDamagePerTick(sourceTower.traitLoadout(), 200.0, 10);

        SemionMonsterEntity fourTickTarget = spawnRoleMonsterEntity(
                context,
                "ignite-four-tick-target",
                Optional.empty(),
                TeamId.RED,
                1,
                Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO.east(3))),
                1_000.0,
                List.of(SummonRole.SIEGE)
        );
        fourTickTarget.setNoAi(true);
        sourceEntity.recordAttack(fourTickTarget, 100.0, false);
        for (int tick = 0; tick < 80; tick++) {
            fourTickTarget.aiStep();
        }
        if (!assertClose(context, 1_000.0 - igniteDamage * 4, fourTickTarget.runtimeMonster().health(),
                "Ignite should deal exactly four configured damage ticks.")) {
            return;
        }
        if (!assertTrue(context, playerId.equals(fourTickTarget.runtimeMonster().lastHitPlayerId().orElse(null)), "Ignite should preserve owner attribution.")) {
            return;
        }

        SemionMonsterEntity refreshTarget = spawnRoleMonsterEntity(
                context,
                "ignite-refresh-target",
                Optional.empty(),
                TeamId.RED,
                1,
                Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO.east(5))),
                1_000.0,
                List.of(SummonRole.SIEGE)
        );
        refreshTarget.setNoAi(true);
        sourceEntity.recordAttack(refreshTarget, 100.0, false);
        for (int tick = 0; tick < 19; tick++) {
            refreshTarget.aiStep();
        }
        sourceEntity.recordAttack(refreshTarget, 50.0, false);
        refreshTarget.aiStep();
        if (!assertClose(context, 1_000.0 - igniteDamage, refreshTarget.runtimeMonster().health(),
                "A weaker refresh should keep the stronger damage and original tick cadence.")) {
            return;
        }
        for (int tick = 0; tick < 10; tick++) {
            refreshTarget.aiStep();
        }
        sourceEntity.recordAttack(refreshTarget, 200.0, false);
        for (int tick = 0; tick < 9; tick++) {
            refreshTarget.aiStep();
        }
        if (!assertClose(context, 1_000.0 - igniteDamage, refreshTarget.runtimeMonster().health(),
                "A stronger refresh must not reset the pending tick.")) {
            return;
        }
        refreshTarget.aiStep();
        if (!assertClose(context, 1_000.0 - igniteDamage - strongerIgniteDamage,
                refreshTarget.runtimeMonster().health(), "The next scheduled tick should use the stronger configured damage.")) {
            return;
        }

        SemionMonsterEntity killTarget = spawnRoleMonsterEntity(
                context,
                "ignite-kill-target",
                Optional.empty(),
                TeamId.RED,
                1,
                Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO.east(7))),
                20.0,
                List.of(SummonRole.SIEGE)
        );
        killTarget.setNoAi(true);
        sourceEntity.recordAttack(killTarget, 100.0, false);
        for (int tick = 0; tick < 60 && killTarget.isAlive(); tick++) {
            killTarget.aiStep();
        }
        if (!assertTrue(context, !killTarget.isAlive(), "Ignite should be able to kill after the source tower is absent from the world.")) {
            return;
        }
        if (!assertTrue(context, killTarget.runtimeMonster().lastHitSourceKind() == KillSourceKind.TOWER, "Ignite kills should keep tower kill attribution.")) {
            return;
        }
        if (!assertClose(context, igniteDamage * 5 + strongerIgniteDamage + 20.0,
                sourceTower.roundMagicDamageDealt(),
                "Ignite ticks should count as magic damage for the tower that applied the retained ignite.")) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 40)
    public void igniteAppliesFromBasicAttackSplashAndAppearsInDamageSidebar(GameTestHelper context) {
        UUID playerId = stableUuid("ignite-splash-sidebar-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        lane.assignTraitLoadout(new TraitLoadout(BuiltInTraits.IGNITE_ID, BuiltInTraits.NONE_ID));
        TestTower sourceTower = new TestTower(
                TestTowerTypes.TEST_DIRECT,
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(towerPlacementPos(lane))
        );
        lane.addTower(sourceTower);
        sourceTower.markWaveStarted(10);
        SemionTowerEntity sourceEntity = (SemionTowerEntity) lane.arenaWorld()
                .getEntity(sourceTower.entityId().orElseThrow());
        SemionMonsterEntity splashTarget = spawnRoleMonsterEntity(
                context,
                "ignite-splash-target",
                Optional.empty(),
                TeamId.RED,
                1,
                sourceEntity.position().add(3.0, 0.0, 0.0),
                1_000.0,
                List.of(SummonRole.SIEGE)
        );
        splashTarget.setNoAi(true);

        sourceEntity.damageBasicAttackSecondaryTargetResult(splashTarget, 50.0);
        if (!assertTrue(context, splashTarget.activeTimedEffectTicks(TimedEffectType.MONSTER_IGNITED) > 0,
                "Basic-attack splash damage should apply ignite to its secondary target.")) {
            return;
        }
        for (int tick = 0; tick < 20; tick++) {
            splashTarget.aiStep();
        }
        double igniteDamage = TraitEffects.igniteDamagePerTick(sourceTower.traitLoadout(), 50.0, 10);
        if (!assertClose(context, 50.0, sourceTower.roundPhysicalDamageDealt(),
                "Basic-attack splash should remain physical damage.")) {
            return;
        }
        if (!assertClose(context, igniteDamage, sourceTower.roundMagicDamageDealt(),
                "Ignite should merge into the source tower's magic damage.")) {
            return;
        }
        String markup = SemionHudTextService.damageSidebarMarkupFor(playerId, game);
        long roundedIgniteDamage = Math.round(igniteDamage);
        if (!assertTrue(context, markup.contains("<#ec8d34>🪓 50</#ec8d34> <dark_gray>|</dark_gray> <#796CFF>🔥 "
                        + roundedIgniteDamage + "</#796CFF> <dark_gray>|</dark_gray> <aqua>🛡 0</aqua>"),
                "Damage sidebar should show physical and magic totals without a separate ignite subtotal.")) {
            return;
        }
        if (!assertTrue(context, markup.contains("Test Direct Tower</white> <#ec8d34>🪓 50</#ec8d34> <#796CFF>🔥 "
                        + roundedIgniteDamage + "</#796CFF>"),
                "Tower damage ranking should split physical and magic damage.")) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120)
    public void testTowerMovesTowardOutOfRangeMonster(GameTestHelper context) {
        UUID playerId = stableUuid("red-tower-anchor-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        TowerType lowRangeType = new TowerType("anchor_test", "Anchor Test", TowerCategory.DIRECT, 0, 50.0, 1.0, 12.0, 20, 0);
        lane.addTower(new TestTower(lowRangeType, playerId, TeamId.RED, 1, new kim.biryeong.semiontd.game.GridPosition(
                towerPos.getX(),
                towerPos.getY(),
                towerPos.getZ()
        )));

        TestTower tower = (TestTower) lane.towers().getFirst();
        if (!assertTrue(context, tower.entityId().isPresent(), "Tower entity should still exist.")) {
            return;
        }
        if (!(lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity towerEntity)) {
            context.fail(Component.literal("Tower entity should still be present in the arena world."));
            return;
        }

        Vec3 initialTowerPosition = towerEntity.position();
        SemionMonsterEntity monsterEntity = spawnRoleMonsterEntity(
                context,
                "tower-move-target",
                Optional.empty(),
                TeamId.RED,
                1,
                initialTowerPosition.add(8.0, 0.0, 0.0),
                100000.0,
                List.of(SummonRole.RUSH)
        );
        monsterEntity.setNoAi(true);
        double initialDistance = initialTowerPosition.distanceTo(monsterEntity.position());

        context.runAfterDelay(40, () -> {
            if (!assertTrue(context, monsterEntity.isAlive() && !monsterEntity.isRemoved(), "Anchor test monster entity should still exist.")) {
                return;
            }
            if (!(lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity currentTowerEntity)) {
                context.fail(Component.literal("Tower entity should still be present in the arena world."));
                return;
            }
            Vec3 currentTowerPos = currentTowerEntity.position();
            if (!assertTrue(
                    context,
                    currentTowerPos.distanceTo(initialTowerPosition) > 0.1,
                    "Tower entity should move away from its initial position toward a live target that starts out of range."
            )) {
                return;
            }
            if (!assertTrue(
                    context,
                    currentTowerPos.distanceTo(monsterEntity.position()) < initialDistance,
                    "Tower entity should get closer to the out-of-range target."
            )) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = 100)
    public void finalDefenseTowerDoesNotChaseOutOfRangeMonster(GameTestHelper context) {
        UUID playerId = stableUuid("red-final-defense-anchor-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.PURPLE);
        PlayerLane lane = lane(game, TeamId.PURPLE, 1);
        TowerType shortRangeType = new TowerType(
                "final_defense_short_range",
                "Final Defense Short Range",
                TowerCategory.DIRECT,
                0,
                50.0,
                3.0,
                0.0,
                20,
                0
        );
        lane.addTower(new TestTower(
                shortRangeType,
                playerId,
                TeamId.PURPLE,
                1,
                GridPosition.from(towerPlacementPos(lane))
        ));

        GridPosition finalDefenseSlot = lane.laneLayout().finalDefenseTowerSlots().getFirst();
        BlockPos finalDefenseAirPos = new BlockPos(finalDefenseSlot.x(), finalDefenseSlot.y(), finalDefenseSlot.z());
        context.getLevel().setBlock(finalDefenseAirPos.below(), Blocks.STONE.defaultBlockState(), 3);
        context.getLevel().setBlock(finalDefenseAirPos, Blocks.AIR.defaultBlockState(), 3);

        game.teams().get(TeamId.PURPLE).resetForRound();
        game.teams().get(TeamId.PURPLE).tick(context.getLevel().getServer());

        TestTower tower = (TestTower) lane.towers().getFirst();
        if (!assertTrue(context, tower.deployedAtFinalDefense(), "Tower should be deployed at final defense before chase validation.")) {
            return;
        }
        if (!assertTrue(context, tower.entityId().isPresent(), "Final defense tower entity should exist before chase validation.")) {
            return;
        }
        if (!(lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity towerEntity)) {
            context.fail(Component.literal("Final defense tower entity should be available."));
            return;
        }

        Vec3 initialTowerPosition = towerEntity.position();
        SemionMonsterEntity outOfRangeTarget = spawnRoleMonsterEntity(
                context,
                "final-defense-anchor-target",
                Optional.empty(),
                TeamId.PURPLE,
                1,
                initialTowerPosition.add(7.5, 0.0, 0.0),
                100000.0,
                List.of(SummonRole.RUSH)
        );
        outOfRangeTarget.setNoAi(true);
        outOfRangeTarget.runtimeMonster().syncLaneProgress(1.0);

        context.runAfterDelay(40, () -> {
            if (!(lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity currentTowerEntity)) {
                context.fail(Component.literal("Final defense tower entity should still be available."));
                return;
            }

            Vec3 currentTowerPosition = currentTowerEntity.position();
            double horizontalMovement = Math.hypot(
                    currentTowerPosition.x - initialTowerPosition.x,
                    currentTowerPosition.z - initialTowerPosition.z
            );
            if (!assertTrue(
                    context,
                    horizontalMovement < 0.05,
                    "Final defense tower should not chase targets outside its attack range."
            )) {
                return;
            }
            if (!assertTrue(
                    context,
                    lane.laneLayout().isInsideFinalDefenseTowerArea(currentTowerPosition),
                    "Final defense tower should stay inside the final defense tower area."
            )) {
                return;
            }
            if (!assertTrue(
                    context,
                    currentTowerEntity.currentAttackTarget() == null,
                    "Final defense tower should ignore monsters farther than seven blocks away."
            )) {
                return;
            }

            currentTowerEntity.setNoAi(true);
            TowerAttackMonsterGoal targetingGoal = new TowerAttackMonsterGoal(currentTowerEntity);
            outOfRangeTarget.setPos(initialTowerPosition.add(6.0, 0.0, 0.0));
            targetingGoal.tick();
            if (!assertTrue(
                    context,
                    currentTowerEntity.currentAttackTarget() == outOfRangeTarget,
                    "Final defense tower should acquire a monster inside seven blocks."
            )) {
                return;
            }

            SemionMonsterEntity attackableTarget = spawnRoleMonsterEntity(
                    context,
                    "final-defense-attackable-target",
                    Optional.empty(),
                    TeamId.PURPLE,
                    1,
                    initialTowerPosition.add(2.0, 0.0, 0.0),
                    100000.0,
                    List.of(SummonRole.RUSH)
            );
            attackableTarget.setNoAi(true);
            attackableTarget.runtimeMonster().syncLaneProgress(1.0);
            targetingGoal.tick();
            if (!assertTrue(
                    context,
                    currentTowerEntity.currentAttackTarget() == attackableTarget,
                    "An attackable target should replace a cached target outside attack range."
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
                2.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                1
        ));
        lane.tick(context.getLevel().getServer());

        context.runAfterDelay(120, () -> {
            if (!assertTrue(
                    context,
                    lane.arenaWorld().getEntity(towerEntityId) instanceof SemionTowerEntity,
                    "Tower entity should still exist while checking retaliation."
            )) {
                return;
            }

            SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(towerEntityId);
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
                    lane.arenaWorld().getEntity(highPriorityTower.entityId().getAsInt()) instanceof SemionTowerEntity,
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

        GridPosition finalDefenseSlot = lane.laneLayout().finalDefenseTowerSlots().getFirst();
        BlockPos finalDefenseAirPos = new BlockPos(
                finalDefenseSlot.x(),
                finalDefenseSlot.y(),
                finalDefenseSlot.z()
        );
        BlockPos finalDefenseFloorPos = finalDefenseAirPos.below();
        context.getLevel().setBlock(finalDefenseFloorPos, Blocks.STONE.defaultBlockState(), 3);
        context.getLevel().setBlock(finalDefenseAirPos, Blocks.AIR.defaultBlockState(), 3);

        game.teams().get(TeamId.RED).resetForRound();
        game.teams().get(TeamId.RED).tick(context.getLevel().getServer());

        TestTower tower = (TestTower) lane.towers().getFirst();
        if (!assertTrue(context, tower.deployedAtFinalDefense(), "Tower should be marked as deployed at final defense.")) {
            return;
        }
        if (!assertEquals(
                context,
                finalDefenseFloorPos,
                BlockPos.containing(tower.position().x(), tower.position().y(), tower.position().z()),
                "Tower runtime position should use the final defense floor block when the slot itself is air."
        )) {
            return;
        }
        if (!assertTrue(context, tower.entityId().isPresent(), "Final defense tower entity should exist.")) {
            return;
        }
        if (!(lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity towerEntity)) {
            context.fail(Component.literal("Final defense tower entity should be available."));
            return;
        }
        if (!assertEquals(
                context,
                finalDefenseAirPos.getY(),
                BlockPos.containing(towerEntity.position()).getY(),
                "Tower entity should stand at the final defense slot height instead of one block above it."
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
        if (!(lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity towerEntity)) {
            context.fail(Component.literal("Tower entity should be available before reset validation."));
            return;
        }
        int originalEntityId = tower.entityId().getAsInt();
        lane.moveTowersToFinalDefense();
        if (!assertTrue(context, tower.deployedAtFinalDefense(), "Tower should enter final defense before reset validation.")) {
            return;
        }
        towerEntity.getMoveControl().setWantedPosition(towerEntity.getX() + 4.0, towerEntity.getY(), towerEntity.getZ(), 1.0);

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
        if (!assertTrue(context, tower.entityId().isPresent(), "Live tower should retain a tower entity on round reset.")) {
            return;
        }
        if (!assertEquals(context, originalEntityId, tower.entityId().getAsInt(), "Live tower reset should keep the existing tower entity id.")) {
            return;
        }
        if (!assertTrue(
                context,
                lane.arenaWorld().getEntity(tower.entityId().getAsInt()) instanceof SemionTowerEntity resetEntity
                        && resetEntity.isAlive(),
                "Reset tower entity should exist and be alive after round reset."
        )) {
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
        SemionTowerEntity resetEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().getAsInt());
        context.runAfterDelay(1, () -> {
            if (!assertClose(context, originalPosition.getX() + 0.5, resetEntity.getX(),
                    "Round reset should clear stale movement and keep the tower hitbox on its visual X anchor.")) {
                return;
            }
            if (!assertClose(context, originalPosition.getY() + 1.0, resetEntity.getY(),
                    "Round reset should keep the tower hitbox on its visual Y anchor.")) {
                return;
            }
            if (!assertClose(context, originalPosition.getZ() + 0.5, resetEntity.getZ(),
                    "Round reset should clear stale movement and keep the tower hitbox on its visual Z anchor.")) {
                return;
            }
            context.succeed();
        });
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
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));

        SemionTowerEntity tower = spawnTowerEntity(context, TeamId.RED, 1, origin, TestTowerTypes.TEST_DIRECT);
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
    public void towerDamageTakenStatsUseMitigatedHealthLossAndExcludeOverkill(GameTestHelper context) {
        UUID playerId = stableUuid("tower-damage-taken-owner");
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        TowerType type = new TowerType(
                "tower-damage-taken",
                "Tower Damage Taken",
                TowerCategory.DIRECT,
                0,
                100.0,
                5.0,
                0.0,
                20,
                0
        );
        TestTower runtimeTower = new TestTower(type, playerId, TeamId.RED, 1, GridPosition.from(BlockPos.containing(origin)));
        runtimeTower.markWaveStarted(1);
        SemionTowerEntity towerEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        towerEntity.configure(runtimeTower, null);
        towerEntity.setPos(origin);
        towerEntity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION, 0.25, 40);
        context.getLevel().addFreshEntity(towerEntity);
        SemionMonsterEntity source = spawnSummonEntity(
                context,
                "tower-damage-source",
                TeamId.BLUE,
                TeamId.RED,
                1,
                origin.add(2.0, 0.0, 0.0),
                100.0,
                80.0
        );

        context.hurt(towerEntity, source.damageSources().mobAttack(source), 80.0F);
        if (!assertClose(context, 40.0, runtimeTower.health(), "Damage reduction should leave the tower at 40 health.")) {
            return;
        }
        if (!assertClose(context, 60.0, runtimeTower.roundDamageTaken(), "Taken stats should record mitigated health loss.")) {
            return;
        }

        context.hurt(towerEntity, source.damageSources().mobAttack(source), 1_000.0F);
        if (!assertClose(context, 100.0, runtimeTower.roundDamageTaken(), "Lethal overkill should only count the tower's remaining health.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void activePlayersAndSpectatorsAreCombatProtected(GameTestHelper context) {
        UUID activeId = stableUuid("protected-active");
        UUID spectatorId = stableUuid("protected-spectator");
        UUID outsiderId = stableUuid("protected-outsider");
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(new AssignedParticipant(activeId, "protected-active", TeamId.RED, 1)),
                Set.of(spectatorId),
                1
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Protection test game should start.")) {
            return;
        }
        if (!assertTrue(context, SemionPlayerProtectionService.shouldProtectPlayer(game, activeId), "Active players should be protected from combat.")) {
            return;
        }
        if (!assertTrue(context, SemionPlayerProtectionService.shouldProtectPlayer(game, spectatorId), "Match spectators should be protected from combat.")) {
            return;
        }
        if (!assertTrue(context, !SemionPlayerProtectionService.shouldProtectPlayer(game, outsiderId), "Players outside the active match should not be protected by match rules.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void monsterTowerTargetSearchUsesFiveBlockPadding(GameTestHelper context) {
        if (!assertEquals(context, 5.0, SemionMonsterEntity.DEFENSE_SEARCH_HORIZONTAL_PADDING, "Monster tower target search should use five-block horizontal padding.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void laneMonsterDropsDistantDefenseTargetAndResumesPath(GameTestHelper context) {
        UUID playerId = stableUuid("monster-leash-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        Vec3 monsterPosition = lane.laneLayout().positionAt(0.45);
        SemionMonsterEntity monster = spawnLaneMonsterEntity(
                context,
                lane,
                "leash-check",
                TeamId.RED,
                1,
                monsterPosition
        );
        SemionTowerEntity distantTower = spawnTowerEntity(
                context,
                TeamId.RED,
                1,
                monsterPosition.add(SemionMonsterEntity.DEFENSE_TARGET_LEASH_RANGE + 2.0, 0.0, 0.0),
                TestTowerTypes.TEST_DIRECT
        );

        monster.setTarget(distantTower);
        new MonsterAttackTargetGoal(monster, 1.1).tick();
        if (!assertTrue(context, monster.getTarget() == null, "Monsters should drop defense targets outside the leash range.")) {
            return;
        }
        if (!assertTrue(context, monster.nextPathPointIndex() > 0, "A monster that drops a target mid-lane should resume from the current path progress.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void bossEntityStaysAnchoredAndPullsRangedMonsters(GameTestHelper context) {
        Vec3 anchor = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO)).add(4.0, 24.0, 4.0);
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
    public void longRangeMonsterClosesIntoFinalDefenseEngagementRange(GameTestHelper context) {
        Vec3 anchor = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO)).add(4.0, 2.0, 4.0);
        SemionBossEntity boss = new SemionBossEntity(SemionEntityTypes.BOSS, context.getLevel());
        boss.configure(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        boss.setPos(anchor);
        boss.setAnchorPosition(anchor);
        boss.setNoAi(true);
        context.getLevel().addFreshEntity(boss);

        WaveMonsterEntry artillery = new WaveMonsterEntry(
                "final-defense-artillery",
                100.0,
                0.0,
                4.0,
                AttackKind.RANGED,
                "minecraft:pillager",
                null,
                MonsterDimensions.DEFAULT,
                0,
                1,
                0.0,
                1.0,
                11.0,
                24
        );
        Monster runtimeMonster = Monster.fromWaveEntry(artillery, TeamId.RED, 1);
        runtimeMonster.enterFinalDefenseCombat();
        SemionMonsterEntity rangedEntity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        rangedEntity.configureFrom(runtimeMonster, null);
        rangedEntity.setPos(anchor.add(8.0, 0.0, 0.0));
        rangedEntity.setTarget(boss);
        context.getLevel().addFreshEntity(rangedEntity);

        new MonsterAttackTargetGoal(rangedEntity, 1.1).tick();
        if (!assertTrue(context, rangedEntity.getMoveControl().hasWanted(), "Long-range monsters should enter the movement branch outside final-defense range.")) {
            return;
        }
        if (!assertEquals(context, boss.getX(), rangedEntity.getMoveControl().getWantedX(), "Long-range monsters should move toward the boss X position.")) {
            return;
        }
        if (!assertEquals(context, boss.getZ(), rangedEntity.getMoveControl().getWantedZ(), "Long-range monsters should move toward the boss Z position.")) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 40)
    public void bossAttackDamagesNearbyMonstersWithSplash(GameTestHelper context) {
        Vec3 anchor = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO)).add(4.0, 2.0, 4.0);
        SemionBossEntity boss = new SemionBossEntity(SemionEntityTypes.BOSS, context.getLevel());
        boss.configure(TeamId.PURPLE, BossMonster.defaultBoss(TeamId.PURPLE));
        boss.setPos(anchor);
        boss.setAnchorPosition(anchor);
        boss.setNoAi(true);
        context.getLevel().addFreshEntity(boss);

        SemionMonsterEntity nearby = spawnBossTargetMonster(context, "boss-splash-nearby", anchor.add(3.0, 0.0, 0.0));
        SemionMonsterEntity primary = spawnBossTargetMonster(context, "boss-splash-primary", anchor.add(2.0, 0.0, 0.0));
        SemionMonsterEntity far = spawnBossTargetMonster(context, "boss-splash-far", anchor.add(7.0, 0.0, 0.0));

        context.runAfterDelay(1, () -> {
            new BossAttackLaneMonsterGoal(boss).tick();

            if (!assertEquals(context, primary, boss.getTarget(), "Boss should select the nearest eligible monster regardless of spawn order.")) {
                return;
            }

            if (!assertTrue(context, primary.getHealth() < 100.0F, "Boss should damage its primary target.")) {
                return;
            }
            if (!assertTrue(context, nearby.getHealth() < 100.0F, "Boss attack should splash onto nearby monsters.")) {
                return;
            }
            if (!assertEquals(context, 100.0F, far.getHealth(), "Boss splash should not hit monsters outside the splash radius.")) {
                return;
            }
            if (!assertClose(context, 73.0, primary.runtimeMonster().health(), "Boss physical damage should use armor, not resistance.")) {
                return;
            }
            if (!assertClose(context, primary.runtimeMonster().health(), primary.getHealth(), "Boss damage should synchronize primary runtime and entity health.")) {
                return;
            }
            if (!assertClose(context, nearby.runtimeMonster().health(), nearby.getHealth(), "Boss splash should synchronize nearby runtime and entity health.")) {
                return;
            }
            if (!assertTrue(context, primary.runtimeMonster().lastHitSourceKind() == KillSourceKind.BOSS, "Boss damage should preserve boss kill attribution.")) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void bossDamageScalesByRoundAndTriplesAgainstSummons(GameTestHelper context) {
        SemionBossEntity boss = new SemionBossEntity(SemionEntityTypes.BOSS, context.getLevel());
        boss.configure(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        boss.setCurrentRound(3);

        Monster waveMonster = new Monster(
                "boss-wave-damage-target",
                TeamId.RED,
                1,
                Optional.empty(),
                Optional.empty(),
                100.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                0
        );
        Monster summonMonster = new Monster(
                "boss-summon-damage-target",
                TeamId.RED,
                1,
                Optional.of(stableUuid("boss-summon-owner")),
                Optional.of(TeamId.BLUE),
                100.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                DamageType.PHYSICAL,
                0.0,
                SummonTier.T1,
                List.of(SummonRole.RUSH),
                0
        );

        double expectedWaveDamage = 18.0 * 1.2;
        double expectedSummonDamage = expectedWaveDamage * 3.0;
        if (!assertTrue(context, Math.abs(boss.attackDamageAgainst(waveMonster) - expectedWaveDamage) < 0.001, "Boss damage should gain 10% per round after round 1.")) {
            return;
        }
        if (!assertTrue(context, Math.abs(boss.attackDamageAgainst(summonMonster) - expectedSummonDamage) < 0.001, "Boss damage should be tripled against summon monsters.")) {
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
        TowerType timeoutTowerType = new TowerType(
                "timeout_final_defense_test_tower",
                "Timeout Final Defense Test Tower",
                TowerCategory.DIRECT,
                0,
                10000.0,
                6.0,
                0.0,
                20,
                0
        );
        lane.addTower(new TestTower(timeoutTowerType, redId, TeamId.RED, 1, GridPosition.from(towerPlacementPos(lane))));
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
        if (!assertTrue(
                context,
                lane.laneLayout().progressAt(monsterEntity.position()) >= 0.75,
                "Wave timeout should move enemy toward the final defense side."
        )) {
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
        if (!assertEquals(context, 167L, game.players().get(redId).economy().mineral(), "Round payout should pay RED's accumulated income.")) {
            return;
        }
        if (!assertEquals(context, 169L, game.players().get(blueId).economy().mineral(), "Round payout should pay BLUE's accumulated income.")) {
            return;
        }

        long redGasBeforeSummon = game.players().get(redId).economy().gas();
        long redIncomeBeforeSummon = game.players().get(redId).economy().income();
        var summonResult = game.summonMonster(redId, "grunt");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.UNKNOWN_SUMMON, summonResult.type(), "Removed income summons should not be available in round 2 prepare.")) {
            return;
        }
        if (!assertEquals(context, redGasBeforeSummon, game.players().get(redId).economy().gas(), "Unknown lifecycle summon should not spend gas.")) {
            return;
        }
        if (!assertEquals(context, redIncomeBeforeSummon, game.players().get(redId).economy().income(), "Unknown lifecycle summon should not add income.")) {
            return;
        }
        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS + 2);
        if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Empty round 2 should resolve into the next prepare phase.")) {
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

        if (!assertEquals(context, 150L, game.players().get(playerId).economy().mineral(), "Starting mineral should match config default.")) {
            return;
        }
        if (!assertEquals(context, 0L, game.players().get(playerId).economy().gas(), "Starting gas should match config default.")) {
            return;
        }
        if (!assertEquals(context, 10L, game.players().get(playerId).economy().income(), "Starting income should match config default.")) {
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
        if (!assertEquals(context, 120L, game.players().get(playerId).economy().mineral(), "Gas upgrade should consume mineral cost.")) {
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
    public void roundWaitsForEveryActiveTeamToClearWave(GameTestHelper context) {
        UUID redId = stableUuid("round-barrier-red-owner");
        UUID blueId = stableUuid("round-barrier-blue-owner");
        WaveMonsterEntry entry = new WaveMonsterEntry(
                "round-barrier",
                100.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                0,
                1
        );
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(
                        new kim.biryeong.semiontd.config.RoundWaveConfig(1, Map.of("lane_1", List.of(entry)))
                ), 20, null),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "round-barrier-red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "round-barrier-blue", TeamId.BLUE, 1)
                ),
                java.util.Set.of(),
                2
        );

        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Round barrier game should start.")) {
            return;
        }
        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS + 1);
        if (!assertEquals(context, RoundPhase.LANE_WAVE, game.phase(), "Round barrier game should enter wave phase.")) {
            return;
        }

        PlayerLane redLane = redLane(game, 1);
        PlayerLane blueLane = lane(game, TeamId.BLUE, 1);
        if (!assertEquals(context, 1, redLane.activeMonsters().size(), "RED lane should have one wave monster.")) {
            return;
        }
        if (!assertEquals(context, 1, blueLane.activeMonsters().size(), "BLUE lane should have one wave monster.")) {
            return;
        }

        redLane.activeMonsters().getFirst().damage(Double.MAX_VALUE);
        tickGame(game, context.getLevel().getServer(), 1);
        if (!assertTrue(context, redLane.clearedThisRound(), "RED lane should be cleared after killing its wave monster.")) {
            return;
        }
        if (!assertTrue(context, !blueLane.clearedThisRound(), "BLUE lane should still be unresolved.")) {
            return;
        }
        if (!assertEquals(context, RoundPhase.LANE_WAVE, game.phase(), "Round should stay in wave phase until every active team is resolved.")) {
            return;
        }
        if (!assertEquals(context, 1, game.currentRound(), "Round should not advance while another active team is still fighting.")) {
            return;
        }

        blueLane.activeMonsters().getFirst().damage(Double.MAX_VALUE);
        tickGame(game, context.getLevel().getServer(), 2);
        if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Round should advance after every active team clears.")) {
            return;
        }
        if (!assertEquals(context, 2, game.currentRound(), "Next prepare phase should be round 2 after all teams clear.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void matchResultRecordsPerBuilderWaveOutcomes(GameTestHelper context) {
        UUID redId = stableUuid("builder-outcome-red");
        UUID blueId = stableUuid("builder-outcome-blue");
        WaveMonsterEntry entry = new WaveMonsterEntry(
                "builder-outcome",
                100.0,
                0.0,
                0.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                0,
                1
        );
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(
                        new kim.biryeong.semiontd.config.RoundWaveConfig(1, Map.of("lane_1", List.of(entry)))
                ), 20, null),
                testArena(context)
        );
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "builder-outcome-red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "builder-outcome-blue", TeamId.BLUE, 1)
                ),
                Set.of(),
                2
        );
        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Builder outcome game should start.")) {
            return;
        }
        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS + 1);

        PlayerLane redLane = redLane(game, 1);
        redLane.activeMonsters().getFirst().damage(Double.MAX_VALUE);
        tickGame(game, context.getLevel().getServer(), 1);
        if (!assertTrue(context, redLane.clearedThisRound(), "RED builder should clear its own lane.")) {
            return;
        }
        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "BLUE boss kill should end the game.")) {
            return;
        }

        Optional<MatchResult> result = game.matchResult();
        if (!assertPresent(context, result, "Ended game should expose individual wave results.")) {
            return;
        }
        Map<UUID, MatchParticipantResult> participants = result.get().participants().stream()
                .collect(Collectors.toMap(MatchParticipantResult::playerId, participant -> participant));
        MatchParticipantResult red = participants.get(redId);
        MatchParticipantResult blue = participants.get(blueId);
        if (!assertEquals(context, List.of(1), red.attemptedRounds(), "RED should record the first wave attempt.")) {
            return;
        }
        if (!assertEquals(context, List.of(1), red.clearedRounds(), "RED should record only its own cleared wave.")) {
            return;
        }
        if (!assertEquals(context, List.of(1), blue.attemptedRounds(), "BLUE should record the first wave attempt.")) {
            return;
        }
        if (!assertEquals(context, List.of(), blue.clearedRounds(), "An eliminated BLUE lane must not inherit the team result.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void infiniteWaveTemplateSelectionIsSharedAcrossTeams(GameTestHelper context) {
        WaveMonsterEntry firstEntry = new WaveMonsterEntry("template-first", 100.0, 0.0, 1.0, AttackKind.MELEE, "minecraft:piglin", null, 1);
        WaveMonsterEntry secondEntry = new WaveMonsterEntry("template-second", 100.0, 0.0, 1.0, AttackKind.MELEE, "minecraft:blaze", null, 1);
        WaveMonsterEntry thirdEntry = new WaveMonsterEntry("template-third", 100.0, 0.0, 1.0, AttackKind.MELEE, "minecraft:wither_skeleton", null, 1);
        var first = new kim.biryeong.semiontd.config.RoundWaveConfig(1, Map.of("default", List.of(firstEntry)));
        var second = new kim.biryeong.semiontd.config.RoundWaveConfig(1, Map.of("default", List.of(secondEntry)));
        var third = new kim.biryeong.semiontd.config.RoundWaveConfig(1, Map.of("default", List.of(thirdEntry)));
        SemionGame game = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 1, first, List.of(first, second, third)),
                testArena(context)
        );
        UUID redId = stableUuid("shared-template-red");
        UUID blueId = stableUuid("shared-template-blue");
        ParticipantSelectionPlan plan = new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, "red", TeamId.RED, 1),
                        new AssignedParticipant(blueId, "blue", TeamId.BLUE, 1)
                ),
                Set.of(),
                2
        );

        if (!assertTrue(context, game.start(context.getLevel().getServer(), plan), "Shared-template game should start.")) {
            return;
        }
        String previewMonsterId = game.upcomingWaveEntries(redId).getFirst().id();
        String prepareHud = SemionHudTextService.matchSidebarMarkupFor(
                redId,
                Optional.of(game.teams().get(TeamId.RED)),
                game,
                MatchMode.NORMAL
        );
        if (!assertTrue(context, prepareHud.contains("다음 웨이브") && !prepareHud.contains("전체 팀 보스"),
                "Preparation should show the selected wave instead of the team boss summary.")) {
            return;
        }
        if (!assertTrue(context, prepareHud.contains("<lang:entity.minecraft.") && !prepareHud.contains(previewMonsterId),
                "Preparation should use the client's localized entity name without exposing the internal monster id.")) {
            return;
        }
        if (!assertEquals(
                context,
                "TranslatableContents",
                SemionText.mini("<lang:entity.minecraft.hoglin>").getContents().getClass().getSimpleName(),
                "Upcoming-wave entity names should remain translatable on the client."
        )) {
            return;
        }
        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS + 1);
        String redMonsterId = redLane(game, 1).activeMonsters().getFirst().id();
        String blueMonsterId = lane(game, TeamId.BLUE, 1).activeMonsters().getFirst().id();
        if (!assertEquals(context, previewMonsterId, redMonsterId, "The prepared template should be the one spawned when combat starts.")) {
            return;
        }
        if (!assertEquals(context, redMonsterId, blueMonsterId, "All teams should receive the same infinite-wave template.")) {
            return;
        }
        String combatHud = SemionHudTextService.matchSidebarMarkupFor(
                redId,
                Optional.of(game.teams().get(TeamId.RED)),
                game,
                MatchMode.NORMAL
        );
        if (!assertTrue(context, combatHud.contains("전체 팀 보스") && !combatHud.contains("다음 웨이브"),
                "Combat should restore the team boss summary.")) {
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

        if (!assertEquals(context, 167L, game.players().get(redId).economy().mineral(), "Living RED player should receive round payout.")) {
            return;
        }
        if (!assertEquals(context, 169L, game.players().get(blueId).economy().mineral(), "Living BLUE player should receive round payout.")) {
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
    public void incomeSummonConsumesEmeraldAndAddsIncome(GameTestHelper context) {
        UUID redId = stableUuid("summon-red-owner");
        UUID blueId = stableUuid("summon-blue-owner");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);
        game.players().get(redId).economy().addGas(20L, Long.MAX_VALUE);

        var result = game.summonMonster(redId, "chicken");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, result.type(), "Default income summon should be summonable.")) {
            return;
        }
        if (!assertEquals(context, 0L, game.players().get(redId).economy().gas(), "Successful summon should spend chicken emerald cost.")) {
            return;
        }
        if (!assertEquals(context, 11L, game.players().get(redId).economy().income(), "Successful summon should add chicken income.")) {
            return;
        }
        PlayerLane targetLane = lane(game, result.targetTeam().orElseThrow(), result.targetLaneId().orElseThrow());
        if (!assertEquals(context, 1, targetLane.queuedSummonCount(), "Successful summon should queue one monster in the target lane.")) {
            return;
        }
        UUID targetPlayerId = result.targetTeam().orElseThrow() == TeamId.RED ? redId : blueId;
        String targetHud = SemionHudTextService.matchSidebarMarkupFor(
                targetPlayerId,
                Optional.of(game.teams().get(result.targetTeam().orElseThrow())),
                game,
                MatchMode.NORMAL
        );
        if (!assertTrue(context, targetHud.contains("추가 소환 1기"), "Target HUD should include queued income summons in its warning.")) {
            return;
        }
        if (!assertEquals(context, Optional.of(1), result.scheduledRound(), "Prepare phase summon should be scheduled for the current round.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void incomeSummonDisplaysColoredSenderName(GameTestHelper context) {
        UUID redId = stableUuid("summon-display-name-red-owner");
        UUID blueId = stableUuid("summon-display-name-blue-owner");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);
        game.players().get(redId).economy().addGas(20L, Long.MAX_VALUE);

        var result = game.summonMonster(redId, "chicken");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, result.type(), "Income summon should succeed before checking its display name.")) {
            return;
        }

        PlayerLane targetLane = lane(game, result.targetTeam().orElseThrow(), result.targetLaneId().orElseThrow());
        targetLane.tick(context.getLevel().getServer());
        if (!assertEquals(context, 1, targetLane.activeMonsters().size(), "Income summon should spawn in its target lane.")) {
            return;
        }

        Monster monster = targetLane.activeMonsters().getFirst();
        if (!(targetLane.arenaWorld().getEntity(monster.minecraftEntityId()) instanceof SemionMonsterEntity entity)) {
            context.fail(Component.literal("Spawned income monster entity should exist."));
            return;
        }
        Component displayName = entity.getCustomName();
        if (!assertTrue(context, displayName != null, "Income monster should have a custom display name.")) {
            return;
        }
        if (!assertEquals(context, "red", displayName.getString(), "Income monster should display the sending player's name.")) {
            return;
        }
        if (!assertEquals(context, net.minecraft.network.chat.TextColor.RED.getValue(), displayName.getStyle().getColor().getValue(), "Income monster sender name should use the sender team's color.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void incomeSummonFeedbackTargetsColoredLaneOwnerNickname(GameTestHelper context) {
        UUID redId = stableUuid("summon-feedback-red-owner");
        UUID blueId = stableUuid("summon-feedback-blue-owner");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);
        game.players().get(redId).economy().addGas(20L, Long.MAX_VALUE);

        var result = game.summonMonster(redId, "chicken");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, result.type(), "Income summon should succeed for feedback formatting.")) {
            return;
        }
        String markup = SemionCommands.summonSuccessMarkup(
                game,
                result,
                "chicken",
                game.currentRound(),
                result.scheduledRound().orElse(game.currentRound())
        );
        if (!assertEquals(context, "닭 이(가) <blue>blue</blue> 의 라인으로 공격합니다!", markup, "Income summon feedback should use the income name and target lane owner's colored nickname.")) {
            return;
        }
        if (!assertTrue(context, !markup.contains("팀=") && !markup.contains("라인="), "Income summon feedback should not expose team/lane labels.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void wavePhaseIncomeSummonQueuesForNextRound(GameTestHelper context) {
        UUID redId = stableUuid("wave-summon-red-owner");
        UUID blueId = stableUuid("wave-summon-blue-owner");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);

        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS);
        if (!assertEquals(context, RoundPhase.LANE_WAVE, game.phase(), "Game should enter wave phase before reserved summon purchase.")) {
            return;
        }

        game.players().get(redId).economy().addGas(20L, Long.MAX_VALUE);
        long gasBeforeSummon = game.players().get(redId).economy().gas();
        var result = game.summonMonster(redId, "chicken");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, result.type(), "Wave phase income summon should be purchasable.")) {
            return;
        }
        if (!assertEquals(context, Optional.of(2), result.scheduledRound(), "Wave phase summon should be scheduled for the next round.")) {
            return;
        }
        String markup = SemionCommands.summonSuccessMarkup(
                game,
                result,
                "chicken",
                game.currentRound(),
                result.scheduledRound().orElse(game.currentRound())
        );
        if (!assertEquals(context, "닭 이(가) <blue>blue</blue> 의 라인으로 공격합니다!", markup, "Reserved summon feedback should use the fallback attack message.")) {
            return;
        }
        if (!assertTrue(context, !markup.contains("예약") && !markup.contains("팀=") && !markup.contains("라인="), "Reserved summon feedback should not expose reservation or team/lane labels.")) {
            return;
        }
        if (!assertEquals(context, gasBeforeSummon - 20, game.players().get(redId).economy().gas(), "Wave phase summon should spend emerald immediately.")) {
            return;
        }
        if (!assertEquals(context, 11L, game.players().get(redId).economy().income(), "Wave phase summon should add income immediately.")) {
            return;
        }
        if (!assertEquals(context, 1L, game.players().get(redId).matchStats().summonedMonsters(), "Wave phase summon should update match stats immediately.")) {
            return;
        }

        PlayerLane targetLane = lane(game, result.targetTeam().orElseThrow(), result.targetLaneId().orElseThrow());
        if (!assertEquals(context, 0, targetLane.queuedSummonCount(), "Wave phase summon should not enter the current round summon queue.")) {
            return;
        }
        if (!assertEquals(context, 1, targetLane.pendingNextRoundSummonCount(), "Wave phase summon should wait in the next-round queue.")) {
            return;
        }
        if (!assertEquals(context, 0, targetLane.activeMonsters().size(), "Wave phase summon should not spawn in the current wave.")) {
            return;
        }

        tickGame(game, context.getLevel().getServer(), 2);
        if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, game.phase(), "Empty wave should resolve into next prepare after the reserved purchase.")) {
            return;
        }
        if (!assertEquals(context, 2, game.currentRound(), "Reserved summon should carry into round 2 prepare.")) {
            return;
        }
        if (!assertEquals(context, 1, targetLane.queuedSummonCount(), "Reserved summon should move into the target lane summon queue during next prepare.")) {
            return;
        }
        if (!assertEquals(context, 0, targetLane.pendingNextRoundSummonCount(), "Next-round queue should be empty after transfer.")) {
            return;
        }

        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS);
        if (!assertEquals(context, RoundPhase.LANE_WAVE, game.phase(), "Round 2 should enter wave phase.")) {
            return;
        }
        if (!assertEquals(context, 1, targetLane.queuedSummonCount(), "Reserved summon should be queued for the next wave.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void wavePhaseIncomeSummonUsesNextRoundScaling(GameTestHelper context) {
        String summonId = "wave_scale_probe";
        UUID redId = stableUuid("wave-scale-red-owner");
        UUID blueId = stableUuid("wave-scale-blue-owner");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);
        SummonRegistry.register(new SummonMonsterType(
                summonId,
                "Wave Scale Probe",
                0,
                0,
                100,
                0,
                20,
                AttackKind.MELEE,
                "minecraft:zombie",
                0
        ) {
        });
        game.refreshSummonShop();

        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS);
        var result = game.summonMonster(redId, summonId);
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, result.type(), "Wave scaling probe should be purchasable during wave phase.")) {
            return;
        }
        PlayerLane targetLane = lane(game, result.targetTeam().orElseThrow(), result.targetLaneId().orElseThrow());
        tickGame(game, context.getLevel().getServer(), SemionGame.DEFAULT_PREPARE_TICKS + 3);

        if (!assertEquals(context, 1, targetLane.activeMonsters().size(), "Wave scaling probe should spawn in round 2 wave.")) {
            return;
        }
        Monster spawned = targetLane.activeMonsters().getFirst();
        double expectedHealth = 100 * SummonBalancePolicy.summonHealthMultiplier(2);
        double expectedAttackDamage = 20 * SummonBalancePolicy.summonAttackDamageMultiplier(2);
        if (!assertClose(context, expectedHealth, spawned.maxHealth(), "Wave phase summon should use next round health scaling.")) {
            return;
        }
        if (!assertClose(context, expectedAttackDamage, spawned.attackDamage(), "Wave phase summon should use next round attack scaling.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void incomeSummonInvalidOutsidePrepareAndWave(GameTestHelper context) {
        UUID redId = stableUuid("summon-phase-red-owner");
        UUID blueId = stableUuid("summon-phase-blue-owner");

        SemionGame waitingGame = new SemionGame(
                EconomyConfig.defaultConfig(),
                new WaveConfig(List.of(), 20, null),
                testArena(context)
        );
        var waitingResult = waitingGame.summonMonster(redId, "chicken");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.INVALID_PHASE, waitingResult.type(), "Waiting game should reject summon purchases by phase.")) {
            return;
        }

        SemionGame payoutGame = startedTwoPlayerGame(context, redId, blueId);
        setField(payoutGame, "phase", RoundPhase.ROUND_PAYOUT);
        var payoutResult = payoutGame.summonMonster(redId, "chicken");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.INVALID_PHASE, payoutResult.type(), "Round payout should reject summon purchases by phase.")) {
            return;
        }

        SemionGame endedGame = startedTwoPlayerGame(context, redId, blueId);
        if (!assertTrue(context, endedGame.killBoss(TeamId.BLUE), "Ended phase setup should eliminate BLUE.")) {
            return;
        }
        var endedResult = endedGame.summonMonster(redId, "chicken");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.INVALID_PHASE, endedResult.type(), "Ended game should reject summon purchases by phase.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void incomeSummonRefundsWhenNoTargetTeamExists(GameTestHelper context) {
        UUID redId = stableUuid("refund-red-owner");
        SemionGame game = startedSinglePlayerGame(context, redId, TeamId.RED);
        game.players().get(redId).economy().addGas(20L, Long.MAX_VALUE);

        var result = game.summonMonster(redId, "chicken");
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.NO_TARGET_TEAM, result.type(), "Summon should fail when there is no target team.")) {
            return;
        }
        if (!assertEquals(context, 20L, game.players().get(redId).economy().gas(), "Failed summon should refund emerald cost.")) {
            return;
        }
        if (!assertEquals(context, 10L, game.players().get(redId).economy().income(), "Failed summon should not add income.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void incomeSummonDoesNotTargetEliminatedTeams(GameTestHelper context) {
        UUID redId = stableUuid("summon-living-red-owner");
        UUID blueId = stableUuid("summon-eliminated-blue-owner");
        UUID greenId = stableUuid("summon-living-green-owner");
        SemionGame game = startedThreePlayerGame(context, redId, blueId, greenId);

        if (!assertTrue(context, game.killBoss(TeamId.BLUE), "Blue boss kill should eliminate BLUE before summon targeting.")) {
            return;
        }

        game.players().get(redId).economy().addGas(20L, Long.MAX_VALUE);
        var result = game.summonMonster(redId, "chicken");
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

        context.runAfterDelay(100, () -> {
            lane.tick(context.getLevel().getServer(), new EconomyService(game.economyConfig()), game.players());
            if (!assertEquals(context, 159L, game.players().get(playerId).economy().mineral(), "Tower owner should receive wave monster mineral reward.")) {
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

        if (!assertEquals(context, 161L, game.players().get(playerId).economy().mineral(), "Defender last hit should pay the reward only once.")) {
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

        if (!assertEquals(context, 150L, game.players().get(playerId).economy().mineral(), "Boss or unknown kills should not pay mineral reward.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void incomeSummonRegistryProvidesDefaultFortyFour(GameTestHelper context) {
        reloadDefaultIncomeSummons();
        List<String> expectedSummonIds = List.of(
                "chicken", "rabbit", "silverfish", "zombie", "husk", "skeleton", "wolf", "spider",
                "cave_spider", "bee", "turtle", "sheep", "zombie_villager", "stray", "allay", "vex",
                "fox", "slime", "goat", "bogged", "pillager", "piglin_brute", "ravager", "hoglin",
                "horse", "llama", "phantom", "enderman", "breeze", "guardian", "polar_bear",
                "magma_cube", "ocelot", "vindicator", "witch", "iron_golem", "blaze", "shulker",
                "ghast", "zoglin", "wither_skeleton", "evoker", "elder_guardian", "warden",
                "goblin_scout", "elf_assassin", "dark_priest", "dwarf_gunner", "troll_javelineer",
                "orc_warrior", "necromancer", "creaking", "siege_golem", "legion_commander", "ogre_champion"
        );
        if (!assertEquals(context, 55, SummonRegistry.all().size(), "Default income registry should contain the 44 legacy summons and the 11 income tower units.")) {
            return;
        }
        for (String summonId : expectedSummonIds) {
            if (!assertPresent(context, SummonRegistry.find(summonId), "Default income summon should be registered: " + summonId)) {
                return;
            }
        }
        SummonMonsterType enderman = SummonRegistry.find("enderman").orElseThrow();
        if (!assertEquals(context, 300L, enderman.gasCost(), "Enderman should keep the planned high-income emerald cost.")) {
            return;
        }
        if (!assertEquals(context, 15L, enderman.incomeGain(), "Enderman should keep the planned high-income gain.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void summonConfigAppendsMissingDefaultSummons(GameTestHelper context) {
        SummonConfig.SummonDefinition chicken = SummonConfig.defaultConfig().summons().get("chicken");
        SummonConfig partial = new SummonConfig(Map.of("chicken", chicken));
        SummonConfig merged = partial.withMissingDefaults(SummonConfig.defaultConfig());
        if (!assertEquals(context, 55, merged.summons().size(), "Summon config should append missing default summon ids.")) {
            return;
        }
        if (!assertPresent(context, Optional.ofNullable(merged.summons().get("warden")), "Missing T5 summon should be appended.")) {
            return;
        }
        if (!assertEquals(context, chicken, merged.summons().get("chicken"), "Existing summon config entry should be preserved.")) {
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
        double expectedHealth = 130.0 - 10.0 * 100.0 / 108.0;
        if (!assertClose(context, expectedHealth, monster.health(), "Physical damage should use percentage armor mitigation.")) {
            return;
        }
        monster.damage(10, DamageType.MAGIC);
        expectedHealth -= 10.0 * 100.0 / 101.0;
        if (!assertClose(context, expectedHealth, monster.health(), "Magic damage should use percentage resistance mitigation.")) {
            return;
        }
        monster.damage(10, DamageType.TRUE);
        expectedHealth -= 10.0;
        if (!assertClose(context, expectedHealth, monster.health(), "True damage should ignore armor and resistance.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void summonTargetPriorityUsesRoleProgressAndSiegeBonus(GameTestHelper context) {
        Monster support = new Monster(
                "manual_support_priority",
                TeamId.BLUE,
                1,
                Optional.empty(),
                Optional.of(TeamId.RED),
                60.0,
                0.0,
                2.0,
                AttackKind.RANGED,
                "minecraft:allay",
                null,
                DamageType.MAGIC,
                0.0,
                SummonTier.T2,
                List.of(SummonRole.SUPPORT),
                4
        );
        Monster tank = new Monster(
                "manual_tank_priority",
                TeamId.BLUE,
                1,
                Optional.empty(),
                Optional.of(TeamId.RED),
                130.0,
                8.0,
                5.0,
                AttackKind.MELEE,
                "minecraft:husk",
                null,
                DamageType.PHYSICAL,
                0.0,
                SummonTier.T2,
                List.of(SummonRole.TANK),
                1
        );
        support.syncLaneProgress(0.5);
        tank.syncLaneProgress(0.5);
        if (!assertTrue(context, tank.targetPriorityScore() > support.targetPriorityScore(), "Tank should be prioritized over support at the same progress.")) {
            return;
        }

        Monster siege = new Monster(
                "manual_siege_priority",
                TeamId.BLUE,
                1,
                Optional.empty(),
                Optional.of(TeamId.RED),
                360.0,
                14.0,
                80.0,
                AttackKind.RANGED,
                "minecraft:ravager",
                null,
                DamageType.PHYSICAL,
                0.0,
                SummonTier.T5,
                List.of(SummonRole.SIEGE),
                4
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
        SemionMonsterEntity waveInjury = spawnRoleMonsterEntity(context, "wave_injury", Optional.empty(), TeamId.BLUE, 1, origin.add(3.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));
        waveInjury.runtimeMonster().damage(35.0, DamageType.TRUE);
        waveInjury.setHealth((float) waveInjury.runtimeMonster().health());
        SemionMonsterEntity wrongTarget = spawnSummonEntity(context, "wrong_target_injury", TeamId.GREEN, TeamId.RED, 1, origin.add(4.0, 0.0, 0.0), 100.0, 35.0);

        new SingleAllyHealGoal<>(caster, SemionMonsterEntity.class, 8.0, 12.0, 80, 10).tick();

        if (!assertEquals(context, SemionAnimationState.HEAL, caster.animationState(), "Successful single heal should play the caster heal animation.")) {
            return;
        }
        if (!assertEquals(context, 90.0, lightInjury.runtimeMonster().health(), "Single heal should not heal the less injured friendly summon.")) {
            return;
        }
        if (!assertEquals(context, 75.0, heavyInjury.runtimeMonster().health(), "Single heal should not heal the less injured friendly summon.")) {
            return;
        }
        if (!assertEquals(context, 77.0, waveInjury.runtimeMonster().health(), "Single heal should heal the most injured same target-lane wave unit.")) {
            return;
        }
        if (!assertEquals(context, 65.0, wrongTarget.runtimeMonster().health(), "Single heal should ignore units attacking another target team.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void areaAllyHealGoalHealsNearbyTargetLaneUnits(GameTestHelper context) {
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        SemionMonsterEntity caster = spawnSummonEntity(context, "manual_area_support", TeamId.RED, TeamId.BLUE, 1, origin, 80.0, 0.0);
        SemionMonsterEntity first = spawnSummonEntity(context, "area_first", TeamId.RED, TeamId.BLUE, 1, origin.add(1.0, 0.0, 0.0), 100.0, 10.0);
        SemionMonsterEntity second = spawnSummonEntity(context, "area_second", TeamId.RED, TeamId.BLUE, 1, origin.add(2.0, 0.0, 0.0), 100.0, 10.0);
        SemionMonsterEntity wave = spawnRoleMonsterEntity(context, "area_wave", Optional.empty(), TeamId.BLUE, 1, origin.add(3.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));
        wave.runtimeMonster().damage(10.0, DamageType.TRUE);
        wave.setHealth((float) wave.runtimeMonster().health());
        SemionMonsterEntity far = spawnSummonEntity(context, "area_far", TeamId.RED, TeamId.BLUE, 1, origin.add(8.0, 0.0, 0.0), 100.0, 10.0);
        SemionMonsterEntity wrongLane = spawnSummonEntity(context, "area_wrong_lane", TeamId.GREEN, TeamId.BLUE, 2, origin.add(1.0, 0.0, 1.0), 100.0, 10.0);

        new AreaAllyHealGoal<>(caster, SemionMonsterEntity.class, 5.0, 5.0, 4, 100, 10).tick();

        if (!assertEquals(context, 95.0, first.runtimeMonster().health(), "Area heal should heal nearby same sender units.")) {
            return;
        }
        if (!assertEquals(context, 95.0, second.runtimeMonster().health(), "Area heal should heal multiple same target-lane units.")) {
            return;
        }
        if (!assertEquals(context, 95.0, wave.runtimeMonster().health(), "Area heal should heal nearby same target-lane wave units.")) {
            return;
        }
        if (!assertEquals(context, 90.0, far.runtimeMonster().health(), "Area heal should ignore friendlies outside radius.")) {
            return;
        }
        if (!assertEquals(context, 90.0, wrongLane.runtimeMonster().health(), "Area heal should ignore units attacking another lane.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void monsterTimedEffectsTargetSameLaneWaveUnits(GameTestHelper context) {
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        SemionMonsterEntity caster = spawnRoleMonsterEntity(context, "effect_caster", Optional.of(TeamId.RED), TeamId.BLUE, 1, origin, 100.0, List.of(SummonRole.SUPPORT));
        SemionMonsterEntity summon = spawnRoleMonsterEntity(context, "effect_summon", Optional.of(TeamId.RED), TeamId.BLUE, 1, origin.add(1.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));
        SemionMonsterEntity wave = spawnRoleMonsterEntity(context, "effect_wave", Optional.empty(), TeamId.BLUE, 1, origin.add(2.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));
        SemionMonsterEntity wrongLane = spawnRoleMonsterEntity(context, "effect_wrong_lane", Optional.empty(), TeamId.BLUE, 2, origin.add(3.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));
        SemionMonsterEntity wrongTarget = spawnRoleMonsterEntity(context, "effect_wrong_target", Optional.of(TeamId.GREEN), TeamId.RED, 1, origin.add(4.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH));

        new ApplyMonsterTimedEffectGoal(
                caster,
                TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS,
                0.25,
                6.0,
                80,
                60,
                10,
                4
        ).tick();

        if (!assertEquals(context, 0.25, caster.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS), "Caster should count as a same target-lane buff target.")) {
            return;
        }
        if (!assertEquals(context, 0.25, summon.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS), "Summoned units on the same target lane should receive monster buffs.")) {
            return;
        }
        if (!assertEquals(context, 0.25, wave.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS), "Wave units on the same target lane should receive monster buffs.")) {
            return;
        }
        if (!assertEquals(context, 0.0, wrongLane.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS), "Monster buffs should ignore another lane.")) {
            return;
        }
        if (!assertEquals(context, 0.0, wrongTarget.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS), "Monster buffs should ignore another target team.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void timedEffectsKeepStrongestMagnitudeAndRefreshDuration(GameTestHelper context) {
        TimedEffectSet effects = new TimedEffectSet();
        effects.apply(TimedEffectType.MONSTER_MOVE_SPEED_BONUS, 0.50, 10);
        if (!assertEquals(context, 0.50, effects.magnitude(TimedEffectType.MONSTER_MOVE_SPEED_BONUS), "Timed effects should keep the configured magnitude without clamping.")) {
            return;
        }
        effects.apply(TimedEffectType.MONSTER_MOVE_SPEED_BONUS, 0.20, 50);
        if (!assertEquals(context, 0.50, effects.magnitude(TimedEffectType.MONSTER_MOVE_SPEED_BONUS), "Lower magnitude should not replace a stronger active effect.")) {
            return;
        }
        if (!assertEquals(context, 10, effects.remainingTicks(TimedEffectType.MONSTER_MOVE_SPEED_BONUS), "Lower magnitude should not refresh the stronger effect duration.")) {
            return;
        }
        effects.apply(TimedEffectType.MONSTER_MOVE_SPEED_BONUS, 0.50, 40);
        if (!assertEquals(context, 40, effects.remainingTicks(TimedEffectType.MONSTER_MOVE_SPEED_BONUS), "Equal magnitude should refresh the active effect duration.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void timedEffectsRejectDuplicateSourcesAndStackDifferentSources(GameTestHelper context) {
        TimedEffectSet effects = new TimedEffectSet();
        Identifier firstSource = Identifier.fromNamespaceAndPath("semion-td", "test/first_damage_bonus");
        Identifier secondSource = Identifier.fromNamespaceAndPath("semion-td", "test/second_damage_bonus");

        if (!assertTrue(context, effects.apply(TimedEffectType.TOWER_DAMAGE_BONUS, firstSource, 0.10, 20), "First sourced effect should apply.")) {
            return;
        }
        if (!assertEquals(context, 0.10, effects.magnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Sourced effect should contribute its magnitude.")) {
            return;
        }
        if (!assertTrue(context, !effects.apply(TimedEffectType.TOWER_DAMAGE_BONUS, firstSource, 0.20, 40), "Duplicate source should not refresh or stack while active.")) {
            return;
        }
        if (!assertEquals(context, 0.10, effects.magnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Duplicate source should leave magnitude unchanged.")) {
            return;
        }
        if (!assertEquals(context, 20, effects.remainingTicks(TimedEffectType.TOWER_DAMAGE_BONUS), "Duplicate source should leave duration unchanged.")) {
            return;
        }
        if (!assertTrue(context, effects.apply(TimedEffectType.TOWER_DAMAGE_BONUS, secondSource, 0.15, 30), "Different sources should stack.")) {
            return;
        }
        if (!assertEquals(context, 0.25, effects.magnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Different sourced effects should stack by type.")) {
            return;
        }
        if (!assertEquals(context, 30, effects.remainingTicks(TimedEffectType.TOWER_DAMAGE_BONUS), "Type duration should expose the longest active source duration.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void sourcedTimedEffectsCanBeRefreshedForPersistentAuras(GameTestHelper context) {
        TimedEffectSet effects = new TimedEffectSet();
        Identifier source = Identifier.fromNamespaceAndPath("semion-td", "test/refresh_damage_bonus");

        if (!assertTrue(context, effects.refresh(TimedEffectType.TOWER_DAMAGE_BONUS, source, 0.10, 20), "Refresh should apply a missing sourced effect.")) {
            return;
        }
        effects.tick();
        if (!assertEquals(context, 19, effects.remainingTicks(TimedEffectType.TOWER_DAMAGE_BONUS), "Refresh test should tick the sourced effect.")) {
            return;
        }
        if (!assertTrue(context, effects.refresh(TimedEffectType.TOWER_DAMAGE_BONUS, source, 0.10, 40), "Refresh should extend an existing sourced effect.")) {
            return;
        }
        if (!assertEquals(context, 40, effects.remainingTicks(TimedEffectType.TOWER_DAMAGE_BONUS), "Refresh should expose the extended duration.")) {
            return;
        }
        if (!assertTrue(context, effects.refresh(TimedEffectType.TOWER_DAMAGE_BONUS, source, 0.15, 30), "Refresh should replace a changed source magnitude.")) {
            return;
        }
        if (!assertEquals(context, 0.15, effects.magnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Refresh should expose the changed source magnitude.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void nullImpDebuffsOnlyNearestTargetLaneTower(GameTestHelper context) {
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        SemionMonsterEntity caster = spawnSummonEntity(context, "null_imp", TeamId.RED, TeamId.BLUE, 1, origin, 100.0, 0.0);
        SemionTowerEntity wrongTeam = spawnTowerEntity(context, TeamId.RED, 1, origin.add(1.0, 0.0, 0.0), TestTowerTypes.TEST_DIRECT);
        SemionTowerEntity wrongLane = spawnTowerEntity(context, TeamId.BLUE, 2, origin.add(2.0, 0.0, 0.0), TestTowerTypes.TEST_DIRECT);
        SemionTowerEntity target = spawnTowerEntity(context, TeamId.BLUE, 1, origin.add(3.0, 0.0, 0.0), TestTowerTypes.TEST_DIRECT);
        SemionTowerEntity fartherTarget = spawnTowerEntity(context, TeamId.BLUE, 1, origin.add(4.0, 0.0, 0.0), TestTowerTypes.TEST_DIRECT);

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
    public void siegeSummonsDealTrueBonusDamage(GameTestHelper context) {
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        SemionMonsterEntity bombard = spawnSummonEntity(context, "bombard_toad", TeamId.RED, TeamId.BLUE, 1, origin, 100.0, 0.0);
        SemionTowerEntity tower = spawnTowerEntity(context, TeamId.BLUE, 1, origin.add(1.0, 0.0, 0.0), TestTowerTypes.TEST_DIRECT);
        bombard.setTarget(tower);

        new SiegeTrueDamageGoal(
                bombard,
                SummonBalancePolicy.BOMBARD_TOAD_TRUE_DAMAGE,
                SummonBalancePolicy.BOMBARD_TOAD_TRUE_DAMAGE_COOLDOWN_TICKS,
                SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS,
                0.0
        ).tick();
        if (!assertEquals(context, 30.0F, tower.getHealth(), "Bombard toad should bonus-damage towers without a progress condition.")) {
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
                SummonBalancePolicy.SIEGE_BREAKER_TRUE_DAMAGE,
                SummonBalancePolicy.SIEGE_BREAKER_TRUE_DAMAGE_COOLDOWN_TICKS,
                SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS,
                0.0
        ).tick();
        if (!assertEquals(context, 955.0F, boss.getHealth(), "Siege breaker should bonus-damage boss targets.")) {
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

        SemionTowerEntity healerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(healerTower.entityId().orElseThrow());
        SemionTowerEntity targetEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(targetTower.entityId().orElseThrow());
        targetEntity.syncTowerState(targetTower);

        new SingleAllyHealGoal<>(healerEntity, SemionTowerEntity.class, 6.0, 15.0, 80, 10).tick();

        if (!assertEquals(context, 65.0, targetTower.health(), "Generic heal goal should update the tower runtime health.")) {
            return;
        }
        if (!assertEquals(context, 65.0F, targetEntity.getHealth(), "Generic heal goal should update the tower entity health.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void areaEffectApiFiltersOtherLanesAndReportsAppliedTargets(GameTestHelper context) {
        UUID playerId = stableUuid("area-effect-api-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPosition = towerPlacementPos(lane);
        TestTower tower = new TestTower(playerId, TeamId.RED, 1, GridPosition.from(towerPosition));
        lane.addTower(tower);
        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().orElseThrow());
        Vec3 center = towerEntity.position().add(1.5, 0.0, 0.0);
        SemionMonsterEntity sameLane = spawnRoleMonsterEntity(
                context,
                "area-api-same-lane",
                Optional.empty(),
                TeamId.RED,
                1,
                center,
                100.0,
                List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity otherLane = spawnRoleMonsterEntity(
                context,
                "area-api-other-lane",
                Optional.empty(),
                TeamId.RED,
                2,
                center.add(0.5, 0.0, 0.0),
                100.0,
                List.of(SummonRole.RUSH)
        );
        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                Identifier.fromNamespaceAndPath("semion-td", "gametest/area_api_lane_filter"),
                towerEntity,
                center,
                3.0,
                Set.of(),
                null,
                AreaVfxSpec.none()
        );

        AreaEffectResult<SemionMonsterEntity> result = SemionTdApi.areaEffects().applyToMonsters(request, target -> {
            target.setHealth(target.getHealth() - 10.0F);
            return AreaEffectOutcome.APPLIED;
        });

        if (!assertEquals(context, 1, result.candidateCount(), "Area API should only query the defended lane.")) {
            return;
        }
        if (!assertEquals(context, 1, result.appliedCount(), "Area API should report the changed target.")) {
            return;
        }
        if (!assertEquals(context, 0, result.killedCount(), "Non-lethal area effects should not report kills.")) {
            return;
        }
        if (!assertClose(context, 90.0, sameLane.getHealth(), "Area API should apply the action to the defended lane.")) {
            return;
        }
        if (!assertClose(context, 100.0, otherLane.getHealth(), "Area API should ignore monsters assigned to another lane.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void areaEffectApiRegisteredAndClonesModeOnlyAddsIllusions(GameTestHelper context) {
        UUID playerId = stableUuid("area-effect-api-clone-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos sourcePosition = towerPlacementPos(lane);
        BlockPos targetPosition = nearbyTowerPlacementPos(lane, sourcePosition);
        TestTower source = new TestTower(playerId, TeamId.RED, 1, GridPosition.from(sourcePosition));
        TestTower registeredTarget = new TestTower(playerId, TeamId.RED, 1, GridPosition.from(targetPosition));
        lane.addTower(source);
        lane.addTower(registeredTarget);
        SemionTowerEntity sourceEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(source.entityId().orElseThrow());

        Vec3 illusionPosition = sourceEntity.position().add(1.0, 0.0, 0.0);
        IllusionRuntimeTower illusion = new IllusionRuntimeTower(
                TestTowerTypes.TEST_DIRECT,
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(BlockPos.containing(illusionPosition))
        );
        SemionTowerEntity illusionEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        illusionEntity.configure(illusion, lane.laneLayout());
        illusionEntity.setPos(illusionPosition);
        context.getLevel().addFreshEntity(illusionEntity);

        Vec3 strayPosition = sourceEntity.position().add(1.5, 0.0, 0.0);
        TestTower unregisteredTower = new TestTower(playerId, TeamId.RED, 1, GridPosition.from(BlockPos.containing(strayPosition)));
        SemionTowerEntity unregisteredEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        unregisteredEntity.configure(unregisteredTower, lane.laneLayout());
        unregisteredEntity.setPos(strayPosition);
        context.getLevel().addFreshEntity(unregisteredEntity);

        TowerAreaEffectRequest request = new TowerAreaEffectRequest(
                Identifier.fromNamespaceAndPath("semion-td", "gametest/area_api_clone_filter"),
                sourceEntity,
                sourceEntity.position(),
                6.0,
                TowerAreaTargetMode.REGISTERED_AND_CLONES,
                false,
                null,
                AreaVfxSpec.none()
        );
        AreaEffectResult<AreaTowerTarget> result = SemionTdApi.areaEffects()
                .applyToTowers(request, ignored -> AreaEffectOutcome.APPLIED);

        if (!assertEquals(context, 2, result.candidateCount(), "Registered-and-clones mode should include one registered target and one illusion.")) {
            return;
        }
        if (!assertEquals(context, 1L, result.hits().stream().filter(hit -> hit.target().illusion()).count(), "Only IllusionRuntimeTower should be marked as an illusion.")) {
            return;
        }
        if (!assertTrue(context, result.hits().stream().noneMatch(hit -> hit.target().tower() == unregisteredTower), "An unrelated unregistered tower entity should not be treated as an illusion.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void builtInCatalogReloadRegistersTowerJobs(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());

        if (!assertPresent(context, JobRegistry.find(VillagerTowerJob.ID), "Built-in reload should register the villager tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(UndeadTowerJob.ID), "Built-in reload should register the undead tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(AnimalTowerJob.ID), "Built-in reload should register the animal tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(WarlockTowerJob.ID), "Built-in reload should register the warlock tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(LegionTowerJob.ID), "Built-in reload should register the legion tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(ResonanceTowerJob.ID), "Built-in reload should register the resonance tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(IllagerTowerJob.ID), "Built-in reload should register the illager tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(NetherTowerJob.ID), "Built-in reload should register the nether tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(EndTowerJob.ID), "Built-in reload should register the end tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(OceanTowerJob.ID), "Built-in reload should register the ocean tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(AncientCityTowerJob.ID), "Built-in reload should register the ancient-city tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(AdversaryTowerJob.ID), "Built-in reload should register the adversary tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(MageTowerJob.ID), "Built-in reload should register the mage tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(EngineerTowerJob.ID), "Built-in reload should register the engineer tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(InsectTowerJob.ID), "Built-in reload should register the insect tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(FutureAgencyTowerJob.ID), "Built-in reload should register the future-agency tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(QueenTowerJob.ID), "Built-in reload should register the queen tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(HeroPartyTowerJob.ID), "Built-in reload should register the hero-party tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(AtlantisTowerJob.ID), "Built-in reload should register the atlantis tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(PlantTowerJob.ID), "Built-in reload should register the plant tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(ThunderTowerJob.ID), "Built-in reload should register the thunder tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(ArmyTowerJob.ID), "Built-in reload should register the army tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(DemonLordTowerJob.ID), "Built-in reload should register the demon lord tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(BodyTowerJob.ID), "Built-in reload should register the body tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(DeveloperTowerJob.ID), "Built-in reload should register the developer tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(FrostTowerJob.ID), "Built-in reload should register the frost tower job.")) {
            return;
        }
        if (!assertPresent(context, JobRegistry.find(kim.biryeong.semiontd.job.MagicSchoolTowerJob.ID), "Built-in reload should register the magic school tower job.")) {
            return;
        }
        if (!assertEquals(context, 156L, ProductionTowerCatalog.all().stream()
                .filter(entry -> entry.availability() == ProductionTowerCatalog.Availability.JOB)
                .filter(entry -> !kim.biryeong.semiontd.tower.blueprint.BlueprintTowers.isBlueprintId(entry.type().id()))
                .filter(ProductionTowerCatalog.CatalogEntry::starter).count(), "Built-in reload should preserve every fixed job starter family independently of augments and personal blueprints.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void blockTowerSelectionStaysInsideTheResolvedTeamWorld(GameTestHelper context) {
        UUID redId = stableUuid("tower-selection-red");
        UUID blueId = stableUuid("tower-selection-blue");
        SemionGame game = startedTwoPlayerGame(context, redId, blueId);
        PlayerLane redLane = game.teams().get(TeamId.RED).laneGroup().lane(1).orElseThrow();
        PlayerLane blueLane = game.teams().get(TeamId.BLUE).laneGroup().lane(1).orElseThrow();
        BlockPos blockPosition = towerPlacementPos(redLane);
        GridPosition position = GridPosition.from(blockPosition);
        TestTower redTower = new TestTower(TestTowerTypes.TEST_DIRECT, redId, TeamId.RED, 1, position);
        TestTower blueTower = new TestTower(TestTowerTypes.TEST_DIRECT, blueId, TeamId.BLUE, 1, position);
        redLane.addTower(redTower);
        blueLane.addTower(blueTower);

        if (!assertEquals(
                context,
                blueTower,
                SemionTowerInteractionService.resolveTowerAt(game.teams().get(TeamId.BLUE), blockPosition),
                "Block selection should resolve the tower from the current world's team only."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void defaultPersistenceBackendIsSqlite(GameTestHelper context) {
        try {
            Path tempDir = Files.createTempDirectory("semion-persistence-config-test");
            SemionConfigLoader.LoadedConfigs configs = SemionConfigLoader.load(tempDir, LoggerFactory.getLogger("semion-td-persistence-config-test"));
            if (!assertEquals(context, SemionPersistenceBackendType.SQLITE, configs.persistence().backend(), "Default persistence backend should be SQLITE.")) {
                return;
            }
            if (!assertTrue(context, Files.exists(tempDir.resolve("persistence.json")), "Persistence config should be created next to other config files.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Failed to load persistence config: " + exception.getMessage()));
        }
    }

    @GameTest
    public void appliedMatchRepositorySeparatesSubsystems(GameTestHelper context) {
        Path storePath;
        try {
            storePath = Files.createTempDirectory("semion-applied-match-test").resolve("progression-applied-matches.json");
        } catch (java.io.IOException exception) {
            context.fail(Component.literal("Failed to create temporary applied-match store path."));
            return;
        }

        FileAppliedMatchRepository repository = new FileAppliedMatchRepository(storePath);
        MatchId matchId = MatchId.newId();
        if (!assertTrue(context, repository.markApplied(matchId, "progression", 1000L), "First progression mark should be recorded.")) {
            return;
        }
        if (!assertTrue(context, repository.hasApplied(matchId, "progression"), "Progression mark should be readable.")) {
            return;
        }
        if (!assertTrue(context, !repository.markApplied(matchId, "progression", 2000L), "Duplicate progression mark should be rejected.")) {
            return;
        }
        if (!assertTrue(context, repository.markApplied(matchId, "rating", 3000L), "Same matchId should be markable for another subsystem.")) {
            return;
        }

        FileAppliedMatchRepository reloaded = new FileAppliedMatchRepository(storePath);
        if (!assertTrue(context, reloaded.hasApplied(matchId, "progression"), "Progression mark should survive reload.")) {
            return;
        }
        if (!assertTrue(context, reloaded.hasApplied(matchId, "rating"), "Rating mark should survive reload separately.")) {
            return;
        }
        context.succeed();
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
        if (!assertEquals(context, net.minecraft.world.entity.EntityTypes.BLOCK_DISPLAY, entity.getPolymerEntityType(null), "Blockbench monsters should render through BIL's animated entity display type.")) {
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

        game.players().get(redId).economy().addGas(15L, Long.MAX_VALUE);
        var result = game.summonMonster(redId, summonId);
        if (!assertEquals(context, kim.biryeong.semiontd.summon.SummonResultType.SUCCESS, result.type(), "Custom summon class should be registered in the game.")) {
            return;
        }
        if (!assertEquals(context, 0L, game.players().get(redId).economy().gas(), "Custom summon class should spend its gas cost.")) {
            return;
        }
        if (!assertEquals(context, 14L, game.players().get(redId).economy().income(), "Custom summon class should grant its income.")) {
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
        SemionTowerEntity entity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        entity.configure(tower, null);
        if (!assertEquals(context, net.minecraft.world.entity.EntityTypes.BLOCK_DISPLAY, entity.getPolymerEntityType(null), "Modeled towers should render through BIL's animated entity display type.")) {
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

    @GameTest
    public void entityVisualScaleAppliesToTowerEntity(GameTestHelper context) {
        EntityVisual visual = EntityVisual.builder("minecraft:villager").scale(1.5).build();
        if (!assertClose(context, 1.5, visual.scale(), "EntityVisual builder should keep configured scale.")) {
            return;
        }

        EntityVisual stringScaleVisual = new EntityVisual("minecraft:villager", null, Map.of("scale", "2.25"));
        if (!assertClose(context, 2.25, stringScaleVisual.scale(), "EntityVisual should accept scale from property maps.")) {
            return;
        }
        if (!assertTrue(context, !stringScaleVisual.properties().containsKey("scale"), "Scale should be normalized out of generic visual properties.")) {
            return;
        }

        TowerType towerType = new TowerType(
                "scaled_visual_tower",
                "Scaled Visual Tower",
                TowerCategory.DIRECT,
                100,
                50,
                8,
                12,
                20,
                0,
                visual,
                List.of()
        );
        TestTower tower = new TestTower(
                towerType,
                stableUuid("scaled-visual-tower-owner"),
                TeamId.RED,
                1,
                new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)
        );
        SemionTowerEntity entity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        entity.configure(tower, null);
        if (!assertClose(context, 1.5, entity.getScale(), "Tower entity should apply EntityVisual scale to the runtime entity.")) {
            return;
        }
        if (!assertClose(context, 1.5, entity.getAttributeValue(Attributes.SCALE), "Tower entity scale attribute should match EntityVisual scale.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void slimeVisualBuilderAppliesClampedSize(GameTestHelper context) {
        EntityVisual visual = SlimeVisual.builder().size(4).build();
        if (!assertEquals(context, "minecraft:slime", visual.entityTypeId(), "Slime visual builder should use the slime entity type.")) {
            return;
        }
        if (!assertEquals(context, Optional.of(4), appliedSlimeSize(context, visual), "Slime visual should apply size 4 to tracked data.")) {
            return;
        }
        if (!assertEquals(context, Optional.of(1), appliedSlimeSize(context, SlimeVisual.builder().size(0).build()), "Slime size should clamp zero to 1.")) {
            return;
        }
        if (!assertEquals(context, Optional.of(1), appliedSlimeSize(context, SlimeVisual.builder().size(-5).build()), "Slime size should clamp negative values to 1.")) {
            return;
        }
        if (!assertEquals(context, Optional.of(127), appliedSlimeSize(context, SlimeVisual.builder().size(200).build()), "Slime size should clamp large values to 127.")) {
            return;
        }
        if (!assertEquals(
                context,
                Optional.of(6),
                appliedSlimeSize(context, new EntityVisual("minecraft:slime", null, Map.of("size", "6"))),
                "Slime visual should accept the size alias."
        )) {
            return;
        }
        context.succeed();
    }

    private static StartCandidate candidate(String name) {
        return new StartCandidate(stableUuid(name), name);
    }

    private static StartCandidate candidate(String name, int displayElo) {
        return new StartCandidate(stableUuid(name), name, displayElo);
    }

    private static int eloFor(UUID playerId, StartCandidate... candidates) {
        for (StartCandidate candidate : candidates) {
            if (candidate.uuid().equals(playerId)) {
                return candidate.displayElo();
            }
        }
        throw new IllegalArgumentException("Unknown playerId " + playerId);
    }

    private static SemionGame startedThreePlayerGame(GameTestHelper context, UUID redId, UUID blueId, UUID greenId) {
        reloadDefaultIncomeSummons();
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

    private static SemionJob registerTowerAllowingJob(String path, Set<String> towerIds) {
        Set<String> allowedTowerIds = Set.copyOf(towerIds);
        return JobRegistry.registerIfAbsent(new SemionJob(
                Identifier.fromNamespaceAndPath("semion-td", "test/" + path),
                Component.literal("Test Job"),
                List.of()
        ) {
            @Override
            public boolean canUseTower(kim.biryeong.semiontd.job.JobContext context, TowerType towerType) {
                return towerType != null && allowedTowerIds.contains(towerType.id());
            }
        });
    }

    private static void selectNoTraits(SemionGameManager manager, MinecraftServer server, UUID playerId) {
        manager.selectTrait(server, playerId, TraitSlot.PRIMARY, BuiltInTraits.NONE_ID);
        manager.selectTrait(server, playerId, TraitSlot.SECONDARY, BuiltInTraits.NONE_ID);
    }

    private static SemionMonsterEntity spawnBossTargetMonster(GameTestHelper context, String id, Vec3 position) {
        Monster monster = new Monster(
                id,
                TeamId.PURPLE,
                1,
                Optional.empty(),
                Optional.of(TeamId.BLUE),
                100.0,
                100,
                0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                DamageType.PHYSICAL,
                300,
                SummonTier.T1,
                List.of(SummonRole.RUSH),
                0
        );
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, null);
        entity.setPos(position);
        context.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static SemionMonsterEntity spawnLaneMonsterEntity(
            GameTestHelper context,
            PlayerLane lane,
            String id,
            TeamId targetTeam,
            int targetLaneId,
            Vec3 position
    ) {
        Monster monster = new Monster(
                id,
                targetTeam,
                targetLaneId,
                Optional.empty(),
                Optional.empty(),
                100.0,
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
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, lane.laneLayout());
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

    private static Map<TeamId, TeamMatchResult> teamResultsByTeam(MatchResult matchResult) {
        Map<TeamId, TeamMatchResult> byTeam = new EnumMap<>(TeamId.class);
        for (TeamMatchResult result : matchResult.teamResults()) {
            byTeam.put(result.teamId(), result);
        }
        return byTeam;
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

    private static Optional<Integer> appliedSlimeSize(GameTestHelper context, EntityVisual visual) {
        List<SynchedEntityData.DataValue<?>> data = new ArrayList<>();
        EntityVisualApplierRegistry.apply(visual, net.minecraft.world.entity.EntityTypes.SLIME, context.getLevel().registryAccess(), data);
        return dataValue(data, SlimeAccessor.semiontd$idSize());
    }

    @SuppressWarnings("unchecked")
    private static <T> Optional<T> dataValue(
            List<SynchedEntityData.DataValue<?>> data,
            EntityDataAccessor<T> accessor
    ) {
        for (SynchedEntityData.DataValue<?> dataValue : data) {
            if (dataValue.id() == accessor.id()) {
                return Optional.of((T)dataValue.value());
            }
        }
        return Optional.empty();
    }

    private static byte[] syntheticOggVorbis(int sampleRate, long samples) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeOggPage(output, 0L, 0, syntheticVorbisIdentificationPacket(sampleRate));
        writeOggPage(output, samples, 1, new byte[] {0});
        return output.toByteArray();
    }

    private static byte[] syntheticVorbisIdentificationPacket(int sampleRate) {
        byte[] packet = new byte[30];
        packet[0] = 1;
        byte[] vorbis = "vorbis".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(vorbis, 0, packet, 1, vorbis.length);
        packet[11] = 1;
        writeLittleEndianInt(packet, 12, sampleRate);
        packet[28] = 0x11;
        packet[29] = 1;
        return packet;
    }

    private static void writeOggPage(ByteArrayOutputStream output, long granulePosition, int sequence, byte[] body) throws java.io.IOException {
        output.write("OggS".getBytes(StandardCharsets.US_ASCII));
        output.write(0);
        output.write(0);
        writeLittleEndianLong(output, granulePosition);
        writeLittleEndianInt(output, 1);
        writeLittleEndianInt(output, sequence);
        writeLittleEndianInt(output, 0);
        output.write(1);
        output.write(body.length);
        output.write(body);
    }

    private static void writeLittleEndianInt(ByteArrayOutputStream output, int value) {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 24) & 0xff);
    }

    private static void writeLittleEndianInt(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xff);
        data[offset + 1] = (byte) ((value >>> 8) & 0xff);
        data[offset + 2] = (byte) ((value >>> 16) & 0xff);
        data[offset + 3] = (byte) ((value >>> 24) & 0xff);
    }

    private static void writeLittleEndianLong(ByteArrayOutputStream output, long value) {
        for (int index = 0; index < 8; index++) {
            output.write((int) ((value >>> (8 * index)) & 0xff));
        }
    }

    private static final class FixtureSupportTower extends EntityBackedTower {
        private int persistentBonus;

        private FixtureSupportTower(
                TowerType type,
                UUID ownerPlayer,
                TeamId teamId,
                int laneId,
                kim.biryeong.semiontd.game.GridPosition originalPosition,
                kim.biryeong.semiontd.game.GridPosition currentPosition
        ) {
            super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
        }

        private void setPersistentBonus(int persistentBonus) {
            this.persistentBonus = persistentBonus;
        }

        private int persistentBonus() {
            return persistentBonus;
        }

        @Override
        protected void copyRuntimeStateFrom(kim.biryeong.semiontd.tower.Tower previousTower) {
            if (previousTower instanceof FixtureSupportTower fixtureSupportTower) {
                persistentBonus = fixtureSupportTower.persistentBonus;
            }
        }
    }

    private static final class CapturingResourcePackBuilder implements ResourcePackBuilder {
        private final Map<String, byte[]> data = new java.util.HashMap<>();

        private Map<String, byte[]> data() {
            return data;
        }

        @Override
        public boolean addData(String path, eu.pb4.polymer.resourcepack.api.PackResource value) {
            this.data.put(path, value.readAllBytes());
            return true;
        }

        @Override
        public boolean copyAssets(String modId) {
            return false;
        }

        @Override
        public boolean copyFromPath(Path path, String targetPrefix, boolean override, String source) {
            return false;
        }

        @Override
        public byte @Nullable [] getData(String path) {
            return data.get(path);
        }

        @Override
        public eu.pb4.polymer.resourcepack.api.PackResource getResource(String path) {
            byte[] value = data.get(path);
            return value == null ? null : eu.pb4.polymer.resourcepack.api.PackResource.of(value);
        }

        @Override
        public byte @Nullable [] getDataOrSource(String path) {
            return data.get(path);
        }

        @Override
        public void forEachResource(BiConsumer<String, eu.pb4.polymer.resourcepack.api.PackResource> consumer) {
            data.forEach((path, value) -> consumer.accept(path, eu.pb4.polymer.resourcepack.api.PackResource.of(value)));
        }

        @Override
        public boolean addAssetsSource(String modId) {
            return false;
        }

        @Override
        public void addResourceConverter(ResourcePackBuilder.ResourceConverter converter) {
        }

        @Override
        public void addPreFinishTask(Consumer<ResourcePackBuilder> consumer) {
        }
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
}
