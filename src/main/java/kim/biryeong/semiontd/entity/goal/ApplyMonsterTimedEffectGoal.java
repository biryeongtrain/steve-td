package kim.biryeong.semiontd.entity.goal;

import java.util.Comparator;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import net.minecraft.world.phys.AABB;

public final class ApplyMonsterTimedEffectGoal extends CooldownAbilityGoal {
    private final SemionMonsterEntity caster;
    private final TimedEffectType effectType;
    private final double magnitude;
    private final double radius;
    private final int durationTicks;
    private final int maxTargets;

    public ApplyMonsterTimedEffectGoal(
            SemionMonsterEntity caster,
            TimedEffectType effectType,
            double magnitude,
            double radius,
            int durationTicks,
            int cooldownTicks,
            int retryDelayTicks,
            int maxTargets
    ) {
        super(caster, cooldownTicks, retryDelayTicks);
        this.caster = caster;
        this.effectType = effectType;
        this.magnitude = magnitude;
        this.radius = Math.max(0.0, radius);
        this.durationTicks = durationTicks;
        this.maxTargets = Math.max(1, maxTargets);
    }

    public static ApplyMonsterTimedEffectGoal self(
            SemionMonsterEntity caster,
            TimedEffectType effectType,
            double magnitude,
            int durationTicks,
            int cooldownTicks
    ) {
        return new ApplyMonsterTimedEffectGoal(caster, effectType, magnitude, 0.0, durationTicks, cooldownTicks, cooldownTicks, 1);
    }

    @Override
    protected boolean castAbility() {
        if (radius <= 0.0) {
            caster.applyTimedEffect(effectType, magnitude, durationTicks);
            return true;
        }

        double radiusSqr = radius * radius;
        AABB searchBox = caster.getBoundingBox().inflate(radius);
        int applied = 0;
        for (SemionMonsterEntity target : caster.level().getEntities(
                        caster,
                        searchBox,
                        entity -> entity instanceof SemionMonsterEntity monsterEntity && monsterEntity.isAlive()
                ).stream()
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .filter(target -> caster.distanceToSqr(target) <= radiusSqr)
                .filter(this::sameSummonSide)
                .sorted(Comparator.comparingDouble(caster::distanceToSqr))
                .toList()) {
            target.applyTimedEffect(effectType, magnitude, durationTicks);
            applied++;
            if (applied >= maxTargets) {
                break;
            }
        }
        if (sameSummonSide(caster) && applied < maxTargets) {
            caster.applyTimedEffect(effectType, magnitude, durationTicks);
            applied++;
        }
        return applied > 0;
    }

    private boolean sameSummonSide(SemionMonsterEntity target) {
        Monster casterMonster = caster.runtimeMonster();
        Monster targetMonster = target.runtimeMonster();
        return casterMonster != null
                && targetMonster != null
                && casterMonster.senderTeam().isPresent()
                && casterMonster.senderTeam().equals(targetMonster.senderTeam())
                && casterMonster.targetTeam() == targetMonster.targetTeam()
                && casterMonster.targetLaneId() == targetMonster.targetLaneId();
    }
}
