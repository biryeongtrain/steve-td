package kim.biryeong.semiontd.game;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MatchParticipantResult(
        UUID playerId,
        String playerName,
        TeamId teamId,
        boolean winner,
        PlayerMatchStatsSnapshot stats,
        String jobId,
        List<Integer> attemptedRounds,
        List<Integer> clearedRounds
) {
    public MatchParticipantResult(UUID playerId, String playerName, TeamId teamId, boolean winner) {
        this(playerId, playerName, teamId, winner, PlayerMatchStatsSnapshot.empty(), null, List.of(), List.of());
    }

    public MatchParticipantResult(
            UUID playerId,
            String playerName,
            TeamId teamId,
            boolean winner,
            PlayerMatchStatsSnapshot stats
    ) {
        this(playerId, playerName, teamId, winner, stats, null, List.of(), List.of());
    }

    public MatchParticipantResult(
            UUID playerId,
            String playerName,
            TeamId teamId,
            boolean winner,
            PlayerMatchStatsSnapshot stats,
            String jobId
    ) {
        this(playerId, playerName, teamId, winner, stats, jobId, List.of(), List.of());
    }

    public MatchParticipantResult {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(teamId, "teamId");
        stats = stats == null ? PlayerMatchStatsSnapshot.empty() : stats;
        jobId = jobId == null || jobId.isBlank() ? null : jobId;
        attemptedRounds = normalizeRounds(attemptedRounds);
        clearedRounds = normalizeRounds(clearedRounds).stream()
                .filter(attemptedRounds::contains)
                .toList();
    }

    private static List<Integer> normalizeRounds(List<Integer> rounds) {
        if (rounds == null) {
            return List.of();
        }
        return rounds.stream()
                .filter(Objects::nonNull)
                .filter(round -> round > 0)
                .distinct()
                .sorted()
                .toList();
    }
}
