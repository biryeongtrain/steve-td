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

    /** 몬스터가 쳐들어간 레인(목표 팀·레인이 같고 같은 월드). 유닛 능력이 그 레인의 타워·몬스터를 다룰 때 씁니다. */
    public static Optional<PlayerLane> findForMonster(kim.biryeong.semiontd.entity.monster.SemionMonsterEntity entity) {
        if (entity == null || entity.runtimeMonster() == null) {
            return Optional.empty();
        }
        var monster = entity.runtimeMonster();
        // 지난 게임의 레인도 목록에 남아 있을 수 있으므로, 이 몬스터를 실제로 들고 있는 레인을 먼저 고릅니다.
        Optional<PlayerLane> holding = LANES.stream()
                .filter(lane -> lane.arenaWorld() == entity.level() && lane.activeMonsters().contains(monster))
                .findFirst();
        return holding.isPresent() ? holding : LANES.stream()
                .filter(lane -> lane.arenaWorld() == entity.level()
                        && lane.teamId() == monster.targetTeam()
                        && lane.laneId() == monster.targetLaneId())
                .findFirst();
    }

    private static boolean matches(SemionTowerEntity source, PlayerLane lane) {
        return lane.arenaWorld() == source.level()
                && lane.ownerPlayer().equals(source.ownerPlayer())
                && lane.teamId() == source.teamId()
                && lane.laneId() == source.runtimeTower().laneId();
    }
}
