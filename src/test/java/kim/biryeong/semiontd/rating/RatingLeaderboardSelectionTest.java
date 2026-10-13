package kim.biryeong.semiontd.rating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class RatingLeaderboardSelectionTest {
    @Test
    void matchesStableFullSortAcrossInputSizesLimitsAndRankingTies() {
        Random random = new Random(497183L);
        for (int size : new int[]{0, 1, 6, 32, 79, 80, 81, 100, 101, 799, 800, 801, 1024}) {
            List<PlayerRatingProfile> profiles = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                profiles.add(profile(i, random.nextInt(5), random.nextInt(7), random.nextInt(3),
                        i % 2 == 0 ? "ALPHA" : "alpha"));
            }
            for (int limit : new int[]{-1, 0, 1, 3, 10, 100, 101, Integer.MAX_VALUE}) {
                assertEquals(reference(profiles, limit), RatingLeaderboardSelection.select(profiles, limit),
                        "size=" + size + ", limit=" + limit);
            }
        }
    }

    @Test
    void retainsEarlierTiesAtTheCutoffEvenAfterHigherRankedEntriesArrive() {
        List<PlayerRatingProfile> profiles = new ArrayList<>();
        profiles.add(profile(1, 1, 1500, 1, "Alpha"));
        profiles.add(profile(2, 1, 1500, 1, "ALPHA"));
        profiles.add(profile(3, 1, 1500, 1, "alpha"));
        profiles.add(profile(4, 0, 9000, 1, "Unrated"));
        profiles.add(profile(5, 1, 1600, 1, "Higher"));
        var selected = RatingLeaderboardSelection.select(profiles, 2);
        assertEquals(List.of(profiles.get(4), profiles.getFirst()), selected);
        assertThrows(UnsupportedOperationException.class, () -> selected.add(profiles.getFirst()));
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L),
                profiles.stream().map(profile -> profile.playerId().getLeastSignificantBits()).toList());
    }

    @Test
    void reselectsChangedProfilesWithoutStaleCachedRanksAndHandlesExtremeElo() {
        var lower = profile(1, 1, Integer.MIN_VALUE, 1, "a");
        var higher = profile(2, 1, Integer.MAX_VALUE, 1, "b");
        List<PlayerRatingProfile> profiles = new ArrayList<>(List.of(lower, higher));
        assertEquals(List.of(higher), RatingLeaderboardSelection.select(profiles, 1));
        profiles.remove(higher);
        assertEquals(List.of(lower), RatingLeaderboardSelection.select(profiles, 1));
        profiles.clear();
        profiles.add(profile(3, 0, 1500, 0, "No games"));
        assertEquals(List.of(), RatingLeaderboardSelection.select(profiles, 1));
    }

    private static List<PlayerRatingProfile> reference(List<PlayerRatingProfile> profiles, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return profiles.stream().filter(profile -> profile.gamesPlayed() > 0)
                .sorted(Comparator.comparingInt(PlayerRatingProfile::displayElo).reversed()
                        .thenComparing(Comparator.comparingInt(PlayerRatingProfile::gamesPlayed).reversed())
                        .thenComparing(Comparator.comparingLong(PlayerRatingProfile::updatedAtEpochMillis).reversed())
                        .thenComparing(PlayerRatingProfile::lastKnownName, String.CASE_INSENSITIVE_ORDER))
                .limit(Math.min(100, limit)).toList();
    }

    private static PlayerRatingProfile profile(long id, int games, int elo, long updated, String name) {
        return new PlayerRatingProfile(new UUID(0L, id), name, RatingSystemId.ELO, 1, games, 0, 0,
                elo, 350.0, elo, null, updated);
    }
}
