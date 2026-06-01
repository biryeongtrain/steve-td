package kim.biryeong.semiontd.persistence;

import kim.biryeong.semiontd.game.MatchId;

public interface AppliedMatchRepository {
    boolean hasApplied(MatchId matchId, String subsystem);

    boolean markApplied(MatchId matchId, String subsystem, long appliedAtEpochMillis);
}
