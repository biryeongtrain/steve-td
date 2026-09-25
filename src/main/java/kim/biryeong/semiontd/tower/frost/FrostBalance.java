package kim.biryeong.semiontd.tower.frost;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.TowerType;

/** 혹한 빌더의 설정 키와 런타임 밸런스 접근점. */
public final class FrostBalance {
    public static final String CONFIG_ID = "frost_global";

    public static final int FIRST_THRESHOLD = 3;
    public static final int SECOND_THRESHOLD = 6;
    public static final int THIRD_THRESHOLD = 9;
    public static final double CHILL_PER_HIT = 0.15;
    public static final double CHILL_THRESHOLD = 1.0;
    public static final double REFRIGERANT_DAMAGE_REDUCTION = 0.15;
    public static final double REFRIGERANT_ATTACK_SPEED_REDUCTION = 0.15;
    public static final double THAW_MAX_HEALTH_DAMAGE = 0.05;
    public static final double COOLING_WAVE_RANGE = 50.0;
    public static final double COOLING_WAVE_WIDTH = 7.0;
    public static final int ERUPTION_MAX_STACKS = 10;
    public static final int ERUPTION_STACKS_AT_3 = 1;
    public static final int ERUPTION_STACKS_AT_6 = 2;
    public static final int ERUPTION_STACKS_AT_9 = 4;
    public static final double ERUPTION_OWN_DAMAGE_REDUCTION_PER_STACK = 0.02;
    public static final double ERUPTION_OWN_ATTACK_SPEED_REDUCTION_PER_STACK = 0.015;
    public static final double ERUPTION_ALLY_DAMAGE_REDUCTION_PER_STACK = 0.015;
    public static final double ERUPTION_ALLY_ATTACK_SPEED_REDUCTION_PER_STACK = 0.01;
    public static final int ERUPTION_AURA_REFRESH_TICKS = 10;
    public static final int ERUPTION_AURA_DURATION_TICKS = 30;
    public static final int HEALER_COOLING_ADVANCE_TICKS = 10;
    public static final double HEALER_REFRIGERANT_PULSE_MULTIPLIER = 1.41;
    public static final double HEALER_DAMAGE_REDUCTION_AT_3 = 0.05;
    public static final double HEALER_DAMAGE_REDUCTION_AT_6 = 0.07;
    public static final double HEALER_DAMAGE_REDUCTION_AT_9 = 0.10;
    public static final double FULLY_FROZEN_DAMAGE_REDUCTION = 0.10;
    public static final int FULLY_FROZEN_DURATION_TICKS = 20;
    public static final double FULLY_FROZEN_CHILL_RADIUS = 3.0;
    public static final int FULL_OPERATION_REQUIRED_ACTIVATIONS = 9;
    public static final int FULL_OPERATION_MAX_ACTIVATIONS_PER_FAMILY = 3;
    public static final double FULL_OPERATION_ERUPTION_CHILL = 3.0;
    public static final int FULL_OPERATION_DURATION_TICKS = 100;
    public static final double FULL_OPERATION_DAMAGE_REDUCTION = 0.95;
    public static final double FULL_OPERATION_FIXED_ATTACK_DAMAGE = 5.0;
    public static final int FULL_OPERATION_CHILL_INTERVAL_TICKS = 20;
    public static final double FULL_OPERATION_CHILL_PER_PULSE = 1.0;
    public static final double FULL_OPERATION_AREA_RADIUS = 128.0;
    public static final double FROZEN_FOOD_T1_DAMAGE_BONUS_AT_3 = 3.0;
    public static final double FROZEN_FOOD_T2_DAMAGE_BONUS_AT_3 = 5.0;
    public static final double FROZEN_FOOD_T3_DAMAGE_BONUS_AT_3 = 10.0;
    public static final double FROZEN_FOOD_SPLASH_RADIUS_BONUS_AT_6 = 1.0;
    public static final double FROZEN_FOOD_T1_INCOME_DAMAGE_BONUS_AT_9 = 0.10;
    public static final double FROZEN_FOOD_T2_INCOME_DAMAGE_BONUS_AT_9 = 0.20;
    public static final double FROZEN_FOOD_T3_INCOME_DAMAGE_BONUS_AT_9 = 0.30;
    public static final int FROZEN_FOOD_REFRIGERANT_BONUS_ATTACKS = 3;

