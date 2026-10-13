package kim.biryeong.semiontd.tower.magicschool;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.progression.HeroCompanionSkinPreference;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.*;

class MagicSchoolSkinsTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach @AfterEach void clear() { MagicSchoolSkins.clearAll(); }

    @Test void fiveChoicesShareHouseSkinsAcrossRanksAndKeepOwnersSeparate() {
        var owner = UUID.randomUUID();
        var other = UUID.randomUUID();
        assertEquals(5, MagicSchoolSkins.Kind.values().length);
        assertNull(MagicSchoolSkins.Kind.forTower(MagicSchoolTowers.HOGWARTS));
        for (var kind : MagicSchoolSkins.Kind.values()) {
            var skin = new HeroCompanionSkinPreference(kind.id(), UUID.randomUUID().toString(), "texture-" + kind.id(), "signature");
            MagicSchoolSkins.set(owner, kind, skin);
            var matches = MagicSchoolTowers.all().stream().filter(kind::matches).toList();
            assertEquals(kind == MagicSchoolSkins.Kind.FRESHMAN ? 1 : 2, matches.size());
            for (var type : matches) {
                assertEquals(kind, MagicSchoolSkins.Kind.forTower(type));
                assertEquals(skin, MagicSchoolSkins.preference(owner, MagicSchoolSkins.Kind.forTower(type)).orElseThrow());
            }
            assertTrue(MagicSchoolSkins.preference(other, kind).isEmpty());
        }
    }

    @Test void profilesUseSignedTexturesAndResetRestoresOwnerSkinWithoutSharingProfileIds() {
        var owner = UUID.randomUUID();
        var original = new Property("textures", "owner-texture", "owner-signature");
        var fallback = new GameProfile(owner, "Owner", new com.mojang.authlib.properties.PropertyMap(
                com.google.common.collect.ImmutableMultimap.of("textures", original)));
        var kind = MagicSchoolSkins.Kind.GRYFFINDOR;
        var originalProfile = MagicSchoolSkins.profile(owner, kind, fallback);
        assertEquals(original, originalProfile.properties().get("textures").iterator().next());
        var skin = new HeroCompanionSkinPreference("Wizard", UUID.randomUUID().toString(), "wizard-texture", "wizard-signature");
        MagicSchoolSkins.load(owner, Map.of(kind.id(), skin, "unknown", skin));
        var selected = MagicSchoolSkins.profile(owner, kind, fallback);
        assertEquals(skin.textureProperty().orElseThrow(), selected.properties().get("textures").iterator().next());
        assertNotEquals(originalProfile.id(), selected.id());
        assertEquals(original, fallback.properties().get("textures").iterator().next());
        MagicSchoolSkins.set(owner, kind, null);
        assertEquals(originalProfile, MagicSchoolSkins.profile(owner, kind, fallback));
        MagicSchoolSkins.load(owner, null);
        assertTrue(MagicSchoolSkins.preference(owner, kind).isEmpty());
    }
}
