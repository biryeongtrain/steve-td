package kim.biryeong.semiontd.tower.warlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import kim.biryeong.semiontd.job.WarlockTowerJob;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WarlockTowerBalanceTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void resetBalance() {
        WarlockAwakeningProgress.clearAllForTesting();
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void globalConfigDefinesRequestedCapsAndSplashGrowth() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();

        TowerBalanceConfig.TowerStats baseStats = config.towers().get(WarlockTowers.BASE_WARLOCK_TOWER.id());
        assertEquals(80.0, baseStats.maxHealth(), 0.0001);
        assertEquals(4.0, baseStats.range(), 0.0001);
        assertEquals(5.0, baseStats.damage(), 0.0001);
        assertEquals(20, baseStats.attackIntervalTicks());
        assertEquals(30, baseStats.aggroPriority());
        TowerBalanceConfig.TowerStats rangedStats = config.towers().get(WarlockTowers.RANGED_WARLOCK_TOWER.id());
        assertEquals(100.0, rangedStats.maxHealth(), 0.0001);
        assertEquals(7.0, rangedStats.range(), 0.0001);
        assertEquals(8.0, rangedStats.damage(), 0.0001);
        assertEquals(20, rangedStats.attackIntervalTicks());
        assertEquals(20, rangedStats.aggroPriority());
        TowerBalanceConfig.TowerStats meleeStats = config.towers().get(WarlockTowers.MELEE_WARLOCK_TOWER.id());
        assertEquals(120.0, meleeStats.maxHealth(), 0.0001);
        assertEquals(3.0, meleeStats.range(), 0.0001);
        assertEquals(7.0, meleeStats.damage(), 0.0001);
        assertEquals(20, meleeStats.attackIntervalTicks());
        assertEquals(80, meleeStats.aggroPriority());
        assertEquals(70, config.upgradeCost(
                WarlockTowers.T1_SLAVE.id(),
                WarlockTowers.T2_SLAVE.id(),
                -1
        ));
        assertEquals(150, config.upgradeCost(
                WarlockTowers.T2_SLAVE.id(),
                WarlockTowers.T3_SLAVE.id(),
                -1
        ));
        assertEquals(80, config.upgradeCost(
                WarlockTowers.T1_RANGED_SLAVE.id(),
                WarlockTowers.T2_RANGED_SLAVE.id(),
                -1
        ));
        assertEquals(160, config.upgradeCost(
                WarlockTowers.T2_RANGED_SLAVE.id(),
                WarlockTowers.T3_RANGED_SLAVE.id(),
                -1
        ));

        assertEquals(150.0, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "damageThreshold", -1.0), 0.0001);
        assertEquals(20.0, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "damageScale", -1.0), 0.0001);
        assertEquals(2000.0, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "healthThreshold", -1.0), 0.0001);
        assertEquals(500.0, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "healthScale", -1.0), 0.0001);
        assertEquals(200.0, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "damageThreshold", -1.0), 0.0001);
        assertEquals(20.0, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "damageScale", -1.0), 0.0001);
        assertEquals(3500.0, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "healthThreshold", -1.0), 0.0001);
        assertEquals(500.0, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "healthScale", -1.0), 0.0001);
        assertEquals(0.07, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "lifeCap", -1.0), 0.0001);
        assertEquals(0.05, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "incomeDebuffResistance", -1.0), 0.0001);
        assertEquals(10.0, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "lifeEvery", -1.0), 0.0001);
        assertEquals(0.13, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "lifeCap", -1.0), 0.0001);
        assertEquals(0.05, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "incomeDebuffResistance", -1.0), 0.0001);
        assertEquals(0.30, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "defenseCap", -1.0), 0.0001);
        assertEquals(10.0, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "defenseEvery", -1.0), 0.0001);
        assertEquals(0.1, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "splashStep", -1.0), 0.0001);
        assertEquals(2.0, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "splashEvery", -1.0), 0.0001);
        assertEquals(8.0, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "splashCap", -1.0), 0.0001);
        assertEquals(25.0, config.ability(WarlockTower.CONFIG_ID, "sacrificeRadius", -1.0), 0.0001);
        assertEquals(30.0, config.ability(WarlockTower.CONFIG_ID, "absorptionHeal", -1.0), 0.0001);
        assertEquals(5.0, config.ability(WarlockTower.CONFIG_ID, "minInterval", -1.0), 0.0001);
        assertEquals(6.0, config.ability(WarlockTowers.BASE_WARLOCK_TOWER.id(), "sacrificeRadius", -1.0), 0.0001);
        assertEquals(0.40, config.ability(WarlockTower.CONFIG_ID, "awakeningThreshold", -1.0), 0.0001);
        assertEquals(1250.0, config.ability(WarlockTower.CONFIG_ID, "awakeningKills", -1.0), 0.0001);
        assertEquals(0.55, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "threshold", -1.0), 0.0001);
        assertEquals(0.50, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "roundStat", -1.0), 0.0001);
        assertEquals(15.0, config.ability(WarlockTower.CONFIG_ID, "speedCap", -1.0), 0.0001);
        assertEquals(3.0, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "defenseThreshold", -1.0), 0.0001);
        assertEquals(0.15, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "defense", -1.0), 0.0001);
        assertEquals(0.50, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "splashDamage", -1.0), 0.0001);
        assertEquals(0.20, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "petHealthCap", -1.0), 0.0001);
        assertEquals(0.04, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "petHealth", -1.0), 0.0001);
        assertEquals(0.50, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "petDamageCap", -1.0), 0.0001);
        assertEquals(0.10, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "petDamage", -1.0), 0.0001);
        assertEquals(600.0, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "awakeningHeal", -1.0), 0.0001);
        assertEquals(40.0, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "awakeningRegeneration", -1.0), 0.0001);
        assertEquals(20.0, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "awakeningRegenerationTicks", -1.0), 0.0001);
        assertEquals(0.55, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "threshold", -1.0), 0.0001);
        assertEquals(0.60, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "roundStat", -1.0), 0.0001);
        assertEquals(1.0, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "speedStep", -1.0), 0.0001);
        assertEquals(75.0, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "awakeningDamage", -1.0), 0.0001);
        assertEquals(0.30, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "awakeningMoveSpeed", -1.0), 0.0001);
        assertEquals(0.25, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "splashStep", -1.0), 0.0001);
        assertEquals(2.0, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "splashCap", -1.0), 0.0001);
        assertEquals(0.75, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "splashDamage", -1.0), 0.0001);
        assertEquals(0.50, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "petHealthCap", -1.0), 0.0001);
        assertEquals(0.10, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "petHealth", -1.0), 0.0001);
        assertEquals(0.20, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "petDamageCap", -1.0), 0.0001);
        assertEquals(0.04, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "petDamage", -1.0), 0.0001);
        assertEquals(600.0, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "awakeningHeal", -1.0), 0.0001);
        assertEquals(List.of(
                "sacrificeRadius", "absorptionHeal", "minInterval", "speedCap", "awakeningKills", "awakeningThreshold"
        ), List.copyOf(config.abilities().get(WarlockTowers.CONFIG_ID).keySet()));
        assertEquals(List.of(
                "sacrificeRadius", "permanentHealth", "permanentDamage"
        ), List.copyOf(config.abilities().get(WarlockTowers.BASE_WARLOCK_TOWER.id()).keySet()));
        assertEquals(List.of(
                "threshold", "roundStat", "permanentHealth", "healthThreshold", "healthScale", "permanentDamage",
                "damageThreshold", "damageScale", "lifeEvery", "lifeStep", "lifeCap", "incomeDebuffResistance",
                "splashEvery", "splashStep", "splashCap", "splashDamage", "defenseThreshold", "defense", "petHealth", "petHealthCap",
                "petDamage", "petDamageCap", "awakeningHeal", "awakeningRegeneration", "awakeningRegenerationTicks"
        ), List.copyOf(config.abilities().get(WarlockTowers.RANGED_WARLOCK_TOWER.id()).keySet()));
        assertEquals(List.of(
                "threshold", "roundStat", "permanentHealth", "healthThreshold", "healthScale", "permanentDamage",
                "damageThreshold", "damageScale", "lifeStep", "lifeCap", "incomeDebuffResistance", "speedStep",
                "splashStep", "splashCap", "splashDamage", "defenseEvery", "defenseStep", "defenseCap", "petHealth",
                "petHealthCap", "petDamage", "petDamageCap", "awakeningHeal", "awakeningDamage", "awakeningMoveSpeed"
        ), List.copyOf(config.abilities().get(WarlockTowers.MELEE_WARLOCK_TOWER.id()).keySet()));
    }

    @Test
    void combatCapsDamageAndSplashRadius() {
        WarlockCombat combat = new WarlockCombat(WarlockConfig.RUNTIME);

        assertEquals(0.0, combat.splashRadiusForCount(1), 0.0001);
        assertEquals(0.1, combat.splashRadiusForCount(2), 0.0001);
        assertEquals(0.3, combat.splashRadiusForCount(7), 0.0001);
        assertEquals(0.4, combat.splashRadiusForCount(8), 0.0001);
        assertEquals(3.2, combat.splashRadiusForCount(64), 0.0001);
        assertEquals(8.0, combat.splashRadiusForCount(160), 0.0001);
        assertEquals(8.0, combat.splashRadiusForCount(200), 0.0001);
        assertEquals(0.25, combat.meleeSplashRadiusForCount(1), 0.0001);
        assertEquals(1.0, combat.meleeSplashRadiusForCount(4), 0.0001);
        assertEquals(1.5, combat.meleeSplashRadiusForCount(6), 0.0001);
        assertEquals(2.0, combat.meleeSplashRadiusForCount(8), 0.0001);
        assertEquals(2.0, combat.meleeSplashRadiusForCount(100), 0.0001);
        assertEquals(175.0, combat.resolvedSplashDamage(WarlockTowers.RANGED_WARLOCK_TOWER, 350.0), 0.0001);
        assertEquals(262.5, combat.resolvedSplashDamage(WarlockTowers.MELEE_WARLOCK_TOWER, 350.0), 0.0001);
    }

    @Test
    void liveDamageCurvePreservesNormalAbsorptionsAndLimitsExtremeGrowth() {
        assertEquals(600.0, WarlockTower.scaledDamageBonus(WarlockTowers.BASE_WARLOCK_TOWER, 600.0), 0.0001);
        assertEquals(108.0, WarlockTower.scaledDamageBonus(WarlockTowers.RANGED_WARLOCK_TOWER, 108.0), 0.0001);
        assertEquals(140.0, WarlockTower.scaledDamageBonus(WarlockTowers.RANGED_WARLOCK_TOWER, 140.0), 0.0001);
        assertEquals(192.8013, WarlockTower.scaledDamageBonus(WarlockTowers.RANGED_WARLOCK_TOWER, 300.0), 0.0001);
        assertEquals(213.1400, WarlockTower.scaledDamageBonus(WarlockTowers.RANGED_WARLOCK_TOWER, 600.0), 0.0001);
        assertEquals(200.0, WarlockTower.scaledDamageBonus(WarlockTowers.MELEE_WARLOCK_TOWER, 200.0), 0.0001);
        assertEquals(235.8352, WarlockTower.scaledDamageBonus(WarlockTowers.MELEE_WARLOCK_TOWER, 300.0), 0.0001);
        assertEquals(260.8904, WarlockTower.scaledDamageBonus(WarlockTowers.MELEE_WARLOCK_TOWER, 600.0), 0.0001);
    }

    @Test
    void liveHealthCurvePreservesNormalAbsorptionsAndLimitsExtremeGrowth() {
        assertEquals(6000.0, WarlockTower.scaledHealthBonus(WarlockTowers.BASE_WARLOCK_TOWER, 6000.0), 0.0001);
        assertEquals(2000.0, WarlockTower.scaledHealthBonus(WarlockTowers.RANGED_WARLOCK_TOWER, 2000.0), 0.0001);
        assertEquals(3098.6123, WarlockTower.scaledHealthBonus(WarlockTowers.RANGED_WARLOCK_TOWER, 6000.0), 0.0001);
        assertEquals(3000.0, WarlockTower.scaledHealthBonus(WarlockTowers.MELEE_WARLOCK_TOWER, 3000.0), 0.0001);
        assertEquals(3500.0, WarlockTower.scaledHealthBonus(WarlockTowers.MELEE_WARLOCK_TOWER, 3500.0), 0.0001);
        assertEquals(4395.8797, WarlockTower.scaledHealthBonus(WarlockTowers.MELEE_WARLOCK_TOWER, 6000.0), 0.0001);
    }

    @Test
    void damageScalingConfigAcceptsZeroAndBackfillsMissingValues() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> invalidAbilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> invalidRanged = new LinkedHashMap<>(invalidAbilities.get(WarlockTowers.RANGED_WARLOCK_TOWER.id()));
        invalidRanged.put("damageThreshold", 0.0);
        invalidAbilities.put(WarlockTowers.RANGED_WARLOCK_TOWER.id(), invalidRanged);
        TowerBalanceConfig zero = new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), invalidAbilities);
        assertDoesNotThrow(() -> TowerBalanceRuntime.apply(zero));

        Map<String, Map<String, Double>> partialAbilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> partialRanged = new LinkedHashMap<>(partialAbilities.get(WarlockTowers.RANGED_WARLOCK_TOWER.id()));
        partialRanged.remove("damageThreshold");
        partialRanged.remove("damageScale");
        partialRanged.remove("healthThreshold");
        partialRanged.remove("healthScale");
        partialAbilities.put(WarlockTowers.RANGED_WARLOCK_TOWER.id(), partialRanged);
        Map<String, Double> partialMelee = new LinkedHashMap<>(partialAbilities.get(WarlockTowers.MELEE_WARLOCK_TOWER.id()));
        partialMelee.remove("damageThreshold");
        partialMelee.remove("damageScale");
        partialMelee.remove("healthThreshold");
        partialMelee.remove("healthScale");
        partialAbilities.put(WarlockTowers.MELEE_WARLOCK_TOWER.id(), partialMelee);
        TowerBalanceConfig merged = new TowerBalanceConfig(
                defaults.towers(),
                defaults.upgradeCosts(),
                partialAbilities
        ).withMissingDefaults(defaults);
        assertEquals(150.0, merged.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "damageThreshold", -1.0), 0.0001);
        assertEquals(20.0, merged.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "damageScale", -1.0), 0.0001);
        assertEquals(2000.0, merged.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "healthThreshold", -1.0), 0.0001);
        assertEquals(500.0, merged.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "healthScale", -1.0), 0.0001);
        assertEquals(200.0, merged.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "damageThreshold", -1.0), 0.0001);
        assertEquals(20.0, merged.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "damageScale", -1.0), 0.0001);
        assertEquals(3500.0, merged.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "healthThreshold", -1.0), 0.0001);
        assertEquals(500.0, merged.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "healthScale", -1.0), 0.0001);
        TowerBalanceRuntime.apply(merged);
        assertEquals(213.1400, WarlockTower.scaledDamageBonus(WarlockTowers.RANGED_WARLOCK_TOWER, 600.0), 0.0001);
    }

    @Test
    void discreteWarlockConfigRejectsFractionalAndOverflowValues() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();

        for (double invalid : List.of(1349.5, (double) Integer.MAX_VALUE + 1.0)) {
            Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
            Map<String, Double> global = new LinkedHashMap<>(abilities.get(WarlockTowers.CONFIG_ID));
            global.put("awakeningKills", invalid);
            abilities.put(WarlockTowers.CONFIG_ID, global);
            TowerBalanceConfig invalidConfig = new TowerBalanceConfig(
                    defaults.towers(),
                    defaults.upgradeCosts(),
                    abilities
            );

            assertThrows(IllegalArgumentException.class, invalidConfig::validateForRuntime);
        }

        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> ranged = new LinkedHashMap<>(abilities.get(WarlockTowers.RANGED_WARLOCK_TOWER.id()));
        ranged.put("splashEvery", 1.5);
        abilities.put(WarlockTowers.RANGED_WARLOCK_TOWER.id(), ranged);
        TowerBalanceConfig fractionalSplashPeriod = new TowerBalanceConfig(
                defaults.towers(),
                defaults.upgradeCosts(),
                abilities
        );
        assertThrows(IllegalArgumentException.class, fractionalSplashPeriod::validateForRuntime);
    }

    @Test
    void rangedLifeStealGrowsEveryTenAbsorptionsAndCapsAtPercent() {
        WarlockCombat combat = new WarlockCombat(WarlockConfig.RUNTIME);

        assertEquals(0.0, combat.lifeStealRatioForCount(WarlockTowers.RANGED_WARLOCK_TOWER, 9), 0.0001);
        assertEquals(0.005, combat.lifeStealRatioForCount(WarlockTowers.RANGED_WARLOCK_TOWER, 10), 0.0001);
        assertEquals(0.005, combat.lifeStealRatioForCount(WarlockTowers.RANGED_WARLOCK_TOWER, 19), 0.0001);
        assertEquals(0.01, combat.lifeStealRatioForCount(WarlockTowers.RANGED_WARLOCK_TOWER, 20), 0.0001);
        assertEquals(0.065, combat.lifeStealRatioForCount(WarlockTowers.RANGED_WARLOCK_TOWER, 130), 0.0001);
        assertEquals(0.07, combat.lifeStealRatioForCount(WarlockTowers.RANGED_WARLOCK_TOWER, 140), 0.0001);
        assertEquals(0.07, combat.lifeStealRatioForCount(WarlockTowers.RANGED_WARLOCK_TOWER, 149), 0.0001);
        assertEquals(0.07, combat.lifeStealRatioForCount(WarlockTowers.RANGED_WARLOCK_TOWER, 200), 0.0001);
    }

    @Test
    void configuredPassiveCapsRemainAuthoritative() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> configuredAbilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> ranged = new LinkedHashMap<>(configuredAbilities.get(WarlockTowers.RANGED_WARLOCK_TOWER.id()));
        ranged.put("petHealthCap", 0.25);
        configuredAbilities.put(WarlockTowers.RANGED_WARLOCK_TOWER.id(), ranged);
        Map<String, Double> melee = new LinkedHashMap<>(configuredAbilities.get(WarlockTowers.MELEE_WARLOCK_TOWER.id()));
        melee.put("petDamageCap", 0.25);
        configuredAbilities.put(WarlockTowers.MELEE_WARLOCK_TOWER.id(), melee);

        TowerBalanceConfig merged = new TowerBalanceConfig(
                defaults.towers(),
                defaults.upgradeCosts(),
                configuredAbilities
        ).withMissingDefaults(defaults);

        assertEquals(0.25, merged.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "petHealthCap", -1.0), 0.0001);
        assertEquals(0.25, merged.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "petDamageCap", -1.0), 0.0001);
    }

    @Test
    void meleeLifeStealUsesCurrentRoundAbsorptionsAndCapsAtPercent() {
        WarlockCombat combat = new WarlockCombat(WarlockConfig.RUNTIME);

        assertEquals(0.0, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 20, 0), 0.0001);
        assertEquals(0.03, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 20, 3), 0.0001);
        assertEquals(0.12, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 40, 12), 0.0001);
        assertEquals(0.13, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 40, 13), 0.0001);
        assertEquals(0.13, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 40, 25), 0.0001);
        assertEquals(0.0, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 20, 3, false), 0.0001);
        assertEquals(0.0, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 0, 0, true), 0.0001);
        assertEquals(0.03, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 20, 3, true), 0.0001);
        assertEquals(0.13, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 40, 20, true), 0.0001);
        assertEquals(0.005, combat.lifeStealRatioForCounts(WarlockTowers.RANGED_WARLOCK_TOWER, 10, 3), 0.0001);
        assertEquals(0.005, combat.lifeStealRatioForCounts(WarlockTowers.RANGED_WARLOCK_TOWER, 10, 3, true), 0.0001);
    }

    @Test
    void descriptionsExposeAwakeningAndPreviouslyHiddenAbilities() {
        List<String> rangedDescriptionLines = TowerBalanceRuntime.resolve(WarlockTowers.RANGED_WARLOCK_TOWER).description();
        String rangedMarkup = String.join("\n", rangedDescriptionLines);
        String description = rangedMarkup.replaceAll("<[^>]+>", "");
        assertTrue(description.contains("체력 55% 이하이면"));
        assertTrue(description.contains("흡수 시 최대 체력 증가분에 체력 30을 더해 회복"));
        assertTrue(description.contains("내 아군 타워를 흡수합니다."));
        assertTrue(description.contains("흡수한 타워 체력과 피해의 50%"));
        assertTrue(description.contains("체력 +2.5%"));
        assertTrue(description.contains("피해 +5%"));
        assertTrue(description.contains("생존 중인 개구리 계열마다 체력 +4%, 피해 +10%"));
        assertTrue(description.contains("최대 체력 +20%, 피해 +50%까지 증가"));
        assertFalse(description.contains("누적 1250킬에 각성을 해금"));
        assertTrue(description.contains("체력 40% 이하"));
        assertTrue(description.contains("각성 시 체력을 회복하고, 재생이 증가합니다."));
        assertEquals(9, rangedDescriptionLines.size());
        assertEquals("능력치는 높아질수록 증가 효율이 감소합니다.",
                rangedDescriptionLines.getLast().replaceAll("<[^>]+>", ""));
        assertFalse(rangedMarkup.contains("{ability."));

        List<String> meleeDescriptionLines = TowerBalanceRuntime.resolve(WarlockTowers.MELEE_WARLOCK_TOWER).description();
        String meleeMarkup = String.join("\n", meleeDescriptionLines);
        String meleeDescription = meleeMarkup.replaceAll("<[^>]+>", "");
        assertTrue(meleeDescription.contains("체력 55% 이하이면"));
        assertTrue(meleeDescription.contains("흡수 시 최대 체력 증가분에 체력 30을 더해 회복"));
        assertTrue(meleeDescription.contains("내 아군 타워를 흡수합니다."));
        assertTrue(meleeDescription.contains("흡수한 타워 체력과 피해의 60%"));
        assertTrue(meleeDescription.contains("체력 +5%"));
        assertTrue(meleeDescription.contains("피해 +2.5%"));
        assertTrue(meleeDescription.contains("생존 중인 양 계열마다 체력 +10%, 피해 +4%"));
        assertTrue(meleeDescription.contains("최대 체력 +50%, 피해 +20%까지 증가"));
        assertFalse(meleeDescription.contains("누적 1250킬에 각성을 해금"));
        assertTrue(meleeDescription.contains("각성 시 체력을 회복하고, 추가 피해와 이동 속도가 증가합니다."));
        assertTrue(meleeMarkup.contains("<#F1E7D4>이동 속도</#F1E7D4>"));
        assertEquals(9, meleeDescriptionLines.size());
        assertEquals("능력치는 높아질수록 증가 효율이 감소합니다.",
                meleeDescriptionLines.getLast().replaceAll("<[^>]+>", ""));
        assertFalse(meleeMarkup.contains("{ability."));

        List<String> baseDescriptionLines = TowerBalanceRuntime.resolve(WarlockTowers.BASE_WARLOCK_TOWER).description();
        assertEquals(List.of(
                "원거리 또는 근거리 흑마법사를 선택할 수 있습니다.",
                "흑마법사 타워는 1기만 설치할 수 있습니다."
        ), baseDescriptionLines.stream().map(line -> line.replaceAll("<[^>]+>", "")).toList());
        assertEquals(
                "<gray><dark_purple>흑마법사</dark_purple> 타워는 1기만 설치할 수 있습니다.</gray>",
                baseDescriptionLines.get(1)
        );
    }

    @Test
    void sacrificeDescriptionsStateTheDeathDebuffDirectly() {
        String meleeTierTwo = plainDescription(WarlockTowers.T2_SLAVE);
        String meleeTierThree = plainDescription(WarlockTowers.T3_SLAVE);
        String rangedTierTwo = plainDescription(WarlockTowers.T2_RANGED_SLAVE);
        String rangedTierThree = plainDescription(WarlockTowers.T3_RANGED_SLAVE);

        assertTrue(meleeTierTwo.contains("사망 시 주위 20블록 내 적이 받는 피해를 10% 증가시킵니다."));
        assertTrue(meleeTierThree.contains("사망 시 주위 20블록 내 적이 받는 피해를 10% 증가시킵니다."));
        assertTrue(rangedTierTwo.contains("사망 시 주위 20블록 내 적의 공격 속도를 10% 감소시킵니다."));
        assertTrue(rangedTierThree.contains("사망 시 주위 20블록 내 적의 공격 속도를 10% 감소시킵니다."));
        assertFalse(meleeTierThree.contains("해당 라운드 동안"));
        assertFalse(rangedTierThree.contains("해당 라운드 동안"));
    }

    private static String plainDescription(kim.biryeong.semiontd.tower.TowerType type) {
        return String.join("\n", TowerBalanceRuntime.resolve(type).description()).replaceAll("<[^>]+>", "");
    }

    @Test
    void jobDescriptionCommunicatesTheCompleteBuilderFantasy() {
        String description = new WarlockTowerJob().description().stream()
                .map(component -> component.getString())
                .collect(java.util.stream.Collectors.joining("\n"));

        assertTrue(description.contains("희생으로 성장해"));
        assertTrue(description.contains("원거리·근거리 중 선택"));
        assertTrue(description.contains("1250킬 후 최후 생존·저체력에서 각성"));
        assertTrue(new WarlockTowerJob().description().stream()
                .allMatch(component -> component.getString().length() <= 31));

        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> global = new LinkedHashMap<>(abilities.get(WarlockTowers.CONFIG_ID));
        global.put("awakeningKills", 42.0);
        abilities.put(WarlockTowers.CONFIG_ID, global);
        TowerBalanceRuntime.apply(new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities));

        String configuredDescription = new WarlockTowerJob().description().stream()
                .map(component -> component.getString())
                .collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(configuredDescription.contains("42킬 후 최후 생존·저체력에서 각성"));
        assertFalse(configuredDescription.contains("1250킬 후 최후 생존·저체력에서 각성"));
    }

    @Test
    void runtimeStatsShowAbsorptionAndAccumulatedCombatValues() {
        WarlockTower tower = new WarlockTower(
                TowerBalanceRuntime.resolve(WarlockTowers.RANGED_WARLOCK_TOWER),
                UUID.randomUUID(),
                TeamId.RED,
                0,
                new GridPosition(0, 0, 0)
        );
        String details = String.join("\n", tower.runtimeDetailLines()).replaceAll("<[^>]+>", "");
        assertEquals(7.0, tower.adjustAttackRange(7.0), 0.0001);
        assertEquals(5, tower.minimumAttackIntervalTicks());
        assertEquals(0.05, tower.incomeDebuffResistance(), 0.0001);
        assertTrue(details.contains("영구 흡수: 0기"));
        assertTrue(details.contains("라운드 흡수: 0기"));
        assertTrue(details.contains("각성 해금: 0/1250킬"));
        assertFalse(details.contains("최후 생존"));
        assertTrue(details.contains("영구 체력: +0"));
        assertFalse(details.contains("재생:"));
        assertTrue(details.contains("생명력 흡수: +0% (10)"));
        assertTrue(details.contains("피해 감소: +0% (4)"));
        assertTrue(details.contains("영구 피해: +0"));
        assertTrue(details.contains("공격 속도: -0틱"));
        assertTrue(details.contains("공격 범위: +0 블록 (2)"));
        assertEquals("<#53DFFF>🛡 디버프 저항</#53DFFF><white>: </white><#53DFFF>+5%</#53DFFF>", tower.runtimeDetailLines().getLast());
        UUID baseOwner = UUID.randomUUID();
        WarlockTower base = new WarlockTower(
                TowerBalanceRuntime.resolve(WarlockTowers.BASE_WARLOCK_TOWER),
                baseOwner,
                TeamId.RED,
                0,
                new GridPosition(0, 0, 0)
        );
        String baseLockedDetails = String.join("\n", base.runtimeDetailLines()).replaceAll("<[^>]+>", "");
        assertEquals(0.0, base.incomeDebuffResistance(), 0.0001);
        assertFalse(baseLockedDetails.contains("디버프 저항:"));
        assertTrue(baseLockedDetails.contains("각성 해금: 0/1250킬"));
        for (int kill = 0; kill < 1250; kill++) {
            WarlockAwakeningProgress.recordKill(baseOwner);
        }
        String baseUnlockedDetails = String.join("\n", base.runtimeDetailLines()).replaceAll("<[^>]+>", "");
        assertTrue(baseUnlockedDetails.contains("각성 해금: 완료 · 분기 선택 필요"));
        assertFalse(baseUnlockedDetails.contains("각성 조건:"));
        WarlockTower melee = new WarlockTower(
                TowerBalanceRuntime.resolve(WarlockTowers.MELEE_WARLOCK_TOWER),
                UUID.randomUUID(),
                TeamId.RED,
                0,
                new GridPosition(0, 0, 0)
        );
        String meleeDetails = String.join("\n", melee.runtimeDetailLines()).replaceAll("<[^>]+>", "");
        assertEquals(5, melee.minimumAttackIntervalTicks());
        assertEquals(0.05, melee.incomeDebuffResistance(), 0.0001);
        assertTrue(meleeDetails.contains("영구 흡수: 0기"));
        assertTrue(meleeDetails.contains("라운드 흡수: 0기"));
        assertTrue(meleeDetails.contains("각성 해금: 0/1250킬"));
        assertTrue(meleeDetails.contains("영구 체력: +0"));
        assertFalse(meleeDetails.contains("재생:"));
        assertTrue(meleeDetails.contains("생명력 흡수: +0% (1)"));
        assertTrue(meleeDetails.contains("피해 감소: +0% (10)"));
        assertTrue(meleeDetails.contains("영구 피해: +0"));
        assertTrue(meleeDetails.contains("공격 속도: -0틱 (1)"));
        assertTrue(meleeDetails.contains("공격 범위: +0 블록 (1)"));
        assertEquals("<#53DFFF>🛡 디버프 저항</#53DFFF><white>: </white><#53DFFF>+5%</#53DFFF>", melee.runtimeDetailLines().getLast());
    }

    @Test
    void statsViewIncludesRequestedWarlockStats() {
        List<String> lines = WarlockStatsView.core(
                new WarlockStatsView.CoreStats(
                        12,
                        7,
                        true,
                        true,
                        new WarlockStatsView.AwakeningStats(1250, 1250, true, 0.35, 0.40, true, 40.0, 0.0, 0.0),
                        true,
                        false,
                        new WarlockStatsView.CombatStats(
                                42.5,
                                4,
                                15,
                                1.5,
                                8.0,
                                true
                        ),
                        new WarlockStatsView.DefenseStats(
                                75.0,
                                0.08,
                                0.08,
                                0.10,
                                0.10,
                                0.05
                        )
                )
        );
        assertEquals("<white>영구 흡수: <dark_purple>12기</dark_purple></white>", lines.get(0));
        assertEquals("<white>라운드 흡수: <dark_purple>7기</dark_purple></white>", lines.get(1));
        assertEquals("<white>각성 상태: <dark_purple>각성 완료</dark_purple></white>", lines.get(2));
        String details = String.join("\n", lines).replaceAll("<[^>]+>", "");
        assertTrue(details.contains("영구 흡수: 12기"));
        assertTrue(details.contains("라운드 흡수: 7기"));
        assertTrue(details.contains("각성 상태: 각성 완료"));
        assertTrue(details.contains("영구 체력: +75"));
        assertTrue(details.contains("재생: +40 HP/s"));
        assertTrue(details.contains("생명력 흡수: +8% (MAX)"));
        assertTrue(details.contains("피해 감소: +10% (MAX)"));
        assertTrue(details.contains("영구 피해: +42.5"));
        assertTrue(details.contains("공격 속도: -4틱"));
        assertTrue(details.contains("공격 범위: +1.5 블록 (14)"));
        assertEquals("<#53DFFF>🛡 디버프 저항</#53DFFF><white>: </white><#53DFFF>+5%</#53DFFF>", lines.get(lines.size() - 2));
        assertEquals("<#20985d>➕ 재생</#20985d><white>: </white><#20985d>+40 HP/s</#20985d>", lines.getLast());

        List<String> awakenedMeleeLines = WarlockStatsView.core(
                new WarlockStatsView.CoreStats(
                        12,
                        7,
                        true,
                        true,
                        new WarlockStatsView.AwakeningStats(1250, 1250, true, 0.35, 0.40, true, 0.0, 75.0, 0.30),
                        false,
                        true,
                        new WarlockStatsView.CombatStats(42.5, 4, 15, 1.5, 8.0, true),
                        new WarlockStatsView.DefenseStats(75.0, 0.08, 0.08, 0.10, 0.10, 0.05)
                )
        );
        assertEquals("<#53DFFF>🛡 디버프 저항</#53DFFF><white>: </white><#53DFFF>+5%</#53DFFF>", awakenedMeleeLines.get(awakenedMeleeLines.size() - 3));
        assertEquals("<#ec8d34>🪓 추가 피해</#ec8d34><white>: </white><#ec8d34>75</#ec8d34>", awakenedMeleeLines.get(awakenedMeleeLines.size() - 2));
        assertEquals("<#F1E7D4>👟 이동 속도</#F1E7D4><white>: </white><#F1E7D4>+30%</#F1E7D4>", awakenedMeleeLines.getLast());

        List<String> compressedLines = WarlockStatsView.core(
                new WarlockStatsView.CoreStats(
                        100,
                        20,
                        false,
                        false,
                        new WarlockStatsView.AwakeningStats(0, 1250, false, 0.35, 0.40, true, 0.0, 0.0, 0.0),
                        true,
                        false,
                        new WarlockStatsView.CombatStats(
                                247.2593,
                                15,
                                15,
                                8.0,
                                8.0,
                                true
                        ),
                        new WarlockStatsView.DefenseStats(
                                4395.8797,
                                0.0,
                                0.08,
                                0.0,
                                0.10,
                                0.05
                        )
                )
        );
        String compressedDetails = String.join("\n", compressedLines).replaceAll("<[^>]+>", "");
        assertTrue(compressedDetails.contains("영구 체력: +4395.88"));
        assertTrue(compressedDetails.contains("영구 피해: +247.26"));
    }
}
