package kim.biryeong.semiontd.config;

import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;

public final class TowerBalanceRuntime {
    private static final TowerBalanceConfig DEFAULT_CONFIG = TowerBalanceConfig.defaultConfig();
    private static TowerBalanceConfig current = DEFAULT_CONFIG;

    private TowerBalanceRuntime() {
    }

    public static TowerBalanceConfig current() {
        return current;
    }

    public static void apply(TowerBalanceConfig config) {
        current = config == null ? DEFAULT_CONFIG : config;
    }

    public static TowerType resolve(TowerType defaults) {
        TowerBalanceConfig.TowerStats stats = current.statsFor(defaults);
        TowerType resolved = new TowerType(
                defaults.id(),
                defaults.displayName(),
                defaults.category(),
                stats.mineralCost(),
                stats.maxHealth(),
                stats.range(),
                stats.damage(),
                stats.attackIntervalTicks(),
                stats.aggroPriority(),
                defaults.description(),
                defaults.visual(),
                defaults.upgradeOptions()
        );
        return new TowerType(
                defaults.id(),
                defaults.displayName(),
                defaults.category(),
                stats.mineralCost(),
                stats.maxHealth(),
                stats.range(),
                stats.damage(),
                stats.attackIntervalTicks(),
                stats.aggroPriority(),
                TowerDescriptionRegistry.describe(resolved).orElse(defaults.description()),
                defaults.visual(),
                defaults.upgradeOptions()
        );
    }

    public static long upgradeCost(TowerType from, String upgradeId, long fallback) {
        return current.upgradeCost(from.id(), upgradeId, fallback);
    }

    public static long upgradeCost(TowerType from, String upgradeId) {
        return current.upgradeCost(from.id(), upgradeId, DEFAULT_CONFIG.upgradeCost(from.id(), upgradeId, 0));
    }

    public static double ability(String towerId, String key, double fallback) {
        return current.ability(towerId, key, fallback);
    }

    public static double ability(String towerId, String key) {
        return current.ability(towerId, key, DEFAULT_CONFIG.ability(towerId, key, 0.0));
    }

    public static int abilityTicks(String towerId, String key, int fallback) {
        return current.abilityTicks(towerId, key, fallback);
    }

    public static int abilityTicks(String towerId, String key) {
        return current.abilityTicks(towerId, key, DEFAULT_CONFIG.abilityTicks(towerId, key, 0));
    }

    public static int abilityInt(String towerId, String key, int fallback) {
        return current.abilityInt(towerId, key, fallback);
    }

    public static int abilityInt(String towerId, String key) {
        return current.abilityInt(towerId, key, DEFAULT_CONFIG.abilityInt(towerId, key, 0));
    }

    public static int illusionCloneSpawnSpreadTicks() {
        return current.illusionCloneQueue().resolvedSpreadTicks();
    }

    public static int illusionCloneMaxSpawnsPerTick() {
        return current.illusionCloneQueue().resolvedMaxSpawnsPerTick();
    }
}
