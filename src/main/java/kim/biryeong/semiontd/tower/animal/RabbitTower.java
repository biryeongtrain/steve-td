package kim.biryeong.semiontd.tower.animal;

import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
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
        return damageAmount + currentStacks() * value("damagePerStack");
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        if ((is(AnimalTowers.T2_RABBIT_TOWER) || is(AnimalTowers.T3_RABBIT_TOWER)) && atMaxStacks()) {
            return Math.max(1, baseIntervalTicks - ticks("maxStackExtraIntervalReduction"));
        }
        return baseIntervalTicks;
    }

    @Override
    public java.util.List<String> runtimeDetailLines() {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>(super.runtimeDetailLines());
        lines.add("무리 효과 공격력 +" + oneDecimal(currentStacks() * value("damagePerStack")));
        if ((is(AnimalTowers.T2_RABBIT_TOWER) || is(AnimalTowers.T3_RABBIT_TOWER)) && atMaxStacks()) {
            lines.add("최대 무리 효과 공격 간격 -" + ticks("maxStackExtraIntervalReduction") + "틱");
        }
        if (is(AnimalTowers.T3_RABBIT_TOWER) && atMaxStacks()) {
            lines.add("최대 무리 효과 추가 공격 피해 " + percent(value("extraAttackDamageRatio")));
        }
        return lines;
    }

    @Override
    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
        if (!is(AnimalTowers.T3_RABBIT_TOWER) || !atMaxStacks() || killedTarget || towerEntity == null || target == null || !target.isAlive()) {
            return;
        }
        boolean killed = damageTarget(towerEntity, target, damageAmount * value("extraAttackDamageRatio"));
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
        );
    }

    @Override
    protected int maxStacks() {
        return TowerBalanceRuntime.abilityInt(type().id(), "maxStacks");
    }

    private boolean is(TowerType towerType) {
        return type().id().equals(towerType.id());
    }

    private double value(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    private int ticks(String key) {
        return TowerBalanceRuntime.abilityTicks(type().id(), key);
    }
}
