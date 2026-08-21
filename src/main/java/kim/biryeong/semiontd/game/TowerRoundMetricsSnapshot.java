package kim.biryeong.semiontd.game;

import java.util.Objects;

public record TowerRoundMetricsSnapshot(
        String towerTypeId,
        int sampleCount,
        int startCount,
        int endAliveCount,
        int deathCount,
        double physicalDamageDealt,
        double magicDamageDealt,
        double damageTaken,
        double healingDone,
        long killCount,
        int firstCombatTick,
        int lastCombatTick,
        long survivalTicks
) {
    public TowerRoundMetricsSnapshot {
        Objects.requireNonNull(towerTypeId, "towerTypeId");
        sampleCount = Math.max(0, sampleCount);
        startCount = Math.max(0, startCount);
        endAliveCount = Math.max(0, endAliveCount);
        deathCount = Math.max(0, deathCount);
        physicalDamageDealt = finiteNonNegative(physicalDamageDealt);
        magicDamageDealt = finiteNonNegative(magicDamageDealt);
        damageTaken = finiteNonNegative(damageTaken);
        healingDone = finiteNonNegative(healingDone);
        killCount = Math.max(0L, killCount);
        if (firstCombatTick < 0 || lastCombatTick < firstCombatTick) {
            firstCombatTick = -1;
            lastCombatTick = -1;
        }
        survivalTicks = Math.max(0L, survivalTicks);
    }

    public double damageDealt() {
        return physicalDamageDealt + magicDamageDealt;
    }

    public int combatTicks() {
        return firstCombatTick < 0 ? 0 : lastCombatTick - firstCombatTick + 1;
    }

    public double dps() {
        int ticks = combatTicks();
        return ticks == 0 ? 0.0 : damageDealt() * 20.0 / ticks;
    }

    public double averageSurvivalSeconds() {
        return sampleCount == 0 ? 0.0 : survivalTicks / (sampleCount * 20.0);
    }

    public TowerRoundMetricsSnapshot merge(TowerRoundMetricsSnapshot other) {
        if (!towerTypeId.equals(other.towerTypeId)) {
            throw new IllegalArgumentException("Cannot merge different tower types");
        }
        return new TowerRoundMetricsSnapshot(
                towerTypeId,
                sampleCount + other.sampleCount,
                startCount + other.startCount,
                endAliveCount + other.endAliveCount,
                deathCount + other.deathCount,
                physicalDamageDealt + other.physicalDamageDealt,
                magicDamageDealt + other.magicDamageDealt,
                damageTaken + other.damageTaken,
                healingDone + other.healingDone,
                killCount + other.killCount,
                firstCombatTick(firstCombatTick, other.firstCombatTick),
                Math.max(lastCombatTick, other.lastCombatTick),
                survivalTicks + other.survivalTicks
        );
    }

    private static int firstCombatTick(int first, int second) {
        if (first < 0) {
            return second;
        }
        if (second < 0) {
            return first;
        }
        return Math.min(first, second);
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }
}
