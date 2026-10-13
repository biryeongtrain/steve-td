package kim.biryeong.semiontd.game;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.gametest.GameTestParticipantFixture;
import kim.biryeong.semiontd.gametest.RuntimePlayerFixture;
import kim.biryeong.semiontd.job.NetherTowerJob;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class GameLeaderToolTest extends GameTestParticipantFixture {
    @GameTest
    public void normalMatchGrantsThirdSlotToHighestEloLeaderAndRestoresItWithoutDuplicates(GameTestHelper context) {
        var server = context.getLevel().getServer();
        var position = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        try (var low = RuntimePlayerFixture.connect(context, context.getLevel(), position, GameType.SURVIVAL, UUID.randomUUID(), "leader-low");
                var high = RuntimePlayerFixture.connect(context, context.getLevel(), position, GameType.SURVIVAL, UUID.randomUUID(), "leader-high")) {
            var game = new SemionGame(EconomyConfig.defaultConfig(), new WaveConfig(List.of(), 20, null), testArena(context));
            try {
                game.selectJob(low.player().getUUID(), NetherTowerJob.ID);
                game.selectJob(high.player().getUUID(), NetherTowerJob.ID);
                var plan = new ParticipantSelectionPlan(MatchMode.NORMAL, List.of(
                        new AssignedParticipant(low.player().getUUID(), "leader-low", TeamId.RED, 1, 1200),
                        new AssignedParticipant(high.player().getUUID(), "leader-high", TeamId.RED, 2, 1800),
                        new AssignedParticipant(UUID.randomUUID(), "blue", TeamId.BLUE, 1, 1500)), Set.of(), 2);
                context.assertTrue(game.start(server, plan), "Normal match starts");
                context.assertValueEqual(high.player().getUUID(), game.teams().get(TeamId.RED).leaderPlayerId().orElseThrow(), "Highest ELO is leader");
                assertTool(context, high.player(), true);
                assertTool(context, low.player(), false);
                high.player().getInventory().setItem(2, ItemStack.EMPTY);
                context.assertTrue(game.restorePlayerPlacement(server, high.player()), "Reconnect placement restores leader tools");
                assertTool(context, high.player(), true);
                game.restorePlayerPlacement(server, high.player());
                assertTool(context, high.player(), true);
                tickGame(game, server, SemionGame.DEFAULT_PREPARE_TICKS + 3);
                context.assertValueEqual(2, game.currentRound(), "The next round is prepared");
                assertTool(context, high.player(), true);
                assertTool(context, low.player(), false);
                context.succeed();
            } finally {
                game.close();
            }
        }
    }

    @GameTest
    public void tiedHighestEloUsesLowestLaneAndNeverIncludesLowerElo(GameTestHelper context) {
        var game = new SemionGame(EconomyConfig.defaultConfig(), new WaveConfig(List.of(), 20, null), testArena(context));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var participants = List.of(new AssignedParticipant(UUID.randomUUID(), "low", TeamId.RED, 1, 1200),
                new AssignedParticipant(first, "first", TeamId.RED, 2, 1800),
                new AssignedParticipant(second, "second", TeamId.RED, 3, 1800),
                new AssignedParticipant(UUID.randomUUID(), "blue", TeamId.BLUE, 1, 1500));
        try {
            context.assertTrue(game.start(context.getLevel().getServer(), new ParticipantSelectionPlan(MatchMode.NORMAL, participants, Set.of(), 2)), "Match starts");
            context.assertValueEqual(first, game.teams().get(TeamId.RED).leaderPlayerId().orElseThrow(),
                    "Match start chooses the lowest lane among the highest ELO participants");
            for (int attempt = 0; attempt < 100; attempt++) {
                game.assignTeamLeadersFromParticipants(attempt % 2 == 0 ? participants : participants.reversed());
                context.assertValueEqual(first, game.teams().get(TeamId.RED).leaderPlayerId().orElseThrow(),
                        "Candidate order never changes the lowest tied lane selection");
            }
            context.succeed();
        } finally {
            game.close();
        }
    }

    private static void assertTool(GameTestHelper context, ServerPlayer player, boolean leader) {
        var stack = player.getInventory().getItem(2);
        if (leader) {
            context.assertTrue(stack.is(Items.BLAZE_ROD) && stack.getCount() == 1, "Leader receives exactly one blaze rod in third slot");
            context.assertValueEqual("팀장 타깃", stack.get(DataComponents.CUSTOM_NAME).getString(), "Leader tool keeps its visible name");
        }
        int count = 0;
        for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
            var item = player.getInventory().getItem(index);
            if (item.is(Items.BLAZE_ROD)) {
                count += item.getCount();
            }
        }
        context.assertValueEqual(leader ? 1 : 0, count, "Only leaders receive one targeting rod");
    }
}
