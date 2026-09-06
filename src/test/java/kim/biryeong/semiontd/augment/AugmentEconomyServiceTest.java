package kim.biryeong.semiontd.augment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.game.EconomyService;
import kim.biryeong.semiontd.game.AugmentTelemetrySnapshot.EconomyEvent;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionTeam;
import kim.biryeong.semiontd.game.TeamId;
import org.junit.jupiter.api.Test;

final class AugmentEconomyServiceTest {
    @Test
    void loanRepaysOnlyAtRealPayoutAndDuplicateRoundDoesNotPayTwice() {
        SemionPlayer player = player(80);
        select(player, "emergency_loan", 5);
        assertEquals(240, player.economy().diamond());
        assertEquals(320, player.economyAugments().debt());
        assertEquals(0, AugmentEconomyService.previewPayout(player, 80));
        for (int round = 5; round <= 8; round++) {
            assertTrue(AugmentEconomyService.payRoundIncome(player, round));
            assertFalse(AugmentEconomyService.payRoundIncome(player, round));
        }
        assertEquals(0, player.economyAugments().debt());
        assertEquals(240, player.economy().diamond());
        assertTrue(AugmentEconomyService.payRoundIncome(player, 9));
        assertEquals(320, player.economy().diamond());
    }

    @Test
    void loanInsufficientPayoutCarriesDebtPastFourPaymentsWithoutNegativeBalance() {
        SemionPlayer player = player(10);
        select(player, "emergency_loan", 5);
        player.economy().overrideStartingValues(30, 1_000, 5, 0);
        for (int round = 5; round <= 8; round++) { AugmentEconomyService.payRoundIncome(player, round); }
        assertEquals(20, player.economyAugments().debt());
        assertEquals(0, player.economyAugments().repaymentsRemaining());
        assertEquals(30, player.economy().diamond());
        for (int round = 9; round <= 12; round++) { AugmentEconomyService.payRoundIncome(player, round); }
        assertEquals(0, player.economyAugments().debt());
        assertEquals(30, player.economy().diamond());
    }

    @Test
    void allNineReserveRewardsApplyOncePerMilestoneAndCanRepeatLater() {
        String[] rarities = { "silver", "gold", "prismatic" };
        long[] diamonds = { 60, 120, 240 };
        long[] income = { 10, 20, 40 };
        for (int tier = 0; tier < rarities.length; tier++) {
            SemionPlayer player = player(0);
            String suffix = rarities[tier];
            for (String family : List.of("reserve_diamonds_", "reserve_income_", "reserve_production_")) {
                select(player, family + suffix, 5);
                select(player, family + suffix, 5);
                select(player, family + suffix, 15);
            }
            assertEquals(2 * diamonds[tier], player.economy().diamond());
            assertEquals(2 * income[tier], player.economy().income());
            assertEquals(2 * (tier + 1), player.economy().emeraldPerSec());
            assertEquals(0, player.economy().emeraldProductionUpgradeCount());
            List<EconomyEvent> events = player.augmentTelemetry().snapshot().economyEvents();
            assertEquals(6, events.size());
            assertTrue(events.stream().allMatch(event -> event.tick() == null));
            int expectedProductionUnits = 2 * (tier + 1);
            assertEquals(expectedProductionUnits, events.stream().filter(event -> event.augmentId().contains("reserve_production"))
                    .mapToInt(EconomyEvent::units).sum());
        }
    }

    @Test
    void reserveProductionKeepsUpgradePriceAndCapAndUsesRound25Multiplier() {
        EconomyConfig config = EconomyConfig.defaultConfig();
        SemionPlayer player = player(0);
        player.economy().addDiamond(1_000_000);
        for (int i = 0; i < config.gasProduction().maxUpgradeCount(); i++) {
            assertTrue(player.economy().upgradeGasProduction(config.gasProduction()));
        }
        long before = player.economy().emeraldPerSec();
        select(player, "reserve_production_prismatic", 25);
        assertEquals(before + 3, player.economy().emeraldPerSec());
        assertFalse(player.economy().upgradeGasProduction(config.gasProduction()));
        player.economy().spendEmerald(player.economy().emerald());
        SemionTeam team = new SemionTeam(TeamId.RED);
        team.activate();
        new EconomyService(config).tickEmerald(List.of(player), Map.of(TeamId.RED, team), 25);
        assertEquals((before + 3) * 2, player.economy().emerald());
    }

