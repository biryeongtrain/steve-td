package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.goal.ApplyMonsterTimedEffectGoal;
import kim.biryeong.semiontd.entity.goal.ApplyTowerTimedEffectGoal;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public final class ApexWardenSummon extends SummonMonsterType {
    public ApexWardenSummon() {
        super(
                "apex_warden",
                SummonDisplayNames.APEX_WARDEN,
                430,
                19,
                900,
                22,
                30,
                AttackKind.MELEE,
                "minecraft:warden",
                null,
                MonsterDimensions.of(1.55, 2.6),
                DamageType.PHYSICAL,
                18,
                SummonTier.T5,
                List.of(SummonRole.TANK, SummonRole.DISRUPTOR),
                List.of(SummonAbilityActivation.PASSIVE, SummonAbilityActivation.COOLDOWN),
                48
        );
    }

    @Override
    public List<Goal> createAbilityGoals(SemionMonsterEntity entity) {
        return List.of(
                new ApplyTowerTimedEffectGoal(
                        entity,
                        TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION,
                        SummonBalancePolicy.APEX_WARDEN_ATTACK_SPEED_REDUCTION,
                        SummonBalancePolicy.APEX_WARDEN_TOWER_PRESSURE_RADIUS,
                        SummonBalancePolicy.APEX_WARDEN_TOWER_PRESSURE_DURATION_TICKS,
                        SummonBalancePolicy.APEX_WARDEN_TOWER_PRESSURE_COOLDOWN_TICKS,
                        SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS,
                        Integer.MAX_VALUE
                ),
                new ApplyMonsterTimedEffectGoal(
                        entity,
                        TimedEffectType.MONSTER_DAMAGE_REDUCTION,
                        SummonBalancePolicy.APEX_WARDEN_DAMAGE_REDUCTION,
                        SummonBalancePolicy.APEX_WARDEN_PROTECTION_RADIUS,
                        SummonBalancePolicy.APEX_WARDEN_PROTECTION_DURATION_TICKS,
                        SummonBalancePolicy.APEX_WARDEN_PROTECTION_COOLDOWN_TICKS,
                        SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS,
                        Integer.MAX_VALUE
                )
        );
    }
}
