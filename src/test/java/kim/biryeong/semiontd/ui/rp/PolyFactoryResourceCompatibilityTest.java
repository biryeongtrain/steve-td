package kim.biryeong.semiontd.ui.rp;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PolyFactoryResourceCompatibilityTest {
    @Test
    void canisterRetainsGeometryDisplayAndTintWhileUsingTheCurrentParent() {
        var model = JsonParser.parseString("{\"parent\":\"polyfactory:item/canister\",\"elements\":[{\"tintindex\":0}],\"display\":{\"gui\":{\"scale\":[1,2,3]}}}").getAsJsonObject();
        var elements = model.get("elements").deepCopy();
        var display = model.get("display").deepCopy();
        assertTrue(PolyFactoryResourceCompatibility.repairCanisterParent(model));
        assertEquals("polyfactory:item/canister_outer", model.get("parent").getAsString());
        assertEquals(elements, model.get("elements"));
        assertEquals(display, model.get("display"));
        assertFalse(PolyFactoryResourceCompatibility.repairCanisterParent(model));
    }

    @Test
    void cableParticleUsesItsExistingFaceTextureAndPreservesGeometry() {
        var model = JsonParser.parseString("{\"textures\":{\"particle\":\"polyfactory:block/cable/cable_gate_side\",\"side\":\"polyfactory:block/redstone_locking_mechanism_side\",\"back\":\"polyfactory:block/cable/cable_gate_front\"},\"elements\":[{\"from\":[0,0,2]}]}").getAsJsonObject();
        var elements = model.get("elements").deepCopy();
        assertTrue(PolyFactoryResourceCompatibility.repairCableParticle(model));
        assertEquals("#side", model.getAsJsonObject("textures").get("particle").getAsString());
        assertEquals("polyfactory:block/cable/cable_gate_front", model.getAsJsonObject("textures").get("back").getAsString());
        assertEquals(elements, model.get("elements"));
        assertFalse(PolyFactoryResourceCompatibility.repairCableParticle(model));
    }

    @Test
    void correctedOrCustomResourceReferencesStayUntouched() {
        var model = JsonParser.parseString("{\"parent\":\"custom:item/canister\",\"textures\":{\"particle\":\"custom:particle\",\"side\":\"custom:side\"}}").getAsJsonObject();
        var original = model.deepCopy();
        assertFalse(PolyFactoryResourceCompatibility.repairCanisterParent(model));
        assertFalse(PolyFactoryResourceCompatibility.repairCableParticle(model));
        assertEquals(original, model);
    }
}
