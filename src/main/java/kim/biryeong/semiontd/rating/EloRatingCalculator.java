package kim.biryeong.semiontd.rating;

import java.util.ArrayList;
import java.util.List;

public final class EloRatingCalculator implements RatingCalculator {
    public static final double DEFAULT_K_FACTOR = 32.0;
    private static final double ELO_SCALE = 400.0;
    private static final double WIN_SCORE = 1.0;
    private static final double LOSS_SCORE = 0.0;

    private final double kFactor;

    public EloRatingCalculator() {
        this(RatingConfig.defaultConfig());
    }

    public EloRatingCalculator(RatingConfig config) {
        this((config == null ? RatingConfig.defaultConfig() : config).eloKFactor());
    }

    public EloRatingCalculator(double kFactor) {
        if (!Double.isFinite(kFactor) || kFactor <= 0.0) {
            throw new IllegalArgumentException("kFactor must be positive and finite");
        }
        this.kFactor = kFactor;
    }

    @Override
    public RatingMatchResult calculate(RatingMatchInput input) {
        if (input.participants().isEmpty()) {
            return RatingMatchResult.empty(input.matchId());
        }

        double winnerAverage = averageMu(input, true);
        double loserAverage = averageMu(input, false);
        List<RatingAdjustment> adjustments = new ArrayList<>();
        for (RatingParticipant participant : input.participants()) {
            PlayerRatingProfile before = participant.currentProfile();
            double opponentAverage = participant.winner() ? loserAverage : winnerAverage;
            double actualScore = participant.winner() ? WIN_SCORE : LOSS_SCORE;
            double expectedScore = expectedScore(before.mu(), opponentAverage);
            double muDelta = kFactor * (actualScore - expectedScore);
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
                    after.displayElo() - before.displayElo()
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

    private static double expectedScore(double ownRating, double opponentRating) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponentRating - ownRating) / ELO_SCALE));
    }
}
