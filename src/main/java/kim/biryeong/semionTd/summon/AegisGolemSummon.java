package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.goal.ApplyMonsterTimedEffectGoal;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public final class AegisGolemSummon extends SummonMonsterType {
    public AegisGolemSummon() {
        super(
                "aegis_golem",
                SummonDisplayNames.AEGIS_GOLEM,
                270,
                15,
                540,
                18,
                18,
                AttackKind.MELEE,
                "minecraft:iron_golem",
                null,
                MonsterDimensions.of(1.4, 2.2),
                DamageType.PHYSICAL,
                8,
                SummonTier.T4,
                List.of(SummonRole.TANK),
                List.of(SummonAbilityActivation.PASSIVE),
                31
        );
    }

    @Override
    public List<Goal> createAbilityGoals(SemionMonsterEntity entity) {
        return List.of(new ApplyMonsterTimedEffectGoal(
                entity,
                TimedEffectType.MONSTER_DAMAGE_REDUCTION,
                SummonBalancePolicy.AEGIS_GOLEM_DAMAGE_REDUCTION,
                SummonBalancePolicy.AEGIS_GOLEM_PROTECTION_RADIUS,
                SummonBalancePolicy.AEGIS_GOLEM_PROTECTION_DURATION_TICKS,
                SummonBalancePolicy.AEGIS_GOLEM_PROTECTION_COOLDOWN_TICKS,
                SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS,
                Integer.MAX_VALUE
        ));
    }
}
