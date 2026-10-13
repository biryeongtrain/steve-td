package kim.biryeong.semiontd.entity.visual;

import net.minecraft.world.entity.animal.cow.MushroomCow;

public final class MooshroomVisual {
    private MooshroomVisual() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final EntityVisual.Builder visual = EntityVisual.builder("minecraft:mooshroom");

        public Builder variant(MushroomCow.Variant variant) {
            visual.propertyValue(EntityVisualProperties.MOOSHROOM_VARIANT, variant);
            return this;
        }

        public EntityVisual build() {
            return visual.build();
        }
    }
}
