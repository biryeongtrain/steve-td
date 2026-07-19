package kim.biryeong.semiontd.tower.undead;

import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.SplashTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.phys.Vec3;

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
        return java.util.List.of("사망 스택 " + killStacks + "/" + stackCap()
                + " (공격력 +" + oneDecimal(killStacks * damagePerStack())
                + ", 체력 +" + oneDecimal(killStacks * healthPerStack()) + ")");
    }

    @Override
    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
        super.onAttack(towerEntity, target, damageAmount, killedTarget);
        heal(towerEntity, damageAmount);
    }

    @Override
    protected boolean damage(SemionTowerEntity tower, SemionMonsterEntity monster, double damage) {
        double splashDamage = damage * getSplashRatio();
        boolean killed = damageTarget(tower, monster, splashDamage);
        heal(tower, splashDamage);
        if (killed) {
            onKill(tower, monster, splashDamage);
        }
        return killed;
    }

    @Override
    public void onKill(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
    }

    @Override
    public void onNearbyMonsterDeath(PlayerLane lane, Monster monster, Vec3 deathPosition) {
        if (isWithinDeathStackRange(deathPosition)) {
            incrementDeathStack(lane);
        }
    }

    @Override
    public void onNearbyTowerDeath(PlayerLane lane, Tower destroyedTower) {
        if (destroyedTower != null && isWithinDeathStackRange(destroyedTower.position())) {
            incrementDeathStack(lane);
        }
    }

    @Override
    protected boolean isWithinDeathStackRange(Vec3 deathPosition) {
        if (deathPosition == null) {
            return false;
        }
        double radius = Math.max(0.0, value("deathStackRange"));
        return deathPosition.distanceToSqr(
                position().x() + 0.5,
                position().y() + 1.0,
                position().z() + 0.5
        ) <= radius * radius;
    }

    private void incrementDeathStack(PlayerLane lane) {
        if (killStacks >= stackCap()) {
            return;
        }
        killStacks++;
        syncHealth(health() + healthPerStack());
        if (lane != null) {
            onStateChanged(lane);
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
