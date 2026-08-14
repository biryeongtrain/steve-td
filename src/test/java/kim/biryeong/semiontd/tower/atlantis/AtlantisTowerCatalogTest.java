package kim.biryeong.semiontd.tower.atlantis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.AtlantisTowerJob;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AtlantisTowerCatalogTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("atlantis-owner".getBytes());

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void reload() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @AfterEach
    void resetState() {
        AtlantisStates.clearAll();
        AtlantisPressure.clearAll();
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void catalogContainsTwelveTowersWithFourStarters() {
        List<ProductionTowerCatalog.CatalogEntry> entries = ProductionTowerCatalog.all().stream()
                .filter(entry -> AtlantisTowers.isAtlantisTower(entry.type()))
                .toList();

        assertEquals(12, entries.size(), "Atlantis registers four families of three tiers");
        assertEquals(4, entries.stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count(),
                "only tier one entries are starters");
    }

    @Test
    void everyTowerBelongsToTheAtlantisBuilderOnly() {
        var atlantis = JobRegistry.find(AtlantisTowerJob.ID).orElseThrow();
        for (TowerType type : AtlantisTowers.all()) {
            assertTrue(atlantis.includesTowerInCatalog(type), type.id() + " must be owned by the Atlantis builder");
            long owners = JobRegistry.all().stream()
                    .filter(job -> job.includesTowerInCatalog(type))
                    .count();
            assertEquals(1, owners, type.id() + " must have exactly one owning builder");
        }
    }

    @Test
    void upgradeGraphUsesDirectedCostsRatherThanPlacementPrice() {
        assertUpgrade(AtlantisTowers.TURTLE_T1, AtlantisTowers.TURTLE_T2, 115);
        assertUpgrade(AtlantisTowers.TURTLE_T2, AtlantisTowers.TURTLE_T3, 240);
        assertUpgrade(AtlantisTowers.DOLPHIN_T1, AtlantisTowers.DOLPHIN_T2, 120);
        assertUpgrade(AtlantisTowers.DOLPHIN_T2, AtlantisTowers.DOLPHIN_T3, 250);
        assertUpgrade(AtlantisTowers.AXOLOTL_T1, AtlantisTowers.AXOLOTL_T2, 95);
        assertUpgrade(AtlantisTowers.AXOLOTL_T2, AtlantisTowers.AXOLOTL_T3, 200);
        assertUpgrade(AtlantisTowers.CONDUIT_T1, AtlantisTowers.CONDUIT_T2, 105);
        assertUpgrade(AtlantisTowers.CONDUIT_T2, AtlantisTowers.CONDUIT_T3, 215);
    }

    @Test
    void factoriesCreateAtlantisRuntimeTowers() {
        for (TowerType type : AtlantisTowers.all()) {
            var entry = ProductionTowerCatalog.find(type.id()).orElseThrow();
            var tower = entry.create(OWNER, TeamId.RED, 1, new GridPosition(0, 80, 0));
            assertInstanceOf(AtlantisTower.class, tower, type.id() + " must use the Atlantis runtime class");
            assertEquals(AtlantisTowers.roleOf(type), ((AtlantisTower) tower).role());
        }
    }

    @Test
    void resolvedDescriptionsLeaveNoPlaceholders() {
        for (TowerType type : AtlantisTowers.all()) {
            TowerType resolved = TowerBalanceRuntime.resolve(type);
            for (String line : resolved.description()) {
                assertFalse(line.contains("{ability."), type.id() + " leaves an unresolved ability placeholder: " + line);
                assertFalse(line.contains("{stat."), type.id() + " leaves an unresolved stat placeholder: " + line);
            }
        }
    }

    @Test
    void baseDamageStaysUnderTheHouseCeiling() {
        for (TowerType type : AtlantisTowers.all()) {
            TowerType resolved = TowerBalanceRuntime.resolve(type);
            assertTrue(resolved.damage() <= 50.0,
                    type.id() + " base damage must stay at or below 50, was " + resolved.damage());
        }
    }

    @Test
    void turtleZoneCapacityGrowsWithTier() {
        assertEquals(1, AtlantisStates.zoneCapacity(AtlantisTowers.TURTLE_T1));
        assertEquals(2, AtlantisStates.zoneCapacity(AtlantisTowers.TURTLE_T2));
        assertEquals(3, AtlantisStates.zoneCapacity(AtlantisTowers.TURTLE_T3));
        assertEquals(0, AtlantisStates.zoneCapacity(AtlantisTowers.DOLPHIN_T1),
                "only turtles contribute zone capacity");
    }

    private static void assertUpgrade(TowerType from, TowerType to, long expectedCost) {
        var option = ProductionTowerCatalog.upgrade(from, to.id()).orElseThrow(
                () -> new AssertionError("missing upgrade edge " + from.id() + " -> " + to.id()));
        assertEquals(expectedCost, option.mineralCost(),
                "upgrade " + from.id() + " -> " + to.id() + " must use the directed cost");
    }
}
