package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.goal.SiegeTrueDamageGoal;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public final class BombardToadSummon extends SummonMonsterType {
    public BombardToadSummon() {
        super(
                "bombard_toad",
                SummonDisplayNames.BOMBARD_TOAD,
                310,
                16,
                380,
                8,
                21,
                AttackKind.RANGED,
                "minecraft:frog",
                null,
                MonsterDimensions.of(1.25, 1.0),
                DamageType.PHYSICAL,
                6,
                SummonTier.T4,
                List.of(SummonRole.SIEGE),
                List.of(SummonAbilityActivation.CONDITIONAL),
                35
        );
    }

    @Override
    public List<Goal> createAbilityGoals(SemionMonsterEntity entity) {
        return List.of(new SiegeTrueDamageGoal(
                entity,
                SummonBalancePolicy.BOMBARD_TOAD_PROGRESS_THRESHOLD,
                SummonBalancePolicy.BOMBARD_TOAD_TRUE_DAMAGE,
                SummonBalancePolicy.BOMBARD_TOAD_TRUE_DAMAGE_COOLDOWN_TICKS,
                SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS
        ));
    }
}
