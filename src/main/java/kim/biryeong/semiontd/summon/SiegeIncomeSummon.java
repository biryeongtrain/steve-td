package kim.biryeong.semiontd.summon;

import java.util.List;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.entity.goal.SiegeTrueDamageGoal;
import kim.biryeong.semiontd.entity.goal.UtilitySupportGoal;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public class SiegeIncomeSummon extends BasicIncomeSummon {
    private final UtilitySupportProfile support;

    protected SiegeIncomeSummon(SummonConfig.SummonDefinition definition) {
        super(definition, List.of(SummonAbilityActivation.COOLDOWN));
        support = UtilitySupportProfile.from(definition);
    }

    @Override
    public List<Goal> createAbilityGoals(SemionMonsterEntity entity) {
        if (!support.physicalCanary()) {
            return List.of(new UtilitySupportGoal(entity, support));
        }
        return List.of(new SiegeTrueDamageGoal(
                entity,
                100.0,
                60,
                abilityInt("retryDelayTicks", 20),
                0.0,
                false
        ));
    }
}
