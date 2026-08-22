package kim.biryeong.semiontd.tower.developer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Per-tower developer state, stored in the typed tower data map.
 *
 * <p>Everything a patch, bug or optimisation writes lives here rather than in fields, because
 * {@code Tower.copyFrom} calls {@code copyDataFrom} on upgrade and copies the whole map. That means
 * a tower carries its patches and defects across 알파 → 베타 → 정식판 with no bespoke
 * {@code copyRuntimeStateFrom} at all. Anything that belongs only to the current wave — timers,
 * cached targets — stays a plain field on {@link DeveloperTower} instead.
 *
 * <p>Patches are stored as a resolved <em>amount</em> alongside a count rather than as a bare
 * count. The amount has to be baked in at application time because the same count means different
 * things depending on where it came from: a hotfix into an LTS is worth 1.5x a reviewed patch, and
 * a patch into 정식판 is worth 1.4x one into 알파. Storing only the count would lose that. It also
 * gives the honest semantics for a patch — what was shipped is what the tower keeps, even if the
 * balance file is retuned later.
 *
 * <p>Sets are persisted as comma separated keys rather than collections: the data map casts values
 * through {@code Class.isInstance}, and a raw {@code List} would erase to something it cannot
 * safely round-trip.
 */
public final class DeveloperTowerData {
    private static final Map<DeveloperPatch, TowerDataKey<Double>> ACTIVE_AMOUNT =
            new EnumMap<>(DeveloperPatch.class);
    private static final Map<DeveloperPatch, TowerDataKey<Integer>> ACTIVE_COUNT =
            new EnumMap<>(DeveloperPatch.class);
    private static final Map<DeveloperPatch, TowerDataKey<Double>> PENDING_AMOUNT =
            new EnumMap<>(DeveloperPatch.class);
    private static final Map<DeveloperPatch, TowerDataKey<Integer>> PENDING_COUNT =
            new EnumMap<>(DeveloperPatch.class);

    /** Hotfix stacks. Never rises on an LTS. */
    public static final TowerDataKey<Integer> INSTABILITY = key("instability", Integer.class);

    public static final TowerDataKey<String> BUGS = key("bugs", String.class);
    public static final TowerDataKey<String> OPTIMIZATIONS = key("optimizations", String.class);
    public static final TowerDataKey<Boolean> VERSION_PINNED = key("version_pinned", Boolean.class);

    /** Round the tower is sitting out for 긴급 점검. Zero when it is not under maintenance. */
    public static final TowerDataKey<Integer> MAINTENANCE_ROUND = key("maintenance_round", Integer.class);

    /** Round the post-maintenance damage bonus applies to. */
    public static final TowerDataKey<Integer> MAINTENANCE_BONUS_ROUND = key("maintenance_bonus_round", Integer.class);

    /** Rounds of 메모리 누수 accumulation. Cleared by a maintenance. */
    public static final TowerDataKey<Integer> LEAK_ROUNDS = key("leak_rounds", Integer.class);

    /** Monster id 하드코딩 latched onto. */
    public static final TowerDataKey<String> HARDCODED_TYPE = key("hardcoded_type", String.class);

    static {
        for (DeveloperPatch patch : DeveloperPatch.values()) {
            ACTIVE_AMOUNT.put(patch, key("patch_amount_" + patch.key(), Double.class));
            ACTIVE_COUNT.put(patch, key("patch_count_" + patch.key(), Integer.class));
            PENDING_AMOUNT.put(patch, key("pending_amount_" + patch.key(), Double.class));
            PENDING_COUNT.put(patch, key("pending_count_" + patch.key(), Integer.class));
        }
    }

    private DeveloperTowerData() {
    }

