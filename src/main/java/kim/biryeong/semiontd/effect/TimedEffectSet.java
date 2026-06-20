package kim.biryeong.semiontd.effect;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class TimedEffectSet {
    private final EnumMap<TimedEffectType, ActiveTimedEffect> effects = new EnumMap<>(TimedEffectType.class);
    private final EnumMap<TimedEffectType, Map<ResourceLocation, ActiveTimedEffect>> sourcedEffects = new EnumMap<>(TimedEffectType.class);

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

    public boolean apply(TimedEffectType type, ResourceLocation sourceId, double magnitude, int durationTicks) {
        if (type == null || sourceId == null || durationTicks <= 0) {
            return false;
        }

        double cappedMagnitude = type.cappedMagnitude(magnitude);
        if (cappedMagnitude <= 0.0) {
            return false;
        }

        Map<ResourceLocation, ActiveTimedEffect> effectsBySource = sourcedEffects.computeIfAbsent(type, ignored -> new HashMap<>());
        if (effectsBySource.containsKey(sourceId)) {
            return false;
        }

        effectsBySource.put(sourceId, new ActiveTimedEffect(cappedMagnitude, durationTicks));
        return true;
    }

    public boolean refresh(TimedEffectType type, ResourceLocation sourceId, double magnitude, int durationTicks) {
        if (type == null || sourceId == null || durationTicks <= 0) {
            return false;
        }

        double cappedMagnitude = type.cappedMagnitude(magnitude);
        if (cappedMagnitude <= 0.0) {
            return false;
        }

        Map<ResourceLocation, ActiveTimedEffect> effectsBySource = sourcedEffects.computeIfAbsent(type, ignored -> new HashMap<>());
        ActiveTimedEffect active = effectsBySource.get(sourceId);
        if (active == null || Double.compare(cappedMagnitude, active.magnitude) != 0) {
            effectsBySource.put(sourceId, new ActiveTimedEffect(cappedMagnitude, durationTicks));
            return true;
        }

        int previousTicks = active.remainingTicks;
        active.remainingTicks = Math.max(active.remainingTicks, durationTicks);
        return active.remainingTicks != previousTicks;
    }

    public double magnitude(TimedEffectType type) {
        ActiveTimedEffect active = effects.get(type);
        double totalMagnitude = active == null ? 0.0 : active.magnitude;
        Map<ResourceLocation, ActiveTimedEffect> effectsBySource = sourcedEffects.get(type);
        if (effectsBySource != null) {
            for (ActiveTimedEffect sourcedEffect : effectsBySource.values()) {
                totalMagnitude += sourcedEffect.magnitude;
            }
        }
        return type == null ? 0.0 : type.cappedMagnitude(totalMagnitude);
    }

    public int remainingTicks(TimedEffectType type) {
        ActiveTimedEffect active = effects.get(type);
        int remainingTicks = active == null ? 0 : active.remainingTicks;
        Map<ResourceLocation, ActiveTimedEffect> effectsBySource = sourcedEffects.get(type);
        if (effectsBySource != null) {
            for (ActiveTimedEffect sourcedEffect : effectsBySource.values()) {
                remainingTicks = Math.max(remainingTicks, sourcedEffect.remainingTicks);
            }
        }
        return remainingTicks;
    }

    public boolean hasSource(TimedEffectType type, ResourceLocation sourceId) {
        if (type == null || sourceId == null) {
            return false;
        }
        Map<ResourceLocation, ActiveTimedEffect> effectsBySource = sourcedEffects.get(type);
        return effectsBySource != null && effectsBySource.containsKey(sourceId);
    }

    public double magnitude(TimedEffectType type, ResourceLocation sourceId) {
        ActiveTimedEffect active = sourcedEffect(type, sourceId);
        return active == null ? 0.0 : active.magnitude;
    }

    public int remainingTicks(TimedEffectType type, ResourceLocation sourceId) {
        ActiveTimedEffect active = sourcedEffect(type, sourceId);
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

        Iterator<Map.Entry<TimedEffectType, Map<ResourceLocation, ActiveTimedEffect>>> sourcedIterator = sourcedEffects.entrySet().iterator();
        while (sourcedIterator.hasNext()) {
            Map<ResourceLocation, ActiveTimedEffect> effectsBySource = sourcedIterator.next().getValue();
            Iterator<Map.Entry<ResourceLocation, ActiveTimedEffect>> sourceIterator = effectsBySource.entrySet().iterator();
            while (sourceIterator.hasNext()) {
                ActiveTimedEffect active = sourceIterator.next().getValue();
                active.remainingTicks--;
                if (active.remainingTicks <= 0) {
                    sourceIterator.remove();
                }
            }
            if (effectsBySource.isEmpty()) {
                sourcedIterator.remove();
            }
        }
    }

    private ActiveTimedEffect sourcedEffect(TimedEffectType type, ResourceLocation sourceId) {
        if (type == null || sourceId == null) {
            return null;
        }
        Map<ResourceLocation, ActiveTimedEffect> effectsBySource = sourcedEffects.get(type);
        return effectsBySource == null ? null : effectsBySource.get(sourceId);
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
