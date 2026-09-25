package kim.biryeong.semiontd.tower.army;

import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

/**
 * Per-player runtime state for the 군대 builder.
 *
 * <p>Medals, retirement snapshots, and free-starter tickets live here. Rank rides on {@link ArmyTower}, because two
 * towers placed on the same wave can still hold different ranks once a 조교 covers one of them.
 *
 * <p>Medals are the family's long-term growth: the builder does not get stronger by keeping towers
 * alive, it gets stronger by having discharged them. They are stored as a fractional count so the
 * 보급관 bonus can award more than one per discharge without introducing a second currency.
 */
public final class ArmyStates {
    private static final Map<UUID, Double> MEDALS = new HashMap<>();
    private static final Map<UUID, AugmentState> AUGMENTS = new HashMap<>();

    private ArmyStates() {
    }

    public static void clear(UUID playerId) {
        if (playerId != null) {
            MEDALS.remove(playerId);
            AUGMENTS.remove(playerId);
        }
    }

    public static void clearAll() {
        MEDALS.clear();
        AUGMENTS.clear();
    }

    static void recordRetirement(ArmyTower tower, Retirement retirement) {
        AugmentState state = AUGMENTS.computeIfAbsent(tower.ownerPlayer(), ignored -> new AugmentState());
        int round = state.round > 0 ? state.round : tower.currentRound();
        state.retirements.addLast(retirement);
        while (state.retirements.size() > 2) state.retirements.removeFirst();
        if (tower.augmentSnapshot().has("job_army_g2") && !state.freeStarter
                && state.ticketRound != round) {
            state.freeStarter = true;
            state.ticketRound = round;
        }
    }

    public static void beginRound(UUID playerId, int round) {
        AUGMENTS.computeIfAbsent(playerId, ignored -> new AugmentState()).round = round;
    }

    public static boolean hasFreeStarter(UUID playerId, TowerType type) {
        AugmentState state = AUGMENTS.get(playerId);
        return state != null && state.freeStarter && ArmyTowers.ranks(type)
                && ProductionTowerCatalog.find(type.id()).map(ProductionTowerCatalog.CatalogEntry::starter).orElse(false);
    }

    public static boolean consumeFreeStarter(UUID playerId, TowerType type) {
        if (!hasFreeStarter(playerId, type)) return false;
        AUGMENTS.get(playerId).freeStarter = false;
        return true;
    }

    public static void spawnReserves(PlayerLane lane, int round) {
        if (lane == null || !lane.augmentSnapshot().has("job_army_p")) return;
        AugmentState state = AUGMENTS.get(lane.ownerPlayer());
        if (state == null || state.reserveRound == round) return;
        state.reserveRound = round;
        for (Retirement retirement : List.copyOf(state.retirements)) {
            TowerType original = retirement.type();
            TowerType reserveType = new TowerType(original.id(), "예비군 " + original.displayName(),
                    original.category(), 0L,
                    retirement.maxHealth() * lane.augmentSnapshot().parameter("job_army_p", "healthRatio", 1.0),
                    original.range(),
                    retirement.attackDamage() * lane.augmentSnapshot().parameter("job_army_p", "damageRatio", 1.5),
                    original.attackIntervalTicks(), original.aggroPriority(), original.description(),
                    original.visual(), List.of(), original.primaryDamageType());
            ArmyTower reserve = new ArmyTower(reserveType, lane.ownerPlayer(), lane.teamId(), lane.laneId(), retirement.position());
            reserve.markTemporaryCopy(null);
            lane.addTower(reserve);
            reserve.markWaveStarted(round, false);
            reserve.onWaveStarted(lane, round);
        }
    }

    static List<Retirement> retirements(UUID playerId) {
        AugmentState state = AUGMENTS.get(playerId);
        return state == null ? List.of() : List.copyOf(state.retirements);
    }

    public record Retirement(TowerType type, GridPosition position, double maxHealth, double attackDamage) {
    }

    private static final class AugmentState {
        private final ArrayDeque<Retirement> retirements = new ArrayDeque<>();
        private int ticketRound = -1;
        private int round;
        private int reserveRound = -1;
        private boolean freeStarter;
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
