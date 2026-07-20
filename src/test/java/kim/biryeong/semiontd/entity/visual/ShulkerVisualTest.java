package kim.biryeong.semiontd.entity.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.api.Test;

class ShulkerVisualTest {
    @Test
    void coloredShulkerStoresDyeColorVisualState() {
        EntityVisual visual = ShulkerVisual.builder().color(DyeColor.PURPLE).build();

        assertEquals("minecraft:shulker", visual.entityTypeId());
        assertEquals(DyeColor.PURPLE, visual.properties().get("shulker_color"));
    }
}
