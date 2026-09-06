package kim.biryeong.semiontd.augment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterDataKey;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.game.AugmentTelemetrySnapshot.EconomyEvent;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.summon.AllyTimedEffectIncomeSummon;
import kim.biryeong.semiontd.summon.AreaHealIncomeSummon;
import kim.biryeong.semiontd.summon.BasicIncomeSummon;
import kim.biryeong.semiontd.summon.ElderGuardianSummon;
import kim.biryeong.semiontd.summon.SummonMonsterType;
import kim.biryeong.semiontd.summon.UtilitySupportProfile;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import net.minecraft.resources.ResourceLocation;

/** Server-thread quote/commit operations; failed purchases never consume a contract or a ticket. */
public final class AugmentEconomyService {
    public enum Contract {
        NONE(null), FORECAST("forecast_offensive"), CASH("cash_settlement"),
        LOW_PRESSURE("low_pressure_high_yield"), DECISIVE("decisive_delivery");

        final String card;
        Contract(String card) { this.card = card; }
    }

    private static final MonsterDataKey<PurchaseBinding> PURCHASE = MonsterDataKey.of(
            ResourceLocation.fromNamespaceAndPath("semiontd", "augment_purchase"), PurchaseBinding.class);
    private static final MonsterDataKey<UUID> BODY_APPLIED = MonsterDataKey.of(
            ResourceLocation.fromNamespaceAndPath("semiontd", "augment_purchase_body"), UUID.class);

    private AugmentEconomyService() {}

    public static void beginPrepare(SemionPlayer player, int round) {
        AugmentEconomyState state = player.economyAugments();
        if (state.closed || round <= state.prepareRound) { return; }
        expireTickets(player);
        state.prepareRound = round;
        state.preparation = true;
        state.contract = Contract.NONE;
        state.payloadArmed = false;
        state.payloadUsed = false;
        state.usedContracts.clear();
        state.lowPressureRoundIncome = 0;
        state.supportPurchases.clear();
        state.revision++;
    }

    public static void endPrepare(SemionPlayer player, int round) {
        AugmentEconomyState state = player.economyAugments();
        if (!state.preparation || state.prepareRound != round) { return; }
        state.preparation = false;
        state.contract = Contract.NONE;
        state.payloadArmed = false;
        expireTickets(player);
        state.revision++;
    }

    public static void close(SemionPlayer player) {
        AugmentEconomyState state = player.economyAugments();
        if (state.closed) { return; }
        expireTickets(player);
        for (AugmentEconomyState.DeferredIncome pending : state.pendingForecasts()) {
            player.augmentTelemetry().recordEconomy(new EconomyEvent(
                    telemetryRound(player), player.augmentTelemetry().currentTick(), "semiontd:forecast_offensive", "FORECAST_CANCELLED",
                    null, null, null, null, null, null, null, null, 1, List.of("semiontd:forecast_offensive"), -pending.amount()));
        }
        state.closed = true;
        state.preparation = false;
        state.contract = Contract.NONE;
        state.payloadArmed = false;
        state.debt = 0;
        state.repaymentsRemaining = 0;
        state.deferredIncome.clear();
        state.supportPurchases.clear();
        state.revision++;
    }

