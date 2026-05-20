package kim.biryeong.semiontd.summon;

import java.util.List;
import java.util.Map;
import kim.biryeong.semiontd.config.SummonConfig;

public class BasicIncomeSummon extends SummonMonsterType {
    private final Map<String, Double> abilityValues;

    public BasicIncomeSummon(SummonConfig.SummonDefinition definition) {
        this(definition, definition.abilityActivations());
    }

    protected BasicIncomeSummon(SummonConfig.SummonDefinition definition, List<SummonAbilityActivation> abilityActivations) {
        super(
                definition.id(),
                definition.displayName(),
                definition.emeraldCost(),
                definition.incomeGain(),
                definition.maxHealth(),
                definition.armor(),
                definition.attackDamage(),
                definition.attackKind(),
                definition.entityTypeId(),
                definition.blockbenchModelId(),
                definition.monsterDimensions(),
                definition.damageType(),
                definition.resistance(),
                definition.tier(),
                definition.roles(),
                abilityActivations,
                SummonDescriptionFactory.describe(definition),
                definition.diamondReward()
        );
        this.abilityValues = definition.abilityValues();
    }

    protected final double abilityValue(String key, double defaultValue) {
        return abilityValues.getOrDefault(key, defaultValue);
    }

    protected final int abilityInt(String key, int defaultValue) {
        return (int) Math.round(abilityValue(key, defaultValue));
    }
}
