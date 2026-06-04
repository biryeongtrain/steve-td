package kim.biryeong.semiontd.config;

public record IncomeLaneRoutingConfig(
        boolean enabled,
        Mode mode,
        double queuedThreatWeight,
        double nextRoundQueuedThreatWeight,
        TieBreakMode tieBreakMode
) {
    public enum Mode {
        RANDOM,
        LEAST_THREAT_PRESSURE
    }

    public enum TieBreakMode {
        RANDOM,
        ROUND_ROBIN
    }

    public IncomeLaneRoutingConfig {
        mode = mode == null ? Mode.LEAST_THREAT_PRESSURE : mode;
        tieBreakMode = tieBreakMode == null ? TieBreakMode.ROUND_ROBIN : tieBreakMode;
        queuedThreatWeight = sanitizeWeight(queuedThreatWeight, 1.0);
        nextRoundQueuedThreatWeight = sanitizeWeight(nextRoundQueuedThreatWeight, 0.75);
    }

    public static IncomeLaneRoutingConfig defaultConfig() {
        return new IncomeLaneRoutingConfig(true, Mode.LEAST_THREAT_PRESSURE, 1.0, 0.75, TieBreakMode.ROUND_ROBIN);
    }

    private static double sanitizeWeight(double value, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0) {
            return fallback;
        }
        return value;
    }
}
