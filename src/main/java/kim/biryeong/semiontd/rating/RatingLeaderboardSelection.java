package kim.biryeong.semiontd.rating;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

final class RatingLeaderboardSelection {
    private static final int FULL_SORT_SELECTION_RATIO = 8;
    private static final Comparator<PlayerRatingProfile> ORDER = Comparator
            .comparingInt(PlayerRatingProfile::displayElo).reversed()
            .thenComparing(Comparator.comparingInt(PlayerRatingProfile::gamesPlayed).reversed())
            .thenComparing(Comparator.comparingLong(PlayerRatingProfile::updatedAtEpochMillis).reversed())
            .thenComparing(PlayerRatingProfile::lastKnownName, String.CASE_INSENSITIVE_ORDER);

    private static final Comparator<RankedProfile> RANKED_ORDER = Comparator.comparing(RankedProfile::profile, ORDER)
            .thenComparingInt(RankedProfile::encounterOrder);

    private RatingLeaderboardSelection() {
    }

    static List<PlayerRatingProfile> select(Collection<PlayerRatingProfile> profiles, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        int boundedLimit = Math.min(100, limit);
        if (profiles.size() <= boundedLimit * FULL_SORT_SELECTION_RATIO) {
            return profiles.stream().filter(profile -> profile.gamesPlayed() > 0).sorted(ORDER).limit(boundedLimit).toList();
        }
        PriorityQueue<RankedProfile> selected = new PriorityQueue<>(boundedLimit, RANKED_ORDER.reversed());
        int encounterOrder = 0;
        for (PlayerRatingProfile profile : profiles) {
            if (profile.gamesPlayed() <= 0) {
                continue;
            }
            if (selected.size() < boundedLimit) {
                selected.add(new RankedProfile(profile, encounterOrder));
            } else if (ORDER.compare(profile, selected.peek().profile()) < 0) {
                selected.remove();
                selected.add(new RankedProfile(profile, encounterOrder));
            }
            encounterOrder++;
        }
        return selected.stream().sorted(RANKED_ORDER).map(RankedProfile::profile).toList();
    }

    private record RankedProfile(PlayerRatingProfile profile, int encounterOrder) {
    }
}
