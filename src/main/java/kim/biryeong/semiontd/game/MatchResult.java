package kim.biryeong.semiontd.game;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record MatchResult(
        List<MatchParticipantResult> participants,
        Set<UUID> spectatorIds,
        Set<TeamId> winningTeams,
        int finalRound
) {
    public MatchResult {
        participants = List.copyOf(participants);
        spectatorIds = Set.copyOf(spectatorIds);
        winningTeams = Set.copyOf(winningTeams);
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
}