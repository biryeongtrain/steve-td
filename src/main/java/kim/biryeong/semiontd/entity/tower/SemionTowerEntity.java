package kim.biryeong.semiontd.entity.tower;

import de.tomalbrc.bil.api.AnimatedEntity;
import de.tomalbrc.bil.api.AnimatedEntityHolder;
import de.tomalbrc.bil.core.holder.entity.living.LivingEntityHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.effect.TimedEffectSet;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.defender.LaneDefenseEntity;
import kim.biryeong.semiontd.entity.healing.HealingTarget;
import kim.biryeong.semiontd.entity.model.SemionBilModelCache;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisualApplierRegistry;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.packettweaker.PacketContext;

public final class SemionTowerEntity extends PathfinderMob implements AnimatedEntity, LaneDefenseEntity, HealingTarget {
    private static final double DEFAULT_MOVE_SPEED = 0.23;
    private static final double DEFAULT_TARGET_ACQUIRE_RANGE = 24.0;
    private static final double TARGET_SEARCH_HORIZONTAL_PADDING = 8.0;
    private static final double TARGET_SEARCH_VERTICAL_PADDING = 3.0;
    private static final double FINAL_DEFENSE_RETURN_SPEED_MULTIPLIER = 1.25;

    private Tower runtimeTower;
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
    private Vec3 finalDefenseAnchorPosition;
    private EntityVisual visual = EntityVisual.vanilla(EntityVisual.DEFAULT_TOWER_ENTITY_TYPE);
    private String blockbenchModelId;
    private SemionAnimationState animationState = SemionAnimationState.IDLE;
    private EntityType<?> polymerEntityType = EntityType.ARMOR_STAND;
    private final TimedEffectSet timedEffects = new TimedEffectSet();
    private LivingEntityHolder<SemionTowerEntity> holder;
    private EntityAttachment holderAttachment;
    private SemionTowerEntity attackTargetSource;
    private SemionMonsterEntity currentAttackTarget;

