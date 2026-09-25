package kim.biryeong.semiontd.tower.undead;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.augment.*;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UndeadAugmentsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void boneChargesRequireCappedGrowthAndSameLaneEnemiesThenAccumulateAndReset() {
        Tower tower = tower(UndeadTowers.T2_MELEE_TOWER);
        select(tower, UndeadAugments.BONES);
        Monster enemy = enemy(1);
        Vec3 nearby = new Vec3(.5, 65, .5);
        int cap = TowerBalanceRuntime.abilityInt(tower.type().id(), "stackCap");
        for (int i = 0; i < cap; i++) {tower.onNearbyMonsterDeath(null, enemy, nearby);}
        assertFalse(UndeadAugments.consumeBoneCharge(tower), "The cap-reaching death is still growth.");
        for (int i = 0; i < 10; i++) {tower.onNearbyMonsterDeath(null, enemy, nearby);}
        assertTrue(UndeadAugments.consumeBoneCharge(tower));
        AugmentCombat.runWithoutTriggers(() -> assertFalse(UndeadAugments.consumeBoneCharge(tower)));
        assertTrue(UndeadAugments.consumeBoneCharge(tower));
        assertFalse(UndeadAugments.consumeBoneCharge(tower));
        for (int i = 0; i < 5; i++) {
            tower.onNearbyMonsterDeath(null, enemy(2), nearby);
            tower.onNearbyMonsterDeath(null, enemy, nearby.add(100, 0, 0));
            tower.onNearbyTowerDeath(null, tower(UndeadTowers.T1_ZOMBIE_TOWER));
            AugmentCombat.runWithoutTriggers(() -> tower.onNearbyMonsterDeath(null, enemy, nearby));
        }
        assertFalse(UndeadAugments.consumeBoneCharge(tower));
        for (int i = 0; i < 5; i++) {tower.onNearbyMonsterDeath(null, enemy, nearby);}
        UndeadAugments.resetTower(tower);
        assertFalse(UndeadAugments.consumeBoneCharge(tower));
    }

    @Test
    void kingFollowsLogicalUpgradeAndCopiesFreezeWithoutGrowthOrAugments() {
        Tower king = tower(UndeadTowers.T2_RANGED_SKELETON_TOWER);
        double before = king.currentMaxHealth();
        king.syncHealth(before * .4);
        select(king, UndeadAugments.KING);
        assertEquals(1, UndeadAugments.maxHealthBonus(king));
        assertEquals(before * 2, king.currentMaxHealth(), .001);
        assertEquals(king.currentMaxHealth() * .4, king.health(), .001);
        Tower upgraded = tower(UndeadTowers.T3_RANGED_SKELETON_TOWER);
        upgraded.copyFrom(king, 100);
        assertEquals(king.logicalId(), upgraded.logicalId());
        assertEquals(1, UndeadAugments.maxHealthBonus(upgraded));
        Tower copy = UndeadAugments.createCopy(upgraded, 350, 35);
        assertTrue(copy.isTemporaryCopy());
        assertFalse(copy.canBeSold());
        assertEquals(0, copy.slotWeight());
        assertEquals(upgraded.logicalId(), copy.temporaryCopySourceId());
        assertEquals(350, copy.currentMaxHealth(), .001);
        assertEquals(35, copy.modifyAttackDamage(null, null, 1000), .001);
        for (int i = 0; i < 100; i++) {
            copy.onNearbyMonsterDeath(null, enemy(1), new Vec3(.5, 65, .5));
            copy.onNearbyTowerDeath(null, king);
        }
        assertEquals(350, copy.currentMaxHealth(), .001);
        assertEquals(35, copy.modifyAttackDamage(null, null, 1000), .001);
        assertEquals(0, UndeadAugments.damageBonus(copy));
        assertFalse(UndeadAugments.skeletonTarget(copy));
    }

    @Test
    void secondChanceWaitsTwentyTicksOnlyOnceAndRoundResetCancelsPendingRevival() {
        Tower tower = tower(UndeadTowers.T3_ZOMBIE_TOWER);
        select(tower, UndeadAugments.SECOND_CHANCE);
        tower.syncHealth(0);
        UndeadAugments.onDeath(null, tower);
        for (int i = 0; i < 19; i++) {UndeadAugments.tickRevival(null, tower);}
        assertEquals(0, tower.health());
        UndeadAugments.tickRevival(null, tower);
        assertEquals(tower.currentMaxHealth() * .6, tower.health(), .001);
        assertEquals(.6, UndeadAugments.damageBonus(tower), .001);
        tower.syncHealth(0);
        UndeadAugments.onDeath(null, tower);
        for (int i = 0; i < 20; i++) {UndeadAugments.tickRevival(null, tower);}
        assertEquals(0, tower.health());
        UndeadAugments.resetTower(tower);
        UndeadAugments.onDeath(null, tower);
        UndeadAugments.resetTower(tower);
        for (int i = 0; i < 20; i++) {UndeadAugments.tickRevival(null, tower);}
        assertEquals(0, tower.health());
        assertEquals(0, UndeadAugments.damageBonus(tower));
    }

    private static Tower tower(TowerType type) {
        return ProductionTowerCatalog.find(type.id()).orElseThrow()
                .create(UUID.randomUUID(), TeamId.RED, 1, new GridPosition(0, 64, 0));
    }

    private static Monster enemy(int lane) {
        return new Monster("undead_test", TeamId.RED, lane, Optional.empty(), Optional.empty(),
                100, 0, 1, AttackKind.MELEE, "minecraft:zombie", 0);
    }

    private static void select(Tower tower, String card) {
        tower.syncAugments(new AugmentSnapshot(AugmentConfig.defaults(), List.of(new PlayerAugmentState.Selection(
                5, AugmentRarity.GOLD, card, PlayerAugmentState.Outcome.SELECTED, null,
                new AugmentChoice(tower.logicalId(), null, "")))), null);
    }
}
