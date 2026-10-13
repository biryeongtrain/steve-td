package kim.biryeong.semiontd.tower.adversary;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.game.TowerSellResult;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.job.AdversaryTowerJob;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.adversary.AdversaryFoxTower;
import kim.biryeong.semiontd.tower.adversary.AdversaryProgressState;
import kim.biryeong.semiontd.tower.adversary.AdversaryProgressStates;
import kim.biryeong.semiontd.tower.adversary.AdversaryTowers;
import kim.biryeong.semiontd.tower.adversary.FoxForm;
import kim.biryeong.semiontd.tower.adversary.FoxRoute;
import kim.biryeong.semiontd.tower.adversary.RivalContribution;
import kim.biryeong.semiontd.tower.adversary.RivalKind;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import kim.biryeong.semiontd.gametest.GameTestParticipantFixture;

public final class AdversaryTowerRuntimeTest extends GameTestParticipantFixture {
    @GameTest
    public void adversaryFoxesUseSharedScoreForManualEvolutionAndReleaseItOnSale(GameTestHelper context) {
        UUID playerId = stableUuid("adversary-multi-fox-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, AdversaryTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        game.players().get(playerId).economy().addMineral(2_000);

        List<BlockPos> foxPositions = List.of(
                base,
                base.offset(1, 0, 0),
                base.offset(2, 0, 0),
                base.offset(3, 0, 0)
        );
        for (BlockPos position : foxPositions) {
            if (!assertEquals(
                    context,
                    TowerPlacementResult.SUCCESS,
                    ProductionTowerService.placeTower(game, playerId, position, AdversaryTowers.FOX.id()),
                    "The first four adversary foxes should be placeable."
            )) {
                return;
            }
        }
        if (!assertEquals(
                context,
                TowerPlacementResult.TOWER_NOT_ALLOWED,
                ProductionTowerService.placeTower(
                        game,
                        playerId,
                        base.offset(4, 0, 0),
                        AdversaryTowers.FOX.id()
                ),
                "The fifth adversary fox should be rejected by the builder limit."
        )) {
            return;
        }

        GridPosition firstPosition = GridPosition.from(foxPositions.getFirst());
        AdversaryFoxTower first = (AdversaryFoxTower) lane.towerAt(firstPosition);
        UUID logicalFoxId = first.foxId();
        first.syncHealth(first.currentMaxHealth() * 0.5);
        AdversaryProgressStates.state(playerId).reconcileRivals(List.of(new RivalContribution(
                stableUuid("adversary-multi-fox-breeze-score"),
                RivalKind.BREEZE,
                50
        )));

        long mineralBeforeEvolution = game.players().get(playerId).economy().mineral();
        if (!assertEquals(
                context,
                TowerUpgradeResult.SUCCESS,
                ProductionTowerService.upgradeTower(
                        game,
                        playerId,
                        firstPosition,
                        AdversaryTowers.typeFor(FoxForm.BREEZE).id()
                ),
                "A scored base fox should manually evolve into Breeze for 200 diamonds."
        )) {
            return;
        }
        AdversaryFoxTower breeze = (AdversaryFoxTower) lane.towerAt(firstPosition);
        if (!assertTrue(context, breeze.foxId().equals(logicalFoxId), "Evolution should preserve the logical fox id.")) {
            return;
        }
        if (!assertClose(context, 0.5, breeze.health() / breeze.currentMaxHealth(), "Evolution should preserve health ratio.")) {
            return;
        }
        if (!assertEquals(context, mineralBeforeEvolution - 200L, game.players().get(playerId).economy().mineral(), "First fox evolution should cost 200 diamonds.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerService.availableUpgrades(game, playerId, firstPosition).isEmpty(), "Final evolution should require one completed intermediate wave.")) {
            return;
        }

        GridPosition secondPosition = GridPosition.from(foxPositions.get(1));
        if (!assertEquals(
                context,
                TowerUpgradeResult.UPGRADE_REQUIREMENTS_NOT_MET,
                ProductionTowerService.upgradeTower(
                        game,
                        playerId,
                        secondPosition,
                        AdversaryTowers.typeFor(FoxForm.BREEZE).id()
                ),
                "Another fox should not claim the already occupied Rapid route."
        )) {
            return;
        }

        new AdversaryTowerJob().onRoundEnded(
                new JobContext(game, game.players().get(playerId)),
                game.currentRound()
        );
        if (!assertEquals(
                context,
                Set.of(AdversaryTowers.typeFor(FoxForm.GOLDEN_FANG).id()),
                ProductionTowerService.availableUpgrades(game, playerId, firstPosition).stream()
                        .map(option -> option.targetType().id())
                        .collect(Collectors.toSet()),
                "One completed Breeze wave should unlock its affordable final form."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerUpgradeResult.SUCCESS,
                ProductionTowerService.upgradeTower(
                        game,
                        playerId,
                        firstPosition,
                        AdversaryTowers.typeFor(FoxForm.GOLDEN_FANG).id()
                ),
                "The selected final form should replace the intermediate fox."
        )) {
            return;
        }
        if (!assertEquals(context, mineralBeforeEvolution - 600L, game.players().get(playerId).economy().mineral(), "Final fox evolution should cost another 400 diamonds.")) {
            return;
        }

        ProductionTowerService.SaleResult sale = ProductionTowerService.sellTower(game, playerId, firstPosition);
        if (!assertEquals(context, TowerSellResult.SUCCESS, sale.result(), "Selling the final fox should succeed.")) {
            return;
        }
        AdversaryProgressState progress = AdversaryProgressStates.state(playerId);
        if (!assertEquals(context, 0, progress.spentScore(RivalKind.BREEZE), "Selling a fox should refund all of its committed score.")) {
            return;
        }
        if (!assertTrue(context, progress.routeOwner(FoxRoute.RAPID).isEmpty(), "Selling a fox should release its route claim.")) {
            return;
        }
        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                ProductionTowerService.placeTower(game, playerId, foxPositions.getFirst(), AdversaryTowers.FOX.id()),
                "A replacement fourth fox should be placeable after the sale."
        )) {
            return;
        }
        context.succeed();
    }
}
