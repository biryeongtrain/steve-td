package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.DamageType;

public final class SkitterSwarmSummon extends SummonMonsterType {
    public SkitterSwarmSummon() {
        super(
                "skitter_swarm",
                SummonDisplayNames.HONEY_BEE,
                30,
                3,
                24,
                0,
                2,
                AttackKind.MELEE,
                "minecraft:silverfish",
                "semion-td:summon/t1_honey_bee",
                DamageType.PHYSICAL,
                0,
                SummonTier.T1,
                List.of(SummonRole.SWARM),
                List.of(SummonAbilityActivation.PASSIVE),
                3
        );
    }
}
