package kim.biryeong.semiontd.entity.goal;

import java.util.Comparator;
import kim.biryeong.semiontd.config.WaveHealingConfig;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.monster.WaveHealingState;
import kim.biryeong.semiontd.tower.succubus.SuccubusDreams;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Natural-wave filtering and lifetime caps differ from the unrestricted income Allay heal. */
public final class NaturalWaveHealGoal extends Goal {
    private final SemionMonsterEntity caster;

    public NaturalWaveHealGoal(SemionMonsterEntity caster) {
        this.caster = caster;
    }

    @Override
    public boolean canUse() {
        Monster monster = caster.runtimeMonster();
        return caster.isAlive() && !caster.isRemoved() && monster != null && monster.isAlive()
                && monster.health() > 0.0 && !monster.laneLeakRecorded()
                && monster.origin() == MonsterOrigin.NATURAL_WAVE && monster.waveHealing() != null;
    }

    @Override
    public boolean canContinueToUse() {return canUse();}

    @Override
    public boolean requiresUpdateEveryTick() {return true;}

    @Override
    public void tick() {
        if (!canUse()) {return;}
        Monster monster = caster.runtimeMonster();
        WaveHealingConfig config = monster.waveHealing();
        WaveHealingState state = monster.waveHealingState();
        if (!state.tick(config, caster.isStunned() || SuccubusDreams.isAsleep(caster))) {return;}

        var candidates = caster.level().getEntitiesOfClass(SemionMonsterEntity.class,
                caster.getBoundingBox().inflate(config.radius()), this::eligible);
        candidates.sort(Comparator.comparingDouble(SemionMonsterEntity::missingHealingHealth).reversed()
                .thenComparingDouble(caster::distanceToSqr).thenComparingInt(SemionMonsterEntity::getId));
        var targets = candidates.subList(0, Math.min(config.maxTargets(), candidates.size()));
        double injury = targets.stream().mapToDouble(target -> Math.min(config.amount(), target.missingHealingHealth())).sum();
        if (targets.isEmpty() || injury < config.minimumInjury()) {
            state.recordAttempt(config, candidates.size(), injury, 0, 0, targets.isEmpty()
                    ? WaveHealingState.Failure.NO_ELIGIBLE_TARGET : WaveHealingState.Failure.INSUFFICIENT_INJURY);
            return;
        }

        double effective = 0.0;
        for (SemionMonsterEntity target : targets) {
            double before = target.missingHealingHealth();
            if (caster.healTarget(target, config.amount())) {
                effective += Math.max(0.0, before - target.missingHealingHealth());
                showConnection(target);
            }
        }
        state.recordAttempt(config, candidates.size(), injury, config.amount() * targets.size(), effective,
                effective > 0.0 ? WaveHealingState.Failure.NONE : WaveHealingState.Failure.ZERO_EFFECTIVE_HEALING);
        if (effective > 0.0) {
            caster.playHealingAnimation();
            updateName();
        }
    }

    public void updateName() {
        Monster monster = caster.runtimeMonster();
        caster.setCustomName(Component.literal("회복 · 남은 시전 "
                + Math.max(0, monster.waveHealing().maxSuccessfulCasts() - monster.waveHealingState().successfulCasts()) + "회"));
    }

    private boolean eligible(SemionMonsterEntity target) {
        Monster source = caster.runtimeMonster();
        Monster monster = target.runtimeMonster();
        return target != caster && target.isAlive() && !target.isRemoved() && monster != null
                && monster.isAlive() && monster.health() > 0.0 && !monster.laneLeakRecorded()
                && monster.origin() == MonsterOrigin.NATURAL_WAVE && monster.waveHealing() == null
                && !monster.id().equals("warden_boss_15")
                && source.targetTeam() == monster.targetTeam() && source.targetLaneId() == monster.targetLaneId()
                && target.canReceiveHealing() && caster.distanceToSqr(target) <= source.waveHealing().radius() * source.waveHealing().radius();
    }

    private void showConnection(SemionMonsterEntity target) {
        if (!(caster.level() instanceof ServerLevel level)) {return;}
        Vec3 start = caster.position().add(0, caster.getBbHeight() * 0.5, 0);
        Vec3 end = target.position().add(0, target.getBbHeight() * 0.5, 0);
        for (int i = 1; i <= 4; i++) {
            Vec3 point = start.lerp(end, i / 4.0);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.HEART, end.x, end.y, end.z, 2, 0.1, 0.1, 0.1, 0);
    }
}
