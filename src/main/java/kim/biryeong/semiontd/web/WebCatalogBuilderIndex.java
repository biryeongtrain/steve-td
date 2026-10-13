package kim.biryeong.semiontd.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;

final class WebCatalogBuilderIndex {
    private final Map<String, String> towerBuilders;
    private final Map<String, List<String>> builderTowers;

    private WebCatalogBuilderIndex(Map<String, String> towerBuilders) {
        this.towerBuilders = towerBuilders;
        Map<String, List<String>> grouped = new HashMap<>();
        towerBuilders.forEach((towerId, builderId) -> grouped.computeIfAbsent(builderId, ignored -> new ArrayList<>()).add(towerId));
        grouped.replaceAll((builderId, towerIds) -> List.copyOf(towerIds));
        this.builderTowers = Map.copyOf(grouped);
    }

    static WebCatalogBuilderIndex create(List<ProductionTowerCatalog.CatalogEntry> catalogEntries, List<SemionJob> jobs) {
        Map<String, String> towerBuilders = new TreeMap<>();
        for (ProductionTowerCatalog.CatalogEntry entry : catalogEntries) {
            List<SemionJob> owners = jobs.stream()
                    .filter(job -> job.includesTowerInCatalog(entry.type()))
                    .toList();
            if (entry.availability() == ProductionTowerCatalog.Availability.AUGMENT) {
                if (!owners.isEmpty()) {
                    throw new IllegalStateException("Augment tower cannot belong to a builder: " + entry.type().id());
                }
                continue;
            }
            if (owners.size() != 1) {
                throw new IllegalStateException("Tower must belong to exactly one builder: "
                        + entry.type().id() + " owners=" + owners.stream().map(job -> job.id().toString()).toList());
            }
            towerBuilders.put(entry.type().id(), owners.getFirst().id().toString());
        }
        return new WebCatalogBuilderIndex(towerBuilders);
    }

    String builderId(String towerId) {
        return towerBuilders.get(towerId);
    }

    List<String> towerIds(String builderId) {
        return builderTowers.getOrDefault(builderId, List.of());
    }
}
