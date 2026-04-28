package kim.biryeong.semiontd.entity.model;

import de.tomalbrc.bil.core.model.Model;
import de.tomalbrc.bil.file.loader.AjModelLoader;
import de.tomalbrc.bil.file.loader.BbModelLoader;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

public final class SemionBilModelCache {
    private static final Map<String, Optional<Model>> MODELS = new ConcurrentHashMap<>();

    private SemionBilModelCache() {
    }

    public static Optional<Model> load(String modelId) {
        String normalizedId = normalize(modelId);
        if (normalizedId == null) {
            return Optional.empty();
        }
        return MODELS.computeIfAbsent(normalizedId, SemionBilModelCache::loadUncached);
    }

    private static Optional<Model> loadUncached(String modelId) {
        ResourceLocation id = ResourceLocation.tryParse(modelId);
        if (id == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(BbModelLoader.load(id));
        } catch (RuntimeException ignored) {
            try {
                return Optional.of(AjModelLoader.load(id));
            } catch (RuntimeException ignoredAgain) {
                return Optional.empty();
            }
        }
    }

    public static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
