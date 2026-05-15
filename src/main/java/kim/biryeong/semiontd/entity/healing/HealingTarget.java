package kim.biryeong.semiontd.entity.healing;

public interface HealingTarget {
    boolean isHealingAlly(HealingTarget other);

    boolean canReceiveHealing();

    double missingHealingHealth();

    boolean receiveHealing(double amount);

    default void playHealingAnimation() {
    }
}
