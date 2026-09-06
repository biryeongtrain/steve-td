package kim.biryeong.semiontd.config;

public record WaveHealingConfig(
        double radius,
        double amount,
        int maxTargets,
        int cooldownTicks,
        int retryDelayTicks,
        int maxSuccessfulCasts
) {
    public WaveHealingConfig {
        if (!Double.isFinite(radius) || radius <= 0.0 || !Double.isFinite(amount) || amount <= 0.0
                || maxTargets <= 0 || cooldownTicks <= 0 || retryDelayTicks <= 0 || maxSuccessfulCasts <= 0) {
            throw new IllegalArgumentException("Wave healing values must be finite and positive.");
        }
    }

    public WaveHealingConfig scale(double multiplier) {
        return new WaveHealingConfig(radius, amount * multiplier, maxTargets, cooldownTicks, retryDelayTicks, maxSuccessfulCasts);
    }

    public double minimumInjury() {
        return 0.5 * amount * maxTargets;
    }
}