    @Test
    void payloadAndCashShareOneSuccessfulTransactionAndNeverConsumeOnQuote() {
        SemionPlayer player = player(10);
        player.augmentTelemetry().bindClock(() -> 1_200L, () -> 5);
        AugmentEconomyService.beginPrepare(player, 5);
        select(player, "additional_payload", 5);
        select(player, "cash_settlement", 5);
        assertTrue(AugmentEconomyService.setAdditionalPayload(player, 5, true));
        assertTrue(AugmentEconomyService.setContract(player, 5, AugmentEconomyService.Contract.CASH));
        AugmentEconomyService.PurchasePlan plan = quote(player, 5, true, true, false, false, 241, 15);
        assertEquals(302, plan.emeraldCost());
        assertEquals(241, plan.normalEmeraldCost());
        assertEquals(0, plan.incomeGain());
        assertEquals(45, plan.instantDiamond());
        assertTrue(AugmentEconomyService.payloadArmed(player));
        assertEquals(AugmentEconomyService.Contract.CASH, AugmentEconomyService.contract(player));
        assertTrue(player.augmentTelemetry().snapshot().economyEvents().isEmpty());
        Monster monster = paidMonster(player);
        AugmentEconomyService.applyPurchaseBody(monster, plan);
        AugmentEconomyService.applyPurchaseBody(monster, plan);
        assertEquals(135, monster.maxHealth(), 0.0001);
        assertTrue(AugmentEconomyService.commitPurchase(player, plan, monster));
        assertFalse(AugmentEconomyService.commitPurchase(player, plan, monster));
        assertEquals(1.35, AugmentEconomyService.supportMultiplier(monster));
        assertEquals(45, player.economy().diamond());
        assertEquals(10, player.economy().income());
        assertEquals(15, player.economyAugments().incomeForgone());
        assertFalse(AugmentEconomyService.payloadArmed(player));
        assertFalse(AugmentEconomyService.setContract(player, 5, AugmentEconomyService.Contract.CASH));
        List<EconomyEvent> events = player.augmentTelemetry().snapshot().economyEvents();
        assertEquals(1, events.size());
        EconomyEvent event = events.getFirst();
        assertEquals(1_200L, event.tick());
        assertEquals("PURCHASE_COMMIT", event.eventType());
        assertNull(event.augmentId());
        assertEquals(List.of("semiontd:additional_payload", "semiontd:cash_settlement"), event.relatedAugmentIds());
        assertEquals(241L, event.baseEmeraldCost());
        assertEquals(302L, event.emeraldPaid());
        assertEquals(45L, event.diamondGranted());
        assertEquals(15L, event.incomeForgone());
        assertNull(event.incomeGranted());
    }

    @Test
    void staleQuoteAndCombatPurchaseNeverConsumePreparationToggle() {
        SemionPlayer player = player(0);
        AugmentEconomyService.beginPrepare(player, 5);
        select(player, "cash_settlement", 5);
        AugmentEconomyService.setContract(player, 5, AugmentEconomyService.Contract.CASH);
        AugmentEconomyService.PurchasePlan stale = quote(player, 5, true, false, true, true, 100, 15);
        AugmentEconomyService.setContract(player, 5, AugmentEconomyService.Contract.NONE);
        assertFalse(AugmentEconomyService.commitPurchase(player, stale, paidMonster(player)));
        AugmentEconomyService.setContract(player, 5, AugmentEconomyService.Contract.CASH);
        AugmentEconomyService.PurchasePlan combat = quote(player, 5, false, false, true, true, 100, 15);
        assertEquals(AugmentEconomyService.Contract.NONE, combat.contract());
        assertTrue(AugmentEconomyService.commitPurchase(player, combat, paidMonster(player)));
        assertEquals(15, player.economy().income());
        assertEquals(AugmentEconomyService.Contract.CASH, AugmentEconomyService.contract(player));
        AugmentEconomyService.endPrepare(player, 5);
        assertEquals(AugmentEconomyService.Contract.NONE, AugmentEconomyService.contract(player));
    }

