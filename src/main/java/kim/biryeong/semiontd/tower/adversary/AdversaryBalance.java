package kim.biryeong.semiontd.tower.adversary;

import java.util.Locale;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;

/**
 * Stable configuration keys and source fallbacks for the Adversary builder.
 *
 * <p>The constants in this class are only the source defaults used while the
 * common balance configuration is being bootstrapped. Gameplay code must read
 * through the accessors so server overrides in {@link TowerBalanceConfig} are
 * applied without rebuilding the catalog.</p>
 */
public final class AdversaryBalance {
    public static final String GLOBAL_CONFIG_ID = "adversary_global";
    public static final String FOX_TOWER_ID = "adversary_fox";

    public static final long FOX_COST = 100L;
    public static final long FIRST_EVOLUTION_COST = 200L;
    public static final long FINAL_EVOLUTION_COST = 400L;
    public static final int MAX_FOX_TOWERS = 4;

    public static final double RIVAL_ROUND_HEALTH_GROWTH = 0.07;
    public static final double RIVAL_ROUND_DAMAGE_GROWTH = 0.03;
    public static final int RIVAL_ARMOR_ROUND_INTERVAL = 5;
    public static final double ENHANCED_RIVAL_HEALTH_MULTIPLIER = 2.40;
    public static final double ENHANCED_RIVAL_DAMAGE_MULTIPLIER = 1.70;
    public static final double ENHANCED_RIVAL_ARMOR_BONUS = 4.0;
    public static final double ENHANCED_RIVAL_ATTACK_INTERVAL_MULTIPLIER = 0.80;
    public static final double ENHANCED_RIVAL_RANGE_BONUS = 0.50;
    public static final int BASE_RIVAL_SCORE_PER_KILL = 2;
    public static final int ENHANCED_RIVAL_SCORE_PER_KILL = 3;
    public static final double POST_EVOLUTION_DAMAGE_BONUS_PER_SCORE = 0.005;
    public static final double POST_EVOLUTION_DAMAGE_BONUS_CAP = 2.00;

    public static final double BASE_SPLASH_RADIUS = 4.0;
    public static final int BASE_SPLASH_EXTRA_TARGETS = 3;
    public static final double BASE_SPLASH_DAMAGE_RATIO = 0.50;
    public static final double EVOLVED_SPLASH_DAMAGE_RATIO = 0.50;

    public static final double BASE_RIVAL_KILL_HEAL_RATIO = 0.20;
    public static final double ENHANCED_RIVAL_KILL_HEAL_RATIO = 0.30;
    public static final double RIVAL_KILL_HEAL_CAP_RATIO_PER_WAVE = 2.00;
    public static final double FOCUS_FIRE_DAMAGE_REDUCTION_PER_EXTRA_ATTACKER = 0.04;
    public static final double FOCUS_FIRE_DAMAGE_REDUCTION_CAP = 0.45;

    public static final int BREEZE_EXTRA_TARGETS = 4;
    public static final double BREEZE_EXTRA_TARGET_DAMAGE_RATIO = 0.60;

    public static final int GOLDEN_FANG_EXTRA_ATTACK_EVERY = 7;
    public static final double GOLDEN_FANG_EXTRA_DAMAGE_RATIO = 0.70;

    public static final double SHIELD_COUNTER_DAMAGE = 75.0;
    public static final int SHIELD_COUNTER_COOLDOWN_TICKS = 40;

    public static final int BELL_HEAL_INTERVAL_TICKS = 60;
    public static final double BELL_HEAL_RADIUS = 8.0;
    public static final int BELL_HEAL_TARGET_COUNT = 1;
    public static final double BELL_HEAL_MAX_HEALTH_RATIO = 0.08;
    public static final int BEACON_HEAL_INTERVAL_TICKS = 40;
    public static final double BEACON_HEAL_RADIUS = 10.0;
    public static final int BEACON_HEAL_TARGET_COUNT = 2;
    public static final double BEACON_HEAL_MAX_HEALTH_RATIO = 0.14;
    public static final double OMINOUS_MONSTER_DAMAGE_REDUCTION = 0.08;
    public static final double OMINOUS_MONSTER_ATTACK_SPEED_REDUCTION = 0.30;
    public static final double OMINOUS_MONSTER_TOWER_DAMAGE_TAKEN_BONUS = 0.04;
    public static final int TEAM_EFFECT_SCAN_INTERVAL_TICKS = 20;
    public static final int TEAM_EFFECT_DURATION_TICKS = 40;

    public static final double FIREWORK_WAVE_DAMAGE_MULTIPLIER = 1.80;
    public static final double FIREWORK_INCOME_DAMAGE_MULTIPLIER = 0.70;
    public static final int FIREWORK_MAX_TARGETS = 8;
    public static final double[] FIREWORK_TARGET_DAMAGE_RATIOS = {
            1.00, 0.55, 0.40, 0.25, 0.15, 0.10, 0.08, 0.05
    };

