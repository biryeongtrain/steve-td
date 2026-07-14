package kim.biryeong.semiontd.entity.goal;

import kim.biryeong.semiontd.entity.boss.SemionBossEntity;
import kim.biryeong.semiontd.entity.defender.LaneDefenseEntity;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import net.minecraft.world.entity.LivingEntity;

public final class SiegeTrueDamageGoal extends CooldownAbilityGoal {
    private final SemionMonsterEntity caster;
    private final double bonusDamage;
    private final double progressThreshold;

    public SiegeTrueDamageGoal(
            SemionMonsterEntity caster,
            double bonusDamage,
            int cooldownTicks,
            int retryDelayTicks,
            double progressThreshold
    ) {
        super(caster, cooldownTicks, retryDelayTicks);
        this.caster = caster;
        this.bonusDamage = Math.max(0.0, bonusDamage);
        this.progressThreshold = Math.max(0.0, Math.min(1.0, progressThreshold));
    }

    @Override
    protected boolean castAbility() {
        LivingEntity target = caster.getTarget();
        if (target == null || !target.isAlive() || !(target instanceof LaneDefenseEntity || target instanceof SemionBossEntity)) {
            return false;
        }
        if (caster.runtimeMonster() == null || caster.runtimeMonster().laneProgress() < progressThreshold) {
            return false;
        }

        double attackRange = caster.attackRange();
        if (caster.distanceToSqr(target) > attackRange * attackRange) {
            return false;
        }

        float previousHealth = target.getHealth();
        caster.playAnimation(SemionAnimationState.ATTACK);
        target.hurt(caster.damageSources().mobAttack(caster), (float) bonusDamage);
        if (target.getHealth() >= previousHealth - 0.01F) {
            target.setHealth(Math.max(0.0F, previousHealth - (float) bonusDamage));
        }
        return true;
    }
}
