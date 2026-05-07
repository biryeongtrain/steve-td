package kim.biryeong.semiontd.entity.monster.goal;

import java.util.EnumSet;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class MonsterAttackTargetGoal extends Goal {
    private final SemionMonsterEntity monster;
    private final double speedModifier;
    private int cooldownTicks;

    public MonsterAttackTargetGoal(SemionMonsterEntity monster, double speedModifier) {
        this.monster = monster;
        this.speedModifier = speedModifier;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return monster.isAlive() && monster.getTarget() != null && monster.getTarget().isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }

        LivingEntity target = monster.getTarget();
        if (target == null || !target.isAlive()) {
            monster.playAnimation(SemionAnimationState.IDLE);
            return;
        }

        monster.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double attackRange = monster.attackRange();
        double attackRangeSqr = attackRange * attackRange;
        double distanceSqr = monster.distanceToSqr(target);
        if (distanceSqr > attackRangeSqr) {
            monster.playAnimation(SemionAnimationState.WALK);
            double adjustedSpeed = speedModifier * monster.movementSpeedMultiplier();
            monster.getNavigation().moveTo(target, adjustedSpeed);
            monster.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), adjustedSpeed);
            return;
        }

        monster.getNavigation().stop();
        if (cooldownTicks > 0) {
            monster.playAnimation(SemionAnimationState.IDLE);
            return;
        }

        monster.playAnimation(SemionAnimationState.ATTACK);
        target.hurt(monster.damageSources().mobAttack(monster), (float) monster.getAttributeValue(Attributes.ATTACK_DAMAGE));
        cooldownTicks = monster.attackIntervalTicks();
    }
}
