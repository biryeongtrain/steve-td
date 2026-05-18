package kim.biryeong.semiontd.test;

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
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.test.tower.TestTowerTypes;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
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

        JobContext jobContext = new JobContext(game, laneContext.player);
        SemionJob job = laneContext.player.job().orElse(JobRegistry.defaultJob());
        if (!job.canUseTower(jobContext, TestTowerTypes.TEST_DIRECT)) {
            return TowerPlacementResult.TOWER_NOT_ALLOWED_BY_JOB;
        }
        long mineralCost = Math.max(0, job.modifyTowerMineralCost(
                jobContext,
                TestTowerTypes.TEST_DIRECT,
                TestTowerTypes.TEST_DIRECT.mineralCost()
        ));
        if (!laneContext.player.economy().spendMineral(mineralCost)) {
            return TowerPlacementResult.NOT_ENOUGH_MINERAL;
        }

        TestTower tower = new TestTower(
                laneContext.player.uuid(),
                laneContext.player.teamId(),
                laneContext.player.laneId(),
                position
        );
        tower.recordPlacementEconomy(mineralCost, game.currentRound());
        laneContext.lane.addTower(tower);
        job.onTowerPlaced(jobContext, laneContext.lane, TestTowerTypes.TEST_DIRECT);
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
            return TowerUpgradeResult.TOWER_NOT_OWNED;
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

        var targetType = upgrade.targetType();

        JobContext jobContext = new JobContext(game, laneContext.player);
        SemionJob job = laneContext.player.job().orElse(JobRegistry.defaultJob());
        if (!job.canUseTower(jobContext, targetType)) {
            return TowerUpgradeResult.TOWER_NOT_ALLOWED_BY_JOB;
        }
        long mineralCost = Math.max(0, job.modifyTowerMineralCost(jobContext, targetType, upgrade.mineralCost()));
        if (!laneContext.player.economy().spendMineral(mineralCost)) {
            return TowerUpgradeResult.NOT_ENOUGH_MINERAL;
        }

        TestTower evolvedTower = new TestTower(
                targetType,
                testTower.ownerPlayer(),
                testTower.teamId(),
                testTower.laneId(),
                testTower.originalPosition(),
                testTower.position()
        );
        evolvedTower.copyFrom(testTower, mineralCost);
        if (!laneContext.lane.replaceTower(testTower, evolvedTower)) {
            laneContext.player.economy().addMineral(mineralCost);
            return TowerUpgradeResult.NO_TOWER_AT_POSITION;
        }
        job.onTowerPlaced(jobContext, laneContext.lane, targetType);
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
            case OUTSIDE_LANE_AREA, OCCUPIED, TOWER_NOT_ALLOWED_BY_JOB, NOT_ENOUGH_MINERAL, SUCCESS -> throw new IllegalStateException(
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