    @Test
    void lowPressureRetainsOnlyFractionWhenCappedAndResetsRoundAllowance() {
        SemionPlayer player = player(0);
        AugmentEconomyService.beginPrepare(player, 5);
        select(player, "low_pressure_high_yield", 5);
        AugmentEconomyService.setContract(player, 5, AugmentEconomyService.Contract.LOW_PRESSURE);
        AugmentEconomyService.PurchasePlan large = quote(player, 5, true, false, true, true, 100, 101);
        assertEquals(113, large.incomeGain());
        Monster weakened = paidMonster(player);
        assertTrue(AugmentEconomyService.commitPurchase(player, large, weakened));
        assertTrue(AugmentEconomyService.isLowPressure(weakened));
        assertEquals(0.25, player.economyAugments().lowPressureFraction());
        AugmentEconomyService.PurchasePlan capped = quote(player, 5, true, false, true, true, 100, 3);
        assertEquals(0, capped.bonusIncome());
        assertTrue(AugmentEconomyService.commitPurchase(player, capped, paidMonster(player)));
        assertEquals(0, player.economyAugments().lowPressureFraction());
        List<EconomyEvent> measured = player.augmentTelemetry().snapshot().economyEvents();
        assertEquals(2, measured.size());
        assertEquals(12L, measured.getFirst().incomeGranted());
        assertEquals(0L, measured.getLast().incomeGranted());
        AugmentEconomyService.beginPrepare(player, 6);
        AugmentEconomyService.setContract(player, 6, AugmentEconomyService.Contract.LOW_PRESSURE);
        AugmentEconomyService.PurchasePlan next = quote(player, 6, true, false, true, true, 100, 4);
        assertEquals(1, next.bonusIncome());
    }

    @Test
    void perPurchaseLowPressureChoiceIsReadOnlyAndCannotBypassAnotherValueContract() {
        SemionPlayer player = player(0);
        AugmentEconomyService.beginPrepare(player, 5);
        select(player, "low_pressure_high_yield", 5);
        long revision = player.economyAugments().revision();
        AugmentEconomyService.PurchasePlan low = AugmentEconomyService.quotePurchase(player, UUID.randomUUID(), 5,
                true, true, false, true, true, 100, 4, AugmentEconomyService.Contract.LOW_PRESSURE);
        assertEquals(5, low.incomeGain());
        assertEquals(revision, player.economyAugments().revision());
        assertEquals(AugmentEconomyService.Contract.NONE, AugmentEconomyService.contract(player));
        AugmentEconomyService.PurchasePlan normal = AugmentEconomyService.quotePurchase(player, UUID.randomUUID(), 5,
                true, true, false, true, true, 100, 4, AugmentEconomyService.Contract.NONE);
        assertEquals(4, normal.incomeGain());
        assertThrows(IllegalArgumentException.class, () -> AugmentEconomyService.quotePurchase(player, UUID.randomUUID(), 5,
                true, true, true, false, false, 100, 4, AugmentEconomyService.Contract.LOW_PRESSURE));
        select(player, "cash_settlement", 5);
        AugmentEconomyService.setContract(player, 5, AugmentEconomyService.Contract.CASH);
        assertThrows(IllegalArgumentException.class, () -> AugmentEconomyService.quotePurchase(player, UUID.randomUUID(), 5,
                true, true, false, true, true, 100, 4, AugmentEconomyService.Contract.NONE));
        assertEquals(AugmentEconomyService.Contract.CASH, AugmentEconomyService.contract(player));
    }

