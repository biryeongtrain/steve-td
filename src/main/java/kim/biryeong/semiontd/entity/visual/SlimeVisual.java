package kim.biryeong.semiontd.entity.visual;

public final class SlimeVisual {
    private SlimeVisual() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final EntityVisual.Builder visual = EntityVisual.builder("minecraft:slime");

        public Builder size(int size) {
            visual.propertyValue(EntityVisualProperties.SLIME_SIZE, size);
            return this;
        }

        public EntityVisual build() {
            return visual.build();
        }
    }
}
