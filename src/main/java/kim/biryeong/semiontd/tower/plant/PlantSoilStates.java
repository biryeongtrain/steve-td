package kim.biryeong.semiontd.tower.plant;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import xyz.nucleoid.map_templates.BlockBounds;

/**
 * Tracks the terraformed lane tiles owned by each plant builder.
 *
 * <p>Soil is stored per player and keyed by lane column, so a lookup only needs the x/z of a tower
 * or monster. A column belongs to exactly one {@link PlantSoil}; a family never overwrites another
 * family's tile, which is what makes lane space the builder's real resource.
 */
public final class PlantSoilStates {
    private static final Map<UUID, Map<Long, SoilTile>> SOILS = new HashMap<>();

    private PlantSoilStates() {
    }

    /**
     * Converts every eligible floor tile within {@code radius} (square) of {@code center}.
     *
     * <p>Each converted tile remembers which terraformer made it and what block was there before, so
     * selling that terraformer can hand the ground back.
     *
     * @return how many tiles were newly converted
     */
    public static int terraform(PlayerLane lane, UUID owner, GridPosition center, int radius, PlantSoil soil) {
        if (lane == null || lane.arenaWorld() == null || owner == null || center == null || soil == null || radius < 0) {
            return 0;
        }
        Map<Long, SoilTile> tiles = SOILS.computeIfAbsent(owner, ignored -> new HashMap<>());
        int converted = 0;
        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                GridPosition target = new GridPosition(center.x() + offsetX, center.y(), center.z() + offsetZ);
                if (convert(lane, tiles, target, soil, center)) {
                    converted++;
                }
            }
        }
        return converted;
    }

    /**
     * Hands back every tile a terraformer created, restoring the block that was there before.
     *
     * <p>Without this a player could place a terraformer, pocket the sell refund, replant it one tile
     * over, and repeat until the whole lane is their soil for almost nothing.
     *
     * @return how many tiles were released
     */
    public static int releaseFrom(PlayerLane lane, UUID owner, GridPosition source) {
        Map<Long, SoilTile> tiles = SOILS.get(owner);
        if (tiles == null || source == null) {
            return 0;
        }
        int released = 0;
        Iterator<Map.Entry<Long, SoilTile>> iterator = tiles.entrySet().iterator();
        while (iterator.hasNext()) {
            SoilTile tile = iterator.next().getValue();
            if (!source.equals(tile.source())) {
                continue;
            }
            restore(lane, tile);
            iterator.remove();
            released++;
        }
        if (tiles.isEmpty()) {
            SOILS.remove(owner);
        }
        return released;
    }

    private static void restore(PlayerLane lane, SoilTile tile) {
        if (lane == null || lane.arenaWorld() == null || tile.previousState() == null) {
            return;
        }
        if (lane.arenaWorld().getBlockState(tile.position()).is(tile.soil().block())) {
            lane.arenaWorld().setBlock(tile.position(), tile.previousState(), Block.UPDATE_CLIENTS);
        }
    }

    public static PlantSoil soilAt(UUID owner, GridPosition position) {
        return position == null ? null : soilAtColumn(owner, position.x(), position.z());
    }

    public static PlantSoil soilAtColumn(UUID owner, int x, int z) {
        Map<Long, SoilTile> tiles = SOILS.get(owner);
        if (tiles == null) {
            return null;
        }
        SoilTile tile = tiles.get(columnKey(x, z));
        return tile == null ? null : tile.soil();
    }

    public static GridPosition sourceAtColumn(UUID owner, int x, int z) {
        Map<Long, SoilTile> tiles = SOILS.get(owner);
        if (tiles == null) {
            return null;
        }
        SoilTile tile = tiles.get(columnKey(x, z));
        return tile == null ? null : tile.source();
    }

    /**
     * Placement rule for the plant builder: a combat tower needs its own family's soil under it,
     * while a terraformer only needs a tile no other family owns yet.
     *
     * <p>Claims are permanent. A tile that already carries soil is never reconverted, so lane space
     * is decided once and cannot be taken back.
     */
    public static boolean canPlantAt(UUID owner, GridPosition position, TowerType type) {
        PlantSoil family = PlantTowers.soilOf(type);
        if (family == null) {
            return true;
        }
        PlantSoil existing = soilAt(owner, position);
        if (PlantTowers.isCombatTower(type)) {
            return existing == family;
        }
        return existing == null || existing == family;
    }

    public static int count(UUID owner, PlantSoil soil) {
        Map<Long, SoilTile> tiles = SOILS.get(owner);
        if (tiles == null || soil == null) {
            return 0;
        }
        int count = 0;
        for (SoilTile tile : tiles.values()) {
            if (tile.soil() == soil) {
                count++;
            }
        }
        return count;
    }

    public static int totalCount(UUID owner) {
        Map<Long, SoilTile> tiles = SOILS.get(owner);
        return tiles == null ? 0 : tiles.size();
    }

    public static void clear(UUID owner) {
        if (owner != null) {
            SOILS.remove(owner);
        }
    }

    public static void clearAllForTesting() {
        SOILS.clear();
    }

    private static boolean convert(
            PlayerLane lane,
            Map<Long, SoilTile> tiles,
            GridPosition position,
            PlantSoil soil,
            GridPosition source
    ) {
        BlockPos floor = floorAt(lane, position).orElse(null);
        if (floor == null) {
            return false;
        }
        long key = columnKey(floor.getX(), floor.getZ());
        SoilTile existing = tiles.get(key);
        if (existing != null) {
            return false;
        }
        BlockState current = lane.arenaWorld().getBlockState(floor);
        if (!current.is(soil.block())
                && !lane.arenaWorld().setBlock(floor, soil.block().defaultBlockState(), Block.UPDATE_CLIENTS)) {
            return false;
        }
        tiles.put(key, new SoilTile(floor.immutable(), soil, source, current));
        return true;
    }

    private static Optional<BlockPos> floorAt(PlayerLane lane, GridPosition position) {
        if (lane == null || position == null) {
            return Optional.empty();
        }
        BlockBounds bounds = lane.laneLayout().laneArea();
        if (position.x() < bounds.min().getX() || position.x() > bounds.max().getX()
                || position.z() < bounds.min().getZ() || position.z() > bounds.max().getZ()) {
            return Optional.empty();
        }
        for (int y = bounds.max().getY(); y >= bounds.min().getY() - 4; y--) {
            BlockPos candidate = new BlockPos(position.x(), y, position.z());
            if (!lane.arenaWorld().getBlockState(candidate).isAir()) {
                return eligibleFloor(lane, candidate) ? Optional.of(candidate) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static boolean eligibleFloor(PlayerLane lane, BlockPos position) {
        if (lane == null || position == null || lane.arenaWorld().getBlockEntity(position) != null) {
            return false;
        }
        BlockBounds bounds = lane.laneLayout().laneArea();
        if (position.getX() < bounds.min().getX() || position.getX() > bounds.max().getX()
                || position.getZ() < bounds.min().getZ() || position.getZ() > bounds.max().getZ()) {
            return false;
        }
        BlockState floor = lane.arenaWorld().getBlockState(position);
        return !floor.isAir()
                && floor.getFluidState().isEmpty()
                && !floor.getCollisionShape(lane.arenaWorld(), position).isEmpty()
                && lane.arenaWorld().getBlockState(position.above()).isAir();
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private record SoilTile(BlockPos position, PlantSoil soil, GridPosition source, BlockState previousState) {
    }
}