    @Test
    void forecastDefersIncomeUntilActualNextRoundSpawnAndEchoPaysNothing() {
        SemionPlayer player = player(10);
        AugmentEconomyService.beginPrepare(player, 5);
        select(player, "forecast_offensive", 5);
        AugmentEconomyService.setContract(player, 5, AugmentEconomyService.Contract.FORECAST);
        AugmentEconomyService.PurchasePlan plan = quote(player, 5, true, false, true, true, 100, 15);
        Monster original = paidMonster(player);
        assertTrue(AugmentEconomyService.commitPurchase(player, plan, original));
        assertEquals(1, player.economyAugments().pendingForecasts().size());
        assertEquals(6, player.economyAugments().pendingForecasts().getFirst().round());
        assertEquals(15, player.economyAugments().pendingForecasts().getFirst().amount());
        Monster echo = AugmentEconomyService.createForecastEcho(original, plan.echoRatio());
        assertEquals(6, plan.scheduledRound());
        assertEquals(30, echo.maxHealth());
        assertEquals(3, echo.attackDamage());
        assertEquals(0, echo.mineralReward());
        assertEquals(MonsterOrigin.ECHO, echo.origin());
        assertFalse(AugmentEconomyService.onForecastSpawn(original, 5));
        assertFalse(AugmentEconomyService.onForecastSpawn(echo, 6));
        AugmentEconomyService.payRoundIncome(player, 5);
        assertEquals(10, player.economy().diamond());
        assertTrue(AugmentEconomyService.onForecastSpawn(original, 6));
        assertFalse(AugmentEconomyService.onForecastSpawn(original, 6));
        assertEquals(25, player.economy().income());
        assertTrue(player.economyAugments().pendingForecasts().isEmpty());
    }

    @Test
    void decisiveChangesOnlyStandardAttackerAndForgoesJobAdjustedIncome() {
        SemionPlayer player = player(10);
        AugmentEconomyService.beginPrepare(player, 15);
        select(player, "decisive_delivery", 15);
        AugmentEconomyService.setContract(player, 15, AugmentEconomyService.Contract.DECISIVE);
        assertEquals(AugmentEconomyService.Contract.NONE, quote(player, 15, true, true, false, false, 100, 15).contract());
        AugmentEconomyService.PurchasePlan plan = quote(player, 15, true, false, true, true, 100, 15);
        Monster monster = paidMonster(player);
        AugmentEconomyService.applyPurchaseBody(monster, plan);
        assertTrue(AugmentEconomyService.commitPurchase(player, plan, monster));
        assertEquals(160, monster.maxHealth());
        assertEquals(14, monster.attackDamage());
        assertEquals(10, player.economy().income());
        assertEquals(15, player.economyAugments().incomeForgone());
        assertFalse(plan.ordnanceEligible());
        EconomyEvent event = player.augmentTelemetry().snapshot().economyEvents().getFirst();
        assertEquals("semiontd:decisive_delivery", event.augmentId());
        assertEquals(15L, event.incomeForgone());
        assertNull(event.diamondGranted());
    }

    @Test
    void blueprintConsumesOnlySuccessfulUpgradeOnDistinctLogicalTowersThenExpires() {
        SemionPlayer player = player(101);
        AugmentEconomyService.beginPrepare(player, 5);
        select(player, "forbidden_blueprint", 5);
        UUID tower = UUID.randomUUID();
        AugmentEconomyService.UpgradePlan first = AugmentEconomyService.quoteUpgrade(player, UUID.randomUUID(), 5, tower, true, 400, true).orElseThrow();
        assertEquals(100, first.cost());
        assertEquals(2, player.economyAugments().remainingTickets());
        assertTrue(AugmentEconomyService.commitUpgrade(player, first));
        assertFalse(AugmentEconomyService.commitUpgrade(player, first));
        assertTrue(AugmentEconomyService.quoteUpgrade(player, UUID.randomUUID(), 5, tower, true, 400, true).isEmpty());
        AugmentEconomyService.UpgradePlan second = AugmentEconomyService.quoteUpgrade(player, UUID.randomUUID(), 5, UUID.randomUUID(), true, 40, true).orElseThrow();
        assertEquals(0, second.cost());
        assertEquals(40, second.discount());
        assertTrue(AugmentEconomyService.commitUpgrade(player, second));
        assertEquals(81, AugmentEconomyService.previewPayout(player, 101));
        AugmentEconomyService.beginPrepare(player, 6);
        assertEquals(0, player.economyAugments().remainingTickets());
        assertEquals(81, AugmentEconomyService.previewPayout(player, 101));
    }

