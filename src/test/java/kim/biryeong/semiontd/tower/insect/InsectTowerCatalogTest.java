package kim.biryeong.semiontd.tower.insect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.InsectTowerJob;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerCapacity;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

final class InsectTowerCatalogTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("insect-owner".getBytes());

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void resetCatalog() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void catalogRegistersFourStartersAndSixUpgradeEdges() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());

        var entries = ProductionTowerCatalog.all().stream()
                .filter(entry -> InsectTowers.isInsectTower(entry.type()))
                .toList();
        assertEquals(10, entries.size());
        assertEquals(4, entries.stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count());
        assertTrue(JobRegistry.find(InsectTowerJob.ID).isPresent());
        assertInstanceOf(InsectUnitTower.class, create(InsectTowers.SILVERFISH));
        assertInstanceOf(InsectSpawnerTower.class, create(InsectTowers.SPAWNER));
        assertEquals(75, ProductionTowerCatalog.upgrades(InsectTowers.SILVERFISH).getFirst().mineralCost());
        assertEquals(140, ProductionTowerCatalog.upgrades(InsectTowers.ENDERMITE).getFirst().mineralCost());
        assertEquals(90, ProductionTowerCatalog.upgrades(InsectTowers.CAVE_SPIDER).getFirst().mineralCost());
        assertEquals(160, ProductionTowerCatalog.upgrades(InsectTowers.SPIDER).getFirst().mineralCost());
        assertEquals(90, ProductionTowerCatalog.upgrades(InsectTowers.BEE).getFirst().mineralCost());
        assertEquals(170, ProductionTowerCatalog.upgrades(InsectTowers.ENHANCED_BEE).getFirst().mineralCost());
        assertTrue(ProductionTowerCatalog.upgrades(InsectTowers.SPAWNER).isEmpty());
        assertEquals(4.0, ProductionTowerCatalog.find(InsectTowers.CAVE_SPIDER.id()).orElseThrow().type().damage());
        assertEquals(150.0, ProductionTowerCatalog.find(InsectTowers.CAVE_SPIDER.id()).orElseThrow().type().maxHealth());
        assertEquals(7.0, ProductionTowerCatalog.find(InsectTowers.SPIDER.id()).orElseThrow().type().damage());
        assertEquals(300.0, ProductionTowerCatalog.find(InsectTowers.SPIDER.id()).orElseThrow().type().maxHealth());
        assertEquals(12.0, ProductionTowerCatalog.find(InsectTowers.ENHANCED_SPIDER.id()).orElseThrow().type().damage());
        assertEquals(540.0, ProductionTowerCatalog.find(InsectTowers.ENHANCED_SPIDER.id()).orElseThrow().type().maxHealth());
        assertEquals(7.0, ProductionTowerCatalog.find(InsectTowers.BEE.id()).orElseThrow().type().damage());
        assertEquals(12.0, ProductionTowerCatalog.find(InsectTowers.ENHANCED_BEE.id()).orElseThrow().type().damage());
        assertEquals(21.0, ProductionTowerCatalog.find(InsectTowers.QUEEN_BEE.id()).orElseThrow().type().damage());
    }

    @Test
    void firstWaveBoostsTierOneAndBlocksEarlyUpgrade() {
        InsectUnitTower unit = unit(InsectTowers.SILVERFISH, 0);
        unit.recordPlacementEconomy(30, 1);
        unit.onPlaced(null);
        assertFalse(unit.waveStartedAfterPlacement());
        assertTrue(unit.freshPowerActive());
        assertFalse(unit.freshPowerPending());
        assertEquals(157.5, unit.currentMaxHealth(), 0.0001);
        assertEquals(12.25, unit.modifyAttackDamage(null, null, 7.0), 0.0001);
        assertEquals(1.2, unit.visual().scale(), 0.0001);
        assertFalse(unit.meetsUpgradeRequirements(null, null));

        unit.markWaveStarted(1);
        unit.onWaveStarted(null, 1);
        assertTrue(unit.freshPowerActive());
        assertFalse(unit.freshPowerPending());
        assertEquals(157.5, unit.currentMaxHealth(), 0.0001);
        assertEquals(12.25, unit.modifyAttackDamage(null, null, 7.0), 0.0001);
        assertTrue(unit.meetsUpgradeRequirements(null, null));

        unit.resetForRound(null);
        assertFalse(unit.freshPowerActive());
        assertEquals(90.0, unit.currentMaxHealth(), 0.0001);
        assertEquals(7.0, unit.modifyAttackDamage(null, null, 7.0), 0.0001);
        assertEquals(1.0, unit.visual().scale(), 0.0001);
    }

    @Test
    void revivalUsesFourSevenThenTenSecondsAndCancelsWhenSpawnerDies() {
        PlayerLane lane = testLane();
        ProductionTower spawner = new ProductionTower(
                InsectTowers.SPAWNER, OWNER, TeamId.RED, 1, position(0));
        InsectUnitTower unit = unit(InsectTowers.SILVERFISH, 2);
        lane.addTower(spawner);
        lane.addTower(unit);
        unit.recordPlacementEconomy(30, 1);
        unit.markWaveStarted(1);
        unit.onWaveStarted(lane, 1);

        unit.syncHealth(0.0);
        assertFalse(unit.isDestroyed(lane));
        assertEquals(1, unit.deathsThisRound());
        assertEquals(80, unit.reviveTicksRemaining());
        for (int tick = 0; tick < 80; tick++) {
            unit.tick(lane);
        }
        assertEquals(unit.currentMaxHealth(), unit.health(), 0.0001);

        unit.syncHealth(0.0);
        assertFalse(unit.isDestroyed(lane));
        assertEquals(140, unit.reviveTicksRemaining());
        for (int tick = 0; tick < 140; tick++) {
            unit.tick(lane);
        }
        unit.syncHealth(0.0);
        assertFalse(unit.isDestroyed(lane));
        assertEquals(200, unit.reviveTicksRemaining());
        spawner.syncHealth(0.0);
        assertTrue(unit.isDestroyed(lane));
    }

    @Test
    void spiderReductionMultipliesWithDeathVulnerability() {
        PlayerLane lane = testLane();
        ProductionTower spawner = new ProductionTower(
                InsectTowers.SPAWNER, OWNER, TeamId.RED, 1, position(0));
        InsectUnitTower spider = unit(InsectTowers.CAVE_SPIDER, 2);
        lane.addTower(spawner);
        lane.addTower(spider);
        spider.markWaveStarted(1);
        spider.onWaveStarted(lane, 1);
        assertEquals(92.0, spider.modifyIncomingDamage(null, null, 100.0), 0.0001);

        spider.syncHealth(0.0);
        assertFalse(spider.isDestroyed(lane));
        assertEquals(110.4, spider.modifyIncomingDamage(null, null, 100.0), 0.0001);
        spider.resetForRound(lane);
        assertEquals(92.0, spider.modifyIncomingDamage(null, null, 100.0), 0.0001);
    }

    @Test
    void outsideSpawnerRangeMakesDeathPermanent() {
        PlayerLane lane = testLane();
        lane.addTower(new ProductionTower(InsectTowers.SPAWNER, OWNER, TeamId.RED, 1, position(0)));
        InsectUnitTower unit = unit(InsectTowers.BEE, 8);
        lane.addTower(unit);
        unit.markWaveStarted(1);
        unit.onWaveStarted(lane, 1);
        unit.syncHealth(0.0);
        assertTrue(unit.isDestroyed(lane));
    }

    @Test
    void defaultsMergeAndRejectInvalidReduction() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertTrue(InsectTowers.all().stream().allMatch(type -> defaults.towers().containsKey(type.id())));
        assertEquals(1.75, defaults.ability(InsectBalance.GLOBAL_ID, "freshPowerMultiplier", -1), 0.0001);
        assertEquals(1.2, defaults.ability(InsectBalance.GLOBAL_ID, "freshPowerScale", -1), 0.0001);
        assertEquals(80, defaults.abilityTicks(InsectBalance.GLOBAL_ID, "reviveBaseTicks", -1));
        assertEquals(60, defaults.abilityTicks(InsectBalance.GLOBAL_ID, "reviveIncrementTicks", -1));
        assertEquals(80, defaults.abilityTicks(InsectBalance.GLOBAL_ID, "radiusVfxIntervalTicks", -1));
        assertEquals(0.20, defaults.ability(InsectBalance.GLOBAL_ID, "deathDamageTakenPerStack", -1), 0.0001);
        assertEquals(6.0, defaults.ability(InsectTowers.SPAWNER.id(), "reviveRadius", -1), 0.0001);
        assertEquals(20, defaults.towers().get(InsectTowers.SPAWNER.id()).aggroPriority());

        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        abilities.put(InsectTowers.CAVE_SPIDER.id(), Map.of("damageReduction", 1.0));
        TowerBalanceConfig invalid = new TowerBalanceConfig(
                defaults.towers(), defaults.upgradeCosts(), abilities,
                defaults.illusionCloneQueue(), defaults.villagerAdv(), defaults.schemaVersion());
        assertThrows(IllegalArgumentException.class, invalid::validateForRuntime);
    }

    @Test
    void tunedDamageHealthAndSlotCeilingsMatchThePlan() {
        assertEquals(9.3333, dps(InsectTowers.SILVERFISH), 0.001);
        assertEquals(21.5384, dps(InsectTowers.ENDERMITE), 0.001);
        assertEquals(47.2727, dps(InsectTowers.ENHANCED_ENDERMITE), 0.001);
        assertEquals(4.0, dps(InsectTowers.CAVE_SPIDER), 0.001);
        assertEquals(7.7777, dps(InsectTowers.SPIDER), 0.001);
        assertEquals(15.0, dps(InsectTowers.ENHANCED_SPIDER), 0.001);
        assertEquals(8.75, dps(InsectTowers.BEE), 0.001);
        assertEquals(18.4615, dps(InsectTowers.ENHANCED_BEE), 0.001);
        assertEquals(42.0, dps(InsectTowers.QUEEN_BEE), 0.001);

        double freshSilverfishDps = dps(InsectTowers.SILVERFISH) * InsectBalance.FRESH_POWER_MULTIPLIER;
        assertEquals(49.0, freshSilverfishDps * 3, 0.001);
        assertEquals(752.5, InsectTowers.SPAWNER.maxHealth()
                + InsectTowers.SILVERFISH.maxHealth() * InsectBalance.FRESH_POWER_MULTIPLIER * 3, 0.001);
        assertEquals(189.0909, dps(InsectTowers.ENHANCED_ENDERMITE) * 4, 0.001);
        assertEquals(60.0, dps(InsectTowers.ENHANCED_SPIDER) * 4, 0.001);
        assertEquals(168.0, dps(InsectTowers.QUEEN_BEE) * 4, 0.001);
        assertEquals(720.0, InsectTowers.ENHANCED_SPIDER.maxHealth() / (1.0 - 0.25), 0.001);
        assertEquals(1_040.0, dps(InsectTowers.ENHANCED_ENDERMITE) * 22, 0.001);
        assertEquals(23, TowerCapacity.slotCost(InsectTowers.SPAWNER)
                + TowerCapacity.slotCost(InsectTowers.ENHANCED_ENDERMITE) * 22);

        for (TowerType type : List.of(
                InsectTowers.ENHANCED_ENDERMITE, InsectTowers.ENHANCED_SPIDER, InsectTowers.QUEEN_BEE)) {
            double singleTargetDps = dps(type);
            assertEquals(singleTargetDps, effectiveDps(type, 1), 0.0001);
            assertEquals(singleTargetDps, effectiveDps(type, 3), 0.0001);
            assertEquals(singleTargetDps, effectiveDps(type, 5), 0.0001);
        }
    }

    @Test
    void visualScalesStayDistinctAndWithinOneBlockHitbox() {
        assertEquals(0.75, InsectTowers.BEE.visual().scale(), 0.0001);
        assertEquals(0.95, InsectTowers.ENHANCED_BEE.visual().scale(), 0.0001);
        assertEquals(1.20, InsectTowers.QUEEN_BEE.visual().scale(), 0.0001);
        assertEquals(1.20, InsectTowers.ENHANCED_ENDERMITE.visual().scale(), 0.0001);
        assertEquals(1.20, InsectTowers.ENHANCED_SPIDER.visual().scale(), 0.0001);
        assertTrue(InsectTowers.all().stream()
                .filter(type -> !InsectTowers.isSpawner(type))
                .allMatch(type -> type.visual().scale() <= 1.25));
    }

    @Test
    void missingDefaultsPreserveOverridesAndInvalidInsectValuesAreRejected() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceConfig merged = new TowerBalanceConfig(
                Map.of(), Map.of(), Map.of(InsectBalance.GLOBAL_ID, Map.of("freshPowerMultiplier", 1.9))
        ).withMissingDefaults(defaults);
        assertEquals(1.9, merged.ability(InsectBalance.GLOBAL_ID, "freshPowerMultiplier", -1), 0.0001);
        assertEquals(80, merged.abilityTicks(InsectBalance.GLOBAL_ID, "radiusVfxIntervalTicks", -1));
        assertEquals(6.0, merged.ability(InsectTowers.SPAWNER.id(), "reviveRadius", -1), 0.0001);

        assertInvalidAbility(defaults, InsectBalance.GLOBAL_ID, "freshPowerMultiplier", 0.99);
        assertInvalidAbility(defaults, InsectBalance.GLOBAL_ID, "freshPowerScale", 1.26);
        assertInvalidAbility(defaults, InsectBalance.GLOBAL_ID, "reviveBaseTicks", 80.5);
        assertInvalidAbility(defaults, InsectBalance.GLOBAL_ID, "radiusVfxIntervalTicks", 0.0);
        assertInvalidAbility(defaults, InsectBalance.GLOBAL_ID, "deathDamageTakenPerStack", 1.01);
        assertInvalidAbility(defaults, InsectTowers.SPAWNER.id(), "reviveRadius", Double.NaN);
        assertInvalidAbility(defaults, InsectTowers.SPIDER.id(), "damageReduction", 0.05);

        LinkedHashMap<String, TowerBalanceConfig.TowerStats> towers = new LinkedHashMap<>(defaults.towers());
        TowerBalanceConfig.TowerStats endermite = towers.get(InsectTowers.ENDERMITE.id());
        towers.put(InsectTowers.ENDERMITE.id(), new TowerBalanceConfig.TowerStats(
                endermite.mineralCost(), endermite.maxHealth(), endermite.range(), 6.0,
                endermite.attackIntervalTicks(), endermite.aggroPriority()
        ));
        TowerBalanceConfig invalidTier = new TowerBalanceConfig(
                towers, defaults.upgradeCosts(), defaults.abilities(),
                defaults.illusionCloneQueue(), defaults.villagerAdv(), defaults.schemaVersion()
        );
        assertThrows(IllegalArgumentException.class, invalidTier::validateForRuntime);
    }

    @Test
    void bundledInsectDefaultsMatchJavaDefaults() throws Exception {
        try (var input = InsectTowerCatalogTest.class.getResourceAsStream(
                "/semiontd/balance-defaults/tower_balance.json")) {
            var root = JsonParser.parseReader(new InputStreamReader(
                    java.util.Objects.requireNonNull(input), StandardCharsets.UTF_8)).getAsJsonObject();
            var bundledAbilities = root.getAsJsonObject("abilities");
            TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
            for (var entry : defaults.abilities().entrySet()) {
                if (!entry.getKey().startsWith("insect_")) {
                    continue;
                }
                var bundled = bundledAbilities.getAsJsonObject(entry.getKey());
                assertEquals(entry.getValue().keySet(), bundled.keySet(), entry.getKey());
                entry.getValue().forEach((key, value) ->
                        assertEquals(value, bundled.get(key).getAsDouble(), 0.0001, entry.getKey() + "." + key));
            }
            for (TowerType type : InsectTowers.all()) {
                var bundled = root.getAsJsonObject("towers").getAsJsonObject(type.id());
                TowerBalanceConfig.TowerStats stats = defaults.towers().get(type.id());
                assertEquals(stats.maxHealth(), bundled.get("maxHealth").getAsDouble(), 0.0001, type.id());
                assertEquals(stats.damage(), bundled.get("damage").getAsDouble(), 0.0001, type.id());
                assertEquals(stats.attackIntervalTicks(), bundled.get("attackIntervalTicks").getAsInt(), type.id());
            }
        }
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

    private static double dps(TowerType type) {
        return type.damage() * 20.0 / type.attackIntervalTicks();
    }

    private static double effectiveDps(TowerType type, int enemies) {
        return enemies <= 0 ? 0.0 : dps(type);
    }

    private static InsectUnitTower unit(kim.biryeong.semiontd.tower.TowerType type, int x) {
        return new InsectUnitTower(type, OWNER, TeamId.RED, 1, position(x), position(x));
    }

    private static kim.biryeong.semiontd.tower.Tower create(kim.biryeong.semiontd.tower.TowerType type) {
        return ProductionTowerCatalog.find(type.id()).orElseThrow()
                .create(OWNER, TeamId.RED, 1, position(0));
    }

    private static GridPosition position(int x) {
        return new GridPosition(x, 64, 0);
    }

    private static PlayerLane testLane() {
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                new Vec3(0.5, 64, 0.5),
                List.of(new Vec3(5.5, 64, 0.5)),
                new Vec3(10.5, 64, 0.5),
                BlockBounds.of(new BlockPos(0, 63, 0), new BlockPos(20, 66, 4)),
                List.of(new GridPosition(10, 63, 0))
        );
        return new PlayerLane(TeamId.RED, 1, OWNER, null, layout);
    }
}
