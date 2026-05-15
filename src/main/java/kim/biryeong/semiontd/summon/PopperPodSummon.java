package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;

public final class PopperPodSummon extends SummonMonsterType {
    public PopperPodSummon() {
        super(
                "popper_pod",
                SummonDisplayNames.PINCER_CRAB,
                35,
                3,
                68,
                1,
                8,
                AttackKind.MELEE,
                "minecraft:armadillo",
                "semion-td:summon/t1_pincer_crab",
                MonsterDimensions.of(0.95, 0.9),
                DamageType.PHYSICAL,
                0,
                SummonTier.T1,
                List.of(SummonRole.SIEGE),
                List.of(SummonAbilityActivation.CONDITIONAL),
                7
        );
    }
}
