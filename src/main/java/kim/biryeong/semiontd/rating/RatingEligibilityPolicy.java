package kim.biryeong.semiontd.rating;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.MatchResultGroup;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamMatchResult;

public final class RatingEligibilityPolicy {
    private static final int MIN_RATED_TEAMS = 2;
    private static final int MAX_RATED_TEAMS = 5;

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
        Set<TeamId> ratedTeams = ratedTeams(participants);
        int ratedTeamCount = ratedTeams.size();
        if (ratedTeamCount < MIN_RATED_TEAMS || ratedTeamCount > MAX_RATED_TEAMS) {
            return "rating requires between two and five participant teams";
        }
        if (matchResult.winningTeams().size() != 1) {
            return "rating requires exactly one winning team";
        }
        Map<TeamId, TeamMatchResult> teamResults = teamResultsFor(ratedTeams, matchResult);
        if (teamResults.size() != ratedTeams.size()) {
            return "rating requires team results for every participant team";
        }
        if (!hasOneTeamResultPerRatedTeam(ratedTeams, matchResult)) {
            return "rating requires one team result per participant team";
        }
        if (hasDrawOrUnratedTeam(teamResults)) {
            return "rating does not support draw or unrated participant teams yet";
        }
        if (!hasUniqueContiguousPlacements(teamResults, ratedTeamCount)) {
            return "rating requires unique contiguous team placements";
        }
        for (MatchParticipantResult participant : participants) {
            TeamMatchResult teamResult = teamResults.get(participant.teamId());
            if (participant.winner() != (teamResult.placement() == 1)) {
                return "participant winner flag does not match team placement result";
            }
        }
        TeamId firstPlaceTeam = teamResults.values().stream()
                .filter(teamResult -> teamResult.placement() == 1)
                .map(TeamMatchResult::teamId)
                .findFirst()
                .orElseThrow();
        if (!matchResult.winningTeams().contains(firstPlaceTeam)) {
            return "winning team does not match first-place team";
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

    private static Set<TeamId> ratedTeams(List<MatchParticipantResult> participants) {
        Set<TeamId> teams = new HashSet<>();
        for (MatchParticipantResult participant : participants) {
            teams.add(participant.teamId());
        }
        return teams;
    }

    private static boolean hasOneTeamResultPerRatedTeam(Set<TeamId> ratedTeams, MatchResult matchResult) {
        Map<TeamId, Integer> counts = new HashMap<>();
        for (TeamMatchResult teamResult : matchResult.teamResults()) {
            if (ratedTeams.contains(teamResult.teamId())) {
                counts.merge(teamResult.teamId(), 1, Integer::sum);
            }
        }
        for (TeamId teamId : ratedTeams) {
            if (counts.getOrDefault(teamId, 0) != 1) {
                return false;
            }
        }
        return true;
    }

    private static Map<TeamId, TeamMatchResult> teamResultsFor(Set<TeamId> ratedTeams, MatchResult matchResult) {
        Map<TeamId, TeamMatchResult> results = new HashMap<>();
        for (TeamMatchResult teamResult : matchResult.teamResults()) {
            if (ratedTeams.contains(teamResult.teamId())) {
                results.put(teamResult.teamId(), teamResult);
            }
        }
        return results;
    }

    private static boolean hasDrawOrUnratedTeam(Map<TeamId, TeamMatchResult> teamResults) {
        for (TeamMatchResult teamResult : teamResults.values()) {
            if (teamResult.resultGroup() == MatchResultGroup.DRAW_OR_UNRATED) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUniqueContiguousPlacements(Map<TeamId, TeamMatchResult> teamResults, int ratedTeamCount) {
        Set<Integer> placements = new HashSet<>();
        for (TeamMatchResult teamResult : teamResults.values()) {
            placements.add(teamResult.placement());
        }
        if (placements.size() != ratedTeamCount) {
            return false;
        }
        for (int placement = 1; placement <= ratedTeamCount; placement++) {
            if (!placements.contains(placement)) {
                return false;
            }
        }
        return true;
    }
}
