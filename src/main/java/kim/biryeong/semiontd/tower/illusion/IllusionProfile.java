package kim.biryeong.semiontd.tower.illusion;

public record IllusionProfile(
        int cloneCount,
        int durationTicks,
        double healthRatio,
        double damageRatio,
        double rangeRatio,
        double attackIntervalMultiplier,
        double spawnRadius,
        int aggroPriorityBonus
) {
    public static final int DEFAULT_CLONE_COUNT = 1;
    public static final int DEFAULT_DURATION_TICKS = 0;
    public static final double DEFAULT_HEALTH_RATIO = 0.5;
    public static final double DEFAULT_DAMAGE_RATIO = 0.5;
    public static final double DEFAULT_RANGE_RATIO = 1.0;
    public static final double DEFAULT_ATTACK_INTERVAL_MULTIPLIER = 1.0;
    public static final double DEFAULT_SPAWN_RADIUS = 1.5;
    public static final int DEFAULT_AGGRO_PRIORITY_BONUS = 5;

    public static IllusionProfile defaults() {
        return new IllusionProfile(
                DEFAULT_CLONE_COUNT,
                DEFAULT_DURATION_TICKS,
                DEFAULT_HEALTH_RATIO,
                DEFAULT_DAMAGE_RATIO,
                DEFAULT_RANGE_RATIO,
                DEFAULT_ATTACK_INTERVAL_MULTIPLIER,
                DEFAULT_SPAWN_RADIUS,
                DEFAULT_AGGRO_PRIORITY_BONUS
        );
    }

    public IllusionProfile {
        cloneCount = Math.max(0, cloneCount);
        healthRatio = Math.max(0.01, healthRatio);
        damageRatio = Math.max(0.0, damageRatio);
        rangeRatio = Math.max(0.0, rangeRatio);
        attackIntervalMultiplier = Math.max(0.01, attackIntervalMultiplier);
        spawnRadius = Math.max(0.0, spawnRadius);
    }
}
