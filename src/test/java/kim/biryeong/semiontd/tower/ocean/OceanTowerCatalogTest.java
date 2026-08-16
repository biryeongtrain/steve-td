package kim.biryeong.semiontd.tower.ocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.OceanTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class OceanTowerCatalogTest {
    private static final double EPSILON = 0.0001;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void resetBalanceAndCatalogs() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(defaults);
        ProductionTowerCatalogs.reloadBuiltIns(defaults);
    }

    @Test
    void oceanJobUsesApprovedNameAndOnlyOceanTowers() {
        OceanTowerJob job = new OceanTowerJob();

        assertEquals("semion-td:ocean", job.id().toString());
        assertEquals("바다 빌더", job.displayName().getString());
        assertTrue(job.canUseTower(null, OceanTowers.T1_WATER));
        assertTrue(job.canUseTower(null, OceanTowers.T3_GIANT_COD));
        assertTrue(job.canUseTower(null, OceanTowers.T3_DOLPHIN));
        assertFalse(job.canUseTower(null, AnimalTowers.T1_PIG_TOWER));
    }

    @Test
    void catalogRegistersSixStarterPathsWithApprovedNamesAndCosts() {
        List<TowerType> starters = ProductionTowerCatalog.all().stream()
                .filter(ProductionTowerCatalog.CatalogEntry::starter)
                .map(ProductionTowerCatalog.CatalogEntry::type)
                .filter(OceanTowers::isOceanTower)
                .toList();

        assertEquals(6, starters.size());
        assertStarter(OceanTowers.T1_WATER, "물 타워", 25);
        assertStarter(OceanTowers.T1_PUFFERFISH, "복어 타워", 40);
        assertStarter(OceanTowers.T1_TROPICAL_FISH, "열대어 타워", 40);
        assertStarter(OceanTowers.T1_SQUID, "오징어 타워", 50);
        assertStarter(OceanTowers.T1_SALMON, "연어 타워", 45);
        assertStarter(OceanTowers.T1_COD, "대구 타워", 45);

        assertUpgrade(OceanTowers.T1_WATER, OceanTowers.T2_SPRING_WATER, "샘물 타워", 60);
        assertUpgrade(OceanTowers.T2_SPRING_WATER, OceanTowers.T3_CURRENT, "해류 타워", 150);
        assertUpgrade(OceanTowers.T1_PUFFERFISH, OceanTowers.T2_GUARDIAN, "가디언 타워", 100);
        assertUpgrade(OceanTowers.T2_GUARDIAN, OceanTowers.T3_ELDER_GUARDIAN, "엘더 가디언 타워", 180);
        assertUpgrade(OceanTowers.T1_TROPICAL_FISH, OceanTowers.T2_LARGE_TROPICAL_FISH, "큰 열대어 타워", 110);
        assertUpgrade(OceanTowers.T2_LARGE_TROPICAL_FISH, OceanTowers.T3_GIANT_TROPICAL_FISH, "거대 열대어 타워", 190);
        assertUpgrade(OceanTowers.T1_SQUID, OceanTowers.T2_GLOW_SQUID, "발광 오징어 타워", 120);
        assertUpgrade(OceanTowers.T2_GLOW_SQUID, OceanTowers.T3_DOLPHIN, "돌고래 타워", 180);
        assertUpgrade(OceanTowers.T1_SALMON, OceanTowers.T2_LARGE_SALMON, "큰 연어 타워", 90);
        assertUpgrade(OceanTowers.T2_LARGE_SALMON, OceanTowers.T3_GIANT_SALMON, "거대 연어 타워", 150);
        assertUpgrade(OceanTowers.T1_COD, OceanTowers.T2_LARGE_COD, "큰 대구 타워", 80);
        assertUpgrade(OceanTowers.T2_LARGE_COD, OceanTowers.T3_GIANT_COD, "거대 대구 타워", 150);
    }

    @Test
    void catalogsCreateWaterAndCombatRuntimeTypes() {
        UUID owner = UUID.nameUUIDFromBytes("ocean-runtime".getBytes());
        GridPosition position = new GridPosition(0, 64, 0);

        assertInstanceOf(OceanWaterTower.class, ProductionTowerCatalog.find(OceanTowers.T1_WATER.id())
                .orElseThrow().create(owner, TeamId.RED, 1, position));
        assertInstanceOf(OceanTower.class, ProductionTowerCatalog.find(OceanTowers.T1_PUFFERFISH.id())
                .orElseThrow().create(owner, TeamId.RED, 1, position));
        assertInstanceOf(OceanTower.class, ProductionTowerCatalog.find(OceanTowers.T1_SQUID.id())
                .orElseThrow().create(owner, TeamId.RED, 1, position));
    }

    @Test
    void waterHasNoCapPersistsThroughUpgradeAndSoftensDamageAboveThreshold() {
        UUID owner = UUID.nameUUIDFromBytes("ocean-water-state".getBytes());
        GridPosition position = new GridPosition(0, 64, 0);
        OceanTower tierOne = new OceanTower(OceanTowers.T1_COD, owner, TeamId.RED, 1, position);

        assertEquals(100.0, tierOne.water(), EPSILON);
        assertEquals(1.0 + 0.75 * Math.sqrt(100.0 / 250.0), tierOne.waterDamageMultiplier(), EPSILON);
        tierOne.addWater(1_000_000.0);
        assertEquals(1_000_100.0, tierOne.water(), EPSILON);

        OceanTower tierTwo = new OceanTower(OceanTowers.T2_LARGE_COD, owner, TeamId.RED, 1, position);
        tierTwo.copyFrom(tierOne, 100);
        assertEquals(tierOne.water(), tierTwo.water(), EPSILON);
        double effectiveWater = 1_000.0 + 1_000.0 * Math.log1p((tierTwo.water() - 1_000.0) / 1_000.0);
        assertEquals(1.0 + 0.9 * Math.sqrt(effectiveWater / 250.0), tierTwo.waterDamageMultiplier(), EPSILON);

        assertTrue(tierTwo.spendWater(tierTwo.water()));
        assertEquals(0.0, tierTwo.water(), EPSILON);
        assertEquals(0.30, tierTwo.waterDamageMultiplier(), EPSILON);
        assertEquals(38, tierTwo.adjustAttackInterval(OceanTowers.T2_LARGE_COD.attackIntervalTicks()));
    }

    @Test
    void liveHighWaterDamageAndSixSourceSupplyUseInflationControls() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> global = new LinkedHashMap<>(abilities.get(OceanTower.CONFIG_ID));
        global.put("initialWater", 100.0);
        global.put("waterScale", 250.0);
        abilities.put(OceanTower.CONFIG_ID, global);
        LinkedHashMap<String, Double> cod = new LinkedHashMap<>(abilities.get(OceanTowers.T3_GIANT_COD.id()));
        cod.put("waterDamageCoefficient", 1.4);
        abilities.put(OceanTowers.T3_GIANT_COD.id(), cod);
        TowerBalanceRuntime.apply(new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities));

        OceanTower tower = new OceanTower(
                OceanTowers.T3_GIANT_COD,
                UUID.nameUUIDFromBytes("ocean-live-high-water".getBytes()),
                TeamId.RED,
                1,
                new GridPosition(0, 64, 0)
        );
        tower.addWater(9_900.0);
        double tenThousandEffective = 1_000.0 + 1_000.0 * Math.log1p(9.0);
        double tenThousandRoot = Math.sqrt(tenThousandEffective / 250.0);
        assertEquals(1.0 + 1.4 * tenThousandRoot, tower.waterDamageMultiplier(), EPSILON);
        assertEquals(1.0 + 2.0 * 1.4 * tenThousandRoot, tower.incomeWaterMultiplier(), EPSILON);

        tower.addWater(90_000.0);
        double hundredThousandEffective = 1_000.0 + 1_000.0 * Math.log1p(99.0);
        double hundredThousandRoot = Math.sqrt(hundredThousandEffective / 250.0);
        assertEquals(1.0 + 1.4 * hundredThousandRoot, tower.waterDamageMultiplier(), EPSILON);
        assertEquals(1.0 + 2.0 * 1.4 * hundredThousandRoot, tower.incomeWaterMultiplier(), EPSILON);

        double sixSourceMultiplier = OceanWaterTower.stackedSupplyMultiplier(6, 0.60);
        assertEquals(2.38336, sixSourceMultiplier * 6.0, EPSILON);
        assertEquals(3.57504, 1.5 * sixSourceMultiplier * 6.0, EPSILON);
        assertEquals(16.68352, 7.0 * sixSourceMultiplier * 6.0, EPSILON);
    }

    @Test
    void oceanInflationControlDefaultsAreConfigurable() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();

        assertEquals(1_000.0, config.ability(OceanTower.CONFIG_ID, "waterSoftCap", -1.0), EPSILON);
        assertEquals(2_500.0, config.ability(OceanTower.CONFIG_ID, "waterSupplyStopThreshold", -1.0), EPSILON);
        assertEquals(0.60, config.ability(OceanTower.CONFIG_ID, "waterSupplyStackDecay", -1.0), EPSILON);
        assertEquals(2.0, config.ability(OceanTower.CONFIG_ID, "incomeCoefficientMultiplier", -1.0), EPSILON);
    }

    @Test
    void oneTierThreeWaterTowerCannotSustainAnyTierThreeBranch() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();
        double supplyPerSecond = config.ability(OceanTowers.T3_CURRENT.id(), "waterPerSupply", -1.0);

        assertEquals(3.2, supplyPerSecond, EPSILON);
        assertTrue(supplyPerSecond < waterPerSecond(config, OceanTowers.T3_ELDER_GUARDIAN, "attackWaterCost", 0.0));
        assertTrue(supplyPerSecond < waterPerSecond(config, OceanTowers.T3_GIANT_TROPICAL_FISH, "abilityWaterCost", 0.0));
        assertTrue(supplyPerSecond < waterPerSecond(config, OceanTowers.T3_DOLPHIN, "abilityWaterCost", 0.0));
        assertTrue(supplyPerSecond < waterPerSecond(config, OceanTowers.T3_GIANT_SALMON, "attackWaterCost", "splashWaterCost"));
        assertTrue(supplyPerSecond < waterPerSecond(config, OceanTowers.T3_GIANT_COD, "attackWaterCost", 0.0));
    }

    @Test
    void tankWaterTransferUsesFiveSecondCooldownAndLiveCaps() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();

        assertEquals(100.0, config.ability(OceanTowers.T1_PUFFERFISH.id(), "transferCooldownTicks", -1.0), EPSILON);
        assertEquals(100.0, config.ability(OceanTowers.T2_GUARDIAN.id(), "transferCooldownTicks", -1.0), EPSILON);
        assertEquals(100.0, config.ability(OceanTowers.T3_ELDER_GUARDIAN.id(), "transferCooldownTicks", -1.0), EPSILON);
        assertEquals(24.0, config.ability(OceanTowers.T1_PUFFERFISH.id(), "transferCap", -1.0), EPSILON);
        assertEquals(50.0, config.ability(OceanTowers.T2_GUARDIAN.id(), "transferCap", -1.0), EPSILON);
        assertEquals(90.0, config.ability(OceanTowers.T3_ELDER_GUARDIAN.id(), "transferCap", -1.0), EPSILON);
    }

    @Test
    void waterTowerMarkerIsAZeroLightWaterSourceWithoutCollision() {
        var marker = OceanWaterTower.waterMarker();

        assertEquals(0, marker.getLightEmission());
        assertEquals(Fluids.WATER, marker.getFluidState().getType());
        assertTrue(marker.getFluidState().isSource());
        assertTrue(marker.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
    }

    @Test
    void combatBranchesUseDistinctEntityTypesAndStayWithinOneBlockVisualSize() {
        Set<String> tankTypes = Set.of(
                OceanTowers.T1_PUFFERFISH.visual().entityTypeId(),
                OceanTowers.T2_GUARDIAN.visual().entityTypeId(),
                OceanTowers.T3_ELDER_GUARDIAN.visual().entityTypeId()
        );
        Set<String> supportTypes = Set.of(OceanTowers.T1_TROPICAL_FISH.visual().entityTypeId());
        Set<String> healerTypes = Set.of(
                OceanTowers.T1_SQUID.visual().entityTypeId(),
                OceanTowers.T2_GLOW_SQUID.visual().entityTypeId(),
                OceanTowers.T3_DOLPHIN.visual().entityTypeId()
        );
        Set<String> splashTypes = Set.of(OceanTowers.T1_SALMON.visual().entityTypeId());
        Set<String> hunterTypes = Set.of(OceanTowers.T1_COD.visual().entityTypeId());

        assertTrue(disjoint(tankTypes, supportTypes, healerTypes, splashTypes, hunterTypes));
        assertEquals(0.7, OceanTowers.T1_TROPICAL_FISH.visual().scale(), EPSILON);
        assertEquals(1.2, OceanTowers.T3_GIANT_TROPICAL_FISH.visual().scale(), EPSILON);
        assertEquals(0.75, OceanTowers.T1_SQUID.visual().scale(), EPSILON);
        assertEquals(0.9, OceanTowers.T2_GLOW_SQUID.visual().scale(), EPSILON);
        assertEquals(1.0, OceanTowers.T3_DOLPHIN.visual().scale(), EPSILON);
        assertEquals(0.75, OceanTowers.T1_SALMON.visual().scale(), EPSILON);
        assertEquals(1.2, OceanTowers.T3_GIANT_SALMON.visual().scale(), EPSILON);
        assertEquals(0.7, OceanTowers.T1_COD.visual().scale(), EPSILON);
        assertEquals(1.2, OceanTowers.T3_GIANT_COD.visual().scale(), EPSILON);
        assertEquals(0.9, OceanTowers.T2_GUARDIAN.visual().scale(), EPSILON);
        assertEquals(0.5, OceanTowers.T3_ELDER_GUARDIAN.visual().scale(), EPSILON);

        List.of(
                OceanTowers.T1_PUFFERFISH, OceanTowers.T2_GUARDIAN, OceanTowers.T3_ELDER_GUARDIAN,
                OceanTowers.T1_TROPICAL_FISH, OceanTowers.T2_LARGE_TROPICAL_FISH, OceanTowers.T3_GIANT_TROPICAL_FISH,
                OceanTowers.T1_SQUID, OceanTowers.T2_GLOW_SQUID, OceanTowers.T3_DOLPHIN,
                OceanTowers.T1_SALMON, OceanTowers.T2_LARGE_SALMON, OceanTowers.T3_GIANT_SALMON,
                OceanTowers.T1_COD, OceanTowers.T2_LARGE_COD, OceanTowers.T3_GIANT_COD
        ).forEach(OceanTowerCatalogTest::assertOneBlockVisualSize);
    }

    private static void assertStarter(TowerType type, String displayName, long cost) {
        var entry = ProductionTowerCatalog.find(type.id()).orElseThrow();
        assertTrue(entry.starter());
        assertEquals(displayName, entry.type().displayName());
        assertEquals(cost, entry.type().mineralCost());
    }

    private static void assertUpgrade(TowerType from, TowerType to, String displayName, long cost) {
        var upgrade = ProductionTowerCatalog.upgrade(from, to.id()).orElseThrow();
        assertEquals(displayName, upgrade.displayName());
        assertEquals(cost, upgrade.mineralCost());
    }

    private static void assertOneBlockVisualSize(TowerType type) {
        ResourceLocation entityTypeId = ResourceLocation.parse(type.visual().entityTypeId());
        var dimensions = BuiltInRegistries.ENTITY_TYPE.getOptional(entityTypeId).orElseThrow().getDimensions();
        double scale = type.visual().scale();
        assertTrue(dimensions.width() * scale <= 1.0 + EPSILON, type.id() + " visual width exceeds one block.");
        assertTrue(dimensions.height() * scale <= 1.0 + EPSILON, type.id() + " visual height exceeds one block.");
    }

    private static double waterPerSecond(TowerBalanceConfig config, TowerType type, String costKey, double extraCost) {
        double cost = config.ability(type.id(), costKey, -1.0) + extraCost;
        return cost * 20.0 / type.attackIntervalTicks();
    }

    private static double waterPerSecond(TowerBalanceConfig config, TowerType type, String costKey, String extraCostKey) {
        double extraCost = config.ability(type.id(), extraCostKey, -1.0);
        return waterPerSecond(config, type, costKey, extraCost);
    }

    @SafeVarargs
    private static boolean disjoint(Set<String>... sets) {
        for (int left = 0; left < sets.length; left++) {
            for (int right = left + 1; right < sets.length; right++) {
                if (!java.util.Collections.disjoint(sets[left], sets[right])) {
                    return false;
                }
            }
        }
        return true;
    }
}