    @Test
    void ticketPreviewUsesOriginalPayoutAndRoundsOnlyOnceWithoutConsumingState() {
        SemionPlayer player = player(13);
        AugmentEconomyService.beginPrepare(player, 5);
        select(player, "forbidden_blueprint", 5);
        long revision = player.economyAugments().revision();
        assertEquals(11, AugmentEconomyService.previewPayoutAfterTicket(player, 13));
        assertEquals(revision, player.economyAugments().revision());
        assertEquals(2, player.economyAugments().remainingTickets());
        AugmentEconomyService.commitUpgrade(player, AugmentEconomyService.quoteUpgrade(player,
                UUID.randomUUID(), 5, UUID.randomUUID(), true, 300, true).orElseThrow());
        assertEquals(11, AugmentEconomyService.previewPayout(player, 13));
        assertEquals(10, AugmentEconomyService.previewPayoutAfterTicket(player, 13));
        AugmentEconomyService.commitUpgrade(player, AugmentEconomyService.quoteUpgrade(player,
                UUID.randomUUID(), 5, UUID.randomUUID(), true, 300, true).orElseThrow());
        AugmentEconomyService.payRoundIncome(player, 5);
        assertEquals(10, player.economy().diamond());
    }

    @Test
    void wartimePayoutReducesOnlyPeriodicIncomeIncludingLaterReserveIncome() {
        SemionPlayer player = player(101);
        select(player, "wartime_economy", 5);
        select(player, "reserve_income_gold", 15);
        select(player, "reserve_diamonds_silver", 25);
        assertEquals(121, player.economy().income());
        assertEquals(60, player.economy().diamond());
        AugmentEconomyService.payRoundIncome(player, 25);
        assertEquals(138, player.economy().diamond());
        assertEquals(43, player.economyAugments().payoutWithheld());
    }

    @Test
    void telemetrySeparatesActualMultiplierWithholdingDebtRepaymentAndTicketExpiration() {
        SemionPlayer player = player(101);
        player.augmentTelemetry().bindClock(() -> 900L, () -> 5);
        AugmentEconomyService.beginPrepare(player, 5);
        select(player, "emergency_loan", 5);
        select(player, "forbidden_blueprint", 5);
        select(player, "wartime_economy", 5);
        select(player, "cash_settlement", 5);
        UUID tower = UUID.randomUUID();
        var upgrade = AugmentEconomyService.quoteUpgrade(player, UUID.randomUUID(), 5, tower, true, 400, true).orElseThrow();
        assertTrue(AugmentEconomyService.commitUpgrade(player, upgrade));
        assertFalse(AugmentEconomyService.commitUpgrade(player, upgrade));
        assertTrue(AugmentEconomyService.payRoundIncome(player, 5));
        assertFalse(AugmentEconomyService.payRoundIncome(player, 5));
        List<EconomyEvent> events = player.augmentTelemetry().snapshot().economyEvents();
        EconomyEvent payout = events.stream().filter(event -> event.eventType().equals("PERIODIC_PAYOUT")).findFirst().orElseThrow();
        EconomyEvent debt = events.stream().filter(event -> event.eventType().equals("DEBT_REPAYMENT")).findFirst().orElseThrow();
        EconomyEvent ticket = events.stream().filter(event -> event.eventType().equals("TICKET_USED")).findFirst().orElseThrow();
        assertEquals(0L, payout.diamondGranted());
        assertEquals(42L, payout.payoutWithheld());
        assertEquals(59L, debt.payoutWithheld());
        assertEquals(101L, payout.payoutWithheld() + debt.payoutWithheld());
        assertNull(debt.diamondGranted());
        assertEquals(List.of("semiontd:forbidden_blueprint", "semiontd:wartime_economy", "semiontd:emergency_loan", "semiontd:cash_settlement"),
                payout.relatedAugmentIds());
        assertEquals(100L, ticket.diamondPaid());
        assertEquals(player.augmentTelemetry().towerRef(tower), ticket.towerRef());
        assertEquals(5, events.size());
        AugmentEconomyService.endPrepare(player, 5);
        AugmentEconomyService.endPrepare(player, 5);
        AugmentEconomyService.beginPrepare(player, 6);
        List<EconomyEvent> expired = player.augmentTelemetry().snapshot().economyEvents().stream()
                .filter(event -> event.eventType().equals("TICKET_EXPIRED")).toList();
        assertEquals(1, expired.size());
        assertEquals(1, expired.getFirst().units());
    }

