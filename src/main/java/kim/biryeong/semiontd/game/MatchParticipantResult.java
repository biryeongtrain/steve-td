package kim.biryeong.semiontd.game;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import kim.biryeong.semiontd.buildguide.BuildAction;
import kim.biryeong.semiontd.trait.TraitLoadoutSnapshot;

public record MatchParticipantResult(
        UUID playerId,
        String playerName,
        TeamId teamId,
        boolean winner,
        PlayerMatchStatsSnapshot stats,
        String jobId,
        List<Integer> attemptedRounds,
        List<Integer> clearedRounds,
        TraitLoadoutSnapshot traitLoadout,
        List<TowerCompositionEntry> finalTowerComposition,
        List<BuildAction> buildActions,
        List<PlayerRoundMetricsSnapshot> roundMetrics
) {
    public MatchParticipantResult(UUID playerId, String playerName, TeamId teamId, boolean winner) {
        this(playerId, playerName, teamId, winner, PlayerMatchStatsSnapshot.empty(), null, List.of(), List.of(),
                TraitLoadoutSnapshot.none(), List.of(), List.of(), List.of());
    }

    public MatchParticipantResult(
            UUID playerId,
            String playerName,
            TeamId teamId,
            boolean winner,
            PlayerMatchStatsSnapshot stats
    ) {
        this(playerId, playerName, teamId, winner, stats, null, List.of(), List.of(),
                TraitLoadoutSnapshot.none(), List.of(), List.of(), List.of());
    }

    public MatchParticipantResult(
            UUID playerId,
            String playerName,
            TeamId teamId,
            boolean winner,
            PlayerMatchStatsSnapshot stats,
            String jobId
    ) {
        this(playerId, playerName, teamId, winner, stats, jobId, List.of(), List.of(),
                TraitLoadoutSnapshot.none(), List.of(), List.of(), List.of());
    }

    public MatchParticipantResult(
            UUID playerId,
            String playerName,
            TeamId teamId,
            boolean winner,
            PlayerMatchStatsSnapshot stats,
            String jobId,
            List<Integer> attemptedRounds,
            List<Integer> clearedRounds
    ) {
        this(playerId, playerName, teamId, winner, stats, jobId, attemptedRounds, clearedRounds,
                TraitLoadoutSnapshot.none(), List.of(), List.of(), List.of());
    }

    public MatchParticipantResult(
            UUID playerId,
            String playerName,
            TeamId teamId,
            boolean winner,
            PlayerMatchStatsSnapshot stats,
            String jobId,
            List<Integer> attemptedRounds,
            List<Integer> clearedRounds,
            TraitLoadoutSnapshot traitLoadout,
            List<TowerCompositionEntry> finalTowerComposition
    ) {
        this(playerId, playerName, teamId, winner, stats, jobId, attemptedRounds, clearedRounds,
                traitLoadout, finalTowerComposition, List.of(), List.of());
    }

    public MatchParticipantResult(
            UUID playerId,
            String playerName,
            TeamId teamId,
            boolean winner,
            PlayerMatchStatsSnapshot stats,
            String jobId,
            List<Integer> attemptedRounds,
            List<Integer> clearedRounds,
            TraitLoadoutSnapshot traitLoadout,
            List<TowerCompositionEntry> finalTowerComposition,
            List<BuildAction> buildActions
    ) {
        this(playerId, playerName, teamId, winner, stats, jobId, attemptedRounds, clearedRounds,
                traitLoadout, finalTowerComposition, buildActions, List.of());
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
        traitLoadout = traitLoadout == null ? TraitLoadoutSnapshot.none() : traitLoadout;
        finalTowerComposition = finalTowerComposition == null
                ? List.of()
                : finalTowerComposition.stream()
                .filter(Objects::nonNull)
                .sorted(java.util.Comparator.comparing(TowerCompositionEntry::towerTypeId)
                        .thenComparingInt(TowerCompositionEntry::tier))
                .toList();
        buildActions = buildActions == null
                ? List.of()
                : buildActions.stream().filter(Objects::nonNull).toList();
        roundMetrics = roundMetrics == null
                ? List.of()
                : roundMetrics.stream()
                .filter(Objects::nonNull)
                .sorted(java.util.Comparator.comparingInt(PlayerRoundMetricsSnapshot::round))
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
