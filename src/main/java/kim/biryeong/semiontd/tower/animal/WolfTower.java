package kim.biryeong.semiontd.tower.animal;

import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;

public class WolfTower extends AnimalStackTower {
    public WolfTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public WolfTower(
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
        if (is(AnimalTowers.T3_WOLF_DPS_TOWER) && atMaxStacks()) {
            amount += value("maxStackDamageBonus");
        }
        return amount;
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        int interval = baseIntervalTicks - (int) Math.round(currentStacks() * value("intervalReductionPerStack"));
        if (!is(AnimalTowers.T1_WOLF_TOWER) && atMaxStacks()) {
            interval -= ticks("maxStackExtraIntervalReduction");
        }
        return Math.max(1, interval);
    }

    @Override
    public java.util.List<String> runtimeDetailLines() {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>(super.runtimeDetailLines());
        lines.add("무리 효과 공격력 +" + oneDecimal(currentStacks() * value("damagePerStack"))
                + ", 공격 간격 -" + Math.round(currentStacks() * value("intervalReductionPerStack")) + "틱");
        if (!is(AnimalTowers.T1_WOLF_TOWER) && atMaxStacks()) {
            lines.add("최대 무리 효과 공격 간격 추가 -" + ticks("maxStackExtraIntervalReduction") + "틱");
        }
        if (is(AnimalTowers.T3_WOLF_DPS_TOWER) && atMaxStacks()) {
            lines.add("최대 무리 효과 공격력 추가 +" + oneDecimal(value("maxStackDamageBonus")));
        }
        return lines;
    }

    @Override
    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
        if (is(AnimalTowers.T2_WOLF_DPS_TOWER) || is(AnimalTowers.T3_WOLF_DPS_TOWER)) {
            splash(towerEntity, target, damageAmount);
        }
    }

    @Override
    protected boolean isStackFamily(Tower tower) {
        return tower != null && (
                tower.type().id().equals(AnimalTowers.T1_WOLF_TOWER.id())
                        || tower.type().id().equals(AnimalTowers.T2_WOLF_DPS_TOWER.id())
                        || tower.type().id().equals(AnimalTowers.T3_WOLF_DPS_TOWER.id())
        );
    }

    @Override
    protected int maxStacks() {
        return TowerBalanceRuntime.abilityInt(type().id(), "maxStacks");
    }

    private void splash(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        if (towerEntity == null || target == null) {
            return;
        }
        double radius = value("splashRadius");
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                AreaEffectIds.tower(this, "splash"), towerEntity, target, radius,
                AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)
        );
        TowerAreaDamage.apply(this, towerEntity, request,
                monster -> damageAmount * value("splashDamageRatio"), true);
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
