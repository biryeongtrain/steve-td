package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.goal.ApplyMonsterTimedEffectGoal;
import kim.biryeong.semiontd.entity.goal.ApplyTowerTimedEffectGoal;
import kim.biryeong.semiontd.entity.goal.AreaAllyHealGoal;
import kim.biryeong.semiontd.entity.goal.SingleAllyHealGoal;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public final class OraclePhoenixSummon extends SummonMonsterType {
    public OraclePhoenixSummon() {
        super(
                "oracle_phoenix",
                SummonDisplayNames.ORACLE_PHOENIX,
                470,
                21,
                560,
                4,
                17,
                AttackKind.RANGED,
                "minecraft:blaze",
                null,
                DamageType.MAGIC,
                20,
                SummonTier.T5,
                List.of(SummonRole.SUPPORT, SummonRole.DISRUPTOR),
                List.of(SummonAbilityActivation.COOLDOWN),
                52
        );
    }

    @Override
    public List<Goal> createAbilityGoals(SemionMonsterEntity entity) {
        return List.of(
                new SingleAllyHealGoal<>(
                        entity,
                        SemionMonsterEntity.class,
                        SummonBalancePolicy.T5_SUPPORT_SINGLE_HEAL_RADIUS,
                        SummonBalancePolicy.T5_SUPPORT_SINGLE_HEAL_AMOUNT,
                        SummonBalancePolicy.T5_SUPPORT_SINGLE_HEAL_COOLDOWN_TICKS,
                        SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS
                ),
                new AreaAllyHealGoal<>(
                        entity,
                        SemionMonsterEntity.class,
                        SummonBalancePolicy.T5_SUPPORT_AREA_HEAL_RADIUS,
                        SummonBalancePolicy.T5_SUPPORT_AREA_HEAL_AMOUNT,
                        SummonBalancePolicy.T5_SUPPORT_AREA_HEAL_MAX_TARGETS,
                        SummonBalancePolicy.T5_SUPPORT_AREA_HEAL_COOLDOWN_TICKS,
                        SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS
                ),
                new ApplyMonsterTimedEffectGoal(
                        entity,
                        TimedEffectType.MONSTER_MOVE_SPEED_BONUS,
                        SummonBalancePolicy.ORACLE_PHOENIX_MOVE_SPEED_BONUS,
                        SummonBalancePolicy.ORACLE_PHOENIX_BLESSING_RADIUS,
                        SummonBalancePolicy.ORACLE_PHOENIX_BLESSING_DURATION_TICKS,
                        SummonBalancePolicy.ORACLE_PHOENIX_BLESSING_COOLDOWN_TICKS,
                        SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS,
                        Integer.MAX_VALUE
                ),
                new ApplyTowerTimedEffectGoal(
                        entity,
                        TimedEffectType.TOWER_RANGE_REDUCTION,
                        SummonBalancePolicy.ORACLE_PHOENIX_RANGE_REDUCTION,
                        SummonBalancePolicy.ORACLE_PHOENIX_RANGE_RADIUS,
                        SummonBalancePolicy.ORACLE_PHOENIX_RANGE_DURATION_TICKS,
                        SummonBalancePolicy.ORACLE_PHOENIX_RANGE_COOLDOWN_TICKS,
                        SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS,
                        Integer.MAX_VALUE
                )
        );
    }
}
