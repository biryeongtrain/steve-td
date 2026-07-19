package kim.biryeong.semiontd.statistics;

import java.util.Objects;
import java.util.OptionalDouble;

public record TraitTowerStatisticsEntry(
        String towerTypeId,
        int tier,
        long participantAppearances,
        long totalCount
) {
    public TraitTowerStatisticsEntry {
        Objects.requireNonNull(towerTypeId, "towerTypeId");
        tier = Math.max(0, tier);
        participantAppearances = Math.max(0L, participantAppearances);
        totalCount = Math.max(0L, totalCount);
    }

    public OptionalDouble averageCount(long combinationAppearances) {
        return combinationAppearances <= 0L
                ? OptionalDouble.empty()
                : OptionalDouble.of((double) totalCount / combinationAppearances);
    }
}
