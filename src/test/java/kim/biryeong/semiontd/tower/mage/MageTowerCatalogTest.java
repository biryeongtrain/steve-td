package kim.biryeong.semiontd.tower.mage;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.MageTowerJob;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

final class MageTowerCatalogTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("mage-owner".getBytes());

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void reset() {
        MageStates.clear(OWNER);
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void catalogExposesThreeStartersAndTemporaryZeroCostChoices() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());

        var entries = ProductionTowerCatalog.all().stream()
                .filter(entry -> MageTowers.isMageTower(entry.type()))
                .toList();
        assertEquals(MageTowers.all().size(), entries.size());
        assertEquals(3, entries.stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count());
        assertTrue(JobRegistry.find(MageTowerJob.ID).isPresent());
        assertEquals(MageSpell.values().length, ProductionTowerCatalog.upgrades(MageTowers.WIZARD).size());
        assertEquals(MageTowers.predictionTypes().size(), ProductionTowerCatalog.upgrades(MageTowers.PROPHET).size());
        assertTrue(ProductionTowerCatalog.upgrades(MageTowers.WIZARD).stream()
                .allMatch(option -> option.mineralCost() == 0));
        assertTrue(ProductionTowerCatalog.upgrades(MageTowers.PROPHET).stream()
                .allMatch(option -> option.mineralCost() == 0));
        assertFalse(ProductionTowerCatalog.hasUpgrades(MageTowers.MAGIC_CORE));
    }

    @Test
    void factoriesCreateDedicatedRuntimeTypes() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        GridPosition position = new GridPosition(0, 64, 0);

        assertInstanceOf(MageWizardTower.class, ProductionTowerCatalog.find(MageTowers.WIZARD.id())
                .orElseThrow().create(OWNER, TeamId.RED, 1, position));
        assertInstanceOf(MageProphetTower.class, ProductionTowerCatalog.find(MageTowers.PROPHET.id())
                .orElseThrow().create(OWNER, TeamId.RED, 1, position));
        assertInstanceOf(MageCoreTower.class, ProductionTowerCatalog.find(MageTowers.MAGIC_CORE.id())
                .orElseThrow().create(OWNER, TeamId.RED, 1, position));
    }

    @Test
    void manaStartsOnceCapsAtOneThousandAndCoreBreakPreservesIt() {
        MageStates.PlayerState state = MageStates.state(OWNER);
        state.grantStartingMana();
        state.grantStartingMana();
        assertEquals(30, state.mana());

        state.addMana(5_000);
        assertEquals(1_000, state.mana());
        MageCoreTower core = new MageCoreTower(
                MageTowers.MAGIC_CORE, OWNER, TeamId.RED, 1, new GridPosition(0, 64, 0)
        );
        core.onDeath(null);
        assertEquals(800, state.mana());
        state.clearMana();
        state.grantStartingMana();
        assertEquals(0, state.mana(), "Reinstalling the core must not grant starting mana twice.");
    }

    @Test
    void spellSelectionDoesNotSpendManaBeforeTheSpellActuallyCasts() {
        MageStates.PlayerState state = MageStates.state(OWNER);
        state.grantStartingMana();
        MageWizardTower support = new MageWizardTower(
                MageTowers.spellType(MageSpell.MAGIC_AMPLIFICATION), OWNER, TeamId.RED, 1,
                new GridPosition(0, 64, 0)
        );
        support.onPlaced(null);
        support.onWaveStarted(null, 1);
        assertEquals(30, state.mana());
        assertFalse(support.spellUsed());
        assertEquals(8, support.naturalManaProduction());

        MageStates.clear(OWNER);
        state = MageStates.state(OWNER);
        state.grantStartingMana();
        MageWizardTower unusedAttack = new MageWizardTower(
                MageTowers.spellType(MageSpell.MANA_MISSILE), OWNER, TeamId.RED, 1,
                new GridPosition(0, 64, 0)
        );
        unusedAttack.onPlaced(null);
        unusedAttack.onWaveStarted(null, 1);
        assertEquals(30, state.mana());
        assertFalse(unusedAttack.spellUsed());
        assertEquals(8, unusedAttack.naturalManaProduction());
    }

    @Test
    void livingCoreProducesFiftyManaAtRoundEnd() {
        MageStates.PlayerState state = MageStates.state(OWNER);
        PlayerLane lane = testLane();
        MageCoreTower core = new MageCoreTower(
                MageTowers.MAGIC_CORE, OWNER, TeamId.RED, 1,
                new GridPosition(0, 64, 0), new GridPosition(0, 64, 0)
        );
        lane.addTower(core);
        assertEquals(30, state.mana());

        MageTowerLifecycle.finishRound(lane, OWNER);

        assertEquals(80, state.mana());
    }

    @Test
    void defaultsPublishEveryMageValue() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertTrue(MageTowers.all().stream().allMatch(type -> defaults.towers().containsKey(type.id())));
        assertEquals(1_000.0, defaults.ability(MageBalance.GLOBAL_ID, "manaCapacity", -1), 0.0001);
        assertEquals(50.0, defaults.ability(MageBalance.GLOBAL_ID, "coreMana", -1), 0.0001);
        assertEquals(0.20, defaults.ability(MageBalance.GLOBAL_ID, "coreBreakManaLossRatio", -1), 0.0001);
        assertEquals(400.0, defaults.ability(MageBalance.GLOBAL_ID, "dimensional_collapseManaCost", -1), 0.0001);
        assertEquals(0.65, defaults.ability(MageBalance.GLOBAL_ID, "rangedBarrierReduction", -1), 0.0001);
        assertEquals(0.6, defaults.ability(MageBalance.GLOBAL_ID, "amplificationBonus", -1), 0.0001);
        assertEquals(0.7, defaults.ability(MageBalance.GLOBAL_ID, "manaDamageBonusAtCapacity", -1), 0.0001);
        assertEquals(3.0, defaults.ability(MageBalance.GLOBAL_ID, "maxSpellDamageMultiplier", -1), 0.0001);
        assertEquals(10.0, defaults.ability(MageBalance.GLOBAL_ID, "missileDamage", -1), 0.0001);
        assertEquals(30.0, defaults.ability(MageBalance.GLOBAL_ID, "windCutterDamage", -1), 0.0001);
        assertEquals(10.0, defaults.ability(MageBalance.GLOBAL_ID, "windCutterMaxTargets", -1), 0.0001);
        assertEquals(105.0, defaults.ability(MageBalance.GLOBAL_ID, "manaBombDamage", -1), 0.0001);
        assertEquals(90.0, defaults.ability(MageBalance.GLOBAL_ID, "chainDamage1", -1), 0.0001);
        assertEquals(60.0, defaults.ability(MageBalance.GLOBAL_ID, "frostWaveDamage", -1), 0.0001);
        assertEquals(380.0, defaults.ability(MageBalance.GLOBAL_ID, "collapseDamage", -1), 0.0001);

    }

    @Test
    void missingDefaultsMergeKeepsOverridesAndValidationRejectsBadRatios() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        TowerBalanceConfig partial = new TowerBalanceConfig(
                Map.of(), Map.of(), Map.of(MageBalance.GLOBAL_ID, Map.of("manaCapacity", 777.0))
        );
        TowerBalanceConfig merged = partial.withMissingDefaults(defaults);
        assertEquals(777.0, merged.ability(MageBalance.GLOBAL_ID, "manaCapacity", -1), 0.0001);
        assertEquals(80.0, merged.ability(MageBalance.GLOBAL_ID, "prophecyReward", -1), 0.0001);
        assertEquals(3.0, merged.ability(MageBalance.GLOBAL_ID, "maxSpellDamageMultiplier", -1), 0.0001);
        assertEquals(10, merged.abilityInt(MageBalance.GLOBAL_ID, "windCutterMaxTargets", -1));

        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> mage = new LinkedHashMap<>(abilities.get(MageBalance.GLOBAL_ID));
        mage.put("rangedBarrierReduction", 1.25);
        abilities.put(MageBalance.GLOBAL_ID, mage);
        TowerBalanceConfig invalid = new TowerBalanceConfig(
                defaults.towers(), defaults.upgradeCosts(), abilities,
                defaults.illusionCloneQueue(), defaults.villagerAdv(), defaults.schemaVersion()
        );
        assertThrows(IllegalArgumentException.class, invalid::validateForRuntime);
    }

    @Test
    void naturalManaOnlyCountsLivingTowersWithALivingCore() {
        PlayerLane wizardLane = testLane();
        MageStates.PlayerState state = MageStates.state(OWNER);
        wizardLane.addTower(coreAt(0));
        for (int index = 1; index <= 3; index++) {
            wizardLane.addTower(new MageWizardTower(
                    MageTowers.WIZARD, OWNER, TeamId.RED, 1,
                    new GridPosition(index, 64, 0), new GridPosition(index, 64, 0)
            ));
        }
        int before = state.mana();
        MageTowerLifecycle.finishRound(wizardLane, OWNER);
        assertEquals(74, state.mana() - before);

        MageStates.clear(OWNER);
        PlayerLane prophetLane = testLane();
        state = MageStates.state(OWNER);
        prophetLane.addTower(coreAt(0));
        for (int index = 1; index <= 3; index++) {
            prophetLane.addTower(new MageProphetTower(
                    MageTowers.PROPHET, OWNER, TeamId.RED, 1,
                    new GridPosition(index, 64, 0), new GridPosition(index, 64, 0)
            ));
        }
        before = state.mana();
        MageTowerLifecycle.finishRound(prophetLane, OWNER);
        assertEquals(95, state.mana() - before);

        MageStates.clear(OWNER);
        PlayerLane deadLane = testLane();
        state = MageStates.state(OWNER);
        MageCoreTower core = coreAt(0);
        deadLane.addTower(core);
        MageWizardTower deadWizard = new MageWizardTower(
                MageTowers.WIZARD, OWNER, TeamId.RED, 1,
                new GridPosition(1, 64, 0), new GridPosition(1, 64, 0)
        );
        MageProphetTower deadProphet = new MageProphetTower(
                MageTowers.PROPHET, OWNER, TeamId.RED, 1,
                new GridPosition(2, 64, 0), new GridPosition(2, 64, 0)
        );
        deadWizard.syncHealth(0.0);
        deadProphet.syncHealth(0.0);
        deadLane.addTower(deadWizard);
        deadLane.addTower(deadProphet);
        before = state.mana();
        MageTowerLifecycle.finishRound(deadLane, OWNER);
        assertEquals(50, state.mana() - before);
        core.syncHealth(0.0);
        before = state.mana();
        MageTowerLifecycle.finishRound(deadLane, OWNER);
        assertEquals(0, state.mana() - before);
    }

    @Test
    void coreSaleClearsSelectionsAndManaButPreservesWizardCasts() {
        PlayerLane lane = testLane();
        MageCoreTower core = coreAt(0);
        lane.addTower(core);
        MageWizardTower wizard = new MageWizardTower(
                MageTowers.spellType(MageSpell.MANA_MISSILE), OWNER, TeamId.RED, 1,
                new GridPosition(1, 64, 0), new GridPosition(1, 64, 0)
        );
        lane.addTower(wizard);
        wizard.onWaveStarted(lane, 1);
        MageStates.state(OWNER).addMana(100);
        assertTrue(wizard.tryBeginCast(MageSpell.MANA_MISSILE));
        MageProphetTower prophet = new MageProphetTower(
                MageTowers.predictionTypes().values().iterator().next(), OWNER, TeamId.RED, 1,
                new GridPosition(2, 64, 0), new GridPosition(2, 64, 0)
        );
        lane.addTower(prophet);

        assertTrue(lane.removeTower(core));

        assertEquals(0, MageStates.state(OWNER).mana());
        MageWizardTower resetWizard = lane.towers().stream()
                .filter(MageWizardTower.class::isInstance)
                .map(MageWizardTower.class::cast)
                .findFirst().orElseThrow();
        MageProphetTower resetProphet = lane.towers().stream()
                .filter(MageProphetTower.class::isInstance)
                .map(MageProphetTower.class::cast)
                .findFirst().orElseThrow();
        assertEquals(MageTowers.WIZARD.id(), resetWizard.type().id());
        assertEquals(1, resetWizard.spellCasts());
        assertEquals(MageTowers.PROPHET.id(), resetProphet.type().id());
    }

    @Test
    void finalDefenseKeepsSupportActivationAndSpellDamageIsCapped() {
        MageStates.state(OWNER).addMana(1_000);
        MageWizardTower support = new MageWizardTower(
                MageTowers.spellType(MageSpell.MAGIC_AMPLIFICATION), OWNER, TeamId.RED, 1,
                new GridPosition(0, 64, 0)
        );
        support.onWaveStarted(null, 1);
        assertTrue(support.tryBeginCast(MageSpell.MAGIC_AMPLIFICATION));
        support.moveToFinalDefense(null, new GridPosition(5, 64, 0));
        assertTrue(support.spellUsed());

        assertEquals(3.0, MageWizardTower.spellDamageMultiplier(1.0, 0.7, true, 0.6, 1.45, 3.0), 0.0001);
        assertEquals(1.35, MageWizardTower.spellDamageMultiplier(0.5, 0.7, false, 0.6, 1.0, 3.0), 0.0001);
    }

    @Test
    void maximumSpellDpsMatchesTheModerateCeilings() {
        double multiplier = MageBalance.MAX_SPELL_DAMAGE_MULTIPLIER;
        double missile = MageBalance.MISSILE_DAMAGE * MageBalance.MISSILE_COUNT * multiplier * 20.0
                / (MageSpell.MANA_MISSILE.defaultCooldownTicks()
                + (MageBalance.MISSILE_COUNT - 1) * MageBalance.MISSILE_INTERVAL_TICKS);
        assertEquals(40.0, missile, 0.0001);

        double wind = MageBalance.WIND_CUTTER_DAMAGE * multiplier * 20.0
                / MageSpell.WIND_CUTTER.defaultCooldownTicks();
        assertEquals(18.0, wind, 0.0001);
        assertEquals(54.0, wind * 3, 0.0001);
        assertEquals(90.0, wind * 5, 0.0001);
        assertEquals(180.0, wind * MageBalance.WIND_CUTTER_MAX_TARGETS, 0.0001);

        double bomb = MageBalance.MANA_BOMB_DAMAGE * multiplier * 20.0
                / (MageSpell.MANA_BOMB.defaultCooldownTicks() + MageBalance.MANA_BOMB_DELAY_TICKS);
        assertEquals(45.0, bomb, 0.0001);
        assertEquals(135.0, bomb * 3, 0.0001);
        assertEquals(225.0, bomb * 5, 0.0001);
        assertEquals(315.0, bomb * MageBalance.MANA_BOMB_MAX_TARGETS, 0.0001);

        double chainPerDamage = multiplier * 20.0 / MageSpell.CHAIN_LIGHTNING.defaultCooldownTicks();
        assertEquals(38.57, MageBalance.CHAIN_LIGHTNING_DAMAGE[0] * chainPerDamage, 0.01);
        assertEquals(90.0, (90.0 + 70.0 + 50.0) * chainPerDamage, 0.0001);
        assertEquals(115.71, (90.0 + 70.0 + 50.0 + 35.0 + 25.0) * chainPerDamage, 0.01);
        assertEquals(122.14, java.util.Arrays.stream(MageBalance.CHAIN_LIGHTNING_DAMAGE).sum() * chainPerDamage, 0.01);

        double frost = MageBalance.FROST_WAVE_DAMAGE * multiplier * 20.0
                / MageSpell.FROST_WAVE.defaultCooldownTicks();
        assertEquals(22.5, frost, 0.0001);
        assertEquals(67.5, frost * 3, 0.0001);
        assertEquals(112.5, frost * 5, 0.0001);
        assertEquals(225.0, frost * MageBalance.FROST_WAVE_MAX_TARGETS, 0.0001);

        double collapse = MageBalance.DIMENSIONAL_COLLAPSE_DAMAGE * multiplier * 20.0
                / (MageSpell.DIMENSIONAL_COLLAPSE.defaultCooldownTicks()
                + MageBalance.DIMENSIONAL_COLLAPSE_DELAY_TICKS);
        assertEquals(51.82, collapse, 0.01);
        assertEquals(155.45, collapse * 3, 0.01);
        assertEquals(259.09, collapse * 5, 0.01);
    }

    @Test
    void invalidMageCapsRatiosAndRankOrderAreRejected() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertInvalidAbility(defaults, "amplificationBonus", 1.01);
        assertInvalidAbility(defaults, "windCutterMaxTargets", 2.5);
        assertInvalidAbility(defaults, "windCutterMaxTargets", 0.0);
        assertInvalidAbility(defaults, "intermediateDamageMultiplier", 0.9);
        assertInvalidAbility(defaults, "maxSpellDamageMultiplier", 1.3);
    }

    @Test
    void bundledMageDefaultsMatchJavaDefaults() throws Exception {
        try (var input = MageTowerCatalogTest.class.getResourceAsStream(
                "/semiontd/balance-defaults/tower_balance.json")) {
            var root = JsonParser.parseReader(new InputStreamReader(
                    java.util.Objects.requireNonNull(input), StandardCharsets.UTF_8)).getAsJsonObject();
            var bundled = root.getAsJsonObject("abilities").getAsJsonObject(MageBalance.GLOBAL_ID);
            TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
            var javaDefaults = defaults.abilities().get(MageBalance.GLOBAL_ID);
            assertEquals(javaDefaults.keySet(), bundled.keySet());
            javaDefaults.forEach((key, value) ->
                    assertEquals(value, bundled.get(key).getAsDouble(), 0.0001, key));
        }
    }

    @Test
    void manaBossBarShowsCurrentConfiguredCapacity() {
        assertEquals("마나 - 30/1000", MageManaBossBarService.title(30, 1_000).getString());
        assertEquals(0.03f, MageManaBossBarService.progress(30, 1_000), 0.0001f);
        assertEquals(1.0f, MageManaBossBarService.progress(2_000, 1_000), 0.0001f);
    }

    private static PlayerLane testLane() {
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                new Vec3(0.5, 64.0, 0.5),
                List.of(new Vec3(5.5, 64.0, 0.5)),
                new Vec3(10.5, 64.0, 0.5),
                BlockBounds.of(new BlockPos(-4, 60, -4), new BlockPos(14, 70, 4)),
                List.of(new GridPosition(8, 64, 0))
        );
        return new PlayerLane(TeamId.RED, 1, OWNER, null, layout);
    }

    private static MageCoreTower coreAt(int x) {
        GridPosition position = new GridPosition(x, 64, 0);
        return new MageCoreTower(MageTowers.MAGIC_CORE, OWNER, TeamId.RED, 1, position, position);
    }

    private static void assertInvalidAbility(TowerBalanceConfig defaults, String key, double value) {
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> mage = new LinkedHashMap<>(abilities.get(MageBalance.GLOBAL_ID));
        mage.put(key, value);
        abilities.put(MageBalance.GLOBAL_ID, mage);
        TowerBalanceConfig invalid = new TowerBalanceConfig(
                defaults.towers(), defaults.upgradeCosts(), abilities,
                defaults.illusionCloneQueue(), defaults.villagerAdv(), defaults.schemaVersion()
        );
        assertThrows(IllegalArgumentException.class, invalid::validateForRuntime);
    }
}
