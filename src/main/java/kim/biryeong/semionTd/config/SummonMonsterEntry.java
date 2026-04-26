package kim.biryeong.semionTd.config;

import kim.biryeong.semionTd.summon.SummonMonsterType;

public record SummonMonsterEntry(
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
    public SummonMonsterEntry {
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

    public SummonMonsterType toType() {
        return new SummonMonsterType(id, displayName, gasCost, incomeGain, maxHealth, armor, attackDamage, attackKind, entityTypeId, mineralReward);
    }
}