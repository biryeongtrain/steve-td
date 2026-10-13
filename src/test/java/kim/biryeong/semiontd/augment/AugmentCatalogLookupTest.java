package kim.biryeong.semiontd.augment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AugmentCatalogLookupTest {
    private static final Map<String, List<String>> STANCES = Map.of(
            "semiontd:tactical_designation_1", List.of("ASSAULT", "COVER"),
            "semiontd:tactical_designation_2", List.of("ASSAULT", "COVER"),
            "semiontd:tactical_designation_3", List.of("ASSAULT", "COVER"),
            "semiontd:engagement_plan", List.of("QUICK", "LONG"),
            "semiontd:biased_armor", List.of("PHYSICAL", "MAGIC"));

    @Test
    void indexedLookupReturnsEveryExactCatalogObjectAndPreservesPartitionOrder() {
        for (AugmentDefinition card : AugmentCatalog.definitions()) {
            assertSame(card, AugmentCatalog.find(card.id()).orElseThrow());
            assertSame(card, AugmentCatalog.find(card.id().substring("semiontd:".length())).orElseThrow());
        }
        assertEquals(AugmentCatalog.definitions().stream().filter(card -> !card.reserve()).toList(),
                AugmentCatalog.normalDefinitions());
        assertEquals(AugmentCatalog.definitions().stream().filter(AugmentDefinition::reserve).toList(),
                AugmentCatalog.reserveDefinitions());
        assertThrows(UnsupportedOperationException.class, () -> AugmentCatalog.normalDefinitions().clear());
        assertThrows(UnsupportedOperationException.class, () -> AugmentCatalog.reserveDefinitions().clear());
    }

    @Test
    void legacyCombinedCardsAndFixedModesRemainReadableWithoutEnteringOffers() {
        for (var entry : STANCES.entrySet()) {
            String parent = entry.getKey();
            assertEquals(parent, AugmentCatalog.find(parent).orElseThrow().id());
            assertFalse(AugmentCatalog.definitions().stream().anyMatch(card -> card.id().equals(parent)));
            assertEquals("", AugmentCatalog.fixedMode(parent));
            for (String mode : entry.getValue()) {
                String id = parent + "_" + mode.toLowerCase(Locale.ROOT);
                assertTrue(AugmentCatalog.find(id).isPresent());
                assertEquals(parent, AugmentCatalog.effectId(id));
                assertEquals(mode, AugmentCatalog.fixedMode(id));
                assertTrue(AugmentCatalog.matchesSelection(parent, id));
                assertFalse(AugmentCatalog.matchesSelection(id, parent));
                for (String malformed : List.of(id + "_extra", parent + "_" + mode, "other:" + id.substring(9))) {
                    assertEquals(malformed, AugmentCatalog.effectId(malformed));
                    assertEquals("", AugmentCatalog.fixedMode(malformed));
                    assertTrue(AugmentCatalog.find(malformed).isEmpty());
                }
            }
        }
    }

    @Test
    void selectionMatchingEqualsNormalizedComparisonForPrefixedAndBareIds() {
        List<String> ids = new ArrayList<>();
        AugmentCatalog.definitions().forEach(card -> ids.add(card.id()));
        ids.addAll(STANCES.keySet());
        ids.addAll(List.of("semiontd:unknown", "other:biased_armor", "semiontd:biased_armor_physicalx"));
        for (String selected : ids) {
            for (String requested : ids) {
                for (String form : List.of(requested, requested.substring(requested.indexOf(':') + 1))) {
                    String normalized = AugmentCatalog.normalizeId(form);
                    boolean expected = normalized.equals(selected)
                            || normalized.equals(AugmentCatalog.effectId(selected));
                    assertEquals(expected, AugmentCatalog.matchesSelection(form, selected), form + " vs " + selected);
                }
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> AugmentCatalog.matchesSelection(" ", "semiontd:biased_armor"));
        assertThrows(IllegalArgumentException.class, () -> AugmentSnapshot.none().has(null));
        assertThrows(IllegalArgumentException.class, () -> AugmentSnapshot.none().has(" "));
    }

    @Test
    void unknownAndInvalidIdsKeepTheirExistingContracts() {
        for (String id : List.of("", " ", "\t", "unknown", "semiontd:unknown", "semiontd:decisive_delivery")) {
            assertTrue(AugmentCatalog.find(id).isEmpty());
        }
        assertTrue(AugmentCatalog.find(null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> AugmentCatalog.effectId(null));
        assertThrows(IllegalArgumentException.class, () -> AugmentCatalog.fixedMode(" "));
        assertEquals("semiontd:unknown", AugmentCatalog.effectId("unknown"));
        assertFalse(AugmentCatalog.matchesSelection("unknown", null));
    }

    @Test
    void firstSplitDefinitionWinsOverDuplicateAndLegacyDefinitions() {
        AugmentDefinition first = card("overlap", "first", false);
        AugmentDefinition duplicate = card("overlap", "duplicate", true);
        AugmentDefinition legacy = card("overlap", "legacy", true);
        AugmentDefinition onlyLegacy = card("legacy", "legacy-only", false);
        AugmentDefinition laterLegacy = card("legacy", "later-legacy", true);
        var lookup = new AugmentCatalogLookup(List.of(first, duplicate), List.of(legacy, onlyLegacy, laterLegacy), Map.of());
        assertSame(first, lookup.find("semiontd:overlap").orElseThrow());
        assertSame(onlyLegacy, lookup.find("semiontd:legacy").orElseThrow());
        assertEquals(List.of(first), lookup.normalDefinitions());
        assertEquals(List.of(duplicate), lookup.reserveDefinitions());
    }

    @Test
    void lookupOwnsImmutableSnapshotOfConstructorInputs() {
        AugmentDefinition first = card("first", "first", false);
        AugmentDefinition fallback = card("fallback", "fallback", true);
        var split = new ArrayList<>(List.of(first));
        var base = new ArrayList<>(List.of(fallback));
        var modes = new ArrayList<>(List.of("ASSAULT"));
        Map<String, List<String>> stances = new HashMap<>();
        stances.put("semiontd:parent", modes);
        var lookup = new AugmentCatalogLookup(split, base, stances);
        split.clear();
        base.clear();
        modes.clear();
        stances.clear();
        assertSame(first, lookup.find(first.id()).orElseThrow());
        assertSame(fallback, lookup.find(fallback.id()).orElseThrow());
        assertEquals(List.of(first), lookup.normalDefinitions());
        assertEquals("semiontd:parent", lookup.effectId("semiontd:parent_assault"));
        assertEquals("ASSAULT", lookup.fixedMode("semiontd:parent_assault"));
    }

    private static AugmentDefinition card(String id, String name, boolean reserve) {
        return new AugmentDefinition(id, name, AugmentRarity.SILVER, AugmentCategory.GENERAL,
                id, true, false, false, reserve, Set.of(5), Set.of(), name);
    }
}
