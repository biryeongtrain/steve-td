package kim.biryeong.semiontd.game;

import java.util.UUID;
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

    public static boolean shouldProtectPlayer(SemionGame game, UUID playerId) {
        return game != null
                && playerId != null
                && game.rosterLocked()
                && (game.isActiveParticipant(playerId) || game.isMatchSpectator(playerId));
    }
}
