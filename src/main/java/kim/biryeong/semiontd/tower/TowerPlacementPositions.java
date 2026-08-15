package kim.biryeong.semiontd.tower;

import java.util.Optional;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import xyz.nucleoid.map_templates.BlockBounds;

public final class TowerPlacementPositions {
    private static final int FLOOR_SCAN_BELOW_LANE = 4;

    private TowerPlacementPositions() {
    }

    public static Optional<BlockPos> resolve(PlayerLane lane, BlockPos sourcePos) {
        if (lane == null || sourcePos == null) {
            return Optional.empty();
        }

        BlockBounds bounds = lane.laneLayout().laneArea();
        if (!containsColumn(bounds, sourcePos)) {
            return Optional.empty();
        }

        if (bounds.contains(sourcePos)) {
            BlockPos below = sourcePos.below();
            if (!lane.arenaWorld().getBlockState(sourcePos).isAir()
                    || lane.arenaWorld().getBlockState(below).isAir()) {
                return Optional.of(sourcePos);
            }
            return Optional.of(below);
        }

        int scanStartY = Math.min(sourcePos.getY(), bounds.max().getY() + 1);
        BlockPos scanStart = new BlockPos(sourcePos.getX(), scanStartY, sourcePos.getZ());
        BlockPos floor = floorAtOrBelow(lane.arenaWorld(), scanStart, bounds.min().getY() - FLOOR_SCAN_BELOW_LANE);
        if (floor != null) {
            return Optional.of(floor);
        }

        BlockPos below = sourcePos.below();
        if (bounds.contains(below)) {
            return Optional.of(below);
        }

        int y = Math.max(bounds.min().getY(), Math.min(sourcePos.getY(), bounds.max().getY()));
        return Optional.of(new BlockPos(sourcePos.getX(), y, sourcePos.getZ()));
    }

    public static Optional<GridPosition> resolveGrid(PlayerLane lane, BlockPos sourcePos) {
        return resolve(lane, sourcePos).map(position -> {
            GridPosition resolved = GridPosition.from(position);
            if (lane.towerAt(resolved) != null) {
                return resolved;
            }
            GridPosition below = GridPosition.from(position.below());
            return lane.towerAt(below) != null ? below : resolved;
        });
    }

    private static BlockPos floorAtOrBelow(ServerLevel level, BlockPos sourcePos, int minY) {
        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos(
                sourcePos.getX(),
                sourcePos.getY(),
                sourcePos.getZ()
        );
        for (int y = sourcePos.getY(); y >= minY; y--) {
            scan.setY(y);
            if (!level.getBlockState(scan).isAir()) {
                return scan.immutable();
            }
        }
        return null;
    }

    private static boolean containsColumn(BlockBounds bounds, BlockPos blockPos) {
        return blockPos.getX() >= bounds.min().getX()
                && blockPos.getX() <= bounds.max().getX()
                && blockPos.getZ() >= bounds.min().getZ()
                && blockPos.getZ() <= bounds.max().getZ();
    }
}
