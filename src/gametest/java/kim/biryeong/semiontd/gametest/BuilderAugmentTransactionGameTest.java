package kim.biryeong.semiontd.gametest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentEconomyService;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.game.TowerSellResult;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.job.ArmyTowerJob;
import kim.biryeong.semiontd.job.PirateTowerJob;
import kim.biryeong.semiontd.job.VillagerAdvTowerJob;
import kim.biryeong.semiontd.job.WarlockTowerJob;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.tower.army.ArmyBalance;
import kim.biryeong.semiontd.tower.army.ArmyRank;
import kim.biryeong.semiontd.tower.army.ArmyStates;
import kim.biryeong.semiontd.tower.army.ArmyTower;
import kim.biryeong.semiontd.tower.army.ArmyTowers;
import kim.biryeong.semiontd.tower.pirate.PirateAugments;
import kim.biryeong.semiontd.tower.pirate.PirateStates;
import kim.biryeong.semiontd.tower.pirate.PirateTower;
import kim.biryeong.semiontd.tower.pirate.PirateTowers;
import kim.biryeong.semiontd.tower.villager.VillagerAdvStates;
import kim.biryeong.semiontd.tower.villager.VillagerTowers;
import kim.biryeong.semiontd.tower.warlock.WarlockTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

public final class BuilderAugmentTransactionGameTest {
    @GameTest
    public void secondWarlockCoreChargesQuotedSevenHundredAndPreservesFailedPurchase(GameTestHelper context) {
        UUID owner = UUID.randomUUID();
        SemionGame game = game(context, owner, WarlockTowerJob.ID, "job_warlock_towers_p");
        try {
            PlayerLane lane = game.playerLane(owner).orElseThrow();
            SemionPlayer player = game.players().get(owner);
            var type = ProductionTowerCatalog.find(WarlockTowers.BASE_WARLOCK_TOWER.id()).orElseThrow().type();
            long firstCost = ProductionTowerService.placementCost(lane, type);
            long before = player.economy().mineral();
            require(ProductionTowerService.placeTower(game, owner, emptyPosition(lane), type.id()) == TowerPlacementResult.SUCCESS,
                    "First core must place at its original cost.");
            require(before - player.economy().mineral() == firstCost, "First core quote must match actual payment.");
            require(ProductionTowerService.placementCost(lane, type) == 700, "Second core quote must be seven hundred.");
            player.economy().overrideStartingValues(699, 0, 0, 0);
            BlockPos position = emptyPosition(lane);
            require(ProductionTowerService.placeTower(game, owner, position, type.id()) == TowerPlacementResult.NOT_ENOUGH_MINERAL,
                    "An unaffordable second core must fail before payment.");
            require(player.economy().mineral() == 699 && lane.towers().size() == 1, "Failed core must preserve funds and tower count.");
            player.economy().addMineral(1);
            require(ProductionTowerService.placeTower(game, owner, position, type.id()) == TowerPlacementResult.SUCCESS,
                    "Exactly seven hundred must buy the second core.");
            require(player.economy().mineral() == 0 && lane.towerAt(GridPosition.from(position)).paidMineralCost() == 700,
                    "Second core must record only the actual seven hundred spent.");
            require(ProductionTowerService.placeTower(game, owner, emptyPosition(lane), type.id()) == TowerPlacementResult.TOWER_NOT_ALLOWED,
                    "Partnership must retain the two-core limit.");
            context.succeed();
        } finally {
            game.close();
        }
    }

