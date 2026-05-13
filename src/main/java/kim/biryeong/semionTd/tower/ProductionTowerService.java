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
import kim.biryeong.semiontd.game.TowerSellResult;
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

        ProductionTower tower = new ProductionTower(
                towerType,
                entry.get().behavior(),
                laneContext.player.uuid(),
                laneContext.player.teamId(),
                laneContext.player.laneId(),
                position
        );
        tower.recordPlacementEconomy(mineralCost, game.currentRound());
        laneContext.lane.addTower(tower);
        job.onTowerPlaced(jobContext, laneContext.lane, towerType);
        return TowerPlacementResult.SUCCESS;
    }

    public static SaleResult sellTower(SemionGame game, UUID playerId, BlockPos blockPos) {
        LaneContext laneContext = resolveActiveLaneContext(game, playerId);
        if (laneContext.failureResult != null) {
            return SaleResult.failure(mapSellFailure(laneContext.failureResult));
        }

        Tower tower = laneContext.lane.towerAt(GridPosition.from(blockPos));
        if (tower == null) {
            return SaleResult.failure(TowerSellResult.NO_TOWER_AT_POSITION);
        }
        if (!tower.ownerPlayer().equals(playerId)) {
            return SaleResult.failure(TowerSellResult.TOWER_NOT_OWNED);
        }

        long refund = tower.sellRefundAmount();
        if (!laneContext.lane.removeTower(tower)) {
            return SaleResult.failure(TowerSellResult.NO_TOWER_AT_POSITION);
        }
        laneContext.player.economy().addMineral(refund);
        return SaleResult.success(refund);
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
        return resolveActiveLaneContext(game, playerId);
    }

    private static LaneContext resolveActiveLaneContext(SemionGame game, UUID playerId) {
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

    private static TowerSellResult mapSellFailure(TowerPlacementResult result) {
        return switch (result) {
            case INVALID_PHASE -> TowerSellResult.INVALID_PHASE;
            case PLAYER_NOT_IN_GAME -> TowerSellResult.PLAYER_NOT_IN_GAME;
            case PLAYER_TEAM_ELIMINATED -> TowerSellResult.PLAYER_TEAM_ELIMINATED;
            case UNKNOWN_LANE -> TowerSellResult.UNKNOWN_LANE;
            case OUTSIDE_LANE_AREA, OCCUPIED, TOWER_NOT_ALLOWED_BY_JOB, NOT_ENOUGH_MINERAL, SUCCESS -> throw new IllegalStateException(
                    "Unexpected sell failure " + result
            );
        };
    }

    public record SaleResult(TowerSellResult result, long refundAmount) {
        public static SaleResult success(long refundAmount) {
            return new SaleResult(TowerSellResult.SUCCESS, Math.max(0, refundAmount));
        }

        public static SaleResult failure(TowerSellResult result) {
            return new SaleResult(result, 0);
        }
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
