package kim.biryeong.semiontd.game;

import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.gametest.RuntimePlayerFixture;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class GamePlayerJoinDispatchTest {
    @GameTest
    public void connectedJoinStillMovesToLobby(GameTestHelper context) throws Exception {
        var manager = manager();
        var fixture = connect(context, UUID.randomUUID(), "join-connected");
        var player = fixture.player();
        context.runAfterDelay(2, () -> {
            try {
                require(player.connection.isAcceptingMessages(), "The joined player must remain connected.");
                requireLobby(manager, player);
                context.succeed();
            } finally {
                fixture.close();
            }
        });
    }

    @GameTest
    public void disconnectedJoinDoesNotApplyDelayedLobbyChanges(GameTestHelper context) throws Exception {
        manager();
        var fixture = connect(context, UUID.randomUUID(), "join-disconnect");
        var player = fixture.player();
        player.connection.disconnect(Component.literal("Join dispatch regression test"));
        require(!player.connection.isAcceptingMessages(), "The test must close the connection before dispatch.");
        context.runAfterDelay(2, () -> {
            try {
                require(player.gameMode.getGameModeForPlayer() == GameType.CREATIVE,
                        "A disconnected player's pending join must not change their game mode.");
                require(player.level() == context.getLevel(),
                        "A disconnected player's pending join must not move them to a lobby world.");
                context.succeed();
            } finally {
                fixture.close();
            }
        });
    }

    @GameTest
    public void replacementConnectionReceivesOnlyItsOwnDelayedJoin(GameTestHelper context) throws Exception {
        var manager = manager();
        var id = UUID.randomUUID();
        var original = connect(context, id, "join-original");
        var oldPlayer = original.player();
        var server = context.getLevel().getServer();
        server.getPlayerList().remove(oldPlayer);
        var replacement = connect(context, id, "join-replace");
        require(oldPlayer.connection.isAcceptingMessages(),
                "The superseded connection must remain open to exercise the player identity guard.");
        require(server.getPlayerList().getPlayer(id) == replacement.player(),
                "The replacement must own the UUID before either queued join runs.");
        context.runAfterDelay(2, () -> {
            try {
                require(oldPlayer.gameMode.getGameModeForPlayer() == GameType.CREATIVE,
                        "The superseded player's pending join must not change their game mode.");
                require(server.getPlayerList().getPlayer(id) == replacement.player(),
                        "A stale join must not displace the replacement connection.");
                requireLobby(manager, replacement.player());
                context.succeed();
            } finally {
                original.close();
                replacement.close();
            }
        });
    }

    private static RuntimePlayerFixture connect(GameTestHelper context, UUID id, String name) {
        return RuntimePlayerFixture.connect(context, context.getLevel(),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1))), GameType.CREATIVE, id, name);
    }

    private static SemionGameManager manager() throws Exception {
        var initializer = FabricLoader.getInstance().getEntrypoints("main", ModInitializer.class).stream()
                .filter(SemionTd.class::isInstance).map(SemionTd.class::cast).findFirst().orElseThrow();
        var field = SemionTd.class.getDeclaredField("gameManager");
        field.setAccessible(true);
        var manager = (SemionGameManager) field.get(initializer);
        require(manager.activeGame().isEmpty(), "The JOIN fixture requires the global manager's idle lobby.");
        return manager;
    }

    private static void requireLobby(SemionGameManager manager, ServerPlayer player) {
        var lobby = manager.lobbyWorld().orElseThrow();
        require(player.level() == lobby.world() && player.position().distanceToSqr(lobby.spawn()) < 0.01,
                "The current connected player must receive their delayed lobby teleport.");
        require(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                "The current connected player must receive the lobby game mode.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
