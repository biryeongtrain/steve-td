package kim.biryeong.semiontd.tower.body;

import java.util.List;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.minecraft.world.phys.Vec3;

final class BodyTowerTargetGeometry {
    private BodyTowerTargetGeometry() {
    }

    static Vec3 eyeDirection(LaneRegionLayout layout) {
        return eyeDirection(layout, false);
    }

    static Vec3 eyeDirection(LaneRegionLayout layout, boolean finalDefense) {
        List<Vec3> pathPoints = layout.pathPoints();
        int start = finalDefense ? pathPoints.size() - 2 : 0;
        int end = finalDefense ? -1 : pathPoints.size() - 1;
        int step = finalDefense ? -1 : 1;
        for (int index = start; index != end; index += step) {
            Vec3 from = pathPoints.get(index);
            Vec3 to = pathPoints.get(index + 1);
            Vec3 againstTravel = new Vec3(from.x - to.x, 0.0, from.z - to.z);
            if (againstTravel.lengthSqr() > 0.0) {
                return againstTravel.normalize();
            }
        }
        return Vec3.ZERO;
    }

    static boolean insideEyeRay(
            Vec3 origin,
            Vec3 target,
            Vec3 direction,
            double range,
            double width
    ) {
        Vec3 delta = target.subtract(origin);
        double projection = delta.x * direction.x + delta.z * direction.z;
        if (projection < 0.0 || projection > range) {
            return false;
        }
        double horizontalDistanceSqr = delta.x * delta.x + delta.z * delta.z;
        double perpendicularSqr = Math.max(0.0, horizontalDistanceSqr - projection * projection);
        return perpendicularSqr <= width * width;
    }
}
