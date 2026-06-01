package kim.biryeong.semiontd.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.game.MatchParticipantResult;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.MatchResultGroup;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamMatchResult;
import kim.biryeong.semiontd.persistence.FileAppliedMatchRepository;
import kim.biryeong.semiontd.persistence.FileRatingEventRepository;
import kim.biryeong.semiontd.persistence.FileRatingRepository;
import kim.biryeong.semiontd.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RatingServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void appliesMatchResultOnceAndWritesProfilesAndEvents() {
        FileRatingRepository ratingRepository = new FileRatingRepository(tempDir.resolve("ratings.json"));
        FileRatingEventRepository eventRepository = new FileRatingEventRepository(tempDir.resolve("rating-events.json"));
        RatingService service = new RatingService(
                ratingRepository,
                eventRepository,
                new FileAppliedMatchRepository(tempDir.resolve("rating-applied-matches.json")),
                new EloRatingCalculator()
        );
        UUID winnerId = UUID.nameUUIDFromBytes("service-winner".getBytes());
        UUID loserId = UUID.nameUUIDFromBytes("service-loser".getBytes());
        MatchResult matchResult = matchResult(new MatchId(11L), winnerId, loserId);

        RatingMatchResult first = service.applyMatchResult(null, matchResult);
        RatingMatchResult second = service.applyMatchResult(null, matchResult);

        assertEquals(2, first.adjustments().size());
        assertEquals(first, second);
        assertEquals(1516, ratingRepository.findProfile(winnerId).orElseThrow().displayElo());
        assertEquals(1484, ratingRepository.findProfile(loserId).orElseThrow().displayElo());
        assertTrue(eventRepository.findMatchResult(matchResult.matchId()).isPresent());
    }

    @Test
    void excludesSpectatorsFromRatingAdjustments() {
        FileRatingRepository ratingRepository = new FileRatingRepository(tempDir.resolve("ratings.json"));
        RatingService service = new RatingService(
                ratingRepository,
                new FileRatingEventRepository(tempDir.resolve("rating-events.json")),
                new FileAppliedMatchRepository(tempDir.resolve("rating-applied-matches.json")),
                new EloRatingCalculator()
        );
        UUID winnerId = UUID.nameUUIDFromBytes("spectator-winner".getBytes());
        UUID loserId = UUID.nameUUIDFromBytes("spectator-loser".getBytes());
        UUID spectatorId = UUID.nameUUIDFromBytes("spectator".getBytes());
        MatchResult matchResult = new MatchResult(
                new MatchId(12L),
                1L,
                2L,
                List.of(
                        new MatchParticipantResult(winnerId, "winner", TeamId.RED, true),
                        new MatchParticipantResult(loserId, "loser", TeamId.BLUE, false),
                        new MatchParticipantResult(spectatorId, "spectator", TeamId.BLUE, false)
                ),
                Set.of(spectatorId),
                Set.of(TeamId.RED),
                List.of(
                        new TeamMatchResult(TeamId.RED, 1, MatchResultGroup.WIN_GROUP, 1.0, 1, 1, 0.0),
                        new TeamMatchResult(TeamId.BLUE, 2, MatchResultGroup.LOSS_GROUP, 0.0, 1, 1, 0.0)
                ),
                10
        );

        RatingMatchResult result = service.applyMatchResult(null, matchResult);

        assertEquals(2, result.adjustments().size());
        assertTrue(ratingRepository.findProfile(spectatorId).isEmpty());
    }

    @Test
    void topProfilesAreSortedByEloThenGamesPlayed() {
        FileRatingRepository ratingRepository = new FileRatingRepository(tempDir.resolve("ratings.json"));
        RatingService service = new RatingService(
                ratingRepository,
                new FileRatingEventRepository(tempDir.resolve("rating-events.json")),
                new FileAppliedMatchRepository(tempDir.resolve("rating-applied-matches.json")),
                new EloRatingCalculator()
        );
        UUID strongerId = UUID.nameUUIDFromBytes("stronger".getBytes());
        UUID moreGamesId = UUID.nameUUIDFromBytes("more-games".getBytes());
        UUID fewerGamesId = UUID.nameUUIDFromBytes("fewer-games".getBytes());
        ratingRepository.saveProfile(strongerId, profile(strongerId, "stronger", 1600, 1));
        ratingRepository.saveProfile(moreGamesId, profile(moreGamesId, "moreGames", 1500, 3));
        ratingRepository.saveProfile(fewerGamesId, profile(fewerGamesId, "fewerGames", 1500, 1));

        List<PlayerRatingProfile> top = service.topProfiles(3);

        assertEquals(List.of(strongerId, moreGamesId, fewerGamesId), top.stream().map(PlayerRatingProfile::playerId).toList());
    }

    @Test
    void profileAlreadyUpdatedForMatchStopsUnsafeReplayWithoutNewEvent() {
        FileRatingRepository ratingRepository = new FileRatingRepository(tempDir.resolve("ratings.json"));
        FileRatingEventRepository eventRepository = new FileRatingEventRepository(tempDir.resolve("rating-events.json"));
        FileAppliedMatchRepository appliedMatchRepository = new FileAppliedMatchRepository(tempDir.resolve("rating-applied-matches.json"));
        RatingService service = new RatingService(
                ratingRepository,
                eventRepository,
                appliedMatchRepository,
                new EloRatingCalculator()
        );
        UUID winnerId = UUID.nameUUIDFromBytes("partial-winner".getBytes());
        UUID loserId = UUID.nameUUIDFromBytes("partial-loser".getBytes());
        MatchId matchId = new MatchId(13L);
        ratingRepository.saveProfile(winnerId, profileForMatch(winnerId, "winner", 1516, matchId));

        assertThrows(PersistenceException.class, () -> service.applyMatchResult(null, matchResult(matchId, winnerId, loserId)));

        assertEquals(1516, ratingRepository.findProfile(winnerId).orElseThrow().displayElo());
        assertTrue(ratingRepository.findProfile(loserId).isEmpty());
        assertTrue(eventRepository.findMatchResult(matchId).isEmpty());
        assertTrue(!appliedMatchRepository.hasApplied(matchId, "rating"));
    }

    private static PlayerRatingProfile profile(UUID playerId, String name, int elo, int gamesPlayed) {
        return new PlayerRatingProfile(
                playerId,
                name,
                RatingSystemId.ELO,
                gamesPlayed,
                gamesPlayed,
                gamesPlayed,
                0,
                elo,
                350.0,
                elo,
                new MatchId(99L),
                1000L + gamesPlayed
        );
    }

    private static PlayerRatingProfile profileForMatch(UUID playerId, String name, int elo, MatchId matchId) {
        return new PlayerRatingProfile(
                playerId,
                name,
                RatingSystemId.ELO,
                1,
                1,
                1,
                0,
                elo,
                350.0,
                elo,
                matchId,
                1000L
        );
    }

    private static MatchResult matchResult(MatchId matchId, UUID winnerId, UUID loserId) {
        return new MatchResult(
                matchId,
                1L,
                2L,
                List.of(
                        new MatchParticipantResult(winnerId, "winner", TeamId.RED, true),
                        new MatchParticipantResult(loserId, "loser", TeamId.BLUE, false)
                ),
                Set.of(),
                Set.of(TeamId.RED),
                List.of(
                        new TeamMatchResult(TeamId.RED, 1, MatchResultGroup.WIN_GROUP, 1.0, 1, 1, 0.0),
                        new TeamMatchResult(TeamId.BLUE, 2, MatchResultGroup.LOSS_GROUP, 0.0, 1, 1, 0.0)
                ),
                10
        );
    }
}
