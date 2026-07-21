package kim.biryeong.semiontd.entity.boss.goal;

import java.util.EnumSet;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.boss.SemionBossEntity;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BossAttackLaneMonsterGoal extends Goal {
    private static final double SPLASH_RADIUS = 3.0;
    private static final double RANGED_PULL_RANGE = 10.0;
    private static final double RANGED_PULL_STEP = 0.45;

    private final SemionBossEntity boss;
    private int cooldownTicks;

    public BossAttackLaneMonsterGoal(SemionBossEntity boss) {
        this.boss = boss;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return boss.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return boss.isAlive();
    }

    @Override
    public void tick() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }

        SemionMonsterEntity target = boss.level().getEntities(
                        boss,
                        boss.getBoundingBox().inflate(RANGED_PULL_RANGE),
                        entity -> entity instanceof SemionMonsterEntity monster
                                && monster.isAlive()
                                && monster.runtimeMonster() != null
                                && monster.runtimeMonster().targetTeam() == boss.teamId()
                                && (monster.distanceToSqr(boss) <= attackRangeSqr()
                                || monster.runtimeMonster().attackKind() == AttackKind.RANGED)
                ).stream()
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .findFirst()
                .orElse(null);
        if (target == null) {
            return;
        }

        boss.setTarget(target);
        boss.lookAt(target, 30.0F, 30.0F);
        double distanceSqr = boss.distanceToSqr(target);
        Monster runtimeMonster = target.runtimeMonster();
        if (runtimeMonster != null
                && runtimeMonster.attackKind() == AttackKind.RANGED
                && distanceSqr > attackRangeSqr()) {
            pullTowardBoss(target);
            return;
        }

        if (cooldownTicks <= 0 && distanceSqr <= attackRangeSqr()) {
            attackTargetAndSplash(target);
            cooldownTicks = boss.attackIntervalTicks();
        }
    }

    private void attackTargetAndSplash(SemionMonsterEntity primaryTarget) {
        damage(primaryTarget);

        AABB splashBox = primaryTarget.getBoundingBox().inflate(SPLASH_RADIUS);
        double splashRadiusSqr = SPLASH_RADIUS * SPLASH_RADIUS;
        boss.level().getEntities(
                        boss,
                        splashBox,
                        entity -> entity instanceof SemionMonsterEntity monster
                                && monster != primaryTarget
                                && monster.isAlive()
                                && monster.runtimeMonster() != null
                                && monster.runtimeMonster().targetTeam() == boss.teamId()
                                && monster.distanceToSqr(primaryTarget) <= splashRadiusSqr
                ).stream()
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .forEach(this::damage);
    }

    private void damage(SemionMonsterEntity target) {
        Monster runtimeMonster = target.runtimeMonster();
        double damageAmount = boss.attackDamageAgainst(runtimeMonster);
        if (damageAmount <= 0.0) {
            return;
        }
        double previousHealth = runtimeMonster == null ? 0.0 : runtimeMonster.health();
        target.applyRuntimeDamage(
                boss.damageSources().mobAttack(boss),
                damageAmount,
                DamageType.PHYSICAL
        );
        if (runtimeMonster != null && runtimeMonster.health() < previousHealth) {
            runtimeMonster.recordBossHit();
        }
    }

    private void pullTowardBoss(SemionMonsterEntity target) {
        Vec3 delta = boss.position().subtract(target.position());
        double horizontalDistance = Math.hypot(delta.x, delta.z);
        if (horizontalDistance <= 0.001) {
            return;
        }

        double step = Math.min(RANGED_PULL_STEP, horizontalDistance);
        double x = target.getX() + delta.x / horizontalDistance * step;
        double z = target.getZ() + delta.z / horizontalDistance * step;
        target.teleportTo(x, target.getY(), z);
        target.getNavigation().stop();
    }

    private static double attackRangeSqr() {
        return SemionBossEntity.FINAL_DEFENSE_ENGAGEMENT_RANGE
                * SemionBossEntity.FINAL_DEFENSE_ENGAGEMENT_RANGE;
    }
}