    public static void onSelected(SemionPlayer player, String cardId, int round, Map<String, Double> parameters) {
        Objects.requireNonNull(cardId, "cardId");
        AugmentEconomyState state = player.economyAugments();
        if (state.closed) { return; }
        String id = shortId(cardId);
        if (!state.selectionReceipts.add(round + ":" + id)) { return; }
        state.parameters.put(id, parameters == null ? Map.of() : Map.copyOf(parameters));
        if (id.equals("emergency_loan")) {
            long advance = Math.min(valueLong(state, id, "advanceCap", 300),
                    floorProduct(player.economy().income(), value(state, id, "advanceMultiplier", 3)));
            state.debt = ceilProduct(advance, value(state, id, "debtMultiplier", 4.0 / 3.0));
            state.repaymentsRemaining = (int) valueLong(state, id, "repaymentCount", 4);
            grantDiamond(player, advance);
            player.augmentTelemetry().recordEconomy(new EconomyEvent(round, player.augmentTelemetry().currentTick(),
                    "semiontd:" + id, "SELECTION_GRANT", null, null, null, null, advance, null, null, null, 1));
        } else if (id.equals("forbidden_blueprint")) {
            state.ticketRound = round;
            state.remainingTickets = (int) valueLong(state, id, "ticketCount", 2);
            player.augmentTelemetry().recordEconomy(new EconomyEvent(round, player.augmentTelemetry().currentTick(),
                    "semiontd:" + id, "TICKET_GRANTED", null, null, null, null, null, null, null, null, state.remainingTickets));
        } else if (id.startsWith("reserve_")) {
            int tier = id.endsWith("_silver") ? 1 : id.endsWith("_gold") ? 2 : 3;
            if (id.startsWith("reserve_diamond")) {
                long amount = valueLong(state, id, "amount", 60L << (tier - 1));
                grantDiamond(player, amount);
                player.augmentTelemetry().recordEconomy(new EconomyEvent(round, player.augmentTelemetry().currentTick(),
                        "semiontd:" + id, "SELECTION_GRANT", null, null, null, null, amount, null, null, null, 1));
            } else if (id.startsWith("reserve_income")) {
                long amount = valueLong(state, id, "amount", 10L << (tier - 1));
                grantIncome(player, amount);
                player.augmentTelemetry().recordEconomy(new EconomyEvent(round, player.augmentTelemetry().currentTick(),
                        "semiontd:" + id, "SELECTION_GRANT", null, null, null, null, null, amount, null, null, 1));
            } else if (id.startsWith("reserve_production")) {
                long amount = valueLong(state, id, "amount", tier);
                player.economy().addAugmentEmeraldProduction(amount);
                player.augmentTelemetry().recordEconomy(new EconomyEvent(round, player.augmentTelemetry().currentTick(),
                        "semiontd:" + id, "SELECTION_GRANT", null, null, null, null, null, null, null, null, Math.toIntExact(amount)));
            }
        }
        state.revision++;
    }

    public static boolean setContract(SemionPlayer player, int round, Contract contract) {
        AugmentEconomyState state = player.economyAugments();
        if (contract == null || !preparing(state, round)) { return false; }
        if (contract != Contract.NONE && (!state.parameters.containsKey(contract.card)
                || state.usedContracts.contains(contract))) { return false; }
        if (contract == Contract.LOW_PRESSURE && state.contract != Contract.NONE
                && state.contract != Contract.LOW_PRESSURE) { return false; }
        state.contract = contract;
        state.revision++;
        return true;
    }

    public static boolean setAdditionalPayload(SemionPlayer player, int round, boolean armed) {
        AugmentEconomyState state = player.economyAugments();
        if (!preparing(state, round) || !state.parameters.containsKey("additional_payload")
                || (armed && state.payloadUsed)) { return false; }
        state.payloadArmed = armed;
        state.revision++;
        return true;
    }

    public static Contract contract(SemionPlayer player) { return player.economyAugments().contract; }
    public static boolean payloadArmed(SemionPlayer player) { return player.economyAugments().payloadArmed; }

    public static PurchasePlan quotePurchase(
            SemionPlayer player, UUID transactionId, int round, boolean preparation, boolean normalPaid,
            boolean utility, boolean standardAttack, boolean attackEligible, long normalEmeraldCost, long normalIncomeGain
    ) {
        return quotePurchase(player, transactionId, round, preparation, normalPaid, utility, standardAttack,
                attackEligible, normalEmeraldCost, normalIncomeGain, null);
    }