    @Test
    void telemetryTracksReservationRealizationAndCancellationWithoutCountingDeferredIncomeAsGranted() {
        SemionPlayer player = player(10);
        AugmentEconomyService.beginPrepare(player, 5);
        select(player, "forecast_offensive", 5);
        AugmentEconomyService.setContract(player, 5, AugmentEconomyService.Contract.FORECAST);
        Monster first = paidMonster(player);
        assertTrue(AugmentEconomyService.commitPurchase(player, quote(player, 5, true, false, true, true, 100, 15), first));
        EconomyEvent reservation = player.augmentTelemetry().snapshot().economyEvents().getFirst();
        assertEquals("FORECAST_RESERVED", reservation.eventType());
        assertEquals(15L, reservation.incomeDeferred());
        assertNull(reservation.incomeGranted());
        assertNull(reservation.incomeForgone());
        assertFalse(AugmentEconomyService.onForecastSpawn(first, 5));
        assertTrue(AugmentEconomyService.onForecastSpawn(first, 6));
        assertFalse(AugmentEconomyService.onForecastSpawn(first, 6));
        AugmentEconomyService.beginPrepare(player, 6);
        AugmentEconomyService.setContract(player, 6, AugmentEconomyService.Contract.FORECAST);
        Monster cancelled = paidMonster(player);
        assertTrue(AugmentEconomyService.commitPurchase(player, quote(player, 6, true, false, true, true, 100, 16), cancelled));
        AugmentEconomyService.close(player);
        AugmentEconomyService.close(player);
        assertFalse(AugmentEconomyService.onForecastSpawn(cancelled, 7));
        List<EconomyEvent> events = player.augmentTelemetry().snapshot().economyEvents();
        assertEquals(4, events.size());
        assertEquals(0L, events.stream().mapToLong(EconomyEvent::incomeDeferred).sum());
        assertEquals(15L, events.stream().filter(event -> event.incomeGranted() != null).mapToLong(EconomyEvent::incomeGranted).sum());
        assertEquals("FORECAST_CANCELLED", events.getLast().eventType());
        assertEquals(-16L, events.getLast().incomeDeferred());
        assertNull(events.getLast().incomeForgone());
    }

    @Test
    void supportCountsDistinctTargetsOncePerPreparationAndCapsAtEight() {
        SemionPlayer player = player(0);
        select(player, "support_performance", 5);
        for (int round = 5; round <= 9; round++) {
            AugmentEconomyService.beginPrepare(player, round);
            Monster source = paidMonster(player);
            AugmentEconomyService.commitPurchase(player, quote(player, round, true, true, false, false, 100, 0), source);
            Monster first = naturalMonster();
            assertFalse(AugmentEconomyService.recordSupport(source, first, 0));
            assertFalse(AugmentEconomyService.recordSupport(source, first, 10));
            assertFalse(AugmentEconomyService.recordSupport(source, first, 10));
            assertFalse(AugmentEconomyService.recordSupport(source, source, 10));
            assertFalse(AugmentEconomyService.recordSupport(source, naturalMonster(), 10));
            assertEquals(round < 9, AugmentEconomyService.recordSupport(source, naturalMonster(), 10));
            assertFalse(AugmentEconomyService.recordSupport(source, naturalMonster(), 10));
        }
        assertEquals(8, player.economy().income());
        assertEquals(8, player.economyAugments().supportIncome());
        List<EconomyEvent> rewards = player.augmentTelemetry().snapshot().economyEvents();
        assertEquals(4, rewards.size());
        assertTrue(rewards.stream().allMatch(event -> event.eventType().equals("SUPPORT_REWARD") && event.incomeGranted() == 2));
    }

    @Test
    void ordnanceOnlyCountsUnmodifiedSuccessfulPreparationStandardAttackPurchases() {
        SemionPlayer player = player(0);
        AugmentEconomyService.beginPrepare(player, 15);
        assertTrue(quote(player, 15, true, false, true, true, 100, 10).ordnanceEligible());
        assertFalse(quote(player, 15, false, false, true, true, 100, 10).ordnanceEligible());
        assertFalse(quote(player, 15, true, true, false, false, 100, 10).ordnanceEligible());
        select(player, "cash_settlement", 15);
        AugmentEconomyService.setContract(player, 15, AugmentEconomyService.Contract.CASH);
        assertFalse(quote(player, 15, true, false, true, true, 100, 10).ordnanceEligible());
    }

