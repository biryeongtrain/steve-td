package kim.biryeong.semiontd.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import kim.biryeong.semiontd.config.ProgressionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class HeroCompanionSkinPersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void missingSkinFieldLoadsAndSavedRolesSurviveServiceReload() throws Exception {
        Path path = tempDir.resolve("profiles.json");
        UUID playerId = UUID.randomUUID();
        Files.writeString(path, "{\n  \"" + playerId + "\": {\"lastKnownName\": \"Legacy\"}\n}\n");

        ProgressionService service = new ProgressionService(ProgressionConfig.defaultConfig(), path);
        assertTrue(service.profile(null, playerId, "Legacy").heroCompanionSkins().isEmpty());

        HeroCompanionSkinPreference knight = skin("KnightSkin", UUID.randomUUID(), "value-a", "signature-a");
        HeroCompanionSkinPreference mage = skin("MageSkin", UUID.randomUUID(), "value-b", "");
        assertTrue(service.saveHeroCompanionSkin(playerId, "Legacy", "knight", knight));
        assertTrue(service.saveHeroCompanionSkin(playerId, "Legacy", "mage", mage));

        ProgressionService reloaded = new ProgressionService(ProgressionConfig.defaultConfig(), path);
        SemionPlayerProfile saved = reloaded.profile(null, playerId, "Legacy");
        assertEquals(knight, saved.heroCompanionSkins().get("knight"));
        assertEquals(mage, saved.heroCompanionSkins().get("mage"));

        HeroCompanionSkinPreference replacement = skin("Replacement", UUID.randomUUID(), "value-c", "signature-c");
        assertTrue(reloaded.saveHeroCompanionSkin(playerId, "Legacy", "knight", replacement));
        assertTrue(reloaded.saveHeroCompanionSkin(playerId, "Legacy", "mage", null));
        SemionPlayerProfile updated = new ProgressionService(ProgressionConfig.defaultConfig(), path)
                .profile(null, playerId, "Legacy");
        assertEquals(replacement, updated.heroCompanionSkins().get("knight"));
        assertFalse(updated.heroCompanionSkins().containsKey("mage"));
    }

    @Test
    void profileMutationsPreserveSkinsAndAccountsStayIsolated() {
        Path path = tempDir.resolve("profiles.json");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        HeroCompanionSkinPreference skin = skin("Shared", UUID.randomUUID(), "value", "signature");
        ProgressionService service = new ProgressionService(ProgressionConfig.defaultConfig(), path);

        assertTrue(service.saveHeroCompanionSkin(first, "First", "archer", skin));
        service.saveSelectedSkybox(null, first, "FirstRenamed", "night");

        assertEquals(skin, service.profile(null, first, "FirstRenamed").heroCompanionSkins().get("archer"));
        assertTrue(service.profile(null, second, "Second").heroCompanionSkins().isEmpty());
    }

    @Test
    void failedSaveRollsBackTheProfileMutation() throws Exception {
        Path path = Files.createDirectory(tempDir.resolve("profiles.json"));
        UUID playerId = UUID.randomUUID();
        ProgressionService service = new ProgressionService(ProgressionConfig.defaultConfig(), path);
        service.profile(null, playerId, "Player");

        boolean saved = service.saveHeroCompanionSkin(
                playerId,
                "Player",
                "rogue",
                skin("Rogue", UUID.randomUUID(), "value", "signature")
        );

        assertFalse(saved);
        assertTrue(service.profile(null, playerId, "Player").heroCompanionSkins().isEmpty());
        assertTrue(Files.exists(tempDir.resolve("progression-fallback.log")));
    }

    private static HeroCompanionSkinPreference skin(String name, UUID uuid, String value, String signature) {
        return new HeroCompanionSkinPreference(name, uuid.toString(), value, signature);
    }
}
