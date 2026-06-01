package kim.biryeong.semiontd.persistence;

import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.game.MatchResult;

public interface MatchResultRepository {
    void saveMatchResult(MatchResult matchResult);

    Optional<MatchResult> findMatchResult(UUID matchId);
}
