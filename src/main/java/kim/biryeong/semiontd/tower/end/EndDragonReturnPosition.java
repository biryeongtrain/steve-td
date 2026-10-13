package kim.biryeong.semiontd.tower.end;

import java.util.Optional;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerPlacementPositions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class EndDragonReturnPosition {
    private EndDragonReturnPosition() { }

    static Optional<GridPosition> find(EndTower tower, PlayerLane lane, SemionTowerEntity entity) {
        var bounds = lane.laneLayout().laneArea();
        double centerX = (bounds.min().getX() + bounds.max().getX()) / 2.0;
        double centerZ = (bounds.min().getZ() + bounds.max().getZ()) / 2.0;
        GridPosition nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
            for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                double distance = (x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ);
                if (distance >= nearestDistance) continue;
                var resolved = TowerPlacementPositions.resolve(lane, new BlockPos(x, bounds.max().getY() + 1, z));
                if (resolved.isEmpty()) continue;
                BlockPos floor = resolved.get();
                if (!lane.arenaWorld().hasChunkAt(floor)
                        || lane.arenaWorld().getBlockState(floor).getCollisionShape(lane.arenaWorld(), floor).isEmpty()) continue;
                GridPosition candidate = GridPosition.from(floor);
                if (lane.towers().stream().anyMatch(other -> occupied(other, tower, candidate))
                        || lane.reinforcingTowers().stream().anyMatch(other -> occupied(other, tower, candidate))) continue;
                Vec3 destination = new Vec3(candidate.x() + .5, candidate.y() + tower.entityAnchorYOffset(), candidate.z() + .5);
                if (!lane.arenaWorld().noCollision(entity, entity.getBoundingBox().move(destination.subtract(entity.position())))) continue;
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private static boolean occupied(Tower other, EndTower source, GridPosition candidate) {
        return other != source && (sameColumn(other.position(), candidate) || sameColumn(other.managementPosition(), candidate));
    }

    private static boolean sameColumn(GridPosition left, GridPosition right) {
        return left.x() == right.x() && left.z() == right.z();
    }
}
