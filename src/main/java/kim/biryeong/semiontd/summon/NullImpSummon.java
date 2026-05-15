package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.goal.ApplyTowerTimedEffectGoal;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public final class NullImpSummon extends SummonMonsterType {
    public NullImpSummon() {
        super(
                "null_imp",
                SummonDisplayNames.NULL_IMP,
                220,
                12,
                170,
                1,
                13,
                AttackKind.RANGED,
                "minecraft:vex",
                null,
                DamageType.MAGIC,
                14,
                SummonTier.T4,
                List.of(SummonRole.DISRUPTOR),
                List.of(SummonAbilityActivation.COOLDOWN),
                25
        );
    }

    @Override
    public List<Goal> createAbilityGoals(SemionMonsterEntity entity) {
        return List.of(new ApplyTowerTimedEffectGoal(
                entity,
                TimedEffectType.TOWER_RANGE_REDUCTION,
                SummonBalancePolicy.NULL_IMP_RANGE_REDUCTION,
                SummonBalancePolicy.NULL_IMP_RANGE_RADIUS,
                SummonBalancePolicy.NULL_IMP_RANGE_DURATION_TICKS,
                SummonBalancePolicy.NULL_IMP_RANGE_COOLDOWN_TICKS,
                SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS,
                1
        ));
    }
}
