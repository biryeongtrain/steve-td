package kim.biryeong.semiontd.tower.futureagency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.FutureAgencyTowerJob;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class FutureAgencyTowerCatalogTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("future-agency-owner".getBytes());

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void loadDefaults() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @AfterEach
    void restoreDefaults() {
        FutureAgencyStates.clear(OWNER);
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void catalogRegistersEighteenTowersAndFourInternalStarters() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());

        var entries = ProductionTowerCatalog.all().stream()
                .filter(entry -> FutureAgencyTowers.isFutureAgencyTower(entry.type()))
                .toList();
        assertEquals(18, entries.size());
        assertEquals(4, entries.stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count());
        assertTrue(JobRegistry.find(FutureAgencyTowerJob.ID).isPresent());
        assertInstanceOf(FutureAgencyLeaderTower.class, create(FutureAgencyTowers.ESCAPEE));
        assertInstanceOf(FutureAgencyAgentTower.class,
                create(FutureAgencyTowers.agent(FutureAgencyRole.COMBAT, 5)));

        assertEquals(0, upgradeCost(FutureAgencyTowers.ESCAPEE, FutureAgencyLeaderTower.RECONSTRUCT));
        assertEquals(800, upgradeCost(FutureAgencyTowers.REBUILDER,
                FutureAgencyLeaderTower.PROMOTE_COMMANDER));
        assertEquals(1500, upgradeCost(FutureAgencyTowers.COMMANDER,
                FutureAgencyLeaderTower.SAVE_WORLD));
        assertEquals(1500, upgradeCost(FutureAgencyTowers.REBUILDER,
                FutureAgencyLeaderTower.SAVE_WORLD));
        assertEquals(100, upgradeCost(FutureAgencyTowers.agent(FutureAgencyRole.COMBAT, 5),
                FutureAgencyTowers.agent(FutureAgencyRole.COMBAT, 4).id()));
        assertEquals(700, upgradeCost(FutureAgencyTowers.agent(FutureAgencyRole.PROTECTION, 2),
                FutureAgencyTowers.agent(FutureAgencyRole.PROTECTION, 1).id()));
        assertEquals(200, new FutureAgencyTowerJob().modifyStartingMineral(null, 200));
        assertEquals(150, ProductionTowerCatalog.find(FutureAgencyTowers.ESCAPEE.id()).orElseThrow()
                .type().mineralCost());
    }

    @Test
    void policyOffersAreStableDistinctAndOnlyOneCanBeChosenPerRound() {
        FutureAgencyStates.PlayerState state = FutureAgencyStates.state(OWNER);
        state.reconstruct();
        state.openRound(7);
        var firstOffers = state.offers();

        assertEquals(30, FutureAgencyPolicy.values().length);
        assertEquals(3, firstOffers.size());
        assertEquals(3, new HashSet<>(firstOffers).size());
        state.openRound(7);
        assertEquals(firstOffers, state.offers());

        FutureAgencyPolicy chosen = firstOffers.getFirst();
        assertTrue(state.choose(chosen));
        assertEquals(1, state.stacks(chosen));
        assertEquals(1, state.policySelections());
        assertTrue(state.offers().isEmpty());
        assertFalse(state.choose(firstOffers.get(1)));

        state.openRound(8);
        assertEquals(3, state.offers().size());
    }

    @Test
    void cleanLaneRewardOffersThreePoliciesTwiceWithoutImmediateRepeats() {
        FutureAgencyStates.PlayerState state = FutureAgencyStates.state(OWNER);
        state.reconstruct();
        state.setNextSelectionLimit(2);
        state.openRound(8);
        var firstOffers = state.offers();

        assertEquals(1, state.selectionNumber());
        assertEquals(2, state.selectionLimit());
        assertTrue(state.choose(firstOffers.getFirst()));
        var secondOffers = state.offers();
        assertEquals(3, secondOffers.size());
        assertTrue(java.util.Collections.disjoint(firstOffers, secondOffers));
        assertEquals(2, state.selectionNumber());
        assertTrue(state.choose(secondOffers.getFirst()));
        assertTrue(state.selectedThisRound());
        assertTrue(state.offers().isEmpty());
        assertEquals(2, state.policySelections());
    }

    @Test
    void secondPolicyRollFallsBackToPreviouslyShownCandidatesWhenPoolIsSmall() {
        FutureAgencyStates.PlayerState state = FutureAgencyStates.state(OWNER);
        state.reconstruct();
        int round = 1;
        while (java.util.Arrays.stream(FutureAgencyPolicy.values())
                .filter(policy -> state.stacks(policy) < policy.maxStacks()).count() > 3) {
            state.openRound(round++);
            assertTrue(state.choose(state.offers().getFirst()));
        }
        state.setNextSelectionLimit(2);
        state.openRound(round);
        var firstOffers = state.offers();
        FutureAgencyPolicy chosen = firstOffers.getFirst();

        assertEquals(3, firstOffers.size());
        assertTrue(state.choose(chosen));
        assertEquals(2, state.offers().size());
        assertFalse(state.offers().contains(chosen));
        assertTrue(firstOffers.containsAll(state.offers()));
    }

    @Test
    void reconstructionCommanderAndWorldSaveArePermanentStateTransitions() {
        FutureAgencyStates.PlayerState state = FutureAgencyStates.state(OWNER);
        assertFalse(state.reconstructed());
        state.reconstruct();
        assertTrue(state.reconstructed());

        for (int round = 1; state.policySelections() < 10; round++) {
            state.openRound(round);
            assertTrue(state.choose(state.offers().getFirst()));
            if (state.policySelections() == 5) state.promoteCommander();
        }
        assertTrue(state.commander());
        state.saveWorld();
        assertTrue(state.worldSaved());
        state.openRound(99);
        assertEquals(3, state.offers().size());
        assertTrue(state.choose(state.offers().getFirst()));
        assertEquals(11, state.policySelections());
    }

    @Test
    void defaultsMergeAndRejectInvalidCap() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        FutureAgencyTowers.all().forEach(type -> assertTrue(defaults.towers().containsKey(type.id())));
        assertEquals(0.65, defaults.ability(FutureAgencyBalance.GLOBAL_ID,
                "damageReductionCap", -1), 0.0001);
        assertEquals(0.10, defaults.ability(FutureAgencyBalance.GLOBAL_ID,
                "policy.agency_tactics", -1), 0.0001);
        assertEquals(0.07, defaults.ability(FutureAgencyBalance.GLOBAL_ID,
                "survivorDamagePerCopy", -1), 0.0001);
        assertEquals(3.0, defaults.ability(FutureAgencyBalance.GLOBAL_ID,
                "survivorDamageCap", -1), 0.0001);
        assertEquals(0.07, FutureAgencyPolicy.REACTION_TRAINING.defaultValue(), 0.0001);
        assertEquals(0.15, FutureAgencyPolicy.PRECISION_FIRE.defaultValue(), 0.0001);
        assertEquals(0.12, FutureAgencyPolicy.FAST_RELOAD.defaultValue(), 0.0001);
        assertEquals(0.50, FutureAgencyPolicy.AREA_SUPPRESSION.defaultValue(), 0.0001);
        assertEquals(2.0, FutureAgencyPolicy.MULTI_TARGET.defaultValue(), 0.0001);
        assertEquals(0.30, FutureAgencyBalance.SUPPRESSION_DENSE_CAP, 0.0001);

        TowerBalanceConfig merged = new TowerBalanceConfig(Map.of(), Map.of(), Map.of(
                FutureAgencyBalance.GLOBAL_ID, Map.of("policy.agency_tactics", 0.04)))
                .withMissingDefaults(defaults);
        assertEquals(150, merged.towers().get(FutureAgencyTowers.ESCAPEE.id()).mineralCost());
        assertEquals(1500, merged.upgradeCost(FutureAgencyTowers.COMMANDER.id(),
                FutureAgencyLeaderTower.SAVE_WORLD, -1));
        assertEquals(0.04, merged.ability(FutureAgencyBalance.GLOBAL_ID,
                "policy.agency_tactics", -1), 0.0001);
        assertEquals(0.65, merged.ability(FutureAgencyBalance.GLOBAL_ID,
                "damageReductionCap", -1), 0.0001);
        assertEquals(3.0, merged.ability(FutureAgencyBalance.GLOBAL_ID,
                "survivorDamageCap", -1), 0.0001);

        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> invalidGlobal = new LinkedHashMap<>(
                abilities.get(FutureAgencyBalance.GLOBAL_ID));
        invalidGlobal.put("damageReductionCap", 1.1);
        abilities.put(FutureAgencyBalance.GLOBAL_ID, invalidGlobal);
        TowerBalanceConfig invalid = new TowerBalanceConfig(
                defaults.towers(), defaults.upgradeCosts(), abilities,
                defaults.illusionCloneQueue(), defaults.villagerAdv(), defaults.schemaVersion());
        assertThrows(IllegalArgumentException.class, invalid::validateForRuntime);

        assertInvalidAbility(defaults, FutureAgencyTowers.agent(FutureAgencyRole.SUPPRESSION, 1).id(),
                "suppressionMaxTargets", 2.5);
        assertInvalidAbility(defaults, FutureAgencyTowers.agent(FutureAgencyRole.PROTECTION, 1).id(),
                "damageReduction", 0.70);
        assertInvalidAbility(defaults, FutureAgencyBalance.GLOBAL_ID,
                "policy.precision_fire", 1.01);
        assertInvalidAbility(defaults, FutureAgencyBalance.GLOBAL_ID,
                "survivorDamagePerCopy", 1.01);
    }

    @Test
    void leaderCannotBeSoldBeforeSavingTheWorld() {
        FutureAgencyLeaderTower leader = (FutureAgencyLeaderTower) create(FutureAgencyTowers.REBUILDER);
        FutureAgencyStates.state(OWNER).reconstruct();
        var save = ProductionTowerCatalog.upgrade(
                ProductionTowerCatalog.find(FutureAgencyTowers.REBUILDER.id()).orElseThrow().type(),
                FutureAgencyLeaderTower.SAVE_WORLD).orElseThrow();
        assertFalse(leader.canBeSold());
        assertTrue(leader.showsUnavailableUpgrade(null, save));
        assertFalse(leader.meetsUpgradeRequirements(null, save));
        assertEquals(20.0, FutureAgencyTowers.REBUILDER.damage(), 0.0001);
        assertEquals(7.0, FutureAgencyTowers.REBUILDER.range(), 0.0001);
        assertEquals(32.0, FutureAgencyTowers.COMMANDER.damage(), 0.0001);
    }

    @Test
    void futureAgencyDefaultsMatchDpsAndDefenseTargets() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        assertTower(defaults, FutureAgencyTowers.COMMANDER, 1000, 32, 14);
        assertTower(defaults, FutureAgencyTowers.agent(FutureAgencyRole.COMBAT, 1), 650, 48, 10);
        assertTower(defaults, FutureAgencyTowers.agent(FutureAgencyRole.SUPPRESSION, 1), 700, 38, 13);
        assertTower(defaults, FutureAgencyTowers.agent(FutureAgencyRole.PROTECTION, 1), 1300, 32, 14);

        double combatDps = 48.0 * 20.0 / 10.0;
        assertEquals(96.0, combatDps, 0.0001);
        assertEquals(192.0, combatDps * 2.0, 0.0001);
        double tenAttackPolicies = combatDps * 2.0
                * (1.0 + 0.12 + 3 * 0.10 + 3 * 0.15 + 0.20)
                * (1.0 + 0.08 + 3 * 0.12);
        assertEquals(572.31, tenAttackPolicies, 0.01);

        double suppressionDps = 38.0 * 20.0 / 13.0;
        assertEquals(58.46, suppressionDps, 0.01);
        assertEquals(128.62, suppressionDps * (1.0 + 2.0 * 0.60), 0.01);
        assertEquals(198.77, suppressionDps * (1.0 + 4.0 * 0.60), 0.01);
        assertEquals(0.65, defaults.ability(FutureAgencyBalance.GLOBAL_ID,
                "damageReductionCap", -1), 0.0001);
    }

    @Test
    void survivorDamageScalesByAgencyStageAndCapsAtThreeHundredPercent() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        FutureAgencyStates.PlayerState state = FutureAgencyStates.state(OWNER);
        assertEquals(0.07, FutureAgencyBalance.survivorDamage(state, 1), 0.0001);
        state.reconstruct();
        assertEquals(0.14, FutureAgencyBalance.survivorDamage(state, 1), 0.0001);
        for (int round = 1; round <= 5; round++) {
            state.openRound(round);
            assertTrue(state.choose(state.offers().getFirst()));
        }
        state.promoteCommander();
        assertEquals(0.21, FutureAgencyBalance.survivorDamage(state, 1), 0.0001);
        assertEquals(3.0, FutureAgencyBalance.survivorDamage(state, 100), 0.0001);
    }

    @Test
    void leaderDialogOrdersSaveBeforePoliciesAndAgentStatsAllowNullTarget() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        var upgrades = ProductionTowerCatalog.upgrades(
                ProductionTowerCatalog.find(FutureAgencyTowers.REBUILDER.id()).orElseThrow().type());
        assertEquals(FutureAgencyLeaderTower.SAVE_WORLD, upgrades.getFirst().id());
        assertTrue(FutureAgencyPolicy.fromUpgradeId(upgrades.get(1).id()).isPresent());

        FutureAgencyAgentTower agent = (FutureAgencyAgentTower) create(
                FutureAgencyTowers.agent(FutureAgencyRole.COMBAT, 5));
        assertEquals(agent.type().damage(), agent.modifyAttackDamage(null, null, agent.type().damage()), 0.0001);
    }

    private static long upgradeCost(kim.biryeong.semiontd.tower.TowerType type, String id) {
        return ProductionTowerCatalog.upgrade(
                ProductionTowerCatalog.find(type.id()).orElseThrow().type(), id).orElseThrow().mineralCost();
    }

    private static kim.biryeong.semiontd.tower.Tower create(
            kim.biryeong.semiontd.tower.TowerType type) {
        return ProductionTowerCatalog.find(type.id()).orElseThrow()
                .create(OWNER, TeamId.RED, 1, new GridPosition(0, 64, 0));
    }

    private static void assertTower(TowerBalanceConfig config,
                                    kim.biryeong.semiontd.tower.TowerType type,
                                    double health, double damage, int interval) {
        TowerBalanceConfig.TowerStats stats = config.towers().get(type.id());
        assertEquals(health, stats.maxHealth(), 0.0001);
        assertEquals(damage, stats.damage(), 0.0001);
        assertEquals(interval, stats.attackIntervalTicks());
    }

    private static void assertInvalidAbility(TowerBalanceConfig defaults, String configId,
                                             String key, double value) {
        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        LinkedHashMap<String, Double> changed = new LinkedHashMap<>(abilities.get(configId));
        changed.put(key, value);
        abilities.put(configId, changed);
        TowerBalanceConfig invalid = new TowerBalanceConfig(
                defaults.towers(), defaults.upgradeCosts(), abilities,
                defaults.illusionCloneQueue(), defaults.villagerAdv(), defaults.schemaVersion());
        assertThrows(IllegalArgumentException.class, invalid::validateForRuntime);
    }
}
