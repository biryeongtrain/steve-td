package kim.biryeong.semiontd.tower.frost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.entity.boss.BossMonster;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.FrostTowerJob;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

class FrostTowerTest {
    private static final UUID OWNER = UUID.fromString("18c0c74c-e45f-4aa7-bd47-d5fd67f26fc1");
    private static final UUID ALLY = UUID.fromString("a2741384-77b8-4713-9047-a666304605b8");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void reload() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void catalogContainsSixteenTowersAndSixStarters() {
        List<ProductionTowerCatalog.CatalogEntry> entries = ProductionTowerCatalog.all().stream()
                .filter(entry -> FrostTowers.isFrostTower(entry.type()))
                .toList();

        assertEquals(16, entries.size());
        assertEquals(6, entries.stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count());
        assertEquals(List.of(
                FrostTowers.ICE_VANGUARD.id(),
                FrostTowers.ICE_BREAKER_T1.id(),
                FrostTowers.FROZEN_DUMPLING_T1.id(),
                FrostTowers.ICEBOX_T1.id(),
                FrostTowers.EMISSION_COOLING_DEVICE.id(),
                FrostTowers.ERUPTION_COOLING_DEVICE.id()
        ), entries.stream()
                .filter(ProductionTowerCatalog.CatalogEntry::starter)
                .map(entry -> entry.type().id())
                .toList());
        assertTrue(ProductionTowerCatalog.find(FrostTowers.ICE_VANGUARD.id()).orElseThrow().starter());
        assertTrue(ProductionTowerCatalog.find(FrostTowers.EMISSION_COOLING_DEVICE.id()).orElseThrow().starter());
        assertTrue(ProductionTowerCatalog.find(FrostTowers.ERUPTION_COOLING_DEVICE.id()).orElseThrow().starter());
        assertTrue(ProductionTowerCatalog.find(FrostTowers.ICE_BREAKER_T1.id()).orElseThrow().starter());
        assertTrue(ProductionTowerCatalog.find(FrostTowers.FROZEN_DUMPLING_T1.id()).orElseThrow().starter());
        assertTrue(ProductionTowerCatalog.find(FrostTowers.ICEBOX_T1.id()).orElseThrow().starter());
        assertFalse(ProductionTowerCatalog.find(FrostTowers.STURDY_ICE_VANGUARD.id()).orElseThrow().starter());
        assertFalse(ProductionTowerCatalog.find(FrostTowers.DONGTAE.id()).orElseThrow().starter());
        assertFalse(ProductionTowerCatalog.find(
                FrostTowers.EMISSION_COOLING_DEVICE_EXPANDED.id()).orElseThrow().starter());
        assertFalse(ProductionTowerCatalog.find(
                FrostTowers.ERUPTION_COOLING_DEVICE_EXPANDED.id()).orElseThrow().starter());
        assertFalse(ProductionTowerCatalog.find(FrostTowers.ICE_BREAKER_T2.id()).orElseThrow().starter());
        assertFalse(ProductionTowerCatalog.find(FrostTowers.ICE_BREAKER_T3.id()).orElseThrow().starter());
        assertFalse(ProductionTowerCatalog.find(FrostTowers.FROZEN_DUMPLING_T2.id()).orElseThrow().starter());
        assertFalse(ProductionTowerCatalog.find(FrostTowers.FROZEN_DUMPLING_T3.id()).orElseThrow().starter());
        assertFalse(ProductionTowerCatalog.find(FrostTowers.ICEBOX_T2.id()).orElseThrow().starter());
        assertFalse(ProductionTowerCatalog.find(FrostTowers.ICEBOX_T3.id()).orElseThrow().starter());
    }

    @Test
    void combatTowerNamesEndWithTowerWhileCoolingDeviceNamesStayUnchanged() {
        assertEquals("얼음 파쇄병 타워", FrostTowers.ICE_BREAKER_T1.displayName());
        assertEquals("동토 분쇄자 타워", FrostTowers.ICE_BREAKER_T2.displayName());
        assertEquals("제상 히터 타워", FrostTowers.ICE_BREAKER_T3.displayName());
        assertEquals("얼음 요리사 타워", FrostTowers.FROZEN_DUMPLING_T1.displayName());
        assertEquals("빙하 주계병 타워", FrostTowers.FROZEN_DUMPLING_T2.displayName());
        assertEquals("냉동 식품 타워", FrostTowers.FROZEN_DUMPLING_T3.displayName());
        assertEquals("얼음 구호병 타워", FrostTowers.ICEBOX_T1.displayName());
        assertEquals("혹한 의무관 타워", FrostTowers.ICEBOX_T2.displayName());
        assertEquals("아이스박스 타워", FrostTowers.ICEBOX_T3.displayName());
        assertEquals("냉기 방출 장치", FrostTowers.EMISSION_COOLING_DEVICE.displayName());
        assertEquals("산업용 냉기 순환팬", FrostTowers.EMISSION_COOLING_DEVICE_EXPANDED.displayName());
        assertEquals("냉기 분출 장치", FrostTowers.ERUPTION_COOLING_DEVICE.displayName());
        assertEquals("중앙 냉매 압축기", FrostTowers.ERUPTION_COOLING_DEVICE_EXPANDED.displayName());

        FrostTowers.all().stream()
                .filter(type -> !FrostTowers.isCoolingDevice(type))
                .forEach(type -> assertTrue(type.displayName().endsWith("타워"), type.displayName()));
    }