    private static <T> TowerDataKey<T> key(String path, Class<T> type) {
        return TowerDataKey.of(
                ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "developer/" + path),
                type
        );
    }

    // ------------------------------------------------------------------ 패치

    /** Resolved effect of every patch of this kind that has already taken hold. */
    public static double activeAmount(Tower tower, DeveloperPatch patch) {
        return tower == null ? 0.0 : Math.max(0.0, tower.getDataOrDefault(ACTIVE_AMOUNT.get(patch), 0.0));
    }

    public static int activeCount(Tower tower, DeveloperPatch patch) {
        return tower == null ? 0 : Math.max(0, tower.getDataOrDefault(ACTIVE_COUNT.get(patch), 0));
    }

    public static double pendingAmount(Tower tower, DeveloperPatch patch) {
        return tower == null ? 0.0 : Math.max(0.0, tower.getDataOrDefault(PENDING_AMOUNT.get(patch), 0.0));
    }

    public static int pendingCount(Tower tower, DeveloperPatch patch) {
        return tower == null ? 0 : Math.max(0, tower.getDataOrDefault(PENDING_COUNT.get(patch), 0));
    }

    /**
     * How many patches of this kind already exist, counting ones still waiting to deploy.
     *
     * <p>Pending patches count toward the diminishing curve so queuing three attack patches in one
     * round is worth the same as spreading them over three rounds.
     */
    public static int effectiveCount(Tower tower, DeveloperPatch patch) {
        return activeCount(tower, patch) + pendingCount(tower, patch);
    }

    public static int totalActiveCount(Tower tower) {
        int total = 0;
        for (DeveloperPatch patch : DeveloperPatch.values()) {
            total += activeCount(tower, patch);
        }
        return total;
    }

    /** A hotfix takes effect this round, so it goes straight into the active bucket. */
    public static void addActivePatch(Tower tower, DeveloperPatch patch, double amount) {
        if (tower == null || amount <= 0.0) {
            return;
        }
        tower.setData(ACTIVE_AMOUNT.get(patch), activeAmount(tower, patch) + amount);
        tower.setData(ACTIVE_COUNT.get(patch), activeCount(tower, patch) + 1);
    }

    /** A reviewed patch waits until the next wave starts. */
    public static void addPendingPatch(Tower tower, DeveloperPatch patch, double amount) {
        if (tower == null || amount <= 0.0) {
            return;
        }
        tower.setData(PENDING_AMOUNT.get(patch), pendingAmount(tower, patch) + amount);
        tower.setData(PENDING_COUNT.get(patch), pendingCount(tower, patch) + 1);
    }

    /**
     * Moves every pending patch into the active bucket.
     *
     * @return true when anything actually moved, so the caller can skip a stat resync
     */
    public static boolean promotePendingPatches(Tower tower) {
        if (tower == null) {
            return false;
        }
        boolean promoted = false;
        for (DeveloperPatch patch : DeveloperPatch.values()) {
            int pending = pendingCount(tower, patch);
            if (pending <= 0) {
                continue;
            }
            tower.setData(ACTIVE_AMOUNT.get(patch), activeAmount(tower, patch) + pendingAmount(tower, patch));
            tower.setData(ACTIVE_COUNT.get(patch), activeCount(tower, patch) + pending);
            tower.removeData(PENDING_AMOUNT.get(patch));
            tower.removeData(PENDING_COUNT.get(patch));
            promoted = true;
        }
        return promoted;
    }

    /**
     * Drops one active patch, taking it from the kind the tower has most of.
     *
     * <p>Used by 가비지 컬렉션. The amount removed is the running average for that kind rather than
     * the exact last increment — the individual increments are not kept, and averaging keeps the
     * loss proportional instead of silently erasing a single expensive aggro patch.
     */
    public static boolean dropOneActivePatch(Tower tower) {
        if (tower == null) {
            return false;
        }
        DeveloperPatch largest = null;
        int largestCount = 0;
        for (DeveloperPatch patch : DeveloperPatch.values()) {
            int count = activeCount(tower, patch);
            if (count > largestCount) {
                largest = patch;
                largestCount = count;
            }
        }
        if (largest == null) {
            return false;
        }
        double amount = activeAmount(tower, largest);
        if (largestCount <= 1) {
            tower.removeData(ACTIVE_AMOUNT.get(largest));
            tower.removeData(ACTIVE_COUNT.get(largest));
            return true;
        }
        tower.setData(ACTIVE_AMOUNT.get(largest), Math.max(0.0, amount - amount / largestCount));
        tower.setData(ACTIVE_COUNT.get(largest), largestCount - 1);
        return true;
    }

    // ------------------------------------------------------------------ 불안정

    public static int instability(Tower tower) {
        return tower == null ? 0 : Math.max(0, tower.getDataOrDefault(INSTABILITY, 0));
    }

    /** LTS is immune, which is the entire reason to run one as a hotfix sink. */
    public static void addInstability(Tower tower, int amount) {
        if (tower == null || amount <= 0 || DeveloperTowers.isLts(tower.type())) {
            return;
        }
        tower.setData(INSTABILITY, Math.min(DeveloperBalance.maxInstability(), instability(tower) + amount));
    }

    public static void clearInstability(Tower tower) {
        if (tower != null) {
            tower.removeData(INSTABILITY);
        }
    }

    // ------------------------------------------------------------------ 버그

    public static Set<DeveloperBug> bugs(Tower tower) {
        Set<DeveloperBug> bugs = new LinkedHashSet<>();
        String encoded = tower == null ? "" : tower.getDataOrDefault(BUGS, "");
        if (encoded == null || encoded.isBlank()) {
            return bugs;
        }
        for (String token : encoded.split(",")) {
            DeveloperBug.fromKey(token).ifPresent(bugs::add);
        }
        return bugs;
    }

    public static boolean hasBug(Tower tower, DeveloperBug bug) {
        return bug != null && bugs(tower).contains(bug);
    }

    public static boolean addBug(Tower tower, DeveloperBug bug) {
        if (tower == null || bug == null) {
            return false;
        }
        Set<DeveloperBug> current = bugs(tower);
        if (current.contains(bug) || current.size() >= DeveloperBalance.maxBugsPerTower()) {
            return false;
        }
        current.add(bug);
        tower.setData(BUGS, encode(current, DeveloperBug::key));
        return true;
    }

    public static boolean removeBug(Tower tower, DeveloperBug bug) {
        if (tower == null || bug == null) {
            return false;
        }
        Set<DeveloperBug> current = bugs(tower);
        if (!current.remove(bug)) {
            return false;
        }
        if (current.isEmpty()) {
            tower.removeData(BUGS);
        } else {
            tower.setData(BUGS, encode(current, DeveloperBug::key));
        }
        return true;
    }

    // ------------------------------------------------------------------ 최적화

    public static Set<DeveloperOptimization> optimizations(Tower tower) {
        Set<DeveloperOptimization> result = new LinkedHashSet<>();
        String encoded = tower == null ? "" : tower.getDataOrDefault(OPTIMIZATIONS, "");
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        for (String token : encoded.split(",")) {
            DeveloperOptimization.fromKey(token).ifPresent(result::add);
        }
        return result;
    }

    public static boolean hasOptimization(Tower tower, DeveloperOptimization optimization) {
        return optimization != null && optimizations(tower).contains(optimization);
    }

    /** 읽기 전용 locks the tower out of optimisation entirely, which is checked here. */
    public static boolean addOptimization(Tower tower, DeveloperOptimization optimization) {
        if (tower == null || optimization == null || hasBug(tower, DeveloperBug.READ_ONLY)) {
            return false;
        }
        Set<DeveloperOptimization> current = optimizations(tower);
        if (!current.add(optimization)) {
            return false;
        }
        tower.setData(OPTIMIZATIONS, encode(current, DeveloperOptimization::key));
        return true;
    }

    // ------------------------------------------------------------------ 버전 고정

    public static boolean isPinned(Tower tower) {
        return tower != null && Boolean.TRUE.equals(tower.getDataOrDefault(VERSION_PINNED, Boolean.FALSE));
    }

    public static void setPinned(Tower tower, boolean pinned) {
        if (tower == null) {
            return;
        }
        if (pinned) {
            tower.setData(VERSION_PINNED, Boolean.TRUE);
        } else {
            tower.removeData(VERSION_PINNED);
        }
    }

    // ------------------------------------------------------------------ 긴급 점검

    public static boolean underMaintenance(Tower tower, int round) {
        return tower != null && round > 0 && tower.getDataOrDefault(MAINTENANCE_ROUND, 0) == round;
    }

    public static boolean hasMaintenanceBonus(Tower tower, int round) {
        return tower != null && round > 0 && tower.getDataOrDefault(MAINTENANCE_BONUS_ROUND, 0) == round;
    }

    /**
     * Takes the tower offline for {@code round} and schedules its payoff for the round after.
     *
     * <p>Instability and 메모리 누수 are wiped here, which is what turns a rolling restart across
     * two towers into a real rotation rather than a penalty.
     */
    public static void scheduleMaintenance(Tower tower, int round) {
        if (tower == null || round <= 0) {
            return;
        }
        tower.setData(MAINTENANCE_ROUND, round);
        tower.setData(MAINTENANCE_BONUS_ROUND, round + 1);
        clearInstability(tower);
        tower.removeData(LEAK_ROUNDS);
    }

    // ------------------------------------------------------------------ 기타

    public static int leakRounds(Tower tower) {
        return tower == null ? 0 : Math.max(0, tower.getDataOrDefault(LEAK_ROUNDS, 0));
    }

    public static void advanceLeak(Tower tower) {
        if (tower != null) {
            tower.setData(LEAK_ROUNDS, leakRounds(tower) + 1);
        }
    }

    public static String hardcodedType(Tower tower) {
        return tower == null ? "" : tower.getDataOrDefault(HARDCODED_TYPE, "");
    }

    /** Latches on the first monster this tower ever hits and never changes afterwards. */
    public static void latchHardcodedType(Tower tower, String monsterId) {
        if (tower == null || monsterId == null || monsterId.isBlank() || !hardcodedType(tower).isEmpty()) {
            return;
        }
        tower.setData(HARDCODED_TYPE, monsterId.trim().toLowerCase(Locale.ROOT));
    }

    private static <T> String encode(Set<T> values, java.util.function.Function<T, String> keyOf) {
        List<String> keys = new ArrayList<>(values.size());
        values.forEach(value -> keys.add(keyOf.apply(value)));
        return String.join(",", keys);
    }
}
