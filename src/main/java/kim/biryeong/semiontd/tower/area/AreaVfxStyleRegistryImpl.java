package kim.biryeong.semiontd.tower.area;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import kim.biryeong.semiontd.api.area.AreaVfxStylePlanner;
import kim.biryeong.semiontd.api.area.AreaVfxStyleRegistry;
import net.minecraft.resources.ResourceLocation;

public final class AreaVfxStyleRegistryImpl implements AreaVfxStyleRegistry {
    private final ConcurrentMap<ResourceLocation, AreaVfxStylePlanner> planners = new ConcurrentHashMap<>();
    private final AtomicBoolean frozen = new AtomicBoolean();

    @Override
    public void register(ResourceLocation id, AreaVfxStylePlanner planner) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(planner, "planner");
        if (frozen.get()) {
            throw new IllegalStateException("Area VFX style registry is frozen");
        }
        if (planners.putIfAbsent(id, planner) != null) {
            throw new IllegalArgumentException("Area VFX style is already registered: " + id);
        }
    }

    @Override
    public Optional<AreaVfxStylePlanner> find(ResourceLocation id) {
        return Optional.ofNullable(id == null ? null : planners.get(id));
    }

    @Override
    public boolean frozen() {
        return frozen.get();
    }

    public void freeze() {
        frozen.set(true);
    }
}
