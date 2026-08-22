package kim.biryeong.semiontd.tower;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;
import kim.biryeong.semiontd.entity.healing.HealingTarget;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerRoundMetricsSnapshot;
import org.junit.jupiter.api.Test;

class TowerDamagePipelineTest {
    @Test
    void towerFinalDamageIsIncludedInResolvedDamageCap() {
        double baseDamage = 250.0;
        double damageWithPrimaryDoubleEdgedSword = baseDamage * 1.25;
        double damageAfterFinalBonus = Tower.applyOutgoingDamageStages(damageWithPrimaryDoubleEdgedSword, damage -> damage, damage -> damage * 1.10);
        double outgoingDamage = Math.min(250.0, damageAfterFinalBonus);
        assertEquals(250.0, outgoingDamage, 0.0001);
    }

    @Test
    void roundDamageStatsResetAtWaveStartAndSurviveUpgrade() {
        TowerType tierOneType = type("round_stats_t1", "Round Stats T1");
        TowerType tierTwoType = type("round_stats_t2", "Round Stats T2");
        ProductionTower tierOne = tower(tierOneType);
        tierOne.markWaveStarted(3);
        tierOne.syncHealth(80.0);
        assertEquals(0.0, tierOne.roundDamageTaken(), 0.0001);
        tierOne.recordDamageDealt(125.5);
        tierOne.recordDamageDealt(12.5, DamageType.MAGIC);
        tierOne.recordDamageTaken(40.25);
        tierOne.recordDamageDealt(Double.NaN);
        tierOne.recordDamageTaken(-5.0);

        ProductionTower tierTwo = tower(tierTwoType);
        tierTwo.copyFrom(tierOne, 50);

        assertEquals(138.0, tierTwo.roundDamageDealt(), 0.0001);
        assertEquals(125.5, tierTwo.roundPhysicalDamageDealt(), 0.0001);
        assertEquals(12.5, tierTwo.roundMagicDamageDealt(), 0.0001);
        assertEquals(40.25, tierTwo.roundDamageTaken(), 0.0001);
        assertSame(tierOneType, tierTwo.roundCombatType());
        assertSame(tierOne.roundMetricsTracker(), tierTwo.roundMetricsTracker());

        tierTwo.markWaveStarted(4);

        assertEquals(0.0, tierTwo.roundDamageDealt(), 0.0001);
        assertEquals(0.0, tierTwo.roundPhysicalDamageDealt(), 0.0001);
        assertEquals(0.0, tierTwo.roundMagicDamageDealt(), 0.0001);
        assertEquals(0.0, tierTwo.roundDamageTaken(), 0.0001);
        assertSame(tierTwoType, tierTwo.roundCombatType());
    }

    @Test
    void roundMetricsUseActualCombatWindowHealingAndSurvival() {
        ProductionTower tower = tower(type("round_metrics", "Round Metrics"));
        tower.markWaveStarted(2);
        TowerRoundMetricsTracker tracker = tower.roundMetricsTracker();
        tracker.setCurrentTick(5);
        tower.recordDamageDealt(100.0, DamageType.PHYSICAL);
        tracker.setCurrentTick(25);
        tower.recordDamageDealt(50.0, DamageType.MAGIC);
        tower.recordDamageTaken(30.0);
        TestHealingTarget target = new TestHealingTarget(90.0, 100.0);
        tower.healTarget(target, 50.0);
        for (int tick = 0; tick < 10; tick++) {
            tracker.recordSurvivalTick(true);
        }
        tracker.recordSurvivalTick(false);
        for (int tick = 0; tick < 5; tick++) {
            tracker.recordSurvivalTick(true);
        }
        tracker.recordSurvivalTick(false);

        TowerRoundMetricsSnapshot metrics = tracker.snapshot();
        assertEquals(100.0, metrics.physicalDamageDealt(), 0.0001);
        assertEquals(50.0, metrics.magicDamageDealt(), 0.0001);
        assertEquals(30.0, metrics.damageTaken(), 0.0001);
        assertEquals(10.0, metrics.healingDone(), 0.0001);
        assertEquals(21, metrics.combatTicks());
        assertEquals(150.0 * 20.0 / 21.0, metrics.dps(), 0.0001);
        assertEquals(2, metrics.deathCount());
        assertEquals(15L, metrics.survivalTicks());
        assertEquals(0.75, metrics.averageSurvivalSeconds(), 0.0001);
    }

    @Test
    void roundMetricsRemainZeroWithoutCombatEvents() {
        TowerRoundMetricsTracker tracker = new TowerRoundMetricsTracker("semion-td:idle", false);
        tracker.recordSurvivalTick(true);

        TowerRoundMetricsSnapshot metrics = tracker.snapshot();
        assertEquals(0, metrics.startCount());
        assertEquals(1, metrics.sampleCount());
        assertEquals(0, metrics.combatTicks());
        assertEquals(0.0, metrics.dps(), 0.0001);
    }

    private static ProductionTower tower(TowerType type) {
        return new ProductionTower(
                type,
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                TeamId.RED,
                1,
                new GridPosition(0, 0, 0)
        );
    }

    private static TowerType type(String id, String name) {
        return new TowerType(id, name, TowerCategory.DIRECT, 10, 100.0, 5.0, 10.0, 20, 0);
    }

    private static final class TestHealingTarget implements HealingTarget {
        private double health;
        private final double maxHealth;

        private TestHealingTarget(double health, double maxHealth) {
            this.health = health;
            this.maxHealth = maxHealth;
        }

        @Override
        public boolean isHealingAlly(HealingTarget other) {
            return true;
        }

        @Override
        public boolean canReceiveHealing() {
            return health < maxHealth;
        }

        @Override
        public double missingHealingHealth() {
            return maxHealth - health;
        }

        @Override
        public boolean receiveHealing(double amount) {
            if (amount <= 0.0 || !canReceiveHealing()) {
                return false;
            }
            health = Math.min(maxHealth, health + amount);
            return true;
        }
    }
}
