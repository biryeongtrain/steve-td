package kim.biryeong.semiontd.test.entity.goal;

import java.util.Comparator;
import java.util.EnumSet;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.test.entity.SemionTestTowerEntity;
import kim.biryeong.semiontd.tower.ProductionTowerBehavior;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

public final class TestTowerAttackMonsterGoal extends Goal {
    private final SemionTestTowerEntity tower;
    private int cooldownTicks;

    public TestTowerAttackMonsterGoal(SemionTestTowerEntity tower) {
        this.tower = tower;
        setFlags(EnumSet.of(Flag.LOOK));
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
            tower.getNavigation().stop();
            tower.playAnimation(SemionAnimationState.IDLE);
            return;
        }

        tower.getNavigation().stop();
        if (cooldownTicks > 0) {
            tower.playAnimation(SemionAnimationState.IDLE);
            return;
        }

        tower.playAnimation(SemionAnimationState.ATTACK);
        double damageAmount = tower.attackDamageAmount();
        playRangedAttackSound();
        boolean killedPrimaryTarget = damageTarget(target, damageAmount);
        tower.recordProductionAttack(killedPrimaryTarget);
        applyProductionSplash(target, damageAmount, killedPrimaryTarget);
        cooldownTicks = tower.attackIntervalTicks();
    }

    private void playRangedAttackSound() {
        if (!tower.playsRangedAttackSound()) {
            return;
        }
        tower.level().playSound(
                null,
                tower.blockPosition(),
                SoundEvents.ARROW_SHOOT,
                SoundSource.HOSTILE,
                0.7F,
                1.15F
        );
    }

    private boolean damageTarget(SemionMonsterEntity target, double baseDamage) {
        Monster runtimeMonster = target.runtimeMonster();
        if (runtimeMonster != null) {
            runtimeMonster.recordLastHit(tower.ownerPlayer(), KillSourceKind.TOWER);
        }
        double damageAmount = target.towerDamageTaken(baseDamage);

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
                return true;
            }
        } else if (runtimeMonster != null) {
            runtimeMonster.syncHealth(target.getHealth());
        }
        return target.isRemoved() || !target.isAlive() || target.getHealth() <= 0.0F
                || (runtimeMonster != null && !runtimeMonster.isAlive());
    }

    private void applyProductionSplash(SemionMonsterEntity primaryTarget, double primaryDamage, boolean killedPrimaryTarget) {
        ProductionTowerBehavior behavior = tower.productionBehavior();
        if (behavior == null) {
            return;
        }
        if (behavior.splashRadius() > 0.0 && behavior.splashDamageMultiplier() > 0.0) {
            damageNearby(primaryTarget, behavior.splashRadius(), primaryDamage * behavior.splashDamageMultiplier(), primaryTarget);
        }
        if (killedPrimaryTarget && behavior.killSplashRadius() > 0.0 && behavior.killSplashDamageMultiplier() > 0.0) {
            damageNearby(primaryTarget, behavior.killSplashRadius(), primaryDamage * behavior.killSplashDamageMultiplier(), primaryTarget);
        }
    }

    private void damageNearby(SemionMonsterEntity center, double radius, double damageAmount, SemionMonsterEntity excluded) {
        AABB splashBox = center.getBoundingBox().inflate(radius);
        tower.level().getEntities(
                        tower,
                        splashBox,
                        entity -> entity instanceof SemionMonsterEntity monster
                                && monster != excluded
                                && monster.isAlive()
                                && monster.runtimeMonster() != null
                                && tower.defendsLane(monster.runtimeMonster().targetLaneId())
                                && monster.distanceToSqr(center) <= radius * radius
                ).stream()
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .forEach(monster -> damageTarget(monster, damageAmount));
    }

}
