package kim.biryeong.semiontd.tower.warlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals(85, config.upgradeCost(
                WarlockTowers.T1_SLAVE.id(),
                WarlockTowers.T2_SLAVE.id(),
                -1
        ));
        assertEquals(135, config.upgradeCost(
                WarlockTowers.T2_SLAVE.id(),
                WarlockTowers.T3_SLAVE.id(),
                -1
        ));
        assertEquals(90, config.upgradeCost(
                WarlockTowers.T1_RANGED_SLAVE.id(),
                WarlockTowers.T2_RANGED_SLAVE.id(),
                -1
        ));
        assertEquals(140, config.upgradeCost(
                WarlockTowers.T2_RANGED_SLAVE.id(),
                WarlockTowers.T3_RANGED_SLAVE.id(),
                -1
        ));

        assertEquals(180.0, config.ability(WarlockTower.CONFIG_ID, "damageSoftCap", -1.0), 0.0001);
        assertEquals(-1.0, config.ability(WarlockTower.CONFIG_ID, "damageCap", -1.0), 0.0001);
        assertEquals(0.085, config.ability(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "lifeCap", -1.0), 0.0001);
        assertEquals(0.16, config.ability(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "lifeCap", -1.0), 0.0001);
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
        assertEquals(150.0, WarlockTower.scaledDamageBonus(150.0), 0.0001);
        assertEquals(271.9486, WarlockTower.scaledDamageBonus(300.0), 0.0001);
        assertEquals(396.7151, WarlockTower.scaledDamageBonus(600.0), 0.0001);
    }

    @Test
    void damageScalingConfigRejectsInvalidRangesAndMergesLegacyFiles() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> invalidAbilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> invalidWarlock = new LinkedHashMap<>(invalidAbilities.get(WarlockTowers.CONFIG_ID));
        invalidWarlock.put("damageSoftCap", 0.0);
        invalidAbilities.put(WarlockTowers.CONFIG_ID, invalidWarlock);
        TowerBalanceConfig invalid = new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), invalidAbilities);
        assertThrows(IllegalArgumentException.class, () -> TowerBalanceRuntime.apply(invalid));

        Map<String, Map<String, Double>> legacyAbilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> legacyWarlock = new LinkedHashMap<>(legacyAbilities.get(WarlockTowers.CONFIG_ID));
        legacyWarlock.remove("damageSoftCap");
        legacyWarlock.put("damageCap", 340.0);
        legacyAbilities.put(WarlockTowers.CONFIG_ID, legacyWarlock);
        TowerBalanceConfig merged = new TowerBalanceConfig(
                defaults.towers(),
                defaults.upgradeCosts(),
                legacyAbilities
        ).withMissingDefaults(defaults);
        assertEquals(180.0, merged.ability(WarlockTowers.CONFIG_ID, "damageSoftCap", -1.0), 0.0001);
        TowerBalanceRuntime.apply(merged);
        assertEquals(396.7151, WarlockTower.scaledDamageBonus(600.0), 0.0001);
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
    void meleeLifeStealUsesCurrentRoundAbsorptionsAndCapsAtPercent() {
        WarlockCombat combat = new WarlockCombat(WarlockConfig.RUNTIME);

        assertEquals(0.0, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 20, 0), 0.0001);
        assertEquals(0.03, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 20, 3), 0.0001);
        assertEquals(0.15, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 40, 15), 0.0001);
        assertEquals(0.16, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 40, 16), 0.0001);
        assertEquals(0.16, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 40, 17), 0.0001);
        assertEquals(0.16, combat.lifeStealRatioForCounts(WarlockTowers.MELEE_WARLOCK_TOWER, 40, 25), 0.0001);
        assertEquals(0.01, combat.lifeStealRatioForCounts(WarlockTowers.RANGED_WARLOCK_TOWER, 10, 3), 0.0001);
    }

    @Test
    void descriptionUsesAttackRangeTerminologyAndExactStep() {
        assertFalse(WarlockConfig.AWAKENING_ENABLED);
        String description = String.join("\n", TowerBalanceRuntime.resolve(WarlockTowers.RANGED_WARLOCK_TOWER).description()).replaceAll("<[^>]+>", "");
        assertTrue(description.contains("체력 55% 이하이면"));
        assertTrue(description.contains("주위 25 블록 내"));
        assertTrue(description.contains("흡수한 타워 체력과 피해의 40%"));
        assertTrue(description.contains("이번 라운드 동안 획득"));
        assertTrue(description.contains("흡수한 타워마다"));
        assertTrue(description.contains("체력 +2.5%"));
        assertTrue(description.contains("피해 +5%"));
        assertTrue(description.contains("영구 누적"));
        assertFalse(description.contains("각성"));
        assertFalse(description.contains("공격 속도"));
        assertFalse(description.contains("공격 범위"));
        assertFalse(description.contains("생명력 흡수"));
        assertFalse(description.contains("피해 감소"));
        assertFalse(description.contains("애완 타워마다"));
        assertFalse(description.contains("스플래시 범위"));
        assertFalse(description.contains("중첩 제한 없음"));
        String meleeDescription = String.join("\n", TowerBalanceRuntime.resolve(WarlockTowers.MELEE_WARLOCK_TOWER).description()).replaceAll("<[^>]+>", "");
        assertTrue(meleeDescription.contains("체력 55% 이하이면"));
        assertTrue(meleeDescription.contains("주위 25 블록 내"));
        assertTrue(meleeDescription.contains("흡수한 타워 체력과 피해의 60%"));
        assertTrue(meleeDescription.contains("이번 라운드 동안 획득"));
        assertTrue(meleeDescription.contains("흡수한 타워마다"));
        assertTrue(meleeDescription.contains("체력 +5%"));
        assertTrue(meleeDescription.contains("피해 +2.5%"));
        assertTrue(meleeDescription.contains("영구 누적"));
        assertFalse(meleeDescription.contains("각성"));
        assertFalse(meleeDescription.contains("공격 속도"));
        assertFalse(meleeDescription.contains("공격 범위"));
        assertFalse(meleeDescription.contains("생명력 흡수"));
        assertFalse(meleeDescription.contains("피해 감소"));
        assertFalse(meleeDescription.contains("희생양마다"));
        assertFalse(meleeDescription.contains("스플래시 범위"));
        assertFalse(meleeDescription.contains("중첩 제한 없음"));
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
        assertEquals(500.0, tower.modifyResolvedOutgoingDamage(null, null, 500.0), 0.0001);
        assertEquals(500.0, tower.modifyAppliedDamage(null, null, 500.0), 0.0001);
        assertEquals(5, tower.minimumAttackIntervalTicks());
        assertTrue(details.contains("흡수한 타워: 0기"));
        assertTrue(details.contains("이번 라운드에 흡수한 타워: 0기"));
        assertFalse(details.contains("각성 상태:"));
        assertFalse(details.contains("피해량 상한"));
        assertTrue(details.contains("영구 체력: +0"));
        assertFalse(details.contains("재생:"));
        assertTrue(details.contains("생명력 흡수: +0% (5)"));
        assertTrue(details.contains("피해 감소: +0% (4)"));
        assertTrue(details.contains("영구 피해: +0"));
        assertTrue(details.contains("공격 속도: -0틱"));
        assertTrue(details.contains("공격 범위: +0 블록 (1)"));
        assertFalse(details.contains("제한 없음"));
        assertFalse(details.contains("스플래시 범위:"));
        assertFalse(details.contains("최종 피해 제외"));
        assertFalse(details.contains("받는 피해 감소:"));
        WarlockTower melee = new WarlockTower(
                TowerBalanceRuntime.resolve(WarlockTowers.MELEE_WARLOCK_TOWER),
                UUID.randomUUID(),
                TeamId.RED,
                0,
                new GridPosition(0, 0, 0)
        );
        String meleeDetails = String.join("\n", melee.runtimeDetailLines()).replaceAll("<[^>]+>", "");
        assertEquals(5, melee.minimumAttackIntervalTicks());
        assertTrue(meleeDetails.contains("흡수한 타워: 0기"));
        assertTrue(meleeDetails.contains("이번 라운드에 흡수한 타워: 0기"));
        assertFalse(meleeDetails.contains("각성 상태:"));
        assertTrue(meleeDetails.contains("영구 체력: +0"));
        assertFalse(meleeDetails.contains("재생:"));
        assertTrue(meleeDetails.contains("생명력 흡수: +0% (1)"));
        assertTrue(meleeDetails.contains("피해 감소: +0% (5)"));
        assertTrue(meleeDetails.contains("영구 피해: +0"));
        assertTrue(meleeDetails.contains("공격 속도: -0틱 (1)"));
        assertTrue(meleeDetails.contains("공격 범위: +0 블록 (1)"));
        assertFalse(meleeDetails.contains("피해량 상한"));
        assertFalse(meleeDetails.contains("제한 없음"));
        assertFalse(meleeDetails.contains("스플래시 범위:"));
        assertFalse(meleeDetails.contains("받는 피해 감소:"));
    }

    @Test
    void statsViewIncludesRequestedWarlockStats() {
        List<String> lines = WarlockStatsView.core(
                new WarlockStatsView.CoreStats(
                        12,
                        7,
                        true,
                        true,
                        true,
                        false,
                        new WarlockStatsView.CombatStats(
                                42.5,
                                42.5,
                                4,
                                15,
                                1.5,
                                8.0,
                                true
                        ),
                        new WarlockStatsView.DefenseStats(
                                75.0,
                                40.0,
                                40.0,
                                0.085,
                                0.085,
                                0.10,
                                0.10
                        )
                )
        );
        String details = String.join("\n", lines).replaceAll("<[^>]+>", "");
        assertTrue(details.contains("흡수한 타워: 12기"));
        assertTrue(details.contains("이번 라운드에 흡수한 타워: 7기"));
        assertTrue(details.contains("각성 상태: 각성"));
        assertFalse(details.contains("피해량 상한"));
        assertTrue(details.contains("영구 체력: +75"));
        assertTrue(details.contains("재생: +40 HP/s"));
        assertFalse(details.contains("재생: +40 HP/s (MAX)"));
        assertTrue(details.contains("생명력 흡수: +8.5% (MAX)"));
        assertTrue(details.contains("피해 감소: +10% (MAX)"));
        assertTrue(details.contains("영구 피해: +42.5"));
        assertTrue(details.contains("공격 속도: -4틱"));
        assertTrue(details.contains("공격 범위: +1.5 블록 (13)"));
        assertFalse(details.contains("제한 없음"));
        assertFalse(details.contains("스플래시 범위:"));
        assertFalse(details.contains("받는 피해 감소:"));

        List<String> compressedLines = WarlockStatsView.core(
                new WarlockStatsView.CoreStats(
                        100,
                        20,
                        false,
                        false,
                        true,
                        false,
                        new WarlockStatsView.CombatStats(
                                600.0,
                                396.7151,
                                15,
                                15,
                                8.0,
                                8.0,
                                true
                        ),
                        new WarlockStatsView.DefenseStats(0.0, 0.0, 0.0, 0.0, 0.085, 0.0, 0.10)
                )
        );
        assertTrue(String.join("\n", compressedLines).replaceAll("<[^>]+>", "")
                .contains("영구 피해: +396.72 (누적 600)"));
    }
}
