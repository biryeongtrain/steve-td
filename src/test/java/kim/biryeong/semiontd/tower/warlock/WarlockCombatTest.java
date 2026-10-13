package kim.biryeong.semiontd.tower.warlock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WarlockCombatTest {
    @AfterEach
    void resetBalance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void capsDamageAndSplashRadius() {
        WarlockCombat combat = new WarlockCombat(WarlockConfig.RUNTIME);
        WarlockRules.SplashRule rangedSplash = WarlockConfig.RUNTIME.path(WarlockPath.RANGED).splash();
        WarlockRules.SplashRule meleeSplash = WarlockConfig.RUNTIME.path(WarlockPath.MELEE).splash();

        assertEquals(0.0, rangedSplash.radius(1), 0.0001);
        assertEquals(0.1, rangedSplash.radius(2), 0.0001);
        assertEquals(0.3, rangedSplash.radius(7), 0.0001);
        assertEquals(0.4, rangedSplash.radius(8), 0.0001);
        assertEquals(3.2, rangedSplash.radius(64), 0.0001);
        assertEquals(8.0, rangedSplash.radius(160), 0.0001);
        assertEquals(8.0, rangedSplash.radius(200), 0.0001);
        assertEquals(0.25, meleeSplash.radius(1), 0.0001);
        assertEquals(1.0, meleeSplash.radius(4), 0.0001);
        assertEquals(1.5, meleeSplash.radius(6), 0.0001);
        assertEquals(2.0, meleeSplash.radius(8), 0.0001);
        assertEquals(2.0, meleeSplash.radius(100), 0.0001);
        assertEquals(175.0, combat.resolvedSplashDamage(WarlockPath.RANGED, 350.0), 0.0001);
        assertEquals(262.5, combat.resolvedSplashDamage(WarlockPath.MELEE, 350.0), 0.0001);
    }

    @Test
    void liveDamageCurvePreservesNormalAbsorptionsAndLimitsExtremeGrowth() {
        WarlockRules.ScalingRule base = WarlockConfig.RUNTIME.path(WarlockPath.BASE).damageScaling();
        WarlockRules.ScalingRule ranged = WarlockConfig.RUNTIME.path(WarlockPath.RANGED).damageScaling();
        WarlockRules.ScalingRule melee = WarlockConfig.RUNTIME.path(WarlockPath.MELEE).damageScaling();

        assertEquals(600.0, base.value(600.0), 0.0001);
        assertEquals(108.0, ranged.value(108.0), 0.0001);
        assertEquals(160.0, ranged.value(160.0), 0.0001);
        assertEquals(220.1631, ranged.value(300.0), 0.0001);
        assertEquals(259.3963, ranged.value(600.0), 0.0001);
        assertEquals(200.0, melee.value(200.0), 0.0001);
        assertEquals(235.8352, melee.value(300.0), 0.0001);
        assertEquals(260.8904, melee.value(600.0), 0.0001);
    }

    @Test
    void liveHealthCurvePreservesNormalAbsorptionsAndLimitsExtremeGrowth() {
        WarlockRules.ScalingRule base = WarlockConfig.RUNTIME.path(WarlockPath.BASE).healthScaling();
        WarlockRules.ScalingRule ranged = WarlockConfig.RUNTIME.path(WarlockPath.RANGED).healthScaling();
        WarlockRules.ScalingRule melee = WarlockConfig.RUNTIME.path(WarlockPath.MELEE).healthScaling();

        assertEquals(6000.0, base.value(6000.0), 0.0001);
        assertEquals(2000.0, ranged.value(2000.0), 0.0001);
        assertEquals(3098.6123, ranged.value(6000.0), 0.0001);
        assertEquals(3000.0, melee.value(3000.0), 0.0001);
        assertEquals(3500.0, melee.value(3500.0), 0.0001);
        assertEquals(4395.8797, melee.value(6000.0), 0.0001);
    }

    @Test
    void rangedLifeStealGrowsEveryTenAbsorptionsAndCapsAtPercent() {
        WarlockRules.StackRule lifeSteal = WarlockConfig.RUNTIME.path(WarlockPath.RANGED).lifeSteal();

        assertEquals(0.0, lifeSteal.value(9), 0.0001);
        assertEquals(0.005, lifeSteal.value(10), 0.0001);
        assertEquals(0.005, lifeSteal.value(19), 0.0001);
        assertEquals(0.01, lifeSteal.value(20), 0.0001);
        assertEquals(0.065, lifeSteal.value(130), 0.0001);
        assertEquals(0.07, lifeSteal.value(140), 0.0001);
        assertEquals(0.07, lifeSteal.value(149), 0.0001);
        assertEquals(0.07, lifeSteal.value(200), 0.0001);
    }

    @Test
    void meleeLifeStealUsesCurrentRoundAbsorptionsAndCapsAtPercent() {
        WarlockCombat combat = new WarlockCombat(WarlockConfig.RUNTIME);
        WarlockRules.StackRule meleeLifeSteal = WarlockConfig.RUNTIME.path(WarlockPath.MELEE).lifeSteal();
        WarlockRules.StackRule rangedLifeSteal = WarlockConfig.RUNTIME.path(WarlockPath.RANGED).lifeSteal();

        assertEquals(0.0, meleeLifeSteal.value(0), 0.0001);
        assertEquals(0.03, meleeLifeSteal.value(3), 0.0001);
        assertEquals(0.12, meleeLifeSteal.value(12), 0.0001);
        assertEquals(0.12, meleeLifeSteal.value(13), 0.0001);
        assertEquals(0.12, meleeLifeSteal.value(25), 0.0001);
        assertEquals(0.03, combat.lifeStealRatioForCount(WarlockPath.MELEE, 3), 0.0001);
        assertEquals(0.0, combat.lifeStealRatioForCount(WarlockPath.MELEE, 0), 0.0001);
        assertEquals(0.03, combat.lifeStealRatioForCount(WarlockPath.MELEE, 3), 0.0001);
        assertEquals(0.12, combat.lifeStealRatioForCount(WarlockPath.MELEE, 20), 0.0001);
        assertEquals(0.005, rangedLifeSteal.value(10), 0.0001);
        assertEquals(0.005, combat.lifeStealRatioForCount(WarlockPath.RANGED, 10), 0.0001);
    }

    @Test
    void everyExistingRangedAndMeleeStackStepSurvivesEfficiencyScaling() {
        WarlockCombat combat = new WarlockCombat(WarlockConfig.RUNTIME);
        for (int count = 0; count <= 200; count++) {
            double ranged = Math.min(.07, (count / 10) * .005);
            double melee = Math.min(.12, count * .01);
            assertEquals(ranged, combat.lifeStealRatioForCount(WarlockPath.RANGED, count), .000001);
            assertEquals(melee, combat.lifeStealRatioForCount(WarlockPath.MELEE, count), .000001);
            assertEquals(400 * ranged, WarlockCombat.lifeStealHealing(600, 600, ranged), .000001);
            assertEquals(400 * melee, WarlockCombat.lifeStealHealing(600, 600, melee), .000001);
        }
    }


    @Test
    void healingUsesFortyDamageEfficiencyAndTenfoldStackRates() {
        WarlockCombat combat = new WarlockCombat(WarlockConfig.RUNTIME);
        for (WarlockPath path : new WarlockPath[] {WarlockPath.RANGED, WarlockPath.MELEE}) {
            for (int count = 0; count <= 150; count++) {
                double ratio = path == WarlockPath.RANGED ? Math.min(.07, count / 10 * .005) : Math.min(.12, count * .01);
                assertEquals(ratio, combat.lifeStealRatioForCount(path, count), 1e-12);
                for (double damage : new double[] {1, 39, 40, 41, 400, 4000, 8000}) {
                    double expected = damage <= 40 ? damage * ratio * 10 : damage <= 4000 ? 400 * ratio : damage * ratio * .1;
                    assertEquals(expected, WarlockCombat.lifeStealHealing(damage, damage, ratio), 1e-10);
                }
            }
        }
        assertEquals(28, WarlockCombat.lifeStealHealing(400, 400, .07), 1e-12);
        assertEquals(14, WarlockCombat.lifeStealHealing(200, 400, .07), 1e-12);
        assertEquals(48, WarlockCombat.lifeStealHealing(400, 400, .12), 1e-12);
        assertEquals(36, WarlockCombat.lifeStealHealing(300, 400, .12), 1e-12);
        assertEquals(.7, WarlockCombat.lifeStealHealing(10, 400, .07), 1e-12);
        assertEquals(1.2, WarlockCombat.lifeStealHealing(10, 400, .12), 1e-12);
    }

    @Test
    void invalidLifeStealInputsCannotHeal() {
        for (double invalid : new double[] {0, -1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertEquals(0, WarlockCombat.lifeStealHealing(invalid, 400, .07));
            assertEquals(0, WarlockCombat.lifeStealHealing(200, invalid, .07));
            assertEquals(0, WarlockCombat.lifeStealHealing(200, 400, invalid));
        }
        assertEquals(0, WarlockCombat.lifeStealHealing(Double.MAX_VALUE, 40, Double.MAX_VALUE));
    }
}
