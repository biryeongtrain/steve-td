package kim.biryeong.semiontd.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.MatchResultGroup;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamMatchResult;
import org.junit.jupiter.api.Test;

final class RatingEligibilityPolicyTest {
    @Test
    void winnerAndLoserMatchIsEligible() {
        RatingEligibilityPolicy policy = new RatingEligibilityPolicy(RatingConfig.defaultConfig());

        assertTrue(policy.isEligible(matchResult(
                List.of(participant("winner", TeamId.RED, true), participant("loser", TeamId.BLUE, false)),
                Set.of(),
                Set.of(TeamId.RED)
        )));
    }

    @Test
    void disabledConfigSkipsRating() {
        RatingEligibilityPolicy policy = new RatingEligibilityPolicy(new RatingConfig(
                false,
                32.0,
                1500,
                1500.0,
                350.0,
                10,
                2,
                true
        ));

        assertFalse(policy.isEligible(matchResult(
                List.of(participant("winner", TeamId.RED, true), participant("loser", TeamId.BLUE, false)),
                Set.of(),
                Set.of(TeamId.RED)
        )));
    }

    @Test
    void oneParticipantMatchIsNotEligible() {
        RatingEligibilityPolicy policy = new RatingEligibilityPolicy(RatingConfig.defaultConfig());

        assertEquals("not enough rating-eligible participants", policy.skippedReason(matchResult(
                List.of(participant("winner", TeamId.RED, true)),
                Set.of(),
                Set.of(TeamId.RED)
        )));
    }

    @Test
    void spectatorOnlyOpponentIsNotEligible() {
        RatingEligibilityPolicy policy = new RatingEligibilityPolicy(RatingConfig.defaultConfig());
        MatchParticipantResult winner = participant("winner", TeamId.RED, true);
        MatchParticipantResult spectator = participant("spectator", TeamId.BLUE, false);

        assertFalse(policy.isEligible(matchResult(
                List.of(winner, spectator),
                Set.of(spectator.playerId()),
                Set.of(TeamId.RED)
        )));
    }

    @Test
    void participantWinnerFlagMustMatchWinningTeams() {
        RatingEligibilityPolicy policy = new RatingEligibilityPolicy(RatingConfig.defaultConfig());

        assertEquals("participant winner flag does not match team placement result", policy.skippedReason(matchResult(
                List.of(participant("winner", TeamId.RED, false), participant("loser", TeamId.BLUE, false)),
                Set.of(),
                Set.of(TeamId.RED)
        )));
    }

    @Test
    void multipleWinningTeamsAreNotEligibleUntilTiePolicyExists() {
        RatingEligibilityPolicy policy = new RatingEligibilityPolicy(RatingConfig.defaultConfig());

        assertEquals("rating requires exactly one winning team", policy.skippedReason(matchResult(
                List.of(participant("winner", TeamId.RED, true), participant("otherWinner", TeamId.BLUE, true)),
                Set.of(),
                Set.of(TeamId.RED, TeamId.BLUE)
        )));
    }

    @Test
    void drawOrUnratedParticipantTeamIsNotEligible() {
        RatingEligibilityPolicy policy = new RatingEligibilityPolicy(RatingConfig.defaultConfig());
        MatchParticipantResult winner = participant("winner", TeamId.RED, true);
        MatchParticipantResult unrated = participant("unrated", TeamId.BLUE, false);
        MatchResult result = new MatchResult(
                new MatchId(302L),
                1L,
                2L,
                List.of(winner, unrated),
                Set.of(),
                Set.of(TeamId.RED),
                List.of(
                        new TeamMatchResult(TeamId.RED, 1, MatchResultGroup.WIN_GROUP, 1.0, 1, 1, 0.0),
                        new TeamMatchResult(TeamId.BLUE, 2, MatchResultGroup.DRAW_OR_UNRATED, 0.0, 1, 1, 0.0)
                ),
                10
        );

        assertEquals("rating does not support draw or unrated participant teams yet", policy.skippedReason(result));
    }

    @Test
    void threeTeamPlacementMatchIsEligible() {
        RatingEligibilityPolicy policy = new RatingEligibilityPolicy(RatingConfig.defaultConfig());

        assertTrue(policy.isEligible(matchResult(
                List.of(
                        participant("winner", TeamId.RED, true),
                        participant("second", TeamId.BLUE, false),
                        participant("third", TeamId.GREEN, false)
                ),
                Set.of(),
                Set.of(TeamId.RED),
                List.of(
                        new TeamMatchResult(TeamId.RED, 1, MatchResultGroup.WIN_GROUP, 1.0, 1, 1, 0.0),
                        new TeamMatchResult(TeamId.BLUE, 2, MatchResultGroup.LOSS_GROUP, 0.0, 1, 1, 0.0),
                        new TeamMatchResult(TeamId.GREEN, 3, MatchResultGroup.LOSS_GROUP, 0.0, 1, 1, 0.0)
                )
        )));
    }