    private FrostBalance() {
    }

    public static int firstThreshold() {
        return TowerBalanceRuntime.abilityInt(CONFIG_ID, "firstThreshold", FIRST_THRESHOLD);
    }

    public static int secondThreshold() {
        return TowerBalanceRuntime.abilityInt(CONFIG_ID, "secondThreshold", SECOND_THRESHOLD);
    }

    public static int thirdThreshold() {
        return TowerBalanceRuntime.abilityInt(CONFIG_ID, "thirdThreshold", THIRD_THRESHOLD);
    }

    public static double chillPerHit() {
        return TowerBalanceRuntime.ability(CONFIG_ID, "chillPerHit", CHILL_PER_HIT);
    }

    public static double chillThreshold() {
        return TowerBalanceRuntime.ability(CONFIG_ID, "chillThreshold", CHILL_THRESHOLD);
    }

    public static double refrigerantDamageReduction() {
        return TowerBalanceRuntime.ability(
                CONFIG_ID,
                "refrigerantDamageReduction",
                REFRIGERANT_DAMAGE_REDUCTION
        );
    }

    public static double refrigerantAttackSpeedReduction() {
        return TowerBalanceRuntime.ability(
                CONFIG_ID,
                "refrigerantAttackSpeedReduction",
                REFRIGERANT_ATTACK_SPEED_REDUCTION
        );
    }

    public static double thawMaxHealthDamage() {
        return TowerBalanceRuntime.ability(CONFIG_ID, "thawMaxHealthDamage", THAW_MAX_HEALTH_DAMAGE);
    }

    public static double coolingWaveRange(TowerType type) {
        return TowerBalanceRuntime.ability(
                type.id(),
                "waveRange",
                COOLING_WAVE_RANGE
        );
    }

    public static double coolingWaveWidth(TowerType type) {
        return TowerBalanceRuntime.ability(
                type.id(),
                "waveWidth",
                COOLING_WAVE_WIDTH
        );
    }

    public static int healerCoolingAdvanceTicks() {
        return TowerBalanceRuntime.abilityInt(
                CONFIG_ID,
                "healerCoolingAdvanceTicks",
                HEALER_COOLING_ADVANCE_TICKS
        );
    }

    public static double healerRefrigerantPulseMultiplier() {
        return TowerBalanceRuntime.ability(
                CONFIG_ID,
                "healerRefrigerantPulseMultiplier",
                HEALER_REFRIGERANT_PULSE_MULTIPLIER
        );
    }

    public static double healerDamageReduction(int familyCount) {
        String key;
        double fallback;
        if (familyCount >= thirdThreshold()) {
            key = "healerDamageReductionAt9";
            fallback = HEALER_DAMAGE_REDUCTION_AT_9;
        } else if (familyCount >= secondThreshold()) {
            key = "healerDamageReductionAt6";
            fallback = HEALER_DAMAGE_REDUCTION_AT_6;
        } else if (familyCount >= firstThreshold()) {
            key = "healerDamageReductionAt3";
            fallback = HEALER_DAMAGE_REDUCTION_AT_3;
        } else {
            return 0.0;
        }
        return clampRatio(TowerBalanceRuntime.ability(CONFIG_ID, key, fallback));
    }

    public static double fullyFrozenDamageReduction() {
        return clampRatio(TowerBalanceRuntime.ability(
                CONFIG_ID, "fullyFrozenDamageReduction", FULLY_FROZEN_DAMAGE_REDUCTION));
    }

    public static int fullyFrozenDurationTicks() {
        return TowerBalanceRuntime.abilityInt(
                CONFIG_ID, "fullyFrozenDurationTicks", FULLY_FROZEN_DURATION_TICKS);
    }

