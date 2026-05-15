package kim.biryeong.semiontd.entity.goal;

import kim.biryeong.semiontd.entity.boss.SemionBossEntity;
import kim.biryeong.semiontd.entity.defender.LaneDefenseEntity;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import net.minecraft.world.entity.LivingEntity;

public final class SiegeTrueDamageGoal extends CooldownAbilityGoal {
    private final SemionMonsterEntity caster;
    private final double progressThreshold;
    private final double bonusDamage;

    public SiegeTrueDamageGoal(
            SemionMonsterEntity caster,
            double progressThreshold,
            double bonusDamage,
            int cooldownTicks,
            int retryDelayTicks
    ) {
        super(caster, cooldownTicks, retryDelayTicks);
        this.caster = caster;
        this.progressThreshold = progressThreshold;
        this.bonusDamage = Math.max(0.0, bonusDamage);
    }

    @Override
    protected boolean castAbility() {
        LivingEntity target = caster.getTarget();
        if (target == null || !target.isAlive() || !(target instanceof LaneDefenseEntity || target instanceof SemionBossEntity)) {
            return false;
        }

        Monster runtimeMonster = caster.runtimeMonster();
        boolean conditionMet = target instanceof SemionBossEntity
                || (runtimeMonster != null && runtimeMonster.laneProgress() >= progressThreshold);
        if (!conditionMet) {
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
