package kim.biryeong.semiontd.tower.gamble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.GambleTowerJob;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.tower.illager.IllagerTowers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class GambleTowerTest {
    private static final double EPSILON = 0.0001;
    private static final UUID OWNER = UUID.nameUUIDFromBytes("gamble-test".getBytes(StandardCharsets.UTF_8));

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void reloadCatalog() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void allThirtySixTwoDiceOutcomesUseTheNonlinearScoreAndDoubleRule() {
        double[] bySum = {0, 0, -70, -50, -30, -10, 20, 40, 50, 60, 90, 120, 150};
        double abilityAdjustedTotal = 0.0;
        for (int first = 1; first <= 6; first++) {
            for (int second = 1; second <= 6; second++) {
                double expected = bySum[first + second] * (first == second ? 2.0 : 1.0);
                assertEquals(expected, GambleRolls.defaultTwoDiceDelta(first, second), EPSILON,
                        first + "+" + second);
                assertEquals(expected, GambleRolls.twoDiceDelta(first, second), EPSILON,
                        "configured " + first + "+" + second);
                abilityAdjustedTotal += expected > 0.0 ? expected * 0.75 : expected;
            }
        }
        assertEquals(38.8888888889, GambleRolls.defaultExpectedTwoDiceDelta(), EPSILON);
        assertEquals(38.8888888889, GambleRolls.expectedTwoDiceDelta(), EPSILON);
        assertEquals(26.3888888889, abilityAdjustedTotal / 36.0, EPSILON);
        assertEquals(-140.0, GambleRolls.defaultTwoDiceDelta(1, 1), EPSILON);
        assertEquals(300.0, GambleRolls.defaultTwoDiceDelta(6, 6), EPSILON);
    }

    @Test
    void twoDiceResultTextCallsOutDoublesOnlyForMatchingFaces() {
        assertEquals("6+6=12 더블!", GambleRolls.formatResultRoll(GambleBet.TWO_DICE, 6, 6));
        assertEquals("2+5=7", GambleRolls.formatResultRoll(GambleBet.TWO_DICE, 2, 5));
        assertEquals("3", GambleRolls.formatResultRoll(GambleBet.ODD, 3, 0));
        assertEquals("4", GambleRolls.formatResultRoll(GambleBet.EVEN, 4, 0));
        assertThrows(IllegalArgumentException.class,
                () -> GambleRolls.formatResultRoll(GambleBet.TWO_DICE, 1, 0));
    }

    @Test
    void twoDiceTotalsTenAndAboveRewardTwoDistinctStatsInOneBet() {
        for (int first = 1; first <= 6; first++) {
            for (int second = 1; second <= 6; second++) {
                assertEquals(first + second >= 10 ? 2 : 1,
                        GambleRolls.twoDiceStatRewardCount(first, second), first + "+" + second);
            }
        }
        for (int firstIndex = -3; firstIndex <= 5; firstIndex++) {
            for (int offset = -2; offset <= 3; offset++) {
                List<GambleStat> stats = GambleRewards.chooseDistinctStats(firstIndex, offset);
                assertEquals(2, stats.size());
                assertFalse(stats.get(0) == stats.get(1));
            }
        }

        double splitScore = GambleRolls.twoDiceDelta(6, 6) / 2.0;
        assertEquals(750, GambleBalance.statDelta(GambleStat.MAX_HEALTH, splitScore), EPSILON);
        assertEquals(75, GambleBalance.statDelta(GambleStat.DAMAGE, splitScore), EPSILON);
        assertEquals(7.5, GambleBalance.statDelta(GambleStat.RANGE, splitScore), EPSILON);
        GambleState state = GambleState.EMPTY.recordStats(List.of(
                new GambleState.StatChange(GambleStat.MAX_HEALTH, 750, 110),
                new GambleState.StatChange(GambleStat.DAMAGE, 75, 10)
        ), 300, "6+6 분할 복합 보상");
        assertEquals(1, state.totalBets());
        assertEquals(300, state.cumulativeScore(), EPSILON);
        assertEquals(750, state.maxHealthDelta(), EPSILON);
        assertEquals(75, state.damageDelta(), EPSILON);
    }

    @Test
    void oddEvenAndFixedStatConversionNeverUseMultiplicativePercentages() {
        assertEquals(70.0, GambleRolls.oddEvenDelta(GambleBet.ODD, 3), EPSILON);
        assertEquals(-40.0, GambleRolls.oddEvenDelta(GambleBet.ODD, 4), EPSILON);
        assertEquals(70.0, GambleRolls.oddEvenDelta(GambleBet.EVEN, 4), EPSILON);
        assertEquals(-40.0, GambleRolls.oddEvenDelta(GambleBet.EVEN, 3), EPSILON);
        assertThrows(IllegalArgumentException.class,
                () -> GambleRolls.oddEvenDelta(GambleBet.TWO_DICE, 3));
        assertEquals(6.25, (70.0 * 0.75 - 40.0) / 2.0, EPSILON,
                "Odd/even stat expectation must remain positive while abilities can replace wins.");
        assertEquals(19.0, (70.0 - 40.0 * 0.80) / 2.0, EPSILON,
                "Insured odd/even must trade peak growth for its lower price and lower variance.");

        assertEquals(200.0, GambleBalance.statDelta(GambleStat.MAX_HEALTH, 40), EPSILON);
        assertEquals(20.0, GambleBalance.statDelta(GambleStat.DAMAGE, 40), EPSILON);
        assertEquals(2.0, GambleBalance.statDelta(GambleStat.RANGE, 40), EPSILON);
        assertEquals(1.0, GambleBalance.statDelta(GambleStat.SPLASH_RADIUS, 40), EPSILON);
        assertEquals(50.0, GambleBalance.statDelta(GambleStat.MAX_HEALTH, 10), EPSILON);
        assertEquals(5.0, GambleBalance.statDelta(GambleStat.DAMAGE, 10), EPSILON);
        assertEquals(0.5, GambleBalance.statDelta(GambleStat.RANGE, 10), EPSILON);
        assertEquals(0.25, GambleBalance.statDelta(GambleStat.SPLASH_RADIUS, 10), EPSILON);
    }

    @Test
    void stateCapsScoreAndPositiveDeltasAndFloorsEachStatAtTwentyPercent() {
        GambleState state = GambleState.EMPTY
                .recordStat(GambleStat.MAX_HEALTH, 40, 100, 8, "hp")
                .recordStat(GambleStat.DAMAGE, 4, 8, 8, "damage")
                .recordStat(GambleStat.RANGE, 1, 6, 20, "range")
                .recordStat(GambleStat.SPLASH_RADIUS, 1, 1.5, 40, "splash");
        assertEquals(140, state.resolvedValue(GambleStat.MAX_HEALTH, 100), EPSILON);
        assertEquals(12, state.resolvedValue(GambleStat.DAMAGE, 8), EPSILON);
        assertEquals(7, state.resolvedValue(GambleStat.RANGE, 6), EPSILON);
        assertEquals(2.5, state.resolvedValue(GambleStat.SPLASH_RADIUS, 1.5), EPSILON);

        assertEquals(76, state.cumulativeScore(), EPSILON);
        state = state.recordStat(GambleStat.DAMAGE, 10_000, 8, 100, "capped");
        assertEquals(258, state.resolvedValue(GambleStat.DAMAGE, 8), EPSILON);
        state = state.recordStat(GambleStat.DAMAGE, -20_000, 8, -140, "floor");
        assertEquals(1.6, state.resolvedValue(GambleStat.DAMAGE, 8), EPSILON);
        assertEquals(-6.4, state.damageDelta(), EPSILON);
        state = state.recordStat(GambleStat.SPLASH_RADIUS, -100, 1.5, -28, "splash floor");
        assertEquals(0.3, state.resolvedValue(GambleStat.SPLASH_RADIUS, 1.5), EPSILON);
        assertEquals(-1.2, state.splashRadiusDelta(), EPSILON);
        assertEquals(8, state.cumulativeScore(), EPSILON);

        GambleState capped = GambleState.EMPTY.recordStat(
                GambleStat.MAX_HEALTH, 20_000, 100, 2_500, "score cap");
        assertEquals(500, capped.cumulativeScore(), EPSILON);
        assertEquals(2_500, capped.maxHealthDelta(), EPSILON);
        assertTrue(capped.atScoreCap());
        GamblerTower cappedTower = new GamblerTower(GambleTowers.GAMBLER, OWNER, TeamId.RED, 1,
                new GridPosition(0, 64, 0), new GridPosition(0, 64, 0));
        cappedTower.setData(GamblerTower.STATE, capped);
        for (GambleBet bet : GambleBet.values()) {
            TowerUpgradeOption option = ProductionTowerCatalog.upgrade(
                    GambleTowers.GAMBLER, bet.upgradeId()).orElseThrow();
            assertFalse(cappedTower.meetsUpgradeRequirements(null, option));
        }
        assertTrue(cappedTower.runtimeDetailLines().stream()
                .anyMatch(line -> line.contains("도박 상태: 종료")));
        // The final slot bet keeps its full reward while the score reaches its cap.

        GambleState before = GambleState.EMPTY.recordAbility(GambleAbility.LOSS_INSURANCE, 499, "near cap");
        var result = GambleSlots.resolve(GambleSlots.Symbol.DIAMOND,
                GambleSlots.Symbol.DIAMOND, GambleSlots.Symbol.DIAMOND);
        GambleState after = before.recordStats(List.of(
                new GambleState.StatChange(GambleStat.DAMAGE, GambleBalance.statDelta(GambleStat.DAMAGE, 150), 5),
                new GambleState.StatChange(GambleStat.MAGIC_DAMAGE, GambleBalance.statDelta(GambleStat.MAGIC_DAMAGE, 150), 5)
        ), result.score(), result.display());
        assertEquals(500, after.cumulativeScore());
        assertEquals(75, after.damageDelta());
        assertEquals(75, after.magicDamageDelta());
        assertTrue(after.atScoreCap());
    }

    @Test
    void balanceReloadReclampsExistingScoreStatsHealthAndUpgradeAvailability() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceConfig legacy = withGambleScores(defaults, 1_000.0, 2_000.0);
        try {
            ProductionTowerCatalogs.reloadBuiltIns(legacy);
            GamblerTower gambler = new GamblerTower(
                    ProductionTowerCatalog.find(GambleTowers.GAMBLER.id()).orElseThrow().type(),
                    OWNER, TeamId.RED, 1,
                    new GridPosition(0, 64, 0), new GridPosition(0, 64, 0));
            gambler.setData(GamblerTower.STATE, new GambleState(
                    10_000.0, 1_000.0, 0.0, 100.0, 50.0,
                    2_000.0, Set.of(GambleAbility.LOSS_INSURANCE), 99, "이전 상한"
            ));
            gambler.syncMaxHealth(gambler.effectBaseMaxHealth(), false);
            gambler.syncHealth(gambler.currentMaxHealth() * 0.5);

            ProductionTowerCatalogs.reloadBuiltIns(defaults);
            gambler.refreshType(
                    ProductionTowerCatalog.find(GambleTowers.GAMBLER.id()).orElseThrow().type(), null);

            GambleState rebalanced = gambler.state();
            assertEquals(500.0, rebalanced.cumulativeScore(), EPSILON);
            assertEquals(2_500.0, rebalanced.maxHealthDelta(), EPSILON);
            assertEquals(250.0, rebalanced.damageDelta(), EPSILON);
            assertEquals(25.0, rebalanced.rangeDelta(), EPSILON);
            assertEquals(12.5, rebalanced.splashRadiusDelta(), EPSILON);
            assertEquals(gambler.currentMaxHealth() * 0.5, gambler.health(), EPSILON);
            assertTrue(rebalanced.atScoreCap());
            assertTrue(gambler.runtimeDetailLines().stream()
                    .anyMatch(line -> line.contains("+500.0 / +500.0")));
            for (GambleBet bet : GambleBet.values()) {
                TowerUpgradeOption option = ProductionTowerCatalog.upgrade(
                        GambleTowers.GAMBLER, bet.upgradeId()).orElseThrow();
                assertFalse(gambler.meetsUpgradeRequirements(null, option));
            }
        } finally {
            ProductionTowerCatalogs.reloadBuiltIns(defaults);
        }
    }

    @Test
    void insuranceAndAbilityRewardRulesAreDeterministicAndUnique() {
        GambleState insured = GambleState.EMPTY.recordAbility(GambleAbility.LOSS_INSURANCE, 40, "insured");
        assertEquals(-8.0, GambleRewards.insuredDelta(insured, -10.0), EPSILON);
        assertEquals(10.0, GambleRewards.insuredDelta(insured, 10.0), EPSILON);
        assertEquals(-10.0, GambleRewards.insuredDelta(GambleState.EMPTY, -10.0), EPSILON);

        assertTrue(GambleRewards.awardsAbility(GambleState.EMPTY, 2.0, 0.249999));
        assertFalse(GambleRewards.awardsAbility(GambleState.EMPTY, 2.0, 0.25));
        assertFalse(GambleRewards.awardsAbility(GambleState.EMPTY, -5.0, 0.0));
        GambleState all = GambleState.EMPTY.recordAbility(GambleAbility.LOSS_INSURANCE, 40, "all");
        assertFalse(GambleRewards.awardsAbility(all, 40.0, 0.0));
        assertEquals(0, GambleRewards.statRewardScore(70, GambleAbility.LOSS_INSURANCE));
        assertEquals(0, GambleRewards.statRewardScore(40, GambleAbility.LOSS_INSURANCE));
        double remainder = GambleRewards.statRewardScore(GambleRolls.twoDiceDelta(6, 6), GambleAbility.LOSS_INSURANCE);
        assertEquals(230, remainder);
        assertEquals(300, GambleRewards.statRewardScore(300, null));
        GambleState reward = GambleState.EMPTY.recordReward(List.of(
                new GambleState.StatChange(GambleStat.MAX_HEALTH, GambleBalance.statDelta(GambleStat.MAX_HEALTH, remainder / 2), 110),
                new GambleState.StatChange(GambleStat.DAMAGE, GambleBalance.statDelta(GambleStat.DAMAGE, remainder / 2), 5)),
                GambleAbility.LOSS_INSURANCE, 300, "double with insurance");
        assertEquals(575, reward.maxHealthDelta());
        assertEquals(57.5, reward.damageDelta());
        assertTrue(reward.has(GambleAbility.LOSS_INSURANCE));
        assertEquals(1, reward.totalBets());
        assertEquals(300, reward.cumulativeScore());
        assertEquals(1, GambleRewards.missingAbilities(GambleState.EMPTY).size());
        assertEquals(0, GambleRewards.missingAbilities(all).size());
        assertEquals(GambleAbility.LOSS_INSURANCE, GambleRewards.chooseMissing(GambleState.EMPTY, 0));
        assertThrows(IllegalStateException.class, () -> GambleRewards.chooseMissing(all, 0));
        assertEquals(4, GambleRewards.rollableStatCount());
        assertEquals(GambleStat.MAGIC_DAMAGE, GambleRewards.chooseStat(3));
        assertEquals(GambleStat.MAX_HEALTH, GambleRewards.chooseStat(4));
        assertFalse(java.util.stream.IntStream.range(0, 12)
                .mapToObj(GambleRewards::chooseStat)
                .anyMatch(stat -> stat == GambleStat.SPLASH_RADIUS));
    }

    @Test
    void lossInsuranceDescriptionStatesOnlyThePlayerFacingEffect() {
        assertEquals("도박 실패 시 능력치 감소량이 20% 줄어듭니다.",
                GambleAbility.LOSS_INSURANCE.description());
        assertFalse(GambleAbility.LOSS_INSURANCE.description().contains("홀수·짝수"));
        assertFalse(GambleAbility.LOSS_INSURANCE.description().contains("주사위 두 개"));
        assertFalse(GambleTowers.GAMBLER.description().stream().anyMatch(line -> line.contains("모든 도박")));
        assertFalse(GambleTowers.DICE_T3.description().stream().anyMatch(line -> line.contains("효과 배율")));
        assertTrue(GambleTowers.DICE_T2.description().stream().anyMatch(
                line -> line.contains("2배") && line.contains("약화 수치는 증가하지")));
        assertTrue(GambleTowers.DICE_T3.description().stream().anyMatch(
                line -> line.contains("3.5배") && line.contains("약화 수치는 증가하지")));
        assertEquals("초당 체력 회복 +2.5", new GambleSupportEffect(
                GambleSupportStat.REGENERATION, true, 2.5).displayLine());
        assertEquals("초당 체력 감소 -1", new GambleSupportEffect(
                GambleSupportStat.REGENERATION, false, 1.0).displayLine());

        GamblerTower gambler = new GamblerTower(GambleTowers.GAMBLER, OWNER, TeamId.RED, 1,
                new GridPosition(0, 64, 0), new GridPosition(0, 64, 0));
        GambleState state = GambleState.EMPTY.recordAbility(GambleAbility.LOSS_INSURANCE, 40, "insured");
        gambler.setData(GamblerTower.STATE, state);
        List<String> details = gambler.runtimeDetailLines();
        assertTrue(details.contains(GambleAbility.LOSS_INSURANCE.detailLine()));
        assertTrue(details.stream().anyMatch(line -> line.contains("누적 도박 점수: +40")));
    }

    @Test
    void catalogUsesFourStartersConfiguredUpgradeCostsAndCreativeClassification() {
        List<ProductionTowerCatalog.CatalogEntry> entries = ProductionTowerCatalog.all().stream()
                .filter(entry -> GambleTowers.isGambleTower(entry.type())).toList();
        assertEquals(10, entries.size());
        assertEquals(4, entries.stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count());
        assertTrue(entries.stream().filter(ProductionTowerCatalog.CatalogEntry::starter)
                .map(entry -> entry.type().id()).toList().containsAll(List.of(
                        GambleTowers.DICE_T1.id(), GambleTowers.GAMBLER.id(), GambleTowers.SPECTATOR_T1.id())));
        GambleSupportTower dice = assertInstanceOf(GambleSupportTower.class,
                ProductionTowerCatalog.find(GambleTowers.DICE_T1.id()).orElseThrow()
                        .create(OWNER, TeamId.RED, 1, new GridPosition(0, 64, 0)));
        GambleSupportTower spectator = assertInstanceOf(GambleSupportTower.class,
                ProductionTowerCatalog.find(GambleTowers.SPECTATOR_T1.id()).orElseThrow()
                        .create(OWNER, TeamId.RED, 1, new GridPosition(0, 64, 1)));
        assertEquals(0.0, dice.adjustAttackRange(dice.type().range()), EPSILON);
        assertEquals(0.0, spectator.adjustAttackRange(spectator.type().range()), EPSILON);
        assertEquals(3.5, dice.type().range(), EPSILON);
        GamblerTower gambler = assertInstanceOf(GamblerTower.class,
                ProductionTowerCatalog.find(GambleTowers.GAMBLER.id()).orElseThrow()
                        .create(OWNER, TeamId.RED, 1, new GridPosition(1, 64, 0)));
        assertFalse(gambler.type().description().stream().anyMatch(line -> line.contains("{")));
        TowerUpgradeOption twoDice = ProductionTowerCatalog.upgrade(
                GambleTowers.GAMBLER, GambleBet.TWO_DICE.upgradeId()).orElseThrow();
        assertTrue(gambler.upgradeTooltipLines(twoDice).stream().anyMatch(
                line -> line.contains("합이 10 이상") && line.contains("절반씩")));
        TowerUpgradeOption odd = ProductionTowerCatalog.upgrade(
                GambleTowers.GAMBLER, GambleBet.ODD.upgradeId()).orElseThrow();
        List<String> oddTooltip = gambler.upgradeTooltipLines(odd);
        assertTrue(oddTooltip.stream().anyMatch(line -> line.contains("체력 +350")
                && line.contains("공격력 +35") && line.contains("사거리 +3.5")));
        assertTrue(oddTooltip.stream().anyMatch(line -> line.contains("체력 -200")
                && line.contains("공격력 -20") && line.contains("사거리 -2")));
        assertTrue(oddTooltip.stream().anyMatch(line -> line.contains("체력 -160")
                && line.contains("공격력 -16") && line.contains("사거리 -1.6")));
        assertTrue(gambler.upgradeTooltipLines(twoDice).stream().anyMatch(line -> line.contains("합 7")
                && line.contains("체력 +200") && line.contains("공격력 +20")));
        assertTrue(gambler.type().maxHealth() >= IllagerTowers.T1_PILLAGER.maxHealth());
        assertTrue(gambler.type().range() >= IllagerTowers.T1_PILLAGER.range());
        assertTrue((gambler.type().damage() + gambler.magicAttackDamage(null)) * IllagerTowers.T1_PILLAGER.attackIntervalTicks()
                > IllagerTowers.T1_PILLAGER.damage() * gambler.type().attackIntervalTicks());
        for (var gamblerType : List.of(
                GambleTowers.GAMBLER, GambleTowers.KING, GambleTowers.DARK_KING)) {
            for (GambleBet bet : GambleBet.values()) {
                TowerUpgradeOption option = ProductionTowerCatalog.upgrade(gamblerType, bet.upgradeId())
                        .orElseThrow();
                assertEquals(switch (bet) {
                    case ODD, EVEN -> 85;
                    case TWO_DICE -> 170;
                    case SLOTS -> 260;
                }, option.mineralCost());
                assertFalse(gambler.upgradeCostAddsToSaleValue(option));
            }
        }
        assertEquals(100, ProductionTowerCatalog.upgrade(GambleTowers.DICE_T1, GambleTowers.DICE_T2.id())
                .orElseThrow().mineralCost());
        assertEquals(200, ProductionTowerCatalog.upgrade(GambleTowers.DICE_T2, GambleTowers.DICE_T3.id())
                .orElseThrow().mineralCost());
        assertEquals(100, ProductionTowerCatalog.upgrade(
                GambleTowers.SPECTATOR_T1, GambleTowers.SPECTATOR_T2.id()).orElseThrow().mineralCost());
        assertEquals(200, ProductionTowerCatalog.upgrade(
                GambleTowers.SPECTATOR_T2, GambleTowers.SPECTATOR_T3.id()).orElseThrow().mineralCost());
        assertEquals(List.of(3.5, 5.0, 6.5), List.of(
                GambleTowers.DICE_T1.range(), GambleTowers.DICE_T2.range(), GambleTowers.DICE_T3.range()));
        assertEquals(List.of(3.5, 5.0, 6.5), List.of(
                GambleTowers.SPECTATOR_T1.range(), GambleTowers.SPECTATOR_T2.range(),
                GambleTowers.SPECTATOR_T3.range()));
        assertTrue(GambleTowers.all().stream()
                .filter(type -> GambleTowers.isDice(type) || GambleTowers.isSpectator(type))
                .allMatch(type -> Math.abs(type.maxHealth()
                        - (type.id().endsWith("t3") ? 300 : type.id().endsWith("t2") ? 100 : 10)) < EPSILON));
        assertTrue(JobRegistry.creativeBuilders().stream().anyMatch(job -> job.id().equals(GambleTowerJob.ID)));
        assertFalse(JobRegistry.officialBuilders().stream().anyMatch(job -> job.id().equals(GambleTowerJob.ID)));
        assertEquals("semion-td:gamble_towers", new GambleTowerJob().id().toString());
    }

    @Test
    void promotionThresholdsCreateFinalFormsWithoutDiscardingGambleState() {
        assertEquals(GambleTowers.KING,
                GambleTowers.promotionTarget(GambleTowers.GAMBLER, 400.0));
        assertEquals(GambleTowers.DARK_KING,
                GambleTowers.promotionTarget(GambleTowers.GAMBLER, -200.0));
        assertEquals(null, GambleTowers.promotionTarget(GambleTowers.GAMBLER, 399.999));
        assertEquals(null, GambleTowers.promotionTarget(GambleTowers.GAMBLER, -199.999));
        assertEquals(null, GambleTowers.promotionTarget(GambleTowers.KING, -1_000.0));
        assertEquals(null, GambleTowers.promotionTarget(GambleTowers.DARK_KING, 1_000.0));

        assertEquals(400.0, GambleTowers.KING.maxHealth(), EPSILON);
        assertEquals(20.0, GambleTowers.KING.damage(), EPSILON);
        assertEquals(20.0, GambleBalance.baseMagicDamage(GambleTowers.KING), EPSILON);
        assertEquals(7.5, GambleTowers.KING.range(), EPSILON);
        assertEquals(8, GambleTowers.KING.attackIntervalTicks());
        assertEquals(440.0, GambleTowers.DARK_KING.maxHealth(), EPSILON);
        assertEquals(22.0, GambleTowers.DARK_KING.damage(), EPSILON);
        assertEquals(22.0, GambleBalance.baseMagicDamage(GambleTowers.DARK_KING), EPSILON);
        assertEquals(8.0, GambleTowers.DARK_KING.range(), EPSILON);
        assertEquals(8, GambleTowers.DARK_KING.attackIntervalTicks());
        assertEquals(3.0, GambleBalance.gamblerSplashRadius(GambleTowers.KING), EPSILON);
        assertEquals(3.25, GambleBalance.gamblerSplashRadius(GambleTowers.DARK_KING), EPSILON);

        GambleState state = GambleState.EMPTY
                .recordStat(GambleStat.MAX_HEALTH, 250.0, 110.0, 350.0, "health")
                .recordAbility(GambleAbility.LOSS_INSURANCE, 50.0, "insurance");
        GamblerTower original = new GamblerTower(GambleTowers.GAMBLER, OWNER, TeamId.RED, 1,
                new GridPosition(0, 64, 0), new GridPosition(0, 64, 0));
        original.setData(GamblerTower.STATE, state);
        GamblerTower promoted = new GamblerTower(GambleTowers.KING, OWNER, TeamId.RED, 1,
                original.originalPosition(), original.position());
        promoted.copyFrom(original, 0L);
        assertEquals(state, promoted.state());
        assertEquals(650.0, promoted.effectBaseMaxHealth(), EPSILON);
        assertTrue(promoted.state().has(GambleAbility.LOSS_INSURANCE));
    }

    @Test
    void immutableStateCopiesAcrossSelfUpgradeReplacement() {
        GamblerTower original = new GamblerTower(GambleTowers.GAMBLER, OWNER, TeamId.RED, 1,
                new GridPosition(0, 64, 0), new GridPosition(0, 64, 0));
        GambleState state = GambleState.EMPTY
                .recordStat(GambleStat.MAX_HEALTH, 40, 100, 8, "hp")
                .recordAbility(GambleAbility.LOSS_INSURANCE, 40, "insured");
        original.setData(GamblerTower.STATE, state);
        original.syncMaxHealth(state.resolvedValue(GambleStat.MAX_HEALTH, 100), false);
        original.syncHealth(70);
        GamblerTower replacement = new GamblerTower(GambleTowers.GAMBLER, OWNER, TeamId.RED, 1,
                original.originalPosition(), original.position());
        replacement.copyFrom(original, 0);
        assertEquals(state, replacement.state());
        assertEquals(0, replacement.paidMineralCost() - original.paidMineralCost());
    }

    @Test
    void supportFacesUseTheRequiredStatCountsSignsAndFaceStrengths() {
        List<GambleSupportStat> stats = List.of(
                GambleSupportStat.RANGE,
                GambleSupportStat.REGENERATION,
                GambleSupportStat.DAMAGE,
                GambleSupportStat.MAX_HEALTH
        );
        assertEffects(GambleSupportRolls.resolve(1, 1.0, stats), false, 0.50);
        assertEffects(GambleSupportRolls.resolve(2, 1.0, stats), false, 0.25);
        assertEffects(GambleSupportRolls.resolve(3, 1.0, stats), true, 0.25);
        assertEffects(GambleSupportRolls.resolve(4, 1.0, stats), true, 0.375);
        assertEffects(GambleSupportRolls.resolve(5, 1.0, stats), true, 0.4375, 4.375);
        assertEffects(GambleSupportRolls.resolve(6, 1.0, stats), true, 0.5625, 5.625);


        assertEffects(GambleSupportRolls.resolve(6, 3.5, stats), true, 1.96875, 19.6875);
        var iron = GambleSlots.Symbol.IRON;
        var nugget = GambleSlots.Symbol.IRON_NUGGET;
        assertEffects(GambleSlotSupportRolls.resolve(GambleTowers.SPECTATOR_T1, List.of(iron, iron, nugget)).effects(), true, 62.5);
        assertEffects(GambleSlotSupportRolls.resolve(GambleTowers.SPECTATOR_T1, List.of(iron, iron, iron)).effects(), true, 125);
        var blank = GambleSlotSupportRolls.resolve(GambleTowers.SPECTATOR_T1, List.of(nugget, nugget, nugget));
        assertTrue(blank.jackpot());
        assertTrue(blank.effects().isEmpty());
        double[] sums = new double[GambleSupportStat.values().length];
        int jackpots = 0;
        for (var first : GambleSlots.Symbol.values()) for (var second : GambleSlots.Symbol.values()) for (var third : GambleSlots.Symbol.values()) {
            var result = GambleSlotSupportRolls.resolve(GambleTowers.SPECTATOR_T1, List.of(first, second, third));
            if (result.jackpot()) jackpots++;
            result.effects().forEach(effect -> {
                assertTrue(effect.positive());
                sums[effect.stat().ordinal()] += effect.magnitude();
            });
        }
        assertEquals(6, jackpots);
        assertEquals(25 * 235.0 / 432, sums[GambleSupportStat.MAX_HEALTH.ordinal()] / 216, EPSILON);
        assertEquals(3.5 * 235.0 / 432, sums[GambleSupportStat.REGENERATION.ordinal()] / 216, EPSILON);
        assertEquals(3 * 235.0 / 432, sums[GambleSupportStat.DAMAGE.ordinal()] / 216, EPSILON);
        assertEquals(sums[GambleSupportStat.DAMAGE.ordinal()], sums[GambleSupportStat.MAGIC_DAMAGE.ordinal()], EPSILON);
        assertThrows(IllegalArgumentException.class,
                () -> GambleSupportRolls.resolve(0, 1.0, stats));
    }

    @Test
    void supportTierConfigurationMatchesRangePowerAndMinimumRollDesign() {
        assertEquals(1, GambleBalance.minimumRoll(GambleTowers.DICE_T3));
        assertEquals(1.0, GambleBalance.supportPowerMultiplier(GambleTowers.DICE_T1), EPSILON);
        assertEquals(2.0, GambleBalance.supportPowerMultiplier(GambleTowers.DICE_T2), EPSILON);
        assertEquals(3.5, GambleBalance.supportPowerMultiplier(GambleTowers.DICE_T3), EPSILON);
        assertEquals(30, GambleBalance.spectatorJackpotDiamondReward(GambleTowers.SPECTATOR_T1));
        assertEquals(90, GambleBalance.spectatorJackpotDiamondReward(GambleTowers.SPECTATOR_T2));
        assertEquals(210, GambleBalance.spectatorJackpotDiamondReward(GambleTowers.SPECTATOR_T3));
        assertEquals(0, GambleBalance.spectatorJackpotDiamondReward(GambleTowers.DICE_T3));
        assertEquals(3, GambleBalance.maxSpectatorsPerGambler());
        assertEquals(2.5, GambleBalance.baseSplashRadius(), EPSILON);
        assertEquals(0.60, GambleBalance.splashDamageRatio(), EPSILON);
        assertEquals(400.0, GambleBalance.kingPromotionScore(), EPSILON);
        assertEquals(-200.0, GambleBalance.darkKingPromotionScore(), EPSILON);
        assertEquals(500.0, GambleBalance.maxGambleScore(), EPSILON);
    }

    @Test
    void defaultsMergeMissingGambleValuesAndRejectInvalidOnes() throws Exception {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertInvalidAbility(defaults, GambleBalance.GLOBAL_ID, "slotTripleDiamond", 301);
        assertInvalidAbility(defaults, GambleTowers.POKER_TABLE.id(), "healthScoreDivisor", 0);
        var code = TowerBalanceConfig.codeDefaults();
        GambleTowers.all().forEach(type -> {
            assertEquals(code.towers().get(type.id()), defaults.towers().get(type.id()));
            assertEquals(code.abilities().get(type.id()), defaults.abilities().get(type.id()));
        });
        GambleTowers.all().forEach(type -> assertTrue(defaults.towers().containsKey(type.id())));
        assertEquals(85, defaults.upgradeCost(GambleTowers.GAMBLER.id(), GambleBet.ODD.upgradeId(), -1));
        assertEquals(170, defaults.upgradeCost(
                GambleTowers.GAMBLER.id(), GambleBet.TWO_DICE.upgradeId(), -1));
        TowerBalanceConfig partial = new TowerBalanceConfig(Map.of(), Map.of(), Map.of(
                GambleBalance.GLOBAL_ID, Map.of("damagePerScore", 0.2))).withMissingDefaults(defaults);
        assertEquals(defaults.ability(GambleBalance.GLOBAL_ID, "slotTripleDiamond", -1),
                partial.ability(GambleBalance.GLOBAL_ID, "slotTripleDiamond", -1));
        assertEquals(defaults.ability(GambleTowers.POKER_TABLE.id(), "healthScoreDivisor", -1),
                partial.ability(GambleTowers.POKER_TABLE.id(), "healthScoreDivisor", -1));
        assertEquals(0.2, partial.ability(GambleBalance.GLOBAL_ID, "damagePerScore", -1), EPSILON);
        assertEquals(5.0, partial.ability(GambleBalance.GLOBAL_ID, "maxHealthPerScore", -1), EPSILON);
        assertEquals(3.0, partial.ability(GambleBalance.GLOBAL_ID, "maxSpectatorsPerGambler", -1), EPSILON);
        assertEquals(10.0, partial.ability(GambleBalance.GLOBAL_ID, "twoDiceCompoundMinSum", -1), EPSILON);
        assertEquals(3.5, partial.ability(
                GambleTowers.DICE_T3.id(), "supportPowerMultiplier", -1), EPSILON);
        assertEquals(210.0, partial.ability(
                GambleTowers.SPECTATOR_T3.id(), "jackpotDiamondReward", -1), EPSILON);
        assertEquals(110, partial.towers().get(GambleTowers.GAMBLER.id()).maxHealth(), EPSILON);
        assertEquals(400, partial.towers().get(GambleTowers.KING.id()).maxHealth(), EPSILON);
        assertEquals(440, partial.towers().get(GambleTowers.DARK_KING.id()).maxHealth(), EPSILON);
        assertEquals(400.0, partial.ability(
                GambleBalance.GLOBAL_ID, "kingPromotionScore", -1), EPSILON);
        assertEquals(200.0, partial.ability(
                GambleBalance.GLOBAL_ID, "darkKingPromotionScoreMagnitude", -1), EPSILON);
        assertEquals(500.0, partial.ability(
                GambleBalance.GLOBAL_ID, "maxGambleScore", -1), EPSILON);
        assertEquals(0.5, partial.ability(
                GambleTowers.KING.id(), "splashRadiusBonus", -1), EPSILON);
        assertEquals(0.75, partial.ability(
                GambleTowers.DARK_KING.id(), "splashRadiusBonus", -1), EPSILON);

        assertInvalidAbility(defaults, GambleBalance.GLOBAL_ID, "abilityRewardChance", 1.1);
        assertInvalidAbility(defaults, GambleBalance.GLOBAL_ID, "splashDamageRatio", 1.1);
        assertInvalidAbility(defaults, GambleBalance.GLOBAL_ID, "maxSpectatorsPerGambler", 3.5);
        assertInvalidAbility(defaults, GambleBalance.GLOBAL_ID, "supportPositiveRangeUnit", -1.0);
        assertInvalidAbility(defaults, GambleBalance.GLOBAL_ID, "twoDiceCompoundMinSum", 9.5);
        assertInvalidAbility(defaults, GambleBalance.GLOBAL_ID, "twoDiceLoss2", 1_000.0);
        assertInvalidAbility(defaults, GambleBalance.GLOBAL_ID, "kingPromotionScore", 0.0);
        assertInvalidAbility(defaults, GambleBalance.GLOBAL_ID, "darkKingPromotionScoreMagnitude", 0.0);
        assertInvalidAbility(defaults, GambleBalance.GLOBAL_ID, "maxGambleScore", 0.0);
        assertInvalidAbility(defaults, GambleBalance.GLOBAL_ID, "maxGambleScore", 399.0);
        assertInvalidAbility(defaults, GambleTowers.KING.id(), "splashRadiusBonus", 0.0);
        assertInvalidAbility(defaults, GambleTowers.SPECTATOR_T3.id(), "slotBaseHealth", -1.0);
        assertInvalidAbility(defaults, GambleTowers.SPECTATOR_T3.id(), "slotBaseRegeneration", -1.0);
        assertInvalidAbility(defaults, GambleTowers.SPECTATOR_T3.id(), "jackpotDiamondReward", 3.5);

        try (var input = GambleTowerTest.class.getResourceAsStream(
                "/semiontd/balance-defaults/tower_balance.json")) {
            var root = JsonParser.parseReader(new InputStreamReader(
                    java.util.Objects.requireNonNull(input), StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(root.getAsJsonObject("towers").has(GambleTowers.GAMBLER.id()));
            assertTrue(root.getAsJsonObject("towers").has(GambleTowers.KING.id()));
            assertTrue(root.getAsJsonObject("towers").has(GambleTowers.DARK_KING.id()));
            assertTrue(root.getAsJsonObject("abilities").has(GambleBalance.GLOBAL_ID));
        }
    }

    @Test
    void spectatorJackpotPaysOnlyDuringTheOwnersOpenRound() {
        PlayerEconomy economy = new PlayerEconomy(EconomyConfig.defaultConfig());
        long startingDiamond = economy.diamond();
        try {
            GambleSpectatorRewards.openRound(OWNER, economy);
            assertEquals(0, GambleSpectatorRewards.awardJackpot(
                    OWNER, GambleTowers.SPECTATOR_T3, false));
            assertEquals(0, GambleSpectatorRewards.awardJackpot(
                    OWNER, GambleTowers.DICE_T3, true));
            assertEquals(30, GambleSpectatorRewards.awardJackpot(
                    OWNER, GambleTowers.SPECTATOR_T1, true));
            assertEquals(90, GambleSpectatorRewards.awardJackpot(
                    OWNER, GambleTowers.SPECTATOR_T2, true));
            assertEquals(210, GambleSpectatorRewards.awardJackpot(
                    OWNER, GambleTowers.SPECTATOR_T3, true));
            assertEquals(startingDiamond + 330, economy.diamond());
        } finally {
            GambleSpectatorRewards.closeRound(OWNER);
        }
        assertEquals(0, GambleSpectatorRewards.awardJackpot(
                OWNER, GambleTowers.SPECTATOR_T3, true));
        assertEquals(startingDiamond + 330, economy.diamond());
    }

    private static void assertEffects(
            List<GambleSupportEffect> effects, boolean positive, double... expectedMagnitudes
    ) {
        assertEquals(expectedMagnitudes.length, effects.size());
        for (int index = 0; index < expectedMagnitudes.length; index++) {
            GambleSupportEffect effect = effects.get(index);
            assertEquals(positive, effect.positive());
            assertEquals(expectedMagnitudes[index], effect.magnitude(), EPSILON);
        }
    }

    private static void assertInvalidAbility(
            TowerBalanceConfig defaults, String configId, String key, double value
    ) {
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> invalid = new LinkedHashMap<>(abilities.get(configId));
        invalid.put(key, value);
        abilities.put(configId, invalid);
        TowerBalanceConfig broken = new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities,
                defaults.illusionCloneQueue(), defaults.villagerAdv(), defaults.schemaVersion());
        assertThrows(IllegalArgumentException.class, broken::validateForRuntime);
    }

    private static TowerBalanceConfig withGambleScores(
            TowerBalanceConfig defaults, double kingPromotionScore, double maxGambleScore
    ) {
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> gamble = new LinkedHashMap<>(abilities.get(GambleBalance.GLOBAL_ID));
        gamble.put("kingPromotionScore", kingPromotionScore);
        gamble.put("maxGambleScore", maxGambleScore);
        abilities.put(GambleBalance.GLOBAL_ID, gamble);
        return new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities,
                defaults.illusionCloneQueue(), defaults.villagerAdv(), defaults.schemaVersion());
    }
}