    public static double fullyFrozenChillRadius() {
        return TowerBalanceRuntime.ability(
                CONFIG_ID, "fullyFrozenChillRadius", FULLY_FROZEN_CHILL_RADIUS);
    }

    public static int fullOperationRequiredActivations() {
        return TowerBalanceRuntime.abilityInt(
                CONFIG_ID, "fullOperationRequiredActivations", FULL_OPERATION_REQUIRED_ACTIVATIONS);
    }

    public static int fullOperationMaxActivationsPerFamily() {
        return TowerBalanceRuntime.abilityInt(
                CONFIG_ID,
                "fullOperationMaxActivationsPerFamily",
                FULL_OPERATION_MAX_ACTIVATIONS_PER_FAMILY
        );
    }

    public static double fullOperationEruptionChill() {
        return TowerBalanceRuntime.ability(
                CONFIG_ID, "fullOperationEruptionChill", FULL_OPERATION_ERUPTION_CHILL);
    }

    public static int fullOperationDurationTicks() {
        return TowerBalanceRuntime.abilityInt(
                CONFIG_ID, "fullOperationDurationTicks", FULL_OPERATION_DURATION_TICKS);
    }

    public static double fullOperationDamageReduction() {
        return clampRatio(TowerBalanceRuntime.ability(
                CONFIG_ID, "fullOperationDamageReduction", FULL_OPERATION_DAMAGE_REDUCTION));
    }

    public static double fullOperationFixedAttackDamage() {
        return TowerBalanceRuntime.ability(
                CONFIG_ID, "fullOperationFixedAttackDamage", FULL_OPERATION_FIXED_ATTACK_DAMAGE);
    }

    public static int fullOperationChillIntervalTicks() {
        return TowerBalanceRuntime.abilityInt(
                CONFIG_ID, "fullOperationChillIntervalTicks", FULL_OPERATION_CHILL_INTERVAL_TICKS);
    }

    public static double fullOperationChillPerPulse() {
        return TowerBalanceRuntime.ability(
                CONFIG_ID, "fullOperationChillPerPulse", FULL_OPERATION_CHILL_PER_PULSE);
    }

    public static double fullOperationAreaRadius() {
        return TowerBalanceRuntime.ability(
                CONFIG_ID, "fullOperationAreaRadius", FULL_OPERATION_AREA_RADIUS);
    }

    public static double frozenFoodDamageBonusAt3(TowerType type) {
        return TowerBalanceRuntime.ability(
                type.id(),
                "frozenFoodDamageBonusAt3",
                frozenFoodDamageBonusAt3Default(type)
        );
    }

    public static double frozenFoodSplashRadiusBonusAt6() {
        return TowerBalanceRuntime.ability(
                CONFIG_ID,
                "frozenFoodSplashRadiusBonusAt6",
                FROZEN_FOOD_SPLASH_RADIUS_BONUS_AT_6
        );
    }

    public static double frozenFoodIncomeDamageBonusAt9(TowerType type) {
        return TowerBalanceRuntime.ability(
                type.id(),
                "frozenFoodIncomeDamageBonusAt9",
                frozenFoodIncomeDamageBonusAt9Default(type)
        );
    }

    public static int frozenFoodRefrigerantBonusAttacks() {
        return TowerBalanceRuntime.abilityInt(
                CONFIG_ID,
                "frozenFoodRefrigerantBonusAttacks",
                FROZEN_FOOD_REFRIGERANT_BONUS_ATTACKS
        );
    }

    public static int eruptionMaxStacks() {
        return TowerBalanceRuntime.abilityInt(CONFIG_ID, "eruptionMaxStacks", ERUPTION_MAX_STACKS);
    }

    public static int eruptionStacksForFamilyCount(int familyCount) {
        if (familyCount >= thirdThreshold()) {
            return TowerBalanceRuntime.abilityInt(CONFIG_ID, "eruptionStacksAt9", ERUPTION_STACKS_AT_9);
        }
        if (familyCount >= secondThreshold()) {
            return TowerBalanceRuntime.abilityInt(CONFIG_ID, "eruptionStacksAt6", ERUPTION_STACKS_AT_6);
        }
        if (familyCount >= firstThreshold()) {
            return TowerBalanceRuntime.abilityInt(CONFIG_ID, "eruptionStacksAt3", ERUPTION_STACKS_AT_3);
        }
        return 0;
    }

