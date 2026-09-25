package kim.biryeong.semiontd.game;

import java.util.List;

/**
 * Actual Season 3 observations for one participant. Missing payload/values mean unmeasured, not zero.
 * Tower references are participant-local integers; entity, player and request UUIDs are never exported here.
 */
public record AugmentTelemetrySnapshot(
        List<GuiEvent> guiEvents,
        List<EconomyEvent> economyEvents,
        List<CombatRoundSample> combatRounds,
        List<TowerSample> towerSamples,
        List<LaneRoundSample> laneRounds
) {
    public AugmentTelemetrySnapshot {
        guiEvents = guiEvents == null ? null : List.copyOf(guiEvents);
        economyEvents = economyEvents == null ? null : List.copyOf(economyEvents);
        combatRounds = combatRounds == null ? null : List.copyOf(combatRounds);
        towerSamples = towerSamples == null ? null : List.copyOf(towerSamples);
        laneRounds = laneRounds == null ? null : List.copyOf(laneRounds);
    }

    /**
     * SHOWN is an actual server display, not candidate generation. elapsedTicks starts at the first
     * actual display of that milestone; it is null before display. inputCount/backCount are cumulative
     * within the milestone. Candidate lists are joined through the existing offerRevision history.
     */
    public record GuiEvent(
            int milestoneRound,
            long tick,
            String eventType,
            long offerRevision,
            Integer slot,
            String augmentId,
            String route,
            String result,
            String reason,
            String mode,
            List<Integer> targetRefs,
            Long elapsedTicks,
            Integer inputCount,
            Integer backCount
    ) {
        public GuiEvent {
            targetRefs = targetRefs == null ? null : List.copyOf(targetRefs);
        }
    }

    /**
     * Confirmed transaction deltas, never cumulative totals or hypothetical savings. The tick is null
     * if no match clock is attached. Joint modifiers use relatedAugmentIds without assigning a causal
     * share to each card. PERIODIC_PAYOUT withholding excludes debt repayment; DEBT_REPAYMENT records
     * that repayment separately. Periodic diamondGranted is the actual final payment after both.
     * units means uses/expirations/payments, or production-rate increase for a production grant;
     * it is not the quantity of currency subsequently produced. incomeDeferred is the signed change
     * in reserved, not-yet-paid income: reservation positive, realization/cancellation negative.
     */
    public record EconomyEvent(
            int round,
            Long tick,
            String augmentId,
            String eventType,
            Integer towerRef,
            Long baseEmeraldCost,
            Long emeraldPaid,
            Long diamondPaid,
            Long diamondGranted,
            Long incomeGranted,
            Long incomeForgone,
            Long payoutWithheld,
            Integer units,
            List<String> relatedAugmentIds,
            Long incomeDeferred
    ) {
        public EconomyEvent {
            relatedAugmentIds = relatedAugmentIds == null ? null : List.copyOf(relatedAugmentIds);
        }

        public EconomyEvent(int round, Long tick, String augmentId, String eventType, Integer towerRef,
                            Long baseEmeraldCost, Long emeraldPaid, Long diamondPaid, Long diamondGranted,
                            Long incomeGranted, Long incomeForgone, Long payoutWithheld, Integer units) {
            this(round, tick, augmentId, eventType, towerRef, baseEmeraldCost, emeraldPaid, diamondPaid,
                    diamondGranted, incomeGranted, incomeForgone, payoutWithheld, units,
                    augmentId == null ? List.of() : List.of(augmentId), null);
        }

        public EconomyEvent(int round, Long tick, String augmentId, String eventType, Integer towerRef,
                            Long baseEmeraldCost, Long emeraldPaid, Long diamondPaid, Long diamondGranted,
                            Long incomeGranted, Long incomeForgone, Long payoutWithheld, Integer units,
                            List<String> relatedAugmentIds) {
            this(round, tick, augmentId, eventType, towerRef, baseEmeraldCost, emeraldPaid, diamondPaid,
                    diamondGranted, incomeGranted, incomeForgone, payoutWithheld, units, relatedAugmentIds, null);
        }
    }

    /** START/END/REMOVED capture one logical tower. Repeated stages are snapshots, not additive events. */
    public record CombatRoundSample(
            int round,
            int towerRef,
            String towerTypeId,
            String stage,
            CombatState state
    ) {
    }

    /**
     * Counters are round-to-date. Armor modifier differences are measured at the armor modifier
     * stage, before later modifiers/shields/overkill, and are NOT HP damage prevented or a causal
     * estimate of total augment damage. Direct HP damage is recorded only after actual HP loss.
     * twinAffectedSlotRatio uses occupied eligible twin slots / all occupied player tower slots.
     * barrageActivations counts charged primary attacks that dealt actual HP damage, including
     * shots consumed on low-pressure bodies; it does not count reloads from eligible kills.
     * enemyHpDamage is the tower's actual health lost to enemies, including low-pressure units;
     * it is not damage dealt to enemies. masteryEligibleHpDamage excludes
     * low-pressure bodies and is the separate, measured input to the mastery qualification.
     */
    public record CombatState(
            Double startingMaxHealth,
            Double enemyHpDamage,
            Integer heatStacks,
            Integer masteryStacks,
            Boolean masteryQualified,
            String masteryResult,
            Integer masteryStacksLost,
            Boolean twinEligible,
            Integer twinEligibleGroups,
            Double twinAffectedSlotRatio,
            String armorMode,
            Double physicalDirectHpDamage,
            Double magicDirectHpDamage,
            Double armorModifierReducedDamage,
            Double armorModifierIncreasedDamage,
            Long finishingHits,
            Long barrageActivations,
            Long dominoTransfers,
            Double masteryEligibleHpDamage
    ) {
    }

    /**
     * Actual placement/removal/round-end observations. Action counters and barrierAbsorbed are
     * round-to-date snapshots; do not sum different events for the same tower/round. Cocoon successes
     * are the current persistent growth state. slotWeight is actual occupied capacity, not tower count.
     */
    public record TowerSample(
            int round,
            long tick,
            int towerRef,
            String towerTypeId,
            String eventType,
            int slotWeight,
            Double barrierAbsorbed,
            Long relayCharges,
            Long relayTriggers,
            Long mineExplosions,
            Long capacitorChargedShots,
            Long ordnanceShellsFired,
            Integer cocoonSuccesses,
            Boolean hatched
    ) {
    }

    /** One completed round's actual leak observations, including measured zero-leak rounds. */
    public record LaneRoundSample(int round, boolean leaked, double leakedThreat, int leakedCount) {
    }
}
