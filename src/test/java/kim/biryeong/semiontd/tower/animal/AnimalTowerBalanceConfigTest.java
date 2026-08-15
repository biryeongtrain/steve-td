package kim.biryeong.semiontd.tower.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig.TowerStats;
import kim.biryeong.semiontd.job.AnimalTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.villager.VillagerTowers;
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
        assertEquals(new TowerStats(300L, 400.0, 2.0, 15.0, 20, 60), stats(config, AnimalTowers.T3_PIG_TOWER));
        assertEquals(new TowerStats(50L, 50.0, 6.0, 5.0, 20, 5), stats(config, AnimalTowers.T1_WOLF_TOWER));
        assertEquals(new TowerStats(110L, 70.0, 6.0, 10.0, 20, 5), stats(config, AnimalTowers.T2_WOLF_DPS_TOWER));
        assertEquals(new TowerStats(110L, 90.0, 6.0, 20.0, 20, 0), stats(config, AnimalTowers.T3_WOLF_DPS_TOWER));
        assertEquals(new TowerStats(50L, 30.0, 7.0, 5.0, 15, -5), stats(config, AnimalTowers.T1_RABBIT_TOWER));
        assertEquals(new TowerStats(180L, 55.0, 7.0, 8.0, 15, -5), stats(config, AnimalTowers.T2_RABBIT_TOWER));
        assertEquals(new TowerStats(300L, 70.0, 7.0, 10.0, 13, -5), stats(config, AnimalTowers.T3_RABBIT_TOWER));
        assertEquals(new TowerStats(60L, 45.0, 7.0, 12.0, 20, 5), stats(config, AnimalTowers.T1_FOX_TOWER));
        assertEquals(new TowerStats(170L, 60.0, 7.0, 20.0, 15, 5), stats(config, AnimalTowers.T2_FOX_TOWER));
        assertEquals(new TowerStats(320L, 80.0, 8.0, 30.0, 10, 5), stats(config, AnimalTowers.T3_FOX_TOWER));
        assertEquals(new TowerStats(350L, 450.0, 2.0, 18.0, 20, 40), stats(config, AnimalTowers.T4_PIG_LEADER_TOWER));
        assertEquals(new TowerStats(400L, 105.0, 6.5, 24.0, 18, -20), stats(config, AnimalTowers.T4_WOLF_LEADER_TOWER));
        assertEquals(new TowerStats(450L, 85.0, 8.0, 12.0, 12, -20), stats(config, AnimalTowers.T4_RABBIT_LEADER_TOWER));
        assertEquals(new TowerStats(500L, 95.0, 8.5, 35.0, 10, -20), stats(config, AnimalTowers.T4_FOX_LEADER_TOWER));
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
        assertEquals(350, config.upgradeCost("t3_pig_tower", "t4_pig_leader_tower", -1));
        assertEquals(400, config.upgradeCost("t3_wolf_dps_tower", "t4_wolf_leader_tower", -1));
        assertEquals(450, config.upgradeCost("t3_rabbit_tower", "t4_rabbit_leader_tower", -1));
        assertEquals(500, config.upgradeCost("t3_fox_tower", "t4_fox_leader_tower", -1));
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
        assertAbilities(config, AnimalTowers.T4_PIG_LEADER_TOWER, Map.of(
                "maxStacks", 2.0, "healthPerStack", 90.0, "damagePerStack", 15.0,
                "damageReduction", 0.30, "splashRadius", 1.0, "splashDamageRatio", 0.50,
                "leaderAuraRadius", 8.0, "leaderMaxHealthBonus", 0.15,
                "leaderDamageReductionBonus", 0.05
        ));
        assertAbilities(config, AnimalTowers.T4_WOLF_LEADER_TOWER, Map.of(
                "maxStacks", 4.0, "damagePerStack", 10.0, "intervalReductionPerStack", 1.25,
                "splashRadius", 2.0, "splashDamageRatio", 0.75,
                "maxStackExtraIntervalReduction", 5.0, "maxStackDamageBonus", 5.0,
                "leaderAuraRadius", 8.0, "leaderAttackIntervalReductionTicks", 1.0,
                "leaderSplashDamageRatioBonus", 0.10
        ));
        assertAbilities(config, AnimalTowers.T4_RABBIT_LEADER_TOWER, Map.of(
                "maxStacks", 4.0, "damagePerStack", 12.5,
                "maxStackExtraIntervalReduction", 5.0, "extraAttackDamageRatio", 2.0,
                "leaderAuraRadius", 7.0, "leaderDamageBonus", 0.08, "leaderRangeBonus", 1.0
        ));
        assertAbilities(config, AnimalTowers.T4_FOX_LEADER_TOWER, Map.ofEntries(
                Map.entry("maxStacks", 4.0), Map.entry("executeHealthThreshold", 0.40),
                Map.entry("executeThresholdPerStack", 0.04), Map.entry("maxExecuteHealthThreshold", 0.60),
                Map.entry("executeDamageBonusRatio", 0.75), Map.entry("executeDamageBonusPerStack", 0.30),
                Map.entry("killBonusDamage", 0.40), Map.entry("killBonusDamageCap", 40.0),
                Map.entry("leaderAuraRadius", 8.0), Map.entry("leaderExecuteThresholdBonus", 0.05),
                Map.entry("leaderExecuteThresholdCap", 0.65), Map.entry("leaderExecuteDamageBonus", 0.25)
        ));
    }

    @Test
    void animalLeaderCatalogRegistersTierFourOnlyAfterTierThree() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());

        assertLeaderUpgrade(AnimalTowers.T3_PIG_TOWER, AnimalTowers.T4_PIG_LEADER_TOWER);
        assertLeaderUpgrade(AnimalTowers.T3_WOLF_DPS_TOWER, AnimalTowers.T4_WOLF_LEADER_TOWER);
        assertLeaderUpgrade(AnimalTowers.T3_RABBIT_TOWER, AnimalTowers.T4_RABBIT_LEADER_TOWER);
        assertLeaderUpgrade(AnimalTowers.T3_FOX_TOWER, AnimalTowers.T4_FOX_LEADER_TOWER);
        assertTrue(ProductionTowerCatalog.upgrades(AnimalTowers.T2_PIG_TOWER).stream()
                .noneMatch(option -> option.targetType().id().equals(AnimalTowers.T4_PIG_LEADER_TOWER.id())));

        AnimalTowerJob job = new AnimalTowerJob();
        assertTrue(job.canUseTower(null, AnimalTowers.T4_PIG_LEADER_TOWER));
        assertTrue(job.canUseTower(null, AnimalTowers.T4_WOLF_LEADER_TOWER));
        assertTrue(job.canUseTower(null, AnimalTowers.T4_RABBIT_LEADER_TOWER));
        assertTrue(job.canUseTower(null, AnimalTowers.T4_FOX_LEADER_TOWER));
        assertFalse(job.canUseTower(null, VillagerTowers.T1_SPLASH_TOWER));
    }

    @Test
    void missingLeaderDefaultsMergeWithoutOverwritingExistingValues() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, TowerStats> towers = new LinkedHashMap<>();
        towers.put(AnimalTowers.T3_PIG_TOWER.id(), new TowerStats(999L, null, null, null, null, null));
        Map<String, Long> costs = Map.of("t2_pig_tower->t3_pig_tower", 777L);
        Map<String, Map<String, Double>> abilities = Map.of(
                AnimalTowers.T3_PIG_TOWER.id(), Map.of("damagePerStack", 123.0)
        );

        TowerBalanceConfig merged = new TowerBalanceConfig(towers, costs, abilities).withMissingDefaults(defaults);

        assertEquals(999L, merged.towers().get(AnimalTowers.T3_PIG_TOWER.id()).mineralCost());
        assertEquals(400.0, merged.towers().get(AnimalTowers.T3_PIG_TOWER.id()).maxHealth());
        assertEquals(777L, merged.upgradeCost("t2_pig_tower", "t3_pig_tower", -1));
        assertEquals(123.0, merged.ability(AnimalTowers.T3_PIG_TOWER.id(), "damagePerStack", -1.0));
        assertEquals(stats(defaults, AnimalTowers.T4_PIG_LEADER_TOWER), stats(merged, AnimalTowers.T4_PIG_LEADER_TOWER));
        assertEquals(350L, merged.upgradeCost("t3_pig_tower", "t4_pig_leader_tower", -1));
        assertEquals(0.15, merged.ability(AnimalTowers.T4_PIG_LEADER_TOWER.id(), "leaderMaxHealthBonus", -1.0));
    }

    private static TowerStats stats(TowerBalanceConfig config, TowerType type) {
        return config.towers().get(type.id());
    }

    private static void assertAbilities(TowerBalanceConfig config, TowerType type, Map<String, Double> expected) {
        assertEquals(expected, config.abilities().get(type.id()));
    }

    private static void assertLeaderUpgrade(TowerType tierThree, TowerType leader) {
        assertEquals(4, ProductionTowerCatalog.find(leader.id()).orElseThrow().tier());
        assertEquals(leader.id(), ProductionTowerCatalog.upgrade(tierThree, leader.id()).orElseThrow().targetType().id());
    }
}
