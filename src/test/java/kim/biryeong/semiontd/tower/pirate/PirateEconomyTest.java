package kim.biryeong.semiontd.tower.pirate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentEconomyService;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.EconomyService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerSellResult;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.job.PirateTowerJob;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import xyz.nucleoid.map_templates.BlockBounds;

class PirateEconomyTest {
    private final UUID owner = UUID.randomUUID();
    private SemionGame game;
    private SemionPlayer player;
    private PlayerLane lane;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void setup() throws ReflectiveOperationException {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        EconomyConfig economy = EconomyConfig.defaultConfig();
        game = new SemionGame(economy, WaveConfig.defaultConfig(), new GameArena(Map.of()));
        player = new SemionPlayer(owner, "pirate-economy-test", TeamId.RED, 1, new PlayerEconomy(economy));
        player.assignJob(new PirateTowerJob());
        player.economy().overrideStartingValues(100_000, 100_000, 10, 1);
        game.players().put(owner, player);
        game.teams().get(TeamId.RED).activate();
        LaneRegionLayout layout = new LaneRegionLayout(1, new Vec3(.5, 64, .5),
                List.of(new Vec3(.5, 64, 4.5)), new Vec3(.5, 64, 10.5),
                BlockBounds.of(new BlockPos(0, 63, 0), new BlockPos(40, 66, 10)),
                List.of(new GridPosition(0, 63, 10)));
        assertTrue(game.teams().get(TeamId.RED).addPlayer(player, null, layout));
        lane = game.playerLane(owner).orElseThrow();
        Field phase = SemionGame.class.getDeclaredField("phase");
        phase.setAccessible(true);
        phase.set(game, RoundPhase.PREPARE_AND_SUMMON);
        Field round = SemionGame.class.getDeclaredField("currentRound");
        round.setAccessible(true);
        round.setInt(game, 5);
        PirateStates.open(game, player);
    }

    @AfterEach
    void cleanup() {
        game.close();
        PirateStates.close(owner);
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void twentyChestSalesKeepTwentyDiamondsAndOneHundredPermanentHealth() {
        buyFerrymen(0, 9, 0);
        long before = player.economy().diamond();
        for (int index = 0; index < 20; index++) {
            sell(buy(PirateTowers.SHABBY_CHEST, 30, 50));
        }
        assertEquals(20, player.economy().diamond() - before);
        assertEquals(100, lane.towers().stream().mapToDouble(Tower::permanentMaxHealthBonus).sum(), 1e-9);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0, -25",
            "1, 0, 0, -23",
            "12, 0, 0, -1",
            "11, 1, 0, 0",
            "13, 0, 0, 0",
            "0, 9, 0, 1",
            "0, 0, 7, 1",
            "0, 0, 22, 31"
    })
    void directSalesOnlyHalvePositiveProfit(int ordinary, int veteran, int legendary, long expectedProfit) {
        buyFerrymen(ordinary, veteran, legendary);
        long before = player.economy().diamond();
        sell(buy(PirateTowers.SHABBY_CHEST, 30, 50));
        assertEquals(expectedProfit, player.economy().diamond() - before);
    }

    @Test
    void upgradedChestUsesActualCumulativePurchaseCost() {
        buyFerrymen(0, 0, 22);
        long before = player.economy().diamond();
        PirateTower original = buy(PirateTowers.SHABBY_CHEST, 30, 50);
        PirateTower upgraded = new PirateTower(PirateTowers.DEEP_CHEST, owner, TeamId.RED, 1, original.position());
        assertTrue(player.economy().spendDiamond(100));
        upgraded.copyFrom(original, 100);
        assertTrue(lane.removeTower(original));
        lane.addTower(upgraded);
        PirateStates.recordDiamondSpend(player, 100);
        assertEquals(150, upgraded.paidMineralCost());
        assertEquals(75, upgraded.sellRefundAmount());
        sell(upgraded);
        assertEquals(6, player.economy().diamond() - before);
        assertEquals(10, lane.towers().stream().mapToDouble(Tower::permanentMaxHealthBonus).sum(), 1e-9);
    }

    @Test
    void regularIncomeStillPaysEveryOwnedFerrymanInFull() {
        buyFerrymen(1, 1, 1);
        long before = player.economy().diamond();
        PirateStates.grantFerrymanIncome(player);
        assertEquals(9, player.economy().diamond() - before);
        before = player.economy().diamond();
        new EconomyService(EconomyConfig.defaultConfig(), game).payRoundIncome(game.players().values(), game.teams());
        assertEquals(19, player.economy().diamond() - before);
        Monster monster = new Monster("pirate-income-test", TeamId.RED, 1, Optional.empty(), Optional.empty(),
                10, 0, 1, AttackKind.MELEE, "minecraft:zombie", 5);
        monster.recordLastHit(owner, KillSourceKind.TOWER);
        before = player.economy().diamond();
        new EconomyService(EconomyConfig.defaultConfig(), game).awardMonsterKillReward(monster, game.players());
        assertEquals(14, player.economy().diamond() - before);
    }

