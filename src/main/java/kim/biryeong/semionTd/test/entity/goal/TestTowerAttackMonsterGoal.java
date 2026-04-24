package kim.biryeong.semionTd.test.entity.goal;

import java.util.Comparator;
import java.util.EnumSet;
import kim.biryeong.semionTd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semionTd.test.entity.SemionTestTowerEntity;
import net.minecraft.server.level.ServerLevel;
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

        AABB searchBox = tower.getBoundingBox().inflate(tower.targetAcquireRange());
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
                .max(Comparator.comparingDouble(monster -> monster.runtimeMonster().laneProgress()))
                .orElse(null);
        if (target == null) {
            tower.getNavigation().stop();
            return;
        }

        tower.getLookControl().setLookAt(target);
        double attackRangeSqr = tower.attackRange() * tower.attackRange();
        double distanceSqr = tower.distanceToSqr(target);
        if (distanceSqr > attackRangeSqr) {
            moveToward(target);
            return;
        }

        tower.getNavigation().stop();
        if (cooldownTicks > 0) {
            return;
        }

        float previousHealth = target.getHealth();
        boolean damaged = tower.level() instanceof ServerLevel serverLevel
                && tower.doHurtTarget(serverLevel, target);
        if (!damaged) {
            target.hurt(tower.damageSources().mobAttack(tower), (float) tower.attackDamageAmount());
            damaged = target.getHealth() < previousHealth - 0.01F;
        }
        if (!damaged || target.getHealth() >= previousHealth - 0.01F) {
            float nextHealth = Math.max(0.0F, previousHealth - (float) tower.attackDamageAmount());
            target.setHealth(nextHealth);
            if (nextHealth <= 0.0F) {
                target.discard();
            }
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

