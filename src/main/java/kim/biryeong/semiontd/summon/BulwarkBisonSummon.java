package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;

public final class BulwarkBisonSummon extends SummonMonsterType {
    public BulwarkBisonSummon() {
        super(
                "bulwark_bison",
                SummonDisplayNames.BULWARK_BISON,
                150,
                10,
                300,
                12,
                9,
                AttackKind.MELEE,
                "minecraft:hoglin",
                "semion-td:summon/t3_bulwark_bison",
                MonsterDimensions.of(1.35, 1.15),
                DamageType.PHYSICAL,
                4,
                SummonTier.T3,
                List.of(SummonRole.TANK),
                List.of(SummonAbilityActivation.PASSIVE),
                17
        );
    }
}