    @Test
    void catalogUsesDirectedCostsAndExpectedRuntimeClasses() {
        assertEquals(90, TowerBalanceRuntime.upgradeCost(
                FrostTowers.ICE_VANGUARD,
                FrostTowers.STURDY_ICE_VANGUARD.id(),
                -1
        ));
        assertEquals(170, TowerBalanceRuntime.upgradeCost(
                FrostTowers.STURDY_ICE_VANGUARD,
                FrostTowers.DONGTAE.id(),
                -1
        ));
        assertTrue(ProductionTowerCatalog.upgrade(
                FrostTowers.ICE_VANGUARD,
                FrostTowers.STURDY_ICE_VANGUARD.id()
        ).isPresent());
        assertTrue(ProductionTowerCatalog.upgrade(
                FrostTowers.STURDY_ICE_VANGUARD,
                FrostTowers.DONGTAE.id()
        ).isPresent());
        assertUpgrade(FrostTowers.ERUPTION_COOLING_DEVICE,
                FrostTowers.ERUPTION_COOLING_DEVICE_EXPANDED, 1000);
        assertUpgrade(FrostTowers.EMISSION_COOLING_DEVICE,
                FrostTowers.EMISSION_COOLING_DEVICE_EXPANDED, 500);
        assertUpgrade(FrostTowers.ICE_BREAKER_T1, FrostTowers.ICE_BREAKER_T2, 100);
        assertUpgrade(FrostTowers.ICE_BREAKER_T2, FrostTowers.ICE_BREAKER_T3, 200);
        assertUpgrade(FrostTowers.FROZEN_DUMPLING_T1, FrostTowers.FROZEN_DUMPLING_T2, 100);
        assertUpgrade(FrostTowers.FROZEN_DUMPLING_T2, FrostTowers.FROZEN_DUMPLING_T3, 200);
        assertUpgrade(FrostTowers.ICEBOX_T1, FrostTowers.ICEBOX_T2, 145);
        assertUpgrade(FrostTowers.ICEBOX_T2, FrostTowers.ICEBOX_T3, 225);

        GridPosition position = new GridPosition(0, 64, 0);
        assertInstanceOf(FrostVanguardTower.class, create(FrostTowers.ICE_VANGUARD, position));
        assertInstanceOf(FrostVanguardTower.class, create(FrostTowers.DONGTAE, position));
        FrostCoolingTower emission = assertInstanceOf(
                FrostCoolingTower.class,
                create(FrostTowers.EMISSION_COOLING_DEVICE, position)
        );
        assertInstanceOf(
                FrostCoolingTower.class,
                create(FrostTowers.EMISSION_COOLING_DEVICE_EXPANDED, position)
        );
        FrostEruptionCoolingTower eruption = assertInstanceOf(
                FrostEruptionCoolingTower.class,
                create(FrostTowers.ERUPTION_COOLING_DEVICE_EXPANDED, position)
        );
        assertInstanceOf(FrostSplashTower.class, create(FrostTowers.ICE_BREAKER_T3, position));
        assertInstanceOf(FrostSplashTower.class, create(FrostTowers.FROZEN_DUMPLING_T3, position));
        assertInstanceOf(FrostHealingTower.class, create(FrostTowers.ICEBOX_T3, position));
        assertFalse(emission.drawsAggro());
        assertFalse(eruption.drawsAggro());
        assertFalse(emission.countsForLaneDefense());
        assertFalse(emission.participatesInFinalDefense());
        assertFalse(eruption.countsForLaneDefense());
        assertFalse(eruption.participatesInFinalDefense());
    }

