package kim.biryeong.semiontd.augment;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class AugmentIconCategoryTest {
    @Test
    void everyCurrentCardHasExactlyOneExplicitIconWithNoRetiredIds() {
        var expected = AugmentCatalog.definitions().stream().map(AugmentDefinition::id).collect(Collectors.toSet());
        assertEquals(expected, AugmentIconCategory.classifiedIds(), "Missing and obsolete classifications both need review");
        var used = new HashSet<AugmentIconCategory>();
        for (var card : AugmentCatalog.definitions()) used.add(AugmentIconCategory.of(card));
        assertEquals(new HashSet<>(Arrays.asList(AugmentIconCategory.values())), used);
    }

    @Test
    void effectIdentityWinsOverNamesAndSecondaryEffects() {
        assertCategory("job_future_agency_towers_s", AugmentIconCategory.CONTROL);
        assertCategory("job_future_agency_towers_g1", AugmentIconCategory.REACH);
        assertCategory("job_future_agency_towers_g2", AugmentIconCategory.DEPLOY);
        assertCategory("job_future_agency_towers_p", AugmentIconCategory.REVIVAL);
        assertCategory("job_insect_towers_s2", AugmentIconCategory.VITALITY);
        assertCategory("job_insect_towers_g3", AugmentIconCategory.VITALITY);
        assertCategory("job_demon_lord_towers_p", AugmentIconCategory.DAMAGE);
        assertCategory("job_magic_school_g1", AugmentIconCategory.SYNERGY);
        assertCategory("job_resonance_towers_g1", AugmentIconCategory.SYNERGY);
        assertCategory("job_mage_towers_s", AugmentIconCategory.RESOURCE);
        assertCategory("job_pet_towers_g2", AugmentIconCategory.ENHANCEMENT);
        assertCategory("job_gamble_p", AugmentIconCategory.FORTUNE);
        assertCategory("overheat_core", AugmentIconCategory.DAMAGE);
        assertCategory("tactical_designation_1_cover", AugmentIconCategory.GUARD);
    }

    @Test
    void jobScopeUsesRequiredJobIdentityIndependentlyOfEffectAndTowerCategory() {
        var cards = AugmentCatalog.definitions();
        assertEquals(130, cards.stream().filter(card -> AugmentScope.of(card) == AugmentScope.JOB_SPECIFIC).count());
        assertEquals(51, cards.stream().filter(card -> AugmentScope.of(card) == AugmentScope.COMMON).count());
        assertEquals(32, cards.stream().filter(card -> AugmentScope.of(card) == AugmentScope.JOB_SPECIFIC)
                .map(AugmentDefinition::requiredJobId).distinct().count());
        for (var card : cards) {
            assertEquals(card.requiredJobId() != null, AugmentScope.of(card) == AugmentScope.JOB_SPECIFIC);
            if (card.towerAugment()) assertEquals(AugmentScope.COMMON, AugmentScope.of(card));
            assertNotNull(AugmentIconCategory.of(card));
        }
    }

    private static void assertCategory(String id, AugmentIconCategory expected) {
        assertEquals(expected, AugmentIconCategory.of(AugmentCatalog.find(id).orElseThrow()), id);
    }
}
