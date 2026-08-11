package kim.biryeong.semiontd.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Set;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.job.AdversaryTowerJob;
import kim.biryeong.semiontd.summon.IncomeSummons;
import kim.biryeong.semiontd.summon.SummonRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.adversary.AdversaryBalance;
import kim.biryeong.semiontd.tower.adversary.AdversaryTowers;
import kim.biryeong.semiontd.trait.TraitRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WebCatalogExporterTest {
    @TempDir
    Path tempDir;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void restoreDefaults() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        IncomeSummons.reloadBuiltIns(SummonConfig.defaultConfig());
        WebCatalogExporter.clearCurrentVersion();
    }

    @Test
    void hashIsStableAndExportUsesRuntimeCatalogAndUpgradeCosts() throws Exception {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        ProductionTowerCatalogs.reloadBuiltIns(defaults);
        IncomeSummons.reloadBuiltIns(SummonConfig.defaultConfig());

        WebCatalogExporter.CatalogDocument first = WebCatalogExporter.snapshot(1L);
        WebCatalogExporter.CatalogDocument second = WebCatalogExporter.snapshot(2L);
        assertEquals(first.versionHash(), second.versionHash());
        assertEquals(ProductionTowerCatalog.all().size(), first.towers().size());
        assertEquals(TraitRegistry.all().size(), first.traits().size());
        assertEquals(SummonRegistry.all().size(), first.summons().size());
        assertTrue(first.traits().stream().allMatch(trait -> !trait.displayName().equals(trait.id())));
        assertTrue(first.summons().stream().allMatch(summon -> !summon.displayName().equals(summon.id())));
        assertTrue(first.towers().stream().allMatch(tower -> tower.builderId() != null));
        first.upgrades().forEach(upgrade -> {
            var sourceType = ProductionTowerCatalog.find(upgrade.fromTowerId()).orElseThrow().type();
            var option = ProductionTowerCatalog.upgrade(sourceType, upgrade.id()).orElseThrow();
            assertEquals(
                    TowerBalanceRuntime.upgradeCost(sourceType, upgrade.id(), option.mineralCost()),
                    upgrade.mineralCost()
            );
        });

        WebCatalogExporter.CatalogDocument exported = WebCatalogExporter.export(tempDir);
        assertTrue(Files.exists(tempDir.resolve("web_catalog/current.json")));
        assertTrue(Files.exists(tempDir.resolve("web_catalog/versions/" + exported.versionHash() + ".json")));
        assertEquals(exported.versionHash(), WebCatalogExporter.currentVersion().orElseThrow());

        LinkedHashMap<String, TowerBalanceConfig.TowerStats> changedTowers = new LinkedHashMap<>(defaults.towers());
        String towerId = changedTowers.keySet().iterator().next();
        TowerBalanceConfig.TowerStats current = changedTowers.get(towerId);
        changedTowers.put(towerId, new TowerBalanceConfig.TowerStats(
                current.mineralCost(),
                current.maxHealth() + 1.0,
                current.range(),
                current.damage(),
                current.attackIntervalTicks(),
                current.aggroPriority()
        ));
        ProductionTowerCatalogs.reloadBuiltIns(new TowerBalanceConfig(
                changedTowers,
                defaults.upgradeCosts(),
                defaults.abilities(),
                defaults.illusionCloneQueue(),
                defaults.villagerAdv(),
                defaults.schemaVersion()
        ));
        assertNotEquals(first.versionHash(), WebCatalogExporter.snapshot(3L).versionHash());
    }

    @Test
    void adversaryFamilyExportsWithOneBuilderAndResolvedDescriptions() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        IncomeSummons.reloadBuiltIns(SummonConfig.defaultConfig());

        WebCatalogExporter.CatalogDocument document = WebCatalogExporter.snapshot(1L);
        Set<String> expectedIds = AdversaryTowers.all().stream()
                .map(type -> type.id())
                .collect(java.util.stream.Collectors.toSet());
        var builder = document.builders().stream()
                .filter(entry -> entry.id().equals(AdversaryTowerJob.ID.toString()))
                .findFirst()
                .orElseThrow();
        assertEquals(expectedIds, Set.copyOf(builder.towerIds()));

        var towers = document.towers().stream()
                .filter(entry -> entry.builderId().equals(AdversaryTowerJob.ID.toString()))
                .toList();
        assertEquals(expectedIds, towers.stream().map(WebCatalogExporter.TowerEntry::id)
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(towers.stream().flatMap(entry -> entry.description().stream())
                .noneMatch(line -> line.contains("{ability.") || line.contains("{stat.")));
        assertTrue(document.abilities().containsKey(AdversaryBalance.GLOBAL_CONFIG_ID));
    }
}
