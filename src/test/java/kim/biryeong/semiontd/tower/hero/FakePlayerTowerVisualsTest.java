package kim.biryeong.semiontd.tower.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakePlayerTowerVisualsTest {
    @Test
    void succubusAlwaysUsesTheFixedSlimTextureWithAnOwnerScopedProfile() throws Exception {
        GameProfile first = FakePlayerTowerVisuals.succubusProfile(UUID.randomUUID());
        GameProfile second = FakePlayerTowerVisuals.succubusProfile(UUID.randomUUID());
        var firstTexture = first.getProperties().get("textures").iterator().next();
        var secondTexture = second.getProperties().get("textures").iterator().next();

        assertNotEquals(first.getId(), second.getId());
        assertEquals(firstTexture.value(), secondTexture.value());
        assertEquals(FakePlayerTowerVisuals.SUCCUBUS_TEXTURE_SIGNATURE, firstTexture.signature());
        String metadata = new String(Base64.getDecoder().decode(firstTexture.value()), StandardCharsets.UTF_8);
        assertTrue(metadata.contains("\"model\" : \"slim\""));
        assertTrue(metadata.contains("d90589379f402e90fc6e449608cc65cb1ec4e896f75de52c0b171d6b684ef04f"));

        byte[] encoded;
        try (var stream = getClass().getResourceAsStream("/semiontd/skins/succubus-slim.png.base64")) {
            encoded = stream.readAllBytes();
        }
        byte[] png = Base64.getMimeDecoder().decode(encoded);
        assertEquals("4c1066305e8c78d6f85837db71e5a2783b5dfce1ebd435e8f47e10682ae61df6",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png)));
    }
}
