package kim.biryeong.semiontd.entity.visual;

import net.minecraft.world.entity.animal.fox.Fox;

public final class FoxVisual {
    private FoxVisual() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final EntityVisual.Builder visual = EntityVisual.builder("minecraft:fox");

        public Builder variant(Fox.Variant variant) {
            visual.propertyValue(EntityVisualProperties.FOX_VARIANT, variant);
            return this;
        }

        public EntityVisual build() {
            return visual.build();
        }
    }
}
