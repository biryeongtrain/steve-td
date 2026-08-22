package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class ParticipantSelectionServiceTest {
    @Test
    void teamAssignmentBalancesDisplayElo() {
        StartCandidate strongest = candidate("strongest", 2000);
        StartCandidate strong = candidate("strong", 1900);
        StartCandidate weak = candidate("weak", 1000);
        StartCandidate weakest = candidate("weakest", 900);

        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.selectReady(
                List.of(strongest, strong, weak, weakest),
                Set.of(strongest.uuid(), strong.uuid(), weak.uuid(), weakest.uuid()),
                MatchMode.NORMAL
        );

        assertTrue(plan.isPresent());
        Map<TeamId, Integer> eloByTeam = plan.get().activeParticipants().stream()
                .collect(Collectors.groupingBy(
                        AssignedParticipant::teamId,
                        Collectors.summingInt(participant -> eloFor(participant.uuid(), strongest, strong, weak, weakest))
                ));
        assertEquals(2900, eloByTeam.get(TeamId.RED));
        assertEquals(2900, eloByTeam.get(TeamId.BLUE));
    }

    @Test
    void selectionPlanCarriesDisplayEloForRuntimeLeaderSelection() {
        StartCandidate high = candidate("leader-high-elo", 1800);
        StartCandidate low = candidate("leader-low-elo", 1200);

        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.selectReady(
                List.of(high, low),
                Set.of(high.uuid(), low.uuid()),
                MatchMode.TEST
        );

        assertTrue(plan.isPresent());
        assertEquals(1800, plan.get().activeParticipants().stream()
                .filter(participant -> participant.uuid().equals(high.uuid()))
                .findFirst()
                .orElseThrow()
                .displayElo());
    }

    @Test
    void teamAssignmentCanDisableDisplayEloBalancing() {
        StartCandidate strongest = candidate("disabled-strongest", 2000);
        StartCandidate strong = candidate("disabled-strong", 1900);
        StartCandidate weak = candidate("disabled-weak", 1000);
        StartCandidate weakest = candidate("disabled-weakest", 900);

        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.selectReady(
                List.of(strongest, strong, weak, weakest),
                Set.of(strongest.uuid(), strong.uuid(), weak.uuid(), weakest.uuid()),
                MatchMode.NORMAL,
                Set.of(),
                false,
                new Random(0)
        );

        assertTrue(plan.isPresent());
        Map<TeamId, Integer> eloByTeam = plan.get().activeParticipants().stream()
                .collect(Collectors.groupingBy(
                        AssignedParticipant::teamId,
                        Collectors.summingInt(participant -> eloFor(participant.uuid(), strongest, strong, weak, weakest))
                ));
        assertEquals(2800, eloByTeam.get(TeamId.RED));
        assertEquals(3000, eloByTeam.get(TeamId.BLUE));
    }

    @Test
    void previousSpectatorPriorityIsAppliedBeforeEloActiveCutoff() {
        List<StartCandidate> candidates = java.util.stream.IntStream.rangeClosed(1, 35)
                .mapToObj(index -> candidate("candidate-" + index, index <= 30 ? 2000 : 1000))
                .toList();
        Set<UUID> readyPlayerIds = candidates.stream()
                .map(StartCandidate::uuid)
                .collect(Collectors.toUnmodifiableSet());
        Set<UUID> priorityPlayerIds = Set.of(
                stableUuid("candidate-31"),
                stableUuid("candidate-32"),
                stableUuid("candidate-33"),
                stableUuid("candidate-34"),
                stableUuid("candidate-35")
        );

        Optional<ParticipantSelectionPlan> plan = ParticipantSelectionService.selectReady(
                candidates,
                readyPlayerIds,
                MatchMode.NORMAL,
                priorityPlayerIds
        );

        assertTrue(plan.isPresent());
        Set<UUID> activeIds = plan.get().activeParticipants().stream()
                .map(AssignedParticipant::uuid)
                .collect(Collectors.toUnmodifiableSet());
        assertTrue(activeIds.containsAll(priorityPlayerIds));
    }

    @Test
    void twentyFourPlayersUseSixTeamsOfFour() {
        ParticipantSelectionPlan plan = planFor(24);

        assertEquals(6, plan.activeTeamCount());
        assertEquals(Set.of(4), Set.copyOf(plan.teamSizes().values()));
        assertEquals(4, plan.teamSizes().get(TeamId.AQUA));
    }

    @Test
    void thirtyPlayersUseSixTeamsOfFive() {
        ParticipantSelectionPlan plan = planFor(30);

        assertEquals(30, plan.activePlayerCount());
        assertEquals(6, plan.activeTeamCount());
        assertEquals(Set.of(5), Set.copyOf(plan.teamSizes().values()));
        assertEquals(5, plan.teamSizes().get(TeamId.AQUA));
    }

    private static ParticipantSelectionPlan planFor(int playerCount) {
        List<StartCandidate> candidates = java.util.stream.IntStream.rangeClosed(1, playerCount)
                .mapToObj(index -> candidate("six-team-" + index, 1500))
                .toList();
        return ParticipantSelectionService.select(
                candidates,
                MatchMode.NORMAL,
                Set.of(),
                false,
                new Random(0)
        ).orElseThrow();
    }

    private static int eloFor(UUID playerId, StartCandidate... candidates) {
        for (StartCandidate candidate : candidates) {
            if (candidate.uuid().equals(playerId)) {
                return candidate.displayElo();
            }
        }
        throw new IllegalArgumentException("Unknown playerId " + playerId);
    }

    private static StartCandidate candidate(String name, int displayElo) {
        return new StartCandidate(stableUuid(name), name, displayElo);
    }

    private static UUID stableUuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }
}
