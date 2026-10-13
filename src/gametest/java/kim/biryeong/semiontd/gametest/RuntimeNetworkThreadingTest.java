package kim.biryeong.semiontd.gametest;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class RuntimeNetworkThreadingTest {
    @GameTest(maxTicks = 100)
    public void embeddedAsyncSendsPreserveThreadOrderAndCompletion(GameTestHelper context) throws Exception {
        var server = context.getLevel().getServer();
        var fixture = RuntimePlayerFixture.connect(context, context.getLevel(), Vec3.ZERO,
                GameType.ADVENTURE, UUID.randomUUID(), "network-thread-test");
        try {
            var connection = connection(fixture);
            var channel = channel(connection);
            List<ClientboundSetActionBarTextPacket> packets = new ArrayList<>();
            for (int index = 0; index < 64; index++) {
                packets.add(new ClientboundSetActionBarTextPacket(Component.literal("async-" + index)));
            }
            var observed = new ConcurrentLinkedQueue<Object>();
            var wrongThread = new AtomicBoolean();
            var completed = new AtomicInteger();
            var failure = new AtomicReference<Throwable>();
            channel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext handler, Object message, ChannelPromise promise) {
                    if (packets.contains(message)) {
                        observed.add(message);
                        if (!server.isSameThread()) wrongThread.set(true);
                    }
                    handler.write(message, promise);
                }
            });
            Thread sender = Thread.ofPlatform().name("gametest-async-packets").start(() -> {
                try {
                    for (int index = 0; index < packets.size(); index++) {
                        connection.send(packets.get(index), future -> {
                            if (!future.isSuccess()) failure.compareAndSet(null, future.cause());
                            completed.incrementAndGet();
                        }, index == packets.size() - 1);
                    }
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                }
            });
            sender.join(1_000);
            context.assertTrue(!sender.isAlive(), "Background sends must enqueue without waiting for the server thread.");
            context.assertTrue(failure.get() == null, "Background sends must not fail.");
            context.assertTrue(observed.isEmpty(), "Background sends must wait for the server-thread handoff.");
            var synchronous = new ClientboundSetActionBarTextPacket(Component.literal("sync-control"));
            connection.send(synchronous);
            boolean controlFound = false;
            Object outbound;
            while ((outbound = channel.readOutbound()) != null) {
                if (outbound == synchronous) controlFound = true;
                io.netty.util.ReferenceCountUtil.release(outbound);
            }
            context.assertTrue(controlFound, "Server-thread sends must retain immediate packet delivery.");
            context.runAfterDelay(1, () -> {
                try {
                    context.assertTrue(!wrongThread.get(), "Embedded outbound writes must stay on the server thread.");
                    context.assertTrue(failure.get() == null, "Deferred sends and their listeners must succeed.");
                    context.assertTrue(new ArrayList<>(observed).equals(packets), "Every packet must retain its original order.");
                    context.assertTrue(completed.get() == packets.size(), "Every send listener must complete exactly once.");
                    channel.checkException();
                    context.succeed();
                } finally {
                    fixture.close();
                }
            });
        } catch (Throwable error) {
            fixture.close();
            throw error;
        }
    }

    @GameTest(maxTicks = 100)
    public void disconnectedQueuedSendCompletesWithFailure(GameTestHelper context) throws Exception {
        var server = context.getLevel().getServer();
        var fixture = RuntimePlayerFixture.connect(context, context.getLevel(), Vec3.ZERO,
                GameType.ADVENTURE, UUID.randomUUID(), "network-close-test");
        try {
            var connection = connection(fixture);
            var completed = new AtomicInteger();
            var failure = new AtomicReference<Throwable>();
            var wrongThread = new AtomicBoolean();
            Thread sender = Thread.ofPlatform().name("gametest-queued-close").start(() -> {
                connection.send(new ClientboundSetActionBarTextPacket(Component.literal("queued-before-close")), future -> {
                    failure.set(future.cause());
                    if (!server.isSameThread()) wrongThread.set(true);
                    completed.incrementAndGet();
                });
            });
            sender.join(1_000);
            context.assertTrue(!sender.isAlive(), "Queued sends must return before the server executes them.");
            context.assertTrue(completed.get() == 0, "Queued send completion must await the server thread.");
            fixture.close();
            context.runAfterDelay(1, () -> {
                context.assertTrue(completed.get() == 1, "Closing before delivery must still complete the send listener exactly once.");
                context.assertTrue(failure.get() instanceof ClosedChannelException, "The closed channel must report delivery failure.");
                context.assertTrue(!wrongThread.get(), "Closed-channel completion must remain on the server thread.");
                context.succeed();
            });
        } catch (Throwable error) {
            fixture.close();
            throw error;
        }
    }

    private static Connection connection(RuntimePlayerFixture fixture) throws Exception {
        var field = ServerCommonPacketListenerImpl.class.getDeclaredField("connection");
        field.setAccessible(true);
        return (Connection) field.get(fixture.player().connection);
    }

    private static EmbeddedChannel channel(Connection connection) throws Exception {
        var field = Connection.class.getDeclaredField("channel");
        field.setAccessible(true);
        return (EmbeddedChannel) field.get(connection);
    }
}
