package kim.biryeong.semiontd.tower.army;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
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

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

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
        assertEquals(ArmyRank.STAFF_SERGEANT, ArmyRank.of(ArmyBalance.dischargeService()));
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
        assertEquals(0.40, ArmyRank.STAFF_SERGEANT.attackMultiplier(), EPSILON,
                "the top rank must retain enough firepower to prevent a same-wave roster shutdown");
    }

    /**
     * The number the family's stats were derived from.
     *
     * <p>{@code (2*1.00 + 3*0.75 + 4*0.60 + 4*0.40) / 13}. If this drifts, the listed damage on
     * every 전투 tower has to be recomputed against the dealer curve.
     */
    @Test
    void lifetimeAverageAttackMultiplierIsAboutSixtyThreePercent() {
        double total = 0.0;
        for (int service = 0; service < ArmyBalance.dischargeService(); service++) {
            total += ArmyRank.of(service).attackMultiplier();
        }
        double average = total / ArmyBalance.dischargeService();
        assertEquals(0.634615, average, 0.000001,
                "family stats assume the aggressive mid-late rank curve; re-derive them before changing it");
    }

    @Test
    void promotionCountdownStopsAtTheTopRank() {
        assertEquals(2, ArmyRank.wavesUntilPromotion(0));
        assertEquals(1, ArmyRank.wavesUntilPromotion(4));
        assertEquals(-1, ArmyRank.wavesUntilPromotion(9), "병장 has nothing left to be promoted to");
    }

    @Test
    void dischargeCountdownReachesZeroAndStops() {
        assertEquals(ArmyBalance.dischargeService(), ArmyRank.wavesUntilDischarge(0));
        assertEquals(0, ArmyRank.wavesUntilDischarge(ArmyBalance.dischargeService()));
        assertEquals(0, ArmyRank.wavesUntilDischarge(ArmyBalance.dischargeService() + 5),
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
     * <p>A 고참 still pays personal damage to buff juniors, but the aggressive curve lets later
     * ranks recover that loss with fewer nearby juniors.
     */
    @Test
    void everyRankBreaksEvenWithinAboutTwoJuniors() {
        for (ArmyRank rank : ArmyRank.values()) {
            if (rank == ArmyRank.PRIVATE) {
                continue;
            }
            double surrendered = ArmyRank.PRIVATE.attackMultiplier() - rank.attackMultiplier();
            double breakEven = surrendered / rank.damageBuff();
            assertTrue(breakEven >= 1.1 && breakEven <= 2.1,
                    rank + " break-even should stay within about two juniors but was " + breakEven);
        }
    }
}
