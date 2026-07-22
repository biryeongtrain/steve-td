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
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.game.TowerSellResult;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.tower.villager.VillagerAdvStates;
import kim.biryeong.semiontd.tower.ocean.OceanTowers;
import kim.biryeong.semiontd.tower.ocean.OceanWaterTower;
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
        if (entry.isEmpty() || !entry.get().starter()) {
            return TowerPlacementResult.UNKNOWN_TOWER;
        }
        Optional<BlockPos> placementPos = TowerPlacementPositions.resolve(laneContext.lane, blockPos);
        if (placementPos.isEmpty()) {
            return TowerPlacementResult.OUTSIDE_LANE_AREA;
        }

        GridPosition position = GridPosition.from(placementPos.get());
        if (laneContext.lane.hasTowerAt(position)) {
            return TowerPlacementResult.OCCUPIED;
        }

        TowerType towerType = entry.get().type();
        if (OceanTowers.isWaterTower(towerType) && !OceanWaterTower.canPlaceAt(laneContext.lane, position)) {
            return TowerPlacementResult.OCCUPIED;
        }
        if (!canUseTower(game, laneContext.player, towerType)) {
            return TowerPlacementResult.TOWER_NOT_ALLOWED;
        }
        if (!game.canPlaceMoreTowers(playerId)) {
            return TowerPlacementResult.TOWER_LIMIT_REACHED;
        }

        long mineralCost = Math.max(0, towerType.mineralCost());
        if (!laneContext.player.economy().spendMineral(mineralCost)) {
            return TowerPlacementResult.NOT_ENOUGH_MINERAL;
        }

        Tower tower = entry.get().create(
                laneContext.player.uuid(),
                laneContext.player.teamId(),
                laneContext.player.laneId(),
                position
        );
        tower.recordPlacementEconomy(mineralCost, game.currentRound());
        laneContext.lane.addTower(tower);
        VillagerAdvStates.refreshTowerEffects(laneContext.player, laneContext.lane, tower);
        game.recordTowerPlacement(playerId, towerType.id(), position, mineralCost);
        return TowerPlacementResult.SUCCESS;
    }

    public static SaleResult sellTower(SemionGame game, UUID playerId, BlockPos blockPos) {
        LaneContext laneContext = resolveActiveLaneContext(game, playerId);
        if (laneContext.failureResult != null) {
            return SaleResult.failure(mapSellFailure(laneContext.failureResult));
        }
        return TowerPlacementPositions.resolveGrid(laneContext.lane, blockPos)
                .map(position -> sellTower(laneContext, playerId, position))
                .orElseGet(() -> SaleResult.failure(TowerSellResult.NO_TOWER_AT_POSITION));
    }

    public static SaleResult sellTower(SemionGame game, UUID playerId, GridPosition position) {
        LaneContext laneContext = resolveActiveLaneContext(game, playerId);
        if (laneContext.failureResult != null) {
            return SaleResult.failure(mapSellFailure(laneContext.failureResult));
        }
        return sellTower(laneContext, playerId, position);
    }

    private static SaleResult sellTower(LaneContext laneContext, UUID playerId, GridPosition position) {
        Tower tower = laneContext.lane.towerAt(position);
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
        return ProductionTowerCatalog.all().stream()
                .filter(ProductionTowerCatalog.CatalogEntry::starter)
                .filter(entry -> canUseTower(game, player, entry.type()))
                .toList();
    }

    public static List<TowerUpgradeOption> availableUpgrades(SemionGame game, UUID playerId, BlockPos blockPos) {
        LaneContext laneContext = resolveLaneContext(game, playerId);
        if (laneContext.failureResult != null) {
            return List.of();
        }
        return TowerPlacementPositions.resolveGrid(laneContext.lane, blockPos)
                .map(position -> availableUpgrades(game, laneContext, playerId, position))
                .orElseGet(List::of);
    }

    public static List<TowerUpgradeOption> availableUpgrades(SemionGame game, UUID playerId, GridPosition position) {
        LaneContext laneContext = resolveLaneContext(game, playerId);
        if (laneContext.failureResult != null) {
            return List.of();
        }
        return availableUpgrades(game, laneContext, playerId, position);
    }

    private static List<TowerUpgradeOption> availableUpgrades(SemionGame game, LaneContext laneContext, UUID playerId, GridPosition position) {
        Tower tower = laneContext.lane.towerAt(position);
        if (tower == null || !tower.ownerPlayer().equals(playerId)) {
            return List.of();
        }
        return ProductionTowerCatalog.upgrades(tower.type()).stream()
                .filter(option -> canUseTower(game, laneContext.player, option.targetType()))
                .toList();
    }

    public static TowerUpgradeResult upgradeTower(SemionGame game, UUID playerId, BlockPos blockPos, String upgradeId) {
        LaneContext laneContext = resolveLaneContext(game, playerId);
        if (laneContext.failureResult != null) {
            return mapPlacementFailure(laneContext.failureResult);
        }
        return TowerPlacementPositions.resolveGrid(laneContext.lane, blockPos)
                .map(position -> upgradeTower(game, laneContext, playerId, position, upgradeId))
                .orElse(TowerUpgradeResult.NO_TOWER_AT_POSITION);
    }

    public static TowerUpgradeResult upgradeTower(SemionGame game, UUID playerId, GridPosition position, String upgradeId) {
        LaneContext laneContext = resolveLaneContext(game, playerId);
        if (laneContext.failureResult != null) {
            return mapPlacementFailure(laneContext.failureResult);
        }
        return upgradeTower(game, laneContext, playerId, position, upgradeId);
    }

    private static TowerUpgradeResult upgradeTower(SemionGame game, LaneContext laneContext, UUID playerId, GridPosition position, String upgradeId) {
        Tower tower = laneContext.lane.towerAt(position);
        if (tower == null) {
            return TowerUpgradeResult.NO_TOWER_AT_POSITION;
        }
        if (!tower.ownerPlayer().equals(playerId)) {
            return TowerUpgradeResult.TOWER_NOT_OWNED;
        }
        if (!ProductionTowerCatalog.hasUpgrades(tower.type())) {
            return TowerUpgradeResult.TOWER_NOT_UPGRADABLE;
        }

        TowerUpgradeOption upgrade = ProductionTowerCatalog.upgrade(tower.type(), upgradeId).orElse(null);
        if (upgrade == null) {
            return TowerUpgradeResult.UNKNOWN_UPGRADE;
        }

        TowerType targetType = upgrade.targetType();
        Optional<ProductionTowerCatalog.CatalogEntry> targetEntry = ProductionTowerCatalog.entry(targetType);
        if (targetEntry.isEmpty()) {
            return TowerUpgradeResult.UNKNOWN_TARGET_TYPE;
        }
        if (!canUseTower(game, laneContext.player, targetType)) {
            return TowerUpgradeResult.TOWER_NOT_ALLOWED;
        }
        if (!VillagerAdvStates.canUpgrade(laneContext.player, tower, upgrade)) {
            return TowerUpgradeResult.NOT_ENOUGH_ADV_EXPERIENCE;
        }

        long mineralCost = Math.max(0, upgrade.mineralCost());
        if (!laneContext.player.economy().spendMineral(mineralCost)) {
            return TowerUpgradeResult.NOT_ENOUGH_MINERAL;
        }

        Tower upgradedTower = targetEntry.get().create(
                tower.ownerPlayer(),
                tower.teamId(),
                tower.laneId(),
                tower.originalPosition(),
                tower.position()
        );
        upgradedTower.copyFrom(tower, mineralCost);
        if (!laneContext.lane.replaceTower(tower, upgradedTower)) {
            laneContext.player.economy().addMineral(mineralCost);
            return TowerUpgradeResult.NO_TOWER_AT_POSITION;
        }
        VillagerAdvStates.refreshTowerEffects(laneContext.player, laneContext.lane, upgradedTower);
        game.recordTowerUpgrade(playerId, upgradeId, position, mineralCost);
        return TowerUpgradeResult.SUCCESS;
    }

    private static boolean canUseTower(SemionGame game, SemionPlayer player, TowerType towerType) {
        SemionJob job = player.job().orElse(JobRegistry.defaultJob());
        return job.canUseTower(new JobContext(game, player), towerType);
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
            case UNKNOWN_TOWER, TOWER_NOT_ALLOWED, OUTSIDE_LANE_AREA, OCCUPIED, TOWER_LIMIT_REACHED, NOT_ENOUGH_MINERAL, SUCCESS -> throw new IllegalStateException(
                    "Unexpected sell failure " + result
            );
        };
    }

    private static TowerUpgradeResult mapPlacementFailure(TowerPlacementResult result) {
        return switch (result) {
            case INVALID_PHASE -> TowerUpgradeResult.INVALID_PHASE;
            case PLAYER_NOT_IN_GAME -> TowerUpgradeResult.PLAYER_NOT_IN_GAME;
            case PLAYER_TEAM_ELIMINATED -> TowerUpgradeResult.PLAYER_TEAM_ELIMINATED;
            case UNKNOWN_LANE -> TowerUpgradeResult.UNKNOWN_LANE;
            case UNKNOWN_TOWER, TOWER_NOT_ALLOWED, OUTSIDE_LANE_AREA, OCCUPIED, TOWER_LIMIT_REACHED, NOT_ENOUGH_MINERAL, SUCCESS -> throw new IllegalStateException(
                    "Unexpected placement-only failure " + result
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
