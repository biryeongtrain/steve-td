package kim.biryeong.semiontd.effect;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.resources.Identifier;

public final class TimedEffectSet {
    private final EnumMap<TimedEffectType, ActiveTimedEffect> effects = new EnumMap<>(TimedEffectType.class);
    private final EnumMap<TimedEffectType, Map<Identifier, ActiveTimedEffect>> sourcedEffects = new EnumMap<>(TimedEffectType.class);
    private final EnumMap<TimedEffectType, Map<Identifier, Double>> persistentEffects = new EnumMap<>(TimedEffectType.class);

    public record Snapshot(TimedEffectType type, String sourceId, double magnitude,
                           Integer remainingTicks, boolean persistent) {}

    /** Detached diagnostic values; reading must not refresh or consume effects. */
    public java.util.List<Snapshot> snapshot() {
        var result = new java.util.ArrayList<Snapshot>();
        effects.forEach((type, effect) -> result.add(new Snapshot(type, null, effect.magnitude, effect.remainingTicks, false)));
        sourcedEffects.forEach((type, sources) -> sources.forEach((source, effect) ->
                result.add(new Snapshot(type, source.toString(), effect.magnitude, effect.remainingTicks, false))));
        persistentEffects.forEach((type, sources) -> sources.forEach((source, magnitude) ->
                result.add(new Snapshot(type, source.toString(), magnitude, null, true))));
        result.sort(java.util.Comparator.comparing((Snapshot value) -> value.type().name())
                .thenComparing(value -> value.sourceId() == null ? "" : value.sourceId())
                .thenComparing(Snapshot::persistent));
        return java.util.List.copyOf(result);
    }

    public void apply(TimedEffectType type, double magnitude, int durationTicks) {
        if (type == null || durationTicks <= 0) {
            return;
        }

        double sanitizedMagnitude = Math.max(0.0, magnitude);
        if (sanitizedMagnitude <= 0.0) {
            return;
        }

        ActiveTimedEffect active = effects.get(type);
        if (active == null || sanitizedMagnitude > active.magnitude) {
            effects.put(type, new ActiveTimedEffect(sanitizedMagnitude, durationTicks));
            return;
        }
        if (Double.compare(sanitizedMagnitude, active.magnitude) == 0) {
            active.remainingTicks = Math.max(active.remainingTicks, durationTicks);
        }
    }

    public boolean apply(TimedEffectType type, Identifier sourceId, double magnitude, int durationTicks) {
        if (type == null || sourceId == null || durationTicks <= 0) {
            return false;
        }

        double sanitizedMagnitude = Math.max(0.0, magnitude);
        if (sanitizedMagnitude <= 0.0) {
            Map<Identifier, ActiveTimedEffect> effectsBySource = sourcedEffects.get(type);
            if (effectsBySource == null) {
                return false;
            }
            boolean removed = effectsBySource.remove(sourceId) != null;
            if (effectsBySource.isEmpty()) {
                sourcedEffects.remove(type);
            }
            return removed;
        }

        Map<Identifier, ActiveTimedEffect> effectsBySource = sourcedEffects.computeIfAbsent(type, ignored -> new HashMap<>());
        if (effectsBySource.containsKey(sourceId)) {
            return false;
        }

        effectsBySource.put(sourceId, new ActiveTimedEffect(sanitizedMagnitude, durationTicks));
        return true;
    }

    public boolean refresh(TimedEffectType type, Identifier sourceId, double magnitude, int durationTicks) {
        if (type == null || sourceId == null || durationTicks <= 0) {
            return false;
        }

        double sanitizedMagnitude = Math.max(0.0, magnitude);
        if (sanitizedMagnitude <= 0.0) {
            Map<Identifier, ActiveTimedEffect> effectsBySource = sourcedEffects.get(type);
            if (effectsBySource == null) {
                return false;
            }
            boolean removed = effectsBySource.remove(sourceId) != null;
            if (effectsBySource.isEmpty()) {
                sourcedEffects.remove(type);
            }
            return removed;
        }

        Map<Identifier, ActiveTimedEffect> effectsBySource = sourcedEffects.computeIfAbsent(type, ignored -> new HashMap<>());
        ActiveTimedEffect active = effectsBySource.get(sourceId);
        if (active == null || Double.compare(sanitizedMagnitude, active.magnitude) != 0) {
            effectsBySource.put(sourceId, new ActiveTimedEffect(sanitizedMagnitude, durationTicks));
            return true;
        }

        int previousTicks = active.remainingTicks;
        active.remainingTicks = Math.max(active.remainingTicks, durationTicks);
        return active.remainingTicks != previousTicks;
    }

