package kim.biryeong.semiontd.game;

import java.util.List;
import kim.biryeong.semiontd.entity.monster.MonsterSupportMetrics;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;

final class GameRoundMetricsSnapshot {
    private static final String DEMON_LORD_METRICS_ID = "semion-td:demon_lord";

    private GameRoundMetricsSnapshot() {
    }

    static PlayerRoundMetricsSnapshot capture(SemionPlayer player, PlayerLane lane, int round,
            int waveDurationTicks, long killBaseline, boolean augmentsEnabled,
            MonsterSupportMetrics.Snapshot utilitySupport) {
        List<TowerRoundMetricsSnapshot> towerMetrics = lane.roundTowerMetrics();
        List<TowerRoundMetricsSnapshot> builderTowerMetrics = towerMetrics.stream()
                .filter(metrics -> !DEMON_LORD_METRICS_ID.equals(metrics.towerTypeId()))
                .filter(metrics -> ProductionTowerCatalog.find(metrics.towerTypeId())
                        .map(entry -> entry.availability() == ProductionTowerCatalog.Availability.JOB).orElse(true))
                .toList();
        int firstCombatTick = towerMetrics.stream()
                .mapToInt(TowerRoundMetricsSnapshot::firstCombatTick)
                .filter(tick -> tick >= 0)
                .min()
                .orElse(-1);
        int lastCombatTick = towerMetrics.stream()
                .mapToInt(TowerRoundMetricsSnapshot::lastCombatTick)
                .max()
                .orElse(-1);
        PlayerEconomy economy = player.economy();
        return new PlayerRoundMetricsSnapshot(
                round,
                waveDurationTicks,
                firstCombatTick < 0 ? 0 : lastCombatTick - firstCombatTick + 1,
                builderTowerMetrics.stream().mapToInt(TowerRoundMetricsSnapshot::startCount).sum(),
                builderTowerMetrics.stream().mapToInt(TowerRoundMetricsSnapshot::endAliveCount).sum(),
                builderTowerMetrics.stream().mapToInt(TowerRoundMetricsSnapshot::deathCount).sum(),
                economy.emeraldProductionUpgradeCount(),
                economy.emeraldPerSec(),
                economy.income(),
                economy.emerald(),
                economy.diamond(),
                economy.towerLimitPurchaseCount(),
                player.matchStats().monsterKills() - killBaseline,
                towerMetrics,
                utilitySupport,
                lane.naturalWaveSupportMetrics(),
                lane.waveSupportMetrics(),
                lane.waveTemplateId(),
                lane.naturalWaveCount(),
                lane.naturalWaveStartingHealth(),
                augmentsEnabled ? new AugmentEconomyMetricsSnapshot(
                        player.economyAugments().diamondGranted(), player.economyAugments().incomeGranted(),
                        player.economyAugments().incomeForgone(), player.economyAugments().payoutWithheld()) : null
        );
    }
}
