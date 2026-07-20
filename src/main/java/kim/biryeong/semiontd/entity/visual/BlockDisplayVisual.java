package kim.biryeong.semiontd.entity.visual;

import net.minecraft.world.level.block.state.BlockState;

public final class BlockDisplayVisual {
    private static final String ENTITY_TYPE_ID = "minecraft:block_display";

    private BlockDisplayVisual() {
    }

    public static Builder builder(BlockState blockState) {
        return new Builder(blockState);
    }

    public static boolean matches(EntityVisual visual) {
        return visual != null && ENTITY_TYPE_ID.equals(visual.entityTypeId());
    }

    public static BlockState blockState(EntityVisual visual) {
        if (visual == null) {
            return null;
        }
        Object blockState = visual.properties().get(EntityVisualProperties.BLOCK_STATE);
        return blockState instanceof BlockState state ? state : null;
    }

    public static final class Builder {
        private final EntityVisual.Builder visual = EntityVisual.builder(ENTITY_TYPE_ID);

        private Builder(BlockState blockState) {
            visual.propertyValue(EntityVisualProperties.BLOCK_STATE, blockState);
        }

        public Builder scale(double scale) {
            visual.scale(scale);
            return this;
        }

        public EntityVisual build() {
            return visual.build();
        }
    }
}
