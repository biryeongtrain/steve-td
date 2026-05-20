package kim.biryeong.semiontd.tower.description;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kim.biryeong.semiontd.tower.TowerType;

public final class TowerDescriptionRegistry {
    private static final Map<String, TowerDescriptionFactory> FACTORIES = new LinkedHashMap<>();

    private TowerDescriptionRegistry() {
    }

    public static void register(TowerType type, TowerDescriptionFactory factory) {
        if (type == null || factory == null) {
            return;
        }
        FACTORIES.put(type.id(), factory);
    }

    public static void registerTemplate(TowerType type, List<String> template) {
        register(type, TowerDescriptionTemplate.of(template));
    }

    public static Optional<List<String>> describe(TowerType type) {
        if (type == null) {
            return Optional.empty();
        }
        TowerDescriptionFactory factory = FACTORIES.get(type.id());
        return factory == null ? Optional.empty() : Optional.of(factory.build(type));
    }
}
