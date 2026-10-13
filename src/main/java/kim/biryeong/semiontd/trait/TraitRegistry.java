package kim.biryeong.semiontd.trait;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;

public final class TraitRegistry {
    private static final Map<Identifier, SemionTrait> TRAITS = new LinkedHashMap<>();

    private TraitRegistry() {
    }

    public static synchronized SemionTrait register(SemionTrait trait) {
        if (TRAITS.containsKey(trait.id())) {
            throw new IllegalArgumentException("Duplicate Semion TD trait id: " + trait.id());
        }
        TRAITS.put(trait.id(), trait);
        return trait;
    }

    static synchronized SemionTrait registerIfAbsent(SemionTrait trait) {
        SemionTrait existing = TRAITS.get(trait.id());
        if (existing != null) {
            return existing;
        }
        TRAITS.put(trait.id(), trait);
        return trait;
    }

    public static synchronized Optional<SemionTrait> find(Identifier id) {
        BuiltInTraits.register();
        return Optional.ofNullable(TRAITS.get(id));
    }

    public static synchronized Collection<SemionTrait> all() {
        BuiltInTraits.register();
        return List.copyOf(TRAITS.values());
    }
}
