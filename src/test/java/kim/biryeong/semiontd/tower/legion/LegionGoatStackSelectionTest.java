package kim.biryeong.semiontd.tower.legion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

final class LegionGoatStackSelectionTest {
    private static final UUID OWNER = new UUID(0, 1);
    private static final UUID OTHER_OWNER = new UUID(0, 2);
    private static final GridPosition CENTER = new GridPosition(0, 64, 0);
    private static Method stackIndexFor;
    private static Field laneTowers;

    @BeforeAll
    static void bootstrap() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        stackIndexFor = LegionGoatTower.class.getDeclaredMethod("stackIndexFor", Tower.class, PlayerLane.class);
        stackIndexFor.setAccessible(true);
        laneTowers = PlayerLane.class.getDeclaredField("towers");
        laneTowers.setAccessible(true);
    }

    @AfterEach
    void resetBalance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void linearRanksMatchStableSortAcrossSizesLimitsAndFilters() throws ReflectiveOperationException {
        Random random = new Random(761031);
        TowerType[] types = {LegionTowers.T1_GOAT_TOWER, LegionTowers.T2_STRONG_GOAT_TOWER, LegionTowers.T3_EXTREME_GOAT_TOWER};
        for (int maximum : new int[] {0, 1, 2, 3, 8}) {
            configure(maximum, 5);
            for (int size : new int[] {0, 1, 2, 3, 4, 8, 32, 128}) {
                for (int sample = 0; sample < 8; sample++) {
                    LegionGoatTower selected = goat(types[random.nextInt(types.length)], OWNER, TeamId.RED, 1,
                            new GridPosition(random.nextInt(5) - 2, 63 + random.nextInt(3), random.nextInt(5) - 2), CENTER);
                    Tower target = target(OWNER, TeamId.RED, 1, CENTER);
                    List<Tower> candidates = new ArrayList<>();
                    for (int i = 0; i < size; i++) {
                        GridPosition original = new GridPosition(random.nextInt(5) - 2,
                                63 + random.nextInt(3), random.nextInt(5) - 2);
                        GridPosition current = new GridPosition(random.nextInt(13) - 6,
                                62 + random.nextInt(5), random.nextInt(13) - 6);
                        Tower tower = random.nextInt(6) == 0 ? target(OWNER, TeamId.RED, 1, current)
                                : goat(types[random.nextInt(types.length)], random.nextInt(5) == 0 ? OTHER_OWNER : OWNER,
                                        random.nextInt(5) == 0 ? TeamId.BLUE : TeamId.RED,
                                        random.nextInt(5) == 0 ? 2 : 1, original, current);
                        if (random.nextInt(5) == 0) {tower.syncHealth(0);}
                        candidates.add(tower);
                    }
                    if (sample != 0) {candidates.add(selected);}
                    Collections.shuffle(candidates, random);
                    PlayerLane lane = lane(candidates);
                    assertEquals(sortedIndex(selected, target, lane), select(selected, target, lane),
                            "maximum=" + maximum + " size=" + size + " sample=" + sample);
                    assertEquals(candidates, lane.towers());
                }
            }
        }
    }

    @Test
    void negativeStackLimitIsRejectedWithoutReplacingCurrentBalance() {
        configure(3, 5);
        TowerBalanceConfig previous = TowerBalanceRuntime.current();
        assertThrows(IllegalArgumentException.class, () -> configure(-3, 5));
        assertSame(previous, TowerBalanceRuntime.current());
    }

    @Test
    void exactTiesAndDuplicateReferencesKeepFirstEncounterRank() throws ReflectiveOperationException {
        configure(3, 5);
        LegionGoatTower selected = goat(LegionTowers.T1_GOAT_TOWER, OWNER, TeamId.RED, 1, CENTER, CENTER);
        LegionGoatTower tied = goat(LegionTowers.T1_GOAT_TOWER, OWNER, TeamId.RED, 1, CENTER, CENTER);
        LegionGoatTower earlier = goat(LegionTowers.T1_GOAT_TOWER, OWNER, TeamId.RED, 1,
                new GridPosition(-1, 64, 0), CENTER);
        Tower target = target(OWNER, TeamId.RED, 1, CENTER);
        assertRank(1, selected, target, List.of(tied, selected));
        assertRank(0, selected, target, List.of(selected, tied));
        assertRank(2, selected, target, List.of(tied, selected, earlier));
        PlayerLane duplicates = lane(List.of(tied, selected, earlier));
        mutableTowers(duplicates).add(1, tied);
        mutableTowers(duplicates).add(selected);
        assertEquals(OptionalInt.empty(), select(selected, target, duplicates));
        assertEquals(sortedIndex(selected, target, duplicates), select(selected, target, duplicates));
        mutableTowers(duplicates).clear();
        mutableTowers(duplicates).addAll(List.of(selected, tied, selected, earlier));
        assertEquals(OptionalInt.of(1), select(selected, target, duplicates));
        assertEquals(sortedIndex(selected, target, duplicates), select(selected, target, duplicates));
    }

    @Test
    void rankingUsesOriginalCoordinatesThenTypeAndCurrentCoordinatesForRange() throws ReflectiveOperationException {
        configure(3, 5);
        LegionGoatTower selected = goat(LegionTowers.T2_STRONG_GOAT_TOWER, OWNER, TeamId.RED, 1, CENTER, CENTER);
        LegionGoatTower lowerX = goat(LegionTowers.T3_EXTREME_GOAT_TOWER, OWNER, TeamId.RED, 1,
                new GridPosition(-1, 80, 10), CENTER);
        LegionGoatTower lowerY = goat(LegionTowers.T3_EXTREME_GOAT_TOWER, OWNER, TeamId.RED, 1,
                new GridPosition(0, 63, 10), CENTER);
        LegionGoatTower lowerZ = goat(LegionTowers.T3_EXTREME_GOAT_TOWER, OWNER, TeamId.RED, 1,
                new GridPosition(0, 64, -1), CENTER);
        Tower target = target(OWNER, TeamId.RED, 1, CENTER);
        assertRank(-1, selected, target, List.of(selected, lowerZ, lowerY, lowerX));
        assertRank(2, selected, target, List.of(selected, lowerZ, lowerY));
        LegionGoatTower otherType = goat(LegionTowers.T1_GOAT_TOWER, OWNER, TeamId.RED, 1, CENTER, CENTER);
        PlayerLane lane = lane(List.of(selected, otherType));
        assertEquals(sortedIndex(selected, target, lane), select(selected, target, lane));
        lowerX.syncPosition(new GridPosition(5, 64, 0));
        assertRank(1, selected, target, List.of(selected, lowerX));
        lowerX.syncPosition(new GridPosition(5, 65, 0));
        assertRank(0, selected, target, List.of(selected, lowerX));
    }

    @Test
    void healthMovementRemovalReplacementAndReloadAreObservedOnEveryCall() throws ReflectiveOperationException {
        configure(3, 5);
        LegionGoatTower selected = goat(LegionTowers.T1_GOAT_TOWER, OWNER, TeamId.RED, 1, CENTER, CENTER);
        LegionGoatTower earlier = goat(LegionTowers.T1_GOAT_TOWER, OWNER, TeamId.RED, 1,
                new GridPosition(-1, 64, 0), CENTER);
        Tower target = target(OWNER, TeamId.RED, 1, CENTER);
        PlayerLane lane = lane(List.of(earlier, selected));
        assertEquals(OptionalInt.of(1), select(selected, target, lane));
        earlier.syncHealth(0);
        assertEquals(OptionalInt.of(0), select(selected, target, lane));
        earlier.syncHealth(earlier.currentMaxHealth());
        assertEquals(OptionalInt.of(1), select(selected, target, lane));
        target.syncPosition(new GridPosition(6, 64, 0));
        assertEquals(OptionalInt.empty(), select(selected, target, lane));
        configure(3, 6);
        assertEquals(OptionalInt.of(1), select(selected, target, lane));
        configure(0, 6);
        assertEquals(OptionalInt.empty(), select(selected, target, lane));
        lane.removeTower(earlier);
        assertEquals(OptionalInt.of(0), select(selected, target, lane));
        LegionGoatTower replacement = goat(LegionTowers.T2_STRONG_GOAT_TOWER, OWNER, TeamId.RED, 1, CENTER, CENTER);
        lane.replaceTower(selected, replacement);
        assertEquals(OptionalInt.empty(), select(selected, target, lane));
        assertEquals(OptionalInt.of(0), select(replacement, target, lane));
        target.syncHealth(0);
        assertEquals(OptionalInt.empty(), select(replacement, target, lane));
    }

    @Test
    void ownerTeamLaneSelfHealthAndNullTargetFiltersRemainRequired() throws ReflectiveOperationException {
        configure(3, 5);
        LegionGoatTower selected = goat(LegionTowers.T1_GOAT_TOWER, OWNER, TeamId.RED, 1, CENTER, CENTER);
        PlayerLane lane = lane(List.of(selected));
        assertEquals(OptionalInt.empty(), select(selected, null, lane));
        assertEquals(OptionalInt.empty(), select(selected, target(OTHER_OWNER, TeamId.RED, 1, CENTER), lane));
        assertEquals(OptionalInt.empty(), select(selected, target(OWNER, TeamId.BLUE, 1, CENTER), lane));
        assertEquals(OptionalInt.empty(), select(selected, target(OWNER, TeamId.RED, 2, CENTER), lane));
        selected.syncHealth(0);
        assertEquals(OptionalInt.empty(), select(selected, target(OWNER, TeamId.RED, 1, CENTER), lane));
    }

    private static void assertRank(int expected, LegionGoatTower selected, Tower target, List<Tower> towers)
            throws ReflectiveOperationException {
        PlayerLane lane = lane(towers);
        assertEquals(expected < 0 ? OptionalInt.empty() : OptionalInt.of(expected), select(selected, target, lane));
        assertEquals(sortedIndex(selected, target, lane), select(selected, target, lane));
    }

    private static OptionalInt select(LegionGoatTower selected, Tower target, PlayerLane lane)
            throws ReflectiveOperationException {
        return (OptionalInt) stackIndexFor.invoke(selected, target, lane);
    }

    private static OptionalInt sortedIndex(LegionGoatTower selected, Tower target, PlayerLane lane) {
        List<LegionGoatTower> providers = lane.towers().stream()
                .filter(LegionGoatTower.class::isInstance).map(LegionGoatTower.class::cast)
                .filter(goat -> canBuff(goat, target))
                .sorted(Comparator.comparingInt((LegionGoatTower goat) -> goat.originalPosition().x())
                        .thenComparingInt(goat -> goat.originalPosition().y())
                        .thenComparingInt(goat -> goat.originalPosition().z())
                        .thenComparing(goat -> goat.type().id()))
                .limit(Math.max(1, Math.min(3, TowerBalanceRuntime.abilityInt(selected.type().id(), "maxStacks"))))
                .toList();
        for (int index = 0; index < providers.size(); index++) {
            if (providers.get(index) == selected) {return OptionalInt.of(index);}
        }
        return OptionalInt.empty();
    }

    private static boolean canBuff(LegionGoatTower goat, Tower target) {
        if (goat.health() <= 0 || target == null || target.health() <= 0
                || !goat.ownerPlayer().equals(target.ownerPlayer()) || goat.teamId() != target.teamId()
                || goat.laneId() != target.laneId()) {return false;}
        double radius = TowerBalanceRuntime.ability(goat.type().id(), "radius");
        double dx = target.position().x() - goat.position().x();
        double dy = target.position().y() - goat.position().y();
        double dz = target.position().z() - goat.position().z();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    private static void configure(int maximum, double radius) {
        Map<String, Double> abilities = Map.of("maxStacks", (double) maximum, "radius", radius);
        TowerBalanceRuntime.apply(new TowerBalanceConfig(Map.of(), Map.of(), Map.of(
                LegionTowers.T1_GOAT_TOWER.id(), abilities,
                LegionTowers.T2_STRONG_GOAT_TOWER.id(), abilities,
                LegionTowers.T3_EXTREME_GOAT_TOWER.id(), abilities)));
    }

    private static LegionGoatTower goat(TowerType type, UUID owner, TeamId team, int lane,
            GridPosition original, GridPosition current) {
        return new LegionGoatTower(type, owner, team, lane, original, current);
    }

    private static Tower target(UUID owner, TeamId team, int lane, GridPosition position) {
        return new ProductionTower(LegionTowers.T1_SLIME_TOWER, owner, team, lane, position);
    }

    private static PlayerLane lane(List<Tower> towers) {
        LaneRegionLayout layout = new LaneRegionLayout(1, new Vec3(0.5, 64, 0.5),
                List.of(new Vec3(0.5, 64, 4.5)), new Vec3(0.5, 64, 10.5),
                BlockBounds.of(new BlockPos(-32, 60, -32), new BlockPos(32, 90, 32)),
                List.of(new GridPosition(0, 63, 10)));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, OWNER, null, layout);
        towers.forEach(lane::addTower);
        return lane;
    }

    @SuppressWarnings("unchecked")
    private static List<Tower> mutableTowers(PlayerLane lane) throws IllegalAccessException {
        return (List<Tower>) laneTowers.get(lane);
    }
}
