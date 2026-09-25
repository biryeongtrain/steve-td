package kim.biryeong.semiontd.tower;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.game.TowerRoundMetricsSnapshot;
import org.junit.jupiter.api.Test;

class TowerRoundMetricsTrackerTest {
    @Test
    void waveStartHealthIsCapturedOnceAndEnemyDamageIsSeparateFromTotalDamage() {
        TowerRoundMetricsTracker tracker = new TowerRoundMetricsTracker("test_tower", true);
        assertNull(tracker.snapshot().waveStartMaxHealth());
        assertEquals(0.0, tracker.snapshot().enemyHpDamage());

        tracker.captureWaveStartMaxHealth(Double.NaN);
        tracker.captureWaveStartMaxHealth(-1.0);
        tracker.captureWaveStartMaxHealth(0.0);
        tracker.captureWaveStartMaxHealth(Double.POSITIVE_INFINITY);
        assertNull(tracker.snapshot().waveStartMaxHealth());
        tracker.captureWaveStartMaxHealth(150.0);
        tracker.captureWaveStartMaxHealth(200.0);
        tracker.setCurrentTick(12);
        tracker.recordDamageTaken(60.0);
        tracker.recordEnemyHealthDamage(25.0);
        tracker.recordEnemyHealthDamage(5.0);
        tracker.setCurrentTick(40);
        tracker.recordEnemyHealthDamage(Double.NaN);
        tracker.recordEnemyHealthDamage(Double.POSITIVE_INFINITY);
        tracker.recordEnemyHealthDamage(-2.0);
        tracker.recordEnemyHealthDamage(0.0);

        TowerRoundMetricsSnapshot snapshot = tracker.snapshot();
        assertEquals(150.0, snapshot.waveStartMaxHealth());
        assertEquals(30.0, snapshot.enemyHpDamage());
        assertEquals(60.0, snapshot.damageTaken());
        assertEquals(12, snapshot.lastCombatTick());
    }

    @Test
    void midWaveTowersDoNotIncreaseTheStartingHealthDenominator() {
        TowerRoundMetricsTracker start = new TowerRoundMetricsTracker("test_tower", true);
        start.captureWaveStartMaxHealth(150.0);
        start.recordEnemyHealthDamage(20.0);
        TowerRoundMetricsTracker added = new TowerRoundMetricsTracker("test_tower", false);
        added.captureWaveStartMaxHealth(300.0);
        added.recordEnemyHealthDamage(40.0);

        TowerRoundMetricsSnapshot merged = start.snapshot().merge(added.snapshot());
        assertEquals(2, merged.sampleCount());
        assertEquals(1, merged.startCount());
        assertEquals(150.0, merged.waveStartMaxHealth());
        assertEquals(60.0, merged.enemyHpDamage());
    }

    @Test
    void historicalOrInvalidMeasurementsStayUnknownWhenMerged() {
        TowerRoundMetricsSnapshot historical = new TowerRoundMetricsSnapshot(
                "test_tower", 1, 1, 1, 0, 10.0, 0.0, 5.0, 0.0, 0, 1, 2, 20);
        assertNull(historical.waveStartMaxHealth());
        assertNull(historical.enemyHpDamage());
        assertNull(historical.augmentSpecialDamageDealt());

        TowerRoundMetricsTracker measured = new TowerRoundMetricsTracker("test_tower", true);
        measured.captureWaveStartMaxHealth(100.0);
        measured.recordEnemyHealthDamage(50.0);
        TowerRoundMetricsSnapshot merged = measured.snapshot().merge(historical);
        assertNull(merged.waveStartMaxHealth());
        assertNull(merged.enemyHpDamage());
        assertNull(merged.augmentSpecialDamageDealt());
        assertEquals(5.0, merged.damageTaken());
        assertEquals(10.0, merged.damageDealt());

        TowerRoundMetricsSnapshot invalid = new TowerRoundMetricsSnapshot(
                "test_tower", 1, 1, 1, 0, 0.0, 0.0, 0.0, 0.0, 0, -1, -1, 0,
                Double.POSITIVE_INFINITY, -1.0);
        assertNull(invalid.waveStartMaxHealth());
        assertNull(invalid.enemyHpDamage());
        assertNull(invalid.augmentSpecialDamageDealt());
    }

    @Test
    void augmentSpecialDamageMeasuresActualHealthDamageWithoutDoubleCountingTotalDamage() {
        TowerRoundMetricsTracker tracker = new TowerRoundMetricsTracker("test_tower", true);
        assertEquals(0.0, tracker.snapshot().augmentSpecialDamageDealt());
        tracker.setCurrentTick(4);
        tracker.recordDamageDealt(80.0, DamageType.MAGIC);
        tracker.recordAugmentSpecialDamage(20.0);
        tracker.setCurrentTick(8);
        tracker.recordAugmentSpecialDamage(5.0);
        tracker.setCurrentTick(16);
        tracker.recordAugmentSpecialDamage(0.0);
        tracker.recordAugmentSpecialDamage(-1.0);
        tracker.recordAugmentSpecialDamage(Double.NaN);
        tracker.recordAugmentSpecialDamage(Double.POSITIVE_INFINITY);
        TowerRoundMetricsSnapshot snapshot = tracker.snapshot();
        assertEquals(25.0, snapshot.augmentSpecialDamageDealt());
        assertEquals(80.0, snapshot.damageDealt());
        assertEquals(8, snapshot.lastCombatTick());

        TowerRoundMetricsTracker other = new TowerRoundMetricsTracker("test_tower", true);
        other.recordAugmentSpecialDamage(10.0);
        assertEquals(35.0, snapshot.merge(other.snapshot()).augmentSpecialDamageDealt());

        TowerRoundMetricsSnapshot invalid = new TowerRoundMetricsSnapshot(
                "test_tower", 1, 1, 1, 0, 0.0, 0.0, 0.0, 0.0, 0, -1, -1, 0,
                100.0, 0.0, Double.POSITIVE_INFINITY);
        assertNull(invalid.augmentSpecialDamageDealt());
    }
}
