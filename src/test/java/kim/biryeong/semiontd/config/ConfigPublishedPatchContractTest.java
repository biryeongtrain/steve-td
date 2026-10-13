package kim.biryeong.semiontd.config;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import kim.biryeong.semiontd.augment.AugmentCatalog;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

final class ConfigPublishedPatchContractTest {
    private static final Gson JSON = new Gson();
    private static SemionConfigLoader.LoadedConfigs loaded;
    private static Map<String, JsonElement> runtime;
    @TempDir
    static Path directory;

    @BeforeAll
    static void loadDefaults() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        loaded = SemionConfigLoader.load(directory, LoggerFactory.getLogger("patch-contract"));
        runtime = Map.of("tower", JSON.toJsonTree(loaded.towerBalance()),
                "augment", JSON.toJsonTree(loaded.augments()), "wave", JSON.toJsonTree(loaded.waves()));
    }

    static Stream<Arguments> publishedValues() throws Exception {
        try (var input = ConfigPublishedPatchContractTest.class.getResourceAsStream("/balance/applied-patch-values.json")) {
            assertNotNull(input);
            var rows = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("values");
            return rows.asList().stream().map(JsonElement::getAsJsonObject)
                    .map(row -> Arguments.of(row.get("path").getAsString(), row));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("publishedValues")
    void appliedPatchSurvivesBundlingAndRuntimeLoading(String path, JsonObject row) throws Exception {
        String[] split = path.split(":/", 2);
        String file = switch (split[0]) {
            case "tower" -> "tower_balance.json";
            case "augment" -> "augment_balance.json";
            case "wave" -> "wave.json";
            default -> throw new AssertionError(path);
        };
        double expected = row.get("value").getAsDouble();
        String evidence = row.get("source").getAsString() + " " + path;
        JsonElement written = JsonParser.parseString(Files.readString(directory.resolve(file)));
        assertEquals(expected, at(written, split[1]).getAsDouble(), 0.0000001, "seeded " + evidence);
        assertEquals(expected, at(runtime.get(split[0]), split[1]).getAsDouble(), 0.0000001, "runtime " + evidence);
    }

    @Test
    void retiredDeliveryCardRemainsRemoved() {
        assertTrue(AugmentCatalog.find("semiontd:decisive_delivery").isEmpty());
        assertFalse(loaded.augments().parameters().containsKey("semiontd:decisive_delivery"));
    }

    @Test
    void existingOperatorOverrideIsPreserved() throws Exception {
        Path overrideDirectory = Files.createDirectory(directory.resolve("operator-override"));
        var config = JSON.toJsonTree(loaded.towerBalance()).getAsJsonObject();
        config.getAsJsonObject("towers").getAsJsonObject("pirate_deckhand").addProperty("damage", 123.0);
        Files.writeString(overrideDirectory.resolve("tower_balance.json"), JSON.toJson(config));
        var reloaded = SemionConfigLoader.load(overrideDirectory, LoggerFactory.getLogger("patch-contract"));
        assertEquals(123.0, reloaded.towerBalance().towers().get("pirate_deckhand").damage());
    }

    private static JsonElement at(JsonElement root, String path) {
        JsonElement value = root;
        for (String key : path.split("/")) {
            assertNotNull(value, path);
            value = value.isJsonArray() ? value.getAsJsonArray().get(Integer.parseInt(key)) : value.getAsJsonObject().get(key);
        }
        assertNotNull(value, path);
        return value;
    }
}
