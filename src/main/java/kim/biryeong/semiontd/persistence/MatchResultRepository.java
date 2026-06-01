package kim.biryeong.semiontd.persistence;

import java.util.Optional;
import kim.biryeong.semiontd.game.MatchResult;
import kim.biryeong.semiontd.game.MatchId;

public interface MatchResultRepository {
    void saveMatchResult(MatchResult matchResult);

    Optional<MatchResult> findMatchResult(MatchId matchId);
}
