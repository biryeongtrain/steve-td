package kim.biryeong.semiontd.test.entity.goal;

import java.util.Comparator;
import java.util.EnumSet;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.test.entity.SemionTestTowerEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

public final class TestTowerAttackMonsterGoal extends Goal {
    private final SemionTestTowerEntity tower;
    private int cooldownTicks;

    public TestTowerAttackMonsterGoal(SemionTestTowerEntity tower) {
        this.tower = tower;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return tower.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return tower.isAlive();
    }

    @Override
    public void tick() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }

        AABB searchBox = tower.targetSearchBox();
        SemionMonsterEntity target = tower.level().getEntities(
                        tower,
                        searchBox,
                        entity -> entity instanceof SemionMonsterEntity monster
                                && monster.isAlive()
                                && monster.runtimeMonster() != null
                                && tower.defendsLane(monster.runtimeMonster().targetLaneId())
                ).stream()
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .max(Comparator.comparingDouble(monster -> monster.runtimeMonster().targetPriorityScore()))
                .orElse(null);
        if (target == null) {
            tower.getNavigation().stop();
            tower.playAnimation(SemionAnimationState.IDLE);
            return;
        }

        tower.getLookControl().setLookAt(target);
        double attackRangeSqr = tower.attackRange() * tower.attackRange();
        double distanceSqr = tower.distanceToSqr(target);
        if (distanceSqr > attackRangeSqr) {
            tower.playAnimation(SemionAnimationState.WALK);
            moveToward(target);
            return;
        }

        tower.getNavigation().stop();
        if (cooldownTicks > 0) {
            tower.playAnimation(SemionAnimationState.IDLE);
            return;
        }

        tower.playAnimation(SemionAnimationState.ATTACK);
        Monster runtimeMonster = target.runtimeMonster();
        if (runtimeMonster != null) {
            runtimeMonster.recordLastHit(tower.ownerPlayer(), KillSourceKind.TOWER);
        }
        double damageAmount = target.towerDamageTaken(tower.attackDamageAmount());

        float previousHealth = target.getHealth();
        target.hurt(tower.damageSources().mobAttack(tower), (float) damageAmount);
        boolean damaged = target.getHealth() < previousHealth - 0.01F;
        if (!damaged || target.getHealth() >= previousHealth - 0.01F) {
            float nextHealth = Math.max(0.0F, previousHealth - (float) damageAmount);
            target.setHealth(nextHealth);
            if (runtimeMonster != null) {
                runtimeMonster.syncHealth(nextHealth);
            }
            if (nextHealth <= 0.0F) {
                target.discard();
            }
        } else if (runtimeMonster != null) {
            runtimeMonster.syncHealth(target.getHealth());
        }
        cooldownTicks = tower.attackIntervalTicks();
    }

    private void moveToward(SemionMonsterEntity target) {
        var current = tower.position();
        var targetPos = target.position();
        var delta = targetPos.subtract(current);
        double horizontalDistance = Math.hypot(delta.x, delta.z);
        if (horizontalDistance <= 0.001) {
            return;
        }

        double step = Math.min(tower.moveSpeedAmount(), horizontalDistance);
        double moveX = delta.x / horizontalDistance * step;
        double moveZ = delta.z / horizontalDistance * step;
        tower.setPos(current.x + moveX, tower.getY(), current.z + moveZ);
    }
}
