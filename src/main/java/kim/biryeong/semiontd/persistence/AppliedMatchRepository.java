package kim.biryeong.semiontd.persistence;

import java.util.UUID;

public interface AppliedMatchRepository {
    boolean hasApplied(UUID matchId, String subsystem);

    boolean markApplied(UUID matchId, String subsystem, long appliedAtEpochMillis);
}
