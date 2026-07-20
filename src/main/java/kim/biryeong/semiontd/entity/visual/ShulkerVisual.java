package kim.biryeong.semiontd.entity.visual;

import net.minecraft.world.item.DyeColor;

public final class ShulkerVisual {
    private ShulkerVisual() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final EntityVisual.Builder visual = EntityVisual.builder("minecraft:shulker");

        public Builder color(DyeColor color) {
            visual.propertyValue(EntityVisualProperties.SHULKER_COLOR, color);
            return this;
        }

        public EntityVisual build() {
            return visual.build();
        }
    }
}
