package kim.biryeong.semiontd.tower.developer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-player runtime state for the 개발자 builder.
 *
 * <p>Only budgets live here — how many patches, hotfixes, maintenances and reproductions the
 * player has left this round, and how many optimisations remain for the whole match. The effects
 * themselves ride on the towers via {@link DeveloperTowerData}.
 *
 * <p>Nothing in this family may write into {@code TowerBalanceRuntime}: that map is shared by every
 * player on the server, so a patch written there would leak across lanes. Budgets are read here and
 * multipliers are applied at query time inside {@link DeveloperTower}.
 */
public final class DeveloperStates {
    private static final Map<UUID, PlayerState> STATES = new HashMap<>();

    private DeveloperStates() {
    }

    public static synchronized PlayerState of(UUID playerId) {
        if (playerId == null) {
            return new PlayerState();
        }
        return STATES.computeIfAbsent(playerId, id -> new PlayerState());
    }

    public static synchronized void clear(UUID playerId) {
        if (playerId != null) {
            STATES.remove(playerId);
        }
    }

    public static synchronized void clearAll() {
        STATES.clear();
    }

    /** Rolls every per-round budget. Called once per round from the job. */
    public static synchronized void openRound(UUID playerId, int round, Capacity capacity) {
        if (playerId == null) {
            return;
        }
        of(playerId).openRound(round, capacity);
    }

    /**
     * What the player's ability towers currently unlock.
     *
     * <p>Recomputed from the lane every round rather than tracked incrementally, so selling an
     * ability tower takes its budget away immediately and rebuying restores it. Slots do not add up
     * across a line: the highest owned 작업대 tier wins.
     */
    public record Capacity(
            int patchSlots,
            int hotfixes,
            boolean maintenanceUnlocked,
            boolean bugsVisible,
            int debugRemovals,
            boolean reproduceUnlocked,
            int versionPinSlots
    ) {
        public static Capacity none() {
            return new Capacity(0, 0, false, false, 0, false, 0);
        }
    }

    public record PendingReproduction(DeveloperTower source, DeveloperBug bug, int round) {
    }

    public static final class PlayerState {
        private int round;
        private Capacity capacity = Capacity.none();

        private int patchesUsed;
        private int hotfixesUsed;
        private int maintenancesUsed;
        private int debugRemovalsUsed;
        private int reproductionsUsed;
        private PendingReproduction pendingReproduction;

        /** Match-wide, deliberately not reset per round. */
        private int optimizationsUsed;

        private PlayerState() {
        }

        void openRound(int newRound, Capacity newCapacity) {
            this.round = newRound;
            this.capacity = newCapacity == null ? Capacity.none() : newCapacity;
            this.patchesUsed = 0;
            this.hotfixesUsed = 0;
            this.maintenancesUsed = 0;
            this.debugRemovalsUsed = 0;
            this.reproductionsUsed = 0;
            this.pendingReproduction = null;
        }

        public int round() {
            return round;
        }

        public Capacity capacity() {
            return capacity;
        }

        /** Lets the dialog reflect a tower sold mid-preparation without waiting for the next round. */
        public void refreshCapacity(Capacity newCapacity) {
            this.capacity = newCapacity == null ? Capacity.none() : newCapacity;
        }

        public int patchesRemaining() {
            return Math.max(0, capacity.patchSlots() - patchesUsed);
        }

        public boolean consumePatch() {
            if (patchesRemaining() <= 0) {
                return false;
            }
            patchesUsed++;
            return true;
        }

        public int hotfixesRemaining() {
            return Math.max(0, capacity.hotfixes() - hotfixesUsed);
        }

        public boolean consumeHotfix() {
            if (hotfixesRemaining() <= 0) {
                return false;
            }
            hotfixesUsed++;
            return true;
        }

        public int maintenancesRemaining() {
            if (!capacity.maintenanceUnlocked()) {
                return 0;
            }
            return Math.max(0, DeveloperBalance.maintenancePerRound() - maintenancesUsed);
        }

        public boolean consumeMaintenance() {
            if (maintenancesRemaining() <= 0) {
                return false;
            }
            maintenancesUsed++;
            return true;
        }

        public int debugRemovalsRemaining() {
            return Math.max(0, capacity.debugRemovals() - debugRemovalsUsed);
        }

        public boolean consumeDebugRemoval() {
            if (debugRemovalsRemaining() <= 0) {
                return false;
            }
            debugRemovalsUsed++;
            return true;
        }

        public int reproductionsRemaining() {
            if (!capacity.reproduceUnlocked()) {
                return 0;
            }
            return Math.max(0, DeveloperBalance.reproducePerRound() - reproductionsUsed);
        }

        public boolean consumeReproduction() {
            if (reproductionsRemaining() <= 0) {
                return false;
            }
            reproductionsUsed++;
            return true;
        }

        public Optional<PendingReproduction> pendingReproduction() {
            return Optional.ofNullable(pendingReproduction);
        }

        public void armReproduction(DeveloperTower source, DeveloperBug bug) {
            pendingReproduction = new PendingReproduction(source, bug, round);
        }

        public void clearPendingReproduction() {
            pendingReproduction = null;
        }

        public int optimizationsRemaining() {
            return Math.max(0, DeveloperBalance.optimizationsPerMatch() - optimizationsUsed);
        }

        public boolean consumeOptimization() {
            if (optimizationsRemaining() <= 0) {
                return false;
            }
            optimizationsUsed++;
            return true;
        }

        public boolean bugsVisible() {
            return capacity.bugsVisible();
        }

        public int versionPinSlots() {
            return capacity.versionPinSlots();
        }
    }
}
