package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.DamageType;

public final class StaticDisruptorSummon extends SummonMonsterType {
    public StaticDisruptorSummon() {
        super(
                "static_disruptor",
                "Static Disruptor",
                65,
                4,
                55,
                0,
                4,
                AttackKind.RANGED,
                "minecraft:witch",
                null,
                DamageType.MAGIC,
                3,
                SummonTier.T2,
                List.of(SummonRole.DISRUPTOR),
                List.of(SummonAbilityActivation.COOLDOWN),
                7
        );
    }
}
