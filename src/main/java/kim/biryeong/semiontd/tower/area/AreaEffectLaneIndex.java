package kim.biryeong.semiontd.tower.area;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;

public final class AreaEffectLaneIndex {
    private static final Set<PlayerLane> LANES = ConcurrentHashMap.newKeySet();

    private AreaEffectLaneIndex() {
    }

    public static void register(PlayerLane lane) {
        if (lane != null) {
            LANES.add(lane);
        }
    }

    public static void unregister(PlayerLane lane) {
        if (lane != null) {
            LANES.remove(lane);
        }
    }

    static Optional<PlayerLane> find(SemionTowerEntity source) {
        if (source == null || source.ownerPlayer() == null || source.runtimeTower() == null) {
            return Optional.empty();
        }
        Optional<PlayerLane> exact = LANES.stream()
                .filter(lane -> matches(source, lane))
                .filter(lane -> lane.towers().contains(source.runtimeTower()))
                .findFirst();
        return exact.isPresent() ? exact : LANES.stream().filter(lane -> matches(source, lane)).findFirst();
    }

    private static boolean matches(SemionTowerEntity source, PlayerLane lane) {
        return lane.arenaWorld() == source.level()
                && lane.ownerPlayer().equals(source.ownerPlayer())
                && lane.teamId() == source.teamId()
                && lane.laneId() == source.laneId();
    }
}