    public static int clampEruptionStacks(int stacks) {
        return Math.max(0, Math.min(eruptionMaxStacks(), stacks));
    }

    public static double eruptionDamageReduction(int stacks, boolean ownLane) {
        double perStack = TowerBalanceRuntime.ability(
                CONFIG_ID,
                ownLane ? "eruptionOwnDamageReductionPerStack" : "eruptionAllyDamageReductionPerStack",
                ownLane ? ERUPTION_OWN_DAMAGE_REDUCTION_PER_STACK : ERUPTION_ALLY_DAMAGE_REDUCTION_PER_STACK
        );
        return clampRatio(clampEruptionStacks(stacks) * perStack);
    }

    public static double eruptionAttackSpeedReduction(int stacks, boolean ownLane) {
        double perStack = TowerBalanceRuntime.ability(
                CONFIG_ID,
                ownLane ? "eruptionOwnAttackSpeedReductionPerStack"
                        : "eruptionAllyAttackSpeedReductionPerStack",
                ownLane ? ERUPTION_OWN_ATTACK_SPEED_REDUCTION_PER_STACK
                        : ERUPTION_ALLY_ATTACK_SPEED_REDUCTION_PER_STACK
        );
        return clampRatio(clampEruptionStacks(stacks) * perStack);
    }

    public static int eruptionAuraRefreshTicks() {
        return TowerBalanceRuntime.abilityInt(
                CONFIG_ID,
                "eruptionAuraRefreshTicks",
                ERUPTION_AURA_REFRESH_TICKS
        );
    }

    public static int eruptionAuraDurationTicks() {
        return TowerBalanceRuntime.abilityInt(
                CONFIG_ID,
                "eruptionAuraDurationTicks",
                ERUPTION_AURA_DURATION_TICKS
        );
    }

    public static double splashRadius(TowerType type) {
        return type == null ? 0.0 : Math.max(0.0, TowerBalanceRuntime.ability(type.id(), "splashRadius", 0.0));
    }

    public static double vanguardDamageReduction(TowerType type, int familyCount) {
        String key;
        if (familyCount >= thirdThreshold()) {
            key = "damageReductionAt9";
        } else if (familyCount >= secondThreshold()) {
            key = "damageReductionAt6";
        } else if (familyCount >= firstThreshold()) {
            key = "damageReductionAt3";
        } else {
            return 0.0;
        }
        return Math.max(0.0, Math.min(0.95, TowerBalanceRuntime.ability(type.id(), key)));
    }

    private static double frozenFoodDamageBonusAt3Default(TowerType type) {
        if (FrostTowers.FROZEN_DUMPLING_T3.id().equals(type.id())) {
            return FROZEN_FOOD_T3_DAMAGE_BONUS_AT_3;
        }
        if (FrostTowers.FROZEN_DUMPLING_T2.id().equals(type.id())) {
            return FROZEN_FOOD_T2_DAMAGE_BONUS_AT_3;
        }
        return FROZEN_FOOD_T1_DAMAGE_BONUS_AT_3;
    }

    private static double frozenFoodIncomeDamageBonusAt9Default(TowerType type) {
        if (FrostTowers.FROZEN_DUMPLING_T3.id().equals(type.id())) {
            return FROZEN_FOOD_T3_INCOME_DAMAGE_BONUS_AT_9;
        }
        if (FrostTowers.FROZEN_DUMPLING_T2.id().equals(type.id())) {
            return FROZEN_FOOD_T2_INCOME_DAMAGE_BONUS_AT_9;
        }
        return FROZEN_FOOD_T1_INCOME_DAMAGE_BONUS_AT_9;
    }

    private static double clampRatio(double value) {
        return Math.max(0.0, Math.min(0.95, value));
    }
}
