package kim.biryeong.semiontd.tower.thunder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ThunderPowerTest {
    private static final UUID PLAYER = UUID.nameUUIDFromBytes("thunder-player".getBytes());
    private static final double EPSILON = 1.0E-6;

    /**
     * {@code ThunderBalance} reads through {@code TowerBalanceRuntime}, whose static initializer
     * builds the default config and therefore touches {@code EntityType}. Without the bootstrap
     * that initializer throws, and a class that failed to initialize stays unusable for the rest
     * of the JVM — which takes every later test in the same fork down with it.
     */
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void tearDown() {
        ThunderStates.clearAll();
    }

    private static ThunderPower.Snapshot at(double loadFactor) {
        return new ThunderPower.Snapshot(100.0, 100.0 * loadFactor, loadFactor);
    }

    @Test
    void fullSurplusBonusAtAndBelowTheFloor() {
        double expected = 1.0 + ThunderBalance.SURPLUS_DAMAGE_BONUS;
        assertEquals(expected, at(ThunderBalance.SURPLUS_FLOOR).damageMultiplier(), EPSILON);
        assertEquals(expected, at(0.1).damageMultiplier(), EPSILON,
                "below the floor the bonus must stay capped, not keep growing");
        assertEquals(expected, at(0.0).damageMultiplier(), EPSILON);
    }

    @Test
    void balancedGridIsNeutral() {
        assertEquals(1.0, at(1.0).damageMultiplier(), EPSILON);
        assertEquals(1.0, at(1.0).attackSpeedMultiplier(), EPSILON);
    }

    @Test
    void surplusFadesLinearlyTowardsBalance() {
        // Halfway between the floor (0.5) and balance (1.0) pays half the bonus.
        double halfway = ThunderBalance.SURPLUS_FLOOR + (1.0 - ThunderBalance.SURPLUS_FLOOR) / 2.0;
        assertEquals(
                1.0 + ThunderBalance.SURPLUS_DAMAGE_BONUS / 2.0,
                at(halfway).damageMultiplier(),
                EPSILON
        );
    }

    @Test
    void shortagePenaltyBottomsOutAtTheCeiling() {
        double floor = 1.0 - ThunderBalance.SHORTAGE_DAMAGE_PENALTY;
        assertEquals(floor, at(ThunderBalance.SHORTAGE_CEILING).damageMultiplier(), EPSILON);
        assertEquals(floor, at(5.0).damageMultiplier(), EPSILON,
                "a hopeless overdraw must not scale past the configured floor");

        double speedFloor = 1.0 - ThunderBalance.SHORTAGE_ATTACK_SPEED_PENALTY;
        assertEquals(speedFloor, at(ThunderBalance.SHORTAGE_CEILING).attackSpeedMultiplier(), EPSILON);
        assertEquals(speedFloor, at(5.0).attackSpeedMultiplier(), EPSILON);
    }

    @Test
    void attackSpeedIsUntouchedWhilePowerIsSufficient() {
        assertEquals(1.0, at(0.2).attackSpeedMultiplier(), EPSILON,
                "spare power speeds nothing up; only shortage slows towers down");
        assertEquals(1.0, at(0.99).attackSpeedMultiplier(), EPSILON);
    }

    @Test
    void headroomIsClampedToTheUnitRange() {
        assertEquals(0.75, at(0.25).headroom(), EPSILON);
        assertEquals(0.0, at(1.0).headroom(), EPSILON);
        assertEquals(0.0, at(2.0).headroom(), EPSILON, "an overdrawn grid has no headroom to sell");
    }

    @Test
    void emptyGridSuppliesBasePowerAndDrawsNothing() {
        ThunderPower.Snapshot empty = ThunderPower.empty();
        assertEquals(ThunderBalance.basePower(), empty.generation(), EPSILON);
        assertEquals(0.0, empty.consumption(), EPSILON);
        assertTrue(empty.surplus(), "the opening grid must start with headroom, not shortage");
    }

    @Test
    void stormWavesLandOnTheConfiguredInterval() {
        int interval = ThunderBalance.stormWaveInterval();
        assertTrue(ThunderStates.isStormWave(interval));
        assertTrue(ThunderStates.isStormWave(interval * 2));
        assertFalse(ThunderStates.isStormWave(interval + 1));
        assertFalse(ThunderStates.isStormWave(0), "round 0 is not a storm");

        assertEquals(0, ThunderStates.wavesUntilStorm(interval));
        assertEquals(interval - 1, ThunderStates.wavesUntilStorm(interval + 1));
    }

    @Test
    void stormWaveForcesMaximumOutputRegardlessOfTheRoll() {
        int stormRound = ThunderBalance.stormWaveInterval();
        ThunderStates.rollStorm(PLAYER, stormRound, 0.0);
        assertEquals(1.0, ThunderStates.stormRoll(PLAYER), EPSILON,
                "the forecast promises maximum output, so the roll must be ignored");
    }

    @Test
    void ordinaryWavesKeepTheirRoll() {
        int ordinary = ThunderBalance.stormWaveInterval() + 1;
        ThunderStates.rollStorm(PLAYER, ordinary, 0.25);
        assertEquals(0.25, ThunderStates.stormRoll(PLAYER), EPSILON);
    }

    @Test
    void unrolledPlayerReportsTheMidpoint() {
        assertEquals(0.5, ThunderStates.stormRoll(PLAYER), EPSILON,
                "a snapshot taken before the first roll must not advertise an extreme");
    }

    @Test
    void clearingOnePlayerLeavesOthersAlone() {
        UUID other = UUID.nameUUIDFromBytes("other-player".getBytes());
        int ordinary = ThunderBalance.stormWaveInterval() + 1;
        ThunderStates.rollStorm(PLAYER, ordinary, 0.2);
        ThunderStates.rollStorm(other, ordinary, 0.8);

        ThunderStates.clear(PLAYER);

        assertEquals(0.5, ThunderStates.stormRoll(PLAYER), EPSILON);
        assertEquals(0.8, ThunderStates.stormRoll(other), EPSILON);
    }

    @Test
    void forecastCountsDownFromTheRoundThePlayerLastRolled() {
        int interval = ThunderBalance.stormWaveInterval();
        ThunderStates.rollStorm(PLAYER, 1, 0.4);
        assertEquals(interval - 1, ThunderStates.wavesUntilStorm(PLAYER));

        ThunderStates.rollStorm(PLAYER, interval, 0.4);
        assertEquals(0, ThunderStates.wavesUntilStorm(PLAYER),
                "on a storm wave the forecast must read as arrived, not as a full interval away");
    }

    @Test
    void forecastBeforeTheFirstRollReportsAFullInterval() {
        assertEquals(0, ThunderStates.currentRound(PLAYER));
        assertEquals(ThunderBalance.stormWaveInterval(), ThunderStates.wavesUntilStorm(PLAYER));
    }

    @Test
    void clearingAPlayerAlsoForgetsTheirRound() {
        ThunderStates.rollStorm(PLAYER, ThunderBalance.stormWaveInterval() + 1, 0.3);
        assertEquals(ThunderBalance.stormWaveInterval() + 1, ThunderStates.currentRound(PLAYER));

        ThunderStates.clear(PLAYER);

        assertEquals(0, ThunderStates.currentRound(PLAYER),
                "a stale round would make the next match forecast a storm that is not coming");
    }
}
