package kim.biryeong.semiontd.tower.area;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kim.biryeong.semiontd.api.area.AreaVfxStylePlanner;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class AreaVfxStyleRegistryImplTest {
    private static final ResourceLocation STYLE_ID = ResourceLocation.fromNamespaceAndPath("test", "style");

    @Test
    void registrationRejectsDuplicatesAndLateWrites() {
        AreaVfxStyleRegistryImpl registry = new AreaVfxStyleRegistryImpl();
        AreaVfxStylePlanner planner = (context, output) -> {
        };

        registry.register(STYLE_ID, planner);
        assertTrue(registry.find(STYLE_ID).isPresent());
        assertThrows(IllegalArgumentException.class, () -> registry.register(STYLE_ID, planner));

        registry.freeze();
        assertTrue(registry.frozen());
        assertThrows(IllegalStateException.class, () -> registry.register(
                ResourceLocation.fromNamespaceAndPath("test", "late"), planner));
    }
}
