package kim.biryeong.semiontd.tower.end;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import org.junit.jupiter.api.Test;

class EndConfigTest extends EndTestFixture {
    @Test
    void damageRemainsLinearThrough175WithTheSameLogarithmicScale() {
        var scaling = EndConfig.RUNTIME.damageScaling();
        assertEquals(150, scaling.apply(150), .0001);
        assertEquals(175, scaling.apply(175), .0001);
        assertEquals(175 + 50 * Math.log1p(1), scaling.apply(225), .0001);
        assertEquals(175 + 50 * Math.log1p(8.5), scaling.apply(600), .0001);
    }

    @Test
    void zeroEndBalanceValueCanBecomeRuntimeState() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> end = new LinkedHashMap<>(abilities.get(EndTower.CONFIG_ID));
        end.put("transferTicks", 0.0);
        abilities.put(EndTower.CONFIG_ID, end);
        TowerBalanceConfig config = new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities);

        assertDoesNotThrow(() -> TowerBalanceRuntime.apply(config));
        assertEquals(1, EndConfig.RUNTIME.transfer().durationTicks());
    }

    @Test
    void endValuesDoNotRequireRatioIntegerOrCrossFieldOrdering() {
        assertDoesNotThrow(() -> TowerBalanceRuntime.apply(endConfig(Map.of("splashDamageRatio", 1.01))));
        assertDoesNotThrow(() -> TowerBalanceRuntime.apply(endConfig(Map.of("transferTicks", 1.5))));
        assertDoesNotThrow(() -> TowerBalanceRuntime.apply(endConfig(Map.of(
                "phantomScaleBase", 2.0,
                "phantomScaleCap", 1.0
        ))));
        assertDoesNotThrow(() -> TowerBalanceRuntime.apply(endConfig(Map.of("attackSpeedMinimumTicks", 16.0))));
        assertDoesNotThrow(() -> TowerBalanceRuntime.apply(endConfig(Map.of("damageScale", 0.0))));
        assertThrows(
                IllegalArgumentException.class,
                () -> TowerBalanceRuntime.apply(endConfig(Map.of("damageScale", -0.01)))
        );
    }

    @Test
    void legacyEndConfigReceivesScalingDefaultsWithoutOverwritingExistingValues() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> end = new LinkedHashMap<>(abilities.get(EndTower.CONFIG_ID));
        end.remove("healthThreshold");
        end.remove("healthScale");
        end.remove("damageThreshold");
        end.remove("damageScale");
        end.put("roundDamageRatio", 0.5);
        abilities.put(EndTower.CONFIG_ID, end);

        TowerBalanceConfig merged = new TowerBalanceConfig(
                defaults.towers(),
                defaults.upgradeCosts(),
                abilities
        ).withMissingDefaults(defaults);

        assertEquals(3000.0, merged.ability(EndTower.CONFIG_ID, "healthThreshold", -1.0), 0.0001);
        assertEquals(500.0, merged.ability(EndTower.CONFIG_ID, "healthScale", -1.0), 0.0001);
        assertEquals(175.0, merged.ability(EndTower.CONFIG_ID, "damageThreshold", -1.0), 0.0001);
        assertEquals(50.0, merged.ability(EndTower.CONFIG_ID, "damageScale", -1.0), 0.0001);
        assertEquals(0.5, merged.ability(EndTower.CONFIG_ID, "roundDamageRatio", -1.0), 0.0001);

        TowerBalanceRuntime.apply(merged);
        assertEquals(3000.0, EndConfig.RUNTIME.healthScaling().threshold(), 0.0001);
        assertEquals(500.0, EndConfig.RUNTIME.healthScaling().scale(), 0.0001);
        assertEquals(175.0, EndConfig.RUNTIME.damageScaling().threshold(), 0.0001);
        assertEquals(50.0, EndConfig.RUNTIME.damageScaling().scale(), 0.0001);
        assertEquals(0.5, EndConfig.RUNTIME.transfer().roundDamageRatio(), 0.0001);
    }

    @Test
    void nonFiniteTowerStatsAreRejectedAndLargeAbilityValuesAreAccepted() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, TowerBalanceConfig.TowerStats> towers = new LinkedHashMap<>(defaults.towers());
        TowerBalanceConfig.TowerStats base = towers.get(EndTowers.BASE_END_TOWER.id());
        towers.put(
                EndTowers.BASE_END_TOWER.id(),
                new TowerBalanceConfig.TowerStats(
                        base.mineralCost(),
                        Double.NaN,
                        base.range(),
                        base.damage(),
                        base.attackIntervalTicks(),
                        base.aggroPriority()
                )
        );
        TowerBalanceConfig invalidStats = new TowerBalanceConfig(
                towers,
                defaults.upgradeCosts(),
                defaults.abilities()
        );
        assertThrows(IllegalArgumentException.class, () -> TowerBalanceRuntime.apply(invalidStats));

        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> end = new LinkedHashMap<>(abilities.get(EndTower.CONFIG_ID));
        end.put("transferTicks", (double) Integer.MAX_VALUE + 1.0);
        abilities.put(EndTower.CONFIG_ID, end);
        TowerBalanceConfig oversizedInteger = new TowerBalanceConfig(
                defaults.towers(),
                defaults.upgradeCosts(),
                abilities
        );
        assertDoesNotThrow(() -> TowerBalanceRuntime.apply(oversizedInteger));
    }
}
