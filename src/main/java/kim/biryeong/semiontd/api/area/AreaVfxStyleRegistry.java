package kim.biryeong.semiontd.api.area;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public interface AreaVfxStyleRegistry {
    void register(ResourceLocation id, AreaVfxStylePlanner planner);

    Optional<AreaVfxStylePlanner> find(ResourceLocation id);

    boolean frozen();
}