    public static final double BIG_GAME_WAVE_DAMAGE_MULTIPLIER = 0.80;
    public static final double BIG_GAME_INCOME_DAMAGE_MULTIPLIER = 1.50;
    public static final double[] BIG_GAME_STREAK_MULTIPLIERS = {1.00, 1.75, 2.50};

    public static final double ECHO_STREAK_DAMAGE_BONUS_PER_HIT = 0.25;
    public static final int ECHO_MAX_STREAK_BONUS_STACKS = 4;

    public static final int MACE_FOCUS_TICKS = 15;
    public static final int MACE_STRIKE_INTERVAL_TICKS = 20;
    public static final double MACE_STRIKE_DAMAGE = 400.0;
    public static final double MACE_FOCUS_BREAK_MAX_HEALTH_RATIO = 0.20;
    public static final double[] MACE_STREAK_MULTIPLIERS = {1.00, 1.50, 2.00, 2.50, 3.00};
    public static final double MACE_SWEEP_RADIUS = 1.50;
    public static final int MACE_SWEEP_EXTRA_TARGETS = 8;
    public static final double MACE_SWEEP_DAMAGE_RATIO = 0.25;

    public static final int SCULK_DETONATION_DELAY_TICKS = 20;
    public static final int SCULK_ATTACK_INTERVAL_TICKS = 50;
    public static final double SCULK_DETONATION_DAMAGE = 800.0;
    public static final double SCULK_DETONATION_RADIUS = 5.0;
    public static final int SCULK_MAX_TARGETS = 15;
    public static final double SCULK_SELF_DAMAGE_MAX_HEALTH_RATIO = 0.15;
    public static final double SCULK_SELF_DAMAGE_HEALTH_FLOOR_RATIO = 0.40;

    private AdversaryBalance() {
    }

    public static String formConfigId(FoxForm form) {
        if (form == null || form == FoxForm.BASE) {
            return FOX_TOWER_ID;
        }
        return "adversary_fox_form_" + form.name().toLowerCase(Locale.ROOT);
    }

    public static String rivalTowerId(RivalKind kind, boolean enhanced) {
        return "adversary_" + kind.id() + "_rival" + (enhanced ? "_enhanced" : "");
    }

    public static double globalValue(String key, double fallback) {
        return TowerBalanceRuntime.ability(GLOBAL_CONFIG_ID, key, fallback);
    }

    public static int globalInt(String key, int fallback) {
        return TowerBalanceRuntime.abilityInt(GLOBAL_CONFIG_ID, key, fallback);
    }

    public static double formValue(FoxForm form, String key, double fallback) {
        return TowerBalanceRuntime.ability(formConfigId(form), key, fallback);
    }

    public static int formInt(FoxForm form, String key, int fallback) {
        return TowerBalanceRuntime.abilityInt(formConfigId(form), key, fallback);
    }

    public static int evolutionRequirement(FoxForm form, RivalKind kind, int fallback) {
        return formInt(form, requirementKey(kind), fallback);
    }

    public static String requirementKey(RivalKind kind) {
        return "required" + switch (kind) {
            case BREEZE -> "Breeze";
            case CREEPER -> "Creeper";
            case PHANTOM -> "Phantom";
            case POLAR_BEAR -> "PolarBear";
        } + "Score";
    }

    public static double defaultFormValue(FoxForm form, String key) {
        FormDefaults defaults = defaultForm(form);
        return switch (key) {
            case "maxHealth" -> defaults.maxHealth();
            case "range" -> defaults.range();
            case "damage" -> defaults.damage();
            case "attackIntervalTicks" -> defaults.attackIntervalTicks();
            case "damageReduction" -> defaults.damageReduction();
            default -> 0.0;
        };
    }

    public static int defaultFormInt(FoxForm form, String key) {
        return (int) Math.round(defaultFormValue(form, key));
    }

    public static long defaultRivalBaseCost(RivalKind kind) {
        return switch (kind) {
            case BREEZE -> 45L;
            case CREEPER -> 60L;
            case PHANTOM -> 75L;
            case POLAR_BEAR -> 100L;
        };
    }

    public static double defaultRivalBaseHealth(RivalKind kind) {
        return switch (kind) {
            case BREEZE -> 40.0;
            case CREEPER -> 65.0;
            case PHANTOM -> 75.0;
            case POLAR_BEAR -> 100.0;
        };
    }

    public static double defaultRivalBaseArmor(RivalKind kind) {
        return switch (kind) {
            case BREEZE -> 0.0;
            case CREEPER -> 2.0;
            case PHANTOM -> 1.0;
            case POLAR_BEAR -> 3.0;
        };
    }

