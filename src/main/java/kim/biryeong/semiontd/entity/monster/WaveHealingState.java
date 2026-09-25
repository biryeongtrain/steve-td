package kim.biryeong.semiontd.entity.monster;

import kim.biryeong.semiontd.config.WaveHealingConfig;

/** Owned by the logical monster, so recreating its Minecraft entity cannot restore casts. */
public final class WaveHealingState {
    public enum Failure { NONE, NO_ELIGIBLE_TARGET, INSUFFICIENT_INJURY, ZERO_EFFECTIVE_HEALING }

    private int remainingCooldownTicks;
    private long attempts;
    private long successfulCasts;
    private long noEligibleTarget;
    private long insufficientInjury;
    private long zeroEffectiveHealing;
    private long eligibleTargets;
    private double recoverableInjury;
    private double requestedHealing;
    private double effectiveHealing;

    public boolean tick(WaveHealingConfig config, boolean incapacitated) {
        if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--;
        }
        return remainingCooldownTicks == 0 && !incapacitated && successfulCasts < config.maxSuccessfulCasts();
    }

    public void recordAttempt(WaveHealingConfig config, int eligible, double injury, double requested, double effective, Failure failure) {
        attempts++;
        eligibleTargets += eligible;
        recoverableInjury += injury;
        requestedHealing += requested;
        effectiveHealing += effective;
        if (effective > 0.0) {
            successfulCasts++;
            remainingCooldownTicks = config.cooldownTicks();
        } else {
            switch (failure) {
                case NO_ELIGIBLE_TARGET -> noEligibleTarget++;
                case INSUFFICIENT_INJURY -> insufficientInjury++;
                default -> zeroEffectiveHealing++;
            }
            remainingCooldownTicks = config.retryDelayTicks();
        }
    }

    public int remainingCooldownTicks() {return remainingCooldownTicks;}

    public long successfulCasts() {return successfulCasts;}

    public Snapshot snapshot() {
        return new Snapshot(attempts, successfulCasts, noEligibleTarget, insufficientInjury, zeroEffectiveHealing,
                eligibleTargets, recoverableInjury, requestedHealing, 0.0,
                Math.max(0.0, requestedHealing - effectiveHealing), effectiveHealing);
    }

    public record Snapshot(long attempts, long successfulCasts, long noEligibleTarget, long insufficientInjury,
            long zeroEffectiveHealing, long eligibleTargets, double recoverableInjury, double requestedHealing,
            double healingReduction, double overhealing, double effectiveHealing) {
        public static Snapshot empty() {
            return new Snapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        public Snapshot plus(Snapshot other) {
            return new Snapshot(attempts + other.attempts, successfulCasts + other.successfulCasts,
                    noEligibleTarget + other.noEligibleTarget, insufficientInjury + other.insufficientInjury,
                    zeroEffectiveHealing + other.zeroEffectiveHealing, eligibleTargets + other.eligibleTargets,
                    recoverableInjury + other.recoverableInjury, requestedHealing + other.requestedHealing,
                    healingReduction + other.healingReduction, overhealing + other.overhealing, effectiveHealing + other.effectiveHealing);
        }
    }
}
