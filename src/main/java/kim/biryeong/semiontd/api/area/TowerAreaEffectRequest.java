package kim.biryeong.semiontd.api.area;

import java.util.Objects;
import java.util.function.Predicate;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record TowerAreaEffectRequest(
        ResourceLocation effectId,
        SemionTowerEntity source,
        Vec3 center,
        double radius,
        TowerAreaTargetMode targetMode,
        boolean includeSource,
        Predicate<AreaTowerTarget> targetFilter,
        AreaVfxSpec vfx
) {
    public TowerAreaEffectRequest {
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(center, "center");
        if (!Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)) {
            throw new IllegalArgumentException("center must contain finite coordinates");
        }
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("radius must be finite and greater than zero");
        }
        targetMode = targetMode == null ? TowerAreaTargetMode.REGISTERED : targetMode;
        targetFilter = targetFilter == null ? ignored -> true : targetFilter;
        vfx = vfx == null ? AreaVfxSpec.none() : vfx;
    }

    public static TowerAreaEffectRequest aroundTower(
            ResourceLocation effectId,
            SemionTowerEntity source,
            double radius,
            TowerAreaTargetMode mode,
            AreaVfxSpec vfx
    ) {
        return new TowerAreaEffectRequest(effectId, source, source.position(), radius, mode, false, null, vfx);
    }

    public TowerAreaEffectRequest withFilter(Predicate<AreaTowerTarget> filter) {
        return new TowerAreaEffectRequest(effectId, source, center, radius, targetMode, includeSource, filter, vfx);
    }
}
