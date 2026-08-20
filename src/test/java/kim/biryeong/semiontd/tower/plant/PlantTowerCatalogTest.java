package kim.biryeong.semiontd.tower.plant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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
        // 지뢰는 폭발 한 번이 아니라 라운드 하나를 사는 값이라 티어 비용을 낮춰 두었습니다.
        assertUpgrade(PlantTowers.T1_MYCELIUM_TOWER, PlantTowers.T2_MYCELIUM_TOWER, 80);
        assertUpgrade(PlantTowers.T2_MYCELIUM_TOWER, PlantTowers.T3_MYCELIUM_TOWER, 130);
        // 탱커는 싼값에 벽을 세우지 못하도록 상위 티어 값을 올렸습니다.
        assertUpgrade(PlantTowers.T1_DESERT_TOWER, PlantTowers.T2_DESERT_TOWER, 190);
        assertUpgrade(PlantTowers.T2_DESERT_TOWER, PlantTowers.T3_DESERT_TOWER, 300);
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
        //
        // 지금 딱 1.5 배입니다(20 → 30). 장미 덤불 사거리를 더 올리려면 물병 식물도 같이 올려야
        // 하고, 안 그러면 "라인 전체를 덮는 곡사 포대" 라는 역할이 사라집니다.
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

    /**
     * 2칸짜리 식물은 윗단까지 그려야 합니다.
     *
     * <p>{@code defaultBlockState()} 는 아랫단이라 그것만 그리면 장미 덤불·라일락·큰 고사리·
     * 물병 식물·해바라기가 반토막으로 보입니다. 블록 목록을 나열하는 대신 속성으로 판별하므로,
     * 나중에 다른 2칸 식물을 써도 자동으로 처리됩니다.
     */
    @Test
    void twoBlockTallPlantsRenderTheirUpperHalf() {
        List<TowerType> tallPlants = Stream.concat(
                        PlantTowers.TERRAFORM_TOWERS.stream(), PlantTowers.COMBAT_TOWERS.stream())
                .map(TowerBalanceRuntime::resolve)
                .filter(type -> BlockDisplayVisual.matches(type.visual()))
                .filter(type -> BlockDisplayVisual.blockState(type.visual())
                        .hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF))
                .toList();
        assertFalse(tallPlants.isEmpty(), "2칸 식물을 쓰는 타워가 하나도 없다면 이 테스트가 무의미합니다");

        for (TowerType type : tallPlants) {
            var visual = type.visual();
            assertEquals(DoubleBlockHalf.LOWER,
                    BlockDisplayVisual.blockState(visual).getValue(BlockStateProperties.DOUBLE_BLOCK_HALF),
                    type.id() + " 아랫단");
            var top = BlockDisplayVisual.topBlockState(visual);
            assertNotNull(top, type.id() + " 는 윗단이 없어 잘려 보입니다");
            assertEquals(DoubleBlockHalf.UPPER, top.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF),
                    type.id() + " 윗단");
            assertEquals(BlockDisplayVisual.blockState(visual).getBlock(), top.getBlock(),
                    type.id() + " 윗단은 같은 블록이어야 합니다");
        }
    }

    /**
     * 플레이어가 체감하는 능력은 툴팁에 반드시 나와야 합니다.
     *
     * <p>고사리 T1·T2 는 치명타를 8%/18% 로 갖고 있으면서도 툴팁에 한 줄도 없어서 없는 기능처럼
     * 보였습니다. 설정에 값을 넣는 것과 설명을 쓰는 것이 따로 놀기 때문에, 같은 누락이 다시
     * 생기지 않도록 여기서 대조합니다.
     */
    @Test
    void everyAdvertisableAbilityAppearsInTheTooltip() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        // 값이 켜져 있으면 플레이어에게 반드시 알려야 하는 키들입니다.
        List<String> mustAdvertise = List.of(
                "critChance", "superCritChance", "snareMoveSpeedReduction",
                "splashRadius", "novaRadius", "diamondPerWave");

        List<String> missing = new java.util.ArrayList<>();
        for (TowerType type : PlantTowers.COMBAT_TOWERS) {
            String tooltip = String.join("\n", type.description());
            for (String key : mustAdvertise) {
                if (defaults.ability(type.id(), key, 0.0) > 0.0
                        && !tooltip.contains("{ability." + key + ":")) {
                    missing.add(type.id() + " -> " + key);
                }
            }
        }
        assertTrue(missing.isEmpty(), "툴팁에서 빠진 능력: " + missing);
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
            // 전투 타워는 무적이 아닙니다. 지뢰도 광역 피해로는 깎이고, 깎이면 약하게 터집니다.
            assertFalse(tower.invulnerable(), type.id());
            // 어그로는 다릅니다. 지뢰는 밟고 지나가는 함정이지 물어뜯을 몸이 아닙니다.
            // 자세한 이유는 minesAreTrapsNotBodiesToChewOn 을 봅니다.
            assertEquals(!(tower instanceof PlantMineTower), tower.drawsAggro(), type.id());
        }
    }

    @Test
    void soilEffectsUseMidgameEnvironmentDefaults() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertEquals(20.0, defaults.ability(PlantTowers.GLOBAL_CONFIG_ID, "environmentTickIntervalTicks", -1), EPSILON);
        assertEquals(0.15, defaults.ability(PlantSoil.MYCELIUM.configId(), "environmentWeakness", -1), EPSILON);
        assertEquals(0.25, defaults.ability(PlantSoil.MYCELIUM.configId(), "environmentDamageTakenBonus", -1), EPSILON);
        assertEquals(0.15, defaults.ability(PlantSoil.DESERT.configId(), "environmentAttackSpeedReduction", -1), EPSILON);
        assertEquals(0.0075, defaults.ability(PlantSoil.DESERT.configId(), "environmentMaxHealthDamagePerSecond", -1), EPSILON);
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
        assertEquals(0.30, defaults.ability(PlantSoil.DESERT.configId(), "thornReflectRatio", -1), EPSILON);
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
        assertEquals(4.0, defaults.ability(PlantTowers.T1_MEADOW_TOWER.id(), "diamondPerWave", -1), EPSILON);
        assertEquals(11.0, defaults.ability(PlantTowers.T2_MEADOW_TOWER.id(), "diamondPerWave", -1), EPSILON);
        assertEquals(28.0, defaults.ability(PlantTowers.T3_MEADOW_TOWER.id(), "diamondPerWave", -1), EPSILON);
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
        assertEquals(0.03, defaults.ability(PlantTowers.T3_PODZOL_LILAC_TOWER.id(), "splashMissingHealthRatio", -1), EPSILON);
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
    void soilAmplificationRewardsACompletedTerraformer() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        double bloomCap = defaults.ability(PlantTowers.GLOBAL_CONFIG_ID, "bloomDamageCap", -1);
        // T1 테라포머 한 기(3x3)만 깔아도 전투 타워를 놓을 빈 칸이 남아야 합니다.
        int t1Tiles = (int) Math.pow(PlantTowers.terraformRadius(PlantTowers.T1_OAK_SEED_TOWER) * 2 + 1, 2);
        assertTrue(t1Tiles - 1 >= 4, "T1 free tiles " + (t1Tiles - 1));
        double frailty = defaults.ability(PlantSoil.MYCELIUM.configId(), "environmentDamageTakenBonus", -1);
        assertEquals(0.6, bloomCap, EPSILON);
        assertEquals(0.25, frailty, EPSILON);

        double totalMultiplier = (1.0 + bloomCap) * (1.0 + frailty);
        assertEquals(2.0, totalMultiplier, EPSILON);
    }

    @Test
    void defaultConfigCarriesEverySoilValue() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertEquals(0.015, defaults.ability(PlantTowers.GLOBAL_CONFIG_ID, "bloomDamagePerTile", -1), EPSILON);
        assertEquals(1.0, defaults.ability(PlantTowers.T1_OAK_SEED_TOWER.id(), "terraformRadius", -1), EPSILON);
        assertEquals(20.0, defaults.ability(PlantTowers.GLOBAL_CONFIG_ID, "soilPulseIntervalTicks", -1), EPSILON);
        assertEquals(0.012, defaults.ability(PlantSoil.MEADOW.configId(), "healPercentPerPulse", -1), EPSILON);
        assertEquals(6.0, defaults.ability(PlantSoil.MEADOW.configId(), "supportRadius", -1), EPSILON);
        assertEquals(0.15, defaults.ability(PlantSoil.MEADOW.configId(), "growthShareRatio", -1), EPSILON);
        assertEquals(0.25, defaults.ability(PlantSoil.MEADOW.configId(), "growthShareCap", -1), EPSILON);
        assertEquals(0.2, defaults.ability(PlantSoil.PODZOL.configId(), "growthShareRatio", -1), EPSILON);
        assertEquals(0.4, defaults.ability(PlantSoil.PODZOL.configId(), "growthShareCap", -1), EPSILON);
        assertEquals(60.0, defaults.ability(PlantSoil.PODZOL.configId(), "supportDurationTicks", -1), EPSILON);

        double perRound = defaults.ability(PlantSoil.MEADOW.configId(), "maxHealthGrowthPerRound", -1);
        double growthCap = defaults.ability(PlantSoil.MEADOW.configId(), "maxHealthGrowthCap", -1);
        assertEquals(0.015, perRound, EPSILON);
        assertEquals(0.5, growthCap, EPSILON);
        assertEquals(2.0, defaults.ability(PlantTowers.T1_MYCELIUM_TOWER.id(), "explosionDamageMultiplier", -1), EPSILON);
        assertEquals(0.25, defaults.ability(PlantSoil.DESERT.configId(), "attackSpeedReduction", -1), EPSILON);
        assertEquals(0.30, defaults.ability(PlantSoil.DESERT.configId(), "thornReflectRatio", -1), EPSILON);
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

        assertEquals(176.0, baseDps * 1.6 * critMultiplier, 0.5);
        assertEquals(222.0, baseDps * (1.6 + 0.015 * soilPower * 20) * critMultiplier, 0.5);
        assertEquals(268.0, baseDps * (1.6 + 0.6 * soilPower) * critMultiplier, 0.5);
        assertEquals(335.0, baseDps * (1.6 + 0.6 * soilPower) * critMultiplier * 1.25, 0.6);

        assertEffectiveTargetMultiplier(PlantTowers.T3_MEADOW_NOVA_TOWER, "novaDamageRatio", 1.0, 2.5, 4.0);
        assertEffectiveTargetMultiplier(PlantTowers.T3_PODZOL_LILAC_TOWER, "splashDamageRatio", 1.0, 1.9, 2.8);
        assertEffectiveTargetMultiplier(PlantTowers.T3_PODZOL_PITCHER_TOWER, "splashDamageRatio", 1.0, 2.2, 3.4);

        var mine = TowerBalanceRuntime.resolve(PlantTowers.T3_MYCELIUM_TOWER);
        double explosion = (mine.damage() * 2.0 + mine.maxHealth() * 0.25) * 1.6;
        assertEquals(344.0, explosion, EPSILON);
    }

    /**
     * 지뢰는 폭발 한 번이 아니라 라운드 하나를 삽니다.
     *
     * <p>재장전이 없으면 감시 간격(5틱)마다 다시 터져 지뢰 하나가 광역 기관총이 됩니다. 삭는
     * 사슬이 끊기면 지뢰가 영원히 남습니다. 둘 다 값 하나만 잘못 들어가도 조용히 깨지는 곳이라
     * 여기서 붙잡습니다.
     */
    @Test
    void minesRearmAndDecayOneTierPerRound() {
        assertEquals(PlantTowers.T2_MYCELIUM_TOWER,
                PlantTowers.previousMyceliumTier(PlantTowers.T3_MYCELIUM_TOWER));
        assertEquals(PlantTowers.T1_MYCELIUM_TOWER,
                PlantTowers.previousMyceliumTier(PlantTowers.T2_MYCELIUM_TOWER));
        assertNull(PlantTowers.previousMyceliumTier(PlantTowers.T1_MYCELIUM_TOWER),
                "붉은 버섯 아래는 없습니다. 라운드가 끝나면 사라져야 합니다.");
        assertNull(PlantTowers.previousMyceliumTier(PlantTowers.T3_DESERT_TOWER),
                "다른 계열은 삭지 않습니다.");

        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        for (TowerType mine : List.of(
                PlantTowers.T1_MYCELIUM_TOWER, PlantTowers.T2_MYCELIUM_TOWER, PlantTowers.T3_MYCELIUM_TOWER)) {
            // 라운드 안에서 다시 장전하는 값은 두지 않습니다. 두는 순간 무력화 시간과의 관계에
            // 따라 그 길목의 적이 영영 공격하지 못하는 장판이 만들어집니다.
            assertEquals(0.0, defaults.ability(mine.id(), "rearmTicks", 0.0), EPSILON,
                    mine.id() + ": 라운드당 한 번이라 재장전 값이 있으면 안 됩니다");
            // 도화선이 있어야 밟은 쪽에 빠져나갈 여지가 생깁니다. 0 이면 즉발입니다.
            assertTrue(defaults.ability(mine.id(), "fuseTicks", -1) > 0.0,
                    mine.id() + ": 도화선이 0 이면 밟는 순간 이미 맞은 뒤라 피할 수 없습니다");
        }
        assertPlantConfigRejected(defaults, PlantTowers.T1_MYCELIUM_TOWER.id(), "fuseTicks", 0.0);
        assertPlantConfigRejected(defaults, PlantTowers.T1_MYCELIUM_TOWER.id(), "fuseTicks", 8.5);
    }

    /**
     * 지뢰는 몬스터의 표적이 아닙니다.
     *
     * <p>터지고 사라지던 시절에는 상관없었지만, 라운드 내내 남게 된 지금 어그로를 끌면 지뢰가
     * 사암 탱커보다 싼 고기방패가 됩니다. 붉은 버섯은 다이아당 체력이 죽은 덤불보다 높습니다.
     */
    @Test
    void minesAreTrapsNotBodiesToChewOn() {
        for (TowerType type : List.of(
                PlantTowers.T1_MYCELIUM_TOWER, PlantTowers.T2_MYCELIUM_TOWER, PlantTowers.T3_MYCELIUM_TOWER)) {
            assertFalse(create(type).drawsAggro(),
                    type.id() + ": 지뢰가 어그로를 끌면 도배 벽이 사암보다 싸게 세워집니다");
        }
        assertTrue(create(PlantTowers.T1_DESERT_TOWER).drawsAggro(),
                "탱커는 계속 어그로를 끌어야 합니다. 그게 그 계열의 역할입니다.");

        var t1Mine = TowerBalanceRuntime.resolve(PlantTowers.T1_MYCELIUM_TOWER);
        var t1Tank = TowerBalanceRuntime.resolve(PlantTowers.T1_DESERT_TOWER);
        assertTrue(t1Mine.maxHealth() / t1Mine.mineralCost() > t1Tank.maxHealth() / t1Tank.mineralCost(),
                "이 테스트의 전제입니다. 지뢰가 탱커보다 다이아당 체력이 낮아지면 어그로 여부를 "
                        + "다시 판단해도 됩니다.");
    }

    /**
     * 죽은 덤불을 도배해 레인을 막는 것을 막습니다.
     *
     * <p>벽의 값은 개수가 아니라 총 체력입니다. 시작 다이아 안에서 살 수 있는 T1 탱커 벽의 체력이
     * 그대로면 비용만 만져 봐야 소용이 없습니다.
     */
    @Test
    void tierOneTankCannotWallTheLaneOnTheOpeningBudget() {
        var t1 = TowerBalanceRuntime.resolve(PlantTowers.T1_DESERT_TOWER);
        var t2 = TowerBalanceRuntime.resolve(PlantTowers.T2_DESERT_TOWER);
        var t3 = TowerBalanceRuntime.resolve(PlantTowers.T3_DESERT_TOWER);

        // 오프닝 예산은 그대로 둡니다. 여기를 올리면 사암 계열로는 시작조차 못 합니다.
        assertEquals(50L, t1.mineralCost());
        // 다이아당 체력이 상위 티어보다 높으면, 올리는 것보다 도배하는 쪽이 언제나 이깁니다.
        double t1PerMineral = t1.maxHealth() / t1.mineralCost();
        assertTrue(t1PerMineral <= t2.maxHealth() / t2.mineralCost(),
                "T1 다이아당 체력 " + t1PerMineral + " 이 T2 보다 높으면 도배가 정답이 됩니다");
        assertTrue(t1PerMineral <= t3.maxHealth() / t3.mineralCost(),
                "T1 다이아당 체력 " + t1PerMineral + " 이 T3 보다 높으면 도배가 정답이 됩니다");
    }

    /**
     * 잔디를 겹쳐 두면 회복이 개수만큼 곱절로 늘던 것을 깎습니다.
     *
     * <p>같은 대상에 붙는 두 번째 회복부터가 감산 대상입니다. 겹치기를 아예 막지는 않습니다 -
     * 잔디는 후방 지원 지형이고 여러 개 두는 것 자체는 정상적인 운영입니다.
     */
    @Test
    void stackedMeadowHealsLoseHalfOfTheOverlap() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        double reduction = defaults.ability(PlantTowers.GLOBAL_CONFIG_ID, "meadowHealOverlapReduction", -1);
        assertEquals(0.5, reduction, EPSILON);
        assertPlantConfigRejected(defaults, PlantTowers.GLOBAL_CONFIG_ID, "meadowHealOverlapReduction", 1.01);
        assertPlantConfigRejected(defaults, PlantTowers.GLOBAL_CONFIG_ID, "meadowHealOverlapReduction", -0.01);

        // 겹침 판정 창은 펄스 간격을 그대로 씁니다. 잔디들의 펄스는 서로 맞춰져 있지 않아서
        // "같은 펄스"라는 것이 없고, 창이 펄스보다 짧으면 감산이 그냥 새 버립니다.
        assertTrue(defaults.ability(PlantTowers.GLOBAL_CONFIG_ID, "soilPulseIntervalTicks", -1) > 0.0);
    }

    /**
     * 반사의 고정항이 도배 벽의 실제 화력이었습니다.
     *
     * <p>반사는 <b>때린 몹 하나</b>에게만 돌아가지만, 공식이 {@code 받은 피해 × 비율 + 타워 공격력}
     * 이라 맞은 크기와 무관하게 매 타격마다 고정값이 나갑니다. 값싼 탱커를 스무 개 깔면 그만큼의
     * 독립된 딜러가 되는 셈이라, 체력만 깎아서는 도배가 계속 통했습니다.
     *
     * <p>고정항을 아예 없애지는 않았습니다. 없애면 작은 공격에는 반사가 사실상 사라져 계열의
     * 역할이 무너집니다. 티어가 오를수록 덜 깎이는 곡선으로 두어, 도배보다 올리는 쪽이 낫게
     * 유지합니다.
     */
    @Test
    void thornReflectIsSingleTargetAndItsFlatTermFavoursUpgrades() {
        var t1 = TowerBalanceRuntime.resolve(PlantTowers.T1_DESERT_TOWER);
        var t2 = TowerBalanceRuntime.resolve(PlantTowers.T2_DESERT_TOWER);
        var t3 = TowerBalanceRuntime.resolve(PlantTowers.T3_DESERT_TOWER);

        // 사암 계열은 스스로 공격하지 않습니다. 사거리가 0 이라 damage 는 반사에만 쓰입니다.
        assertEquals(0.0, t1.range(), EPSILON);
        assertEquals(0.0, t2.range(), EPSILON);
        assertEquals(0.0, t3.range(), EPSILON);

        assertTrue(t1.damage() > 0.0, "고정항이 0 이면 작은 공격에는 반사가 없는 것과 같습니다");
        assertTrue(t1.damage() < t2.damage() && t2.damage() < t3.damage(),
                "티어가 오르면 반사 고정항도 올라야 올릴 이유가 생깁니다");

        // 다이아당 고정 반사가 T1 에서 가장 높으면 도배가 다시 정답이 됩니다.
        double t1PerMineral = t1.damage() / t1.mineralCost();
        assertTrue(t1PerMineral <= t2.damage() / t2.mineralCost(),
                "T1 다이아당 고정 반사 " + t1PerMineral + " 이 T2 보다 높습니다");
        assertTrue(t1PerMineral <= t3.damage() / t3.mineralCost(),
                "T1 다이아당 고정 반사 " + t1PerMineral + " 이 T3 보다 높습니다");
    }

    /**
     * 식물은 못 움직이므로 사거리가 곧 생존과 기여입니다.
     *
     * <p>민들레 계열은 어그로가 가장 낮아 뒤에 서는데, 사거리가 짧으면 뒤에 선 채로는 아무것도
     * 못 합니다. 고사리 계열은 딜러인데 사거리가 짧으면 맞아 가며 쏴야 합니다. 둘 다 티어가
     * 오를수록 길어지는 순서만은 깨지지 않아야 합니다.
     */
    @Test
    void rootedLinesKeepTheirRangeOrderAcrossTiers() {
        double dandelion = TowerBalanceRuntime.resolve(PlantTowers.T1_MEADOW_TOWER).range();
        double daisy = TowerBalanceRuntime.resolve(PlantTowers.T2_MEADOW_TOWER).range();
        double sunflower = TowerBalanceRuntime.resolve(PlantTowers.T3_MEADOW_TOWER).range();
        assertTrue(dandelion < daisy && daisy < sunflower,
                "민들레 계열 사거리 " + dandelion + " / " + daisy + " / " + sunflower);

        double fern = TowerBalanceRuntime.resolve(PlantTowers.T1_PODZOL_TOWER).range();
        double largeFern = TowerBalanceRuntime.resolve(PlantTowers.T2_PODZOL_TOWER).range();
        assertTrue(fern < largeFern, "고사리 계열 사거리 " + fern + " -> " + largeFern);
        for (TowerType finisher : List.of(
                PlantTowers.T3_PODZOL_LILAC_TOWER,
                PlantTowers.T3_PODZOL_ROSE_TOWER,
                PlantTowers.T3_PODZOL_PITCHER_TOWER)) {
            assertTrue(TowerBalanceRuntime.resolve(finisher).range() >= largeFern,
                    finisher.id() + " 는 큰 고사리보다 사거리가 짧으면 안 됩니다");
        }

        // 자기 중심 광역인 튤립 계열은 붙어서 싸우는 역할이라 짧게 둡니다. 이쪽까지 같이
        // 늘리면 근접 광역이라는 구분이 사라집니다.
        assertTrue(TowerBalanceRuntime.resolve(PlantTowers.T3_MEADOW_NOVA_TOWER).range() < sunflower,
                "횃불꽃이 해바라기보다 멀리 쏘면 근접 광역이라는 역할이 사라집니다");
    }

    /**
     * 탱커가 계열 중에서 가장 단단해야 합니다.
     *
     * <p>잔디 계열은 다이아당 체력이 사암 탱커보다 높았습니다. 민들레는 탱커의 두 배였습니다.
     * 뒤에서 회복과 정산을 담당하는 라인이 앞에서 맞아 주는 라인보다 단단하면, 탱커를 세울
     * 이유가 없어집니다. 잔디는 서로 회복까지 하므로 실제 내구력은 기본 체력보다 더 높습니다.
     */
    @Test
    void supportLinesAreLessDurablePerDiamondThanTheTankLine() {
        for (int tier = 1; tier <= 3; tier++) {
            double tank = healthPerDiamond(switch (tier) {
                case 1 -> PlantTowers.T1_DESERT_TOWER;
                case 2 -> PlantTowers.T2_DESERT_TOWER;
                default -> PlantTowers.T3_DESERT_TOWER;
            });
            double economy = healthPerDiamond(switch (tier) {
                case 1 -> PlantTowers.T1_MEADOW_TOWER;
                case 2 -> PlantTowers.T2_MEADOW_TOWER;
                default -> PlantTowers.T3_MEADOW_TOWER;
            });
            double nova = healthPerDiamond(switch (tier) {
                case 1 -> PlantTowers.T1_MEADOW_NOVA_TOWER;
                case 2 -> PlantTowers.T2_MEADOW_NOVA_TOWER;
                default -> PlantTowers.T3_MEADOW_NOVA_TOWER;
            });
            assertTrue(economy < tank,
                    "T" + tier + " 민들레 계열 " + economy + " 이 탱커 " + tank + " 보다 단단합니다");
            assertTrue(nova < tank,
                    "T" + tier + " 튤립 계열 " + nova + " 이 탱커 " + tank + " 보다 단단합니다");
        }
    }

    private static double healthPerDiamond(TowerType type) {
        var resolved = TowerBalanceRuntime.resolve(type);
        return resolved.maxHealth() / resolved.mineralCost();
    }

    @Test
    void midAndLateTiersPayBackTheirTerrainInvestment() {
        assertEquals(20.0, TowerBalanceRuntime.resolve(PlantTowers.T3_MEADOW_TOWER).damage(), EPSILON);
        assertEquals(34.0, TowerBalanceRuntime.resolve(PlantTowers.T3_MEADOW_NOVA_TOWER).damage(), EPSILON);
        assertEquals(460.0, TowerBalanceRuntime.resolve(PlantTowers.T3_MYCELIUM_TOWER).maxHealth(), EPSILON);
        assertEquals(750.0, TowerBalanceRuntime.resolve(PlantTowers.T3_DESERT_TOWER).maxHealth(), EPSILON);
        assertEquals(34.0, TowerBalanceRuntime.resolve(PlantTowers.T3_PODZOL_LILAC_TOWER).damage(), EPSILON);
        assertEquals(48.0, TowerBalanceRuntime.resolve(PlantTowers.T3_PODZOL_PITCHER_TOWER).damage(), EPSILON);
    }

    @Test
    void plantConfigRejectsInvalidRatiosTicksAndAuraBounds() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertDoesNotThrow(defaults::validateForRuntime);
        assertPlantConfigRejected(defaults, PlantTowers.GLOBAL_CONFIG_ID, "bloomDamageCap", 1.01);
        assertPlantConfigRejected(defaults, PlantTowers.T1_MEADOW_TOWER.id(), "diamondPerWave", 3.5);
        assertPlantConfigRejected(defaults, PlantTowers.T1_MYCELIUM_TOWER.id(), "explosionDisableTicks", 20.5);
        assertPlantConfigRejected(defaults, PlantSoil.PODZOL.configId(), "growthShareRatio", 1.01);
        assertPlantConfigRejected(defaults, PlantSoil.PODZOL.configId(), "supportDurationTicks", 20.5);

        Map<String, Map<String, Double>> abilities = mutableAbilities(defaults);
        abilities.get(PlantTowers.GLOBAL_CONFIG_ID).put("soilAuraMinRadius", 7.0);
        assertThrows(IllegalArgumentException.class, () -> configWithAbilities(defaults, abilities).validateForRuntime());
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
        assertEquals(11.0, merged.ability(PlantTowers.T2_MEADOW_TOWER.id(), "diamondPerWave", -1), EPSILON);
        assertEquals(0.2, merged.ability(PlantSoil.PODZOL.configId(), "growthShareRatio", -1), EPSILON);
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
