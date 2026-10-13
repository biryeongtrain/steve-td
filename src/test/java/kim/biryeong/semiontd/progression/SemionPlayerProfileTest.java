package kim.biryeong.semiontd.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class SemionPlayerProfileTest {
    @Test
    void selectedSkyboxSurvivesOtherProfileUpdates() {
        SemionPlayerProfile profile = SemionPlayerProfile.fresh("Player")
                .updateSelectedSkybox("Player", "space")
                .updateSelectedJob("Player", Identifier.fromNamespaceAndPath("semion-td", "animal"))
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
        assertTrue(existing.ownedCosmeticIds().isEmpty());
        assertTrue(existing.selectedCosmeticId().isEmpty());
        assertTrue(existing.selectedCosmeticIds().isEmpty());

        SemionPlayerProfile disabled = existing
                .updateTipsEnabled("Player", false)
                .updateSelectedJob("Player", Identifier.fromNamespaceAndPath("semion-td", "animal"))
                .recordMatch("Player", true, 10)
                .rememberRecentBuildCode("Player", "ABC123");

        assertEquals(false, disabled.tipsEnabled());
    }

    @Test
    void cosmeticPurchaseDeductsOnceAndSelectionSurvivesOtherUpdates() {
        SemionPlayerProfile funded = SemionPlayerProfile.fresh("Player").recordMatch("Player", true, 100);

        SemionPlayerProfile purchased = funded.purchaseCosmetic("Player", "crown", 35);
        SemionPlayerProfile duplicate = purchased.purchaseCosmetic("Player", "crown", 35);
        SemionPlayerProfile selected = duplicate
                .updateSelectedCosmetic("Player", "crown")
                .updateSelectedJob("Player", Identifier.fromNamespaceAndPath("semion-td", "animal"))
                .recordMatch("Player", false, 5);

        assertEquals(65, purchased.cosmeticCurrency());
        assertEquals(purchased, duplicate);
        assertTrue(selected.ownsCosmetic("crown"));
        assertEquals("crown", selected.selectedCosmeticId());
        assertEquals(java.util.List.of("crown"), selected.selectedCosmeticIds());
        assertEquals(70, selected.cosmeticCurrency());
    }

    @Test
    void legacySingleCosmeticSelectionMigratesToSelectionList() {
        SemionPlayerProfile profile = new Gson().fromJson("""
                {
                  "lastKnownName": "Player",
                  "ownedCosmeticIds": ["crown"],
                  "selectedCosmeticId": "crown"
                }
                """, SemionPlayerProfile.class);

        assertEquals(java.util.List.of("crown"), profile.selectedCosmeticIds());
    }

    @Test
    void insufficientFundsAndUnownedSelectionDoNotChangeProfile() {
        SemionPlayerProfile profile = SemionPlayerProfile.fresh("Player").recordMatch("Player", false, 10);

        assertEquals(profile, profile.purchaseCosmetic("Player", "crown", 11));
        assertEquals(profile, profile.updateSelectedCosmetic("Player", "crown"));
        assertFalse(profile.ownsCosmetic("crown"));
    }
}
