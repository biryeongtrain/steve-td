package kim.biryeong.semiontd.tower.gamble;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import net.minecraft.resources.ResourceLocation;

public final class GambleRoundEffects {
    private static final Map<PlayerLane, Map<UUID, Set<ResourceLocation>>> ACTIVE_SOURCES = new WeakHashMap<>();
    private static final Map<PlayerLane, Map<UUID, Map<ResourceLocation, GridPosition>>> SPECTATOR_LINKS =
            new WeakHashMap<>();
    private static final List<TimedEffectType> EFFECT_TYPES = List.of(
            TimedEffectType.TOWER_FLAT_RANGE_BONUS,
            TimedEffectType.TOWER_FLAT_RANGE_REDUCTION,
            TimedEffectType.TOWER_HEALTH_REGEN_PER_SECOND,
            TimedEffectType.TOWER_HEALTH_LOSS_PER_SECOND,
            TimedEffectType.TOWER_FLAT_DAMAGE_BONUS,
            TimedEffectType.TOWER_FLAT_DAMAGE_REDUCTION,
            TimedEffectType.TOWER_FLAT_MAX_HEALTH_BONUS,
            TimedEffectType.TOWER_FLAT_MAX_HEALTH_REDUCTION
    );

    private GambleRoundEffects() {
    }

    public static ResourceLocation sourceId(Tower tower) {
        String owner = tower.ownerPlayer().toString().replace("-", "");
        var position = tower.originalPosition();
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID,
                "gamble/support/" + owner + "/" + position.x() + "_" + position.y() + "_" + position.z());
    }

    public static void clearSource(PlayerLane lane, UUID owner, ResourceLocation sourceId) {
        if (lane == null || sourceId == null) {
            return;
        }
        for (Tower target : lane.towers()) {
            if (owner.equals(target.ownerPlayer())) {
                towerEntity(target, lane).ifPresent(entity -> clearSource(entity, sourceId));
            }
        }
        releaseSpectatorSource(lane, owner, sourceId);
        GambleRollLabels.clearSource(lane, owner, sourceId);
    }

    public static synchronized Optional<GamblerTower> assignSpectator(
            PlayerLane lane,
            UUID owner,
            ResourceLocation sourceId,
            SemionTowerEntity source,
            double range
    ) {
        if (lane == null || owner == null || sourceId == null || source == null || range < 0.0) {
            return Optional.empty();
        }
        releaseSpectatorSource(lane, owner, sourceId);
        double rangeSquared = range * range;
        Optional<GamblerTower> selected = lane.towers().stream()
                .filter(GamblerTower.class::isInstance)
                .map(GamblerTower.class::cast)
                .filter(tower -> owner.equals(tower.ownerPlayer()) && !tower.isDestroyed(lane))
                .filter(tower -> towerEntity(tower, lane)
                        .map(entity -> entity.position().distanceToSqr(source.position()) <= rangeSquared)
                        .orElse(false))
                .filter(tower -> spectatorLinkCount(lane, owner, tower.originalPosition())
                        < GambleBalance.maxSpectatorsPerGambler())
                .sorted(Comparator.comparingDouble(GamblerTower::gambleScore).reversed()
                        .thenComparingDouble(tower -> towerEntity(tower, lane)
                                .map(entity -> entity.position().distanceToSqr(source.position()))
                                .orElse(Double.MAX_VALUE))
                        .thenComparingInt(tower -> tower.originalPosition().x())
                        .thenComparingInt(tower -> tower.originalPosition().y())
                        .thenComparingInt(tower -> tower.originalPosition().z()))
                .findFirst();
        selected.ifPresent(tower -> SPECTATOR_LINKS
                .computeIfAbsent(lane, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(owner, ignored -> new LinkedHashMap<>())
                .put(sourceId, tower.originalPosition()));
        return selected;
    }

    public static synchronized int spectatorLinkCount(PlayerLane lane, UUID owner, GridPosition target) {
        if (lane == null || owner == null || target == null) {
            return 0;
        }
        return (int) SPECTATOR_LINKS.getOrDefault(lane, Map.of())
                .getOrDefault(owner, Map.of()).values().stream().filter(target::equals).count();
    }

    public static synchronized void rememberSource(
            PlayerLane lane, UUID owner, ResourceLocation sourceId
    ) {
        if (lane == null || owner == null || sourceId == null) {
            return;
        }
        ACTIVE_SOURCES.computeIfAbsent(lane, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(owner, ignored -> new LinkedHashSet<>()).add(sourceId);
    }

    public static synchronized void clearAll(PlayerLane lane, UUID owner) {
        if (lane == null || owner == null) {
            return;
        }
        LinkedHashSet<ResourceLocation> sources = new LinkedHashSet<>(ACTIVE_SOURCES
                .getOrDefault(lane, Map.of()).getOrDefault(owner, Set.of()));
        lane.towers().stream()
                .filter(tower -> owner.equals(tower.ownerPlayer()))
                .filter(tower -> tower instanceof GambleSupportTower)
                .map(GambleRoundEffects::sourceId)
                .forEach(sources::add);
        for (Tower target : lane.towers()) {
            if (!owner.equals(target.ownerPlayer())) {
                continue;
            }
            towerEntity(target, lane).ifPresent(entity -> sources.forEach(source -> clearSource(entity, source)));
        }
        Map<UUID, Set<ResourceLocation>> byOwner = ACTIVE_SOURCES.get(lane);
        if (byOwner != null) {
            byOwner.remove(owner);
            if (byOwner.isEmpty()) ACTIVE_SOURCES.remove(lane);
        }
        Map<UUID, Map<ResourceLocation, GridPosition>> spectatorOwners = SPECTATOR_LINKS.get(lane);
        if (spectatorOwners != null) {
            spectatorOwners.remove(owner);
            if (spectatorOwners.isEmpty()) SPECTATOR_LINKS.remove(lane);
        }
        GambleRollLabels.clearAll(lane, owner);
    }

    static java.util.Optional<SemionTowerEntity> towerEntity(Tower tower, PlayerLane lane) {
        if (!(tower instanceof EntityBackedTower backed)) {
            return java.util.Optional.empty();
        }
        return backed.runtimeEntity(lane);
    }

    private static void clearSource(SemionTowerEntity entity, ResourceLocation sourceId) {
        EFFECT_TYPES.forEach(type -> entity.setPersistentEffect(type, sourceId, 0.0));
    }

    private static synchronized void releaseSpectatorSource(
            PlayerLane lane, UUID owner, ResourceLocation sourceId
    ) {
        Map<UUID, Map<ResourceLocation, GridPosition>> owners = SPECTATOR_LINKS.get(lane);
        if (owners == null) {
            return;
        }
        Map<ResourceLocation, GridPosition> links = owners.get(owner);
        if (links != null) {
            links.remove(sourceId);
            if (links.isEmpty()) owners.remove(owner);
        }
        if (owners.isEmpty()) SPECTATOR_LINKS.remove(lane);
    }
}
