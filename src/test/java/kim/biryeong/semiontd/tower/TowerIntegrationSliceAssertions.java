package kim.biryeong.semiontd.tower;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import net.minecraft.resources.ResourceLocation;

public final class TowerIntegrationSliceAssertions {
    private TowerIntegrationSliceAssertions() {
    }

    public static void assertFamilyClosed(FamilyContract family, TowerBalanceConfig defaults) {
        SemionJob job = JobRegistry.find(family.jobId()).orElseThrow(
                () -> new AssertionError("Missing builder registration: " + family.jobId())
        );
        Set<String> expectedIds = family.towers().stream().map(TowerType::id).collect(Collectors.toSet());
        Set<String> ownedCatalogIds = ProductionTowerCatalog.all().stream()
                .map(ProductionTowerCatalog.CatalogEntry::type)
                .filter(job::includesTowerInCatalog)
                .map(TowerType::id)
                .collect(Collectors.toSet());
        assertEquals(expectedIds, ownedCatalogIds, "Builder ownership and family tower list diverged");
        assertTrue(defaults.abilities().containsKey(family.globalConfigId()),
                "Missing global ability defaults for " + family.globalConfigId());

        Map<String, List<UpgradeExpectation>> upgradesBySource = family.upgrades().stream()
                .collect(Collectors.groupingBy(UpgradeExpectation::fromTowerId, LinkedHashMap::new, Collectors.toList()));
        for (TowerType declaredType : family.towers()) {
            assertTrue(defaults.towers().containsKey(declaredType.id()),
                    "Missing tower balance defaults: " + declaredType.id());
            ProductionTowerCatalog.CatalogEntry entry = ProductionTowerCatalog.find(declaredType.id()).orElseThrow(
                    () -> new AssertionError("Missing production catalog entry: " + declaredType.id())
            );
            assertEquals(family.tiers().get(declaredType.id()), entry.tier(),
                    "Wrong production tier for " + declaredType.id());
            List<ResourceLocation> owners = JobRegistry.all().stream()
                    .filter(candidate -> candidate.includesTowerInCatalog(entry.type()))
                    .map(SemionJob::id)
                    .toList();
            assertEquals(List.of(family.jobId()), owners,
                    "Tower must have exactly one expected web owner: " + declaredType.id());
            Tower runtime = entry.create(
                    UUID.nameUUIDFromBytes(("integration-slice-" + declaredType.id())
                            .getBytes(StandardCharsets.UTF_8)),
                    TeamId.RED,
                    1,
                    new GridPosition(0, 64, 0)
            );
            assertInstanceOf(family.runtimeType().apply(declaredType), runtime,
                    "Wrong runtime factory for " + declaredType.id());

            List<UpgradeExpectation> expectedUpgrades = upgradesBySource.getOrDefault(declaredType.id(), List.of());
            Map<String, String> actualUpgrades = ProductionTowerCatalog.upgrades(entry.type()).stream()
                    .collect(Collectors.toMap(TowerUpgradeOption::id, option -> option.targetType().id()));
            Map<String, String> expectedUpgradeTargets = expectedUpgrades.stream()
                    .collect(Collectors.toMap(UpgradeExpectation::upgradeId, UpgradeExpectation::toTowerId));
            assertEquals(expectedUpgradeTargets, actualUpgrades,
                    "Directed upgrade graph diverged for " + declaredType.id());
            expectedUpgrades.forEach(upgrade -> {
                TowerUpgradeOption option = ProductionTowerCatalog.upgrade(entry.type(), upgrade.upgradeId()).orElseThrow();
                assertEquals(TowerBalanceRuntime.upgradeCost(entry.type(), upgrade.upgradeId()), option.mineralCost(),
                        "Upgrade cost did not resolve through runtime config for " + upgrade.configKey());
                assertTrue(ProductionTowerCatalog.find(upgrade.toTowerId()).isPresent(),
                        "Upgrade target is not registered: " + upgrade.toTowerId());
            });
        }
        assertResolvedDescriptions(family.towers());
        assertFalse(job.description().isEmpty(), "Builder description must be player-visible: " + family.jobId());
    }

    public static void assertResolvedDescriptions(List<TowerType> towerTypes) {
        for (TowerType towerType : towerTypes) {
            TowerType resolved = ProductionTowerCatalog.find(towerType.id()).orElseThrow().type();
            assertFalse(resolved.description().isEmpty(), "Missing description for " + towerType.id());
            assertTrue(resolved.description().stream().noneMatch(TowerIntegrationSliceAssertions::hasPlaceholder),
                    "Unresolved description placeholder for " + towerType.id() + ": " + resolved.description());
        }
    }

    public static UpgradeExpectation upgrade(TowerType from, String upgradeId, TowerType to) {
        return new UpgradeExpectation(from.id(), upgradeId, to.id());
    }

    private static boolean hasPlaceholder(String line) {
        return line.contains("{ability.") || line.contains("{stat.");
    }

    public record FamilyContract(
            ResourceLocation jobId,
            String globalConfigId,
            List<TowerType> towers,
            Map<String, Integer> tiers,
            List<UpgradeExpectation> upgrades,
            Function<TowerType, Class<? extends Tower>> runtimeType
    ) {
    }

    public record UpgradeExpectation(String fromTowerId, String upgradeId, String toTowerId) {
        public String configKey() {
            return TowerBalanceConfig.upgradeKey(fromTowerId, upgradeId);
        }
    }
}
