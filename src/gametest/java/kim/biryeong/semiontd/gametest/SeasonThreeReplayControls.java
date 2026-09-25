package kim.biryeong.semiontd.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.job.FrostTowerJob;
import kim.biryeong.semiontd.tower.frost.FrostFullOperationService;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.GameType;

/** Opt-in replay controls; the production manager and gameplay readiness are never changed. */
final class SeasonThreeReplayControls implements AutoCloseable {
    // ponytail: one opt-in trial at a time; the single listener stays registered with no game after close.
    private static SemionGameManager replayManager;
    private static final Field ACTIVE_GAME = field(SemionGameManager.class, "activeGame");
    private static final Field PLAYERS_BY_UUID = field(PlayerList.class, "playersByUUID");
    private final MinecraftServer server;
    private final SemionGame game;
    private final Map<UUID, ControlledPlayer> players = new LinkedHashMap<>();
    private final List<Activation> activations = new ArrayList<>();
    private boolean attached;
    private boolean closed;

    record Activation(UUID playerId, int round, long worldTick, String action) {}
    private record ControlledPlayer(ServerPlayer player, Connection connection, EmbeddedChannel channel) {}

    private SeasonThreeReplayControls(GameTestHelper context, SemionGame game) {
        this.server = context.getLevel().getServer();
        this.game = game;
    }

