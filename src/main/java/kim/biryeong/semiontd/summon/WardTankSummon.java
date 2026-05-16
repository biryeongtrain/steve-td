package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.DamageType;

public final class WardTankSummon extends SummonMonsterType {
    public WardTankSummon() {
        super(
                "ward_tank",
                SummonDisplayNames.WARD_RAM,
                75,
                5,
                115,
                1,
                4,
                AttackKind.MELEE,
                "minecraft:zombie_villager",
                "semion-td:summon/t2_ward_ram",
                DamageType.MAGIC,
                8,
                SummonTier.T2,
                List.of(SummonRole.TANK),
                List.of(SummonAbilityActivation.PASSIVE),
                8
        );
    }
}
