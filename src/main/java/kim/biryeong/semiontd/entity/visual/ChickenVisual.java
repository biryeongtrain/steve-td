package kim.biryeong.semiontd.entity.visual;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.animal.chicken.ChickenVariant;

public final class ChickenVisual {
    private ChickenVisual() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final EntityVisual.Builder visual = EntityVisual.builder("minecraft:chicken");

        public Builder variant(ResourceKey<ChickenVariant> variant) {
            visual.propertyValue(EntityVisualProperties.CHICKEN_VARIANT, variant);
            return this;
        }

        public EntityVisual build() {
            return visual.build();
        }
    }
}
