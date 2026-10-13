package kim.biryeong.semiontd.tower;

import java.util.Objects;
import net.minecraft.resources.Identifier;

public record TowerDataKey<T>(Identifier id, Class<T> type) {
    public TowerDataKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
    }

    public static <T> TowerDataKey<T> of(Identifier id, Class<T> type) {
        return new TowerDataKey<>(id, type);
    }

    T cast(Object value) {
        return type.cast(value);
    }
}
