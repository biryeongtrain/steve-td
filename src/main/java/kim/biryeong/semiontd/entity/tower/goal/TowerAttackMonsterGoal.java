package kim.biryeong.semiontd.entity.tower.goal;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

public final class TowerAttackMonsterGoal extends Goal {
    private final SemionTowerEntity tower;
    private int cooldownTicks;

    public TowerAttackMonsterGoal(SemionTowerEntity tower) {
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

        if (tower.needsFinalDefenseReturn()) {
            tower.returnToFinalDefenseAreaIfNeeded();
            return;
        }

        SemionMonsterEntity target = findTarget();
        tower.recordCurrentAttackTarget(target);
        if (target == null) {
            tower.getNavigation().stop();
            tower.playAnimation(SemionAnimationState.IDLE);
            return;
        }

        tower.getLookControl().setLookAt(target);
        double attackRangeSqr = tower.attackRange() * tower.attackRange();
        double distanceSqr = tower.distanceToSqr(target);
        if (distanceSqr > attackRangeSqr) {
            if (tower.deployedAtFinalDefense()) {
                tower.getNavigation().stop();
                tower.playAnimation(SemionAnimationState.IDLE);
                return;
            }
            tower.moveTowardTarget(target.position(), tower.chaseSpeedModifier());
            return;
        }

        tower.getNavigation().stop();
        if (cooldownTicks > 0) {
            tower.playAnimation(SemionAnimationState.IDLE);
            return;
        }

        tower.playAnimation(SemionAnimationState.ATTACK);
        double damageAmount = tower.attackDamageAmount(target);
        playRangedAttackSound();
        boolean killedPrimaryTarget = tower.damageTarget(target, damageAmount);
        tower.recordAttack(target, damageAmount, killedPrimaryTarget);
        cooldownTicks = tower.attackIntervalTicks();
    }

    private SemionMonsterEntity findTarget() {
        SemionMonsterEntity sharedTarget = tower.sharedAttackTarget();
        if (sharedTarget != null) {
            return sharedTarget;
        }
        if (tower.usesSharedAttackTarget()) {
            return null;
        }

        List<SemionMonsterEntity> targets = targetCandidates();
        if (targets.isEmpty()) {
            return null;
        }

        SemionMonsterEntity towerSelectedTarget = tower.selectAttackTarget(targets);
        if (towerSelectedTarget != null) {
            return towerSelectedTarget;
        }

        Comparator<SemionMonsterEntity> targetPriority = Comparator
                .comparingDouble((SemionMonsterEntity monster) -> monster.runtimeMonster().targetPriorityScore())
                .thenComparingDouble(monster -> -tower.distanceToSqr(monster));
        return targets.stream()
                .filter(this::isInAttackRange)
                .max(targetPriority)
                .orElseGet(() -> targets.stream()
                        .min(Comparator.comparingDouble(tower::distanceToSqr))
                        .orElse(null));
    }

    private List<SemionMonsterEntity> targetCandidates() {
        AABB searchBox = tower.targetSearchBox();
        return tower.level().getEntities(
                        tower,
                        searchBox,
                        entity -> entity instanceof SemionMonsterEntity monster
                                && tower.isValidAttackTarget(monster)
                ).stream()
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .toList();
    }

    private boolean isInAttackRange(SemionMonsterEntity monster) {
        double attackRangeSqr = tower.attackRange() * tower.attackRange();
        return tower.distanceToSqr(monster) <= attackRangeSqr;
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

}
