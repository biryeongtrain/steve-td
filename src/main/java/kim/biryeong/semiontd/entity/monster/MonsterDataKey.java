package kim.biryeong.semiontd.entity.monster;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record MonsterDataKey<T>(ResourceLocation id, Class<T> type) {
    public MonsterDataKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
    }

    public static <T> MonsterDataKey<T> of(ResourceLocation id, Class<T> type) {
        return new MonsterDataKey<>(id, type);
    }

    public T cast(Object value) {
        return type.cast(value);
    }
}
