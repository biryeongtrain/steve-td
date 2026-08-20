package kim.biryeong.semiontd.api.area;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * @param crossLane 다른 레인을 노리는 몬스터까지 대상에 넣을지. 기본은 {@code false} 로, 타워는
 *     자기 레인(과 최종 방어)만 때립니다. 레인을 자유롭게 돌아다니는 시전자만 켭니다.
 * @param maxTargets 중심에서 가까운 순서로 적용할 최대 대상 수
 */
public record MonsterAreaEffectRequest(
        ResourceLocation effectId,
        SemionTowerEntity source,
        Vec3 center,
        double radius,
        Set<UUID> excludedTargetIds,
        Predicate<SemionMonsterEntity> targetFilter,
        AreaVfxSpec vfx,
        boolean crossLane,
        int maxTargets
) {
    /** 레인 경계를 지키는 기본 요청. 타워는 전부 이쪽입니다. */
    public MonsterAreaEffectRequest(
            ResourceLocation effectId,
            SemionTowerEntity source,
            Vec3 center,
            double radius,
            Set<UUID> excludedTargetIds,
            Predicate<SemionMonsterEntity> targetFilter,
            AreaVfxSpec vfx
    ) {
        this(effectId, source, center, radius, excludedTargetIds, targetFilter, vfx, false, Integer.MAX_VALUE);
    }

    public MonsterAreaEffectRequest(
            ResourceLocation effectId,
            SemionTowerEntity source,
            Vec3 center,
            double radius,
            Set<UUID> excludedTargetIds,
            Predicate<SemionMonsterEntity> targetFilter,
            AreaVfxSpec vfx,
            boolean crossLane
    ) {
        this(effectId, source, center, radius, excludedTargetIds, targetFilter, vfx, crossLane, Integer.MAX_VALUE);
    }

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
        if (maxTargets <= 0) {
            throw new IllegalArgumentException("maxTargets must be greater than zero");
        }
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
        return new MonsterAreaEffectRequest(
                effectId, source, center, radius, excludedTargetIds, filter, vfx, crossLane, maxTargets);
    }

    /** 레인 경계를 무시합니다. 서 있는 자리에 있는 적이면 누구 레인이든 맞습니다. */
    public MonsterAreaEffectRequest acrossLanes() {
        return new MonsterAreaEffectRequest(
                effectId, source, center, radius, excludedTargetIds, targetFilter, vfx, true, maxTargets);
    }

    public MonsterAreaEffectRequest nearestTargets(int limit) {
        return new MonsterAreaEffectRequest(
                effectId, source, center, radius, excludedTargetIds, targetFilter, vfx, crossLane, limit);
    }

    public MonsterAreaEffectRequest including(UUID targetId) {
        if (targetId == null || !excludedTargetIds.contains(targetId)) {
            return this;
        }
        java.util.HashSet<UUID> updated = new java.util.HashSet<>(excludedTargetIds);
        updated.remove(targetId);
        return new MonsterAreaEffectRequest(
                effectId, source, center, radius, updated, targetFilter, vfx, crossLane, maxTargets);
    }

    private static void validateCenter(Vec3 center) {
        Objects.requireNonNull(center, "center");
        if (!Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)) {
            throw new IllegalArgumentException("center must contain finite coordinates");
        }
    }
}
