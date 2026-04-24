package kim.biryeong.semionTd.config;

public record WaveMonsterEntry(
        String id,
        double health,
        double armor,
        double attackDamage,
        AttackKind attackKind,
        String entityType,
        String bliModelId,
        int count
) {
    public WaveMonsterEntry {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Wave monster id cannot be blank.");
        }
        if (health <= 0) {
            throw new IllegalArgumentException("Wave monster health must be positive.");
        }
        if (armor < 0 || attackDamage < 0 || count < 0) {
            throw new IllegalArgumentException("Wave monster numeric values cannot be negative.");
        }
        if (attackKind == null) {
            attackKind = AttackKind.MELEE;
        }
        boolean hasEntityType = entityType != null && !entityType.isBlank();
        boolean hasBliModel = bliModelId != null && !bliModelId.isBlank();
        if (!hasEntityType && !hasBliModel) {
            throw new IllegalArgumentException("Wave monster must define entityType or bliModelId.");
        }
    }
}
