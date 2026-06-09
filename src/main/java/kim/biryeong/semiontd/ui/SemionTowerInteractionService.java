package kim.biryeong.semiontd.ui;

import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
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
        SemionTowerEntity towerEntity = resolveTowerEntity(world, entity);
        if (towerEntity == null || towerEntity.runtimeTower() == null) {
            return InteractionResult.PASS;
        }

        SemionGame game = gameManager.playableGame(serverPlayer.getUUID()).orElse(null);
        if (game == null || !game.isActiveParticipant(serverPlayer.getUUID())) {
            return InteractionResult.PASS;
        }

        gameManager.dialogService().showTowerDetails(serverPlayer, game, towerEntity.runtimeTower(), gameManager.buildGuideService());
        return InteractionResult.SUCCESS;
    }

    static SemionTowerEntity resolveTowerEntity(Level world, Entity entity) {
        if (entity instanceof SemionTowerEntity towerEntity) {
            return towerEntity;
        }
        if (world == null || entity == null) {
            return null;
        }
        return world.getEntitiesOfClass(
                        SemionTowerEntity.class,
                        entity.getBoundingBox().inflate(2.0),
                        candidate -> candidate.ownsMoobloomVisualEntity(entity)
                )
                .stream()
                .findFirst()
                .orElse(null);
    }
}
