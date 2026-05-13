package kim.biryeong.semiontd.entity.boss.goal;

import java.util.EnumSet;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.boss.SemionBossEntity;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public final class BossAttackLaneMonsterGoal extends Goal {
    private static final double ATTACK_RANGE = 3.5;
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
                                && (monster.distanceToSqr(boss) <= ATTACK_RANGE * ATTACK_RANGE
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
                && distanceSqr > ATTACK_RANGE * ATTACK_RANGE) {
            pullTowardBoss(target);
            return;
        }

        if (cooldownTicks <= 0 && distanceSqr <= ATTACK_RANGE * ATTACK_RANGE) {
            if (runtimeMonster != null) {
                runtimeMonster.recordBossHit();
            }
            target.hurt(boss.damageSources().mobAttack(boss), (float) boss.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
            if (runtimeMonster != null) {
                runtimeMonster.syncHealth(target.getHealth());
            }
            cooldownTicks = 20;
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
}
