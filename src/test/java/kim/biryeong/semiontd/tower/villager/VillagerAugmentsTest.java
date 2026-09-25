package kim.biryeong.semiontd.tower.villager;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.augment.*;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VillagerAugmentsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void growthFamiliesSpanTiersButNeverMixThornsAndSplash() {
        UUID owner = UUID.randomUUID();
        GridPosition p = new GridPosition(0, 0, 0);
        var thorn = new VillagerThornTower(VillagerTowers.T2_GOLEM_TOWER, owner, TeamId.RED, 1, p);
        var upgraded = new VillagerThornTower(VillagerTowers.T3_GOLEM_TOWER, owner, TeamId.RED, 1, p);
        var splash = new VillagerSplashTower(VillagerTowers.T2_LIBRARIAN_TOWER, owner, TeamId.RED, 1, p);
        assertTrue(VillagerAugments.sameFamily(thorn, upgraded));
        assertFalse(VillagerAugments.sameFamily(thorn, splash));
        thorn.addSurvivalStacks(3);
        assertEquals(3, VillagerAugments.permanentStacks(thorn));
        upgraded.copyFrom(thorn, 100);
        assertEquals(3, VillagerAugments.permanentStacks(upgraded));
        assertFalse(VillagerAugments.giantTarget(thorn));
        assertTrue(VillagerAugments.giantTarget(upgraded));
    }

    @Test
    void giantSelectionPreservesHealthRatioAndDoesNotBuffOtherGolems() {
        UUID owner = UUID.randomUUID();
        GridPosition p = new GridPosition(0, 0, 0);
        var tower = new VillagerThornTower(VillagerTowers.T3_GOLEM_TOWER, owner, TeamId.RED, 1, p);
        double before = tower.currentMaxHealth();
        tower.syncHealth(before * .4);
        var choice = new AugmentChoice(tower.logicalId(), null, "");
        tower.syncAugments(new AugmentSnapshot(AugmentConfig.defaults(), List.of(new PlayerAugmentState.Selection(
                5, AugmentRarity.PRISMATIC, VillagerAugments.GIANT, PlayerAugmentState.Outcome.SELECTED, null, choice))), null);
        assertEquals(before * 3, tower.currentMaxHealth(), .001);
        assertEquals(tower.currentMaxHealth() * .4, tower.health(), .001);
        tower.syncAugments(AugmentSnapshot.none(), null);
        assertEquals(before, tower.currentMaxHealth(), .001);
        assertEquals(before * .4, tower.health(), .001);
    }
}
