package kim.biryeong.semiontd.entity.visual;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.animal.feline.CatVariant;
import net.minecraft.world.item.DyeColor;

public final class CatVisual {
    private CatVisual() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final EntityVisual.Builder visual = EntityVisual.builder("minecraft:cat");

        public Builder variant(ResourceKey<CatVariant> variant) {
            visual.propertyValue(EntityVisualProperties.CAT_VARIANT, variant);
            return this;
        }

        public Builder collarColor(DyeColor color) {
            visual.propertyValue(EntityVisualProperties.COLLAR_COLOR, color);
            return this;
        }

        public Builder tame(boolean tame) {
            visual.propertyValue(EntityVisualProperties.TAME, tame);
            return this;
        }

        public Builder sitting(boolean sitting) {
            visual.propertyValue(EntityVisualProperties.SITTING, sitting);
            return this;
        }

        public EntityVisual build() {
            return visual.build();
        }
    }
}
