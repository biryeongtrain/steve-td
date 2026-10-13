package kim.biryeong.semiontd.tower.end;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import org.junit.jupiter.api.Test;

class EndTransferControllerTest extends EndTestFixture {
    @Test
    void particleScheduleUsesStableTowerIdentity() {
        UUID owner = UUID.fromString("96e1f36c-d2eb-4bf7-9929-96bc8bc02761");
        GridPosition position = new GridPosition(4, 5, 6);
        EndTower first = new EndTower(EndTowers.T2_SHULKER_TOWER, owner, TeamId.RED, 1, position);
        EndTower equivalent = new EndTower(EndTowers.T2_SHULKER_TOWER, owner, TeamId.RED, 1, position);

        long emissions = IntStream.range(0, 5)
                .filter(tick -> EndTransferController.shouldEmitParticles(first, tick))
                .count();
        assertEquals(1L, emissions);
        for (int tick = 0; tick < 10; tick++) {
            assertEquals(
                    EndTransferController.shouldEmitParticles(first, tick),
                    EndTransferController.shouldEmitParticles(equivalent, tick)
            );
        }
    }

    @Test
    void transferTickProjectsParticleSourcesWithoutCallingVfx() {
        applyTransferDuration(1);
        PlayerLane lane = lane();
        EndTower core = tower(EndTowers.BASE_END_TOWER, 0);
        EndTower source = tower(EndTowers.T1_SHULKER_TOWER, 1);
        lane.addTower(core);
        lane.addTower(source);
        EndTransferController controller = new EndTransferController(EndConfig.RUNTIME);

        EndTransferController.TickResult result = controller.tick(core, lane);

        assertTrue(result.countsChanged());
        assertEquals(1, controller.progressionSnapshot().stacks().shulkerCount());
        assertEquals(0.0, source.health(), 0.0001);
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
        double partialPermanentDamage = expectedDamageBonus(0.45);
        double partialTotalDamage = expectedDamageBonus(5.4);
        assertEquals(0, dragon.transferStats().endCrystalCount());
        assertTrue(lane.towers().contains(enderman));
        assertEquals(partialTotalDamage - partialPermanentDamage, dragon.transferStats().roundDamageBonus(), 0.0001);
        assertEquals(partialPermanentDamage, dragon.transferStats().permanentDamageBonus(), 0.0001);
        assertEquals(0.75, EndTransferController.progress(enderman), 0.0001);

        tick(dragon, lane, 1);
        double completedPermanentDamage = expectedDamageBonus(0.6);
        double completedTotalDamage = expectedDamageBonus(7.2);
        assertEquals(1, dragon.transferStats().endCrystalCount());
        assertEquals(completedTotalDamage - completedPermanentDamage, dragon.transferStats().roundDamageBonus(), 0.0001);
        assertEquals(completedPermanentDamage, dragon.transferStats().permanentDamageBonus(), 0.0001);
        assertEquals(0.0, enderman.health(), 0.0001);
        assertEquals(0.0, EndTransferController.progress(enderman), 0.0001);

        double completedDamage = dragon.transferStats().totalDamageBonus();
        dragon.tick(lane);
        assertEquals(1, dragon.transferStats().endCrystalCount());
        assertEquals(completedDamage, dragon.transferStats().totalDamageBonus(), 0.0001);
    }

    @Test
    void interruptedTransferRollsBackStatsAndDoesNotCountTower() {
        applyTransferDuration(4);
        PlayerLane lane = lane();
        EndTower dragon = tower(EndTowers.BASE_END_TOWER, 0);
        EndTower source = tower(EndTowers.T1_ENDERMITE_TOWER, 1);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.tick(lane);
        lane.addTower(source);
        tick(dragon, lane, 2);
        lane.removeTower(source);
        dragon.tick(lane);

        assertEquals(0, dragon.transferStats().endCrystalCount());
        assertEquals(0.0, dragon.transferStats().roundDamageBonus(), 0.0001);
        assertEquals(0.0, dragon.transferStats().permanentDamageBonus(), 0.0001);
        assertEquals(0.0, EndTransferController.progress(source), 0.0001);
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
        EndTower first = tower(EndTowers.T1_SHULKER_TOWER, 1);
        lane.addTower(dragon);
        dragon.onWaveStarted(lane, 1);
        dragon.syncHealth(100.0);
        lane.addTower(first);
        tick(dragon, lane, 2);
        assertEquals(200.0 + expectedHealthBonus(25.0), dragon.currentMaxHealth(), 0.0001);
        assertEquals(100.0, dragon.health(), 0.0001);
        lane.removeTower(first);
        dragon.tick(lane);
        assertEquals(200.0, dragon.currentMaxHealth(), 0.0001);
        assertEquals(100.0, dragon.health(), 0.0001);

        EndTower second = tower(EndTowers.T1_SHULKER_TOWER, 2);
        lane.addTower(second);
        tick(dragon, lane, 2);
        lane.removeTower(second);
        dragon.tick(lane);
        assertEquals(200.0, dragon.currentMaxHealth(), 0.0001);
        assertEquals(100.0, dragon.health(), 0.0001);
    }

    @Test
    void transferSnapshotsRemainUnchangedAfterLaterCompletions() {
        applyTransferDuration(1);
        PlayerLane lane = lane();
        EndTower core = tower(EndTowers.BASE_END_TOWER, 0);
        EndTransferController controller = new EndTransferController(EndConfig.RUNTIME);
        lane.addTower(core);
        lane.addTower(tower(EndTowers.T1_SHULKER_TOWER, 1));

        controller.tick(core, lane);
        EndTransferSnapshot first = controller.progressionSnapshot();
        lane.addTower(tower(EndTowers.T1_ENDERMITE_TOWER, 2));
        controller.tick(core, lane);
        EndTransferSnapshot second = controller.progressionSnapshot();

        assertEquals(1, first.stacks().shulkerCount());
        assertEquals(0, first.stacks().endCrystalCount());
        assertEquals(1, second.stacks().shulkerCount());
        assertEquals(1, second.stacks().endCrystalCount());
    }
}
