package kim.biryeong.semiontd.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TraitBalanceConfig;
import kim.biryeong.semiontd.config.TraitBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.test.tower.TestTowerTypes;
import kim.biryeong.semiontd.tower.warlock.WarlockTowers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class TraitEffectsTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void resetTraitBalance() {
        TraitBalanceRuntime.apply(TraitBalanceConfig.defaultConfig());
    }

    @Test
    void builtInTraitsExposeAllPlannedChoices() {
        BuiltInTraits.register();

        Set<?> registeredIds = TraitRegistry.all().stream()
                .map(SemionTrait::id)
                .collect(Collectors.toSet());
        assertTrue(registeredIds.containsAll(Set.of(
                BuiltInTraits.NONE_ID,
                BuiltInTraits.MOBILIZATION_GRANT_ID,
                BuiltInTraits.CLEAN_LANE_BONUS_ID,
                BuiltInTraits.RAPID_DEPLOYMENT_ID,
                BuiltInTraits.BERSERK_SUMMONS_ID,
                BuiltInTraits.INTERCEPTION_DOCTRINE_ID,
                BuiltInTraits.OPENING_SALVO_ID,
                BuiltInTraits.WAVEBREAKER_DOCTRINE_ID,
                BuiltInTraits.FORTITUDE_ID,
                BuiltInTraits.DOUBLE_EDGED_SWORD_ID,
                BuiltInTraits.STRENGTH_IN_NUMBERS_ID,
                BuiltInTraits.DIVERSITY_ID,
                BuiltInTraits.SUPPLY_DEPOT_ID,
                BuiltInTraits.TRANSCENDENCE_ID,
                BuiltInTraits.WEEKLY_HOLIDAY_PAY_ID,
                BuiltInTraits.RUTHLESS_ID,
                BuiltInTraits.IGNITE_ID,
                BuiltInTraits.GIANT_SLAYER_ID,
                BuiltInTraits.FINISHING_BLOW_ID,
                BuiltInTraits.PERFORMANCE_BONUS_ID
        )));

    }

    @Test
    void economicTraitsUseFullPrimaryAndHalfSecondaryPower() {
        TraitLoadout primaryGold = primary(BuiltInTraits.MOBILIZATION_GRANT_ID);
        TraitLoadout secondaryGold = secondary(BuiltInTraits.MOBILIZATION_GRANT_ID);
        TraitLoadout primaryClean = primary(BuiltInTraits.CLEAN_LANE_BONUS_ID);
        TraitLoadout secondaryClean = secondary(BuiltInTraits.CLEAN_LANE_BONUS_ID);

        assertEquals(120L, TraitEffects.startingMineralBonus(primaryGold));
        assertEquals(60L, TraitEffects.startingMineralBonus(secondaryGold));
        assertEquals(180L, TraitEffects.cleanLaneBonus(primaryClean, 1_000L));
        assertEquals(90L, TraitEffects.cleanLaneBonus(secondaryClean, 1_000L));
        assertEquals(0L, TraitEffects.cleanLaneBonus(primaryClean, -1L));
        assertEquals(4, TraitEffects.towerLimitBonus(primary(BuiltInTraits.SUPPLY_DEPOT_ID)));
        assertEquals(2, TraitEffects.towerLimitBonus(secondary(BuiltInTraits.SUPPLY_DEPOT_ID)));

        EconomyConfig.TowerLimitConfig towerLimit = new EconomyConfig.TowerLimitConfig(5, 5, 5, 3, 23);
        assertEquals(9, towerLimit.limitForRound(1)
                + TraitEffects.towerLimitBonus(primary(BuiltInTraits.SUPPLY_DEPOT_ID)));
        assertEquals(7, towerLimit.limitForRound(1)
                + TraitEffects.towerLimitBonus(secondary(BuiltInTraits.SUPPLY_DEPOT_ID)));
        assertEquals(27, towerLimit.limitForRound(100)
                + TraitEffects.towerLimitBonus(primary(BuiltInTraits.SUPPLY_DEPOT_ID)));
        assertEquals(25, towerLimit.limitForRound(100)
                + TraitEffects.towerLimitBonus(secondary(BuiltInTraits.SUPPLY_DEPOT_ID)));

        assertEquals(3_000, TraitEffects.weeklyHolidayPayIntervalTicks());
        assertFalse(TraitEffects.weeklyHolidayPayDue(2_999));
        assertTrue(TraitEffects.weeklyHolidayPayDue(3_000));
        assertTrue(TraitEffects.weeklyHolidayPayDue(6_000));
        assertEquals(26L, TraitEffects.weeklyHolidayPay(primary(BuiltInTraits.WEEKLY_HOLIDAY_PAY_ID), 100L));
        assertEquals(13L, TraitEffects.weeklyHolidayPay(secondary(BuiltInTraits.WEEKLY_HOLIDAY_PAY_ID), 100L));
        assertEquals(2, TraitEffects.performanceBonusFirstRound());
        assertEquals(0L, TraitEffects.performanceBonus(primary(BuiltInTraits.PERFORMANCE_BONUS_ID), 1_000L, 1));
        assertEquals(160L, TraitEffects.performanceBonus(primary(BuiltInTraits.PERFORMANCE_BONUS_ID), 1_000L, 2));
        assertEquals(80L, TraitEffects.performanceBonus(secondary(BuiltInTraits.PERFORMANCE_BONUS_ID), 1_000L, 2));
    }

    @Test
    void conditionalDamageTraitsUsePreDamageCurrentHealthAndDebuffs() {
        Monster target = monster(Optional.empty(), 1_000.0);

        assertEquals(
                0.25,
                TraitEffects.conditionalTargetDamageBonus(primary(BuiltInTraits.RUTHLESS_ID), target, true),
                0.000_001
        );
        assertEquals(
                0.0,
                TraitEffects.conditionalTargetDamageBonus(primary(BuiltInTraits.RUTHLESS_ID), target, false),
                0.000_001
        );

        target.syncHealth(800.0);
        assertEquals(
                0.20,
                TraitEffects.conditionalTargetDamageBonus(primary(BuiltInTraits.GIANT_SLAYER_ID), target, false),
                0.000_001
        );
        target.syncHealth(799.9);
        assertEquals(
                0.0,
                TraitEffects.conditionalTargetDamageBonus(primary(BuiltInTraits.GIANT_SLAYER_ID), target, false),
                0.000_001
        );

        target.syncHealth(400.0);
        assertEquals(
                0.20,
                TraitEffects.conditionalTargetDamageBonus(primary(BuiltInTraits.FINISHING_BLOW_ID), target, false),
                0.000_001
        );
        target.syncHealth(400.1);
        assertEquals(
                0.0,
                TraitEffects.conditionalTargetDamageBonus(primary(BuiltInTraits.FINISHING_BLOW_ID), target, false),
                0.000_001
        );

        target.syncHealth(800.0);
        assertEquals(
                0.35,
                TraitEffects.conditionalTargetDamageBonus(
                        new TraitLoadout(BuiltInTraits.RUTHLESS_ID, BuiltInTraits.GIANT_SLAYER_ID),
                        target,
                        true
                ),
                0.000_001
        );
    }

    @Test
    void igniteUsesRoundScaledAttackDamageAndHalfSecondaryPower() {
        assertEquals(
                12.0,
                TraitEffects.igniteDamagePerTick(primary(BuiltInTraits.IGNITE_ID), 100.0, 10),
                0.000_001
        );
        assertEquals(
                6.0,
                TraitEffects.igniteDamagePerTick(secondary(BuiltInTraits.IGNITE_ID), 100.0, 10),
                0.000_001
        );
        assertEquals(80, TraitEffects.igniteDurationTicks());
        assertEquals(20, TraitEffects.igniteTickIntervalTicks());
    }

    @Test
    void attackAndSaleTraitsUsePlannedValues() {
        TraitLoadout primaryBerserk = primary(BuiltInTraits.BERSERK_SUMMONS_ID);
        TraitLoadout secondaryBerserk = secondary(BuiltInTraits.BERSERK_SUMMONS_ID);

        assertEquals(1.30, TraitEffects.incomeAttackDamageMultiplier(primaryBerserk), 0.000_001);
        assertEquals(1.20, TraitEffects.incomeAttackSpeedMultiplier(primaryBerserk), 0.000_001);
        assertEquals(1.15, TraitEffects.incomeAttackDamageMultiplier(secondaryBerserk), 0.000_001);
        assertEquals(1.10, TraitEffects.incomeAttackSpeedMultiplier(secondaryBerserk), 0.000_001);

        assertEquals(1.00, TraitEffects.sellRefundRate(primary(BuiltInTraits.RAPID_DEPLOYMENT_ID), true), 0.000_001);
        assertEquals(0.75, TraitEffects.sellRefundRate(secondary(BuiltInTraits.RAPID_DEPLOYMENT_ID), true), 0.000_001);
        assertEquals(1.00, TraitEffects.sellRefundRate(primary(BuiltInTraits.RAPID_DEPLOYMENT_ID), false), 0.000_001);
        assertEquals(0.25, TraitEffects.openingAttackSpeedBonus(primary(BuiltInTraits.OPENING_SALVO_ID)), 0.000_001);
        assertEquals(0.125, TraitEffects.openingAttackSpeedBonus(secondary(BuiltInTraits.OPENING_SALVO_ID)), 0.000_001);
        assertEquals(200, TraitEffects.transcendenceActivationDelayTicks());
        assertEquals(0.35, TraitEffects.transcendenceDamageBonus(primary(BuiltInTraits.TRANSCENDENCE_ID)), 0.000_001);
        assertEquals(0.175, TraitEffects.transcendenceDamageBonus(secondary(BuiltInTraits.TRANSCENDENCE_ID)), 0.000_001);
    }

    @Test
    void configuredTraitValuesDriveRuntime() {
        TraitBalanceRuntime.apply(new TraitBalanceConfig(Map.of(
                "opening_salvo", Map.of(
                        "attackSpeedBonus", 0.25,
                        "durationSeconds", 12.0
                )
        )));
        assertEquals(0.25, TraitEffects.openingAttackSpeedBonus(primary(BuiltInTraits.OPENING_SALVO_ID)), 0.000_001);
        assertEquals(240, TraitEffects.openingAttackSpeedDurationTicks());
    }

    @Test
    void configuredSupplyAndTranscendenceValuesDriveRuntime() {
        TraitBalanceRuntime.apply(new TraitBalanceConfig(Map.of(
                "supply_depot", Map.of("towerLimitBonus", 6.0),
                "transcendence", Map.of(
                        "activationDelaySeconds", 10.0,
                        "damageBonus", 0.40
                )
        )));
        assertEquals(6, TraitEffects.towerLimitBonus(primary(BuiltInTraits.SUPPLY_DEPOT_ID)));
        assertEquals(3, TraitEffects.towerLimitBonus(secondary(BuiltInTraits.SUPPLY_DEPOT_ID)));
        assertEquals(200, TraitEffects.transcendenceActivationDelayTicks());
        assertEquals(0.40, TraitEffects.transcendenceDamageBonus(primary(BuiltInTraits.TRANSCENDENCE_ID)), 0.000_001);
        assertEquals(0.20, TraitEffects.transcendenceDamageBonus(secondary(BuiltInTraits.TRANSCENDENCE_ID)), 0.000_001);
    }

    @Test
    void targetAndDoubleEdgedBonusesDistinguishIncomeFromWaveMonsters() {
        Monster incomeMonster = monster(Optional.of(TeamId.BLUE));
        Monster waveMonster = monster(Optional.empty());

        assertEquals(0.25,
                TraitEffects.targetDamageBonus(primary(BuiltInTraits.INTERCEPTION_DOCTRINE_ID), incomeMonster),
                0.000_001);
        assertEquals(0.0,
                TraitEffects.targetDamageBonus(primary(BuiltInTraits.INTERCEPTION_DOCTRINE_ID), waveMonster),
                0.000_001);
        assertEquals(0.25,
                TraitEffects.targetDamageBonus(primary(BuiltInTraits.WAVEBREAKER_DOCTRINE_ID), waveMonster),
                0.000_001);
        assertEquals(0.25,
                TraitEffects.doubleEdgedIncomingDamageBonus(primary(BuiltInTraits.DOUBLE_EDGED_SWORD_ID)),
                0.000_001);
        assertEquals(0.125,
                TraitEffects.doubleEdgedIncomingDamageBonus(secondary(BuiltInTraits.DOUBLE_EDGED_SWORD_ID)),
                0.000_001);
        assertEquals(0.25,
                TraitEffects.doubleEdgedOutgoingDamageBonus(primary(BuiltInTraits.DOUBLE_EDGED_SWORD_ID)),
                0.000_001);
    }

    @Test
    void fortitudeUsesTheWarlockCoreException() {
        UUID owner = UUID.nameUUIDFromBytes("fortitude-owner".getBytes());
        GridPosition position = new GridPosition(0, 0, 0);
        TestTower normal = new TestTower(TestTowerTypes.TEST_DIRECT, owner, TeamId.RED, 1, position);
        TestTower warlockCore = new TestTower(WarlockTowers.BASE_WARLOCK_TOWER, owner, TeamId.RED, 1, position);
        TraitLoadout fortitude = primary(BuiltInTraits.FORTITUDE_ID);

        assertEquals(0.30, TraitEffects.towerMaxHealthBonus(fortitude, normal), 0.000_001);
        assertEquals(0.10, TraitEffects.towerMaxHealthBonus(fortitude, warlockCore), 0.000_001);
    }

    @Test
    void incomeMonsterRuntimeAppliesDamageAndConservativeAttackSpeed() {
        Monster monster = monster(Optional.of(TeamId.BLUE));

        monster.applyAttackModifiers(1.20, 1.10);

        assertEquals(12.0, monster.attackDamage(), 0.000_001);
        assertEquals(12, monster.attackIntervalTicks());
    }

    private static TraitLoadout primary(net.minecraft.resources.ResourceLocation traitId) {
        return new TraitLoadout(traitId, BuiltInTraits.NONE_ID);
    }

    private static TraitLoadout secondary(net.minecraft.resources.ResourceLocation traitId) {
        return new TraitLoadout(BuiltInTraits.NONE_ID, traitId);
    }

    private static Monster monster(Optional<TeamId> senderTeam) {
        return monster(senderTeam, 100.0);
    }

    private static Monster monster(Optional<TeamId> senderTeam, double maxHealth) {
        return new Monster(
                "trait-test",
                TeamId.RED,
                1,
                Optional.of(UUID.nameUUIDFromBytes("trait-test-owner".getBytes())),
                senderTeam,
                maxHealth,
                0.0,
                10.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                0L
        );
    }
}
