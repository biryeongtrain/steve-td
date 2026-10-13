package kim.biryeong.semiontd.entity.visual;

import net.minecraft.world.entity.animal.fish.Salmon;

public final class SalmonVisual {
    private SalmonVisual() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final EntityVisual.Builder visual = EntityVisual.builder("minecraft:salmon");

        public Builder size(Salmon.Variant size) {
            visual.propertyValue(EntityVisualProperties.SALMON_SIZE, size);
            return this;
        }

        public EntityVisual build() {
            return visual.build();
        }
    }
}
