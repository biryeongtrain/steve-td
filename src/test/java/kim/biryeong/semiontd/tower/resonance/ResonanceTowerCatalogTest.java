package kim.biryeong.semiontd.tower.resonance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig.TowerStats;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.ResonanceTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ResonanceTowerCatalogTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void resetTowerBalance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void defaultBalanceConfigIncludesResonanceTowerStatsAndAbilities() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();

        assertTrue(config.towers().containsKey(ResonanceTowers.FOCUS_CRYSTAL.id()));
        assertTrue(config.towers().containsKey(ResonanceTowers.FOCUS_PRISM.id()));
        assertTrue(config.towers().containsKey(ResonanceTowers.FOCUS_CORE.id()));
        assertTrue(config.towers().containsKey(ResonanceTowers.WAVE_CRYSTAL.id()));
        assertTrue(config.towers().containsKey(ResonanceTowers.WAVE_PRISM.id()));
        assertTrue(config.towers().containsKey(ResonanceTowers.WAVE_CORE.id()));
        assertTrue(config.towers().containsKey(ResonanceTowers.FROST_CRYSTAL.id()));
        assertTrue(config.towers().containsKey(ResonanceTowers.FROST_PRISM.id()));
        assertTrue(config.towers().containsKey(ResonanceTowers.FROST_CORE.id()));
        assertTrue(config.towers().containsKey(ResonanceTowers.AMPLIFY_CRYSTAL.id()));
        assertTrue(config.towers().containsKey(ResonanceTowers.AMPLIFY_PRISM.id()));
        assertTrue(config.towers().containsKey(ResonanceTowers.AMPLIFY_CORE.id()));
        assertEquals(new TowerStats(50L, 45.0, 8.0, 8.0, 20, 5), config.towers().get(ResonanceTowers.FOCUS_CRYSTAL.id()));
        assertEquals(new TowerStats(180L, 80.0, 6.5, 15.0, 15, 8), config.towers().get(ResonanceTowers.FOCUS_PRISM.id()));
        assertEquals(new TowerStats(320L, 95.0, 7.0, 28.0, 16, 12), config.towers().get(ResonanceTowers.FOCUS_CORE.id()));
        assertEquals(new TowerStats(45L, 70.0, 7.0, 8.0, 16, 10), config.towers().get(ResonanceTowers.WAVE_CRYSTAL.id()));
        assertEquals(new TowerStats(50L, 60.0, 5.5, 15.0, 14, 12), config.towers().get(ResonanceTowers.WAVE_PRISM.id()));
        assertEquals(new TowerStats(300L, 80.0, 8.0, 22.0, 12, 15), config.towers().get(ResonanceTowers.WAVE_CORE.id()));
        assertEquals(new TowerStats(45L, 50.0, 7.0, 8.0, 20, 0), config.towers().get(ResonanceTowers.FROST_CRYSTAL.id()));
        assertEquals(new TowerStats(150L, 75.0, 8.0, 10.0, 16, 2), config.towers().get(ResonanceTowers.FROST_PRISM.id()));
        assertEquals(new TowerStats(280L, 105.0, 12.0, 16.0, 12, 5), config.towers().get(ResonanceTowers.FROST_CORE.id()));
        assertEquals(new TowerStats(45L, 100.0, 5.0, 8.0, 15, 40), config.towers().get(ResonanceTowers.AMPLIFY_CRYSTAL.id()));
        assertEquals(new TowerStats(200L, 200.0, 5.5, 11.0, 12, 45), config.towers().get(ResonanceTowers.AMPLIFY_PRISM.id()));
        assertEquals(new TowerStats(350L, 450.0, 6.0, 20.0, 10, 50), config.towers().get(ResonanceTowers.AMPLIFY_CORE.id()));
        assertEquals(60, config.upgradeCost(ResonanceTowers.FOCUS_CRYSTAL.id(), ResonanceTowers.FOCUS_PRISM.id(), -1));
        assertEquals(180, config.upgradeCost(ResonanceTowers.FOCUS_PRISM.id(), ResonanceTowers.FOCUS_CORE.id(), -1));
        assertEquals(60, config.upgradeCost(ResonanceTowers.WAVE_CRYSTAL.id(), ResonanceTowers.WAVE_PRISM.id(), -1));
        assertEquals(200, config.upgradeCost(ResonanceTowers.WAVE_PRISM.id(), ResonanceTowers.WAVE_CORE.id(), -1));
        assertEquals(60, config.upgradeCost(ResonanceTowers.FROST_CRYSTAL.id(), ResonanceTowers.FROST_PRISM.id(), -1));
        assertEquals(220, config.upgradeCost(ResonanceTowers.FROST_PRISM.id(), ResonanceTowers.FROST_CORE.id(), -1));
        assertEquals(60, config.upgradeCost(ResonanceTowers.AMPLIFY_CRYSTAL.id(), ResonanceTowers.AMPLIFY_PRISM.id(), -1));
        assertEquals(220, config.upgradeCost(ResonanceTowers.AMPLIFY_PRISM.id(), ResonanceTowers.AMPLIFY_CORE.id(), -1));
        assertEquals(1.0, config.ability(ResonanceTowers.FOCUS_CRYSTAL.id(), "linkRange", -1.0), 0.0001);
        assertEquals(1.0, config.ability(ResonanceTowers.FOCUS_CORE.id(), "linkRange", -1.0), 0.0001);
        assertEquals(6.0, config.ability(ResonanceTowers.FOCUS_CORE.id(), "maxLinksPerTower", -1.0), 0.0001);
        assertEquals(1.0, config.ability(ResonanceTowers.FOCUS_CORE.id(), "level1RequiredLinks", -1.0), 0.0001);
        assertEquals(3.0, config.ability(ResonanceTowers.FOCUS_CORE.id(), "level2RequiredLinks", -1.0), 0.0001);
        assertEquals(5.0, config.ability(ResonanceTowers.FOCUS_CORE.id(), "level3RequiredLinks", -1.0), 0.0001);
        assertEquals(3.0, config.ability(ResonanceTowers.FOCUS_CRYSTAL.id(), "maxResonanceLevel", -1.0), 0.0001);
        assertEquals(3.0, config.ability(ResonanceTowers.FOCUS_PRISM.id(), "maxResonanceLevel", -1.0), 0.0001);
        assertEquals(3.0, config.ability(ResonanceTowers.FOCUS_CORE.id(), "maxResonanceLevel", -1.0), 0.0001);
        assertEquals(0.20, config.ability(ResonanceTowers.FOCUS_CORE.id(), "focusLevel1AttackSpeedBonus", -1.0), 0.0001);
        assertEquals(0.40, config.ability(ResonanceTowers.FOCUS_CORE.id(), "focusLevel2AttackSpeedBonus", -1.0), 0.0001);
        assertEquals(0.80, config.ability(ResonanceTowers.FOCUS_CORE.id(), "focusLevel3DamageBonus", -1.0), 0.0001);
        assertEquals(1.00, config.ability(ResonanceTowers.WAVE_CORE.id(), "waveLevel3SplashDamageRatio", -1.0), 0.0001);
        assertEquals(1.75, config.ability(ResonanceTowers.WAVE_CORE.id(), "wavePulseDamageRatio", -1.0), 0.0001);
        assertEquals(0.40, config.ability(ResonanceTowers.FROST_CORE.id(), "frostLevel3SlowMagnitude", -1.0), 0.0001);
        assertEquals(0.40, config.ability(ResonanceTowers.FROST_CORE.id(), "frostLevel3AttackSpeedReductionMagnitude", -1.0), 0.0001);
        assertEquals(0.35, config.ability(ResonanceTowers.FROST_CORE.id(), "frostLevel2AuraDamageVsSlowedBonus", -1.0), 0.0001);
        assertEquals(1.00, config.ability(ResonanceTowers.FROST_CORE.id(), "frostLevel3AuraDamageVsSlowedBonus", -1.0), 0.0001);
        assertEquals(1.0, config.ability(ResonanceTowers.FROST_CORE.id(), "frostAuraRange", -1.0), 0.0001);
        assertEquals(0.50, config.ability(ResonanceTowers.FROST_CORE.id(), "frostPulseAttackSpeedReductionMagnitude", -1.0), 0.0001);
        assertEquals(0.35, config.ability(ResonanceTowers.AMPLIFY_CORE.id(), "bloomLevel3AuraAttackSpeedBonus", -1.0), 0.0001);
    }

    @Test
    void resonanceTowerIdsUseTierPrefixesForConfigEditing() {
        assertEquals("t1_resonance_focus_moobloom", ResonanceTowers.FOCUS_CRYSTAL.id());
        assertEquals("t2_resonance_focus_moobloom", ResonanceTowers.FOCUS_PRISM.id());
        assertEquals("t3_resonance_focus_moobloom", ResonanceTowers.FOCUS_CORE.id());
        assertEquals("t1_resonance_wave_moobloom", ResonanceTowers.WAVE_CRYSTAL.id());
        assertEquals("t2_resonance_wave_moobloom", ResonanceTowers.WAVE_PRISM.id());
        assertEquals("t3_resonance_wave_moobloom", ResonanceTowers.WAVE_CORE.id());
        assertEquals("t1_resonance_frost_moobloom", ResonanceTowers.FROST_CRYSTAL.id());
        assertEquals("t2_resonance_frost_moobloom", ResonanceTowers.FROST_PRISM.id());
        assertEquals("t3_resonance_frost_moobloom", ResonanceTowers.FROST_CORE.id());
        assertEquals("t1_resonance_amplify_moobloom", ResonanceTowers.AMPLIFY_CRYSTAL.id());
        assertEquals("t2_resonance_amplify_moobloom", ResonanceTowers.AMPLIFY_PRISM.id());
        assertEquals("t3_resonance_amplify_moobloom", ResonanceTowers.AMPLIFY_CORE.id());
    }

    @Test
    void resonanceTowersUseMoobloomNamesAndFloweryVariants() {
        assertEquals("민들레 무블룸", ResonanceTowers.FOCUS_CRYSTAL.displayName());
        assertEquals("알리움 무블룸", ResonanceTowers.AMPLIFY_CRYSTAL.displayName());
        assertEquals("라일락 무블룸", ResonanceTowers.AMPLIFY_PRISM.displayName());
        assertEquals("무블룸 빌더", new ResonanceTowerJob().displayName().getString());

        assertMoobloomVariant(ResonanceTowers.FOCUS_CRYSTAL, "dandelion");
        assertMoobloomVariant(ResonanceTowers.FOCUS_PRISM, "sunflower");
        assertMoobloomVariant(ResonanceTowers.FOCUS_CORE, "orange_tulip");
        assertMoobloomVariant(ResonanceTowers.WAVE_CRYSTAL, "cornflower");
        assertMoobloomVariant(ResonanceTowers.WAVE_PRISM, "blue_orchid");
        assertMoobloomVariant(ResonanceTowers.WAVE_CORE, "azure_bluet");
        assertMoobloomVariant(ResonanceTowers.FROST_CRYSTAL, "lily_of_the_valley");
        assertMoobloomVariant(ResonanceTowers.FROST_PRISM, "white_tulip");
        assertMoobloomVariant(ResonanceTowers.FROST_CORE, "oxeye_daisy");
        assertMoobloomVariant(ResonanceTowers.AMPLIFY_CRYSTAL, "allium");
        assertMoobloomVariant(ResonanceTowers.AMPLIFY_PRISM, "lilac");
        assertMoobloomVariant(ResonanceTowers.AMPLIFY_CORE, "peony");
    }

    @Test
    void moobloomDescriptionsUseTowerDefenseRoleTextAndAvoidThematicFlowerBedWording() {
        var focusDescription = TowerBalanceRuntime.resolve(ResonanceTowers.FOCUS_CRYSTAL).description();
        var waveDescription = TowerBalanceRuntime.resolve(ResonanceTowers.WAVE_CRYSTAL).description();
        var frostDescription = TowerBalanceRuntime.resolve(ResonanceTowers.FROST_CRYSTAL).description();
        var bloomDescription = TowerBalanceRuntime.resolve(ResonanceTowers.AMPLIFY_CRYSTAL).description();
        var playerFacingLines = new java.util.ArrayList<String>();
        playerFacingLines.addAll(focusDescription);
        playerFacingLines.addAll(waveDescription);
        playerFacingLines.addAll(bloomDescription);
        new ResonanceTowerJob().description().forEach(line -> playerFacingLines.add(line.getString()));

        assertTrue(focusDescription.stream().anyMatch(line -> line.contains("단일 타겟")));
        assertTrue(focusDescription.stream().anyMatch(line -> line.contains("현재 해금") && line.contains("3단계")));
        assertTrue(focusDescription.stream().anyMatch(line -> line.contains("공명 1단계")));
        assertTrue(focusDescription.stream().anyMatch(line -> line.contains("공명 2단계")));
        assertTrue(focusDescription.stream().anyMatch(line -> line.contains("공명 3단계")));
        assertTrue(waveDescription.stream().anyMatch(line -> line.contains("공명 3단계")));
        assertTrue(frostDescription.stream().anyMatch(line -> line.contains("공명 3단계")));
        assertTrue(bloomDescription.stream().anyMatch(line -> line.contains("공명 3단계")));
        assertTrue(waveDescription.stream().anyMatch(line -> line.contains("범위 공격")));
        assertTrue(bloomDescription.stream().anyMatch(line -> line.contains("저항 효과")));
        assertFalse(playerFacingLines.stream().anyMatch(line -> line.contains("꽃밭")));
        assertFalse(playerFacingLines.stream().anyMatch(line -> line.contains("꽃향")));
        assertFalse(playerFacingLines.stream().anyMatch(line -> line.contains("증폭")));
    }

    @Test
    void allTowerTiersUseLiveMaximumResonanceLevel() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        ResonanceTower focusT1 = resonanceTower(ResonanceTowers.FOCUS_CRYSTAL, playerId, 0);
        ResonanceTower focusT2 = resonanceTower(ResonanceTowers.FOCUS_PRISM, playerId, 0);
        ResonanceTower focusT3 = resonanceTower(ResonanceTowers.FOCUS_CORE, playerId, 0);
        ResonanceTower wave = resonanceTower(ResonanceTowers.WAVE_CRYSTAL, playerId, 1, 0);
        ResonanceTower frost = resonanceTower(ResonanceTowers.FROST_CRYSTAL, playerId, -1, 0);
        ResonanceTower bloom = resonanceTower(ResonanceTowers.AMPLIFY_CRYSTAL, playerId, 0, 1);
        ResonanceTower waveT2 = resonanceTower(ResonanceTowers.WAVE_PRISM, playerId, 0, -1);
        ResonanceTower frostT2 = resonanceTower(ResonanceTowers.FROST_PRISM, playerId, 1, 1);

        var nearbySpecies = java.util.List.of(wave, frost, bloom, waveT2, frostT2);

        ResonanceService.refresh(withLinks(focusT1, nearbySpecies));
        ResonanceService.refresh(withLinks(focusT2, nearbySpecies));
        ResonanceService.refresh(withLinks(focusT3, nearbySpecies));

        assertEquals(3, focusT1.resonanceLevel());
        assertEquals(3, focusT2.resonanceLevel());
        assertEquals(3, focusT3.resonanceLevel());
        assertEquals(5, focusT3.resonanceLinks());
    }

    @Test
    void towerSaleRefreshCanLowerResonanceWithoutChangingNormalRetention() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        ResonanceTower focus = resonanceTower(ResonanceTowers.FOCUS_CORE, playerId, 0);
        ResonanceTower wave = resonanceTower(ResonanceTowers.WAVE_CRYSTAL, playerId, 1, 0);
        ResonanceTower frost = resonanceTower(ResonanceTowers.FROST_CRYSTAL, playerId, -1, 0);
        ResonanceTower bloom = resonanceTower(ResonanceTowers.AMPLIFY_CRYSTAL, playerId, 0, 1);
        var towers = withLinks(focus, java.util.List.of(wave, frost, bloom));

        ResonanceService.refresh(towers);

        assertEquals(2, focus.resonanceLevel());
        assertEquals(3, focus.resonanceLinks());

        towers.remove(bloom);
        ResonanceService.refresh(towers);

        assertEquals(2, focus.resonanceLevel());
        assertEquals(3, focus.resonanceLinks());

        ResonanceService.refreshAfterTowerSale(towers);

        assertEquals(1, focus.resonanceLevel());
        assertEquals(2, focus.resonanceLinks());
    }

    @Test
    void resonanceTowerJobAllowsOnlyResonanceTowers() {
        ResonanceTowerJob job = new ResonanceTowerJob();

        assertTrue(job.canUseTower(null, ResonanceTowers.FOCUS_CRYSTAL));
        assertTrue(job.canUseTower(null, ResonanceTowers.FOCUS_PRISM));
        assertTrue(job.canUseTower(null, ResonanceTowers.FOCUS_CORE));
        assertTrue(job.canUseTower(null, ResonanceTowers.WAVE_CRYSTAL));
        assertTrue(job.canUseTower(null, ResonanceTowers.WAVE_PRISM));
        assertTrue(job.canUseTower(null, ResonanceTowers.WAVE_CORE));
        assertTrue(job.canUseTower(null, ResonanceTowers.FROST_CRYSTAL));
        assertTrue(job.canUseTower(null, ResonanceTowers.FROST_PRISM));
        assertTrue(job.canUseTower(null, ResonanceTowers.FROST_CORE));
        assertTrue(job.canUseTower(null, ResonanceTowers.AMPLIFY_CRYSTAL));
        assertTrue(job.canUseTower(null, ResonanceTowers.AMPLIFY_PRISM));
        assertTrue(job.canUseTower(null, ResonanceTowers.AMPLIFY_CORE));
    }

    @Test
    void builtInCatalogRegistersResonanceStartersAndUpgradeTree() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());

        assertTrue(ProductionTowerCatalog.find(ResonanceTowers.FOCUS_CRYSTAL.id()).orElseThrow().starter());
        assertTrue(ProductionTowerCatalog.find(ResonanceTowers.WAVE_CRYSTAL.id()).orElseThrow().starter());
        assertTrue(ProductionTowerCatalog.find(ResonanceTowers.FROST_CRYSTAL.id()).orElseThrow().starter());
        assertTrue(ProductionTowerCatalog.find(ResonanceTowers.AMPLIFY_CRYSTAL.id()).orElseThrow().starter());
        assertFalse(ProductionTowerCatalog.find(ResonanceTowers.FOCUS_PRISM.id()).orElseThrow().starter());
        assertEquals(2, ProductionTowerCatalog.find(ResonanceTowers.FOCUS_PRISM.id()).orElseThrow().tier());
        assertEquals(3, ProductionTowerCatalog.find(ResonanceTowers.FOCUS_CORE.id()).orElseThrow().tier());
        assertEquals(ResonanceTowers.FOCUS_PRISM.id(), ProductionTowerCatalog.upgrade(ResonanceTowers.FOCUS_CRYSTAL, ResonanceTowers.FOCUS_PRISM.id()).orElseThrow().targetType().id());
        assertEquals(60, ProductionTowerCatalog.upgrade(ResonanceTowers.FOCUS_CRYSTAL, ResonanceTowers.FOCUS_PRISM.id()).orElseThrow().mineralCost());
        assertEquals(ResonanceTowers.FOCUS_CORE.id(), ProductionTowerCatalog.upgrade(ResonanceTowers.FOCUS_PRISM, ResonanceTowers.FOCUS_CORE.id()).orElseThrow().targetType().id());
        assertEquals(ResonanceTowers.WAVE_PRISM.id(), ProductionTowerCatalog.upgrade(ResonanceTowers.WAVE_CRYSTAL, ResonanceTowers.WAVE_PRISM.id()).orElseThrow().targetType().id());
        assertEquals(ResonanceTowers.WAVE_CORE.id(), ProductionTowerCatalog.upgrade(ResonanceTowers.WAVE_PRISM, ResonanceTowers.WAVE_CORE.id()).orElseThrow().targetType().id());
        assertEquals(ResonanceTowers.FROST_PRISM.id(), ProductionTowerCatalog.upgrade(ResonanceTowers.FROST_CRYSTAL, ResonanceTowers.FROST_PRISM.id()).orElseThrow().targetType().id());
        assertEquals(ResonanceTowers.FROST_CORE.id(), ProductionTowerCatalog.upgrade(ResonanceTowers.FROST_PRISM, ResonanceTowers.FROST_CORE.id()).orElseThrow().targetType().id());
        assertEquals(ResonanceTowers.AMPLIFY_PRISM.id(), ProductionTowerCatalog.upgrade(ResonanceTowers.AMPLIFY_CRYSTAL, ResonanceTowers.AMPLIFY_PRISM.id()).orElseThrow().targetType().id());
        assertEquals(ResonanceTowers.AMPLIFY_CORE.id(), ProductionTowerCatalog.upgrade(ResonanceTowers.AMPLIFY_PRISM, ResonanceTowers.AMPLIFY_CORE.id()).orElseThrow().targetType().id());
    }

    @Test
    void resonanceDescriptionsRenderCurrentConfigValues() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        abilities.put(ResonanceTowers.FOCUS_CORE.id(), Map.ofEntries(
                Map.entry("linkRange", 7.0),
                Map.entry("maxLinksPerTower", 6.0),
                Map.entry("maxResonanceLevel", 3.0),
                Map.entry("level1RequiredLinks", 2.0),
                Map.entry("level2RequiredLinks", 4.0),
                Map.entry("level3RequiredLinks", 6.0),
                Map.entry("focusLevel1AttackSpeedBonus", 0.11),
                Map.entry("focusLevel2AttackSpeedBonus", 0.22),
                Map.entry("focusLevel2DamageBonus", 0.13),
                Map.entry("focusLevel3AttackSpeedBonus", 0.33),
                Map.entry("focusLevel3DamageBonus", 0.17),
                Map.entry("focusStrikeEveryAttacks", 4.0),
                Map.entry("focusStrikeDamageRatio", 0.44)
        ));
        TowerBalanceRuntime.apply(new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities));

        var description = TowerBalanceRuntime.resolve(ResonanceTowers.FOCUS_CORE).description();

        assertTrue(description.stream().anyMatch(line -> line.contains("7칸")));
        assertTrue(description.stream().anyMatch(line -> line.contains("2/4/6기")));
        assertTrue(description.stream().anyMatch(line -> line.contains("11%")));
        assertTrue(description.stream().anyMatch(line -> line.contains("22%") && line.contains("13%")));
        assertTrue(description.stream().anyMatch(line -> line.contains("33%") && line.contains("17%")));
        assertTrue(description.stream().anyMatch(line -> line.contains("4번째") && line.contains("44%")));
    }

    private static void assertMoobloomVariant(kim.biryeong.semiontd.tower.TowerType type, String variant) {
        assertEquals("friendsandfoes:moobloom", type.visual().entityTypeId());
        assertEquals(variant, type.visual().properties().get("moobloom_variant"));
    }

    private static ResonanceTower resonanceTower(kim.biryeong.semiontd.tower.TowerType type, UUID playerId, int x) {
        return resonanceTower(type, playerId, x, 0);
    }

    private static ResonanceTower resonanceTower(kim.biryeong.semiontd.tower.TowerType type, UUID playerId, int x, int z) {
        GridPosition position = new GridPosition(x, 64, z);
        return new ResonanceTower(type, playerId, TeamId.RED, 1, position, position);
    }

    private static java.util.List<kim.biryeong.semiontd.tower.Tower> withLinks(ResonanceTower focus, java.util.List<ResonanceTower> links) {
        java.util.ArrayList<kim.biryeong.semiontd.tower.Tower> towers = new java.util.ArrayList<>();
        towers.add(focus);
        towers.addAll(links);
        return towers;
    }
}