    @GameTest
    public void freePirateChestPreservesTicketOnFailureAndRecordsNoPurchaseCredit(GameTestHelper context) {
        UUID owner = UUID.randomUUID();
        SemionGame game = game(context, owner, PirateTowerJob.ID, "job_pirate_s");
        try {
            PlayerLane lane = game.playerLane(owner).orElseThrow();
            SemionPlayer player = game.players().get(owner);
            BlockPos position = emptyPosition(lane);
            require(ProductionTowerService.placeTower(game, owner, position, PirateTowers.SHABBY_CHEST.id()) == TowerPlacementResult.SUCCESS,
                    "The first paid chest must place.");
            PirateTower chest = (PirateTower) lane.towerAt(GridPosition.from(position));
            chest.onRoundEnded(lane, chest.placedRound() + TowerBalanceRuntime.abilityInt(chest.type().id(), "rounds"));
            require(PirateAugments.freeChestAvailable(owner), "Mature opening must grant the free basic chest ticket.");
            var type = ProductionTowerCatalog.find(PirateTowers.SHABBY_CHEST.id()).orElseThrow().type();
            require(ProductionTowerService.placementCost(lane, type) == 0, "Placement preview must show the free chest.");
            BlockPos occupied = emptyPosition(lane);
            require(ProductionTowerService.placeTower(game, owner, occupied, PirateTowers.FERRYMAN.id()) == TowerPlacementResult.SUCCESS,
                    "A ferryman must occupy the failure test position.");
            long before = player.economy().mineral();
            long credit = PirateStates.diamondSpent(owner);
            require(ProductionTowerService.placeTower(game, owner, occupied, type.id()) == TowerPlacementResult.OCCUPIED,
                    "Occupied-cell placement must fail.");
            require(PirateAugments.freeChestAvailable(owner) && player.economy().mineral() == before,
                    "Failed placement must preserve the chest ticket and money.");
            BlockPos freePosition = emptyPosition(lane);
            require(ProductionTowerService.placeTower(game, owner, freePosition, type.id()) == TowerPlacementResult.SUCCESS,
                    "Free chest must use the normal placement service.");
            Tower free = lane.towerAt(GridPosition.from(freePosition));
            require(!PirateAugments.freeChestAvailable(owner) && free.paidMineralCost() == 0 && free.sellRefundAmount() == 0,
                    "Successful free chest must consume one ticket and add no sale value.");
            require(player.economy().mineral() == before && PirateStates.diamondSpent(owner) == credit,
                    "Free placement must not grant pirate diamond-spend credit.");
            context.succeed();
        } finally {
            game.close();
        }
    }

    @GameTest
    public void freeArmyStarterSurvivesFailedPlacementThenPromotesWithoutSaleValue(GameTestHelper context) {
        UUID owner = UUID.randomUUID();
        SemionGame game = game(context, owner, ArmyTowerJob.ID, "job_army_g2");
        try {
            PlayerLane lane = game.playerLane(owner).orElseThrow();
            SemionPlayer player = game.players().get(owner);
            BlockPos position = emptyPosition(lane);
            require(ProductionTowerService.placeTower(game, owner, position, ArmyTowers.RECRUIT.id()) == TowerPlacementResult.SUCCESS,
                    "Initial recruit must place.");
            ArmyTower retired = (ArmyTower) lane.towerAt(GridPosition.from(position));
            while (retired.service() < ArmyBalance.dischargeService()) {
                retired.onWaveStarted(lane, 1);
                retired.completeServiceWave(lane);
            }
            require(lane.removeTower(retired) && retired.completeDischarge(lane), "Retirement must settle once.");
            require(ArmyStates.hasFreeStarter(owner, ArmyTowers.RECRUIT), "Retirement must grant a starter ticket.");
            BlockPos occupied = emptyPosition(lane);
            require(ProductionTowerService.placeTower(game, owner, occupied, ArmyTowers.CLERK.id()) == TowerPlacementResult.SUCCESS,
                    "Facilities must keep their normal price and must not consume the combat ticket.");
            long before = player.economy().mineral();
            require(ProductionTowerService.placeTower(game, owner, occupied, ArmyTowers.RECRUIT.id()) == TowerPlacementResult.OCCUPIED,
                    "Occupied placement must fail without consuming a free starter.");
            require(ArmyStates.hasFreeStarter(owner, ArmyTowers.RECRUIT), "Failed placement must keep the ticket.");
            var type = ProductionTowerCatalog.find(ArmyTowers.RECRUIT.id()).orElseThrow().type();
            require(ProductionTowerService.placementCost(lane, type) == 0, "Starter quote must show zero.");
            BlockPos freePosition = emptyPosition(lane);
            require(ProductionTowerService.placeTower(game, owner, freePosition, type.id()) == TowerPlacementResult.SUCCESS,
                    "Free starter must place.");
            ArmyTower free = (ArmyTower) lane.towerAt(GridPosition.from(freePosition));
            require(free.rank() == ArmyRank.CORPORAL && free.paidMineralCost() == 0 && free.sellRefundAmount() == 0,
                    "Free starter must gain one rank and no paid sale value.");
            require(player.economy().mineral() == before && !ArmyStates.hasFreeStarter(owner, type),
                    "Successful free starter must consume the ticket without payment.");
            context.succeed();
        } finally {
            game.close();
        }
    }

