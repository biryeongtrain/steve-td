package kim.biryeong.semiontd.api.area;

import java.util.Optional;
import net.minecraft.resources.Identifier;

public interface AreaVfxStyleRegistry {
    void register(Identifier id, AreaVfxStylePlanner planner);

    Optional<AreaVfxStylePlanner> find(Identifier id);

    boolean frozen();
}
