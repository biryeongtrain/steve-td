package kim.biryeong.semiontd.game;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record MatchResult(
        UUID matchId,
        long startedAtEpochMillis,
        long endedAtEpochMillis,
        List<MatchParticipantResult> participants,
        Set<UUID> spectatorIds,
        Set<TeamId> winningTeams,
        List<TeamMatchResult> teamResults,
        int finalRound
) {
    public MatchResult(
            List<MatchParticipantResult> participants,
            Set<UUID> spectatorIds,
            Set<TeamId> winningTeams,
            int finalRound
    ) {
        this(
                UUID.randomUUID(),
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                participants,
                spectatorIds,
                winningTeams,
                fallbackTeamResults(participants, winningTeams),
                finalRound
        );
    }

    public MatchResult {
        Objects.requireNonNull(matchId, "matchId");
        participants = List.copyOf(participants);
        spectatorIds = Set.copyOf(spectatorIds);
        winningTeams = Set.copyOf(winningTeams);
        teamResults = List.copyOf(teamResults);
        if (startedAtEpochMillis < 0 || endedAtEpochMillis < 0) {
            throw new IllegalArgumentException("match timestamps cannot be negative");
        }
    }

    public int participantCount() {
        return participants.size();
    }

    public int winnerCount() {
        return (int) participants.stream().filter(MatchParticipantResult::winner).count();
    }

    public int loserCount() {
        return participantCount() - winnerCount();
    }

    private static List<TeamMatchResult> fallbackTeamResults(
            List<MatchParticipantResult> participants,
            Set<TeamId> winningTeams
    ) {
        Set<TeamId> participantTeams = new HashSet<>();
        for (MatchParticipantResult participant : participants) {
            participantTeams.add(participant.teamId());
        }
        return participantTeams.stream()
                .sorted()
                .map(teamId -> new TeamMatchResult(
                        teamId,
                        winningTeams.contains(teamId) ? 1 : 2,
                        winningTeams.contains(teamId) ? MatchResultGroup.WIN_GROUP : MatchResultGroup.LOSS_GROUP,
                        winningTeams.contains(teamId) ? 1.0 : 0.0,
                        -1,
                        -1,
                        0.0
                ))
                .toList();
    }
}
