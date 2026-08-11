package kim.biryeong.semiontd.tower.adversary;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;

public record EvolutionRecipe(Map<RivalKind, Integer> requirements, boolean hidden) {
    public EvolutionRecipe {
        EnumMap<RivalKind, Integer> copy = new EnumMap<>(RivalKind.class);
        if (requirements != null) {
            requirements.forEach((kind, amount) -> {
                Objects.requireNonNull(kind, "recipe rival kind");
                if (amount == null || amount <= 0) {
                    throw new IllegalArgumentException("Evolution requirements must be positive.");
                }
                copy.put(kind, amount);
            });
        }
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("Evolution recipe cannot be empty.");
        }
        requirements = Collections.unmodifiableMap(copy);
    }

    public static EvolutionRecipe visible(Object... requirements) {
        return create(false, requirements);
    }

    public static EvolutionRecipe hidden(Object... requirements) {
        return create(true, requirements);
    }

    public int required(RivalKind kind) {
        return requirements.getOrDefault(kind, 0);
    }

    public boolean requires(RivalKind kind) {
        return required(kind) > 0;
    }

    public boolean satisfiedBy(ToIntFunction<RivalKind> scoreLookup) {
        Objects.requireNonNull(scoreLookup, "scoreLookup");
        return requirements.entrySet().stream()
                .allMatch(entry -> scoreLookup.applyAsInt(entry.getKey()) >= entry.getValue());
    }

    public boolean satisfiedBy(Map<RivalKind, Integer> scores) {
        Map<RivalKind, Integer> safeScores = scores == null ? Map.of() : scores;
        return satisfiedBy(kind -> safeScores.getOrDefault(kind, 0));
    }

    private static EvolutionRecipe create(boolean hidden, Object... entries) {
        if (entries == null || entries.length == 0 || entries.length % 2 != 0) {
            throw new IllegalArgumentException("Recipes require RivalKind/amount pairs.");
        }
        EnumMap<RivalKind, Integer> requirements = new EnumMap<>(RivalKind.class);
        for (int index = 0; index < entries.length; index += 2) {
            if (!(entries[index] instanceof RivalKind kind) || !(entries[index + 1] instanceof Number amount)) {
                throw new IllegalArgumentException("Recipes require RivalKind/amount pairs.");
            }
            requirements.put(kind, amount.intValue());
        }
        return new EvolutionRecipe(requirements, hidden);
    }
}
