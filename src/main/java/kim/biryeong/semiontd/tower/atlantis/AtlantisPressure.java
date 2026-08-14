package kim.biryeong.semiontd.tower.atlantis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;

/**
 * Tracks pressure stacks carried by individual monsters.
 *
 * <p>Stacks are keyed by monster UUID rather than stored on the monster so that the owning tower
 * can be replaced by an upgrade without losing accumulated pressure, and so that a monster leaving
 * the lane cannot strand state on an entity the family no longer sees.
 *
 * <p>Each entry remembers which dolphin owns it, by the tower's original grid position rather than
 * a tower instance: upgrades replace the runtime object but keep that position, and the owning
 * dolphin is the one that releases the pressure when it lapses.
 */
public final class AtlantisPressure {
    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();

    private AtlantisPressure() {
    }

    /** Pressure carried by one monster, attributed to the player whose dolphins applied it. */
    private static final class Entry {
        private final UUID ownerPlayer;
        private GridPosition sourceTower;
        private int stacks;
        private int remainingTicks;
        private double sourceDamage;
        private boolean insideZone;

        private Entry(UUID ownerPlayer, GridPosition sourceTower) {
            this.ownerPlayer = ownerPlayer;
            this.sourceTower = sourceTower;
        }
    }

    public static void clearAll() {
        ENTRIES.clear();
    }

    public static void clearPlayer(UUID playerId) {
        if (playerId != null) {
            ENTRIES.values().removeIf(entry -> playerId.equals(entry.ownerPlayer));
        }
    }

    public static int stacks(UUID monsterId) {
        Entry entry = ENTRIES.get(monsterId);
        return entry == null ? 0 : entry.stacks;
    }

    public static boolean insideZone(UUID monsterId) {
        Entry entry = ENTRIES.get(monsterId);
        return entry != null && entry.insideZone;
    }

    public static UUID ownerOf(UUID monsterId) {
        Entry entry = ENTRIES.get(monsterId);
        return entry == null ? null : entry.ownerPlayer;
    }

    /**
     * Monsters whose pressure is owned by the dolphin standing at {@code sourceTower}.
     *
     * <p>Returned as a copy so the caller can detonate while iterating, which removes entries.
     */
    public static List<UUID> monstersFrom(GridPosition sourceTower) {
        if (sourceTower == null) {
            return List.of();
        }
        List<UUID> carried = new ArrayList<>();
        for (Map.Entry<UUID, Entry> entry : ENTRIES.entrySet()) {
            if (sourceTower.equals(entry.getValue().sourceTower)) {
                carried.add(entry.getKey());
            }
        }
        return carried;
    }

    /**
     * Adds pressure to a monster and refreshes its duration.
     *
     * @param sourceTower  grid position of the applying dolphin, used to decide which tower
     *                     releases the pressure when it lapses
     * @param sourceDamage the applying tower's damage, used later to size the burst
     * @param maxStacks    the stack ceiling including conduit bonuses
     * @return the resulting stack count
     */
    public static int addStacks(
            UUID monsterId,
            UUID ownerPlayer,
            GridPosition sourceTower,
            int amount,
            double sourceDamage,
            int maxStacks,
            int durationTicks
    ) {
        if (monsterId == null || ownerPlayer == null || amount <= 0) {
            return stacks(monsterId);
        }
        Entry entry = ENTRIES.computeIfAbsent(monsterId, ignored -> new Entry(ownerPlayer, sourceTower));
        entry.stacks = Math.min(Math.max(1, maxStacks), entry.stacks + amount);
        entry.remainingTicks = Math.max(entry.remainingTicks, durationTicks);
        // The hardest hitter owns the pressure: the burst is sized from its damage, so the tower
        // that released it should be the same one the damage is attributed to.
        if (sourceDamage > entry.sourceDamage) {
            entry.sourceDamage = sourceDamage;
            entry.sourceTower = sourceTower;
        }
        return entry.stacks;
    }

    public static void markZoneState(UUID monsterId, boolean insideZone) {
        Entry entry = ENTRIES.get(monsterId);
        if (entry != null) {
            entry.insideZone = insideZone;
        }
    }

    /** Decrements the duration and reports whether the pressure expired this tick. */
    public static boolean tickExpired(UUID monsterId, int elapsedTicks) {
        Entry entry = ENTRIES.get(monsterId);
        if (entry == null) {
            return false;
        }
        entry.remainingTicks -= Math.max(1, elapsedTicks);
        return entry.remainingTicks <= 0;
    }

    /**
     * Removes and returns the water pressure burst damage for a monster, or {@code 0} when it
     * carries no pressure. The caller is responsible for routing the damage through the shared
     * pipeline.
     */
    public static double consumeForBurst(UUID monsterId, double ratioBonus) {
        Entry entry = ENTRIES.remove(monsterId);
        if (entry == null || entry.stacks <= 0) {
            return 0.0;
        }
        return burstDamage(entry.sourceDamage, entry.stacks, ratioBonus);
    }

    public static void remove(UUID monsterId) {
        ENTRIES.remove(monsterId);
    }

    /**
     * {@code damage x min(stacks x (ratio + bonus), cap)}.
     *
     * <p>Extracted so unit tests can assert the ceiling without a live world.
     */
    public static double burstDamage(double sourceDamage, int stacks, double ratioBonus) {
        if (sourceDamage <= 0.0 || stacks <= 0) {
            return 0.0;
        }
        double ratio = AtlantisBalance.waterPressureDamageRatio() + Math.max(0.0, ratioBonus);
        double multiplier = Math.min(stacks * ratio, AtlantisBalance.waterPressureDamageCap());
        return sourceDamage * multiplier;
    }

    /** Movement speed reduction produced by the given stack count, clamped by {@code maxSlow}. */
    public static double slowMagnitude(int stacks) {
        if (stacks <= 0) {
            return 0.0;
        }
        return Math.min(stacks * AtlantisBalance.slowPerStack(), AtlantisBalance.maxSlow());
    }

    /**
     * Guards the death chain. A monster may only burst once per chain, which bounds the
     * cascade regardless of how densely monsters are packed.
     */
    public static final class Chain {
        private final Set<UUID> burst = new HashSet<>();
        private int depth;

        public boolean canBurst(UUID monsterId) {
            return depth < AtlantisBalance.maxChainDepth() && burst.add(monsterId);
        }

        public void enter() {
            depth++;
        }

        public void exit() {
            depth--;
        }
    }
}
