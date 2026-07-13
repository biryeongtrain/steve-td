package kim.biryeong.semiontd.config;

import kim.biryeong.semiontd.entity.monster.MonsterDimensions;

public record WaveMonsterEntry(
        String id,
        double health,
        double armor,
        double attackDamage,
        AttackKind attackKind,
        String entityType,
        String blockbenchModelId,
        MonsterDimensions dimensions,
        long mineralReward,
        int count,
        double targetPriority,
        double movementSpeedMultiplier,
        double attackRange,
        int attackIntervalTicks
) {
    public static final double DEFAULT_MELEE_ATTACK_RANGE = 2.5;
    public static final double DEFAULT_RANGED_ATTACK_RANGE = 6.0;
    public static final double DEFAULT_MOVEMENT_SPEED_MULTIPLIER = 1.0;
    public static final int DEFAULT_ATTACK_INTERVAL_TICKS = 13;

    public WaveMonsterEntry(
            String id,
            double health,
            double armor,
            double attackDamage,
            AttackKind attackKind,
            String entityType,
            String blockbenchModelId,
            int count
    ) {
        this(id, health, armor, attackDamage, attackKind, entityType, blockbenchModelId, MonsterDimensions.DEFAULT, 0, count);
    }

    public WaveMonsterEntry(
            String id,
            double health,
            double armor,
            double attackDamage,
            AttackKind attackKind,
            String entityType,
            String blockbenchModelId,
            long mineralReward,
            int count
    ) {
        this(id, health, armor, attackDamage, attackKind, entityType, blockbenchModelId, MonsterDimensions.DEFAULT, mineralReward, count);
    }

    public WaveMonsterEntry(
            String id,
            double health,
            double armor,
            double attackDamage,
            AttackKind attackKind,
            String entityType,
            String blockbenchModelId,
            MonsterDimensions dimensions,
            int count
    ) {
        this(id, health, armor, attackDamage, attackKind, entityType, blockbenchModelId, dimensions, 0, count);
    }

    public WaveMonsterEntry(
            String id,
            double health,
            double armor,
            double attackDamage,
            AttackKind attackKind,
            String entityType,
            String blockbenchModelId,
            MonsterDimensions dimensions,
            long mineralReward,
            int count
    ) {
        this(
                id,
                health,
                armor,
                attackDamage,
                attackKind,
                entityType,
                blockbenchModelId,
                dimensions,
                mineralReward,
                count,
                0.0,
                DEFAULT_MOVEMENT_SPEED_MULTIPLIER,
                defaultAttackRange(attackKind),
                DEFAULT_ATTACK_INTERVAL_TICKS
        );
    }

    public WaveMonsterEntry {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Wave monster id cannot be blank.");
        }
        if (health <= 0) {
            throw new IllegalArgumentException("Wave monster health must be positive.");
        }
        if (armor < 0 || attackDamage < 0 || mineralReward < 0 || count < 0) {
            throw new IllegalArgumentException("Wave monster numeric values cannot be negative.");
        }
        if (attackKind == null) {
            attackKind = AttackKind.MELEE;
        }
        if (!Double.isFinite(targetPriority) || targetPriority < 0.0) {
            throw new IllegalArgumentException("Wave monster target priority must be finite and non-negative.");
        }
        if (movementSpeedMultiplier == 0.0) {
            movementSpeedMultiplier = DEFAULT_MOVEMENT_SPEED_MULTIPLIER;
        }
        if (!Double.isFinite(movementSpeedMultiplier) || movementSpeedMultiplier < 0.0) {
            throw new IllegalArgumentException("Wave monster movement speed multiplier must be finite and positive.");
        }
        if (attackRange == 0.0) {
            attackRange = defaultAttackRange(attackKind);
        }
        if (!Double.isFinite(attackRange) || attackRange < 0.0) {
            throw new IllegalArgumentException("Wave monster attack range must be finite and positive.");
        }
        if (attackIntervalTicks == 0) {
            attackIntervalTicks = DEFAULT_ATTACK_INTERVAL_TICKS;
        }
        if (attackIntervalTicks < 0) {
            throw new IllegalArgumentException("Wave monster attack interval must be positive.");
        }
        dimensions = MonsterDimensions.orDefault(dimensions);
        boolean hasEntityType = entityType != null && !entityType.isBlank();
        boolean hasBlockbenchModel = blockbenchModelId != null && !blockbenchModelId.isBlank();
        if (!hasEntityType && !hasBlockbenchModel) {
            throw new IllegalArgumentException("Wave monster must define entityType or blockbenchModelId.");
        }
    }

    public static double defaultAttackRange(AttackKind attackKind) {
        return attackKind == AttackKind.RANGED ? DEFAULT_RANGED_ATTACK_RANGE : DEFAULT_MELEE_ATTACK_RANGE;
    }
}
