package kim.biryeong.semiontd.persistence;

import java.util.Optional;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.rating.RatingMatchResult;

public interface RatingEventRepository {
    void saveMatchResult(RatingMatchResult ratingMatchResult);

    Optional<RatingMatchResult> findMatchResult(MatchId matchId);
}
