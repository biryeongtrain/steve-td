package kim.biryeong.semiontd.augment;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class AugmentCatalogLookup {
    private final Map<String, AugmentDefinition> definitionsById;
    private final List<AugmentDefinition> normalDefinitions;
    private final List<AugmentDefinition> reserveDefinitions;
    private final Map<String, String> effectsById;
    private final Map<String, String> modesById;

    AugmentCatalogLookup(List<AugmentDefinition> definitions, List<AugmentDefinition> baseDefinitions,
                         Map<String, List<String>> stances) {
        Map<String, AugmentDefinition> definitionsById = new HashMap<>();
        definitions.forEach(card -> definitionsById.putIfAbsent(card.id(), card));
        baseDefinitions.forEach(card -> definitionsById.putIfAbsent(card.id(), card));
        this.definitionsById = Map.copyOf(definitionsById);
        normalDefinitions = definitions.stream().filter(card -> !card.reserve()).toList();
        reserveDefinitions = definitions.stream().filter(AugmentDefinition::reserve).toList();
        Map<String, String> effectsById = new HashMap<>();
        Map<String, String> modesById = new HashMap<>();
        stances.forEach((effect, modes) -> modes.forEach(mode -> {
            String id = effect + "_" + mode.toLowerCase(Locale.ROOT);
            effectsById.put(id, effect);
            modesById.put(id, mode.toUpperCase(Locale.ROOT));
        }));
        this.effectsById = Map.copyOf(effectsById);
        this.modesById = Map.copyOf(modesById);
    }

    Optional<AugmentDefinition> find(String normalized) {
        return Optional.ofNullable(definitionsById.get(normalized));
    }

    List<AugmentDefinition> normalDefinitions() {
        return normalDefinitions;
    }

    List<AugmentDefinition> reserveDefinitions() {
        return reserveDefinitions;
    }

    String effectId(String normalized) {
        return effectsById.getOrDefault(normalized, normalized);
    }

    String fixedMode(String normalized) {
        return modesById.getOrDefault(normalized, "");
    }
}
