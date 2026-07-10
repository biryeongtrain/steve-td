package kim.biryeong.semiontd.api.area;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record MonsterAreaEffectRequest(
        ResourceLocation effectId,
        SemionTowerEntity source,
        Vec3 center,
        double radius,
        Set<UUID> excludedTargetIds,
        Predicate<SemionMonsterEntity> targetFilter,
        AreaVfxSpec vfx
) {
    public MonsterAreaEffectRequest {
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(source, "source");
        validateCenter(center);
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("radius must be finite and greater than zero");
        }
        excludedTargetIds = excludedTargetIds == null ? Set.of() : Set.copyOf(excludedTargetIds);
        targetFilter = targetFilter == null ? ignored -> true : targetFilter;
        vfx = vfx == null ? AreaVfxSpec.none() : vfx;
    }

    public static MonsterAreaEffectRequest aroundTarget(
            ResourceLocation effectId,
            SemionTowerEntity source,
            SemionMonsterEntity target,
            double radius,
            AreaVfxSpec vfx
    ) {
        Objects.requireNonNull(target, "target");
        return new MonsterAreaEffectRequest(effectId, source, target.position(), radius, Set.of(target.getUUID()), null, vfx);
    }

    public static MonsterAreaEffectRequest aroundTower(
            ResourceLocation effectId,
            SemionTowerEntity source,
            double radius,
            AreaVfxSpec vfx
    ) {
        return new MonsterAreaEffectRequest(effectId, source, source.position(), radius, Set.of(), null, vfx);
    }

    public MonsterAreaEffectRequest withFilter(Predicate<SemionMonsterEntity> filter) {
        return new MonsterAreaEffectRequest(effectId, source, center, radius, excludedTargetIds, filter, vfx);
    }

    public MonsterAreaEffectRequest including(UUID targetId) {
        if (targetId == null || !excludedTargetIds.contains(targetId)) {
            return this;
        }
        java.util.HashSet<UUID> updated = new java.util.HashSet<>(excludedTargetIds);
        updated.remove(targetId);
        return new MonsterAreaEffectRequest(effectId, source, center, radius, updated, targetFilter, vfx);
    }

    private static void validateCenter(Vec3 center) {
        Objects.requireNonNull(center, "center");
        if (!Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)) {
            throw new IllegalArgumentException("center must contain finite coordinates");
        }
    }
}
