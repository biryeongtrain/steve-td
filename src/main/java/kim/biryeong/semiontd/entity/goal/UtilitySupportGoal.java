package kim.biryeong.semiontd.entity.goal;

import java.util.Comparator;
import kim.biryeong.semiontd.augment.AugmentEconomyService;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.summon.UtilitySupportProfile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

/** Uses the existing ally-heal goal's bounded entity query; the area API requires a tower caster. */
public final class UtilitySupportGoal extends CooldownAbilityGoal {
    private final SemionMonsterEntity caster;
    private final UtilitySupportProfile profile;

    public UtilitySupportGoal(SemionMonsterEntity caster, UtilitySupportProfile profile) {
        super(caster, profile.cooldownTicks(), profile.retryDelayTicks());
        this.caster = caster;
        this.profile = profile;
    }

    @Override
    protected boolean castAbility() {
        Monster source = caster.runtimeMonster();
        if (source == null || !source.isAlive() || source.health() <= 0.0 || source.laneLeakRecorded()) {
            return false;
        }
        var candidates = caster.level().getEntitiesOfClass(SemionMonsterEntity.class,
                caster.getBoundingBox().inflate(profile.radius()), this::eligible);
        Comparator<SemionMonsterEntity> nearest = Comparator.<SemionMonsterEntity>comparingDouble(caster::distanceToSqr)
                .thenComparingInt(SemionMonsterEntity::getId);
        candidates.sort(switch (profile.priority()) {
            case NEAREST -> nearest;
            case MISSING_HEALTH -> Comparator.comparingDouble(SemionMonsterEntity::missingHealingHealth).reversed().thenComparing(nearest);
            case HEALTH_RATIO -> Comparator.comparingDouble((SemionMonsterEntity target) ->
                    target.runtimeMonster().health() / target.runtimeMonster().maxHealth()).thenComparing(nearest);
        });
        boolean changed = false;
        double supportMultiplier = AugmentEconomyService.supportMultiplier(source);
        long gameTime = caster.level().getGameTime();
        for (int i = 0; i < Math.min(profile.maxTargets(), candidates.size()); i++) {
            SemionMonsterEntity target = candidates.get(i);
            boolean healed = profile.healing() > 0.0 && caster.healTarget(target, profile.healing());
            double shieldBefore = target.runtimeMonster().shieldRemaining(DamageType.PHYSICAL, gameTime)
                    + target.runtimeMonster().shieldRemaining(DamageType.MAGIC, gameTime);
            boolean shielded = target.runtimeMonster().grantShield(DamageType.PHYSICAL, profile.physicalShield() * supportMultiplier,
                    profile.shieldDurationTicks(), gameTime, source);
            shielded |= target.runtimeMonster().grantShield(DamageType.MAGIC, profile.magicShield() * supportMultiplier,
                    profile.shieldDurationTicks(), gameTime, source);
            double shieldAdded = target.runtimeMonster().shieldRemaining(DamageType.PHYSICAL, gameTime)
                    + target.runtimeMonster().shieldRemaining(DamageType.MAGIC, gameTime) - shieldBefore;
            AugmentEconomyService.recordSupport(source, target.runtimeMonster(), shieldAdded);
            if (healed || shielded) {
                changed = true;
                if (caster.level() instanceof ServerLevel level) {
                    level.sendParticles(healed ? ParticleTypes.HEART : ParticleTypes.END_ROD,
                            target.getX(), target.getY() + target.getBbHeight(), target.getZ(), 3, 0.2, 0.1, 0.2, 0.0);
                }
            }
        }
        if (changed) {
            caster.playHealingAnimation();
        }
        return changed;
    }

    private boolean eligible(SemionMonsterEntity target) {
        Monster source = caster.runtimeMonster();
        Monster monster = target.runtimeMonster();
        if (source == null || monster == null || !target.isAlive() || target.isRemoved()
                || !monster.canReceiveUtilitySupportFrom(source)
                || (!profile.includesSelf() && target == caster)
                || caster.distanceToSqr(target) > profile.radius() * profile.radius()) {
            return false;
        }
        return profile.physicalShield() > 0.0 || profile.magicShield() > 0.0 || target.canReceiveHealing();
    }
}
