package kim.biryeong.semiontd.tower.animal;

import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.world.damagesource.DamageSource;

public class PigTower extends AnimalStackTower {
    public PigTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public PigTower(
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
    public double currentMaxHealth() {
        return maxHealth() + currentStacks() * value("healthPerStack");
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        return damageAmount + currentStacks() * value("damagePerStack");
    }

    @Override
    public double modifyIncomingDamage(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount) {
        if (!is(AnimalTowers.T1_PIG_TOWER) && atMaxStacks()) {
            return damageAmount * (1.0 - value("damageReduction"));
        }
        return damageAmount;
    }

    @Override
    public java.util.List<String> runtimeDetailLines() {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>(super.runtimeDetailLines());
        lines.add("무리 효과 체력 +" + oneDecimal(currentStacks() * value("healthPerStack"))
                + ", 공격력 +" + oneDecimal(currentStacks() * value("damagePerStack")));
        if (!is(AnimalTowers.T1_PIG_TOWER) && atMaxStacks()) {
            lines.add("최대 무리 효과 받는 피해 -" + percent(value("damageReduction")));
        }
        if (is(AnimalTowers.T3_PIG_TOWER) && atMaxStacks()) {
            lines.add("최대 무리 효과 스플래시 활성");
        }
        return lines;
    }

    @Override
    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
        if (is(AnimalTowers.T3_PIG_TOWER) && atMaxStacks()) {
            splash(towerEntity, target, damageAmount);
        }
    }

    @Override
    protected void onStacksChanged(PlayerLane lane, int previousStacks, int currentStacks) {
        double healthDelta = (currentStacks - previousStacks) * value("healthPerStack");
        if (healthDelta > 0.0) {
            syncHealth(health() + healthDelta);
        } else if (healthDelta < 0.0) {
            syncHealth(health());
        }
        if (previousStacks != currentStacks) {
            onStateChanged(lane);
        }
    }

    @Override
    protected boolean isStackFamily(Tower tower) {
        return tower != null && (
                tower.type().id().equals(AnimalTowers.T1_PIG_TOWER.id())
                        || tower.type().id().equals(AnimalTowers.T2_PIG_TOWER.id())
                        || tower.type().id().equals(AnimalTowers.T3_PIG_TOWER.id())
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
}
