package kim.biryeong.semiontd.tower.mage;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;

public final class MageBalance {
    public static final String GLOBAL_ID = "mage_global";

    public static final int MANA_CAPACITY = 1_000;
    public static final int STARTING_MANA = 45;
    public static final int IDLE_WIZARD_MANA = 12;
    public static final int PROPHET_MANA = 23;
    public static final int CORE_MANA = 75;
    public static final double CORE_BREAK_MANA_LOSS_RATIO = 0.05;
    public static final int PROPHECY_REWARD = 120;
    public static final double SUPPORT_RADIUS = 1.5;
    public static final double AMPLIFICATION_BONUS = 0.60;
    public static final double MANA_DAMAGE_BONUS_AT_CAPACITY = 0.70;
    public static final double RANGED_BARRIER_REDUCTION = 0.65;
    public static final int INTERMEDIATE_CASTS = 5;
    public static final int ARCHMAGE_CASTS = 15;
    public static final double INTERMEDIATE_DAMAGE_MULTIPLIER = 1.20;
    public static final double ARCHMAGE_DAMAGE_MULTIPLIER = 1.45;
    public static final double MAX_SPELL_DAMAGE_MULTIPLIER = 3.00;
    public static final int MANA_RETRY_TICKS = 20;

    public static final double MISSILE_DAMAGE = 15.0;
    public static final int MISSILE_COUNT = 6;
    public static final int MISSILE_INTERVAL_TICKS = 2;
    public static final double WIND_CUTTER_DAMAGE = 45.0;
    public static final double WIND_CUTTER_WIDTH = 0.75;
    public static final int WIND_CUTTER_MAX_TARGETS = 10;
    public static final double MANA_BOMB_DAMAGE = 160.0;
    public static final double MANA_BOMB_RADIUS = 2.5;
    public static final int MANA_BOMB_MAX_TARGETS = 7;
    public static final int MANA_BOMB_DELAY_TICKS = 20;
    public static final double[] CHAIN_LIGHTNING_DAMAGE = {135.0, 105.0, 75.0, 55.0, 40.0, 25.0};
    public static final double CHAIN_LIGHTNING_JUMP_RANGE = 4.0;
    public static final double FROST_WAVE_DAMAGE = 90.0;
    public static final double FROST_WAVE_RADIUS = 8.0;
    public static final int FROST_WAVE_MAX_TARGETS = 10;
    public static final double FROST_WAVE_SLOW = 0.45;
    public static final int FROST_WAVE_DURATION_TICKS = 80;
    public static final double DIMENSIONAL_COLLAPSE_DAMAGE = 570.0;
    public static final double DIMENSIONAL_COLLAPSE_RADIUS = 256.0;
    public static final int DIMENSIONAL_COLLAPSE_DELAY_TICKS = 40;

    private MageBalance() {
    }

    public static int coreMana() {
        return TowerBalanceRuntime.abilityInt(GLOBAL_ID, "coreMana", CORE_MANA);
    }

    public static double coreBreakManaLossRatio() {
        return TowerBalanceRuntime.ability(
                GLOBAL_ID,
                "coreBreakManaLossRatio",
                CORE_BREAK_MANA_LOSS_RATIO
        );
    }
}
