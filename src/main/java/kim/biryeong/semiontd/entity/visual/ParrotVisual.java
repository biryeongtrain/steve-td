package kim.biryeong.semiontd.entity.visual;

import net.minecraft.world.entity.animal.parrot.Parrot;

public final class ParrotVisual {
    private ParrotVisual() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final EntityVisual.Builder visual = EntityVisual.builder("minecraft:parrot");

        public Builder variant(Parrot.Variant variant) {
            visual.propertyValue(EntityVisualProperties.PARROT_VARIANT, variant);
            return this;
        }

        public EntityVisual build() {
            return visual.build();
        }
    }
}
