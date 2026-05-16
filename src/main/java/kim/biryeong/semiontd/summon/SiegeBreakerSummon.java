package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.goal.SiegeTrueDamageGoal;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public final class SiegeBreakerSummon extends SummonMonsterType {
    public SiegeBreakerSummon() {
        super(
                "siege_breaker",
                SummonDisplayNames.SIEGE_BREAKER,
                380,
                18,
                520,
                14,
                22,
                AttackKind.MELEE,
                "minecraft:ravager",
                "semion-td:summon/t5_siege",
                MonsterDimensions.of(2.0, 1.35),
                DamageType.PHYSICAL,
                4,
                SummonTier.T5,
                List.of(SummonRole.SIEGE),
                List.of(SummonAbilityActivation.CONDITIONAL),
                34
        );
    }

    @Override
    public List<Goal> createAbilityGoals(SemionMonsterEntity entity) {
        return List.of(new SiegeTrueDamageGoal(
                entity,
                SummonBalancePolicy.SIEGE_BREAKER_PROGRESS_THRESHOLD,
                SummonBalancePolicy.SIEGE_BREAKER_TRUE_DAMAGE,
                SummonBalancePolicy.SIEGE_BREAKER_TRUE_DAMAGE_COOLDOWN_TICKS,
                SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS
        ));
    }
}
