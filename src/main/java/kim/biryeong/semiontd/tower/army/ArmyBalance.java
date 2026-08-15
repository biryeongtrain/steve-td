package kim.biryeong.semiontd.tower.army;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;

/**
 * Typed access to the {@code army_global} family configuration and per-tower abilities.
 *
 * <p>Fallbacks mirror {@code TowerBalanceConfig} source defaults so runtime never reads a zero when
 * a configuration file predates a key.
 */
public final class ArmyBalance {
    public static final String CONFIG_ID = "army_global";

    /** Radius within which a 고참 buffs its juniors. */
    public static final double COMMAND_RADIUS = 5.5;

    /**
     * Ceiling on stacked rank buffs.
     *
     * <p>Sized from the steady-state maths: with the average tower granting about +26% and a lane
     * holding up to ten towers, an uncapped stack would put the family far past every other builder
     * late game. At +120% the family lands near 93% of the normal curve on five slots and 99% on
     * ten, which is the intended "scales with slots" shape.
     */
    public static final double MAX_COMMAND_BONUS = 1.2;

    /** How often the command scan runs. Walking the lane roster every tick is wasteful. */
    public static final int COMMAND_SCAN_INTERVAL_TICKS = 20;

    /** Refund fraction paid when a tower is discharged rather than sold. */
    public static final double DISCHARGE_REFUND_RATIO = 0.9;

    /** Permanent lane-wide damage each medal grants. */
    public static final double MEDAL_DAMAGE_BONUS = 0.015;
    public static final int MAX_MEDALS = 10;

    private ArmyBalance() {
    }

    public static double commandRadius() {
        return Math.max(0.5, global("commandRadius", COMMAND_RADIUS));
    }

    public static double maxCommandBonus() {
        return clamp(global("maxCommandBonus", MAX_COMMAND_BONUS), 0.0, 5.0);
    }

    public static double dischargeRefundRatio() {
        return clamp(global("dischargeRefundRatio", DISCHARGE_REFUND_RATIO), 0.0, 1.0);
    }

    public static double medalDamageBonus() {
        return Math.max(0.0, global("medalDamageBonus", MEDAL_DAMAGE_BONUS));
    }

    public static int maxMedals() {
        return Math.max(0, globalInt("maxMedals", MAX_MEDALS));
    }

    // ------------------------------------------------------------------ per-tower

    /**
     * Extra service a tower accrues each wave beyond the default one.
     *
     * <p>Positive on the 조교 (faster promotion), negative on the 초소장 (holds towers in the
     * efficient 상병 band for longer). The two are deliberate opposites: one hand speeds the cycle
     * up to farm medals, the other slows it down to keep a peak roster alive.
     *
     * <p>Stored as two separate non-negative keys because the balance loader rejects any negative
     * ability value outright — a single signed key fails config validation and takes every other
     * builder's defaults down with it. The sign is composed here instead.
     */
    public static double serviceRateBonus(String towerId) {
        double bonus = Math.max(0.0, TowerBalanceRuntime.ability(towerId, "serviceRateBonus", 0.0));
        double penalty = Math.max(0.0, TowerBalanceRuntime.ability(towerId, "serviceRatePenalty", 0.0));
        return bonus - penalty;
    }

    /** Radius over which a support tower applies its service-rate change. */
    public static double serviceRateRadius(String towerId) {
        return Math.max(0.0, TowerBalanceRuntime.ability(towerId, "serviceRateRadius", 0.0));
    }

    /** Multiplier applied to discharge refunds while this tower is alive. */
    public static double dischargeRefundBonus(String towerId) {
        return Math.max(0.0, TowerBalanceRuntime.ability(towerId, "dischargeRefundBonus", 0.0));
    }

    /** Multiplier applied to medal value while this tower is alive. */
    public static double medalValueBonus(String towerId) {
        return Math.max(0.0, TowerBalanceRuntime.ability(towerId, "medalValueBonus", 0.0));
    }

    /** Fraction of incoming damage the guard route sheds. */
    public static double damageReduction(String towerId) {
        return clamp(TowerBalanceRuntime.ability(towerId, "damageReduction", 0.0), 0.0, 0.85);
    }

    /** Splash ratio for the artillery route. */
    public static double splashDamageRatio(String towerId) {
        return clamp(TowerBalanceRuntime.ability(towerId, "splashDamageRatio", 0.0), 0.0, 1.0);
    }

    public static double splashRadius(String towerId) {
        return Math.max(0.0, TowerBalanceRuntime.ability(towerId, "splashRadius", 0.0));
    }

    public static double global(String key, double fallback) {
        return TowerBalanceRuntime.ability(CONFIG_ID, key, fallback);
    }

    public static int globalInt(String key, int fallback) {
        return TowerBalanceRuntime.abilityInt(CONFIG_ID, key, fallback);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
