package kim.biryeong.semiontd;

import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.cosmetic.CosmeticItemSupport;
import kim.biryeong.semiontd.cosmetic.CosmeticService;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.SemionPlayerProtectionService;
import kim.biryeong.semiontd.skybox.SemionSkyboxService;
import kim.biryeong.semiontd.tip.SemionTipService;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import xyz.nucleoid.stimuli.Stimuli;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerConsumeHungerEvent;
import xyz.nucleoid.stimuli.event.player.PlayerSwapWithOffhandEvent;

public final class Events {

    public static void initialize(
            SemionGameManager gameManager,
            SemionSkyboxService skyboxService,
            SemionTipService tipService,
            CosmeticService cosmeticService
    ) {
        SemionPlayerProtectionService.register(gameManager);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            gameManager.tick(server);
            gameManager.tickStartupLobbyLoad(server);
            skyboxService.tick(server);
            tipService.tick(server);
            TowerVfxService.endServerTick(server);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            cosmeticService.load(server);
            gameManager.scheduleStartupLobbyLoad(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TowerVfxService.shutdown();
            skyboxService.shutdown();
            tipService.shutdown();
            gameManager.shutdown();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            gameManager.handlePlayerJoin(handler.getPlayer());
            tipService.handlePlayerJoin(handler.getPlayer());
            cosmeticService.syncPlayer(handler.getPlayer());
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> cosmeticService.syncPlayer(newPlayer));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            // The disconnect callback may run from Netty's channel thread. Entity
            // cleanup must be deferred to the server thread (C2ME enforces this).
            var player = handler.getPlayer();
            server.execute(() -> {
                skyboxService.handlePlayerDisconnect(player);
                tipService.handlePlayerDisconnect(player);
                gameManager.handlePlayerDisconnect(player);
            });
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            skyboxService.handlePlayerWorldChanged(player);
            gameManager.handlePlayerWorldChanged(player);
        });

        Stimuli.global().listen(PlayerConsumeHungerEvent.EVENT, ((serverPlayer, i, v, v1) -> EventResult.DENY));
        Stimuli.global().listen(PlayerSwapWithOffhandEvent.EVENT, player ->
                CosmeticItemSupport.isLockedOffhandCosmetic(player.getOffhandItem())
                        ? EventResult.DENY
                        : EventResult.PASS
        );
    }

    private Events() throws IllegalAccessException {
        throw new IllegalAccessException("Utility Class");
    }
}
