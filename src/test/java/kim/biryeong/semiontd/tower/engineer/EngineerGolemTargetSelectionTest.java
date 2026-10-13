package kim.biryeong.semiontd.tower.engineer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

final class EngineerGolemTargetSelectionTest {
    private static final UUID OWNER = new UUID(0, 1);
    private static final UUID OTHER_OWNER = new UUID(0, 2);
    private static Method choosePlate;
    private static Field plateCooldowns;
    private static Field lastPressedPlate;

    @BeforeAll
    static void bootstrap() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        choosePlate = EngineerGolemTower.class.getDeclaredMethod("choosePlate", PlayerLane.class, Vec3.class);
        choosePlate.setAccessible(true);
        plateCooldowns = EngineerGolemTower.class.getDeclaredField("plateCooldowns");
        plateCooldowns.setAccessible(true);
        lastPressedPlate = EngineerGolemTower.class.getDeclaredField("lastPressedPlate");
        lastPressedPlate.setAccessible(true);
    }

    @Test
    void linearSelectionMatchesStableSortAcrossSizesAndFilters() throws ReflectiveOperationException {
        Random random = new Random(826031);
        EngineerTowers.PlateKind[] kinds = EngineerTowers.PlateKind.values();
        for (int size : new int[] {0, 1, 2, 8, 9, 32, 128, 512}) {
            for (int sample = 0; sample < 12; sample++) {
                List<Tower> candidates = new ArrayList<>();
                Map<GridPosition, Integer> cooldowns = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    GridPosition position = new GridPosition(random.nextInt(33) - 16,
                            62 + random.nextInt(5), random.nextInt(33) - 16);
                    UUID owner = random.nextInt(4) == 0 ? OTHER_OWNER : OWNER;
                    TowerType type = random.nextInt(5) == 0 ? EngineerTowers.REDSTONE_DUST
                            : EngineerTowers.plate(kinds[random.nextInt(kinds.length)]);
                    Tower tower = random.nextInt(7) == 0 ? new NonCircuitTower(type, owner, position)
                            : new EngineerCircuitTower(type, owner, TeamId.RED, 1, position,
                                    new GridPosition(position.x() + 10, position.y(), position.z() - 10));
                    candidates.add(tower);
                    if (random.nextInt(5) == 0) {
                        cooldowns.put(position, random.nextInt(5) - 2);
                    }
                }
                Collections.shuffle(candidates, random);
                GridPosition last = candidates.isEmpty() || random.nextBoolean() ? null
                        : candidates.get(random.nextInt(candidates.size())).originalPosition();
                PlayerLane lane = lane(candidates);
                EngineerGolemTower golem = golem();
                setSelectionState(golem, cooldowns, last);
                Vec3 origin = new Vec3(random.nextInt(17) - 8.5,
                        62.0625 + random.nextInt(5), random.nextInt(17) - 8.5);
                GridPosition expected = sortedSelection(lane, origin, cooldowns, last);
                assertSame(expected, select(golem, lane, origin), "size=" + size + " sample=" + sample);
                assertEquals(candidates, lane.towers());
                assertEquals(cooldowns, cooldowns(golem));
                assertSame(last, lastPressedPlate.get(golem));
            }
        }
    }

    @Test
    void priorityThenDistanceThenXThenZRemainTheSelectionOrder() throws ReflectiveOperationException {
        Vec3 origin = new Vec3(0.5, 65.0625, 0.5);
        EngineerCircuitTower wood = plate(EngineerTowers.PlateKind.WOOD, 0, 64, 0);
        EngineerCircuitTower stone = plate(EngineerTowers.PlateKind.STONE, 2, 64, 0);
        EngineerCircuitTower iron = plate(EngineerTowers.PlateKind.IRON, 4, 64, 0);
        EngineerCircuitTower farGold = plate(EngineerTowers.PlateKind.GOLD, 8, 64, 0);
        assertSelected(List.of(wood, stone, iron, farGold), origin, farGold);
        EngineerCircuitTower nearGold = plate(EngineerTowers.PlateKind.GOLD, 2, 64, 0);
        assertSelected(List.of(farGold, nearGold), origin, nearGold);
        EngineerCircuitTower lowerX = plate(EngineerTowers.PlateKind.GOLD, -2, 64, 0);
        assertSelected(List.of(nearGold, lowerX), origin, lowerX);
        EngineerCircuitTower positiveZ = plate(EngineerTowers.PlateKind.GOLD, 0, 64, 2);
        EngineerCircuitTower negativeZ = plate(EngineerTowers.PlateKind.GOLD, 0, 64, -2);
        assertSelected(List.of(positiveZ, negativeZ), origin, negativeZ);
    }

    @Test
    void exactTiesKeepEncounterOrderWithoutAnExtraHeightTieBreaker() throws ReflectiveOperationException {
        Vec3 origin = new Vec3(0.5, 65.0625, 0.5);
        EngineerCircuitTower lower = plate(EngineerTowers.PlateKind.GOLD, 0, 63, 0);
        EngineerCircuitTower upper = plate(EngineerTowers.PlateKind.GOLD, 0, 65, 0);
        assertSelected(List.of(upper, lower), origin, upper);
        assertSelected(List.of(lower, upper), origin, lower);
        EngineerCircuitTower duplicate = plate(EngineerTowers.PlateKind.GOLD, 0, 63, 0);
        assertSelected(List.of(duplicate, lower), origin, duplicate);
        assertSelected(List.of(lower, duplicate), origin, lower);
    }

    @Test
    void excludedCandidatesAndChangedCooldownStateAreObservedOnEveryCall() throws ReflectiveOperationException {
        EngineerCircuitTower allowed = plate(EngineerTowers.PlateKind.WOOD, 20, 64, 0);
        EngineerCircuitTower zeroCooldown = plate(EngineerTowers.PlateKind.GOLD, 2, 64, 0);
        EngineerCircuitTower negativeCooldown = plate(EngineerTowers.PlateKind.GOLD, 4, 64, 0);
        EngineerCircuitTower last = plate(EngineerTowers.PlateKind.GOLD, 3, 64, 0);
        GridPosition originPosition = new GridPosition(0, 64, 0);
        EngineerCircuitTower foreign = new EngineerCircuitTower(EngineerTowers.plate(EngineerTowers.PlateKind.GOLD),
                OTHER_OWNER, TeamId.RED, 1, originPosition, originPosition);
        EngineerCircuitTower dust = new EngineerCircuitTower(EngineerTowers.REDSTONE_DUST,
                OWNER, TeamId.RED, 1, originPosition, originPosition);
        PlayerLane lane = lane(List.of(new NonCircuitTower(EngineerTowers.plate(EngineerTowers.PlateKind.GOLD),
                OWNER, originPosition), dust, foreign, zeroCooldown, negativeCooldown, last, allowed));
        EngineerGolemTower golem = golem();
        Map<GridPosition, Integer> cooldowns = Map.of(zeroCooldown.originalPosition(), 0,
                negativeCooldown.originalPosition(), -1);
        setSelectionState(golem, cooldowns, last.originalPosition());
        Vec3 origin = new Vec3(0.5, 65.0625, 0.5);
        assertSame(allowed.originalPosition(), select(golem, lane, origin));
        setSelectionState(golem, Map.of(), last.originalPosition());
        assertSame(zeroCooldown.originalPosition(), select(golem, lane, origin));
        setSelectionState(golem, cooldowns, null);
        assertSame(last.originalPosition(), select(golem, lane, origin));
        lane.removeTower(last);
        assertSame(allowed.originalPosition(), select(golem, lane, origin));
        lane.removeTower(allowed);
        assertNull(select(golem, lane, origin));
    }

    private static void assertSelected(List<Tower> candidates, Vec3 origin, Tower expected)
            throws ReflectiveOperationException {
        assertSame(expected.originalPosition(), select(golem(), lane(candidates), origin));
    }

    private static GridPosition select(EngineerGolemTower golem, PlayerLane lane, Vec3 origin)
            throws ReflectiveOperationException {
        return (GridPosition) choosePlate.invoke(golem, lane, origin);
    }

    private static void setSelectionState(EngineerGolemTower golem, Map<GridPosition, Integer> cooldowns,
            GridPosition last) throws ReflectiveOperationException {
        cooldowns(golem).clear();
        cooldowns(golem).putAll(cooldowns);
        lastPressedPlate.set(golem, last);
    }

    @SuppressWarnings("unchecked")
    private static Map<GridPosition, Integer> cooldowns(EngineerGolemTower golem) throws IllegalAccessException {
        return (Map<GridPosition, Integer>) plateCooldowns.get(golem);
    }

    private static GridPosition sortedSelection(PlayerLane lane, Vec3 origin,
            Map<GridPosition, Integer> cooldowns, GridPosition last) {
        return lane.towers().stream()
                .filter(EngineerCircuitTower.class::isInstance)
                .map(EngineerCircuitTower.class::cast)
                .filter(tower -> OWNER.equals(tower.ownerPlayer()))
                .filter(tower -> tower.plateKind() != null)
                .filter(tower -> !cooldowns.containsKey(tower.originalPosition()))
                .filter(tower -> !tower.originalPosition().equals(last))
                .sorted(Comparator.comparingInt((EngineerCircuitTower tower) -> tower.plateKind().priority()).reversed()
                        .thenComparingDouble(tower -> plateCenter(tower).distanceToSqr(origin))
                        .thenComparingInt(tower -> tower.originalPosition().x())
                        .thenComparingInt(tower -> tower.originalPosition().z()))
                .map(Tower::originalPosition)
                .findFirst()
                .orElse(null);
    }

    private static Vec3 plateCenter(EngineerCircuitTower tower) {
        BlockPos block = tower.circuitPosition();
        return new Vec3(block.getX() + 0.5, block.getY() + 0.0625, block.getZ() + 0.5);
    }

    private static EngineerCircuitTower plate(EngineerTowers.PlateKind kind, int x, int y, int z) {
        GridPosition position = new GridPosition(x, y, z);
        return new EngineerCircuitTower(EngineerTowers.plate(kind), OWNER, TeamId.RED, 1, position, position);
    }

    private static EngineerGolemTower golem() {
        GridPosition position = new GridPosition(0, 64, 0);
        return new EngineerGolemTower(EngineerTowers.COPPER_GOLEM, OWNER, TeamId.RED, 1, position, position);
    }

    private static PlayerLane lane(List<Tower> towers) {
        LaneRegionLayout layout = new LaneRegionLayout(1, new Vec3(0.5, 64.0, 0.5),
                List.of(new Vec3(0.5, 64.0, 4.5)), new Vec3(0.5, 64.0, 10.5),
                BlockBounds.of(new BlockPos(-32, 60, -32), new BlockPos(32, 70, 32)),
                List.of(new GridPosition(0, 63, 10)));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, OWNER, null, layout);
        towers.forEach(lane::addTower);
        return lane;
    }

    private static final class NonCircuitTower extends Tower {
        private NonCircuitTower(TowerType type, UUID owner, GridPosition position) {
            super(type, owner, TeamId.RED, 1, position, position);
        }

        @Override
        protected boolean execute(PlayerLane lane) {
            return false;
        }
    }
}
