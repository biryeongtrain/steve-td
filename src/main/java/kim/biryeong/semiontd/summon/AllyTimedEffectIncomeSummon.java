package kim.biryeong.semiontd.summon;

import java.util.ArrayList;
import java.util.List;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.goal.ApplyMonsterTimedEffectGoal;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public class AllyTimedEffectIncomeSummon extends BasicIncomeSummon {
    private final List<TimedEffectType> effectTypes;

    protected AllyTimedEffectIncomeSummon(SummonConfig.SummonDefinition definition, TimedEffectType effectType) {
        this(definition, List.of(effectType));
    }

    protected AllyTimedEffectIncomeSummon(SummonConfig.SummonDefinition definition, List<TimedEffectType> effectTypes) {
        super(definition);
        this.effectTypes = List.copyOf(effectTypes);
    }

    @Override
    public List<Goal> createAbilityGoals(SemionMonsterEntity entity) {
        ArrayList<Goal> goals = new ArrayList<>();
        for (TimedEffectType effectType : effectTypes) {
            goals.add(new ApplyMonsterTimedEffectGoal(
                    entity,
                    effectType,
                    magnitude(effectType),
                    abilityValue("radius", 6.0),
                    abilityInt("durationTicks", 80),
                    abilityInt("cooldownTicks", 60),
                    abilityInt("retryDelayTicks", 20),
                    abilityInt("maxTargets", 8)
            ));
        }
        return goals;
    }

    private double magnitude(TimedEffectType type) {
        return switch (type) {
            case MONSTER_ATTACK_DAMAGE_BONUS -> abilityValue("attackMagnitude", abilityValue("magnitude", 0.15));
            case MONSTER_ATTACK_SPEED_BONUS -> abilityValue("attackSpeedMagnitude", abilityValue("magnitude", 0.15));
            case MONSTER_DAMAGE_REDUCTION -> abilityValue("damageReductionMagnitude", abilityValue("magnitude", 0.15));
            case MONSTER_MOVE_SPEED_BONUS -> abilityValue("moveMagnitude", abilityValue("magnitude", 0.15));
            default -> abilityValue("magnitude", 0.15);
        };
    }
}
