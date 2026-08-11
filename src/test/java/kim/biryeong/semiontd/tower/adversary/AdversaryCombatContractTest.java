package kim.biryeong.semiontd.tower.adversary;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectSet;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pure/runtime-boundary contracts for the Adversary combat implementation. */
class AdversaryCombatContractTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("adversary-combat-owner".getBytes());
    private static final UUID OTHER = UUID.nameUUIDFromBytes("adversary-combat-other".getBytes());
    private static final GridPosition POSITION = new GridPosition(3, 64, 5);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void resetBalance() {
        AdversaryProgressStates.clearAllForTesting();
        AdversaryTeamEffects.clearAllForTesting();
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @AfterEach
    void clearProgress() {
        AdversaryProgressStates.clearAllForTesting();
        AdversaryTeamEffects.clearAllForTesting();
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void formChangesKeepHealthRatioAndTheSameLogicalFox() {
        AdversaryFoxTower fox = fox();
        fox.syncHealth(87.5);

        fox.setForm(FoxForm.BEACON_KEEPER, null);

        assertAll(
                () -> assertSame(AdversaryTowers.FOX, fox.type()),
                () -> assertEquals(OWNER, fox.ownerPlayer()),
                () -> assertEquals(FoxForm.BEACON_KEEPER, fox.form()),
                () -> assertEquals(1_600.0, fox.currentMaxHealth(), 0.0001),
                () -> assertEquals(400.0, fox.health(), 0.0001),
                () -> assertEquals(
                        FoxForm.BEACON_KEEPER,
                        AdversaryProgressStates.currentForm(OWNER)
                )
        );

        fox.setForm(FoxForm.SCULK_CORE, null);
        assertEquals(1_100.0, fox.currentMaxHealth(), 0.0001);
        assertEquals(275.0, fox.health(), 0.0001);

        fox.setForm(FoxForm.BASE, null);
        assertEquals(350.0, fox.currentMaxHealth(), 0.0001);
        assertEquals(87.5, fox.health(), 0.0001);
    }

    @Test
    void defensiveFormsApplyOnlyTheirApprovedDamageReduction() {
        AdversaryFoxTower fox = fox();

        assertIncomingDamage(fox, FoxForm.BASE, 100.0);
        assertIncomingDamage(fox, FoxForm.SHIELD_BEARER, 80.0);
        assertIncomingDamage(fox, FoxForm.BEACON_KEEPER, 70.0);
        assertIncomingDamage(fox, FoxForm.OMINOUS_HEXER, 88.0);
        assertIncomingDamage(fox, FoxForm.MACE_EXECUTIONER, 100.0);
        assertIncomingDamage(fox, FoxForm.SCULK_CORE, 100.0);
    }

    @Test
    void ordinaryAttackPipelineUsesEachFormAndSpecialFormsKeepZeroDamageAimRays() {
        AdversaryFoxTower fox = fox();

        assertBasicAttack(fox, FoxForm.BASE, 18.0, 10);
        assertBasicAttack(fox, FoxForm.BREEZE, 30.0, 4);
        assertBasicAttack(fox, FoxForm.GOLDEN_FANG, 36.0, 3);
        assertBasicAttack(fox, FoxForm.SHIELD_BEARER, 75.0, 7);
        assertBasicAttack(fox, FoxForm.BELL_KEEPER, 70.0, 7);
        assertBasicAttack(fox, FoxForm.BEACON_KEEPER, 90.0, 6);
        assertBasicAttack(fox, FoxForm.OMINOUS_HEXER, 90.0, 6);
        assertBasicAttack(fox, FoxForm.TRACKER, 60.0, 7);
        assertBasicAttack(fox, FoxForm.FIREWORK_PIERCER, 108.0, 8);
        assertBasicAttack(fox, FoxForm.BIG_GAME_TRACKER, 96.0, 16);
        assertBasicAttack(fox, FoxForm.ECHO_FOX, 90.0, 8);
        assertBasicAttack(fox, FoxForm.MACE_EXECUTIONER, 0.0, 50);
        assertBasicAttack(fox, FoxForm.SCULK_CORE, 0.0, 100);
    }

    @Test
    void beaconAttackSpeedBonusAlsoAcceleratesMaceAndSculkBaseIntervals() {
        AdversaryFoxTower fox = fox();
        TimedEffectSet effects = new TimedEffectSet();
        effects.apply(
                TimedEffectType.TOWER_ATTACK_SPEED_BONUS,
                AdversaryBalance.BEACON_TEAM_ATTACK_SPEED_BONUS,
                AdversaryBalance.TEAM_EFFECT_DURATION_TICKS
        );

        fox.setForm(FoxForm.MACE_EXECUTIONER, null);
        int maceInterval = genericAttackInterval(fox, effects);
        fox.setForm(FoxForm.SCULK_CORE, null);
        int sculkInterval = genericAttackInterval(fox, effects);

        assertAll(
                () -> assertEquals(1, fox.minimumAttackIntervalTicks()),
                () -> assertEquals(46, maceInterval),
                () -> assertEquals(91, sculkInterval)
        );
    }

    @Test
    void teamSupportUsesTheStrongestValuePerChannelAndBuffsCoexistWithDebuffs() {
        AdversaryTeamEffects.TeamProfile profile = AdversaryTeamEffects.strongestProfile(List.of(
                FoxForm.BELL_KEEPER,
                FoxForm.BELL_KEEPER,
                FoxForm.BEACON_KEEPER,
                FoxForm.BEACON_KEEPER,
                FoxForm.OMINOUS_HEXER,
                FoxForm.OMINOUS_HEXER
        ));

        assertAll(
                () -> assertEquals(AdversaryBalance.BEACON_TEAM_DAMAGE_BONUS,
                        profile.towerDamageBonus(), 0.0001),
                () -> assertEquals(AdversaryBalance.BEACON_TEAM_ATTACK_SPEED_BONUS,
                        profile.towerAttackSpeedBonus(), 0.0001),
                () -> assertEquals(AdversaryBalance.BEACON_TEAM_MAX_HEALTH_BONUS,
                        profile.towerMaxHealthBonus(), 0.0001),
                () -> assertEquals(AdversaryBalance.OMINOUS_MONSTER_DAMAGE_REDUCTION,
                        profile.monsterDamageReduction(), 0.0001),
                () -> assertEquals(AdversaryBalance.OMINOUS_MONSTER_ATTACK_SPEED_REDUCTION,
                        profile.monsterAttackSpeedReduction(), 0.0001),
                () -> assertEquals(AdversaryBalance.OMINOUS_MONSTER_TOWER_DAMAGE_TAKEN_BONUS,
                        profile.monsterTowerDamageTakenBonus(), 0.0001),
                () -> assertEquals(20, AdversaryBalance.TEAM_EFFECT_SCAN_INTERVAL_TICKS),
                () -> assertEquals(40, AdversaryBalance.TEAM_EFFECT_DURATION_TICKS)
        );
    }

    @Test
    void liveBalanceOverridesDriveFormsRecipesCombatAndTeamProfiles() {
        AdversaryFoxTower fox = fox();
        fox.setForm(FoxForm.BEACON_KEEPER, null);
        fox.syncHealth(800.0);

        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = mutableAbilities(defaults.abilities());
        abilities.get(AdversaryBalance.formConfigId(FoxForm.BEACON_KEEPER)).put("maxHealth", 2_000.0);
        abilities.get(AdversaryBalance.formConfigId(FoxForm.BEACON_KEEPER)).put("range", 6.0);
        abilities.get(AdversaryBalance.formConfigId(FoxForm.BEACON_KEEPER)).put("damage", 100.0);
        abilities.get(AdversaryBalance.formConfigId(FoxForm.BEACON_KEEPER)).put("attackIntervalTicks", 5.0);
        abilities.get(AdversaryBalance.formConfigId(FoxForm.BEACON_KEEPER)).put("damageReduction", 0.40);
        abilities.get(AdversaryBalance.formConfigId(FoxForm.BEACON_KEEPER)).put("requiredPhantomScore", 60.0);
        abilities.get(AdversaryBalance.GLOBAL_CONFIG_ID).put("beaconTeamDamageBonus", 0.08);
        TowerBalanceRuntime.apply(new TowerBalanceConfig(
                defaults.towers(),
                defaults.upgradeCosts(),
                abilities
        ));

        fox.refreshType(AdversaryTowers.FOX, null);
        AdversaryTeamEffects.TeamProfile profile = AdversaryTeamEffects.strongestProfile(
                List.of(FoxForm.BEACON_KEEPER)
        );

        assertAll(
                () -> assertEquals(2_000.0, fox.currentMaxHealth(), 0.0001),
                () -> assertEquals(800.0, fox.health(), 0.0001),
                () -> assertEquals(6.0, fox.adjustAttackRange(0.0), 0.0001),
                () -> assertEquals(100.0, fox.modifyAttackDamage(null, null, fox.type().damage()), 0.0001),
                () -> assertEquals(5, fox.adjustAttackInterval(0)),
                () -> assertEquals(60.0, fox.modifyIncomingDamage(null, null, 100.0), 0.0001),
                () -> assertEquals(60, FoxForm.BEACON_KEEPER.recipe().orElseThrow()
                        .required(RivalKind.PHANTOM)),
                () -> assertEquals(0.08, profile.towerDamageBonus(), 0.0001)
        );
    }

    @Test
    void bigGameWaveDamageNeverUsesTheIncomeStreak() throws ReflectiveOperationException {
        AdversaryFoxTower fox = fox();
        fox.setForm(FoxForm.BIG_GAME_TRACKER, null);
        var hits = AdversaryFoxTower.class.getDeclaredField("spyglassTargetHits");
        hits.setAccessible(true);
        hits.setInt(fox, 2);

        assertEquals(96.0, fox.modifyAttackDamage(null, null, fox.type().damage()), 0.0001);
    }

    @Test
    void publishedAbilityConstantsProduceTheApprovedLongRunDamage() {
        assertAll(
                () -> assertEquals(2, AdversaryBalance.BASE_SPLASH_EXTRA_TARGETS),
                () -> assertEquals(0.30, AdversaryBalance.BASE_SPLASH_DAMAGE_RATIO, 0.0001),
                () -> assertEquals(1, AdversaryBalance.BREEZE_EXTRA_TARGETS),
                () -> assertEquals(0.60, AdversaryBalance.BREEZE_EXTRA_TARGET_DAMAGE_RATIO, 0.0001),
                () -> assertEquals(5, AdversaryBalance.GOLDEN_FANG_EXTRA_ATTACK_EVERY),
                () -> assertEquals(0.50, AdversaryBalance.GOLDEN_FANG_EXTRA_DAMAGE_RATIO, 0.0001),
                () -> assertEquals(240.0, dps(30.0, 4) * 1.60, 0.0001),
                () -> assertEquals(264.0, dps(36.0, 3) * 1.10, 0.0001),
                () -> assertEquals(393.75, dps(90.0, 8) * 1.75, 0.0001)
        );

        assertArrayEquals(
                new double[]{1.00, 0.55, 0.40, 0.25, 0.15},
                AdversaryBalance.FIREWORK_TARGET_DAMAGE_RATIOS,
                0.0001
        );
        double fireworkRatioSum = java.util.Arrays.stream(
                AdversaryBalance.FIREWORK_TARGET_DAMAGE_RATIOS
        ).sum();
        assertAll(
                () -> assertEquals(5, AdversaryBalance.FIREWORK_MAX_TARGETS),
                () -> assertEquals(270.0, dps(60.0, 8)
                        * AdversaryBalance.FIREWORK_WAVE_DAMAGE_MULTIPLIER, 0.0001),
                () -> assertEquals(634.5, dps(60.0, 8)
                        * AdversaryBalance.FIREWORK_WAVE_DAMAGE_MULTIPLIER
                        * fireworkRatioSum, 0.0001),
                () -> assertEquals(90.0, dps(60.0, 8)
                        * AdversaryBalance.FIREWORK_INCOME_DAMAGE_MULTIPLIER, 0.0001)
        );

        assertArrayEquals(
                new double[]{1.00, 1.35, 1.70},
                AdversaryBalance.BIG_GAME_STREAK_MULTIPLIERS,
                0.0001
        );
        assertAll(
                () -> assertEquals(120.0, dps(120.0, 16)
                        * AdversaryBalance.BIG_GAME_WAVE_DAMAGE_MULTIPLIER, 0.0001),
                () -> assertEquals(225.0, dps(120.0, 16)
                        * AdversaryBalance.BIG_GAME_INCOME_DAMAGE_MULTIPLIER, 0.0001),
                () -> assertEquals(382.5, dps(120.0, 16)
                        * AdversaryBalance.BIG_GAME_INCOME_DAMAGE_MULTIPLIER
                        * AdversaryBalance.BIG_GAME_STREAK_MULTIPLIERS[2], 0.0001)
        );

        assertArrayEquals(
                new double[]{1.00, 1.50, 2.00, 2.50, 3.00},
                AdversaryBalance.MACE_STREAK_MULTIPLIERS,
                0.0001
        );
        assertAll(
                () -> assertEquals(30, AdversaryBalance.MACE_FOCUS_TICKS),
                () -> assertEquals(600.0, dps(
                        AdversaryBalance.MACE_STRIKE_DAMAGE
                                * AdversaryBalance.MACE_STREAK_MULTIPLIERS[4],
                        AdversaryBalance.MACE_STRIKE_INTERVAL_TICKS
                ), 0.0001),
                () -> assertEquals(200.0, dps(
                        AdversaryBalance.SCULK_DETONATION_DAMAGE,
                        AdversaryBalance.SCULK_ATTACK_INTERVAL_TICKS
                ), 0.0001),
                () -> assertEquals(1_000.0, dps(
                        AdversaryBalance.SCULK_DETONATION_DAMAGE,
                        AdversaryBalance.SCULK_ATTACK_INTERVAL_TICKS
                ) * AdversaryBalance.SCULK_MAX_TARGETS, 0.0001),
                () -> assertEquals(0.20, AdversaryBalance.MACE_FOCUS_BREAK_MAX_HEALTH_RATIO, 0.0001),
                () -> assertEquals(1.5, AdversaryBalance.MACE_SWEEP_RADIUS, 0.0001),
                () -> assertEquals(2, AdversaryBalance.MACE_SWEEP_EXTRA_TARGETS),
                () -> assertEquals(0.25, AdversaryBalance.MACE_SWEEP_DAMAGE_RATIO, 0.0001),
                () -> assertEquals(0.10, AdversaryBalance.SCULK_SELF_DAMAGE_MAX_HEALTH_RATIO, 0.0001),
                () -> assertEquals(0.20, AdversaryBalance.SCULK_SELF_DAMAGE_HEALTH_FLOOR_RATIO, 0.0001)
        );
    }

    @Test
    void sculkRecoilNeverDropsTheFoxBelowTwentyPercentHealth() {
        assertAll(
                () -> assertEquals(110.0, AdversaryFoxTower.sculkRecoilDamage(1_100.0, 1_100.0), 0.0001),
                () -> assertEquals(55.0, AdversaryFoxTower.sculkRecoilDamage(275.0, 1_100.0), 0.0001),
                () -> assertEquals(0.0, AdversaryFoxTower.sculkRecoilDamage(220.0, 1_100.0), 0.0001),
                () -> assertEquals(0.0, AdversaryFoxTower.sculkRecoilDamage(100.0, 1_100.0), 0.0001)
        );
    }

    @Test
    void runtimeDetailsExposeTheCurrentFormsConfiguredMechanic() {
        AdversaryFoxTower fox = fox();

        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line -> line.contains("현재 형태</gold>:")));
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line -> line.contains("전직 점수")));
        assertTrue(fox.runtimeDetailLines().stream().noneMatch(line -> line.contains("인컴 처치")));

        fox.setForm(FoxForm.BEACON_KEEPER, null);
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("모든 아군 타워")
                        && line.contains("피해 +4%")
                        && line.contains("공격 속도 +10%")
                        && line.contains("최대 체력 +5%")));

        fox.setForm(FoxForm.OMINOUS_HEXER, null);
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("숙적에게는 적용되지 않습니다")));

        fox.setForm(FoxForm.FIREWORK_PIERCER, null);
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("웨이브 적에게 1.8배") && line.contains("인컴 적에게 0.6배")));
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("직선상의 적 최대 5기")
                        && line.contains("100% / 55% / 40% / 25% / 15%")));

        fox.setForm(FoxForm.SCULK_CORE, null);
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("40틱 뒤 폭발")));
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("최대 5기") && line.contains("1000의 마법 피해")));
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("방어를 무시") && line.contains("체력은 20% 아래")));

        fox.setForm(FoxForm.MACE_EXECUTIONER, null);
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("주변 적 최대 2기") && line.contains("25%만큼 피해")));
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("최대 체력의 20%") && line.contains("공격이 취소")));
    }

    @Test
    void rivalProxyCarriesOnlyAdversaryTagsAndNeverPaysAReward() throws Exception {
        for (RivalKind kind : RivalKind.values()) {
            AdversaryRivalTower baseTower = rival(kind, false);
            Monster base = proxy(baseTower, 10);
            AdversaryRivalTower enhancedTower = rival(kind, true);
            Monster enhanced = proxy(enhancedTower, 10);

            assertAll(kind.name(),
                    () -> assertTrue(AdversaryRivalTower.isOwnedRival(base, OWNER)),
                    () -> assertFalse(AdversaryRivalTower.isOwnedRival(base, OTHER)),
                    () -> assertTrue(AdversaryRivalTower.isOwnedRival(enhanced, OWNER)),
                    () -> assertEquals(Optional.of(kind), AdversaryRivalTower.kindOf(base)),
                    () -> assertEquals(Optional.of(kind), AdversaryRivalTower.kindOf(enhanced)),
                    () -> assertEquals(
                            Optional.of(baseTower.rivalId()),
                            AdversaryRivalTower.logicalRivalIdOf(base)
                    ),
                    () -> assertEquals(
                            Optional.of(enhancedTower.rivalId()),
                            AdversaryRivalTower.logicalRivalIdOf(enhanced)
                    ),
                    () -> assertFalse(AdversaryRivalTower.isEnhancedProxy(base)),
                    () -> assertTrue(AdversaryRivalTower.isEnhancedProxy(enhanced)),
                    () -> assertEquals(0L, base.mineralReward()),
                    () -> assertEquals(0L, enhanced.mineralReward()),
                    () -> assertTrue(base.ownerPlayer().isEmpty()),
                    () -> assertTrue(base.senderTeam().isEmpty()),
                    () -> assertEquals(TeamId.RED, base.targetTeam()),
                    () -> assertEquals(2, base.targetLaneId()),
                    () -> assertEquals(kind.entityTypeId(), base.entityTypeId()),
                    () -> assertEquals(kind.attackKind(), base.attackKind()),
                    () -> assertEquals(kind.range(), base.attackRange(), 0.0001),
                    () -> assertEquals(kind.attackIntervalTicks(), base.attackIntervalTicks()),
                    () -> assertEquals(kind.maxHealth(10, false), base.maxHealth(), 0.0001),
                    () -> assertEquals(kind.armor(10, false), base.armor(), 0.0001),
                    () -> assertEquals(kind.damage(10, false), base.attackDamage(), 0.0001),
                    () -> assertEquals(kind.maxHealth(10, true), enhanced.maxHealth(), 0.0001),
                    () -> assertEquals(kind.armor(10, true), enhanced.armor(), 0.0001),
                    () -> assertEquals(kind.damage(10, true), enhanced.attackDamage(), 0.0001)
            );
        }
    }

    private static void assertIncomingDamage(
            AdversaryFoxTower fox,
            FoxForm form,
            double expected
    ) {
        fox.setForm(form, null);
        assertEquals(expected, fox.modifyIncomingDamage(null, null, 100.0), 0.0001);
    }

    private static void assertBasicAttack(
            AdversaryFoxTower fox,
            FoxForm form,
            double expectedDamage,
            int expectedInterval
    ) {
        fox.setForm(form, null);
        assertEquals(form.range(), fox.adjustAttackRange(999.0), 0.0001);
        assertEquals(expectedInterval, fox.adjustAttackInterval(999));
        assertEquals(expectedDamage, fox.modifyAttackDamage(null, null, fox.type().damage()), 0.0001);
    }

    private static int genericAttackInterval(AdversaryFoxTower fox, TimedEffectSet effects) {
        double speedMultiplier = 1.0
                + effects.magnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS)
                - effects.magnitude(TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION);
        int resolved = (int) Math.ceil(fox.adjustAttackInterval(1) / Math.max(0.01, speedMultiplier));
        return Math.max(fox.minimumAttackIntervalTicks(), resolved);
    }

    private static double dps(double damage, int intervalTicks) {
        return damage * 20.0 / intervalTicks;
    }

    private static Map<String, Map<String, Double>> mutableAbilities(
            Map<String, Map<String, Double>> source
    ) {
        Map<String, Map<String, Double>> copy = new LinkedHashMap<>();
        source.forEach((id, values) -> copy.put(id, new LinkedHashMap<>(values)));
        return copy;
    }

    private static AdversaryFoxTower fox() {
        return new AdversaryFoxTower(
                AdversaryTowers.FOX,
                OWNER,
                TeamId.RED,
                2,
                POSITION
        );
    }

    private static AdversaryRivalTower rival(RivalKind kind, boolean enhanced) {
        return new AdversaryRivalTower(
                enhanced ? AdversaryTowers.enhancedRival(kind) : AdversaryTowers.baseRival(kind),
                OWNER,
                TeamId.RED,
                2,
                POSITION
        );
    }

    /** Exercises the private construction boundary without requiring a ServerLevel. */
    private static Monster proxy(AdversaryRivalTower tower, int round) throws Exception {
        Method method = AdversaryRivalTower.class.getDeclaredMethod("createProxy", int.class);
        method.setAccessible(true);
        return (Monster) method.invoke(tower, round);
    }
}
