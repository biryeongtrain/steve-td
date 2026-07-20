package kim.biryeong.semiontd.entity.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockDisplayVisualTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void builderStoresBlockStateAndScale() {
        EntityVisual visual = BlockDisplayVisual.builder(Blocks.DRAGON_EGG.defaultBlockState())
                .scale(0.75)
                .build();

        assertTrue(BlockDisplayVisual.matches(visual));
        assertEquals(Blocks.DRAGON_EGG.defaultBlockState(), BlockDisplayVisual.blockState(visual));
        assertEquals(0.75, visual.scale(), 0.0001);
    }
}
