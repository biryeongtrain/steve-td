package kim.biryeong.semiontd.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.game.PlayerMatchStatsSnapshot;
import kim.biryeong.semiontd.game.TeamId;
import org.junit.jupiter.api.Test;

final class RatingContributionCalculatorTest {
    @Test
    void averageContributionKeepsMultiplierNeutral() {
        RatingContributionCalculator calculator = new RatingContributionCalculator(RatingConfig.defaultConfig());
        RatingMatchInput input = input(
                participant("winner", TeamId.RED, true, stats(10, 100, 2, 50)),
                participant("loser", TeamId.BLUE, false, stats(10, 100, 2, 50))
        );

        RatingContributionBreakdown breakdown = calculator.breakdown(input, input.participants().get(0));

        assertEquals(1.0, breakdown.defenseScore(), 0.0001);
        assertEquals(1.0, breakdown.pressureScore(), 0.0001);
        assertEquals(1.0, breakdown.economyScore(), 0.0001);
        assertEquals(1.0, breakdown.assistScore(), 0.0001);
        assertEquals(1.0, breakdown.appliedMultiplier(), 0.0001);
    }

    @Test
    void strongerContributionRaisesWinnerDeltaAndLowersLoserPenalty() {
        RatingMatchInput input = input(
                participant("strongWinner", TeamId.RED, true, stats(20, 200, 4, 80)),
                participant("weakWinner", TeamId.RED, true, stats(5, 50, 1, 20)),
                participant("strongLoser", TeamId.BLUE, false, stats(20, 200, 4, 80)),
                participant("weakLoser", TeamId.BLUE, false, stats(5, 50, 1, 20))
        );

        RatingMatchResult result = new EloRatingCalculator(RatingConfig.defaultConfig()).calculate(input);

        RatingAdjustment strongWinner = result.adjustments().get(0);
        RatingAdjustment weakWinner = result.adjustments().get(1);
        RatingAdjustment strongLoser = result.adjustments().get(2);
        RatingAdjustment weakLoser = result.adjustments().get(3);
        assertTrue(strongWinner.displayEloDelta() > weakWinner.displayEloDelta());
        assertTrue(Math.abs(strongLoser.displayEloDelta()) < Math.abs(weakLoser.displayEloDelta()));
        assertTrue(strongWinner.contribution().appliedMultiplier() > 1.0);
        assertTrue(weakWinner.contribution().appliedMultiplier() < 1.0);
    }

    @Test
    void explicitAttributionStatsDriveContributionScoresBeforeFallbackCounters() {
        RatingContributionCalculator calculator = new RatingContributionCalculator(RatingConfig.defaultConfig());
        RatingMatchInput input = input(
                participant("defender", TeamId.RED, true, attributionStats(10, 100, 1, 20, 100, 10, 0, 0, 10, 2, 1, 0, 0)),
                participant("leaker", TeamId.RED, true, attributionStats(100, 1000, 10, 200, 100, 50, 0, 0, 2, 1, 1, 0, 0)),
                participant("attacker", TeamId.BLUE, false, attributionStats(1, 1, 1, 10, 100, 20, 100, 50, 1, 1, 20, 5, 50)),
                participant("weakAttacker", TeamId.BLUE, false, attributionStats(1, 1, 1, 10, 100, 20, 10, 0, 1, 0, 1, 0, 0))
        );

        RatingContributionBreakdown defender = calculator.breakdown(input, input.participants().get(0));
        RatingContributionBreakdown leaker = calculator.breakdown(input, input.participants().get(1));
        RatingContributionBreakdown attacker = calculator.breakdown(input, input.participants().get(2));

        assertTrue(defender.defenseScore() > leaker.defenseScore());
        assertTrue(attacker.pressureScore() > 1.0);
        assertTrue(attacker.economyScore() > 1.0);
        assertTrue(attacker.assistScore() > 1.0);
    }

