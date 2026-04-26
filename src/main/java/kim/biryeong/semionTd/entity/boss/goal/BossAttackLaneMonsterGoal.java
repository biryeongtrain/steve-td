package kim.biryeong.semionTd.entity.boss.goal;

import java.util.EnumSet;
import kim.biryeong.semionTd.entity.boss.SemionBossEntity;
import kim.biryeong.semionTd.entity.monster.Monster;
import kim.biryeong.semionTd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public final class BossAttackLaneMonsterGoal extends Goal {
    private static final double ATTACK_RANGE = 3.5;

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
                        boss.getBoundingBox().inflate(ATTACK_RANGE),
                        entity -> entity instanceof SemionMonsterEntity && entity.isAlive()
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
        if (cooldownTicks <= 0 && boss.distanceToSqr(target) <= ATTACK_RANGE * ATTACK_RANGE) {
            Monster runtimeMonster = target.runtimeMonster();
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
}