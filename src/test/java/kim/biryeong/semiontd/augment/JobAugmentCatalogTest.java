package kim.biryeong.semiontd.augment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import kim.biryeong.semiontd.job.JobRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JobAugmentCatalogTest {
    private static final List<String> JOBS = List.of(
            "villager_towers", "villager_adv_towers", "undead_towers", "animal_towers", "warlock_towers",
            "legion_towers", "resonance_towers", "illager_towers", "nether", "end_towers", "ocean", "ancient_city",
            "hero_party", "succubus", "adversary_towers", "engineer_towers", "queen_towers", "atlantis_towers",
            "plant_towers", "army", "thunder", "demon_lord_towers", "gamble", "body", "pet_towers",
            "developer_towers", "frost", "pirate", "mage_towers", "insect_towers", "future_agency_towers");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void all124ReviewIdsKeepTheirExactRegisteredBuilderAndFourRarities() {
        var entries = JobAugmentCatalog.entries();
        assertEquals(124, entries.size());
        assertEquals(124, entries.stream().map(JobAugmentCatalog.Entry::reviewId).distinct().count());
        for (int index = 0; index < JOBS.size(); index++) {
            String job = JOBS.get(index);
            String owner = "semion-td:" + switch (job) {
                case "atlantis_towers" -> "atlantis";
                case "developer_towers" -> "developer";
                case "gamble" -> "gamble_towers";
                default -> job;
            };
            String reviewPrefix = String.format(java.util.Locale.ROOT, "J%02d-", index + 1);
            assertTrue(JobRegistry.find(ResourceLocation.parse(owner)).isPresent(), owner);
            for (String suffix : List.of("s", "g1", "g2", "p")) {
                String reviewId = reviewPrefix + suffix.toUpperCase(java.util.Locale.ROOT);
                var entry = entries.stream().filter(candidate -> candidate.reviewId().equals(reviewId)).findFirst().orElseThrow();
                AugmentDefinition card = entry.definition();
                assertEquals("semiontd:job_" + job + "_" + suffix, card.id(), reviewId);
                assertEquals(owner, card.requiredJobId(), reviewId);
                assertEquals(suffix.equals("s") ? AugmentRarity.SILVER
                        : suffix.equals("p") ? AugmentRarity.PRISMATIC : AugmentRarity.GOLD, card.rarity(), reviewId);
                assertEquals(card, AugmentCatalog.find(card.id()).orElseThrow());
                assertFalse(card.reserve());
                assertFalse(card.towerAugment());
            }
        }
    }

    @Test
    void everyJobDefaultIsBundledAndPartialConfigurationPreservesExplicitValues() {
        AugmentConfig defaults = AugmentConfig.defaults();
        assertFalse(defaults.enabled());
        assertFalse(defaults.publicPoolEnabled());
        for (var entry : JobAugmentCatalog.entries()) {
            assertEquals(entry.parameters(), defaults.parametersFor(entry.definition().id()), entry.reviewId());
            assertFalse(AugmentDescriptions.describe(entry.definition(), defaults).contains("{"), entry.reviewId());
        }
        AugmentConfig changed = new AugmentConfig(false, false, null,
                Map.of("job_pet_towers_g1", Map.of("damageRatio", .73)), Set.of("job_mage_towers_p"));
        assertEquals(.73, changed.parameter("job_pet_towers_g1", "damageRatio", -1));
        assertEquals(3, changed.parameter("job_pet_towers_g1", "hitsRequired", -1));
        assertFalse(changed.isEnabled("job_mage_towers_p"));
        assertTrue(changed.isEnabled("job_mage_towers_s"));
        assertEquals(changed, AugmentConfig.fromJson(changed.toJson()));
        assertEquals(124, changed.parameters().keySet().stream().filter(id -> id.startsWith("semiontd:job_")).count());
    }

    @Test
    void invalidJobCountsFractionsAndUnknownKeysAreRejected() {
        for (Map<String, Map<String, Double>> invalid : List.of(
                Map.of("job_pet_towers_g1", Map.of("hitsRequired", 1.5)),
                Map.of("job_pet_towers_p", Map.of("healTargets", 0.0)),
                Map.of("job_developer_towers_s", Map.of("bonusRatio", 1.1)),
                Map.of("job_undead_towers_g2", Map.of("reviveHealthRatio", 1.1)),
                Map.of("job_pet_towers_s", Map.of("missingParameter", 1.0)))) {
            assertThrows(IllegalArgumentException.class,
                    () -> new AugmentConfig(false, false, null, invalid, Set.of()), invalid.toString());
        }
    }
}
