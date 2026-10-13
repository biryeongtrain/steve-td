package kim.biryeong.semiontd.tower.demonlord;

import java.util.List;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

final class DemonLordLaneGeometry {
    private DemonLordLaneGeometry() {}

    static Vec3 laneCentre(LaneRegionLayout layout) {
        List<Vec3> inside = layout.pathPoints().stream()
                .filter(point -> containsHorizontally(layout.laneArea(), point))
                .toList();
        if (inside.size() == 1) {
            return inside.getFirst();
        }
        if (inside.size() >= 2) {
            return midpointAlong(inside);
        }
        BlockBounds area = layout.laneArea();
        return new Vec3(
                (area.min().getX() + area.max().getX() + 1.0) / 2.0,
                layout.spawn().y,
                (area.min().getZ() + area.max().getZ() + 1.0) / 2.0
        );
    }

    static Vec3 midpointAlong(List<Vec3> points) {
        double total = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            total += points.get(i).distanceTo(points.get(i + 1));
        }
        if (total <= 0.0) {
            return points.getFirst();
        }
        double target = total / 2.0;
        double walked = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 from = points.get(i);
            Vec3 to = points.get(i + 1);
            double segment = from.distanceTo(to);
            if (segment <= 0.0) {
                continue;
            }
            if (walked + segment >= target) {
                return from.lerp(to, (target - walked) / segment);
            }
            walked += segment;
        }
        return points.getLast();
    }

    static boolean containsHorizontally(BlockBounds area, Vec3 point) {
        return point.x >= area.min().getX()
                && point.x < area.max().getX() + 1.0
                && point.z >= area.min().getZ()
                && point.z < area.max().getZ() + 1.0;
    }
}
