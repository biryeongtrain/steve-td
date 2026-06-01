package kim.biryeong.semiontd.persistence;

import java.util.List;
import java.util.Objects;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.MatchId;

public final class CascadingAppliedMatchRepository implements AppliedMatchRepository {
    private final List<AppliedMatchRepository> repositories;

    public CascadingAppliedMatchRepository(
            AppliedMatchRepository sqlite,
            AppliedMatchRepository file,
            AppliedMatchRepository log
    ) {
        this.repositories = List.of(
                Objects.requireNonNull(sqlite, "sqlite"),
                Objects.requireNonNull(file, "file"),
                Objects.requireNonNull(log, "log")
        );
    }

    @Override
    public synchronized boolean hasApplied(MatchId matchId, String subsystem) {
        for (AppliedMatchRepository repository : repositories) {
            try {
                if (repository.hasApplied(matchId, subsystem)) {
                    return true;
                }
            } catch (RuntimeException exception) {
                SemionTd.LOGGER.warn(
                        "Applied-match repository {} failed while loading; trying fallback.",
                        repository.getClass().getSimpleName(),
                        exception
                );
            }
        }
        return false;
    }

    @Override
    public synchronized boolean markApplied(MatchId matchId, String subsystem, long appliedAtEpochMillis) {
        if (hasApplied(matchId, subsystem)) {
            return false;
        }

        RuntimeException lastFailure = null;
        for (AppliedMatchRepository repository : repositories) {
            try {
                if (repository.markApplied(matchId, subsystem, appliedAtEpochMillis)) {
                    return true;
                }
                return false;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                SemionTd.LOGGER.warn(
                        "Applied-match repository {} failed; trying fallback.",
                        repository.getClass().getSimpleName(),
                        exception
                );
            }
        }
        throw new PersistenceException("All applied-match persistence layers failed", lastFailure);
    }
}
