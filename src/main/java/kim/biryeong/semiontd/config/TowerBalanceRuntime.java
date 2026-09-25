package kim.biryeong.semiontd.config;

import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;

public final class TowerBalanceRuntime {
    private static final TowerBalanceConfig DEFAULT_CONFIG = TowerBalanceConfig.defaultConfig();
    private static volatile TowerBalanceConfig current = DEFAULT_CONFIG;

    private TowerBalanceRuntime() {
    }

    public static TowerBalanceConfig current() {
        return current;
    }

    public static void apply(TowerBalanceConfig config) {
        TowerBalanceConfig next = config == null ? DEFAULT_CONFIG : config;
        next.validateForRuntime();
        current = next;
    }

    public static TowerType resolve(TowerType defaults) {
        return resolve(defaults, current);
    }

    public static TowerType resolve(TowerType defaults, TowerBalanceConfig config) {
        TowerBalanceConfig.TowerStats stats = config.statsFor(defaults);
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
                defaults.upgradeOptions(),
                defaults.primaryDamageType()
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
                TowerDescriptionRegistry.describe(resolved, config).orElse(defaults.description()),
                defaults.visual(),
                defaults.upgradeOptions(),
                defaults.primaryDamageType()
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
        return ability(current, towerId, key);
    }

    public static double ability(TowerBalanceConfig config, String towerId, String key) {
        return config.ability(towerId, key, DEFAULT_CONFIG.ability(towerId, key, 0.0));
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

    public static TowerBalanceConfig.VillagerAdvConfig villagerAdv() {
        return current.villagerAdv();
    }

    public static double villagerAdvUpgradeRequirement(TowerType from, String upgradeId) {
        if (from == null || upgradeId == null || upgradeId.isBlank()) {
            return 0.0;
        }
        return current.villagerAdv().upgradeRequirement(from.id(), upgradeId);
    }
}
