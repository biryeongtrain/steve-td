package kim.biryeong.semiontd.tower;

import java.util.List;
import java.util.Optional;
import kim.biryeong.semiontd.entity.model.SemionBilModelCache;

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
        String entityTypeId,
        String blockbenchModelId,
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

    public TowerType(
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
        this(
                id,
                displayName,
                category,
                mineralCost,
                maxHealth,
                range,
                damage,
                attackIntervalTicks,
                aggroPriority,
                "minecraft:villager",
                null,
                upgradeOptions
        );
    }

    public TowerType(
            String id,
            String displayName,
            TowerCategory category,
            long mineralCost,
            double maxHealth,
            double range,
            double damage,
            int attackIntervalTicks,
            int aggroPriority,
            String entityTypeId
    ) {
        this(
                id,
                displayName,
                category,
                mineralCost,
                maxHealth,
                range,
                damage,
                attackIntervalTicks,
                aggroPriority,
                entityTypeId,
                null,
                List.of()
        );
    }

    public TowerType(
            String id,
            String displayName,
            TowerCategory category,
            long mineralCost,
            double maxHealth,
            double range,
            double damage,
            int attackIntervalTicks,
            int aggroPriority,
            String entityTypeId,
            List<TowerUpgradeOption> upgradeOptions
    ) {
        this(
                id,
                displayName,
                category,
                mineralCost,
                maxHealth,
                range,
                damage,
                attackIntervalTicks,
                aggroPriority,
                entityTypeId,
                null,
                upgradeOptions
        );
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
        entityTypeId = SemionBilModelCache.normalize(entityTypeId);
        blockbenchModelId = SemionBilModelCache.normalize(blockbenchModelId);
        if (entityTypeId == null && blockbenchModelId == null) {
            entityTypeId = "minecraft:villager";
        }
        upgradeOptions = List.copyOf(upgradeOptions);
    }

    public boolean hasUpgradeOptions() {
        return !upgradeOptions.isEmpty();
    }

    public Optional<String> blockbenchModel() {
        return Optional.ofNullable(blockbenchModelId);
    }
}
