package kim.biryeong.semiontd.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.game.TeamId;
import org.junit.jupiter.api.Test;

final class EloRatingCalculatorTest {
    @Test
    void equalRatedWinnerGainsAndLoserLosesSixteenElo() {
        UUID winnerId = UUID.nameUUIDFromBytes("winner".getBytes());
        UUID loserId = UUID.nameUUIDFromBytes("loser".getBytes());
        RatingMatchInput input = new RatingMatchInput(
                new MatchId(1L),
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
        );

        RatingMatchResult result = new EloRatingCalculator().calculate(input);

        RatingAdjustment winner = result.adjustments().get(0);
        RatingAdjustment loser = result.adjustments().get(1);
        assertEquals(16, winner.displayEloDelta());
        assertEquals(-16, loser.displayEloDelta());
        assertEquals(1, winner.after().gamesPlayed());
        assertEquals(1, winner.after().wins());
        assertEquals(1, loser.after().losses());
    }

    @Test
    void underdogWinProducesLargerPositiveDeltaThanFavoriteWin() {
        UUID underdogId = UUID.nameUUIDFromBytes("underdog".getBytes());
        UUID favoriteId = UUID.nameUUIDFromBytes("favorite".getBytes());
        PlayerRatingProfile underdog = profile(underdogId, "underdog", 1200.0);
        PlayerRatingProfile favorite = profile(favoriteId, "favorite", 1800.0);
        RatingMatchResult result = new EloRatingCalculator().calculate(new RatingMatchInput(
                new MatchId(2L),
                1000L,
                List.of(
                        new RatingParticipant(underdogId, "underdog", TeamId.RED, true, underdog),
                        new RatingParticipant(favoriteId, "favorite", TeamId.BLUE, false, favorite)
                )
        ));

        assertTrue(result.adjustments().get(0).displayEloDelta() > 30);
        assertTrue(result.adjustments().get(1).displayEloDelta() < -30);
    }

    @Test
    void unevenTeamSizesDoNotInflateTotalDelta() {
        RatingMatchResult result = new EloRatingCalculator().calculate(new RatingMatchInput(
                new MatchId(3L),
                1000L,
                List.of(
                        participant("winner-a", TeamId.RED, true),
                        participant("winner-b", TeamId.RED, true),
                        participant("winner-c", TeamId.RED, true),
                        participant("loser-a", TeamId.BLUE, false),
                        participant("loser-b", TeamId.BLUE, false)
                )
        ));

        double winnerTotal = result.adjustments().stream()
                .filter(RatingAdjustment::winner)
                .mapToDouble(RatingAdjustment::muDelta)
                .sum();
        double loserTotal = result.adjustments().stream()
                .filter(adjustment -> !adjustment.winner())
                .mapToDouble(RatingAdjustment::muDelta)
                .sum();

        assertEquals(32.0, winnerTotal, 0.000001);
        assertEquals(-32.0, loserTotal, 0.000001);
    }

    private static RatingParticipant participant(String name, TeamId teamId, boolean winner) {
        UUID playerId = UUID.nameUUIDFromBytes(name.getBytes());
        return new RatingParticipant(playerId, name, teamId, winner, PlayerRatingProfile.initial(playerId, name));
    }

    private static PlayerRatingProfile profile(UUID playerId, String name, double mu) {
        return new PlayerRatingProfile(
                playerId,
                name,
                RatingSystemId.ELO,
                0,
                0,
                0,
                0,
                mu,
                PlayerRatingProfile.INITIAL_SIGMA,
                (int) Math.round(mu),
                null,
                0L
        );
    }
}
