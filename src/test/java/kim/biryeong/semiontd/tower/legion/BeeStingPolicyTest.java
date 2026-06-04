package kim.biryeong.semiontd.tower.legion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BeeStingPolicyTest {
    @Test
    void applyingStingsStacksUpToConfiguredMaximumAndRefreshesDuration() {
        BeeStingPolicy.State state = null;

        state = BeeStingPolicy.applySting(state, 3, 80, 20);
        state = BeeStingPolicy.applySting(state, 3, 80, 20);
        state = BeeStingPolicy.applySting(state, 3, 40, 20);
        state = BeeStingPolicy.applySting(state, 3, 80, 20);

        assertEquals(3, state.stacks());
        assertEquals(80, state.remainingTicks());
        assertEquals(20, state.ticksUntilDamage());
    }

    @Test
    void tickingDealsStackScaledDamageOnIntervalUntilDurationEnds() {
        BeeStingPolicy.State state = BeeStingPolicy.applySting(null, 4, 5, 2);
        state = BeeStingPolicy.applySting(state, 4, 5, 2);

        BeeStingPolicy.TickResult first = BeeStingPolicy.tick(state, 1.5, 2);
        assertEquals(0.0, first.damage(), 0.0001);
        assertTrue(first.state().isPresent());

        BeeStingPolicy.TickResult second = BeeStingPolicy.tick(first.state().orElseThrow(), 1.5, 2);
        assertEquals(3.0, second.damage(), 0.0001);
        assertEquals(2, second.state().orElseThrow().stacks());

        BeeStingPolicy.TickResult current = second;
        while (current.state().isPresent()) {
            current = BeeStingPolicy.tick(current.state().orElseThrow(), 1.5, 2);
        }
        assertTrue(current.state().isEmpty());
    }
}
