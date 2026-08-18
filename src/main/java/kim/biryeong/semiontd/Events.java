package kim.biryeong.semiontd;

import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.cosmetic.CosmeticItemSupport;
import kim.biryeong.semiontd.cosmetic.CosmeticService;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.SemionPlayerProtectionService;
import kim.biryeong.semiontd.skybox.SemionSkyboxService;
import kim.biryeong.semiontd.tip.SemionTipService;
import kim.biryeong.semiontd.tower.demonlord.DemonLordBinding;
import kim.biryeong.semiontd.tower.demonlord.DemonLordService;
import kim.biryeong.semiontd.tower.demonlord.DemonLordStates;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import xyz.nucleoid.stimuli.Stimuli;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerC2SPacketEvent;
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
        // 보호 서비스 뒤에 등록해야 마왕만 예외로 피해를 받고 평타를 넣을 수 있습니다.
        DemonLordService.register(gameManager);

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
            gameManager.restoreCombatTickRate(server);
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
                DemonLordService.cleanupPlayer(player);
            });
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            skyboxService.handlePlayerWorldChanged(player);
            gameManager.handlePlayerWorldChanged(player);
            // 경기가 끝나 로비로 돌아갈 때 보스바를 걷습니다. 아직 전투 중이면 다음 틱에
            // syncBossBar 가 다시 만들어 주므로 무조건 지워도 안전합니다.
            DemonLordService.clearBossBar(player.getUUID());
        });

        Stimuli.global().listen(PlayerConsumeHungerEvent.EVENT, ((serverPlayer, i, v, v1) -> EventResult.DENY));
        // F 키. 마왕이 여섯 번째 스킬로 쓰므로 오프핸드 교체보다 먼저 가로챕니다.
        Stimuli.global().listen(PlayerSwapWithOffhandEvent.EVENT, player -> {
            if (DemonLordService.handleKeyBinding(gameManager, player, DemonLordBinding.OFFHAND)) {
                return EventResult.DENY;
            }
            return CosmeticItemSupport.isLockedOffhandCosmetic(player.getOffhandItem())
                    ? EventResult.DENY
                    : EventResult.PASS;
        });

        // Q 키. 드롭 패킷을 가로채 일곱 번째 스킬로 씁니다.
        //
        // 이 이벤트는 바닐라가 스레드를 넘기기 전, 즉 <b>네티 채널 스레드</b>에서 돕니다. 여기서
        // 레인·엔티티·파티클을 건드리면 서버 스레드 밖에서 월드를 만지게 되어 연결이 끊깁니다.
        // 그래서 여기서는 값싼 상태 확인만 하고, 실제 시전은 서버 스레드로 넘깁니다.
        Stimuli.global().listen(PlayerC2SPacketEvent.EVENT, (player, packet) -> {
            if (!(packet instanceof ServerboundPlayerActionPacket action)) {
                return EventResult.PASS;
            }
            ServerboundPlayerActionPacket.Action kind = action.getAction();
            if (kind != ServerboundPlayerActionPacket.Action.DROP_ITEM
                    && kind != ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS) {
                return EventResult.PASS;
            }
            if (!DemonLordStates.isInCombat(player.getUUID())) {
                return EventResult.PASS;
            }
            MinecraftServer server = player.getServer();
            if (server == null) {
                return EventResult.PASS;
            }
            server.execute(() -> {
                DemonLordService.handleKeyBinding(gameManager, player, DemonLordBinding.DROP);
                // 패킷을 막아도 클라이언트는 이미 제 화면에서 아이템을 빼 버립니다. 서버는 그대로라
                // 아무 변화가 없으니 자동 동기화도 일어나지 않고, 클라는 빈 손이라 믿은 채로 남아
                // 마검을 다시 못 씁니다. 그래서 되돌려 보냅니다.
                player.containerMenu.sendAllDataToRemote();
            });
            // 전투 중인 마왕의 Q 는 아이템을 버리는 키가 아니므로 드롭 자체는 항상 막습니다.
            return EventResult.DENY;
        });
    }

    private Events() throws IllegalAccessException {
        throw new IllegalAccessException("Utility Class");
    }
}
