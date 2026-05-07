package kim.biryeong.semiontd.entity.monster;

import de.tomalbrc.bil.api.AnimatedEntity;
import de.tomalbrc.bil.api.AnimatedEntityHolder;
import de.tomalbrc.bil.core.holder.entity.living.LivingEntityHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import java.util.ArrayList;
import java.util.List;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.effect.TimedEffectSet;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.defender.LaneDefenseEntity;
import kim.biryeong.semiontd.entity.healing.HealingTarget;
import kim.biryeong.semiontd.entity.model.SemionBilModelCache;
import kim.biryeong.semiontd.entity.monster.goal.AcquireLaneDefenseTargetGoal;
import kim.biryeong.semiontd.entity.monster.goal.LaneFollowGoal;
import kim.biryeong.semiontd.entity.monster.goal.MonsterAttackTargetGoal;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.summon.SummonRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.packettweaker.PacketContext;

public class SemionMonsterEntity extends PathfinderMob implements AnimatedEntity, HealingTarget {
    private static final double DEFAULT_MELEE_RANGE = 2.5;
    private static final double DEFAULT_RANGED_RANGE = 8.0;
    private static final double DEFAULT_FOLLOW_RANGE = 12.0;
    private static final double DEFENSE_SEARCH_HORIZONTAL_PADDING = 8.0;
    private static final double DEFENSE_SEARCH_VERTICAL_PADDING = 3.0;
    private static final int DEFAULT_ATTACK_INTERVAL_TICKS = 20;

    private EntityType<?> polymerEntityType = EntityType.ZOMBIE;
    private Monster runtimeMonster;
    private LaneRegionLayout laneLayout;
    private String blockbenchModelId;
    private EntityDimensions runtimeDimensions = MonsterDimensions.DEFAULT.toEntityDimensions();
    private SemionAnimationState animationState = SemionAnimationState.IDLE;
    private final List<Goal> summonAbilityGoals = new ArrayList<>();
    private final TimedEffectSet timedEffects = new TimedEffectSet();
    private LivingEntityHolder<SemionMonsterEntity> holder;
    private EntityAttachment holderAttachment;

