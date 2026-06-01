package kim.biryeong.semiontd.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        UUID spectatorId = UUID.nameUUIDFromBytes("spectator".getBytes());
        MatchResult matchResult = new MatchResult(
                new MatchId(12L),
                1L,
                2L,
                List.of(
                        new MatchParticipantResult(winnerId, "winner", TeamId.RED, true),
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

        assertEquals(1, result.adjustments().size());
        assertTrue(ratingRepository.findProfile(spectatorId).isEmpty());
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