    @Test
    void bundledStatsAndDescriptionsMatchTheLockedDesign() {
        TowerBalanceConfig config = TowerBalanceConfig.defaultConfig();
        assertEquals(0.15, config.ability(FrostBalance.CONFIG_ID, "chillPerHit", -1.0));
        assertEquals(0.15, config.ability(FrostBalance.CONFIG_ID, "refrigerantDamageReduction", -1.0));
        assertEquals(0.15, config.ability(FrostBalance.CONFIG_ID, "refrigerantAttackSpeedReduction", -1.0));
        assertStats(config, FrostTowers.ICE_VANGUARD, 40, 80.0, 5.0, 20);
        assertStats(config, FrostTowers.STURDY_ICE_VANGUARD, 0, 180.0, 9.0, 20);
        assertStats(config, FrostTowers.DONGTAE, 0, 400.0, 16.0, 20);
        assertStats(config, FrostTowers.EMISSION_COOLING_DEVICE, 300, 100.0, 1.0, 40);
        assertStats(config, FrostTowers.EMISSION_COOLING_DEVICE_EXPANDED, 0, 100.0, 1.0, 20);
        assertStats(config, FrostTowers.ERUPTION_COOLING_DEVICE,
                800, 100.0, 0.0, Integer.MAX_VALUE);
        assertStats(config, FrostTowers.ERUPTION_COOLING_DEVICE_EXPANDED,
                0, 100.0, 0.0, Integer.MAX_VALUE);
        assertStats(config, FrostTowers.ICE_BREAKER_T1, 55, 70.0, 10.0, 20);
        assertStats(config, FrostTowers.ICE_BREAKER_T2, 0, 70.0, 10.0, 17);
        assertStats(config, FrostTowers.ICE_BREAKER_T3, 0, 70.0, 20.0, 17);
        assertStats(config, FrostTowers.FROZEN_DUMPLING_T1, 55, 70.0, 12.0, 20);
        assertStats(config, FrostTowers.FROZEN_DUMPLING_T2, 0, 70.0, 30.0, 17);
        assertStats(config, FrostTowers.FROZEN_DUMPLING_T3, 0, 70.0, 45.0, 14);
        assertStats(config, FrostTowers.ICEBOX_T1, 45, 100.0, 0.0, 100);
        assertStats(config, FrostTowers.ICEBOX_T2, 0, 100.0, 0.0, 100);
        assertStats(config, FrostTowers.ICEBOX_T3, 0, 100.0, 0.0, 100);
        assertEquals(3.0, config.statsFor(FrostTowers.ICEBOX_T1).range());
        assertEquals(3.0, config.statsFor(FrostTowers.ICEBOX_T2).range());
        assertEquals(3.0, config.statsFor(FrostTowers.ICEBOX_T3).range());
        assertEquals(50.0, config.ability(
                FrostTowers.EMISSION_COOLING_DEVICE.id(), "waveRange", -1.0
        ));
        assertEquals(7.0, config.ability(
                FrostTowers.EMISSION_COOLING_DEVICE.id(), "waveWidth", -1.0
        ));
        assertEquals(50.0, config.ability(
                FrostTowers.EMISSION_COOLING_DEVICE_EXPANDED.id(), "waveRange", -1.0
        ));
        assertEquals(2.0, config.ability(FrostTowers.ICE_BREAKER_T1.id(), "splashRadius", -1.0));
        assertEquals(2.5, config.ability(FrostTowers.ICE_BREAKER_T2.id(), "splashRadius", -1.0));
        assertEquals(3.0, config.ability(FrostTowers.ICE_BREAKER_T3.id(), "splashRadius", -1.0));
        assertEquals(0.8, config.ability(FrostTowers.FROZEN_DUMPLING_T1.id(), "splashRadius", -1.0));
        assertEquals(1.5, config.ability(FrostTowers.FROZEN_DUMPLING_T2.id(), "splashRadius", -1.0));
        assertEquals(3.0, config.ability(FrostTowers.FROZEN_DUMPLING_T3.id(), "splashRadius", -1.0));
        assertEquals(3.0, config.ability(
                FrostTowers.FROZEN_DUMPLING_T1.id(), "frozenFoodDamageBonusAt3", -1.0));
        assertEquals(5.0, config.ability(
                FrostTowers.FROZEN_DUMPLING_T2.id(), "frozenFoodDamageBonusAt3", -1.0));
        assertEquals(10.0, config.ability(
                FrostTowers.FROZEN_DUMPLING_T3.id(), "frozenFoodDamageBonusAt3", -1.0));
        assertEquals(0.10, config.ability(
                FrostTowers.FROZEN_DUMPLING_T1.id(), "frozenFoodIncomeDamageBonusAt9", -1.0));
        assertEquals(0.20, config.ability(
                FrostTowers.FROZEN_DUMPLING_T2.id(), "frozenFoodIncomeDamageBonusAt9", -1.0));
        assertEquals(0.30, config.ability(
                FrostTowers.FROZEN_DUMPLING_T3.id(), "frozenFoodIncomeDamageBonusAt9", -1.0));
        assertEquals(22.5, config.ability(FrostTowers.ICEBOX_T1.id(), "healAmount", -1.0));
        assertEquals(52.5, config.ability(FrostTowers.ICEBOX_T2.id(), "healAmount", -1.0));
        assertEquals(120.0, config.ability(FrostTowers.ICEBOX_T3.id(), "healAmount", -1.0));
        assertEquals(6.0, config.ability(FrostTowers.ICEBOX_T1.id(), "healRadius", -1.0));
        assertEquals(7.0, config.ability(FrostTowers.ICEBOX_T2.id(), "healRadius", -1.0));
        assertEquals(8.0, config.ability(FrostTowers.ICEBOX_T3.id(), "healRadius", -1.0));
        assertEquals(100, config.abilityTicks(FrostTowers.ICEBOX_T3.id(), "healIntervalTicks", -1));
        assertEquals(0.05, config.ability(FrostBalance.CONFIG_ID, "healerDamageReductionAt3", -1.0));
        assertEquals(0.07, config.ability(FrostBalance.CONFIG_ID, "healerDamageReductionAt6", -1.0));
        assertEquals(0.10, config.ability(FrostBalance.CONFIG_ID, "healerDamageReductionAt9", -1.0));
        assertEquals(0.10, config.ability(FrostBalance.CONFIG_ID, "fullyFrozenDamageReduction", -1.0));
        assertEquals(20, config.abilityTicks(FrostBalance.CONFIG_ID, "fullyFrozenDurationTicks", -1));
        assertEquals(3.0, config.ability(FrostBalance.CONFIG_ID, "fullyFrozenChillRadius", -1.0));
        assertEquals(9, config.abilityTicks(FrostBalance.CONFIG_ID, "fullOperationRequiredActivations", -1));
        assertEquals(3.0, config.ability(FrostBalance.CONFIG_ID, "fullOperationEruptionChill", -1.0));
        assertEquals(100, config.abilityTicks(FrostBalance.CONFIG_ID, "fullOperationDurationTicks", -1));
        assertEquals(0.95, config.ability(FrostBalance.CONFIG_ID, "fullOperationDamageReduction", -1.0));
        assertEquals(5.0, config.ability(FrostBalance.CONFIG_ID, "fullOperationFixedAttackDamage", -1.0));
        assertEquals(10, config.abilityTicks(FrostBalance.CONFIG_ID, "healerCoolingAdvanceTicks", -1));
        assertEquals(1.41, config.ability(
                FrostBalance.CONFIG_ID, "healerRefrigerantPulseMultiplier", -1.0));
        assertEquals(1.0, config.ability(
                FrostBalance.CONFIG_ID, "frozenFoodSplashRadiusBonusAt6", -1.0));
        assertEquals(3, config.abilityTicks(
                FrostBalance.CONFIG_ID, "frozenFoodRefrigerantBonusAttacks", -1));

        for (TowerType type : FrostTowers.all()) {
            String description = String.join("\n", TowerBalanceRuntime.resolve(type).description());
            assertFalse(description.contains("{"), type.id() + " left an unresolved placeholder: " + description);
        }
        assertTrue(String.join("\n", TowerBalanceRuntime.resolve(FrostTowers.EMISSION_COOLING_DEVICE).description())
                .contains("50블록"));
        assertTrue(String.join("\n", TowerBalanceRuntime.resolve(FrostTowers.ICE_BREAKER_T3).description())
                .contains("즉시 한 번의 추가 공격"));
        assertTrue(String.join("\n", TowerBalanceRuntime.resolve(FrostTowers.FROZEN_DUMPLING_T2).description())
                .contains("공격력이 5 증가"));
        assertTrue(String.join("\n", TowerBalanceRuntime.resolve(FrostTowers.ICEBOX_T3).description())
                .contains("반경 8블록"));
        assertTrue(String.join("\n", TowerBalanceRuntime.resolve(FrostTowers.DONGTAE).description())
                .contains("다음 1초간 받는 피해를 10% 경감"));
        assertTrue(String.join("\n", TowerBalanceRuntime.resolve(FrostTowers.ICEBOX_T3).description())
                .contains("5%/7%/10% 감소"));
        assertTrue(String.join("\n", TowerBalanceRuntime.resolve(
                        FrostTowers.ERUPTION_COOLING_DEVICE_EXPANDED).description())
                .contains("여러 혹한 빌더의 효과 중 가장 강한 하나만 적용"));
        assertTrue(BlockDisplayVisual.matches(FrostTowers.EMISSION_COOLING_DEVICE.visual()));
        assertTrue(BlockDisplayVisual.matches(FrostTowers.EMISSION_COOLING_DEVICE_EXPANDED.visual()));
        assertEquals(
                Blocks.BLUE_ICE.defaultBlockState(),
                BlockDisplayVisual.blockState(FrostTowers.EMISSION_COOLING_DEVICE.visual())
        );
        assertTrue(BlockDisplayVisual.matches(FrostTowers.ERUPTION_COOLING_DEVICE.visual()));
        assertTrue(BlockDisplayVisual.matches(FrostTowers.ICE_BREAKER_T1.visual()));
        assertTrue(BlockDisplayVisual.matches(FrostTowers.FROZEN_DUMPLING_T1.visual()));
        assertEquals(Blocks.SMOKER.defaultBlockState(),
                BlockDisplayVisual.blockState(FrostTowers.FROZEN_DUMPLING_T3.visual()));
        assertEquals(
                Blocks.BARREL.defaultBlockState(),
                BlockDisplayVisual.blockState(FrostTowers.ICEBOX_T3.visual())
        );
        assertEquals("minecraft:snow_golem", FrostTowers.ICE_VANGUARD.visual().entityTypeId());
        assertEquals(false, FrostTowers.ICE_VANGUARD.visual().properties().get("snow_golem_has_pumpkin"));
        assertEquals(0.75, FrostTowers.ICE_VANGUARD.visual().scale());
        assertEquals("minecraft:snow_golem", FrostTowers.STURDY_ICE_VANGUARD.visual().entityTypeId());
        assertEquals(false, FrostTowers.STURDY_ICE_VANGUARD.visual().properties().get("snow_golem_has_pumpkin"));
        assertEquals(1.0, FrostTowers.STURDY_ICE_VANGUARD.visual().scale());
        assertEquals("minecraft:axolotl", FrostTowers.DONGTAE.visual().entityTypeId());
        assertEquals(Axolotl.Variant.BLUE, FrostTowers.DONGTAE.visual().properties().get("axolotl_variant"));
        assertEquals(1.2, FrostTowers.DONGTAE.visual().scale());
        assertTrue(BlockDisplayVisual.matches(FrostTowers.ICE_BREAKER_T3.visual()));
        assertEquals(Blocks.BLAST_FURNACE.defaultBlockState(),
                BlockDisplayVisual.blockState(FrostTowers.ICE_BREAKER_T3.visual()));
    }

