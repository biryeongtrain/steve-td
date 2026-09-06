package kim.biryeong.semiontd.game;

import java.util.Comparator;
import java.util.List;
import kim.biryeong.semiontd.entity.monster.MonsterSupportMetrics;
import kim.biryeong.semiontd.entity.monster.WaveHealingState;

public record PlayerRoundMetricsSnapshot(
        int round,
        int waveDurationTicks,
        int combatTicks,
        int towerCountAtStart,
        int towerCountAtEnd,
        int towerDeathCount,
        int emeraldProductionUpgradeCount,
        long emeraldPerSecond,
        long income,
        long emerald,
        long diamond,
        int towerLimitPurchaseCount,
        long monsterKills,
        List<TowerRoundMetricsSnapshot> towerMetrics,
        MonsterSupportMetrics.Snapshot utilitySupportMetrics,
        MonsterSupportMetrics.Snapshot waveSupportMetrics,
        WaveHealingState.Snapshot naturalWaveMetrics,
        String waveTemplateId,
        Integer naturalWaveCount,
        Double naturalWaveStartingHealth,
        AugmentEconomyMetricsSnapshot augmentEconomyMetrics
) {
    public PlayerRoundMetricsSnapshot(
            int round, int waveDurationTicks, int combatTicks, int towerCountAtStart, int towerCountAtEnd,
            int towerDeathCount, int emeraldProductionUpgradeCount, long emeraldPerSecond, long income,
            long emerald, long diamond, int towerLimitPurchaseCount, long monsterKills,
            List<TowerRoundMetricsSnapshot> towerMetrics
    ) {
        this(round, waveDurationTicks, combatTicks, towerCountAtStart, towerCountAtEnd, towerDeathCount,
                emeraldProductionUpgradeCount, emeraldPerSecond, income, emerald, diamond,
                towerLimitPurchaseCount, monsterKills, towerMetrics, null, null, null, null, null, null, null);
    }

    public PlayerRoundMetricsSnapshot {
        round = Math.max(1, round);
        waveDurationTicks = Math.max(0, waveDurationTicks);
        combatTicks = Math.max(0, combatTicks);
        towerCountAtStart = Math.max(0, towerCountAtStart);
        towerCountAtEnd = Math.max(0, towerCountAtEnd);
        towerDeathCount = Math.max(0, towerDeathCount);
        emeraldProductionUpgradeCount = Math.max(0, emeraldProductionUpgradeCount);
        emeraldPerSecond = Math.max(0L, emeraldPerSecond);
        income = Math.max(0L, income);
        emerald = Math.max(0L, emerald);
        diamond = Math.max(0L, diamond);
        towerLimitPurchaseCount = Math.max(0, towerLimitPurchaseCount);
        monsterKills = Math.max(0L, monsterKills);
        towerMetrics = towerMetrics == null
                ? List.of()
                : towerMetrics.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(TowerRoundMetricsSnapshot::towerTypeId))
                .toList();
        if (naturalWaveCount != null && naturalWaveCount < 0) {
            throw new IllegalArgumentException("Natural wave count cannot be negative");
        }
        if (naturalWaveStartingHealth != null
                && (!Double.isFinite(naturalWaveStartingHealth) || naturalWaveStartingHealth < 0)) {
            throw new IllegalArgumentException("Natural wave starting health must be finite and non-negative");
        }
    }
}
