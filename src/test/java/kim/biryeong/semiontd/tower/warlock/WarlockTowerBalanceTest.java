package kim.biryeong.semiontd.tower.warlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
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

        assertEquals(175.0, config.ability(WarlockTower.CONFIG_ID, "damageThreshold", -1.0), 0.0001);
        assertEquals(25.0, config.ability(WarlockTower.CONFIG_ID, "damageScale", -1.0), 0.0001);
        assertEquals(3500.0, config.ability(WarlockTower.CONFIG_ID, "healthThreshold", -1.0), 0.0001);
        assertEquals(500.0, config.ability(WarlockTower.CONFIG_ID, "healthScale", -1.0), 0.0001);
        assertEquals(-1.0, config.ability(WarlockTower.CONFIG_ID, "damageSoftCap", -1.0), 0.0001);
        assertEquals(-1.0, config.ability(WarlockTower.CONFIG_ID, "damageCap", -1.0), 0.0001);
        assertEquals(0.085, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "lifeCap", -1.0), 0.0001);
        assertEquals(0.12, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "lifeCap", -1.0), 0.0001);
        assertEquals(-1.0, config.ability(WarlockTower.CONFIG_ID, "splashStep", -1.0), 0.0001);
        assertEquals(-1.0, config.ability(WarlockTower.CONFIG_ID, "splashCap", -1.0), 0.0001);
        assertEquals(0.1, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "splashStep", -1.0), 0.0001);
        assertEquals(8.0, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "splashCap", -1.0), 0.0001);
        assertEquals(25.0, config.ability(WarlockTower.CONFIG_ID, "sacrificeRadius", -1.0), 0.0001);
        assertEquals(5.0, config.ability(WarlockTower.CONFIG_ID, "minInterval", -1.0), 0.0001);
        assertEquals(6.0, config.ability(WarlockTowers.BASE_WARLOCK_TOWER.id(), "sacrificeRadius", -1.0), 0.0001);
        assertEquals(-1.0, config.ability(WarlockTower.CONFIG_ID, "attackRangeStep", -1.0), 0.0001);
        assertEquals(20.0, config.ability(WarlockTower.CONFIG_ID, "awakeningAbsorptions", -1.0), 0.0001);
        assertEquals(0.40, config.ability(WarlockTower.CONFIG_ID, "awakeningThreshold", -1.0), 0.0001);
        assertEquals(0.55, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "threshold", -1.0), 0.0001);
        assertEquals(0.40, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "roundStat", -1.0), 0.0001);
        assertEquals(15.0, config.ability(WarlockTower.CONFIG_ID, "speedCap", -1.0), 0.0001);
        assertEquals(-1.0, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "speedCap", -1.0), 0.0001);
        assertEquals(3.0, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "defenseThreshold", -1.0), 0.0001);
        assertEquals(0.50, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "splashDamage", -1.0), 0.0001);
        assertEquals(0.15, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "petHealthCap", -1.0), 0.0001);
        assertEquals(0.75, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "petDamageCap", -1.0), 0.0001);
        assertEquals(400.0, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "awakeningHeal", -1.0), 0.0001);
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
        assertEquals(0.75, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "petHealthCap", -1.0), 0.0001);
        assertEquals(0.15, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "petDamageCap", -1.0), 0.0001);
        assertEquals(List.of(
                "damageThreshold", "damageScale", "healthThreshold", "healthScale",
                "sacrificeRadius", "minInterval", "speedCap", "awakeningAbsorptions", "awakeningThreshold"
        ), List.copyOf(config.abilities().get(WarlockTowers.CONFIG_ID).keySet()));
        assertEquals(List.of(
                "sacrificeRadius", "fatalHeal", "permanentHealth", "permanentDamage"
        ), List.copyOf(config.abilities().get(WarlockTowers.BASE_WARLOCK_TOWER.id()).keySet()));
        assertEquals(List.of(
                "threshold", "roundStat", "permanentHealth", "permanentDamage", "lifeEvery", "lifeStep", "lifeCap",
                "splashStep", "splashCap", "splashDamage", "defenseThreshold", "defense", "petHealth", "petHealthCap",
                "petDamage", "petDamageCap", "awakeningHeal", "awakeningRegeneration", "awakeningRegenerationTicks"
        ), List.copyOf(config.abilities().get(WarlockTowers.RANGED_WARLOCK_TOWER.id()).keySet()));
        assertEquals(List.of(
                "threshold", "roundStat", "permanentHealth", "permanentDamage", "lifeStep", "lifeCap", "speedStep",
                "splashStep", "splashCap", "splashDamage", "defenseEvery", "defenseStep", "defenseCap", "petHealth",
                "petHealthCap", "petDamage", "petDamageCap", "awakeningDamage", "awakeningMoveSpeed"
        ), List.copyOf(config.abilities().get(WarlockTowers.MELEE_WARLOCK_TOWER.id()).keySet()));
    }

    @Test
    void combatCapsDamageAndSplashRadius() {
        WarlockCombat combat = new WarlockCombat(WarlockConfig.RUNTIME);

        assertEquals(0.1, combat.splashRadiusForCount(1), 0.0001);
        assertEquals(0.8, combat.splashRadiusForCount(8), 0.0001);
        assertEquals(6.4, combat.splashRadiusForCount(64), 0.0001);
        assertEquals(8.0, combat.splashRadiusForCount(100), 0.0001);
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
        assertEquals(108.0, WarlockTower.scaledDamageBonus(108.0), 0.0001);
        assertEquals(175.0, WarlockTower.scaledDamageBonus(175.0), 0.0001);
        assertEquals(219.7940, WarlockTower.scaledDamageBonus(300.0), 0.0001);
        assertEquals(247.2593, WarlockTower.scaledDamageBonus(600.0), 0.0001);
    }

    @Test
    void liveHealthCurvePreservesNormalAbsorptionsAndLimitsExtremeGrowth() {
        assertEquals(3000.0, WarlockTower.scaledHealthBonus(3000.0), 0.0001);
        assertEquals(3500.0, WarlockTower.scaledHealthBonus(3500.0), 0.0001);
        assertEquals(4395.8797, WarlockTower.scaledHealthBonus(6000.0), 0.0001);
    }

    @Test
    void damageScalingConfigAcceptsZeroAndBackfillsMissingValues() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> invalidAbilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> invalidWarlock = new LinkedHashMap<>(invalidAbilities.get(WarlockTowers.CONFIG_ID));
        invalidWarlock.put("damageThreshold", 0.0);
        invalidAbilities.put(WarlockTowers.CONFIG_ID, invalidWarlock);
        TowerBalanceConfig zero = new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), invalidAbilities);
        assertDoesNotThrow(() -> TowerBalanceRuntime.apply(zero));

        Map<String, Map<String, Double>> partialAbilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> partialWarlock = new LinkedHashMap<>(partialAbilities.get(WarlockTowers.CONFIG_ID));
        partialWarlock.remove("damageThreshold");
        partialWarlock.remove("damageScale");
        partialWarlock.remove("healthThreshold");
        partialWarlock.remove("healthScale");
        partialAbilities.put(WarlockTowers.CONFIG_ID, partialWarlock);
        TowerBalanceConfig merged = new TowerBalanceConfig(
                defaults.towers(),
                defaults.upgradeCosts(),
                partialAbilities
        ).withMissingDefaults(defaults);
        assertEquals(175.0, merged.ability(WarlockTowers.CONFIG_ID, "damageThreshold", -1.0), 0.0001);
        assertEquals(25.0, merged.ability(WarlockTowers.CONFIG_ID, "damageScale", -1.0), 0.0001);
        assertEquals(3500.0, merged.ability(WarlockTowers.CONFIG_ID, "healthThreshold", -1.0), 0.0001);
        assertEquals(500.0, merged.ability(WarlockTowers.CONFIG_ID, "healthScale", -1.0), 0.0001);
        TowerBalanceRuntime.apply(merged);
        assertEquals(247.2593, WarlockTower.scaledDamageBonus(600.0), 0.0001);
    }

    @Test
    void rangedLifeStealGrowsEveryFiveAbsorptionsAndCapsAtPercent() {
        WarlockCombat combat = new WarlockCombat(WarlockConfig.RUNTIME);

        assertEquals(0.0, combat.lifeStealRatioForCount(WarlockTowers.RANGED_WARLOCK_TOWER, 4), 0.0001);
        assertEquals(0.005, combat.lifeStealRatioForCount(WarlockTowers.RANGED_WARLOCK_TOWER, 5), 0.0001);
        assertEquals(0.005, combat.lifeStealRatioForCount(WarlockTowers.RANGED_WARLOCK_TOWER, 9), 0.0001);
        assertEquals(0.01, combat.lifeStealRatioForCount(WarlockTowers.RANGED_WARLOCK_TOWER, 10), 0.0001);
        assertEquals(0.08, combat.lifeStealRatioForCount(WarlockTowers.RANGED_WARLOCK_TOWER, 80), 0.0001);
        assertEquals(0.085, combat.lifeStealRatioForCount(WarlockTowers.RANGED_WARLOCK_TOWER, 85), 0.0001);
        assertEquals(0.085, combat.lifeStealRatioForCount(WarlockTowers.RANGED_WARLOCK_TOWER, 90), 0.0001);
        assertEquals(0.085, combat.lifeStealRatioForCount(WarlockTowers.RANGED_WARLOCK_TOWER, 200), 0.0001);
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
        assertEquals(0.12, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 40, 13), 0.0001);
        assertEquals(0.12, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 40, 25), 0.0001);
        assertEquals(0.0, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 20, 3, false), 0.0001);
        assertEquals(0.0, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 0, 0, true), 0.0001);
        assertEquals(0.03, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 20, 3, true), 0.0001);
        assertEquals(0.12, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 40, 20, true), 0.0001);
        assertEquals(0.01, combat.lifeStealRatioForCounts(WarlockTowers.RANGED_WARLOCK_TOWER, 10, 3), 0.0001);
        assertEquals(0.01, combat.lifeStealRatioForCounts(WarlockTowers.RANGED_WARLOCK_TOWER, 10, 3, true), 0.0001);
    }

}
