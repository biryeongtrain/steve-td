package kim.biryeong.semiontd;

import kim.biryeong.semiontd.game.SemionGameManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class Events {

    public static void initialize(SemionGameManager gameManager) {

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            gameManager.tick(server);
            gameManager.tickStartupLobbyLoad(server);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(gameManager::scheduleStartupLobbyLoad);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> gameManager.shutdown());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            gameManager.handlePlayerJoin(handler.getPlayer());
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            gameManager.handlePlayerWorldChanged(player);
        });

    }

    private Events() throws IllegalAccessException {
        throw new IllegalAccessException("Utility Class");
    }
}
