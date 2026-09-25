package kim.biryeong.semiontd.tower.frost;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.TowerAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaTargetMode;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.SupportTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import net.minecraft.world.damagesource.DamageSource;

/** 냉기 방출 장치 계열의 파동을 한기로 저장해 치료 주기와 별도 회복파동으로 전환하는 지원 타워. */
public final class FrostHealingTower extends SupportTower {
    private double chill;
    private int waveFamilyCount;

    public FrostHealingTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition position
    ) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public FrostHealingTower(
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
        if (runtimeEntity(lane).isEmpty()) {
            return false;
        }
        emitHealingPulse(lane, 1.0, "healing_pulse");
        // A pulse consumes the configured interval even when nobody is wounded.
        return true;
    }

    @Override
    protected int cooldownTicksAfterExecute(PlayerLane lane) {
        return Math.max(1, TowerBalanceRuntime.abilityTicks(type().id(), "healIntervalTicks", 100));
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        super.onWaveStarted(lane, currentRound);
        waveFamilyCount = lane == null ? 0 : (int) lane.towers().stream()
                .filter(tower -> ownerPlayer().equals(tower.ownerPlayer()))
                .map(Tower::type)
                .filter(FrostTowers::isIcebox)
                .count();
        onStateChanged(lane);
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        chill = 0.0;
        waveFamilyCount = 0;
        super.resetForRound(lane);
    }

    void onEmissionWaveHit(PlayerLane lane) {
        onEmissionWaveHit(lane, FrostBalance.chillPerHit());
    }

    void onEmissionWaveHit(PlayerLane lane, double amount) {
        reduceCooldownTicks(FrostBalance.healerCoolingAdvanceTicks());
        double threshold = Math.max(0.000001, FrostBalance.chillThreshold());
        chill = Math.min(threshold, chill + Math.max(0.0, amount));
        if (chill + 1.0E-9 < threshold) {
            return;
        }
        // Refrigerant is consumed immediately: this tower never retains the monster debuffs.
        chill = 0.0;
        emitHealingPulse(
                lane,
                Math.max(0.0, FrostBalance.healerRefrigerantPulseMultiplier()),
                "refrigerant_healing_pulse"
        );
        FrostFullOperationService.recordSpecialActivation(
                lane, FrostFullOperationService.TriggerFamily.ICEBOX);
    }

    private void emitHealingPulse(PlayerLane lane, double multiplier, String effectName) {
        SemionTowerEntity source = runtimeEntity(lane).orElse(null);
        double radius = TowerBalanceRuntime.ability(type().id(), "healRadius", 6.0);
        double amount = TowerBalanceRuntime.ability(type().id(), "healAmount", 0.0) * multiplier;
        if (source == null || radius <= 0.0 || amount <= 0.0) {
            return;
        }
        double reduction = FrostBalance.healerDamageReduction(waveFamilyCount);
        int reductionTicks = TowerBalanceRuntime.abilityTicks(type().id(), "damageReductionTicks", 100);
        TowerAreaEffectRequest request = TowerAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, effectName),
                source,
                radius,
                TowerAreaTargetMode.REGISTERED,
                AreaVfxSpec.onChange(AreaVfxStyles.BUFF)
        );
        SemionTdApi.areaEffects().applyToTowers(request, target -> {
            Tower ally = target.tower();
            SemionTowerEntity entity = target.entity().orElse(null);
            if (entity == null || ally.health() >= ally.currentMaxHealth() || !healTarget(entity, amount)) {
                return AreaEffectOutcome.UNCHANGED;
            }
            entity.playHealingAnimation();
            if (reduction > 0.0 && reductionTicks > 0) {
                entity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION, reduction, reductionTicks);
            }
            return AreaEffectOutcome.APPLIED;
        });
    }

    @Override
    public List<String> runtimeDetailLines() {
        return List.of(
                "치료 " + oneDecimal(TowerBalanceRuntime.ability(type().id(), "healAmount", 0.0))
                        + " · 반경 " + oneDecimal(TowerBalanceRuntime.ability(type().id(), "healRadius", 6.0)) + "칸",
                "이번 웨이브 계열 " + waveFamilyCount + "기 · 치료 보호 " + percent(
                        FrostBalance.healerDamageReduction(waveFamilyCount)),
                "한기 " + percent(chill / Math.max(0.000001, FrostBalance.chillThreshold())),
                "다음 치료까지 " + oneDecimal(cooldownTicksRemaining() / 20.0) + "초"
        );
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
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof FrostHealingTower previous) {
            chill = previous.chill;
            waveFamilyCount = previous.waveFamilyCount;
        }
    }

    double chillForTest() {
        return chill;
    }

    int waveFamilyCountForTest() {
        return waveFamilyCount;
    }
}
