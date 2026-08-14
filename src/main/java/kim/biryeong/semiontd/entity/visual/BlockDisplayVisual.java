package kim.biryeong.semiontd.entity.visual;

import java.util.Objects;
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
        return blockState(visual, EntityVisualProperties.BLOCK_STATE);
    }

    /**
     * Optional second block rendered one block above the main one, for plants that sit on top of
     * another block (a cactus flower crowning a cactus, for example).
     */
    public static BlockState topBlockState(EntityVisual visual) {
        return blockState(visual, EntityVisualProperties.BLOCK_STATE_TOP);
    }

    private static BlockState blockState(EntityVisual visual, String property) {
        if (visual == null) {
            return null;
        }
        Object blockState = visual.properties().get(property);
        return blockState instanceof BlockState state ? state : null;
    }

    public static final class Builder {
        private final EntityVisual.Builder visual = EntityVisual.builder(ENTITY_TYPE_ID);

        private Builder(BlockState blockState) {
            visual.propertyValue(
                    EntityVisualProperties.BLOCK_STATE,
                    Objects.requireNonNull(blockState, "blockState")
            );
        }

        public Builder scale(double scale) {
            visual.scale(scale);
            return this;
        }

        public Builder topBlockState(BlockState topBlockState) {
            visual.propertyValue(
                    EntityVisualProperties.BLOCK_STATE_TOP,
                    Objects.requireNonNull(topBlockState, "topBlockState")
            );
            return this;
        }

        public EntityVisual build() {
            return visual.build();
        }
    }
}
