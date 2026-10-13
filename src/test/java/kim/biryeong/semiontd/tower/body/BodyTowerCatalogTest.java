package kim.biryeong.semiontd.tower.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.BodyTowerJob;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BodyTowerCatalogTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void reload() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void catalogContainsFifteenTowersWithFiveStarters() {
        List<ProductionTowerCatalog.CatalogEntry> entries = ProductionTowerCatalog.all().stream()
                .filter(entry -> BodyTowers.isBodyTower(entry.type()))
                .toList();

        assertEquals(15, entries.size());
        assertEquals(5, entries.stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count());
    }

    @Test
    void everyTowerBelongsOnlyToBodyBuilderAndUsesBodyRuntime() {
        var body = JobRegistry.find(BodyTowerJob.ID).orElseThrow();
        GridPosition position = new GridPosition(0, 64, 0);
        for (TowerType type : BodyTowers.all()) {
            assertTrue(body.includesTowerInCatalog(type));
            assertEquals(1, JobRegistry.all().stream()
                    .filter(job -> job.includesTowerInCatalog(type))
                    .count());
            var entry = ProductionTowerCatalog.find(type.id()).orElseThrow();
            assertInstanceOf(BodyTower.class, entry.factory().create(
                    entry.type(), UUID.randomUUID(), TeamId.RED, 1, position, position
            ));
        }
    }

    @Test
    void everyTowerUsesItsRequestedBlockVisualAndTierScale() {
        assertVisual(BodyTowers.HEART_T1, Blocks.CREAKING_HEART, 0.95);
        assertVisual(BodyTowers.HEART_T2, Blocks.REDSTONE_BLOCK, 0.95);
        assertVisual(BodyTowers.HEART_T3, Blocks.FIRE_CORAL_BLOCK, 0.95);

        assertVisual(BodyTowers.BRAIN_T1, Blocks.CONCRETE_POWDER.pink(), 0.95);
        assertVisual(BodyTowers.BRAIN_T2, Blocks.WOOL.pink(), 0.95);
        assertVisual(BodyTowers.BRAIN_T3, Blocks.BRAIN_CORAL_BLOCK, 0.95);

        assertVisual(BodyTowers.SKIN_T1, Blocks.OAK_WOOD, 0.95);
        assertVisual(BodyTowers.SKIN_T2, Blocks.SPRUCE_WOOD, 0.95);
        assertVisual(BodyTowers.SKIN_T3, Blocks.ANCIENT_DEBRIS, 0.95);

        assertVisual(BodyTowers.EYE_T1, Blocks.CHORUS_PLANT, 1.0);
        assertVisual(BodyTowers.EYE_T2, Blocks.CHORUS_FLOWER, 1.0);
        assertVisual(BodyTowers.EYE_T3, Blocks.PEARLESCENT_FROGLIGHT, 0.95);

        assertVisual(BodyTowers.GENITAL_T1, Blocks.LEVER, 1.1);
        assertVisual(BodyTowers.GENITAL_T2, Blocks.LIGHTNING_ROD.weathering().unaffected(), 1.1);
        assertVisual(BodyTowers.GENITAL_T3, Blocks.END_ROD, 1.1);
    }

    @Test
    void eachOrganHasAThreeTierUpgradeChain() {
        assertChain(BodyTowers.HEART_T1, BodyTowers.HEART_T2, BodyTowers.HEART_T3);
        assertChain(BodyTowers.BRAIN_T1, BodyTowers.BRAIN_T2, BodyTowers.BRAIN_T3);
        assertChain(BodyTowers.SKIN_T1, BodyTowers.SKIN_T2, BodyTowers.SKIN_T3);
        assertChain(BodyTowers.EYE_T1, BodyTowers.EYE_T2, BodyTowers.EYE_T3);
        assertChain(BodyTowers.GENITAL_T1, BodyTowers.GENITAL_T2, BodyTowers.GENITAL_T3);
    }

    @Test
    void eachTierOwnsItsDescriptionSeparately() {
        assertIndependentDescriptions(BodyTowers.HEART_T1, BodyTowers.HEART_T2, BodyTowers.HEART_T3);
        assertIndependentDescriptions(BodyTowers.BRAIN_T1, BodyTowers.BRAIN_T2, BodyTowers.BRAIN_T3);
        assertIndependentDescriptions(BodyTowers.SKIN_T1, BodyTowers.SKIN_T2, BodyTowers.SKIN_T3);
        assertIndependentDescriptions(BodyTowers.EYE_T1, BodyTowers.EYE_T2, BodyTowers.EYE_T3);
        assertIndependentDescriptions(BodyTowers.GENITAL_T1, BodyTowers.GENITAL_T2, BodyTowers.GENITAL_T3);
    }

    @Test
    void abilityDescriptionsRenderEachTiersCurrentValues() {
        assertDescriptionContains(BodyTowers.BRAIN_T1, "2.5블록", "10%", "8%");
        assertDescriptionContains(BodyTowers.BRAIN_T2, "3.5블록", "15%", "12%");
        assertDescriptionContains(BodyTowers.BRAIN_T3, "4.5블록", "20%", "16%");

        assertDescriptionContains(BodyTowers.SKIN_T1, "2.5블록");
        assertTrue(TowerBalanceRuntime.resolve(BodyTowers.SKIN_T1).description().stream()
                .noneMatch(line -> line.contains("T2부터") || line.contains("받는 피해")));
        assertDescriptionContains(BodyTowers.SKIN_T2, "3블록", "6%");
        assertDescriptionContains(BodyTowers.SKIN_T3, "3.5블록", "11%");

        assertDescriptionContains(BodyTowers.GENITAL_T1, "마법 피해 5");
        assertDescriptionContains(BodyTowers.GENITAL_T2, "마법 피해 14");
        assertDescriptionContains(BodyTowers.GENITAL_T3, "마법 피해 27");
    }

    @Test
    void bundledBalanceContainsBodyStatsAbilitiesAndUpgradeCosts() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();
        for (TowerType type : BodyTowers.all()) {
            assertTrue(config.towers().containsKey(type.id()), type.id());
        }
        assertEquals(110, TowerBalanceRuntime.upgradeCost(
                BodyTowers.BRAIN_T1, BodyTowers.BRAIN_T2.id(), -1
        ));
        assertEquals(4.5, config.ability(BodyTowers.BRAIN_T3.id(), "splashRadius", -1.0));
        assertEquals(2.0, config.ability(BodyTowers.GENITAL_T3.id(), "extraTargets", -1.0));
        assertEquals(24, config.statsFor(BodyTowers.HEART_T1).attackIntervalTicks());
        assertEquals(24, config.statsFor(BodyTowers.HEART_T2).attackIntervalTicks());
        assertEquals(18, config.statsFor(BodyTowers.HEART_T3).attackIntervalTicks());
        assertEquals(60.0, config.ability(BodyTowers.HEART_T2.id(), "maxDeathStacks", -1.0));
        assertEquals(120.0, config.ability(BodyTowers.HEART_T3.id(), "maxDeathStacks", -1.0));
        assertEquals(15.0, config.ability(BodyTowers.HEART_T3.id(), "stacksPerIntervalReduction", -1.0));
        assertEquals(BodyTowers.EYE_T1.range(), config.statsFor(BodyTowers.EYE_T1).range());
        assertEquals(BodyTowers.EYE_T2.range(), config.statsFor(BodyTowers.EYE_T2).range());
    }

    @Test
    void invalidBodyAbilitySemanticsAreRejected() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertInvalidAbility(defaults, BodyTowers.BRAIN_T1.id(), "damageTaken", 1.1);
        assertInvalidAbility(defaults, BodyTowers.BRAIN_T1.id(), "debuffTicks", 1.5);
        assertInvalidAbility(defaults, BodyTowers.SKIN_T2.id(), "damageReductionPerStack", 1.1);
        assertInvalidAbility(defaults, BodyTowers.EYE_T1.id(), "lineWidth", 0.0);
        assertInvalidAbility(defaults, BodyTowers.GENITAL_T2.id(), "extraTargets", 1.5);
        assertInvalidAbility(defaults, BodyTowers.GENITAL_T2.id(), "slow", 1.1);
    }

    @Test
    void heartDeathStacksPersistThroughRoundsAndUpgradesAndRespectCaps() {
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        GridPosition position = new GridPosition(0, 64, 0);
        BodyTower tierOne = bodyTower(BodyTowers.HEART_T1, owner, position);
        BodyTower tierTwo = bodyTower(BodyTowers.HEART_T2, owner, position);
        BodyTower ownSkin = bodyTower(BodyTowers.SKIN_T1, owner, position);
        BodyTower otherSkin = bodyTower(BodyTowers.SKIN_T1, otherOwner, position);

        for (int death = 0; death < 15; death++) {
            tierOne.onNearbyTowerDeath(null, ownSkin);
            tierTwo.onNearbyTowerDeath(null, otherSkin);
        }
        assertEquals(0, tierOne.heartDeathStacks());
        assertEquals(0, tierTwo.heartDeathStacks());
        assertEquals(24, tierOne.adjustAttackInterval(24));
        assertEquals(24, tierTwo.adjustAttackInterval(24));

        for (int death = 0; death < 60; death++) {
            tierTwo.onNearbyTowerDeath(null, ownSkin);
        }
        assertEquals(60, tierTwo.heartDeathStacks());
        assertEquals(20, tierTwo.adjustAttackInterval(24));
        tierTwo.onNearbyTowerDeath(null, ownSkin);
        assertEquals(60, tierTwo.heartDeathStacks());
        tierTwo.resetForRound(null);
        assertEquals(60, tierTwo.heartDeathStacks());

        BodyTower tierThree = bodyTower(BodyTowers.HEART_T3, owner, position);
        tierThree.copyFrom(tierTwo, 0L);
        assertEquals(60, tierThree.heartDeathStacks());
        assertEquals(14, tierThree.adjustAttackInterval(18));
        for (int death = 0; death < 60; death++) {
            tierThree.onNearbyTowerDeath(null, ownSkin);
        }
        assertEquals(120, tierThree.heartDeathStacks());
        assertEquals(10, tierThree.adjustAttackInterval(18));
        assertTrue(tierThree.runtimeDetailLines().getFirst().contains("120/120"));
        assertTrue(tierThree.runtimeDetailLines().getFirst().contains("10틱"));
    }

    private static BodyTower bodyTower(TowerType type, UUID owner, GridPosition position) {
        return new BodyTower(TowerBalanceRuntime.resolve(type), owner, TeamId.RED, 1, position, position);
    }

    private static void assertInvalidAbility(
            TowerBalanceConfig defaults,
            String configId,
            String key,
            double value
    ) {
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> values = new LinkedHashMap<>(abilities.get(configId));
        values.put(key, value);
        abilities.put(configId, values);
        TowerBalanceConfig invalid = new TowerBalanceConfig(
                defaults.towers(), defaults.upgradeCosts(), abilities,
                defaults.illusionCloneQueue(), defaults.villagerAdv(), defaults.schemaVersion()
        );
        assertThrows(IllegalArgumentException.class, invalid::validateForRuntime);
    }

    private static void assertChain(TowerType t1, TowerType t2, TowerType t3) {
        assertTrue(ProductionTowerCatalog.upgrade(t1, t2.id()).isPresent());
        assertTrue(ProductionTowerCatalog.upgrade(t2, t3.id()).isPresent());
        assertTrue(ProductionTowerCatalog.upgrades(t3).isEmpty());
    }

    private static void assertIndependentDescriptions(TowerType t1, TowerType t2, TowerType t3) {
        assertTrue(!t1.description().isEmpty(), t1.id());
        assertTrue(!t2.description().isEmpty(), t2.id());
        assertTrue(!t3.description().isEmpty(), t3.id());
        assertNotSame(t1.description(), t2.description());
        assertNotSame(t1.description(), t3.description());
        assertNotSame(t2.description(), t3.description());
    }

    private static void assertDescriptionContains(TowerType type, String... expectedValues) {
        String description = String.join("\n", TowerBalanceRuntime.resolve(type).description());
        assertTrue(!description.contains("{"), type.id() + " left an unresolved placeholder: " + description);
        for (String expectedValue : expectedValues) {
            assertTrue(description.contains(expectedValue), type.id() + " did not contain " + expectedValue);
        }
    }

    private static void assertVisual(TowerType type, Block block, double scale) {
        assertTrue(BlockDisplayVisual.matches(type.visual()), type.id());
        assertEquals(block.defaultBlockState(), BlockDisplayVisual.blockState(type.visual()), type.id());
        assertEquals(scale, type.visual().scale(), 0.0001, type.id());
    }
}
