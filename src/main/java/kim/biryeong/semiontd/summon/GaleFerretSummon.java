package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.DamageType;

public final class GaleFerretSummon extends SummonMonsterType {
    public GaleFerretSummon() {
        super(
                "gale_ferret",
                SummonDisplayNames.GALE_FERRET,
                100,
                7,
                135,
                1,
                11,
                AttackKind.MELEE,
                "minecraft:fox",
                "semion-td:summon/t3_gale_ferret",
                DamageType.PHYSICAL,
                2,
                SummonTier.T3,
                List.of(SummonRole.RUSH),
                List.of(SummonAbilityActivation.PASSIVE),
                12
        );
    }
}
