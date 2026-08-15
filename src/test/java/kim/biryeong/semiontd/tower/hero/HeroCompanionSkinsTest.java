package kim.biryeong.semiontd.tower.hero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.progression.HeroCompanionSkinPreference;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class HeroCompanionSkinsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void cleanup() {
        HeroCompanionSkins.clearAll();
    }

    @Test
    void profileIdsAreOwnerScopedDeterministicAndKeepDefaultRoleSkin() {
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();
        HeroCompanionRole role = HeroCompanionRole.KNIGHT;

        UUID first = HeroCompanionSkins.visualProfileId(firstOwner, role, null);
        UUID repeated = HeroCompanionSkins.visualProfileId(firstOwner, role, null);
        UUID second = HeroCompanionSkins.visualProfileId(secondOwner, role, null);

        assertEquals(first, repeated);
        assertNotEquals(first, second);
        int roleSkin = Math.floorMod(HeroCompanionSkins.roleDefaultProfileId(role).hashCode(), 18);
        assertEquals(roleSkin, Math.floorMod(first.hashCode(), 18));
        assertEquals(roleSkin, Math.floorMod(second.hashCode(), 18));
    }

    @Test
    void customTexturesAndVanillaDefaultsProduceTheExpectedProfiles() {
        UUID owner = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        HeroCompanionSkinPreference custom = new HeroCompanionSkinPreference(
                "Custom",
                source.toString(),
                "texture-value",
                "texture-signature"
        );
        GameProfile customProfile = HeroCompanionSkins.profile(owner, HeroCompanionRole.MAGE, custom);
        assertEquals("texture-value", customProfile.getProperties().get("textures").iterator().next().value());

        HeroCompanionSkinPreference vanilla = new HeroCompanionSkinPreference(
                "Vanilla",
                source.toString(),
                "",
                ""
        );
        UUID vanillaId = HeroCompanionSkins.visualProfileId(owner, HeroCompanionRole.MAGE, vanilla);
        assertEquals(Math.floorMod(source.hashCode(), 18), Math.floorMod(vanillaId.hashCode(), 18));
    }

    @Test
    void accountSelectionsAreSeparateFromMatchStateCleanup() {
        UUID owner = UUID.randomUUID();
        HeroCompanionSkinPreference skin = new HeroCompanionSkinPreference(
                "Archer",
                UUID.randomUUID().toString(),
                "texture",
                ""
        );
        HeroCompanionSkins.load(owner, Map.of(HeroCompanionRole.ARCHER.id(), skin));

        HeroPartyStates.state(owner);
        HeroPartyStates.clear(owner);

        assertEquals(skin, HeroCompanionSkins.preference(owner, HeroCompanionRole.ARCHER).orElseThrow());
        assertTrue(HeroPartyStates.find(owner).isEmpty());
    }
}
