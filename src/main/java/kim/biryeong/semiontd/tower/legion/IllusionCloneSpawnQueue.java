package kim.biryeong.semiontd.tower.legion;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;
import net.minecraft.world.phys.Vec3;

public final class IllusionCloneSpawnQueue {
    private static final List<PendingCloneSpawn> PENDING_CLONE_SPAWNS = new ArrayList<>();

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
            PENDING_CLONE_SPAWNS.add(new PendingCloneSpawn(owner, lane, sourceTower, profile, offset, delayTicks));
        }
    }

    public static void tick() {
        if (PENDING_CLONE_SPAWNS.isEmpty()) {
            return;
        }

        int maxSpawnsPerTick = TowerBalanceRuntime.illusionCloneMaxSpawnsPerTick();
        int spawnedThisTick = 0;
        Iterator<PendingCloneSpawn> iterator = PENDING_CLONE_SPAWNS.iterator();
        while (iterator.hasNext()) {
            PendingCloneSpawn pending = iterator.next();
            if (!pending.isValid()) {
                iterator.remove();
                continue;
            }
            if (pending.delayTicks() <= 0 && spawnedThisTick < maxSpawnsPerTick) {
                pending.owner().spawnQueuedClone(pending.lane(), pending.sourceTower(), pending.profile(), pending.offset());
                spawnedThisTick++;
                iterator.remove();
                continue;
            }
            if (pending.delayTicks() > 0) {
                pending.decrementDelay();
            }
        }
    }

    public static void cancel(IllusionSummonerTower owner) {
        if (owner == null || PENDING_CLONE_SPAWNS.isEmpty()) {
            return;
        }
        PENDING_CLONE_SPAWNS.removeIf(pending -> pending.owner() == owner);
    }

    public static void clear() {
        PENDING_CLONE_SPAWNS.clear();
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
        private int delayTicks;

        private PendingCloneSpawn(
                IllusionSummonerTower owner,
                PlayerLane lane,
                Tower sourceTower,
                IllusionProfile profile,
                Vec3 offset,
                int delayTicks
        ) {
            this.owner = owner;
            this.lane = lane;
            this.sourceTower = sourceTower;
            this.profile = profile;
            this.offset = offset;
            this.delayTicks = delayTicks;
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

        private int delayTicks() {
            return delayTicks;
        }

        private void decrementDelay() {
            delayTicks--;
        }

        private boolean isValid() {
            return lane.towers().contains(owner)
                    && sourceTower.health() > 0.0
                    && (sourceTower == owner || lane.towers().contains(sourceTower));
        }
    }
}
