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

    @Test
    void threeTeamPlacementEloKeepsMiddlePlacementNearNeutral() {
        RatingMatchResult result = new EloRatingCalculator().calculate(new RatingMatchInput(
                new MatchId(4L),
                1000L,
                List.of(
                        participant("first", TeamId.RED, true, 1),
                        participant("second", TeamId.BLUE, false, 2),
                        participant("third", TeamId.GREEN, false, 3)
                )
        ));

        RatingAdjustment first = result.adjustments().get(0);
        RatingAdjustment second = result.adjustments().get(1);
        RatingAdjustment third = result.adjustments().get(2);
        assertTrue(first.displayEloDelta() > 0);
        assertEquals(0, second.displayEloDelta());
        assertTrue(third.displayEloDelta() < 0);
        assertEquals(1, first.after().wins());
        assertEquals(1, second.after().losses());
        assertEquals(1, third.after().losses());
    }

    @Test
    void fourTeamPlacementEloRewardsTopHalfAndPenalizesBottomHalf() {
        RatingMatchResult result = new EloRatingCalculator().calculate(new RatingMatchInput(
                new MatchId(5L),
                1000L,
                List.of(
                        participant("first-4", TeamId.RED, true, 1),
                        participant("second-4", TeamId.BLUE, false, 2),
                        participant("third-4", TeamId.GREEN, false, 3),
                        participant("fourth-4", TeamId.YELLOW, false, 4)
                )
        ));

        List<Integer> deltas = result.adjustments().stream()
                .map(RatingAdjustment::displayEloDelta)
                .toList();
        assertEquals(List.of(16, 13, -13, -16), deltas);
    }

    @Test
    void fiveTeamPlacementEloDeltasAreMonotonicByPlacement() {
        RatingMatchResult result = new EloRatingCalculator().calculate(new RatingMatchInput(
                new MatchId(6L),
                1000L,
                List.of(
                        participant("first", TeamId.RED, true, 1),
                        participant("second", TeamId.BLUE, false, 2),
                        participant("third", TeamId.GREEN, false, 3),
                        participant("fourth", TeamId.YELLOW, false, 4),
                        participant("fifth", TeamId.PURPLE, false, 5)
                )
        ));

        List<Integer> deltas = result.adjustments().stream()
                .map(RatingAdjustment::displayEloDelta)
                .toList();
        assertEquals(List.of(16, 13, 0, -13, -16), deltas);
    }

    @Test
    void secondPlaceOnlyLosesWhenHeavilyFavored() {
        UUID favoriteId = UUID.nameUUIDFromBytes("favored-second".getBytes());
        RatingMatchResult result = new EloRatingCalculator().calculate(new RatingMatchInput(
                new MatchId(7L),
                1000L,
                List.of(
                        participant("first-vs-favored", TeamId.RED, true, 1),
                        new RatingParticipant(
                                favoriteId,
                                "favored-second",
                                TeamId.BLUE,
                                false,
                                2,
                                1.0,
                                profile(favoriteId, "favored-second", 1800.0)
                        ),
                        participant("third-vs-favored", TeamId.GREEN, false, 3),
                        participant("fourth-vs-favored", TeamId.YELLOW, false, 4)
                )
        ));

        assertTrue(result.adjustments().get(1).displayEloDelta() > 0);
    }

    @Test
    void underdogFirstPlaceAgainstMultipleFavoritesGainsMoreThanFavoriteFirstPlace() {
        UUID underdogId = UUID.nameUUIDFromBytes("multi-underdog".getBytes());
        UUID favoriteAId = UUID.nameUUIDFromBytes("multi-favorite-a".getBytes());
        UUID favoriteBId = UUID.nameUUIDFromBytes("multi-favorite-b".getBytes());
        RatingMatchResult upset = new EloRatingCalculator().calculate(new RatingMatchInput(
                new MatchId(6L),
                1000L,
                List.of(
                        new RatingParticipant(underdogId, "underdog", TeamId.RED, true, 1, 1.0, profile(underdogId, "underdog", 1200.0)),
                        new RatingParticipant(favoriteAId, "favoriteA", TeamId.BLUE, false, 2, 1.0, profile(favoriteAId, "favoriteA", 1800.0)),
                        new RatingParticipant(favoriteBId, "favoriteB", TeamId.GREEN, false, 3, 1.0, profile(favoriteBId, "favoriteB", 1800.0))
                )
        ));
        RatingMatchResult expectedFavoriteWin = new EloRatingCalculator().calculate(new RatingMatchInput(
                new MatchId(7L),
                1000L,
                List.of(
                        new RatingParticipant(favoriteAId, "favoriteA", TeamId.BLUE, true, 1, 1.0, profile(favoriteAId, "favoriteA", 1800.0)),
                        new RatingParticipant(underdogId, "underdog", TeamId.RED, false, 2, 1.0, profile(underdogId, "underdog", 1200.0)),
                        new RatingParticipant(favoriteBId, "favoriteB", TeamId.GREEN, false, 3, 1.0, profile(favoriteBId, "favoriteB", 1800.0))
                )
        ));

        assertTrue(upset.adjustments().get(0).displayEloDelta() > expectedFavoriteWin.adjustments().get(0).displayEloDelta());
    }

    private static RatingParticipant participant(String name, TeamId teamId, boolean winner) {
        UUID playerId = UUID.nameUUIDFromBytes(name.getBytes());
        return new RatingParticipant(playerId, name, teamId, winner, PlayerRatingProfile.initial(playerId, name));
    }

    private static RatingParticipant participant(String name, TeamId teamId, boolean winner, int placement) {
        UUID playerId = UUID.nameUUIDFromBytes(name.getBytes());
        return new RatingParticipant(playerId, name, teamId, winner, placement, 1.0, PlayerRatingProfile.initial(playerId, name));
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
