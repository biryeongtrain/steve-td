package kim.biryeong.semiontd.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import kim.biryeong.semiontd.game.PlayerTeleportTransitions;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class RuntimePlayerFixture implements AutoCloseable {
    private final ServerPlayer player;
    private final EmbeddedChannel channel;
    private boolean closed;

    private RuntimePlayerFixture(ServerPlayer player, EmbeddedChannel channel) {
        this.player = player;
        this.channel = channel;
    }

    public static RuntimePlayerFixture connect(GameTestHelper context, ServerLevel world, Vec3 position,
            GameType mode, UUID id, String name) {
        var server = context.getLevel().getServer();
        RuntimeEnvironmentFixture.configureWorld(world);
        var cookie = CommonListenerCookie.createInitial(new GameProfile(id, name), false);
        var information = new net.minecraft.server.level.ClientInformation(
                "en_us", 10, net.minecraft.world.entity.player.ChatVisiblity.FULL, true, 0x7F,
                net.minecraft.world.entity.HumanoidArm.RIGHT, false, false, net.minecraft.server.level.ParticleStatus.ALL);
        var player = new ServerPlayer(server, context.getLevel(), cookie.gameProfile(), information);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        var fixture = new RuntimePlayerFixture(player, channel);
        try {
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(mode);
            player.teleport(PlayerTeleportTransitions.preservingFacing(world, position, Vec3.ZERO, player));
            if (player.level() != world || player.gameMode.getGameModeForPlayer() != mode
                    || server.getPlayerList().getPlayer(id) != player) {
                throw new AssertionError("Runtime test player must join the intended world and gameplay mode.");
            }
            return fixture;
        } catch (Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    public void enterWorld(ServerLevel world, Vec3 position) {
        player.setGameMode(GameType.ADVENTURE);
        player.teleport(PlayerTeleportTransitions.preservingFacing(world, position, Vec3.ZERO, player));
        if (player.level() != world || !world.players().contains(player)) {
            throw new AssertionError("Runtime test player must be tracked in the intended arena after login initialization.");
        }
    }

    public static void whenChunksTrackEntities(GameTestHelper context, ServerLevel world,
            java.util.List<Vec3> positions, Runnable action, Runnable cleanup) {
        awaitEntityTracking(context, world, positions, action, cleanup, 400);
    }

    private static void awaitEntityTracking(GameTestHelper context, ServerLevel world,
            java.util.List<Vec3> positions, Runnable action, Runnable cleanup, int remainingTicks) {
        boolean ready = positions.stream().allMatch(position -> {
            var chunk = world.getChunkAt(net.minecraft.core.BlockPos.containing(position));
            return chunk.getFullStatus() == net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING
                    && world.areEntitiesActuallyLoadedAndTicking(chunk.getPos());
        });
        if (ready) {
            action.run();
        } else if (remainingTicks == 0) {
            String states = positions.stream().map(position -> world.getChunkAt(net.minecraft.core.BlockPos.containing(position))
                    .getFullStatus() + " at " + position).toList().toString()
                    + "; players=" + world.players().stream().map(p -> p.getName().getString() + "@" + p.position()
                    + ":" + p.gameMode.getGameModeForPlayer()).toList();
            cleanup.run();
            context.fail(net.minecraft.network.chat.Component.literal("Arena chunks did not begin entity tracking within 400 ticks: " + states));
        } else {
            context.runAfterDelay(1, () -> awaitEntityTracking(context, world, positions, action, cleanup, remainingTicks - 1));
        }
    }

    public ServerPlayer player() {
        return player;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            player.level().getServer().getPlayerList().remove(player);
            player.discard();
            channel.finishAndReleaseAll();
        }
    }
}
