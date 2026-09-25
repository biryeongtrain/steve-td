package kim.biryeong.semiontd.tower.warlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.augment.AugmentTower;
import kim.biryeong.semiontd.tower.augment.AugmentTowers;
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
        WarlockSacrificeTower valid = sacrifice(WarlockTowers.T1_RANGED_SLAVE, owner, new GridPosition(2, 0, 0));
        WarlockSacrificeTower foreign = sacrifice(
                WarlockTowers.T1_RANGED_SLAVE,
                UUID.randomUUID(),
                new GridPosition(1, 0, 0)
        );
        WarlockSacrificeTower distant = sacrifice(
                WarlockTowers.T1_RANGED_SLAVE,
                owner,
                new GridPosition(6, 0, 0)
        );
        WarlockTower otherCore = new WarlockTower(
                WarlockTowers.BASE_WARLOCK_TOWER,
                owner,
                TeamId.RED,
                0,
                new GridPosition(1, 0, 0)
        );

        WarlockRules.SacrificeRule rule = rule(5.0);
        assertTrue(WarlockSacrificeController.isEligibleTarget(warlock, valid, rule));
        assertFalse(WarlockSacrificeController.isEligibleTarget(warlock, warlock, rule));
        assertFalse(WarlockSacrificeController.isEligibleTarget(warlock, otherCore, rule));
        assertFalse(WarlockSacrificeController.isEligibleTarget(warlock, foreign, rule));
        assertFalse(WarlockSacrificeController.isEligibleTarget(warlock, distant, rule));

        valid.syncHealth(0.0);
        assertFalse(WarlockSacrificeController.isEligibleTarget(warlock, valid, rule));
        assertFalse(WarlockSacrificeController.isEligibleTarget(warlock, null, rule));
        assertFalse(WarlockSacrificeController.isEligibleTarget(null, valid, rule));
    }

    @Test
    void specializedPathsOnlyAcceptTheirOwnSacrificeLine() {
        UUID owner = UUID.randomUUID();
        WarlockTower ranged = warlock(WarlockTowers.RANGED_WARLOCK_TOWER, owner);
        WarlockTower melee = warlock(WarlockTowers.MELEE_WARLOCK_TOWER, owner);
        WarlockTower base = warlock(WarlockTowers.BASE_WARLOCK_TOWER, owner);
        WarlockSacrificeTower rangedPet = sacrifice(
                WarlockTowers.T1_RANGED_SLAVE,
                owner,
                new GridPosition(1, 0, 0)
        );
        WarlockSacrificeTower meleePet = sacrifice(
                WarlockTowers.T1_SLAVE,
                owner,
                new GridPosition(1, 0, 0)
        );

        WarlockRules.SacrificeRule rule = rule(5.0);
        assertTrue(WarlockSacrificeController.isEligibleTarget(ranged, rangedPet, rule));
        assertFalse(WarlockSacrificeController.isEligibleTarget(ranged, meleePet, rule));
        assertTrue(WarlockSacrificeController.isEligibleTarget(melee, meleePet, rule));
        assertFalse(WarlockSacrificeController.isEligibleTarget(melee, rangedPet, rule));
        assertTrue(WarlockSacrificeController.isEligibleTarget(base, rangedPet, rule));
        assertTrue(WarlockSacrificeController.isEligibleTarget(base, meleePet, rule));
    }

    @Test
    void basePathCannotAbsorbAnyAugmentBodyForPermanentStats() {
        UUID owner = UUID.randomUUID();
        WarlockTower base = warlock(WarlockTowers.BASE_WARLOCK_TOWER, owner);
        GridPosition position = new GridPosition(1, 0, 0);
        for (var type : AugmentTowers.all()) {
            var target = new AugmentTower(type, owner, TeamId.RED, 0, position, position);
            assertFalse(WarlockSacrificeController.isEligibleTarget(base, target, rule(5)), type.id());
        }
    }

    @Test
    void equalPriorityTargetsResolveByDistanceThenPosition() {
        UUID owner = UUID.randomUUID();
        WarlockTower warlock = warlock(WarlockTowers.RANGED_WARLOCK_TOWER, owner);
        WarlockSacrificeTower far = sacrifice(
                WarlockTowers.T1_RANGED_SLAVE,
                owner,
                new GridPosition(4, 0, 0)
        );
        WarlockSacrificeTower near = sacrifice(
                WarlockTowers.T1_RANGED_SLAVE,
                owner,
                new GridPosition(2, 0, 0)
        );

        Tower selected = List.<Tower>of(far, near).stream()
                .min(WarlockSacrificeController.deterministicPriority(
                        warlock,
                        Comparator.comparingInt(Tower::aggroPriority)
                ))
                .orElseThrow();

        assertEquals(near, selected);
    }

    @Test
    void rangedDamageReductionActivatesAtFifteenPercentAfterThreshold() {
        WarlockState state = new WarlockState();
        WarlockSacrificeController sacrifice = new WarlockSacrificeController(WarlockConfig.RUNTIME, state);

        for (int count = 0; count < 3; count++) {
            recordSacrifice(state);
        }
        assertEquals(0.0, sacrifice.damageReduction(WarlockPath.RANGED), 0.0001);
        recordSacrifice(state);
        assertEquals(0.15, sacrifice.damageReduction(WarlockPath.RANGED), 0.0001);
    }

    @Test
    void meleeDamageReductionGrowsEveryTenAbsorptionsAndCapsAtThirtyPercent() {
        WarlockState state = new WarlockState();
        WarlockSacrificeController sacrifice = new WarlockSacrificeController(WarlockConfig.RUNTIME, state);

        for (int count = 0; count < 9; count++) {
            recordSacrifice(state);
        }
        assertEquals(0.0, sacrifice.damageReduction(WarlockPath.MELEE), 0.0001);
        recordSacrifice(state);
        assertEquals(0.025, sacrifice.damageReduction(WarlockPath.MELEE), 0.0001);
        for (int count = 10; count < 120; count++) {
            recordSacrifice(state);
        }
        assertEquals(0.30, sacrifice.damageReduction(WarlockPath.MELEE), 0.0001);
        for (int count = 120; count < 140; count++) {
            recordSacrifice(state);
        }
        assertEquals(0.30, sacrifice.damageReduction(WarlockPath.MELEE), 0.0001);
    }

    private static WarlockTower warlock(kim.biryeong.semiontd.tower.TowerType type, UUID owner) {
        return new WarlockTower(type, owner, TeamId.RED, 0, new GridPosition(0, 0, 0));
    }

    private static WarlockRules.SacrificeRule rule(double radius) {
        return WarlockRules.SacrificeRule.fromConfiguredRadius(radius, 0.0);
    }

    private static void recordSacrifice(WarlockState state) {
        state.recordSacrifice(new WarlockSacrifice.Gain(0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
    }

    private static WarlockSacrificeTower sacrifice(
            kim.biryeong.semiontd.tower.TowerType type,
            UUID owner,
            GridPosition position
    ) {
        return new WarlockSacrificeTower(
                type,
                owner,
                TeamId.RED,
                0,
                position
        );
    }
}
