package kim.biryeong.semiontd.summon;

public final class SummonBalancePolicy {
    public static final double SUMMON_HEALTH_PER_ROUND = 0.05;
    public static final double SUMMON_ATTACK_DAMAGE_PER_ROUND = 0.05;
    public static final int SUMMON_LATE_SCALING_START_ROUND = 15;
    public static final double SUMMON_HEALTH_LATE_SCALING_MULTIPLIER = 2.0;
    public static final double SUMMON_ATTACK_DAMAGE_LATE_SCALING_MULTIPLIER = 2.5;
    public static final double SIEGE_NEAR_BOSS_PROGRESS = 0.80;
    public static final double SIEGE_NEAR_BOSS_TARGET_BONUS = 30.0;
    public static final double STORM_LYNX_MOVE_SPEED_BONUS = 0.22;
    public static final int STORM_LYNX_MOVE_SPEED_DURATION_TICKS = 40;
    public static final int STORM_LYNX_MOVE_SPEED_REFRESH_TICKS = 20;
    public static final double AEGIS_GOLEM_DAMAGE_REDUCTION = 0.18;
    public static final double AEGIS_GOLEM_PROTECTION_RADIUS = 5.5;
    public static final int AEGIS_GOLEM_PROTECTION_DURATION_TICKS = 80;
    public static final int AEGIS_GOLEM_PROTECTION_COOLDOWN_TICKS = 60;
    public static final double NULL_IMP_RANGE_REDUCTION = 0.20;
    public static final double NULL_IMP_RANGE_RADIUS = 8.0;
    public static final int NULL_IMP_RANGE_DURATION_TICKS = 120;
    public static final int NULL_IMP_RANGE_COOLDOWN_TICKS = 100;
    public static final double ELDER_SPRITE_DAMAGE_REDUCTION = 0.12;
    public static final double ELDER_SPRITE_PROTECTION_RADIUS = 6.0;
    public static final int ELDER_SPRITE_PROTECTION_DURATION_TICKS = 80;
    public static final int ELDER_SPRITE_PROTECTION_COOLDOWN_TICKS = 80;
    public static final double BOMBARD_TOAD_PROGRESS_THRESHOLD = 0.70;
    public static final double BOMBARD_TOAD_TRUE_DAMAGE = 20.0;
    public static final int BOMBARD_TOAD_TRUE_DAMAGE_COOLDOWN_TICKS = 80;
    public static final double SIEGE_BREAKER_PROGRESS_THRESHOLD = 0.65;
    public static final double SIEGE_BREAKER_TRUE_DAMAGE = 45.0;
    public static final int SIEGE_BREAKER_TRUE_DAMAGE_COOLDOWN_TICKS = 70;
    public static final double APEX_WARDEN_ATTACK_SPEED_REDUCTION = 0.35;
    public static final double APEX_WARDEN_TOWER_PRESSURE_RADIUS = 7.0;
    public static final int APEX_WARDEN_TOWER_PRESSURE_DURATION_TICKS = 120;
    public static final int APEX_WARDEN_TOWER_PRESSURE_COOLDOWN_TICKS = 80;
    public static final double APEX_WARDEN_DAMAGE_REDUCTION = 0.30;
    public static final double APEX_WARDEN_PROTECTION_RADIUS = 7.0;
    public static final int APEX_WARDEN_PROTECTION_DURATION_TICKS = 80;
    public static final int APEX_WARDEN_PROTECTION_COOLDOWN_TICKS = 50;
    public static final double ORACLE_PHOENIX_MOVE_SPEED_BONUS = 0.25;
    public static final double ORACLE_PHOENIX_BLESSING_RADIUS = 8.0;
    public static final int ORACLE_PHOENIX_BLESSING_DURATION_TICKS = 80;
    public static final int ORACLE_PHOENIX_BLESSING_COOLDOWN_TICKS = 60;
    public static final double ORACLE_PHOENIX_RANGE_REDUCTION = 0.25;
    public static final double ORACLE_PHOENIX_RANGE_RADIUS = 8.0;
    public static final int ORACLE_PHOENIX_RANGE_DURATION_TICKS = 120;
    public static final int ORACLE_PHOENIX_RANGE_COOLDOWN_TICKS = 90;
    public static final double SUPPORT_SINGLE_HEAL_RADIUS = 8.0;
    public static final double SUPPORT_SINGLE_HEAL_AMOUNT = 12.0;
    public static final int SUPPORT_SINGLE_HEAL_COOLDOWN_TICKS = 80;
    public static final double T1_SUPPORT_SINGLE_HEAL_RADIUS = 6.0;
    public static final double T1_SUPPORT_SINGLE_HEAL_AMOUNT = 6.0;
    public static final int T1_SUPPORT_SINGLE_HEAL_COOLDOWN_TICKS = 100;
    public static final double SUPPORT_AREA_HEAL_RADIUS = 5.0;
    public static final double SUPPORT_AREA_HEAL_AMOUNT = 5.0;
    public static final int SUPPORT_AREA_HEAL_COOLDOWN_TICKS = 200;
    public static final int SUPPORT_AREA_HEAL_MAX_TARGETS = 4;
    public static final double T3_SUPPORT_SINGLE_HEAL_RADIUS = 8.5;
    public static final double T3_SUPPORT_SINGLE_HEAL_AMOUNT = 18.0;
    public static final int T3_SUPPORT_SINGLE_HEAL_COOLDOWN_TICKS = 75;
    public static final double T3_SUPPORT_AREA_HEAL_RADIUS = 6.0;
    public static final double T3_SUPPORT_AREA_HEAL_AMOUNT = 8.0;
    public static final int T3_SUPPORT_AREA_HEAL_COOLDOWN_TICKS = 180;
    public static final int T3_SUPPORT_AREA_HEAL_MAX_TARGETS = 5;
    public static final double T4_SUPPORT_SINGLE_HEAL_RADIUS = 9.5;
    public static final double T4_SUPPORT_SINGLE_HEAL_AMOUNT = 26.0;
    public static final int T4_SUPPORT_SINGLE_HEAL_COOLDOWN_TICKS = 70;
    public static final double T4_SUPPORT_AREA_HEAL_RADIUS = 7.0;
    public static final double T4_SUPPORT_AREA_HEAL_AMOUNT = 12.0;
    public static final int T4_SUPPORT_AREA_HEAL_COOLDOWN_TICKS = 160;
    public static final int T4_SUPPORT_AREA_HEAL_MAX_TARGETS = 6;
    public static final double T5_SUPPORT_SINGLE_HEAL_RADIUS = 11.0;
    public static final double T5_SUPPORT_SINGLE_HEAL_AMOUNT = 42.0;
    public static final int T5_SUPPORT_SINGLE_HEAL_COOLDOWN_TICKS = 60;
    public static final double T5_SUPPORT_AREA_HEAL_RADIUS = 8.0;
    public static final double T5_SUPPORT_AREA_HEAL_AMOUNT = 18.0;
    public static final int T5_SUPPORT_AREA_HEAL_COOLDOWN_TICKS = 140;
    public static final int T5_SUPPORT_AREA_HEAL_MAX_TARGETS = 8;
    public static final int SUPPORT_HEAL_RETRY_TICKS = 10;

    private SummonBalancePolicy() {
    }

    public static double summonAttackDamageMultiplier(int round) {
        return summonRoundMultiplier(
                round,
                SUMMON_ATTACK_DAMAGE_PER_ROUND,
                SUMMON_ATTACK_DAMAGE_LATE_SCALING_MULTIPLIER
        );
    }

    public static double summonHealthMultiplier(int round) {
        return summonRoundMultiplier(round, SUMMON_HEALTH_PER_ROUND, SUMMON_HEALTH_LATE_SCALING_MULTIPLIER);
    }

    private static double summonRoundMultiplier(int round, double perRound, double lateScalingMultiplier) {
        int normalRounds = Math.max(0, Math.min(round, SUMMON_LATE_SCALING_START_ROUND - 1) - 1);
        int lateRounds = Math.max(0, round - SUMMON_LATE_SCALING_START_ROUND + 1);
        return 1.0 + normalRounds * perRound + lateRounds * perRound * lateScalingMultiplier;
    }
}
