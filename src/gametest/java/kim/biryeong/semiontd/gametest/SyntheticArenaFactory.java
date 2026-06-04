package kim.biryeong.semiontd.gametest;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.SemionTeam;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.ArenaLayout;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.map.TeamArena;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

final class SyntheticArenaFactory {
    private SyntheticArenaFactory() {
    }

    static GameArena create(ServerLevel level, BlockPos origin) {
        Map<TeamId, TeamArena> arenas = new EnumMap<>(TeamId.class);
        for (TeamId teamId : TeamId.values()) {
            arenas.put(teamId, new TeamArena(teamId, () -> {
            }, level, layoutFor(teamId, origin)));
        }
        return new GameArena(arenas);
    }

    private static ArenaLayout layoutFor(TeamId teamId, BlockPos origin) {
        int baseX = origin.getX() + teamId.ordinal() * 12;
        int baseY = origin.getY();
        int baseZ = origin.getZ();
        Vec3 teamSpawn = new Vec3(baseX + 2.5, baseY + 2.0, baseZ + 1.5);
        Vec3 bossSpawn = new Vec3(baseX + 2.5, baseY + 2.0, baseZ + 9.5);
        Map<Integer, LaneRegionLayout> lanes = new HashMap<>();

        for (int laneId = 1; laneId <= SemionTeam.MAX_PLAYERS; laneId++) {
            int laneOffset = laneId - 3;
            Vec3 spawn = new Vec3(baseX + 2.5 + laneOffset, baseY + 2.0, baseZ + 3.5);
            List<Vec3> waypoints = new ArrayList<>();
            waypoints.add(new Vec3(baseX + 2.5 + laneOffset, baseY + 2.0, baseZ + 5.5));
            waypoints.add(new Vec3(baseX + 2.5, baseY + 2.0, baseZ + 7.5));
            BlockBounds laneArea = BlockBounds.of(
                    new BlockPos(baseX + laneOffset, baseY + 1, baseZ + 3),
                    new BlockPos(baseX + 4 + laneOffset, baseY + 4, baseZ + 8)
            );
            BlockBounds spawnArea = BlockBounds.of(
                    new BlockPos(baseX + 1 + laneOffset, baseY + 2, baseZ + 3),
                    new BlockPos(baseX + 3 + laneOffset, baseY + 2, baseZ + 3)
            );
            List<GridPosition> finalDefenseSlots = finalDefenseSlots(baseX, baseY, baseZ, laneId, bossSpawn);
            lanes.put(laneId, new LaneRegionLayout(laneId, spawn, spawnArea, waypoints, bossSpawn, laneArea, finalDefenseSlots));
        }

        return new ArenaLayout(teamSpawn, bossSpawn, lanes);
    }

    private static List<GridPosition> finalDefenseSlots(int baseX, int baseY, int baseZ, int laneId, Vec3 bossSpawn) {
        int minX = baseX - 3;
        int maxX = baseX + 3;
        int minZ = baseZ + 9 + ((laneId - 3) * 2);
        int maxZ = minZ + 6;
        List<GridPosition> slots = new ArrayList<>(49);
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                slots.add(new GridPosition(x, baseY + 1, z));
            }
        }
        slots.sort(Comparator
                .comparingDouble((GridPosition position) -> bossSpawn.distanceTo(
                        new Vec3(position.x() + 0.5, position.y(), position.z() + 0.5)
                ))
                .thenComparingInt(GridPosition::x)
                .thenComparingInt(GridPosition::z));
        return List.copyOf(slots);
    }
}
