package kim.biryeong.semiontd.tower.ancientcity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.AncientCityTowerJob;
import kim.biryeong.semiontd.job.OceanTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class AncientCityTowerCatalogTest {
    private static final double EPSILON = 0.0001;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void resetCatalogs() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceRuntime.apply(defaults);
        ProductionTowerCatalogs.reloadBuiltIns(defaults);
        AncientCityStates.clearAllForTesting();
    }

    @Test
    void jobAndCatalogExposeOnlyFourAncientCityStarters() {
        AncientCityTowerJob job = new AncientCityTowerJob();
        assertEquals("semion-td:ancient_city", job.id().toString());
        assertEquals("고대 도시 빌더", job.displayName().getString());
        assertTrue(job.canUseTower(null, AncientCityTowers.CATALYST_T1));
        assertFalse(job.canUseTower(null, kim.biryeong.semiontd.tower.ocean.OceanTowers.T1_WATER));
        assertFalse(new OceanTowerJob().canUseTower(null, AncientCityTowers.WARDEN_T1));

        List<TowerType> starters = ProductionTowerCatalog.all().stream()
                .filter(ProductionTowerCatalog.CatalogEntry::starter)
                .map(ProductionTowerCatalog.CatalogEntry::type)
                .filter(AncientCityTowers::isAncientCityTower)
                .toList();
        assertEquals(List.of(
                AncientCityTowers.CATALYST_T1.id(),
                AncientCityTowers.SENSOR_T1.id(),
                AncientCityTowers.SHRIEKER_T1.id(),
                AncientCityTowers.WARDEN_T1.id()
        ), starters.stream().map(TowerType::id).toList());
    }

    @Test
    void everyBranchLinksTierOneToTierThreeWithConfiguredCosts() {
        assertUpgrade(AncientCityTowers.CATALYST_T1, AncientCityTowers.CATALYST_T2, 110);
        assertUpgrade(AncientCityTowers.CATALYST_T2, AncientCityTowers.CATALYST_T3, 230);
        assertUpgrade(AncientCityTowers.SENSOR_T1, AncientCityTowers.SENSOR_T2, 90);
        assertUpgrade(AncientCityTowers.SENSOR_T2, AncientCityTowers.SENSOR_T3, 190);
        assertUpgrade(AncientCityTowers.SHRIEKER_T1, AncientCityTowers.SHRIEKER_T2, 110);
        assertUpgrade(AncientCityTowers.SHRIEKER_T2, AncientCityTowers.SHRIEKER_T3, 220);
        assertUpgrade(AncientCityTowers.WARDEN_T1, AncientCityTowers.WARDEN_T2, 160);
        assertUpgrade(AncientCityTowers.WARDEN_T2, AncientCityTowers.WARDEN_T3, 300);

        var runtime = ProductionTowerCatalog.find(AncientCityTowers.WARDEN_T3.id()).orElseThrow()
                .create(UUID.nameUUIDFromBytes("ancient-runtime".getBytes(StandardCharsets.UTF_8)),
                        TeamId.RED, 1, new GridPosition(0, 64, 0));
        assertInstanceOf(AncientCityTower.class, runtime);
    }

    @Test
    void resonanceAndCombinedBonusRespectTheirCaps() {
        assertEquals(0.0, AncientCityStates.resonanceBonusForCount(0), EPSILON);
        assertEquals(0.5625, AncientCityStates.resonanceBonusForCount(56), EPSILON);
        assertEquals(1.125, AncientCityStates.resonanceBonusForCount(112), EPSILON);
        assertEquals(1.6875, AncientCityStates.resonanceBonusForCount(168), EPSILON);
        assertEquals(2.25, AncientCityStates.resonanceBonusForCount(224), EPSILON);
        assertEquals(2.25, AncientCityStates.resonanceBonusForCount(256), EPSILON);
        assertEquals(2.55, AncientCityTower.combinedMagicBonus(2.25 + 0.30), EPSILON);
        assertEquals(2.55, AncientCityTower.combinedMagicBonus(9.0), EPSILON);
        assertEquals(10.0, AncientCityTower.incomeAdjustedMagicDamage(10.0, false), EPSILON);
        assertEquals(17.5, AncientCityTower.incomeAdjustedMagicDamage(10.0, true), EPSILON);
    }

    @Test
    void defaultAndMissingConfigContainEveryAncientCityValue() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertEquals(256.0, defaults.ability(AncientCityStates.CONFIG_ID, "maxSculk", -1), EPSILON);
        assertEquals(224.0, defaults.ability(AncientCityStates.CONFIG_ID, "resonanceFullAt", -1), EPSILON);
        assertEquals(9.0, defaults.ability(AncientCityStates.CONFIG_ID, "initialSculk", -1), EPSILON);
        assertEquals(4.0, defaults.ability(AncientCityStates.CONFIG_ID, "waveStartSpread", -1), EPSILON);
        assertEquals(6.0, defaults.ability(AncientCityStates.CONFIG_ID, "deathSpreadCapPerRound", -1), EPSILON);
        assertEquals(2.25, defaults.ability(AncientCityStates.CONFIG_ID, "resonanceDamageCap", -1), EPSILON);
        assertEquals(2.55, defaults.ability(AncientCityStates.CONFIG_ID, "maxCombinedDamageBonus", -1), EPSILON);
        assertEquals(1.75, defaults.ability(AncientCityStates.CONFIG_ID, "incomeMagicDamageMultiplier", -1), EPSILON);
        assertEquals(30.0, defaults.ability(AncientCityTowers.CATALYST_T3.id(), "magicDamage", -1), EPSILON);
        assertEquals(0.30, defaults.ability(AncientCityTowers.SENSOR_T3.id(), "markDamageBonus", -1), EPSILON);
        assertEquals(3.0, defaults.ability(AncientCityTowers.SHRIEKER_T3.id(), "magicRadius", -1), EPSILON);
        assertEquals(0.25, defaults.ability(AncientCityTowers.WARDEN_T3.id(), "secondaryDamageRatio", -1), EPSILON);

        TowerBalanceConfig merged = new TowerBalanceConfig(
                Map.of(), Map.of(), Map.of(AncientCityStates.CONFIG_ID, Map.of("maxSculk", 40.0))
        ).withMissingDefaults(defaults);
        assertEquals(40.0, merged.ability(AncientCityStates.CONFIG_ID, "maxSculk", -1), EPSILON);
        assertEquals(224.0, merged.ability(AncientCityStates.CONFIG_ID, "resonanceFullAt", -1), EPSILON);
        assertEquals(2.25, merged.ability(AncientCityStates.CONFIG_ID, "resonanceDamageCap", -1), EPSILON);
        assertEquals(1.75, merged.ability(AncientCityStates.CONFIG_ID, "incomeMagicDamageMultiplier", -1), EPSILON);
        assertTrue(merged.towers().containsKey(AncientCityTowers.WARDEN_T3.id()));
    }

    @Test
    void descriptionsRenderAllAbilityValuesAndExplainResonanceRule() {
        for (TowerType type : AncientCityTowers.all()) {
            String description = String.join(" ", TowerBalanceRuntime.resolve(type).description());
            assertFalse(description.contains("{ability."), type.id());
            assertTrue(description.contains("마법"), type.id());
            assertTrue(description.contains("현재 위치가 자신의 스컬크 영토면"), type.id());
            assertTrue(description.contains("최종 방어선에서도 재생성된 스컬크 위에 있어야 합니다"), type.id());
            assertFalse(description.contains("점화"), type.id());
        }
        String builderDescription = new AncientCityTowerJob().description().stream()
                .map(component -> component.getString())
                .collect(java.util.stream.Collectors.joining(" "));
        assertTrue(builderDescription.contains("스컬크 영토"));
        assertTrue(builderDescription.contains("공명"));
        assertTrue(builderDescription.contains("감지체"));
        assertFalse(builderDescription.matches(".*\\d.*"));

        AncientCityTower tower = new AncientCityTower(
                TowerBalanceRuntime.resolve(AncientCityTowers.CATALYST_T1),
                uuid("runtime-details"),
                TeamId.RED,
                1,
                new GridPosition(0, 64, 0)
        );
        assertEquals(List.of(
                "스컬크 영토 0/256",
                "스컬크 공명 0/224 · +0.0% (비활성)"
        ), tower.runtimeDetailLines());
    }

    @Test
    void marksStayOwnerScopedPreferStrongestAndExpireIndividually() {
        AncientCityMarks.MarkSet marks = new AncientCityMarks.MarkSet();
        UUID ownerA = uuid("owner-a");
        UUID ownerB = uuid("owner-b");
        marks.apply(ownerA, uuid("weak"), 0.10, 10);
        marks.apply(ownerA, uuid("strong"), 0.30, 5);
        marks.apply(ownerB, uuid("other"), 0.20, 20);

        assertEquals(0.30, marks.damageBonus(ownerA, 4), EPSILON);
        assertEquals(0.10, marks.damageBonus(ownerA, 5), EPSILON);
        assertEquals(0.20, marks.damageBonus(ownerB, 10), EPSILON);
        assertEquals(0.0, marks.damageBonus(ownerA, 10), EPSILON);
        assertFalse(marks.empty());
        assertEquals(0.0, marks.damageBonus(ownerB, 20), EPSILON);
        assertTrue(marks.empty());
    }

    private static void assertUpgrade(TowerType from, TowerType to, long cost) {
        var upgrade = ProductionTowerCatalog.upgrade(from, to.id()).orElseThrow();
        assertEquals(to.displayName(), upgrade.displayName());
        assertEquals(cost, upgrade.mineralCost());
    }

    private static UUID uuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