    public static double defaultRivalBaseDamage(RivalKind kind) {
        return switch (kind) {
            case BREEZE -> 3.0;
            case CREEPER -> 6.0;
            case PHANTOM -> 7.0;
            case POLAR_BEAR -> 11.0;
        };
    }

    public static int defaultRivalAttackIntervalTicks(RivalKind kind) {
        return switch (kind) {
            case BREEZE -> 14;
            case CREEPER -> 15;
            case PHANTOM -> 11;
            case POLAR_BEAR -> 16;
        };
    }

    public static int defaultRivalAttackIntervalTicks(RivalKind kind, boolean enhanced) {
        int baseInterval = defaultRivalAttackIntervalTicks(kind);
        return enhanced
                ? Math.max(1, (int) Math.ceil(baseInterval * ENHANCED_RIVAL_ATTACK_INTERVAL_MULTIPLIER))
                : baseInterval;
    }

    public static double defaultRivalRange(RivalKind kind) {
        return switch (kind) {
            case BREEZE -> 6.0;
            case CREEPER, POLAR_BEAR -> 2.5;
            case PHANTOM -> 4.0;
        };
    }

    public static double defaultRivalRange(RivalKind kind, boolean enhanced) {
        return defaultRivalRange(kind) + (enhanced ? ENHANCED_RIVAL_RANGE_BONUS : 0.0);
    }

    public static long rivalBaseCost(RivalKind kind) {
        TowerBalanceConfig.TowerStats stats = configuredRivalStats(kind, false);
        return stats == null || stats.mineralCost() == null
                ? defaultRivalBaseCost(kind)
                : Math.max(0L, stats.mineralCost());
    }

    public static double rivalBaseHealth(RivalKind kind) {
        return rivalHealth(kind, false);
    }

    public static double rivalHealth(RivalKind kind, boolean enhanced) {
        TowerBalanceConfig.TowerStats stats = configuredRivalStats(kind, enhanced);
        double fallback = defaultRivalBaseHealth(kind)
                * (enhanced ? ENHANCED_RIVAL_HEALTH_MULTIPLIER : 1.0);
        return stats == null || stats.maxHealth() == null ? fallback : stats.maxHealth();
    }

    public static double rivalBaseArmor(RivalKind kind) {
        return rivalArmor(kind, false);
    }

    public static double rivalArmor(RivalKind kind, boolean enhanced) {
        double fallback = defaultRivalBaseArmor(kind)
                + (enhanced ? ENHANCED_RIVAL_ARMOR_BONUS : 0.0);
        return TowerBalanceRuntime.ability(rivalTowerId(kind, enhanced), "baseArmor", fallback);
    }

    public static double rivalBaseDamage(RivalKind kind) {
        return rivalDamage(kind, false);
    }

    public static double rivalDamage(RivalKind kind, boolean enhanced) {
        TowerBalanceConfig.TowerStats stats = configuredRivalStats(kind, enhanced);
        double fallback = defaultRivalBaseDamage(kind)
                * (enhanced ? ENHANCED_RIVAL_DAMAGE_MULTIPLIER : 1.0);
        return stats == null || stats.damage() == null ? fallback : stats.damage();
    }

    public static int rivalAttackIntervalTicks(RivalKind kind) {
        return rivalAttackIntervalTicks(kind, false);
    }

    public static int rivalAttackIntervalTicks(RivalKind kind, boolean enhanced) {
        TowerBalanceConfig.TowerStats stats = configuredRivalStats(kind, enhanced);
        return stats == null || stats.attackIntervalTicks() == null
                ? defaultRivalAttackIntervalTicks(kind, enhanced)
                : Math.max(1, stats.attackIntervalTicks());
    }

    public static double rivalRange(RivalKind kind) {
        return rivalRange(kind, false);
    }

    public static double rivalRange(RivalKind kind, boolean enhanced) {
        TowerBalanceConfig.TowerStats stats = configuredRivalStats(kind, enhanced);
        return stats == null || stats.range() == null ? defaultRivalRange(kind, enhanced) : stats.range();
    }

    public static double rivalRoundHealthGrowth() {
        return globalValue("rivalRoundHealthGrowth", RIVAL_ROUND_HEALTH_GROWTH);
    }

    public static double rivalRoundDamageGrowth() {
        return globalValue("rivalRoundDamageGrowth", RIVAL_ROUND_DAMAGE_GROWTH);
    }

    public static int rivalArmorRoundInterval() {
        return globalInt("rivalArmorRoundInterval", RIVAL_ARMOR_ROUND_INTERVAL);
    }