    public boolean setPersistent(TimedEffectType type, Identifier sourceId, double magnitude) {
        if (type == null || sourceId == null) {
            return false;
        }

        double sanitizedMagnitude = Math.max(0.0, magnitude);
        Map<Identifier, Double> effectsBySource = persistentEffects.get(type);
        if (sanitizedMagnitude <= 0.0) {
            if (effectsBySource == null) {
                return false;
            }
            boolean removed = effectsBySource.remove(sourceId) != null;
            if (effectsBySource.isEmpty()) {
                persistentEffects.remove(type);
            }
            return removed;
        }

        effectsBySource = persistentEffects.computeIfAbsent(type, ignored -> new HashMap<>());
        Double previous = effectsBySource.put(sourceId, sanitizedMagnitude);
        return previous == null || Double.compare(previous, sanitizedMagnitude) != 0;
    }

    public double magnitude(TimedEffectType type) {
        if (type == null) {
            return 0.0;
        }
        ActiveTimedEffect active = effects.get(type);
        double totalMagnitude = active == null ? 0.0 : active.magnitude;
        Map<Identifier, ActiveTimedEffect> effectsBySource = sourcedEffects.get(type);
        if (effectsBySource != null) {
            for (ActiveTimedEffect sourcedEffect : effectsBySource.values()) {
                totalMagnitude += sourcedEffect.magnitude;
            }
        }
        Map<Identifier, Double> persistentBySource = persistentEffects.get(type);
        if (persistentBySource != null) {
            for (double persistentMagnitude : persistentBySource.values()) {
                totalMagnitude += persistentMagnitude;
            }
        }
        return totalMagnitude;
    }

    public int remainingTicks(TimedEffectType type) {
        ActiveTimedEffect active = effects.get(type);
        int remainingTicks = active == null ? 0 : active.remainingTicks;
        Map<Identifier, ActiveTimedEffect> effectsBySource = sourcedEffects.get(type);
        if (effectsBySource != null) {
            for (ActiveTimedEffect sourcedEffect : effectsBySource.values()) {
                remainingTicks = Math.max(remainingTicks, sourcedEffect.remainingTicks);
            }
        }
        return remainingTicks;
    }

    public double multiplicativeMagnitude(TimedEffectType type, int maxSources) {
        if (type == null || maxSources <= 0) return 0.0;
        ActiveTimedEffect active = effects.get(type);
        var timed = sourcedEffects.get(type);
        var persistent = persistentEffects.get(type);
        if (timed == null && persistent == null) {
            return active == null || !Double.isFinite(active.magnitude)
                    ? 0.0 : 1.0 - (1.0 - Math.clamp(active.magnitude, 0.0, 1.0));
        }
        return sourcedMultiplicativeMagnitude(active, timed, persistent, maxSources);
    }

    private static double sourcedMultiplicativeMagnitude(ActiveTimedEffect active,
            Map<Identifier, ActiveTimedEffect> timed, Map<Identifier, Double> persistent, int maxSources) {
        int count = (active == null ? 0 : 1) + (timed == null ? 0 : timed.size())
                + (persistent == null ? 0 : persistent.size());
        int capacity = Math.min(count, maxSources);
        if (capacity == 0) return 0.0;
        if (capacity == 1) return singleSourceMagnitude(active, timed, persistent);
        double[] strongest = new double[capacity];
        int size = active == null ? 0 : includeStrongest(strongest, 0, active.magnitude);
        if (timed != null) {
            for (ActiveTimedEffect effect : timed.values()) {
                size = includeStrongest(strongest, size, effect.magnitude);
            }
        }
        if (persistent != null) {
            for (double magnitude : persistent.values()) {
                size = includeStrongest(strongest, size, magnitude);
            }
        }
        java.util.Arrays.sort(strongest, 0, size);
        double remaining = 1.0;
        for (int i = size - 1; i >= 0; i--) {
            remaining *= 1.0 - Math.clamp(strongest[i], 0.0, 1.0);
        }
        return 1.0 - remaining;
    }

