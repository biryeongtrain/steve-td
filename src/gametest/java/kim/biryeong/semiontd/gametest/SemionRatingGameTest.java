package kim.biryeong.semiontd.gametest;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.MatchResultGroup;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamMatchResult;
import kim.biryeong.semiontd.rating.EloRatingCalculator;
import kim.biryeong.semiontd.rating.PlayerRatingProfile;
import kim.biryeong.semiontd.rating.RatingMatchInput;
import kim.biryeong.semiontd.rating.RatingMatchResult;
import kim.biryeong.semiontd.rating.RatingParticipant;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class SemionRatingGameTest {
    @GameTest
    public void eloCalculatorKeepsRatingChangesFinite(GameTestHelper context) {
        UUID winnerId = UUID.nameUUIDFromBytes("gametest-rating-winner".getBytes());
        UUID loserId = UUID.nameUUIDFromBytes("gametest-rating-loser".getBytes());
        RatingMatchResult result = new EloRatingCalculator().calculate(new RatingMatchInput(
                new MatchId(101L),
                1000L,
                List.of(
                        new RatingParticipant(
                                winnerId,
                                "winner",
                                TeamId.RED,
                                true,
                                PlayerRatingProfile.initial(winnerId, "winner")
                        ),
                        new RatingParticipant(
                                loserId,
                                "loser",
                                TeamId.BLUE,
                                false,
                                PlayerRatingProfile.initial(loserId, "loser")
                        )
                )
        ));

        if (result.adjustments().size() != 2) {
            throw new AssertionError("Expected two rating adjustments");
        }
        result.adjustments().forEach(adjustment -> {
            if (!Double.isFinite(adjustment.after().mu()) || !Double.isFinite(adjustment.after().sigma())) {
                throw new AssertionError("Rating values must stay finite");
            }
        });
        context.succeed();
    }

    @GameTest
    public void matchResultCanFeedRatingCalculatorWithoutSpectators(GameTestHelper context) {
        UUID winnerId = UUID.nameUUIDFromBytes("gametest-match-winner".getBytes());
        UUID spectatorId = UUID.nameUUIDFromBytes("gametest-match-spectator".getBytes());
        MatchResult matchResult = new MatchResult(
                new MatchId(102L),
                1L,
                2L,
                List.of(
                        new MatchParticipantResult(winnerId, "winner", TeamId.RED, true),
                        new MatchParticipantResult(spectatorId, "spectator", TeamId.BLUE, false)
                ),
                Set.of(spectatorId),
                Set.of(TeamId.RED),
                List.of(
                        new TeamMatchResult(TeamId.RED, 1, MatchResultGroup.WIN_GROUP, 1.0, 1, 1L, 0.0),
                        new TeamMatchResult(TeamId.BLUE, 2, MatchResultGroup.LOSS_GROUP, 0.0, 1, 1L, 0.0)
                ),
                10
        );
        List<RatingParticipant> ratingParticipants = matchResult.participants().stream()
                .filter(participant -> !matchResult.spectatorIds().contains(participant.playerId()))
                .map(participant -> new RatingParticipant(
                        participant.playerId(),
                        participant.playerName(),
                        participant.teamId(),
                        participant.winner(),
                        PlayerRatingProfile.initial(participant.playerId(), participant.playerName())
                ))
                .toList();

        RatingMatchResult ratingResult = new EloRatingCalculator().calculate(new RatingMatchInput(
                matchResult.matchId(),
                matchResult.endedAtEpochMillis(),
                ratingParticipants
        ));

        if (ratingResult.adjustments().size() != 1) {
            throw new AssertionError("Spectator must not receive a rating adjustment");
        }
        context.succeed();
    }
}
