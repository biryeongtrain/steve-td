package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.DamageType;

public final class GruntSummon extends SummonMonsterType {
    public GruntSummon() {
        super(
                "grunt",
                SummonDisplayNames.FOX_KIT,
                20,
                2,
                50,
                0,
                5,
                AttackKind.MELEE,
                "minecraft:zombie",
                "semion-td:summon/t1_fox_kit",
                DamageType.PHYSICAL,
                0,
                SummonTier.T1,
                List.of(SummonRole.RUSH),
                List.of(SummonAbilityActivation.PASSIVE),
                5
        );
    }
}
