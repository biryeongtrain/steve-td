package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.goal.SingleAllyHealGoal;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public final class ButtonNurseSummon extends SummonMonsterType {
    public ButtonNurseSummon() {
        super(
                "button_nurse",
                SummonDisplayNames.MEDIC_DUCK,
                35,
                3,
                38,
                0,
                1,
                AttackKind.RANGED,
                "minecraft:allay",
                "semion-td:summon/t1_medic_duck",
                MonsterDimensions.of(0.6, 0.95),
                DamageType.MAGIC,
                2,
                SummonTier.T1,
                List.of(SummonRole.SUPPORT),
                List.of(SummonAbilityActivation.COOLDOWN),
                5
        );
    }

    @Override
    public List<Goal> createAbilityGoals(SemionMonsterEntity entity) {
        return List.of(
                new SingleAllyHealGoal<>(
                        entity,
                        SemionMonsterEntity.class,
                        SummonBalancePolicy.T1_SUPPORT_SINGLE_HEAL_RADIUS,
                        SummonBalancePolicy.T1_SUPPORT_SINGLE_HEAL_AMOUNT,
                        SummonBalancePolicy.T1_SUPPORT_SINGLE_HEAL_COOLDOWN_TICKS,
                        SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS
                )
        );
    }
}
