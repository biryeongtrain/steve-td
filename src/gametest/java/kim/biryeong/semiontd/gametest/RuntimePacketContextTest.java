package kim.biryeong.semiontd.gametest;

import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class RuntimePacketContextTest {
    @GameTest
    public void vanillaMockHasRegistryContextForBlockEntityChunks(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        try {
            verifyChunkPacket(context, player);
            context.succeed();
        } finally {
            context.getLevel().getServer().getPlayerList().remove(player);
            player.discard();
        }
    }

    @GameTest
    public void embeddedRuntimePlayerHasRegistryContextForBlockEntityChunks(GameTestHelper context) {
        try (var fixture = RuntimePlayerFixture.connect(context, context.getLevel(),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(3, 2, 3))),
                GameType.ADVENTURE, UUID.randomUUID(), "packet-context-test")) {
            verifyChunkPacket(context, fixture.player());
            context.succeed();
        }
    }

    private static void verifyChunkPacket(GameTestHelper context, ServerPlayer player) {
        var packetContext = player.connection.getPacketContext();
        context.assertTrue(packetContext.get(PacketContext.SERVER_INSTANCE) == context.getLevel().getServer(),
                "Mock login must populate the actual server instance.");
        context.assertTrue(packetContext.get(PacketContext.REGISTRY_ACCESS) == context.getLevel().getServer().registryAccess(),
                "Mock login must populate registry access before Polymer creates a chunk packet.");
        context.assertTrue(player.getGameProfile().equals(packetContext.get(PacketContext.GAME_PROFILE)),
                "Mock login must retain the connection's game profile.");
        context.setBlock(2, 1, 2, Blocks.CHEST);
        var chunk = context.getLevel().getChunkAt(context.absolutePos(new BlockPos(2, 1, 2)));
        context.assertTrue(!chunk.getBlockEntities().isEmpty(), "The chunk must exercise block-entity NBT serialization.");
        var previous = PacketContext.get();
        PacketContext.supplyWithContext(player.connection, () -> {
            context.assertTrue(PacketContext.get() == packetContext, "Chunk creation must run in the player's context.");
            return new ClientboundLevelChunkWithLightPacket(chunk,
                    context.getLevel().getChunkSource().getLightEngine(), null, null);
        });
        context.assertTrue(PacketContext.get() == previous, "Chunk creation must restore the prior scoped context.");
    }
}
