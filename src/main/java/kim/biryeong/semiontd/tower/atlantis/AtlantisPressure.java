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
 * <p>Stacks are keyed by owner and monster UUID rather than stored on the monster so that the
 * owning tower can be replaced by an upgrade without losing accumulated pressure, while different
 * players can build pressure on the same final-defense target independently.
 *
 * <p>Each entry remembers which dolphin owns it, by the tower's original grid position rather than
 * a tower instance: upgrades replace the runtime object but keep that position, and the owning
 * dolphin is the one that releases the pressure when it lapses.
 */
public final class AtlantisPressure {
    private static final Map<Key, Entry> ENTRIES = new HashMap<>();

    private AtlantisPressure() {
    }

    private record Key(UUID ownerPlayer, UUID monsterId) {
    }

    /** Pressure carried by one monster for one player. */
    private static final class Entry {
        private GridPosition sourceTower;
        private int stacks;
        private int remainingTicks;
        private double sourceDamage;
        private boolean insideZone;

        private Entry(GridPosition sourceTower) {
            this.sourceTower = sourceTower;
        }
    }

    public static void clearAll() {
        ENTRIES.clear();
    }

    public static void clearPlayer(UUID playerId) {
        if (playerId != null) {
            ENTRIES.keySet().removeIf(key -> playerId.equals(key.ownerPlayer()));
        }
    }

    public static int stacks(UUID ownerPlayer, UUID monsterId) {
        Entry entry = entry(ownerPlayer, monsterId);
        return entry == null ? 0 : entry.stacks;
    }

    public static boolean insideZone(UUID ownerPlayer, UUID monsterId) {
        Entry entry = entry(ownerPlayer, monsterId);
        return entry != null && entry.insideZone;
    }

    public static GridPosition sourceTower(UUID ownerPlayer, UUID monsterId) {
        Entry entry = entry(ownerPlayer, monsterId);
        return entry == null ? null : entry.sourceTower;
    }

    /**
     * Monsters whose pressure is owned by the dolphin standing at {@code sourceTower}.
     *
     * <p>Returned as a copy so the caller can detonate while iterating, which removes entries.
     */
    public static List<UUID> monstersFrom(UUID ownerPlayer, GridPosition sourceTower) {
        if (ownerPlayer == null || sourceTower == null) {
            return List.of();
        }
        List<UUID> carried = new ArrayList<>();
        for (Map.Entry<Key, Entry> entry : ENTRIES.entrySet()) {
            if (ownerPlayer.equals(entry.getKey().ownerPlayer())
                    && sourceTower.equals(entry.getValue().sourceTower)) {
                carried.add(entry.getKey().monsterId());
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
            return stacks(ownerPlayer, monsterId);
        }
        Entry entry = ENTRIES.computeIfAbsent(new Key(ownerPlayer, monsterId), ignored -> new Entry(sourceTower));
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

    public static void markZoneState(UUID ownerPlayer, UUID monsterId, boolean insideZone) {
        Entry entry = entry(ownerPlayer, monsterId);
        if (entry != null) {
            entry.insideZone = insideZone;
        }
    }

    /** Decrements the duration and reports whether the pressure expired this tick. */
    public static boolean tickExpired(UUID ownerPlayer, UUID monsterId, int elapsedTicks) {
        Entry entry = entry(ownerPlayer, monsterId);
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
    public static double consumeForBurst(UUID ownerPlayer, UUID monsterId, double ratioBonus) {
        Entry entry = ownerPlayer == null || monsterId == null
                ? null
                : ENTRIES.remove(new Key(ownerPlayer, monsterId));
        if (entry == null || entry.stacks <= 0) {
            return 0.0;
        }
        return burstDamage(entry.sourceDamage, entry.stacks, ratioBonus);
    }

    public static void remove(UUID ownerPlayer, UUID monsterId) {
        if (ownerPlayer != null && monsterId != null) {
            ENTRIES.remove(new Key(ownerPlayer, monsterId));
        }
    }

    private static Entry entry(UUID ownerPlayer, UUID monsterId) {
        return ownerPlayer == null || monsterId == null
                ? null
                : ENTRIES.get(new Key(ownerPlayer, monsterId));
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
