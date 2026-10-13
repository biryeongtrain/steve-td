package kim.biryeong.semiontd.entity.visual;

import net.minecraft.world.entity.animal.rabbit.Rabbit;

public final class RabbitVisual {
    private RabbitVisual() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final EntityVisual.Builder visual = EntityVisual.builder("minecraft:rabbit");

        public Builder variant(Rabbit.Variant variant) {
            visual.propertyValue(EntityVisualProperties.RABBIT_VARIANT, variant);
            return this;
        }

        public EntityVisual build() {
            return visual.build();
        }
    }
}
