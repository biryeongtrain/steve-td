package kim.biryeong.semiontd.tower.army;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player runtime state for the 군대 builder.
 *
 * <p>Only medals live here. Rank is per-tower and rides on {@link ArmyTower} itself, because two
 * towers placed on the same wave can still hold different ranks once a 조교 covers one of them.
 *
 * <p>Medals are the family's long-term growth: the builder does not get stronger by keeping towers
 * alive, it gets stronger by having discharged them. They are stored as a fractional count so the
 * 보급관 bonus can award more than one per discharge without introducing a second currency.
 */
public final class ArmyStates {
    private static final Map<UUID, Double> MEDALS = new HashMap<>();

    private ArmyStates() {
    }

    public static void clear(UUID playerId) {
        if (playerId != null) {
            MEDALS.remove(playerId);
        }
    }

    public static void clearAll() {
        MEDALS.clear();
    }

    /**
     * Awards medals for a completed service.
     *
     * <p>Capped rather than unbounded: without a ceiling a player running 조교 could cycle towers
     * fast enough to make the permanent bonus the only thing that matters.
     */
    public static void awardMedal(UUID playerId, double amount) {
        if (playerId == null || amount <= 0.0) {
            return;
        }
        double cap = ArmyBalance.maxMedals();
        MEDALS.merge(playerId, amount, (a, b) -> Math.min(cap, a + b));
    }

    /** Whole medals earned, for display. */
    public static int medalCount(UUID playerId) {
        return (int) Math.floor(rawMedals(playerId));
    }

    /** Permanent lane-wide damage bonus from medals, as a 0..n fraction. */
    public static double medalBonus(UUID playerId) {
        return rawMedals(playerId) * ArmyBalance.medalDamageBonus();
    }

    private static double rawMedals(UUID playerId) {
        if (playerId == null) {
            return 0.0;
        }
        return Math.min(ArmyBalance.maxMedals(), MEDALS.getOrDefault(playerId, 0.0));
    }
}
