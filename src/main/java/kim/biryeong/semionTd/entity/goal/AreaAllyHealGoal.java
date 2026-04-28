package kim.biryeong.semiontd.entity.goal;

import kim.biryeong.semiontd.entity.healing.HealingTarget;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.AABB;

public final class AreaAllyHealGoal<T extends PathfinderMob & HealingTarget> extends CooldownAbilityGoal {
    private final T caster;
    private final Class<T> targetClass;
    private final double radius;
    private final double radiusSqr;
    private final double healAmount;
    private final int maxTargets;

    public AreaAllyHealGoal(T caster, Class<T> targetClass, double radius, double healAmount, int maxTargets, int cooldownTicks, int retryDelayTicks) {
        super(caster, cooldownTicks, retryDelayTicks);
        if (radius <= 0 || healAmount <= 0 || maxTargets <= 0) {
            throw new IllegalArgumentException("Heal radius, amount, and max targets must be positive.");
        }
        this.caster = caster;
        this.targetClass = targetClass;
        this.radius = radius;
        this.radiusSqr = radius * radius;
        this.healAmount = healAmount;
        this.maxTargets = maxTargets;
    }

    @Override
    protected boolean castAbility() {
        int healedTargets = 0;
        AABB searchBox = caster.getBoundingBox().inflate(radius);
        for (T candidate : caster.level().getEntitiesOfClass(targetClass, searchBox, this::canHeal)) {
            if (caster.distanceToSqr(candidate) > radiusSqr || !candidate.receiveHealing(healAmount)) {
                continue;
            }

            healedTargets++;
            if (healedTargets >= maxTargets) {
                break;
            }
        }
        return healedTargets > 0;
    }

    private boolean canHeal(T candidate) {
        return caster.isHealingAlly(candidate) && candidate.canReceiveHealing();
    }
}
