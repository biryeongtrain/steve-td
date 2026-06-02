package kim.biryeong.semiontd.rating;

import java.util.ArrayList;
import java.util.List;

public final class EloRatingCalculator implements RatingCalculator {
    public static final double DEFAULT_K_FACTOR = 32.0;
    private static final double ELO_SCALE = 400.0;
    private static final double TOP_HALF_TARGET_SCORE = 0.90;
    private static final double BOTTOM_HALF_TARGET_SCORE = 0.10;
    private static final double DRAW_SCORE = 0.50;

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

        List<RatingAdjustment> adjustments = new ArrayList<>();
        for (RatingParticipant participant : input.participants()) {
            PlayerRatingProfile before = participant.currentProfile();
            double baseDelta = placementBaseDelta(input.participants(), participant);
            RatingContributionBreakdown contribution = contributionCalculator.breakdown(input, participant);
            double multiplier = baseDelta >= 0.0
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
                    before.wins() + (participant.placement() == 1 ? 1 : 0),
                    before.losses() + (participant.placement() == 1 ? 0 : 1),
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

    private double placementBaseDelta(List<RatingParticipant> participants, RatingParticipant participant) {
        List<RatingParticipant> opponents = participants.stream()
                .filter(opponent -> !opponent.teamId().equals(participant.teamId()))
                .toList();
        if (opponents.isEmpty()) {
            return 0.0;
        }
        int teamCount = distinctTeamCount(participants);
        double targetScore = placementTargetScore(teamCount, participant.placement());
        double averageExpectedScore = opponents.stream()
                .mapToDouble(opponent -> expectedScore(participant.currentProfile().mu(), opponent.currentProfile().mu()))
                .average()
                .orElse(DRAW_SCORE);
        double teamSizeMultiplier = teamSizeMultiplier(participants, participant, opponents.size());
        return kFactor * (targetScore - averageExpectedScore) * teamSizeMultiplier;
    }

    private static int distinctTeamCount(List<RatingParticipant> participants) {
        return (int) participants.stream()
                .map(RatingParticipant::teamId)
                .distinct()
                .count();
    }

    private static double placementTargetScore(int teamCount, int placement) {
        if (teamCount <= 1) {
            return DRAW_SCORE;
        }
        if (placement <= 1) {
            return 1.0;
        }
        if (placement >= teamCount) {
            return 0.0;
        }
        if (teamCount % 2 == 1 && placement == (teamCount + 1) / 2) {
            return DRAW_SCORE;
        }
        return placement <= teamCount / 2
                ? TOP_HALF_TARGET_SCORE
                : BOTTOM_HALF_TARGET_SCORE;
    }

    private static double teamSizeMultiplier(
            List<RatingParticipant> participants,
            RatingParticipant participant,
            int opponentCount
    ) {
        long ownTeamSize = participants.stream()
                .filter(other -> other.teamId().equals(participant.teamId()))
                .count();
        if (ownTeamSize <= 0 || opponentCount <= 0) {
            return 1.0;
        }
        return Math.min(1.0, (double) opponentCount / ownTeamSize);
    }

    private static double validateKFactor(double kFactor) {
        if (!Double.isFinite(kFactor) || kFactor <= 0.0) {
            throw new IllegalArgumentException("kFactor must be positive and finite");
        }
        return kFactor;
    }

    private static double expectedScore(double ownRating, double opponentRating) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponentRating - ownRating) / ELO_SCALE));
    }
}
