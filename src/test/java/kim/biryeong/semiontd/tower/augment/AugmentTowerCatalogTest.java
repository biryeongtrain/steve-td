package kim.biryeong.semiontd.tower.augment;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.entity.tower.vfx.BuilderPalette;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerCapacity;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AugmentTowerCatalogTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void nineNeutralTowersUseSharedCatalogWithoutUpgrades() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        assertEquals(9, AugmentTowers.all().size());
        assertEquals(9, ProductionTowerCatalog.all().stream().filter(e -> e.availability() == ProductionTowerCatalog.Availability.AUGMENT).count());
        for (var type : AugmentTowers.all()) {
            var entry = ProductionTowerCatalog.entry(type).orElseThrow();
            assertEquals(AugmentTowers.augmentId(type), entry.augmentId());
            assertEquals(ProductionTowerCatalog.Availability.AUGMENT, entry.availability());
            assertTrue(entry.starter());
            assertFalse(ProductionTowerCatalog.hasUpgrades(type));
            var tower = entry.create(UUID.randomUUID(), TeamId.RED, 1, new GridPosition(0, 0, 0));
            assertTrue(tower.isAugmentTower());
            assertFalse(tower.canReceiveAllyHealing());
            assertFalse(tower.receivesTraitEffects());
            assertFalse(tower.triggersNearbyDeathEffects());
            assertFalse(tower.canChaseTargets());
            assertEquals(!AugmentTowers.isFreeCall(type), tower.canBeSold());
            assertEquals(AugmentTowers.slots(type), TowerCapacity.slotCost(type));
            assertEquals(AugmentTowers.slots(type), TowerCapacity.slotCost(tower));
            assertEquals(BuilderPalette.AUGMENT, TowerVfxService.paletteFor(type));
        }
    }

    @Test void catalogMetadataRejectsAmbiguousAcquisition() {
        assertThrows(IllegalArgumentException.class, () -> new ProductionTowerCatalog.CatalogEntry(
                AugmentTowers.BARRIER_CORE, AugmentTower::new, 1, ProductionTowerCatalog.Availability.AUGMENT, null));
        assertThrows(IllegalArgumentException.class, () -> new ProductionTowerCatalog.CatalogEntry(
                AugmentTowers.BARRIER_CORE, AugmentTower::new, 1, ProductionTowerCatalog.Availability.JOB, "semiontd:barrier_core_call"));
        assertEquals(ProductionTowerCatalog.Availability.JOB,
                new ProductionTowerCatalog.CatalogEntry(AugmentTowers.BARRIER_CORE, AugmentTower::new, 1).availability());
    }

    @Test void paidBlueprintsAndFreeCallsKeepTheirDifferentCaps() {
        assertEquals(2, AugmentTowers.placementLimit(AugmentTowers.CAPACITOR_POST));
        assertEquals(1, AugmentTowers.placementLimit(AugmentTowers.FOLDING_BARRICADE));
        assertTrue(AugmentTowers.isFreeCall(AugmentTowers.STARLIGHT_COCOON));
        assertFalse(AugmentTowers.isFreeCall(AugmentTowers.PULSE_RELAY));
        assertEquals(2, AugmentTowers.slots(AugmentTowers.BARRIER_CORE));
        assertEquals(2, AugmentTowers.slots(AugmentTowers.STARLIGHT_COCOON));
    }
}