    @ParameterizedTest
    @CsvSource({"wartime_economy, 74", "emergency_loan, 9"})
    void seasonThreePayoutPaysFerrymenOnceWithoutBypassingWithholdingOrClosure(String card, long expected)
            throws ReflectiveOperationException {
        player.economy().overrideStartingValues(100_000, 100_000, 100, 1);
        enableSeasonThree(card);
        buyFerrymen(1, 1, 1);
        EconomyService service = new EconomyService(EconomyConfig.defaultConfig(), game);
        long before = player.economy().diamond();
        service.payRoundIncome(5, game.players().values(), game.teams());
        assertEquals(expected, player.economy().diamond() - before);
        service.payRoundIncome(5, game.players().values(), game.teams());
        service.payRoundIncome(game.players().values(), game.teams());
        assertEquals(expected, player.economy().diamond() - before);
        AugmentEconomyService.close(player);
        assertSame(player, PirateStates.player(owner), "Closed augment state must reject even an attached pirate payout");
        service.payRoundIncome(6, game.players().values(), game.teams());
        service.payRoundIncome(game.players().values(), game.teams());
        assertEquals(expected, player.economy().diamond() - before);
    }

    @Test
    void ticketUpgradeRecordsOnlyPaidDiamondsAndFailedAttemptsDoNotSpend() throws ReflectiveOperationException {
        enableSeasonThree("forbidden_blueprint");
        PirateTower original = buy(PirateTowers.FERRYMAN, 1, 200);
        PirateStates.startRound(owner);
        long before = player.economy().diamond();
        assertEquals(TowerUpgradeResult.SUCCESS, ProductionTowerService.upgradeTower(
                game, owner, original.position(), PirateTowers.VETERAN_FERRYMAN.id(), true));
        Tower upgraded = lane.towerAt(original.position());
        assertEquals(200, before - player.economy().diamond());
        assertEquals(200, PirateStates.diamondSpent(owner));
        assertEquals(400, upgraded.paidMineralCost());
        assertEquals(original.logicalId(), upgraded.logicalId());
        assertEquals(1, player.economyAugments().remainingTickets());
        assertEquals(1, player.economyAugments().usedTickets());
        before = player.economy().diamond();
        long progressBefore = PirateStates.admiralProgress(owner);
        assertEquals(TowerUpgradeResult.UPGRADE_REQUIREMENTS_NOT_MET, ProductionTowerService.upgradeTower(
                game, owner, upgraded.position(), PirateTowers.LEGENDARY_FERRYMAN.id(), true));
        assertEquals(before, player.economy().diamond());
        assertEquals(200, PirateStates.diamondSpent(owner));
        assertEquals(progressBefore, PirateStates.admiralProgress(owner));
        PirateTower second = buy(PirateTowers.FERRYMAN, 2, 200);
        assertTrue(player.economy().spendDiamond(player.economy().diamond() - 199));
        long spentBefore = PirateStates.diamondSpent(owner);
        progressBefore = PirateStates.admiralProgress(owner);
        assertEquals(TowerUpgradeResult.NOT_ENOUGH_MINERAL, ProductionTowerService.upgradeTower(
                game, owner, second.position(), PirateTowers.VETERAN_FERRYMAN.id(), true));
        assertSame(second, lane.towerAt(second.position()));
        assertEquals(199, player.economy().diamond());
        assertEquals(spentBefore, PirateStates.diamondSpent(owner));
        assertEquals(progressBefore, PirateStates.admiralProgress(owner));
        assertEquals(1, player.economyAugments().remainingTickets());
        assertEquals(1, player.economyAugments().usedTickets());
    }

