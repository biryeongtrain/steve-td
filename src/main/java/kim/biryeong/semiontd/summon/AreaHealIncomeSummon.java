package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.entity.goal.AreaAllyHealGoal;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public class AreaHealIncomeSummon extends BasicIncomeSummon {
    protected AreaHealIncomeSummon(SummonConfig.SummonDefinition definition) {
        super(definition);
    }

    @Override
    public List<Goal> createAbilityGoals(SemionMonsterEntity entity) {
        return List.of(new AreaAllyHealGoal<>(
                entity,
                SemionMonsterEntity.class,
                abilityValue("radius", 6.0),
                abilityValue("healAmount", 8.0),
                abilityInt("maxTargets", 6),
                abilityInt("cooldownTicks", 120),
                abilityInt("retryDelayTicks", 20)
        ));
    }
}