    public SemionTowerEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        setSilent(true);
        setPersistenceRequired();
        setNoGravity(false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TowerAttackMonsterGoal(this));
    }

    public void configure(Tower tower, LaneRegionLayout laneLayout) {
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
        finalDefenseAnchorPosition = finalDefense ? towerAnchorPosition(tower.position()) : null;
        setNoGravity(false);
        visual = tower.type().visual();
        blockbenchModelId = visual.blockbenchModel().orElse(null);
        setPolymerEntityType(visual.entityTypeId());
        applyVisualScale(visual);
        targetAcquireRange = Math.max(attackRange + 4.0, DEFAULT_TARGET_ACQUIRE_RANGE);
        moveSpeed = DEFAULT_MOVE_SPEED;
        setCustomName(Component.literal(tower.type().displayName()));
        setCustomNameVisible(true);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(tower.currentMaxHealth());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attackDamage);
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(targetAcquireRange);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(moveSpeed);
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        setHealth((float) tower.health());
        installBilModel(blockbenchModelId);
        playAnimation(SemionAnimationState.IDLE);
    }


    public int laneId() {
        return laneId;
    }

    public Tower runtimeTower() {
        return runtimeTower;
    }

    public void useAttackTargetFrom(SemionTowerEntity source) {
        attackTargetSource = source == this ? null : source;
    }

    public boolean usesSharedAttackTarget() {
        return attackTargetSource != null;
    }

    public SemionMonsterEntity sharedAttackTarget() {
        if (attackTargetSource == null || attackTargetSource.isRemoved() || !attackTargetSource.isAlive()) {
            return null;
        }
        return attackTargetSource.currentAttackTarget();
    }

    public SemionMonsterEntity currentAttackTarget() {
        return isValidAttackTarget(currentAttackTarget) ? currentAttackTarget : null;
    }

    public void recordCurrentAttackTarget(SemionMonsterEntity target) {
        currentAttackTarget = isValidAttackTarget(target) ? target : null;
    }

    public SemionMonsterEntity selectAttackTarget(List<SemionMonsterEntity> candidates) {
        if (runtimeTower == null) {
            return null;
        }
        return runtimeTower.selectAttackTarget(this, candidates).orElse(null);
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

    public boolean isValidAttackTarget(SemionMonsterEntity target) {
        return target != null
                && target.isAlive()
                && !target.isRemoved()
                && target.runtimeMonster() != null
                && defendsLane(target.runtimeMonster().targetLaneId());
    }

    @Override
    public void aiStep() {
        super.aiStep();
        invulnerableTime = 0;
        timedEffects.tick();
        returnToFinalDefenseAreaIfNeeded();
    }

    @Override
    public int aggroPriority() {
        return aggroPriority;
    }

    public double attackRange() {
        double multiplier = 1.0
                + timedEffects.magnitude(TimedEffectType.TOWER_RANGE_BONUS)
                - timedEffects.magnitude(TimedEffectType.TOWER_RANGE_REDUCTION);
        return attackRange * Math.max(0.01, multiplier);
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

    public double attackDamageAmount(SemionMonsterEntity target) {
        double damageAmount = attackDamage * (1.0 + timedEffects.magnitude(TimedEffectType.TOWER_DAMAGE_BONUS));
        if (runtimeTower != null) {
            damageAmount = runtimeTower.modifyAttackDamage(this, target, damageAmount);
        }
        return Math.max(0.0, damageAmount);
    }

    public int attackIntervalTicks() {
        int adjustedInterval = runtimeTower == null ? attackIntervalTicks : runtimeTower.adjustAttackInterval(attackIntervalTicks);
        double attackSpeedMultiplier = 1.0
                + timedEffects.magnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS)
                - timedEffects.magnitude(TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION);
        return Math.max(1, (int) Math.ceil(adjustedInterval / Math.max(0.01, attackSpeedMultiplier)));
    }

    public boolean playsRangedAttackSound() {
        return attackDamage > 0.0 && attackRange() > 3.0;
    }

    public double chaseSpeedModifier() {
        return moveSpeed;
    }

    public boolean deployedAtFinalDefense() {
        return finalDefense;
    }

    public boolean needsFinalDefenseReturn() {
        return finalDefense
                && laneLayout != null
                && !laneLayout.isInsideFinalDefenseTowerArea(position());
    }

    public void returnToFinalDefenseAreaIfNeeded() {
        if (!needsFinalDefenseReturn()) {
            return;
        }

        Vec3 returnPosition = finalDefenseAnchorPosition;
        if (returnPosition == null) {
            returnPosition = laneLayout.clampToFinalDefenseTowerArea(position());
        }
        moveToward(returnPosition, moveSpeed * FINAL_DEFENSE_RETURN_SPEED_MULTIPLIER);
    }

    public void moveTowardTarget(Vec3 targetPosition, double speedModifier) {
        Vec3 moveTarget = targetPosition;
        if (finalDefense && laneLayout != null) {
            moveTarget = laneLayout.clampToFinalDefenseTowerArea(targetPosition);
        }
        moveToward(moveTarget, speedModifier);
    }

    public boolean damageTarget(SemionMonsterEntity target, double baseDamage) {
        if (runtimeTower == null || target == null) {
            return false;
        }
        return runtimeTower.damageTarget(this, target, baseDamage);
    }

    public void recordAttack(SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
        if (runtimeTower == null) {
            return;
        }

        runtimeTower.onAttack(this, target, damageAmount, killedTarget);
        if (killedTarget) {
            runtimeTower.onKill(this, target, damageAmount);
        }
    }

    public void applyTimedEffect(TimedEffectType type, double magnitude, int durationTicks) {
        double previousMagnitude = type == null ? 0.0 : activeTimedEffectMagnitude(type);
        int previousTicks = type == null ? 0 : activeTimedEffectTicks(type);
        timedEffects.apply(type, magnitude, durationTicks);
        double currentMagnitude = type == null ? 0.0 : activeTimedEffectMagnitude(type);
        int currentTicks = type == null ? 0 : activeTimedEffectTicks(type);
        if (runtimeTower != null
                && type != null
                && currentTicks > 0
                && (Double.compare(previousMagnitude, currentMagnitude) != 0 || previousTicks != currentTicks)) {
            runtimeTower.onTimedEffectApplied(this, type, currentMagnitude, currentTicks);
        }
    }

    public boolean applyTimedEffect(TimedEffectType type, ResourceLocation sourceId, double magnitude, int durationTicks) {
        double previousMagnitude = type == null ? 0.0 : activeTimedEffectMagnitude(type);
        int previousTicks = type == null ? 0 : activeTimedEffectTicks(type);
        boolean applied = timedEffects.apply(type, sourceId, magnitude, durationTicks);
        double currentMagnitude = type == null ? 0.0 : activeTimedEffectMagnitude(type);
        int currentTicks = type == null ? 0 : activeTimedEffectTicks(type);
        if (applied
                && runtimeTower != null
                && type != null
                && currentTicks > 0
                && (Double.compare(previousMagnitude, currentMagnitude) != 0 || previousTicks != currentTicks)) {
            runtimeTower.onTimedEffectApplied(this, type, currentMagnitude, currentTicks);
        }
        return applied;
    }

    public double activeTimedEffectMagnitude(TimedEffectType type) {
        return timedEffects.magnitude(type);
    }

    public int activeTimedEffectTicks(TimedEffectType type) {
        return timedEffects.remainingTicks(type);
    }

    public boolean hasTimedEffectSource(TimedEffectType type, ResourceLocation sourceId) {
        return timedEffects.hasSource(type, sourceId);
    }

    public <T> void setTowerData(TowerDataKey<T> key, T value) {
        if (runtimeTower != null) {
            runtimeTower.setData(key, value);
        }
    }

    public boolean hasTowerData(TowerDataKey<?> key) {
        return runtimeTower != null && runtimeTower.hasData(key);
    }

    public <T> Optional<T> getTowerData(TowerDataKey<T> key) {
        return runtimeTower == null ? Optional.empty() : runtimeTower.getData(key);
    }

    public <T> T getTowerDataOrDefault(TowerDataKey<T> key, T fallback) {
        return runtimeTower == null ? fallback : runtimeTower.getDataOrDefault(key, fallback);
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

    public void syncTowerState(Tower tower) {
        runtimeTower = tower;
        laneId = tower.laneId();
        teamId = tower.teamId();
        ownerPlayer = tower.ownerPlayer();
        attackRange = tower.type().range();
        attackDamage = tower.type().damage();
        attackIntervalTicks = tower.type().attackIntervalTicks();
        aggroPriority = tower.aggroPriority();
        boolean wasFinalDefense = finalDefense;
        finalDefense = tower.deployedAtFinalDefense();
        if (finalDefense && (!wasFinalDefense || finalDefenseAnchorPosition == null)) {
            finalDefenseAnchorPosition = towerAnchorPosition(tower.position());
        } else if (!finalDefense) {
            finalDefenseAnchorPosition = null;
        }
        EntityVisual updatedVisual = tower.type().visual();
        String updatedModelId = updatedVisual.blockbenchModel().orElse(null);
        boolean modelChanged = !java.util.Objects.equals(updatedModelId, blockbenchModelId);
        visual = updatedVisual;
        blockbenchModelId = updatedModelId;
        setPolymerEntityType(visual.entityTypeId());
        applyVisualScale(visual);
        targetAcquireRange = Math.max(attackRange + 4.0, DEFAULT_TARGET_ACQUIRE_RANGE);
        setCustomName(Component.literal(tower.type().displayName()));
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(tower.currentMaxHealth());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attackDamage);
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(targetAcquireRange);
        if (Math.abs(getHealth() - tower.health()) > 0.01F) {
            setHealth((float) tower.health());
        }
        if (modelChanged) {
            installBilModel(blockbenchModelId);
            playAnimation(SemionAnimationState.IDLE);
        }
    }

    @Override
    public boolean isHealingAlly(HealingTarget other) {
        if (!(other instanceof SemionTowerEntity towerEntity) || towerEntity == this) {
            return false;
        }
        return teamId != null
                && teamId == towerEntity.teamId
                && (finalDefense || towerEntity.finalDefense || laneId == towerEntity.laneId);
    }

    @Override
    public boolean canReceiveHealing() {
        return runtimeTower != null && runtimeTower.health() > 0.0 && runtimeTower.health() < runtimeTower.currentMaxHealth();
    }

    @Override
    public double missingHealingHealth() {
        if (runtimeTower == null) {
            return 0.0;
        }
        return Math.max(0.0, runtimeTower.currentMaxHealth() - runtimeTower.health());
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
    public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
        EntityVisualApplierRegistry.apply(visual, polymerEntityType, level().registryAccess(), data);
    }

    @Override
    public AnimatedEntityHolder getHolder() {
        return holder;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    protected void actuallyHurt(ServerLevel serverLevel, DamageSource damageSource, float amount) {
        if (damageSource.getEntity() instanceof ServerPlayer) {
            return;
        }

        double damageAmount = amount * (1.0 - timedEffects.magnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION));
        if (runtimeTower != null) {
            damageAmount = runtimeTower.modifyIncomingDamage(this, damageSource, damageAmount);
        }
        if (damageAmount <= 0.0) {
            return;
        }

        double previousHealth = getHealth();
        super.actuallyHurt(serverLevel, damageSource, (float) damageAmount);
        invulnerableTime = 0;
        double currentHealth = getHealth();
        if (runtimeTower != null) {
            runtimeTower.syncHealth(currentHealth);
            runtimeTower.onDamaged(this, damageSource, damageAmount, previousHealth, currentHealth);
        }
    }

    private void setPolymerEntityType(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isBlank()) {
            polymerEntityType = EntityType.VILLAGER;
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(entityTypeId);
        if (id == null) {
            polymerEntityType = EntityType.VILLAGER;
            return;
        }

        polymerEntityType = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(EntityType.VILLAGER);
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

    private void applyVisualScale(EntityVisual visual) {
        getAttribute(Attributes.SCALE).setBaseValue(visual == null ? EntityVisual.DEFAULT_SCALE : visual.scale());
        refreshDimensions();
        if (holder != null) {
            holder.setScale(1.0F);
        }
    }

    private void moveToward(Vec3 targetPosition, double speedModifier) {
        playAnimation(SemionAnimationState.WALK);
        getNavigation().moveTo(targetPosition.x, targetPosition.y, targetPosition.z, speedModifier);
        getMoveControl().setWantedPosition(targetPosition.x, targetPosition.y, targetPosition.z, speedModifier);
        getLookControl().setLookAt(targetPosition.x, targetPosition.y, targetPosition.z);

        Vec3 offset = new Vec3(targetPosition.x - getX(), 0.0, targetPosition.z - getZ());
        double distance = offset.length();
        if (distance <= 0.05) {
            getNavigation().stop();
            Vec3 velocity = getDeltaMovement();
            setDeltaMovement(0.0, velocity.y, 0.0);
            return;
        }

        Vec3 velocity = getDeltaMovement();
        Vec3 step = offset.normalize().scale(Math.min(speedModifier, distance));
        move(MoverType.SELF, step);
        clampToFinalDefenseAreaIfNeeded();
        setDeltaMovement(0.0, velocity.y, 0.0);
    }

    private void clampToFinalDefenseAreaIfNeeded() {
        if (!finalDefense || laneLayout == null || laneLayout.isInsideFinalDefenseTowerArea(position())) {
            return;
        }

        Vec3 clampedPosition = laneLayout.clampToFinalDefenseTowerArea(position());
        teleportTo(clampedPosition.x, clampedPosition.y, clampedPosition.z);
        getNavigation().stop();
    }

    private static Vec3 towerAnchorPosition(GridPosition position) {
        return new Vec3(position.x() + 0.5, position.y() + 1.0, position.z() + 0.5);
    }
}