    @Test
    void iceboxConsumesRefrigerantImmediatelyAfterSevenCoolingWaveHits() {
        FrostHealingTower healer = new FrostHealingTower(
                FrostTowers.ICEBOX_T1,
                OWNER,
                TeamId.RED,
                1,
                new GridPosition(0, 64, 0)
        );

        for (int hit = 1; hit < 7; hit++) {
            healer.onEmissionWaveHit(null);
            assertEquals(hit * 0.15, healer.chillForTest(), 0.0001);
        }
        healer.onEmissionWaveHit(null);
        assertEquals(0.0, healer.chillForTest(), 0.0001);
    }

    @Test
    void fullOperationGainsThreeStacksPerActivationCycleAndCapsEachFamilyAtThree() {
        FrostFullOperationService.clearPlayer(OWNER);
        FrostFullOperationService.PlayerState state = FrostFullOperationService.stateForTest(OWNER);
        state.beginWave();

        long[] cycleTicks = {100L, 200L, 300L};
        for (int cycle = 0; cycle < cycleTicks.length; cycle++) {
            long tick = cycleTicks[cycle];
            for (FrostFullOperationService.TriggerFamily family
                    : FrostFullOperationService.TriggerFamily.values()) {
                assertTrue(state.record(family, tick));
                assertFalse(state.record(family, tick),
                        "Multiple towers in one family must count only once in the same activation cycle.");
            }
            assertEquals((cycle + 1) * 3, state.totalActivations());
        }

        for (FrostFullOperationService.TriggerFamily family
                : FrostFullOperationService.TriggerFamily.values()) {
            assertFalse(state.record(family, 400L), "Each family must contribute at most three times per wave.");
            assertEquals(3, state.familyActivations(family));
        }
        FrostFullOperationService.clearPlayer(OWNER);
    }

