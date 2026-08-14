package kim.biryeong.semiontd.tower.atlantis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Test
    void tunedDefaultsMatchTheBundledAtlantisBalance() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertDoesNotThrow(defaults::validateForRuntime);
        assertEquals(13.0, defaults.towers().get(AtlantisTowers.DOLPHIN_T1.id()).damage());
        assertEquals(24.0, defaults.towers().get(AtlantisTowers.DOLPHIN_T2.id()).damage());
        assertEquals(40.0, defaults.towers().get(AtlantisTowers.DOLPHIN_T3.id()).damage());
        assertEquals(16, defaults.towers().get(AtlantisTowers.DOLPHIN_T1.id()).attackIntervalTicks());
        assertEquals(14, defaults.towers().get(AtlantisTowers.DOLPHIN_T2.id()).attackIntervalTicks());
        assertEquals(12, defaults.towers().get(AtlantisTowers.DOLPHIN_T3.id()).attackIntervalTicks());

        assertEquals(0.16, defaults.ability(AtlantisBalance.CONFIG_ID, "waterPressureDamageRatio", -1.0));
        assertEquals(2.5, defaults.ability(AtlantisBalance.CONFIG_ID, "waterPressureDamageCap", -1.0));
        assertEquals(3.0, defaults.ability(AtlantisBalance.CONFIG_ID, "waterPressureRadius", -1.0));
        assertEquals(2.0, defaults.ability(AtlantisBalance.CONFIG_ID, "zoneStackMultiplier", -1.0));
        assertEquals(40, defaults.abilityTicks(AtlantisBalance.CONFIG_ID, "zoneVfxIntervalTicks", -1));
        assertEquals(3, defaults.abilityInt(AtlantisBalance.CONFIG_ID, "maxChainDepth", -1));

        assertEquals(0.25, defaults.ability(AtlantisTowers.TURTLE_T3.id(), "zoneAllyDamageReduction", -1.0));
        assertEquals(32.0, defaults.ability(AtlantisTowers.AXOLOTL_T3.id(), "regenAmount", -1.0));
        assertEquals(0.15, defaults.ability(AtlantisTowers.AXOLOTL_T3.id(), "attackSpeedBonus", -1.0));
        assertEquals(1, defaults.abilityInt(AtlantisTowers.AXOLOTL_T3.id(), "stackBonus", -1));
        assertEquals(4, defaults.abilityInt(AtlantisTowers.CONDUIT_T3.id(), "maxStackBonus", -1));
        assertEquals(0.06, defaults.ability(AtlantisTowers.CONDUIT_T3.id(), "waterPressureRatioBonus", -1.0));
    }

    @Test
    void tierThreeEffectiveDpsIncludesTheCarrierAndSplashTargets() {
        assertEquals(106.7, effectiveDps(1, false, false), 0.2);
        assertEquals(186.7, effectiveDps(3, false, false), 0.2);
        assertEquals(266.7, effectiveDps(5, false, false), 0.2);
        assertEquals(146.7, effectiveDps(1, true, false), 0.2);
        assertEquals(306.7, effectiveDps(3, true, false), 0.2);
        assertEquals(466.7, effectiveDps(5, true, false), 0.2);
        assertEquals(163.6, effectiveDps(1, true, true), 0.2);
        assertEquals(345.5, effectiveDps(3, true, true), 0.2);
        assertEquals(527.3, effectiveDps(5, true, true), 0.2);
    }

    @Test
    void atlantisConfigRejectsInvalidRatiosTicksAndCaps() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertAtlantisConfigRejected(defaults, AtlantisBalance.CONFIG_ID, "maxSlow", 1.01);
        assertAtlantisConfigRejected(defaults, AtlantisBalance.CONFIG_ID, "maxChainDepth", 2.5);
        assertAtlantisConfigRejected(defaults, AtlantisTowers.AXOLOTL_T3.id(), "stackBonus", -1.0);
        assertAtlantisConfigRejected(defaults, AtlantisBalance.CONFIG_ID, "zoneVfxIntervalTicks", 5.0);
        assertAtlantisConfigRejected(defaults, AtlantisTowers.TURTLE_T3.id(),
                "zoneAllyDamageReduction", 0.36);
    }

    @Test
    void missingDefaultsBackfillTheZoneVfxIntervalWithoutOverwritingOverrides() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceConfig partial = new TowerBalanceConfig(
                Map.of(),
                Map.of(),
                Map.of(AtlantisBalance.CONFIG_ID, Map.of("zoneScanIntervalTicks", 20.0))
        );
        TowerBalanceConfig merged = partial.withMissingDefaults(defaults);
        assertEquals(20, merged.abilityTicks(AtlantisBalance.CONFIG_ID, "zoneScanIntervalTicks", -1));
        assertEquals(40, merged.abilityTicks(AtlantisBalance.CONFIG_ID, "zoneVfxIntervalTicks", -1));
        assertDoesNotThrow(merged::validateForRuntime);
    }

    private static double effectiveDps(int targetCount, boolean insideZone, boolean fullSupport) {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();
        TowerType dolphin = TowerBalanceRuntime.resolve(AtlantisTowers.DOLPHIN_T3);
        int stackPerHit = config.abilityInt(dolphin.id(), "stackPerHit", 1)
                + (fullSupport ? config.abilityInt(AtlantisTowers.AXOLOTL_T3.id(), "stackBonus", 0) : 0);
        if (insideZone) {
            stackPerHit = (int) Math.round(stackPerHit
                    * config.ability(AtlantisBalance.CONFIG_ID, "zoneStackMultiplier", 1.0));
        }
        int maxStacks = config.abilityInt(AtlantisBalance.CONFIG_ID, "maxPressureStacks", 10)
                + (fullSupport ? config.abilityInt(AtlantisTowers.CONDUIT_T3.id(), "maxStackBonus", 0) : 0);
        int hitsPerBurst = (int) Math.ceil((double) maxStacks / stackPerHit);
        double ratio = config.ability(AtlantisBalance.CONFIG_ID, "waterPressureDamageRatio", 0.0)
                + config.ability(dolphin.id(), "waterPressureRatioBonus", 0.0);
        if (fullSupport) {
            ratio += config.ability(AtlantisTowers.AXOLOTL_T3.id(), "waterPressureRatioBonus", 0.0)
                    + config.ability(AtlantisTowers.CONDUIT_T3.id(), "waterPressureRatioBonus", 0.0);
        }
        double burst = dolphin.damage() * Math.min(
                maxStacks * ratio,
                config.ability(AtlantisBalance.CONFIG_ID, "waterPressureDamageCap", 1.0)
        );
        double attackSpeed = fullSupport
                ? config.ability(AtlantisTowers.AXOLOTL_T3.id(), "attackSpeedBonus", 0.0)
                : 0.0;
        int interval = (int) Math.ceil(dolphin.attackIntervalTicks() / (1.0 + attackSpeed));
        return dolphin.damage() * 20.0 / interval
                + burst * targetCount * 20.0 / (hitsPerBurst * interval);
    }

    private static void assertAtlantisConfigRejected(
            TowerBalanceConfig defaults,
            String configId,
            String ability,
            double value
    ) {
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>();
        defaults.abilities().forEach((id, values) -> abilities.put(id, new LinkedHashMap<>(values)));
        abilities.get(configId).put(ability, value);
        TowerBalanceConfig changed = new TowerBalanceConfig(
                defaults.towers(), defaults.upgradeCosts(), abilities,
                defaults.illusionCloneQueue(), defaults.villagerAdv(), defaults.schemaVersion()
        );
        assertThrows(IllegalArgumentException.class, changed::validateForRuntime);
    }

    private static void assertUpgrade(TowerType from, TowerType to, long expectedCost) {
        var option = ProductionTowerCatalog.upgrade(from, to.id()).orElseThrow(
                () -> new AssertionError("missing upgrade edge " + from.id() + " -> " + to.id()));
        assertEquals(expectedCost, option.mineralCost(),
                "upgrade " + from.id() + " -> " + to.id() + " must use the directed cost");
    }
}
