package kim.biryeong.semiontd.game;

import java.util.Comparator;
import java.util.List;

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
        List<TowerRoundMetricsSnapshot> towerMetrics
) {
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
    }
}
