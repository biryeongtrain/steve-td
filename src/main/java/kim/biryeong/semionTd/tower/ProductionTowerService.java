package kim.biryeong.semiontd.tower;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionTeam;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import net.minecraft.core.BlockPos;

public final class ProductionTowerService {
    private ProductionTowerService() {
    }

    public static TowerPlacementResult placeTower(SemionGame game, UUID playerId, BlockPos blockPos, String towerId) {
        LaneContext laneContext = resolveLaneContext(game, playerId);
        if (laneContext.failureResult != null) {
            return laneContext.failureResult;
        }

        Optional<ProductionTowerCatalog.CatalogEntry> entry = ProductionTowerCatalog.find(towerId);
        if (entry.isEmpty()) {
            return TowerPlacementResult.TOWER_NOT_ALLOWED_BY_JOB;
        }
        if (!laneContext.lane.canPlaceTowerAt(blockPos)) {
            return TowerPlacementResult.OUTSIDE_LANE_AREA;
        }

        GridPosition position = GridPosition.from(blockPos);
        if (laneContext.lane.hasTowerAt(position)) {
            return TowerPlacementResult.OCCUPIED;
        }

        JobContext jobContext = new JobContext(game, laneContext.player);
        SemionJob job = laneContext.player.job().orElse(JobRegistry.defaultJob());
        TowerType towerType = entry.get().type();
        if (!job.canUseTower(jobContext, towerType)) {
            return TowerPlacementResult.TOWER_NOT_ALLOWED_BY_JOB;
        }
        long mineralCost = Math.max(0, job.modifyTowerMineralCost(jobContext, towerType, towerType.mineralCost()));
        if (!laneContext.player.economy().spendMineral(mineralCost)) {
            return TowerPlacementResult.NOT_ENOUGH_MINERAL;
        }

        laneContext.lane.addTower(new ProductionTower(
                towerType,
                entry.get().behavior(),
                laneContext.player.uuid(),
                laneContext.player.teamId(),
                laneContext.player.laneId(),
                position
        ));
        job.onTowerPlaced(jobContext, laneContext.lane, towerType);
        return TowerPlacementResult.SUCCESS;
    }

    public static List<ProductionTowerCatalog.CatalogEntry> availableTowers(SemionGame game, UUID playerId) {
        SemionPlayer player = game.players().get(playerId);
        if (player == null) {
            return List.of();
        }
        JobContext context = new JobContext(game, player);
        SemionJob job = player.job().orElse(JobRegistry.defaultJob());
        return ProductionTowerCatalog.all().stream()
                .filter(entry -> job.canUseTower(context, entry.type()))
                .toList();
    }

    private static LaneContext resolveLaneContext(SemionGame game, UUID playerId) {
        if (game.phase() != RoundPhase.PREPARE_AND_SUMMON) {
            return LaneContext.failure(TowerPlacementResult.INVALID_PHASE);
        }

        SemionPlayer player = game.players().get(playerId);
        if (player == null) {
            return LaneContext.failure(TowerPlacementResult.PLAYER_NOT_IN_GAME);
        }

        SemionTeam team = game.teams().get(player.teamId());
        if (team == null || team.eliminated()) {
            return LaneContext.failure(TowerPlacementResult.PLAYER_TEAM_ELIMINATED);
        }

        Optional<PlayerLane> lane = team.laneGroup().lane(player.laneId());
        if (lane.isEmpty()) {
            return LaneContext.failure(TowerPlacementResult.UNKNOWN_LANE);
        }

        return new LaneContext(player, lane.get(), null);
    }

    private record LaneContext(
            SemionPlayer player,
            PlayerLane lane,
            TowerPlacementResult failureResult
    ) {
        private static LaneContext failure(TowerPlacementResult result) {
            return new LaneContext(null, null, result);
        }
    }
}
