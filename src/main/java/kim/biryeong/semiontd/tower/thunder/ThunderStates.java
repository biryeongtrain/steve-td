package kim.biryeong.semiontd.tower.thunder;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Per-player runtime state for the 람쥐썬더 builder.
 *
 * <p>Only one thing outlives an individual tower instance: the storm rod's output roll. It is
 * shared by every storm rod the player owns because a thunderstorm covers the whole lane, and it
 * has to survive the rod being upgraded mid-wave.
 *
 * <p>Grounding marks deliberately are <em>not</em> tracked here. They are applied through
 * {@code MONSTER_TOWER_DAMAGE_TAKEN_BONUS} timed effects on the monster itself, which already
 * handles stacking, expiry and cleanup.
 */
public final class ThunderStates {
    private static final Map<UUID, Double> STORM_ROLL = new HashMap<>();

    /**
     * Last round each player rolled for.
     *
     * <p>Tracked here rather than read off the lane because the forecast is a player-level fact and
     * {@code runtimeDetailLines()} has no round argument to work with.
     */
    private static final Map<UUID, Integer> LAST_ROUND = new HashMap<>();

    private static final Random RANDOM = new Random();

    private ThunderStates() {
    }

    public static void clear(UUID playerId) {
        if (playerId != null) {
            STORM_ROLL.remove(playerId);
            LAST_ROUND.remove(playerId);
        }
    }

    public static void clearAll() {
        STORM_ROLL.clear();
        LAST_ROUND.clear();
    }

    /** Round this player last rolled for, 0 before the first wave. */
    public static int currentRound(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        return LAST_ROUND.getOrDefault(playerId, 0);
    }

    /** Waves remaining until this player's next guaranteed storm. */
    public static int wavesUntilStorm(UUID playerId) {
        return wavesUntilStorm(currentRound(playerId));
    }

    /** True when this round guarantees the storm rod its maximum output. */
    public static boolean isStormWave(int round) {
        int interval = ThunderBalance.stormWaveInterval();
        return round > 0 && round % interval == 0;
    }

    /** Waves remaining until the next guaranteed storm, 0 when this round is one. */
    public static int wavesUntilStorm(int round) {
        int interval = ThunderBalance.stormWaveInterval();
        if (round <= 0) {
            return interval;
        }
        int remainder = round % interval;
        return remainder == 0 ? 0 : interval - remainder;
    }

    /** Rolls this round's shared storm output. Storm waves are pinned to the maximum. */
    public static void rollStorm(UUID playerId, int round) {
        rollStorm(playerId, round, isStormWave(round) ? 1.0 : RANDOM.nextDouble());
    }

    /** Explicit-roll overload so tests can pin the outcome. */
    public static void rollStorm(UUID playerId, int round, double roll) {
        if (playerId == null) {
            return;
        }
        LAST_ROUND.put(playerId, Math.max(0, round));
        STORM_ROLL.put(playerId, isStormWave(round) ? 1.0 : clamp01(roll));
    }

    /**
     * This round's roll as a 0..1 fraction between the rod's minimum and maximum output.
     *
     * <p>Defaults to the midpoint before the first roll so a snapshot taken during the build phase
     * does not advertise a grid the player will never actually see.
     */
    public static double stormRoll(UUID playerId) {
        if (playerId == null) {
            return 0.5;
        }
        return STORM_ROLL.getOrDefault(playerId, 0.5);
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
