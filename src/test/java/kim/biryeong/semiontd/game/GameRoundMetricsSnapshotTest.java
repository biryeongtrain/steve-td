package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.entity.monster.MonsterSupportMetrics;
import kim.biryeong.semiontd.tower.TowerRoundMetricsTracker;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class GameRoundMetricsSnapshotTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void snapshotKeepsBuilderCountsSeparateFromAllTowerCombatDuration() throws ReflectiveOperationException {
        SemionTeam team = new SemionTeam(TeamId.BLUE);
        PlayerLane lane = GameRoundSupportMetricsTest.lane(team, 1);
        SemionPlayer player = new SemionPlayer(new UUID(0, 1), "metrics", TeamId.BLUE, 1,
                new PlayerEconomy(EconomyConfig.defaultConfig()));
        TowerRoundMetricsTracker living = new TowerRoundMetricsTracker("test:living", true);
        living.setCurrentTick(10);
        living.recordDamageTaken(5);
        TowerRoundMetricsTracker dead = new TowerRoundMetricsTracker("test:dead", true);
        dead.setCurrentTick(20);
        dead.recordHealingDone(5);
        dead.updateAlive(false);
        TowerRoundMetricsTracker demon = new TowerRoundMetricsTracker("semion-td:demon_lord", true);
        demon.setCurrentTick(2);
        demon.recordDamageTaken(5);
        demon.setCurrentTick(30);
        demon.recordDamageTaken(5);
        var field = PlayerLane.class.getDeclaredField("roundTowerTrackers");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<TowerRoundMetricsTracker> trackers = (Set<TowerRoundMetricsTracker>) field.get(lane);
        trackers.addAll(List.of(living, dead, demon));
        var support = new MonsterSupportMetrics.Snapshot(1, 10, 7, 3, 0, 0, 0, 0, 0, 0);

        var result = GameRoundMetricsSnapshot.capture(player, lane, 4, 120, 0, false, support);

        assertEquals(4, result.round());
        assertEquals(120, result.waveDurationTicks());
        assertEquals(29, result.combatTicks());
        assertEquals(2, result.towerCountAtStart());
        assertEquals(1, result.towerCountAtEnd());
        assertEquals(1, result.towerDeathCount());
        assertEquals(3, result.towerMetrics().size());
        assertEquals(support, result.utilitySupportMetrics());
        assertEquals(player.economy().emerald(), result.emerald());
        assertEquals(player.economy().diamond(), result.diamond());
        assertEquals(player.economy().income(), result.income());
        assertNull(result.augmentEconomyMetrics());
        assertNull(result.naturalWaveCount());
        assertNull(result.naturalWaveStartingHealth());
    }

    @Test
    void emptyRoundKeepsUnobservedFieldsAndAugmentTelemetryShape() {
        SemionTeam team = new SemionTeam(TeamId.BLUE);
        PlayerLane lane = GameRoundSupportMetricsTest.lane(team, 1);
        SemionPlayer player = new SemionPlayer(new UUID(0, 1), "empty", TeamId.BLUE, 1,
                new PlayerEconomy(EconomyConfig.defaultConfig()));
        var result = GameRoundMetricsSnapshot.capture(player, lane, 1, 0, 0, true,
                MonsterSupportMetrics.Snapshot.empty());
        assertEquals(0, result.combatTicks());
        assertEquals(0, result.towerCountAtStart());
        assertEquals(List.of(), result.towerMetrics());
        assertNotNull(result.augmentEconomyMetrics());
        assertNull(result.waveTemplateId());
        assertNull(result.naturalWaveCount());
    }
}
