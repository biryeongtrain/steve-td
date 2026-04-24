package kim.biryeong.semionTd.game;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record ParticipantSelectionPlan(
        MatchMode mode,
        List<AssignedParticipant> activeParticipants,
        Set<UUID> spectatorIds,
        int activeTeamCount
) {
    public ParticipantSelectionPlan {
        activeParticipants = List.copyOf(activeParticipants);
        spectatorIds = Set.copyOf(spectatorIds);
    }

    public int activePlayerCount() {
        return activeParticipants.size();
    }

    public int spectatorCount() {
        return spectatorIds.size();
    }

    public Map<TeamId, Integer> teamSizes() {
        Map<TeamId, Integer> sizes = new EnumMap<>(TeamId.class);
        for (AssignedParticipant participant : activeParticipants) {
            sizes.merge(participant.teamId(), 1, Integer::sum);
        }
        return Map.copyOf(sizes);
    }

    public String compositionSummary() {
        return teamSizes().values().stream()
                .sorted(Comparator.reverseOrder())
                .map(String::valueOf)
                .collect(Collectors.joining("/"));
    }
}
