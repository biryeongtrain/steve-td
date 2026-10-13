package kim.biryeong.semiontd.entity.visual;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.animal.cow.CowVariant;

public final class CowVisual {
    private CowVisual() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final EntityVisual.Builder visual = EntityVisual.builder("minecraft:cow");

        public Builder variant(ResourceKey<CowVariant> variant) {
            visual.propertyValue(EntityVisualProperties.COW_VARIANT, variant);
            return this;
        }

        public EntityVisual build() {
            return visual.build();
        }
    }
}
