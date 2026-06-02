package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    void previousSpectatorPriorityIsAppliedBeforeEloActiveCutoff() {
        List<StartCandidate> candidates = java.util.stream.IntStream.rangeClosed(1, 30)
                .mapToObj(index -> candidate("candidate-" + index, index <= 25 ? 2000 : 1000))
                .toList();
        Set<UUID> readyPlayerIds = candidates.stream()
                .map(StartCandidate::uuid)
                .collect(Collectors.toUnmodifiableSet());
        Set<UUID> priorityPlayerIds = Set.of(
                stableUuid("candidate-26"),
                stableUuid("candidate-27"),
                stableUuid("candidate-28"),
                stableUuid("candidate-29"),
                stableUuid("candidate-30")
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
