package kim.biryeong.semiontd.api.area;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

public record AreaEffectHit<T>(T target, Vec3 position, AreaEffectOutcome outcome) {
    public AreaEffectHit {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(outcome, "outcome");
    }
}
