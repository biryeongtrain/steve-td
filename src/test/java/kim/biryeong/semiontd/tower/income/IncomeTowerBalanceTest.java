package kim.biryeong.semiontd.tower.income;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class IncomeTowerBalanceTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void levelCostsIncomeAndStatsGrowPerLevel() {
        assertEquals(100, IncomeTowerBalance.upgradeCost(100, 1));
        assertEquals(150, IncomeTowerBalance.upgradeCost(100, 2));
        assertEquals(250, IncomeTowerBalance.upgradeCost(100, 4));
        assertEquals(3, IncomeTowerBalance.incomeAt(3, 1));
        assertEquals(15, IncomeTowerBalance.incomeAt(3, 5));
        assertEquals(0.85, IncomeTowerBalance.statMultiplier(1), 1.0E-9);
        assertEquals(0.85 * 2.4, IncomeTowerBalance.statMultiplier(5), 1.0E-9);
        assertEquals(55, IncomeTowerBalance.sellRefund(110));
    }

    @Test
    void unitsFollowTheSummonRoundCurveTimesLevel() {
        assertEquals(0.85, IncomeTowerBalance.healthMultiplier(1, 1), 1.0E-9);
        assertEquals(0.85 * 2.4 * kim.biryeong.semiontd.summon.SummonBalancePolicy.summonHealthMultiplier(25),
                IncomeTowerBalance.healthMultiplier(5, 25), 1.0E-9);
        assertEquals(0.85 * kim.biryeong.semiontd.summon.SummonBalancePolicy.summonAttackDamageMultiplier(20),
                IncomeTowerBalance.attackDamageMultiplier(1, 20), 1.0E-9);
        assertTrue(IncomeTowerBalance.healthMultiplier(5, 30) < 8.0,
                "A max-level unit stays within a few times a regular summon even in late rounds.");
    }

    @Test
    void expensiveIncomeTowersTakeMoreTowerSlots() {
        kim.biryeong.semiontd.config.TowerBalanceConfig bundled = kim.biryeong.semiontd.config.TowerBalanceConfig.defaultConfig();
        kim.biryeong.semiontd.config.TowerBalanceConfig code = kim.biryeong.semiontd.config.TowerBalanceConfig.codeDefaults();
        for (String id : IncomeTowerBalance.UNIT_IDS) {
            String towerId = IncomeTowerBalance.towerId(id);
            double slots = bundled.ability(towerId, kim.biryeong.semiontd.tower.TowerCapacity.CONFIG_KEY, -1);
            assertTrue(slots >= 1.0, id + " must take at least one slot");
            assertEquals(code.abilities().get(towerId), bundled.abilities().get(towerId), id + " drifted between code and bundle");
        }
        assertEquals(1.0, bundled.ability(IncomeTowerBalance.towerId("goblin_scout"), "towerSlotCost", -1));
        assertEquals(4.0, bundled.ability(IncomeTowerBalance.towerId("ogre_champion"), "towerSlotCost", -1));
        assertEquals(3.0, bundled.ability(IncomeTowerBalance.towerId("legion_commander"), "towerSlotCost", -1));
    }

    @Test
    void everyIncomeTowerUnitHasAnInvasionModelInTheDefaults() {
        SummonConfig defaults = SummonConfig.defaultConfig();
        for (String id : IncomeTowerBalance.UNIT_IDS) {
            SummonConfig.SummonDefinition definition = defaults.summons().get(id);
            assertTrue(definition != null && definition.enabled(), "Missing default summon " + id);
            if ("creaking".equals(id)) {
                // The creaking keeps the vanilla model instead of a Blockbench one.
                assertEquals("minecraft:creaking", definition.entityTypeId());
                assertEquals(null, definition.blockbenchModelId());
            } else {
                assertEquals("semion-td:invasion/" + id, definition.blockbenchModelId());
            }
            assertTrue(definition.emeraldCost() > 0 && definition.incomeGain() > 0, "Unit " + id + " must cost and pay.");
        }
    }

    @Test
    void ogreDefaultBackfillsButSavedOperatorCostsRemainAuthoritative(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var logger = org.slf4j.LoggerFactory.getLogger("ogre-population-test");
        var fresh = kim.biryeong.semiontd.config.SemionConfigLoader.load(directory, logger).towerBalance();
        assertEquals(4, fresh.ability("income_ogre_champion", "towerSlotCost", -1));
        var path = directory.resolve("tower_balance.json");
        for (double saved : new double[]{2, 3, 6}) {
            var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
            json.getAsJsonObject("abilities").getAsJsonObject("income_ogre_champion").addProperty("towerSlotCost", saved);
            java.nio.file.Files.writeString(path, json.toString());
            for (int reload = 0; reload < 2; reload++) {
                var loaded = kim.biryeong.semiontd.config.SemionConfigLoader.load(directory, logger).towerBalance();
                assertEquals(saved, loaded.ability("income_ogre_champion", "towerSlotCost", -1));
                assertEquals(3, loaded.ability("income_legion_commander", "towerSlotCost", -1));
            }
        }
    }
}
