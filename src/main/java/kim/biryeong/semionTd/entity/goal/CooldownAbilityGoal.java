package kim.biryeong.semiontd.entity.goal;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

public abstract class CooldownAbilityGoal extends Goal {
    private final PathfinderMob caster;
    private final int cooldownTicks;
    private final int retryDelayTicks;
    private int remainingCooldownTicks;

    protected CooldownAbilityGoal(PathfinderMob caster, int cooldownTicks, int retryDelayTicks) {
        if (cooldownTicks <= 0 || retryDelayTicks <= 0) {
            throw new IllegalArgumentException("Ability cooldown and retry delay must be positive.");
        }
        this.caster = caster;
        this.cooldownTicks = cooldownTicks;
        this.retryDelayTicks = retryDelayTicks;
    }

    @Override
    public boolean canUse() {
        return caster.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--;
            return;
        }

        remainingCooldownTicks = castAbility() ? cooldownTicks : retryDelayTicks;
    }

    protected abstract boolean castAbility();
}
