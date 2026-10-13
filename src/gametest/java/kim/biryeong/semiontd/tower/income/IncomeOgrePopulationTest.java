package kim.biryeong.semiontd.tower.income;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;
import kim.biryeong.semiontd.job.DemonLordTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerCapacity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

public final class IncomeOgrePopulationTest implements RuntimeArenaFixture {
    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void ogreUsesFourSlotsThroughUpgradeDispatchDeathResetSaleAndRemoval(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        UUID owner = UUID.randomUUID();
        var game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO)));
        try {
            require(game.selectJob(owner, DemonLordTowerJob.ID), "Demon Lord must be selectable.");
            require(game.start(context.getLevel().getServer(), new ParticipantSelectionPlan(MatchMode.NORMAL,
                    List.of(new AssignedParticipant(owner, "ogre-owner", TeamId.RED, 1)), Set.of(), 1)), "The match must start.");
            var lane = game.playerLane(owner).orElseThrow();
            var economy = game.players().get(owner).economy();
            economy.addEmerald(1000000);
            var type = IncomeTowerService.towerType(game.summonShop().find("ogre_champion").orElseThrow());
            require(TowerCapacity.slotCost(type) == 4, "UI and placement must resolve four slots from the common key.");
            BlockPos position = empty(lane);
            require(IncomeTowerService.build(game, owner, position, "ogre_champion") == IncomeTowerService.Result.SUCCESS,
                    "An ogre must fit in the empty lane.");
            var tower = (IncomeTower) lane.towerAt(GridPosition.from(position));
            require(game.towerCapacityUsed(owner) == 4, "One ogre occupies four slots.");
            for (int level = 2; level <= 5; level++) {
                require(IncomeTowerService.upgrade(game, owner, tower.position()) == IncomeTowerService.Result.SUCCESS,
                        "Ogre level upgrade must remain available.");
                require(game.towerCapacityUsed(owner) == 4, "All five levels use the same four-slot cost.");
            }
            while (game.canFitTower(owner, type)) {
                require(IncomeTowerService.build(game, owner, empty(lane), "ogre_champion") == IncomeTowerService.Result.SUCCESS,
                        "Placement must accept every remaining group of four slots.");
            }
            int occupied = game.towerCapacityUsed(owner);
            long emerald = economy.emerald();
            require(IncomeTowerService.build(game, owner, empty(lane), "ogre_champion") == IncomeTowerService.Result.TOWER_LIMIT_REACHED,
                    "Fewer than four free slots must reject another ogre.");
            require(economy.emerald() == emerald, "Rejected placement must not spend emerald.");
            var dispatch = IncomeTowerService.createDispatch(game, game.players().get(owner), tower, TeamId.BLUE, 1).orElseThrow();
            dispatch.syncHealth(0);
            require(game.towerCapacityUsed(owner) == occupied, "A dispatched monster's death does not release its persistent income tower.");
            lane.markWaveStarted(1);
            require(tower.entityId().isEmpty() && game.towerCapacityUsed(owner) == occupied,
                    "Hiding the income body for combat must retain its slots.");
            require(lane.killTower(tower), "The lifecycle regression must remove the tower body.");
            require(game.towerCapacityUsed(owner) == occupied, "Round death preserves placed towers and their occupied slots.");
            lane.resetForRound();
            require(tower.entityId().isPresent() && game.towerCapacityUsed(owner) == occupied,
                    "Round restoration must count the same placed tower exactly once.");
            long refund = IncomeTowerBalance.sellRefund(tower.paidEmerald());
            long beforeSale = economy.emerald();
            require(IncomeTowerService.sell(game, owner, tower.position()) == IncomeTowerService.Result.SUCCESS,
                    "Selling an ogre must succeed during preparation.");
            require(game.towerCapacityUsed(owner) == occupied - 4 && economy.emerald() == beforeSale + refund,
                    "Sale releases exactly four slots and retains the existing emerald refund.");
            require(!lane.removeTower(tower) && game.towerCapacityUsed(owner) == occupied - 4,
                    "Repeated removal cannot release slots twice.");
            require(IncomeTowerService.build(game, owner, position, "ogre_champion") == IncomeTowerService.Result.SUCCESS,
                    "The four released slots must admit a replacement.");
            lane.clearTowers();
            require(game.towerCapacityUsed(owner) == 0, "Clearing the lane releases every occupied slot.");
        } finally {
            game.close();
        }
        context.succeed();
    }

    private static BlockPos empty(PlayerLane lane) {
        var bounds = lane.laneLayout().laneArea();
        for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
            for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                var position = new BlockPos(x, bounds.min().getY(), z);
                if (lane.canPlaceTowerAt(position) && !lane.hasTowerAt(GridPosition.from(position))) return position;
            }
        }
        throw new AssertionError("The fixture must have a free income plot.");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
