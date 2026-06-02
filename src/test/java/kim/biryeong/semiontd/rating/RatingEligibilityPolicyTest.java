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

        assertEquals("participant winner flag does not match winning teams", policy.skippedReason(matchResult(
                List.of(participant("winner", TeamId.RED, false), participant("loser", TeamId.BLUE, false)),
                Set.of(),
                Set.of(TeamId.RED)
        )));
    }

    @Test
    void multipleWinningTeamsAreNotEligibleUntilPlacementRatingExists() {
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
    void sameTeamOnlyMatchIsNotEligible() {
        RatingEligibilityPolicy policy = new RatingEligibilityPolicy(RatingConfig.defaultConfig());

        assertEquals("rating requires exactly two participant teams", policy.skippedReason(matchResult(
                List.of(participant("winner", TeamId.RED, true), participant("teammate", TeamId.RED, true)),
                Set.of(),
                Set.of(TeamId.RED)
        )));
    }

    @Test
    void moreThanTwoParticipantTeamsAreNotEligibleForBinaryElo() {
        RatingEligibilityPolicy policy = new RatingEligibilityPolicy(RatingConfig.defaultConfig());

        assertEquals("rating requires exactly two participant teams", policy.skippedReason(matchResult(
                List.of(
                        participant("winner", TeamId.RED, true),
                        participant("loser", TeamId.BLUE, false),
                        participant("third", TeamId.GREEN, false)
                ),
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
        return new MatchResult(
                new MatchId(301L),
                1L,
                2L,
                participants,
                spectatorIds,
                winningTeams,
                List.of(
                        new TeamMatchResult(TeamId.RED, 1, MatchResultGroup.WIN_GROUP, 1.0, 1, 1, 0.0),
                        new TeamMatchResult(TeamId.BLUE, 2, MatchResultGroup.LOSS_GROUP, 0.0, 1, 1, 0.0)
                ),
                10
        );
    }
}
