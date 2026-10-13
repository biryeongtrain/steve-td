package kim.biryeong.semiontd.entity.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tomalbrc.bil.core.model.Model;
import de.tomalbrc.bil.file.loader.AjModelLoader;
import de.tomalbrc.bil.file.loader.BbModelLoader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;

public final class SemionBilModelCache {
    /** 이름이 이것으로 끝나는 큐브는 스스로 빛납니다(눈빛·룬처럼 어두운 데서도 밝게 보이는 부분). */
    public static final String GLOW_SUFFIX = "_glow";
    /** 빛나는 큐브에 주는 밝기(0~15). 모델 요소의 light_emission으로 들어갑니다. */
    public static final int GLOW_LIGHT = 15;

    private static final Map<String, Optional<Model>> MODELS = new ConcurrentHashMap<>();

    private SemionBilModelCache() {
    }

    public static Optional<Model> load(String modelId) {
        String normalizedId = normalize(modelId);
        if (normalizedId == null) {
            return Optional.empty();
        }
        return MODELS.computeIfAbsent(normalizedId, SemionBilModelCache::loadUncached);
    }

    private static Optional<Model> loadUncached(String modelId) {
        Identifier id = Identifier.tryParse(modelId);
        if (id == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(loadBbModel(id));
        } catch (RuntimeException ignored) {
            try {
                return Optional.of(AjModelLoader.load(id));
            } catch (RuntimeException ignoredAgain) {
                return Optional.empty();
            }
        }
    }

    /**
     * BIL의 BbModelLoader.load(id)와 같은 경로에서 .bbmodel을 읽되, {@link #GLOW_SUFFIX}로 끝나는 큐브에 light_emission을
     * 넣은 뒤 넘깁니다. 블록벤치 자유 모델 형식은 큐브 발광 값을 저장하지 않으므로 이름으로 표시합니다.
     */
    private static Model loadBbModel(Identifier id) {
        String path = String.format("/model/%s/%s.bbmodel", id.getNamespace(), id.getPath());
        try (InputStream stream = BbModelLoader.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalArgumentException("Model doesn't exist: " + path);
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            wrapMultiAxisRotations(json);
            applyGlow(json);
            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
            return new BbModelLoader().load(new ByteArrayInputStream(bytes), id.getPath());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read model " + path, exception);
        }
    }

    /** 이름이 {@link #GLOW_SUFFIX}로 끝나고 발광 값이 없는 요소에 {@link #GLOW_LIGHT}를 줍니다. 바꾼 요소 수를 돌려줍니다. */
    public static int applyGlow(JsonObject model) {
        if (!model.has("elements") || !model.get("elements").isJsonArray()) {
            return 0;
        }
        int changed = 0;
        for (JsonElement element : model.getAsJsonArray("elements")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            JsonElement name = object.get("name");
            if (name == null || !name.isJsonPrimitive() || !name.getAsString().endsWith(GLOW_SUFFIX)) {
                continue;
            }
            if (object.has("light_emission") && object.get("light_emission").getAsInt() > 0) {
                continue;
            }
            object.addProperty("light_emission", GLOW_LIGHT);
            changed++;
        }
        return changed;
    }

    /**
     * 두 축 이상으로 돌린 큐브를 같은 회전의 그룹으로 감쌉니다. 바꾼 큐브 수를 돌려줍니다.
     *
     * <p>BIL은 큐브 회전을 아이템 모델 요소로 옮기면서 0이 아닌 첫 축 하나만 남깁니다. 그래서 블록벤치에서 여러 축으로
     * 기울인 판이 게임에서는 엉뚱한 방향의 막대로 보입니다. 그룹(뼈)은 회전 전체를 쿼터니언으로 옮기므로, 큐브의 회전과
     * 회전축을 새 그룹({@code <이름>_rot})에 넘기고 큐브는 돌리지 않습니다. 모델 파일은 그대로 두고 불러올 때만 고칩니다.
     */
    public static int wrapMultiAxisRotations(JsonObject model) {
        if (!model.has("elements") || !model.has("outliner") || !model.get("outliner").isJsonArray()) {
            return 0;
        }
        if (!model.has("groups") || !model.get("groups").isJsonArray()) {
            model.add("groups", new JsonArray());
        }
        JsonArray groups = model.getAsJsonArray("groups");
        int changed = 0;
        for (JsonElement entry : model.getAsJsonArray("elements")) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject element = entry.getAsJsonObject();
            if (!element.has("rotation") || !element.has("uuid") || rotatedAxes(element.getAsJsonArray("rotation")) < 2) {
                continue;
            }
            String elementUuid = element.get("uuid").getAsString();
            String groupUuid = UUID.nameUUIDFromBytes((elementUuid + "/rot").getBytes(StandardCharsets.UTF_8)).toString();
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("uuid", groupUuid);
            wrapper.addProperty("isOpen", false);
            JsonArray children = new JsonArray();
            children.add(elementUuid);
            wrapper.add("children", children);
            if (!replaceChild(model.getAsJsonArray("outliner"), elementUuid, wrapper)) {
                continue;
            }
            JsonObject group = new JsonObject();
            group.addProperty("name", (element.has("name") ? element.get("name").getAsString() : "cube") + "_rot");
            group.addProperty("uuid", groupUuid);
            group.addProperty("export", true);
            group.add("origin", element.has("origin") ? element.get("origin").deepCopy() : zeroVector());
            group.add("rotation", element.get("rotation").deepCopy());
            group.addProperty("mirror_uv", false);
            group.addProperty("autouv", 0);
            group.addProperty("visibility", true);
            groups.add(group);
            element.add("rotation", zeroVector());
            changed++;
        }
        return changed;
    }

    private static int rotatedAxes(JsonArray rotation) {
        int axes = 0;
        for (JsonElement value : rotation) {
            if (Math.abs(value.getAsDouble()) > 1.0e-4) {
                axes++;
            }
        }
        return axes;
    }

    private static boolean replaceChild(JsonArray children, String uuid, JsonObject replacement) {
        for (int index = 0; index < children.size(); index++) {
            JsonElement child = children.get(index);
            if (child.isJsonPrimitive() && uuid.equals(child.getAsString())) {
                children.set(index, replacement);
                return true;
            }
            if (child.isJsonObject() && child.getAsJsonObject().has("children")
                    && replaceChild(child.getAsJsonObject().getAsJsonArray("children"), uuid, replacement)) {
                return true;
            }
        }
        return false;
    }

    private static JsonArray zeroVector() {
        JsonArray vector = new JsonArray();
        vector.add(0);
        vector.add(0);
        vector.add(0);
        return vector;
    }

    public static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
