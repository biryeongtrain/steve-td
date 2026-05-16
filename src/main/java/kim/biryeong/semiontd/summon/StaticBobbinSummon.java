package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;

public final class StaticBobbinSummon extends SummonMonsterType {
    public StaticBobbinSummon() {
        super(
                "static_bobbin",
                SummonDisplayNames.SPARK_AXOLOTL,
                25,
                2,
                34,
                0,
                1,
                AttackKind.RANGED,
                "minecraft:breeze",
                "semion-td:summon/t1_spark_axolotl",
                MonsterDimensions.of(0.6, 1.0),
                DamageType.MAGIC,
                2,
                SummonTier.T1,
                List.of(SummonRole.DISRUPTOR),
                List.of(SummonAbilityActivation.PASSIVE),
                4
        );
    }
}
