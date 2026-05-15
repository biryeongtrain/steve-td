package kim.biryeong.semiontd.ui;

import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.test.entity.SemionTestTowerEntity;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

public final class SemionTowerInteractionService {
    private SemionTowerInteractionService() {
    }

    public static void register(SemionGameManager gameManager) {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                handleUse(gameManager, player, world, hand, entity, hitResult));
    }

    public static InteractionResult handleUse(
            SemionGameManager gameManager,
            Player player,
            Level world,
            InteractionHand hand,
            Entity entity,
            EntityHitResult hitResult
    ) {
        if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (!(entity instanceof SemionTestTowerEntity towerEntity) || towerEntity.runtimeTower() == null) {
            return InteractionResult.PASS;
        }

        SemionGame game = gameManager.activeGame().orElse(null);
        if (game == null || !game.isActiveParticipant(serverPlayer.getUUID())) {
            return InteractionResult.PASS;
        }

        gameManager.dialogService().showTowerDetails(serverPlayer, game, towerEntity.runtimeTower());
        return InteractionResult.SUCCESS;
    }
}
