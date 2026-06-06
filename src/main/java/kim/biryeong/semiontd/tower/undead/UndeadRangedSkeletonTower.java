package kim.biryeong.semiontd.tower.undead;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;

public class UndeadRangedSkeletonTower extends EntityBackedTower {
    private double killStackDamage;

    public UndeadRangedSkeletonTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public UndeadRangedSkeletonTower(
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
        return damageAmount + killStackDamage;
    }

    @Override
    public java.util.List<String> runtimeDetailLines() {
        double step = stackDamageStep();
        int stacks = step <= 0.0 ? 0 : (int) Math.round(killStackDamage / step);
        int maxStacks = step <= 0.0 ? 0 : (int) Math.round(stackDamageCap() / step);
        return java.util.List.of("킬 스택 " + stacks + "/" + maxStacks + " (공격력 +" + oneDecimal(killStackDamage) + ")");
    }

    @Override
    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
        heal(towerEntity, damageAmount);
        for (SemionMonsterEntity extraTarget : pickExtraTargets(towerEntity, target, extraTargetCount())) {
            boolean killed = damageTarget(towerEntity, extraTarget, damageAmount);
            heal(towerEntity, damageAmount);
            if (killed) {
                onKill(towerEntity, extraTarget, damageAmount);
            }
        }
    }

    @Override
    public void onKill(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        killStackDamage = Math.min(stackDamageCap(), killStackDamage + stackDamageStep());
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof UndeadRangedSkeletonTower skeletonTower) {
            killStackDamage = Math.min(stackDamageCap(), skeletonTower.killStackDamage);
        }
    }

    private List<SemionMonsterEntity> pickExtraTargets(SemionTowerEntity towerEntity, SemionMonsterEntity primary, int count) {
        if (towerEntity == null || count <= 0) {
            return List.of();
        }
        List<SemionMonsterEntity> candidates = new ArrayList<>(towerEntity.level().getEntities(
                towerEntity,
                towerEntity.targetSearchBox(),
                entity -> entity instanceof SemionMonsterEntity monster
                        && monster.isAlive()
                        && monster != primary
                        && monster.runtimeMonster() != null
                        && towerEntity.defendsLane(monster.runtimeMonster().targetLaneId())
                        && towerEntity.distanceToSqr(monster) <= towerEntity.attackRange() * towerEntity.attackRange()
        ).stream()
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .toList());
        java.util.Collections.shuffle(candidates, new java.util.Random(towerEntity.level().random.nextLong()));
        return candidates.stream().limit(count).toList();
    }

    private void heal(SemionTowerEntity towerEntity, double damageAmount) {
        if (towerEntity != null && damageAmount > 0.0) {
            towerEntity.receiveHealing(damageAmount * lifeStealRatio());
        }
    }

    private int extraTargetCount() {
        return TowerBalanceRuntime.abilityInt(type().id(), "extraTargets");
    }

    private double lifeStealRatio() {
        return value("lifeStealRatio");
    }

    private double stackDamageStep() {
        return value("stackDamage");
    }

    private double stackDamageCap() {
        return value("stackDamageCap");
    }

    private double value(String key) {
        return TowerBalanceRuntime.ability(type().id(), key);
    }
}
