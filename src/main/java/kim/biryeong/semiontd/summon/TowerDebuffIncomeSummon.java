package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.goal.ApplyTowerTimedEffectGoal;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public class TowerDebuffIncomeSummon extends BasicIncomeSummon {
    private final TimedEffectType effectType;

    protected TowerDebuffIncomeSummon(SummonConfig.SummonDefinition definition, TimedEffectType effectType) {
        super(definition);
        this.effectType = effectType;
    }

    @Override
    public List<Goal> createAbilityGoals(SemionMonsterEntity entity) {
        return List.of(new ApplyTowerTimedEffectGoal(
                entity,
                effectType,
                abilityValue("magnitude", 0.1),
                abilityValue("radius", 6.0),
                abilityInt("durationTicks", 80),
                abilityInt("cooldownTicks", 80),
                abilityInt("retryDelayTicks", 20),
                abilityInt("maxTargets", 1)
        ));
    }
}
