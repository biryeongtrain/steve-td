package kim.biryeong.semiontd.tower.frost;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaTargetMode;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.SupportTower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.DamageSource;

/** 단계별 공격 주기마다 자기 레인의 폭 7, 전방 50 영역을 관통하는 고유 냉각장치. */
public final class FrostCoolingTower extends SupportTower {
    public FrostCoolingTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public FrostCoolingTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    protected boolean execute(PlayerLane lane) {
        SemionTowerEntity source = runtimeEntity(lane).orElse(null);
        if (source == null) {
            return false;
        }
        Vec3 direction = waveDirection(lane == null ? null : lane.laneLayout());
        if (direction.lengthSqr() <= 1.0E-6) {
            return false;
        }

        double range = Math.max(0.01, FrostBalance.coolingWaveRange(type()));
        double width = Math.max(0.01, FrostBalance.coolingWaveWidth(type()));
        double searchRadius = Math.hypot(range, width * 0.5) + 0.25;
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, "cooling_wave"),
                source,
                searchRadius,
                AreaVfxSpec.none()
        ).withFilter(target -> insideWave(source.position(), target.position(), direction, range, width));

        TowerAreaDamage.applyResolved(
                this,
                source,
                request,
                ignored -> FrostFullOperationService.fixedOutgoingDamage(
                        ownerPlayer(), source.level().getGameTime(), type().damage()),
                true,
                (target, damage, killed) -> {
                    if (!killed) {
                        FrostMonsterStates.applyChill(source, target, FrostAugments.emissionChill(this));
                    }
                },
                DamageType.PHYSICAL
        );
        hitExceptionalAlliedTargets(source, lane, direction, range, width, searchRadius);
        faceDirection(source, direction);
        TowerVfxService.showFrostWave(source, direction, range, width);
        return true;
    }

    @Override
    public List<String> runtimeDetailLines() {
        return List.of(
                "관통 파동 " + oneDecimal(FrostBalance.coolingWaveWidth(type())) + "×"
                        + oneDecimal(FrostBalance.coolingWaveRange(type())) + "칸",
                "적중당 한기 +" + percent(FrostAugments.emissionChill(this)),
                "고유 타워 · 1기 제한"
        );
    }

    @Override
    public boolean drawsAggro() {
        return false;
    }

    @Override
    public boolean countsForLaneDefense() {
        return false;
    }

    @Override
    public boolean participatesInFinalDefense() {
        return false;
    }

    public boolean showDebugVfx(PlayerLane lane) {
        SemionTowerEntity source = runtimeEntity(lane).orElse(null);
        Vec3 direction = waveDirection(lane == null ? null : lane.laneLayout());
        if (source == null || direction.lengthSqr() <= 1.0E-6) {
            return false;
        }
        TowerVfxService.showFrostWave(
                source,
                direction,
                Math.max(0.01, FrostBalance.coolingWaveRange(type())),
                Math.max(0.01, FrostBalance.coolingWaveWidth(type()))
        );
        return true;
    }

    @Override
    public double modifyResolvedAttackDamage(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double damageAmount
    ) {
        return FrostFullOperationService.fixedOutgoingDamage(
                ownerPlayer(), towerEntity.level().getGameTime(), damageAmount);
    }

    @Override
    public double modifyFinalIncomingDamage(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double originalDamage,
            double normallyReducedDamage
    ) {
        return FrostFullOperationService.fixedIncomingDamage(
                ownerPlayer(), towerEntity.level().getGameTime(), originalDamage, normallyReducedDamage);
    }

    private void hitExceptionalAlliedTargets(
            SemionTowerEntity source,
            PlayerLane lane,
            Vec3 direction,
            double range,
            double width,
            double searchRadius
    ) {
        TowerAreaEffectRequest request = TowerAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, "cooling_wave_allies"),
                source,
                searchRadius,
                TowerAreaTargetMode.REGISTERED,
                AreaVfxSpec.none()
        ).withFilter(target -> (target.tower() instanceof FrostHealingTower
                || target.tower() instanceof FrostEruptionCoolingTower
                || target.tower() instanceof FrostVanguardTower vanguard
                && FrostTowers.DONGTAE.id().equals(vanguard.type().id())
                || target.tower() instanceof FrostSplashTower splash
                && FrostTowers.isFrozenDumpling(splash.type()))
                && target.entity()
                        .map(entity -> insideWave(source.position(), entity.position(), direction, range, width))
                        .orElse(false));
        SemionTdApi.areaEffects().applyToTowers(request, target -> {
            if (target.tower() instanceof FrostHealingTower healingTower) {
                healingTower.onEmissionWaveHit(lane, FrostAugments.emissionChill(this));
            } else if (target.tower() instanceof FrostEruptionCoolingTower eruptionTower) {
                eruptionTower.onEmissionWaveHit(lane, FrostAugments.emissionChill(this));
            } else if (target.tower() instanceof FrostVanguardTower vanguardTower) {
                vanguardTower.onEmissionWaveHit(lane, FrostAugments.emissionChill(this));
            } else if (target.tower() instanceof FrostSplashTower splashTower) {
                splashTower.onEmissionWaveHit(lane, FrostAugments.emissionChill(this));
            }
            return AreaEffectOutcome.APPLIED;
        });
    }

    static Vec3 waveDirection(LaneRegionLayout layout) {
        if (layout == null) {
            return Vec3.ZERO;
        }
        List<Vec3> pathPoints = layout.pathPoints();
        for (int index = 0; index < pathPoints.size() - 1; index++) {
            Vec3 from = pathPoints.get(index);
            Vec3 to = pathPoints.get(index + 1);
            Vec3 againstMonsterTravel = new Vec3(from.x - to.x, 0.0, from.z - to.z);
            if (againstMonsterTravel.lengthSqr() > 1.0E-6) {
                return againstMonsterTravel.normalize();
            }
        }
        return Vec3.ZERO;
    }

    static boolean insideWave(
            Vec3 origin,
            Vec3 target,
            Vec3 direction,
            double range,
            double width
    ) {
        if (origin == null || target == null || direction == null || direction.lengthSqr() <= 1.0E-6) {
            return false;
        }
        Vec3 horizontalDirection = new Vec3(direction.x, 0.0, direction.z).normalize();
        Vec3 delta = target.subtract(origin);
        double projection = delta.x * horizontalDirection.x + delta.z * horizontalDirection.z;
        if (projection < 0.0 || projection > range) {
            return false;
        }
        double horizontalDistanceSqr = delta.x * delta.x + delta.z * delta.z;
        double perpendicularSqr = Math.max(0.0, horizontalDistanceSqr - projection * projection);
        double halfWidth = width * 0.5;
        return perpendicularSqr <= halfWidth * halfWidth;
    }

    private static void faceDirection(SemionTowerEntity source, Vec3 direction) {
        float yaw = (float) (Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0);
        source.setYRot(yaw);
        source.setYHeadRot(yaw);
        source.setYBodyRot(yaw);
    }

}
