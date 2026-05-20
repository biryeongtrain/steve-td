package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.entity.goal.SiegeTrueDamageGoal;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public class SiegeIncomeSummon extends BasicIncomeSummon {
    protected SiegeIncomeSummon(SummonConfig.SummonDefinition definition) {
        super(definition);
    }

    @Override
    public List<Goal> createAbilityGoals(SemionMonsterEntity entity) {
        return List.of(new SiegeTrueDamageGoal(
                entity,
                abilityValue("progressThreshold", 0.70),
                abilityValue("bonusDamage", 20.0),
                abilityInt("cooldownTicks", 80),
                abilityInt("retryDelayTicks", 20)
        ));
    }
}
