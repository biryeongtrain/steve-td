package kim.biryeong.semiontd.tower.atlantis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AtlantisPressureTest {
    private static final double EPSILON = 1.0e-6;
    private static final UUID OWNER = UUID.nameUUIDFromBytes("pressure-owner".getBytes());
    private static final UUID MONSTER = UUID.nameUUIDFromBytes("pressure-monster".getBytes());
    private static final GridPosition TOWER = new GridPosition(4, 80, 7);
    private static final GridPosition OTHER_TOWER = new GridPosition(9, 80, 2);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void applyDefaults() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        AtlantisPressure.clearAll();
    }

    @AfterEach
    void reset() {
        AtlantisPressure.clearAll();
    }

    @Test
    void burstScalesWithStacksUpToTheConfiguredCeiling() {
        double three = AtlantisPressure.burstDamage(20.0, 3, 0.0);
        double ten = AtlantisPressure.burstDamage(20.0, 10, 0.0);

        assertTrue(ten > three, "more stacks must mean a bigger burst");
        assertEquals(20.0 * 3 * AtlantisBalance.waterPressureDamageRatio(), three, EPSILON);
        assertEquals(20.0 * 10 * AtlantisBalance.waterPressureDamageRatio(), ten, EPSILON,
                "the unsupported full stack burst must use the tuned base ratio");
    }

    @Test
    void burstIsClampedByTheDamageCap() {
        double capped = AtlantisPressure.burstDamage(20.0, 20, 0.0);
        assertEquals(20.0 * AtlantisBalance.waterPressureDamageCap(), capped, EPSILON);
        assertTrue(capped < 20.0 * 20 * AtlantisBalance.waterPressureDamageRatio(),
                "cap must bind before raw stacks do");
    }

    @Test
    void supportBonusRaisesTheRatioButNotBeyondTheCap() {
        double base = AtlantisPressure.burstDamage(20.0, 10, 0.0);
        assertTrue(AtlantisPressure.burstDamage(20.0, 10, 0.05) >= base,
                "a support bonus must never lower the burst");
        assertEquals(20.0 * AtlantisBalance.waterPressureDamageCap(),
                AtlantisPressure.burstDamage(20.0, 10, 5.0), EPSILON,
                "an arbitrarily large bonus is absorbed by the cap");
    }

    @Test
    void zeroDamageOrZeroStacksProduceNoBurst() {
        assertEquals(0.0, AtlantisPressure.burstDamage(0.0, 10, 0.0), EPSILON);
        assertEquals(0.0, AtlantisPressure.burstDamage(20.0, 0, 0.0), EPSILON);
    }

    @Test
    void slowGrowsPerStackAndSaturates() {
        assertEquals(0.0, AtlantisPressure.slowMagnitude(0), EPSILON);
        assertEquals(3 * AtlantisBalance.slowPerStack(), AtlantisPressure.slowMagnitude(3), EPSILON);
        assertEquals(AtlantisBalance.maxSlow(), AtlantisPressure.slowMagnitude(50), EPSILON,
                "slow must saturate at maxSlow");
    }

    @Test
    void stacksAccumulateAndRespectTheCeiling() {
        AtlantisPressure.addStacks(MONSTER, OWNER, TOWER, 3, 20.0, 10, 100);
        assertEquals(3, AtlantisPressure.stacks(OWNER, MONSTER));

        AtlantisPressure.addStacks(MONSTER, OWNER, TOWER, 4, 20.0, 10, 100);
        assertEquals(7, AtlantisPressure.stacks(OWNER, MONSTER));

        AtlantisPressure.addStacks(MONSTER, OWNER, TOWER, 99, 20.0, 10, 100);
        assertEquals(10, AtlantisPressure.stacks(OWNER, MONSTER), "stack ceiling must bind");
    }

    @Test
    void consumingWaterPressureRemovesTheEntry() {
        AtlantisPressure.addStacks(MONSTER, OWNER, TOWER, 10, 20.0, 10, 100);
        assertEquals(AtlantisPressure.burstDamage(20.0, 10, 0.0),
                AtlantisPressure.consumeForBurst(OWNER, MONSTER, 0.0), EPSILON);
        assertEquals(0, AtlantisPressure.stacks(OWNER, MONSTER), "pressure must not survive its own burst");
        assertEquals(0.0, AtlantisPressure.consumeForBurst(OWNER, MONSTER, 0.0), EPSILON,
                "a second detonation on the same monster yields nothing");
    }

    @Test
    void chainRefusesToDetonateTheSameMonsterTwice() {
        AtlantisPressure.Chain chain = new AtlantisPressure.Chain();
        assertTrue(chain.canBurst(MONSTER));
        assertFalse(chain.canBurst(MONSTER), "a monster may only detonate once per chain");
    }

    @Test
    void chainStopsAtTheConfiguredDepth() {
        AtlantisPressure.Chain chain = new AtlantisPressure.Chain();
        int depth = AtlantisBalance.maxChainDepth();
        for (int index = 0; index < depth; index++) {
            assertTrue(chain.canBurst(UUID.nameUUIDFromBytes(("chain-" + index).getBytes())),
                    "depth " + index + " must still be allowed");
            chain.enter();
        }
        assertFalse(chain.canBurst(UUID.nameUUIDFromBytes("chain-overflow".getBytes())),
                "the cascade must stop once maxChainDepth is reached");
    }

    @Test
    void clearingAPlayerDropsOnlyThatPlayersPressure() {
        UUID other = UUID.nameUUIDFromBytes("other-owner".getBytes());
        UUID otherMonster = UUID.nameUUIDFromBytes("other-monster".getBytes());
        AtlantisPressure.addStacks(MONSTER, OWNER, TOWER, 5, 20.0, 10, 100);
        AtlantisPressure.addStacks(otherMonster, other, OTHER_TOWER, 5, 20.0, 10, 100);

        AtlantisPressure.clearPlayer(OWNER);

        assertEquals(0, AtlantisPressure.stacks(OWNER, MONSTER));
        assertEquals(5, AtlantisPressure.stacks(other, otherMonster), "another player's pressure must survive");
    }

    @Test
    void pressureIsGroupedByTheDolphinThatOwnsIt() {
        UUID second = UUID.nameUUIDFromBytes("second-monster".getBytes());
        AtlantisPressure.addStacks(MONSTER, OWNER, TOWER, 2, 20.0, 10, 100);
        AtlantisPressure.addStacks(second, OWNER, OTHER_TOWER, 2, 20.0, 10, 100);

        assertEquals(List.of(MONSTER), AtlantisPressure.monstersFrom(OWNER, TOWER));
        assertEquals(List.of(second), AtlantisPressure.monstersFrom(OWNER, OTHER_TOWER));
        assertTrue(AtlantisPressure.monstersFrom(OWNER, new GridPosition(99, 80, 99)).isEmpty(),
                "a dolphin that applied nothing owns nothing");
    }

    @Test
    void theHardestHitterTakesOverThePressureItSized() {
        AtlantisPressure.addStacks(MONSTER, OWNER, TOWER, 1, 9.0, 10, 100);
        assertEquals(List.of(MONSTER), AtlantisPressure.monstersFrom(OWNER, TOWER));

        // A stronger dolphin joins in: the burst is sized from its damage, so it must also be the
        // tower that releases it, otherwise the damage is attributed to the wrong tower.
        AtlantisPressure.addStacks(MONSTER, OWNER, OTHER_TOWER, 1, 26.0, 10, 100);

        assertTrue(AtlantisPressure.monstersFrom(OWNER, TOWER).isEmpty(), "the weaker dolphin hands over ownership");
        assertEquals(List.of(MONSTER), AtlantisPressure.monstersFrom(OWNER, OTHER_TOWER));
        assertEquals(AtlantisPressure.burstDamage(26.0, 2, 0.0),
                AtlantisPressure.consumeForBurst(OWNER, MONSTER, 0.0), EPSILON,
                "the burst uses the strongest applier's damage");
    }

    @Test
    void pressureExpiresOnceItsDurationRunsOut() {
        AtlantisPressure.addStacks(MONSTER, OWNER, TOWER, 4, 20.0, 10, 40);

        assertFalse(AtlantisPressure.tickExpired(OWNER, MONSTER, 20), "half the duration must not expire it");
        assertTrue(AtlantisPressure.tickExpired(OWNER, MONSTER, 20), "the duration is spent, the pressure lapses");
        assertFalse(AtlantisPressure.tickExpired(OWNER, UUID.randomUUID(), 20),
                "a monster carrying nothing never reports an expiry");
    }

    @Test
    void twoPlayersTrackTheSameMonsterIndependently() {
        UUID other = UUID.nameUUIDFromBytes("same-monster-other-owner".getBytes());
        AtlantisPressure.addStacks(MONSTER, OWNER, TOWER, 3, 20.0, 10, 100);
        AtlantisPressure.addStacks(MONSTER, other, TOWER, 7, 30.0, 10, 100);

        assertEquals(3, AtlantisPressure.stacks(OWNER, MONSTER));
        assertEquals(7, AtlantisPressure.stacks(other, MONSTER));
        assertEquals(List.of(MONSTER), AtlantisPressure.monstersFrom(OWNER, TOWER));
        assertEquals(List.of(MONSTER), AtlantisPressure.monstersFrom(other, TOWER));

        AtlantisPressure.consumeForBurst(OWNER, MONSTER, 0.0);
        assertEquals(0, AtlantisPressure.stacks(OWNER, MONSTER));
        assertEquals(7, AtlantisPressure.stacks(other, MONSTER),
                "one sandbox or final-defense owner must not consume another owner's pressure");
    }
}
