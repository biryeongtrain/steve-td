package kim.biryeong.semiontd.tower.villager;

import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.SplashTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;

import java.util.UUID;

public class VillagerSplashTower extends SplashTower {
    private int attackAttempt = 0;
    private int survivalBouns = 0;

    public VillagerSplashTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public VillagerSplashTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition originalPosition, GridPosition currentPosition) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        if (!this.isDestroyed(lane)) {
            incrementSurvivalBonus();
        }
        super.resetForRound(lane);
    }

    private void incrementSurvivalBonus() {
        this.survivalBouns = Math.min(TowerBalanceRuntime.abilityInt(type().id(), "maxSurvivalStacks"), survivalBouns + 1);
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        double scale = value("bonusPerSurvivedRound");
        return damageAmount * (1 + scale * survivalBouns);
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        if (isT3()) {
            return (int) (baseIntervalTicks * (1 - value("bonusPerSurvivedRound") * survivalBouns));
        }

        return super.adjustAttackInterval(baseIntervalTicks);
    }

    @Override
    public java.util.List<String> runtimeDetailLines() {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        double bonus = value("bonusPerSurvivedRound") * survivalBouns;
        String effect = isT3() ? "피해/공속 +" + percent(bonus) : "피해 +" + percent(bonus);
        lines.add("생존 스택 " + survivalBouns + "/" + maxSurvivalStacks() + " (" + effect + ")");
        return lines;
    }

    @Override
    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
        super.onAttack(towerEntity, target, damageAmount, killedTarget); // splash
        if (isT3()) {
            attackAttempt++; // attack attempt
            int every = Math.max(1, TowerBalanceRuntime.abilityInt(type().id(), "extraAttackEvery"));
            if (!killedTarget && attackAttempt >= every) { // skip if target is dead. but stack attack attempt value
                attackAttempt -= every + 1; // remove stack. it will stack 1 because calls itself
                boolean killed = damageTarget(towerEntity, target, damageAmount); // damage main target
                this.onAttack(towerEntity, target, damageAmount, killed); // splash and trigger addition attack if has more stack
                if (killed) {
                    this.onKill(towerEntity, target, damageAmount); // trigger kill event
                }
            }
        }
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof VillagerSplashTower splashTower) {
            survivalBouns = Math.min(TowerBalanceRuntime.abilityInt(type().id(), "maxSurvivalStacks"), splashTower.survivalBouns);
        }
    }

    @Override
    public float getSplashRange() {
        return (float) value("splashRadius");
    }

    @Override
    public float getSplashRatio() {
        return (float) value("splashDamageRatio");
    }

    private double value(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }

    private int maxSurvivalStacks() {
        return TowerBalanceRuntime.abilityInt(type().id(), "maxSurvivalStacks");
    }

    private boolean isT3() {
        return type().id().equals(VillagerTowers.T3_CLERIC_TOWER.id());
    }
}
