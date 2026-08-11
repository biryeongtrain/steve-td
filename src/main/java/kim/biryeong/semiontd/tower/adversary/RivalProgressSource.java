package kim.biryeong.semiontd.tower.adversary;

import java.util.UUID;

/** Implemented by rival runtime towers so the player ledger can be rebuilt safely. */
public interface RivalProgressSource {
    UUID rivalId();

    RivalKind rivalKind();

    int contributedScore();

    default RivalContribution snapshot() {
        return new RivalContribution(rivalId(), rivalKind(), Math.max(0, contributedScore()));
    }
}
