package kim.biryeong.semiontd.tower.legion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Queue;
import java.util.TreeMap;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;
import net.minecraft.world.phys.Vec3;

public final class IllusionCloneSpawnQueue {
    private static final NavigableMap<Integer, Queue<PendingCloneSpawn>> PENDING_CLONE_SPAWNS = new TreeMap<>();
    private static int currentTick;

    public static void enqueue(
            IllusionSummonerTower owner,
            PlayerLane lane,
            Tower sourceTower,
            IllusionProfile profile,
            List<Vec3> offsets
    ) {
        if (owner == null || lane == null || sourceTower == null || profile.cloneCount() <= 0
                || sourceTower.health() <= 0.0 || offsets == null || offsets.isEmpty()) {
            return;
        }
        int spreadTicks = TowerBalanceRuntime.illusionCloneSpawnSpreadTicks();
        for (int index = 0; index < profile.cloneCount(); index++) {
            Vec3 offset = offsets.get(index % offsets.size());
            int delayTicks = (int) Math.floor(index * (double) spreadTicks / profile.cloneCount());
            int dueTick = currentTick + delayTicks + (delayTicks > 0 ? 1 : 0);
            PENDING_CLONE_SPAWNS
                    .computeIfAbsent(dueTick, ignored -> new ArrayDeque<>())
                    .add(new PendingCloneSpawn(owner, lane, sourceTower, profile, offset));
        }
    }

    public static void tick() {
        if (PENDING_CLONE_SPAWNS.isEmpty()) {
            return;
        }

        int maxSpawnsPerTick = TowerBalanceRuntime.illusionCloneMaxSpawnsPerTick();
        int spawnedThisTick = 0;
        while (spawnedThisTick < maxSpawnsPerTick && !PENDING_CLONE_SPAWNS.isEmpty()) {
            Map.Entry<Integer, Queue<PendingCloneSpawn>> entry = PENDING_CLONE_SPAWNS.firstEntry();
            if (entry.getKey() > currentTick) {
                break;
            }

            Queue<PendingCloneSpawn> readySpawns = entry.getValue();
            PendingCloneSpawn pending = readySpawns.poll();
            if (readySpawns.isEmpty()) {
                PENDING_CLONE_SPAWNS.pollFirstEntry();
            }
            if (!pending.isValid()) {
                continue;
            }
            pending.owner().spawnQueuedClone(pending.lane(), pending.sourceTower(), pending.profile(), pending.offset());
            spawnedThisTick++;
        }
        currentTick++;
    }

    public static void cancel(IllusionSummonerTower owner) {
        if (owner == null || PENDING_CLONE_SPAWNS.isEmpty()) {
            return;
        }
        Iterator<Queue<PendingCloneSpawn>> iterator = PENDING_CLONE_SPAWNS.values().iterator();
        while (iterator.hasNext()) {
            Queue<PendingCloneSpawn> pendingSpawns = iterator.next();
            pendingSpawns.removeIf(pending -> pending.owner() == owner);
            if (pendingSpawns.isEmpty()) {
                iterator.remove();
            }
        }
    }

    public static void clear() {
        PENDING_CLONE_SPAWNS.clear();
        currentTick = 0;
    }

    private IllusionCloneSpawnQueue() throws IllegalAccessException {
        throw new IllegalAccessException("Utility Class");
    }

    private static final class PendingCloneSpawn {
        private final IllusionSummonerTower owner;
        private final PlayerLane lane;
        private final Tower sourceTower;
        private final IllusionProfile profile;
        private final Vec3 offset;

        private PendingCloneSpawn(
                IllusionSummonerTower owner,
                PlayerLane lane,
                Tower sourceTower,
                IllusionProfile profile,
                Vec3 offset
        ) {
            this.owner = owner;
            this.lane = lane;
            this.sourceTower = sourceTower;
            this.profile = profile;
            this.offset = offset;
        }

        private IllusionSummonerTower owner() {
            return owner;
        }

        private PlayerLane lane() {
            return lane;
        }

        private Tower sourceTower() {
            return sourceTower;
        }

        private IllusionProfile profile() {
            return profile;
        }

        private Vec3 offset() {
            return offset;
        }

        private boolean isValid() {
            return lane.towers().contains(owner)
                    && sourceTower.health() > 0.0
                    && (sourceTower == owner || lane.towers().contains(sourceTower));
        }
    }
}
