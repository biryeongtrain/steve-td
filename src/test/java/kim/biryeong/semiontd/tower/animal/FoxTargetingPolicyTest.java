package kim.biryeong.semiontd.tower.animal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FoxTargetingPolicyTest {
    @Test
    void selectsLowestHealthRatioExecuteCandidateInAttackRange() {
        TestCandidate healthyClose = new TestCandidate("healthy-close", 80.0, 100.0, 1.0, true);
        TestCandidate lowFar = new TestCandidate("low-far", 25.0, 100.0, 9.0, true);
        TestCandidate lowerMid = new TestCandidate("lower-mid", 10.0, 100.0, 4.0, true);

        var selected = FoxTargetingPolicy.select(
                List.of(healthyClose, lowFar, lowerMid),
                0.30
        );

        assertTrue(selected.isPresent());
        assertEquals("lower-mid", selected.orElseThrow().id());
    }

    @Test
    void ignoresHealthyAndOutOfRangeTargets() {
        TestCandidate healthy = new TestCandidate("healthy", 60.0, 100.0, 1.0, true);
        TestCandidate lowOutOfRange = new TestCandidate("low-out-of-range", 5.0, 100.0, 1.0, false);

        var selected = FoxTargetingPolicy.select(List.of(healthy, lowOutOfRange), 0.30);

        assertTrue(selected.isEmpty());
    }

    @Test
    void tiesByDistanceAfterHealthRatio() {
        TestCandidate far = new TestCandidate("far", 20.0, 100.0, 9.0, true);
        TestCandidate close = new TestCandidate("close", 20.0, 100.0, 1.0, true);

        var selected = FoxTargetingPolicy.select(List.of(far, close), 0.30);

        assertTrue(selected.isPresent());
        assertEquals("close", selected.orElseThrow().id());
    }

    @Test
    void stackThresholdIsCapped() {
        double threshold = FoxTargetingPolicy.effectiveThreshold(0.40, 10, 0.05, 0.60);

        assertEquals(0.60, threshold, 0.0001);
    }

    private record TestCandidate(
            String id,
            double currentHealth,
            double maxHealth,
            double distanceSqr,
            boolean inAttackRange
    ) implements FoxTargetingPolicy.Candidate {
    }
}
