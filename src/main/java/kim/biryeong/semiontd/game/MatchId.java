package kim.biryeong.semiontd.game;

import io.hypersistence.tsid.TSID;

public record MatchId(long value) {
    public MatchId {
        if (value <= 0L) {
            throw new IllegalArgumentException("match id must be positive");
        }
    }

    public static MatchId newId() {
        return new MatchId(TSID.Factory.getTsid().toLong());
    }

    public static MatchId fromString(String value) {
        return new MatchId(Long.parseUnsignedLong(value));
    }

    @Override
    public String toString() {
        return Long.toUnsignedString(value);
    }
}