    public static PurchasePlan quotePurchase(
            SemionPlayer player, UUID transactionId, int round, boolean preparation, boolean normalPaid,
            boolean utility, boolean standardAttack, boolean attackEligible, long normalEmeraldCost, long normalIncomeGain,
            Contract contractOverride
    ) {
        Objects.requireNonNull(transactionId, "transactionId");
        if (normalEmeraldCost < 0 || normalIncomeGain < 0 || round < 1) {
            throw new IllegalArgumentException("Purchase quote values must be non-negative and have a round.");
        }
        AugmentEconomyState state = player.economyAugments();
        boolean active = preparation && normalPaid && preparing(state, round);
        if (contractOverride != null && (!active || !state.parameters.containsKey("low_pressure_high_yield")
                || (contractOverride != Contract.NONE && contractOverride != Contract.LOW_PRESSURE)
                || (state.contract != Contract.NONE && state.contract != Contract.LOW_PRESSURE))) {
            throw new IllegalArgumentException("A purchase override requires an unlocked preparation low-pressure choice.");
        }
        Contract requested = contractOverride == null ? state.contract : contractOverride;
        Contract contract = active ? eligibleContract(requested, utility, standardAttack, attackEligible, normalIncomeGain) : Contract.NONE;
        if (contractOverride == Contract.LOW_PRESSURE && contract != Contract.LOW_PRESSURE) {
            throw new IllegalArgumentException("This summon cannot use a low-pressure contract.");
        }
        boolean payload = active && utility && state.payloadArmed && !state.payloadUsed;
        long cost = payload ? ceilProduct(normalEmeraldCost, value(state, "additional_payload", "costMultiplier", 1.25)) : normalEmeraldCost;
        long income = normalIncomeGain;
        long instant = 0;
        long deferred = 0;
        long bonus = 0;
        BigDecimal fraction = state.lowPressureFraction;
        double health = payload ? value(state, "additional_payload", "healthMultiplier", 1.35) : 1;
        double attack = 1;
        double support = payload ? value(state, "additional_payload", "supportMultiplier", 1.35) : 1;
        switch (contract) {
            case CASH -> {
                income = 0;
                instant = floorProduct(normalIncomeGain, value(state, contract.card, "diamondMultiplier", 3));
            }
            case FORECAST -> { income = 0; deferred = normalIncomeGain; }
            case LOW_PRESSURE -> {
                BigDecimal raw = fraction.add(BigDecimal.valueOf(normalIncomeGain)
                        .multiply(BigDecimal.valueOf(value(state, contract.card, "bonusRatio", 0.25))));
                long whole = raw.setScale(0, RoundingMode.FLOOR).longValueExact();
                bonus = Math.min(whole, Math.max(0, valueLong(state, contract.card, "roundBonusCap", 12) - state.lowPressureRoundIncome));
                income = Math.addExact(income, bonus);
                fraction = raw.subtract(BigDecimal.valueOf(whole));
                health = attack = value(state, contract.card, "bodyMultiplier", 0.7);
            }
            case DECISIVE -> {
                income = 0;
                health = value(state, contract.card, "healthMultiplier", 1.6);
                attack = value(state, contract.card, "attackMultiplier", 1.4);
            }
            case NONE -> { }
        }
        return new PurchasePlan(player.uuid(), transactionId, state.revision, round, normalPaid, preparation, utility,
                cost, normalIncomeGain, income, instant, deferred, health, attack, support, contract, payload,
                bonus, fraction, active && standardAttack && contract == Contract.NONE && !payload,
                value(state, "forecast_offensive", "echoRatio", 0.3), normalEmeraldCost);
    }

    public static boolean canCommitPurchase(SemionPlayer player, PurchasePlan plan) {
        AugmentEconomyState state = player.economyAugments();
        return !state.closed && player.uuid().equals(plan.playerId()) && state.revision == plan.revision()
                && !state.purchaseReceipts.contains(plan.transactionId())
                && ((plan.contract() == Contract.NONE && !plan.payload()) || preparing(state, plan.round()));
    }

