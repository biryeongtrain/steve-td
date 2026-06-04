package kim.biryeong.semiontd.tower.animal;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

final class FoxTargetingPolicy {
    private FoxTargetingPolicy() {
    }

    static double effectiveThreshold(double baseThreshold, int stacks, double thresholdPerStack, double maxThreshold) {
        return Math.min(maxThreshold, baseThreshold + Math.max(0, stacks) * thresholdPerStack);
    }

    static <T extends Candidate> Optional<T> select(Collection<T> candidates, double executeHealthThreshold) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream()
                .filter(Candidate::inAttackRange)
                .filter(candidate -> candidate.maxHealth() > 0.0)
                .filter(candidate -> candidate.healthRatio() <= executeHealthThreshold)
                .min(Comparator
                        .comparingDouble(Candidate::healthRatio)
                        .thenComparingDouble(Candidate::distanceSqr));
    }

    interface Candidate {
        double currentHealth();

        double maxHealth();

        double distanceSqr();

        boolean inAttackRange();

        default double healthRatio() {
            return currentHealth() / maxHealth();
        }
    }
}
