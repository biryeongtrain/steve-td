package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.goal.ApplyTowerTimedEffectGoal;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public final class ElderGuardianSummon extends BasicIncomeSummon {
    public ElderGuardianSummon(SummonConfig.SummonDefinition definition) {
        super(definition);
    }

    @Override
    public List<Goal> createAbilityGoals(SemionMonsterEntity entity) {
        return List.of(
                new ApplyTowerTimedEffectGoal(
                        entity,
                        TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION,
                        abilityValue("attackSpeedMagnitude", 0.30),
                        abilityValue("radius", 8.0),
                        abilityInt("durationTicks", 100),
                        abilityInt("cooldownTicks", 80),
                        abilityInt("retryDelayTicks", 20),
                        abilityInt("maxTargets", 3)
                ),
                new ApplyTowerTimedEffectGoal(
                        entity,
                        TimedEffectType.TOWER_RANGE_REDUCTION,
                        abilityValue("rangeMagnitude", 0.20),
                        abilityValue("radius", 8.0),
                        abilityInt("durationTicks", 100),
                        abilityInt("cooldownTicks", 80),
                        abilityInt("retryDelayTicks", 20),
                        abilityInt("maxTargets", 3)
                )
        );
    }
}
