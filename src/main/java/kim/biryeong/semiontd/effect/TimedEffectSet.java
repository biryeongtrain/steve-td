package kim.biryeong.semiontd.effect;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;

public final class TimedEffectSet {
    private final EnumMap<TimedEffectType, ActiveTimedEffect> effects = new EnumMap<>(TimedEffectType.class);

    public void apply(TimedEffectType type, double magnitude, int durationTicks) {
        if (type == null || durationTicks <= 0) {
            return;
        }

        double cappedMagnitude = type.cappedMagnitude(magnitude);
        if (cappedMagnitude <= 0.0) {
            return;
        }

        ActiveTimedEffect active = effects.get(type);
        if (active == null || cappedMagnitude > active.magnitude) {
            effects.put(type, new ActiveTimedEffect(cappedMagnitude, durationTicks));
            return;
        }
        if (Double.compare(cappedMagnitude, active.magnitude) == 0) {
            active.remainingTicks = Math.max(active.remainingTicks, durationTicks);
        }
    }

    public double magnitude(TimedEffectType type) {
        ActiveTimedEffect active = effects.get(type);
        return active == null ? 0.0 : active.magnitude;
    }

    public int remainingTicks(TimedEffectType type) {
        ActiveTimedEffect active = effects.get(type);
        return active == null ? 0 : active.remainingTicks;
    }

    public void tick() {
        Iterator<Map.Entry<TimedEffectType, ActiveTimedEffect>> iterator = effects.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveTimedEffect active = iterator.next().getValue();
            active.remainingTicks--;
            if (active.remainingTicks <= 0) {
                iterator.remove();
            }
        }
    }

    private static final class ActiveTimedEffect {
        private final double magnitude;
        private int remainingTicks;

        private ActiveTimedEffect(double magnitude, int remainingTicks) {
            this.magnitude = magnitude;
            this.remainingTicks = remainingTicks;
        }
    }
}