    public static int rivalScorePerKill(boolean enhanced) {
        return enhanced ? ENHANCED_RIVAL_SCORE_PER_KILL : BASE_RIVAL_SCORE_PER_KILL;
    }

    public static int rivalScorePerKill(RivalKind kind, boolean enhanced) {
        return TowerBalanceRuntime.abilityInt(
                rivalTowerId(kind, enhanced),
                "scorePerKill",
                rivalScorePerKill(enhanced)
        );
    }

    public static double rivalRoundHealthMultiplier(int round) {
        return 1.0 + rivalRoundHealthGrowth() * (Math.max(1, round) - 1);
    }

    public static double rivalRoundDamageMultiplier(int round) {
        return 1.0 + rivalRoundDamageGrowth() * (Math.max(1, round) - 1);
    }

    public static int rivalRoundArmorBonus(int round) {
        return (Math.max(1, round) - 1) / Math.max(1, rivalArmorRoundInterval());
    }

    public static double[] fireworkTargetDamageRatios() {
        return new double[]{
                1.0,
                globalValue("fireworkSecondary2Ratio", FIREWORK_TARGET_DAMAGE_RATIOS[1]),
                globalValue("fireworkSecondary3Ratio", FIREWORK_TARGET_DAMAGE_RATIOS[2]),
                globalValue("fireworkSecondary4Ratio", FIREWORK_TARGET_DAMAGE_RATIOS[3]),
                globalValue("fireworkSecondary5Ratio", FIREWORK_TARGET_DAMAGE_RATIOS[4]),
                globalValue("fireworkSecondary6Ratio", FIREWORK_TARGET_DAMAGE_RATIOS[5]),
                globalValue("fireworkSecondary7Ratio", FIREWORK_TARGET_DAMAGE_RATIOS[6]),
                globalValue("fireworkSecondary8Ratio", FIREWORK_TARGET_DAMAGE_RATIOS[7])
        };
    }

    public static double[] bigGameStreakMultipliers() {
        return new double[]{
                1.0,
                globalValue("bigGameStreak2", BIG_GAME_STREAK_MULTIPLIERS[1]),
                globalValue("bigGameStreak3", BIG_GAME_STREAK_MULTIPLIERS[2])
        };
    }

    public static double[] maceStreakMultipliers() {
        return new double[]{
                1.0,
                globalValue("maceStreak2", MACE_STREAK_MULTIPLIERS[1]),
                globalValue("maceStreak3", MACE_STREAK_MULTIPLIERS[2]),
                globalValue("maceStreak4", MACE_STREAK_MULTIPLIERS[3]),
                globalValue("maceStreak5", MACE_STREAK_MULTIPLIERS[4])
        };
    }

    private static TowerBalanceConfig.TowerStats configuredRivalStats(RivalKind kind, boolean enhanced) {
        TowerBalanceConfig config = TowerBalanceRuntime.current();
        return config == null ? null : config.towers().get(rivalTowerId(kind, enhanced));
    }

    private static FormDefaults defaultForm(FoxForm form) {
        return switch (form) {
            case BASE -> new FormDefaults(300.0, 3.0, 16.0, 10, 0.0);
            case BREEZE -> new FormDefaults(550.0, 7.0, 26.0, 4, 0.0);
            case GOLDEN_FANG -> new FormDefaults(750.0, 5.0, 30.0, 3, 0.10);
            case SHIELD_BEARER -> new FormDefaults(1050.0, 3.5, 60.0, 7, 0.20);
            case BELL_KEEPER -> new FormDefaults(650.0, 5.0, 60.0, 7, 0.0);
            case BEACON_KEEPER -> new FormDefaults(850.0, 4.0, 72.0, 6, 0.30);
            case OMINOUS_HEXER -> new FormDefaults(700.0, 8.0, 72.0, 6, 0.12);
            case TRACKER -> new FormDefaults(550.0, 8.0, 52.0, 7, 0.0);
            case FIREWORK_PIERCER -> new FormDefaults(650.0, 10.0, 56.0, 5, 0.0);
            case BIG_GAME_TRACKER -> new FormDefaults(750.0, 11.0, 96.0, 8, 0.0);
            case ECHO_FOX -> new FormDefaults(700.0, 7.0, 76.0, 8, 0.0);
            case MACE_EXECUTIONER -> new FormDefaults(900.0, 4.5, MACE_STRIKE_DAMAGE, MACE_STRIKE_INTERVAL_TICKS, 0.0);
            case SCULK_CORE -> new FormDefaults(800.0, 13.0, SCULK_DETONATION_DAMAGE, SCULK_ATTACK_INTERVAL_TICKS, 0.0);
        };
    }

    private record FormDefaults(
            double maxHealth,
            double range,
            double damage,
            int attackIntervalTicks,
            double damageReduction
    ) {
    }
}
