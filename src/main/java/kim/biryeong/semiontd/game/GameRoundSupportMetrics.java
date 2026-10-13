package kim.biryeong.semiontd.game;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.MonsterSupportMetrics;

final class GameRoundSupportMetrics {
    private GameRoundSupportMetrics() {
    }

    static Map<UUID, MonsterSupportMetrics.Snapshot> capture(Collection<SemionTeam> teams, Set<UUID> purchasers) {
        Map<UUID, MonsterSupportMetrics.Snapshot> totals = new LinkedHashMap<>();
        if (purchasers.isEmpty()) {
            return totals;
        }
        for (SemionTeam team : teams) {
            for (PlayerLane lane : team.laneGroup().lanes()) {
                lane.utilitySupportMetricsByPurchaser(purchasers).forEach((purchaser, laneTotal) -> {
                    MonsterSupportMetrics.Snapshot total = totals.computeIfAbsent(
                            purchaser, ignored -> MonsterSupportMetrics.Snapshot.empty());
                    totals.put(purchaser, total.plus(laneTotal));
                });
            }
        }
        return totals;
    }
}
