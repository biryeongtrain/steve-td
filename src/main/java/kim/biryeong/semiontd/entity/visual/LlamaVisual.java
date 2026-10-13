package kim.biryeong.semiontd.entity.visual;

import net.minecraft.world.entity.animal.equine.Llama;

public final class LlamaVisual {
    private LlamaVisual() {
    }

    public static Builder builder() {
        return new Builder("minecraft:llama");
    }

    public static Builder traderBuilder() {
        return new Builder("minecraft:trader_llama");
    }

    public static final class Builder {
        private final EntityVisual.Builder visual;

        private Builder(String entityTypeId) {
            visual = EntityVisual.builder(entityTypeId);
        }

        public Builder variant(Llama.Variant variant) {
            visual.propertyValue(EntityVisualProperties.LLAMA_VARIANT, variant);
            return this;
        }

        public EntityVisual build() {
            return visual.build();
        }
    }
}
