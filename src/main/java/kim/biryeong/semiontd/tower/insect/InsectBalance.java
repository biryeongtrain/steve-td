package kim.biryeong.semiontd.tower.insect;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.TowerType;

public final class InsectBalance {
    public static final String GLOBAL_ID = "insect_global";
    public static final double FRESH_POWER_MULTIPLIER = 2.0;
    public static final double FRESH_DAMAGE_TAKEN_MULTIPLIER = 3.0;
    public static final double FRESH_POWER_SCALE = 1.2;
    public static final int REVIVE_BASE_TICKS = 120;
    public static final int REVIVE_INCREMENT_TICKS = 60;
    public static final int BEE_REVIVE_BASE_TICKS = 40;
    public static final int BEE_REVIVE_INCREMENT_TICKS = 30;
    public static final double REVIVE_HEALTH_LOSS_RATIO = 0.05;
    public static final double TIER1_DEATH_EXPLOSION_HEALTH_RATIO = 0.25;
    public static final double TIER2_DEATH_EXPLOSION_HEALTH_RATIO = 0.4;
    public static final double DEATH_EXPLOSION_HEALTH_RATIO = 0.5;
    public static final double TIER1_DEATH_EXPLOSION_RADIUS = 2.0;
    public static final double DEATH_EXPLOSION_RADIUS = 3.0;
    public static final double CONTACT_DETONATION_RANGE = 1.5;
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

    public static double freshDamageTakenMultiplier() {
        return TowerBalanceRuntime.ability(GLOBAL_ID, "freshDamageTakenMultiplier", FRESH_DAMAGE_TAKEN_MULTIPLIER);
    }

    public static double reviveHealthLossRatio() {
        return TowerBalanceRuntime.ability(GLOBAL_ID, "reviveHealthLossRatio", REVIVE_HEALTH_LOSS_RATIO);
    }

    public static double deathExplosionHealthRatio(TowerType type) {
        int tier = InsectTowers.tier(type);
        double fallback = switch (tier) {
            case 1 -> TIER1_DEATH_EXPLOSION_HEALTH_RATIO;
            case 2 -> TIER2_DEATH_EXPLOSION_HEALTH_RATIO;
            default -> DEATH_EXPLOSION_HEALTH_RATIO;
        };
        return TowerBalanceRuntime.ability(GLOBAL_ID, deathExplosionHealthRatioKey(tier), fallback);
    }

    public static double deathExplosionRadius(TowerType type) {
        int tier = InsectTowers.tier(type);
        return TowerBalanceRuntime.ability(GLOBAL_ID, deathExplosionRadiusKey(tier),
                tier == 1 ? TIER1_DEATH_EXPLOSION_RADIUS : DEATH_EXPLOSION_RADIUS);
    }

    static String deathExplosionHealthRatioKey(int tier) {
        return switch (tier) {
            case 1 -> "tier1DeathExplosionHealthRatio";
            case 2 -> "tier2DeathExplosionHealthRatio";
            default -> "deathExplosionHealthRatio";
        };
    }

    static String deathExplosionRadiusKey(int tier) {
        return tier == 1 ? "tier1DeathExplosionRadius" : "deathExplosionRadius";
    }

    public static double contactDetonationRange() {
        return TowerBalanceRuntime.ability(GLOBAL_ID, "contactDetonationRange", CONTACT_DETONATION_RANGE);
    }

    public static int reviveBaseTicks(TowerType type) {
        if (InsectTowers.line(type) == InsectTowers.UnitLine.BEE) {
            return TowerBalanceRuntime.abilityTicks(GLOBAL_ID, "beeReviveBaseTicks", BEE_REVIVE_BASE_TICKS);
        }
        return TowerBalanceRuntime.abilityTicks(GLOBAL_ID, "reviveBaseTicks", REVIVE_BASE_TICKS);
    }

    public static int reviveIncrementTicks(TowerType type) {
        if (InsectTowers.line(type) == InsectTowers.UnitLine.BEE) {
            return TowerBalanceRuntime.abilityTicks(GLOBAL_ID, "beeReviveIncrementTicks", BEE_REVIVE_INCREMENT_TICKS);
        }
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
