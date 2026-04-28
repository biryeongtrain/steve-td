package kim.biryeong.semiontd.config;

public record WaveMonsterEntry(
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
        this(id, health, armor, attackDamage, attackKind, entityType, blockbenchModelId, 0, count);
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
        boolean hasEntityType = entityType != null && !entityType.isBlank();
        boolean hasBlockbenchModel = blockbenchModelId != null && !blockbenchModelId.isBlank();
        if (!hasEntityType && !hasBlockbenchModel) {
            throw new IllegalArgumentException("Wave monster must define entityType or blockbenchModelId.");
        }
    }
}