    private static double singleSourceMagnitude(ActiveTimedEffect active,
            Map<Identifier, ActiveTimedEffect> timed, Map<Identifier, Double> persistent) {
        double strongest = active == null || !Double.isFinite(active.magnitude)
                ? Double.NEGATIVE_INFINITY : active.magnitude;
        if (timed != null) {
            for (ActiveTimedEffect effect : timed.values()) {
                if (Double.isFinite(effect.magnitude) && Double.compare(effect.magnitude, strongest) > 0) {
                    strongest = effect.magnitude;
                }
            }
        }
        if (persistent != null) {
            for (double magnitude : persistent.values()) {
                if (Double.isFinite(magnitude) && Double.compare(magnitude, strongest) > 0) {
                    strongest = magnitude;
                }
            }
        }
        return strongest == Double.NEGATIVE_INFINITY ? 0.0 : 1.0 - (1.0 - Math.clamp(strongest, 0.0, 1.0));
    }

    private static int includeStrongest(double[] values, int size, double magnitude) {
        if (!Double.isFinite(magnitude)) return size;
        if (size < values.length) {
            int index = size;
            while (index > 0) {
                int parent = (index - 1) >>> 1;
                if (Double.compare(values[parent], magnitude) <= 0) break;
                values[index] = values[parent];
                index = parent;
            }
            values[index] = magnitude;
            return size + 1;
        }
        if (Double.compare(magnitude, values[0]) <= 0) return size;
        int index = 0;
        int half = size >>> 1;
        while (index < half) {
            int child = index * 2 + 1;
            if (child + 1 < size && Double.compare(values[child + 1], values[child]) < 0) child++;
            if (Double.compare(magnitude, values[child]) <= 0) break;
            values[index] = values[child];
            index = child;
        }
        values[index] = magnitude;
        return size;
    }

    public boolean hasSource(TimedEffectType type, Identifier sourceId) {
        if (type == null || sourceId == null) {
            return false;
        }
        Map<Identifier, ActiveTimedEffect> effectsBySource = sourcedEffects.get(type);
        return effectsBySource != null && effectsBySource.containsKey(sourceId);
    }

    public boolean hasPersistent(TimedEffectType type) {
        Map<Identifier, Double> effectsBySource = persistentEffects.get(type);
        return effectsBySource != null && !effectsBySource.isEmpty();
    }

    public boolean hasPersistent(TimedEffectType type, Identifier sourceId) {
        if (type == null || sourceId == null) {
            return false;
        }
        Map<Identifier, Double> effectsBySource = persistentEffects.get(type);
        return effectsBySource != null && effectsBySource.containsKey(sourceId);
    }

    public double magnitude(TimedEffectType type, Identifier sourceId) {
        ActiveTimedEffect active = sourcedEffect(type, sourceId);
        return active == null ? 0.0 : active.magnitude;
    }

    public double persistentMagnitude(TimedEffectType type, Identifier sourceId) {
        Map<Identifier, Double> effectsBySource = persistentEffects.get(type);
        return effectsBySource == null ? 0.0 : effectsBySource.getOrDefault(sourceId, 0.0);
    }

    public int remainingTicks(TimedEffectType type, Identifier sourceId) {
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

        Iterator<Map.Entry<TimedEffectType, Map<Identifier, ActiveTimedEffect>>> sourcedIterator = sourcedEffects.entrySet().iterator();
        while (sourcedIterator.hasNext()) {
            Map<Identifier, ActiveTimedEffect> effectsBySource = sourcedIterator.next().getValue();
            Iterator<Map.Entry<Identifier, ActiveTimedEffect>> sourceIterator = effectsBySource.entrySet().iterator();
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

    public boolean remove(TimedEffectType type) {
        boolean changed = effects.remove(type) != null;
        changed |= sourcedEffects.remove(type) != null;
        changed |= persistentEffects.remove(type) != null;
        return changed;
    }

    private ActiveTimedEffect sourcedEffect(TimedEffectType type, Identifier sourceId) {
        if (type == null || sourceId == null) {
            return null;
        }
        Map<Identifier, ActiveTimedEffect> effectsBySource = sourcedEffects.get(type);
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