    @Test
    void staleTicketCommitRestoresTowerAndNeverTriggersAdmiral() throws ReflectiveOperationException {
        enableSeasonThree("forbidden_blueprint");
        buy(PirateTowers.ADMIRAL, 0, 1_000);
        PirateTower original = buy(PirateTowers.FERRYMAN, 1, 200);
        var source = ProductionTowerCatalog.entry(PirateTowers.FERRYMAN).orElseThrow();
        var target = ProductionTowerCatalog.entry(PirateTowers.VETERAN_FERRYMAN).orElseThrow();
        var upgrade = ProductionTowerCatalog.upgrade(source.type(), target.type().id()).orElseThrow();
        // Keep the real factories and upgrade price; invalidate only the quote during target construction.
        ProductionTowerCatalog.clear();
        ProductionTowerCatalog.register(source.type(), source.factory(), source.tier());
        ProductionTowerCatalog.register(target.type(), (type, id, team, laneId, origin, current) -> {
            assertTrue(AugmentEconomyService.setContract(player, 5, AugmentEconomyService.Contract.NONE));
            return target.factory().create(type, id, team, laneId, origin, current);
        }, target.tier());
        ProductionTowerCatalog.linkUpgrade(source.type(), upgrade.id(), upgrade.displayName(), target.type(), upgrade.mineralCost());
        long before = player.economy().diamond();
        long spentBefore = PirateStates.diamondSpent(owner);
        long progressBefore = PirateStates.admiralProgress(owner);
        double healthBefore = lane.towers().stream().mapToDouble(Tower::permanentMaxHealthBonus).sum();
        double damageBefore = lane.towers().stream().mapToDouble(Tower::permanentFlatDamageBonus).sum();
        assertEquals(TowerUpgradeResult.UPGRADE_REQUIREMENTS_NOT_MET, ProductionTowerService.upgradeTower(
                game, owner, original.position(), upgrade.id(), true));
        assertSame(original, lane.towerAt(original.position()));
        assertEquals(before, player.economy().diamond());
        assertEquals(spentBefore, PirateStates.diamondSpent(owner));
        assertEquals(progressBefore, PirateStates.admiralProgress(owner));
        assertEquals(healthBefore, lane.towers().stream().mapToDouble(Tower::permanentMaxHealthBonus).sum(), 1e-9);
        assertEquals(damageBefore, lane.towers().stream().mapToDouble(Tower::permanentFlatDamageBonus).sum(), 1e-9);
        assertEquals(2, player.economyAugments().remainingTickets());
        assertEquals(0, player.economyAugments().usedTickets());
    }

    @Test
    void maturedChestKeepsItsRewardRefundAndFullFerrymanBonus() {
        buyFerrymen(0, 9, 0);
        PirateTower chest = buy(PirateTowers.SHABBY_CHEST, 30, 50);
        long before = player.economy().diamond();
        // The existing maturity schedule is outside the direct-sale change.
        chest.onRoundEnded(lane, chest.placedRound() + 5);
        assertFalse(lane.towers().contains(chest));
        assertEquals(142, player.economy().diamond() - before);
        assertEquals(5, lane.towers().stream().mapToDouble(Tower::permanentMaxHealthBonus).sum(), 1e-9);
    }

    @Test
    void firstNavigatorGrowsOnlyAfterSeventyFiveSpentDiamonds() {
        PirateTower navigator = buy(PirateTowers.FIRST_NAVIGATOR, 1, 2_200);
        PirateStates.startRound(owner);
        double baseHealth = navigator.effectBaseMaxHealth();
        double baseDamage = navigator.modifyAttackDamage(null, null, navigator.type().damage());
        PirateStates.recordDiamondSpend(player, 74);
        assertEquals(baseHealth, navigator.effectBaseMaxHealth(), 1e-9);
        assertEquals(baseDamage, navigator.modifyAttackDamage(null, null, navigator.type().damage()), 1e-9);
        PirateStates.recordDiamondSpend(player, 1);
        assertEquals(baseHealth + 5, navigator.effectBaseMaxHealth(), 1e-9);
        assertEquals(baseDamage + .5, navigator.modifyAttackDamage(null, null, navigator.type().damage()), 1e-9);
    }