    @Test
    void zeroRewardNaturalKillCountsOnceWhileEchoDoesNotCreateRewardsOrKillStats() {
        SemionPlayer player = player(0);
        EconomyService economy = new EconomyService(EconomyConfig.defaultConfig());
        Monster natural = new Monster("natural", TeamId.RED, 1, Optional.empty(), Optional.empty(),
                100, 0, 10, AttackKind.MELEE, "minecraft:zombie", 0);
        natural.setOrigin(MonsterOrigin.NATURAL_WAVE);
        natural.recordLastHit(player.uuid(), KillSourceKind.TOWER);
        economy.awardMonsterKillReward(natural, Map.of(player.uuid(), player));
        economy.awardMonsterKillReward(natural, Map.of(player.uuid(), player));
        assertEquals(1, player.matchStats().monsterKills());
        assertEquals(0, player.economy().diamond());
        assertTrue(natural.rewardGranted());
        Monster echo = AugmentEconomyService.createForecastEcho(paidMonster(player), 0.3);
        echo.recordLastHit(player.uuid(), KillSourceKind.TOWER);
        economy.awardMonsterKillReward(echo, Map.of(player.uuid(), player));
        assertEquals(1, player.matchStats().monsterKills());
        assertEquals(0, player.economy().diamond());
    }

    @Test
    void eliminationClosesUnpaidReservationsAndDebtWithoutErasingRecordedGains() {
        SemionPlayer player = player(10);
        AugmentEconomyService.beginPrepare(player, 5);
        select(player, "forecast_offensive", 5);
        AugmentEconomyService.setContract(player, 5, AugmentEconomyService.Contract.FORECAST);
        Monster original = paidMonster(player);
        AugmentEconomyService.commitPurchase(player, quote(player, 5, true, false, true, true, 100, 15), original);
        select(player, "emergency_loan", 5);
        assertEquals(30, player.economyAugments().diamondGranted());
        AugmentEconomyService.close(player);
        assertEquals(0, player.economyAugments().debt());
        assertFalse(AugmentEconomyService.onForecastSpawn(original, 6));
        assertFalse(AugmentEconomyService.payRoundIncome(player, 6));
        assertEquals(10, player.economy().income());
        assertEquals(30, player.economyAugments().diamondGranted());
    }

    private static AugmentEconomyService.PurchasePlan quote(SemionPlayer player, int round, boolean prepare,
            boolean utility, boolean standard, boolean attack, long cost, long gain) {
        return AugmentEconomyService.quotePurchase(player, UUID.randomUUID(), round, prepare, true, utility, standard, attack, cost, gain);
    }

    private static void select(SemionPlayer player, String id, int round) {
        AugmentEconomyService.onSelected(player, "semiontd:" + id, round, Map.of());
    }

    private static SemionPlayer player(long income) {
        PlayerEconomy economy = new PlayerEconomy(EconomyConfig.defaultConfig());
        economy.overrideStartingValues(0, 1_000, income, 0);
        return new SemionPlayer(UUID.randomUUID(), "augment-economy", TeamId.RED, 1, economy);
    }

    private static Monster paidMonster(SemionPlayer player) {
        Monster monster = new Monster("zombie", TeamId.BLUE, 1, Optional.of(player.uuid()), Optional.of(TeamId.RED),
                100, 0, 10, AttackKind.MELEE, "minecraft:zombie", 10);
        monster.setOrigin(MonsterOrigin.NORMAL_PAID);
        return monster;
    }

    private static Monster naturalMonster() {
        Monster monster = new Monster("natural", TeamId.BLUE, 1, Optional.empty(), Optional.empty(),
                100, 0, 10, AttackKind.MELEE, "minecraft:zombie", 10);
        monster.setOrigin(MonsterOrigin.NATURAL_WAVE);
        return monster;
    }
}
