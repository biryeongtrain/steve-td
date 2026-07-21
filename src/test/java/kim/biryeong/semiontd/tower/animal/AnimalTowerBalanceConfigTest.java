package kim.biryeong.semiontd.tower.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig.TowerStats;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AnimalTowerBalanceConfigTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void defaultAnimalTowerStatsMatchLiveBalance() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();

        assertEquals(new TowerStats(40L, 80.0, 2.0, 5.0, 20, 40), stats(config, AnimalTowers.T1_PIG_TOWER));
        assertEquals(new TowerStats(180L, 150.0, 2.0, 7.0, 20, 55), stats(config, AnimalTowers.T2_PIG_TOWER));
        assertEquals(new TowerStats(300L, 350.0, 2.0, 15.0, 20, 60), stats(config, AnimalTowers.T3_PIG_TOWER));
        assertEquals(new TowerStats(50L, 50.0, 6.0, 5.0, 20, 5), stats(config, AnimalTowers.T1_WOLF_TOWER));
        assertEquals(new TowerStats(110L, 70.0, 6.0, 10.0, 20, 5), stats(config, AnimalTowers.T2_WOLF_DPS_TOWER));
        assertEquals(new TowerStats(110L, 90.0, 6.0, 20.0, 20, 0), stats(config, AnimalTowers.T3_WOLF_DPS_TOWER));
        assertEquals(new TowerStats(50L, 40.0, 7.0, 5.0, 15, -5), stats(config, AnimalTowers.T1_RABBIT_TOWER));
        assertEquals(new TowerStats(180L, 55.0, 7.0, 8.0, 15, -5), stats(config, AnimalTowers.T2_RABBIT_TOWER));
        assertEquals(new TowerStats(300L, 70.0, 7.0, 10.0, 13, -5), stats(config, AnimalTowers.T3_RABBIT_TOWER));
        assertEquals(new TowerStats(60L, 45.0, 7.0, 12.0, 20, 5), stats(config, AnimalTowers.T1_FOX_TOWER));
        assertEquals(new TowerStats(170L, 60.0, 7.0, 20.0, 15, 5), stats(config, AnimalTowers.T2_FOX_TOWER));
        assertEquals(new TowerStats(320L, 80.0, 8.0, 30.0, 10, 5), stats(config, AnimalTowers.T3_FOX_TOWER));
    }

    @Test
    void defaultAnimalUpgradeCostsMatchLiveBalance() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();

        assertEquals(95, config.upgradeCost("t1_pig_tower", "t2_pig_tower", -1));
        assertEquals(150, config.upgradeCost("t2_pig_tower", "t3_pig_tower", -1));
        assertEquals(90, config.upgradeCost("t1_wolf_tower", "t2_wolf_dps_tower", -1));
        assertEquals(180, config.upgradeCost("t2_wolf_dps_tower", "t3_wolf_dps_tower", -1));
        assertEquals(100, config.upgradeCost("t1_rabbit_tower", "t2_rabbit_tower", -1));
        assertEquals(200, config.upgradeCost("t2_rabbit_tower", "t3_rabbit_tower", -1));
        assertEquals(150, config.upgradeCost("t1_fox_tower", "t2_fox_tower", -1));
        assertEquals(225, config.upgradeCost("t2_fox_tower", "t3_fox_tower", -1));
    }

    @Test
    void defaultAnimalAbilitiesMatchLiveBalance() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();

        assertAbilities(config, AnimalTowers.T1_PIG_TOWER, Map.of(
                "maxStacks", 2.0, "healthPerStack", 10.0, "damagePerStack", 2.5
        ));
        assertAbilities(config, AnimalTowers.T2_PIG_TOWER, Map.of(
                "maxStacks", 2.0, "healthPerStack", 25.0, "damagePerStack", 5.0, "damageReduction", 0.10
        ));
        assertAbilities(config, AnimalTowers.T3_PIG_TOWER, Map.of(
                "maxStacks", 2.0, "healthPerStack", 90.0, "damagePerStack", 15.0,
                "damageReduction", 0.30, "splashRadius", 1.0, "splashDamageRatio", 0.50
        ));
        assertAbilities(config, AnimalTowers.T1_WOLF_TOWER, Map.of(
                "maxStacks", 4.0, "damagePerStack", 2.0, "intervalReductionPerStack", 1.25
        ));
        assertAbilities(config, AnimalTowers.T2_WOLF_DPS_TOWER, Map.of(
                "maxStacks", 4.0, "damagePerStack", 5.0, "intervalReductionPerStack", 1.25,
                "splashRadius", 1.25, "splashDamageRatio", 0.50, "maxStackExtraIntervalReduction", 3.0
        ));
        assertAbilities(config, AnimalTowers.T3_WOLF_DPS_TOWER, Map.of(
                "maxStacks", 4.0, "damagePerStack", 10.0, "intervalReductionPerStack", 1.25,
                "splashRadius", 2.0, "splashDamageRatio", 0.75,
                "maxStackExtraIntervalReduction", 5.0, "maxStackDamageBonus", 5.0
        ));
        assertAbilities(config, AnimalTowers.T1_RABBIT_TOWER, Map.of(
                "maxStacks", 4.0, "damagePerStack", 2.5
        ));
        assertAbilities(config, AnimalTowers.T2_RABBIT_TOWER, Map.of(
                "maxStacks", 4.0, "damagePerStack", 6.25, "maxStackExtraIntervalReduction", 5.0
        ));
        assertAbilities(config, AnimalTowers.T3_RABBIT_TOWER, Map.of(
                "maxStacks", 4.0, "damagePerStack", 12.5,
                "maxStackExtraIntervalReduction", 5.0, "extraAttackDamageRatio", 2.0
        ));
        assertAbilities(config, AnimalTowers.T1_FOX_TOWER, Map.of(
                "maxStacks", 4.0, "executeHealthThreshold", 0.30,
                "executeThresholdPerStack", 0.02, "maxExecuteHealthThreshold", 0.40,
                "executeDamageBonusRatio", 0.50, "executeDamageBonusPerStack", 0.20,
                "killBonusDamage", 0.10, "killBonusDamageCap", 10.0
        ));
        assertAbilities(config, AnimalTowers.T2_FOX_TOWER, Map.of(
                "maxStacks", 4.0, "executeHealthThreshold", 0.35,
                "executeThresholdPerStack", 0.025, "maxExecuteHealthThreshold", 0.50,
                "executeDamageBonusRatio", 0.50, "executeDamageBonusPerStack", 0.25,
                "killBonusDamage", 0.20, "killBonusDamageCap", 20.0
        ));
        assertAbilities(config, AnimalTowers.T3_FOX_TOWER, Map.of(
                "maxStacks", 4.0, "executeHealthThreshold", 0.40,
                "executeThresholdPerStack", 0.04, "maxExecuteHealthThreshold", 0.60,
                "executeDamageBonusRatio", 0.75, "executeDamageBonusPerStack", 0.30,
                "killBonusDamage", 0.40, "killBonusDamageCap", 40.0
        ));
    }

    @Test
    void rabbitT3DescriptionShowsLiveExtraDamageRatio() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());

        assertTrue(TowerBalanceRuntime.resolve(AnimalTowers.T3_RABBIT_TOWER).description().stream()
                .anyMatch(line -> line.contains("추가 피해 +200%")));
    }

    private static TowerStats stats(TowerBalanceConfig config, TowerType type) {
        return config.towers().get(type.id());
    }

    private static void assertAbilities(TowerBalanceConfig config, TowerType type, Map<String, Double> expected) {
        assertEquals(expected, config.abilities().get(type.id()));
    }
}
