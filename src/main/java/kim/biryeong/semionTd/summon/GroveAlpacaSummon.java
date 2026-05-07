package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.goal.AreaAllyHealGoal;
import kim.biryeong.semiontd.entity.goal.SingleAllyHealGoal;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public final class GroveAlpacaSummon extends SummonMonsterType {
    public GroveAlpacaSummon() {
        super(
                "grove_alpaca",
                SummonDisplayNames.GROVE_ALPACA,
                140,
                9,
                125,
                1,
                6,
                AttackKind.RANGED,
                "minecraft:llama",
                "semion-td:summon/t3_grove_alpaca",
                DamageType.MAGIC,
                7,
                SummonTier.T3,
                List.of(SummonRole.SUPPORT),
                List.of(SummonAbilityActivation.COOLDOWN),
                15
        );
    }

    @Override
    public List<Goal> createAbilityGoals(SemionMonsterEntity entity) {
        return List.of(
                new SingleAllyHealGoal<>(
                        entity,
                        SemionMonsterEntity.class,
                        SummonBalancePolicy.T3_SUPPORT_SINGLE_HEAL_RADIUS,
                        SummonBalancePolicy.T3_SUPPORT_SINGLE_HEAL_AMOUNT,
                        SummonBalancePolicy.T3_SUPPORT_SINGLE_HEAL_COOLDOWN_TICKS,
                        SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS
                ),
                new AreaAllyHealGoal<>(
                        entity,
                        SemionMonsterEntity.class,
                        SummonBalancePolicy.T3_SUPPORT_AREA_HEAL_RADIUS,
                        SummonBalancePolicy.T3_SUPPORT_AREA_HEAL_AMOUNT,
                        SummonBalancePolicy.T3_SUPPORT_AREA_HEAL_MAX_TARGETS,
                        SummonBalancePolicy.T3_SUPPORT_AREA_HEAL_COOLDOWN_TICKS,
                        SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS
                )
        );
    }
}
