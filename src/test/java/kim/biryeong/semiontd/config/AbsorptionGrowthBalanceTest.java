package kim.biryeong.semiontd.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class AbsorptionGrowthBalanceTest {
    private static final Map<String, Map<String, Double>> EXPECTED = Map.of(
            "base_warlock_tower", Map.of("permanentHealth", .02, "permanentDamage", .02),
            "ranged_warlock_tower", Map.of("permanentHealth", .04, "permanentDamage", .07, "damageThreshold", 160.0),
            "melee_warlock_tower", Map.of("permanentHealth", .07, "permanentDamage", .04),
            "end_global", Map.of("permanentHealthRatio", .06, "permanentDamageRatio", .06, "damageThreshold", 175.0));

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void packagedAndFallbackDefaultsAgreeOnGrowthAndPreserveOtherMechanics() {
        for (TowerBalanceConfig config : new TowerBalanceConfig[]{TowerBalanceConfig.defaultConfig(), TowerBalanceConfig.codeDefaults()}) {
            assertValues(config, EXPECTED);
            assertEquals(.50, config.ability("ranged_warlock_tower", "roundStat", -1));
            assertEquals(.60, config.ability("melee_warlock_tower", "roundStat", -1));
            assertEquals(200, config.ability("melee_warlock_tower", "damageThreshold", -1));
            assertEquals(40, config.ability("ranged_warlock_tower", "damageScale", -1));
            assertEquals(20, config.ability("melee_warlock_tower", "damageScale", -1));
            assertEquals(.50, config.ability("end_global", "roundHealthRatio", -1));
            assertEquals(.66, config.ability("end_global", "roundDamageRatio", -1));
            assertEquals(50, config.ability("end_global", "damageScale", -1));
            assertEquals(.05, config.ability("end_global", "transferHealRatio", -1));
            assertEquals(.07, config.ability("ranged_warlock_tower", "lifeCap", -1));
            assertEquals(.12, config.ability("melee_warlock_tower", "lifeCap", -1));
            assertEquals(5, config.towers().get("base_warlock_tower").damage());
            assertEquals(8, config.towers().get("ranged_warlock_tower").damage());
            assertEquals(7, config.towers().get("melee_warlock_tower").damage());
        }
    }

    @Test
    void loaderBackfillsMissingGrowthButPreservesSavedOldAndCustomValues(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("tower_balance.json");
        var logger = LoggerFactory.getLogger("absorption-growth-test");
        assertValues(SemionConfigLoader.load(directory, logger).towerBalance(), EXPECTED);
        Map<String, Map<String, Double>> previous = Map.of(
                "base_warlock_tower", Map.of("permanentHealth", .025, "permanentDamage", .05),
                "ranged_warlock_tower", Map.of("permanentHealth", .025, "permanentDamage", .05, "damageThreshold", 140.0),
                "melee_warlock_tower", Map.of("permanentHealth", .05, "permanentDamage", .025),
                "end_global", Map.of("permanentHealthRatio", .04, "permanentDamageRatio", .04, "damageThreshold", 150.0));
        for (double multiplier : new double[]{1.0, 1.1}) {
            JsonObject saved = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            previous.forEach((id, values) -> values.forEach((key, value) -> saved.getAsJsonObject("abilities")
                    .getAsJsonObject(id).addProperty(key, value * multiplier)));
            Files.writeString(path, saved.toString());
            for (int reload = 0; reload < 2; reload++) {
                var loaded = SemionConfigLoader.load(directory, logger).towerBalance();
                previous.forEach((id, values) -> values.forEach((key, value) ->
                        assertEquals(value * multiplier, loaded.ability(id, key, -1), 1.0E-9, id + "." + key)));
            }
        }
        JsonObject partial = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        EXPECTED.forEach((id, values) -> values.keySet().forEach(key -> partial.getAsJsonObject("abilities").getAsJsonObject(id).remove(key)));
        Files.writeString(path, partial.toString());
        assertValues(SemionConfigLoader.load(directory, logger).towerBalance(), EXPECTED);
    }

    private static void assertValues(TowerBalanceConfig config, Map<String, Map<String, Double>> expected) {
        expected.forEach((id, values) -> values.forEach((key, value) -> assertEquals(value,
                config.ability(id, key, -1), 1.0E-9, id + "." + key)));
    }
}
