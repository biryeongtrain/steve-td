package kim.biryeong.semiontd.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.MatchId;
import kim.biryeong.semiontd.game.MatchResult;

public final class CascadingMatchResultRepository implements MatchResultRepository {
    private final List<MatchResultRepository> repositories;

    public CascadingMatchResultRepository(MatchResultRepository sqlite, MatchResultRepository file, MatchResultRepository log) {
        this.repositories = List.of(
                Objects.requireNonNull(sqlite, "sqlite"),
                Objects.requireNonNull(file, "file"),
                Objects.requireNonNull(log, "log")
        );
    }

    @Override
    public synchronized void saveMatchResult(MatchResult matchResult) {
        RuntimeException lastFailure = null;
        for (MatchResultRepository repository : repositories) {
            try {
                repository.saveMatchResult(matchResult);
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                SemionTd.LOGGER.warn("Match result repository {} failed; trying fallback.", repository.getClass().getSimpleName(), exception);
            }
        }
        throw new PersistenceException("All match result persistence layers failed", lastFailure);
    }

    @Override
    public synchronized Optional<MatchResult> findMatchResult(MatchId matchId) {
        for (MatchResultRepository repository : repositories) {
            try {
                Optional<MatchResult> result = repository.findMatchResult(matchId);
                if (result.isPresent()) {
                    return result;
                }
            } catch (RuntimeException exception) {
                SemionTd.LOGGER.warn("Match result repository {} failed while loading.", repository.getClass().getSimpleName(), exception);
            }
        }
        return Optional.empty();
    }
}
