package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.DamageType;

public final class StaticDisruptorSummon extends SummonMonsterType {
    public StaticDisruptorSummon() {
        super(
                "static_disruptor",
                SummonDisplayNames.STATIC_OWL,
                65,
                4,
                55,
                0,
                3,
                AttackKind.RANGED,
                "minecraft:witch",
                "semion-td:summon/t2_static_owl",
                DamageType.MAGIC,
                3,
                SummonTier.T2,
                List.of(SummonRole.DISRUPTOR),
                List.of(SummonAbilityActivation.COOLDOWN),
                7
        );
    }
}
