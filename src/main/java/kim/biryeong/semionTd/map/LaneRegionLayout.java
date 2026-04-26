package kim.biryeong.semionTd.map;

import java.util.ArrayList;
import java.util.List;
import kim.biryeong.semionTd.game.GridPosition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public record LaneRegionLayout(
        int laneId,
        Vec3 spawn,
        List<Vec3> waypoints,
        Vec3 bossPosition,
        BlockBounds laneArea,
        List<GridPosition> finalDefenseTowerSlots
) {
    public LaneRegionLayout {
        waypoints = List.copyOf(waypoints);
        finalDefenseTowerSlots = List.copyOf(finalDefenseTowerSlots);
    }

    public List<Vec3> pathPoints() {
        List<Vec3> points = new ArrayList<>(waypoints.size() + 2);
        points.add(spawn);
        points.addAll(waypoints);
        points.add(bossPosition);
        return points;
    }

    public AABB defenseSearchBox(Vec3 currentPosition, double horizontalPadding, double verticalPadding) {
        BlockBounds bounds = laneArea;
        AABB searchBox = new AABB(
                bounds.min().getX(),
                bounds.min().getY(),
                bounds.min().getZ(),
                bounds.max().getX() + 1.0,
                bounds.max().getY() + 1.0,
                bounds.max().getZ() + 1.0
        );
        for (Vec3 point : pathPoints()) {
            searchBox = includePoint(searchBox, point);
        }
        searchBox = includePoint(searchBox, currentPosition);
        return searchBox.inflate(horizontalPadding, verticalPadding, horizontalPadding);
    }

    public Vec3 positionAt(double progress) {
        List<Vec3> points = pathPoints();

        double clamped = Math.max(0.0, Math.min(1.0, progress));
        double totalDistance = totalDistance(points);
        if (totalDistance <= 0.0) {
            return bossPosition;
        }

        double targetDistance = totalDistance * clamped;
        double walked = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 from = points.get(i);
            Vec3 to = points.get(i + 1);
            double segmentDistance = from.distanceTo(to);
            if (segmentDistance <= 0.0) {
                continue;
            }

            if (walked + segmentDistance >= targetDistance) {
                double segmentProgress = (targetDistance - walked) / segmentDistance;
                return from.lerp(to, segmentProgress);
            }
            walked += segmentDistance;
        }
        return bossPosition;
    }

    public double progressAt(Vec3 position) {
        List<Vec3> points = pathPoints();
        double totalDistance = totalDistance(points);
        if (totalDistance <= 0.0) {
            return 1.0;
        }

        double bestWalked = 0.0;
        double walked = 0.0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 from = points.get(i);
            Vec3 to = points.get(i + 1);
            Vec3 segment = to.subtract(from);
            double segmentLength = segment.length();
            if (segmentLength <= 0.0) {
                continue;
            }

            Vec3 relative = position.subtract(from);
            double projection = Math.max(0.0, Math.min(1.0, relative.dot(segment) / (segmentLength * segmentLength)));
            Vec3 closestPoint = from.add(segment.scale(projection));
            double distance = closestPoint.distanceTo(position);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestWalked = walked + (segmentLength * projection);
            }
            walked += segmentLength;
        }
        return Math.max(0.0, Math.min(1.0, bestWalked / totalDistance));
    }

    private static double totalDistance(List<Vec3> points) {
        double distance = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            distance += points.get(i).distanceTo(points.get(i + 1));
        }
        return distance;
    }

    private static AABB includePoint(AABB box, Vec3 point) {
        return new AABB(
                Math.min(box.minX, point.x),
                Math.min(box.minY, point.y),
                Math.min(box.minZ, point.z),
                Math.max(box.maxX, point.x),
                Math.max(box.maxY, point.y),
                Math.max(box.maxZ, point.z)
        );
    }
}
