package kim.biryeong.semiontd.tower.end;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class EndTowerAbsorptionTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("ender-absorption-owner".getBytes());

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
    void onlyFullyAbsorbedTowerIsCountedWhileStatsTransferGradually() {
        applyAbsorptionDuration(4);
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_ENDER_TOWER, 0);
        EndTower enderman = tower(EndTowers.T1_ENDERMITE_TOWER, 1);
        lane.towers().add(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        lane.towers().add(enderman);

        tick(dragon, lane, 3);

        assertEquals(0, dragon.absorbedEndCrystalCount());
        assertTrue(lane.towers().contains(enderman));
        assertEquals(5.0, dragon.roundDamageBonus(), 0.0001);
        assertEquals(0.375, dragon.permanentDamageBonus(), 0.0001);
        assertEquals(5.375, dragon.absorbedDamageBonus(), 0.0001);
        assertEquals(0.0, dragon.absorbedHealthBonus(), 0.0001);

        tick(dragon, lane, 1);

        assertEquals(1, dragon.absorbedEndCrystalCount());
        assertTrue(lane.towers().contains(enderman));
        assertEquals(0.0, enderman.health(), 0.0001);
        assertEquals(5.0, dragon.roundDamageBonus(), 0.0001);
        assertEquals(0.5, dragon.permanentDamageBonus(), 0.0001);
        assertEquals(5.5, dragon.absorbedDamageBonus(), 0.0001);
        assertEquals(0.0, dragon.absorbedHealthBonus(), 0.0001);
        assertEquals(1, dragon.roundCompletedTransferCount());
        assertEquals(20, dragon.adjustAttackInterval(20));

        tick(dragon, lane, 4);

        assertEquals(1, dragon.absorbedEndCrystalCount());
        assertTrue(lane.towers().contains(enderman));
        assertEquals(0.0, enderman.health(), 0.0001);
        assertEquals(5.5, dragon.absorbedDamageBonus(), 0.0001);
    }

    @Test
    void alreadyTransferredStatsRemainWhenAChannelStopsEarlyButTheTowerIsNotCounted() {
        applyAbsorptionDuration(4);
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_ENDER_TOWER, 0);
        EndTower endCrystalLine = tower(EndTowers.T1_ENDERMITE_TOWER, 1);
        lane.towers().add(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        lane.towers().add(endCrystalLine);

        tick(dragon, lane, 2);
        lane.towers().remove(endCrystalLine);
        tick(dragon, lane, 1);

        assertEquals(0, dragon.absorbedEndCrystalCount());
        assertEquals(5.0, dragon.roundDamageBonus(), 0.0001);
        assertEquals(0.25, dragon.permanentDamageBonus(), 0.0001);
    }

    @Test
    void healthTransferredPastTheRoundCapHealsTheEnderCore() {
        applyEnderAbilities(Map.of(
                "absorptionDurationTicks", 1.0,
                "endCrystalAttackIntervalEvery", 1.0
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_ENDER_TOWER, 0);
        EndTower shulker = tower(EndTowers.T1_SHULKER_TOWER, 1);
        lane.towers().add(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        dragon.syncHealth(10.0);
        lane.towers().add(shulker);

        dragon.tick(lane);

        assertEquals(100.0, dragon.roundHealthBonus(), 0.0001);
        assertEquals(5.0, dragon.permanentHealthBonus(), 0.0001);
        assertEquals(305.0, dragon.currentMaxHealth(), 0.0001);
        assertEquals(115.0, dragon.health(), 0.0001);
        assertEquals(0.0, shulker.health(), 0.0001);
        assertEquals(1, dragon.roundCompletedTransferCount());
        assertEquals(20, dragon.adjustAttackInterval(20));
    }

    @Test
    void coreReturnsToEggEachRoundAndPermanentHealthReturnsAfterHatching() {
        applyAbsorptionDuration(1);
        PlayerLane lane = lane();
        EndTower core = tower(EndTowers.BASE_ENDER_TOWER, 0);
        EndTower shulker = tower(EndTowers.T1_SHULKER_TOWER, 1);
        lane.towers().add(core);
        core.onWaveStarted(lane, 1);
        core.tick(lane);
        lane.towers().add(shulker);
        core.tick(lane);

        assertEquals(5.0, core.permanentHealthBonus(), 0.0001);
        assertEquals(305.0, core.currentMaxHealth(), 0.0001);

        core.resetForRound(null);

        assertEquals(EndTowerState.EGG, core.state());
        assertEquals(200.0, core.currentMaxHealth(), 0.0001);
        assertEquals(5.0, core.permanentHealthBonus(), 0.0001);

        core.onWaveStarted(null, 2);
        core.tick(null);

        assertEquals(EndTowerState.PHANTOM, core.state());
        assertEquals(205.0, core.currentMaxHealth(), 0.0001);
        assertEquals(5.0, core.permanentHealthBonus(), 0.0001);
    }

    @Test
    void completedLineCountsApplyRequestedBonusesAndStatCaps() {
        applyAbsorptionDuration(1);
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_ENDER_TOWER, 0);
        lane.towers().add(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        for (int index = 0; index < 20; index++) {
            lane.towers().add(tower(EndTowers.T1_ENDERMITE_TOWER, index + 1));
            lane.towers().add(tower(EndTowers.T1_SHULKER_TOWER, index + 21));
        }
        tick(dragon, lane, 1);

        assertEquals(20, dragon.absorbedEndCrystalCount());
        assertEquals(20, dragon.absorbedShulkerCount());
        assertEquals(40, dragon.roundCompletedTransferCount());
        assertEquals(41, lane.towers().size());
        assertEquals(40, lane.towers().stream().filter(tower -> tower != dragon && tower.health() <= 0.0).count());
        assertEquals(100.0, dragon.roundHealthBonus(), 0.0001);
        assertEquals(5.0, dragon.roundDamageBonus(), 0.0001);
        assertEquals(100.0, dragon.permanentHealthBonus(), 0.0001);
        assertEquals(10.0, dragon.permanentDamageBonus(), 0.0001);
        assertEquals(200.0, dragon.absorbedHealthBonus(), 0.0001);
        assertEquals(15.0, dragon.absorbedDamageBonus(), 0.0001);
        assertEquals(400.0, dragon.effectBaseMaxHealth(), 0.0001);
        assertEquals(12.5, dragon.modifyAttackDamage(null, null, 5.0), 0.0001);
        assertEquals(5.0, dragon.adjustAttackRange(5.0), 0.0001);
        assertEquals(0.5, dragon.splashRadius(), 0.0001);
        assertEquals(19, dragon.adjustAttackInterval(20));
        assertEquals(97.5, dragon.modifyIncomingDamage(null, null, 100.0), 0.0001);
        assertTrue(dragon.runtimeDetailLines().stream().anyMatch(line -> line.contains("누적 스택: 엔드 수정 20 / 셜커 20")));
        assertTrue(dragon.runtimeDetailLines().stream().anyMatch(line -> line.contains("생명력 흡수 2.0%")));

        dragon.resetRoundTransferBonuses(null);

        assertEquals(0.0, dragon.roundHealthBonus(), 0.0001);
        assertEquals(0.0, dragon.roundDamageBonus(), 0.0001);
        assertEquals(100.0, dragon.permanentHealthBonus(), 0.0001);
        assertEquals(10.0, dragon.permanentDamageBonus(), 0.0001);
        assertEquals(300.0, dragon.effectBaseMaxHealth(), 0.0001);
        assertEquals(10.0, dragon.modifyAttackDamage(null, null, 5.0), 0.0001);
        assertEquals(0, dragon.roundCompletedTransferCount());
        assertEquals(19, dragon.adjustAttackInterval(20));
    }

    @Test
    void cumulativeLineBonusesUseTheirRequestedFamiliesAndRespectEveryCap() {
        applyEnderAbilities(Map.of(
                "absorptionDurationTicks", 1.0,
                "endCrystalSplashEvery", 1.0,
                "splashRadiusCap", 0.5,
                "endCrystalAttackIntervalEvery", 1.0,
                "maxAttackIntervalReductionTicks", 2.0,
                "shulkerLifeStealEvery", 1.0,
                "lifeStealCap", 0.02,
                "shulkerReductionEvery", 1.0,
                "damageReductionCap", 0.05
        ));
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_ENDER_TOWER, 0);
        lane.towers().add(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        for (int index = 0; index < 3; index++) {
            lane.towers().add(tower(EndTowers.T1_ENDERMITE_TOWER, index + 1));
            lane.towers().add(tower(EndTowers.T1_SHULKER_TOWER, index + 4));
        }

        dragon.tick(lane);

        assertEquals(3, dragon.absorbedEndCrystalCount());
        assertEquals(3, dragon.absorbedShulkerCount());
        assertEquals(0.5, dragon.splashRadius(), 0.0001);
        assertEquals(18, dragon.adjustAttackInterval(20));
        assertEquals(95.0, dragon.modifyIncomingDamage(null, null, 100.0), 0.0001);
        assertTrue(dragon.runtimeDetailLines().stream().anyMatch(line -> line.contains("생명력 흡수 2.0%")));
    }

    @Test
    void completedTransfersUseRegisteredTowerTiersAsStackWeight() {
        applyAbsorptionDuration(1);
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_ENDER_TOWER, 0);
        lane.towers().add(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        lane.towers().add(tower(EndTowers.T2_ENDERMAN_TOWER, 1));
        lane.towers().add(tower(EndTowers.T3_END_CRYSTAL_TOWER, 2));
        lane.towers().add(tower(EndTowers.T2_SHULKER_TOWER, 3));
        lane.towers().add(tower(EndTowers.T3_SHULKER_TOWER, 4));

        dragon.tick(lane);

        assertEquals(5, dragon.absorbedEndCrystalCount());
        assertEquals(5, dragon.absorbedShulkerCount());
        assertEquals(4, dragon.roundCompletedTransferCount());
    }

    @Test
    void dragonEggAndHatchedPhantomAreStatesOfOneTowerType() {
        applyAbsorptionDuration(1);
        EndTower tower = tower(EndTowers.BASE_ENDER_TOWER, 0);

        assertEquals(EndTowerState.EGG, tower.state());
        assertTrue(BlockDisplayVisual.matches(tower.visual()));
        assertEquals(
                Blocks.DRAGON_EGG.defaultBlockState(),
                BlockDisplayVisual.blockState(tower.visual())
        );

        tower.onWaveStarted(null, 1);
        tower.tick(null);

        assertEquals(EndTowerState.PHANTOM, tower.state());
        assertEquals(EndTowers.BASE_ENDER_TOWER, tower.type());
        assertEquals("minecraft:phantom", tower.visual().entityTypeId());
        assertTrue(tower.visual().blockbenchModel().isEmpty());

        tower.resetForRound(null);

        assertEquals(EndTowerState.EGG, tower.state());
        assertTrue(BlockDisplayVisual.matches(tower.visual()));
        assertEquals(200.0, tower.currentMaxHealth(), 0.0001);
    }

    @Test
    void shulkerTiersReduceIncomingDamageByConfiguredAmount() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());

        assertEquals(90.0, tower(EndTowers.T1_SHULKER_TOWER, 0)
                .modifyIncomingDamage(null, null, 100.0), 0.0001);
        assertEquals(70.0, tower(EndTowers.T2_SHULKER_TOWER, 0)
                .modifyIncomingDamage(null, null, 100.0), 0.0001);
        assertEquals(50.0, tower(EndTowers.T3_SHULKER_TOWER, 0)
                .modifyIncomingDamage(null, null, 100.0), 0.0001);
    }

    private static void applyAbsorptionDuration(int durationTicks) {
        applyEnderAbilities(Map.of(
                "absorptionDurationTicks", (double) durationTicks
        ));
    }

    private static void applyEnderAbilities(Map<String, Double> overrides) {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        Map<String, Double> ender = new LinkedHashMap<>(abilities.get(EndTower.CONFIG_ID));
        ender.putAll(overrides);
        abilities.put(EndTower.CONFIG_ID, ender);
        TowerBalanceRuntime.apply(new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities));
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
}
