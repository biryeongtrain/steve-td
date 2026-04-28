package kim.biryeong.semiontd.entity.goal;

import kim.biryeong.semiontd.entity.healing.HealingTarget;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.AABB;

public final class SingleAllyHealGoal<T extends PathfinderMob & HealingTarget> extends CooldownAbilityGoal {
    private final T caster;
    private final Class<T> targetClass;
    private final double radius;
    private final double radiusSqr;
    private final double healAmount;

    public SingleAllyHealGoal(T caster, Class<T> targetClass, double radius, double healAmount, int cooldownTicks, int retryDelayTicks) {
        super(caster, cooldownTicks, retryDelayTicks);
        if (radius <= 0 || healAmount <= 0) {
            throw new IllegalArgumentException("Heal radius and amount must be positive.");
        }
        this.caster = caster;
        this.targetClass = targetClass;
        this.radius = radius;
        this.radiusSqr = radius * radius;
        this.healAmount = healAmount;
    }

    @Override
    protected boolean castAbility() {
        T target = null;
        double highestMissingHealth = 0.0;
        AABB searchBox = caster.getBoundingBox().inflate(radius);
        for (T candidate : caster.level().getEntitiesOfClass(targetClass, searchBox, this::canHeal)) {
            if (caster.distanceToSqr(candidate) > radiusSqr) {
                continue;
            }

            double missingHealth = candidate.missingHealingHealth();
            if (missingHealth > highestMissingHealth) {
                highestMissingHealth = missingHealth;
                target = candidate;
            }
        }

        return target != null && target.receiveHealing(healAmount);
    }

    private boolean canHeal(T candidate) {
        return caster.isHealingAlly(candidate) && candidate.canReceiveHealing();
    }
}
