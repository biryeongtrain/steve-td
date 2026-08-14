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
        UUID foxId = fox.foxId();
        fox.syncHealth(75.0);

        fox.setForm(FoxForm.BEACON_KEEPER, null);

        assertAll(
                () -> assertSame(AdversaryTowers.FOX, fox.type()),
                () -> assertEquals(OWNER, fox.ownerPlayer()),
                () -> assertEquals(FoxForm.BEACON_KEEPER, fox.form()),
                () -> assertEquals(850.0, fox.currentMaxHealth(), 0.0001),
                () -> assertEquals(212.5, fox.health(), 0.0001),
                () -> assertEquals(foxId, fox.foxId())
        );

        fox.setForm(FoxForm.SCULK_CORE, null);
        assertEquals(800.0, fox.currentMaxHealth(), 0.0001);
        assertEquals(200.0, fox.health(), 0.0001);

        fox.setForm(FoxForm.BASE, null);
        assertEquals(300.0, fox.currentMaxHealth(), 0.0001);
        assertEquals(75.0, fox.health(), 0.0001);
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

        assertBasicAttack(fox, FoxForm.BASE, 16.0, 10);
        assertBasicAttack(fox, FoxForm.BREEZE, 26.0, 4);
        assertBasicAttack(fox, FoxForm.GOLDEN_FANG, 30.0, 3);
        assertBasicAttack(fox, FoxForm.SHIELD_BEARER, 60.0, 7);
        assertBasicAttack(fox, FoxForm.BELL_KEEPER, 60.0, 7);
        assertBasicAttack(fox, FoxForm.BEACON_KEEPER, 72.0, 6);
        assertBasicAttack(fox, FoxForm.OMINOUS_HEXER, 72.0, 6);
        assertBasicAttack(fox, FoxForm.TRACKER, 52.0, 7);
        assertBasicAttack(fox, FoxForm.FIREWORK_PIERCER, 100.8, 5);
        assertBasicAttack(fox, FoxForm.BIG_GAME_TRACKER, 76.8, 8);
        assertBasicAttack(fox, FoxForm.ECHO_FOX, 76.0, 8);
        assertBasicAttack(fox, FoxForm.MACE_EXECUTIONER, 0.0, 20);
        assertBasicAttack(fox, FoxForm.SCULK_CORE, 0.0, 50);
    }

    @Test
    void finalFormsGainCappedDamageFromScoreEarnedAfterEvolution() {
        AdversaryFoxTower fox = fox();
        AdversaryProgressState progress = AdversaryProgressStates.state(OWNER);
        progress.registerFox(fox.foxId(), FoxForm.BASE);
        progress.reconcileRivals(List.of(new RivalContribution(
                UUID.randomUUID(),
                RivalKind.BREEZE,
                60
        )));
        assertTrue(progress.commitEvolution(fox.foxId(), FoxForm.BASE, FoxForm.BREEZE));
        progress.recordCompletedWave(fox.foxId(), FoxForm.BREEZE);
        assertTrue(progress.commitEvolution(fox.foxId(), FoxForm.BREEZE, FoxForm.GOLDEN_FANG));
        fox.setForm(FoxForm.GOLDEN_FANG, null);

        assertEquals(10, progress.postEvolutionBonusScore());
        assertEquals(105.0, fox.modifyResolvedAttackDamage(null, null, 100.0), 0.0001);

        progress.reconcileRivals(List.of(new RivalContribution(
                UUID.randomUUID(),
                RivalKind.BREEZE,
                450
        )));
        assertEquals(300.0, fox.modifyResolvedAttackDamage(null, null, 100.0), 0.0001);

        fox.setForm(FoxForm.BREEZE, null);
        assertEquals(100.0, fox.modifyResolvedAttackDamage(null, null, 100.0), 0.0001);
    }

    @Test
    void genericAttackSpeedBonusAlsoAcceleratesMaceAndSculkBaseIntervals() {
        AdversaryFoxTower fox = fox();
        TimedEffectSet effects = new TimedEffectSet();
        effects.apply(
                TimedEffectType.TOWER_ATTACK_SPEED_BONUS,
                0.10,
                AdversaryBalance.TEAM_EFFECT_DURATION_TICKS
        );

        fox.setForm(FoxForm.MACE_EXECUTIONER, null);
        int maceInterval = genericAttackInterval(fox, effects);
        fox.setForm(FoxForm.SCULK_CORE, null);
        int sculkInterval = genericAttackInterval(fox, effects);

        assertAll(
                () -> assertEquals(1, fox.minimumAttackIntervalTicks()),
                () -> assertEquals(19, maceInterval),
                () -> assertEquals(46, sculkInterval)
        );
    }

    @Test
    void supportHealingProfilesAndOminousTeamDebuffsUseConfiguredValues() {
        AdversaryTeamEffects.TeamProfile profile = AdversaryTeamEffects.strongestProfile(List.of(
                FoxForm.BELL_KEEPER,
                FoxForm.BELL_KEEPER,
                FoxForm.BEACON_KEEPER,
                FoxForm.BEACON_KEEPER,
                FoxForm.OMINOUS_HEXER,
                FoxForm.OMINOUS_HEXER
        ));
        AdversaryTeamEffects.HealProfile bell = AdversaryTeamEffects.healingProfile(FoxForm.BELL_KEEPER);
        AdversaryTeamEffects.HealProfile beacon = AdversaryTeamEffects.healingProfile(FoxForm.BEACON_KEEPER);
        AdversaryTeamEffects.HealProfile ominous = AdversaryTeamEffects.healingProfile(FoxForm.OMINOUS_HEXER);

        assertAll(
                () -> assertEquals(60, bell.intervalTicks()),
                () -> assertEquals(8.0, bell.radius(), 0.0001),
                () -> assertEquals(1, bell.targetCount()),
                () -> assertEquals(0.08, bell.maxHealthRatio(), 0.0001),
                () -> assertEquals(40, beacon.intervalTicks()),
                () -> assertEquals(10.0, beacon.radius(), 0.0001),
                () -> assertEquals(2, beacon.targetCount()),
                () -> assertEquals(0.14, beacon.maxHealthRatio(), 0.0001),
                () -> assertEquals(bell, ominous),
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
        abilities.get(AdversaryBalance.GLOBAL_CONFIG_ID).put("beaconHealMaxHealthRatio", 0.08);
        TowerBalanceRuntime.apply(new TowerBalanceConfig(
                defaults.towers(),
                defaults.upgradeCosts(),
                abilities
        ));

        fox.refreshType(AdversaryTowers.FOX, null);
        AdversaryTeamEffects.HealProfile healing = AdversaryTeamEffects.healingProfile(FoxForm.BEACON_KEEPER);

        assertAll(
                () -> assertEquals(2_000.0, fox.currentMaxHealth(), 0.0001),
                () -> assertEquals(800.0, fox.health(), 0.0001),
                () -> assertEquals(6.0, fox.adjustAttackRange(0.0), 0.0001),
                () -> assertEquals(100.0, fox.modifyAttackDamage(null, null, fox.type().damage()), 0.0001),
                () -> assertEquals(5, fox.adjustAttackInterval(0)),
                () -> assertEquals(60.0, fox.modifyIncomingDamage(null, null, 100.0), 0.0001),
                () -> assertEquals(60, FoxForm.BEACON_KEEPER.recipe().orElseThrow()
                        .required(RivalKind.PHANTOM)),
                () -> assertEquals(0.08, healing.maxHealthRatio(), 0.0001)
        );
    }

    @Test
    void bigGameWaveDamageNeverUsesTheIncomeStreak() throws ReflectiveOperationException {
        AdversaryFoxTower fox = fox();
        fox.setForm(FoxForm.BIG_GAME_TRACKER, null);
        var hits = AdversaryFoxTower.class.getDeclaredField("spyglassTargetHits");
        hits.setAccessible(true);
        hits.setInt(fox, 2);

        assertEquals(76.8, fox.modifyAttackDamage(null, null, fox.type().damage()), 0.0001);
    }

    @Test
    void publishedAbilityConstantsProduceTheApprovedLongRunDamage() {
        assertAll(
                () -> assertEquals(3, AdversaryBalance.BASE_SPLASH_EXTRA_TARGETS),
                () -> assertEquals(0.50, AdversaryBalance.BASE_SPLASH_DAMAGE_RATIO, 0.0001),
                () -> assertEquals(0.005, AdversaryBalance.POST_EVOLUTION_DAMAGE_BONUS_PER_SCORE, 0.0001),
                () -> assertEquals(2.00, AdversaryBalance.POST_EVOLUTION_DAMAGE_BONUS_CAP, 0.0001),
                () -> assertEquals(4, AdversaryBalance.BREEZE_EXTRA_TARGETS),
                () -> assertEquals(0.60, AdversaryBalance.BREEZE_EXTRA_TARGET_DAMAGE_RATIO, 0.0001),
                () -> assertEquals(7, AdversaryBalance.GOLDEN_FANG_EXTRA_ATTACK_EVERY),
                () -> assertEquals(0.70, AdversaryBalance.GOLDEN_FANG_EXTRA_DAMAGE_RATIO, 0.0001),
                () -> assertEquals(208.0, dps(26.0, 4) * 1.60, 0.0001),
                () -> assertEquals(220.0, dps(30.0, 3) * 1.10, 0.0001),
                () -> assertEquals(380.0, dps(76.0, 8) * 2.00, 0.0001)
        );

        assertArrayEquals(
                new double[]{1.00, 0.55, 0.40, 0.25, 0.15, 0.10, 0.08, 0.05},
                AdversaryBalance.FIREWORK_TARGET_DAMAGE_RATIOS,
                0.0001
        );
        double fireworkRatioSum = java.util.Arrays.stream(
                AdversaryBalance.FIREWORK_TARGET_DAMAGE_RATIOS
        ).sum();
        assertAll(
                () -> assertEquals(8, AdversaryBalance.FIREWORK_MAX_TARGETS),
                () -> assertEquals(AdversaryBalance.FIREWORK_MAX_TARGETS,
                        AdversaryBalance.fireworkTargetDamageRatios().length),
                () -> assertEquals(403.2, dps(56.0, 5)
                        * AdversaryBalance.FIREWORK_WAVE_DAMAGE_MULTIPLIER, 0.0001),
                () -> assertEquals(1_040.256, dps(56.0, 5)
                        * AdversaryBalance.FIREWORK_WAVE_DAMAGE_MULTIPLIER
                        * fireworkRatioSum, 0.0001),
                () -> assertEquals(156.8, dps(56.0, 5)
                        * AdversaryBalance.FIREWORK_INCOME_DAMAGE_MULTIPLIER, 0.0001)
        );

        assertArrayEquals(
                new double[]{1.00, 1.75, 2.50},
                AdversaryBalance.BIG_GAME_STREAK_MULTIPLIERS,
                0.0001
        );
        assertAll(
                () -> assertEquals(192.0, dps(96.0, 8)
                        * AdversaryBalance.BIG_GAME_WAVE_DAMAGE_MULTIPLIER, 0.0001),
                () -> assertEquals(360.0, dps(96.0, 8)
                        * AdversaryBalance.BIG_GAME_INCOME_DAMAGE_MULTIPLIER, 0.0001),
                () -> assertEquals(900.0, dps(96.0, 8)
                        * AdversaryBalance.BIG_GAME_INCOME_DAMAGE_MULTIPLIER
                        * AdversaryBalance.BIG_GAME_STREAK_MULTIPLIERS[2], 0.0001)
        );

        assertArrayEquals(
                new double[]{1.00, 1.50, 2.00, 2.50, 3.00},
                AdversaryBalance.MACE_STREAK_MULTIPLIERS,
                0.0001
        );
        assertAll(
                () -> assertEquals(15, AdversaryBalance.MACE_FOCUS_TICKS),
                () -> assertEquals(1_200.0, dps(
                        AdversaryBalance.MACE_STRIKE_DAMAGE
                                * AdversaryBalance.MACE_STREAK_MULTIPLIERS[4],
                        AdversaryBalance.MACE_STRIKE_INTERVAL_TICKS
                ), 0.0001),
                () -> assertEquals(320.0, dps(
                        AdversaryBalance.SCULK_DETONATION_DAMAGE,
                        AdversaryBalance.SCULK_ATTACK_INTERVAL_TICKS
                ), 0.0001),
                () -> assertEquals(4_800.0, dps(
                        AdversaryBalance.SCULK_DETONATION_DAMAGE,
                        AdversaryBalance.SCULK_ATTACK_INTERVAL_TICKS
                ) * AdversaryBalance.SCULK_MAX_TARGETS, 0.0001),
                () -> assertEquals(0.20, AdversaryBalance.MACE_FOCUS_BREAK_MAX_HEALTH_RATIO, 0.0001),
                () -> assertEquals(1.5, AdversaryBalance.MACE_SWEEP_RADIUS, 0.0001),
                () -> assertEquals(8, AdversaryBalance.MACE_SWEEP_EXTRA_TARGETS),
                () -> assertEquals(0.25, AdversaryBalance.MACE_SWEEP_DAMAGE_RATIO, 0.0001),
                () -> assertEquals(0.15, AdversaryBalance.SCULK_SELF_DAMAGE_MAX_HEALTH_RATIO, 0.0001),
                () -> assertEquals(0.40, AdversaryBalance.SCULK_SELF_DAMAGE_HEALTH_FLOOR_RATIO, 0.0001)
        );
    }

    @Test
    void sculkRecoilNeverDropsTheFoxBelowFortyPercentHealth() {
        assertAll(
                () -> assertEquals(165.0, AdversaryFoxTower.sculkRecoilDamage(1_100.0, 1_100.0), 0.0001),
                () -> assertEquals(55.0, AdversaryFoxTower.sculkRecoilDamage(495.0, 1_100.0), 0.0001),
                () -> assertEquals(0.0, AdversaryFoxTower.sculkRecoilDamage(440.0, 1_100.0), 0.0001),
                () -> assertEquals(0.0, AdversaryFoxTower.sculkRecoilDamage(100.0, 1_100.0), 0.0001)
        );
    }

    @Test
    void rivalHealingAndFocusFireMitigationRespectTheirWaveCaps() {
        assertAll(
                () -> assertEquals(70.0,
                        AdversaryFoxTower.rivalKillHealingAmount(100.0, 350.0, 0.0, false), 0.0001),
                () -> assertEquals(105.0,
                        AdversaryFoxTower.rivalKillHealingAmount(100.0, 350.0, 0.0, true), 0.0001),
                () -> assertEquals(105.0,
                        AdversaryFoxTower.rivalKillHealingAmount(100.0, 350.0, 240.0, true), 0.0001),
                () -> assertEquals(5.0,
                        AdversaryFoxTower.rivalKillHealingAmount(345.0, 350.0, 0.0, true), 0.0001),
                () -> assertEquals(0.0, AdversaryFoxTower.focusFireDamageReduction(1), 0.0001),
                () -> assertEquals(0.12, AdversaryFoxTower.focusFireDamageReduction(4), 0.0001),
                () -> assertEquals(0.40, AdversaryFoxTower.focusFireDamageReduction(11), 0.0001),
                () -> assertEquals(0.45, AdversaryFoxTower.focusFireDamageReduction(100), 0.0001)
        );
    }

    @Test
    void runtimeDetailsExposeTheCurrentFormsConfiguredMechanic() {
        AdversaryFoxTower fox = fox();

        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line -> line.contains("현재 형태</gold>:")));
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line -> line.contains("점수</yellow>")));
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("숙적 처치 회복") && line.contains("일반 20%") && line.contains("강화 30%")));
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("집중포화 방어") && line.contains("최대 45%")));
        assertTrue(fox.runtimeDetailLines().stream().noneMatch(line -> line.contains("인컴 처치")));

        fox.setForm(FoxForm.BREEZE, null);
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("연쇄 마법 피해")));

        fox.setForm(FoxForm.BEACON_KEEPER, null);
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("최종 성장") && line.contains("최대 200%")));
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("주변 적 최대 3기에게 공격력의 50%")));
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("2초마다")
                        && line.contains("반경 10블록")
                        && line.contains("다른 여우 최대 2기")
                        && line.contains("각각 14%")));

        fox.setForm(FoxForm.OMINOUS_HEXER, null);
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("3초마다")
                        && line.contains("다른 여우 1기")
                        && line.contains("8%")));
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("숙적에게는 적용되지 않습니다")));

        fox.setForm(FoxForm.FIREWORK_PIERCER, null);
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("웨이브 적에게 1.8배") && line.contains("인컴 적에게 0.7배")));
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("직선상의 적 최대 8기")
                        && line.contains("100% / 55% / 40% / 25% / 15% / 10% / 8% / 5%")
                        && line.contains("물리 피해")));

        fox.setForm(FoxForm.SCULK_CORE, null);
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("20틱 뒤 폭발")));
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("최대 15기") && line.contains("800의 마법 피해")));
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("방어를 무시") && line.contains("체력은 40% 아래")));

        fox.setForm(FoxForm.MACE_EXECUTIONER, null);
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("집중한 뒤 400의 물리 피해")));
        assertTrue(fox.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("주변 적 최대 8기") && line.contains("25%만큼 피해")));
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
