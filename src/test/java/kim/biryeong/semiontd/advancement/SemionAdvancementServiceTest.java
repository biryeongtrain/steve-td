package kim.biryeong.semiontd.advancement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.PlayerMatchStats;
import kim.biryeong.semiontd.game.PlayerMatchStatsSnapshot;
import kim.biryeong.semiontd.game.TeamId;
import org.junit.jupiter.api.Test;

final class SemionAdvancementServiceTest {
    @Test
    void receivedThreatUsesOnlyCurrentRoundIncomeMonsters() {
        PlayerMatchStats stats = new PlayerMatchStats();
        stats.recordOwnLaneIncomingThreat(30_000.0, true);
        PlayerMatchStatsSnapshot start = stats.snapshot(10);

        stats.recordOwnLaneIncomingThreat(50_000.0, false);
        assertEquals(0.0, SemionAdvancementService.receivedIncomeThreatThisRound(
                start, stats.snapshot(10)));

        stats.recordOwnLaneIncomingThreat(20_000.0, true);
        assertEquals(20_000.0, SemionAdvancementService.receivedIncomeThreatThisRound(
                start, stats.snapshot(10)));
    }

    @Test
    void awardsOnlyVerifiedMatchConditions() {
        UUID redVeteran = UUID.randomUUID();
        UUID redPartner = UUID.randomUUID();
        UUID bluePerfect = UUID.randomUUID();
        UUID blueFailed = UUID.randomUUID();
        UUID blueFailedAgain = UUID.randomUUID();
        MatchResult result = new MatchResult(
                List.of(
                        participant(redVeteran, TeamId.RED, true, "semion-td:animal", List.of(1, 2), List.of(1, 2)),
                        participant(redPartner, TeamId.RED, true, "semion-td:nether", List.of(1, 2), List.of(1, 2)),
                        participant(bluePerfect, TeamId.BLUE, false, "semion-td:animal", List.of(1, 2), List.of(1, 2)),
                        participant(blueFailed, TeamId.BLUE, false, "semion-td:animal", List.of(1, 2), List.of(1)),
                        participant(blueFailedAgain, TeamId.BLUE, false, "semion-td:nether", List.of(1, 2), List.of())
                ),
                Set.of(),
                Set.of(TeamId.RED),
                20
        );

        Map<UUID, Set<net.minecraft.resources.ResourceLocation>> awards = SemionAdvancementService.matchAwards(
                result,
                Map.of(redVeteran, 100, redPartner, 9, bluePerfect, 10),
                Set.of(redVeteran, redPartner, bluePerfect)
        );

        assertEquals(Set.of(
                SemionAdvancementService.PERFECT_DEFENSE_WIN,
                SemionAdvancementService.DREAM_TEAM,
                SemionAdvancementService.UNDERDOG,
                SemionAdvancementService.NEWBIE_EXIT,
                SemionAdvancementService.VETERAN_100
        ), awards.get(redVeteran));
        assertEquals(Set.of(
                SemionAdvancementService.PERFECT_DEFENSE_WIN,
                SemionAdvancementService.DREAM_TEAM,
                SemionAdvancementService.UNDERDOG
        ), awards.get(redPartner));
        assertEquals(Set.of(
                SemionAdvancementService.TEAM_GAP_GG,
                SemionAdvancementService.NEWBIE_EXIT
        ), awards.get(bluePerfect));
        assertEquals(Set.of(), awards.getOrDefault(blueFailed, Set.of()));
        assertEquals(Set.of(), awards.getOrDefault(blueFailedAgain, Set.of()));
    }

    private static MatchParticipantResult participant(
            UUID playerId,
            TeamId teamId,
            boolean winner,
            String jobId,
            List<Integer> attemptedRounds,
            List<Integer> clearedRounds
    ) {
        return new MatchParticipantResult(
                playerId,
                playerId.toString(),
                teamId,
                winner,
                PlayerMatchStatsSnapshot.empty(),
                jobId,
                attemptedRounds,
                clearedRounds
        );
    }
}
