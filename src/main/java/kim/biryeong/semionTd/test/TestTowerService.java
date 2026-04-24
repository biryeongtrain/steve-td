package kim.biryeong.semionTd.test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semionTd.game.GridPosition;
import kim.biryeong.semionTd.game.PlayerLane;
import kim.biryeong.semionTd.game.RoundPhase;
import kim.biryeong.semionTd.game.SemionGame;
import kim.biryeong.semionTd.game.SemionPlayer;
import kim.biryeong.semionTd.game.SemionTeam;
import kim.biryeong.semionTd.game.TowerPlacementResult;
import kim.biryeong.semionTd.game.TowerUpgradeResult;
import kim.biryeong.semionTd.test.tower.TestTower;
import kim.biryeong.semionTd.test.tower.TestTowerTypes;
import kim.biryeong.semionTd.tower.Tower;
import kim.biryeong.semionTd.tower.TowerType;
import kim.biryeong.semionTd.tower.TowerUpgradeOption;
import net.minecraft.core.BlockPos;

public final class TestTowerService {
    private TestTowerService() {
    }

    public static TowerPlacementResult placeTestTower(SemionGame game, UUID playerId, BlockPos blockPos) {
        LaneContext laneContext = resolveLaneContext(game, playerId);
        if (laneContext.failureResult != null) {
            return laneContext.failureResult;
        }

        if (!laneContext.lane.canPlaceTowerAt(blockPos)) {
            return TowerPlacementResult.OUTSIDE_LANE_AREA;
        }

        GridPosition position = GridPosition.from(blockPos);
        if (laneContext.lane.hasTowerAt(position)) {
            return TowerPlacementResult.OCCUPIED;
        }

        if (!laneContext.player.economy().spendMineral(TestTowerTypes.TEST_DIRECT.mineralCost())) {
            return TowerPlacementResult.NOT_ENOUGH_MINERAL;
        }

        laneContext.lane.addTower(new TestTower(
                laneContext.player.uuid(),
                laneContext.player.teamId(),
                laneContext.player.laneId(),
                position
        ));
        return TowerPlacementResult.SUCCESS;
    }

    public static List<TowerUpgradeOption> availableUpgrades(SemionGame game, UUID playerId, BlockPos blockPos) {
        LaneContext laneContext = resolveLaneContext(game, playerId);
        if (laneContext.failureResult != null) {
            return List.of();
        }

        Tower tower = laneContext.lane.towerAt(GridPosition.from(blockPos));
        if (!(tower instanceof TestTower testTower)) {
            return List.of();
        }
        return testTower.type().upgradeOptions();
    }

    public static TowerUpgradeResult upgradeTestTower(SemionGame game, UUID playerId, BlockPos blockPos, String upgradeId) {
        LaneContext laneContext = resolveLaneContext(game, playerId);
        if (laneContext.failureResult != null) {
            return mapPlacementFailure(laneContext.failureResult);
        }

        Tower tower = laneContext.lane.towerAt(GridPosition.from(blockPos));
        if (!(tower instanceof TestTower testTower)) {
            return tower == null ? TowerUpgradeResult.NO_TOWER_AT_POSITION : TowerUpgradeResult.TOWER_NOT_UPGRADABLE;
        }
        if (!testTower.ownerPlayer().equals(playerId)) {
            return TowerUpgradeResult.TOWER_NOT_UPGRADABLE;
        }
        if (!testTower.type().hasUpgradeOptions()) {
            return TowerUpgradeResult.TOWER_NOT_UPGRADABLE;
        }

        TowerUpgradeOption upgrade = testTower.type().upgradeOptions().stream()
                .filter(option -> option.id().equalsIgnoreCase(upgradeId))
                .findFirst()
                .orElse(null);
        if (upgrade == null) {
            return TowerUpgradeResult.UNKNOWN_UPGRADE;
        }

        Optional<TowerType> targetType = TestTowerTypes.find(upgrade.targetTypeId());
        if (targetType.isEmpty()) {
            return TowerUpgradeResult.UNKNOWN_TARGET_TYPE;
        }
        if (!laneContext.player.economy().spendMineral(upgrade.mineralCost())) {
            return TowerUpgradeResult.NOT_ENOUGH_MINERAL;
        }

        TestTower evolvedTower = new TestTower(
                targetType.get(),
                testTower.ownerPlayer(),
                testTower.teamId(),
                testTower.laneId(),
                testTower.originalPosition(),
                testTower.position()
        );
        if (!laneContext.lane.replaceTower(testTower, evolvedTower)) {
            laneContext.player.economy().addMineral(upgrade.mineralCost());
            return TowerUpgradeResult.NO_TOWER_AT_POSITION;
        }
        return TowerUpgradeResult.SUCCESS;
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

    private static TowerUpgradeResult mapPlacementFailure(TowerPlacementResult result) {
        return switch (result) {
            case INVALID_PHASE -> TowerUpgradeResult.INVALID_PHASE;
            case PLAYER_NOT_IN_GAME -> TowerUpgradeResult.PLAYER_NOT_IN_GAME;
            case PLAYER_TEAM_ELIMINATED -> TowerUpgradeResult.PLAYER_TEAM_ELIMINATED;
            case UNKNOWN_LANE -> TowerUpgradeResult.UNKNOWN_LANE;
            case OUTSIDE_LANE_AREA, OCCUPIED, NOT_ENOUGH_MINERAL, SUCCESS -> throw new IllegalStateException(
                    "Unexpected placement-only failure " + result
            );
        };
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

