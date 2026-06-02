package kim.biryeong.semiontd.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ParticipantSelectionService {
    private static final List<TeamId> TEAM_ORDER = List.of(TeamId.RED, TeamId.BLUE, TeamId.GREEN, TeamId.YELLOW, TeamId.PURPLE);
    private static final int MAX_ACTIVE_PLAYERS = SemionTeam.MAX_PLAYERS * TEAM_ORDER.size();

    private ParticipantSelectionService() {
    }

    public static Optional<ParticipantSelectionPlan> select(List<StartCandidate> candidates, MatchMode mode) {
        return select(candidates, mode, Set.of());
    }

    public static Optional<ParticipantSelectionPlan> select(
            List<StartCandidate> candidates,
            MatchMode mode,
            Set<UUID> priorityPlayerIds
    ) {
        List<StartCandidate> shuffledCandidates = prioritizedRandomCandidates(candidates, priorityPlayerIds);

        SelectionShape shape = shapeFor(shuffledCandidates.size(), mode);
        if (shape == null) {
            return Optional.empty();
        }

        List<StartCandidate> activeCandidates = shuffledCandidates.subList(0, shape.activePlayerCount()).stream()
                .sorted((left, right) -> Integer.compare(right.displayElo(), left.displayElo()))
                .toList();
        List<TeamId> activeTeams = TEAM_ORDER.subList(0, shape.activeTeamCount());
        Map<TeamId, Integer> capacities = capacitiesByTeam(activeTeams, shape.teamCapacities());
        Map<TeamId, List<StartCandidate>> assigned = new EnumMap<>(TeamId.class);
        for (TeamId teamId : activeTeams) {
            assigned.put(teamId, new ArrayList<>());
        }

        for (StartCandidate candidate : activeCandidates) {
            Optional<TeamId> targetTeam = lowestEloAvailableTeam(assigned, capacities, activeTeams);
            if (targetTeam.isEmpty()) {
                break;
            }
            assigned.get(targetTeam.get()).add(candidate);
        }

        List<AssignedParticipant> activeParticipants = new ArrayList<>(shape.activePlayerCount());
        Set<UUID> spectatorIds = new HashSet<>();

        for (TeamId teamId : activeTeams) {
            List<StartCandidate> teamCandidates = assigned.get(teamId);
            for (int i = 0; i < teamCandidates.size(); i++) {
                StartCandidate candidate = teamCandidates.get(i);
                activeParticipants.add(new AssignedParticipant(candidate.uuid(), candidate.name(), teamId, i + 1));
            }
        }

        for (int i = shape.activePlayerCount(); i < shuffledCandidates.size(); i++) {
            spectatorIds.add(shuffledCandidates.get(i).uuid());
        }

        return Optional.of(new ParticipantSelectionPlan(
                mode,
                activeParticipants,
                spectatorIds,
                shape.activeTeamCount()
        ));
    }

    public static Optional<ParticipantSelectionPlan> selectReady(
            List<StartCandidate> candidates,
            Set<UUID> readyPlayerIds,
            MatchMode mode
    ) {
        return selectReady(candidates, readyPlayerIds, mode, Set.of());
    }

    public static Optional<ParticipantSelectionPlan> selectReady(
            List<StartCandidate> candidates,
            Set<UUID> readyPlayerIds,
            MatchMode matchMode,
            Set<UUID> priorityPlayerIds
    ) {
        if (readyPlayerIds == null || readyPlayerIds.isEmpty()) {
            return Optional.empty();
        }
        return select(candidates.stream()
                .filter(candidate -> readyPlayerIds.contains(candidate.uuid()))
                .toList(), matchMode, priorityPlayerIds);
    }

    private static List<StartCandidate> prioritizedRandomCandidates(
            List<StartCandidate> candidates,
            Set<UUID> priorityPlayerIds
    ) {
        Set<UUID> priorities = priorityPlayerIds == null ? Set.of() : priorityPlayerIds;
        List<StartCandidate> priorityCandidates = new ArrayList<>();
        List<StartCandidate> regularCandidates = new ArrayList<>();
        for (StartCandidate candidate : candidates) {
            if (priorities.contains(candidate.uuid())) {
                priorityCandidates.add(candidate);
            } else {
                regularCandidates.add(candidate);
            }
        }

        Collections.shuffle(priorityCandidates);
        Collections.shuffle(regularCandidates);

        List<StartCandidate> shuffledCandidates = new ArrayList<>(candidates.size());
        shuffledCandidates.addAll(priorityCandidates);
        shuffledCandidates.addAll(regularCandidates);
        return shuffledCandidates;
    }

    private static Map<TeamId, Integer> capacitiesByTeam(List<TeamId> activeTeams, List<Integer> teamCapacities) {
        Map<TeamId, Integer> capacities = new EnumMap<>(TeamId.class);
        for (int i = 0; i < activeTeams.size(); i++) {
            capacities.put(activeTeams.get(i), teamCapacities.get(i));
        }
        return capacities;
    }

    private static Optional<TeamId> lowestEloAvailableTeam(
            Map<TeamId, List<StartCandidate>> assigned,
            Map<TeamId, Integer> capacities,
            List<TeamId> activeTeams
    ) {
        TeamId bestTeam = null;
        int bestElo = Integer.MAX_VALUE;
        int bestSize = Integer.MAX_VALUE;
        for (TeamId teamId : activeTeams) {
            int currentSize = assigned.get(teamId).size();
            int capacity = capacities.get(teamId);
            if (currentSize >= capacity) {
                continue;
            }
            int teamElo = assigned.get(teamId).stream().mapToInt(StartCandidate::displayElo).sum();
            if (teamElo < bestElo || (teamElo == bestElo && currentSize < bestSize)) {
                bestTeam = teamId;
                bestElo = teamElo;
                bestSize = currentSize;
            }
        }
        return Optional.ofNullable(bestTeam);
    }

    private static SelectionShape shapeFor(int playerCount, MatchMode mode) {
        if (mode == MatchMode.TEST) {
            return playerCount >= 2 ? new SelectionShape(2, List.of(1, 1)) : null;
        }

        if (playerCount < 4) {
            return null;
        }

        int activePlayerCount = Math.min(playerCount, MAX_ACTIVE_PLAYERS);
        SelectionShape fallback = null;
        int maxTeams = Math.min(TEAM_ORDER.size(), activePlayerCount);
        for (int teamCount = 2; teamCount <= maxTeams; teamCount++) {
            int maxTeamSize = (activePlayerCount + teamCount - 1) / teamCount;
            if (maxTeamSize > SemionTeam.MAX_PLAYERS) {
                continue;
            }

            int minTeamSize = activePlayerCount / teamCount;
            List<Integer> capacities = distributedCapacities(activePlayerCount, teamCount);
            if (minTeamSize >= 3) {
                return new SelectionShape(activePlayerCount, capacities);
            }
            if (minTeamSize >= 2 && fallback == null) {
                fallback = new SelectionShape(activePlayerCount, capacities);
            }
        }
        return fallback;
    }

    private static List<Integer> distributedCapacities(int activePlayerCount, int teamCount) {
        List<Integer> capacities = new ArrayList<>(teamCount);
        int base = activePlayerCount / teamCount;
        int remainder = activePlayerCount % teamCount;
        for (int i = 0; i < teamCount; i++) {
            capacities.add(base + (i < remainder ? 1 : 0));
        }
        return capacities;
    }

    private record SelectionShape(int activePlayerCount, List<Integer> teamCapacities) {
        private SelectionShape {
            teamCapacities = List.copyOf(teamCapacities);
        }

        private int activeTeamCount() {
            return teamCapacities.size();
        }
    }
}
