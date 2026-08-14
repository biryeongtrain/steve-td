package kim.biryeong.semiontd.tower.plant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.PlantTowerJob;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.ocean.OceanTowers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class PlantTowerCatalogTest {
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
        PlantSoilStates.clearAllForTesting();
    }

    @Test
    void jobExposesEveryPlantTowerAndNothingElse() {
        PlantTowerJob job = new PlantTowerJob();
        assertEquals("semion-td:plant_towers", job.id().toString());
        assertEquals("식물 빌더", job.displayName().getString());
        assertTrue(job.canUseTower(null, PlantTowers.T1_OAK_SEED_TOWER));
        assertTrue(job.canUseTower(null, PlantTowers.T3_PODZOL_ROSE_TOWER));
        assertFalse(job.canUseTower(null, OceanTowers.T1_WATER));
    }

    @Test
    void catalogExposesFourTerraformAndFourCombatStarters() {
        List<String> starters = ProductionTowerCatalog.all().stream()
                .filter(ProductionTowerCatalog.CatalogEntry::starter)
                .map(ProductionTowerCatalog.CatalogEntry::type)
                .filter(PlantTowers::isPlantTower)
                .map(TowerType::id)
                .toList();
        assertEquals(List.of(
                PlantTowers.T1_OAK_SEED_TOWER.id(),
                PlantTowers.T1_MUSHROOM_SPORE_TOWER.id(),
                PlantTowers.T1_DRY_GRASS_SEED_TOWER.id(),
                PlantTowers.T1_SPRUCE_SEED_TOWER.id(),
                PlantTowers.T1_MEADOW_TOWER.id(),
                PlantTowers.T1_MEADOW_NOVA_TOWER.id(),
                PlantTowers.T1_MYCELIUM_TOWER.id(),
                PlantTowers.T1_DESERT_TOWER.id(),
                PlantTowers.T1_PODZOL_TOWER.id()
        ), starters);
    }

    @Test
    void everyFamilyLinksTierOneToTierThreeWithConfiguredCosts() {
        assertUpgrade(PlantTowers.T1_OAK_SEED_TOWER, PlantTowers.T2_OAK_SEED_TOWER, 85);
        assertUpgrade(PlantTowers.T2_OAK_SEED_TOWER, PlantTowers.T3_OAK_SEED_TOWER, 180);
        assertUpgrade(PlantTowers.T1_MUSHROOM_SPORE_TOWER, PlantTowers.T2_MUSHROOM_SPORE_TOWER, 85);
        assertUpgrade(PlantTowers.T2_MUSHROOM_SPORE_TOWER, PlantTowers.T3_MUSHROOM_SPORE_TOWER, 180);
        assertUpgrade(PlantTowers.T1_DRY_GRASS_SEED_TOWER, PlantTowers.T2_DRY_GRASS_SEED_TOWER, 85);
        assertUpgrade(PlantTowers.T2_DRY_GRASS_SEED_TOWER, PlantTowers.T3_DRY_GRASS_SEED_TOWER, 180);
        assertUpgrade(PlantTowers.T1_SPRUCE_SEED_TOWER, PlantTowers.T2_SPRUCE_SEED_TOWER, 85);
        assertUpgrade(PlantTowers.T2_SPRUCE_SEED_TOWER, PlantTowers.T3_SPRUCE_SEED_TOWER, 180);

        assertUpgrade(PlantTowers.T1_MEADOW_TOWER, PlantTowers.T2_MEADOW_TOWER, 150);
        assertUpgrade(PlantTowers.T2_MEADOW_TOWER, PlantTowers.T3_MEADOW_TOWER, 240);
        assertUpgrade(PlantTowers.T1_MYCELIUM_TOWER, PlantTowers.T2_MYCELIUM_TOWER, 110);
        assertUpgrade(PlantTowers.T2_MYCELIUM_TOWER, PlantTowers.T3_MYCELIUM_TOWER, 180);
        // 탱커 업그레이드는 실서버 기준값(160/250)에 맞춰 둡니다.
        assertUpgrade(PlantTowers.T1_DESERT_TOWER, PlantTowers.T2_DESERT_TOWER, 160);
        assertUpgrade(PlantTowers.T2_DESERT_TOWER, PlantTowers.T3_DESERT_TOWER, 250);
        assertUpgrade(PlantTowers.T1_PODZOL_TOWER, PlantTowers.T2_PODZOL_TOWER, 170);

        // 계열마다 자기 테라포머가 필요합니다. 다른 계열을 섞으면 지형값을 두 번 냅니다.
        long oakSeed = TowerBalanceRuntime.resolve(PlantTowers.T1_OAK_SEED_TOWER).mineralCost();
        long dryGrassSeed = TowerBalanceRuntime.resolve(PlantTowers.T1_DRY_GRASS_SEED_TOWER).mineralCost();
        long meadow = TowerBalanceRuntime.resolve(PlantTowers.T1_MEADOW_TOWER).mineralCost();
        long desert = TowerBalanceRuntime.resolve(PlantTowers.T1_DESERT_TOWER).mineralCost();

        // 한 계열로 시작: 테라포머 1개 + 전투 타워 2기.
        assertTrue(oakSeed + meadow * 2 <= 150, "single family opening " + (oakSeed + meadow * 2));
        // 두 계열로 시작: 테라포머 2개 + 전투 타워 각 1기. 시작 다이아를 넘으면 안 됩니다.
        long twoFamilies = oakSeed + meadow + dryGrassSeed + desert;
        assertTrue(twoFamilies <= 150, "two family opening " + twoFamilies);
    }

    @Test
    void podzolBranchesIntoThreeFinishersFromTierTwo() {
        assertUpgrade(PlantTowers.T2_PODZOL_TOWER, PlantTowers.T3_PODZOL_LILAC_TOWER, 285);
        assertUpgrade(PlantTowers.T2_PODZOL_TOWER, PlantTowers.T3_PODZOL_ROSE_TOWER, 285);
        assertUpgrade(PlantTowers.T2_PODZOL_TOWER, PlantTowers.T3_PODZOL_PITCHER_TOWER, 285);
        assertEquals(3, ProductionTowerCatalog.upgrades(PlantTowers.T2_PODZOL_TOWER).size());

        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertEquals(5.0, defaults.ability(PlantTowers.T3_PODZOL_LILAC_TOWER.id(), "splashRadius", -1), EPSILON);
        assertEquals(4.0, defaults.ability(PlantTowers.T3_PODZOL_PITCHER_TOWER.id(), "splashRadius", -1), EPSILON);
        assertEquals(0.0, defaults.ability(PlantTowers.T3_PODZOL_ROSE_TOWER.id(), "splashRadius", 0.0), EPSILON);
        // 곡사 포대는 단일 극딜형보다 사거리가 확실히 길어야 역할이 갈립니다.
        assertTrue(TowerBalanceRuntime.resolve(PlantTowers.T3_PODZOL_PITCHER_TOWER).range()
                >= TowerBalanceRuntime.resolve(PlantTowers.T3_PODZOL_ROSE_TOWER).range() * 1.5);
    }

    @Test
    void floweringCactusStacksItsFlowerOnTop() {
        var visual = TowerBalanceRuntime.resolve(PlantTowers.T3_DESERT_TOWER).visual();
        assertTrue(BlockDisplayVisual.matches(visual));
        assertEquals(Blocks.CACTUS.defaultBlockState(), BlockDisplayVisual.blockState(visual));
        assertEquals(Blocks.CACTUS_FLOWER.defaultBlockState(), BlockDisplayVisual.topBlockState(visual));
        assertNull(BlockDisplayVisual.topBlockState(
                TowerBalanceRuntime.resolve(PlantTowers.T2_DESERT_TOWER).visual()));
    }

    @Test
    void terraformersAndCombatTowersUseTheirOwnRuntimeClasses() {
        assertInstanceOf(PlantTerraformTower.class, create(PlantTowers.T3_OAK_SEED_TOWER));
        assertInstanceOf(PlantCombatTower.class, create(PlantTowers.T3_PODZOL_ROSE_TOWER));
    }

    @Test
    void terraformersAreUntouchableFixturesAndCombatTowersAreNot() {
        for (TowerType type : PlantTowers.TERRAFORM_TOWERS) {
            Tower tower = create(type);
            assertTrue(tower.invulnerable(), type.id());
            assertFalse(tower.drawsAggro(), type.id());
        }
        for (TowerType type : PlantTowers.COMBAT_TOWERS) {
            Tower tower = create(type);
            assertFalse(tower.invulnerable(), type.id());
            assertTrue(tower.drawsAggro(), type.id());
        }
    }

    @Test
    void soilEffectsUseConservativeEnvironmentDefaults() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertEquals(20.0, defaults.ability(PlantTowers.GLOBAL_CONFIG_ID, "environmentTickIntervalTicks", -1), EPSILON);
        assertEquals(0.15, defaults.ability(PlantSoil.MYCELIUM.configId(), "environmentWeakness", -1), EPSILON);
        assertEquals(0.25, defaults.ability(PlantSoil.MYCELIUM.configId(), "environmentDamageTakenBonus", -1), EPSILON);
        assertEquals(0.15, defaults.ability(PlantSoil.DESERT.configId(), "environmentAttackSpeedReduction", -1), EPSILON);
        assertEquals(0.005, defaults.ability(PlantSoil.DESERT.configId(), "environmentMaxHealthDamagePerSecond", -1), EPSILON);
        // 잔디와 회백토는 아군 지형이라 환경 효과가 없습니다.
        assertEquals(0.0, defaults.ability(PlantSoil.MEADOW.configId(), "environmentMaxHealthDamagePerSecond", 0.0), EPSILON);
        assertEquals(0.0, defaults.ability(PlantSoil.PODZOL.configId(), "environmentWeakness", 0.0), EPSILON);
    }

    @Test
    void myceliumLineIsAConsumableMineInsteadOfAnAttacker() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        for (TowerType type : List.of(
                PlantTowers.T1_MYCELIUM_TOWER, PlantTowers.T2_MYCELIUM_TOWER, PlantTowers.T3_MYCELIUM_TOWER)) {
            assertInstanceOf(PlantMineTower.class, create(type), type.id());
            // 사거리 0 이면 공격 goal 이 대상을 잡지 않아 지뢰처럼 가만히 있습니다.
            assertEquals(0.0, TowerBalanceRuntime.resolve(type).range(), EPSILON, type.id());
            assertTrue(defaults.ability(type.id(), "triggerRadius", -1) > 0.0, type.id());
            assertTrue(defaults.ability(type.id(), "explosionRadius", -1) > 0.0, type.id());
            assertTrue(defaults.abilityTicks(type.id(), "explosionDisableTicks", -1) > 0, type.id());
            // 폭발은 공격력뿐 아니라 남은 체력도 함께 터뜨립니다.
            assertEquals(0.25, defaults.ability(type.id(), "explosionHealthRatio", -1), EPSILON, type.id());
            assertEquals(2.0, defaults.ability(type.id(), "explosionDamageMultiplier", -1), EPSILON, type.id());
            assertTrue(TowerBalanceRuntime.resolve(type).maxHealth() > 0.0, type.id());
        }
        // 티어가 오를수록 폭발 범위와 무력화 시간이 길어집니다.
        assertTrue(defaults.ability(PlantTowers.T3_MYCELIUM_TOWER.id(), "explosionRadius", -1)
                > defaults.ability(PlantTowers.T1_MYCELIUM_TOWER.id(), "explosionRadius", -1));
        assertTrue(defaults.abilityTicks(PlantTowers.T3_MYCELIUM_TOWER.id(), "explosionDisableTicks", -1)
                > defaults.abilityTicks(PlantTowers.T1_MYCELIUM_TOWER.id(), "explosionDisableTicks", -1));
    }

    @Test
    void desertLineNeverAttacksAndFoldsItsDamageIntoTheReflect() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        for (TowerType type : List.of(
                PlantTowers.T1_DESERT_TOWER, PlantTowers.T2_DESERT_TOWER, PlantTowers.T3_DESERT_TOWER)) {
            // 사거리 0 이면 공격 goal 이 대상을 잡지 않습니다.
            assertEquals(0.0, TowerBalanceRuntime.resolve(type).range(), EPSILON, type.id());
            // 반사에 얹을 공격력은 남아 있어야 합니다.
            assertTrue(TowerBalanceRuntime.resolve(type).damage() > 0.0, type.id());
        }
        assertEquals(0.25, defaults.ability(PlantSoil.DESERT.configId(), "thornReflectRatio", -1), EPSILON);
        // 사거리가 0 이라 장판은 지형이 정한 반경을 씁니다.
        assertEquals(5.0, defaults.ability(PlantSoil.DESERT.configId(), "auraRadius", -1), EPSILON);

        // 이속은 균사, 공속은 사암이 담당합니다. 서로 넘어오면 안 됩니다.
        assertEquals(0.25, defaults.ability(PlantSoil.MYCELIUM.configId(), "environmentMoveSpeedReduction", -1), EPSILON);
        assertEquals(0.0, defaults.ability(PlantSoil.DESERT.configId(), "environmentMoveSpeedReduction", 0.0), EPSILON);
        assertEquals(0.0, defaults.ability(PlantSoil.MYCELIUM.configId(), "environmentAttackSpeedReduction", 0.0), EPSILON);
        // 타워 오라(0.25)가 지형 자체 공속 감소(0.15)보다 커야 겹칠 때 타워가 이깁니다.
        assertTrue(defaults.ability(PlantSoil.DESERT.configId(), "attackSpeedReduction", -1)
                > defaults.ability(PlantSoil.DESERT.configId(), "environmentAttackSpeedReduction", -1));
    }

    @Test
    void everyPlantIsRootedInsideTheLane() {
        for (TowerType type : allPlantTowers().toList()) {
            assertFalse(create(type).canChaseTargets(), type.id());
        }
    }

    @Test
    void terraformRadiusFollowsTheTerraformerTierAndCombatTowersNeverTerraform() {
        // T1 도 3x3 을 열어야 자기 칸을 뺀 자리에 전투 타워를 놓을 수 있습니다.
        assertEquals(1, PlantTowers.terraformRadius(PlantTowers.T1_OAK_SEED_TOWER));
        assertEquals(2, PlantTowers.terraformRadius(PlantTowers.T2_OAK_SEED_TOWER));
        assertEquals(3, PlantTowers.terraformRadius(PlantTowers.T3_OAK_SEED_TOWER));
        assertEquals(-1, PlantTowers.terraformRadius(PlantTowers.T3_MEADOW_TOWER));
        assertTrue(PlantTowers.isTerraformTower(PlantTowers.T1_DRY_GRASS_SEED_TOWER));
        assertTrue(PlantTowers.isCombatTower(PlantTowers.T1_DESERT_TOWER));
    }

    @Test
    void combatTowersNeedTheirOwnSoilWhileTerraformersOnlyNeedFreeGround() {
        UUID owner = uuid("plant-placement");
        GridPosition position = new GridPosition(0, 64, 0);
        assertTrue(PlantSoilStates.canPlantAt(owner, position, PlantTowers.T1_OAK_SEED_TOWER));
        assertFalse(PlantSoilStates.canPlantAt(owner, position, PlantTowers.T1_MEADOW_TOWER));
        assertTrue(PlantSoilStates.canPlantAt(owner, position, OceanTowers.T1_WATER));
    }

    @Test
    void meadowSplitsIntoAnEconomyLineAndASelfCenteredNovaLine() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertUpgrade(PlantTowers.T1_MEADOW_NOVA_TOWER, PlantTowers.T2_MEADOW_NOVA_TOWER, 175);
        assertUpgrade(PlantTowers.T2_MEADOW_NOVA_TOWER, PlantTowers.T3_MEADOW_NOVA_TOWER, 275);
        assertEquals(PlantSoil.MEADOW, PlantTowers.soilOf(PlantTowers.T3_MEADOW_NOVA_TOWER));

        // 민들레 계열만 웨이브 정산 다이아를, 튤립 계열만 자기 중심 광역을 가집니다.
        assertEquals(3.0, defaults.ability(PlantTowers.T1_MEADOW_TOWER.id(), "diamondPerWave", -1), EPSILON);
        assertEquals(9.0, defaults.ability(PlantTowers.T2_MEADOW_TOWER.id(), "diamondPerWave", -1), EPSILON);
        assertEquals(24.0, defaults.ability(PlantTowers.T3_MEADOW_TOWER.id(), "diamondPerWave", -1), EPSILON);
        assertEquals(0.0, defaults.ability(PlantTowers.T3_MEADOW_TOWER.id(), "novaRadius", 0.0), EPSILON);
        assertEquals(5.5, defaults.ability(PlantTowers.T3_MEADOW_NOVA_TOWER.id(), "novaRadius", -1), EPSILON);
        assertEquals(0.0, defaults.ability(PlantTowers.T3_MEADOW_NOVA_TOWER.id(), "diamondPerWave", 0.0), EPSILON);

        // 어그로 순서: 지원 < 광역 < 모래 탱커. 탱킹은 모래가 전담합니다.
        assertTrue(TowerBalanceRuntime.resolve(PlantTowers.T3_MEADOW_TOWER).aggroPriority()
                < TowerBalanceRuntime.resolve(PlantTowers.T3_MEADOW_NOVA_TOWER).aggroPriority());
        assertTrue(TowerBalanceRuntime.resolve(PlantTowers.T3_MEADOW_NOVA_TOWER).aggroPriority()
                < TowerBalanceRuntime.resolve(PlantTowers.T3_DESERT_TOWER).aggroPriority());
        assertTrue(TowerBalanceRuntime.resolve(PlantTowers.T1_MEADOW_NOVA_TOWER).aggroPriority()
                < TowerBalanceRuntime.resolve(PlantTowers.T1_DESERT_TOWER).aggroPriority());
    }

    @Test
    void shopGroupsPlantTowersBySoilAndLeavesOtherBuildersAlone() {
        PlantTowerJob job = new PlantTowerJob();
        assertEquals("잔디", job.towerGroup(PlantTowers.T1_OAK_SEED_TOWER));
        assertEquals("잔디", job.towerGroup(PlantTowers.T3_MEADOW_NOVA_TOWER));
        assertEquals("사암", job.towerGroup(PlantTowers.T2_DESERT_TOWER));
        assertEquals("회백토", job.towerGroup(PlantTowers.T3_PODZOL_ROSE_TOWER));
        assertEquals("균사", job.towerGroup(PlantTowers.T1_MYCELIUM_TOWER));
        assertNull(job.towerGroup(OceanTowers.T1_WATER));

        // 분류를 쓰지 않는 빌더는 기존 평면 목록을 그대로 유지해야 합니다.
        assertNull(new kim.biryeong.semiontd.job.OceanTowerJob().towerGroup(OceanTowers.T1_WATER));

        // 건설 후보 전체가 네 계열로만 나뉘어야 합니다.
        assertEquals(
                java.util.Set.of("잔디", "사암", "회백토", "균사"),
                allPlantTowers().map(job::towerGroup).collect(java.util.stream.Collectors.toSet())
        );
    }

    @Test
    void podzolLineHasCritAndEachFinisherHasItsOwnAbility() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        // 계열 공통 치명타. 티어가 오를수록 확률이 높아집니다.
        assertEquals(0.08, defaults.ability(PlantTowers.T1_PODZOL_TOWER.id(), "critChance", -1), EPSILON);
        assertEquals(0.18, defaults.ability(PlantTowers.T2_PODZOL_TOWER.id(), "critChance", -1), EPSILON);
        assertEquals(0.35, defaults.ability(PlantTowers.T3_PODZOL_ROSE_TOWER.id(), "critChance", -1), EPSILON);

        // 장미 덤불만 초치명타를 가집니다.
        assertEquals(0.05, defaults.ability(PlantTowers.T3_PODZOL_ROSE_TOWER.id(), "superCritChance", -1), EPSILON);
        assertEquals(3.0, defaults.ability(PlantTowers.T3_PODZOL_ROSE_TOWER.id(), "superCritMultiplier", -1), EPSILON);
        assertEquals(0.0, defaults.ability(PlantTowers.T3_PODZOL_LILAC_TOWER.id(), "superCritChance", 0.0), EPSILON);

        // 라일락만 부채꼴 + 잃은 체력 비례, 물병 식물만 속박입니다.
        assertEquals(130.0, defaults.ability(PlantTowers.T3_PODZOL_LILAC_TOWER.id(), "splashConeDegrees", -1), EPSILON);
        assertEquals(0.02, defaults.ability(PlantTowers.T3_PODZOL_LILAC_TOWER.id(), "splashMissingHealthRatio", -1), EPSILON);
        assertEquals(0.0, defaults.ability(PlantTowers.T3_PODZOL_PITCHER_TOWER.id(), "splashConeDegrees", 0.0), EPSILON);
        assertEquals(0.7, defaults.ability(PlantTowers.T3_PODZOL_PITCHER_TOWER.id(), "snareMoveSpeedReduction", -1), EPSILON);

        // 속박이 실효 공격 간격보다 길면 재장전 중에도 계속 묶여 영구 고정이 됩니다.
        double speedBonus = defaults.ability(PlantSoil.PODZOL.configId(), "attackSpeedBonus", 0.0)
                * defaults.ability(PlantTowers.T3_PODZOL_PITCHER_TOWER.id(), "soilPower", 1.0);
        int effectiveInterval = (int) Math.ceil(
                TowerBalanceRuntime.resolve(PlantTowers.T3_PODZOL_PITCHER_TOWER).attackIntervalTicks()
                        / (1.0 + speedBonus));
        int snareTicks = defaults.abilityTicks(PlantTowers.T3_PODZOL_PITCHER_TOWER.id(), "snareDurationTicks", -1);
        assertTrue(snareTicks < effectiveInterval, "snare " + snareTicks + " vs interval " + effectiveInterval);
        assertEquals(0.0, defaults.ability(PlantTowers.T3_PODZOL_LILAC_TOWER.id(), "snareMoveSpeedReduction", 0.0), EPSILON);
    }

    @Test
    void everyFamilyMapsToItsOwnSoil() {
        assertEquals(PlantSoil.MEADOW, PlantTowers.soilOf(PlantTowers.T2_MEADOW_TOWER));
        assertEquals(PlantSoil.MYCELIUM, PlantTowers.soilOf(PlantTowers.T2_MYCELIUM_TOWER));
        assertEquals(PlantSoil.DESERT, PlantTowers.soilOf(PlantTowers.T2_DESERT_TOWER));
        assertEquals(PlantSoil.PODZOL, PlantTowers.soilOf(PlantTowers.T3_PODZOL_LILAC_TOWER));
        assertEquals("plant_soil_meadow", PlantSoil.MEADOW.configId());
        assertEquals("잔디", PlantSoil.MEADOW.displayName());

        // 레인 바닥이 1칸일 수 있어 중력 블록을 지형으로 쓰면 떨어져 사라집니다.
        for (PlantSoil soil : PlantSoil.values()) {
            assertFalse(soil.block() instanceof net.minecraft.world.level.block.FallingBlock, soil.key());
        }
    }

    @Test
    void baseDamageStaysAtOrBelowFiftyOnEveryTier() {
        for (TowerType type : allPlantTowers().toList()) {
            double damage = TowerBalanceRuntime.resolve(type).damage();
            assertTrue(damage <= 50.0, type.id() + " base damage " + damage);
        }
    }

    @Test
    void soilAmplificationStaysWithinTheConservativeCap() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        double bloomCap = defaults.ability(PlantTowers.GLOBAL_CONFIG_ID, "bloomDamageCap", -1);
        // T1 테라포머 한 기(3x3)만 깔아도 전투 타워를 놓을 빈 칸이 남아야 합니다.
        int t1Tiles = (int) Math.pow(PlantTowers.terraformRadius(PlantTowers.T1_OAK_SEED_TOWER) * 2 + 1, 2);
        assertTrue(t1Tiles - 1 >= 4, "T1 free tiles " + (t1Tiles - 1));
        double frailty = defaults.ability(PlantSoil.MYCELIUM.configId(), "environmentDamageTakenBonus", -1);
        assertEquals(0.4, bloomCap, EPSILON);
        assertEquals(0.25, frailty, EPSILON);

        double totalMultiplier = (1.0 + bloomCap) * (1.0 + frailty);
        assertEquals(1.75, totalMultiplier, EPSILON);
    }

    @Test
    void defaultConfigCarriesEverySoilValue() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertEquals(0.01, defaults.ability(PlantTowers.GLOBAL_CONFIG_ID, "bloomDamagePerTile", -1), EPSILON);
        assertEquals(1.0, defaults.ability(PlantTowers.T1_OAK_SEED_TOWER.id(), "terraformRadius", -1), EPSILON);
        assertEquals(20.0, defaults.ability(PlantTowers.GLOBAL_CONFIG_ID, "soilPulseIntervalTicks", -1), EPSILON);
        assertEquals(0.015, defaults.ability(PlantSoil.MEADOW.configId(), "healPercentPerPulse", -1), EPSILON);
        assertEquals(6.0, defaults.ability(PlantSoil.MEADOW.configId(), "supportRadius", -1), EPSILON);
        assertEquals(0.2, defaults.ability(PlantSoil.MEADOW.configId(), "growthShareRatio", -1), EPSILON);
        assertEquals(0.5, defaults.ability(PlantSoil.MEADOW.configId(), "growthShareCap", -1), EPSILON);

        // 성장은 40라운드 게임 내내 붙어야 합니다. 상한에 너무 일찍 닿으면 후반이 죽습니다.
        double perRound = defaults.ability(PlantSoil.MEADOW.configId(), "maxHealthGrowthPerRound", -1);
        double growthCap = defaults.ability(PlantSoil.MEADOW.configId(), "maxHealthGrowthCap", -1);
        assertTrue(growthCap / perRound >= 40.0, "rounds to cap " + (growthCap / perRound));
        assertEquals(2.0, defaults.ability(PlantTowers.T1_MYCELIUM_TOWER.id(), "explosionDamageMultiplier", -1), EPSILON);
        assertEquals(0.25, defaults.ability(PlantSoil.DESERT.configId(), "attackSpeedReduction", -1), EPSILON);
        assertEquals(0.25, defaults.ability(PlantSoil.DESERT.configId(), "thornReflectRatio", -1), EPSILON);
        assertEquals(4.0, defaults.ability(PlantSoil.PODZOL.configId(), "rangeBonus", -1), EPSILON);

        // 체력은 잔디, 피해는 회백토가 키웁니다. 둘 다 40라운드까지 붙어야 합니다.
        double damagePerRound = defaults.ability(PlantSoil.PODZOL.configId(), "damageGrowthPerRound", -1);
        double damageCap = defaults.ability(PlantSoil.PODZOL.configId(), "damageGrowthCap", -1);
        assertTrue(damageCap / damagePerRound >= 40.0, "rounds to damage cap " + (damageCap / damagePerRound));
        assertEquals(0.0, defaults.ability(PlantSoil.MEADOW.configId(), "damageGrowthPerRound", 0.0), EPSILON);
        assertEquals(6.0, defaults.ability(PlantTowers.GLOBAL_CONFIG_ID, "soilAuraMaxRadius", -1), EPSILON);
        assertEquals(0.25, defaults.ability(PlantSoil.PODZOL.configId(), "attackSpeedBonus", -1), EPSILON);
        for (TowerType type : allPlantTowers().toList()) {
            assertTrue(defaults.towers().containsKey(type.id()), type.id());
        }
    }

    @Test
    void roseAndAreaDamageStayWithinTheExpectedDpsBudget() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        var rose = TowerBalanceRuntime.resolve(PlantTowers.T3_PODZOL_ROSE_TOWER);
        double soilPower = defaults.ability(PlantTowers.T3_PODZOL_ROSE_TOWER.id(), "soilPower", -1);
        int interval = (int) Math.ceil(rose.attackIntervalTicks()
                / (1.0 + defaults.ability(PlantSoil.PODZOL.configId(), "attackSpeedBonus", -1) * soilPower));
        double baseDps = rose.damage() * 20.0 / interval;
        double critMultiplier = 0.05 * 3.0 + 0.95 * (0.35 * 2.0 + 0.65);

        assertEquals(154.0, baseDps * 1.4 * critMultiplier, 0.5);
        assertEquals(200.0, baseDps * (1.4 + 0.015 * soilPower * 20) * critMultiplier, 0.5);
        assertEquals(246.0, baseDps * (1.4 + 0.6 * soilPower) * critMultiplier, 0.5);
        assertEquals(307.0, baseDps * (1.4 + 0.6 * soilPower) * critMultiplier * 1.25, 0.6);

        assertEffectiveTargetMultiplier(PlantTowers.T3_MEADOW_NOVA_TOWER, "novaDamageRatio", 1.0, 2.2, 3.4);
        assertEffectiveTargetMultiplier(PlantTowers.T3_PODZOL_LILAC_TOWER, "splashDamageRatio", 1.0, 1.7, 2.4);
        assertEffectiveTargetMultiplier(PlantTowers.T3_PODZOL_PITCHER_TOWER, "splashDamageRatio", 1.0, 1.9, 2.8);

        var mine = TowerBalanceRuntime.resolve(PlantTowers.T3_MYCELIUM_TOWER);
        double explosion = (mine.damage() * 2.0 + mine.maxHealth() * 0.25) * 1.4;
        assertEquals(259.0, explosion, EPSILON);
    }

    @Test
    void plantConfigRejectsInvalidRatiosTicksAndAuraBounds() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertDoesNotThrow(defaults::validateForRuntime);
        assertPlantConfigRejected(defaults, PlantTowers.GLOBAL_CONFIG_ID, "bloomDamageCap", 1.01);
        assertPlantConfigRejected(defaults, PlantTowers.T1_MEADOW_TOWER.id(), "diamondPerWave", 3.5);
        assertPlantConfigRejected(defaults, PlantTowers.T1_MYCELIUM_TOWER.id(), "explosionDisableTicks", 20.5);

        Map<String, Map<String, Double>> abilities = mutableAbilities(defaults);
        abilities.get(PlantTowers.GLOBAL_CONFIG_ID).put("soilAuraMinRadius", 7.0);
        assertThrows(IllegalArgumentException.class, () -> configWithAbilities(defaults, abilities).validateForRuntime());
    }

    @Test
    void plantDescriptionsTrackConfiguredValuesWithoutPlaceholders() {
        for (TowerType type : allPlantTowers().toList()) {
            List<String> description = TowerBalanceRuntime.resolve(type).description();
            assertFalse(description.isEmpty(), type.id());
            assertTrue(description.stream().noneMatch(line -> line.contains("{stat.") || line.contains("{ability.")), type.id());
        }
        assertTrue(String.join(" ", TowerBalanceRuntime.resolve(PlantTowers.T1_MEADOW_TOWER).description())
                .contains("다이아를 3개"));
        assertTrue(String.join(" ", TowerBalanceRuntime.resolve(PlantTowers.T3_PODZOL_ROSE_TOWER).description())
                .contains("35%"));
    }

    @Test
    void missingDefaultsBackfillPerWaveIncomeWithoutOverwritingOverrides() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceConfig partial = new TowerBalanceConfig(
                Map.of(),
                Map.of(),
                Map.of(PlantTowers.T1_MEADOW_TOWER.id(), Map.of("diamondPerWave", 5.0))
        );
        TowerBalanceConfig merged = partial.withMissingDefaults(defaults);
        assertEquals(5.0, merged.ability(PlantTowers.T1_MEADOW_TOWER.id(), "diamondPerWave", -1), EPSILON);
        assertEquals(9.0, merged.ability(PlantTowers.T2_MEADOW_TOWER.id(), "diamondPerWave", -1), EPSILON);
    }

    private static Stream<TowerType> allPlantTowers() {
        return Stream.concat(PlantTowers.TERRAFORM_TOWERS.stream(), PlantTowers.COMBAT_TOWERS.stream());
    }

    private static Tower create(TowerType type) {
        return ProductionTowerCatalog.find(type.id()).orElseThrow()
                .create(uuid("plant-runtime"), TeamId.RED, 1, new GridPosition(0, 64, 0));
    }

    private static void assertEffectiveTargetMultiplier(
            TowerType type,
            String ratioKey,
            double oneTarget,
            double threeTargets,
            double fiveTargets
    ) {
        double ratio = TowerBalanceConfig.defaultConfig().ability(type.id(), ratioKey, -1);
        assertEquals(oneTarget, 1.0, EPSILON);
        assertEquals(threeTargets, 1.0 + ratio * 2, EPSILON);
        assertEquals(fiveTargets, 1.0 + ratio * 4, EPSILON);
    }

    private static void assertPlantConfigRejected(
            TowerBalanceConfig defaults,
            String configId,
            String ability,
            double value
    ) {
        Map<String, Map<String, Double>> abilities = mutableAbilities(defaults);
        abilities.get(configId).put(ability, value);
        assertThrows(IllegalArgumentException.class, () -> configWithAbilities(defaults, abilities).validateForRuntime());
    }

    private static Map<String, Map<String, Double>> mutableAbilities(TowerBalanceConfig config) {
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>();
        config.abilities().forEach((id, values) -> abilities.put(id, new LinkedHashMap<>(values)));
        return abilities;
    }

    private static TowerBalanceConfig configWithAbilities(
            TowerBalanceConfig defaults,
            Map<String, Map<String, Double>> abilities
    ) {
        return new TowerBalanceConfig(
                defaults.towers(),
                defaults.upgradeCosts(),
                abilities,
                defaults.illusionCloneQueue(),
                defaults.villagerAdv(),
                defaults.schemaVersion()
        );
    }

    private static void assertUpgrade(TowerType from, TowerType to, long cost) {
        var upgrade = ProductionTowerCatalog.upgrade(from, to.id()).orElseThrow();
        assertEquals(cost, upgrade.mineralCost(), from.id() + " -> " + to.id());
    }

    private static UUID uuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
