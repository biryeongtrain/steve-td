package kim.biryeong.semiontd.tower.atlantis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.phys.Vec3;

/**
 * Owns the high pressure zones deployed by each player's turtle towers.
 *
 * <p>Zones are keyed by player UUID and rebuilt whenever the turtle roster changes (placement,
 * upgrade, sale, destruction). They are deliberately not stored on the turtle tower itself, since
 * a zone outlives the individual runtime tower instance across upgrades.
 */
public final class AtlantisStates {
    private static final Map<UUID, List<PressureZone>> ZONES = new HashMap<>();

    private AtlantisStates() {
    }

    public static void clear(UUID playerId) {
        if (playerId != null) {
            ZONES.remove(playerId);
        }
    }

    public static void clearAll() {
        ZONES.clear();
    }

    public static List<PressureZone> zones(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        return List.copyOf(ZONES.getOrDefault(playerId, List.of()));
    }

    public static int zoneCount(UUID playerId) {
        return playerId == null ? 0 : ZONES.getOrDefault(playerId, List.of()).size();
    }

    /**
     * Returns the strongest zone containing {@code position}, or {@code null} when the position is
     * outside every zone. Overlapping zones do not stack; the largest radius wins, which matches the
     * unsourced timed-effect model used to apply the zone effect.
     */
    public static PressureZone strongestZoneAt(UUID playerId, Vec3 position) {
        PressureZone best = null;
        for (PressureZone zone : ZONES.getOrDefault(playerId, List.of())) {
            if (zone.contains(position) && (best == null || zone.radius() > best.radius())) {
                best = zone;
            }
        }
        return best;
    }

    /**
     * How many of this player's zones cover {@code position}.
     *
     * <p>Zone effects are applied per zone (sourced), so overlapping zones each contribute their
     * own entry. Callers divide their magnitude by this count to keep the total at the configured
     * ceiling: movement reduction is consumed as {@code 1.0 - reduction}, so letting two zones sum
     * freely would pin a monster in place.
     */
    public static int overlapCount(UUID playerId, Vec3 position) {
        int count = 0;
        for (PressureZone zone : ZONES.getOrDefault(playerId, List.of())) {
            if (zone.contains(position)) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    /**
     * Rebuilds every zone owned by {@code playerId} from the turtle towers currently on the lane.
     * Called after any change to the turtle roster.
     */
    public static void rebuild(UUID playerId, PlayerLane lane) {
        if (playerId == null || lane == null) {
            return;
        }
        List<Tower> turtles = lane.towers().stream()
                .filter(tower -> playerId.equals(tower.ownerPlayer()))
                .filter(tower -> AtlantisTowers.isTurtle(tower.type()))
                .filter(tower -> !tower.isDestroyed(lane))
                .toList();
        if (turtles.isEmpty()) {
            ZONES.remove(playerId);
            return;
        }

        boolean finalDefense = turtles.stream().anyMatch(Tower::deployedAtFinalDefense);
        PlayerLane pathLane = finalDefense ? lane.finalDefensePathLane() : lane;

        // One wall, not one cluster per turtle. Deploying independently around each turtle stacked
        // zones on top of each other whenever turtles stood close together, so adding a turtle did
        // not visibly add coverage. A single line anchored on the rearmost turtle makes the roster
        // legible: more turtles, or higher tiers, means a longer wall.
        int budget = Math.min(AtlantisBalance.maxZoneCount(), totalCapacity(turtles));
        if (budget <= 0) {
            ZONES.remove(playerId);
            return;
        }

        double anchor = finalDefense
                ? Monster.FINAL_DEFENSE_PROGRESS
                : rearmostProgress(lane, turtles);
        if (anchor < 0.0) {
            ZONES.remove(playerId);
            return;
        }

        // Monsters walk from progress 0 towards 1, so the ground they have yet to cross is the
        // lower side. Zones laid towards 1 sit behind the wave and never get used.
        double step = spacingProgress(pathLane);
        GridPosition owner = anchorTurtle(lane, turtles, finalDefense).originalPosition();
        double radius = strongest(turtles, "zoneRadius", 2.5);
        double reduction = strongest(turtles, "zoneAllyDamageReduction", 0.05);

        List<PressureZone> deployed = new ArrayList<>();
        for (int index = 0; index < budget; index++) {
            double progress = anchor - step * index;
            if (progress <= 0.0) {
                // Ran out of approach to cover: stop rather than clamping onto the spawn.
                break;
            }
            deployed.add(new PressureZone(
                    owner,
                    pathLane.laneLayout().positionAt(progress),
                    radius,
                    reduction
            ));
        }

        if (deployed.isEmpty()) {
            ZONES.remove(playerId);
        } else {
            ZONES.put(playerId, List.copyOf(deployed));
        }
    }

    private static int totalCapacity(List<Tower> turtles) {
        int total = 0;
        for (Tower turtle : turtles) {
            total += zoneCapacity(turtle.type());
        }
        return total;
    }

    /** Converts the configured block spacing into a fraction of this lane's path. */
    private static double spacingProgress(PlayerLane lane) {
        double length = pathLength(lane);
        if (length <= 0.0) {
            return 0.05;
        }
        return Math.min(0.5, AtlantisBalance.zoneSpacingBlocks() / length);
    }

    private static double pathLength(PlayerLane lane) {
        List<Vec3> points = lane.laneLayout().pathPoints();
        double total = 0.0;
        for (int index = 0; index < points.size() - 1; index++) {
            total += points.get(index).distanceTo(points.get(index + 1));
        }
        return total;
    }

    /** The turtle closest to the boss: the wall grows forward from the last line of defense. */
    private static double rearmostProgress(PlayerLane lane, List<Tower> turtles) {
        double best = -1.0;
        for (Tower turtle : turtles) {
            best = Math.max(best, turtleProgress(lane, turtle));
        }
        return best;
    }

    private static Tower anchorTurtle(PlayerLane lane, List<Tower> turtles, boolean finalDefense) {
        if (finalDefense) {
            return turtles.get(0);
        }
        Tower anchor = turtles.get(0);
        double best = turtleProgress(lane, anchor);
        for (Tower turtle : turtles) {
            double progress = turtleProgress(lane, turtle);
            if (progress > best) {
                best = progress;
                anchor = turtle;
            }
        }
        return anchor;
    }

    /** The best value any turtle on the lane contributes: the strongest turtle sets the wall. */
    private static double strongest(List<Tower> turtles, String key, double fallback) {
        double best = 0.0;
        for (Tower turtle : turtles) {
            best = Math.max(best, TowerBalanceRuntime.ability(turtle.type().id(), key, fallback));
        }
        return best <= 0.0 ? fallback : best;
    }

    public static int zoneCapacity(TowerType type) {
        if (!AtlantisTowers.isTurtle(type)) {
            return 0;
        }
        return Math.max(0, TowerBalanceRuntime.abilityInt(type.id(), "zoneCapacity", AtlantisTowers.tier(type)));
    }

    private static double turtleProgress(PlayerLane lane, Tower turtle) {
        var position = turtle.position();
        if (position == null) {
            return -1.0;
        }
        Vec3 world = new Vec3(position.x() + 0.5, position.y(), position.z() + 0.5);
        return lane.laneLayout().progressAt(world);
    }
}
