package kim.biryeong.semiontd.summon;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import kim.biryeong.semiontd.config.SummonConfig;
import org.junit.jupiter.api.Test;

final class UtilitySupportProfileTest {
    @Test
    void profilesUseApprovedH0ValuesAndDescriptionsExcludeLegacyTrueDamage() {
        SummonConfig defaults = SummonConfig.defaultConfig();
        assertEquals(30, UtilitySupportProfile.from(defaults.summons().get("guardian")).physicalShield());
        assertEquals(40, UtilitySupportProfile.from(defaults.summons().get("blaze")).magicShield());
        assertEquals(45, UtilitySupportProfile.from(defaults.summons().get("ghast")).healing());
        UtilitySupportProfile wither = UtilitySupportProfile.from(defaults.summons().get("wither_skeleton"));
        assertEquals(35, wither.healing());
        assertEquals(25, wither.physicalShield());
        assertEquals(25, wither.magicShield());
        assertEquals(100, wither.shieldDurationTicks(), "H0 shield duration is five seconds.");
        UtilitySupportProfile warden = UtilitySupportProfile.from(defaults.summons().get("warden"));
        assertEquals(70, warden.physicalShield());
        assertEquals(70, warden.magicShield());
        assertEquals(120, warden.cooldownTicks());
        assertFalse(warden.physicalCanary());
        for (String id : new String[] { "guardian", "blaze", "ghast", "wither_skeleton", "warden" }) {
            assertTrue(SummonDescriptionFactory.describe(defaults.summons().get(id)).stream()
                    .noneMatch(line -> line.contains("고정 피해")), id);
        }
    }

    @Test
    void missingSupportKeysBackfillWithoutReusingLegacyCooldownOrOverwritingConfiguredValues() {
        SummonConfig defaults = SummonConfig.defaultConfig();
        var old = defaults.summons().get("warden").withAbilityValues(Map.of("cooldownTicks", 60.0, "physicalShield", 91.0));
        SummonConfig merged = new SummonConfig(Map.of("warden", old)).withMissingDefaults(defaults);
        UtilitySupportProfile profile = UtilitySupportProfile.from(merged.summons().get("warden"));
        assertEquals(91, profile.physicalShield());
        assertEquals(120, profile.cooldownTicks());
        assertEquals(60.0, merged.summons().get("warden").abilityValues().get("cooldownTicks"), 0.0001);
    }

    @Test
    void physicalCanaryIsExplicitAndInvalidSupportConfigurationFailsBeforeRegistration() {
        var warden = SummonConfig.defaultConfig().summons().get("warden");
        var values = new LinkedHashMap<>(warden.abilityValues());
        values.put("physicalCanary", 1.0);
        assertTrue(UtilitySupportProfile.from(warden.withAbilityValues(values)).physicalCanary());
        assertTrue(SummonDescriptionFactory.describe(warden.withAbilityValues(values)).getFirst().contains("물리 피해 100"));
        for (Map<String, Double> invalid : java.util.List.of(Map.of("supportRadius", Double.NaN),
                Map.of("supportMaxTargets", 2.5), Map.of("shieldDurationTicks", 0.0),
                Map.of("physicalShield", -1.0), Map.of("physicalCanary", 2.0))) {
            assertThrows(IllegalArgumentException.class, () -> warden.withAbilityValues(invalid));
        }
    }
}
