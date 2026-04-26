package kim.biryeong.semionTd.game;

import java.util.Objects;
import java.util.UUID;

public record MatchParticipantResult(
        UUID playerId,
        String playerName,
        TeamId teamId,
        boolean winner,
        PlayerMatchStatsSnapshot stats
) {
    public MatchParticipantResult(UUID playerId, String playerName, TeamId teamId, boolean winner) {
        this(playerId, playerName, teamId, winner, PlayerMatchStatsSnapshot.empty());
    }

    public MatchParticipantResult {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(teamId, "teamId");
        stats = stats == null ? PlayerMatchStatsSnapshot.empty() : stats;
    }
}