    static SeasonThreeReplayControls attach(GameTestHelper context, SemionGame game, Map<UUID, String> names) {
        SeasonThreeReplayControls controls = new SeasonThreeReplayControls(context, game);
        require(controls.server.isSameThread(), "Replay controls must attach on the server thread");
        if (names.isEmpty()) {return controls;}
        require(Boolean.getBoolean("semiontd.builderBalanceAcceptance")
                        || Boolean.getBoolean("semiontd.baseline.operation"),
                "Player controls require an opt-in baseline test");
        if (replayManager == null) {
            replayManager = new SemionGameManager();
            FrostFullOperationService.register(replayManager);
        }
        require(replayManager.activeGame().isEmpty(), "Another replay control trial is still attached");
        try {
            ACTIVE_GAME.set(replayManager, game);
            controls.attached = true;
            for (var entry : names.entrySet()) {
                require(controls.server.getPlayerList().getPlayer(entry.getKey()) == null,
                        "Replay player UUID is already online: " + entry.getKey());
                CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                        new GameProfile(entry.getKey(), entry.getValue()), false);
                ServerPlayer player = new ServerPlayer(controls.server, context.getLevel(),
                        cookie.gameProfile(), cookie.clientInformation());
                Connection connection = new Connection(PacketFlow.SERVERBOUND);
                // Native VFX sends asynchronously. This fixture tests gameplay, not packet delivery;
                // consume writes before they reach EmbeddedChannel's non-thread-safe outbound buffer.
                EmbeddedChannel channel = new EmbeddedChannel(connection, new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                        ReferenceCountUtil.release(message);
                        promise.setSuccess();
                    }

                    @Override
                    public void flush(ChannelHandlerContext context) {
                        // No outbound data is retained, so neither sending thread should flush a buffer.
                    }
                });
                controls.players.put(entry.getKey(), new ControlledPlayer(player, connection, channel));
                player.connection = new ServerGamePacketListenerImpl(controls.server, connection, player, cookie);
                // Match PlayerList's protocol binding before the first game-mode packet is sent.
                connection.setupInboundProtocol(GameProtocols.SERVERBOUND_TEMPLATE.bind(
                        RegistryFriendlyByteBuf.decorator(controls.server.registryAccess()), player.connection),
                        player.connection);
                player.setGameMode(GameType.ADVENTURE);
                player.setInvulnerable(true);
                // Match the mock player's lookup, without JOIN callbacks, lobby moves, or profile persistence.
                controls.server.getPlayerList().getPlayers().add(player);
                controls.onlinePlayers().put(player.getUUID(), player);
                require(controls.server.getPlayerList().getPlayer(entry.getKey()) == player,
                        "Replay player registration must preserve the assigned UUID");
            }
            return controls;
        } catch (ReflectiveOperationException | RuntimeException | Error failure) {
            try {controls.close();}
            catch (Throwable cleanupFailure) {failure.addSuppressed(cleanupFailure);}
            SemionTd.LOGGER.error("Cannot attach replay controls", failure);
            throw new AssertionError("Cannot attach replay controls: " + failure, failure);
        }
    }

    void automaticActions(SemionGame currentGame) {
        require(!closed && currentGame == game && server.isSameThread(), "Invalid replay control invocation");
        for (ControlledPlayer controlled : players.values()) {
            ServerPlayer player = controlled.player();
            var participant = game.players().get(player.getUUID());
            if (game.phase() != RoundPhase.LANE_WAVE || !game.isActiveParticipant(player.getUUID())
                    || participant == null
                    || participant.job().filter(job -> FrostTowerJob.ID.equals(job.id())).isEmpty()
                    || !FrostFullOperationService.isActivationItem(player.getInventory().getItem(8))) {
                continue;
            }
            var lane = game.playerLane(player.getUUID()).orElseThrow();
            long tick = lane.arenaWorld().getGameTime();
            require(!FrostFullOperationService.isActive(player.getUUID(), tick),
                    "An active full operation must not retain its activation item");
            int previousSlot = player.getInventory().getSelectedSlot();
            try {
                player.getInventory().setSelectedSlot(8);
                InteractionResult result = UseItemCallback.EVENT.invoker()
                        .interact(player, lane.arenaWorld(), InteractionHand.MAIN_HAND);
                if (result == InteractionResult.SUCCESS) {
                    require(FrostFullOperationService.isActive(player.getUUID(), tick)
                                    && !FrostFullOperationService.isActivationItem(player.getInventory().getItem(8)),
                            "A successful callback must activate full operation and consume its item");
                    require(activations.stream().noneMatch(event -> event.playerId().equals(player.getUUID())
                                    && event.round() == game.currentRound()),
                            "Full operation must not activate twice in the same wave");
                    activations.add(new Activation(player.getUUID(), game.currentRound(), tick,
                            "FROST_FULL_OPERATION_WHEN_READY"));
                }
            } finally {
                player.getInventory().setSelectedSlot(previousSlot);
            }
        }
    }

    int activationCount(UUID playerId) {
        return (int) activations.stream().filter(event -> event.playerId().equals(playerId)).count();
    }

    List<Activation> events() {return List.copyOf(activations);}

    @Override
    public void close() {
        if (closed) {return;}
        closed = true;
        Throwable failure = null;
        try {
            if (attached) {
                ACTIVE_GAME.set(replayManager, null);
                attached = false;
            }
        } catch (ReflectiveOperationException exception) {
            failure = exception;
        }
        for (ControlledPlayer controlled : players.values()) {
            try {
                ServerPlayer player = controlled.player();
                server.getPlayerList().getPlayers().remove(player);
                onlinePlayers().remove(player.getUUID(), player);
                try {
                    if (player.connection != null
                            && controlled.connection().getPacketListener() == player.connection) {
                        FrostFullOperationService.cleanupPlayer(player);
                    }
                    else {FrostFullOperationService.clearPlayer(player.getUUID());}
                } finally {
                    try {
                        player.getTextFilter().leave();
                        player.discard();
                    } finally {
                        controlled.channel().finishAndReleaseAll();
                    }
                }
            } catch (Throwable exception) {
                if (failure == null) {failure = exception;}
                else {failure.addSuppressed(exception);}
            }
        }
        players.clear();
        if (failure != null) {throw new AssertionError("Cannot detach replay controls", failure);}
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, ServerPlayer> onlinePlayers() throws IllegalAccessException {
        return (Map<UUID, ServerPlayer>) PLAYERS_BY_UUID.get(server.getPlayerList());
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Missing replay fixture field: " + name, failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {throw new AssertionError(message);}
    }
}
