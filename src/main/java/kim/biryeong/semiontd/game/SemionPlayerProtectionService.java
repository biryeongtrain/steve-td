package kim.biryeong.semiontd.game;

import java.util.UUID;
import kim.biryeong.semiontd.tower.demonlord.DemonLordStates;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

public final class SemionPlayerProtectionService {
    private SemionPlayerProtectionService() {
    }

    public static void register(SemionGameManager gameManager) {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player
                    && shouldProtectPlayer(gameManager.protectionGame(player.getUUID()), player.getUUID())) {
                return false;
            }
            return true;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()
                    || hand != InteractionHand.MAIN_HAND
                    || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (shouldProtectPlayer(gameManager.protectionGame(serverPlayer.getUUID()), serverPlayer.getUUID())) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    /**
     * 마왕은 [전투 상태]인 동안 스스로 싸우는 빌더라 보호 대상에서 빠집니다. 대신 실제 피해는
     * {@code DemonLordService} 가 가로채 보스바 체력 풀로 넘기므로, 바닐라 체력은 여전히 줄지 않습니다.
     */
    public static boolean shouldProtectPlayer(SemionGame game, UUID playerId) {
        return game != null
                && playerId != null
                && game.rosterLocked()
                && (game.isActiveParticipant(playerId) || game.isMatchSpectator(playerId))
                && !DemonLordStates.isInCombat(playerId);
    }
}
