package kim.biryeong.semionTd.tower;

import java.util.List;

public record TowerType(
        String id,
        String displayName,
        TowerCategory category,
        long mineralCost,
        double maxHealth,
        double range,
        double damage,
        int attackIntervalTicks,
        int aggroPriority,
        List<TowerUpgradeOption> upgradeOptions
) {
    public TowerType(
            String id,
            String displayName,
            TowerCategory category,
            long mineralCost,
            double maxHealth,
            double range,
            double damage,
            int attackIntervalTicks,
            int aggroPriority
    ) {
        this(id, displayName, category, mineralCost, maxHealth, range, damage, attackIntervalTicks, aggroPriority, List.of());
    }

    public TowerType {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Tower id cannot be blank.");
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = id;
        }
        if (category == null) {
            category = TowerCategory.DIRECT;
        }
        if (mineralCost < 0 || maxHealth <= 0 || range < 0 || damage < 0 || attackIntervalTicks < 1) {
            throw new IllegalArgumentException("Tower numeric values are invalid.");
        }
        upgradeOptions = List.copyOf(upgradeOptions);
    }

    public boolean hasUpgradeOptions() {
        return !upgradeOptions.isEmpty();
    }
}
