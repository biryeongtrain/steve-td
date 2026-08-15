package kim.biryeong.semiontd.tower.insect;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;

public final class InsectBalance {
    public static final String GLOBAL_ID = "insect_global";
    public static final double FRESH_POWER_MULTIPLIER = 1.75;
    public static final double FRESH_POWER_SCALE = 1.2;
    public static final int REVIVE_BASE_TICKS = 80;
    public static final int REVIVE_INCREMENT_TICKS = 60;
    public static final int RADIUS_VFX_INTERVAL_TICKS = 80;
    public static final double DEATH_DAMAGE_TAKEN_PER_STACK = 0.20;
    public static final double SPAWNER_RADIUS = 6.0;

    private InsectBalance() {
    }

    public static double freshPowerMultiplier() {
        return TowerBalanceRuntime.ability(GLOBAL_ID, "freshPowerMultiplier", FRESH_POWER_MULTIPLIER);
    }

    public static double freshPowerScale() {
        return TowerBalanceRuntime.ability(GLOBAL_ID, "freshPowerScale", FRESH_POWER_SCALE);
    }

    public static int reviveBaseTicks() {
        return TowerBalanceRuntime.abilityTicks(GLOBAL_ID, "reviveBaseTicks", REVIVE_BASE_TICKS);
    }

    public static int reviveIncrementTicks() {
        return TowerBalanceRuntime.abilityTicks(GLOBAL_ID, "reviveIncrementTicks", REVIVE_INCREMENT_TICKS);
    }

    public static int radiusVfxIntervalTicks() {
        return TowerBalanceRuntime.abilityTicks(
                GLOBAL_ID, "radiusVfxIntervalTicks", RADIUS_VFX_INTERVAL_TICKS);
    }

    public static double deathDamageTakenPerStack() {
        return TowerBalanceRuntime.ability(
                GLOBAL_ID, "deathDamageTakenPerStack", DEATH_DAMAGE_TAKEN_PER_STACK);
    }

    public static double spawnerRadius() {
        return TowerBalanceRuntime.ability(InsectTowers.SPAWNER.id(), "reviveRadius", SPAWNER_RADIUS);
    }

    public static double spiderDamageReduction(int tier) {
        double fallback = switch (Math.max(1, Math.min(3, tier))) {
            case 1 -> 0.10;
            case 2 -> 0.20;
            default -> 0.30;
        };
        return TowerBalanceRuntime.ability(InsectTowers.spider(tier).id(), "damageReduction", fallback);
    }
}
