package kim.biryeong.semiontd.entity.visual;

import net.minecraft.world.entity.animal.equine.Markings;
import net.minecraft.world.entity.animal.equine.Variant;

public final class HorseVisual {
    private HorseVisual() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final EntityVisual.Builder visual = EntityVisual.builder("minecraft:horse");

        public Builder variant(Variant variant) {
            visual.propertyValue(EntityVisualProperties.HORSE_VARIANT, variant);
            return this;
        }

        public Builder markings(Markings markings) {
            visual.propertyValue(EntityVisualProperties.HORSE_MARKINGS, markings);
            return this;
        }

        public EntityVisual build() {
            return visual.build();
        }
    }
}
