package kim.biryeong.semiontd.tower.atlantis;

import java.util.Objects;
import kim.biryeong.semiontd.game.GridPosition;
import net.minecraft.world.phys.Vec3;

/**
 * A high pressure field deployed on the lane path. The centre is independent of the owning
 * turtle tower's position, so moving or upgrading a turtle redeploys the field rather than
 * dragging a radius around with the entity.
 */
public record PressureZone(GridPosition ownerPosition, Vec3 center, double radius, double allyDamageReduction) {
    public PressureZone {
        Objects.requireNonNull(ownerPosition, "ownerPosition");
        Objects.requireNonNull(center, "center");
        if (radius <= 0.0) {
            throw new IllegalArgumentException("radius must be positive: " + radius);
        }
        if (allyDamageReduction < 0.0 || allyDamageReduction >= 1.0) {
            throw new IllegalArgumentException("allyDamageReduction out of range: " + allyDamageReduction);
        }
    }

    public boolean contains(Vec3 position) {
        return position != null && position.distanceToSqr(center) <= radius * radius;
    }
}
