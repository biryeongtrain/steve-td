package kim.biryeong.semiontd.rating;

public record SemionRatingProfile(
        String lastKnownName,
        int gamesPlayed,
        int wins,
        int losses,
        double mu,
        double sigma,
        int displayElo,
        long updatedAtEpochMillis
) {
    public SemionRatingProfile {
        lastKnownName = lastKnownName == null ? "" : lastKnownName;
        if (gamesPlayed < 0 || wins < 0 || losses < 0 || updatedAtEpochMillis < 0) {
            throw new IllegalArgumentException("Rating profile counters cannot be negative");
        }
        if (!Double.isFinite(mu) || !Double.isFinite(sigma) || sigma < 0.0) {
            throw new IllegalArgumentException("Rating profile values must be finite");
        }
    }
}
