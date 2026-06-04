package kim.biryeong.semiontd.tower.legion;

import java.util.Optional;

final class BeeStingPolicy {
    private BeeStingPolicy() {
    }

    static State applySting(State previous, int maxStacks, int durationTicks, int tickIntervalTicks) {
        int resolvedMaxStacks = Math.max(1, maxStacks);
        int resolvedDuration = Math.max(1, durationTicks);
        int resolvedInterval = Math.max(1, tickIntervalTicks);
        if (previous == null) {
            return new State(1, resolvedDuration, resolvedInterval);
        }
        int stacks = Math.min(resolvedMaxStacks, previous.stacks() + 1);
        int ticksUntilDamage = Math.max(1, Math.min(previous.ticksUntilDamage(), resolvedInterval));
        return new State(stacks, resolvedDuration, ticksUntilDamage);
    }

    static TickResult tick(State state, double damagePerStack, int tickIntervalTicks) {
        if (state == null) {
            return new TickResult(Optional.empty(), 0.0);
        }
        int remainingTicks = state.remainingTicks() - 1;
        if (remainingTicks <= 0) {
            return new TickResult(Optional.empty(), 0.0);
        }
        int ticksUntilDamage = state.ticksUntilDamage() - 1;
        double damage = 0.0;
        if (ticksUntilDamage <= 0) {
            damage = Math.max(0.0, damagePerStack) * state.stacks();
            ticksUntilDamage = Math.max(1, tickIntervalTicks);
        }
        return new TickResult(Optional.of(new State(state.stacks(), remainingTicks, ticksUntilDamage)), damage);
    }

    record State(int stacks, int remainingTicks, int ticksUntilDamage) {
        State {
            stacks = Math.max(1, stacks);
            remainingTicks = Math.max(0, remainingTicks);
            ticksUntilDamage = Math.max(1, ticksUntilDamage);
        }
    }

    record TickResult(Optional<State> state, double damage) {
        TickResult {
            state = state == null ? Optional.empty() : state;
            damage = Math.max(0.0, damage);
        }
    }
}
