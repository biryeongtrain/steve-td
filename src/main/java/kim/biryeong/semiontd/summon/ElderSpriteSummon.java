package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.goal.ApplyMonsterTimedEffectGoal;
import kim.biryeong.semiontd.entity.goal.AreaAllyHealGoal;
import kim.biryeong.semiontd.entity.goal.SingleAllyHealGoal;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public final class ElderSpriteSummon extends SummonMonsterType {
    public ElderSpriteSummon() {
        super(
                "elder_sprite",
                SummonDisplayNames.ELDER_SPRITE,
                250,
                14,
                185,
                1,
                8,
                AttackKind.RANGED,
                "minecraft:allay",
                null,
                DamageType.MAGIC,
                12,
                SummonTier.T4,
                List.of(SummonRole.SUPPORT),
                List.of(SummonAbilityActivation.COOLDOWN),
                28
        );
    }

    @Override
    public List<Goal> createAbilityGoals(SemionMonsterEntity entity) {
        return List.of(
                new SingleAllyHealGoal<>(
                        entity,
                        SemionMonsterEntity.class,
                        SummonBalancePolicy.T4_SUPPORT_SINGLE_HEAL_RADIUS,
                        SummonBalancePolicy.T4_SUPPORT_SINGLE_HEAL_AMOUNT,
                        SummonBalancePolicy.T4_SUPPORT_SINGLE_HEAL_COOLDOWN_TICKS,
                        SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS
                ),
                new AreaAllyHealGoal<>(
                        entity,
                        SemionMonsterEntity.class,
                        SummonBalancePolicy.T4_SUPPORT_AREA_HEAL_RADIUS,
                        SummonBalancePolicy.T4_SUPPORT_AREA_HEAL_AMOUNT,
                        SummonBalancePolicy.T4_SUPPORT_AREA_HEAL_MAX_TARGETS,
                        SummonBalancePolicy.T4_SUPPORT_AREA_HEAL_COOLDOWN_TICKS,
                        SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS
                ),
                new ApplyMonsterTimedEffectGoal(
                        entity,
                        TimedEffectType.MONSTER_DAMAGE_REDUCTION,
                        SummonBalancePolicy.ELDER_SPRITE_DAMAGE_REDUCTION,
                        SummonBalancePolicy.ELDER_SPRITE_PROTECTION_RADIUS,
                        SummonBalancePolicy.ELDER_SPRITE_PROTECTION_DURATION_TICKS,
                        SummonBalancePolicy.ELDER_SPRITE_PROTECTION_COOLDOWN_TICKS,
                        SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS,
                        Integer.MAX_VALUE
                )
        );
    }
}
