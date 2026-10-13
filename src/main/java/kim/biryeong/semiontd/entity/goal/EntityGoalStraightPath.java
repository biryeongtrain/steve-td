package kim.biryeong.semiontd.entity.goal;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

public final class EntityGoalStraightPath {
    private Path path;
    private BlockPos target;

    public boolean moveTo(Mob mob, double x, double y, double z, double speed) {
        PathNavigation navigation = mob.getNavigation();
        if (!(navigation instanceof GroundPathNavigation)
                || !(mob.onGround() || mob.isInLiquid() || mob.isPassenger())) {
            path = null;
            return navigation.moveTo(x, y, z, speed);
        }
        BlockPos requested = BlockPos.containing(x, y, z);
        if (path == null || path != navigation.getPath() || path.isDone() || !requested.equals(target)) {
            target = requested;
            path = straight(mob, requested);
            if (path == null) {
                boolean moving = navigation.moveTo(x, y, z, speed);
                path = navigation.getPath();
                return moving;
            }
        }
        return navigation.moveTo(path, speed);
    }

    static Path straight(Mob mob, BlockPos requested) {
        if (!mob.onGround()) {
            return null;
        }
        PathfindingContext context = new PathfindingContext(mob.level(), mob);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int startX = Mth.floor(mob.getX());
        int startZ = Mth.floor(mob.getZ());
        int y = Mth.floor(mob.getY() + 0.5);
        if (!walkable(context, cursor, mob, startX, y, startZ)) {
            return null;
        }
        int deltaX = requested.getX() - startX;
        int deltaZ = requested.getZ() - startZ;
        int steps = Math.max(Math.abs(deltaX), Math.abs(deltaZ));
        float range = (float) mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        List<Node> nodes = new ArrayList<>();
        nodes.add(node(startX, y, startZ));
        boolean reached = Math.abs(deltaX) + Math.abs(deltaZ) <= 1;
        float walked = 0.0F;
        int x = startX;
        int z = startZ;
        for (int step = 1; step <= steps && !reached; step++) {
            int nextX = startX + Math.round((float) step * deltaX / steps);
            int nextZ = startZ + Math.round((float) step * deltaZ / steps);
            boolean diagonal = nextX != x && nextZ != z;
            walked += diagonal ? Mth.SQRT_OF_TWO : 1.0F;
            if (walked >= range) {
                break;
            }
            if (!walkable(context, cursor, mob, nextX, y, nextZ) || diagonal
                    && !(walkable(context, cursor, mob, nextX, y, z) && walkable(context, cursor, mob, x, y, nextZ))) {
                return null;
            }
            nodes.add(node(nextX, y, nextZ));
            x = nextX;
            z = nextZ;
            reached = Math.abs(requested.getX() - x) + Math.abs(requested.getZ() - z) <= 1;
        }
        if (reached && !walkable(context, cursor, mob, requested.getX(), y, requested.getZ())) {
            return null;
        }
        return new Path(nodes, new BlockPos(requested.getX(), y, requested.getZ()), reached);
    }

    private static boolean walkable(PathfindingContext context, BlockPos.MutableBlockPos cursor, Mob mob,
                                    int x, int y, int z) {
        int width = Mth.floor(mob.getBbWidth() + 1.0F);
        int height = Mth.floor(mob.getBbHeight() + 1.0F);
        boolean ground = false;
        for (int offsetX = 0; offsetX < width; offsetX++) {
            for (int offsetY = 0; offsetY < height; offsetY++) {
                for (int offsetZ = 0; offsetZ < width; offsetZ++) {
                    PathType type = WalkNodeEvaluator.getPathTypeStatic(context,
                            cursor.set(x + offsetX, y + offsetY, z + offsetZ));
                    if (type == PathType.WALKABLE) {
                        ground = true;
                    } else if (type != PathType.OPEN) {
                        return false;
                    }
                }
            }
        }
        return ground;
    }

    private static Node node(int x, int y, int z) {
        Node node = new Node(x, y, z);
        node.type = PathType.WALKABLE;
        return node;
    }
}
