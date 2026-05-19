package kim.biryeong.semiontd.tower;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions;

public final class ProductionTowerCatalog {
    private static final Map<String, CatalogEntry> ENTRIES = new LinkedHashMap<>();
    private static final Map<String, List<TowerUpgradeOption>> UPGRADES = new LinkedHashMap<>();

    private ProductionTowerCatalog() {
    }

    public static Optional<CatalogEntry> find(String towerId) {
        return Optional.ofNullable(ENTRIES.get(towerId));
    }

    public static Collection<CatalogEntry> all() {
        return List.copyOf(ENTRIES.values());
    }

    public static Optional<CatalogEntry> entry(TowerType type) {
        return type == null ? Optional.empty() : find(type.id());
    }

    public static void clearForTesting() {
        ENTRIES.clear();
        UPGRADES.clear();
    }

    public static CatalogEntry registerStarter(TowerType type) {
        return register(type, ProductionTowerDefinitions.DEFAULT_TOWER_FACTORY, 1);
    }

    public static CatalogEntry registerStarter(TowerType type, TowerFactory factory) {
        return register(type, factory, 1);
    }

    public static CatalogEntry register(TowerType type) {
        return register(type, ProductionTowerDefinitions.DEFAULT_TOWER_FACTORY, 2);
    }

    public static CatalogEntry register(TowerType type, TowerFactory factory) {
        return register(type, factory, 2);
    }

    public static CatalogEntry register(TowerType type, int tier) {
        return register(type, ProductionTowerDefinitions.DEFAULT_TOWER_FACTORY, tier);
    }

    public static TowerUpgradeOption linkUpgrade(TowerType from, String id, String displayName, TowerType to, long mineralCost) {
        requireRegistered(from);
        requireRegistered(to);
        TowerUpgradeOption option = new TowerUpgradeOption(id, displayName, to, mineralCost);
        List<TowerUpgradeOption> options = UPGRADES.computeIfAbsent(from.id(), ignored -> new ArrayList<>());
        if (options.stream().anyMatch(existing -> existing.id().equalsIgnoreCase(option.id()))) {
            throw new IllegalArgumentException("Duplicate production tower upgrade id for " + from.id() + ": " + option.id());
        }
        options.add(option);
        return option;
    }

    public static List<TowerUpgradeOption> upgrades(TowerType type) {
        if (type == null) {
            return List.of();
        }
        return List.copyOf(UPGRADES.getOrDefault(type.id(), List.of()));
    }

    public static boolean hasUpgrades(TowerType type) {
        return !upgrades(type).isEmpty();
    }

    public static Optional<TowerUpgradeOption> upgrade(TowerType type, String upgradeId) {
        if (type == null || upgradeId == null) {
            return Optional.empty();
        }
        return upgrades(type).stream()
                .filter(option -> option.id().equalsIgnoreCase(upgradeId))
                .findFirst();
    }

    public static CatalogEntry register(TowerType type, TowerFactory factory, int tier) {
        CatalogEntry entry = new CatalogEntry(type, factory, tier);
        CatalogEntry previous = ENTRIES.putIfAbsent(type.id(), entry);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate production tower id: " + type.id());
        }
        UPGRADES.putIfAbsent(type.id(), new ArrayList<>());
        return entry;
    }

    private static void requireRegistered(TowerType type) {
        if (type == null || !ENTRIES.containsKey(type.id())) {
            throw new IllegalArgumentException("Production tower must be registered before linking upgrades: " + (type == null ? "null" : type.id()));
        }
    }

    @FunctionalInterface
    public interface TowerFactory {
        EntityBackedTower create(
                TowerType type,
                UUID ownerPlayer,
                TeamId teamId,
                int laneId,
                GridPosition originalPosition,
                GridPosition currentPosition
        );
    }

    public record CatalogEntry(TowerType type, TowerFactory factory, int tier) {
        public CatalogEntry {
            factory = factory == null ? ProductionTowerDefinitions.DEFAULT_TOWER_FACTORY : factory;
        }

        public boolean starter() {
            return tier == 1;
        }

        public EntityBackedTower create(UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
            return create(ownerPlayer, teamId, laneId, position, position);
        }

        public EntityBackedTower create(
                UUID ownerPlayer,
                TeamId teamId,
                int laneId,
                GridPosition originalPosition,
                GridPosition currentPosition
        ) {
            return factory.create(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
        }
    }
}
