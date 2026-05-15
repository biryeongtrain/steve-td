package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.DamageType;

public final class IroncladTankSummon extends SummonMonsterType {
    public IroncladTankSummon() {
        super(
                "ironclad_tank",
                SummonDisplayNames.IRONCLAD_BOAR,
                70,
                5,
                130,
                8,
                7,
                AttackKind.MELEE,
                "minecraft:husk",
                "semion-td:summon/t2_ironclad_boar",
                DamageType.PHYSICAL,
                1,
                SummonTier.T2,
                List.of(SummonRole.TANK),
                List.of(SummonAbilityActivation.PASSIVE),
                8
        );
    }
}