    @Test
    void fullOperationFixesOrdinaryDamageAtFiveAndIncomingDamageAtFivePercent() {
        FrostFullOperationService.clearPlayer(OWNER);
        FrostFullOperationService.PlayerState state = FrostFullOperationService.stateForTest(OWNER);
        state.beginWave();
        state.activate(100L);

        assertEquals(5.0, FrostFullOperationService.fixedOutgoingDamage(OWNER, 100L, 999.0));
        assertEquals(5.0,
                FrostFullOperationService.fixedIncomingDamage(OWNER, 100L, 100.0, 40.0),
                0.0001);
        assertEquals(999.0, FrostFullOperationService.fixedOutgoingDamage(OWNER, 200L, 999.0));
        assertEquals(40.0, FrostFullOperationService.fixedIncomingDamage(OWNER, 200L, 100.0, 40.0));
        FrostFullOperationService.clearPlayer(OWNER);
    }

    @Test
    void fullOperationItemAndBuilderDescriptionExplainTheLockedEffect() {
        ItemLore lore = FrostFullOperationService.activationItemForTest().get(DataComponents.LORE);
        assertTrue(lore != null);
        String loreText = String.join("\n", lore.lines().stream().map(Component::getString).toList());
        assertTrue(loreText.contains("받는 피해 감소가 95%로 고정"));
        assertTrue(loreText.contains("공격력 피해가 5로 고정"));

        String jobDescription = String.join("\n", new FrostTowerJob().description().stream()
                .map(Component::getString)
                .toList());
        assertFalse(jobDescription.contains("3 / 6 / 9"));
        assertFalse(jobDescription.contains("9회"));
        assertTrue(String.join("\n", FrostTowers.ERUPTION_COOLING_DEVICE.description())
                .contains("!!냉기 방출 타워의 앞에 위치하게 하세요!!"));
    }

    @Test
    void activationItemUsesCustomDataAndClearsEveryInventorySlot() {
        ItemStack activation = FrostFullOperationService.activationItemForTest();
        ItemStack namedIce = new ItemStack(Items.ICE);
        namedIce.set(DataComponents.CUSTOM_NAME, Component.literal("냉동창고 완전 가동"));
        assertTrue(FrostFullOperationService.isActivationItem(activation));
        assertFalse(FrostFullOperationService.isActivationItem(namedIce));
        assertNull(activation.get(DataComponents.CREATIVE_SLOT_LOCK));

        SimpleContainer inventory = new SimpleContainer(20);
        inventory.setItem(0, activation.copy());
        inventory.setItem(8, activation.copy());
        inventory.setItem(19, activation.copy());
        inventory.setItem(5, namedIce);

        assertTrue(FrostFullOperationService.clearActivationItems(inventory));
        assertTrue(inventory.getItem(0).isEmpty());
        assertTrue(inventory.getItem(8).isEmpty());
        assertTrue(inventory.getItem(19).isEmpty());
        assertFalse(inventory.getItem(5).isEmpty());
        assertFalse(FrostFullOperationService.clearActivationItems(inventory));
    }

    @Test
    void nonDefaultSettingsUpdateWaveDetailsDescriptionsAndOperationLore() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> emission = new LinkedHashMap<>(
                abilities.get(FrostTowers.EMISSION_COOLING_DEVICE.id()));
        emission.put("waveRange", 41.0);
        emission.put("waveWidth", 5.0);
        abilities.put(FrostTowers.EMISSION_COOLING_DEVICE.id(), emission);
        LinkedHashMap<String, Double> expanded = new LinkedHashMap<>(
                abilities.get(FrostTowers.EMISSION_COOLING_DEVICE_EXPANDED.id()));
        expanded.put("waveRange", 73.0);
        expanded.put("waveWidth", 9.0);
        abilities.put(FrostTowers.EMISSION_COOLING_DEVICE_EXPANDED.id(), expanded);
        LinkedHashMap<String, Double> global = new LinkedHashMap<>(abilities.get(FrostBalance.CONFIG_ID));
        global.put("firstThreshold", 2.0);
        global.put("secondThreshold", 4.0);
        global.put("thirdThreshold", 7.0);
        global.put("fullOperationRequiredActivations", 6.0);
        global.put("fullOperationMaxActivationsPerFamily", 2.0);
        global.put("fullOperationDurationTicks", 160.0);
        global.put("fullOperationDamageReduction", 0.80);
        global.put("fullOperationFixedAttackDamage", 7.0);
        global.put("fullOperationChillIntervalTicks", 40.0);
        global.put("fullOperationChillPerPulse", 0.50);
        abilities.put(FrostBalance.CONFIG_ID, global);
        TowerBalanceConfig custom = new TowerBalanceConfig(
                defaults.towers(),
                defaults.upgradeCosts(),
                abilities,
                defaults.illusionCloneQueue(),
                defaults.villagerAdv(),
                defaults.schemaVersion()
        );

