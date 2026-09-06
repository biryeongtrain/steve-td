package kim.biryeong.semiontd.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Set;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.job.AdversaryTowerJob;
import kim.biryeong.semiontd.job.EndTowerJob;
import kim.biryeong.semiontd.job.WarlockTowerJob;
import kim.biryeong.semiontd.summon.IncomeSummons;
import kim.biryeong.semiontd.summon.SummonRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.adversary.AdversaryBalance;
import kim.biryeong.semiontd.tower.adversary.AdversaryTowers;
import kim.biryeong.semiontd.tower.augment.AugmentTowers;
import kim.biryeong.semiontd.tower.end.EndTowers;
import kim.biryeong.semiontd.tower.warlock.WarlockTowers;
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
        assertTrue(first.towers().stream().allMatch(tower -> "AUGMENT".equals(tower.availability())
                ? tower.builderId() == null && tower.augmentId() != null
                : tower.builderId() != null && tower.augmentId() == null));
        assertTrue(first.builders().stream().flatMap(entry -> entry.description().stream())
                .noneMatch(WebCatalogExporterTest::hasUnresolvedPlaceholder));
        assertTrue(first.towers().stream().flatMap(entry -> entry.description().stream())
                .noneMatch(WebCatalogExporterTest::hasUnresolvedPlaceholder));
        assertTrue(first.traits().stream().flatMap(entry -> entry.description().stream())
                .noneMatch(WebCatalogExporterTest::hasUnresolvedPlaceholder));
        assertTrue(SummonRegistry.all().stream().flatMap(summon -> summon.description().stream())
                .noneMatch(WebCatalogExporterTest::hasUnresolvedPlaceholder));
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
    void exportsAllAugmentsAndChangesVersionWhenOfferRulesChange() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        SummonConfig summons = SummonConfig.defaultConfig();
        IncomeSummons.reloadBuiltIns(summons);
        WaveConfig waves = WaveConfig.defaultConfig();
        EconomyConfig economy = EconomyConfig.defaultConfig();
        AugmentConfig config = AugmentConfig.defaults();
        var document = WebCatalogExporter.snapshot(1, waves, economy, summons, config);
        assertEquals(44, document.augments().size());
        assertEquals(35, document.augments().stream().filter(augment -> !augment.reserve()).count());
        assertEquals(9, document.augments().stream().filter(WebCatalogExporter.AugmentEntry::reserve).count());
        assertEquals(config.version(), document.augmentVersion());
        assertTrue(document.augments().stream().allMatch(augment -> augment.description() != null
                && !augment.description().isBlank()));
        assertTrue(document.towers().stream().filter(tower -> "AUGMENT".equals(tower.availability()))
                .allMatch(tower -> document.augments().stream().anyMatch(augment -> augment.id().equals(tower.augmentId()))));

        var changedRules = config.toJson();
        changedRules.addProperty("publicPoolEnabled", !config.publicPoolEnabled());
        var changed = WebCatalogExporter.snapshot(1, waves, economy, summons, AugmentConfig.fromJson(changedRules));
        assertNotEquals(document.augmentVersion(), changed.augmentVersion());
        assertNotEquals(document.versionHash(), changed.versionHash());
    }

    @Test
    void exportsNeutralAugmentTowersWithoutInventingBuilderOwnership() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        IncomeSummons.reloadBuiltIns(SummonConfig.defaultConfig());
        var document = WebCatalogExporter.snapshot(1);
        Set<String> expected = AugmentTowers.all().stream().map(type -> type.id())
                .collect(java.util.stream.Collectors.toSet());
        var neutral = document.towers().stream().filter(tower -> "AUGMENT".equals(tower.availability())).toList();
        assertEquals(expected, neutral.stream().map(WebCatalogExporter.TowerEntry::id)
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(neutral.stream().allMatch(tower -> tower.builderId() == null && tower.augmentId() != null));
        assertTrue(document.builders().stream().flatMap(builder -> builder.towerIds().stream())
                .noneMatch(expected::contains));
        assertTrue(document.builders().stream().allMatch(builder -> builder.builderOrigin() != null
                && builder.builderEnabled() != null));
    }

    @Test
    void hashIncludesWaveEconomyAndSummonRulesWithoutDependingOnMapOrder() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        SummonConfig summons = SummonConfig.defaultConfig();
        IncomeSummons.reloadBuiltIns(summons);
        WaveConfig waves = WaveConfig.defaultConfig();
        EconomyConfig economy = EconomyConfig.defaultConfig();
        String initial = WebCatalogExporter.snapshot(1, waves, economy, summons).versionHash();

        WaveConfig changedWaves = new WaveConfig(waves.rounds(), waves.infiniteFromRound() + 1,
                waves.infinite(), waves.infiniteTemplates());
        assertNotEquals(initial, WebCatalogExporter.snapshot(1, changedWaves, economy, summons).versionHash());
        EconomyConfig changedEconomy = new EconomyConfig(economy.startingDiamond() + 1, economy.startingEmerald(),
                economy.startingIncome(), economy.emeraldCap(), economy.emeraldProduction(), economy.towerLimit(),
                economy.killReward(), economy.teamTransfer(), economy.emeraldIncomeBoost());
        assertNotEquals(initial, WebCatalogExporter.snapshot(1, waves, changedEconomy, summons).versionHash());

        var reversed = new LinkedHashMap<String, SummonConfig.SummonDefinition>();
        summons.summons().entrySet().stream().sorted(java.util.Map.Entry.<String, SummonConfig.SummonDefinition>
                comparingByKey().reversed()).forEach(entry -> reversed.put(entry.getKey(), entry.getValue()));
        assertEquals(initial, WebCatalogExporter.snapshot(1, waves, economy, new SummonConfig(reversed)).versionHash());
        reversed.remove(reversed.keySet().iterator().next());
        assertNotEquals(initial, WebCatalogExporter.snapshot(1, waves, economy, new SummonConfig(reversed)).versionHash());
    }

    @Test
    void adversaryFamilyExportsWithOneBuilder() {
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
                .filter(entry -> AdversaryTowerJob.ID.toString().equals(entry.builderId()))
                .toList();
        assertEquals(expectedIds, towers.stream().map(WebCatalogExporter.TowerEntry::id)
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(document.abilities().containsKey(AdversaryBalance.GLOBAL_CONFIG_ID));
    }

    @Test
    void atlantisFamilyExportsWithOneBuilder() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        IncomeSummons.reloadBuiltIns(SummonConfig.defaultConfig());

        WebCatalogExporter.CatalogDocument document = WebCatalogExporter.snapshot(1L);
        Set<String> expectedIds = kim.biryeong.semiontd.tower.atlantis.AtlantisTowers.all().stream()
                .map(type -> type.id())
                .collect(java.util.stream.Collectors.toSet());
        var builder = document.builders().stream()
                .filter(entry -> entry.id().equals(kim.biryeong.semiontd.job.AtlantisTowerJob.ID.toString()))
                .findFirst()
                .orElseThrow();
        assertEquals(expectedIds, Set.copyOf(builder.towerIds()));

        var towers = document.towers().stream()
                .filter(entry -> kim.biryeong.semiontd.job.AtlantisTowerJob.ID.toString().equals(entry.builderId()))
                .toList();
        assertEquals(expectedIds, towers.stream().map(WebCatalogExporter.TowerEntry::id)
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(document.abilities()
                .containsKey(kim.biryeong.semiontd.tower.atlantis.AtlantisBalance.CONFIG_ID));
    }

    @Test
    void endFamilyExportsCompleteOwnedUpgradeGraph() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        IncomeSummons.reloadBuiltIns(SummonConfig.defaultConfig());

        WebCatalogExporter.CatalogDocument document = WebCatalogExporter.snapshot(1L);
        assertExportedFamily(
                document,
                EndTowerJob.ID.toString(),
                EndTowers.all().stream().map(type -> type.id()).collect(java.util.stream.Collectors.toSet()),
                EndTowers.CONFIG_ID,
                Set.of(
                        edge(EndTowers.T1_SHULKER_TOWER.id(), EndTowers.T2_SHULKER_TOWER.id(), EndTowers.T2_SHULKER_TOWER.id()),
                        edge(EndTowers.T2_SHULKER_TOWER.id(), EndTowers.T3_SHULKER_TOWER.id(), EndTowers.T3_SHULKER_TOWER.id()),
                        edge(EndTowers.T1_ENDERMITE_TOWER.id(), EndTowers.T2_ENDERMAN_TOWER.id(), EndTowers.T2_ENDERMAN_TOWER.id()),
                        edge(EndTowers.T2_ENDERMAN_TOWER.id(), EndTowers.T3_END_CRYSTAL_TOWER.id(), EndTowers.T3_END_CRYSTAL_TOWER.id())
                )
        );
    }

    @Test
    void warlockFamilyExportsCompleteOwnedUpgradeGraph() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        IncomeSummons.reloadBuiltIns(SummonConfig.defaultConfig());

        WebCatalogExporter.CatalogDocument document = WebCatalogExporter.snapshot(1L);
        assertExportedFamily(
                document,
                WarlockTowerJob.ID.toString(),
                WarlockTowers.all().stream().map(type -> type.id()).collect(java.util.stream.Collectors.toSet()),
                WarlockTowers.CONFIG_ID,
                Set.of(
                        edge(WarlockTowers.BASE_WARLOCK_TOWER.id(), "ranged_warlock_tower", WarlockTowers.RANGED_WARLOCK_TOWER.id()),
                        edge(WarlockTowers.BASE_WARLOCK_TOWER.id(), "melee_warlock_tower", WarlockTowers.MELEE_WARLOCK_TOWER.id()),
                        edge(WarlockTowers.T1_SLAVE.id(), "t2_slave", WarlockTowers.T2_SLAVE.id()),
                        edge(WarlockTowers.T2_SLAVE.id(), "t3_slave", WarlockTowers.T3_SLAVE.id()),
                        edge(WarlockTowers.T1_RANGED_SLAVE.id(), "t2_ranged_slave", WarlockTowers.T2_RANGED_SLAVE.id()),
                        edge(WarlockTowers.T2_RANGED_SLAVE.id(), "t3_ranged_slave", WarlockTowers.T3_RANGED_SLAVE.id())
                )
        );
    }

    private static void assertExportedFamily(
            WebCatalogExporter.CatalogDocument document,
            String builderId,
            Set<String> expectedTowerIds,
            String globalAbilityId,
            Set<String> expectedUpgradeEdges
    ) {
        WebCatalogExporter.BuilderEntry builder = document.builders().stream()
                .filter(entry -> entry.id().equals(builderId))
                .findFirst()
                .orElseThrow();
        assertEquals(expectedTowerIds, Set.copyOf(builder.towerIds()));
        assertTrue(builder.description().stream().noneMatch(WebCatalogExporterTest::hasUnresolvedPlaceholder));

        var towers = document.towers().stream()
                .filter(entry -> builderId.equals(entry.builderId()))
                .toList();
        assertEquals(expectedTowerIds, towers.stream().map(WebCatalogExporter.TowerEntry::id)
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(towers.stream().flatMap(entry -> entry.description().stream())
                .noneMatch(WebCatalogExporterTest::hasUnresolvedPlaceholder));
        assertTrue(towers.stream().allMatch(entry -> entry.visual() != null
                && entry.visual().entityTypeId() != null));

        Set<String> actualUpgradeEdges = document.upgrades().stream()
                .filter(upgrade -> expectedTowerIds.contains(upgrade.fromTowerId()))
                .map(upgrade -> edge(upgrade.fromTowerId(), upgrade.id(), upgrade.toTowerId()))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(expectedUpgradeEdges, actualUpgradeEdges);
        assertTrue(document.abilities().containsKey(globalAbilityId));
    }

    private static String edge(String fromTowerId, String upgradeId, String toTowerId) {
        return fromTowerId + "|" + upgradeId + "|" + toTowerId;
    }

    private static boolean hasUnresolvedPlaceholder(String line) {
        return line.contains("{ability.") || line.contains("{stat.");
    }
}
