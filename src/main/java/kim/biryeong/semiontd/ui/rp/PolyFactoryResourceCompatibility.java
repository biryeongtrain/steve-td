package kim.biryeong.semiontd.ui.rp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.util.function.Predicate;

public final class PolyFactoryResourceCompatibility {
    private PolyFactoryResourceCompatibility() {
    }

    public static void register() {
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(builder -> {
            if (builder.getStringDataOrSource("assets/polyfactory/models/item/canister.json") == null
                    && builder.getStringDataOrSource("assets/polyfactory/models/item/canister_outer.json") != null) {
                patch(builder, "assets/polyfactory/models/block/fluid/canister_model.json",
                        PolyFactoryResourceCompatibility::repairCanisterParent);
            }
            patch(builder, "assets/polyfactory/models/block/gated_cable.json",
                    PolyFactoryResourceCompatibility::repairCableParticle);
        });
    }

    private static void patch(ResourcePackBuilder builder, String path, Predicate<JsonObject> repair) {
        String source = builder.getStringDataOrSource(path);
        if (source == null) {
            return;
        }
        JsonObject model = JsonParser.parseString(source).getAsJsonObject();
        if (repair.test(model)) {
            builder.addStringData(path, model.toString());
        }
    }

    static boolean repairCanisterParent(JsonObject model) {
        if (model.has("parent") && "polyfactory:item/canister".equals(model.get("parent").getAsString())) {
            model.addProperty("parent", "polyfactory:item/canister_outer");
            return true;
        }
        return false;
    }

    static boolean repairCableParticle(JsonObject model) {
        JsonObject textures = model.getAsJsonObject("textures");
        if (textures != null && textures.has("particle") && textures.has("side")
                && "polyfactory:block/cable/cable_gate_side".equals(textures.get("particle").getAsString())
                && "polyfactory:block/redstone_locking_mechanism_side".equals(textures.get("side").getAsString())) {
            textures.addProperty("particle", "#side");
            return true;
        }
        return false;
    }
}
