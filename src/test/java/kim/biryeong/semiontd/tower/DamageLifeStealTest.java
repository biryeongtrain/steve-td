package kim.biryeong.semiontd.tower;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DamageLifeStealTest {
    @Test
    void inverseDamagePreservesTheRequestedEfficiency() {
        for (double threshold : new double[] {30, 40, 80}) {
            assertEquals(1, DamageLifeSteal.efficiency(1, threshold));
            assertEquals(1, DamageLifeSteal.efficiency(threshold, threshold));
            assertEquals(.1, DamageLifeSteal.efficiency(threshold * 10, threshold), 1e-12);
            assertEquals(.05, DamageLifeSteal.efficiency(threshold * 20, threshold), 1e-12);
            assertEquals(.01, DamageLifeSteal.efficiency(threshold * 100, threshold));
            assertEquals(.01, DamageLifeSteal.efficiency(Double.MAX_VALUE, threshold));
        }
    }

    @Test
    void efficiencyIsContinuousMonotoneAndBounded() {
        for (double threshold : new double[] {30, 40}) {
            double previous = 1;
            for (double damage = 0; damage <= 8000; damage += .25) {
                double efficiency = DamageLifeSteal.efficiency(damage, threshold);
                assertTrue(efficiency >= .01 && efficiency <= 1);
                assertTrue(efficiency <= previous + 1e-12);
                previous = efficiency;
            }
            for (double boundary : new double[] {threshold, threshold * 10, threshold * 100}) {
                assertEquals(DamageLifeSteal.efficiency(boundary, threshold),
                        DamageLifeSteal.efficiency(Math.nextDown(boundary), threshold), 1e-12);
                assertEquals(DamageLifeSteal.efficiency(boundary, threshold),
                        DamageLifeSteal.efficiency(Math.nextUp(boundary), threshold), 1e-12);
            }
        }
    }

    @Test
    void efficiencyMultipliesExistingLifeStealWithoutGrantingIt() {
        for (double threshold : new double[] {30, 40}) {
            for (double rate : new double[] {0, .005, .1, .6, .7, 1, 1.2}) {
                for (double damage : new double[] {1, 30, 40, 250, 400, 3000, 4000, 8000}) {
                    double expectedRate = rate * Math.clamp(threshold / damage, .01, 1);
                    assertEquals(expectedRate, DamageLifeSteal.rate(damage, rate, threshold), 1e-12);
                    assertEquals(damage * expectedRate, DamageLifeSteal.healing(damage, damage, rate, threshold), 1e-12);
                }
            }
        }
    }

    @Test
    void invalidDamageAndRatiosCannotHealOrProduceNonFiniteHealth() {
        for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertEquals(0, DamageLifeSteal.healing(invalid, 400, .7, 40));
            assertEquals(0, DamageLifeSteal.healing(200, invalid, .7, 40));
            assertEquals(0, DamageLifeSteal.healing(200, 400, invalid, 40));
            assertEquals(0, DamageLifeSteal.healing(200, 400, .7, invalid));
        }
        assertEquals(0, DamageLifeSteal.healing(Double.MAX_VALUE, 40, Double.MAX_VALUE, 40));
    }

    @Test
    void sharedHealingPreservesEndAndBothWarlockPathsWithSeparateSplashReferences() {
        assertEquals(18, DamageLifeSteal.healing(250, 250, .6, 30), 1e-12);
        assertEquals(11.88, DamageLifeSteal.healing(165, 250, .6, 30), 1e-12);
        assertEquals(.072, DamageLifeSteal.rate(250, .6, 30), 1e-12);
        assertEquals(28, DamageLifeSteal.healing(400, 400, .7, 40), 1e-12);
        assertEquals(14, DamageLifeSteal.healing(200, 400, .7, 40), 1e-12);
        assertEquals(48, DamageLifeSteal.healing(400, 400, 1.2, 40), 1e-12);
        assertEquals(36, DamageLifeSteal.healing(300, 400, 1.2, 40), 1e-12);
        assertEquals(12, DamageLifeSteal.healing(10, 10, 1.2, 40), 1e-12);
        assertEquals(1.2, DamageLifeSteal.healing(10, 400, 1.2, 40), 1e-12);
    }

    @Test
    void endPreviewRetainsItsExistingZeroAndNonFiniteEfficiencyFallbacks() {
        assertEquals(.6, DamageLifeSteal.rate(0, .6, 30), 1e-12);
        assertEquals(.006, DamageLifeSteal.rate(Double.NaN, .6, 30), 1e-12);
        assertEquals(.006, DamageLifeSteal.rate(Double.POSITIVE_INFINITY, .6, 30), 1e-12);
        assertEquals(0, DamageLifeSteal.healing(1, Double.NaN, .6, 30));
    }
}
