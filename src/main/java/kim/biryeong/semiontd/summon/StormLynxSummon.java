package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.goal.ApplyMonsterTimedEffectGoal;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public final class StormLynxSummon extends SummonMonsterType {
    public StormLynxSummon() {
        super(
                "storm_lynx",
                SummonDisplayNames.STORM_LYNX,
                190,
                10,
                190,
                2,
                22,
                AttackKind.MELEE,
                "minecraft:ocelot",
                null,
                DamageType.PHYSICAL,
                4,
                SummonTier.T4,
                List.of(SummonRole.RUSH),
                List.of(SummonAbilityActivation.PASSIVE),
                22
        );
    }

    @Override
    public List<Goal> createAbilityGoals(SemionMonsterEntity entity) {
        return List.of(ApplyMonsterTimedEffectGoal.self(
                entity,
                TimedEffectType.MONSTER_MOVE_SPEED_BONUS,
                SummonBalancePolicy.STORM_LYNX_MOVE_SPEED_BONUS,
                SummonBalancePolicy.STORM_LYNX_MOVE_SPEED_DURATION_TICKS,
                SummonBalancePolicy.STORM_LYNX_MOVE_SPEED_REFRESH_TICKS
        ));
    }
}
