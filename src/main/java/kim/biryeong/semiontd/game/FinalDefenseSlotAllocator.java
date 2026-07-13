package kim.biryeong.semiontd.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.Tower;
import net.minecraft.world.phys.Vec3;

final class FinalDefenseSlotAllocator {
    static final double RANGED_TOWER_RANGE_THRESHOLD = 3.0;

    private final List<GridPosition> slots;
    private final Vec3 bossPosition;
    private final Map<GridPosition, Integer> occupancy = new HashMap<>();

    private FinalDefenseSlotAllocator(List<GridPosition> slots, Vec3 bossPosition) {
        this.slots = List.copyOf(slots);
        this.bossPosition = bossPosition;
    }

    static FinalDefenseSlotAllocator fromLanes(List<PlayerLane> lanes) {
        List<LaneRegionLayout> layouts = lanes == null
                ? List.of()
                : lanes.stream().map(PlayerLane::laneLayout).toList();
        return fromLayouts(layouts);
    }

    static FinalDefenseSlotAllocator fromLayouts(List<LaneRegionLayout> layouts) {
        if (layouts == null || layouts.isEmpty()) {
            return new FinalDefenseSlotAllocator(List.of(), Vec3.ZERO);
        }

        Set<GridPosition> uniqueSlots = new LinkedHashSet<>();
        for (LaneRegionLayout layout : layouts) {
            if (layout != null) {
                uniqueSlots.addAll(layout.finalDefenseTowerSlots());
            }
        }
        return new FinalDefenseSlotAllocator(new ArrayList<>(uniqueSlots), layouts.getFirst().bossPosition());
    }

    Optional<GridPosition> allocate(Tower tower) {
        if (tower == null || slots.isEmpty()) {
            return Optional.empty();
        }

        boolean ranged = tower.type().range() > RANGED_TOWER_RANGE_THRESHOLD;
        Comparator<GridPosition> preferredOrder = Comparator
                .comparingInt((GridPosition slot) -> occupancy.getOrDefault(slot, 0))
                .thenComparing(ranged ? distanceToBoss() : distanceToBoss().reversed())
                .thenComparingInt(GridPosition::x)
                .thenComparingInt(GridPosition::z);
        GridPosition selected = slots.stream().min(preferredOrder).orElseThrow();
        occupancy.merge(selected, 1, Integer::sum);
        return Optional.of(selected);
    }

    void reset() {
        occupancy.clear();
    }

    int occupancy(GridPosition slot) {
        return occupancy.getOrDefault(slot, 0);
    }

    private Comparator<GridPosition> distanceToBoss() {
        return Comparator.comparingDouble(slot -> bossPosition.distanceTo(
                new Vec3(slot.x() + 0.5, slot.y(), slot.z() + 0.5)
        ));
    }
}
