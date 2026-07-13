package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import xyz.nucleoid.map_templates.BlockBounds;

final class FinalDefenseSlotAllocatorTest {
    private static final GridPosition FRONT = new GridPosition(0, 64, 2);
    private static final GridPosition MIDDLE = new GridPosition(0, 64, 5);
    private static final GridPosition BACK = new GridPosition(0, 64, 8);
    private static final Vec3 BOSS = new Vec3(0.5, 65.0, 10.5);

    @Test
    void meleeUsesFrontAndRangedUsesBackWithoutDuplicates() {
        FinalDefenseSlotAllocator allocator = allocator();

        assertEquals(FRONT, allocator.allocate(tower("melee-1", 3.0)).orElseThrow());
        assertEquals(BACK, allocator.allocate(tower("ranged-1", 6.0)).orElseThrow());
        assertEquals(MIDDLE, allocator.allocate(tower("melee-2", 3.0)).orElseThrow());
    }

    @Test
    void allocationWrapsFromEachRoleDirectionAfterEverySlotIsUsed() {
        FinalDefenseSlotAllocator allocator = allocator();

        allocator.allocate(tower("melee-1", 3.0));
        allocator.allocate(tower("ranged-1", 6.0));
        allocator.allocate(tower("melee-2", 3.0));

        assertEquals(BACK, allocator.allocate(tower("ranged-2", 6.0)).orElseThrow());
        assertEquals(2, allocator.occupancy(BACK));
        assertEquals(1, allocator.occupancy(MIDDLE));
        assertEquals(1, allocator.occupancy(FRONT));
    }

    @Test
    void duplicateSlotsFromMultipleLanesShareOneOccupancyPool() {
        LaneRegionLayout lane = layout();
        FinalDefenseSlotAllocator allocator = FinalDefenseSlotAllocator.fromLayouts(List.of(lane, lane));

        allocator.allocate(tower("melee-1", 3.0));
        allocator.allocate(tower("ranged-1", 6.0));
        allocator.allocate(tower("melee-2", 3.0));

        assertEquals(1, allocator.occupancy(FRONT));
        assertEquals(1, allocator.occupancy(MIDDLE));
        assertEquals(1, allocator.occupancy(BACK));
    }

    private static FinalDefenseSlotAllocator allocator() {
        return FinalDefenseSlotAllocator.fromLayouts(List.of(layout()));
    }

    private static LaneRegionLayout layout() {
        return new LaneRegionLayout(
                1,
                new Vec3(0.5, 65.0, 0.5),
                List.of(new Vec3(0.5, 65.0, 5.5)),
                BOSS,
                BlockBounds.of(new BlockPos(-4, 63, 0), new BlockPos(4, 70, 10)),
                List.of(BACK, FRONT, MIDDLE)
        );
    }

    private static TestTower tower(String id, double range) {
        TowerType type = new TowerType(id, id, TowerCategory.DIRECT, 0, 100.0, range, 1.0, 20, 0);
        return new TestTower(type, UUID.nameUUIDFromBytes(id.getBytes()), TeamId.RED, 1, FRONT);
    }
}
