package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.DamageType;

public final class WardTankSummon extends SummonMonsterType {
    public WardTankSummon() {
        super(
                "ward_tank",
                "Ward Tank",
                75,
                5,
                115,
                1,
                6,
                AttackKind.MELEE,
                "minecraft:zombie_villager",
                null,
                DamageType.MAGIC,
                8,
                SummonTier.T2,
                List.of(SummonRole.TANK),
                List.of(SummonAbilityActivation.PASSIVE),
                8
        );
    }
}
