package kim.biryeong.semiontd.progression;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import kim.biryeong.semiontd.config.ProgressionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MagicSchoolSkinPersistenceTest {
    @TempDir Path tempDir;

    @Test void legacyProfilesLoadAndWizardChoicesSurviveReloadAndOtherProfileChanges() throws Exception {
        var path = tempDir.resolve("profiles.json");
        var owner = UUID.randomUUID();
        Files.writeString(path, "{\"" + owner + "\":{\"lastKnownName\":\"Owner\"}}");
        var service = new ProgressionService(ProgressionConfig.defaultConfig(), path);
        assertTrue(service.profile(null, owner, "Owner").magicSchoolSkins().isEmpty());
        var wizard = new HeroCompanionSkinPreference("Wizard", UUID.randomUUID().toString(), "wizard", "signature");
        var hero = new HeroCompanionSkinPreference("Hero", UUID.randomUUID().toString(), "hero", "signature");
        for (String kind : java.util.List.of("freshman", "gryffindor", "hufflepuff", "ravenclaw", "slytherin")) {
            assertTrue(service.saveMagicSchoolSkin(owner, "Owner", kind, wizard));
        }
        assertTrue(service.saveHeroCompanionSkin(owner, "Owner", "mage", hero));
        service.saveSelectedSkybox(null, owner, "Renamed", "night");
        var reloaded = new ProgressionService(ProgressionConfig.defaultConfig(), path);
        var profile = reloaded.profile(null, owner, "Renamed");
        assertEquals(5, profile.magicSchoolSkins().size());
        assertTrue(profile.magicSchoolSkins().values().stream().allMatch(wizard::equals));
        assertEquals(hero, profile.heroCompanionSkins().get("mage"));
        assertEquals(profile.magicSchoolSkins(), profile.recordMatch("Renamed", true, 10).magicSchoolSkins());
        assertTrue(reloaded.profile(null, UUID.randomUUID(), "Other").magicSchoolSkins().isEmpty());
        assertTrue(reloaded.saveMagicSchoolSkin(owner, "Renamed", "gryffindor", null));
        var reset = new ProgressionService(ProgressionConfig.defaultConfig(), path).profile(null, owner, "Renamed");
        assertEquals(4, reset.magicSchoolSkins().size());
        assertFalse(reset.magicSchoolSkins().containsKey("gryffindor"));
        assertEquals(hero, reset.heroCompanionSkins().get("mage"));
    }

    @Test void failedSaveAndInvalidTexturesDoNotChangeStoredPreferences() throws Exception {
        var path = Files.createDirectory(tempDir.resolve("profiles.json"));
        var owner = UUID.randomUUID();
        var service = new ProgressionService(ProgressionConfig.defaultConfig(), path);
        service.profile(null, owner, "Owner");
        var skin = new HeroCompanionSkinPreference("Wizard", UUID.randomUUID().toString(), "value", "signature");
        assertFalse(service.saveMagicSchoolSkin(owner, "Owner", "freshman", skin));
        assertFalse(service.saveMagicSchoolSkin(owner, "Owner", "freshman", new HeroCompanionSkinPreference("", "", "", "")));
        assertTrue(service.profile(null, owner, "Owner").magicSchoolSkins().isEmpty());
    }

    @Test void wizardSkinsAndBlueprintsSurviveEachOthersWritesAndProfileUpdates() {
        var path = tempDir.resolve("profiles.json");
        var owner = UUID.randomUUID();
        var service = new ProgressionService(ProgressionConfig.defaultConfig(), path);
        var skin = new HeroCompanionSkinPreference("Wizard", UUID.randomUUID().toString(), "texture", "signature");
        var design = new kim.biryeong.semiontd.tower.blueprint.BlueprintDesign(
                "Saved design", 200, 30, 22, 8, 25, "MAGIC", "magic_school_freshman_t1", java.util.Map.of(), "first");
        assertTrue(service.saveBlueprints(owner, "Owner", java.util.List.of(design)));
        assertTrue(service.saveMagicSchoolSkin(owner, "Owner", "freshman", skin));
        var second = design.withName("Second design");
        assertTrue(service.saveBlueprints(owner, "Renamed", java.util.List.of(design, second)));
        var profile = new ProgressionService(ProgressionConfig.defaultConfig(), path).profile(null, owner, "Renamed");
        assertEquals(java.util.List.of(design, second), profile.blueprints());
        assertEquals(skin, profile.magicSchoolSkins().get("freshman"));
        var updated = profile.recordMatch("Renamed", true, 10).grantCosmeticCurrency("Renamed", 5)
                .updateHeroCompanionSkin("Renamed", "mage", skin);
        assertEquals(profile.blueprints(), updated.blueprints());
        assertEquals(profile.magicSchoolSkins(), updated.magicSchoolSkins());
    }
}
