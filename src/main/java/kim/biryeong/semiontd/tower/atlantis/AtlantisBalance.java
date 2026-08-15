package kim.biryeong.semiontd.tower.atlantis;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;

/**
 * Typed access to the {@code atlantis_global} family configuration and per-tower abilities.
 * Fallbacks mirror {@code TowerBalanceConfig} source defaults so runtime never divides by zero
 * when a configuration file predates a key.
 */
public final class AtlantisBalance {
    public static final String CONFIG_ID = "atlantis_global";

    public static final int MAX_PRESSURE_STACKS = 10;
    public static final int STACK_DURATION_TICKS = 100;
    public static final double SLOW_PER_STACK = 0.05;
    public static final double MAX_SLOW = 0.45;
    public static final double MAX_ZONE_ALLY_DAMAGE_REDUCTION = 0.35;
    public static final double WATER_PRESSURE_DAMAGE_RATIO = 0.16;
    public static final double WATER_PRESSURE_DAMAGE_CAP = 2.5;
    public static final double WATER_PRESSURE_RADIUS = 3.0;
    public static final double ZONE_STACK_MULTIPLIER = 2.0;
    public static final int MAX_ZONE_COUNT = 6;
    public static final double ZONE_SPACING_BLOCKS = 4.0;
    public static final int ZONE_SCAN_INTERVAL_TICKS = 10;
    public static final int ZONE_VFX_INTERVAL_TICKS = 40;
    public static final int MAX_CHAIN_DEPTH = 3;

    private AtlantisBalance() {
    }

    public static int maxPressureStacks() {
        return Math.max(1, globalInt("maxPressureStacks", MAX_PRESSURE_STACKS));
    }

    public static int stackDurationTicks() {
        return Math.max(1, globalInt("stackDurationTicks", STACK_DURATION_TICKS));
    }

    public static double slowPerStack() {
        return Math.max(0.0, global("slowPerStack", SLOW_PER_STACK));
    }

    public static double maxSlow() {
        return clamp(global("maxSlow", MAX_SLOW), 0.0, 0.95);
    }

    /**
     * Ceiling on the damage reduction a tower can receive from overlapping zones combined.
     *
     * <p>Zone effects are sourced, so stacked zones sum. Without a ceiling a tower parked where
     * several zones meet would approach immunity.
     */
    public static double maxZoneAllyDamageReduction() {
        return clamp(global("maxZoneAllyDamageReduction", MAX_ZONE_ALLY_DAMAGE_REDUCTION), 0.0, 0.9);
    }

    public static double waterPressureDamageRatio() {
        return Math.max(0.0, global("waterPressureDamageRatio", WATER_PRESSURE_DAMAGE_RATIO));
    }

    public static double waterPressureDamageCap() {
        return Math.max(1.0, global("waterPressureDamageCap", WATER_PRESSURE_DAMAGE_CAP));
    }

    public static double waterPressureRadius() {
        return Math.max(0.5, global("waterPressureRadius", WATER_PRESSURE_RADIUS));
    }

    public static double zoneStackMultiplier() {
        return Math.max(1.0, global("zoneStackMultiplier", ZONE_STACK_MULTIPLIER));
    }

    public static int maxZoneCount() {
        return Math.max(1, globalInt("maxZoneCount", MAX_ZONE_COUNT));
    }

    /**
     * Spacing between deployed zones, in blocks along the lane path.
     *
     * <p>This used to be a fraction of the whole path, which scattered the zones far outside the
     * dolphins' range on a real arena: a 0.12 ratio on a long lane put the third zone dozens of
     * blocks from the tower that made it. Blocks keep the wall inside the family's working range
     * regardless of map size.
     */
    public static double zoneSpacingBlocks() {
        return clamp(global("zoneSpacingBlocks", ZONE_SPACING_BLOCKS), 1.0, 24.0);
    }

    public static int zoneScanIntervalTicks() {
        return Math.max(1, globalInt("zoneScanIntervalTicks", ZONE_SCAN_INTERVAL_TICKS));
    }

    public static int zoneVfxIntervalTicks() {
        return Math.max(1, globalInt("zoneVfxIntervalTicks", ZONE_VFX_INTERVAL_TICKS));
    }

    public static int maxChainDepth() {
        return Math.max(0, globalInt("maxChainDepth", MAX_CHAIN_DEPTH));
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
