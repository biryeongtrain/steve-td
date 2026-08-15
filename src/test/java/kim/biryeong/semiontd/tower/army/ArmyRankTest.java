package kim.biryeong.semiontd.tower.army;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The rank curve is the whole builder, so it is pinned by test rather than left to inspection.
 *
 * <p>The lifetime-average case is the important one: the family's stats were set from it, and
 * loosening the curve without re-deriving those stats would silently push the builder off every
 * other builder's balance line.
 */
class ArmyRankTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void serviceMapsToTheExpectedRank() {
        assertEquals(ArmyRank.PRIVATE, ArmyRank.of(0));
        assertEquals(ArmyRank.PRIVATE, ArmyRank.of(1));
        assertEquals(ArmyRank.CORPORAL, ArmyRank.of(2));
        assertEquals(ArmyRank.CORPORAL, ArmyRank.of(4));
        assertEquals(ArmyRank.SERGEANT, ArmyRank.of(5));
        assertEquals(ArmyRank.SERGEANT, ArmyRank.of(8));
        assertEquals(ArmyRank.STAFF_SERGEANT, ArmyRank.of(9));
    }

    @Test
    void rankIsClampedPastTheTop() {
        assertEquals(ArmyRank.STAFF_SERGEANT, ArmyRank.of(ArmyRank.DISCHARGE_SERVICE));
        assertEquals(ArmyRank.STAFF_SERGEANT, ArmyRank.of(999));
    }

    @Test
    void negativeServiceIsTreatedAsFresh() {
        assertEquals(ArmyRank.PRIVATE, ArmyRank.of(-3),
                "a lane stacked with 초소장 must not underflow into an undefined rank");
    }

    @Test
    void attackFallsAndBuffRisesWithRank() {
        ArmyRank previous = null;
        for (ArmyRank rank : ArmyRank.values()) {
            if (previous != null) {
                assertTrue(rank.attackMultiplier() < previous.attackMultiplier(),
                        rank + " must fire less than " + previous);
                assertTrue(rank.damageBuff() > previous.damageBuff(),
                        rank + " must buff more than " + previous);
            }
            previous = rank;
        }
        assertEquals(0.0, ArmyRank.STAFF_SERGEANT.attackMultiplier(), EPSILON,
                "the top rank must stop firing entirely, otherwise stacking 고참 costs nothing");
    }

    /**
     * The number the family's stats were derived from.
     *
     * <p>{@code (2*1.00 + 3*0.75 + 4*0.40 + 4*0.00) / 13}. If this drifts, the listed damage on
     * every 전투 tower has to be recomputed against the dealer curve.
     */
    @Test
    void lifetimeAverageAttackMultiplierIsFortyFivePercent() {
        double total = 0.0;
        for (int service = 0; service < ArmyRank.DISCHARGE_SERVICE; service++) {
            total += ArmyRank.of(service).attackMultiplier();
        }
        double average = total / ArmyRank.DISCHARGE_SERVICE;
        assertEquals(0.45, average, 0.005,
                "family stats assume a 0.45 lifetime average; re-derive them before changing the curve");
    }

    @Test
    void promotionCountdownStopsAtTheTopRank() {
        assertEquals(2, ArmyRank.wavesUntilPromotion(0));
        assertEquals(1, ArmyRank.wavesUntilPromotion(4));
        assertEquals(-1, ArmyRank.wavesUntilPromotion(9), "병장 has nothing left to be promoted to");
    }

    @Test
    void dischargeCountdownReachesZeroAndStops() {
        assertEquals(ArmyRank.DISCHARGE_SERVICE, ArmyRank.wavesUntilDischarge(0));
        assertEquals(0, ArmyRank.wavesUntilDischarge(ArmyRank.DISCHARGE_SERVICE));
        assertEquals(0, ArmyRank.wavesUntilDischarge(ArmyRank.DISCHARGE_SERVICE + 5),
                "an overdue tower must not report a negative countdown");
    }

    @Test
    void seniorityIsStrict() {
        assertTrue(ArmyRank.STAFF_SERGEANT.isSuperiorTo(ArmyRank.SERGEANT));
        assertFalse(ArmyRank.SERGEANT.isSuperiorTo(ArmyRank.STAFF_SERGEANT));
        assertFalse(ArmyRank.SERGEANT.isSuperiorTo(ArmyRank.SERGEANT),
                "equal ranks must not buff each other, or two 병장 would be self-sustaining");
    }

    /**
     * The pyramid rule stated in the tower descriptions.
     *
     * <p>A 고참 pays its full damage to buff juniors, so it only breaks even once roughly two
     * juniors are in range. That ratio is what forces a mixed roster instead of a wall of 병장.
     */
    @Test
    void everyRankBreaksEvenAtAboutTwoJuniors() {
        for (ArmyRank rank : ArmyRank.values()) {
            if (rank == ArmyRank.PRIVATE) {
                continue;
            }
            double surrendered = ArmyRank.PRIVATE.attackMultiplier() - rank.attackMultiplier();
            double breakEven = surrendered / rank.damageBuff();
            assertTrue(breakEven > 1.8 && breakEven < 2.3,
                    rank + " break-even should sit near two juniors but was " + breakEven);
        }
    }
}
