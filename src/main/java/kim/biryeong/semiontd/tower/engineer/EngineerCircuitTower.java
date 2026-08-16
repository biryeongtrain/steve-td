package kim.biryeong.semiontd.tower.engineer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.Level;

public final class EngineerCircuitTower extends Tower {
    private static final Map<Level, Set<BlockPos>> ENGINEER_PLATES = new WeakHashMap<>();
    private BlockState replacedState;
    private Block placedBlock;
    private long lastPressedGameTime = Long.MIN_VALUE;

    public EngineerCircuitTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    public int slotWeight() {
        return EngineerTowers.isSlotFree(type()) ? 0 : 1;
    }

    @Override
    public boolean participatesInFinalDefense() {
        return false;
    }

    @Override
    public boolean targetableByMonsters() {
        return false;
    }

    @Override
    public boolean countsForLaneDefense() {
        return false;
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null) {
            return;
        }
        BlockState state = blockState();
        if (state == null) {
            return;
        }
        BlockPos position = circuitPosition();
        replacedState = lane.arenaWorld().getBlockState(position);
        placedBlock = state.getBlock();
        lane.arenaWorld().setBlock(position, state, Block.UPDATE_ALL);
        if (plateKind() != null) {
            ENGINEER_PLATES.computeIfAbsent(lane.arenaWorld(), ignored -> new HashSet<>()).add(position.immutable());
        }
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null || placedBlock == null) {
            return;
        }
        BlockPos position = circuitPosition();
        if (lane.arenaWorld().getBlockState(position).is(placedBlock)) {
            lane.arenaWorld().setBlock(
                    position,
                    replacedState == null ? Blocks.AIR.defaultBlockState() : replacedState,
                    Block.UPDATE_ALL
            );
        }
        unregisterPlate(lane.arenaWorld(), position);
        placedBlock = null;
        replacedState = null;
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        super.resetForRound(lane);
        lastPressedGameTime = Long.MIN_VALUE;
        if (lane != null && lane.arenaWorld() != null
                && (placedBlock == null || !lane.arenaWorld().getBlockState(circuitPosition()).is(placedBlock))) {
            onPlaced(lane);
        }
    }

    @Override
    public void moveToFinalDefense(PlayerLane lane, GridPosition position) {
    }

    public boolean powered(PlayerLane lane) {
        return lane != null && lane.arenaWorld() != null
                && lane.arenaWorld().hasNeighborSignal(circuitPosition());
    }

    public boolean platePressed(PlayerLane lane) {
        if (plateKind() == null || lane == null || lane.arenaWorld() == null) {
            return false;
        }
        BlockState state = lane.arenaWorld().getBlockState(circuitPosition());
        if (state.hasProperty(BlockStateProperties.POWERED)) {
            return state.getValue(BlockStateProperties.POWERED);
        }
        return state.hasProperty(BlockStateProperties.POWER)
                && state.getValue(BlockStateProperties.POWER) > 0;
    }

    public boolean pressPlate(PlayerLane lane) {
        if (plateKind() == null || lane == null || lane.arenaWorld() == null) {
            return false;
        }
        BlockPos position = circuitPosition();
        BlockState state = lane.arenaWorld().getBlockState(position);
        BlockState pressed = state;
        if (state.hasProperty(BlockStateProperties.POWERED)) {
            pressed = state.setValue(BlockStateProperties.POWERED, true);
        } else if (state.hasProperty(BlockStateProperties.POWER)) {
            pressed = state.setValue(BlockStateProperties.POWER, 15);
        } else {
            return false;
        }
        lane.arenaWorld().setBlock(position, pressed, Block.UPDATE_ALL);
        lane.arenaWorld().scheduleTick(position, pressed.getBlock(), 10);
        boolean activated = platePressed(lane);
        if (activated) {
            lastPressedGameTime = lane.arenaWorld().getGameTime();
        }
        return activated;
    }

    long lastPressedGameTime() {
        return lastPressedGameTime;
    }

    public static boolean isEngineerPlate(Level level, BlockPos position) {
        Set<BlockPos> plates = ENGINEER_PLATES.get(level);
        return plates != null && plates.contains(position);
    }

    public BlockPos circuitPosition() {
        return new BlockPos(originalPosition().x(), originalPosition().y() + 1, originalPosition().z());
    }

    public EngineerTowers.PlateKind plateKind() {
        return EngineerTowers.plateKind(type()).orElse(null);
    }

    @Override
    public List<String> runtimeDetailLines() {
        ArrayList<String> lines = new ArrayList<>();
        if (plateKind() != null) {
            lines.add("<aqua>발판 우선순위</aqua> <white>" + plateKind().priority() + "</white>");
        }
        EngineerTowers.repeaterDirection(type()).ifPresent(direction ->
                lines.add("<gold>중계기 방향</gold> <white>" + direction.getName() + "</white>"));
        return List.copyOf(lines);
    }

    private BlockState blockState() {
        if (EngineerTowers.isDust(type())) {
            return Blocks.REDSTONE_WIRE.defaultBlockState();
        }
        Direction repeaterDirection = EngineerTowers.repeaterDirection(type()).orElse(null);
        if (repeaterDirection != null) {
            return Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.FACING, repeaterDirection.getOpposite());
        }
        EngineerTowers.PlateKind kind = plateKind();
        return kind == null ? null : kindBlock(kind).defaultBlockState();
    }

    private static Block kindBlock(EngineerTowers.PlateKind kind) {
        return switch (kind) {
            case WOOD -> Blocks.OAK_PRESSURE_PLATE;
            case STONE -> Blocks.STONE_PRESSURE_PLATE;
            case IRON -> Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE;
            case GOLD -> Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE;
        };
    }

    private static void unregisterPlate(Level level, BlockPos position) {
        Set<BlockPos> plates = ENGINEER_PLATES.get(level);
        if (plates == null) {
            return;
        }
        plates.remove(position);
        if (plates.isEmpty()) {
            ENGINEER_PLATES.remove(level);
        }
    }

    @Override
    protected boolean execute(PlayerLane lane) {
        return false;
    }
}
