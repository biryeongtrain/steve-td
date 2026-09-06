package kim.biryeong.semiontd.augment;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.Comparator;
import kim.biryeong.semiontd.game.TeamId;

/** Match-local state owned by one SemionPlayer, including reconnect-safe transaction receipts. */
public final class AugmentEconomyState {
    final Map<String, Map<String, Double>> parameters = new HashMap<>();
    final Set<String> selectionReceipts = new HashSet<>();
    final Set<UUID> purchaseReceipts = new HashSet<>();
    final Set<UUID> upgradeReceipts = new HashSet<>();
    final Set<UUID> ticketTargets = new HashSet<>();
    final Map<UUID, DeferredIncome> deferredIncome = new HashMap<>();
    final Map<UUID, SupportProgress> supportPurchases = new HashMap<>();
    final EnumSet<AugmentEconomyService.Contract> usedContracts = EnumSet.noneOf(AugmentEconomyService.Contract.class);
    AugmentEconomyService.Contract contract = AugmentEconomyService.Contract.NONE;
    long revision;
    int prepareRound;
    boolean preparation;
    boolean closed;
    boolean payloadArmed;
    boolean payloadUsed;
    long debt;
    int repaymentsRemaining;
    int ticketRound;
    int remainingTickets;
    int usedTickets;
    BigDecimal lowPressureFraction = BigDecimal.ZERO;
    long lowPressureRoundIncome;
    long supportIncome;
    int supportRewardRound;
    int lastPayoutRound;
    long diamondGranted;
    long incomeGranted;
    long incomeForgone;
    long payoutWithheld;

    public long revision() { return revision; }
    public long debt() { return debt; }
    public int repaymentsRemaining() { return repaymentsRemaining; }
    public int remainingTickets() { return remainingTickets; }
    public int usedTickets() { return usedTickets; }
    public double lowPressureFraction() { return lowPressureFraction.doubleValue(); }
    public long lowPressureRoundIncome() { return lowPressureRoundIncome; }
    public long supportIncome() { return supportIncome; }
    public int supportCount(UUID sourceId) {
        SupportProgress progress = supportPurchases.get(sourceId);
        return progress == null ? 0 : progress.targets.size();
    }
    public long diamondGranted() { return diamondGranted; }
    public long incomeGranted() { return incomeGranted; }
    public long incomeForgone() { return incomeForgone; }
    public long payoutWithheld() { return payoutWithheld; }
    public List<DeferredIncome> pendingForecasts() {
        return deferredIncome.values().stream().sorted(Comparator.comparingInt(DeferredIncome::round)
                .thenComparing(DeferredIncome::transactionId)).toList();
    }

    public record DeferredIncome(UUID transactionId, String summonId, int round, TeamId targetTeam,
            int targetLaneId, long paidEmerald, long amount) {}

    static final class SupportProgress {
        final int round;
        final Set<UUID> targets = new HashSet<>();

        SupportProgress(int round) { this.round = round; }
    }
}
