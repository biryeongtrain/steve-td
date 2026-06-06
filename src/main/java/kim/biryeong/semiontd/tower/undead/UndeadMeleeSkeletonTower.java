package kim.biryeong.semiontd.tower.undead;

import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.SplashTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class UndeadMeleeSkeletonTower extends SplashTower {
    private int killStacks;

    public UndeadMeleeSkeletonTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public UndeadMeleeSkeletonTower(
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
        return maxHealth() + killStacks * healthPerStack();
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        return damageAmount + killStacks * damagePerStack();
    }

    @Override
    public java.util.List<String> runtimeDetailLines() {
        return java.util.List.of("킬 스택 " + killStacks + "/" + stackCap()
                + " (공격력 +" + oneDecimal(killStacks * damagePerStack())
                + ", 체력 +" + oneDecimal(killStacks * healthPerStack()) + ")");
    }

    @Override
    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
        super.onAttack(towerEntity, target, damageAmount, killedTarget);
        heal(towerEntity, damageAmount);
    }

    @Override
    protected void damage(SemionTowerEntity tower, SemionMonsterEntity monster, double damage) {
        double splashDamage = damage * getSplashRatio();
        boolean killed = damageTarget(tower, monster, splashDamage);
        heal(tower, splashDamage);
        if (killed) {
            onKill(tower, monster, splashDamage);
        }
    }

    @Override
    public void onKill(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        if (killStacks >= stackCap()) {
            return;
        }
        killStacks++;
        if (towerEntity != null) {
            towerEntity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(currentMaxHealth());
            towerEntity.receiveHealing(healthPerStack());
        }
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof UndeadMeleeSkeletonTower skeletonTower) {
            killStacks = Math.min(stackCap(), skeletonTower.killStacks);
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

    private void heal(SemionTowerEntity towerEntity, double damageAmount) {
        if (towerEntity != null && damageAmount > 0.0) {
            towerEntity.receiveHealing(damageAmount * lifeStealRatio());
        }
    }

    private double lifeStealRatio() {
        return value("lifeStealRatio");
    }

    private double damagePerStack() {
        return value("damagePerStack");
    }

    private double healthPerStack() {
        return value("healthPerStack");
    }

    private int stackCap() {
        return TowerBalanceRuntime.abilityInt(type().id(), "stackCap");
    }

    private double value(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }
}
