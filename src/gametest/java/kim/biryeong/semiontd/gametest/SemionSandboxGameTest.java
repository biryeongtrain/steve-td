package kim.biryeong.semiontd.gametest;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.boss.BossMonster;
import kim.biryeong.semiontd.entity.boss.SemionBossEntity;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.monster.goal.LaneFollowGoal;
import kim.biryeong.semiontd.command.SemionCommands;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.MapConfig;
import kim.biryeong.semiontd.config.ProgressionConfig;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.job.AnimalTowerJob;
import kim.biryeong.semiontd.job.IllagerTowerJob;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.music.SemionMusicLibrary;
import kim.biryeong.semiontd.music.SemionMusicService;
import kim.biryeong.semiontd.music.SemionMusicTrack;
import kim.biryeong.semiontd.summon.SummonResult;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.summon.SummonTier;
import kim.biryeong.semiontd.summon.SummonResultType;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.trait.BuiltInTraits;
import kim.biryeong.semiontd.trait.TraitEffects;
import kim.biryeong.semiontd.trait.TraitSelectionSession;
import kim.biryeong.semiontd.trait.TraitSlot;
import kim.biryeong.semiontd.ui.SemionHotbarService;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class SemionSandboxGameTest {
    @GameTest
    public void koreanSandboxAliasCommandTreeMirrorsSandboxSubcommands(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        var alias = dispatcher.getRoot().getChild("샌드박스");
        if (!assertTrue(context, alias != null, "Expected /샌드박스 alias to be registered.")) {
            return;
        }
        if (!assertTrue(context, alias.getCommand() != null, "Expected bare /샌드박스 to start sandbox like /semiontd sandbox.")) {
            return;
        }
        for (String child : List.of("start", "reset", "leave", "round", "spectate", "give", "money")) {
            if (!assertTrue(context, alias.getChild(child) != null, "Expected /샌드박스 " + child + " subcommand to be registered.")) {
                return;
            }
        }
        CommandSourceStack source = context.getLevel().getServer().createCommandSourceStack();
        for (String command : List.of(
                "샌드박스",
                "샌드박스 start",
                "샌드박스 reset",
                "샌드박스 leave",
                "샌드박스 round 12",
                "샌드박스 spectate @s",
                "샌드박스 give diamond 10",
                "샌드박스 give emerald 10",
                "샌드박스 give income 10",
                "샌드박스 money diamond 10",
                "샌드박스 money emerald 10",
                "샌드박스 money income 10"
        )) {
            var parsed = dispatcher.parse(command, source);
            if (!assertTrue(context, !parsed.getContext().getNodes().isEmpty() && !parsed.getReader().canRead(), "Expected command to parse completely: /" + command)) {
                return;
            }
        }
        context.succeed();
    }

    @GameTest
    public void sandboxSessionRunsSeparateFromActiveGame(GameTestHelper context) {
        SemionGameManager manager = new SemionGameManager();
        try {
            configureManager(manager);
            MinecraftServer server = context.getLevel().getServer();
            SemionGame activeGame = manager.createGame(server);
            UUID activeRedId = uuid("sandbox-active-red");
            UUID activeBlueId = uuid("sandbox-active-blue");
            ParticipantSelectionPlan activePlan = new ParticipantSelectionPlan(
                    MatchMode.TEST,
                    List.of(
                            new AssignedParticipant(activeRedId, "active-red", TeamId.RED, 1),
                            new AssignedParticipant(activeBlueId, "active-blue", TeamId.BLUE, 1)
                    ),
                    Set.of(),
                    2
            );
            if (!assertTrue(context, activeGame.start(server, activePlan), "Active match should start before sandbox.")) {
                return;
            }

            UUID sandboxOwnerId = uuid("sandbox-owner");
            GameArena sandboxArena = SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO));
            SemionGameManager.SandboxStartResult startResult = manager.startSandbox(
                    server,
                    sandboxOwnerId,
                    "sandbox-owner",
                    sandboxArena
            );
            if (!assertEquals(context, SemionGameManager.SandboxStartResult.STARTED, startResult, "Non-participant should be able to start a sandbox.")) {
                return;
            }
            SemionGame sandboxGame = manager.sandboxGame(sandboxOwnerId).orElse(null);
            if (!assertTrue(context, sandboxGame != null, "Sandbox game should be registered for its owner.")) {
                return;
            }
            if (!assertTrue(context, sandboxGame != activeGame, "Sandbox game instance must be separate from the active match.")) {
                return;
            }
            if (!assertTrue(context, manager.activeGame().orElseThrow() == activeGame, "Starting sandbox must not replace the active match.")) {
                return;
            }
            if (!assertTrue(context, !activeGame.players().containsKey(sandboxOwnerId), "Sandbox owner must not be added to active match players.")) {
                return;
            }
            if (!assertTrue(context, sandboxGame.players().containsKey(sandboxOwnerId), "Sandbox owner should be a sandbox participant.")) {
                return;
            }
            if (!assertTrue(context, manager.playableGame(sandboxOwnerId).orElseThrow() == sandboxGame, "Sandbox owner commands should resolve to sandbox game.")) {
                return;
            }
            if (!assertTrue(context, manager.playableGame(activeRedId).orElseThrow() == activeGame, "Active participant commands should still resolve to active match.")) {
                return;
            }

            String starterTowerId = ProductionTowerService.availableTowers(sandboxGame, sandboxOwnerId).stream()
                    .findFirst()
                    .orElseThrow()
                    .type()
                    .id();
            PlayerLane sandboxLane = sandboxGame.playerLane(sandboxOwnerId).orElseThrow();
            BlockPos towerPos = sandboxLane.laneLayout().laneArea().min();
            TowerPlacementResult placement = ProductionTowerService.placeTower(sandboxGame, sandboxOwnerId, towerPos, starterTowerId);
            if (!assertEquals(context, TowerPlacementResult.SUCCESS, placement, "Sandbox tower placement should succeed.")) {
                return;
            }
            if (!assertEquals(context, 1, sandboxGame.towerCount(sandboxOwnerId), "Sandbox should count its placed tower.")) {
                return;
            }
            if (!assertEquals(context, 0, activeGame.towerCount(activeRedId), "Sandbox tower placement must not mutate the active match tower count.")) {
                return;
            }

            long activeIncomeBeforeSandboxSummon = activeGame.players().get(activeRedId).economy().income();
            sandboxGame.players().get(sandboxOwnerId).economy().addGas(20L, Long.MAX_VALUE);
            SummonResult summon = sandboxGame.summonMonster(sandboxOwnerId, "chicken");
            if (!assertEquals(context, SummonResultType.SUCCESS, summon.type(), "Sandbox income summon should use the sandbox game only.")) {
                return;
            }
            if (!assertTrue(context, summon.targetTeam().filter(TeamId.RED::equals).isPresent(), "Sandbox income should route back to the sandbox owner's own team.")) {
                return;
            }
            if (!assertEquals(context, sandboxOwnerId, lane(sandboxGame, TeamId.RED, 1).ownerPlayer(), "Sandbox self-routed income should enter the owner's own lane.")) {
                return;
            }
            if (!assertEquals(context, activeIncomeBeforeSandboxSummon, activeGame.players().get(activeRedId).economy().income(), "Sandbox income summon must not change active match economy.")) {
                return;
            }

            if (!assertEquals(
                    context,
                    SemionGameManager.SandboxStartResult.PLAYER_IN_MATCH,
                    manager.startSandbox(server, activeRedId, "active-red", SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(new BlockPos(60, 0, 0)))),
                    "Active match participants must not be able to start a sandbox."
            )) {
                return;
            }
            if (!assertTrue(context, manager.sandboxGame(activeRedId).isEmpty(), "Denied active participant sandbox should not be registered.")) {
                return;
            }

            if (!assertTrue(context, manager.stopSandbox(sandboxOwnerId), "Stopping sandbox should remove the session.")) {
                return;
            }
            if (!assertTrue(context, manager.sandboxGame(sandboxOwnerId).isEmpty(), "Stopped sandbox should not remain registered.")) {
                return;
            }
            if (!assertTrue(context, manager.activeGame().orElseThrow() == activeGame, "Stopping sandbox must not close active match.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Sandbox isolation test failed: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void sandboxMusicTargetsOwnerAndStopsWhenSandboxEnds(GameTestHelper context) {
        SemionGameManager manager = new SemionGameManager();
        try {
            configureManager(manager);
            MinecraftServer server = context.getLevel().getServer();
            var owner = context.makeMockServerPlayerInLevel();
            UUID ownerId = owner.getUUID();
            SemionMusicTrack track = new SemionMusicTrack(
                    "sandbox",
                    Path.of("sandbox.ogg"),
                    ResourceLocation.fromNamespaceAndPath("semion-td", "music.sandbox"),
                    ResourceLocation.fromNamespaceAndPath("semion-td", "music/sandbox"),
                    200L
            );
            SemionMusicService musicService = new SemionMusicService(new SemionMusicLibrary(List.of(track)), () -> 100L);
            manager.configureMusic(musicService);

            SemionGameManager.SandboxStartResult startResult = manager.startSandbox(
                    server,
                    ownerId,
                    owner.getGameProfile().getName(),
                    SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
            );
            if (!assertEquals(context, SemionGameManager.SandboxStartResult.STARTED, startResult, "Sandbox should start before music playback.")) {
                return;
            }

            manager.tick(server);
            if (!assertEquals(
                    context,
                    SemionMusicService.PlaybackAction.NONE,
                    musicService.decisionFor(ownerId, 0L, false).action(),
                    "Sandbox owner should be tracked by the music service."
            )) {
                return;
            }

            if (!assertTrue(context, manager.stopSandbox(ownerId), "Sandbox should stop before music cleanup.")) {
                return;
            }
            manager.tick(server);
            if (!assertEquals(
                    context,
                    SemionMusicService.PlaybackAction.START_TRACK,
                    musicService.decisionFor(ownerId, 0L, false).action(),
                    "Sandbox owner should no longer be tracked after the sandbox stops."
            )) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Sandbox music test failed: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void sandboxSessionUsesPersistedSelectedJob(GameTestHelper context) {
        SemionGameManager manager = new SemionGameManager();
        try {
            configureManager(manager);
            MinecraftServer server = context.getLevel().getServer();
            UUID sandboxOwnerId = uuid("sandbox-job-owner");
            String playerName = "sandbox-job-owner";
            SemionGame lobby = manager.createGame(server);
            if (!assertTrue(context, lobby.selectJob(sandboxOwnerId, AnimalTowerJob.ID), "Lobby should accept the selected job.")) {
                return;
            }
            manager.saveSelectedJob(server, sandboxOwnerId, playerName, AnimalTowerJob.ID);
            if (!assertEquals(
                    context,
                    TraitSelectionSession.SelectionResult.SELECTED,
                    lobby.selectTrait(sandboxOwnerId, TraitSlot.PRIMARY, BuiltInTraits.FORTITUDE_ID),
                    "Lobby should accept the selected primary trait."
            )) {
                return;
            }
            if (!assertEquals(
                    context,
                    TraitSelectionSession.SelectionResult.SELECTED,
                    lobby.selectTrait(sandboxOwnerId, TraitSlot.SECONDARY, BuiltInTraits.MOBILIZATION_GRANT_ID),
                    "Lobby should accept the selected secondary trait."
            )) {
                return;
            }

            SemionGameManager.SandboxStartResult startResult = manager.startSandbox(
                    server,
                    sandboxOwnerId,
                    playerName,
                    SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
            );
            if (!assertEquals(context, SemionGameManager.SandboxStartResult.STARTED, startResult, "Sandbox should start after saving a selected job.")) {
                return;
            }
            SemionGame sandboxGame = manager.sandboxGame(sandboxOwnerId).orElseThrow();
            var sandboxPlayer = sandboxGame.players().get(sandboxOwnerId);
            if (!assertTrue(context, sandboxPlayer != null, "Sandbox owner should be an active sandbox player.")) {
                return;
            }
            if (!assertEquals(
                    context,
                    AnimalTowerJob.ID,
                    sandboxPlayer.job().orElseThrow().id(),
                    "Sandbox should use the player's persisted selected job instead of a fixed built-in job."
            )) {
                return;
            }
            if (!assertEquals(
                    context,
                    BuiltInTraits.FORTITUDE_ID,
                    sandboxPlayer.traitLoadout().primaryTraitId(),
                    "Sandbox should inherit the primary trait selected in the waiting lobby."
            )) {
                return;
            }
            if (!assertEquals(
                    context,
                    BuiltInTraits.MOBILIZATION_GRANT_ID,
                    sandboxPlayer.traitLoadout().secondaryTraitId(),
                    "Sandbox should inherit the secondary trait selected in the waiting lobby."
            )) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Sandbox job selection test failed: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void sandboxIllagerRaidBossBarSurvivesWithoutActiveMatch(GameTestHelper context) {
        SemionGameManager manager = new SemionGameManager();
        try {
            configureManager(manager);
            MinecraftServer server = context.getLevel().getServer();
            var player = context.makeMockServerPlayerInLevel();
            manager.saveSelectedJob(server, player.getUUID(), player.getGameProfile().getName(), IllagerTowerJob.ID);

            SemionGameManager.SandboxStartResult startResult = manager.startSandbox(
                    server,
                    player.getUUID(),
                    player.getGameProfile().getName(),
                    SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
            );
            if (!assertEquals(context, SemionGameManager.SandboxStartResult.STARTED, startResult, "Illager sandbox should start without an active match.")) {
                return;
            }

            manager.tick(server);

            if (!assertTrue(context, manager.hasIllagerRaidBossBar(player.getUUID()), "Sandbox illager raid bossbar should not be cleared when no active match exists.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Sandbox illager bossbar test failed: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void activeMatchSpectatorCanLeaveSpectatingForSandbox(GameTestHelper context) {
        SemionGameManager manager = new SemionGameManager();
        try {
            configureManager(manager);
            MinecraftServer server = context.getLevel().getServer();
            SemionGame activeGame = manager.createGame(server);
            UUID activeRedId = uuid("sandbox-spectator-active-red");
            UUID activeBlueId = uuid("sandbox-spectator-active-blue");
            UUID spectatorId = uuid("sandbox-spectator-owner");
            ParticipantSelectionPlan activePlan = new ParticipantSelectionPlan(
                    MatchMode.TEST,
                    List.of(
                            new AssignedParticipant(activeRedId, "active-red", TeamId.RED, 1),
                            new AssignedParticipant(activeBlueId, "active-blue", TeamId.BLUE, 1)
                    ),
                    Set.of(spectatorId),
                    3
            );
            if (!assertTrue(context, activeGame.start(server, activePlan), "Active match should start with a spectator.")) {
                return;
            }
            if (!assertTrue(context, activeGame.isMatchSpectator(spectatorId), "Regression setup should register the player as an active match spectator.")) {
                return;
            }

            SemionGameManager.SandboxStartResult startResult = manager.startSandbox(
                    server,
                    spectatorId,
                    "sandbox-spectator-owner",
                    SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(new BlockPos(300, 0, 0)))
            );
            if (!assertEquals(context, SemionGameManager.SandboxStartResult.STARTED, startResult, "Active match spectator should be able to leave spectating and start sandbox.")) {
                return;
            }
            SemionGame sandboxGame = manager.sandboxGame(spectatorId).orElse(null);
            if (!assertTrue(context, sandboxGame != null, "Sandbox should be registered for the former spectator.")) {
                return;
            }
            if (!assertTrue(context, !activeGame.isMatchSpectator(spectatorId), "Starting sandbox should remove the player from active match spectators.")) {
                return;
            }
            if (!assertTrue(context, manager.playableGame(spectatorId).orElseThrow() == sandboxGame, "Former spectator commands should route to sandbox after starting it.")) {
                return;
            }
            if (!assertTrue(context, manager.protectionGame(spectatorId) == sandboxGame, "Former spectator protection context should route to sandbox, not the active match.")) {
                return;
            }
            if (!assertTrue(context, manager.activeGame().orElseThrow() == activeGame, "Starting spectator sandbox must not replace the active match.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Sandbox spectator release test failed: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void nonParticipantCanSelectStoredJobWhileMatchIsRunning(GameTestHelper context) {
        SemionGameManager manager = new SemionGameManager();
        try {
            configureManager(manager);
            MinecraftServer server = context.getLevel().getServer();
            var player = context.makeMockServerPlayerInLevel();
            SemionGame activeGame = manager.createGame(server);
            UUID activeRedId = uuid("running-job-red");
            UUID activeBlueId = uuid("running-job-blue");
            if (!assertTrue(context, activeGame.start(server, new ParticipantSelectionPlan(
                    MatchMode.TEST,
                    List.of(
                            new AssignedParticipant(activeRedId, "running-job-red", TeamId.RED, 1),
                            new AssignedParticipant(activeBlueId, "running-job-blue", TeamId.BLUE, 1)
                    ),
                    Set.of(),
                    2
            )), "Active match should start before non-participant job selection.")) {
                return;
            }

            int result = invokeSelectJob(player.createCommandSourceStack(), manager, AnimalTowerJob.ID.getPath());
            if (!assertEquals(context, 1, result, "Non-participants should be able to select a stored job while a match is running.")) {
                return;
            }
            if (!assertEquals(
                    context,
                    AnimalTowerJob.ID,
                    manager.profile(server, player.getUUID(), player.getGameProfile().getName()).selectedJobResource().orElse(null),
                    "Running-match non-participant job selection should persist to the player's profile."
            )) {
                return;
            }
            if (!assertTrue(context, !activeGame.players().containsKey(player.getUUID()), "Regression setup should keep the selecting player out of the active match roster.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Non-participant running job selection failed: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void runningMatchParticipantCannotChangeJob(GameTestHelper context) {
        SemionGameManager manager = new SemionGameManager();
        try {
            configureManager(manager);
            MinecraftServer server = context.getLevel().getServer();
            var player = context.makeMockServerPlayerInLevel();
            SemionGame activeGame = manager.createGame(server);
            UUID activeBlueId = uuid("running-job-block-blue");
            if (!assertTrue(context, activeGame.start(server, new ParticipantSelectionPlan(
                    MatchMode.TEST,
                    List.of(
                            new AssignedParticipant(player.getUUID(), player.getGameProfile().getName(), TeamId.RED, 1),
                            new AssignedParticipant(activeBlueId, "running-job-block-blue", TeamId.BLUE, 1)
                    ),
                    Set.of(),
                    2
            )), "Active match should start before participant job selection block.")) {
                return;
            }

            int result = invokeSelectJob(player.createCommandSourceStack(), manager, AnimalTowerJob.ID.getPath());
            if (!assertEquals(context, 0, result, "Active match participants should not be able to change job mid-match.")) {
                return;
            }
            if (!assertTrue(
                    context,
                    manager.profile(server, player.getUUID(), player.getGameProfile().getName()).selectedJobResource().isEmpty(),
                    "Blocked mid-match participant job selection should not update the stored profile."
            )) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Running participant job block test failed: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void sandboxHotbarToolsOpenSandboxUi(GameTestHelper context) {
        SemionGameManager manager = new SemionGameManager();
        try {
            configureManager(manager);
            MinecraftServer server = context.getLevel().getServer();
            var player = context.makeMockServerPlayerInLevel();
            GameArena sandboxArena = SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO));
            SemionGameManager.SandboxStartResult startResult = manager.startSandbox(
                    server,
                    player.getUUID(),
                    player.getGameProfile().getName(),
                    sandboxArena
            );
            if (!assertEquals(context, SemionGameManager.SandboxStartResult.STARTED, startResult, "Mock player should be able to start sandbox.")) {
                return;
            }
            if (!assertTrue(context, player.getInventory().getItem(0).is(Items.COMPASS), "Sandbox should grant tower compass.")) {
                return;
            }
            if (!assertTrue(context, player.getInventory().getItem(1).is(Items.ECHO_SHARD), "Sandbox should grant income summon item.")) {
                return;
            }
            if (!assertEquals(
                    context,
                    "샌드박스 몹 소환",
                    player.getInventory().getItem(1).get(DataComponents.CUSTOM_NAME).getString(),
                    "Sandbox summon item should describe its sandbox-only behavior."
            )) {
                return;
            }
            if (!assertTrue(context, player.getInventory().getItem(2).is(Items.CLOCK), "Sandbox should replace the leader tool with a round control clock.")) {
                return;
            }
            if (!assertEquals(
                    context,
                    "라운드 이동",
                    player.getInventory().getItem(2).get(DataComponents.CUSTOM_NAME).getString(),
                    "Sandbox round control clock should have a visible name."
            )) {
                return;
            }

            player.setItemInHand(InteractionHand.MAIN_HAND, player.getInventory().getItem(0));
            InteractionResult towerResult = invokeHotbarUse(manager, player, player.level());
            if (!assertEquals(context, InteractionResult.SUCCESS, towerResult, "Tower compass should open the sandbox tower UI.")) {
                return;
            }

            player.setItemInHand(InteractionHand.MAIN_HAND, player.getInventory().getItem(1));
            InteractionResult summonResult = invokeHotbarUse(manager, player, player.level());
            if (!assertEquals(context, InteractionResult.SUCCESS, summonResult, "Income item should open the sandbox summon UI.")) {
                return;
            }

            player.setItemInHand(InteractionHand.MAIN_HAND, player.getInventory().getItem(2));
            InteractionResult roundResult = invokeHotbarUse(manager, player, player.level());
            if (!assertEquals(context, InteractionResult.SUCCESS, roundResult, "Round clock should open the sandbox round UI.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Sandbox hotbar UI test failed: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void sandboxSummonsAreFreeAndRoundJumpPreservesBuildAndEconomy(GameTestHelper context) {
        SemionGameManager manager = new SemionGameManager();
        try {
            configureManager(manager);
            MinecraftServer server = context.getLevel().getServer();
            var player = context.makeMockServerPlayerInLevel();
            UUID ownerId = player.getUUID();
            if (!assertEquals(
                    context,
                    SemionGameManager.SandboxStartResult.STARTED,
                    manager.startSandbox(
                            server,
                            ownerId,
                            player.getGameProfile().getName(),
                            SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
                    ),
                    "Sandbox should start before free summon and round movement checks."
            )) {
                return;
            }

            SemionGame sandbox = manager.sandboxGame(ownerId).orElseThrow();
            PlayerLane ownerLane = sandbox.playerLane(ownerId).orElseThrow();
            String towerId = ProductionTowerService.availableTowers(sandbox, ownerId).getFirst().type().id();
            if (!assertEquals(
                    context,
                    TowerPlacementResult.SUCCESS,
                    ProductionTowerService.placeTower(sandbox, ownerId, ownerLane.laneLayout().laneArea().min(), towerId),
                    "Sandbox should place a tower before moving rounds."
            )) {
                return;
            }

            var economy = sandbox.players().get(ownerId).economy();
            economy.spendEmerald(economy.emerald());
            long incomeBeforeSummon = economy.income();
            SummonResult summon = sandbox.summonMonster(ownerId, "chicken");
            if (!assertEquals(context, SummonResultType.SUCCESS, summon.type(), "Sandbox summon should succeed with zero emerald.")) {
                return;
            }
            if (!assertEquals(context, 0L, economy.emerald(), "Free sandbox summon should not spend emerald.")) {
                return;
            }
            if (!assertEquals(context, incomeBeforeSummon, economy.income(), "Free sandbox summon should not increase income.")) {
                return;
            }
            if (!assertEquals(context, 1, ownerLane.queuedSummonCount(), "Free summon should still queue on the owner's lane.")) {
                return;
            }

            for (int tick = 0; tick < SemionGame.DEFAULT_PREPARE_TICKS + 2; tick++) {
                manager.tick(server);
            }
            if (!assertEquals(context, RoundPhase.LANE_WAVE, sandbox.phase(), "Sandbox should enter combat before the round jump.")) {
                return;
            }
            if (!assertTrue(context, !ownerLane.activeMonsters().isEmpty(), "Combat setup should have an active monster to clear.")) {
                return;
            }

            sandbox.teams().get(TeamId.RED).laneGroup().boss().damage(7.0D);
            double bossHealthBeforeJump = sandbox.teams().get(TeamId.RED).laneGroup().boss().health();
            long diamondBeforeJump = economy.diamond();
            long emeraldBeforeJump = economy.emerald();
            long incomeBeforeJump = economy.income();
            if (!assertTrue(context, manager.moveSandboxToRound(server, ownerId, 12), "Sandbox owner should move directly to round 12.")) {
                return;
            }
            if (!assertEquals(context, 12, sandbox.currentRound(), "Round jump should set the requested round.")) {
                return;
            }
            if (!assertEquals(context, RoundPhase.PREPARE_AND_SUMMON, sandbox.phase(), "Round jump should restart in prepare phase.")) {
                return;
            }
            if (!assertTrue(context, ownerLane.activeMonsters().isEmpty(), "Round jump should remove active monsters.")) {
                return;
            }
            if (!assertEquals(context, 0, ownerLane.queuedSummonCount(), "Round jump should remove queued current-round summons.")) {
                return;
            }
            if (!assertEquals(context, 0, ownerLane.pendingNextRoundSummonCount(), "Round jump should remove queued next-round summons.")) {
                return;
            }
            if (!assertEquals(context, 1, sandbox.towerCount(ownerId), "Round jump should preserve placed towers.")) {
                return;
            }
            if (!assertEquals(context, bossHealthBeforeJump, sandbox.teams().get(TeamId.RED).laneGroup().boss().health(), "Round jump should preserve boss health.")) {
                return;
            }
            if (!assertEquals(context, diamondBeforeJump, economy.diamond(), "Round jump should not award skipped-round diamond income.")) {
                return;
            }
            if (!assertEquals(context, emeraldBeforeJump, economy.emerald(), "Round jump should preserve emerald.")) {
                return;
            }
            if (!assertEquals(context, incomeBeforeJump, economy.income(), "Round jump should preserve income.")) {
                return;
            }
            if (!assertTrue(context, !sandbox.upcomingWaveEntries(ownerId).isEmpty(), "Round jump should select the target round wave.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Sandbox free summon and round movement test failed: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void sandboxSpectatorSwitchesSessionsAndCleansUpIndependently(GameTestHelper context) {
        SemionGameManager manager = new SemionGameManager();
        try {
            configureManager(manager);
            MinecraftServer server = context.getLevel().getServer();
            var spectator = context.makeMockServerPlayerInLevel();
            UUID spectatorId = spectator.getUUID();
            UUID ownerId = uuid("sandbox-spectate-target-owner");

            if (!assertEquals(
                    context,
                    SemionGameManager.SandboxStartResult.STARTED,
                    manager.startSandbox(
                            server,
                            spectatorId,
                            spectator.getGameProfile().getName(),
                            SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
                    ),
                    "Spectator should start with an owned sandbox."
            )) {
                return;
            }
            if (!assertEquals(
                    context,
                    SemionGameManager.SandboxStartResult.STARTED,
                    manager.startSandbox(
                            server,
                            ownerId,
                            "sandbox-spectate-target-owner",
                            SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(new BlockPos(200, 0, 0)))
                    ),
                    "Target sandbox should start independently."
            )) {
                return;
            }
            SemionGame targetSandbox = manager.sandboxGame(ownerId).orElseThrow();

            if (!assertEquals(
                    context,
                    SemionGameManager.SandboxSpectateResult.SUCCESS,
                    manager.spectateSandbox(server, spectator, ownerId),
                    "Player should switch from their own sandbox to target spectating."
            )) {
                return;
            }
            if (!assertTrue(context, manager.sandboxGame(spectatorId).isEmpty(), "Entering spectate should close the viewer's owned sandbox.")) {
                return;
            }
            if (!assertTrue(context, targetSandbox.isMatchSpectator(spectatorId), "Target sandbox should track the spectator.")) {
                return;
            }
            if (!assertTrue(context, manager.sandboxSpectatorGame(spectatorId).orElse(null) == targetSandbox, "Spectator lookup should resolve the target sandbox.")) {
                return;
            }
            if (!assertTrue(context, manager.playableGame(spectatorId).isEmpty(), "Spectator must not receive sandbox gameplay command access.")) {
                return;
            }
            if (!assertTrue(context, manager.protectionGame(spectatorId) == targetSandbox, "Spectator protection should use the viewed sandbox.")) {
                return;
            }
            if (!assertEquals(context, GameType.SPECTATOR, spectator.gameMode.getGameModeForPlayer(), "Viewer should enter spectator mode.")) {
                return;
            }
            if (!assertTrue(context, spectator.getInventory().getItem(0).isEmpty(), "Spectator should not retain sandbox tools.")) {
                return;
            }

            manager.handlePlayerDisconnect(spectator);
            if (!assertTrue(context, manager.sandboxGame(ownerId).orElse(null) == targetSandbox, "Spectator disconnect must not stop the target sandbox.")) {
                return;
            }
            if (!assertTrue(context, !targetSandbox.isMatchSpectator(spectatorId), "Spectator disconnect should unregister only the viewer.")) {
                return;
            }

            if (!assertEquals(
                    context,
                    SemionGameManager.SandboxSpectateResult.SUCCESS,
                    manager.spectateSandbox(server, spectator, ownerId),
                    "Viewer should be able to re-enter the remaining target sandbox."
            )) {
                return;
            }
            if (!assertTrue(context, manager.stopSandbox(server, ownerId), "Stopping the target sandbox should succeed.")) {
                return;
            }
            if (!assertTrue(context, manager.sandboxSpectatorGame(spectatorId).isEmpty(), "Target shutdown should clear spectator ownership.")) {
                return;
            }
            if (!assertEquals(context, GameType.ADVENTURE, spectator.gameMode.getGameModeForPlayer(), "Target shutdown should return online spectators to the lobby.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Sandbox spectator lifecycle test failed: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void disconnectingOneSandboxOwnerDoesNotStopOtherSandboxes(GameTestHelper context) {
        SemionGameManager manager = new SemionGameManager();
        try {
            configureManager(manager);
            MinecraftServer server = context.getLevel().getServer();
            var leavingPlayer = context.makeMockServerPlayerInLevel();
            UUID remainingOwnerId = uuid("sandbox-disconnect-remaining-owner");

            SemionGameManager.SandboxStartResult leavingStart = manager.startSandbox(
                    server,
                    leavingPlayer.getUUID(),
                    leavingPlayer.getGameProfile().getName(),
                    SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
            );
            if (!assertEquals(context, SemionGameManager.SandboxStartResult.STARTED, leavingStart, "Leaving player sandbox should start.")) {
                return;
            }
            SemionGameManager.SandboxStartResult remainingStart = manager.startSandbox(
                    server,
                    remainingOwnerId,
                    "sandbox-disconnect-remaining-owner",
                    SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(new BlockPos(200, 0, 0)))
            );
            if (!assertEquals(context, SemionGameManager.SandboxStartResult.STARTED, remainingStart, "Second sandbox should start independently.")) {
                return;
            }
            SemionGame remainingSandbox = manager.sandboxGame(remainingOwnerId).orElseThrow();
            int phaseTicksBeforeDisconnectTick = remainingSandbox.phaseTicks();

            manager.handlePlayerDisconnect(leavingPlayer);

            if (!assertTrue(context, manager.sandboxGame(leavingPlayer.getUUID()).isEmpty(), "Disconnect should remove only the leaving player's sandbox.")) {
                return;
            }
            if (!assertTrue(context, manager.sandboxGame(remainingOwnerId).orElse(null) == remainingSandbox, "Other sandbox should remain registered after one owner disconnects.")) {
                return;
            }

            manager.tick(server);

            if (!assertTrue(context, remainingSandbox.phaseTicks() > phaseTicksBeforeDisconnectTick, "Remaining sandbox should keep ticking after another sandbox owner disconnects.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Sandbox disconnect isolation test failed: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void sandboxStartsWithNormalEconomyAndCanGrantCurrency(GameTestHelper context) {
        SemionGameManager manager = new SemionGameManager();
        try {
            configureManager(manager);
            MinecraftServer server = context.getLevel().getServer();
            UUID sandboxOwnerId = uuid("sandbox-economy-owner");
            GameArena sandboxArena = SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO));
            SemionGameManager.SandboxStartResult startResult = manager.startSandbox(
                    server,
                    sandboxOwnerId,
                    "sandbox-economy-owner",
                    sandboxArena
            );
            if (!assertEquals(context, SemionGameManager.SandboxStartResult.STARTED, startResult, "Sandbox should start for economy test.")) {
                return;
            }
            SemionGame sandboxGame = manager.sandboxGame(sandboxOwnerId).orElseThrow();
            var economy = sandboxGame.players().get(sandboxOwnerId).economy();
            EconomyConfig defaultEconomy = EconomyConfig.defaultConfig();
            if (!assertEquals(context, defaultEconomy.startingDiamond(), economy.diamond(), "Sandbox should use normal starting diamond, not infinite resources.")) {
                return;
            }
            if (!assertEquals(context, defaultEconomy.startingEmerald(), economy.emerald(), "Sandbox should use normal starting emerald, not infinite resources.")) {
                return;
            }
            if (!assertEquals(context, defaultEconomy.startingIncome(), economy.income(), "Sandbox should use normal starting income.")) {
                return;
            }

            if (!assertTrue(context, manager.grantSandboxCurrency(sandboxOwnerId, SemionGameManager.SandboxCurrency.DIAMOND, 123), "Sandbox diamond grant command backend should succeed.")) {
                return;
            }
            if (!assertTrue(context, manager.grantSandboxCurrency(sandboxOwnerId, SemionGameManager.SandboxCurrency.EMERALD, 45), "Sandbox emerald grant command backend should succeed.")) {
                return;
            }
            if (!assertTrue(context, manager.grantSandboxCurrency(sandboxOwnerId, SemionGameManager.SandboxCurrency.INCOME, 6), "Sandbox income grant command backend should succeed.")) {
                return;
            }
            if (!assertEquals(context, defaultEconomy.startingDiamond() + 123, economy.diamond(), "Sandbox diamond grant should add to the sandbox owner only.")) {
                return;
            }
            if (!assertEquals(context, defaultEconomy.startingEmerald() + 45, economy.emerald(), "Sandbox emerald grant should add to the sandbox owner only.")) {
                return;
            }
            if (!assertEquals(context, defaultEconomy.startingIncome() + 6, economy.income(), "Sandbox income grant should add to the sandbox owner only.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Sandbox economy test failed: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void sandboxEliminationDoesNotSpectateOwnerOrBroadcastMatchFlow(GameTestHelper context) {
        SemionGameManager manager = new SemionGameManager();
        try {
            configureManager(manager);
            MinecraftServer server = context.getLevel().getServer();
            var player = context.makeMockServerPlayerInLevel();
            GameArena sandboxArena = SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO));
            SemionGameManager.SandboxStartResult startResult = manager.startSandbox(
                    server,
                    player.getUUID(),
                    player.getGameProfile().getName(),
                    sandboxArena
            );
            if (!assertEquals(context, SemionGameManager.SandboxStartResult.STARTED, startResult, "Sandbox should start for elimination test.")) {
                return;
            }
            SemionGame sandboxGame = manager.sandboxGame(player.getUUID()).orElseThrow();
            if (!assertTrue(context, player.getInventory().getItem(0).is(Items.COMPASS), "Sandbox owner should start with tower compass.")) {
                return;
            }

            if (!assertTrue(context, sandboxGame.killBoss(server, TeamId.RED), "Sandbox owner team boss should be killable for regression setup.")) {
                return;
            }
            if (!assertTrue(context, !sandboxGame.isMatchSpectator(player.getUUID()), "Sandbox owner elimination should not run real-match spectator conversion.")) {
                return;
            }
            if (!assertTrue(context, player.getInventory().getItem(0).is(Items.COMPASS), "Sandbox owner should keep sandbox tools after sandbox-only elimination.")) {
                return;
            }
            if (!assertTrue(context, manager.leaveSandbox(server, player), "Sandbox should still be leaveable after a sandbox-only elimination.")) {
                return;
            }
            if (!assertTrue(context, manager.sandboxGame(player.getUUID()).isEmpty(), "Leaving should remove sandbox after elimination.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Sandbox elimination test failed: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void sandboxDummyTeamDoesNotSpawnRoundWaveMonsters(GameTestHelper context) {
        SemionGameManager manager = new SemionGameManager();
        try {
            configureManager(manager);
            MinecraftServer server = context.getLevel().getServer();
            UUID sandboxOwnerId = uuid("sandbox-dummy-wave-owner");
            SemionGameManager.SandboxStartResult startResult = manager.startSandbox(
                    server,
                    sandboxOwnerId,
                    "sandbox-dummy-wave-owner",
                    SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
            );
            if (!assertEquals(context, SemionGameManager.SandboxStartResult.STARTED, startResult, "Sandbox should start before dummy wave test.")) {
                return;
            }

            SemionGame sandboxGame = manager.sandboxGame(sandboxOwnerId).orElseThrow();
            for (int tick = 0; tick < SemionGame.DEFAULT_PREPARE_TICKS + 2; tick++) {
                manager.tick(server);
            }
            if (!assertEquals(context, RoundPhase.LANE_WAVE, sandboxGame.phase(), "Sandbox should enter the first wave phase.")) {
                return;
            }
            if (!assertTrue(context, !lane(sandboxGame, TeamId.RED, 1).activeMonsters().isEmpty(), "Sandbox owner team should still spawn normal round wave monsters.")) {
                return;
            }
            if (!assertTrue(context, lane(sandboxGame, TeamId.BLUE, 1).activeMonsters().isEmpty(), "Sandbox dummy team must not spawn round wave monsters.")) {
                return;
            }
            if (!assertTrue(context, !sandboxGame.teams().get(TeamId.BLUE).eliminated(), "Sandbox dummy team should not be eliminated by skipped round waves.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Sandbox dummy wave test failed: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void laneMonsterTargetsOnlyItsRuntimeTargetTeamBoss(GameTestHelper context) {
        Vec3 bossPosition = context.absolutePos(BlockPos.ZERO).getCenter().add(4.0, 2.0, 4.0);
        LaneRegionLayout laneLayout = new LaneRegionLayout(
                1,
                bossPosition.add(0.0, 0.0, -4.0),
                List.of(bossPosition.add(0.0, 0.0, -2.0)),
                bossPosition,
                BlockBounds.of(
                        new BlockPos((int) bossPosition.x - 2, (int) bossPosition.y - 1, (int) bossPosition.z - 4),
                        new BlockPos((int) bossPosition.x + 2, (int) bossPosition.y + 2, (int) bossPosition.z)
                ),
                List.of(new GridPosition((int) bossPosition.x, (int) bossPosition.y, (int) bossPosition.z))
        );

        SemionBossEntity blueBoss = new SemionBossEntity(SemionEntityTypes.BOSS, context.getLevel());
        blueBoss.configure(TeamId.BLUE, BossMonster.defaultBoss(TeamId.BLUE));
        blueBoss.setPos(bossPosition.add(1.0, 0.0, 0.0));
        context.getLevel().addFreshEntity(blueBoss);

        SemionBossEntity redBoss = new SemionBossEntity(SemionEntityTypes.BOSS, context.getLevel());
        redBoss.configure(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        redBoss.setPos(bossPosition);
        context.getLevel().addFreshEntity(redBoss);

        Monster monster = new Monster(
                "sandbox-self-target-check",
                TeamId.RED,
                1,
                Optional.empty(),
                Optional.of(TeamId.BLUE),
                100.0,
                0.0,
                3.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                DamageType.PHYSICAL,
                0,
                SummonTier.T1,
                List.of(SummonRole.RUSH),
                0
        );
        SemionMonsterEntity monsterEntity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        monsterEntity.configureFrom(monster, laneLayout);
        monsterEntity.setPos(bossPosition);
        context.getLevel().addFreshEntity(monsterEntity);

        LaneFollowGoal goal = new LaneFollowGoal(monsterEntity, 1.0);
        goal.start();
        goal.tick();

        if (!assertTrue(context, monsterEntity.getTarget() == redBoss, "Lane monsters should target the boss for their runtime target team only.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void sandboxDummyBossUnloadDoesNotEliminateDummyTeam(GameTestHelper context) {
        SemionGameManager manager = new SemionGameManager();
        try {
            configureManager(manager);
            MinecraftServer server = context.getLevel().getServer();
            UUID sandboxOwnerId = uuid("sandbox-dummy-boss-unload-owner");
            SemionGameManager.SandboxStartResult startResult = manager.startSandbox(
                    server,
                    sandboxOwnerId,
                    "sandbox-dummy-boss-unload-owner",
                    SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
            );
            if (!assertEquals(context, SemionGameManager.SandboxStartResult.STARTED, startResult, "Sandbox should start before dummy boss unload test.")) {
                return;
            }

            SemionGame sandboxGame = manager.sandboxGame(sandboxOwnerId).orElseThrow();
            var blueBoss = sandboxGame.teams().get(TeamId.BLUE).laneGroup().bossEntity().orElseThrow();
            blueBoss.remove(RemovalReason.UNLOADED_TO_CHUNK);
            for (int tick = 0; tick < 5; tick++) {
                manager.tick(server);
            }

            if (!assertTrue(context, !sandboxGame.teams().get(TeamId.BLUE).eliminated(), "Unloading the sandbox dummy boss entity must not eliminate the dummy team.")) {
                return;
            }
            if (!assertTrue(context, sandboxGame.teams().get(TeamId.BLUE).laneGroup().boss().isAlive(), "Unloading the sandbox dummy boss entity must not kill the runtime boss.")) {
                return;
            }
            if (!assertTrue(context, sandboxGame.phase() != RoundPhase.ENDED, "Unloading the sandbox dummy boss entity must not end the sandbox.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Sandbox dummy boss unload test failed: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void sandboxCurrencyGrantDoesNotAffectActiveMatch(GameTestHelper context) {
        SemionGameManager manager = new SemionGameManager();
        try {
            configureManager(manager);
            MinecraftServer server = context.getLevel().getServer();
            SemionGame activeGame = manager.createGame(server);
            UUID activeRedId = uuid("sandbox-currency-active-red");
            UUID activeBlueId = uuid("sandbox-currency-active-blue");
            if (!assertTrue(context, activeGame.start(server, new ParticipantSelectionPlan(
                    MatchMode.TEST,
                    List.of(
                            new AssignedParticipant(activeRedId, "active-red", TeamId.RED, 1),
                            new AssignedParticipant(activeBlueId, "active-blue", TeamId.BLUE, 1)
                    ),
                    Set.of(),
                    2
            )), "Active match should start for currency isolation test.")) {
                return;
            }
            long activeDiamondBefore = activeGame.players().get(activeRedId).economy().diamond();
            if (!assertTrue(context, !manager.grantSandboxCurrency(activeRedId, SemionGameManager.SandboxCurrency.DIAMOND, 999), "Active match player without sandbox should not receive sandbox currency.")) {
                return;
            }
            if (!assertEquals(context, activeDiamondBefore, activeGame.players().get(activeRedId).economy().diamond(), "Failed sandbox grant must not mutate active match economy.")) {
                return;
            }

            UUID sandboxOwnerId = uuid("sandbox-currency-owner");
            SemionGameManager.SandboxStartResult startResult = manager.startSandbox(
                    server,
                    sandboxOwnerId,
                    "sandbox-currency-owner",
                    SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(new BlockPos(100, 0, 0)))
            );
            if (!assertEquals(context, SemionGameManager.SandboxStartResult.STARTED, startResult, "Non-participant sandbox should start beside active match.")) {
                return;
            }
            if (!assertTrue(context, manager.grantSandboxCurrency(sandboxOwnerId, SemionGameManager.SandboxCurrency.DIAMOND, 999), "Sandbox owner should receive sandbox currency.")) {
                return;
            }
            if (!assertEquals(context, activeDiamondBefore, activeGame.players().get(activeRedId).economy().diamond(), "Sandbox currency grant must not mutate active match economy.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Sandbox currency isolation test failed: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void sandboxResetRestoresNormalEconomy(GameTestHelper context) {
        SemionGameManager manager = new SemionGameManager();
        try {
            configureManager(manager);
            MinecraftServer server = context.getLevel().getServer();
            UUID sandboxOwnerId = uuid("sandbox-reset-economy-owner");
            SemionGameManager.SandboxStartResult startResult = manager.startSandbox(
                    server,
                    sandboxOwnerId,
                    "sandbox-reset-economy-owner",
                    SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
            );
            if (!assertEquals(context, SemionGameManager.SandboxStartResult.STARTED, startResult, "Sandbox should start before reset.")) {
                return;
            }
            if (!assertTrue(context, manager.grantSandboxCurrency(sandboxOwnerId, SemionGameManager.SandboxCurrency.DIAMOND, 999), "Sandbox grant before reset should succeed.")) {
                return;
            }
            SemionGameManager.SandboxStartResult resetResult = manager.startSandbox(
                    server,
                    sandboxOwnerId,
                    "sandbox-reset-economy-owner",
                    SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(new BlockPos(200, 0, 0)))
            );
            if (!assertEquals(context, SemionGameManager.SandboxStartResult.REPLACED, resetResult, "Starting sandbox again should replace the old session.")) {
                return;
            }
            var economy = manager.sandboxGame(sandboxOwnerId).orElseThrow().players().get(sandboxOwnerId).economy();
            if (!assertEquals(context, EconomyConfig.defaultConfig().startingDiamond(), economy.diamond(), "Reset sandbox should restore normal starting diamond instead of carrying grants.")) {
                return;
            }
            if (!assertEquals(context, EconomyConfig.defaultConfig().startingEmerald(), economy.emerald(), "Reset sandbox should restore normal starting emerald.")) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Sandbox reset economy test failed: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    @GameTest
    public void sandboxTraitSelectionAppliesImmediatelyAndSurvivesReset(GameTestHelper context) {
        SemionGameManager manager = new SemionGameManager();
        try {
            configureManager(manager);
            MinecraftServer server = context.getLevel().getServer();
            UUID sandboxOwnerId = uuid("sandbox-trait-owner");
            SemionGameManager.SandboxStartResult startResult = manager.startSandbox(
                    server,
                    sandboxOwnerId,
                    "sandbox-trait-owner",
                    SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO))
            );
            if (!assertEquals(context, SemionGameManager.SandboxStartResult.STARTED, startResult, "Sandbox should start before selecting traits.")) {
                return;
            }

            SemionGame sandbox = manager.sandboxGame(sandboxOwnerId).orElseThrow();
            PlayerLane sandboxLane = sandbox.playerLane(sandboxOwnerId).orElseThrow();
            String starterTowerId = ProductionTowerService.availableTowers(sandbox, sandboxOwnerId).stream()
                    .findFirst()
                    .orElseThrow()
                    .type()
                    .id();
            BlockPos towerPos = sandboxLane.laneLayout().laneArea().min();
            if (!assertEquals(
                    context,
                    TowerPlacementResult.SUCCESS,
                    ProductionTowerService.placeTower(sandbox, sandboxOwnerId, towerPos, starterTowerId),
                    "Sandbox tower placement should succeed before trait selection."
            )) {
                return;
            }
            double baseMaxHealth = sandboxLane.towers().getFirst().currentMaxHealth();

            if (!assertEquals(
                    context,
                    TraitSelectionSession.SelectionResult.SELECTED,
                    manager.selectTrait(server, sandboxOwnerId, TraitSlot.PRIMARY, BuiltInTraits.FORTITUDE_ID),
                    "Running sandbox should accept a primary trait."
            )) {
                return;
            }
            Tower selectedTower = sandboxLane.towers().getFirst();
            double maxHealthBonus = TraitEffects.towerMaxHealthBonus(
                    sandbox.players().get(sandboxOwnerId).traitLoadout(), selectedTower
            );
            if (!assertTrue(
                    context,
                    Math.abs(selectedTower.currentMaxHealth() - baseMaxHealth * (1.0D + maxHealthBonus)) < 0.0001D,
                    "Fortitude should immediately update an existing sandbox tower."
            )) {
                return;
            }
            if (!assertEquals(
                    context,
                    TraitSelectionSession.SelectionResult.SELECTED,
                    manager.selectTrait(server, sandboxOwnerId, TraitSlot.SECONDARY, BuiltInTraits.MOBILIZATION_GRANT_ID),
                    "Running sandbox should accept a secondary trait."
            )) {
                return;
            }
            if (!assertEquals(
                    context,
                    BuiltInTraits.FORTITUDE_ID,
                    sandbox.players().get(sandboxOwnerId).traitLoadout().primaryTraitId(),
                    "Sandbox player should receive the selected trait immediately."
            )) {
                return;
            }
            if (!assertEquals(
                    context,
                    BuiltInTraits.MOBILIZATION_GRANT_ID,
                    sandboxLane.traitLoadout().secondaryTraitId(),
                    "Sandbox lane should receive the selected trait immediately."
            )) {
                return;
            }

            SemionGameManager.SandboxStartResult resetResult = manager.startSandbox(
                    server,
                    sandboxOwnerId,
                    "sandbox-trait-owner",
                    SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(new BlockPos(200, 0, 0)))
            );
            if (!assertEquals(context, SemionGameManager.SandboxStartResult.REPLACED, resetResult, "Sandbox reset should retain trait selection.")) {
                return;
            }
            SemionGame resetSandbox = manager.sandboxGame(sandboxOwnerId).orElseThrow();
            if (!assertEquals(
                    context,
                    BuiltInTraits.MOBILIZATION_GRANT_ID,
                    manager.traitLoadoutOrDefault(sandboxOwnerId).secondaryTraitId(),
                    "Sandbox reset should retain the selected secondary trait."
            )) {
                return;
            }
            long expectedDiamond = EconomyConfig.defaultConfig().startingDiamond()
                    + TraitEffects.startingMineralBonus(manager.traitLoadoutOrDefault(sandboxOwnerId));
            if (!assertEquals(
                    context,
                    expectedDiamond,
                    resetSandbox.players().get(sandboxOwnerId).economy().diamond(),
                    "Starting-resource traits should apply when the sandbox is reset."
            )) {
                return;
            }
            context.succeed();
        } catch (Exception exception) {
            context.fail(Component.literal("Sandbox trait selection test failed: " + exception.getMessage()));
        } finally {
            manager.shutdown();
        }
    }

    private static void configureManager(SemionGameManager manager) throws Exception {
        Path tempDir = Files.createTempDirectory("semion-sandbox-gametest");
        manager.configure(
                EconomyConfig.defaultConfig(),
                WaveConfig.defaultConfig(),
                MapConfig.defaultConfig(),
                ProgressionConfig.defaultConfig(),
                TowerBalanceConfig.defaultConfig(),
                SummonConfig.defaultConfig(),
                tempDir.resolve("progression.json")
        );
    }

    private static UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    private static InteractionResult invokeHotbarUse(SemionGameManager manager, Player player, Level world) throws Exception {
        java.lang.reflect.Method method = SemionHotbarService.class.getDeclaredMethod(
                "handleUse",
                SemionGameManager.class,
                Player.class,
                Level.class,
                InteractionHand.class
        );
        method.setAccessible(true);
        return (InteractionResult) method.invoke(null, manager, player, world, InteractionHand.MAIN_HAND);
    }

    private static int invokeSelectJob(CommandSourceStack source, SemionGameManager manager, String rawJobId) throws Exception {
        Method method = SemionCommands.class.getDeclaredMethod(
                "selectJob",
                CommandSourceStack.class,
                SemionGameManager.class,
                String.class
        );
        method.setAccessible(true);
        return (int) method.invoke(null, source, manager, rawJobId);
    }

    private static PlayerLane lane(SemionGame game, TeamId teamId, int laneId) {
        return game.teams().get(teamId).laneGroup().lane(laneId).orElseThrow();
    }

    private static boolean assertTrue(GameTestHelper context, boolean condition, String message) {
        if (!condition) {
            context.fail(Component.literal(message));
            return false;
        }
        return true;
    }

    private static <T> boolean assertEquals(GameTestHelper context, T expected, T actual, String message) {
        if (!expected.equals(actual)) {
            context.fail(Component.literal(message + " expected=" + expected + " actual=" + actual));
            return false;
        }
        return true;
    }
}
