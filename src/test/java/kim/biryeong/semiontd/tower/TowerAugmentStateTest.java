package kim.biryeong.semiontd.tower;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TowerAugmentStateTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void snapshotChangesPreservePermanentAndCachedGrowthWithHealthFraction() {
        ProductionTower tower = tower(type(100, 20));
        tower.addPermanentMaxHealthBonus(25, null);
        tower.syncMaxHealth(175, false);
        tower.syncHealth(70);
        tower.syncAugments(snapshot(), null);
        assertEquals(175, tower.currentMaxHealth(), .001);
        assertEquals(70, tower.health(), .001);
        assertEquals(25, tower.permanentMaxHealthBonus(), .001);
        tower.syncAugments(AugmentSnapshot.none(), null);
        assertEquals(175, tower.currentMaxHealth(), .001);
        assertEquals(70, tower.health(), .001);
    }

    @Test
    void copyGetsIndependentIdentityAndKeepsFrozenTypeAcrossReload() {
        ProductionTower original = tower(type(100, 20));
        ProductionTower copy = tower(type(40, 8));
        TowerDataKey<Integer> marker = TowerDataKey.of(ResourceLocation.parse("semiontd:copy_test_marker"), Integer.class);
        original.setData(marker, 7);
        copy.copyFrom(original, 0);
        assertEquals(original.logicalId(), copy.logicalId());
        copy.markTemporaryCopy(original.logicalId());
        UUID copyId = copy.logicalId();
        assertNotEquals(original.logicalId(), copyId);
        assertEquals(original.logicalId(), copy.temporaryCopySourceId());
        assertEquals(7, copy.getDataOrDefault(marker, 0));
        copy.markTemporaryCopy(original.logicalId());
        assertEquals(copyId, copy.logicalId());
        copy.refreshType(type(1000, 200), null);
        assertEquals(40, copy.type().maxHealth(), .001);
        assertEquals(8, copy.type().damage(), .001);
        assertEquals(40, copy.currentMaxHealth(), .001);
        original.refreshType(type(1000, 200), null);
        assertEquals(1000, original.type().maxHealth(), .001);
        assertFalse(copy.canBeSold());
        assertEquals(0, copy.slotWeight());
    }

    private static ProductionTower tower(TowerType type) {
        return new ProductionTower(type, UUID.randomUUID(), TeamId.RED, 1, new GridPosition(0, 64, 0));
    }

    private static TowerType type(double health, double damage) {
        return new TowerType("augment_state_test", "Test", TowerCategory.DIRECT, 10, health, 5, damage, 20, 0);
    }

    private static AugmentSnapshot snapshot() {
        return new AugmentSnapshot(AugmentConfig.defaults(), List.of(new PlayerAugmentState.Selection(
                5, AugmentRarity.SILVER, "job_hero_party_s", PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())));
    }
}
