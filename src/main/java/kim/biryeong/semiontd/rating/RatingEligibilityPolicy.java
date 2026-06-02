package kim.biryeong.semiontd.rating;

import java.util.List;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.MatchResultGroup;
import kim.biryeong.semiontd.game.TeamId;

public final class RatingEligibilityPolicy {
    private final RatingConfig config;

    public RatingEligibilityPolicy(RatingConfig config) {
        this.config = config == null ? RatingConfig.defaultConfig() : config;
    }

    public boolean isEligible(MatchResult matchResult) {
        return skippedReason(matchResult).isEmpty();
    }

    public String skippedReason(MatchResult matchResult) {
        if (!config.enabled()) {
            return "rating is disabled";
        }
        List<MatchParticipantResult> participants = ratingParticipants(matchResult);
        if (participants.size() < config.minimumParticipants()) {
            return "not enough rating-eligible participants";
        }
        long ratedTeamCount = participants.stream()
                .map(MatchParticipantResult::teamId)
                .distinct()
                .count();
        if (ratedTeamCount != 2) {
            return "rating requires exactly two participant teams";
        }
        if (matchResult.winningTeams().size() != 1) {
            return "rating requires exactly one winning team";
        }
        if (hasDrawOrUnratedParticipant(matchResult, participants)) {
            return "rating does not support draw or unrated participant teams yet";
        }
        for (MatchParticipantResult participant : participants) {
            if (participant.winner() != matchResult.winningTeams().contains(participant.teamId())) {
                return "participant winner flag does not match winning teams";
            }
        }
        boolean hasWinner = participants.stream().anyMatch(MatchParticipantResult::winner);
        boolean hasLoser = participants.stream().anyMatch(participant -> !participant.winner());
        if (!hasWinner || !hasLoser) {
            return "rating requires at least one winner and one loser";
        }
        return "";
    }

    public List<MatchParticipantResult> ratingParticipants(MatchResult matchResult) {
        if (!config.excludeSpectators()) {
            return List.copyOf(matchResult.participants());
        }
        return matchResult.participants().stream()
                .filter(participant -> !matchResult.spectatorIds().contains(participant.playerId()))
                .toList();
    }

    private static boolean hasDrawOrUnratedParticipant(MatchResult matchResult, List<MatchParticipantResult> participants) {
        for (MatchParticipantResult participant : participants) {
            MatchResultGroup group = teamResultGroup(matchResult, participant.teamId());
            if (group == MatchResultGroup.DRAW_OR_UNRATED) {
                return true;
            }
        }
        return false;
    }

    private static MatchResultGroup teamResultGroup(MatchResult matchResult, TeamId teamId) {
        return matchResult.teamResults().stream()
                .filter(teamResult -> teamResult.teamId().equals(teamId))
                .findFirst()
                .map(teamResult -> teamResult.resultGroup())
                .orElse(MatchResultGroup.DRAW_OR_UNRATED);
    }
}
