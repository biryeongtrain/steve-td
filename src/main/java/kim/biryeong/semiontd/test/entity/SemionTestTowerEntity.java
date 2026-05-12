package kim.biryeong.semiontd.test.entity;

import de.tomalbrc.bil.api.AnimatedEntity;
import de.tomalbrc.bil.api.AnimatedEntityHolder;
import de.tomalbrc.bil.core.holder.entity.living.LivingEntityHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import java.util.UUID;
import kim.biryeong.semiontd.effect.TimedEffectSet;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.defender.LaneDefenseEntity;
import kim.biryeong.semiontd.entity.healing.HealingTarget;
import kim.biryeong.semiontd.entity.model.SemionBilModelCache;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.test.entity.goal.TestTowerAttackMonsterGoal;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.ProductionTowerBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import xyz.nucleoid.packettweaker.PacketContext;

public final class SemionTestTowerEntity extends PathfinderMob implements AnimatedEntity, LaneDefenseEntity, HealingTarget {
    private static final double DEFAULT_MOVE_SPEED = 0.23;
    private static final double DEFAULT_TARGET_ACQUIRE_RANGE = 24.0;
    private static final double TARGET_SEARCH_HORIZONTAL_PADDING = 8.0;
    private static final double TARGET_SEARCH_VERTICAL_PADDING = 3.0;

    private TestTower runtimeTower;
    private TeamId teamId;
    private int laneId;
    private UUID ownerPlayer;
    private double attackRange;
    private double attackDamage;
    private int attackIntervalTicks;
    private int aggroPriority;
    private boolean finalDefense;
    private double targetAcquireRange;
    private double moveSpeed;
    private LaneRegionLayout laneLayout;
    private String blockbenchModelId;
    private SemionAnimationState animationState = SemionAnimationState.IDLE;
    private EntityType<?> polymerEntityType = EntityType.ARMOR_STAND;
    private final TimedEffectSet timedEffects = new TimedEffectSet();
    private LivingEntityHolder<SemionTestTowerEntity> holder;
    private EntityAttachment holderAttachment;

    public SemionTestTowerEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        setSilent(true);
        setPersistenceRequired();
        setNoGravity(true);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TestTowerAttackMonsterGoal(this));
    }

    public void configure(TestTower tower, LaneRegionLayout laneLayout) {
        runtimeTower = tower;
        this.laneLayout = laneLayout;
        laneId = tower.laneId();
        teamId = tower.teamId();
        ownerPlayer = tower.ownerPlayer();
        attackRange = tower.type().range();
        attackDamage = tower.type().damage();
        attackIntervalTicks = tower.type().attackIntervalTicks();
        aggroPriority = tower.aggroPriority();
        finalDefense = tower.deployedAtFinalDefense();
        blockbenchModelId = tower.type().blockbenchModel().orElse(null);
        setPolymerEntityType(tower.type().entityTypeId());
        targetAcquireRange = Math.max(attackRange + 4.0, DEFAULT_TARGET_ACQUIRE_RANGE);
        moveSpeed = DEFAULT_MOVE_SPEED;
        setCustomName(Component.literal(tower.type().displayName()));
        setCustomNameVisible(true);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(tower.maxHealth());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attackDamage);
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(targetAcquireRange);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(moveSpeed);
        setHealth((float) tower.health());
        installBilModel(blockbenchModelId);
        playAnimation(SemionAnimationState.IDLE);
    }

    public int laneId() {
        return laneId;
    }

    public UUID ownerPlayer() {
        return ownerPlayer;
    }

    public TeamId teamId() {
        return teamId;
    }

    @Override
    public boolean defendsLane(int targetLaneId) {
        return finalDefense || laneId == targetLaneId;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        timedEffects.tick();
    }

    @Override
    public int aggroPriority() {
        return aggroPriority;
    }

    public double attackRange() {
        return attackRange * (1.0 - timedEffects.magnitude(TimedEffectType.TOWER_RANGE_REDUCTION));
    }

    public double targetAcquireRange() {
        return targetAcquireRange;
    }

    public AABB targetSearchBox() {
        if (laneLayout == null) {
            return getBoundingBox().inflate(targetAcquireRange);
        }
        return laneLayout.defenseSearchBox(position(), TARGET_SEARCH_HORIZONTAL_PADDING, TARGET_SEARCH_VERTICAL_PADDING);
    }

    public double moveSpeedAmount() {
        return moveSpeed;
    }

    public double attackDamageAmount() {
        if (runtimeTower instanceof ProductionTower productionTower) {
            return attackDamage * productionTower.damageMultiplier();
        }
        return attackDamage;
    }

    public int attackIntervalTicks() {
        if (runtimeTower instanceof ProductionTower productionTower) {
            return productionTower.adjustedAttackInterval(attackIntervalTicks);
        }
        double attackSpeedMultiplier = 1.0 - timedEffects.magnitude(TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION);
        return Math.max(1, (int) Math.ceil(attackIntervalTicks / Math.max(0.01, attackSpeedMultiplier)));
    }

    public ProductionTowerBehavior productionBehavior() {
        return runtimeTower instanceof ProductionTower productionTower ? productionTower.behavior() : null;
    }

    public int productionMechanicStacks() {
        return runtimeTower instanceof ProductionTower productionTower ? productionTower.mechanicStacks() : 0;
    }

    public void recordProductionAttack(boolean killedPrimaryTarget) {
        if (runtimeTower instanceof ProductionTower productionTower) {
            productionTower.recordAttack(killedPrimaryTarget);
        }
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

    public String blockbenchModelId() {
        return blockbenchModelId;
    }

    public boolean hasBilModelHolder() {
        return holder != null;
    }

    public SemionAnimationState animationState() {
        return animationState;
    }

    @Override
    public void playHealingAnimation() {
        playAnimation(SemionAnimationState.HEAL);
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

    public void syncTowerState(TestTower tower) {
        runtimeTower = tower;
        aggroPriority = tower.aggroPriority();
        finalDefense = tower.deployedAtFinalDefense();
        if (Math.abs(getHealth() - tower.health()) > 0.01F) {
            setHealth((float) tower.health());
        }
    }

    @Override
    public boolean isHealingAlly(HealingTarget other) {
        if (!(other instanceof SemionTestTowerEntity towerEntity) || towerEntity == this) {
            return false;
        }
        return teamId != null
                && teamId == towerEntity.teamId
                && (finalDefense || towerEntity.finalDefense || laneId == towerEntity.laneId);
    }

    @Override
    public boolean canReceiveHealing() {
        return runtimeTower != null && runtimeTower.health() > 0.0 && runtimeTower.health() < runtimeTower.maxHealth();
    }

    @Override
    public double missingHealingHealth() {
        if (runtimeTower == null) {
            return 0.0;
        }
        return Math.max(0.0, runtimeTower.maxHealth() - runtimeTower.health());
    }

    @Override
    public boolean receiveHealing(double amount) {
        if (runtimeTower == null || amount <= 0 || runtimeTower.health() <= 0.0) {
            return false;
        }
        double before = runtimeTower.health();
        runtimeTower.syncHealth(before + amount);
        if (runtimeTower.health() <= before) {
            return false;
        }
        setHealth((float) runtimeTower.health());
        return true;
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
    public boolean isPushable() {
        return false;
    }

    private void setPolymerEntityType(String entityTypeId) {
        ResourceLocation id = ResourceLocation.tryParse(entityTypeId);
        if (id == null) {
            polymerEntityType = EntityType.ARMOR_STAND;
            return;
        }

        polymerEntityType = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(EntityType.ARMOR_STAND);
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
}
