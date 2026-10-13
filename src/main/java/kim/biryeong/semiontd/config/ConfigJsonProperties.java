package kim.biryeong.semiontd.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

final class ConfigJsonProperties {
    private final JsonObject root;

    private ConfigJsonProperties(JsonObject root) {
        this.root = root;
    }

    static ConfigJsonProperties parse(String json) {
        try {
            JsonElement value = JsonParser.parseString(json);
            return new ConfigJsonProperties(value instanceof JsonObject object ? object : null);
        } catch (JsonParseException exception) {
            return new ConfigJsonProperties(null);
        }
    }

    boolean has(String key) {
        return root != null && root.has(key) && !root.get(key).isJsonNull();
    }

    boolean hasNested(String parentKey, String childKey) {
        JsonObject parent = nestedObject(parentKey);
        return parent != null && parent.has(childKey) && !parent.get(childKey).isJsonNull();
    }

    boolean hasAllNested(String parentKey, String... childKeys) {
        JsonObject parent = nestedObject(parentKey);
        if (parent == null) {
            return false;
        }
        for (String childKey : childKeys) {
            if (!parent.has(childKey) || parent.get(childKey).isJsonNull()) {
                return false;
            }
        }
        return true;
    }

    private JsonObject nestedObject(String key) {
        return root != null && root.has(key) && root.get(key).isJsonObject()
                ? root.getAsJsonObject(key)
                : null;
    }
}