    /** Call only after the paid unit/reservation is successfully registered in the existing lane queue. */
    public static boolean commitPurchase(SemionPlayer player, PurchasePlan plan, Monster original) {
        Objects.requireNonNull(original, "original");
        if (!canCommitPurchase(player, plan)) { return false; }
        AugmentEconomyState state = player.economyAugments();
        state.purchaseReceipts.add(plan.transactionId());
        if (plan.payload()) { state.payloadUsed = true; state.payloadArmed = false; }
        if (plan.contract() != Contract.NONE && plan.contract() != Contract.LOW_PRESSURE) {
            state.usedContracts.add(plan.contract());
            state.contract = Contract.NONE;
        }
        if (plan.contract() == Contract.LOW_PRESSURE) {
            state.lowPressureFraction = plan.lowPressureFraction();
            state.lowPressureRoundIncome += plan.bonusIncome();
        }
        if (plan.contract() == Contract.FORECAST) {
            state.deferredIncome.put(plan.transactionId(), new AugmentEconomyState.DeferredIncome(plan.transactionId(), original.id(),
                    plan.round() + 1, original.targetTeam(), original.targetLaneId(), plan.emeraldCost(), plan.deferredIncome()));
        } else if (plan.contract() == Contract.CASH || plan.contract() == Contract.DECISIVE) {
            state.incomeForgone += plan.normalIncomeGain();
        }
        if (plan.preparation() && plan.normalPaid() && preparing(state, plan.round())
                && plan.utility() && state.parameters.containsKey("support_performance")) {
            state.supportPurchases.put(original.logicalId(), new AugmentEconomyState.SupportProgress(plan.round()));
        }
        original.setData(PURCHASE, new PurchaseBinding(player, plan));
        player.economy().addIncome(plan.incomeGain());
        state.incomeGranted += plan.bonusIncome();
        grantDiamond(player, plan.instantDiamond());
        player.matchStats().recordIncomeGenerated(plan.incomeGain());
        if (plan.payload() || plan.contract() != Contract.NONE) {
            List<String> related = new ArrayList<>();
            if (plan.payload()) { related.add("semiontd:additional_payload"); }
            if (plan.contract() != Contract.NONE) { related.add("semiontd:" + plan.contract().card); }
            boolean forfeited = plan.contract() == Contract.CASH || plan.contract() == Contract.DECISIVE;
            player.augmentTelemetry().recordEconomy(new EconomyEvent(plan.round(), player.augmentTelemetry().currentTick(),
                    related.size() == 1 ? related.getFirst() : null, plan.forecast() ? "FORECAST_RESERVED" : "PURCHASE_COMMIT", null,
                    plan.normalEmeraldCost(), plan.emeraldCost(), null,
                    plan.contract() == Contract.CASH ? plan.instantDiamond() : null,
                    plan.contract() == Contract.LOW_PRESSURE ? plan.bonusIncome() : null,
                    forfeited ? plan.normalIncomeGain() : null, null, 1, related,
                    plan.forecast() ? plan.deferredIncome() : null));
        }
        state.revision++;
        return true;
    }

    public static void applyPurchaseBody(Monster monster, PurchasePlan plan) {
        UUID applied = monster.getData(BODY_APPLIED).orElse(null);
        if (applied != null) {
            if (!applied.equals(plan.transactionId())) { throw new IllegalStateException("Cannot replace an applied purchase body."); }
            return;
        }
        monster.applyAugmentBodyModifiers(plan.healthMultiplier(), plan.attackMultiplier());
        monster.setData(BODY_APPLIED, plan.transactionId());
    }