    @GameTest
    public void earlyGraduationAdjustsPaymentBeforeTicketAndTemporaryOverridesCannotSell(GameTestHelper context) {
        UUID owner = UUID.randomUUID();
        SemionGame game = game(context, owner, VillagerAdvTowerJob.ID, "job_villager_adv_towers_g1");
        try {
            PlayerLane lane = game.playerLane(owner).orElseThrow();
            SemionPlayer player = game.players().get(owner);
            BlockPos position = emptyPosition(lane);
            require(ProductionTowerService.placeTower(game, owner, position, VillagerTowers.ADV_T1_GOLEM_TOWER.id()) == TowerPlacementResult.SUCCESS,
                    "ADV starter must place.");
            Tower original = lane.towerAt(GridPosition.from(position));
            TowerUpgradeOption option = ProductionTowerCatalog.upgrades(original.type()).getFirst();
            long price = (long) Math.ceil(option.mineralCost() * 1.4);
            require(VillagerAdvStates.experience(original) == 0 && ProductionTowerService.upgradeCost(original, option) == price,
                    "Zero-experience ADV tower must quote forty percent more.");
            player.economy().overrideStartingValues(price - 1, 0, 0, 0);
            require(ProductionTowerService.upgradeTower(game, owner, original.position(), option.id())
                            == TowerUpgradeResult.NOT_ENOUGH_MINERAL,
                    "Normal upgrade must require the adjusted price before touching investment or tickets.");
            require(player.economy().mineral() == price - 1 && lane.towerAt(original.position()) == original,
                    "Failed adjusted-price upgrade must preserve funds and the original tower.");
            AugmentEconomyService.beginPrepare(player, game.currentRound());
            AugmentEconomyService.onSelected(player, "forbidden_blueprint", game.currentRound(), Map.of());
            long expected = Math.max(0, price - 300);
            long before = player.economy().mineral();
            require(ProductionTowerService.upgradeTower(game, owner, original.position(), option.id(), true) == TowerUpgradeResult.SUCCESS,
                    "Early graduation must bypass experience and accept the ticket after price adjustment.");
            Tower upgraded = lane.towerAt(GridPosition.from(position));
            require(before - player.economy().mineral() == expected
                            && upgraded.paidMineralCost() == original.paidMineralCost() + expected,
                    "Ticket upgrade must record only adjusted cost minus ticket value.");
            BlockPos copyPosition = emptyPosition(lane);
            ProductionTower copy = new ProductionTower(VillagerTowers.ADV_T1_GOLEM_TOWER, owner, TeamId.RED, 1,
                    GridPosition.from(copyPosition), GridPosition.from(copyPosition)) {
                @Override public boolean canBeSold() { return true; }
            };
            copy.markTemporaryCopy(original.logicalId());
            lane.addTower(copy);
            require(ProductionTowerService.availableUpgrades(game, owner, copy.position()).isEmpty(), "Copies must expose no upgrade choices.");
            require(ProductionTowerService.upgradeTower(game, owner, copy.position(), option.id()) == TowerUpgradeResult.TOWER_NOT_UPGRADABLE,
                    "Copies must reject upgrade requests.");
            require(ProductionTowerService.sellTower(game, owner, copy.position()).result() == TowerSellResult.TOWER_NOT_SELLABLE,
                    "Copy sale restriction must override family canBeSold implementations.");
            context.succeed();
        } finally {
            game.close();
        }
    }

    private static SemionGame game(GameTestHelper context, UUID owner, ResourceLocation job, String... cards) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO)));
        game.configureAugments(new AugmentConfig(true, false, AugmentConfig.defaults().rarityWeights(), Map.of(), Set.of()));
        require(game.selectJob(owner, job), "Job selection must succeed.");
        require(game.start(context.getLevel().getServer(), new ParticipantSelectionPlan(MatchMode.NORMAL,
                List.of(new AssignedParticipant(owner, "augment-transaction", TeamId.RED, 1)), Set.of(), 1)),
                "Transaction test game must start.");
        game.players().get(owner).economy().addMineral(10000);
        game.playerLane(owner).orElseThrow().assignAugmentSnapshot(new AugmentSnapshot(AugmentConfig.defaults(),
                Arrays.stream(cards).map(id -> new PlayerAugmentState.Selection(5, AugmentRarity.GOLD, id,
                        PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())).toList()));
        return game;
    }

    private static BlockPos emptyPosition(PlayerLane lane) {
        var bounds = lane.laneLayout().laneArea();
        for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
            for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                BlockPos candidate = new BlockPos(x, bounds.min().getY(), z);
                if (lane.canPlaceTowerAt(candidate) && !lane.hasTowerAt(GridPosition.from(candidate))) return candidate;
            }
        }
        throw new AssertionError("No empty tower position.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