    @Test
    void fiveTeamPlacementMatchIsEligible() {
        RatingEligibilityPolicy policy = new RatingEligibilityPolicy(RatingConfig.defaultConfig());

        assertTrue(policy.isEligible(matchResult(
                List.of(
                        participant("winner", TeamId.RED, true),
                        participant("second", TeamId.BLUE, false),
                        participant("third", TeamId.GREEN, false),
                        participant("fourth", TeamId.YELLOW, false),
                        participant("fifth", TeamId.PURPLE, false)
                ),
                Set.of(),
                Set.of(TeamId.RED),
                List.of(
                        new TeamMatchResult(TeamId.RED, 1, MatchResultGroup.WIN_GROUP, 1.0, 1, 1, 0.0),
                        new TeamMatchResult(TeamId.BLUE, 2, MatchResultGroup.LOSS_GROUP, 0.0, 1, 1, 0.0),
                        new TeamMatchResult(TeamId.GREEN, 3, MatchResultGroup.LOSS_GROUP, 0.0, 1, 1, 0.0),
                        new TeamMatchResult(TeamId.YELLOW, 4, MatchResultGroup.LOSS_GROUP, 0.0, 1, 1, 0.0),
                        new TeamMatchResult(TeamId.PURPLE, 5, MatchResultGroup.LOSS_GROUP, 0.0, 1, 1, 0.0)
                )
        )));
    }

    @Test
    void duplicatePlacementIsNotEligible() {
        RatingEligibilityPolicy policy = new RatingEligibilityPolicy(RatingConfig.defaultConfig());

        assertEquals("rating requires unique contiguous team placements", policy.skippedReason(matchResult(
                List.of(
                        participant("winner", TeamId.RED, true),
                        participant("second", TeamId.BLUE, false),
                        participant("third", TeamId.GREEN, false)
                ),
                Set.of(),
                Set.of(TeamId.RED),
                List.of(
                        new TeamMatchResult(TeamId.RED, 1, MatchResultGroup.WIN_GROUP, 1.0, 1, 1, 0.0),
                        new TeamMatchResult(TeamId.BLUE, 2, MatchResultGroup.LOSS_GROUP, 0.0, 1, 1, 0.0),
                        new TeamMatchResult(TeamId.GREEN, 2, MatchResultGroup.LOSS_GROUP, 0.0, 1, 1, 0.0)
                )
        )));
    }

    @Test
    void duplicateTeamResultIsNotEligible() {
        RatingEligibilityPolicy policy = new RatingEligibilityPolicy(RatingConfig.defaultConfig());

        assertEquals("rating requires one team result per participant team", policy.skippedReason(matchResult(
                List.of(
                        participant("winner", TeamId.RED, true),
                        participant("second", TeamId.BLUE, false)
                ),
                Set.of(),
                Set.of(TeamId.RED),
                List.of(
                        new TeamMatchResult(TeamId.RED, 1, MatchResultGroup.WIN_GROUP, 1.0, 1, 1, 0.0),
                        new TeamMatchResult(TeamId.RED, 1, MatchResultGroup.WIN_GROUP, 1.0, 1, 1, 0.0),
                        new TeamMatchResult(TeamId.BLUE, 2, MatchResultGroup.LOSS_GROUP, 0.0, 1, 1, 0.0)
                )
        )));
    }

    @Test
    void missingTeamResultIsNotEligible() {
        RatingEligibilityPolicy policy = new RatingEligibilityPolicy(RatingConfig.defaultConfig());

        assertEquals("rating requires team results for every participant team", policy.skippedReason(matchResult(
                List.of(
                        participant("winner", TeamId.RED, true),
                        participant("second", TeamId.BLUE, false),
                        participant("third", TeamId.GREEN, false)
                ),
                Set.of(),
                Set.of(TeamId.RED),
                List.of(
                        new TeamMatchResult(TeamId.RED, 1, MatchResultGroup.WIN_GROUP, 1.0, 1, 1, 0.0),
                        new TeamMatchResult(TeamId.BLUE, 2, MatchResultGroup.LOSS_GROUP, 0.0, 1, 1, 0.0)
                )
        )));
    }

    @Test
    void sameTeamOnlyMatchIsNotEligible() {
        RatingEligibilityPolicy policy = new RatingEligibilityPolicy(RatingConfig.defaultConfig());

        assertEquals("rating requires between two and five participant teams", policy.skippedReason(matchResult(
                List.of(participant("winner", TeamId.RED, true), participant("teammate", TeamId.RED, true)),
                Set.of(),
                Set.of(TeamId.RED)
        )));
    }

    private static MatchParticipantResult participant(String name, TeamId teamId, boolean winner) {
        return new MatchParticipantResult(UUID.nameUUIDFromBytes(name.getBytes()), name, teamId, winner);
    }

    private static MatchResult matchResult(
            List<MatchParticipantResult> participants,
            Set<UUID> spectatorIds,
            Set<TeamId> winningTeams
    ) {
        return matchResult(
                participants,
                spectatorIds,
                winningTeams,
                List.of(
                        new TeamMatchResult(TeamId.RED, 1, MatchResultGroup.WIN_GROUP, 1.0, 1, 1, 0.0),
                        new TeamMatchResult(TeamId.BLUE, 2, MatchResultGroup.LOSS_GROUP, 0.0, 1, 1, 0.0)
                )
        );
    }

    private static MatchResult matchResult(
            List<MatchParticipantResult> participants,
            Set<UUID> spectatorIds,
            Set<TeamId> winningTeams,
            List<TeamMatchResult> teamResults
    ) {
        return new MatchResult(
                new MatchId(301L),
                1L,
                2L,
                participants,
                spectatorIds,
                winningTeams,
                teamResults,
                10
        );
    }
}