    /** Invoked at actual spawn, not at reservation or round payout. */
    public static boolean onForecastSpawn(Monster original, int currentRound) {
        PurchaseBinding binding = original.getData(PURCHASE).orElse(null);
        if (binding == null || binding.plan().contract() != Contract.FORECAST || original.origin() != MonsterOrigin.NORMAL_PAID) { return false; }
        AugmentEconomyState state = binding.player().economyAugments();
        AugmentEconomyState.DeferredIncome pending = state.deferredIncome.get(binding.plan().transactionId());
        if (state.closed || pending == null || currentRound < pending.round()) { return false; }
        state.deferredIncome.remove(binding.plan().transactionId());
        binding.player().economy().addIncome(pending.amount());
        binding.player().matchStats().recordIncomeGenerated(pending.amount());
        binding.player().augmentTelemetry().recordEconomy(new EconomyEvent(currentRound,
                binding.player().augmentTelemetry().currentTick(), "semiontd:forecast_offensive", "FORECAST_REALIZED",
                null, null, null, null, null, pending.amount(), null, null, 1, List.of("semiontd:forecast_offensive"), -pending.amount()));
        state.revision++;
        return true;
    }

    public static Monster createForecastEcho(Monster original, double ratio) {
        if (!Double.isFinite(ratio) || ratio <= 0 || ratio > 1) { throw new IllegalArgumentException("Invalid echo ratio."); }
        Monster echo = new Monster(original.id(), original.targetTeam(), original.targetLaneId(), original.ownerPlayer(),
                original.senderTeam(), original.maxHealth() * ratio, original.armor(), original.attackDamage() * ratio,
                original.attackKind(), original.entityTypeId(), original.blockbenchModelId().orElse(null), original.damageType(),
                original.resistance(), original.dimensions(), original.summonTier().orElse(null), List.of(), 0);
        echo.setOrigin(MonsterOrigin.ECHO);
        original.senderName().ifPresent(echo::setSenderName);
        return echo;
    }

    public static double supportMultiplier(Monster monster) {
        return monster.getData(PURCHASE).map(binding -> binding.plan().supportMultiplier()).orElse(1.0);
    }

    public static boolean isLowPressure(Monster monster) {
        return monster.getData(PURCHASE).map(binding -> binding.plan().contract() == Contract.LOW_PRESSURE).orElse(false);
    }

    public static Optional<String> supportProgress(Monster monster) {
        PurchaseBinding binding = monster.getData(PURCHASE).orElse(null);
        if (binding == null) { return Optional.empty(); }
        AugmentEconomyState state = binding.player().economyAugments();
        AugmentEconomyState.SupportProgress progress = state.supportPurchases.get(monster.logicalId());
        if (progress == null || state.closed) { return Optional.empty(); }
        if (state.supportRewardRound == progress.round) { return Optional.of("이번 준비 지원 보상 완료"); }
        long needed = valueLong(state, "support_performance", "targetCount", 3);
        return Optional.of("지원 " + Math.min(needed, progress.targets.size()) + "/" + needed);
    }

    public static boolean recordSupport(Monster source, Monster target, double effectiveAmount) {
        if (!Double.isFinite(effectiveAmount) || effectiveAmount <= 0 || source == target
                || !source.isAlive() || !target.isAlive() || source.origin() != MonsterOrigin.NORMAL_PAID
                || source.targetTeam() != target.targetTeam() || source.targetLaneId() != target.targetLaneId()
                || isLowPressure(target) || !supportTarget(target)) { return false; }
        PurchaseBinding binding = source.getData(PURCHASE).orElse(null);
        if (binding == null) { return false; }
        AugmentEconomyState state = binding.player().economyAugments();
        AugmentEconomyState.SupportProgress progress = state.supportPurchases.get(source.logicalId());
        long cap = valueLong(state, "support_performance", "matchIncomeCap", 8);
        if (state.closed || progress == null || state.supportRewardRound == progress.round || state.supportIncome >= cap) { return false; }
        progress.targets.add(target.logicalId());
        if (progress.targets.size() < valueLong(state, "support_performance", "targetCount", 3)) { return false; }
        long bonus = Math.min(cap - state.supportIncome, valueLong(state, "support_performance", "incomeBonus", 2));
        grantIncome(binding.player(), bonus);
        state.supportIncome += bonus;
        state.supportRewardRound = progress.round;
        binding.player().augmentTelemetry().recordEconomy(new EconomyEvent(telemetryRound(binding.player()),
                binding.player().augmentTelemetry().currentTick(), "semiontd:support_performance", "SUPPORT_REWARD",
                null, null, null, null, null, bonus, null, null, 1));
        state.revision++;
        return true;
    }

