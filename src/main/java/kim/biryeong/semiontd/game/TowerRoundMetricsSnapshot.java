package kim.biryeong.semiontd.game;

import java.util.Objects;

/**
 * @param augmentSpecialDamageDealt separately resolved augment secondary HP damage, not total augment uplift
 */
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
        long survivalTicks,
        Double waveStartMaxHealth,
        Double enemyHpDamage,
        Double augmentSpecialDamageDealt
) {
    public TowerRoundMetricsSnapshot(
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
        this(towerTypeId, sampleCount, startCount, endAliveCount, deathCount,
                physicalDamageDealt, magicDamageDealt, damageTaken, healingDone,
                killCount, firstCombatTick, lastCombatTick, survivalTicks, null, null);
    }

    public TowerRoundMetricsSnapshot(
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
            long survivalTicks,
            Double waveStartMaxHealth,
            Double enemyHpDamage
    ) {
        this(towerTypeId, sampleCount, startCount, endAliveCount, deathCount,
                physicalDamageDealt, magicDamageDealt, damageTaken, healingDone,
                killCount, firstCombatTick, lastCombatTick, survivalTicks,
                waveStartMaxHealth, enemyHpDamage, null);
    }

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
        waveStartMaxHealth = measuredNonNegative(waveStartMaxHealth);
        enemyHpDamage = measuredNonNegative(enemyHpDamage);
        augmentSpecialDamageDealt = measuredNonNegative(augmentSpecialDamageDealt);
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
                survivalTicks + other.survivalTicks,
                mergeMeasured(waveStartMaxHealth, other.waveStartMaxHealth),
                mergeMeasured(enemyHpDamage, other.enemyHpDamage),
                mergeMeasured(augmentSpecialDamageDealt, other.augmentSpecialDamageDealt)
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

    private static Double measuredNonNegative(Double value) {
        return value != null && Double.isFinite(value) && value >= 0.0 ? value : null;
    }

    private static Double mergeMeasured(Double first, Double second) {
        return first == null || second == null ? null : first + second;
    }
}
