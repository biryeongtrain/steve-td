package kim.biryeong.semiontd.entity.monster;

import kim.biryeong.semiontd.config.AttackKind;

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