    @Test
    void zeroContributionScoresBelowAverageWhenBaselineExists() {
        RatingContributionCalculator calculator = new RatingContributionCalculator(RatingConfig.defaultConfig());
        RatingMatchInput fallbackInput = input(
                participant("zeroFallback", TeamId.RED, true, stats(0, 0, 0, 0)),
                participant("activeFallback", TeamId.RED, true, stats(10, 100, 4, 80)),
                participant("opponentFallback", TeamId.BLUE, false, stats(10, 100, 4, 80))
        );
        RatingMatchInput attributionInput = input(
                participant("zeroAttribution", TeamId.RED, true, attributionStats(0, 0, 0, 0, 100, 100, 0, 0, 0, 0, 0, 0, 0)),
                participant("activeAttribution", TeamId.RED, true, attributionStats(0, 0, 0, 0, 100, 0, 80, 20, 10, 5, 5, 50, 20)),
                participant("opponentAttribution", TeamId.BLUE, false, attributionStats(0, 0, 0, 0, 100, 0, 80, 20, 10, 5, 5, 50, 20))
        );

        RatingContributionBreakdown fallbackZero = calculator.breakdown(fallbackInput, fallbackInput.participants().get(0));
        RatingContributionBreakdown attributionZero = calculator.breakdown(attributionInput, attributionInput.participants().get(0));

        assertTrue(fallbackZero.appliedMultiplier() < 1.0);
        assertTrue(attributionZero.pressureScore() < 1.0);
        assertTrue(attributionZero.economyScore() < 1.0);
        assertTrue(attributionZero.assistScore() < 1.0);
        assertTrue(attributionZero.appliedMultiplier() < 1.0);
    }

    @Test
    void contributionMultiplierIsClamped() {
        RatingConfig config = new RatingConfig(true, 32.0, 1500, 1500.0, 350.0, 10, 2, true,
                true, 0.90, 1.10, 0.40, 0.25, 0.20, 0.15);
        RatingContributionCalculator calculator = new RatingContributionCalculator(config);
        RatingMatchInput input = input(
                participant("carry", TeamId.RED, true, stats(1000, 10000, 100, 5000)),
                participant("other", TeamId.RED, true, stats(1, 1, 1, 1)),
                participant("loser", TeamId.BLUE, false, stats(1, 1, 1, 1))
        );

        RatingContributionBreakdown breakdown = calculator.breakdown(input, input.participants().get(0));

        assertTrue(breakdown.rawMultiplier() > 1.10);
        assertEquals(1.10, breakdown.appliedMultiplier(), 0.0001);
    }

    private static RatingMatchInput input(RatingParticipant... participants) {
        return new RatingMatchInput(new MatchId(100L), 1000L, List.of(participants));
    }

    private static RatingParticipant participant(String name, TeamId teamId, boolean winner, PlayerMatchStatsSnapshot stats) {
        UUID playerId = UUID.nameUUIDFromBytes(name.getBytes());
        return new RatingParticipant(playerId, name, teamId, winner, PlayerRatingProfile.initial(playerId, name), stats);
    }

    private static PlayerMatchStatsSnapshot stats(long kills, long killMinerals, long summons, long finalIncome) {
        return new PlayerMatchStatsSnapshot(kills, killMinerals, summons, finalIncome);
    }

    private static PlayerMatchStatsSnapshot attributionStats(
            long kills,
            long killMinerals,
            long summons,
            long finalIncome,
            double incomingThreat,
            double leakedThreat,
            double sentIncomeThreat,
            double incomeAttackSuccessThreat,
            long ownLaneDiamondGain,
            long assistClearDiamondGain,
            long incomeGenerated,
            double assistClearThreat,
            double incomingIncomeThreat
    ) {
        return new PlayerMatchStatsSnapshot(
                kills,
                killMinerals,
                summons,
                finalIncome,
                incomingThreat,
                leakedThreat,
                sentIncomeThreat,
                incomeAttackSuccessThreat,
                ownLaneDiamondGain,
                assistClearDiamondGain,
                incomeGenerated,
                assistClearThreat,
                incomingIncomeThreat
        );
    }
}
