package kim.biryeong.semiontd.entity.visual;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.animal.pig.PigVariant;

public final class PigVisual {
    private PigVisual() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final EntityVisual.Builder visual = EntityVisual.builder("minecraft:pig");

        public Builder variant(ResourceKey<PigVariant> variant) {
            visual.propertyValue(EntityVisualProperties.PIG_VARIANT, variant);
            return this;
        }

        public EntityVisual build() {
            return visual.build();
        }
    }
}
