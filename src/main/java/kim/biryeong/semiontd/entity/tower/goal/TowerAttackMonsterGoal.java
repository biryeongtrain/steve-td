package kim.biryeong.semiontd.entity.tower.goal;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

public final class TowerAttackMonsterGoal extends Goal {
    private static final int TARGET_RECHECK_INTERVAL_TICKS = 5;
    private static final double ENCOUNTER_RANGE_BONUS = 1.0;

    private final SemionTowerEntity tower;
    private int cooldownTicks;
    private int targetSearchCooldownTicks;
    private SemionMonsterEntity cachedTarget;

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
        if (tower.attackRange() <= 0.0) {
            cachedTarget = null;
            targetSearchCooldownTicks = 0;
            cooldownTicks = 0;
            tower.recordCurrentAttackTarget(null);
            tower.getNavigation().stop();
            tower.playAnimation(SemionAnimationState.IDLE);
            return;
        }

        if (tower.consumeForceAttackReady()) {
            cooldownTicks = 0;
        }
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
        tower.faceAttackTarget(target);
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
        float healthBeforeAttack = tower.getHealth();
        playRangedAttackSound();
        boolean killedPrimaryTarget = tower.damageTarget(target, damageAmount);
        tower.recordAttack(target, damageAmount, killedPrimaryTarget);
        TowerVfxService.showAttack(
                tower,
                target,
                killedPrimaryTarget,
                tower.getHealth() > healthBeforeAttack + 0.01F
        );
        cooldownTicks = tower.attackIntervalTicks();
    }

    private SemionMonsterEntity findTarget() {
        SemionMonsterEntity forcedTarget = selectForcedTarget();
        if (forcedTarget != null) {
            cachedTarget = forcedTarget;
            return forcedTarget;
        }
        SemionMonsterEntity sharedTarget = tower.sharedAttackTarget();
        if (isUsableTarget(sharedTarget)) {
            cachedTarget = preferAttackableFinalDefenseTarget(sharedTarget);
            return cachedTarget;
        }
        if (tower.usesSharedAttackTarget()) {
            if (tower.deployedAtFinalDefense()) {
                cachedTarget = selectAttackableTarget();
                return cachedTarget;
            }
            cachedTarget = null;
            return null;
        }

        boolean usableCachedTarget = isUsableCachedTarget();
        if (usableCachedTarget && targetSearchCooldownTicks > 0) {
            targetSearchCooldownTicks--;
            cachedTarget = preferAttackableFinalDefenseTarget(cachedTarget);
            return cachedTarget;
        }
        if (usableCachedTarget) {
            cachedTarget = null;
        } else {
            SemionMonsterEntity currentTarget = tower.currentAttackTarget();
            if (cachedTarget == null && isUsableTarget(currentTarget)) {
                cachedTarget = preferAttackableFinalDefenseTarget(currentTarget);
                targetSearchCooldownTicks = TARGET_RECHECK_INTERVAL_TICKS;
                return cachedTarget;
            }
            if (cachedTarget != null) {
                cachedTarget = null;
                targetSearchCooldownTicks = 0;
            }
        }

        if (targetSearchCooldownTicks > 0) {
            targetSearchCooldownTicks--;
            return null;
        }

        cachedTarget = selectTarget();
        targetSearchCooldownTicks = TARGET_RECHECK_INTERVAL_TICKS;
        return cachedTarget;
    }

    private SemionMonsterEntity selectForcedTarget() {
        var runtimeTower = tower.runtimeTower();
        if (runtimeTower == null || !runtimeTower.supportsForcedAttackTargeting()) {
            return null;
        }
        return runtimeTower.selectForcedAttackTarget(tower, targetCandidates()).orElse(null);
    }

    private SemionMonsterEntity preferAttackableFinalDefenseTarget(SemionMonsterEntity target) {
        if (!tower.deployedAtFinalDefense() || target == null || isInAttackRange(target)) {
            return target;
        }
        SemionMonsterEntity replacement = selectAttackableTarget();
        return replacement == null ? target : replacement;
    }

    private SemionMonsterEntity selectTarget() {
        List<SemionMonsterEntity> targets = targetCandidates();
        if (targets.isEmpty()) {
            return null;
        }

        List<SemionMonsterEntity> encounteredTargets = targets.stream()
                .filter(this::isInEncounterRange)
                .toList();
        if (encounteredTargets.isEmpty()) {
            return targets.stream()
                    .min(Comparator.comparingDouble(tower::distanceToSqr))
                    .orElse(null);
        }

        SemionMonsterEntity towerSelectedTarget = tower.selectAttackTarget(encounteredTargets);
        if (towerSelectedTarget != null) {
            return towerSelectedTarget;
        }

        Comparator<SemionMonsterEntity> targetPriority = Comparator
                .comparingDouble((SemionMonsterEntity monster) -> monster.runtimeMonster().targetPriorityScore())
                .thenComparingLong(this::stableTargetOffset)
                .thenComparingDouble(monster -> -tower.distanceToSqr(monster));
        return encounteredTargets.stream().max(targetPriority).orElse(null);
    }

    private SemionMonsterEntity selectAttackableTarget() {
        List<SemionMonsterEntity> targets = targetCandidates().stream()
                .filter(this::isInAttackRange)
                .toList();
        if (targets.isEmpty()) {
            return null;
        }

        SemionMonsterEntity towerSelectedTarget = tower.selectAttackTarget(targets);
        if (towerSelectedTarget != null) {
            return towerSelectedTarget;
        }

        Comparator<SemionMonsterEntity> targetPriority = Comparator
                .comparingDouble((SemionMonsterEntity monster) -> monster.runtimeMonster().targetPriorityScore())
                .thenComparingLong(this::stableTargetOffset)
                .thenComparingDouble(monster -> -tower.distanceToSqr(monster));
        return targets.stream().max(targetPriority).orElse(null);
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
                .filter(this::isInTargetSearchRange)
                .toList();
    }

    private boolean isUsableCachedTarget() {
        return isUsableTarget(cachedTarget);
    }

    private boolean isUsableTarget(SemionMonsterEntity monster) {
        return tower.isValidAttackTarget(monster) && isInTargetSearchRange(monster);
    }

    private boolean isInTargetSearchRange(SemionMonsterEntity monster) {
        if (tower.deployedAtFinalDefense()) {
            double range = SemionTowerEntity.FINAL_DEFENSE_TARGET_RANGE;
            return tower.distanceToSqr(monster) <= range * range;
        }
        return tower.targetSearchBox().intersects(monster.getBoundingBox());
    }

    private boolean isInAttackRange(SemionMonsterEntity monster) {
        double attackRangeSqr = tower.attackRange() * tower.attackRange();
        return tower.distanceToSqr(monster) <= attackRangeSqr;
    }

    private boolean isInEncounterRange(SemionMonsterEntity monster) {
        double encounterRange = tower.attackRange() + ENCOUNTER_RANGE_BONUS;
        return tower.distanceToSqr(monster) <= encounterRange * encounterRange;
    }

    private long stableTargetOffset(SemionMonsterEntity monster) {
        UUID towerId = tower.getUUID();
        UUID monsterId = monster.getUUID();
        return towerId.getMostSignificantBits()
                ^ Long.rotateLeft(towerId.getLeastSignificantBits(), 17)
                ^ Long.rotateLeft(monsterId.getMostSignificantBits(), 31)
                ^ Long.rotateLeft(monsterId.getLeastSignificantBits(), 47);
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
