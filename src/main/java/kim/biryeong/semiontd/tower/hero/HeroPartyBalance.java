package kim.biryeong.semiontd.tower.hero;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;

public final class HeroPartyBalance {
    public static final String GLOBAL_CONFIG_ID = "hero_party_global";
    public static final int MAX_COMPANIONS = 4;
    public static final int MAX_WEAPON_LEVEL = 5;
    public static final int MAX_ARMOR_LEVEL = 5;

    private static final long[] WEAPON_UPGRADE_COSTS = {0, 80, 140, 220, 320, 450};
    private static final double[] WEAPON_MULTIPLIERS = {1.0, 1.15, 1.32, 1.50, 1.72, 2.0};
    private static final long[] ARMOR_UPGRADE_COSTS = {0, 90, 150, 230, 340, 480};
    private static final double[] ARMOR_HEALTH = {0.0, 60.0, 140.0, 240.0, 380.0, 560.0};
    private static final double[] ARMOR_REDUCTION = {0.0, 0.04, 0.08, 0.12, 0.16, 0.20};

    private HeroPartyBalance() {
    }

    public static long weaponPurchaseCost(HeroWeapon weapon) {
        return Math.max(0L, Math.round(value(weapon.configId(), "purchaseCost", weapon.defaultPurchaseCost())));
    }

    public static double weaponDamage(HeroWeapon weapon) {
        return positive(value(weapon.configId(), "damage", weapon.defaultDamage()), weapon.defaultDamage());
    }

    public static double weaponRange(HeroWeapon weapon) {
        return positive(value(weapon.configId(), "range", weapon.defaultRange()), weapon.defaultRange());
    }

    public static int weaponAttackInterval(HeroWeapon weapon) {
        return Math.max(1, integer(weapon.configId(), "attackIntervalTicks", weapon.defaultAttackIntervalTicks()));
    }

    public static long weaponUpgradeCost(int level) {
        int resolved = bounded(level, MAX_WEAPON_LEVEL);
        return Math.max(0L, Math.round(global("weaponUpgradeCost" + resolved, WEAPON_UPGRADE_COSTS[resolved])));
    }

    public static double weaponMultiplier(int level) {
        int resolved = bounded(level, MAX_WEAPON_LEVEL);
        return positive(global("weaponMultiplier" + resolved, WEAPON_MULTIPLIERS[resolved]), WEAPON_MULTIPLIERS[resolved]);
    }

    public static long armorUpgradeCost(int level) {
        int resolved = bounded(level, MAX_ARMOR_LEVEL);
        return Math.max(0L, Math.round(global("armorUpgradeCost" + resolved, ARMOR_UPGRADE_COSTS[resolved])));
    }

    public static double armorHealth(int level) {
        int resolved = bounded(level, MAX_ARMOR_LEVEL);
        return Math.max(0.0, global("armorHealth" + resolved, ARMOR_HEALTH[resolved]));
    }

    public static double armorReduction(int level) {
        int resolved = bounded(level, MAX_ARMOR_LEVEL);
        return ratio(global("armorReduction" + resolved, ARMOR_REDUCTION[resolved]));
    }

    public static double partyDamageMultiplier(int adventurePoints) {
        return 1.0 + Math.max(0, adventurePoints) * global("adventureDamagePerPoint", 0.0025);
    }

    public static double partyHealingMultiplier(int adventurePoints) {
        return 1.0 + Math.max(0, adventurePoints) * global("adventureHealingPerPoint", 0.0025);
    }

    public static double partyHealthMultiplier(int adventurePoints) {
        return 1.0 + Math.max(0, adventurePoints) * global("adventureHealthPerPoint", 0.0035);
    }

    public static double global(String key, double fallback) {
        return value(GLOBAL_CONFIG_ID, key, fallback);
    }

    public static int globalInt(String key, int fallback) {
        return integer(GLOBAL_CONFIG_ID, key, fallback);
    }

    public static double tower(String towerId, String key, double fallback) {
        return value(towerId, key, fallback);
    }

    public static int towerInt(String towerId, String key, int fallback) {
        return integer(towerId, key, fallback);
    }

    private static double value(String configId, String key, double fallback) {
        double configured = TowerBalanceRuntime.ability(configId, key, fallback);
        return Double.isFinite(configured) ? configured : fallback;
    }

    private static int integer(String configId, String key, int fallback) {
        return TowerBalanceRuntime.abilityInt(configId, key, fallback);
    }

    private static int bounded(int level, int maximum) {
        return Math.max(0, Math.min(maximum, level));
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }

    private static double ratio(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(0.95, value)) : 0.0;
    }
}
