package kim.biryeong.semiontd.tower.animal;

import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;

public class RabbitTower extends AnimalStackTower {
    public RabbitTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public RabbitTower(
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
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        double amount = damageAmount + currentStacks() * value("damagePerStack");
        return hasLeaderAura() ? amount * (1.0 + leaderValue("leaderDamageBonus")) : amount;
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        if ((is(AnimalTowers.T2_RABBIT_TOWER) || isT3OrLeader()) && atMaxStacks()) {
            return Math.max(1, baseIntervalTicks - ticks("maxStackExtraIntervalReduction"));
        }
        return baseIntervalTicks;
    }

    @Override
    public double adjustAttackRange(double baseRange) {
        return baseRange + (hasLeaderAura() ? leaderValue("leaderRangeBonus") : 0.0);
    }

    @Override
    public java.util.List<String> runtimeDetailLines() {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>(super.runtimeDetailLines());
        lines.add("무리 효과 공격력 +" + oneDecimal(currentStacks() * value("damagePerStack")));
        if ((is(AnimalTowers.T2_RABBIT_TOWER) || isT3OrLeader()) && atMaxStacks()) {
            lines.add("최대 무리 효과 공격 간격 -" + ticks("maxStackExtraIntervalReduction") + "틱");
        }
        if (isT3OrLeader() && atMaxStacks()) {
            lines.add("최대 무리 효과 추가 공격 피해 " + percent(value("extraAttackDamageRatio")));
        }
        if (hasLeaderAura()) {
            lines.add("우두머리 효과 공격 피해 +" + percent(leaderValue("leaderDamageBonus"))
                    + ", 사거리 +" + oneDecimal(leaderValue("leaderRangeBonus")));
        }
        return lines;
    }

    @Override
    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
        if (!isT3OrLeader() || !atMaxStacks() || killedTarget || towerEntity == null || target == null || !target.isAlive()) {
            return;
        }
        boolean killed = damageBasicAttackTargetResult(
                towerEntity, target, damageAmount * value("extraAttackDamageRatio")
        ).killed();
        TowerVfxService.showSecondaryAttack(towerEntity, target);
        if (killed) {
            onKill(towerEntity, target, damageAmount);
        }
    }

    @Override
    protected boolean isStackFamily(Tower tower) {
        return tower != null && (
                tower.type().id().equals(AnimalTowers.T1_RABBIT_TOWER.id())
                        || tower.type().id().equals(AnimalTowers.T2_RABBIT_TOWER.id())
                        || tower.type().id().equals(AnimalTowers.T3_RABBIT_TOWER.id())
                        || tower.type().id().equals(AnimalTowers.T4_RABBIT_LEADER_TOWER.id())
        );
    }

    @Override
    protected int maxStacks() {
        return TowerBalanceRuntime.abilityInt(type().id(), "maxStacks");
    }

    @Override
    protected TowerType leaderBaseType() {
        return AnimalTowers.T3_RABBIT_TOWER;
    }

    @Override
    protected TowerType leaderType() {
        return AnimalTowers.T4_RABBIT_LEADER_TOWER;
    }

    private boolean is(TowerType towerType) {
        return type().id().equals(towerType.id());
    }

    private boolean isT3OrLeader() {
        return is(AnimalTowers.T3_RABBIT_TOWER) || isLeader();
    }

    private double value(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    private int ticks(String key) {
        return TowerBalanceRuntime.abilityTicks(type().id(), key);
    }
}
