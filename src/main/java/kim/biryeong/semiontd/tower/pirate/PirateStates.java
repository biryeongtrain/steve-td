package kim.biryeong.semiontd.tower.pirate;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;

/** Match-scoped state for pirate spending and income; no mutable job state is used. */
public final class PirateStates {
    private static final ConcurrentMap<UUID, State> STATES = new ConcurrentHashMap<>();
    private PirateStates() { }
    public static void open(SemionGame game, SemionPlayer player) { if (game != null && player != null) STATES.put(player.uuid(), new State(game, player)); }
    public static void close(UUID id) { if (id != null) STATES.remove(id); }
    public static void startRound(UUID id) { State state = STATES.get(id); if (state != null) { state.diamondSpent = 0; state.emeraldSpent = 0; } }
    public static long diamondSpent(UUID id) { State state = STATES.get(id); return state == null ? 0 : state.diamondSpent; }
    public static long emeraldSpent(UUID id) { State state = STATES.get(id); return state == null ? 0 : state.emeraldSpent; }
    public static long admiralProgress(UUID id) { State state = STATES.get(id); return state == null ? 0 : state.admiralRemainder; }
    public static SemionPlayer player(UUID id) { State state = STATES.get(id); return state == null ? null : state.player; }
    public static void recordDiamondSpend(SemionPlayer player, long amount) {
        State state = player == null ? null : STATES.get(player.uuid());
        if (state == null || amount <= 0) return;
        state.diamondSpent += amount;
        int threshold = Math.max(1, TowerBalanceRuntime.abilityInt(PirateTowers.ADMIRAL.id(), "spendThreshold", 200));
        long total = state.admiralRemainder + amount;
        int triggerCount = (int) (total / threshold);
        state.admiralRemainder = total % threshold;
        state.game.playerLane(player.uuid()).ifPresent(lane -> {
            lane.towers().stream().filter(PirateTower.class::isInstance).map(PirateTower.class::cast)
                    .filter(tower -> player.uuid().equals(tower.ownerPlayer()))
                    .forEach(tower -> tower.refreshEconomyStats(lane));
            for (int index = 0; index < triggerCount; index++) PirateTower.triggerAdmiralEffects(lane, player.uuid());
        });
    }
    public static void recordEmeraldSpend(SemionPlayer player, long amount) {
        State state = player == null ? null : STATES.get(player.uuid());
        if (state == null || amount <= 0) return;
        state.emeraldSpent += amount;
        state.game.playerLane(player.uuid()).ifPresent(lane -> lane.towers().stream()
                .filter(PirateTower.class::isInstance).map(PirateTower.class::cast)
                .filter(tower -> player.uuid().equals(tower.ownerPlayer())).forEach(tower -> tower.refreshEconomyStats(lane)));
    }
    /** Awards one payout per owned ferryman; this method deliberately does not recursively invoke itself. */
    public static void grantFerrymanIncome(SemionPlayer player) {
        long bonus = ferrymanIncome(player);
        if (bonus > 0) player.economy().addDiamond(bonus);
    }

    /** Keeps the refund intact and halves only positive direct-sale profit, rounding down. */
    public static void grantFerrymanSaleIncome(SemionPlayer player, long paidMineralCost, long refundAmount) {
        long bonus = ferrymanIncome(player);
        long profit = bonus - (paidMineralCost - refundAmount);
        if (profit > 0) bonus -= profit - profit / 2;
        if (bonus > 0) player.economy().addDiamond(bonus);
    }

    private static long ferrymanIncome(SemionPlayer player) {
        State state = player == null ? null : STATES.get(player.uuid());
        if (state == null) return 0;
        return state.game.playerLane(player.uuid()).map(lane -> lane.towers().stream()
                .filter(tower -> player.uuid().equals(tower.ownerPlayer()) && PirateTowers.isFerryman(tower.type()))
                .mapToLong(tower -> TowerBalanceRuntime.abilityInt(tower.type().id(), "incomeBonus",
                        PirateTowers.matches(tower.type(), PirateTowers.LEGENDARY_FERRYMAN) ? 4
                                : PirateTowers.matches(tower.type(), PirateTowers.VETERAN_FERRYMAN) ? 3 : 2))
                .sum()).orElse(0L);
    }
    private static final class State { private final SemionGame game; private final SemionPlayer player; private long diamondSpent; private long emeraldSpent; private long admiralRemainder; private State(SemionGame game, SemionPlayer player) { this.game = game; this.player = player; } }
}
