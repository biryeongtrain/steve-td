package kim.biryeong.semiontd.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kim.biryeong.semiontd.config.MapConfig;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.SemionTeam;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.map_templates.TemplateRegion;

public record ArenaLayout(
        Vec3 teamSpawn,
        Vec3 bossSpawn,
        Map<Integer, LaneRegionLayout> lanes
) {
    public ArenaLayout {
        lanes = Map.copyOf(lanes);
    }

    public static ArenaLayout fromTemplate(MapTemplate template, BlockPos origin, MapConfig.RegionMarkers markers)
            throws ArenaLoadException {
        Vec3 teamSpawn = requiredPoint(template, origin, markers.teamSpawn());
        Vec3 bossSpawn = requiredPoint(template, origin, markers.bossSpawn());
        Map<Integer, Vec3> laneSpawns = readLanePoints(template, origin, markers.laneSpawn());
        Map<Integer, BlockBounds> laneAreas = readLaneBounds(template, origin, markers.lanePath());
        Map<Integer, List<OrderedPoint>> laneWaypoints = readLaneWaypoints(template, origin, markers.laneWaypoint());
        Map<Integer, List<GridPosition>> finalDefenseTowerSlots = readFinalDefenseTowerSlots(
                template,
                origin,
                markers.finalDefenseTower(),
                bossSpawn
        );

        Map<Integer, LaneRegionLayout> lanes = new HashMap<>();
        for (int laneId = 1; laneId <= SemionTeam.MAX_PLAYERS; laneId++) {
            Vec3 laneSpawn = requiredLanePoint(laneSpawns, laneId, markers.laneSpawn());
            BlockBounds laneArea = requiredLaneBounds(laneAreas, laneId, markers.lanePath());
            List<Vec3> waypoints = laneWaypoints.getOrDefault(laneId, List.of()).stream()
                    .sorted(Comparator.comparingInt(OrderedPoint::order))
                    .map(OrderedPoint::point)
                    .toList();
            List<GridPosition> slots = requiredLaneSlots(finalDefenseTowerSlots, laneId, markers.finalDefenseTower());
            lanes.put(laneId, new LaneRegionLayout(laneId, laneSpawn, waypoints, bossSpawn, laneArea, slots));
        }

        return new ArenaLayout(teamSpawn, bossSpawn, lanes);
    }

    public Optional<LaneRegionLayout> lane(int laneId) {
        return Optional.ofNullable(lanes.get(laneId));
    }

    private static Vec3 requiredPoint(MapTemplate template, BlockPos origin, String marker) throws ArenaLoadException {
        TemplateRegion region = template.getMetadata().getFirstRegion(marker);
        if (region == null) {
            throw new ArenaLoadException("Missing map region " + marker + ".");
        }
        return centerBottom(region, origin);
    }

    private static Map<Integer, Vec3> readLanePoints(MapTemplate template, BlockPos origin, String marker) {
        Map<Integer, Vec3> points = new HashMap<>();
        template.getMetadata().getRegions(marker).forEach(region -> readLane(dataOrEmpty(region))
                .ifPresent(laneId -> points.putIfAbsent(laneId, centerBottom(region, origin))));
        return points;
    }

    private static Map<Integer, BlockBounds> readLaneBounds(MapTemplate template, BlockPos origin, String marker) {
        Map<Integer, BlockBounds> bounds = new HashMap<>();
        template.getMetadata().getRegions(marker).forEach(region -> readLane(dataOrEmpty(region))
                .ifPresent(laneId -> bounds.putIfAbsent(laneId, region.getBounds().offset(origin))));
        return bounds;
    }

    private static Map<Integer, List<OrderedPoint>> readLaneWaypoints(
            MapTemplate template,
            BlockPos origin,
            String marker
    ) {
        Map<Integer, List<OrderedPoint>> waypoints = new HashMap<>();
        template.getMetadata().getRegions(marker).forEach(region -> readLane(dataOrEmpty(region)).ifPresent(laneId -> {
            int order = dataOrEmpty(region).getIntOr("order", waypoints.getOrDefault(laneId, List.of()).size());
            waypoints.computeIfAbsent(laneId, ignored -> new ArrayList<>())
                    .add(new OrderedPoint(order, centerBottom(region, origin)));
        }));
        return waypoints;
    }

    private static Map<Integer, List<GridPosition>> readFinalDefenseTowerSlots(
            MapTemplate template,
            BlockPos origin,
            String marker,
            Vec3 bossSpawn
    ) {
        Map<Integer, List<GridPosition>> slots = new HashMap<>();
        template.getMetadata().getRegions(marker).forEach(region -> readLane(dataOrEmpty(region)).ifPresent(laneId -> {
            BlockBounds bounds = region.getBounds().offset(origin);
            int floorY = bounds.min().getY();
            List<GridPosition> regionSlots = new ArrayList<>();
            for (BlockPos blockPos : bounds) {
                if (blockPos.getY() != floorY) {
                    continue;
                }
                regionSlots.add(GridPosition.from(blockPos));
            }
            regionSlots.sort(finalDefenseSlotComparator(bossSpawn));
            slots.put(laneId, List.copyOf(regionSlots));
        }));
        return slots;
    }

    private static Optional<Integer> readLane(CompoundTag data) {
        int laneId = data.getIntOr("lane", 0);
        if (laneId < 1 || laneId > SemionTeam.MAX_PLAYERS) {
            return Optional.empty();
        }
        return Optional.of(laneId);
    }

    private static Vec3 centerBottom(TemplateRegion region, BlockPos origin) {
        return region.getBounds().offset(origin).centerBottom();
    }

    private static Vec3 requiredLanePoint(Map<Integer, Vec3> points, int laneId, String marker)
            throws ArenaLoadException {
        Vec3 point = points.get(laneId);
        if (point == null) {
            throw new ArenaLoadException("Missing map region " + marker + " for lane " + laneId + ".");
        }
        return point;
    }

    private static BlockBounds requiredLaneBounds(Map<Integer, BlockBounds> bounds, int laneId, String marker)
            throws ArenaLoadException {
        BlockBounds value = bounds.get(laneId);
        if (value == null) {
            throw new ArenaLoadException("Missing map region " + marker + " for lane " + laneId + ".");
        }
        return value;
    }

    private static List<GridPosition> requiredLaneSlots(Map<Integer, List<GridPosition>> slots, int laneId, String marker)
            throws ArenaLoadException {
        List<GridPosition> value = slots.get(laneId);
        if (value == null || value.isEmpty()) {
            throw new ArenaLoadException("Missing map region " + marker + " for lane " + laneId + ".");
        }
        return value;
    }

    private static CompoundTag dataOrEmpty(TemplateRegion region) {
        CompoundTag data = region.getData();
        return data == null ? new CompoundTag() : data;
    }

    private static Comparator<GridPosition> finalDefenseSlotComparator(Vec3 bossSpawn) {
        return Comparator
                .comparingDouble((GridPosition position) -> bossSpawn.distanceTo(
                        new Vec3(position.x() + 0.5, position.y(), position.z() + 0.5)
                ))
                .thenComparingInt(GridPosition::x)
                .thenComparingInt(GridPosition::z);
    }

    private record OrderedPoint(int order, Vec3 point) {
    }
}
