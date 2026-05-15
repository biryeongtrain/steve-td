package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.goal.AreaAllyHealGoal;
import kim.biryeong.semiontd.entity.goal.SingleAllyHealGoal;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public final class PulseSupportSummon extends SummonMonsterType {
    public PulseSupportSummon() {
        super(
                "pulse_support",
                SummonDisplayNames.PULSE_FAWN,
                75,
                5,
                60,
                0,
                3,
                AttackKind.RANGED,
                "minecraft:evoker",
                "semion-td:summon/t2_pulse_fawn",
                DamageType.MAGIC,
                4,
                SummonTier.T2,
                List.of(SummonRole.SUPPORT),
                List.of(SummonAbilityActivation.COOLDOWN),
                7
        );
    }

    @Override
    public List<Goal> createAbilityGoals(SemionMonsterEntity entity) {
        return List.of(
                new SingleAllyHealGoal<>(
                        entity,
                        SemionMonsterEntity.class,
                        SummonBalancePolicy.SUPPORT_SINGLE_HEAL_RADIUS,
                        SummonBalancePolicy.SUPPORT_SINGLE_HEAL_AMOUNT,
                        SummonBalancePolicy.SUPPORT_SINGLE_HEAL_COOLDOWN_TICKS,
                        SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS
                ),
                new AreaAllyHealGoal<>(
                        entity,
                        SemionMonsterEntity.class,
                        SummonBalancePolicy.SUPPORT_AREA_HEAL_RADIUS,
                        SummonBalancePolicy.SUPPORT_AREA_HEAL_AMOUNT,
                        SummonBalancePolicy.SUPPORT_AREA_HEAL_MAX_TARGETS,
                        SummonBalancePolicy.SUPPORT_AREA_HEAL_COOLDOWN_TICKS,
                        SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS
                )
        );
    }
}
