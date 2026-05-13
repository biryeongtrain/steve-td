package kim.biryeong.semiontd.game;

import kim.biryeong.semiontd.map.ArenaLayout;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.minecraft.world.phys.Vec3;

public final class StartPlacement {
    private static final Vec3[] ACTIVE_OFFSETS = new Vec3[] {
            new Vec3(-2.5, 0.0, -2.5),
            new Vec3(2.5, 0.0, -2.5),
            new Vec3(0.0, 0.0, 0.0),
            new Vec3(-2.5, 0.0, 2.5),
            new Vec3(2.5, 0.0, 2.5)
    };
    private static final Vec3[] FINAL_DEFENSE_LANE_OFFSETS = new Vec3[] {
            new Vec3(-4.0, 0.0, -2.0),
            new Vec3(4.0, 0.0, -2.0),
            new Vec3(0.0, 0.0, -3.0),
            new Vec3(-4.0, 0.0, 2.0),
            new Vec3(4.0, 0.0, 2.0)
    };
    private static final Vec3 SPECTATOR_BASE_OFFSET = new Vec3(0.0, 8.0, 0.0);
    private static final double SPECTATOR_SPACING = 2.5;
    private static final double FINAL_DEFENSE_TOWER_SPACING = 1.5;

    private StartPlacement() {
    }

    public static Vec3 activePlayerSpawn(ArenaLayout layout, int laneId) {
        return layout.lane(laneId)
                .map(LaneRegionLayout::spawn)
                .orElseGet(() -> {
                    int index = Math.max(1, Math.min(laneId, ACTIVE_OFFSETS.length)) - 1;
                    return layout.teamSpawn().add(ACTIVE_OFFSETS[index]);
                });
    }

    public static Vec3 spectatorSpawn(ArenaLayout layout, int spectatorIndex) {
        int normalizedIndex = Math.max(0, spectatorIndex);
        double xOffset = (normalizedIndex % 5) * SPECTATOR_SPACING - (2 * SPECTATOR_SPACING);
        double zOffset = (normalizedIndex / 5) * SPECTATOR_SPACING;
        return layout.teamSpawn().add(SPECTATOR_BASE_OFFSET).add(xOffset, 0.0, zOffset);
    }

    public static Vec3 finalDefenseTowerPosition(ArenaLayout layout, int laneId, int towerIndex) {
        return finalDefenseTowerPosition(layout.bossSpawn(), laneId, towerIndex);
    }

    public static Vec3 finalDefenseTowerPosition(Vec3 bossSpawn, int laneId, int towerIndex) {
        int laneIndex = Math.max(1, Math.min(laneId, FINAL_DEFENSE_LANE_OFFSETS.length)) - 1;
        int normalizedTowerIndex = Math.max(0, towerIndex);
        double xOffset = (normalizedTowerIndex % 3) * FINAL_DEFENSE_TOWER_SPACING - FINAL_DEFENSE_TOWER_SPACING;
        double zOffset = (normalizedTowerIndex / 3) * FINAL_DEFENSE_TOWER_SPACING;
        return bossSpawn.add(FINAL_DEFENSE_LANE_OFFSETS[laneIndex]).add(xOffset, 0.0, zOffset);
    }
}
