package kim.biryeong.semiontd.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.MapConfig;
import kim.biryeong.semiontd.config.ProgressionConfig;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.summon.SummonResult;
import kim.biryeong.semiontd.summon.SummonResultType;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public final class SemionSandboxGameTest {
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
            SummonResult summon = sandboxGame.summonMonster(sandboxOwnerId, "chicken");
            if (!assertEquals(context, SummonResultType.SUCCESS, summon.type(), "Sandbox income summon should use the sandbox game only.")) {
                return;
            }
            if (!assertTrue(context, summon.targetTeam().filter(TeamId.BLUE::equals).isPresent(), "Sandbox income should target the sandbox dummy enemy team.")) {
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
