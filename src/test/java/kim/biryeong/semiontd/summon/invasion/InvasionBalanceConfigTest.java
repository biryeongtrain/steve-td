package kim.biryeong.semiontd.summon.invasion;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.summon.SummonDescriptionFactory;
import org.junit.jupiter.api.Test;

final class InvasionBalanceConfigTest {
    @Test
    void packagedDefaultsUseFiveTargetsAndTwoPercentHealingWithoutPierceFalloff() {
        var defaults = SummonConfig.defaultConfig();
        var priest = defaults.summons().get("dark_priest");
        assertEquals(5.0, priest.abilityValues().get("maxTargets"));
        assertEquals(16.0, priest.abilityValues().get("healAmount"));
        assertEquals(0.02, priest.abilityValues().get("healMaxHealthRatio"));
        assertEquals(5.0, defaults.summons().get("ogre_champion").abilityValues().get("maxTargets"));
        assertFalse(defaults.summons().get("dwarf_gunner").abilityValues().containsKey("pierceFalloff"));
        String text = String.join(" ", SummonDescriptionFactory.describe(priest));
        assertTrue(text.contains("16 × 성장 배율") && text.contains("2%"), text);
    }

    @Test
    void legacyConfigurationBackfillsMissingValuesAndPreservesOperatorOverrides() {
        var defaults = SummonConfig.defaultConfig();
        var legacy = defaults.summons().get("dark_priest").withAbilityValues(Map.of("healAmount", 25.0));
        var merged = new SummonConfig(Map.of("dark_priest", legacy)).withMissingDefaults(defaults);
        var values = merged.summons().get("dark_priest").abilityValues();
        assertEquals(25.0, values.get("healAmount"));
        assertEquals(0.02, values.get("healMaxHealthRatio"));
        assertEquals(5.0, values.get("maxTargets"));
        assertEquals(merged, merged.withMissingDefaults(defaults));
        var custom = legacy.withAbilityValues(Map.of("maxTargets", 3.0, "healMaxHealthRatio", 0.07));
        var preserved = new SummonConfig(Map.of("dark_priest", custom)).withMissingDefaults(defaults).summons().get("dark_priest");
        assertEquals(0.07, preserved.abilityValues().get("healMaxHealthRatio"));
        assertEquals(3.0, preserved.abilityValues().get("maxTargets"));
        String text = String.join(" ", SummonDescriptionFactory.describe(preserved));
        assertTrue(text.contains("최대 3기") && text.contains("7%"), text);
    }
}
