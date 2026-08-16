package kim.biryeong.semiontd.tower.warlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WarlockSacrificeControllerTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void eligibilityRejectsDeadCoreForeignAndOutOfRangeTargets() {
        UUID owner = UUID.randomUUID();
        WarlockTower warlock = new WarlockTower(
                WarlockTowers.RANGED_WARLOCK_TOWER,
                owner,
                TeamId.RED,
                0,
                new GridPosition(0, 0, 0)
        );
        WarlockSacrificeTower valid = sacrifice(owner, new GridPosition(2, 0, 0));
        WarlockSacrificeTower foreign = sacrifice(UUID.randomUUID(), new GridPosition(1, 0, 0));
        WarlockSacrificeTower distant = sacrifice(owner, new GridPosition(6, 0, 0));
        WarlockTower otherCore = new WarlockTower(
                WarlockTowers.BASE_WARLOCK_TOWER,
                owner,
                TeamId.RED,
                0,
                new GridPosition(1, 0, 0)
        );

        assertTrue(WarlockSacrificeController.isEligibleTarget(warlock, valid, 5.0));
        assertFalse(WarlockSacrificeController.isEligibleTarget(warlock, warlock, 5.0));
        assertFalse(WarlockSacrificeController.isEligibleTarget(warlock, otherCore, 5.0));
        assertFalse(WarlockSacrificeController.isEligibleTarget(warlock, foreign, 5.0));
        assertFalse(WarlockSacrificeController.isEligibleTarget(warlock, distant, 5.0));

        valid.syncHealth(0.0);
        assertFalse(WarlockSacrificeController.isEligibleTarget(warlock, valid, 5.0));
        assertFalse(WarlockSacrificeController.isEligibleTarget(warlock, null, 5.0));
        assertFalse(WarlockSacrificeController.isEligibleTarget(null, valid, 5.0));
    }

    @Test
    void rangedDamageReductionActivatesAtFifteenPercentAfterThreshold() {
        WarlockState state = new WarlockState();
        WarlockSacrificeController controller = new WarlockSacrificeController(WarlockConfig.RUNTIME, state);
        WarlockTower ranged = new WarlockTower(
                WarlockTowers.RANGED_WARLOCK_TOWER,
                UUID.randomUUID(),
                TeamId.RED,
                0,
                new GridPosition(0, 0, 0)
        );

        for (int count = 0; count < 3; count++) {
            state.absorbForRound(0.0, 0.0, 0.0);
        }
        assertEquals(0.0, controller.damageReduction(ranged), 0.0001);
        state.absorbForRound(0.0, 0.0, 0.0);
        assertEquals(0.15, controller.damageReduction(ranged), 0.0001);
    }

    @Test
    void meleeDamageReductionGrowsEveryTenAbsorptionsAndCapsAtThirtyPercent() {
        WarlockState state = new WarlockState();
        WarlockSacrificeController controller = new WarlockSacrificeController(WarlockConfig.RUNTIME, state);
        WarlockTower melee = new WarlockTower(
                WarlockTowers.MELEE_WARLOCK_TOWER,
                UUID.randomUUID(),
                TeamId.RED,
                0,
                new GridPosition(0, 0, 0)
        );

        for (int count = 0; count < 9; count++) {
            state.absorbForRound(0.0, 0.0, 0.0);
        }
        assertEquals(0.0, controller.damageReduction(melee), 0.0001);
        state.absorbForRound(0.0, 0.0, 0.0);
        assertEquals(0.025, controller.damageReduction(melee), 0.0001);
        for (int count = 10; count < 120; count++) {
            state.absorbForRound(0.0, 0.0, 0.0);
        }
        assertEquals(0.30, controller.damageReduction(melee), 0.0001);
        for (int count = 120; count < 140; count++) {
            state.absorbForRound(0.0, 0.0, 0.0);
        }
        assertEquals(0.30, controller.damageReduction(melee), 0.0001);
    }

    private static WarlockSacrificeTower sacrifice(UUID owner, GridPosition position) {
        return new WarlockSacrificeTower(
                WarlockTowers.T1_RANGED_SLAVE,
                owner,
                TeamId.RED,
                0,
                position
        );
    }
}
