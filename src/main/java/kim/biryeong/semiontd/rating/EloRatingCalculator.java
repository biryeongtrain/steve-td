package kim.biryeong.semiontd.rating;

import java.util.ArrayList;
import java.util.List;

public final class EloRatingCalculator implements RatingCalculator {
    public static final double DEFAULT_K_FACTOR = 32.0;
    private static final double ELO_SCALE = 400.0;
    private static final double WIN_SCORE = 1.0;
    private static final double LOSS_SCORE = 0.0;

    private final double kFactor;
    private final RatingContributionCalculator contributionCalculator;

    public EloRatingCalculator() {
        this(RatingConfig.defaultConfig());
    }

    public EloRatingCalculator(RatingConfig config) {
        this(config == null ? RatingConfig.defaultConfig() : config, null);
    }

    public EloRatingCalculator(double kFactor) {
        this.kFactor = validateKFactor(kFactor);
        this.contributionCalculator = new RatingContributionCalculator(new RatingConfig(
                true,
                kFactor,
                PlayerRatingProfile.INITIAL_DISPLAY_ELO,
                PlayerRatingProfile.INITIAL_MU,
                PlayerRatingProfile.INITIAL_SIGMA,
                10,
                2,
                true,
                false,
                1.0,
                1.0,
                0.40,
                0.25,
                0.20,
                0.15
        ));
    }

    private EloRatingCalculator(RatingConfig config, RatingContributionCalculator contributionCalculator) {
        this.kFactor = validateKFactor(config.eloKFactor());
        this.contributionCalculator = contributionCalculator == null
                ? new RatingContributionCalculator(config)
                : contributionCalculator;
    }

    @Override
    public RatingMatchResult calculate(RatingMatchInput input) {
        if (input.participants().isEmpty()) {
            return RatingMatchResult.empty(input.matchId());
        }

        double winnerAverage = averageMu(input, true);
        double loserAverage = averageMu(input, false);
        int winnerCount = participantCount(input, true);
        int loserCount = participantCount(input, false);
        List<RatingAdjustment> adjustments = new ArrayList<>();
        for (RatingParticipant participant : input.participants()) {
            PlayerRatingProfile before = participant.currentProfile();
            double opponentAverage = participant.winner() ? loserAverage : winnerAverage;
            double actualScore = participant.winner() ? WIN_SCORE : LOSS_SCORE;
            double expectedScore = expectedScore(before.mu(), opponentAverage);
            double baseDelta = teamBalancedBaseDelta(kFactor, actualScore, expectedScore, participant.winner(), winnerCount, loserCount);
            RatingContributionBreakdown contribution = contributionCalculator.breakdown(input, participant);
            double multiplier = participant.winner()
                    ? contribution.appliedMultiplier()
                    : 2.0 - contribution.appliedMultiplier();
            double muDelta = baseDelta * multiplier;
            double afterMu = before.mu() + muDelta;
            int afterDisplayElo = (int) Math.round(afterMu);
            PlayerRatingProfile after = new PlayerRatingProfile(
                    before.playerId(),
                    participant.playerName(),
                    RatingSystemId.ELO,
                    before.ratingVersion() + 1,
                    before.gamesPlayed() + 1,
                    before.wins() + (participant.winner() ? 1 : 0),
                    before.losses() + (participant.winner() ? 0 : 1),
                    afterMu,
                    before.sigma(),
                    afterDisplayElo,
                    input.matchId(),
                    input.endedAtEpochMillis()
            );
            adjustments.add(new RatingAdjustment(
                    participant.playerId(),
                    participant.playerName(),
                    participant.teamId(),
                    participant.winner(),
                    before,
                    after,
                    after.mu() - before.mu(),
                    after.displayElo() - before.displayElo(),
                    contribution
            ));
        }
        return new RatingMatchResult(
                input.matchId(),
                RatingSystemId.ELO,
                1,
                input.endedAtEpochMillis(),
                adjustments
        );
    }

    private static double validateKFactor(double kFactor) {
        if (!Double.isFinite(kFactor) || kFactor <= 0.0) {
            throw new IllegalArgumentException("kFactor must be positive and finite");
        }
        return kFactor;
    }

    private static double averageMu(RatingMatchInput input, boolean winner) {
        List<RatingParticipant> participants = input.participants().stream()
                .filter(participant -> participant.winner() == winner)
                .toList();
        if (participants.isEmpty()) {
            return PlayerRatingProfile.INITIAL_MU;
        }
        return participants.stream()
                .mapToDouble(participant -> participant.currentProfile().mu())
                .average()
                .orElse(PlayerRatingProfile.INITIAL_MU);
    }

    private static int participantCount(RatingMatchInput input, boolean winner) {
        return (int) input.participants().stream()
                .filter(participant -> participant.winner() == winner)
                .count();
    }

    private static double teamBalancedBaseDelta(
            double kFactor,
            double actualScore,
            double expectedScore,
            boolean winner,
            int winnerCount,
            int loserCount
    ) {
        int ownTeamSize = winner ? winnerCount : loserCount;
        int opposingTeamSize = winner ? loserCount : winnerCount;
        if (ownTeamSize <= 0 || opposingTeamSize <= 0) {
            return kFactor * (actualScore - expectedScore);
        }
        double teamSizeMultiplier = Math.min(1.0, (double) opposingTeamSize / ownTeamSize);
        return kFactor * (actualScore - expectedScore) * teamSizeMultiplier;
    }

    private static double expectedScore(double ownRating, double opponentRating) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponentRating - ownRating) / ELO_SCALE));
    }
}
