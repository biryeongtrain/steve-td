package kim.biryeong.semiontd.game;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.gametest.GameTestParticipantFixture;
import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.NetherTowerJob;
import kim.biryeong.semiontd.trait.BuiltInTraits;
import kim.biryeong.semiontd.trait.TraitLoadout;
import kim.biryeong.semiontd.trait.TraitSelectionConfig;
import kim.biryeong.semiontd.trait.TraitSlot;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

public final class GameParticipantLateJoinTest extends GameTestParticipantFixture {
    @GameTest
    public void missingJobRejectsRequestAndCommitWhileSelectedJobAndReconnectRemainValid(GameTestHelper context) {
        var server = context.getLevel().getServer();
        var player = context.makeMockServerPlayerInLevel();
        var game = new SemionGame(EconomyConfig.defaultConfig(), new WaveConfig(List.of(), 20, null),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO)));
        var manager = new SemionGameManager();
        manager.configureTraits(new TraitSelectionConfig(true, 45));
        setField(manager, "activeGame", game);
        try {
            context.assertValueEqual(SemionGameManager.LateJoinResult.INVALID_GAME,
                    manager.requestLateJoin(server, player), "Waiting lobby cannot use mid-game entry");
            var plan = new ParticipantSelectionPlan(MatchMode.NORMAL, List.of(
                    new AssignedParticipant(UUID.randomUUID(), "red", TeamId.RED, 1),
                    new AssignedParticipant(UUID.randomUUID(), "blue", TeamId.BLUE, 1)), Set.of(player.getUUID()), 2);
            context.assertTrue(game.start(server, plan), "Match starts");
            var assignment = new AssignedParticipant(player.getUUID(), player.getGameProfile().name(), TeamId.RED, 2);
            for (var phase : List.of(RoundPhase.PREPARE_AND_SUMMON, RoundPhase.LANE_WAVE)) {
                setField(game, "phase", phase);
                context.assertValueEqual(SemionGameManager.LateJoinResult.JOB_REQUIRED,
                        manager.requestLateJoin(server, player), "Missing job is rejected before reserving a slot");
                context.assertTrue(!game.addLateParticipant(server, player, assignment, TraitLoadout.none(),
                        JobRegistry.defaultJob(), 1), "Direct entry cannot bypass the job requirement");
            }
            context.assertTrue(game.isMatchSpectator(player.getUUID()) && !game.isActiveParticipant(player.getUUID()),
                    "Rejected player remains a spectator without a lane");
            manager.saveSelectedJob(server, player.getUUID(), player.getGameProfile().name(), NetherTowerJob.ID);
            context.assertValueEqual(SemionGameManager.LateJoinResult.SELECTION_STARTED,
                    manager.requestLateJoin(server, player), "Valid selected job permits late entry");
            context.assertValueEqual(SemionGameManager.LateJoinResult.ALREADY_PENDING,
                    manager.requestLateJoin(server, player), "Duplicate request does not reserve another lane");
            manager.saveSelectedJob(server, player.getUUID(), player.getGameProfile().name(), JobRegistry.defaultJob().id());
            selectNone(manager, server, player.getUUID());
            context.assertTrue(!game.isActiveParticipant(player.getUUID()), "Job lost during trait selection is rejected at commit");
            manager.saveSelectedJob(server, player.getUUID(), player.getGameProfile().name(), NetherTowerJob.ID);
            context.assertValueEqual(SemionGameManager.LateJoinResult.SELECTION_STARTED,
                    manager.requestLateJoin(server, player), "Rejected reservation can be retried after selecting a job");
            selectNone(manager, server, player.getUUID());
            context.assertTrue(game.isActiveParticipant(player.getUUID()), "Valid late participant is activated");
            context.assertValueEqual(NetherTowerJob.ID, game.players().get(player.getUUID()).job().orElseThrow().id(), "Selected job survives activation");
            manager.saveSelectedJob(server, player.getUUID(), player.getGameProfile().name(), JobRegistry.defaultJob().id());
            context.assertValueEqual(SemionGameManager.LateJoinResult.ALREADY_PARTICIPANT,
                    manager.requestLateJoin(server, player), "Existing participant is not revalidated as a new late entrant");
            context.assertTrue(game.restorePlayerPlacement(server, player), "Existing participant can restore the locked match job");
            context.assertValueEqual(NetherTowerJob.ID, game.players().get(player.getUUID()).job().orElseThrow().id(), "Reconnect preserves the match job");
            context.succeed();
        } finally {
            manager.shutdown();
        }
    }

    private static void selectNone(SemionGameManager manager, net.minecraft.server.MinecraftServer server, UUID id) {
        manager.selectTrait(server, id, TraitSlot.PRIMARY, BuiltInTraits.NONE_ID);
        manager.selectTrait(server, id, TraitSlot.SECONDARY, BuiltInTraits.NONE_ID);
    }
}