    public static Optional<UpgradePlan> quoteUpgrade(SemionPlayer player, UUID transactionId, int round,
            UUID logicalTowerId, boolean eligible, long normalCost, boolean useTicket) {
        if (transactionId == null || logicalTowerId == null || normalCost < 0) { return Optional.empty(); }
        AugmentEconomyState state = player.economyAugments();
        if (state.closed) { return Optional.empty(); }
        if (useTicket && (!eligible || normalCost == 0 || !preparing(state, round) || state.ticketRound != round
                || state.remainingTickets == 0 || state.ticketTargets.contains(logicalTowerId))) { return Optional.empty(); }
        long discount = useTicket ? Math.min(normalCost, valueLong(state, "forbidden_blueprint", "ticketValue", 300)) : 0;
        return Optional.of(new UpgradePlan(player.uuid(), transactionId, state.revision, round, logicalTowerId,
                normalCost - discount, discount, useTicket));
    }

    public static boolean commitUpgrade(SemionPlayer player, UpgradePlan plan) {
        AugmentEconomyState state = player.economyAugments();
        if (state.closed || !player.uuid().equals(plan.playerId()) || state.revision != plan.revision()
                || state.upgradeReceipts.contains(plan.transactionId())) { return false; }
        if (plan.useTicket() && (!preparing(state, plan.round()) || state.ticketRound != plan.round()
                || state.remainingTickets == 0 || state.ticketTargets.contains(plan.logicalTowerId()))) { return false; }
        state.upgradeReceipts.add(plan.transactionId());
        if (plan.useTicket()) {
            state.remainingTickets--;
            state.usedTickets++;
            state.ticketTargets.add(plan.logicalTowerId());
            player.augmentTelemetry().recordEconomy(new EconomyEvent(plan.round(), player.augmentTelemetry().currentTick(),
                    "semiontd:forbidden_blueprint", "TICKET_USED", player.augmentTelemetry().towerRef(plan.logicalTowerId()),
                    null, null, plan.cost(), null, null, null, null, 1));
        }
        state.revision++;
        return true;
    }

    public static long previewPayout(SemionPlayer player, long normalPayout) {
        AugmentEconomyState state = player.economyAugments();
        long payout = adjustedPayout(state, normalPayout, state.usedTickets);
        return payout - Math.min(payout, nextRepayment(state));
    }

    public static long previewPayoutAfterTicket(SemionPlayer player, long normalPayout) {
        AugmentEconomyState state = player.economyAugments();
        long payout = adjustedPayout(state, normalPayout, state.usedTickets + 1);
        return payout - Math.min(payout, nextRepayment(state));
    }

    /** Round-keyed to prevent duplicate payout, with debt charged only on a real eligible payout. */
    public static boolean payRoundIncome(SemionPlayer player, int round) {
        AugmentEconomyState state = player.economyAugments();
        if (state.closed || round <= state.lastPayoutRound) { return false; }
        long normal = player.economy().income();
        long payout = adjustedPayout(state, normal, state.usedTickets);
        long repayment = Math.min(payout, nextRepayment(state));
        state.debt -= repayment;
        if (state.repaymentsRemaining > 0) { state.repaymentsRemaining--; }
        player.economy().addDiamond(payout - repayment);
        state.payoutWithheld += normal - payout + repayment;
        state.lastPayoutRound = round;
        List<String> related = new ArrayList<>();
        if (state.usedTickets > 0) { related.add("semiontd:forbidden_blueprint"); }
        if (state.parameters.containsKey("wartime_economy")) { related.add("semiontd:wartime_economy"); }
        if (repayment > 0) { related.add("semiontd:emergency_loan"); }
        if (state.parameters.containsKey("cash_settlement")) { related.add("semiontd:cash_settlement"); }
        if (!related.isEmpty()) {
            player.augmentTelemetry().recordEconomy(new EconomyEvent(round, player.augmentTelemetry().currentTick(),
                    related.size() == 1 ? related.getFirst() : null, "PERIODIC_PAYOUT", null, null, null, null,
                    payout - repayment, null, null, normal - payout, 1, related));
        }
        if (repayment > 0) {
            player.augmentTelemetry().recordEconomy(new EconomyEvent(round, player.augmentTelemetry().currentTick(),
                    "semiontd:emergency_loan", "DEBT_REPAYMENT", null, null, null, null, null, null, null, repayment, 1));
        }
        state.revision++;
        return true;
    }

