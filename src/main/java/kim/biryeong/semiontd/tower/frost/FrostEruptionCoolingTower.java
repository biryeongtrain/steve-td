package kim.biryeong.semiontd.tower.frost;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.SupportTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.damagesource.DamageSource;

/** 웨이브 시작 시 팀 혹한 타워 수를 스택으로 고정하고 레인 전체에 냉기를 분출합니다. */
public final class FrostEruptionCoolingTower extends SupportTower {
    private int waveStacks;
    private double operationChill;

    public FrostEruptionCoolingTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition position
    ) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public FrostEruptionCoolingTower(
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
        waveStacks = FrostTeamEffects.snapshotEruptionStacks(ownerPlayer(), teamId(), lane);
        onStateChanged(lane);
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        waveStacks = 0;
        operationChill = 0.0;
        super.resetForRound(lane);
    }

    void onEmissionWaveHit(PlayerLane lane) {
        onEmissionWaveHit(lane, FrostBalance.chillPerHit());
    }

    void onEmissionWaveHit(PlayerLane lane, double amount) {
        operationChill = Math.min(
                Math.max(0.0, FrostBalance.fullOperationEruptionChill()),
                operationChill + Math.max(0.0, amount)
        );
        FrostFullOperationService.onEruptionChillChanged(lane);
        onStateChanged(lane);
    }

    @Override
    protected boolean execute(PlayerLane lane) {
        SemionTowerEntity source = runtimeEntity(lane).orElse(null);
        if (source == null) {
            return false;
        }
        int affected = FrostTeamEffects.refreshEruptionAura(this, lane, waveStacks);
        if (waveStacks > 0 && affected > 0) {
            TowerVfxService.showFrostAura(source, FrostTowers.isExpandedEruptionCoolingDevice(type()));
        }
        return true;
    }

    @Override
    protected int cooldownTicksAfterExecute(PlayerLane lane) {
        return Math.max(1, FrostBalance.eruptionAuraRefreshTicks());
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
        if (source == null) {
            return false;
        }
        TowerVfxService.showFrostAura(source, FrostTowers.isExpandedEruptionCoolingDevice(type()));
        return true;
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
    public List<String> runtimeDetailLines() {
        boolean expanded = FrostTowers.isExpandedEruptionCoolingDevice(type());
        return List.of(
                "이번 웨이브 분출 " + waveStacks + "/" + FrostBalance.eruptionMaxStacks() + "스택",
                "본인 라인 공격력 -" + percent(FrostBalance.eruptionDamageReduction(waveStacks, true))
                        + " · 공격속도 -" + percent(FrostBalance.eruptionAttackSpeedReduction(waveStacks, true)),
                expanded
                        ? "아군 라인 공격력 -" + percent(FrostBalance.eruptionDamageReduction(waveStacks, false))
                                + " · 공격속도 -" + percent(FrostBalance.eruptionAttackSpeedReduction(waveStacks, false))
                        : "T1 · 다른 아군 라인 효과 비활성",
                "완전 가동 한기 " + percent(operationChill / Math.max(
                        0.000001, FrostBalance.fullOperationEruptionChill()))
        );
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof FrostEruptionCoolingTower previous) {
            waveStacks = previous.waveStacks;
            operationChill = previous.operationChill;
        }
    }

    int waveStacks() {
        return waveStacks;
    }

    double operationChill() {
        return operationChill;
    }
}
