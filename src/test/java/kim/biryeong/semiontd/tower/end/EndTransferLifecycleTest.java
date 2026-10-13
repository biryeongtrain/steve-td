package kim.biryeong.semiontd.tower.end;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import kim.biryeong.semiontd.game.PlayerLane;
import org.junit.jupiter.api.Test;

class EndTransferLifecycleTest extends EndTestFixture {
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
        double expectedMaxHealth = 200.0 + expectedHealthBonus(56.0);
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
        assertEquals(0.50, EndTransferController.progress(shulker), 0.0001);
        assertEquals(expectedHealthBonus(25.0), dragon.transferStats().roundHealthBonus(), 0.0001);
        applyEndAbilities(Map.of(
                "transferTicks", 1.0,
                "roundHealthRatio", 0.20,
                "permanentHealthRatio", 0.0
        ));
        dragon.refreshType(dragon.type(), lane);
        assertEquals(0.0, EndTransferController.progress(shulker), 0.0001);
        assertEquals(0.0, dragon.transferStats().roundHealthBonus(), 0.0001);
        dragon.tick(lane);
        assertEquals(expectedHealthBonus(20.0), dragon.transferStats().roundHealthBonus(), 0.0001);
        assertEquals(0.0, shulker.health(), 0.0001);
    }

    @Test
    void copyingTheCoreReadsCommittedStateWithoutMutatingTheSource() {
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
        assertEquals(expectedRoundHealth, original.transferStats().roundHealthBonus(), 0.0001);
        assertEquals(expectedPermanentHealth, original.transferStats().permanentHealthBonus(), 0.0001);
        EndTower replacement = tower(EndTowers.BASE_END_TOWER, 2);
        replacement.copyFrom(original, 0);
        assertEquals(expectedRoundHealth, original.transferStats().roundHealthBonus(), 0.0001);
        assertEquals(expectedPermanentHealth, original.transferStats().permanentHealthBonus(), 0.0001);
        assertEquals(0.0, replacement.transferStats().roundHealthBonus(), 0.0001);
        assertEquals(0.0, replacement.transferStats().permanentHealthBonus(), 0.0001);
        assertEquals(0.5, EndTransferController.progress(source), 0.0001);
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
        double expectedPermanentHealth = expectedHealthBonus(6.0);
        double expectedRoundTotalHealth = expectedHealthBonus(56.0);
        assertEquals(expectedPermanentHealth, core.transferStats().permanentHealthBonus(), 0.0001);
        assertEquals(200.0 + expectedRoundTotalHealth, core.currentMaxHealth(), 0.0001);
        core.resetForRound(null);
        assertEquals(EndTowerState.EGG, core.state());
        assertEquals(200.0, core.currentMaxHealth(), 0.0001);
        assertEquals(expectedPermanentHealth, core.transferStats().permanentHealthBonus(), 0.0001);
        assertEquals(0.0, core.splashRadius(), 0.0001);
        core.onWaveStarted(null, 2);
        core.tick(null);
        assertEquals(EndTowerState.PHANTOM, core.state());
        assertEquals(200.0 + expectedPermanentHealth, core.currentMaxHealth(), 0.0001);
        assertEquals(expectedPermanentHealth, core.transferStats().permanentHealthBonus(), 0.0001);
    }

    @Test
    void replacingEquivalentSourceRestartsTransferForTheNewIdentity() {
        applyTransferDuration(4);
        PlayerLane lane = lane();
        EndTower core = tower(EndTowers.BASE_END_TOWER, 0);
        EndTower original = tower(EndTowers.T1_SHULKER_TOWER, 1);
        EndTower replacement = tower(EndTowers.T1_SHULKER_TOWER, 1);
        lane.addTower(core);
        lane.addTower(original);
        core.onWaveStarted(lane, 1);
        tick(core, lane, 2);
        assertEquals(0.50, EndTransferController.progress(original), 0.0001);

        lane.replaceTower(original, replacement);
        core.tick(lane);

        assertEquals(0.0, EndTransferController.progress(original), 0.0001);
        assertEquals(0.25, EndTransferController.progress(replacement), 0.0001);
        assertEquals(expectedHealthBonus(12.5), core.transferStats().roundHealthBonus(), 0.0001);
    }

}
