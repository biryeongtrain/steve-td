package kim.biryeong.semiontd.tower.army;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Medals are the family's only permanent progression, so the cap and the per-player isolation are
 * pinned here rather than left to inspection.
 */
class ArmyStatesTest {
    private static final UUID PLAYER = UUID.nameUUIDFromBytes("army-player".getBytes());
    private static final double EPSILON = 1.0E-9;

    /** See {@code ArmyTowerCatalogTest}: touching the balance runtime without this poisons the fork. */
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void tearDown() {
        ArmyStates.clearAll();
    }

    @Test
    void freshPlayerHasNoMedals() {
        assertEquals(0, ArmyStates.medalCount(PLAYER));
        assertEquals(0.0, ArmyStates.medalBonus(PLAYER), EPSILON);
    }

    @Test
    void medalsAccumulateAndConvertToDamage() {
        ArmyStates.awardMedal(PLAYER, 1.0);
        ArmyStates.awardMedal(PLAYER, 1.0);
        assertEquals(2, ArmyStates.medalCount(PLAYER));
        assertEquals(2.0 * ArmyBalance.medalDamageBonus(), ArmyStates.medalBonus(PLAYER), EPSILON);
    }

    @Test
    void quartermasterBonusAwardsFractionalMedals() {
        ArmyStates.awardMedal(PLAYER, 1.5);
        assertEquals(1, ArmyStates.medalCount(PLAYER), "display rounds down");
        assertEquals(1.5 * ArmyBalance.medalDamageBonus(), ArmyStates.medalBonus(PLAYER), EPSILON,
                "the fractional part must still pay out, otherwise 보급관 does nothing on odd counts");
    }

    @Test
    void medalsStopAtTheCap() {
        for (int i = 0; i < ArmyBalance.maxMedals() * 3; i++) {
            ArmyStates.awardMedal(PLAYER, 1.0);
        }
        assertEquals(ArmyBalance.maxMedals(), ArmyStates.medalCount(PLAYER));
        assertEquals(ArmyBalance.maxMedals() * ArmyBalance.medalDamageBonus(),
                ArmyStates.medalBonus(PLAYER), EPSILON,
                "an uncapped medal count would make the rotation the only thing that matters");
    }

    @Test
    void nonPositiveAwardsAreIgnored() {
        ArmyStates.awardMedal(PLAYER, 0.0);
        ArmyStates.awardMedal(PLAYER, -5.0);
        assertEquals(0, ArmyStates.medalCount(PLAYER));
    }

    @Test
    void clearingOnePlayerLeavesOthersAlone() {
        UUID other = UUID.nameUUIDFromBytes("other-army-player".getBytes());
        ArmyStates.awardMedal(PLAYER, 3.0);
        ArmyStates.awardMedal(other, 2.0);

        ArmyStates.clear(PLAYER);

        assertEquals(0, ArmyStates.medalCount(PLAYER));
        assertEquals(2, ArmyStates.medalCount(other));
    }

    /**
     * The medal ceiling is a balance budget, not an implementation detail.
     *
     * <p>Per-slot effectiveness was derived with the permanent bonus fixed at +100%: a ten-slot
     * roster already reaches the command cap, so anything added here lands on top of the family's
     * strongest configuration rather than helping the weak early game. Raising it needs the slot
     * maths redone, so the assertion is a hard stop rather than a loose sanity check.
     */
    @Test
    void medalCapKeepsTheFamilyWithinItsBudget() {
        for (int i = 0; i < 100; i++) {
            ArmyStates.awardMedal(PLAYER, 1.0);
        }
        assertEquals(1.0, ArmyStates.medalBonus(PLAYER), 1.0E-9,
                "permanent bonus is budgeted at exactly +100%; re-derive per-slot output before changing it");
    }
}
