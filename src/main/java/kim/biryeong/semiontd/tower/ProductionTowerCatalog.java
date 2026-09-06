package kim.biryeong.semiontd.tower;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    public static synchronized Optional<CatalogEntry> find(String towerId) {
        return Optional.ofNullable(ENTRIES.get(towerId));
    }

    public static synchronized Collection<CatalogEntry> all() {
        return List.copyOf(ENTRIES.values());
    }

    public static synchronized Optional<CatalogEntry> entry(TowerType type) {
        return type == null ? Optional.empty() : find(type.id());
    }

    public static synchronized void clear() {
        ENTRIES.clear();
        UPGRADES.clear();
    }

    public static synchronized CatalogEntry registerStarter(TowerType type) {
        return register(type, ProductionTowerDefinitions.DEFAULT_TOWER_FACTORY, 1);
    }

    public static synchronized CatalogEntry registerStarter(TowerType type, TowerFactory factory) {
        return register(type, factory, 1);
    }

    public static synchronized CatalogEntry register(TowerType type) {
        return register(type, ProductionTowerDefinitions.DEFAULT_TOWER_FACTORY, 2);
    }

    public static synchronized CatalogEntry register(TowerType type, TowerFactory factory) {
        return register(type, factory, 2);
    }

    public static synchronized CatalogEntry register(TowerType type, int tier) {
        return register(type, ProductionTowerDefinitions.DEFAULT_TOWER_FACTORY, tier);
    }

    public static synchronized TowerUpgradeOption linkUpgrade(TowerType from, String id, String displayName, TowerType to, long mineralCost) {
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

    public static synchronized List<TowerUpgradeOption> upgrades(TowerType type) {
        if (type == null) {
            return List.of();
        }
        return List.copyOf(UPGRADES.getOrDefault(type.id(), List.of()));
    }

    public static synchronized boolean hasUpgrades(TowerType type) {
        return !upgrades(type).isEmpty();
    }

    public static synchronized Optional<TowerUpgradeOption> upgrade(TowerType type, String upgradeId) {
        if (type == null || upgradeId == null) {
            return Optional.empty();
        }
        return upgrades(type).stream()
                .filter(option -> option.id().equalsIgnoreCase(upgradeId))
                .findFirst();
    }

    public static synchronized Optional<TowerUpgradeOption> findUpgrade(String upgradeId) {
        if (upgradeId == null) {
            return Optional.empty();
        }
        return UPGRADES.values().stream()
                .flatMap(List::stream)
                .filter(option -> option.id().equalsIgnoreCase(upgradeId))
                .findFirst();
    }

    public static synchronized CatalogEntry register(TowerType type, TowerFactory factory, int tier) {
        return registerEntry(new CatalogEntry(type, factory, tier));
    }

    public static synchronized CatalogEntry registerAugment(TowerType type, TowerFactory factory, String augmentId) {
        return registerEntry(new CatalogEntry(type, factory, 1, Availability.AUGMENT, augmentId));
    }

    private static CatalogEntry registerEntry(CatalogEntry entry) {
        TowerType type = entry.type();
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
        Tower create(
                TowerType type,
                UUID ownerPlayer,
                TeamId teamId,
                int laneId,
                GridPosition originalPosition,
                GridPosition currentPosition
        );
    }

    public enum Availability { JOB, AUGMENT }

    public record CatalogEntry(TowerType type, TowerFactory factory, int tier, Availability availability, String augmentId) {
        public CatalogEntry(TowerType type, TowerFactory factory, int tier) {
            this(type, factory, tier, Availability.JOB, null);
        }

        public CatalogEntry {
            Objects.requireNonNull(type, "type");
            factory = factory == null ? ProductionTowerDefinitions.DEFAULT_TOWER_FACTORY : factory;
            if (tier < 1) {
                throw new IllegalArgumentException("Tower tier must be positive: " + tier);
            }
            Objects.requireNonNull(availability, "availability");
            if (availability == Availability.AUGMENT && (augmentId == null || augmentId.isBlank())) {
                throw new IllegalArgumentException("Augment towers require an augment id");
            }
            if (availability == Availability.JOB && augmentId != null) {
                throw new IllegalArgumentException("Job towers cannot require an augment");
            }
        }

        public boolean starter() {
            return tier == 1;
        }

        public Tower create(UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
            return create(ownerPlayer, teamId, laneId, position, position);
        }

        public Tower create(
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
