package kim.biryeong.semiontd.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.rating.PlayerRatingProfile;
import kim.biryeong.semiontd.rating.RatingAdjustment;
import kim.biryeong.semiontd.rating.RatingContributionBreakdown;
import kim.biryeong.semiontd.rating.RatingMatchResult;
import kim.biryeong.semiontd.rating.RatingSystemId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RatingPersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void sqliteRatingRepositorySavesLoadsAndUpdatesProfile() {
        SQLiteRatingRepository repository = new SQLiteRatingRepository(tempDir.resolve("ratings.db"));
        UUID playerId = UUID.nameUUIDFromBytes("sqlite-rating-player".getBytes());
        PlayerRatingProfile first = new PlayerRatingProfile(
                playerId,
                "winner",
                RatingSystemId.ELO,
                1,
                1,
                1,
                0,
                1516.0,
                350.0,
                1516,
                new MatchId(101L),
                1000L
        );
        PlayerRatingProfile second = new PlayerRatingProfile(
                playerId,
                "winner-renamed",
                RatingSystemId.ELO,
                2,
                2,
                1,
                1,
                1500.0,
                350.0,
                1500,
                new MatchId(102L),
                2000L
        );

        repository.saveProfile(playerId, first);
        repository.saveProfile(playerId, second);

        PlayerRatingProfile loaded = repository.findProfile(playerId).orElseThrow();
        assertEquals("winner-renamed", loaded.lastKnownName());
        assertEquals(1500, loaded.displayElo());
        assertEquals(new MatchId(102L), loaded.lastUpdatedMatchId());
    }

    @Test
    void sqliteRatingRepositoryFindAllProfilesReturnsProfiles() {
        SQLiteRatingRepository repository = new SQLiteRatingRepository(tempDir.resolve("ratings.db"));
        UUID firstId = UUID.nameUUIDFromBytes("sqlite-rating-first".getBytes());
        UUID secondId = UUID.nameUUIDFromBytes("sqlite-rating-second".getBytes());

        repository.saveProfile(firstId, profile(firstId, "first", 1516));
        repository.saveProfile(secondId, profile(secondId, "second", 1484));

        Map<UUID, PlayerRatingProfile> profiles = repository.findAllProfiles();
        assertEquals(2, profiles.size());
        assertTrue(profiles.containsKey(firstId));
        assertTrue(profiles.containsKey(secondId));
    }

    @Test
    void sqliteRatingEventRepositorySavesLoadsAndIgnoresDuplicateMatch() {
        SQLiteRatingEventRepository repository = new SQLiteRatingEventRepository(tempDir.resolve("rating-events.db"));
        UUID playerId = UUID.nameUUIDFromBytes("sqlite-rating-event-player".getBytes());
        PlayerRatingProfile before = PlayerRatingProfile.initial(playerId, "player");
        PlayerRatingProfile after = profile(playerId, "player", 1516);
        RatingMatchResult first = new RatingMatchResult(
                new MatchId(201L),
                RatingSystemId.ELO,
                1,
                1000L,
                List.of(new RatingAdjustment(
                        playerId,
                        "player",
                        TeamId.RED,
                        true,
                        before,
                        after,
                        16.0,
                        16,
                        new RatingContributionBreakdown(1.10, 1.05, 1.00, 1.00, 1.06, 1.06)
                ))
        );
        RatingMatchResult duplicate = new RatingMatchResult(
                new MatchId(201L),
                RatingSystemId.ELO,
                1,
                2000L,
                List.of()
        );

        repository.saveMatchResult(first);
        repository.saveMatchResult(duplicate);

        Optional<RatingMatchResult> loaded = repository.findMatchResult(new MatchId(201L));
        assertTrue(loaded.isPresent());
        assertEquals(1, loaded.get().adjustments().size());
        assertEquals(1000L, loaded.get().appliedAtEpochMillis());
        assertEquals(1.06, loaded.get().adjustments().get(0).contribution().appliedMultiplier(), 0.0001);
    }

    private static PlayerRatingProfile profile(UUID playerId, String name, int elo) {
        return new PlayerRatingProfile(
                playerId,
                name,
                RatingSystemId.ELO,
                1,
                1,
                elo >= 1500 ? 1 : 0,
                elo < 1500 ? 1 : 0,
                elo,
                350.0,
                elo,
                new MatchId(101L),
                1000L
        );
    }
}
