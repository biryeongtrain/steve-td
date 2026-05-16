package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;

public final class QuiltGuardSummon extends SummonMonsterType {
    public QuiltGuardSummon() {
        super(
                "quilt_guard",
                SummonDisplayNames.SHELL_TURTLE,
                35,
                3,
                88,
                4,
                2,
                AttackKind.MELEE,
                "minecraft:husk",
                "semion-td:summon/t1_shell_turtle",
                MonsterDimensions.of(0.75, 1.3),
                DamageType.PHYSICAL,
                1,
                SummonTier.T1,
                List.of(SummonRole.TANK),
                List.of(SummonAbilityActivation.PASSIVE),
                6
        );
    }
}
