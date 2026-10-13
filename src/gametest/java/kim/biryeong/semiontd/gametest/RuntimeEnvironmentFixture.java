package kim.biryeong.semiontd.gametest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.LoggerFactory;

public final class RuntimeEnvironmentFixture implements ModInitializer {
    @Override
    public void onInitialize() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.INIT.register((handler, server) -> {
            if (!(server instanceof net.minecraft.gametest.framework.GameTestServer)) {
                return;
            }
            var context = handler.getPacketContext();
            if (context.get(net.fabricmc.fabric.api.networking.v1.context.PacketContext.REGISTRY_ACCESS) == null) {
                context.set(net.fabricmc.fabric.impl.networking.context.PacketContextImpl.SERVER_INSTANCE, server);
                context.set(net.fabricmc.fabric.impl.networking.context.PacketContextImpl.REGISTRY_ACCESS, server.registryAccess());
                context.set(net.fabricmc.fabric.impl.networking.context.PacketContextImpl.GAME_PROFILE, handler.getPlayer().getGameProfile());
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var players = server.getPlayerList();
            LoggerFactory.getLogger(RuntimeEnvironmentFixture.class).info(
                    "GameTest server distances before runtime fixture: view={}, simulation={}",
                    players.getViewDistance(), players.getSimulationDistance());
            players.setViewDistance(10);
            players.setSimulationDistance(10);
        });
    }
    static void configureWorld(net.minecraft.server.level.ServerLevel world) {
        if (!(world.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)) {
            throw new IllegalStateException("Runtime fixture requires a GameTest server.");
        }
        var players = world.getServer().getPlayerList();
        world.getChunkSource().setViewDistance(players.getViewDistance());
        world.getChunkSource().setSimulationDistance(players.getSimulationDistance());
    }
}
