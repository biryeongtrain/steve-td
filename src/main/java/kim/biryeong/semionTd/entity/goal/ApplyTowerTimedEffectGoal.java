package kim.biryeong.semiontd.entity.goal;

import java.util.Comparator;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.test.entity.SemionTestTowerEntity;
import net.minecraft.world.phys.AABB;

public final class ApplyTowerTimedEffectGoal extends CooldownAbilityGoal {
    private final SemionMonsterEntity caster;
    private final TimedEffectType effectType;
    private final double magnitude;
    private final double radius;
    private final int durationTicks;
    private final int maxTargets;

    public ApplyTowerTimedEffectGoal(
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

    @Override
    protected boolean castAbility() {
        Monster runtimeMonster = caster.runtimeMonster();
        if (runtimeMonster == null) {
            return false;
        }

        AABB searchBox = caster.getBoundingBox().inflate(radius);
        double radiusSqr = radius * radius;
        int applied = 0;
        for (SemionTestTowerEntity tower : caster.level().getEntities(
                        caster,
                        searchBox,
                        entity -> entity instanceof SemionTestTowerEntity towerEntity
                                && towerEntity.isAlive()
                                && towerEntity.teamId() == runtimeMonster.targetTeam()
                                && towerEntity.defendsLane(runtimeMonster.targetLaneId())
                ).stream()
                .filter(SemionTestTowerEntity.class::isInstance)
                .map(SemionTestTowerEntity.class::cast)
                .filter(tower -> caster.distanceToSqr(tower) <= radiusSqr)
                .sorted(Comparator.comparingDouble(caster::distanceToSqr))
                .toList()) {
            tower.applyTimedEffect(effectType, magnitude, durationTicks);
            applied++;
            if (applied >= maxTargets) {
                break;
            }
        }
        return applied > 0;
    }
}
