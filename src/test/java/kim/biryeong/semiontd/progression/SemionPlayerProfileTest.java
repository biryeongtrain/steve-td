package kim.biryeong.semiontd.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class SemionPlayerProfileTest {
    @Test
    void selectedSkyboxSurvivesOtherProfileUpdates() {
        SemionPlayerProfile profile = SemionPlayerProfile.fresh("Player")
                .updateSelectedSkybox("Player", "space")
                .updateSelectedJob("Player", ResourceLocation.fromNamespaceAndPath("semion-td", "animal"))
                .recordMatch("Player", true, 10)
                .rememberRecentBuildCode("Player", "ABC123");

        assertEquals("space", profile.selectedSkyboxId());
    }

    @Test
    void tipsEnabledDefaultsToTrueForExistingProfilesAndSurvivesUpdates() {
        SemionPlayerProfile existing = new Gson().fromJson("""
                {
                  "lastKnownName": "Player",
                  "gamesPlayed": 3,
                  "selectedSkyboxId": "space",
                  "recentBuildCodes": []
                }
                """, SemionPlayerProfile.class);

        assertTrue(existing.tipsEnabled());

        SemionPlayerProfile disabled = existing
                .updateTipsEnabled("Player", false)
                .updateSelectedJob("Player", ResourceLocation.fromNamespaceAndPath("semion-td", "animal"))
                .recordMatch("Player", true, 10)
                .rememberRecentBuildCode("Player", "ABC123");

        assertEquals(false, disabled.tipsEnabled());
    }
}
