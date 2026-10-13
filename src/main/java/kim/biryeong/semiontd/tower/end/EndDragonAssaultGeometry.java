package kim.biryeong.semiontd.tower.end;

import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

record EndDragonAssaultGeometry(Vec3 rear, Vec3 front, Vec3 direction, double length, double width,
                                BlockBounds lane, BlockBounds spawn) {
    static EndDragonAssaultGeometry from(LaneRegionLayout layout, double floorY) {
        BlockBounds lane = layout.laneArea();
        BlockBounds spawn = layout.spawnArea();
        double minX = Math.min(lane.min().getX(), spawn.min().getX());
        double maxX = Math.max(lane.max().getX(), spawn.max().getX()) + 1.0;
        double minZ = Math.min(lane.min().getZ(), spawn.min().getZ());
        double maxZ = Math.max(lane.max().getZ(), spawn.max().getZ()) + 1.0;
        Vec3 back = layout.personalWaypoints().isEmpty() ? layout.bossPosition() : layout.personalWaypoints().getLast();
        Vec3 towardSpawn = layout.spawn().subtract(back);
        boolean alongX = Math.abs(towardSpawn.x) >= Math.abs(towardSpawn.z);
        double sign = (alongX ? towardSpawn.x : towardSpawn.z) >= 0 ? 1 : -1;
        Vec3 direction = alongX ? new Vec3(sign, 0, 0) : new Vec3(0, 0, sign);
        Vec3 rear = alongX
                ? new Vec3(sign > 0 ? minX : maxX, floorY + 1, (minZ + maxZ) / 2)
                : new Vec3((minX + maxX) / 2, floorY + 1, sign > 0 ? minZ : maxZ);
        double length = alongX ? maxX - minX : maxZ - minZ;
        return new EndDragonAssaultGeometry(rear, rear.add(direction.scale(length)), direction, length,
                alongX ? maxZ - minZ : maxX - minX, lane, spawn);
    }

    Vec3 point(double distance) {return rear.add(direction.scale(distance));}

    Vec3 airborneRear(double floorY, double height) {
        Vec3 point = point(-5);
        return new Vec3(point.x, floorY + height, point.z);
    }

    boolean swept(Vec3 target, double from, double to) {
        double distance = target.subtract(rear).dot(direction);
        return distance >= Math.min(from, to) - 1.0e-6 && distance <= Math.max(from, to) + 1.0e-6
                && (contains(lane, target) || contains(spawn, target));
    }

    private static boolean contains(BlockBounds bounds, Vec3 point) {
        return point.x >= bounds.min().getX() && point.x < bounds.max().getX() + 1.0
                && point.z >= bounds.min().getZ() && point.z < bounds.max().getZ() + 1.0;
    }
}
