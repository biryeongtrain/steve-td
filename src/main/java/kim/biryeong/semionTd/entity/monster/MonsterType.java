package kim.biryeong.semionTd.entity.monster;

import kim.biryeong.semionTd.config.AttackKind;

public record MonsterType(
        String id,
        long gasCost,
        long incomeGain,
        double maxHealth,
        double armor,
        double attackDamage,
        AttackKind attackKind,
        long mineralReward
) {
}
