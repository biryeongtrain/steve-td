package kim.biryeong.semiontd.tower.developer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.job.DeveloperTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.web.WebCatalogExporter;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Delivery-surface checks for the 개발자 builder.
 *
 * <p>{@link DeveloperTowerTest} covers the mechanics in isolation. These cover the seams a new
 * family most often breaks and which no other test in the suite would notice: catalog ownership,
 * the web export, and what happens to a server whose {@code tower_balance.json} predates the
 * family entirely — the deployed case, where the file on disk has no developer keys at all.
 */
class DeveloperIntegrationTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void reloadCatalogs() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    /**
     * {@code WebCatalogExporter.snapshot} throws when a tower is claimed by zero or several
     * builders, so a successful export is the real proof of the ownership invariant.
     */
    @Test
    void theWebExportAcceptsTheFamilyAndAssignsEveryTowerToItAlone() {
        WebCatalogExporter.CatalogDocument document = WebCatalogExporter.snapshot(0L);
        assertNotNull(document);

        WebCatalogExporter.BuilderEntry developer = document.builders().stream()
                .filter(builder -> builder.id().equals(DeveloperTowerJob.ID.toString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("개발자 빌더가 웹 카탈로그에 없습니다."));

        Set<String> exported = Set.copyOf(developer.towerIds());
        Set<String> expected = DeveloperTowers.all().stream()
                .map(TowerType::id)
                .collect(Collectors.toUnmodifiableSet());
        assertEquals(expected, exported, "개발자 빌더가 소유한 타워 목록이 12종과 일치해야 합니다.");

        for (WebCatalogExporter.TowerEntry entry : document.towers()) {
            if (expected.contains(entry.id())) {
                assertEquals(DeveloperTowerJob.ID.toString(), entry.builderId(),
                        entry.id() + " 이(가) 다른 빌더에게 귀속되었습니다.");
            } else {
                assertFalse(DeveloperTowerJob.ID.toString().equals(entry.builderId()),
                        entry.id() + " 은(는) 개발자 빌더의 타워가 아닙니다.");
            }
        }
    }

    /**
     * Descriptions are rendered verbatim into the dialog and the web catalog, so a template token
     * that never got wired to an ability value ships as literal braces to the player.
     */
    @Test
    void noDeveloperDescriptionShipsAnUnresolvedTemplateToken() {
        for (TowerType type : DeveloperTowers.all()) {
            TowerType resolved = TowerBalanceRuntime.resolve(type);
            for (String line : resolved.description()) {
                assertFalse(line.contains("{") || line.contains("}"),
                        resolved.id() + " 설명에 치환되지 않은 자리표시자가 있습니다: " + line);
            }
        }
        for (DeveloperBug bug : DeveloperBug.values()) {
            for (String line : bug.description()) {
                assertFalse(line.contains("{") || line.contains("}"),
                        bug.key() + " 버그 설명에 자리표시자가 있습니다: " + line);
            }
        }
    }

    @Test
    void workbenchDescriptionsRenderBaseTowerAndGrowthSlotTerms() {
        TowerBalanceConfig base = TowerBalanceConfig.defaultConfig();
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(base.abilities());
        LinkedHashMap<String, Double> global = new LinkedHashMap<>(abilities.get(DeveloperBalance.CONFIG_ID));
        global.put("basePatchSlots", 2.0);
        global.put("patchSlotsPerTowers", 3.0);
        abilities.put(DeveloperBalance.CONFIG_ID, global);
        abilities.put(DeveloperTowers.WORKBENCH.id(), Map.of("patchSlots", 5.0));
        TowerBalanceRuntime.apply(new TowerBalanceConfig(base.towers(), base.upgradeCosts(), abilities));

        String description = String.join(" ", TowerBalanceRuntime.resolve(DeveloperTowers.WORKBENCH).description());
        assertTrue(description.contains("기본 <yellow>2건</yellow>"));
        assertTrue(description.contains("작업대 <yellow>5건</yellow>"));
        assertTrue(description.contains("<yellow>3기</yellow>마다 1건"));
    }

    /**
     * The deployed case: a live {@code tower_balance.json} written before this family existed.
     *
     * <p>{@code SemionConfigLoader} runs {@code withMissingDefaults} over the loaded file, so every
     * developer key has to arrive from the bundled defaults. Without this the family would load
     * with zero costs and no abilities on any existing server.
     */
    @Test
    void aServerConfigPredatingTheFamilyIsBackfilledFromTheBundledDefaults() {
        TowerBalanceConfig shipped = TowerBalanceConfig.defaultConfig();
        TowerBalanceConfig legacy = withoutDeveloperEntries(shipped);

        assertTrue(legacy.towers().keySet().stream().noneMatch(id -> id.startsWith("developer_")),
                "테스트 전제: 레거시 설정에는 개발자 항목이 없어야 합니다.");

        TowerBalanceConfig merged = legacy.withMissingDefaults(shipped);

        for (TowerType type : DeveloperTowers.all()) {
            assertEquals(shipped.towers().get(type.id()), merged.towers().get(type.id()),
                    type.id() + " 스탯이 백필되지 않았습니다.");
        }
        assertEquals(
                shipped.upgradeCost(DeveloperTowers.BETA.id(), DeveloperTowers.RELEASE.id(), -1L),
                merged.upgradeCost(DeveloperTowers.BETA.id(), DeveloperTowers.RELEASE.id(), -1L),
                "승급 비용이 백필되지 않았습니다."
        );
        assertEquals(
                DeveloperBalance.PATCH_DIMINISHING,
                merged.ability(DeveloperBalance.CONFIG_ID, "patchDiminishing", Double.NaN),
                1.0e-9,
                "전역 능력 값이 백필되지 않았습니다."
        );

        // The merged result still has to survive validation, which is what runtime application does.
        TowerBalanceRuntime.apply(merged);
        assertEquals(290L,
                DeveloperTowers.ALPHA.mineralCost()
                        + TowerBalanceRuntime.upgradeCost(DeveloperTowers.ALPHA, DeveloperTowers.BETA.id())
                        + TowerBalanceRuntime.upgradeCost(DeveloperTowers.BETA, DeveloperTowers.RELEASE.id()));
    }

    /** Backfill must fill gaps, never overwrite a value an operator deliberately tuned. */
    @Test
    void backfillDoesNotOverwriteAValueTheServerAlreadyTuned() {
        TowerBalanceConfig shipped = TowerBalanceConfig.defaultConfig();

        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(shipped.abilities());
        LinkedHashMap<String, Double> tuned = new LinkedHashMap<>(abilities.get(DeveloperTowers.RELEASE.id()));
        tuned.put("patchScale", 9.5);
        abilities.put(DeveloperTowers.RELEASE.id(), tuned);

        LinkedHashMap<String, Long> upgrades = new LinkedHashMap<>(shipped.upgradeCosts());
        upgrades.put(
                TowerBalanceConfig.upgradeKey(DeveloperTowers.ALPHA.id(), DeveloperTowers.BETA.id()),
                777L
        );

        TowerBalanceConfig operatorEdited = new TowerBalanceConfig(
                new LinkedHashMap<>(shipped.towers()), upgrades, abilities);
        TowerBalanceConfig merged = operatorEdited.withMissingDefaults(shipped);

        assertEquals(9.5, merged.ability(DeveloperTowers.RELEASE.id(), "patchScale", Double.NaN), 1.0e-9,
                "운영자가 조정한 능력 값을 덮어쓰면 안 됩니다.");
        assertEquals(777L,
                merged.upgradeCost(DeveloperTowers.ALPHA.id(), DeveloperTowers.BETA.id(), -1L),
                "운영자가 조정한 승급 비용을 덮어쓰면 안 됩니다.");
    }

    @Test
    void partialDeveloperGlobalConfigBackfillsSlotRules() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceConfig partial = new TowerBalanceConfig(
                Map.of(),
                Map.of(),
                Map.of(DeveloperBalance.CONFIG_ID, Map.of("patchAttack", 0.20))
        );

        TowerBalanceConfig merged = partial.withMissingDefaults(defaults);

        assertEquals(0.20, merged.ability(DeveloperBalance.CONFIG_ID, "patchAttack", -1.0), 1.0e-9);
        assertEquals((double) DeveloperBalance.BASE_PATCH_SLOTS,
                merged.ability(DeveloperBalance.CONFIG_ID, "basePatchSlots", -1.0), 1.0e-9);
        assertEquals((double) DeveloperBalance.PATCH_SLOTS_PER_TOWERS,
                merged.ability(DeveloperBalance.CONFIG_ID, "patchSlotsPerTowers", -1.0), 1.0e-9);
    }

    @Test
    void developerConfigRejectsInvalidRatiosCountsAndRadius() {
        assertThrows(IllegalArgumentException.class,
                () -> withDeveloperGlobal("accuracyOptimizationCost", 1.01).validateForRuntime());
        assertThrows(IllegalArgumentException.class,
                () -> withDeveloperGlobal("basePatchSlots", 1.5).validateForRuntime());
        assertThrows(IllegalArgumentException.class,
                () -> withDeveloperGlobal("testBuildAuraRadius", 0.0).validateForRuntime());
        assertThrows(IllegalArgumentException.class,
                () -> withDeveloperGlobal("garbage_collectionBugPrimary", 1.01).validateForRuntime());
    }

    /** Every catalog entry the family registers must be constructible through the real factory. */
    @Test
    void everyRegisteredEntryBuildsADeveloperTower() {
        for (TowerType type : DeveloperTowers.all()) {
            ProductionTowerCatalog.CatalogEntry entry = ProductionTowerCatalog.find(type.id())
                    .orElseThrow(() -> new AssertionError(type.id() + " 이(가) 카탈로그에 없습니다."));
            assertTrue(
                    entry.starter() == (DeveloperTowers.tier(type) == 1),
                    type.id() + " 의 스타터 여부가 티어와 맞지 않습니다."
            );
        }

        List<TowerType> starters = DeveloperTowers.all().stream()
                .filter(type -> DeveloperTowers.tier(type) == 1)
                .toList();
        assertEquals(4, starters.size(), "스타터는 알파·작업대·테스터·프로파일러 넷이어야 합니다.");
    }

    private static TowerBalanceConfig withoutDeveloperEntries(TowerBalanceConfig source) {
        LinkedHashMap<String, TowerBalanceConfig.TowerStats> towers = new LinkedHashMap<>();
        source.towers().forEach((id, stats) -> {
            if (!id.startsWith("developer_")) {
                towers.put(id, stats);
            }
        });
        LinkedHashMap<String, Long> upgrades = new LinkedHashMap<>();
        source.upgradeCosts().forEach((key, cost) -> {
            if (!key.startsWith("developer_")) {
                upgrades.put(key, cost);
            }
        });
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>();
        source.abilities().forEach((id, values) -> {
            if (!id.startsWith("developer_")) {
                abilities.put(id, values);
            }
        });
        return new TowerBalanceConfig(towers, upgrades, abilities);
    }

    private static TowerBalanceConfig withDeveloperGlobal(String key, double value) {
        TowerBalanceConfig base = TowerBalanceConfig.defaultConfig();
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(base.abilities());
        LinkedHashMap<String, Double> global = new LinkedHashMap<>(abilities.get(DeveloperBalance.CONFIG_ID));
        global.put(key, value);
        abilities.put(DeveloperBalance.CONFIG_ID, global);
        return new TowerBalanceConfig(base.towers(), base.upgradeCosts(), abilities);
    }
}
