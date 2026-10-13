package kim.biryeong.semiontd.tower.end;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.PlayerLane;
import org.junit.jupiter.api.Test;

class EndCombatProgressionTest extends EndTestFixture {
    @Test
    void everyShulkerOrEndCrystalTransferReducesAttackIntervalForTheCurrentRoundOnly() {
        applyEndAbilities(Map.of(
                "transferTicks", 1.0,
                "roundHealthRatio", 0.0,
                "permanentHealthRatio", 0.0
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        dragon.syncHealth(10.0);
        lane.addTower(tower(EndTowers.T1_SHULKER_TOWER, 1));
        dragon.tick(lane);
        assertEquals(1, dragon.transferStats().roundCompletedCount());
        assertEquals(40.0, dragon.health(), 0.0001);
        assertEquals(19, dragon.adjustAttackInterval(20));
        lane.addTower(tower(EndTowers.T1_SHULKER_TOWER, 2));
        dragon.tick(lane);
        assertEquals(2, dragon.transferStats().roundCompletedCount());
        assertEquals(70.0, dragon.health(), 0.0001);
        assertEquals(18, dragon.adjustAttackInterval(20));
        lane.addTower(tower(EndTowers.T1_ENDERMITE_TOWER, 3));
        dragon.tick(lane);
        assertEquals(3, dragon.transferStats().roundCompletedCount());
        assertEquals(100.0, dragon.health(), 0.0001);
        assertEquals(17, dragon.adjustAttackInterval(20));
        dragon.resetRoundTransferBonuses(null);
        assertEquals(0, dragon.transferStats().roundCompletedCount());
        assertEquals(20, dragon.adjustAttackInterval(20));
    }

    @Test
    void shulkerTransfersFiftyPercentOfItsHealthForTheCurrentRound() {
        applyEndAbilities(Map.of(
                "transferTicks", 1.0,
                "attackSpeedStacks", 1.0
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        EndTower shulker = tower(EndTowers.T1_SHULKER_TOWER, 1);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        dragon.syncHealth(10.0);
        lane.addTower(shulker);
        dragon.tick(lane);
        double rawPermanentHealth = 6.0;
        double rawRoundHealth = 50.0;
        double expectedPermanentHealth = expectedHealthBonus(rawPermanentHealth);
        double expectedTotalHealth = expectedHealthBonus(rawPermanentHealth + rawRoundHealth);
        double expectedRoundHealth = expectedTotalHealth - expectedPermanentHealth;
        assertEquals(expectedRoundHealth, dragon.transferStats().roundHealthBonus(), 0.0001);
        assertEquals(expectedPermanentHealth, dragon.transferStats().permanentHealthBonus(), 0.0001);
        assertEquals(200.0 + expectedTotalHealth, dragon.currentMaxHealth(), 0.0001);
        assertEquals(40.0, dragon.health(), 0.0001);
        assertEquals(0.0, shulker.health(), 0.0001);
        assertEquals(1, dragon.transferStats().roundCompletedCount());
        assertEquals(19, dragon.adjustAttackInterval(20));
    }

    @Test
    void completedTransfersAccumulateConfiguredFamilyStatsForTheRound() {
        applyTransferDuration(1);
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        for (int index = 0; index < 20; index++) {
            lane.addTower(tower(EndTowers.T1_ENDERMITE_TOWER, index + 1));
            lane.addTower(tower(EndTowers.T1_SHULKER_TOWER, index + 21));
        }
        tick(dragon, lane, 1);
        double rawPermanentHealth = 120.0;
        double rawRoundHealth = 1000.0;
        double rawTotalHealth = rawPermanentHealth + rawRoundHealth;
        double expectedPermanentHealth = expectedHealthBonus(rawPermanentHealth);
        double expectedTotalHealth = expectedHealthBonus(rawTotalHealth);
        double expectedRoundHealth = expectedTotalHealth - expectedPermanentHealth;
        double rawPermanentDamage = 12.0;
        double rawRoundDamage = 132.0;
        double rawTotalDamage = rawPermanentDamage + rawRoundDamage;
        double expectedPermanentDamage = expectedDamageBonus(rawPermanentDamage);
        double expectedTotalDamage = expectedDamageBonus(rawTotalDamage);
        double expectedRoundDamage = expectedTotalDamage - expectedPermanentDamage;
        assertEquals(20, dragon.transferStats().endCrystalCount());
        assertEquals(20, dragon.transferStats().shulkerCount());
        assertEquals(40, dragon.transferStats().roundCompletedCount());
        assertEquals(41, lane.towers().size());
        assertEquals(40, lane.towers().stream().filter(tower -> tower != dragon && tower.health() <= 0.0).count());
        assertEquals(expectedRoundHealth, dragon.transferStats().roundHealthBonus(), 0.0001);
        assertEquals(expectedRoundDamage, dragon.transferStats().roundDamageBonus(), 0.0001);
        assertEquals(expectedPermanentHealth, dragon.transferStats().permanentHealthBonus(), 0.0001);
        assertEquals(expectedPermanentDamage, dragon.transferStats().permanentDamageBonus(), 0.0001);
        assertEquals(expectedTotalHealth, dragon.transferStats().totalHealthBonus(), 0.0001);
        assertEquals(expectedTotalDamage, dragon.transferStats().totalDamageBonus(), 0.0001);
        assertEquals(200.0 + expectedTotalHealth, dragon.effectBaseMaxHealth(), 0.0001);
        assertEquals(5.0 * (1.0 + expectedTotalDamage / dragon.type().damage()), dragon.modifyAttackDamage(null, null, 5.0), 0.0001);
        assertEquals(5.0, dragon.adjustAttackRange(5.0), 0.0001);
        assertEquals(1.0, dragon.splashRadius(), 0.0001);
        assertEquals(5, dragon.adjustAttackInterval(20));
        EndConfig.StackRule damageReduction = EndConfig.RUNTIME.damageReduction();
        int damageReductionStacks = damageReduction.stacksPerStep();
        double damageReductionStep = damageReduction.bonusPerStep();
        double damageReductionCap = damageReduction.maximum();
        double expectedDamageReduction = Math.min(damageReductionCap, (dragon.transferStats().shulkerCount() / damageReductionStacks) * damageReductionStep);
        assertEquals(100.0 * (1.0 - expectedDamageReduction), dragon.modifyIncomingDamage(null, null, 100.0), 0.0001);
        dragon.resetRoundTransferBonuses(null);
        assertEquals(0.0, dragon.transferStats().roundHealthBonus(), 0.0001);
        assertEquals(0.0, dragon.transferStats().roundDamageBonus(), 0.0001);
        assertEquals(expectedPermanentHealth, dragon.transferStats().permanentHealthBonus(), 0.0001);
        assertEquals(expectedPermanentDamage, dragon.transferStats().permanentDamageBonus(), 0.0001);
        assertEquals(200.0 + expectedPermanentHealth, dragon.effectBaseMaxHealth(), 0.0001);
        assertEquals(5.0 * (1.0 + expectedPermanentDamage / dragon.type().damage()), dragon.modifyAttackDamage(null, null, 5.0), 0.0001);
        assertEquals(200.0 + expectedPermanentHealth, dragon.previewHatchedMaxHealth(), 0.0001);
        assertEquals(dragon.type().damage() + expectedPermanentDamage, dragon.previewHatchedAttackDamage(), 0.0001);
        assertEquals(15, dragon.previewHatchedAttackIntervalTicks());
        assertEquals(0, dragon.transferStats().roundCompletedCount());
        assertEquals(15, dragon.adjustAttackInterval(15));
    }

    @Test
    void cumulativeLineBonusesUseTheirRequestedFamiliesAndRespectEveryCap() {
        applyEndAbilities(Map.ofEntries(
                Map.entry("transferTicks", 1.0),
                Map.entry("splash1", 1.0),
                Map.entry("splash2", 2.0),
                Map.entry("splashStep", 0.25),
                Map.entry("splashCap", 0.5),
                Map.entry("attackSpeedStacks", 1.0),
                Map.entry("attackSpeedCap", 2.0),
                Map.entry("attackRangeStacks", 1.0),
                Map.entry("attackRangeStep", 2.0),
                Map.entry("attackRangeCap", 5.0),
                Map.entry("lifeStealStacks", 1.0),
                Map.entry("lifeStealCap", 0.02),
                Map.entry("damageReductionStacks", 1.0),
                Map.entry("damageReductionStep", 0.02),
                Map.entry("damageReductionCap", 0.05)
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        for (int index = 0; index < 3; index++) {
            lane.addTower(tower(EndTowers.T1_ENDERMITE_TOWER, index + 1));
            lane.addTower(tower(EndTowers.T1_SHULKER_TOWER, index + 4));
        }
        dragon.tick(lane);
        assertEquals(3, dragon.transferStats().endCrystalCount());
        assertEquals(3, dragon.transferStats().shulkerCount());
        assertEquals(0.5, dragon.splashRadius(), 0.0001);
        assertEquals(5.0, attackRangeBonus(dragon), 0.0001);
        assertEquals(10.0, dragon.adjustAttackRange(5.0), 0.0001);
        assertEquals(12, dragon.adjustAttackInterval(20));
        assertEquals(95.0, dragon.modifyIncomingDamage(null, null, 100.0), 0.0001);
    }

    @Test
    void configuredDamageThresholdAndScaleApplyToTransferredEndTowerDamage() {
        applyEndAbilities(Map.ofEntries(
                Map.entry("transferTicks", 1.0),
                Map.entry("roundDamageRatio", 1.0),
                Map.entry("permanentDamageRatio", 0.0),
                Map.entry("damageThreshold", 10.0),
                Map.entry("damageScale", 10.0)
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        for (int index = 0; index < 3; index++) {
            lane.addTower(tower(EndTowers.T3_END_CRYSTAL_TOWER, index + 1));
        }

        dragon.tick(lane);

        double expectedDamageBonus = 10.0 + 10.0 * Math.log1p(5.0);
        assertEquals(27.9176, expectedDamageBonus, 0.0001);
        assertEquals(expectedDamageBonus, dragon.transferStats().roundDamageBonus(), 0.0001);
        assertEquals(expectedDamageBonus, dragon.transferStats().totalDamageBonus(), 0.0001);
        assertEquals(10.0 + expectedDamageBonus, dragon.previewHatchedAttackDamage(), 0.0001);
        assertEquals(10.0 + expectedDamageBonus, dragon.modifyAttackDamage(null, null, 10.0), 0.0001);
    }

    @Test
    void splashRatioUsesResolvedPrimaryDamage() {
        applyEndAbilities(Map.of(
                "splashDamageRatio", 0.66
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        double resolvedPrimaryDamage = 1_000.0;
        EndCombat combat = new EndCombat(EndConfig.RUNTIME);
        assertEquals(660.0, combat.resolvedSplashDamage(resolvedPrimaryDamage), 0.0001);
        assertEquals(0.0, combat.resolvedSplashDamage(Double.NaN), 0.0001);
    }

    @Test
    void combatCalculationsUseTheProvidedTransferStackSnapshot() {
        applyEndAbilities(Map.ofEntries(
                Map.entry("lifeStealStacks", 2.0),
                Map.entry("lifeStealStep", 0.01),
                Map.entry("attackRangeStacks", 4.0),
                Map.entry("attackRangeStep", 0.5),
                Map.entry("attackSpeedStacks", 5.0),
                Map.entry("attackSpeedStep", 1.0)
        ));
        EndCombat combat = new EndCombat(EndConfig.RUNTIME);
        EndTransferStacks first = new EndTransferStacks(4, 5, 0);
        EndTransferStacks second = new EndTransferStacks(8, 10, 0);

        assertEquals(0.02, combat.lifeStealRatio(first), 0.0001);
        assertEquals(0.04, combat.lifeStealRatio(second), 0.0001);
        assertEquals(0.5, combat.attackRangeBonus(first), 0.0001);
        assertEquals(1.0, combat.attackRangeBonus(second), 0.0001);
        assertEquals(19, combat.adjustAttackInterval(20, first));
        assertEquals(18, combat.adjustAttackInterval(20, second));
    }

    @Test
    void extremeAttackIntervalConfigurationCannotOverflow() {
        applyEndAbilities(Map.ofEntries(
                Map.entry("transferTicks", 1.0),
                Map.entry("attackSpeedStacks", 1.0),
                Map.entry("attackSpeedStep", (double) Integer.MAX_VALUE),
                Map.entry("attackSpeedCap", (double) Integer.MAX_VALUE),
                Map.entry("transferAttackSpeedStacks", 1.0),
                Map.entry("transferAttackSpeedStep", (double) Integer.MAX_VALUE),
                Map.entry("attackSpeedMinimumTicks", 5.0)
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        lane.addTower(tower(EndTowers.T3_END_CRYSTAL_TOWER, 1));
        dragon.tick(lane);
        assertEquals(3, dragon.transferStats().endCrystalCount());
        assertEquals(1, dragon.transferStats().roundCompletedCount());
        assertEquals(5, dragon.adjustAttackInterval(20));
        assertEquals(5, dragon.previewHatchedAttackIntervalTicks());
    }

    @Test
    void everyStackBasedStatReachesItsCapAtThreeHundredStacks() {
        applyEndAbilities(Map.ofEntries(
                Map.entry("transferTicks", 1.0),
                Map.entry("transferHeal", 0.0),
                Map.entry("roundHealthRatio", 0.0),
                Map.entry("roundDamageRatio", 0.0),
                Map.entry("permanentHealthRatio", 0.0),
                Map.entry("permanentDamageRatio", 0.0)
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        for (int index = 0; index < 99; index++) {
            lane.addTower(tower(EndTowers.T3_END_CRYSTAL_TOWER, index + 1));
            lane.addTower(tower(EndTowers.T3_SHULKER_TOWER, index + 101));
        }
        lane.addTower(tower(EndTowers.T2_ENDERMAN_TOWER, 201));
        lane.addTower(tower(EndTowers.T2_SHULKER_TOWER, 202));
        dragon.tick(lane);
        dragon.resetRoundTransferBonuses(null);
        assertEquals(299, dragon.transferStats().endCrystalCount());
        assertEquals(299, dragon.transferStats().shulkerCount());
        assertEquals(6, dragon.adjustAttackInterval(15));
        assertEquals(4.0, dragon.splashRadius(), 0.0001);
        assertEquals(7.5, dragon.adjustAttackRange(5.0), 0.0001);
        assertEquals(81.0, dragon.modifyIncomingDamage(null, null, 100.0), 0.0001);
        assertEquals(29.0, regenerationPerSecond(dragon), 0.0001);
        lane.addTower(tower(EndTowers.T1_ENDERMITE_TOWER, 203));
        lane.addTower(tower(EndTowers.T1_SHULKER_TOWER, 204));
        dragon.tick(lane);
        dragon.resetRoundTransferBonuses(null);
        assertEquals(300, dragon.transferStats().endCrystalCount());
        assertEquals(300, dragon.transferStats().shulkerCount());
        assertEquals(5, dragon.adjustAttackInterval(15));
        assertEquals(5.0, dragon.splashRadius(), 0.0001);
        assertEquals(8.0, dragon.adjustAttackRange(5.0), 0.0001);
        assertEquals(80.0, dragon.modifyIncomingDamage(null, null, 100.0), 0.0001);
        assertEquals(30.0, regenerationPerSecond(dragon), 0.0001);
    }

    @Test
    void hatchedCoreStartsWithNoSplashAndGainsFirstBlockAtTenStacks() {
        applyEndAbilities(Map.of(
                "transferTicks", 1.0,
                "transferHeal", 0.0,
                "roundDamageRatio", 0.0,
                "permanentDamageRatio", 0.0
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        for (int index = 0; index < 9; index++) {
            lane.addTower(tower(EndTowers.T1_ENDERMITE_TOWER, index + 1));
        }
        dragon.tick(lane);
        assertEquals(9, dragon.transferStats().endCrystalCount());
        assertEquals(0.0, dragon.splashRadius(), 0.0001);
        lane.addTower(tower(EndTowers.T1_ENDERMITE_TOWER, 10));
        dragon.tick(lane);
        assertEquals(10, dragon.transferStats().endCrystalCount());
        assertEquals(1.0, dragon.splashRadius(), 0.0001);
    }

    @Test
    void completedTransfersUseRegisteredTowerTiersAsStackWeight() {
        applyTransferDuration(1);
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        lane.addTower(tower(EndTowers.T2_ENDERMAN_TOWER, 1));
        lane.addTower(tower(EndTowers.T3_END_CRYSTAL_TOWER, 2));
        lane.addTower(tower(EndTowers.T2_SHULKER_TOWER, 3));
        lane.addTower(tower(EndTowers.T3_SHULKER_TOWER, 4));
        dragon.tick(lane);
        assertEquals(5, dragon.transferStats().endCrystalCount());
        assertEquals(5, dragon.transferStats().shulkerCount());
        assertEquals(4, dragon.transferStats().roundCompletedCount());
    }

    @Test
    void shulkerTiersReduceIncomingDamageByConfiguredAmount() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        assertEquals(90.0, tower(EndTowers.T1_SHULKER_TOWER, 0).modifyIncomingDamage(null, null, 100.0), 0.0001);
        assertEquals(70.0, tower(EndTowers.T2_SHULKER_TOWER, 0).modifyIncomingDamage(null, null, 100.0), 0.0001);
        assertEquals(50.0, tower(EndTowers.T3_SHULKER_TOWER, 0).modifyIncomingDamage(null, null, 100.0), 0.0001);
    }


    @Test
    void lifeStealKeepsAllTenStackStepsIndependentOfDamageEfficiency() {
        EndCombat combat = new EndCombat(EndConfig.RUNTIME);
        for (int step = 0; step <= 10; step++) {
            double expected = step * .01;
            var stacks = new EndTransferStacks(step * 30, 0, 0);
            assertEquals(expected, combat.lifeStealRatio(stacks), .000001);
            assertEquals(expected, combat.lifeStealRatio(new EndTransferStacks(step * 30 + 29, 0, 0)), .000001);
            assertEquals(3 * step, combat.lifeStealHealing(300, stacks), .000001);
            assertEquals(expected, combat.lifeStealRatio(stacks), .000001);
        }
        assertEquals(.1, combat.lifeStealRatio(new EndTransferStacks(1000, 0, 0)), .000001);
    }


    @Test
    void normalizedLifeStealMatchesEveryRequestedStageAndDamageWithoutDisplayRounding() {
        EndCombat combat = new EndCombat(EndConfig.RUNTIME);
        for (int step : new int[] {0, 1, 6, 10}) {
            EndTransferStacks stacks = new EndTransferStacks(step * 30, 0, 0);
            for (double damage : new double[] {1, 30, 250, 251, 300, 600, 3000, 6000}) {
                double expected = step / 10.0 * (damage < 30 ? damage : damage <= 3000 ? 30 : damage * .01);
                assertEquals(expected, combat.lifeStealHealing(damage, stacks), .000001,
                        "stage=" + step + ", damage=" + damage);
            }
            for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                assertEquals(0, combat.lifeStealHealing(invalid, stacks));
            }
        }
        assertEquals(30, combat.lifeStealHealing(30, new EndTransferStacks(1000, 0, 0)), .000001);
        assertEquals(0, EndCombat.lifeStealProgress(.06, 0));
    }


    @Test
    void splashUsesTheOutgoingAttackSnapshotBeforeItsOwnDamageReduction() {
        EndCombat combat = new EndCombat(EndConfig.RUNTIME);
        EndTransferStacks stacks = new EndTransferStacks(180, 0, 0);
        assertEquals(18, combat.lifeStealHealing(250, stacks), .000001);
        assertEquals(11.88, combat.lifeStealHealing(165, 250, stacks), .000001);
        assertEquals(5.94, combat.lifeStealHealing(82.5, 250, stacks), .000001);
        assertEquals(.72, combat.lifeStealHealing(10, 250, stacks), .000001);
        assertEquals(0, combat.lifeStealHealing(0, 250, stacks));
        for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertEquals(0, combat.lifeStealHealing(165, invalid, stacks));
            assertEquals(0, combat.lifeStealHealing(invalid, 250, stacks));
        }
    }

}
