package kim.biryeong.semiontd.tower.thunder;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;

/**
 * Typed access to the {@code thunder_global} family configuration and per-tower abilities.
 *
 * <p>Fallbacks mirror {@code TowerBalanceConfig} source defaults so the load-factor math never
 * divides by zero when a configuration file predates a key.
 */
public final class ThunderBalance {
    public static final String CONFIG_ID = "thunder_global";

    /**
     * Power the builder supplies without any generation tower.
     *
     * <p>Lane slots start at 5 and cap at 10 before purchases. Without a base supply the family
     * would have to spend two of its five opening slots on towers that cannot attack, which no
     * other builder has to do. Generation towers are an investment for the expensive late tiers,
     * not a prerequisite for playing at all.
     */
    public static final double BASE_POWER = 80.0;

    /** Load factor at or below which the grid pays the full surplus bonus. */
    public static final double SURPLUS_FLOOR = 0.5;
    public static final double SURPLUS_DAMAGE_BONUS = 0.22;

    /** Load factor at or above which the shortage penalty stops getting worse. */
    public static final double SHORTAGE_CEILING = 1.3;
    public static final double SHORTAGE_DAMAGE_PENALTY = 0.4;
    public static final double SHORTAGE_ATTACK_SPEED_PENALTY = 0.3;

    public static final int STUN_TICKS = 20;
    public static final int STUN_COOLDOWN_TICKS = 40;
    public static final int STUN_IMMUNITY_TICKS = 50;
    public static final int MARK_DURATION_TICKS = 100;

    /** Every N waves the storm rod is guaranteed its maximum output. */
    public static final int STORM_WAVE_INTERVAL = 3;

    private ThunderBalance() {
    }

    public static double basePower() {
        return Math.max(0.0, global("basePower", BASE_POWER));
    }

    public static double surplusFloor() {
        return clamp(global("surplusFloor", SURPLUS_FLOOR), 0.05, 0.95);
    }

    public static double surplusDamageBonus() {
        return clamp(global("surplusDamageBonus", SURPLUS_DAMAGE_BONUS), 0.0, 2.0);
    }

    public static double shortageCeiling() {
        return Math.max(1.01, global("shortageCeiling", SHORTAGE_CEILING));
    }

    public static double shortageDamagePenalty() {
        return clamp(global("shortageDamagePenalty", SHORTAGE_DAMAGE_PENALTY), 0.0, 0.9);
    }

    public static double shortageAttackSpeedPenalty() {
        return clamp(global("shortageAttackSpeedPenalty", SHORTAGE_ATTACK_SPEED_PENALTY), 0.0, 0.9);
    }

    public static int stunTicks() {
        return Math.max(1, globalInt("stunTicks", STUN_TICKS));
    }

    public static int stunCooldownTicks() {
        return Math.max(1, globalInt("stunCooldownTicks", STUN_COOLDOWN_TICKS));
    }

    public static int stunImmunityTicks() {
        return Math.max(1, globalInt("stunImmunityTicks", STUN_IMMUNITY_TICKS));
    }

    public static int markDurationTicks() {
        return Math.max(1, globalInt("markDurationTicks", MARK_DURATION_TICKS));
    }

    public static int stormWaveInterval() {
        return Math.max(1, globalInt("stormWaveInterval", STORM_WAVE_INTERVAL));
    }

    // ------------------------------------------------------------------ per-tower

    /** Fixed generation for a rod tower. Storm rods use {@link #stormRange} instead. */
    public static double powerOutput(String towerId) {
        return Math.max(0.0, TowerBalanceRuntime.ability(towerId, "powerOutput", 0.0));
    }

    public static double stormMinOutput(String towerId) {
        return Math.max(0.0, TowerBalanceRuntime.ability(towerId, "stormMinOutput", 0.0));
    }

    public static double stormMaxOutput(String towerId) {
        return Math.max(0.0, TowerBalanceRuntime.ability(towerId, "stormMaxOutput", 0.0));
    }

    /** Whether the tower produces a variable amount that has to be rolled per wave. */
    public static boolean isStormRod(String towerId) {
        return stormMaxOutput(towerId) > 0.0;
    }

    public static double[] stormRange(String towerId) {
        double min = stormMinOutput(towerId);
        double max = Math.max(min, stormMaxOutput(towerId));
        return new double[] {min, max};
    }

    /** Power a tower draws from the grid each tick it is alive. */
    public static double powerDraw(String towerId) {
        return Math.max(0.0, TowerBalanceRuntime.ability(towerId, "powerDraw", 0.0));
    }

    /**
     * How much generation the insulated route contributes at zero health.
     *
     * <p>Output scales with the fraction of health already lost, so an untouched tank contributes
     * nothing and one that has been tanking all wave contributes the full amount.
     */
    public static double healthToPower(String towerId) {
        return Math.max(0.0, TowerBalanceRuntime.ability(towerId, "healthToPower", 0.0));
    }

    /**
     * Fraction of incoming damage the insulated route sheds into the ground.
     *
     * <p>This is the family's answer to armour: instead of raw health it survives by conducting the
     * hit away. It also creates a deliberate tension with {@link #healthToPower} — shedding more
     * damage means losing less health, which means generating less power.
     */
    public static double damageAbsorb(String towerId) {
        return clamp(TowerBalanceRuntime.ability(towerId, "damageAbsorb", 0.0), 0.0, 0.85);
    }

    /**
     * How much weaker a marked monster's own attacks become.
     *
     * <p>The grounded route defends the whole lane rather than itself: everything the marked
     * monster hits takes less damage, not just the tank that applied the mark.
     */
    public static double markAttackReduction(String towerId) {
        return clamp(TowerBalanceRuntime.ability(towerId, "markAttackReduction", 0.0), 0.0, 0.9);
    }

    /** Extra damage every ally deals to a monster carrying this tower's grounding mark. */
    public static double markDamageBonus(String towerId) {
        return Math.max(0.0, TowerBalanceRuntime.ability(towerId, "markDamageBonus", 0.0));
    }

    public static double dischargeDamage(String towerId) {
        return Math.max(0.0, TowerBalanceRuntime.ability(towerId, "dischargeDamage", 0.0));
    }

    public static double dischargeRadius(String towerId) {
        return Math.max(0.0, TowerBalanceRuntime.ability(towerId, "dischargeRadius", 0.0));
    }

    /** Peak multiplier the surge line reaches when the grid has full headroom. */
    public static double surgeMaxMultiplier(String towerId) {
        return Math.max(1.0, TowerBalanceRuntime.ability(towerId, "surgeMaxMultiplier", 1.0));
    }

    /**
     * How many extra monsters an attack arcs to.
     *
     * <p>Replaced the original straight-line pierce: a 1.2 block wide corridor along the
     * tower-to-target axis almost never contained a second monster on a real lane, so the ability
     * read as doing nothing at all.
     */
    public static int chainTargets(String towerId) {
        return Math.max(0, TowerBalanceRuntime.abilityInt(towerId, "chainTargets", 0));
    }

    public static double chainRadius(String towerId) {
        return Math.max(0.0, TowerBalanceRuntime.ability(towerId, "chainRadius", 0.0));
    }

    public static double chainDamageRatio(String towerId) {
        return clamp(TowerBalanceRuntime.ability(towerId, "chainDamageRatio", 0.0), 0.0, 1.0);
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
