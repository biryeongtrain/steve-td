package kim.biryeong.semiontd.tower.end;

import static kim.biryeong.semiontd.tower.end.EndConfig.Ability.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

class EndTowerTransferTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("end-transfer-owner".getBytes());

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void reloadCatalogs() {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @AfterEach
    void resetBalance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void exposedTowerListCannotBypassLaneMembership() {
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        assertThrows(UnsupportedOperationException.class, () -> lane.towers().add(tower(EndTowers.T1_ENDERMITE_TOWER, 1)));
        lane.addTower(dragon);
        assertEquals(List.of(dragon), lane.towers());
    }

    @Test
    void onlyFullyTransferredTowerIsCountedWhileStatsTransferGradually() {
        applyTransferDuration(4);
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        EndTower enderman = tower(EndTowers.T1_ENDERMITE_TOWER, 1);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        lane.addTower(enderman);
        enderman.onWaveStarted(lane, 1);
        tick(dragon, lane, 3);
        double partialRawPermanentDamage = 0.3;
        double partialRawRoundDamage = 4.95;
        double expectedPartialPermanentDamage = expectedDamageBonus(partialRawPermanentDamage);
        double expectedPartialTotalDamage = expectedDamageBonus(partialRawPermanentDamage + partialRawRoundDamage);
        double expectedPartialRoundDamage = expectedPartialTotalDamage - expectedPartialPermanentDamage;
        assertEquals(0, dragon.endCrystalCount());
        assertTrue(lane.towers().contains(enderman));
        assertEquals(expectedPartialRoundDamage, dragon.roundDamageBonus(), 0.0001);
        assertEquals(expectedPartialPermanentDamage, dragon.permanentDamageBonus(), 0.0001);
        assertEquals(expectedPartialTotalDamage, dragon.damageBonus(), 0.0001);
        assertEquals(0.0, dragon.healthBonus(), 0.0001);
        assertEquals(0.75, enderman.transferProgress(), 0.0001);
        tick(dragon, lane, 1);
        double completedRawPermanentDamage = 0.4;
        double completedRawRoundDamage = 6.6;
        double expectedCompletedPermanentDamage = expectedDamageBonus(completedRawPermanentDamage);
        double expectedCompletedTotalDamage = expectedDamageBonus(completedRawPermanentDamage + completedRawRoundDamage);
        double expectedCompletedRoundDamage = expectedCompletedTotalDamage - expectedCompletedPermanentDamage;
        assertEquals(1, dragon.endCrystalCount());
        assertTrue(lane.towers().contains(enderman));
        assertEquals(0.0, enderman.health(), 0.0001);
        assertEquals(0.0, enderman.transferProgress(), 0.0001);
        assertEquals(expectedCompletedRoundDamage, dragon.roundDamageBonus(), 0.0001);
        assertEquals(expectedCompletedPermanentDamage, dragon.permanentDamageBonus(), 0.0001);
        assertEquals(expectedCompletedTotalDamage, dragon.damageBonus(), 0.0001);
        assertEquals(0.0, dragon.healthBonus(), 0.0001);
        assertEquals(1, dragon.roundCompletedTransferCount());
        assertEquals(19, dragon.adjustAttackInterval(20));
        tick(dragon, lane, 4);
        assertEquals(1, dragon.endCrystalCount());
        assertTrue(lane.towers().contains(enderman));
        assertEquals(0.0, enderman.health(), 0.0001);
        assertEquals(expectedCompletedTotalDamage, dragon.damageBonus(), 0.0001);
    }

    @Test
    void interruptedTransferRollsBackStatsAndDoesNotCountTower() {
        applyTransferDuration(4);
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        EndTower endCrystalLine = tower(EndTowers.T1_ENDERMITE_TOWER, 1);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        lane.addTower(endCrystalLine);
        tick(dragon, lane, 2);
        lane.removeTower(endCrystalLine);
        tick(dragon, lane, 1);
        assertEquals(0, dragon.endCrystalCount());
        assertEquals(0.0, dragon.roundDamageBonus(), 0.0001);
        assertEquals(0.0, dragon.permanentDamageBonus(), 0.0001);
        assertEquals(0.0, endCrystalLine.transferProgress(), 0.0001);
    }

    @Test
    void interruptedTransferAlsoRollsBackDragonEvolutionState() {
        applyEndAbilities(Map.of(
                "transferTicks", 4.0,
                "dragonEvolution", 200.05,
                "roundHealthRatio", 0.50,
                "permanentHealthRatio", 0.0
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        EndTower shulker = tower(EndTowers.T1_SHULKER_TOWER, 1);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        lane.addTower(shulker);
        tick(dragon, lane, 2);
        double expectedTransferredHealth = expectedHealthBonus(25.0);
        assertEquals(EndTowerState.DRAGON, dragon.state());
        assertEquals(200.0 + expectedTransferredHealth, dragon.currentMaxHealth(), 0.0001);
        lane.removeTower(shulker);
        dragon.tick(lane);
        assertEquals(EndTowerState.PHANTOM, dragon.state());
        assertEquals(200.0, dragon.currentMaxHealth(), 0.0001);
        assertEquals(0.0, dragon.finalDamageBonus(), 0.0001);
    }

    @Test
    void interruptedHealthTransferNeverHealsTheCoreForFree() {
        applyEndAbilities(Map.of(
                "transferTicks", 4.0,
                "roundHealthRatio", 0.50,
                "permanentHealthRatio", 0.0,
                "transferHealRatio", 0.0,
                "transferHeal", 0.0
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        EndTower firstShulker = tower(EndTowers.T1_SHULKER_TOWER, 1);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.syncHealth(100.0);
        lane.addTower(firstShulker);
        tick(dragon, lane, 2);
        double expectedTransferredHealth = expectedHealthBonus(25.0);
        assertEquals(200.0 + expectedTransferredHealth, dragon.currentMaxHealth(), 0.0001);
        assertEquals(100.0, dragon.health(), 0.0001);
        lane.removeTower(firstShulker);
        dragon.tick(lane);
        assertEquals(200.0, dragon.currentMaxHealth(), 0.0001);
        assertEquals(100.0, dragon.health(), 0.0001);
        EndTower secondShulker = tower(EndTowers.T1_SHULKER_TOWER, 2);
        lane.addTower(secondShulker);
        tick(dragon, lane, 2);
        lane.removeTower(secondShulker);
        dragon.tick(lane);
        assertEquals(200.0, dragon.currentMaxHealth(), 0.0001);
        assertEquals(100.0, dragon.health(), 0.0001);
    }

    @Test
    void typeRefreshPreservesHealthAfterTransferredMaxHealthIsRecalculated() {
        applyTransferDuration(1);
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        EndTower shulker = tower(EndTowers.T1_SHULKER_TOWER, 1);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        lane.addTower(shulker);
        dragon.tick(lane);
        dragon.syncHealth(200.2);
        dragon.refreshType(dragon.type(), lane);
        double expectedMaxHealth = 200.0 + expectedHealthBonus(54.0);
        assertEquals(expectedMaxHealth, dragon.currentMaxHealth(), 0.0001);
        assertEquals(200.2, dragon.health(), 0.0001);
    }

    @Test
    void typeRefreshRestartsAnActiveTransferWithTheNewBalanceSnapshot() {
        applyEndAbilities(Map.of(
                "transferTicks", 4.0,
                "roundHealthRatio", 0.50,
                "permanentHealthRatio", 0.0
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        EndTower shulker = tower(EndTowers.T1_SHULKER_TOWER, 1);
        lane.addTower(dragon);
        lane.addTower(shulker);
        dragon.onWaveStarted(lane, 1);
        tick(dragon, lane, 2);
        assertEquals(0.50, shulker.transferProgress(), 0.0001);
        assertEquals(expectedHealthBonus(25.0), dragon.roundHealthBonus(), 0.0001);
        applyEndAbilities(Map.of(
                "transferTicks", 1.0,
                "roundHealthRatio", 0.20,
                "permanentHealthRatio", 0.0
        ));
        dragon.refreshType(dragon.type(), lane);
        assertEquals(0.0, shulker.transferProgress(), 0.0001);
        assertEquals(0.0, dragon.roundHealthBonus(), 0.0001);
        dragon.tick(lane);
        assertEquals(expectedHealthBonus(20.0), dragon.roundHealthBonus(), 0.0001);
        assertEquals(0.0, shulker.health(), 0.0001);
    }

    @Test
    void zeroEndBalanceValueCanBecomeRuntimeState() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> end = new LinkedHashMap<>(abilities.get(EndTower.CONFIG_ID));
        end.put("transferTicks", 0.0);
        abilities.put(EndTower.CONFIG_ID, end);
        TowerBalanceConfig config = new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities
        );
        assertDoesNotThrow(() -> TowerBalanceRuntime.apply(config));
        assertEquals(0.0, EndConfig.RUNTIME.value(TRANSFER_TICKS), 0.0001);
    }

    @Test
    void endValuesDoNotRequireRatioIntegerOrCrossFieldOrdering() {
        assertDoesNotThrow(() -> TowerBalanceRuntime.apply(endConfig(Map.of("splashDamageRatio", 1.01))));
        assertDoesNotThrow(() -> TowerBalanceRuntime.apply(endConfig(Map.of("transferTicks", 1.5))));
        assertDoesNotThrow(() -> TowerBalanceRuntime.apply(endConfig(Map.of("phantomScaleBase", 2.0, "phantomScaleCap", 1.0))));
        assertDoesNotThrow(() -> TowerBalanceRuntime.apply(endConfig(Map.of("attackSpeedMinimumTicks", 16.0))));
        assertDoesNotThrow(() -> TowerBalanceRuntime.apply(endConfig(Map.of("damageScale", 0.0))));
        assertThrows(IllegalArgumentException.class, () -> TowerBalanceRuntime.apply(endConfig(Map.of("damageScale", -0.01))));
    }

    @Test
    void legacyEndConfigReceivesTheNewScalingDefaultsWithoutOverwritingExistingValues() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> end = new LinkedHashMap<>(abilities.get(EndTower.CONFIG_ID));
        end.remove("healthThreshold");
        end.remove("healthScale");
        end.remove("damageThreshold");
        end.remove("damageScale");
        end.put("roundDamageRatio", 0.5);
        abilities.put(EndTower.CONFIG_ID, end);

        TowerBalanceConfig merged = new TowerBalanceConfig(
                defaults.towers(),
                defaults.upgradeCosts(),
                abilities
        ).withMissingDefaults(defaults);

        assertEquals(3000.0, merged.ability(EndTower.CONFIG_ID, "healthThreshold", -1.0), 0.0001);
        assertEquals(500.0, merged.ability(EndTower.CONFIG_ID, "healthScale", -1.0), 0.0001);
        assertEquals(140.0, merged.ability(EndTower.CONFIG_ID, "damageThreshold", -1.0), 0.0001);
        assertEquals(20.0, merged.ability(EndTower.CONFIG_ID, "damageScale", -1.0), 0.0001);
        assertEquals(0.5, merged.ability(EndTower.CONFIG_ID, "roundDamageRatio", -1.0), 0.0001);

        TowerBalanceRuntime.apply(merged);
        assertEquals(3000.0, EndConfig.RUNTIME.value(HEALTH_THRESHOLD), 0.0001);
        assertEquals(500.0, EndConfig.RUNTIME.value(HEALTH_SCALE), 0.0001);
        assertEquals(140.0, EndConfig.RUNTIME.value(DAMAGE_THRESHOLD), 0.0001);
        assertEquals(20.0, EndConfig.RUNTIME.value(DAMAGE_SCALE), 0.0001);
        assertEquals(0.5, EndConfig.RUNTIME.value(ROUND_DAMAGE_RATIO), 0.0001);
    }

    @Test
    void nonFiniteTowerStatsAreRejectedAndLargeAbilityValuesAreAccepted() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, TowerBalanceConfig.TowerStats> towers = new LinkedHashMap<>(defaults.towers());
        TowerBalanceConfig.TowerStats base = towers.get(EndTowers.BASE_END_TOWER.id());
        towers.put(
                EndTowers.BASE_END_TOWER.id(),
                new TowerBalanceConfig.TowerStats(
                        base.mineralCost(),
                        Double.NaN,
                        base.range(),
                        base.damage(),
                        base.attackIntervalTicks(),
                        base.aggroPriority()
                )
        );
        TowerBalanceConfig invalidStats = new TowerBalanceConfig(towers, defaults.upgradeCosts(), defaults.abilities());
        assertThrows(IllegalArgumentException.class, () -> TowerBalanceRuntime.apply(invalidStats));
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> end = new LinkedHashMap<>(abilities.get(EndTower.CONFIG_ID));
        end.put("transferTicks", (double) Integer.MAX_VALUE + 1.0);
        abilities.put(EndTower.CONFIG_ID, end);
        TowerBalanceConfig oversizedInteger = new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities);
        assertDoesNotThrow(() -> TowerBalanceRuntime.apply(oversizedInteger));
    }

    @Test
    void everyShulkerOrEndCrystalTransferReducesAttackIntervalForTheCurrentRoundOnly() {
        applyEndAbilities(Map.of(
                "transferTicks", 1.0,
                "roundHealthRatio", 0.0,
                "permanentHealthRatio", 0.0
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        dragon.syncHealth(10.0);
        lane.addTower(tower(EndTowers.T1_SHULKER_TOWER, 1));
        dragon.tick(lane);
        assertEquals(1, dragon.roundCompletedTransferCount());
        assertEquals(40.0, dragon.health(), 0.0001);
        assertEquals(19, dragon.adjustAttackInterval(20));
        lane.addTower(tower(EndTowers.T1_SHULKER_TOWER, 2));
        dragon.tick(lane);
        assertEquals(2, dragon.roundCompletedTransferCount());
        assertEquals(70.0, dragon.health(), 0.0001);
        assertEquals(18, dragon.adjustAttackInterval(20));
        lane.addTower(tower(EndTowers.T1_ENDERMITE_TOWER, 3));
        dragon.tick(lane);
        assertEquals(3, dragon.roundCompletedTransferCount());
        assertEquals(100.0, dragon.health(), 0.0001);
        assertEquals(17, dragon.adjustAttackInterval(20));
        dragon.resetRoundTransferBonuses(null);
        assertEquals(0, dragon.roundCompletedTransferCount());
        assertEquals(20, dragon.adjustAttackInterval(20));
    }

    @Test
    void shulkerTransfersFiftyPercentOfItsHealthForTheCurrentRound() {
        applyEndAbilities(Map.of(
                "transferTicks", 1.0,
                "attackSpeedStacks", 1.0
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        EndTower shulker = tower(EndTowers.T1_SHULKER_TOWER, 1);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        dragon.syncHealth(10.0);
        lane.addTower(shulker);
        dragon.tick(lane);
        double rawPermanentHealth = 4.0;
        double rawRoundHealth = 50.0;
        double expectedPermanentHealth = expectedHealthBonus(rawPermanentHealth);
        double expectedTotalHealth = expectedHealthBonus(rawPermanentHealth + rawRoundHealth);
        double expectedRoundHealth = expectedTotalHealth - expectedPermanentHealth;
        assertEquals(expectedRoundHealth, dragon.roundHealthBonus(), 0.0001);
        assertEquals(expectedPermanentHealth, dragon.permanentHealthBonus(), 0.0001);
        assertEquals(200.0 + expectedTotalHealth, dragon.currentMaxHealth(), 0.0001);
        assertEquals(40.0, dragon.health(), 0.0001);
        assertEquals(0.0, shulker.health(), 0.0001);
        assertEquals(1, dragon.roundCompletedTransferCount());
        assertEquals(19, dragon.adjustAttackInterval(20));
    }

    @Test
    void shulkerStacksGrantCappedRegenerationThatHealsOncePerSecond() {
        applyEndAbilities(Map.of(
                "transferTicks", 1.0,
                "transferHeal", 0.0,
                "roundHealthRatio", 0.0,
                "permanentHealthRatio", 0.0,
                "regenerationStacks", 1.0,
                "regenerationStep", 2.0,
                "regenerationCap", 3.0
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        lane.addTower(tower(EndTowers.T1_SHULKER_TOWER, 1));
        lane.addTower(tower(EndTowers.T1_SHULKER_TOWER, 2));
        dragon.tick(lane);
        assertEquals(3.0, dragon.regenerationPerSecond(), 0.0001);
        dragon.syncHealth(10.0);
        tick(dragon, lane, 18);
        assertEquals(10.0, dragon.health(), 0.0001);
        dragon.tick(lane);
        assertEquals(13.0, dragon.health(), 0.0001);
    }

    @Test
    void activeEndCrystalLineTransfersDoNotGrantPeriodicHealing() {
        applyEndAbilities(Map.of(
                "transferTicks", 40.0,
                "transferHeal", 0.0
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        dragon.syncHealth(10.0);
        lane.addTower(tower(EndTowers.T1_ENDERMITE_TOWER, 1));
        lane.addTower(tower(EndTowers.T1_ENDERMITE_TOWER, 2));
        tick(dragon, lane, 19);
        assertEquals(10.0, dragon.health(), 0.0001);
        dragon.tick(lane);
        assertEquals(10.0, dragon.health(), 0.0001);
        tick(dragon, lane, 20);
        assertEquals(10.0, dragon.health(), 0.0001);
        assertEquals(2, dragon.roundCompletedTransferCount());
    }

    @Test
    void activeShulkerTransfersHealFivePercentOfTraitFreeBaseMaxHealthPerSecond() {
        applyEndAbilities(Map.ofEntries(
                Map.entry("transferTicks", 40.0),
                Map.entry("transferHeal", 0.0),
                Map.entry("roundHealthRatio", 0.0),
                Map.entry("permanentHealthRatio", 0.0),
                Map.entry("transferHealRatio", 0.05)
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        dragon.syncHealth(10.0);
        lane.addTower(tower(EndTowers.T1_SHULKER_TOWER, 1));
        lane.addTower(tower(EndTowers.T2_SHULKER_TOWER, 2));
        lane.addTower(tower(EndTowers.T3_SHULKER_TOWER, 3));
        tick(dragon, lane, 19);
        assertEquals(10.0, dragon.health(), 0.0001);
        dragon.tick(lane);
        assertEquals(32.5, dragon.health(), 0.0001);
    }

    @Test
    void completedTransferDoesNotReceiveAnExtraPeriodicTransferHeal() {
        applyEndAbilities(Map.of(
                "transferTicks", 1.0,
                "transferHeal", 0.0
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.syncHealth(10.0);
        lane.addTower(tower(EndTowers.T1_ENDERMITE_TOWER, 1));
        dragon.tick(lane);
        assertEquals(10.0, dragon.health(), 0.0001);
        assertEquals(1, dragon.roundCompletedTransferCount());
    }

    @Test
    void copyingTheCoreRollsBackIncompleteTransferContributions() {
        applyEndAbilities(Map.of(
                "transferTicks", 4.0,
                "roundHealthRatio", 0.50,
                "permanentHealthRatio", 0.05
        ));
        PlayerLane lane = lane();
        EndTower original = tower(EndTowers.BASE_END_TOWER, 0);
        EndTower source = tower(EndTowers.T1_SHULKER_TOWER, 1);
        lane.addTower(original);
        lane.addTower(source);
        original.onWaveStarted(lane, 1);
        tick(original, lane, 2);
        double rawPermanentHealth = 2.5;
        double rawRoundHealth = 25.0;
        double expectedPermanentHealth = expectedHealthBonus(rawPermanentHealth);
        double expectedTotalHealth = expectedHealthBonus(rawPermanentHealth + rawRoundHealth);
        double expectedRoundHealth = expectedTotalHealth - expectedPermanentHealth;
        assertEquals(expectedRoundHealth, original.roundHealthBonus(), 0.0001);
        assertEquals(expectedPermanentHealth, original.permanentHealthBonus(), 0.0001);
        EndTower replacement = tower(EndTowers.BASE_END_TOWER, 2);
        replacement.copyFrom(original, 0);
        assertEquals(0.0, original.roundHealthBonus(), 0.0001);
        assertEquals(0.0, original.permanentHealthBonus(), 0.0001);
        assertEquals(0.0, replacement.roundHealthBonus(), 0.0001);
        assertEquals(0.0, replacement.permanentHealthBonus(), 0.0001);
        assertEquals(0.0, source.transferProgress(), 0.0001);
    }

    @Test
    void coreReturnsToEggEachRoundAndPermanentHealthReturnsAfterHatching() {
        applyTransferDuration(1);
        PlayerLane lane = lane();
        EndTower core = tower(EndTowers.BASE_END_TOWER, 0);
        EndTower shulker = tower(EndTowers.T1_SHULKER_TOWER, 1);
        lane.addTower(core);
        core.onWaveStarted(lane, 1);
        core.tick(lane);
        lane.addTower(shulker);
        core.tick(lane);
        double expectedPermanentHealth = expectedHealthBonus(4.0);
        double expectedRoundTotalHealth = expectedHealthBonus(54.0);
        assertEquals(expectedPermanentHealth, core.permanentHealthBonus(), 0.0001);
        assertEquals(200.0 + expectedRoundTotalHealth, core.currentMaxHealth(), 0.0001);
        core.resetForRound(null);
        assertEquals(EndTowerState.EGG, core.state());
        assertEquals(200.0, core.currentMaxHealth(), 0.0001);
        assertEquals(expectedPermanentHealth, core.permanentHealthBonus(), 0.0001);
        assertEquals(0.0, core.splashRadius(), 0.0001);
        core.onWaveStarted(null, 2);
        core.tick(null);
        assertEquals(EndTowerState.PHANTOM, core.state());
        assertEquals(200.0 + expectedPermanentHealth, core.currentMaxHealth(), 0.0001);
        assertEquals(expectedPermanentHealth, core.permanentHealthBonus(), 0.0001);
    }

    @Test
    void completedTransfersAccumulateConfiguredFamilyStatsForTheRound() {
        applyTransferDuration(1);
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        for (int index = 0; index < 20; index++) {
            lane.addTower(tower(EndTowers.T1_ENDERMITE_TOWER, index + 1));
            lane.addTower(tower(EndTowers.T1_SHULKER_TOWER, index + 21));
        }
        tick(dragon, lane, 1);
        double rawPermanentHealth = 80.0;
        double rawRoundHealth = 1000.0;
        double rawTotalHealth = rawPermanentHealth + rawRoundHealth;
        double expectedPermanentHealth = expectedHealthBonus(rawPermanentHealth);
        double expectedTotalHealth = expectedHealthBonus(rawTotalHealth);
        double expectedRoundHealth = expectedTotalHealth - expectedPermanentHealth;
        double rawPermanentDamage = 8.0;
        double rawRoundDamage = 132.0;
        double rawTotalDamage = rawPermanentDamage + rawRoundDamage;
        double expectedPermanentDamage = expectedDamageBonus(rawPermanentDamage);
        double expectedTotalDamage = expectedDamageBonus(rawTotalDamage);
        double expectedRoundDamage = expectedTotalDamage - expectedPermanentDamage;
        assertEquals(20, dragon.endCrystalCount());
        assertEquals(20, dragon.shulkerCount());
        assertEquals(40, dragon.roundCompletedTransferCount());
        assertEquals(41, lane.towers().size());
        assertEquals(40, lane.towers().stream().filter(tower -> tower != dragon && tower.health() <= 0.0).count());
        assertEquals(expectedRoundHealth, dragon.roundHealthBonus(), 0.0001);
        assertEquals(expectedRoundDamage, dragon.roundDamageBonus(), 0.0001);
        assertEquals(expectedPermanentHealth, dragon.permanentHealthBonus(), 0.0001);
        assertEquals(expectedPermanentDamage, dragon.permanentDamageBonus(), 0.0001);
        assertEquals(expectedTotalHealth, dragon.healthBonus(), 0.0001);
        assertEquals(expectedTotalDamage, dragon.damageBonus(), 0.0001);
        assertEquals(200.0 + expectedTotalHealth, dragon.effectBaseMaxHealth(), 0.0001);
        assertEquals(5.0 * (1.0 + expectedTotalDamage / dragon.type().damage()), dragon.modifyAttackDamage(null, null, 5.0), 0.0001);
        assertEquals(5.0, dragon.adjustAttackRange(5.0), 0.0001);
        assertEquals(1.0, dragon.splashRadius(), 0.0001);
        assertEquals(5, dragon.adjustAttackInterval(20));
        int damageReductionStacks = Math.max(1, EndConfig.RUNTIME.integer(DAMAGE_REDUCTION_STACKS));
        double damageReductionStep = EndConfig.RUNTIME.value(DAMAGE_REDUCTION_STEP);
        double damageReductionCap = EndConfig.RUNTIME.value(DAMAGE_REDUCTION_CAP);
        double expectedDamageReduction = Math.min(damageReductionCap, (dragon.shulkerCount() / damageReductionStacks) * damageReductionStep);
        assertEquals(100.0 * (1.0 - expectedDamageReduction), dragon.modifyIncomingDamage(null, null, 100.0), 0.0001);
        dragon.resetRoundTransferBonuses(null);
        assertEquals(0.0, dragon.roundHealthBonus(), 0.0001);
        assertEquals(0.0, dragon.roundDamageBonus(), 0.0001);
        assertEquals(expectedPermanentHealth, dragon.permanentHealthBonus(), 0.0001);
        assertEquals(expectedPermanentDamage, dragon.permanentDamageBonus(), 0.0001);
        assertEquals(200.0 + expectedPermanentHealth, dragon.effectBaseMaxHealth(), 0.0001);
        assertEquals(5.0 * (1.0 + expectedPermanentDamage / dragon.type().damage()), dragon.modifyAttackDamage(null, null, 5.0), 0.0001);
        assertEquals(200.0 + expectedPermanentHealth, dragon.previewHatchedMaxHealth(), 0.0001);
        assertEquals(dragon.type().damage() + expectedPermanentDamage, dragon.previewHatchedAttackDamage(), 0.0001);
        assertEquals(15, dragon.previewHatchedAttackIntervalTicks());
        assertEquals(0, dragon.roundCompletedTransferCount());
        assertEquals(15, dragon.adjustAttackInterval(15));
    }

    @Test
    void cumulativeLineBonusesUseTheirRequestedFamiliesAndRespectEveryCap() {
        applyEndAbilities(Map.ofEntries(
                Map.entry("transferTicks", 1.0),
                Map.entry("splash1", 1.0),
                Map.entry("splash2", 2.0),
                Map.entry("splashStep", 0.25),
                Map.entry("splashCap", 0.5),
                Map.entry("attackSpeedStacks", 1.0),
                Map.entry("attackSpeedCap", 2.0),
                Map.entry("attackRangeStacks", 1.0),
                Map.entry("attackRangeStep", 2.0),
                Map.entry("attackRangeCap", 5.0),
                Map.entry("lifeStealStacks", 1.0),
                Map.entry("lifeStealCap", 0.02),
                Map.entry("damageReductionStacks", 1.0),
                Map.entry("damageReductionStep", 0.02),
                Map.entry("damageReductionCap", 0.05)
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        for (int index = 0; index < 3; index++) {
            lane.addTower(tower(EndTowers.T1_ENDERMITE_TOWER, index + 1));
            lane.addTower(tower(EndTowers.T1_SHULKER_TOWER, index + 4));
        }
        dragon.tick(lane);
        assertEquals(3, dragon.endCrystalCount());
        assertEquals(3, dragon.shulkerCount());
        assertEquals(0.5, dragon.splashRadius(), 0.0001);
        assertEquals(5.0, dragon.attackRangeBonus(), 0.0001);
        assertEquals(10.0, dragon.adjustAttackRange(5.0), 0.0001);
        assertEquals(12, dragon.adjustAttackInterval(20));
        assertEquals(95.0, dragon.modifyIncomingDamage(null, null, 100.0), 0.0001);
    }

    @Test
    void resolvedDamageRemainsUncapped() {
        applyEndAbilities(Map.ofEntries(
                Map.entry("transferTicks", 1.0),
                Map.entry("roundDamageRatio", 1.0),
                Map.entry("permanentDamageRatio", 0.0)
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        lane.addTower(tower(EndTowers.T3_END_CRYSTAL_TOWER, 1));
        dragon.tick(lane);
        double expectedTransferredDamage = expectedDamageBonus(20.0);
        double expectedAttackDamage = 10.0 + expectedTransferredDamage;
        assertEquals(expectedTransferredDamage, dragon.roundDamageBonus(), 0.0001);
        assertEquals(expectedAttackDamage, dragon.previewHatchedAttackDamage(), 0.0001);
        assertEquals(expectedAttackDamage, dragon.modifyAttackDamage(null, null, 10.0), 0.0001);
        assertEquals(30.0, dragon.modifyResolvedAttackDamage(null, null, 30.0), 0.0001);
        assertEquals(20.0, dragon.modifyResolvedAttackDamage(null, null, 20.0), 0.0001);
        assertEquals(-10.0, dragon.modifyResolvedAttackDamage(null, null, -10.0), 0.0001);
        assertEquals(30.0, dragon.modifyResolvedOutgoingDamage(null, null, 30.0), 0.0001);
        assertEquals(20.0, dragon.modifyResolvedOutgoingDamage(null, null, 20.0), 0.0001);
        assertEquals(-10.0, dragon.modifyResolvedOutgoingDamage(null, null, -10.0), 0.0001);
    }

    @Test
    void configuredDamageThresholdAndScaleApplyToTransferredEndTowerDamage() {
        applyEndAbilities(Map.ofEntries(
                Map.entry("transferTicks", 1.0),
                Map.entry("roundDamageRatio", 1.0),
                Map.entry("permanentDamageRatio", 0.0),
                Map.entry("damageThreshold", 10.0),
                Map.entry("damageScale", 10.0)
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        for (int index = 0; index < 3; index++) {
            lane.addTower(tower(EndTowers.T3_END_CRYSTAL_TOWER, index + 1));
        }

        dragon.tick(lane);

        double expectedDamageBonus = 10.0 + 10.0 * Math.log1p(5.0);
        assertEquals(27.9176, expectedDamageBonus, 0.0001);
        assertEquals(expectedDamageBonus, dragon.roundDamageBonus(), 0.0001);
        assertEquals(expectedDamageBonus, dragon.damageBonus(), 0.0001);
        assertEquals(10.0 + expectedDamageBonus, dragon.previewHatchedAttackDamage(), 0.0001);
        assertEquals(10.0 + expectedDamageBonus, dragon.modifyAttackDamage(null, null, 10.0), 0.0001);
    }

    @Test
    void splashRatioUsesUncappedPrimaryDamage() {
        applyEndAbilities(Map.of(
                "splashDamageRatio", 0.66
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        double resolvedPrimaryDamage = dragon.modifyResolvedOutgoingDamage(null, null, 1_000.0);
        assertEquals(1_000.0, resolvedPrimaryDamage, 0.0001);
        assertEquals(660.0, dragon.resolvedSplashDamage(resolvedPrimaryDamage), 0.0001);
        assertEquals(0.0, dragon.resolvedSplashDamage(Double.NaN), 0.0001);
    }

    @Test
    void extremeAttackIntervalConfigurationCannotOverflow() {
        applyEndAbilities(Map.ofEntries(
                Map.entry("transferTicks", 1.0),
                Map.entry("attackSpeedStacks", 1.0),
                Map.entry("attackSpeedStep", (double) Integer.MAX_VALUE),
                Map.entry("attackSpeedCap", (double) Integer.MAX_VALUE),
                Map.entry("transferAttackSpeedStacks", 1.0),
                Map.entry("transferAttackSpeedStep", (double) Integer.MAX_VALUE),
                Map.entry("attackSpeedMinimumTicks", 5.0)
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        lane.addTower(tower(EndTowers.T3_END_CRYSTAL_TOWER, 1));
        dragon.tick(lane);
        assertEquals(3, dragon.endCrystalCount());
        assertEquals(1, dragon.roundCompletedTransferCount());
        assertEquals(5, dragon.adjustAttackInterval(20));
        assertEquals(5, dragon.previewHatchedAttackIntervalTicks());
    }

    @Test
    void everyStackBasedStatReachesItsCapAtThreeHundredStacks() {
        applyEndAbilities(Map.ofEntries(
                Map.entry("transferTicks", 1.0),
                Map.entry("transferHeal", 0.0),
                Map.entry("roundHealthRatio", 0.0),
                Map.entry("roundDamageRatio", 0.0),
                Map.entry("permanentHealthRatio", 0.0),
                Map.entry("permanentDamageRatio", 0.0)
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        for (int index = 0; index < 99; index++) {
            lane.addTower(tower(EndTowers.T3_END_CRYSTAL_TOWER, index + 1));
            lane.addTower(tower(EndTowers.T3_SHULKER_TOWER, index + 101));
        }
        lane.addTower(tower(EndTowers.T2_ENDERMAN_TOWER, 201));
        lane.addTower(tower(EndTowers.T2_SHULKER_TOWER, 202));
        dragon.tick(lane);
        dragon.resetRoundTransferBonuses(null);
        assertEquals(299, dragon.endCrystalCount());
        assertEquals(299, dragon.shulkerCount());
        assertEquals(6, dragon.adjustAttackInterval(15));
        assertEquals(4.0, dragon.splashRadius(), 0.0001);
        assertEquals(7.5, dragon.adjustAttackRange(5.0), 0.0001);
        assertEquals(81.0, dragon.modifyIncomingDamage(null, null, 100.0), 0.0001);
        assertEquals(29.0, dragon.regenerationPerSecond(), 0.0001);
        lane.addTower(tower(EndTowers.T1_ENDERMITE_TOWER, 203));
        lane.addTower(tower(EndTowers.T1_SHULKER_TOWER, 204));
        dragon.tick(lane);
        dragon.resetRoundTransferBonuses(null);
        assertEquals(300, dragon.endCrystalCount());
        assertEquals(300, dragon.shulkerCount());
        assertEquals(5, dragon.adjustAttackInterval(15));
        assertEquals(5.0, dragon.splashRadius(), 0.0001);
        assertEquals(8.0, dragon.adjustAttackRange(5.0), 0.0001);
        assertEquals(80.0, dragon.modifyIncomingDamage(null, null, 100.0), 0.0001);
        assertEquals(30.0, dragon.regenerationPerSecond(), 0.0001);
    }

    @Test
    void hatchedCoreStartsWithNoSplashAndGainsFirstBlockAtTenStacks() {
        applyEndAbilities(Map.of(
                "transferTicks", 1.0,
                "transferHeal", 0.0,
                "roundDamageRatio", 0.0,
                "permanentDamageRatio", 0.0
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        for (int index = 0; index < 9; index++) {
            lane.addTower(tower(EndTowers.T1_ENDERMITE_TOWER, index + 1));
        }
        dragon.tick(lane);
        assertEquals(9, dragon.endCrystalCount());
        assertEquals(0.0, dragon.splashRadius(), 0.0001);
        lane.addTower(tower(EndTowers.T1_ENDERMITE_TOWER, 10));
        dragon.tick(lane);
        assertEquals(10, dragon.endCrystalCount());
        assertEquals(1.0, dragon.splashRadius(), 0.0001);
    }

    @Test
    void completedTransfersUseRegisteredTowerTiersAsStackWeight() {
        applyTransferDuration(1);
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        lane.addTower(tower(EndTowers.T2_ENDERMAN_TOWER, 1));
        lane.addTower(tower(EndTowers.T3_END_CRYSTAL_TOWER, 2));
        lane.addTower(tower(EndTowers.T2_SHULKER_TOWER, 3));
        lane.addTower(tower(EndTowers.T3_SHULKER_TOWER, 4));
        dragon.tick(lane);
        assertEquals(5, dragon.endCrystalCount());
        assertEquals(5, dragon.shulkerCount());
        assertEquals(4, dragon.roundCompletedTransferCount());
    }

    @Test
    void dragonEggAndHatchedPhantomAreStatesOfOneTowerType() {
        applyTransferDuration(1);
        EndTower tower = tower(EndTowers.BASE_END_TOWER, 0);
        assertEquals(EndTowerState.EGG, tower.state());
        assertEquals(1.0, tower.entityAnchorYOffset(), 0.0001);
        assertTrue(BlockDisplayVisual.matches(tower.visual()));
        assertEquals(
                Blocks.DRAGON_EGG.defaultBlockState(),
                BlockDisplayVisual.blockState(tower.visual())
        );
        tower.onWaveStarted(null, 1);
        tower.tick(null);
        assertEquals(EndTowerState.PHANTOM, tower.state());
        assertTrue(tower.stopsBeforeFriendlyTowers());
        assertEquals(2.0, tower.entityAnchorYOffset(), 0.0001);
        assertEquals(EndTowers.BASE_END_TOWER, tower.type());
        assertEquals("minecraft:phantom", tower.visual().entityTypeId());
        assertTrue(tower.visual().blockbenchModel().isEmpty());
        assertEquals(0.0, tower.finalDamageBonus(), 0.0001);
        double dragonEvolution = EndConfig.RUNTIME.value(DRAGON_EVOLUTION);
        tower.syncMaxHealth(dragonEvolution, true);
        tower.tick(null);
        assertEquals(EndTowerState.DRAGON, tower.state());
        assertFalse(tower.stopsBeforeFriendlyTowers());
        assertEquals(2.0, tower.entityAnchorYOffset(), 0.0001);
        double dragonFinalDamage = EndConfig.RUNTIME.value(DRAGON_FINAL_DAMAGE);
        double dragonRangeBonus = EndConfig.RUNTIME.value(DRAGON_RANGE_BONUS);
        assertEquals(dragonFinalDamage, tower.finalDamageBonus(), 0.0001);
        assertEquals(
                EndTowers.BASE_END_TOWER.range() + dragonRangeBonus,
                tower.adjustAttackRange(EndTowers.BASE_END_TOWER.range()),
                0.0001
        );
        tower.resetForRound(null);
        assertEquals(EndTowerState.EGG, tower.state());
        assertEquals(1.0, tower.entityAnchorYOffset(), 0.0001);
        assertTrue(BlockDisplayVisual.matches(tower.visual()));
        assertEquals(EndTowers.BASE_END_TOWER.maxHealth(), tower.currentMaxHealth(), 0.0001);
    }

    @Test
    void activeTransferKeepsItsCompletionHealWhenBalanceReloads() {
        applyEndAbilities(Map.of("transferTicks", 4.0, "transferHeal", 30.0));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        EndTower feeder = tower(EndTowers.T1_ENDERMITE_TOWER, 1);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        dragon.syncHealth(10.0);
        lane.addTower(feeder);
        tick(dragon, lane, 2);
        applyEndAbilities(Map.of("transferHeal", 999.0));
        tick(dragon, lane, 2);
        assertEquals(40.0, dragon.health(), 0.0001);
    }

    @Test
    void activeTransferKeepsItsPeriodicHealingRatioWhenBalanceReloads() {
        applyEndAbilities(Map.of("transferTicks", 60.0, "transferHealRatio", 0.05));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        EndTower shulker = tower(EndTowers.T1_SHULKER_TOWER, 1);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        dragon.syncHealth(10.0);
        lane.addTower(shulker);
        tick(dragon, lane, 20);
        assertEquals(15.0, dragon.health(), 0.0001);
        applyEndAbilities(Map.of("transferHealRatio", 1.0));
        tick(dragon, lane, 20);
        assertEquals(20.0, dragon.health(), 0.0001);
    }

    @Test
    void deathNotificationSkipsTowerRemovedByAnEarlierCallback() {
        PlayerLane lane = lane();
        CallbackTower remover = new CallbackTower(EndTowers.T1_ENDERMITE_TOWER, 0);
        CallbackTower removed = new CallbackTower(EndTowers.T1_ENDERMITE_TOWER, 1);
        CallbackTower destroyed = new CallbackTower(EndTowers.T1_ENDERMITE_TOWER, 2);
        remover.removeOnNotification = removed;
        lane.addTower(remover);
        lane.addTower(removed);
        lane.addTower(destroyed);
        assertTrue(lane.killTower(destroyed));
        assertEquals(1, remover.notifications);
        assertEquals(0, removed.notifications);
        assertFalse(lane.towers().contains(removed));
    }

    @Test
    void shulkerTiersReduceIncomingDamageByConfiguredAmount() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        assertEquals(90.0, tower(EndTowers.T1_SHULKER_TOWER, 0).modifyIncomingDamage(null, null, 100.0), 0.0001);
        assertEquals(70.0, tower(EndTowers.T2_SHULKER_TOWER, 0).modifyIncomingDamage(null, null, 100.0), 0.0001);
        assertEquals(50.0, tower(EndTowers.T3_SHULKER_TOWER, 0).modifyIncomingDamage(null, null, 100.0), 0.0001);
    }

    private static double expectedDamageBonus(double raw) {
        return expectedSoftCap(raw, EndConfig.RUNTIME.value(DAMAGE_THRESHOLD), EndConfig.RUNTIME.value(DAMAGE_SCALE));
    }

    private static double expectedHealthBonus(double raw) {
        return expectedSoftCap(raw, EndConfig.RUNTIME.value(HEALTH_THRESHOLD), EndConfig.RUNTIME.value(HEALTH_SCALE));
    }

    private static double expectedSoftCap(double raw, double threshold, double scale) {
        if (raw <= 0.0) {
            return 0.0;
        }
        if (raw <= threshold) {
            return raw;
        }
        return threshold + scale * Math.log1p((raw - threshold) / scale);
    }

    private static void applyTransferDuration(int durationTicks) {
        applyEndAbilities(Map.of("transferTicks", (double) durationTicks));
    }

    private static void applyEndAbilities(Map<String, Double> overrides) {
        TowerBalanceRuntime.apply(endConfig(overrides));
    }

    private static TowerBalanceConfig endConfig(Map<String, Double> overrides) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> end = new LinkedHashMap<>(abilities.get(EndTower.CONFIG_ID));
        end.putAll(overrides);
        abilities.put(EndTower.CONFIG_ID, end);
        return new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities);
    }

    private static EndTower tower(kim.biryeong.semiontd.tower.TowerType type, int x) {
        return new EndTower(type, OWNER, TeamId.BLUE, 1, new GridPosition(x, 64, 0));
    }

    private static void tick(EndTower dragon, PlayerLane lane, int ticks) {
        for (int index = 0; index < ticks; index++) {
            dragon.tick(lane);
        }
    }

    private static PlayerLane lane() {
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                new Vec3(0.5, 64.0, 0.5),
                List.of(new Vec3(0.5, 64.0, 2.5)),
                new Vec3(0.5, 64.0, 10.5),
                BlockBounds.of(new BlockPos(0, 63, 0), new BlockPos(64, 66, 10)),
                List.of(new GridPosition(0, 63, 10))
        );
        return new PlayerLane(TeamId.BLUE, 1, OWNER, null, layout);
    }

    private static final class CallbackTower extends Tower {
        private Tower removeOnNotification;
        private int notifications;

        private CallbackTower(TowerType type, int x) {
            super(type, OWNER, TeamId.BLUE, 1, new GridPosition(x, 64, 0));
        }

        @Override
        public void onNearbyTowerDeath(PlayerLane lane, Tower destroyedTower) {
            notifications++;
            if (removeOnNotification != null) {lane.removeTower(removeOnNotification);}
        }

        @Override
        protected boolean execute(PlayerLane lane) {
            return false;
        }
    }
}