    public static boolean isUtility(SummonMonsterType type) {
        return type instanceof AreaHealIncomeSummon || type instanceof AllyTimedEffectIncomeSummon
                || UtilitySupportProfile.supports(type.id());
    }

    public static boolean isStandardAttack(SummonMonsterType type) {
        return type.getClass() == BasicIncomeSummon.class && type.attackDamage() > 0 && !isUtility(type);
    }

    public static boolean isAttackEligible(SummonMonsterType type) {
        return type.attackDamage() > 0 && !isUtility(type) && !(type instanceof ElderGuardianSummon);
    }

    public static Optional<PurchasePlan> previewPurchase(SemionGame game, SemionPlayer player, SummonMonsterType type) {
        return previewPurchase(game, player, type, null);
    }

    public static Optional<PurchasePlan> previewPurchase(SemionGame game, SemionPlayer player, SummonMonsterType type, Contract override) {
        if (game == null || player == null || type == null) { return Optional.empty(); }
        JobContext context = new JobContext(game, player);
        SemionJob job = player.job().orElse(JobRegistry.defaultJob());
        if (!job.canUseSummon(context, type)) { return Optional.empty(); }
        boolean paid = !game.summonsAreFree();
        long cost = paid ? Math.max(0, job.modifySummonGasCost(context, type, type.gasCost())) : 0;
        long gain = paid ? Math.max(0, job.modifySummonIncomeGain(context, type, type.incomeGain())) : 0;
        try {
            return Optional.of(quotePurchase(player, new UUID(0, 0), game.currentRound(),
                    game.phase() == RoundPhase.PREPARE_AND_SUMMON, paid, isUtility(type), isStandardAttack(type),
                    isAttackEligible(type), cost, gain, override));
        } catch (IllegalArgumentException invalidContract) {
            return Optional.empty();
        }
    }

    /** Reuse the real upgrade requirements for the selection-time ticket offer. */
    public static boolean eligibleForCard(SemionGame game, SemionPlayer player, String cardId) {
        if (game == null || player == null || !shortId(cardId).equals("forbidden_blueprint")) { return false; }
        return game.playerLane(player.uuid()).map(lane -> lane.towers().stream().anyMatch(tower ->
                ProductionTowerCatalog.upgrades(tower.type()).stream().anyMatch(upgrade ->
                        ProductionTowerService.eligibleTicketUpgrade(game, player, tower, upgrade)))).orElse(false);
    }

    private static boolean supportTarget(Monster target) {
        if (target.id().equals("warden_boss_15") || target.id().startsWith("wave_healer_")) { return false; }
        if (target.origin() == MonsterOrigin.NATURAL_WAVE) { return true; }
        if (target.origin() != MonsterOrigin.NORMAL_PAID) { return false; }
        return kim.biryeong.semiontd.summon.SummonRegistry.find(target.id())
                .map(AugmentEconomyService::isAttackEligible).orElse(false);
    }

