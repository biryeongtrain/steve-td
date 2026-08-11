package kim.biryeong.semiontd.tower.ancientcity;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.tower.Tower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class AncientCityStates {
    public static final String CONFIG_ID = "ancient_city_global";
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    private static final Map<UUID, AncientCityState> STATES = new HashMap<>();

    private AncientCityStates() {
    }

    public static void ensureSeeded(AncientCityTower tower, PlayerLane lane) {
        if (tower == null || lane == null) {
            return;
        }
        AncientCityState state = STATES.computeIfAbsent(tower.ownerPlayer(), ignored -> new AncientCityState());
        if (state.seeded) {
            return;
        }
        BlockPos origin = floorAt(lane, tower.originalPosition()).orElse(null);
        if (origin == null) {
            return;
        }
        state.seeded = true;
        state.seedOrigin = origin;
        spread(lane, state.territory, origin, abilityInt("initialSculk"), true);
    }

    public static void onRoundStarted(UUID playerId, int round) {
        AncientCityState state = STATES.get(playerId);
        if (state != null) {
            state.beginRound(round);
        }
    }

    public static void onWaveStarted(AncientCityTower tower, PlayerLane lane, int round) {
        if (tower == null || lane == null) {
            return;
        }
        ensureSeeded(tower, lane);
        AncientCityState state = STATES.get(tower.ownerPlayer());
        if (state == null || !state.seeded || state.waveSpreadRound == round || !hasAliveTower(lane, tower.ownerPlayer())) {
            return;
        }
        state.beginRound(round);
        state.waveSpreadRound = round;
        growMainTerritory(lane, state, abilityInt("waveStartSpread"));
    }

    public static void onMonsterKilled(JobContext context, Monster monster) {
        if (context == null || monster == null) {
            return;
        }
        UUID playerId = context.player().uuid();
        PlayerLane lane = context.game().playerLane(playerId).orElse(null);
        recordAttributedDeath(playerId, lane, context.game().currentRound(),
                lane == null ? null : lane.monsterDeathPosition(monster));
    }

    public static void recordAttributedDeath(UUID playerId, PlayerLane lane, int round, Vec3 deathPosition) {
        AncientCityState state = STATES.get(playerId);
        if (state == null || lane == null || deathPosition == null || !state.seeded
                || !hasAliveMainLaneTower(lane, playerId)) {
            return;
        }
        state.beginRound(round);
        int cap = Math.max(0, abilityInt("deathSpreadCapPerRound"));
        if (state.deathSpreadsThisRound >= cap || state.territory.size() >= abilityInt("maxSculk")) {
            return;
        }
        BlockPos deathFloor = floorAt(lane, GridPosition.from(BlockPos.containing(deathPosition))).orElse(null);
        if (deathFloor == null) {
            return;
        }
        boolean added = state.territory.contains(deathFloor)
                ? spread(lane, state.territory, deathFloor, 1, true) == 1
                : addSculk(lane, state.territory, deathFloor, true);
        if (added) {
            state.deathSpreadsThisRound++;
        }
    }

    public static void ensureFinalDefenseSeeded(AncientCityTower tower, PlayerLane lane) {
        if (tower == null || lane == null || !tower.deployedAtFinalDefense()) {
            return;
        }
        AncientCityState state = STATES.get(tower.ownerPlayer());
        if (state == null || state.finalDefenseSeeded) {
            return;
        }
        BlockPos origin = floorAt(lane, tower.position()).orElse(null);
        if (origin == null) {
            return;
        }
        state.finalDefenseSeeded = true;
        spread(lane, state.finalDefenseTerritory, origin, abilityInt("finalDefenseSeedCount"), false);
    }

    public static boolean resonanceActive(Tower tower) {
        if (tower == null) {
            return false;
        }
        AncientCityState state = STATES.get(tower.ownerPlayer());
        if (state == null) {
            return false;
        }
        GridPosition current = tower.position();
        return containsPosition(state.territory, current)
                || containsPosition(state.finalDefenseTerritory, current);
    }

    private static boolean containsPosition(Set<BlockPos> territory, GridPosition position) {
        return territory.stream().anyMatch(block ->
                block.getX() == position.x() && block.getZ() == position.z()
        );
    }

    public static double resonanceBonus(Tower tower) {
        if (!resonanceActive(tower)) {
            return 0.0;
        }
        return resonanceBonusForCount(territoryCount(tower.ownerPlayer()));
    }

    public static double resonanceBonusForCount(int territoryCount) {
        int maxSculk = Math.max(1, abilityInt("maxSculk"));
        int fullAt = Math.min(maxSculk, Math.max(1, abilityInt("resonanceFullAt")));
        double cap = Math.max(0.0, ability("resonanceDamageCap"));
        return Math.min(cap, Math.max(0, territoryCount) / (double) fullAt * cap);
    }

    public static int territoryCount(UUID playerId) {
        AncientCityState state = STATES.get(playerId);
        return state == null ? 0 : state.territory.size();
    }

    public static Set<BlockPos> territoryPositions(UUID playerId) {
        AncientCityState state = STATES.get(playerId);
        return state == null ? Set.of() : Set.copyOf(state.territory);
    }

    public static void clear(UUID playerId) {
        if (playerId != null) {
            STATES.remove(playerId);
        }
    }

    public static void clearAllForTesting() {
        STATES.clear();
    }

    private static int growMainTerritory(PlayerLane lane, AncientCityState state, int amount) {
        int remaining = Math.max(0, abilityInt("maxSculk") - state.territory.size());
        return spread(lane, state.territory, state.seedOrigin, Math.min(Math.max(0, amount), remaining), true);
    }

    private static int spread(
            PlayerLane lane,
            Set<BlockPos> territory,
            BlockPos origin,
            int amount,
            boolean mainTerritory
    ) {
        if (lane == null || origin == null || amount <= 0) {
            return 0;
        }
        int added = 0;
        if (territory.isEmpty() && addSculk(lane, territory, origin, mainTerritory)) {
            added++;
        }
        PriorityQueue<BlockPos> frontier = new PriorityQueue<>(frontierOrder(origin));
        Set<BlockPos> queued = new HashSet<>();
        for (BlockPos current : territory) {
            enqueueNeighbors(lane, territory, current, frontier, queued);
        }
        while (added < amount && !frontier.isEmpty()) {
            BlockPos next = frontier.remove();
            if (!addSculk(lane, territory, next, mainTerritory)) {
                continue;
            }
            added++;
            enqueueNeighbors(lane, territory, next, frontier, queued);
        }
        return added;
    }

    private static void enqueueNeighbors(
            PlayerLane lane,
            Set<BlockPos> territory,
            BlockPos current,
            PriorityQueue<BlockPos> frontier,
            Set<BlockPos> queued
    ) {
        for (Direction direction : HORIZONTAL) {
            BlockPos adjacent = current.relative(direction);
            floorAt(lane, new GridPosition(adjacent.getX(), current.getY(), adjacent.getZ()))
                    .filter(candidate -> !territory.contains(candidate))
                    .filter(queued::add)
                    .ifPresent(frontier::add);
        }
    }

    private static Comparator<BlockPos> frontierOrder(BlockPos origin) {
        return Comparator
                .comparingInt((BlockPos pos) -> manhattanXZ(pos, origin))
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ)
                .thenComparingInt(BlockPos::getY);
    }

    private static boolean addSculk(PlayerLane lane, Set<BlockPos> territory, BlockPos position, boolean mainTerritory) {
        if (position == null || territory.contains(position) || !eligibleFloor(lane, position)) {
            return false;
        }
        BlockState current = lane.arenaWorld().getBlockState(position);
        if (!current.is(Blocks.SCULK)
                && !lane.arenaWorld().setBlock(position, Blocks.SCULK.defaultBlockState(), Block.UPDATE_CLIENTS)) {
            return false;
        }
        territory.add(position.immutable());
        AncientCityVfx.showGrowth(lane.arenaWorld(), position, mainTerritory);
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

    private static boolean hasAliveTower(PlayerLane lane, UUID ownerPlayer) {
        return lane.towers().stream().anyMatch(tower -> tower instanceof AncientCityTower
                && tower.ownerPlayer().equals(ownerPlayer)
                && tower.health() > 0.0);
    }

    private static boolean hasAliveMainLaneTower(PlayerLane lane, UUID ownerPlayer) {
        return lane.towers().stream().anyMatch(tower -> tower instanceof AncientCityTower
                && tower.ownerPlayer().equals(ownerPlayer)
                && tower.health() > 0.0
                && !tower.deployedAtFinalDefense());
    }

    private static int manhattanXZ(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX()) + Math.abs(first.getZ() - second.getZ());
    }

    private static double ability(String key) {
        return TowerBalanceRuntime.ability(CONFIG_ID, key);
    }

    private static int abilityInt(String key) {
        return TowerBalanceRuntime.abilityInt(CONFIG_ID, key);
    }

    private static final class AncientCityState {
        private final Set<BlockPos> territory = new HashSet<>();
        private final Set<BlockPos> finalDefenseTerritory = new HashSet<>();
        private BlockPos seedOrigin;
        private boolean seeded;
        private boolean finalDefenseSeeded;
        private int activeRound;
        private int waveSpreadRound = -1;
        private int deathSpreadsThisRound;

        private void beginRound(int round) {
            if (activeRound == round) {
                return;
            }
            activeRound = round;
            deathSpreadsThisRound = 0;
        }
    }
}