    @Test
    void admiralWaitsForThreeHundredSpentDiamonds() {
        PirateTower admiral = buy(PirateTowers.ADMIRAL, 1, 1_000);
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> settings = new LinkedHashMap<>(abilities.get(PirateTowers.ADMIRAL.id()));
        settings.put("effectCount", 3.0);
        abilities.put(PirateTowers.ADMIRAL.id(), settings);
        TowerBalanceRuntime.apply(new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities));
        long remaining = 300 - PirateStates.admiralProgress(owner);
        double damage = admiral.permanentFlatDamageBonus();
        PirateStates.recordDiamondSpend(player, remaining - 1);
        assertEquals(299, PirateStates.admiralProgress(owner));
        assertEquals(damage, admiral.permanentFlatDamageBonus(), 1e-9);
        PirateStates.recordDiamondSpend(player, 1);
        assertEquals(0, PirateStates.admiralProgress(owner));
        assertEquals(damage + .5, admiral.permanentFlatDamageBonus(), 1e-9);
    }

    @Test
    void admiralSpendingKeepsItsPaybackAndFullFerrymanBonus() {
        buyFerrymen(0, 9, 0);
        buy(PirateTowers.ADMIRAL, 30, 1_000);
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> admiral = new LinkedHashMap<>(abilities.get(PirateTowers.ADMIRAL.id()));
        // Exercise every existing effect once without relying on the random selection order.
        admiral.put("effectCount", 3.0);
        admiral.put("paybackLow", 50.0);
        admiral.put("paybackHigh", 50.0);
        abilities.put(PirateTowers.ADMIRAL.id(), admiral);
        TowerBalanceRuntime.apply(new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities));
        long before = player.economy().diamond();
        long progressBefore = PirateStates.admiralProgress(owner);
        assertTrue(player.economy().spendDiamond(300));
        PirateStates.recordDiamondSpend(player, 300);
        assertEquals(-223, player.economy().diamond() - before);
        assertEquals(progressBefore, PirateStates.admiralProgress(owner));
    }

    private void buyFerrymen(int ordinary, int veteran, int legendary) {
        int position = 0;
        for (int index = 0; index < ordinary; index++) buy(PirateTowers.FERRYMAN, position++, 200);
        for (int index = 0; index < veteran; index++) buy(PirateTowers.VETERAN_FERRYMAN, position++, 700);
        for (int index = 0; index < legendary; index++) buy(PirateTowers.LEGENDARY_FERRYMAN, position++, 1_500);
        assertTrue(position + 1 <= 23, "Sale scenarios stay within the maximum legal roster size");
    }

    @Test
    void locksmithCascadeSettlesEachChestOnceAndOnlyMultipliesMaturityReward() {
        selectPirateAugments(PirateAugments.LOOT, PirateAugments.LOCKSMITH, PirateAugments.CANNON);
        PirateAugments.onSelected(lane, PirateAugments.CANNON, AugmentConfig.defaults());
        PirateAugments.onSelected(lane, PirateAugments.CANNON, AugmentConfig.defaults());
        assertEquals(1, PirateAugments.cannonStacks(owner));
        buyFerrymen(1, 0, 0);
        PirateTower first = buy(PirateTowers.SHABBY_CHEST, 30, 50);
        PirateTower second = buy(PirateTowers.SHABBY_CHEST, 31, 50);
        PirateTower third = buy(PirateTowers.SHABBY_CHEST, 32, 50);
        PirateTower younger = buy(PirateTowers.SHABBY_CHEST, 33, 50);
        first.recordPlacementEconomy(50, 0);
        second.recordPlacementEconomy(50, 1);
        third.recordPlacementEconomy(50, 1);
        younger.recordPlacementEconomy(50, 2);
        long before = player.economy().diamond();
        first.onRoundEnded(lane, 5);
        assertEquals(3 * (135 + 25 + 2) + 100, player.economy().diamond() - before);
        assertEquals(2, PirateAugments.cannonStacks(owner));
        assertTrue(PirateAugments.freeChestAvailable(owner));
        assertFalse(lane.towers().contains(first));
        assertFalse(lane.towers().contains(second));
        assertFalse(lane.towers().contains(third));
        assertTrue(lane.towers().contains(younger), "Cascade must shorten each surviving chest only once");
        first.onRoundEnded(lane, 5);
        second.onRoundEnded(lane, 5);
        third.onRoundEnded(lane, 5);
        PirateAugments.onMatured(lane, first, 5);
        assertEquals(3 * (135 + 25 + 2) + 100, player.economy().diamond() - before);
        assertEquals(2, PirateAugments.cannonStacks(owner));
    }

    @Test
    void directSaleNeverGrantsMaturityAugments() {
        selectPirateAugments(PirateAugments.LOOT, PirateAugments.LOCKSMITH, PirateAugments.CANNON);
        PirateAugments.onSelected(lane, PirateAugments.CANNON, AugmentConfig.defaults());
        buyFerrymen(1, 0, 0);
        long before = player.economy().diamond();
        sell(buy(PirateTowers.SHABBY_CHEST, 30, 50));
        assertEquals(-23, player.economy().diamond() - before);
        assertFalse(PirateAugments.freeChestAvailable(owner));
        assertEquals(1, PirateAugments.cannonStacks(owner));
    }

    @Test
    void freeChestTicketHasCapacityOneAndRequiresSuccessfulBasicChestPlacement() {
        selectPirateAugments(PirateAugments.LOOT);
        PirateTower first = buy(PirateTowers.SHABBY_CHEST, 30, 50);
        PirateTower second = buy(PirateTowers.SHABBY_CHEST, 31, 50);
        first.onRoundEnded(lane, first.placedRound() + 5);
        assertEquals(0, PirateAugments.placementCost(lane, PirateTowers.SHABBY_CHEST, 50));
        assertEquals(200, PirateAugments.placementCost(lane, PirateTowers.EMPIRE_CHEST, 200));
        PirateTower free = new PirateTower(PirateTowers.SHABBY_CHEST, owner, TeamId.RED, 1, new GridPosition(32, 63, 1));
        free.recordPlacementEconomy(0, 10);
        PirateAugments.onPlaced(lane, free);
        assertTrue(PirateAugments.freeChestAvailable(owner), "Failed/unregistered placement must keep the ticket");
        lane.addTower(free);
        PirateAugments.onPlaced(lane, free);
        assertFalse(PirateAugments.freeChestAvailable(owner));
        assertEquals(0, free.sellRefundAmount());
        second.onRoundEnded(lane, second.placedRound() + 5);
        assertFalse(PirateAugments.freeChestAvailable(owner), "Another maturity in the same round cannot grant another ticket");
        free.onRoundEnded(lane, 15);
        assertTrue(PirateAugments.freeChestAvailable(owner));
        PirateTower held = buy(PirateTowers.SHABBY_CHEST, 34, 50);
        held.onRoundEnded(lane, 16);
        PirateTower next = new PirateTower(PirateTowers.SHABBY_CHEST, owner, TeamId.RED, 1, new GridPosition(35, 63, 1));
        next.recordPlacementEconomy(0, 16);
        lane.addTower(next);
        PirateAugments.onPlaced(lane, next);
        assertFalse(PirateAugments.freeChestAvailable(owner), "Unspent tickets cannot accumulate beyond one");
    }

    @Test
    void cannonUsesOneStackPerWaveAndEndWaveCancelsShotsWithoutRefund() {
        selectPirateAugments(PirateAugments.CANNON);
        PirateAugments.onSelected(lane, PirateAugments.CANNON, AugmentConfig.defaults());
        PirateAugments.beginWave(lane);
        assertEquals(0, PirateAugments.cannonStacks(owner));
        assertEquals(5, PirateAugments.pendingCannonShots(owner));
        PirateAugments.endWave(lane);
        assertEquals(0, PirateAugments.pendingCannonShots(owner));
        assertEquals(0, PirateAugments.cannonStacks(owner));
        PirateStates.close(owner);
        PirateStates.open(game, player);
        assertFalse(PirateAugments.freeChestAvailable(owner));
        assertEquals(0, PirateAugments.cannonStacks(owner));
        PirateAugments.onSelected(lane, PirateAugments.CANNON, AugmentConfig.defaults());
        assertEquals(1, PirateAugments.cannonStacks(owner), "New match may grant its own initial stack");
    }

    private void selectPirateAugments(String... cards) {
        lane.assignAugmentSnapshot(new AugmentSnapshot(AugmentConfig.defaults(), java.util.Arrays.stream(cards)
                .map(id -> new PlayerAugmentState.Selection(5, AugmentRarity.GOLD, id,
                        PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())).toList()));
    }

    private void enableSeasonThree(String card) throws ReflectiveOperationException {
        game.configureAugments(new AugmentConfig(true, false, AugmentConfig.defaults().rarityWeights(), Map.of(), Set.of()));
        Field mode = SemionGame.class.getDeclaredField("matchMode");
        mode.setAccessible(true);
        mode.set(game, MatchMode.NORMAL);
        assertTrue(game.augmentsEnabled());
        AugmentEconomyService.beginPrepare(player, 5);
        AugmentEconomyService.onSelected(player, card, 5, Map.of());
    }

    private PirateTower buy(TowerType type, int x, long paid) {
        // Model paid placement without a Minecraft world; sales use the production service.
        assertTrue(player.economy().spendDiamond(paid));
        PirateTower tower = (PirateTower) ProductionTowerCatalog.find(type.id()).orElseThrow()
                .create(owner, TeamId.RED, 1, new GridPosition(x, 63, 1));
        tower.recordPlacementEconomy(paid, game.currentRound());
        lane.addTower(tower);
        PirateStates.recordDiamondSpend(player, paid);
        return tower;
    }

    private void sell(PirateTower tower) {
        var result = ProductionTowerService.sellTower(game, owner, tower.position());
        assertEquals(TowerSellResult.SUCCESS, result.result());
        assertEquals(tower.sellRefundAmount(), result.refundAmount());
    }
}
