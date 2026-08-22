package kim.biryeong.semiontd.tower.succubus;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;

public final class SuccubusBalance {
    public static final String CONFIG_ID = "succubus_global";
    public static final int MAX_STACKS = 10;
    public static final int STACK_DURATION_TICKS = 240;
    public static final int SLEEP_DURATION_TICKS = 100;
    public static final int TOWER_SLEEP_DURATION_TICKS = 40;
    public static final int AWAKENED_IMMUNITY_TICKS = 60;
    public static final int SPREAD_STACKS = 2;
    public static final double SPREAD_RADIUS = 2.5;
    public static final double ALLY_DAMAGE_PER_STACK = 0.07;
    public static final double ALLY_ATTACK_SPEED_PER_STACK = 0.035;
    public static final double ENEMY_ATTACK_SPEED_PER_STACK = 0.05;
    public static final double ENEMY_MOVE_SPEED_PER_STACK = 0.05;
    public static final double SUCCUBUS_AMPLIFICATION = 0.50;
    public static final double MONSTER_WAKE_DAMAGE_THRESHOLD = 0.40;
    public static final double TOWER_WAKE_DAMAGE_THRESHOLD = 0.10;
    public static final double MONSTER_WAKE_BONUS_DAMAGE = 0.20;
    public static final double TOWER_WAKE_BONUS_DAMAGE = 0.10;
    public static final int EXECUTION_SLEEP_COUNT = 3;
    public static final double ABSORB_ATTACK_RATIO = 0.03;
    public static final double ABSORB_MAX_HEALTH_RATIO = 0.01;

    private SuccubusBalance() {
    }

    public static double global(String key, double fallback) {
        return TowerBalanceRuntime.ability(CONFIG_ID, key, fallback);
    }

    public static int globalInt(String key, int fallback) {
        return Math.max(0, TowerBalanceRuntime.abilityInt(CONFIG_ID, key, fallback));
    }

    public static int maxStacks() {return Math.max(1, globalInt("maxStacks", MAX_STACKS));}
    public static int stackDurationTicks() {return Math.max(1, globalInt("stackDurationTicks", STACK_DURATION_TICKS));}
    public static int sleepDurationTicks() {return Math.max(1, globalInt("sleepDurationTicks", SLEEP_DURATION_TICKS));}
    public static int towerSleepDurationTicks() {return Math.max(1, globalInt("towerSleepDurationTicks", TOWER_SLEEP_DURATION_TICKS));}
    public static int awakenedImmunityTicks() {return Math.max(1, globalInt("awakenedImmunityTicks", AWAKENED_IMMUNITY_TICKS));}
    public static int spreadStacks() {return Math.max(1, globalInt("spreadStacks", SPREAD_STACKS));}
    public static double spreadRadius() {return Math.max(0.5, global("spreadRadius", SPREAD_RADIUS));}
    public static double allyDamagePerStack() {return ratio("allyDamagePerStack", ALLY_DAMAGE_PER_STACK);}
    public static double allyAttackSpeedPerStack() {return ratio("allyAttackSpeedPerStack", ALLY_ATTACK_SPEED_PER_STACK);}
    public static double enemyAttackSpeedPerStack() {return ratio("enemyAttackSpeedPerStack", ENEMY_ATTACK_SPEED_PER_STACK);}
    public static double enemyMoveSpeedPerStack() {return ratio("enemyMoveSpeedPerStack", ENEMY_MOVE_SPEED_PER_STACK);}
    public static double amplification() {return ratio("succubusAmplification", SUCCUBUS_AMPLIFICATION);}
    public static double monsterWakeDamageThreshold() {return ratio("monsterWakeDamageThreshold", MONSTER_WAKE_DAMAGE_THRESHOLD);}
    public static double towerWakeDamageThreshold() {return ratio("towerWakeDamageThreshold", TOWER_WAKE_DAMAGE_THRESHOLD);}
    public static double monsterWakeBonusDamage() {return ratio("monsterWakeBonusDamage", MONSTER_WAKE_BONUS_DAMAGE);}
    public static double towerWakeBonusDamage() {return ratio("towerWakeBonusDamage", TOWER_WAKE_BONUS_DAMAGE);}
    public static int executionSleepCount() {return Math.max(1, globalInt("executionSleepCount", EXECUTION_SLEEP_COUNT));}
    public static double absorbAttackRatio() {return ratio("absorbAttackRatio", ABSORB_ATTACK_RATIO);}
    public static double absorbMaxHealthRatio() {return ratio("absorbMaxHealthRatio", ABSORB_MAX_HEALTH_RATIO);}

    public static double ability(String towerId, String key, double fallback) {
        return TowerBalanceRuntime.ability(towerId, key, fallback);
    }

    public static int abilityInt(String towerId, String key, int fallback) {
        return Math.max(0, TowerBalanceRuntime.abilityInt(towerId, key, fallback));
    }

    private static double ratio(String key, double fallback) {
        return Math.max(0.0, Math.min(0.95, global(key, fallback)));
    }
}
