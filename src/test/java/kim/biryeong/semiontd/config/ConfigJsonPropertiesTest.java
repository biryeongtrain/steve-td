package kim.biryeong.semiontd.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class ConfigJsonPropertiesTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "42", "false", "\"text\"", "{broken"})
    void absentObjectTreatsAllMigrationPropertiesAsMissing(String json) {
        ConfigJsonProperties properties = ConfigJsonProperties.parse(json);

        assertFalse(properties.has("enabled"));
        assertFalse(properties.hasNested("teamTransfer", "enabled"));
        assertFalse(properties.hasAllNested("teamTransfer", "enabled", "maxDiamondPerRound"));
        assertFalse(properties.hasAllNested("teamTransfer"));
    }

    @Test
    void explicitFalseZeroAndEmptyValuesArePresentButNullAndAbsentAreMissing() {
        ConfigJsonProperties properties = ConfigJsonProperties.parse("""
                {"enabled": false, "limit": 0, "message": "", "messages": [], "section": {}, "missing": null}
                """);

        for (String key : new String[] {"enabled", "limit", "message", "messages", "section"}) {
            assertTrue(properties.has(key), key);
        }
        assertFalse(properties.has("missing"));
        assertFalse(properties.has("absent"));
    }

    @Test
    void nestedMigrationChecksRequireAnObjectAndEveryNonNullChild() {
        ConfigJsonProperties properties = ConfigJsonProperties.parse("""
                {"teamTransfer": {"enabled": false, "maxDiamondPerRound": 0, "missing": null},
                 "empty": {}, "array": [], "number": 0, "null": null}
                """);

        assertTrue(properties.hasNested("teamTransfer", "enabled"));
        assertTrue(properties.hasAllNested("teamTransfer", "enabled", "maxDiamondPerRound"));
        assertTrue(properties.hasAllNested("empty"));
        assertFalse(properties.hasNested("teamTransfer", "missing"));
        assertFalse(properties.hasAllNested("teamTransfer", "enabled", "absent"));
        assertFalse(properties.hasAllNested("teamTransfer", "enabled", "missing"));
        for (String key : new String[] {"absent", "array", "number", "null"}) {
            assertFalse(properties.hasNested(key, "enabled"), key);
            assertFalse(properties.hasAllNested(key), key);
        }
    }

    @Test
    void eachReloadInspectsItsOwnContentsWithoutReusingAnEarlierSnapshot() {
        ConfigJsonProperties first = ConfigJsonProperties.parse("{\"enabled\": false}");
        ConfigJsonProperties removed = ConfigJsonProperties.parse("{}");
        ConfigJsonProperties restored = ConfigJsonProperties.parse("{\"enabled\": true}");

        assertTrue(first.has("enabled"));
        assertFalse(removed.has("enabled"));
        assertTrue(restored.has("enabled"));
        assertTrue(first.has("enabled"));
    }
}
