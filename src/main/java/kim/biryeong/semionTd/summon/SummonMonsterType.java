package kim.biryeong.semionTd.summon;

import kim.biryeong.semionTd.config.AttackKind;

public record SummonMonsterType(
        String id,
        String displayName,
        long gasCost,
        long incomeGain,
        double maxHealth,
        double armor,
        double attackDamage,
        AttackKind attackKind,
        String entityTypeId,
        long mineralReward
) {
    public SummonMonsterType {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Summon monster id cannot be blank.");
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = id;
        }
        if (gasCost < 0 || incomeGain < 0 || maxHealth <= 0 || armor < 0 || attackDamage < 0 || mineralReward < 0) {
            throw new IllegalArgumentException("Summon monster numeric values are invalid.");
        }
        if (attackKind == null) {
            attackKind = AttackKind.MELEE;
        }
        if (entityTypeId == null || entityTypeId.isBlank()) {
            entityTypeId = "minecraft:zombie";
        }
    }
}
