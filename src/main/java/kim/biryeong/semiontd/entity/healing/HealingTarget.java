package kim.biryeong.semiontd.entity.healing;

public interface HealingTarget {
    boolean isHealingAlly(HealingTarget other);

    boolean canReceiveHealing();

    double missingHealingHealth();

    boolean receiveHealing(double amount);

    default double receiveHealingAmount(double amount) {
        double missingBefore = missingHealingHealth();
        if (!receiveHealing(amount)) {
            return 0.0;
        }
        return Math.max(0.0, missingBefore - missingHealingHealth());
    }

    default boolean healTarget(HealingTarget target, double amount) {
        return target != null && target.receiveHealing(amount);
    }

    default void playHealingAnimation() {
    }
}
