package kim.biryeong.semiontd.rating;

import java.util.List;
import kim.biryeong.semiontd.game.TeamId;

public record TeamRatingSnapshot(
        TeamId teamId,
        boolean winner,
        List<PlayerRatingProfile> players,
        double averageMu
) {
    public TeamRatingSnapshot {
        players = List.copyOf(players);
    }
}