    public SemionMonsterEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        setSilent(true);
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MonsterAttackTargetGoal(this, 1.1));
        goalSelector.addGoal(2, new LaneFollowGoal(this, 1.0));
        targetSelector.addGoal(0, new AcquireLaneDefenseTargetGoal(this));
    }

    @Override
    public EntityType<?> getPolymerEntityType(PacketContext context) {
        if (blockbenchModelId != null) {
            return AnimatedEntity.super.getPolymerEntityType(context);
        }
        return polymerEntityType;
    }

    @Override
    public AnimatedEntityHolder getHolder() {
        return holder;
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        if (runtimeMonster != null) {
            runtimeMonster.syncHealth(0.0);
        }
    }

    public void configureFrom(Monster monster, LaneRegionLayout laneLayout) {
        this.runtimeMonster = monster;
        this.laneLayout = laneLayout;
        this.blockbenchModelId = monster.blockbenchModelId().orElse(null);
        this.runtimeDimensions = monster.dimensions().toEntityDimensions();
        refreshDimensions();
        setCustomName(Component.literal(monster.id()));
        setCustomNameVisible(true);
        setPolymerEntityType(monster.entityTypeId());
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(monster.maxHealth());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(monster.attackDamage());
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(followRangeFor(monster));
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.28);
        setHealth((float) monster.health());
        installBilModel(blockbenchModelId);
        installSummonAbilityGoals();
        playAnimation(SemionAnimationState.IDLE);
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return runtimeDimensions.scale(getAgeScale());
    }

    @Override
    public void aiStep() {
        super.aiStep();
        timedEffects.tick();

        if (getTarget() instanceof LaneDefenseEntity defenseEntity && runtimeMonster != null) {
            if (!getTarget().isAlive() || !defenseEntity.defendsLane(runtimeMonster.targetLaneId())) {
                setTarget(null);
            }
        } else if (getTarget() != null && !getTarget().isAlive()) {
            setTarget(null);
        }
    }

    public boolean hasLanePath() {
        return laneLayout != null;
    }

    public List<Vec3> pathPoints() {
        if (laneLayout == null) {
            return List.of();
        }

        List<Vec3> points = new ArrayList<>(laneLayout.waypoints().size() + 1);
        points.addAll(laneLayout.waypoints());
        points.add(laneLayout.bossPosition());
        return points;
    }

    public AABB defenseSearchBox() {
        if (laneLayout == null) {
            return getBoundingBox().inflate(DEFAULT_FOLLOW_RANGE);
        }
        return laneLayout.defenseSearchBox(position(), DEFENSE_SEARCH_HORIZONTAL_PADDING, DEFENSE_SEARCH_VERTICAL_PADDING);
    }

    public Monster runtimeMonster() {
        return runtimeMonster;
    }

    @Override
    public boolean isHealingAlly(HealingTarget other) {
        if (!(other instanceof SemionMonsterEntity monsterEntity) || monsterEntity == this
                || runtimeMonster == null || monsterEntity.runtimeMonster == null) {
            return false;
        }
        return runtimeMonster.senderTeam().isPresent()
                && runtimeMonster.senderTeam().equals(monsterEntity.runtimeMonster.senderTeam())
                && runtimeMonster.targetTeam() == monsterEntity.runtimeMonster.targetTeam()
                && runtimeMonster.targetLaneId() == monsterEntity.runtimeMonster.targetLaneId();
    }

    @Override
    public boolean canReceiveHealing() {
        return runtimeMonster != null && runtimeMonster.isAlive() && runtimeMonster.health() < runtimeMonster.maxHealth();
    }

    @Override
    public double missingHealingHealth() {
        if (runtimeMonster == null) {
            return 0.0;
        }
        return Math.max(0.0, runtimeMonster.maxHealth() - runtimeMonster.health());
    }

    @Override
    public boolean receiveHealing(double amount) {
        if (runtimeMonster == null || amount <= 0 || !runtimeMonster.isAlive()) {
            return false;
        }
        double before = runtimeMonster.health();
        runtimeMonster.heal(amount);
        if (runtimeMonster.health() <= before) {
            return false;
        }
        setHealth((float) runtimeMonster.health());
        return true;
    }

    @Override
    public void playHealingAnimation() {
        playAnimation(SemionAnimationState.HEAL);
    }

    public String blockbenchModelId() {
        return blockbenchModelId;
    }

    public boolean hasBilModelHolder() {
        return holder != null;
    }

    public SemionAnimationState animationState() {
        return animationState;
    }

    public void playAnimation(SemionAnimationState animationState) {
        if (animationState == null) {
            return;
        }
        if (holder != null && (this.animationState != animationState || animationState == SemionAnimationState.ATTACK || animationState == SemionAnimationState.HEAL)) {
            for (SemionAnimationState state : SemionAnimationState.values()) {
                if (state != animationState) {
                    holder.getAnimator().pauseAnimation(state.animationId());
                }
            }
            holder.getAnimator().playAnimation(animationState.animationId(), animationState == SemionAnimationState.ATTACK || animationState == SemionAnimationState.HEAL ? 10 : 1, true);
        }
        this.animationState = animationState;
    }

    public double attackRange() {
        if (runtimeMonster == null) {
            return DEFAULT_MELEE_RANGE;
        }
        return runtimeMonster.attackKind() == AttackKind.RANGED ? DEFAULT_RANGED_RANGE : DEFAULT_MELEE_RANGE;
    }

    public int attackIntervalTicks() {
        return DEFAULT_ATTACK_INTERVAL_TICKS;
    }

    public void applyTimedEffect(TimedEffectType type, double magnitude, int durationTicks) {
        timedEffects.apply(type, magnitude, durationTicks);
    }

    public double activeTimedEffectMagnitude(TimedEffectType type) {
        return timedEffects.magnitude(type);
    }

    public int activeTimedEffectTicks(TimedEffectType type) {
        return timedEffects.remainingTicks(type);
    }

    public double movementSpeedMultiplier() {
        return 1.0 + timedEffects.magnitude(TimedEffectType.MONSTER_MOVE_SPEED_BONUS);
    }

    public double towerDamageTaken(double baseDamage) {
        double damageReduction = timedEffects.magnitude(TimedEffectType.MONSTER_DAMAGE_REDUCTION);
        return Math.max(0.0, baseDamage) * (1.0 - damageReduction);
    }

    private double followRangeFor(Monster monster) {
        double baseAttackRange = monster.attackKind() == AttackKind.RANGED ? DEFAULT_RANGED_RANGE : DEFAULT_MELEE_RANGE;
        return Math.max(DEFAULT_FOLLOW_RANGE, baseAttackRange + 2.0);
    }

    private void installSummonAbilityGoals() {
        for (Goal goal : summonAbilityGoals) {
            goalSelector.removeGoal(goal);
        }
        summonAbilityGoals.clear();

        SummonRegistry.find(runtimeMonster.id()).ifPresent(type -> {
            for (Goal goal : type.createAbilityGoals(this)) {
                summonAbilityGoals.add(goal);
                goalSelector.addGoal(3, goal);
            }
        });
    }

    private void installBilModel(String modelId) {
        if (holderAttachment != null) {
            holderAttachment.destroy();
            holderAttachment = null;
        }
        holder = null;

        SemionBilModelCache.load(modelId).ifPresent(model -> {
            holder = new LivingEntityHolder<>(this, model);
            holderAttachment = EntityAttachment.ofTicking(holder, this);
        });
    }

    private void setPolymerEntityType(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isBlank()) {
            polymerEntityType = EntityType.ZOMBIE;
            return;
        }

        ResourceLocation id = ResourceLocation.tryParse(entityTypeId);
        if (id == null) {
            polymerEntityType = EntityType.ZOMBIE;
            return;
        }

        polymerEntityType = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(EntityType.ZOMBIE);
    }
}
