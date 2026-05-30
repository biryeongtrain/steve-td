package kim.biryeong.semiontd.entity.visual;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import kim.biryeong.semiontd.entity.model.SemionBilModelCache;

public record EntityVisual(
        String entityTypeId,
        String blockbenchModelId,
        double scale,
        Map<String, Object> properties
) {
    public static final String DEFAULT_TOWER_ENTITY_TYPE = "minecraft:villager";
    public static final double DEFAULT_SCALE = 1.0;

    public EntityVisual {
        entityTypeId = SemionBilModelCache.normalize(entityTypeId);
        blockbenchModelId = SemionBilModelCache.normalize(blockbenchModelId);
        if (entityTypeId == null && blockbenchModelId == null) {
            entityTypeId = DEFAULT_TOWER_ENTITY_TYPE;
        }
        scale = normalizeScale(scale);
        properties = withoutScaleProperty(properties);
    }

    public EntityVisual(String entityTypeId, String blockbenchModelId, Map<String, Object> properties) {
        this(
                entityTypeId,
                blockbenchModelId,
                scaleProperty(properties).orElse(DEFAULT_SCALE),
                withoutScaleProperty(properties)
        );
    }

    public static EntityVisual vanilla(String entityTypeId) {
        return new EntityVisual(entityTypeId, null, DEFAULT_SCALE, Map.of());
    }

    public static EntityVisual modeled(String entityTypeId, String blockbenchModelId) {
        return new EntityVisual(entityTypeId, blockbenchModelId, DEFAULT_SCALE, Map.of());
    }

    public static Builder builder(String entityTypeId) {
        return new Builder(entityTypeId);
    }

    public Optional<String> blockbenchModel() {
        return Optional.ofNullable(blockbenchModelId);
    }

    Optional<String> property(String key) {
        return property(key, String.class);
    }

    Optional<Object> propertyValue(String key) {
        return Optional.ofNullable(properties.get(normalizeKey(key)));
    }

    <T> Optional<T> property(String key, Class<T> valueType) {
        Object value = properties.get(normalizeKey(key));
        return valueType.isInstance(value) ? Optional.of(valueType.cast(value)) : Optional.empty();
    }

    EntityVisual withProperty(String key, String value) {
        return withPropertyValue(key, value);
    }

    EntityVisual withPropertyValue(String key, Object value) {
        if (EntityVisualProperties.SCALE.equals(normalizeKey(key))) {
            return scaleValue(value).map(this::withScale).orElse(this);
        }
        LinkedHashMap<String, Object> nextProperties = new LinkedHashMap<>(properties);
        putNormalized(nextProperties, key, value);
        return new EntityVisual(entityTypeId, blockbenchModelId, scale, nextProperties);
    }

    public EntityVisual withScale(double scale) {
        return new EntityVisual(entityTypeId, blockbenchModelId, scale, properties);
    }

    private static Map<String, Object> withoutScaleProperty(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<String, Object> withoutScale = new LinkedHashMap<>();
        properties.forEach((key, value) -> {
            if (!EntityVisualProperties.SCALE.equals(normalizeKey(key))) {
                putNormalized(withoutScale, key, value);
            }
        });
        return withoutScale;
    }

    private static void putNormalized(Map<String, Object> properties, String key, Object value) {
        if (key == null || key.isBlank() || value == null) {
            return;
        }
        if (value instanceof String stringValue) {
            if (stringValue.isBlank()) {
                return;
            }
            properties.put(normalizeKey(key), stringValue.trim());
            return;
        }
        properties.put(normalizeKey(key), value);
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private static double normalizeScale(double scale) {
        return Double.isFinite(scale) && scale > 0.0 ? scale : DEFAULT_SCALE;
    }

    private static Optional<Double> scaleProperty(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return Optional.empty();
        }

        Object value = properties.get(EntityVisualProperties.SCALE);
        if (value == null) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                if (EntityVisualProperties.SCALE.equals(normalizeKey(entry.getKey()))) {
                    value = entry.getValue();
                    break;
                }
            }
        }
        return scaleValue(value);
    }

    private static Optional<Double> scaleValue(Object value) {
        if (value instanceof Number number) {
            return Optional.of(normalizeScale(number.doubleValue()));
        }
        if (value instanceof String stringValue) {
            try {
                return Optional.of(normalizeScale(Double.parseDouble(stringValue.trim())));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public static final class Builder {
        private final String entityTypeId;
        private String blockbenchModelId;
        private double scale = DEFAULT_SCALE;
        private final LinkedHashMap<String, Object> properties = new LinkedHashMap<>();

        private Builder(String entityTypeId) {
            this.entityTypeId = entityTypeId;
        }

        public Builder blockbenchModel(String blockbenchModelId) {
            this.blockbenchModelId = blockbenchModelId;
            return this;
        }

        public Builder scale(double scale) {
            this.scale = scale;
            return this;
        }

        Builder property(String key, String value) {
            if (EntityVisualProperties.SCALE.equals(normalizeKey(key))) {
                scaleValue(value).ifPresent(parsedScale -> scale = parsedScale);
                return this;
            }
            putNormalized(properties, key, value);
            return this;
        }

        Builder propertyValue(String key, Object value) {
            if (EntityVisualProperties.SCALE.equals(normalizeKey(key))) {
                scaleValue(value).ifPresent(parsedScale -> scale = parsedScale);
                return this;
            }
            putNormalized(properties, key, value);
            return this;
        }

        public EntityVisual build() {
            return new EntityVisual(entityTypeId, blockbenchModelId, scale, properties);
        }
    }
}
