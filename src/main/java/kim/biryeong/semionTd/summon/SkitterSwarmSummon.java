package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.DamageType;

public final class SkitterSwarmSummon extends SummonMonsterType {
    public SkitterSwarmSummon() {
        super(
                "skitter_swarm",
                "Skitter Swarm",
                30,
                3,
                24,
                0,
                3,
                AttackKind.MELEE,
                "minecraft:silverfish",
                null,
                DamageType.PHYSICAL,
                0,
                SummonTier.T1,
                List.of(SummonRole.SWARM),
                List.of(SummonAbilityActivation.PASSIVE),
                3
        );
    }
}