        try {
            ProductionTowerCatalogs.reloadBuiltIns(custom);
            assertEquals(41.0, FrostBalance.coolingWaveRange(FrostTowers.EMISSION_COOLING_DEVICE));
            assertEquals(5.0, FrostBalance.coolingWaveWidth(FrostTowers.EMISSION_COOLING_DEVICE));
            assertEquals(73.0, FrostBalance.coolingWaveRange(FrostTowers.EMISSION_COOLING_DEVICE_EXPANDED));
            assertEquals(9.0, FrostBalance.coolingWaveWidth(FrostTowers.EMISSION_COOLING_DEVICE_EXPANDED));
            FrostCoolingTower expandedTower = (FrostCoolingTower) create(
                    FrostTowers.EMISSION_COOLING_DEVICE_EXPANDED,
                    new GridPosition(0, 64, 0)
            );
            assertTrue(expandedTower.runtimeDetailLines().getFirst().contains("9.0×73.0"));

            String vanguardDescription = String.join("\n",
                    TowerBalanceRuntime.resolve(FrostTowers.ICE_VANGUARD).description());
            String eruptionDescription = String.join("\n",
                    TowerBalanceRuntime.resolve(FrostTowers.ERUPTION_COOLING_DEVICE).description());
            assertTrue(vanguardDescription.contains("2/4/7기"));
            assertTrue(eruptionDescription.contains("각각 2회 발동해 6스택"));

            ItemLore lore = FrostFullOperationService.activationItemForTest().get(DataComponents.LORE);
            String loreText = String.join("\n", lore.lines().stream().map(Component::getString).toList());
            assertTrue(loreText.contains("8초 동안"));
            assertTrue(loreText.contains("80%로 고정"));
            assertTrue(loreText.contains("2초마다"));
            assertTrue(loreText.contains("한기 50%"));
            assertTrue(loreText.contains("공격력 피해가 7로 고정"));
        } finally {
            ProductionTowerCatalogs.reloadBuiltIns(defaults);
        }
    }

    @Test
    void fullOperationStopsOnlyItsOwnAmbientSoundAtTheRequestedPitch() {
        ClientboundStopSoundPacket packet = FrostFullOperationService.fullOperationAmbientStopPacket();

        assertEquals(ResourceLocation.withDefaultNamespace("ambient.soul_sand_valley.loop"), packet.getName());
        assertEquals(SoundSource.AMBIENT, packet.getSource());
        assertFalse(packet.getSource() == SoundSource.RECORDS,
                "Semion background music uses RECORDS and must remain untouched.");
        assertEquals(0.75F, FrostFullOperationService.fullOperationSoundPitch());
    }

    @Test
    void healerProtectionUsesSharedThreeSixNineFamilyThresholds() {
        assertEquals(0.0, FrostBalance.healerDamageReduction(2));
        assertEquals(0.05, FrostBalance.healerDamageReduction(3));
        assertEquals(0.07, FrostBalance.healerDamageReduction(6));
        assertEquals(0.10, FrostBalance.healerDamageReduction(9));
    }

    @Test
    void everyTowerHasExactlyOneBuilderOwner() {
        var frostJob = JobRegistry.find(FrostTowerJob.ID).orElseThrow();
        for (TowerType type : FrostTowers.all()) {
            assertTrue(frostJob.includesTowerInCatalog(type));
            assertEquals(1, JobRegistry.all().stream()
                    .filter(job -> job.includesTowerInCatalog(type))
                    .count(), type.id());
        }
    }

    @Test
    void coolingDeviceLimitsAreIndependentAndDoNotBlockTheEruptionUpgrade() {
        EconomyConfig economy = EconomyConfig.defaultConfig();
        SemionGame game = new SemionGame(economy, WaveConfig.defaultConfig(), new GameArena(Map.of()));
        SemionPlayer player = new SemionPlayer(OWNER, "frost", TeamId.RED, 1, new PlayerEconomy(economy));
        game.players().put(OWNER, player);
        game.teams().get(TeamId.RED).activate();
        PlayerLane lane = testLane(1, OWNER);
        game.teams().get(TeamId.RED).laneGroup().addLane(lane);
        FrostTowerJob job = new FrostTowerJob();
        JobContext context = new JobContext(game, player);

        assertTrue(job.canUseTower(context, FrostTowers.EMISSION_COOLING_DEVICE));
        assertTrue(job.canUseTower(context, FrostTowers.ERUPTION_COOLING_DEVICE));
        lane.addTower(new TestTower(FrostTowers.EMISSION_COOLING_DEVICE, OWNER, 0));
        assertFalse(job.canUseTower(context, FrostTowers.EMISSION_COOLING_DEVICE));
        assertTrue(job.canUseTower(context, FrostTowers.EMISSION_COOLING_DEVICE_EXPANDED));
        assertTrue(job.canUseTower(context, FrostTowers.ERUPTION_COOLING_DEVICE));
        lane.addTower(new TestTower(FrostTowers.ERUPTION_COOLING_DEVICE, OWNER, 1));
        assertFalse(job.canUseTower(context, FrostTowers.ERUPTION_COOLING_DEVICE));
        assertTrue(job.canUseTower(context, FrostTowers.ERUPTION_COOLING_DEVICE_EXPANDED));
    }

    @Test
    void vanguardDamageReductionUsesWholeFamilyCountAndCurrentTier() {
        assertEquals(0.0, FrostBalance.vanguardDamageReduction(FrostTowers.ICE_VANGUARD, 2));
        assertEquals(0.05, FrostBalance.vanguardDamageReduction(FrostTowers.ICE_VANGUARD, 3));
        assertEquals(0.10, FrostBalance.vanguardDamageReduction(FrostTowers.ICE_VANGUARD, 6));
        assertEquals(0.15, FrostBalance.vanguardDamageReduction(FrostTowers.ICE_VANGUARD, 9));
        assertEquals(0.10, FrostBalance.vanguardDamageReduction(FrostTowers.STURDY_ICE_VANGUARD, 3));
        assertEquals(0.15, FrostBalance.vanguardDamageReduction(FrostTowers.STURDY_ICE_VANGUARD, 6));
        assertEquals(0.20, FrostBalance.vanguardDamageReduction(FrostTowers.STURDY_ICE_VANGUARD, 9));
        assertEquals(0.15, FrostBalance.vanguardDamageReduction(FrostTowers.DONGTAE, 3));
        assertEquals(0.20, FrostBalance.vanguardDamageReduction(FrostTowers.DONGTAE, 6));
        assertEquals(0.25, FrostBalance.vanguardDamageReduction(FrostTowers.DONGTAE, 9));
    }

    @Test
    void frozenFoodSnapshotCountsAllTiersAndUnlocksTheSixTowerSplashBonus() {
        PlayerLane lane = testLane(1, OWNER);
        addTowers(lane, OWNER, FrostTowers.FROZEN_DUMPLING_T1, 2, 0);
        addTowers(lane, OWNER, FrostTowers.FROZEN_DUMPLING_T2, 2, 10);
        addTowers(lane, OWNER, FrostTowers.FROZEN_DUMPLING_T3, 2, 20);
        FrostSplashTower food = new FrostSplashTower(
                FrostTowers.FROZEN_DUMPLING_T1,
                OWNER,
                TeamId.RED,
                1,
                new GridPosition(30, 64, 0)
        );

        food.onWaveStarted(lane, 1);

        assertEquals(6, food.waveFamilyCount());
        assertEquals(1.8, food.effectiveSplashRadiusForTest());
    }

    @Test
    void frozenFoodDamageAndIncomeBonusesIncreaseByTier() {
        assertEquals(3.0, FrostBalance.frozenFoodDamageBonusAt3(FrostTowers.FROZEN_DUMPLING_T1));
        assertEquals(5.0, FrostBalance.frozenFoodDamageBonusAt3(FrostTowers.FROZEN_DUMPLING_T2));
        assertEquals(10.0, FrostBalance.frozenFoodDamageBonusAt3(FrostTowers.FROZEN_DUMPLING_T3));
        assertEquals(0.10, FrostBalance.frozenFoodIncomeDamageBonusAt9(FrostTowers.FROZEN_DUMPLING_T1));
        assertEquals(0.20, FrostBalance.frozenFoodIncomeDamageBonusAt9(FrostTowers.FROZEN_DUMPLING_T2));
        assertEquals(0.30, FrostBalance.frozenFoodIncomeDamageBonusAt9(FrostTowers.FROZEN_DUMPLING_T3));
    }

    @Test
    void coolingWaveIsSevenBlocksWideAndFiftyBlocksLong() {
        Vec3 origin = Vec3.ZERO;
        Vec3 forward = new Vec3(1.0, 0.0, 0.0);

        assertTrue(FrostCoolingTower.insideWave(origin, new Vec3(50.0, 0.0, 3.5), forward, 50.0, 7.0));
        assertTrue(FrostCoolingTower.insideWave(origin, new Vec3(25.0, 20.0, -3.5), forward, 50.0, 7.0));
        assertFalse(FrostCoolingTower.insideWave(origin, new Vec3(50.01, 0.0, 0.0), forward, 50.0, 7.0));
        assertFalse(FrostCoolingTower.insideWave(origin, new Vec3(20.0, 0.0, 3.51), forward, 50.0, 7.0));
        assertFalse(FrostCoolingTower.insideWave(origin, new Vec3(-0.01, 0.0, 0.0), forward, 50.0, 7.0));
    }

    @Test
    void eruptionStacksAndOwnAllyDebuffsMatchTheLockedDesign() {
        assertEquals(0, FrostBalance.eruptionStacksForFamilyCount(2));
        assertEquals(1, FrostBalance.eruptionStacksForFamilyCount(3));
        assertEquals(1, FrostBalance.eruptionStacksForFamilyCount(5));
        assertEquals(2, FrostBalance.eruptionStacksForFamilyCount(6));
        assertEquals(2, FrostBalance.eruptionStacksForFamilyCount(8));
        assertEquals(4, FrostBalance.eruptionStacksForFamilyCount(9));
        assertEquals(10, FrostBalance.clampEruptionStacks(14));
        assertEquals(0.20, FrostBalance.eruptionDamageReduction(10, true));
        assertEquals(0.15, FrostBalance.eruptionAttackSpeedReduction(10, true));
        assertEquals(0.15, FrostBalance.eruptionDamageReduction(10, false));
        assertEquals(0.10, FrostBalance.eruptionAttackSpeedReduction(10, false));
    }

    @Test
    void eruptionSnapshotCountsOnlyTheOwnersCombatFamiliesAndExcludesCoolingDevices() {
        PlayerLane ownerLane = testLane(1, OWNER);
        PlayerLane allyLane = testLane(2, ALLY);
        var group = new kim.biryeong.semiontd.game.TeamLaneGroup(
                TeamId.RED,
                BossMonster.defaultBoss(TeamId.RED)
        );
        group.addLane(ownerLane);
        group.addLane(allyLane);
        addTowers(ownerLane, OWNER, FrostTowers.ICE_VANGUARD, 3, 0);
        addTowers(ownerLane, OWNER, FrostTowers.ICE_BREAKER_T1, 6, 10);
        addTowers(ownerLane, OWNER, FrostTowers.FROZEN_DUMPLING_T1, 9, 20);
        addTowers(allyLane, ALLY, FrostTowers.ICE_VANGUARD, 3, 40);
        addTowers(allyLane, ALLY, FrostTowers.ICE_BREAKER_T2, 3, 50);
        addTowers(allyLane, ALLY, FrostTowers.FROZEN_DUMPLING_T3, 3, 60);
        addTowers(allyLane, ALLY, FrostTowers.EMISSION_COOLING_DEVICE, 9, 70);

        FrostTeamEffects.registerTeam(OWNER, group);
        try {
            assertEquals(7, FrostTeamEffects.snapshotEruptionStacks(OWNER, TeamId.RED, ownerLane));
        } finally {
            FrostTeamEffects.unregisterPlayer(OWNER);
        }
    }

    @Test
    void invalidThresholdsAndRatiosAreRejected() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertInvalidAbility(defaults, FrostBalance.CONFIG_ID, "secondThreshold", 3.0);
        assertInvalidAbility(defaults, FrostBalance.CONFIG_ID, "chillPerHit", 1.1);
        assertInvalidAbility(defaults, FrostTowers.ICE_VANGUARD.id(), "damageReductionAt6", 0.01);
        assertInvalidAbility(defaults, FrostTowers.EMISSION_COOLING_DEVICE.id(), "waveWidth", 0.0);
        assertInvalidAbility(defaults, FrostBalance.CONFIG_ID, "eruptionMaxStacks", 0.0);
        assertInvalidAbility(defaults, FrostBalance.CONFIG_ID, "eruptionStacksAt6", 1.0);
        assertInvalidAbility(defaults, FrostBalance.CONFIG_ID,
                "eruptionAllyDamageReductionPerStack", 0.03);
        assertInvalidAbility(defaults, FrostBalance.CONFIG_ID,
                "eruptionOwnDamageReductionPerStack", 0.20);
        assertInvalidAbility(defaults, FrostBalance.CONFIG_ID, "eruptionAuraDurationTicks", 10.0);
        assertInvalidAbility(defaults, FrostTowers.FROZEN_DUMPLING_T1.id(),
                "frozenFoodIncomeDamageBonusAt9", 1.1);
        assertInvalidAbility(defaults, FrostBalance.CONFIG_ID, "frozenFoodRefrigerantBonusAttacks", 1.5);
        assertInvalidAbility(defaults, FrostTowers.ICE_BREAKER_T1.id(), "splashRadius", 0.0);
        assertInvalidAbility(defaults, FrostTowers.ICEBOX_T1.id(), "healRadius", 0.0);
        assertInvalidAbility(defaults, FrostBalance.CONFIG_ID, "fullOperationDamageReduction", 1.1);
    }

    private static Object create(TowerType type, GridPosition position) {
        var entry = ProductionTowerCatalog.find(type.id()).orElseThrow();
        return entry.factory().create(
                entry.type(),
                UUID.randomUUID(),
                TeamId.RED,
                1,
                position,
                position
        );
    }

    private static void assertUpgrade(TowerType from, TowerType to, long cost) {
        assertEquals(cost, TowerBalanceRuntime.upgradeCost(from, to.id(), -1));
        assertTrue(ProductionTowerCatalog.upgrade(from, to.id()).isPresent());
    }

    private static PlayerLane testLane(int laneId, UUID owner) {
        Vec3 spawn = new Vec3(laneId * 10.0 + 0.5, 64.0, 0.5);
        LaneRegionLayout layout = new LaneRegionLayout(
                laneId,
                spawn,
                List.of(spawn.add(0.0, 0.0, 4.0)),
                spawn.add(0.0, 0.0, 10.0),
                BlockBounds.of(
                        new BlockPos(laneId * 10, 63, 0),
                        new BlockPos(laneId * 10 + 6, 66, 10)
                ),
                List.of(new GridPosition(laneId * 10, 63, 10))
        );
        return new PlayerLane(TeamId.RED, laneId, owner, null, layout);
    }

    private static void addTowers(
            PlayerLane lane,
            UUID owner,
            TowerType type,
            int count,
            int offset
    ) {
        for (int index = 0; index < count; index++) {
            lane.addTower(new TestTower(type, owner, offset + index));
        }
    }

    private static void assertStats(
            TowerBalanceConfig config,
            TowerType type,
            long mineral,
            double health,
            double damage,
            int attackInterval
    ) {
        TowerBalanceConfig.TowerStats stats = config.statsFor(type);
        assertEquals(mineral, stats.mineralCost());
        assertEquals(health, stats.maxHealth());
        assertEquals(damage, stats.damage());
        assertEquals(attackInterval, stats.attackIntervalTicks());
    }

    private static void assertInvalidAbility(
            TowerBalanceConfig defaults,
            String configId,
            String key,
            double value
    ) {
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> values = new LinkedHashMap<>(abilities.get(configId));
        values.put(key, value);
        abilities.put(configId, values);
        TowerBalanceConfig invalid = new TowerBalanceConfig(
                defaults.towers(),
                defaults.upgradeCosts(),
                abilities,
                defaults.illusionCloneQueue(),
                defaults.villagerAdv(),
                defaults.schemaVersion()
        );
        assertThrows(IllegalArgumentException.class, invalid::validateForRuntime);
    }

    private static final class TestTower extends Tower {
        private TestTower(TowerType type, UUID owner, int offset) {
            super(type, owner, TeamId.RED, 1, new GridPosition(offset, 64, 0));
        }

        @Override
        protected boolean execute(PlayerLane lane) {
            return false;
        }
    }
}
