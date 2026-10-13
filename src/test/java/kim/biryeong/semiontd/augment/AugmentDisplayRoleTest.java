package kim.biryeong.semiontd.augment;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class AugmentDisplayRoleTest {
    @Test
    void everyCatalogCardHasAnExplicitReviewedClassification() {
        for (var card : AugmentCatalog.definitions()) {
            assertTrue(AugmentDisplayRole.hasClassification(card), card.id());
            assertNotNull(AugmentDisplayRole.of(card));
        }
    }

    @Test
    void stanceCardsAndRevivalUseTheirActualPrimaryEffect() {
        assertRole("tactical_designation_1_assault", AugmentDisplayRole.ATTACK);
        assertRole("tactical_designation_1_cover", AugmentDisplayRole.DEFENSE);
        assertRole("job_undead_towers_g2", AugmentDisplayRole.DEFENSE);
        assertRole("job_end_towers_p", AugmentDisplayRole.ATTACK);
        assertRole("job_future_agency_towers_p", AugmentDisplayRole.DEFENSE);
        assertRole("reserve_diamonds_gold", AugmentDisplayRole.OTHER);
        assertRole("beneficial_effect_2", AugmentDisplayRole.OTHER);
        assertRole("job_pet_towers_p", AugmentDisplayRole.OTHER);
    }

    private static void assertRole(String id, AugmentDisplayRole role) {
        assertEquals(role, AugmentDisplayRole.of(AugmentCatalog.find(id).orElseThrow()));
    }
}
