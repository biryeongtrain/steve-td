package kim.biryeong.semiontd.tower.developer;

import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.DeveloperTowerJob;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.tower.developer.DeveloperBug;
import kim.biryeong.semiontd.tower.developer.DeveloperPatchService;
import kim.biryeong.semiontd.tower.developer.DeveloperStates;
import kim.biryeong.semiontd.tower.developer.DeveloperTower;
import kim.biryeong.semiontd.tower.developer.DeveloperTowerData;
import kim.biryeong.semiontd.tower.developer.DeveloperTowers;
import kim.biryeong.semiontd.ui.SemionTowerInteractionService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import kim.biryeong.semiontd.gametest.GameTestParticipantFixture;

public final class DeveloperTowerRuntimeTest extends GameTestParticipantFixture {
    @GameTest
    public void developerReproductionUsesEntityAndBlockClicksAndSneakCancels(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        UUID playerId = player.getUUID();
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, DeveloperTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos sourcePos = towerPlacementPos(lane);
        BlockPos targetPos = nearbyTowerPlacementPos(lane, sourcePos);
        BlockPos developerPos = nearbyTowerPlacementPos(lane, targetPos);
        DeveloperTower source = new DeveloperTower(
                TowerBalanceRuntime.resolve(DeveloperTowers.ALPHA), playerId, TeamId.RED, 1,
                GridPosition.from(sourcePos));
        DeveloperTower target = new DeveloperTower(
                TowerBalanceRuntime.resolve(DeveloperTowers.BETA), playerId, TeamId.RED, 1,
                GridPosition.from(targetPos));
        DeveloperTower developer = new DeveloperTower(
                TowerBalanceRuntime.resolve(DeveloperTowers.DEVELOPER), playerId, TeamId.RED, 1,
                GridPosition.from(developerPos));
        lane.addTower(source);
        lane.addTower(target);
        lane.addTower(developer);
        DeveloperStates.openRound(playerId, game.currentRound(), DeveloperPatchService.capacityFor(lane, playerId));
        DeveloperTowerData.addBug(source, DeveloperBug.PRIMITIVE);
        DeveloperTowerData.addBug(source, DeveloperBug.BOUNDARY);
        DeveloperTowerData.addBug(source, DeveloperBug.FLOATING_POINT);

        SemionGameManager manager = new SemionGameManager();
        setField(manager, "activeGame", game);
        try {
            if (!assertTrue(context,
                    DeveloperPatchService.armReproduction(lane, source, DeveloperBug.PRIMITIVE).success(),
                    "Entity-click reproduction should arm.")) return;
            SemionTowerEntity sourceEntity = (SemionTowerEntity) lane.arenaWorld()
                    .getEntity(source.entityId().orElseThrow());
            SemionTowerEntity targetEntity = (SemionTowerEntity) lane.arenaWorld()
                    .getEntity(target.entityId().orElseThrow());

            SemionTowerInteractionService.handleUse(
                    manager, player, context.getLevel(), InteractionHand.MAIN_HAND,
                    sourceEntity, new EntityHitResult(sourceEntity));
            if (!assertTrue(context, DeveloperStates.of(playerId).pendingReproduction().isPresent(),
                    "An invalid same-tower click must keep target selection armed.")) return;

            SemionTowerInteractionService.handleUse(
                    manager, player, context.getLevel(), InteractionHand.MAIN_HAND,
                    targetEntity, new EntityHitResult(targetEntity));
            if (!assertTrue(context, target.hasBug(DeveloperBug.PRIMITIVE),
                    "Entity click should reproduce the selected bug.")) return;

            DeveloperStates.openRound(playerId, game.currentRound(), DeveloperPatchService.capacityFor(lane, playerId));
            if (!assertTrue(context,
                    DeveloperPatchService.armReproduction(lane, source, DeveloperBug.BOUNDARY).success(),
                    "Block-click reproduction should arm.")) return;
            SemionTowerInteractionService.handleBlockUse(
                    manager, player, context.getLevel(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(targetPos), Direction.UP, targetPos, false));
            if (!assertTrue(context, target.hasBug(DeveloperBug.BOUNDARY),
                    "Block click should reproduce the selected bug.")) return;

            DeveloperStates.openRound(playerId, game.currentRound(), DeveloperPatchService.capacityFor(lane, playerId));
            DeveloperPatchService.armReproduction(lane, source, DeveloperBug.FLOATING_POINT);
            player.setShiftKeyDown(true);
            SemionTowerInteractionService.handleUse(
                    manager, player, context.getLevel(), InteractionHand.MAIN_HAND,
                    targetEntity, new EntityHitResult(targetEntity));
            if (!assertTrue(context,
                    DeveloperStates.of(playerId).pendingReproduction().isEmpty()
                            && !target.hasBug(DeveloperBug.FLOATING_POINT),
                    "Sneak-clicking a tower must cancel without reproducing.")) return;

            context.succeed();
        } finally {
            player.setShiftKeyDown(false);
            DeveloperStates.clear(playerId);
            setField(manager, "activeGame", null);
            manager.shutdown();
            lane.clearTowers();
        }
    }
}
