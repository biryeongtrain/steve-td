package kim.biryeong.semiontd.tower.frost;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import net.minecraft.world.damagesource.DamageSource;

/** 웨이브 시작 시 계열 수를 고정하여 단계별 피해 감소를 적용하는 앞라인 타워. */
public final class FrostVanguardTower extends ProductionTower {
    private int waveFamilyCount;
    private double waveDamageReduction;
    private double chill;

    public FrostVanguardTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public FrostVanguardTower(
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
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        super.onWaveStarted(lane, currentRound);
        waveFamilyCount = lane == null ? 0 : (int) lane.towers().stream()
                .filter(tower -> ownerPlayer().equals(tower.ownerPlayer()))
                .map(Tower::type)
                .filter(FrostTowers::isVanguard)
                .count();
        waveDamageReduction = FrostBalance.vanguardDamageReduction(type(), waveFamilyCount);
        onStateChanged(lane);
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        waveFamilyCount = 0;
        waveDamageReduction = 0.0;
        chill = 0.0;
        super.resetForRound(lane);
    }

    @Override
    public double modifyIncomingDamage(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double damageAmount
    ) {
        return damageAmount * Math.max(0.0, 1.0 - waveDamageReduction);
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

    @Override
    public double modifyResolvedOutgoingDamage(
            SemionTowerEntity towerEntity,
            kim.biryeong.semiontd.entity.monster.SemionMonsterEntity target,
            double damageAmount
    ) {
        return FrostFullOperationService.fixedOutgoingDamage(
                ownerPlayer(), towerEntity.level().getGameTime(), damageAmount);
    }

    @Override
    public double modifyResolvedAttackDamage(
            SemionTowerEntity towerEntity,
            kim.biryeong.semiontd.entity.monster.SemionMonsterEntity target,
            double damageAmount
    ) {
        return FrostFullOperationService.fixedOutgoingDamage(
                ownerPlayer(), towerEntity.level().getGameTime(), damageAmount);
    }

    @Override
    public List<String> runtimeDetailLines() {
        if (waveFamilyCount <= 0) {
            return List.of("준비 중 · 다음 웨이브 시작 시 계열 수 고정");
        }
        List<String> lines = new java.util.ArrayList<>(List.of(
                "이번 웨이브 계열 " + waveFamilyCount + "기",
                "받는 피해 -" + String.format(Locale.ROOT, "%.0f%%", waveDamageReduction * 100.0)
        ));
        if (FrostTowers.DONGTAE.id().equals(type().id())) {
            lines.add("완전동결 한기 " + String.format(Locale.ROOT, "%.0f%%", chill * 100.0));
        }
        return List.copyOf(lines);
    }

    void onEmissionWaveHit(PlayerLane lane) {
        onEmissionWaveHit(lane, FrostBalance.chillPerHit());
    }

    void onEmissionWaveHit(PlayerLane lane, double amount) {
        if (!FrostTowers.DONGTAE.id().equals(type().id())) {
            return;
        }
        double threshold = Math.max(0.000001, FrostBalance.chillThreshold());
        chill = Math.min(threshold, chill + Math.max(0.0, amount));
        if (chill + 1.0E-9 < threshold) {
            onStateChanged(lane);
            return;
        }
        chill = 0.0;
        SemionTowerEntity source = runtimeEntity(lane).orElse(null);
        if (source != null) {
            source.applyTimedEffect(
                    TimedEffectType.TOWER_DAMAGE_REDUCTION,
                    FrostBalance.fullyFrozenDamageReduction(),
                    FrostBalance.fullyFrozenDurationTicks()
            );
            MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTower(
                    AreaEffectIds.tower(this, "fully_frozen"),
                    source,
                    Math.max(0.01, FrostBalance.fullyFrozenChillRadius()),
                    AreaVfxSpec.onTrigger(AreaVfxStyles.DEBUFF)
            );
            SemionTdApi.areaEffects().applyToMonsters(request, target -> {
                FrostMonsterStates.ChillResult result = FrostMonsterStates.applyChill(source, target, FrostBalance.chillPerHit());
                return result.currentChill() > result.previousChill() || result.becameRefrigerated()
                        ? AreaEffectOutcome.APPLIED
                        : AreaEffectOutcome.UNCHANGED;
            });
        }
        FrostFullOperationService.recordSpecialActivation(
                lane, FrostFullOperationService.TriggerFamily.DONGTAE);
        onStateChanged(lane);
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof FrostVanguardTower previous) {
            waveFamilyCount = previous.waveFamilyCount;
            waveDamageReduction = previous.waveDamageReduction;
            chill = previous.chill;
        }
    }

    int waveFamilyCount() {
        return waveFamilyCount;
    }

    double waveDamageReduction() {
        return waveDamageReduction;
    }

    double chillForTest() {
        return chill;
    }
}
