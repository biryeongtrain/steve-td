package kim.biryeong.semiontd.tower.adversary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.AdversaryTowerJob;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.ui.SemionDialogService;
import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

class AdversaryTowerCatalogTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("adversary-owner".getBytes());

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void resetState() {
        AdversaryProgressStates.clearAllForTesting();
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void catalogContainsFoxEvolutionTreeAndFourTwoTierRivalLines() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());

        var entries = ProductionTowerCatalog.all().stream()
                .filter(entry -> AdversaryTowers.isAdversaryTower(entry.type()))
                .toList();
        assertEquals(21, entries.size());
        assertEquals(5, entries.stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count());
        assertTrue(ProductionTowerCatalog.find(AdversaryTowers.FOX.id()).orElseThrow().starter());
        assertTrue(JobRegistry.find(AdversaryTowerJob.ID).isPresent());

        for (FoxRoute route : FoxRoute.values()) {
            FoxForm intermediate = FoxForm.intermediateFor(route);
            var intermediateEntry = ProductionTowerCatalog.find(AdversaryTowers.typeFor(intermediate).id())
                    .orElseThrow();
            assertEquals(2, intermediateEntry.tier());
            assertEquals(AdversaryBalance.FIRST_EVOLUTION_COST, ProductionTowerCatalog.upgrade(
                    AdversaryTowers.FOX,
                    intermediateEntry.type().id()
            ).orElseThrow().mineralCost());
            for (FoxForm finalForm : FoxForm.finalsFor(route)) {
                var finalEntry = ProductionTowerCatalog.find(AdversaryTowers.typeFor(finalForm).id()).orElseThrow();
                assertEquals(3, finalEntry.tier());
                assertEquals(AdversaryBalance.FINAL_EVOLUTION_COST, ProductionTowerCatalog.upgrade(
                        intermediateEntry.type(),
                        finalEntry.type().id()
                ).orElseThrow().mineralCost());
            }
        }

        for (RivalKind kind : RivalKind.values()) {
            var base = ProductionTowerCatalog.find(AdversaryTowers.baseRival(kind).id()).orElseThrow();
            var enhanced = ProductionTowerCatalog.find(AdversaryTowers.enhancedRival(kind).id()).orElseThrow();
            var upgrade = ProductionTowerCatalog.upgrade(base.type(), enhanced.type().id()).orElseThrow();
            assertTrue(base.starter());
            assertFalse(enhanced.starter());
            assertEquals(2, enhanced.tier());
            assertEquals(kind.baseCost(), upgrade.mineralCost());
            assertEquals(enhanced.type().id(), upgrade.targetType().id());
        }
    }

    @Test
    void factoriesCreateFoxAndRivalRuntimeTypesWithZeroRivalRefund() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());

        var fox = ProductionTowerCatalog.find(AdversaryTowers.FOX.id()).orElseThrow()
                .create(OWNER, TeamId.RED, 1, new GridPosition(0, 64, 0));
        var rival = ProductionTowerCatalog.find(AdversaryTowers.BREEZE_RIVAL.id()).orElseThrow()
                .create(OWNER, TeamId.RED, 1, new GridPosition(1, 64, 0));

        assertInstanceOf(AdversaryFoxTower.class, fox);
        assertInstanceOf(AdversaryRivalTower.class, rival);
        assertEquals(0L, rival.sellRefundAmount());
    }

    @Test
    void upgradingARivalPreservesItsLogicalIdentityAndLedger() {
        AdversaryRivalTower base = (AdversaryRivalTower) ProductionTowerCatalog
                .find(AdversaryTowers.BREEZE_RIVAL.id())
                .orElseGet(() -> {
                    ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
                    return ProductionTowerCatalog.find(AdversaryTowers.BREEZE_RIVAL.id()).orElseThrow();
                })
                .create(OWNER, TeamId.RED, 1, new GridPosition(1, 64, 0));
        AdversaryRivalTower enhanced = (AdversaryRivalTower) ProductionTowerCatalog
                .find(AdversaryTowers.ENHANCED_BREEZE_RIVAL.id()).orElseThrow()
                .create(OWNER, TeamId.RED, 1, new GridPosition(1, 64, 0));

        PlayerLane lane = testLane();
        lane.addTower(base);
        assertTrue(AdversaryProgressStates.recordFoxKill(OWNER, base.createProxy(1), lane));
        assertTrue(AdversaryProgressStates.recordFoxKill(OWNER, base.createProxy(1), lane));
        enhanced.copyFrom(base, RivalKind.BREEZE.enhancementCost());
        assertTrue(lane.replaceTower(base, enhanced));

        assertEquals(base.rivalId(), enhanced.rivalId());
        assertEquals(4, enhanced.contributedScore());
        assertTrue(enhanced.enhanced());

        assertTrue(AdversaryProgressStates.recordFoxKill(OWNER, enhanced.createProxy(1), lane));
        assertEquals(7, enhanced.contributedScore());
        assertTrue(enhanced.runtimeDetailLines().stream().anyMatch(line ->
                line.contains("처치 점수") && line.contains("3점") && line.contains("누적 기여 7점")));
    }

    @Test
    void jobOwnsAllAdversaryCatalogTypes() {
        AdversaryTowerJob job = new AdversaryTowerJob();

        assertEquals("히어로 빌더", job.displayName().getString());
        assertEquals("히어로 여우", AdversaryTowers.FOX.displayName());
        assertTrue(job.canUseTower(null, AdversaryTowers.FOX));
        for (FoxForm form : FoxForm.values()) {
            assertTrue(job.canUseTower(null, AdversaryTowers.typeFor(form)));
        }
        for (RivalKind kind : RivalKind.values()) {
            assertTrue(job.canUseTower(null, AdversaryTowers.baseRival(kind)));
            assertTrue(job.canUseTower(null, AdversaryTowers.enhancedRival(kind)));
        }
        assertFalse(job.canUseTower(null, AnimalTowers.T1_FOX_TOWER));
    }

    @Test
    void jobDescriptionKeepsOnlyPlayerFacingBasics() {
        List<String> guide = new AdversaryTowerJob().description().stream()
                .map(component -> component.getString())
                .toList();

        assertEquals(List.of(
                "히어로 여우는 4기까지 설치할 수 있습니다. 웨이브가 시작되면 숙적이 적으로 변합니다.",
                "여우가 숙적을 직접 처치하면 전직 점수를 얻습니다. 인컴 적은 점수를 주지 않습니다.",
                "전직 점수는 모든 여우가 공유하며, 같은 전직 계열은 한 번만 선택할 수 있습니다.",
                "첫 전직은 200 다이아, 최종 전직은 400 다이아입니다.",
                "첫 전직 후 웨이브를 한 번 완료해야 최종 전직할 수 있습니다."
        ), guide);
    }

    @Test
    void jobAllowsFourFoxesAndStillAllowsEvolutionAtTheLimit() {
        EconomyConfig economy = EconomyConfig.defaultConfig();
        SemionGame game = new SemionGame(economy, WaveConfig.defaultConfig(), new GameArena(Map.of()));
        SemionPlayer player = new SemionPlayer(
                OWNER,
                "adversary",
                TeamId.RED,
                1,
                new PlayerEconomy(economy)
        );
        game.players().put(OWNER, player);
        game.teams().get(TeamId.RED).activate();
        PlayerLane lane = testLane();
        game.teams().get(TeamId.RED).laneGroup().addLane(lane);
        JobContext context = new JobContext(game, player);
        AdversaryTowerJob job = new AdversaryTowerJob();

        assertTrue(job.canUseTower(context, AdversaryTowers.FOX));
        for (int index = 0; index < 4; index++) {
            lane.addTower(new TestTower(AdversaryTowers.FOX));
        }
        assertFalse(job.canUseTower(context, AdversaryTowers.FOX));
        assertTrue(job.canUseTower(context, AdversaryTowers.typeFor(FoxForm.BREEZE)));
        assertTrue(job.canUseTower(context, AdversaryTowers.BREEZE_RIVAL));
    }

    @Test
    void selectingAdversaryJobExposesItsPlayableStarterCatalog() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        EconomyConfig economy = EconomyConfig.defaultConfig();
        SemionGame game = new SemionGame(economy, WaveConfig.defaultConfig(), new GameArena(Map.of()));
        SemionPlayer player = new SemionPlayer(
                OWNER,
                "adversary",
                TeamId.RED,
                1,
                new PlayerEconomy(economy)
        );
        game.players().put(OWNER, player);

        assertTrue(game.selectJob(OWNER, AdversaryTowerJob.ID));
        player.assignJob(game.selectedJobOrDefault(OWNER));
        assertEquals(
                "/semiontd job select adversary_towers",
                SemionDialogService.jobSelectionCommand(game.selectedJobOrDefault(OWNER))
        );

        var availableIds = ProductionTowerService.availableTowers(game, OWNER).stream()
                .map(entry -> entry.type().id())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(
                AdversaryTowers.FOX.id(),
                AdversaryTowers.BREEZE_RIVAL.id(),
                AdversaryTowers.CREEPER_RIVAL.id(),
                AdversaryTowers.PHANTOM_RIVAL.id(),
                AdversaryTowers.POLAR_BEAR_RIVAL.id()
        ), availableIds);
    }

    @Test
    void rivalRoundAndEnhancementScalingMatchesTheApprovedFormula() {
        assertEquals(1.0, AdversaryBalance.rivalRoundHealthMultiplier(1), 0.0001);
        assertEquals(1.63, AdversaryBalance.rivalRoundHealthMultiplier(10), 0.0001);
        assertEquals(2.33, AdversaryBalance.rivalRoundHealthMultiplier(20), 0.0001);
        assertEquals(3.73, AdversaryBalance.rivalRoundHealthMultiplier(40), 0.0001);
        assertEquals(1.27, AdversaryBalance.rivalRoundDamageMultiplier(10), 0.0001);
        assertEquals(1, AdversaryBalance.rivalRoundArmorBonus(10));

        RivalKind kind = RivalKind.POLAR_BEAR;
        assertEquals(100.0 * 1.63 * 2.40, kind.maxHealth(10, true), 0.0001);
        assertEquals(11.0 * 1.27 * 1.70, kind.damage(10, true), 0.0001);
        assertEquals(8.0, kind.armor(10, true), 0.0001);
        assertEquals(13, kind.attackIntervalTicks(true));
        assertEquals(3.0, kind.range(true), 0.0001);
        assertEquals(3, kind.scorePerKill(true));
    }

    @Test
    void allFourRivalsExposeTheApprovedBaseCombatStats() {
        assertRival(RivalKind.BREEZE, 45, 40, 0, 3, 14, 6, AttackKind.RANGED, "minecraft:breeze");
        assertRival(RivalKind.CREEPER, 60, 65, 2, 6, 15, 2.5, AttackKind.MELEE, "minecraft:creeper");
        assertRival(RivalKind.PHANTOM, 75, 75, 1, 7, 11, 4, AttackKind.RANGED, "minecraft:phantom");
        assertRival(RivalKind.POLAR_BEAR, 100, 100, 3, 11, 16, 2.5, AttackKind.MELEE, "minecraft:polar_bear");

        assertEnhancedRival(RivalKind.BREEZE, 96, 4, 5.1, 12, 6.5);
        assertEnhancedRival(RivalKind.CREEPER, 156, 6, 10.2, 12, 3.0);
        assertEnhancedRival(RivalKind.PHANTOM, 180, 5, 11.9, 9, 4.5);
        assertEnhancedRival(RivalKind.POLAR_BEAR, 240, 7, 18.7, 13, 3.0);

        assertEnhancedHealthAtRound(RivalKind.BREEZE, 20, 223.68);
        assertEnhancedHealthAtRound(RivalKind.CREEPER, 20, 363.48);
        assertEnhancedHealthAtRound(RivalKind.PHANTOM, 20, 419.40);
        assertEnhancedHealthAtRound(RivalKind.POLAR_BEAR, 20, 559.20);
        assertEnhancedHealthAtRound(RivalKind.BREEZE, 40, 358.08);
        assertEnhancedHealthAtRound(RivalKind.CREEPER, 40, 581.88);
        assertEnhancedHealthAtRound(RivalKind.PHANTOM, 40, 671.40);
        assertEnhancedHealthAtRound(RivalKind.POLAR_BEAR, 40, 895.20);

        assertEquals(0, AdversaryBalance.rivalRoundArmorBonus(5));
        assertEquals(1, AdversaryBalance.rivalRoundArmorBonus(6));
    }

    @Test
    void allThirteenFoxFormsExposeApprovedBaseStats() {
        assertEquals(13, FoxForm.values().length);
        assertForm(FoxForm.BASE, 300, 3, 16, 10);
        assertForm(FoxForm.BREEZE, 550, 7, 26, 4);
        assertForm(FoxForm.GOLDEN_FANG, 750, 5, 30, 3);
        assertForm(FoxForm.SHIELD_BEARER, 1050, 3.5, 60, 7);
        assertForm(FoxForm.BELL_KEEPER, 650, 5, 60, 7);
        assertForm(FoxForm.BEACON_KEEPER, 850, 4, 72, 6);
        assertForm(FoxForm.OMINOUS_HEXER, 700, 8, 72, 6);
        assertForm(FoxForm.TRACKER, 550, 8, 52, 7);
        assertForm(FoxForm.FIREWORK_PIERCER, 650, 10, 56, 5);
        assertForm(FoxForm.BIG_GAME_TRACKER, 750, 11, 96, 8);
        assertForm(FoxForm.ECHO_FOX, 700, 7, 76, 8);
        assertForm(FoxForm.MACE_EXECUTIONER, 900, 4.5, 400, 20);
        assertForm(FoxForm.SCULK_CORE, 800, 13, 800, 50);

        assertEquals(Items.STICK, FoxForm.BASE.heldItem());
        assertEquals(Items.BREEZE_ROD, FoxForm.BREEZE.heldItem());
        assertEquals(Items.GOLDEN_SWORD, FoxForm.GOLDEN_FANG.heldItem());
        assertEquals(Items.SHIELD, FoxForm.SHIELD_BEARER.heldItem());
        assertEquals(Items.BELL, FoxForm.BELL_KEEPER.heldItem());
        assertEquals(Items.BEACON, FoxForm.BEACON_KEEPER.heldItem());
        assertEquals(Items.OMINOUS_BOTTLE, FoxForm.OMINOUS_HEXER.heldItem());
        assertEquals(Items.COMPASS, FoxForm.TRACKER.heldItem());
        assertEquals(Items.FIREWORK_ROCKET, FoxForm.FIREWORK_PIERCER.heldItem());
        assertEquals(Items.SPYGLASS, FoxForm.BIG_GAME_TRACKER.heldItem());
        assertEquals(Items.ECHO_SHARD, FoxForm.ECHO_FOX.heldItem());
        assertEquals(Items.MACE, FoxForm.MACE_EXECUTIONER.heldItem());
        assertEquals(Items.SCULK_CATALYST, FoxForm.SCULK_CORE.heldItem());
        assertEquals(0.10, FoxForm.GOLDEN_FANG.damageReduction(), 0.0001);
        assertEquals(0.20, FoxForm.SHIELD_BEARER.damageReduction(), 0.0001);
        assertEquals(0.30, FoxForm.BEACON_KEEPER.damageReduction(), 0.0001);
        assertEquals(0.12, FoxForm.OMINOUS_HEXER.damageReduction(), 0.0001);
        assertTrue(FoxForm.MACE_EXECUTIONER.usesSpecialAttack());
        assertTrue(FoxForm.SCULK_CORE.usesSpecialAttack());
        assertFalse(FoxForm.ECHO_FOX.usesSpecialAttack());
    }

    @Test
    void defaultBalancePublishesEveryAdversaryTowerUpgradeFormAndAbility() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();

        assertTrue(AdversaryTowers.configurableTowers().stream()
                .allMatch(type -> defaults.towers().containsKey(type.id())));
        assertTrue(java.util.Arrays.stream(FoxForm.values())
                .filter(form -> form != FoxForm.BASE)
                .noneMatch(form -> defaults.towers().containsKey(AdversaryTowers.typeFor(form).id())));
        for (RivalKind kind : RivalKind.values()) {
            TowerType base = AdversaryTowers.baseRival(kind);
            TowerType enhanced = AdversaryTowers.enhancedRival(kind);
            assertEquals(
                    AdversaryBalance.defaultRivalBaseCost(kind),
                    defaults.upgradeCosts().get(TowerBalanceConfig.upgradeKey(base.id(), enhanced.id())).longValue()
            );
            assertTrue(defaults.abilities().containsKey(base.id()));
            assertTrue(defaults.abilities().containsKey(enhanced.id()));
        }
        for (FoxForm form : FoxForm.values()) {
            Map<String, Double> values = defaults.abilities().get(AdversaryBalance.formConfigId(form));
            assertTrue(values.containsKey("maxHealth"));
            assertTrue(values.containsKey("attackIntervalTicks"));
            assertTrue(values.containsKey("damageReduction"));
        }

        Map<String, Double> global = defaults.abilities().get(AdversaryBalance.GLOBAL_CONFIG_ID);
        assertEquals(4.0, global.get("maxFoxTowers"), 0.0001);
        assertEquals(4.0, global.get("baseSplashRadius"), 0.0001);
        assertEquals(3.0, global.get("baseSplashExtraTargets"), 0.0001);
        assertEquals(0.07, global.get("rivalRoundHealthGrowth"), 0.0001);
        assertEquals(0.03, global.get("rivalRoundDamageGrowth"), 0.0001);
        assertEquals(20.0, global.get("teamEffectScanIntervalTicks"), 0.0001);
        assertEquals(60.0, global.get("bellHealIntervalTicks"), 0.0001);
        assertEquals(8.0, global.get("bellHealRadius"), 0.0001);
        assertEquals(1.0, global.get("bellHealTargetCount"), 0.0001);
        assertEquals(0.08, global.get("bellHealMaxHealthRatio"), 0.0001);
        assertEquals(40.0, global.get("beaconHealIntervalTicks"), 0.0001);
        assertEquals(10.0, global.get("beaconHealRadius"), 0.0001);
        assertEquals(2.0, global.get("beaconHealTargetCount"), 0.0001);
        assertEquals(0.14, global.get("beaconHealMaxHealthRatio"), 0.0001);
        assertEquals(0.20, global.get("maceBreakHealthRatio"), 0.0001);
        assertEquals(1.50, global.get("maceSweepRadius"), 0.0001);
        assertEquals(8.0, global.get("maceSweepExtraTargets"), 0.0001);
        assertEquals(0.25, global.get("maceSweepDamageRatio"), 0.0001);
        assertEquals(15.0, global.get("sculkMaxTargets"), 0.0001);
        assertEquals(0.50, global.get("baseSplashDamageRatio"), 0.0001);
        assertEquals(0.50, global.get("evolvedSplashDamageRatio"), 0.0001);
        assertEquals(0.005, global.get("postEvolutionDamageBonusPerScore"), 0.0001);
        assertEquals(2.00, global.get("postEvolutionDamageBonusCap"), 0.0001);
        assertEquals(0.20, global.get("baseRivalKillHealRatio"), 0.0001);
        assertEquals(0.30, global.get("enhancedRivalKillHealRatio"), 0.0001);
        assertEquals(2.00, global.get("rivalKillHealCapRatioPerWave"), 0.0001);
        assertEquals(0.04, global.get("focusFireDamageReductionPerExtraAttacker"), 0.0001);
        assertEquals(0.45, global.get("focusFireDamageReductionCap"), 0.0001);
        assertEquals(0.10, global.get("fireworkSecondary6Ratio"), 0.0001);
        assertEquals(0.08, global.get("fireworkSecondary7Ratio"), 0.0001);
        assertEquals(0.05, global.get("fireworkSecondary8Ratio"), 0.0001);
        assertEquals(0.40, global.get("sculkSelfDamageFloorRatio"), 0.0001);
        assertFalse(global.containsKey("maceStrikeDamage"));
        assertFalse(global.containsKey("maceStrikeIntervalTicks"));
        assertFalse(global.containsKey("sculkDamage"));
        assertFalse(global.containsKey("sculkAttackIntervalTicks"));
    }

    @Test
    void missingDefaultsMergePreservesOverridesAndBackfillsAdversaryValues() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        String breezeUpgrade = TowerBalanceConfig.upgradeKey(
                AdversaryTowers.BREEZE_RIVAL.id(),
                AdversaryTowers.ENHANCED_BREEZE_RIVAL.id()
        );
        TowerBalanceConfig partial = new TowerBalanceConfig(
                Map.of(AdversaryTowers.FOX.id(), new TowerBalanceConfig.TowerStats(135L, null, null, null, null, null)),
                Map.of(breezeUpgrade, 123L),
                Map.of(
                        AdversaryBalance.GLOBAL_CONFIG_ID, Map.of("baseSplashRadius", 2.75),
                        AdversaryBalance.formConfigId(FoxForm.MACE_EXECUTIONER), Map.of("damage", 650.0)
                )
        );

        TowerBalanceConfig merged = partial.withMissingDefaults(defaults);
        assertEquals(135L, merged.towers().get(AdversaryTowers.FOX.id()).mineralCost().longValue());
        assertEquals(300.0, merged.towers().get(AdversaryTowers.FOX.id()).maxHealth(), 0.0001);
        assertEquals(16.0, merged.towers().get(AdversaryTowers.FOX.id()).damage(), 0.0001);
        assertEquals(123L, merged.upgradeCosts().get(breezeUpgrade).longValue());
        assertEquals(2.75, merged.ability(AdversaryBalance.GLOBAL_CONFIG_ID, "baseSplashRadius", 0.0), 0.0001);
        assertEquals(3.0, merged.ability(AdversaryBalance.GLOBAL_CONFIG_ID, "baseSplashExtraTargets", 0.0), 0.0001);
        assertEquals(4.0, merged.ability(AdversaryBalance.GLOBAL_CONFIG_ID, "maxFoxTowers", 0.0), 0.0001);
        assertEquals(0.005, merged.ability(
                AdversaryBalance.GLOBAL_CONFIG_ID,
                "postEvolutionDamageBonusPerScore",
                0.0
        ), 0.0001);
        assertEquals(0.20, merged.ability(AdversaryBalance.GLOBAL_CONFIG_ID, "baseRivalKillHealRatio", 0.0), 0.0001);
        assertEquals(0.45, merged.ability(AdversaryBalance.GLOBAL_CONFIG_ID, "focusFireDamageReductionCap", 0.0), 0.0001);
        assertEquals(0.14, merged.ability(AdversaryBalance.GLOBAL_CONFIG_ID, "beaconHealMaxHealthRatio", 0.0), 0.0001);
        assertEquals(0.05, merged.ability(AdversaryBalance.GLOBAL_CONFIG_ID, "fireworkSecondary8Ratio", 0.0), 0.0001);
        assertEquals(650.0, merged.ability(
                AdversaryBalance.formConfigId(FoxForm.MACE_EXECUTIONER),
                "damage",
                0.0
        ), 0.0001);
        assertEquals(4.5, merged.ability(
                AdversaryBalance.formConfigId(FoxForm.MACE_EXECUTIONER),
                "range",
                0.0
        ), 0.0001);
        assertEquals(30.0, merged.ability(
                AdversaryBalance.formConfigId(FoxForm.GOLDEN_FANG),
                "damage",
                0.0
        ), 0.0001);
        assertTrue(AdversaryTowers.configurableTowers().stream()
                .allMatch(type -> merged.towers().containsKey(type.id())));

        ProductionTowerCatalogs.reloadBuiltIns(merged);
        assertEquals(135L, ProductionTowerCatalog.find(AdversaryTowers.FOX.id()).orElseThrow().type().mineralCost());
        assertEquals(123L, ProductionTowerCatalog.upgrade(
                AdversaryTowers.BREEZE_RIVAL,
                AdversaryTowers.ENHANCED_BREEZE_RIVAL.id()
        ).orElseThrow().mineralCost());
        assertEquals(2.75, AdversaryBalance.globalValue("baseSplashRadius", 0.0), 0.0001);
        assertEquals(650.0, FoxForm.MACE_EXECUTIONER.damage(), 0.0001);
    }

    @Test
    void adversaryValidationAcceptsAnyNonNegativeValueAndRejectsNegativeValues() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        LinkedHashMap<String, Map<String, Double>> invalidRatioAbilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> invalidGlobal = new LinkedHashMap<>(
                invalidRatioAbilities.get(AdversaryBalance.GLOBAL_CONFIG_ID)
        );
        invalidGlobal.put("baseSplashDamageRatio", 1.01);
        invalidRatioAbilities.put(AdversaryBalance.GLOBAL_CONFIG_ID, invalidGlobal);
        TowerBalanceConfig invalidRatio = new TowerBalanceConfig(
                defaults.towers(),
                defaults.upgradeCosts(),
                invalidRatioAbilities,
                defaults.illusionCloneQueue(),
                defaults.villagerAdv(),
                defaults.schemaVersion()
        );
        assertDoesNotThrow(invalidRatio::validateForRuntime);

        LinkedHashMap<String, Map<String, Double>> invalidTickAbilities = new LinkedHashMap<>(defaults.abilities());
        String formId = AdversaryBalance.formConfigId(FoxForm.BREEZE);
        LinkedHashMap<String, Double> invalidForm = new LinkedHashMap<>(invalidTickAbilities.get(formId));
        invalidForm.put("attackIntervalTicks", 3.5);
        invalidTickAbilities.put(formId, invalidForm);
        TowerBalanceConfig invalidTick = new TowerBalanceConfig(
                defaults.towers(),
                defaults.upgradeCosts(),
                invalidTickAbilities,
                defaults.illusionCloneQueue(),
                defaults.villagerAdv(),
                defaults.schemaVersion()
        );
        assertDoesNotThrow(invalidTick::validateForRuntime);

        invalidForm.put("attackIntervalTicks", -0.5);
        invalidTickAbilities.put(formId, invalidForm);
        TowerBalanceConfig negative = new TowerBalanceConfig(
                defaults.towers(),
                defaults.upgradeCosts(),
                invalidTickAbilities,
                defaults.illusionCloneQueue(),
                defaults.villagerAdv(),
                defaults.schemaVersion()
        );
        assertThrows(IllegalArgumentException.class, negative::validateForRuntime);
    }

    @Test
    void catalogDescriptionsRenderConfiguredValuesWithoutUnresolvedPlaceholders() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());

        for (TowerType type : AdversaryTowers.all()) {
            List<String> description = ProductionTowerCatalog.find(type.id()).orElseThrow().type().description();
            assertFalse(description.isEmpty());
            assertTrue(description.stream().noneMatch(line -> line.contains("{stat.") || line.contains("{ability.")));
        }
        String foxDescription = String.join(" ", ProductionTowerCatalog.find(AdversaryTowers.FOX.id())
                .orElseThrow().type().description());
        assertTrue(foxDescription.contains("4블록"));
        assertTrue(foxDescription.contains("4기"));
        assertTrue(foxDescription.contains("공유 점수"));
        assertTrue(foxDescription.contains("남은 점수 1점당 피해가 0.5% 증가")
                && foxDescription.contains("최대 200%"));
        assertTrue(foxDescription.contains("최대 체력의 20%") && foxDescription.contains("강화 숙적은 30%"));
        assertTrue(foxDescription.contains("1기를 넘을 때마다 받는 피해가 4% 감소"));
        assertTrue(foxDescription.contains("전직 형태는 주변 적 최대 3기에게 공격력의 50%"));
        assertTrue(foxDescription.contains("질풍의 연쇄 공격과 스컬크 폭발은 마법 피해"));
        assertTrue(foxDescription.contains("나머지 공격은 물리 피해"));
        assertFalse(foxDescription.contains("진화"));

        String enhancedDescription = String.join(" ", ProductionTowerCatalog
                .find(AdversaryTowers.ENHANCED_POLAR_BEAR_RIVAL.id())
                .orElseThrow().type().description());
        assertTrue(enhancedDescription.contains("240"));
        assertTrue(enhancedDescription.contains("7"));
        assertTrue(enhancedDescription.contains("전직 점수 3점"));
        assertTrue(enhancedDescription.contains("라운드가 오를수록 증가"));
        assertTrue(enhancedDescription.contains("플레이어 특성 효과를 받지 않습니다"));
    }

    private static void assertRival(
            RivalKind kind,
            long cost,
            double health,
            double armor,
            double damage,
            int interval,
            double range,
            AttackKind attackKind,
            String entityType
    ) {
        assertEquals(cost, kind.baseCost());
        assertEquals(health, kind.baseMaxHealth(), 0.0001);
        assertEquals(armor, kind.baseArmor(), 0.0001);
        assertEquals(damage, kind.baseDamage(), 0.0001);
        assertEquals(interval, kind.attackIntervalTicks());
        assertEquals(range, kind.range(), 0.0001);
        assertEquals(attackKind, kind.attackKind());
        assertEquals(entityType, kind.entityTypeId());
        assertEquals(cost, kind.enhancementCost());
        assertEquals(2, kind.scorePerKill(false));
        assertEquals(3, kind.scorePerKill(true));
    }

    private static void assertForm(FoxForm form, double health, double range, double damage, int interval) {
        assertEquals(health, form.maxHealth(), 0.0001);
        assertEquals(range, form.range(), 0.0001);
        assertEquals(damage, form.damage(), 0.0001);
        assertEquals(interval, form.attackIntervalTicks());
    }

    private static void assertEnhancedRival(
            RivalKind kind,
            double health,
            double armor,
            double damage,
            int interval,
            double range
    ) {
        assertEquals(health, kind.maxHealth(1, true), 0.0001);
        assertEquals(armor, kind.armor(1, true), 0.0001);
        assertEquals(damage, kind.damage(1, true), 0.0001);
        assertEquals(interval, kind.attackIntervalTicks(true));
        assertEquals(range, kind.range(true), 0.0001);
        assertTrue(kind.maxHealth(1, true) > kind.maxHealth(1, false));
        assertTrue(kind.damage(1, true) > kind.damage(1, false));
        assertTrue(kind.armor(1, true) > kind.armor(1, false));
        assertTrue(kind.attackIntervalTicks(true) < kind.attackIntervalTicks(false));
        assertTrue(kind.range(true) > kind.range(false));
    }

    private static void assertEnhancedHealthAtRound(RivalKind kind, int round, double health) {
        assertEquals(health, kind.maxHealth(round, true), 0.0001);
    }

    private static PlayerLane testLane() {
        Vec3 spawn = new Vec3(0.5, 64.0, 0.5);
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                spawn,
                List.of(new Vec3(0.5, 64.0, 4.5)),
                new Vec3(0.5, 64.0, 10.5),
                BlockBounds.of(new BlockPos(0, 63, 0), new BlockPos(4, 66, 10)),
                List.of(new GridPosition(0, 63, 10))
        );
        return new PlayerLane(TeamId.RED, 1, OWNER, null, layout);
    }

    private static final class TestTower extends Tower {
        private TestTower(TowerType type) {
            super(type, OWNER, TeamId.RED, 1, new GridPosition(1, 64, 1));
        }

        @Override
        protected boolean execute(PlayerLane lane) {
            return false;
        }
    }
}
