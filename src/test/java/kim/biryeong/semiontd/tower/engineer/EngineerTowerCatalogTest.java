package kim.biryeong.semiontd.tower.engineer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.EngineerTowerJob;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCapacity;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class EngineerTowerCatalogTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("engineer-owner".getBytes());
    private static final GridPosition POSITION = new GridPosition(0, 64, 0);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void resetCatalog() {
        EngineerPressStates.clear(OWNER);
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void catalogRegistersEightStartersAndEveryUpgrade() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());

        var entries = ProductionTowerCatalog.all().stream()
                .filter(entry -> EngineerTowers.isEngineerTower(entry.type()))
                .toList();
        assertEquals(EngineerTowers.all().size(), entries.size());
        assertEquals(8, entries.stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count());
        assertTrue(JobRegistry.find(EngineerTowerJob.ID).isPresent());
        assertEquals(15, ProductionTowerCatalog.find(EngineerTowers.REDSTONE_DUST.id())
                .orElseThrow().type().mineralCost());
        assertEquals(4, ProductionTowerCatalog.upgrades(EngineerTowers.REDSTONE_DUST).size());
        assertEquals(1, ProductionTowerCatalog.upgrades(EngineerTowers.repeater(Direction.NORTH)).size());
        for (EngineerTowers.PlateKind kind : EngineerTowers.PlateKind.values()) {
            assertEquals(kind == EngineerTowers.PlateKind.GOLD ? 0 : 1,
                    ProductionTowerCatalog.upgrades(EngineerTowers.plate(kind)).size());
        }
        for (EngineerTowers.TrapKind kind : EngineerTowers.TrapKind.values()) {
            assertEquals(1, ProductionTowerCatalog.upgrades(EngineerTowers.trap(kind, 1)).size());
            assertEquals(1, ProductionTowerCatalog.upgrades(EngineerTowers.trap(kind, 2)).size());
            assertEquals(0, ProductionTowerCatalog.upgrades(EngineerTowers.trap(kind, 3)).size());
        }
    }

    @Test
    void factoriesAndSlotWeightsMatchContract() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());

        assertInstanceOf(EngineerGolemTower.class, create(EngineerTowers.COPPER_GOLEM));
        assertInstanceOf(EngineerCircuitTower.class, create(EngineerTowers.REDSTONE_DUST));
        assertInstanceOf(EngineerCircuitTower.class, create(EngineerTowers.plate(EngineerTowers.PlateKind.GOLD)));
        assertInstanceOf(EngineerTrapTower.class, create(EngineerTowers.trap(EngineerTowers.TrapKind.TNT, 1)));
        assertEquals(0, create(EngineerTowers.COPPER_GOLEM).slotWeight());
        assertEquals(0, create(EngineerTowers.REDSTONE_DUST).slotWeight());
        assertEquals(0, create(EngineerTowers.repeater(Direction.WEST)).slotWeight());
        assertEquals(0, TowerCapacity.slotCost(EngineerTowers.REDSTONE_DUST));
        EngineerTowers.repeaters().values().forEach(type ->
                assertEquals(0, TowerCapacity.slotCost(type)));
        assertEquals(1, create(EngineerTowers.plate(EngineerTowers.PlateKind.WOOD)).slotWeight());
        assertEquals(1, create(EngineerTowers.trap(EngineerTowers.TrapKind.DOOR, 1)).slotWeight());
        assertFalse(create(EngineerTowers.trap(EngineerTowers.TrapKind.DOOR, 1)).participatesInFinalDefense());
        assertTrue(create(EngineerTowers.REDSTONE_DUST).canBeSold());
        assertTrue(create(EngineerTowers.repeater(Direction.WEST)).canBeSold());
        assertTrue(create(EngineerTowers.plate(EngineerTowers.PlateKind.WOOD)).canBeSold());
    }

    @Test
    void priorityAndUpgradeChainRunFromWoodToGold() {
        assertEquals(4, EngineerTowers.PlateKind.GOLD.priority());
        assertEquals(3, EngineerTowers.PlateKind.IRON.priority());
        assertEquals(1, EngineerTowers.PlateKind.WOOD.priority());
        assertEquals(EngineerTowers.PlateKind.STONE, EngineerTowers.PlateKind.WOOD.next().orElseThrow());
        assertTrue(EngineerTowers.PlateKind.GOLD.next().isEmpty());
    }

    @Test
    void defaultsMerge() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertTrue(EngineerTowers.all().stream().allMatch(type -> defaults.towers().containsKey(type.id())));
        assertEquals(60, defaults.abilityTicks(EngineerBalance.GLOBAL_ID, "activeTicks", -1));
        assertEquals(120, defaults.abilityTicks(EngineerBalance.GLOBAL_ID, "doorActiveTicks", -1));
        assertEquals(60, defaults.abilityTicks(EngineerBalance.GLOBAL_ID, "tntFuseTicks", -1));
        assertEquals(35, defaults.abilityInt(EngineerBalance.GLOBAL_ID, "maxRedstone", -1));
        assertEquals(4, defaults.abilityInt(EngineerBalance.GLOBAL_ID, "maxPlates", -1));
        assertEquals(3, defaults.abilityInt(EngineerBalance.GLOBAL_ID, "maxPistons", -1));
        assertEquals(0.10, defaults.ability(
                EngineerBalance.GLOBAL_ID, "plateDamageBonusPerTier", -1), 0.0001);
        assertEquals(0.10, defaults.ability(
                EngineerBalance.GLOBAL_ID, "dispenserDamagePerPlateBlock", -1), 0.0001);
        assertEquals(10, defaults.abilityInt(EngineerBalance.GLOBAL_ID, "dispenserMaxPlateDistance", -1));
        assertEquals(0.01, defaults.ability(
                EngineerBalance.GLOBAL_ID, "dispenserDamageBonusPerGolemPress", -1), 0.0001);
        assertEquals(3.5, defaults.ability(
                EngineerBalance.GLOBAL_ID, "dispenserDamageBonusCap", -1), 0.0001);
        assertEquals(0.001, defaults.ability(
                EngineerBalance.GLOBAL_ID, "doorDamageReductionPerGolemPress", -1), 0.0001);
        assertEquals(0.40, defaults.ability(
                EngineerBalance.GLOBAL_ID, "doorDamageReductionCap", -1), 0.0001);
        assertEquals(10, defaults.abilityInt(EngineerBalance.GLOBAL_ID, "golemPressesPerExtraTarget", -1));
        assertEquals(20, defaults.abilityInt(EngineerBalance.GLOBAL_ID, "tntExtraTargetCap", -1));
        assertEquals(10, defaults.abilityInt(EngineerBalance.GLOBAL_ID, "pistonExtraTargetCap", -1));
        assertEquals(0.001, defaults.ability(
                EngineerBalance.GLOBAL_ID, "slimeSlowPerGolemPress", -1), 0.0001);
        assertEquals(0.80, defaults.ability(EngineerBalance.GLOBAL_ID, "slimeSlowCap", -1), 0.0001);
        assertEquals(0, defaults.abilityInt(EngineerTowers.REDSTONE_DUST.id(), TowerCapacity.CONFIG_KEY, -1));
        assertEquals(20, defaults.abilityTicks(EngineerBalance.GLOBAL_ID, "activeVfxIntervalTicks", -1));
        assertEquals(10, defaults.abilityTicks(EngineerBalance.GLOBAL_ID, "tntFuseVfxIntervalTicks", -1));
        assertEquals(1.1, EngineerBalance.dispenserDamageMultiplier(1), 0.0001);
        assertEquals(1.5, EngineerBalance.dispenserDamageMultiplier(5), 0.0001);
        assertEquals(2.0, EngineerBalance.dispenserDamageMultiplier(10), 0.0001);
        assertEquals(2.0, EngineerBalance.dispenserDamageMultiplier(35), 0.0001);
        assertEquals(1.0, EngineerBalance.plateDamageMultiplier(EngineerTowers.PlateKind.WOOD), 0.0001);
        assertEquals(1.1, EngineerBalance.plateDamageMultiplier(EngineerTowers.PlateKind.STONE), 0.0001);
        assertEquals(1.2, EngineerBalance.plateDamageMultiplier(EngineerTowers.PlateKind.IRON), 0.0001);
        assertEquals(1.3, EngineerBalance.plateDamageMultiplier(EngineerTowers.PlateKind.GOLD), 0.0001);
        assertEquals(480.0, defaults.ability(
                EngineerTowers.trap(EngineerTowers.TrapKind.TNT, 3).id(), "damage", -1), 0.0001);
        assertEquals(16, defaults.abilityInt(
                EngineerTowers.trap(EngineerTowers.TrapKind.TNT, 3).id(), "maxTargets", -1));
        assertEquals(45.0, defaults.ability(
                EngineerTowers.trap(EngineerTowers.TrapKind.DISPENSER, 3).id(), "damage", -1), 0.0001);
        assertEquals(850.0, defaults.towers()
                .get(EngineerTowers.trap(EngineerTowers.TrapKind.DOOR, 3).id()).maxHealth(), 0.0001);
        assertDoesNotThrow(defaults::validateForRuntime);
        ProductionTowerCatalogs.reloadBuiltIns(defaults);
        for (EngineerTowers.TrapKind kind : EngineerTowers.TrapKind.values()) {
            assertEquals(EngineerTowers.trap(kind, 1).displayName(), EngineerTowers.trap(kind, 2).displayName());
            assertEquals("강화", ProductionTowerCatalog.upgrades(EngineerTowers.trap(kind, 1)).getFirst().displayName());
        }

        TowerBalanceConfig partial = new TowerBalanceConfig(
                Map.of(), Map.of(), Map.of(EngineerBalance.GLOBAL_ID, Map.of("activeTicks", 80.0))
        );
        TowerBalanceConfig merged = partial.withMissingDefaults(defaults);
        assertEquals(80, merged.abilityTicks(EngineerBalance.GLOBAL_ID, "activeTicks", -1));
        assertEquals(120, merged.abilityTicks(EngineerBalance.GLOBAL_ID, "doorActiveTicks", -1));
        assertEquals(100, merged.abilityTicks(EngineerBalance.GLOBAL_ID, "plateCooldownTicks", -1));
        assertEquals(10, merged.abilityInt(EngineerBalance.GLOBAL_ID, "dispenserMaxPlateDistance", -1));
        assertEquals(0.10, merged.ability(
                EngineerBalance.GLOBAL_ID, "plateDamageBonusPerTier", -1), 0.0001);
        assertEquals(0.01, merged.ability(
                EngineerBalance.GLOBAL_ID, "dispenserDamageBonusPerGolemPress", -1), 0.0001);
        assertEquals(20, merged.abilityInt(EngineerBalance.GLOBAL_ID, "tntExtraTargetCap", -1));
        assertEquals(0.80, merged.ability(EngineerBalance.GLOBAL_ID, "slimeSlowCap", -1), 0.0001);
        assertEquals(20, merged.abilityTicks(EngineerBalance.GLOBAL_ID, "activeVfxIntervalTicks", -1));

        for (EngineerTowers.TrapKind kind : EngineerTowers.TrapKind.values()) {
            TowerType resolved = TowerBalanceRuntime.resolve(EngineerTowers.trap(kind, 3));
            assertTrue(resolved.description().stream().noneMatch(line -> line.contains("{ability.")));
        }

        for (EngineerTowers.TrapKind kind : List.of(
                EngineerTowers.TrapKind.TNT,
                EngineerTowers.TrapKind.DISPENSER
        )) {
            TowerType resolved = TowerBalanceRuntime.resolve(EngineerTowers.trap(kind, 3));
            assertTrue(resolved.description().stream().anyMatch(line -> line.contains("10%")));
        }

    }

    @Test
    void golemPressBonusesRespectBoundariesCapsAndExistingMultipliers() {
        assertEquals(1.0, EngineerBalance.dispenserPressDamageMultiplier(0), 0.0001);
        assertEquals(1.09, EngineerBalance.dispenserPressDamageMultiplier(9), 0.0001);
        assertEquals(1.10, EngineerBalance.dispenserPressDamageMultiplier(10), 0.0001);
        assertEquals(2.0, EngineerBalance.dispenserPressDamageMultiplier(100), 0.0001);
        assertEquals(3.0, EngineerBalance.dispenserPressDamageMultiplier(200), 0.0001);
        assertEquals(4.5, EngineerBalance.dispenserPressDamageMultiplier(800), 0.0001);

        assertEquals(0.0, EngineerBalance.doorDamageReduction(0), 0.0001);
        assertEquals(0.10, EngineerBalance.doorDamageReduction(100), 0.0001);
        assertEquals(0.40, EngineerBalance.doorDamageReduction(500), 0.0001);
        assertEquals(0.40, EngineerBalance.doorDamageReduction(800), 0.0001);
        assertEquals(0, EngineerBalance.tntExtraTargets(9));
        assertEquals(1, EngineerBalance.tntExtraTargets(10));
        assertEquals(10, EngineerBalance.tntExtraTargets(100));
        assertEquals(20, EngineerBalance.tntExtraTargets(200));
        assertEquals(20, EngineerBalance.tntExtraTargets(800));
        assertEquals(0, EngineerBalance.pistonExtraTargets(9));
        assertEquals(1, EngineerBalance.pistonExtraTargets(10));
        assertEquals(10, EngineerBalance.pistonExtraTargets(100));
        assertEquals(10, EngineerBalance.pistonExtraTargets(800));
        assertEquals(0.55, EngineerBalance.slimeSlow(0.55, 0), 0.0001);
        assertEquals(0.56, EngineerBalance.slimeSlow(0.55, 10), 0.0001);
        assertEquals(0.80, EngineerBalance.slimeSlow(0.55, 500), 0.0001);
        assertEquals(0.80, EngineerBalance.slimeSlow(0.55, 800), 0.0001);

        double composed = EngineerTrapTower.dispenserDamage(3)
                * EngineerBalance.dispenserDamageMultiplier(10)
                * EngineerBalance.plateDamageMultiplier(EngineerTowers.PlateKind.GOLD)
                * EngineerBalance.dispenserPressDamageMultiplier(200);
        assertEquals(351.0, composed, 0.0001);
    }

    @Test
    void pressStateIsPlayerScopedAndRuntimeReloadUsesTheExistingCount() {
        UUID other = UUID.nameUUIDFromBytes("other-engineer".getBytes(StandardCharsets.UTF_8));
        for (int index = 0; index < 10; index++) {
            EngineerPressStates.recordPress(OWNER);
        }
        EngineerPressStates.recordPress(other);
        assertEquals(10, EngineerPressStates.count(OWNER));
        assertEquals(1, EngineerPressStates.count(other));

        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> global = new LinkedHashMap<>(abilities.get(EngineerBalance.GLOBAL_ID));
        global.put("dispenserDamageBonusPerGolemPress", 0.02);
        global.put("golemPressesPerExtraTarget", 5.0);
        abilities.put(EngineerBalance.GLOBAL_ID, global);
        TowerBalanceRuntime.apply(new TowerBalanceConfig(
                defaults.towers(), defaults.upgradeCosts(), abilities,
                defaults.illusionCloneQueue(), defaults.villagerAdv(), defaults.schemaVersion()
        ));

        assertEquals(10, EngineerPressStates.count(OWNER));
        assertEquals(1.20, EngineerBalance.dispenserPressDamageMultiplier(EngineerPressStates.count(OWNER)), 0.0001);
        assertEquals(2, EngineerBalance.tntExtraTargets(EngineerPressStates.count(OWNER)));
        EngineerPressStates.clear(other);
    }

    @Test
    void matchLifecycleClearsPressState() {
        EconomyConfig economy = EconomyConfig.defaultConfig();
        SemionGame game = new SemionGame(economy, WaveConfig.defaultConfig(), new GameArena(Map.of()));
        SemionPlayer player = new SemionPlayer(OWNER, "engineer", TeamId.RED, 1, new PlayerEconomy(economy));
        game.players().put(OWNER, player);
        EngineerTowerJob job = new EngineerTowerJob();
        JobContext context = new JobContext(game, player);

        EngineerPressStates.recordPress(OWNER);
        job.onMatchStarted(context);
        assertEquals(0, EngineerPressStates.count(OWNER));
        EngineerPressStates.recordPress(OWNER);
        job.onEliminated(context);
        assertEquals(0, EngineerPressStates.count(OWNER));
        EngineerPressStates.recordPress(OWNER);
        game.close();
        assertEquals(0, EngineerPressStates.count(OWNER));
    }

    @Test
    void invalidSlowRatioIsRejected() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        String slime = EngineerTowers.trap(EngineerTowers.TrapKind.SLIME, 1).id();
        LinkedHashMap<String, Double> values = new LinkedHashMap<>(abilities.get(slime));
        values.put("slow", 1.2);
        abilities.put(slime, values);
        TowerBalanceConfig invalid = new TowerBalanceConfig(
                defaults.towers(), defaults.upgradeCosts(), abilities,
                defaults.illusionCloneQueue(), defaults.villagerAdv(), defaults.schemaVersion()
        );
        assertThrows(IllegalArgumentException.class, invalid::validateForRuntime);
    }

    @Test
    void tunedDamageRegressionMatchesTheEngineerCeilings() {
        double t3Shot = EngineerTrapTower.dispenserDamage(3);
        double shotsPerSecond = 20.0 / EngineerTrapTower.dispenserInterval(3);
        assertEquals(90.0, t3Shot * shotsPerSecond, 0.0001);
        assertEquals(135.0, t3Shot * shotsPerSecond * EngineerBalance.dispenserDamageMultiplier(5), 0.0001);
        assertEquals(180.0, t3Shot * shotsPerSecond * EngineerBalance.dispenserDamageMultiplier(10), 0.0001);
        assertEquals(180.0, t3Shot * shotsPerSecond * EngineerBalance.dispenserDamageMultiplier(35), 0.0001);
        assertEquals(234.0, t3Shot * shotsPerSecond
                * EngineerBalance.dispenserDamageMultiplier(10)
                * EngineerBalance.plateDamageMultiplier(EngineerTowers.PlateKind.GOLD), 0.0001);

        double tnt = EngineerTrapTower.tntDamage(3);
        assertEquals(480.0, tnt, 0.0001);
        assertEquals(1_440.0, tnt * 3, 0.0001);
        assertEquals(2_400.0, tnt * 5, 0.0001);
        assertEquals(7_680.0, tnt * EngineerTrapTower.tntMaxTargets(3), 0.0001);
        assertEquals(9_984.0, tnt * EngineerTrapTower.tntMaxTargets(3)
                * EngineerBalance.plateDamageMultiplier(EngineerTowers.PlateKind.GOLD), 0.0001);
    }

    @Test
    void invalidEngineerCapsTicksAndTierOrderAreRejected() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertInvalidAbility(defaults, EngineerBalance.GLOBAL_ID, "plateDamageBonusPerTier", 1.1);
        assertInvalidAbility(defaults, EngineerBalance.GLOBAL_ID, "dispenserDamagePerPlateBlock", 1.1);
        assertInvalidAbility(defaults, EngineerBalance.GLOBAL_ID, "dispenserMaxPlateDistance", 36.0);
        assertInvalidAbility(defaults, EngineerBalance.GLOBAL_ID, "doorActiveTicks", 0.0);
        assertInvalidAbility(defaults, EngineerBalance.GLOBAL_ID, "doorActiveTicks", 2.5);
        assertInvalidAbility(defaults, EngineerBalance.GLOBAL_ID, "activeVfxIntervalTicks", 2.5);
        assertInvalidAbility(defaults, EngineerBalance.GLOBAL_ID, "dispenserDamageBonusPerGolemPress", 1.1);
        assertInvalidAbility(defaults, EngineerBalance.GLOBAL_ID, "doorDamageReductionPerGolemPress", 1.1);
        assertInvalidAbility(defaults, EngineerBalance.GLOBAL_ID, "doorDamageReductionCap", 1.0);
        assertInvalidAbility(defaults, EngineerBalance.GLOBAL_ID, "golemPressesPerExtraTarget", 0.0);
        assertInvalidAbility(defaults, EngineerBalance.GLOBAL_ID, "golemPressesPerExtraTarget", 2.5);
        assertInvalidAbility(defaults, EngineerBalance.GLOBAL_ID, "tntExtraTargetCap", 2.5);
        assertInvalidAbility(defaults, EngineerBalance.GLOBAL_ID, "pistonExtraTargetCap", -1.0);
        assertInvalidAbility(defaults, EngineerBalance.GLOBAL_ID, "slimeSlowPerGolemPress", 1.1);
        assertInvalidAbility(defaults, EngineerBalance.GLOBAL_ID, "slimeSlowCap", 1.0);
        assertInvalidAbility(defaults, EngineerBalance.GLOBAL_ID, "slimeSlowCap", 0.50);
        assertInvalidAbility(defaults, EngineerTowers.REDSTONE_DUST.id(), TowerCapacity.CONFIG_KEY, 0.5);
        assertInvalidAbility(defaults,
                EngineerTowers.trap(EngineerTowers.TrapKind.DISPENSER, 2).id(), "intervalTicks", 20.0);
        assertInvalidAbility(defaults,
                EngineerTowers.trap(EngineerTowers.TrapKind.PISTON, 2).id(), "maxTargets", 0.0);
    }

    @Test
    void bundledEngineerDefaultsMatchJavaDefaults() throws Exception {
        try (var input = EngineerTowerCatalogTest.class.getResourceAsStream(
                "/semiontd/balance-defaults/tower_balance.json")) {
            var root = JsonParser.parseReader(new InputStreamReader(
                    java.util.Objects.requireNonNull(input), StandardCharsets.UTF_8)).getAsJsonObject();
            var bundledAbilities = root.getAsJsonObject("abilities");
            TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
            for (var entry : defaults.abilities().entrySet()) {
                if (!entry.getKey().startsWith("engineer_")) {
                    continue;
                }
                var bundled = bundledAbilities.getAsJsonObject(entry.getKey());
                assertEquals(entry.getValue().keySet(), bundled.keySet(), entry.getKey());
                entry.getValue().forEach((key, value) ->
                        assertEquals(value, bundled.get(key).getAsDouble(), 0.0001, entry.getKey() + "." + key));
            }
            for (TowerType type : EngineerTowers.all()) {
                var bundled = root.getAsJsonObject("towers").getAsJsonObject(type.id());
                assertEquals(defaults.towers().get(type.id()).maxHealth(),
                        bundled.get("maxHealth").getAsDouble(), 0.0001, type.id());
            }
        }
    }

    @Test
    void jobLimitsOneGolemThirtyFiveRedstonePartsFourPlatesAndThreePistons() {
        EconomyConfig economy = EconomyConfig.defaultConfig();
        SemionGame game = new SemionGame(economy, WaveConfig.defaultConfig(), new GameArena(Map.of()));
        SemionPlayer player = new SemionPlayer(OWNER, "engineer", TeamId.RED, 1, new PlayerEconomy(economy));
        game.players().put(OWNER, player);
        game.teams().get(TeamId.RED).activate();
        PlayerLane lane = testLane();
        game.teams().get(TeamId.RED).laneGroup().addLane(lane);
        EngineerTowerJob job = new EngineerTowerJob();
        JobContext context = new JobContext(game, player);

        assertTrue(job.canUseTower(context, EngineerTowers.COPPER_GOLEM));
        lane.addTower(new TestTower(EngineerTowers.COPPER_GOLEM, 0));
        assertFalse(job.canUseTower(context, EngineerTowers.COPPER_GOLEM));
        for (int index = 0; index < EngineerBalance.MAX_REDSTONE - 1; index++) {
            lane.addTower(new TestTower(EngineerTowers.REDSTONE_DUST, index + 1));
        }
        lane.addTower(new TestTower(EngineerTowers.repeater(Direction.NORTH), EngineerBalance.MAX_REDSTONE));
        assertFalse(job.canUseTower(context, EngineerTowers.REDSTONE_DUST));
        assertTrue(job.canUseTower(context, EngineerTowers.repeater(Direction.EAST)));
        for (int index = 0; index < EngineerBalance.MAX_PLATES; index++) {
            assertTrue(job.canUseTower(context, EngineerTowers.plate(EngineerTowers.PlateKind.WOOD)));
            lane.addTower(new TestTower(EngineerTowers.plate(EngineerTowers.PlateKind.WOOD), 36 + index));
        }
        assertFalse(job.canUseTower(context, EngineerTowers.plate(EngineerTowers.PlateKind.WOOD)));
        assertTrue(job.canUseTower(context, EngineerTowers.plate(EngineerTowers.PlateKind.STONE)));
        for (int index = 0; index < EngineerBalance.MAX_PISTONS; index++) {
            assertTrue(job.canUseTower(context, EngineerTowers.trap(EngineerTowers.TrapKind.PISTON, 1)));
            lane.addTower(new TestTower(
                    EngineerTowers.trap(EngineerTowers.TrapKind.PISTON, 1),
                    50 + index
            ));
        }
        assertFalse(job.canUseTower(context, EngineerTowers.trap(EngineerTowers.TrapKind.PISTON, 1)));
        assertTrue(job.canUseTower(context, EngineerTowers.trap(EngineerTowers.TrapKind.PISTON, 2)),
                "The three-piston limit must not block upgrading an existing piston.");
    }

    @Test
    void redstoneCircuitsCanBePlacedAndUpgradedAtTheTowerLimitWithoutUsingCapacity() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        EconomyConfig economy = EconomyConfig.defaultConfig();
        SemionGame game = new SemionGame(economy, WaveConfig.defaultConfig(), new GameArena(Map.of()));
        SemionPlayer player = new SemionPlayer(OWNER, "engineer", TeamId.RED, 1, new PlayerEconomy(economy));
        game.players().put(OWNER, player);
        game.teams().get(TeamId.RED).activate();
        PlayerLane lane = testLane();
        game.teams().get(TeamId.RED).laneGroup().addLane(lane);
        for (int index = 0; index < game.towerLimitForPlayer(OWNER); index++) {
            lane.addTower(new TestTower(EngineerTowers.trap(EngineerTowers.TrapKind.DOOR, 1), index));
        }

        assertEquals(game.towerLimitForPlayer(OWNER), game.towerCapacityUsed(OWNER));
        assertTrue(game.canFitTower(OWNER, EngineerTowers.REDSTONE_DUST));
        assertTrue(game.canFitUpgrade(
                OWNER, EngineerTowers.REDSTONE_DUST, EngineerTowers.repeater(Direction.NORTH)));
        assertTrue(game.canFitUpgrade(
                OWNER, EngineerTowers.repeater(Direction.NORTH), EngineerTowers.repeater(Direction.EAST)));
        assertFalse(game.canFitTower(OWNER, EngineerTowers.plate(EngineerTowers.PlateKind.WOOD)));
    }

    @Test
    void redstoneBossBarShowsCurrentConfiguredLimit() {
        assertEquals("레드스톤 - 12/35", EngineerRedstoneBossBarService.title(12, 35).getString());
        assertEquals(12.0f / 35.0f, EngineerRedstoneBossBarService.progress(12, 35), 0.0001f);
        assertEquals(1.0f, EngineerRedstoneBossBarService.progress(99, 35), 0.0001f);
    }

    private static kim.biryeong.semiontd.tower.Tower create(kim.biryeong.semiontd.tower.TowerType type) {
        return ProductionTowerCatalog.find(type.id()).orElseThrow().create(OWNER, TeamId.RED, 1, POSITION);
    }

    private static PlayerLane testLane() {
        Vec3 spawn = new Vec3(0.5, 64.0, 0.5);
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                spawn,
                List.of(new Vec3(0.5, 64.0, 4.5)),
                new Vec3(0.5, 64.0, 10.5),
                BlockBounds.of(new BlockPos(0, 63, 0), new BlockPos(40, 66, 10)),
                List.of(new GridPosition(0, 63, 10))
        );
        return new PlayerLane(TeamId.RED, 1, OWNER, null, layout);
    }

    private static void assertInvalidAbility(
            TowerBalanceConfig defaults,
            String configId,
            String key,
            double value
    ) {
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> invalid = new LinkedHashMap<>(abilities.get(configId));
        invalid.put(key, value);
        abilities.put(configId, invalid);
        TowerBalanceConfig broken = new TowerBalanceConfig(
                defaults.towers(), defaults.upgradeCosts(), abilities,
                defaults.illusionCloneQueue(), defaults.villagerAdv(), defaults.schemaVersion()
        );
        assertThrows(IllegalArgumentException.class, broken::validateForRuntime);
    }

    private static final class TestTower extends Tower {
        private TestTower(TowerType type, int x) {
            super(type, OWNER, TeamId.RED, 1, new GridPosition(x, 64, 1));
        }

        @Override
        protected boolean execute(PlayerLane lane) {
            return false;
        }
    }
}
