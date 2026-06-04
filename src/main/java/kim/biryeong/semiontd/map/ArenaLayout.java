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
    private static final String FINAL_DEFENSE_LANE_MARKER = "final_defense_lane";
    private static final String LEGACY_FINAL_DEFENSE_TOWER_MARKER = "final_defense_tower";

    public ArenaLayout {
        lanes = Map.copyOf(lanes);
    }

    public static ArenaLayout fromTemplate(MapTemplate template, MapConfig.RegionMarkers markers)
            throws ArenaLoadException {
        Vec3 teamSpawn = requiredPoint(template, markers.teamSpawn());
        Vec3 bossSpawn = requiredPoint(template, markers.bossSpawn());
        Map<Integer, Vec3> laneSpawns = readLanePoints(template, markers.laneSpawn());
        Map<Integer, BlockBounds> laneSpawnAreas = readLaneBounds(template, markers.laneSpawn());
        Map<Integer, BlockBounds> laneAreas = readLaneBounds(template, markers.lanePath());
        Map<Integer, List<OrderedPoint>> laneWaypoints = readLaneWaypoints(template, markers.laneWaypoint());
        List<Vec3> finalWaypoints = readOrderedPoints(template, markers.finalWaypoint());
        FinalDefenseTowerSlots finalDefenseTowerSlots = readFinalDefenseTowerSlots(
                template,
                markers.finalDefenseTower(),
                bossSpawn
        );

        Map<Integer, LaneRegionLayout> lanes = new HashMap<>();
        for (int laneId = 1; laneId <= SemionTeam.MAX_PLAYERS; laneId++) {
            Vec3 laneSpawn = requiredLanePoint(laneSpawns, laneId, markers.laneSpawn());
            BlockBounds laneSpawnArea = requiredLaneBounds(laneSpawnAreas, laneId, markers.laneSpawn());
            BlockBounds laneArea = requiredLaneBounds(laneAreas, laneId, markers.lanePath());
            List<Vec3> waypoints = laneWaypoints.getOrDefault(laneId, List.of()).stream()
                    .sorted(Comparator.comparingInt(OrderedPoint::order))
                    .map(OrderedPoint::point)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            waypoints.addAll(finalWaypoints);
            List<GridPosition> slots = requiredFinalDefenseSlots(finalDefenseTowerSlots, laneId, markers.finalDefenseTower());
            lanes.put(laneId, new LaneRegionLayout(laneId, laneSpawn, laneSpawnArea, waypoints, bossSpawn, laneArea, slots));
        }

        return new ArenaLayout(teamSpawn, bossSpawn, lanes);
    }

    public Optional<LaneRegionLayout> lane(int laneId) {
        return Optional.ofNullable(lanes.get(laneId));
    }

    private static Vec3 requiredPoint(MapTemplate template, String marker) throws ArenaLoadException {
        TemplateRegion region = template.getMetadata().getFirstRegion(marker);
        if (region == null) {
            throw new ArenaLoadException("Missing map region " + marker + ".");
        }
        return center(region);
    }

    private static Map<Integer, Vec3> readLanePoints(MapTemplate template, String marker) {
        Map<Integer, Vec3> points = new HashMap<>();
        template.getMetadata().getRegions(marker).forEach(region -> readLane(dataOrEmpty(region))
                .ifPresent(laneId -> points.putIfAbsent(laneId, center(region))));
        return points;
    }

    private static Map<Integer, BlockBounds> readLaneBounds(MapTemplate template, String marker) {
        Map<Integer, BlockBounds> bounds = new HashMap<>();
        template.getMetadata().getRegions(marker).forEach(region -> readLane(dataOrEmpty(region))
                .ifPresent(laneId -> bounds.putIfAbsent(laneId, region.getBounds())));
        return bounds;
    }

    private static Map<Integer, List<OrderedPoint>> readLaneWaypoints(
            MapTemplate template,
            String marker
    ) {
        Map<Integer, List<OrderedPoint>> waypoints = new HashMap<>();
        template.getMetadata().getRegions(marker).forEach(region -> readLane(dataOrEmpty(region)).ifPresent(laneId -> {
            int order = dataOrEmpty(region).getIntOr("order", waypoints.getOrDefault(laneId, List.of()).size());
            waypoints.computeIfAbsent(laneId, ignored -> new ArrayList<>())
                    .add(new OrderedPoint(order, center(region)));
        }));
        return waypoints;
    }

    private static List<Vec3> readOrderedPoints(MapTemplate template, String marker) {
        List<OrderedPoint> points = new ArrayList<>();
        template.getMetadata().getRegions(marker).forEach(region -> {
            int order = dataOrEmpty(region).getIntOr("order", points.size());
            points.add(new OrderedPoint(order, center(region)));
        });
        return points.stream()
                .sorted(Comparator.comparingInt(OrderedPoint::order))
                .map(OrderedPoint::point)
                .toList();
    }

    private static FinalDefenseTowerSlots readFinalDefenseTowerSlots(
            MapTemplate template,
            String marker,
            Vec3 bossSpawn
    ) {
        List<GridPosition> sharedSlots = new ArrayList<>();
        Map<Integer, List<GridPosition>> laneSlots = new HashMap<>();
        List<TemplateRegion> regions = finalDefenseRegions(template, marker);
        regions.forEach(region -> readLane(dataOrEmpty(region)).ifPresent(laneId -> {
            List<GridPosition> regionSlots = finalDefenseSlots(region.getBounds(), bossSpawn);
            laneSlots.put(laneId, regionSlots);
        }));
        regions.stream().filter(region -> readLane(dataOrEmpty(region)).isEmpty())
                .forEach(region -> sharedSlots.addAll(finalDefenseSlots(region.getBounds(), bossSpawn)));
        sharedSlots.sort(finalDefenseSlotComparator(bossSpawn));
        return new FinalDefenseTowerSlots(List.copyOf(sharedSlots), laneSlots);
    }

    private static List<TemplateRegion> finalDefenseRegions(MapTemplate template, String marker) {
        List<TemplateRegion> regions = template.getMetadata().getRegions(marker).toList();
        if (!regions.isEmpty() || !FINAL_DEFENSE_LANE_MARKER.equals(marker)) {
            return regions;
        }
        return template.getMetadata().getRegions(LEGACY_FINAL_DEFENSE_TOWER_MARKER).toList();
    }

    private static List<GridPosition> finalDefenseSlots(BlockBounds bounds, Vec3 bossSpawn) {
        int floorY = bounds.min().getY();
        List<GridPosition> slots = new ArrayList<>();
        for (BlockPos blockPos : bounds) {
            if (blockPos.getY() != floorY) {
                continue;
            }
            slots.add(GridPosition.from(blockPos));
        }
        slots.sort(finalDefenseSlotComparator(bossSpawn));
        return List.copyOf(slots);
    }

    private static Optional<Integer> readLane(CompoundTag data) {
        int laneId = data.getIntOr("lane", 0);
        if (laneId < 1 || laneId > SemionTeam.MAX_PLAYERS) {
            return Optional.empty();
        }
        return Optional.of(laneId);
    }

    private static Vec3 center(TemplateRegion region) {
        return region.getBounds().centerTop();
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

    private static List<GridPosition> requiredFinalDefenseSlots(
            FinalDefenseTowerSlots slots,
            int laneId,
            String marker
    )
            throws ArenaLoadException {
        List<GridPosition> value = slots.forLane(laneId);
        if (value == null || value.isEmpty()) {
            throw new ArenaLoadException("Missing map region " + marker + ".");
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

    private record FinalDefenseTowerSlots(
            List<GridPosition> shared,
            Map<Integer, List<GridPosition>> byLane
    ) {
        private FinalDefenseTowerSlots {
            shared = List.copyOf(shared);
            byLane = Map.copyOf(byLane);
        }

        private List<GridPosition> forLane(int laneId) {
            return byLane.getOrDefault(laneId, shared);
        }
    }
}