    private static Contract eligibleContract(Contract requested, boolean utility, boolean standardAttack, boolean attack, long gain) {
        return switch (requested) {
            case CASH -> gain > 0 ? requested : Contract.NONE;
            case FORECAST -> attack && !utility ? requested : Contract.NONE;
            case LOW_PRESSURE, DECISIVE -> standardAttack && gain > 0 ? requested : Contract.NONE;
            case NONE -> Contract.NONE;
        };
    }

    private static long adjustedPayout(AugmentEconomyState state, long normal, int usedTickets) {
        BigDecimal multiplier = BigDecimal.valueOf(value(state, "forbidden_blueprint", "payoutMultiplier", 0.9)).pow(usedTickets);
        if (state.parameters.containsKey("wartime_economy")) {
            multiplier = multiplier.multiply(BigDecimal.valueOf(value(state, "wartime_economy", "payoutMultiplier", 0.65)));
        }
        return BigDecimal.valueOf(Math.max(0, normal)).multiply(multiplier).setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    private static long nextRepayment(AugmentEconomyState state) {
        return state.repaymentsRemaining > 0
                ? state.debt / state.repaymentsRemaining + (state.debt % state.repaymentsRemaining == 0 ? 0 : 1)
                : state.debt;
    }

    private static void expireTickets(SemionPlayer player) {
        AugmentEconomyState state = player.economyAugments();
        if (state.remainingTickets == 0) { return; }
        player.augmentTelemetry().recordEconomy(new EconomyEvent(telemetryRound(player), player.augmentTelemetry().currentTick(),
                "semiontd:forbidden_blueprint", "TICKET_EXPIRED", null, null, null, null,
                null, null, null, null, state.remainingTickets));
        state.remainingTickets = 0;
    }

    private static int telemetryRound(SemionPlayer player) {
        Integer current = player.augmentTelemetry().currentRound();
        return current == null ? Math.max(player.economyAugments().prepareRound, player.economyAugments().lastPayoutRound) : current;
    }

    private static boolean preparing(AugmentEconomyState state, int round) { return state.preparation && state.prepareRound == round; }
    private static String shortId(String id) { return id.startsWith("semiontd:") ? id.substring(9) : id; }
    private static double value(AugmentEconomyState state, String id, String key, double fallback) {
        return state.parameters.getOrDefault(id, Map.of()).getOrDefault(key, fallback);
    }
    private static long valueLong(AugmentEconomyState state, String id, String key, long fallback) {
        return (long) value(state, id, key, fallback);
    }
    private static long floorProduct(long amount, double ratio) {
        return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(ratio)).setScale(0, RoundingMode.FLOOR).longValueExact();
    }
    private static long ceilProduct(long amount, double ratio) {
        return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(ratio)).setScale(0, RoundingMode.CEILING).longValueExact();
    }
    private static void grantDiamond(SemionPlayer player, long amount) {
        player.economy().addDiamond(amount);
        player.economyAugments().diamondGranted += amount;
    }
    private static void grantIncome(SemionPlayer player, long amount) {
        player.economy().addIncome(amount);
        player.economyAugments().incomeGranted += amount;
    }

    private record PurchaseBinding(SemionPlayer player, PurchasePlan plan) {}

    public record PurchasePlan(UUID playerId, UUID transactionId, long revision, int round, boolean normalPaid,
            boolean preparation, boolean utility, long emeraldCost, long normalIncomeGain, long incomeGain,
            long instantDiamond, long deferredIncome, double healthMultiplier, double attackMultiplier,
            double supportMultiplier, Contract contract, boolean payload, long bonusIncome,
            BigDecimal lowPressureFraction, boolean ordnanceEligible, double echoRatio, long normalEmeraldCost) {
        public boolean forecast() { return contract == Contract.FORECAST; }
        public int scheduledRound() { return preparation ? round + (forecast() ? 1 : 0) : round + 1; }
    }

    public record UpgradePlan(UUID playerId, UUID transactionId, long revision, int round, UUID logicalTowerId,
            long cost, long discount, boolean useTicket) {}
}
