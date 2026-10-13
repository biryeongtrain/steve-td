package kim.biryeong.semiontd.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.blueprint.BlueprintTowers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class WebCatalogBuilderIndexTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void loadCatalog() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void matchesIndependentBuilderScansAndKeepsLexicalTowerOrder() {
        var entries = ProductionTowerCatalog.all().stream()
                .filter(entry -> !BlueprintTowers.isBlueprintTower(entry.type()))
                .sorted(Comparator.comparing((ProductionTowerCatalog.CatalogEntry entry) -> entry.type().id()).reversed())
                .toList();
        var jobs = JobRegistry.all().stream().sorted(Comparator.comparing(job -> job.id().toString())).toList();
        WebCatalogBuilderIndex index = WebCatalogBuilderIndex.create(entries, jobs);
        Map<String, String> expected = new TreeMap<>();
        for (var entry : entries) {
            var owners = jobs.stream().filter(job -> job.includesTowerInCatalog(entry.type())).toList();
            if (entry.availability() == ProductionTowerCatalog.Availability.AUGMENT) {
                assertEquals(List.of(), owners);
                assertNull(index.builderId(entry.type().id()));
            } else {
                assertEquals(1, owners.size());
                expected.put(entry.type().id(), owners.getFirst().id().toString());
                assertEquals(owners.getFirst().id().toString(), index.builderId(entry.type().id()));
            }
        }
        for (var job : jobs) {
            var expectedIds = expected.entrySet().stream().filter(entry -> entry.getValue().equals(job.id().toString()))
                    .map(Map.Entry::getKey).toList();
            assertEquals(expectedIds, index.towerIds(job.id().toString()));
            assertThrows(UnsupportedOperationException.class, () -> index.towerIds(job.id().toString()).add("changed"));
        }
        assertEquals(List.of(), index.towerIds("semion-td:unknown"));
    }

    @Test
    void rejectsMissingDuplicateAndAugmentOwnershipWithOriginalDiagnostics() {
        var entry = ProductionTowerCatalog.all().stream()
                .filter(candidate -> candidate.availability() != ProductionTowerCatalog.Availability.AUGMENT)
                .findFirst().orElseThrow();
        var claimant = claimant(new AtomicBoolean(true));
        assertEquals("Tower must belong to exactly one builder: " + entry.type().id() + " owners=[]",
                assertThrows(IllegalStateException.class,
                        () -> WebCatalogBuilderIndex.create(List.of(entry), List.of())).getMessage());
        assertEquals("Tower must belong to exactly one builder: " + entry.type().id()
                        + " owners=[semion-td:test, semion-td:test]",
                assertThrows(IllegalStateException.class,
                        () -> WebCatalogBuilderIndex.create(List.of(entry), List.of(claimant, claimant))).getMessage());
        var augment = ProductionTowerCatalog.all().stream()
                .filter(candidate -> candidate.availability() == ProductionTowerCatalog.Availability.AUGMENT)
                .findFirst().orElseThrow();
        assertEquals("Augment tower cannot belong to a builder: " + augment.type().id(),
                assertThrows(IllegalStateException.class,
                        () -> WebCatalogBuilderIndex.create(List.of(augment), List.of(claimant))).getMessage());
    }

    @Test
    void rebuildsOwnershipForEachSnapshotWithoutMutatingPriorIndex() {
        var entry = ProductionTowerCatalog.all().stream()
                .filter(candidate -> candidate.availability() != ProductionTowerCatalog.Availability.AUGMENT)
                .findFirst().orElseThrow();
        AtomicBoolean enabled = new AtomicBoolean(true);
        SemionJob claimant = claimant(enabled);
        var index = WebCatalogBuilderIndex.create(List.of(entry), List.of(claimant));
        enabled.set(false);
        assertThrows(IllegalStateException.class, () -> WebCatalogBuilderIndex.create(List.of(entry), List.of(claimant)));
        assertEquals(List.of(entry.type().id()), index.towerIds(claimant.id().toString()));
        assertEquals(List.of(), WebCatalogBuilderIndex.create(List.of(), List.of(claimant)).towerIds(claimant.id().toString()));
    }

    private static SemionJob claimant(AtomicBoolean enabled) {
        return new SemionJob("semion-td:test", "Test") {
            @Override
            public boolean includesTowerInCatalog(TowerType type) {
                return enabled.get();
            }
        };
    }
}
