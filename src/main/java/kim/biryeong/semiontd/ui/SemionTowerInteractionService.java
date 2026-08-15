package kim.biryeong.semiontd.ui;

import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerPlacementPositions;
import kim.biryeong.semiontd.tower.engineer.EngineerGolemTower;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public final class SemionTowerInteractionService {
    private SemionTowerInteractionService() {
    }

    public static void register(SemionGameManager gameManager) {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                handleUse(gameManager, player, world, hand, entity, hitResult));
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
                handleBlockUse(gameManager, player, world, hand, hitResult));
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
        SemionGame game = gameManager.protectionGame(serverPlayer.getUUID());
        if (game == null) {
            return InteractionResult.PASS;
        }
        SemionTowerEntity towerEntity = resolveTowerEntity(world, entity);
        Tower tower = towerEntity == null ? resolveEngineerGolem(game, entity) : towerEntity.runtimeTower();
        if (tower == null) {
            return InteractionResult.PASS;
        }

        gameManager.dialogService().showTowerDetails(
                serverPlayer,
                game,
                tower,
                gameManager.buildGuideService(),
                towerEntity
        );
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult handleBlockUse(
            SemionGameManager gameManager,
            Player player,
            Level world,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        SemionGame game = gameManager.protectionGame(serverPlayer.getUUID());
        if (game == null) {
            return InteractionResult.PASS;
        }
        Tower tower = game.teams().values().stream()
                .flatMap(team -> team.laneGroup().lanes().stream())
                .map(lane -> TowerPlacementPositions.resolveGrid(lane, hitResult.getBlockPos())
                        .map(lane::towerAt)
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (tower == null) {
            return InteractionResult.PASS;
        }
        gameManager.dialogService().showTowerDetails(
                serverPlayer, game, tower, gameManager.buildGuideService(), null
        );
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

    private static Tower resolveEngineerGolem(SemionGame game, Entity entity) {
        return game.teams().values().stream()
                .flatMap(team -> team.laneGroup().lanes().stream())
                .flatMap(lane -> lane.towers().stream())
                .filter(EngineerGolemTower.class::isInstance)
                .map(EngineerGolemTower.class::cast)
                .filter(golem -> golem.ownsGolemEntity(entity))
                .findFirst()
                .orElse(null);
    }
}
