package kim.biryeong.semiontd.entity.visual;

import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.item.DyeColor;

public final class TropicalFishVisual {
    private TropicalFishVisual() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final EntityVisual.Builder visual = EntityVisual.builder("minecraft:tropical_fish");

        public Builder pattern(TropicalFish.Pattern pattern) {
            visual.propertyValue(EntityVisualProperties.TROPICAL_FISH_PATTERN, pattern);
            return this;
        }

        public Builder baseColor(DyeColor color) {
            visual.propertyValue(EntityVisualProperties.BASE_COLOR, color);
            return this;
        }

        public Builder patternColor(DyeColor color) {
            visual.propertyValue(EntityVisualProperties.PATTERN_COLOR, color);
            return this;
        }

        public EntityVisual build() {
            return visual.build();
        }
    }
}
