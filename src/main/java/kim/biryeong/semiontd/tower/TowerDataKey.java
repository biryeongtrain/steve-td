package kim.biryeong.semiontd.tower;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record TowerDataKey<T>(ResourceLocation id, Class<T> type) {
    public TowerDataKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
    }

    public static <T> TowerDataKey<T> of(ResourceLocation id, Class<T> type) {
        return new TowerDataKey<>(id, type);
    }

    T cast(Object value) {
        return type.cast(value);
    }
}
