package kim.biryeong.semiontd.entity.tower;

import de.tomalbrc.bil.api.AnimatedEntity;
import de.tomalbrc.bil.api.AnimatedEntityHolder;
import de.tomalbrc.bil.core.holder.entity.living.LivingEntityHolder;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.effect.TimedEffectSet;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.defender.LaneDefenseEntity;
import kim.biryeong.semiontd.entity.healing.HealingTarget;
import kim.biryeong.semiontd.entity.model.SemionBilModelCache;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisualApplierRegistry;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.entity.visual.MoobloomVisual;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.mixin.accessor.MoobloomAccessor;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.end.EndTower;
import kim.biryeong.semiontd.tower.end.EndTowerState;
import kim.biryeong.semiontd.tower.end.EndTowers;
import kim.biryeong.semiontd.tower.ocean.OceanWaterTower;
import kim.biryeong.semiontd.trait.BuiltInTraits;
import kim.biryeong.semiontd.trait.TraitEffects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import com.faboslav.friendsandfoes.common.entity.MoobloomEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import xyz.nucleoid.packettweaker.PacketContext;

public final class SemionTowerEntity extends PathfinderMob implements AnimatedEntity, LaneDefenseEntity, HealingTarget {
    public static final double FINAL_DEFENSE_TARGET_RANGE = 7.0;
    private static final double DEFAULT_MOVE_SPEED = 0.23;
    private static final double DEFAULT_TARGET_ACQUIRE_RANGE = 24.0;
    private static final double TARGET_SEARCH_HORIZONTAL_PADDING = 8.0;
    private static final double TARGET_SEARCH_VERTICAL_PADDING = 3.0;
    private static final double FINAL_DEFENSE_RETURN_SPEED_MULTIPLIER = 1.25;
    private static final double END_CRYSTAL_COLLISION_SCALE = 0.5;
    private static final double MOOBLOOM_COLLISION_SCALE = 0.75;
    private static final double MOOBLOOM_VISUAL_POSITION_EPSILON = 1.0E-4;
    private static final float END_CORE_HITBOX_WIDTH = 1.0F;
    private static final float END_CORE_HITBOX_HEIGHT = 1.0F;
    private static final float DRAGON_INTERACTION_WIDTH = 16.0F;
    private static final float DRAGON_INTERACTION_HEIGHT = 8.0F;

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
    private AABB cachedTargetSearchBox;
    private LaneRegionLayout cachedTargetSearchLaneLayout;
    private double cachedTargetSearchX = Double.NaN;
    private double cachedTargetSearchY = Double.NaN;
    private double cachedTargetSearchZ = Double.NaN;
    private double cachedTargetSearchAcquireRange = Double.NaN;
    private EntityVisual visual = EntityVisual.vanilla(EntityVisual.DEFAULT_TOWER_ENTITY_TYPE);
    private String blockbenchModelId;
    private SemionAnimationState animationState = SemionAnimationState.IDLE;
    private EntityType<?> polymerEntityType = EntityType.ARMOR_STAND;
    private final TimedEffectSet timedEffects = new TimedEffectSet();
    private LivingEntityHolder<SemionTowerEntity> holder;
    private EntityAttachment holderAttachment;
    private ElementHolder blockDisplayHolder;
    private BlockDisplayElement blockDisplayElement;
    private BlockDisplayElement blockDisplayTopElement;
    private ElementHolder endCoreInteractionHolder;
    private InteractionElement endCoreInteractionElement;
    private MoobloomEntity moobloomVisualEntity;
    private String syncedMoobloomVisualVariant;
    private Component syncedMoobloomVisualName;
    private boolean syncedMoobloomVisualNameVisible;
    private double syncedMoobloomVisualX = Double.NaN;
    private double syncedMoobloomVisualY = Double.NaN;
    private double syncedMoobloomVisualZ = Double.NaN;
    private float syncedMoobloomVisualYRot = Float.NaN;
    private float syncedMoobloomVisualXRot = Float.NaN;
    private float syncedMoobloomVisualYHeadRot = Float.NaN;
    private float syncedMoobloomVisualYBodyRot = Float.NaN;
    private boolean moobloomVisualSyncDirty = true;
    private SemionTowerEntity attackTargetSource;
    private SemionMonsterEntity currentAttackTarget;
    private boolean forceAttackReady;
    private boolean illusionClone;
    private float lockedDragonYaw = Float.NaN;
    private float lockedDragonPitch = Float.NaN;

    public SemionTowerEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        setSilent(true);
        setPersistenceRequired();
        syncEndCoreFlightPhysics();
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
        attackRange = tower.adjustAttackRange(tower.type().range());
        attackDamage = tower.type().damage();
        attackIntervalTicks = tower.type().attackIntervalTicks();
        aggroPriority = tower.aggroPriority();
        finalDefense = tower.deployedAtFinalDefense();
        finalDefenseAnchorPosition = finalDefense ? towerAnchorPosition(tower) : null;
        syncEndCoreFlightPhysics();
        visual = tower.visual();
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
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(resolvedMovementSpeed());
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        setHealth((float) tower.health());
        installBilModel(blockbenchModelId);
        playAnimation(SemionAnimationState.IDLE);
        markMoobloomVisualSyncDirty();
    }


    public int laneId() {
        return laneId;
    }

    public Tower runtimeTower() {
        return runtimeTower;
    }

    public boolean ownsMoobloomVisualEntity(Entity entity) {
        return entity != null && entity == moobloomVisualEntity;
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

    public void faceAttackTarget(Entity target) {
        if (target == null
                || !(runtimeTower instanceof EndTower endTower)
                || endTower.state() != EndTowerState.DRAGON) {
            return;
        }
        double xOffset = target.getX() - getX();
        double zOffset = target.getZ() - getZ();
        double horizontalDistance = Math.sqrt(xOffset * xOffset + zOffset * zOffset);
        double yOffset = target.getEyeY() - getEyeY();
        float yaw = (float) (Math.toDegrees(Math.atan2(zOffset, xOffset)) + 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(yOffset, horizontalDistance));
        lockedDragonYaw = yaw;
        lockedDragonPitch = pitch;
        applyLockedDragonRotation();
    }

    public void forceAttackReady() {
        forceAttackReady = true;
    }

    public boolean consumeForceAttackReady() {
        boolean forced = forceAttackReady;
        forceAttackReady = false;
        return forced;
    }

    public void markIllusionClone() {
        illusionClone = true;
    }

    public boolean isIllusionClone() {
        return illusionClone;
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

    @Override
    public boolean drawsAggro() {
        return runtimeTower == null || runtimeTower.drawsAggro();
    }

    public boolean isInvulnerableTower() {
        return runtimeTower != null && runtimeTower.invulnerable();
    }

    public boolean isValidAttackTarget(SemionMonsterEntity target) {
        return target != null
                && target.isAlive()
                && !target.isRemoved()
                && target.runtimeMonster() != null
                && target.runtimeMonster().targetTeam() == teamId
                && defendsLane(target.runtimeMonster().targetLaneId());
    }

    @Override
    public void aiStep() {
        super.aiStep();
        invulnerableTime = 0;
        double previousMaxHealthBonus = activeTimedEffectMagnitude(TimedEffectType.TOWER_MAX_HEALTH_BONUS);
        timedEffects.tick();
        if (Double.compare(previousMaxHealthBonus, activeTimedEffectMagnitude(TimedEffectType.TOWER_MAX_HEALTH_BONUS)) != 0) {
            syncMaxHealthEffect(TimedEffectType.TOWER_MAX_HEALTH_BONUS);
        }
        syncMoobloomVisualEntity();
        syncBlockDisplayVisual();
        syncEndCoreInteractionHitbox();
        returnToFinalDefenseAreaIfNeeded();
        syncEndCoreFlightPhysics();
        applyLockedDragonRotation();
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
        if (finalDefense) {
            return getBoundingBox().inflate(FINAL_DEFENSE_TARGET_RANGE);
        }
        if (cachedTargetSearchBox != null
                && cachedTargetSearchLaneLayout == laneLayout
                && Double.compare(cachedTargetSearchX, getX()) == 0
                && Double.compare(cachedTargetSearchY, getY()) == 0
                && Double.compare(cachedTargetSearchZ, getZ()) == 0
                && Double.compare(cachedTargetSearchAcquireRange, targetAcquireRange) == 0) {
            return cachedTargetSearchBox;
        }

        cachedTargetSearchBox = laneLayout == null
                ? getBoundingBox().inflate(targetAcquireRange)
                : laneLayout.defenseSearchBox(position(), TARGET_SEARCH_HORIZONTAL_PADDING, TARGET_SEARCH_VERTICAL_PADDING);
        cachedTargetSearchLaneLayout = laneLayout;
        cachedTargetSearchX = getX();
        cachedTargetSearchY = getY();
        cachedTargetSearchZ = getZ();
        cachedTargetSearchAcquireRange = targetAcquireRange;
        return cachedTargetSearchBox;
    }

    public double attackDamageAmount(SemionMonsterEntity target) {
        double damageAmount = attackDamage * (1.0 + timedEffects.magnitude(TimedEffectType.TOWER_DAMAGE_BONUS));
        if (runtimeTower != null) {
            damageAmount = runtimeTower.modifyAttackDamage(this, target, damageAmount);
        }
        Monster runtimeMonster = target == null ? null : target.runtimeMonster();
        if (runtimeMonster != null) {
            if (runtimeMonster.senderTeam().isPresent()) {
                damageAmount *= 1.0 + timedEffects.magnitude(TimedEffectType.TOWER_INCOME_DAMAGE_BONUS);
            }
            else {
                damageAmount *= 1.0 + timedEffects.magnitude(TimedEffectType.TOWER_WAVE_DAMAGE_BONUS);
            }
        }
        if (runtimeTower != null) {damageAmount = runtimeTower.modifyResolvedAttackDamage(this, target, damageAmount);
        }
        return Double.isFinite(damageAmount) ? Math.max(0.0, damageAmount) : 0.0;
    }

    public double applyTraitOutgoingDamage(Monster target, double damageAmount) {
        return applyTowerFinalDamageBonus(applyTraitOutgoingDamage(target, damageAmount, 0.0));
    }

    public double applyTraitOutgoingDamageBeforeTowerFinalAgainst(
            SemionMonsterEntity target,
            double damageAmount
    ) {
        Monster runtimeMonster = target == null ? null : target.runtimeMonster();
        double conditionalBonus = runtimeTower == null
                ? 0.0
                : TraitEffects.conditionalTargetDamageBonus(
                        runtimeTower.traitLoadout(),
                        runtimeMonster,
                        target != null && target.hasDebuff()
                );
        return applyTraitOutgoingDamage(runtimeMonster, damageAmount, conditionalBonus);
    }

    public double applyTraitOutgoingDamageAgainst(SemionMonsterEntity target, double damageAmount) {
        return applyTowerFinalDamageBonus(
                applyTraitOutgoingDamageBeforeTowerFinalAgainst(target, damageAmount)
        );
    }

    private double applyTraitOutgoingDamage(
            Monster target,
            double damageAmount,
            double conditionalBonus
    ) {
        return Math.max(0.0, damageAmount)
                * (1.0 + traitAdditiveDamageBonus(target) + Math.max(0.0, conditionalBonus))
                * traitFinalDamageMultiplier();
    }

    public double applyTowerFinalDamageBonus(double damageAmount) {
        if (!Double.isFinite(damageAmount) || damageAmount <= 0.0) {
            return 0.0;
        }
        double finalDamage = damageAmount * towerFinalDamageMultiplier();
        return Double.isFinite(finalDamage) && finalDamage > 0.0 ? finalDamage : 0.0;
    }

    private double traitAdditiveDamageBonus(Monster target) {
        double additiveBonus = activeEffectMagnitude(TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS);
        if (target != null) {
            additiveBonus += target.senderTeam().isPresent()
                    ? activeEffectMagnitude(TimedEffectType.TOWER_TRAIT_INCOME_DAMAGE_BONUS)
                    : activeEffectMagnitude(TimedEffectType.TOWER_TRAIT_WAVE_DAMAGE_BONUS);
        }
        return additiveBonus;
    }

    private double traitFinalDamageMultiplier() {
        return 1.0 + finiteNonNegativeBonus(
                activeEffectMagnitude(TimedEffectType.TOWER_FINAL_DAMAGE_BONUS)
        );
    }

    private double towerFinalDamageMultiplier() {
        return 1.0 + finiteNonNegativeBonus(
                runtimeTower == null ? 0.0 : runtimeTower.finalDamageBonus()
        );
    }

    private double combinedFinalDamageMultiplier() {
        return traitFinalDamageMultiplier() * towerFinalDamageMultiplier();
    }

    private static double finiteNonNegativeBonus(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 0.0;
    }

    public double applyTraitIncomingDamage(double damageAmount) {
        return Math.max(0.0, damageAmount)
                * (1.0 + activeEffectMagnitude(TimedEffectType.TOWER_DAMAGE_TAKEN_BONUS));
    }

    public int attackIntervalTicks() {
        int adjustedInterval = runtimeTower == null ? attackIntervalTicks : runtimeTower.adjustAttackInterval(attackIntervalTicks);
        double attackSpeedMultiplier = 1.0
                + timedEffects.magnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS)
                - timedEffects.magnitude(TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION);
        int minimumInterval = runtimeTower == null ? 1 : Math.max(1, runtimeTower.minimumAttackIntervalTicks());
        int resolvedInterval = (int) Math.ceil(adjustedInterval / Math.max(0.01, attackSpeedMultiplier));
        return Math.max(minimumInterval, resolvedInterval);
    }

    private double resolvedMovementSpeed() {
        if (runtimeTower == null) {
            return moveSpeed;
        }
        return Math.max(0.0, runtimeTower.adjustMovementSpeed(moveSpeed)
        );
    }

    public boolean playsRangedAttackSound() {
        return attackDamage > 0.0 && attackRange() > 3.0;
    }

    public double chaseSpeedModifier() {
        return resolvedMovementSpeed();
    }

    public boolean canChaseTargets() {
        return runtimeTower == null || runtimeTower.canChaseTargets();
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

    public boolean damageTarget(SemionMonsterEntity target, double baseDamage) {return damageTargetResult(target, baseDamage).killed();}

    public Tower.DamageResult damageTargetResult(SemionMonsterEntity target, double baseDamage) {
        if (runtimeTower == null || target == null) {return Tower.DamageResult.NONE;}
        return runtimeTower.damageTargetResult(this, target, baseDamage);
    }

    public Tower.DamageResult damageBasicAttackSecondaryTargetResult(SemionMonsterEntity target, double baseDamage) {
        Tower.DamageResult result = damageTargetResult(target, baseDamage);
        applyIgniteFromBasicAttack(target, baseDamage, result.killed());
        return result;
    }

    public void recordAttack(SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
        recordAttack(target, damageAmount, damageAmount, damageAmount, killedTarget);
    }

    public void recordAttack(
            SemionMonsterEntity target,
            double attemptedDamage,
            double dealtDamage,
            boolean killedTarget
    ) {
        recordAttack(target, attemptedDamage, attemptedDamage, dealtDamage, killedTarget);
    }

    public void recordAttack(
            SemionMonsterEntity target,
            double attemptedDamage,
            double resolvedOutgoingDamage,
            double dealtDamage,
            boolean killedTarget
    ) {
        if (runtimeTower == null) {
            return;
        }

        runtimeTower.onAttackResolved(
                this,
                target,
                attemptedDamage,
                resolvedOutgoingDamage,
                dealtDamage,
                killedTarget
        );
        applyIgniteFromBasicAttack(target, attemptedDamage, killedTarget);
        if (killedTarget) {
            runtimeTower.onKill(this, target, attemptedDamage);
        }
    }

    public void applyIgniteFromBasicAttack(SemionMonsterEntity target, double attackDamage, boolean killedTarget) {
        if (runtimeTower == null || attackDamage <= 0.0 || killedTarget
                || target == null || !target.isAlive() || target.isRemoved()) {
            return;
        }
        double igniteDamage = TraitEffects.igniteDamagePerTick(
                runtimeTower.traitLoadout(),
                attackDamage,
                runtimeTower.currentRound()
        );
        if (igniteDamage <= 0.0) {
            return;
        }
        target.applyIgnite(
                ownerPlayer,
                runtimeTower,
                runtimeTower.traitLoadout(),
                igniteDamage,
                traitAdditiveDamageBonus(target.runtimeMonster()),
                combinedFinalDamageMultiplier(),
                TraitEffects.igniteDurationTicks(),
                TraitEffects.igniteTickIntervalTicks()
        );
    }

    public void applyTimedEffect(TimedEffectType type, double magnitude, int durationTicks) {
        double previousMagnitude = type == null ? 0.0 : activeTimedEffectMagnitude(type);
        int previousTicks = type == null ? 0 : activeTimedEffectTicks(type);
        timedEffects.apply(type, magnitude, durationTicks);
        double currentMagnitude = type == null ? 0.0 : activeTimedEffectMagnitude(type);
        int currentTicks = type == null ? 0 : activeTimedEffectTicks(type);
        syncMaxHealthEffect(type);
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
        syncMaxHealthEffect(type);
        if (applied
                && runtimeTower != null
                && type != null
                && currentTicks > 0
                && (Double.compare(previousMagnitude, currentMagnitude) != 0 || previousTicks != currentTicks)) {
            runtimeTower.onTimedEffectApplied(this, type, currentMagnitude, currentTicks);
        }
        return applied;
    }

    public boolean refreshTimedEffect(TimedEffectType type, ResourceLocation sourceId, double magnitude, int durationTicks) {
        double previousMagnitude = type == null ? 0.0 : activeTimedEffectMagnitude(type);
        int previousTicks = type == null ? 0 : activeTimedEffectTicks(type);
        boolean refreshed = timedEffects.refresh(type, sourceId, magnitude, durationTicks);
        double currentMagnitude = type == null ? 0.0 : activeTimedEffectMagnitude(type);
        int currentTicks = type == null ? 0 : activeTimedEffectTicks(type);
        syncMaxHealthEffect(type);
        if (refreshed
                && runtimeTower != null
                && type != null
                && currentTicks > 0
                && (Double.compare(previousMagnitude, currentMagnitude) != 0 || previousTicks != currentTicks)) {
            runtimeTower.onTimedEffectApplied(this, type, currentMagnitude, currentTicks);
        }
        return refreshed;
    }

    public boolean setPersistentEffect(TimedEffectType type, ResourceLocation sourceId, double magnitude) {
        boolean changed = timedEffects.setPersistent(type, sourceId, magnitude);
        if (changed) {
            syncMaxHealthEffect(type);
        }
        return changed;
    }

    public void inheritTraitEffectsFrom(SemionTowerEntity source) {
        if (source == null) {
            return;
        }
        copyPersistentTraitEffect(source, TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS, BuiltInTraits.STRENGTH_IN_NUMBERS_ID);
        copyPersistentTraitEffect(source, TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS, BuiltInTraits.DIVERSITY_ID);
        copyPersistentTraitEffect(source, TimedEffectType.TOWER_TRAIT_DAMAGE_BONUS, BuiltInTraits.TRANSCENDENCE_ID);
        copyPersistentTraitEffect(source, TimedEffectType.TOWER_TRAIT_INCOME_DAMAGE_BONUS, BuiltInTraits.INTERCEPTION_DOCTRINE_ID);
        copyPersistentTraitEffect(source, TimedEffectType.TOWER_TRAIT_WAVE_DAMAGE_BONUS, BuiltInTraits.WAVEBREAKER_DOCTRINE_ID);
        copyPersistentTraitEffect(source, TimedEffectType.TOWER_FINAL_DAMAGE_BONUS, BuiltInTraits.DOUBLE_EDGED_SWORD_ID);
        copyPersistentTraitEffect(source, TimedEffectType.TOWER_DAMAGE_TAKEN_BONUS, BuiltInTraits.DOUBLE_EDGED_SWORD_ID);
        copyPersistentTraitEffect(source, TimedEffectType.TOWER_TRAIT_MAX_HEALTH_BONUS, BuiltInTraits.FORTITUDE_ID);

        double openingMagnitude = source.timedEffects.magnitude(
                TimedEffectType.TOWER_ATTACK_SPEED_BONUS,
                BuiltInTraits.OPENING_SALVO_ID
        );
        int openingTicks = source.timedEffects.remainingTicks(
                TimedEffectType.TOWER_ATTACK_SPEED_BONUS,
                BuiltInTraits.OPENING_SALVO_ID
        );
        if (openingMagnitude > 0.0 && openingTicks > 0) {
            refreshTimedEffect(
                    TimedEffectType.TOWER_ATTACK_SPEED_BONUS,
                    BuiltInTraits.OPENING_SALVO_ID,
                    openingMagnitude,
                    openingTicks
            );
        }
    }

    private void copyPersistentTraitEffect(
            SemionTowerEntity source,
            TimedEffectType type,
            ResourceLocation sourceId
    ) {
        setPersistentEffect(type, sourceId, source.timedEffects.persistentMagnitude(type, sourceId));
    }

    public double activeEffectMagnitude(TimedEffectType type) {
        return timedEffects.magnitude(type);
    }

    public double activeTimedEffectMagnitude(TimedEffectType type) {
        return activeEffectMagnitude(type);
    }

    public int activeTimedEffectTicks(TimedEffectType type) {
        return timedEffects.remainingTicks(type);
    }

    public boolean hasTimedEffectSource(TimedEffectType type, ResourceLocation sourceId) {
        return timedEffects.hasSource(type, sourceId);
    }

    public boolean hasPersistentEffect(TimedEffectType type) {
        return timedEffects.hasPersistent(type);
    }

    public boolean hasPersistentEffect(TimedEffectType type, ResourceLocation sourceId) {
        return timedEffects.hasPersistent(type, sourceId);
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

    private void syncMaxHealthEffect(TimedEffectType type) {
        syncMaxHealthEffect(type, true);
    }

    private void syncMaxHealthEffect(TimedEffectType type, boolean healIncrease) {
        if (runtimeTower == null
                || (type != TimedEffectType.TOWER_MAX_HEALTH_BONUS
                && type != TimedEffectType.TOWER_TRAIT_MAX_HEALTH_BONUS)) {
            return;
        }
        double nextMaxHealth = runtimeTower.effectBaseMaxHealth()
                * (1.0 + activeEffectMagnitude(TimedEffectType.TOWER_MAX_HEALTH_BONUS));
        runtimeTower.syncEffectMaxHealth(
                nextMaxHealth,
                activeEffectMagnitude(TimedEffectType.TOWER_TRAIT_MAX_HEALTH_BONUS),
                healIncrease
        );
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(runtimeTower.currentMaxHealth());
        applyVisualScale(visual);
        setHealth((float) runtimeTower.health());
    }

    public void refreshMaxHealthEffects() {
        refreshMaxHealthEffects(true);
    }

    public void refreshMaxHealthEffects(boolean healIncrease) {
        syncMaxHealthEffect(TimedEffectType.TOWER_MAX_HEALTH_BONUS, healIncrease);
    }

    public void refreshCombatStats() {
        if (runtimeTower == null) {
            return;
        }
        attackRange = runtimeTower.adjustAttackRange(runtimeTower.type().range());
        targetAcquireRange = Math.max(attackRange + 4.0, DEFAULT_TARGET_ACQUIRE_RANGE);
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(targetAcquireRange);
    }

    public String blockbenchModelId() {
        return blockbenchModelId;
    }

    public boolean hasBilModelHolder() {
        return holder != null;
    }

    public float bilModelScale() {
        return holder == null ? 0.0F : holder.getScale();
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
        EntityType<?> previousPolymerEntityType = getPolymerEntityType(null);
        EntityVisual previousVisual = visual;
        String previousDisplayName = getCustomName() == null ? null : getCustomName().getString();
        boolean previousNameVisible = isCustomNameVisible();
        runtimeTower = tower;
        laneId = tower.laneId();
        teamId = tower.teamId();
        ownerPlayer = tower.ownerPlayer();
        attackRange = tower.adjustAttackRange(tower.type().range());
        attackDamage = tower.type().damage();
        attackIntervalTicks = tower.type().attackIntervalTicks();
        aggroPriority = tower.aggroPriority();
        boolean wasFinalDefense = finalDefense;
        finalDefense = tower.deployedAtFinalDefense();
        if (finalDefense && (!wasFinalDefense || finalDefenseAnchorPosition == null)) {
            finalDefenseAnchorPosition = towerAnchorPosition(tower);
        } else if (!finalDefense) {
            finalDefenseAnchorPosition = null;
        }
        EntityVisual updatedVisual = tower.visual();
        String updatedModelId = updatedVisual.blockbenchModel().orElse(null);
        boolean modelChanged = !java.util.Objects.equals(updatedModelId, blockbenchModelId);
        visual = updatedVisual;
        blockbenchModelId = updatedModelId;
        syncEndCoreFlightPhysics();
        setPolymerEntityType(visual.entityTypeId());
        applyVisualScale(visual);
        targetAcquireRange = Math.max(attackRange + 4.0, DEFAULT_TARGET_ACQUIRE_RANGE);
        String displayName = tower.type().displayName();
        setCustomName(Component.literal(displayName));
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(tower.currentMaxHealth());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attackDamage);
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(targetAcquireRange);
        if (Math.abs(getHealth() - tower.health()) > 0.01F) {
            setHealth((float) tower.health());
        }
        if (modelChanged) {
            installBilModel(blockbenchModelId);
            applyVisualScale(visual);
            playAnimation(SemionAnimationState.IDLE);
        }
        if (!Objects.equals(previousVisual, updatedVisual)
                || !Objects.equals(previousDisplayName, displayName)
                || !previousNameVisible) {
            markMoobloomVisualSyncDirty();
        }
        syncBlockDisplayVisual();
        syncEndCoreInteractionHitbox();
        if (previousPolymerEntityType != getPolymerEntityType(null)) {
            PolymerEntityUtils.refreshEntity(this);
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
        if (usesMoobloomOverlayVisual()) {
            return EntityType.INTERACTION;
        }
        if (usesBlockDisplayOverlayVisual()) {
            return EntityType.ARMOR_STAND;
        }
        return polymerEntityType;
    }

    @Override
    public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
        if (blockbenchModelId != null) {
            AnimatedEntity.super.modifyRawTrackedData(data, player, initial);
            return;
        }
        if (usesMoobloomOverlayVisual()) {
            return;
        }
        if (usesBlockDisplayOverlayVisual()) {
            applyInvisibleArmorStandProxyData(data);
            return;
        }
        EntityVisualApplierRegistry.apply(
                visual,
                polymerEntityType,
                level().registryAccess(),
                data
        );
    }

    private void applyInvisibleArmorStandProxyData(List<SynchedEntityData.DataValue<?>> data) {
        byte flags = (byte) (entityData.get(DATA_SHARED_FLAGS_ID) | 0x20);
        SynchedEntityData.DataValue<Byte> invisibleFlags =
                SynchedEntityData.DataValue.create(
                        DATA_SHARED_FLAGS_ID,
                        flags
                );
        for (int index = 0; index < data.size(); index++) {
            if (data.get(index).id() == DATA_SHARED_FLAGS_ID.id()) {
                data.set(index, invisibleFlags);
                return;
            }
        }
        data.add(invisibleFlags);
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
    public boolean canBreatheUnderwater() {
        return runtimeTower instanceof OceanWaterTower || super.canBreatheUnderwater();
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public void remove(RemovalReason reason) {
        discardMoobloomVisualEntity();
        discardBlockDisplayVisual();
        discardEndCoreInteractionHitbox();
        if (!isRemoved() && level() instanceof ServerLevel serverLevel) {
            // Lane ticks may discard this entity before vanilla tracking sends Polymer proxy removal.
            ClientboundRemoveEntitiesPacket packet = new ClientboundRemoveEntitiesPacket(getId());
            for (ServerPlayer player : serverLevel.players()) {
                player.connection.send(packet);
            }
        }
        super.remove(reason);
    }

    @Override
    protected void actuallyHurt(ServerLevel serverLevel, DamageSource damageSource, float amount) {
        if (damageSource.getEntity() instanceof ServerPlayer || isInvulnerableTower()) {
            return;
        }

        double damageAmount = amount * (1.0 - timedEffects.magnitude(TimedEffectType.TOWER_DAMAGE_REDUCTION));
        if (runtimeTower != null) {
            damageAmount = applyTraitIncomingDamage(damageAmount);
            damageAmount = runtimeTower.modifyIncomingDamage(this, damageSource, damageAmount);
        }
        if (damageAmount <= 0.0) {
            return;
        }

        applyDamage(serverLevel, damageSource, damageAmount);
    }

    public void hurtIgnoringReductions(DamageSource damageSource, double damageAmount) {
        if (!(level() instanceof ServerLevel serverLevel) || damageAmount <= 0.0 || isInvulnerableTower()) {
            return;
        }
        if (runtimeTower != null) {
            damageAmount = runtimeTower.modifyIncomingDamageIgnoringReductions(this, damageSource, damageAmount);
        }
        if (damageAmount <= 0.0) {
            return;
        }
        applyDamage(serverLevel, damageSource, damageAmount);
    }

    private void applyDamage(ServerLevel serverLevel, DamageSource damageSource, double damageAmount) {
        double previousHealth = getHealth();
        super.actuallyHurt(serverLevel, damageSource, (float) damageAmount);
        invulnerableTime = 0;
        double currentHealth = getHealth();
        if (runtimeTower != null) {
            runtimeTower.syncHealth(currentHealth);
            runtimeTower.recordDamageTaken(Math.max(0.0, previousHealth - currentHealth));
            runtimeTower.onDamaged(this, damageSource, damageAmount, previousHealth, currentHealth);
        }
    }

    private boolean usesMoobloomOverlayVisual() {
        return blockbenchModelId == null && MoobloomVisual.matches(visual);
    }

    private boolean usesBlockDisplayOverlayVisual() {
        return blockbenchModelId == null && BlockDisplayVisual.matches(visual);
    }

    private boolean usesOneBlockEndCoreHitbox() {
        return runtimeTower instanceof EndTower endTower
                && EndTowers.isBaseEndTower(runtimeTower.type())
                && (endTower.state() == EndTowerState.PHANTOM
                || endTower.state() == EndTowerState.DRAGON);
    }

    private boolean usesEndCoreInteractionHitbox() {
        return runtimeTower instanceof EndTower endTower
                && EndTowers.isBaseEndTower(runtimeTower.type())
                && endTower.state() == EndTowerState.DRAGON
                && blockbenchModelId == null
                && polymerEntityType == EntityType.ENDER_DRAGON;
    }

    public boolean hasEndCoreInteractionHitbox() {
        return endCoreInteractionHolder != null && endCoreInteractionElement != null;
    }

    private void syncEndCoreInteractionHitbox() {
        if (!usesEndCoreInteractionHitbox()) {
            discardEndCoreInteractionHitbox();
            return;
        }
        if (endCoreInteractionHolder == null || endCoreInteractionElement == null) {
            endCoreInteractionElement = InteractionElement.redirect(this);
            endCoreInteractionElement.setResponse(true);
            endCoreInteractionHolder = new ElementHolder();
            endCoreInteractionHolder.addElement(endCoreInteractionElement);
            EntityAttachment.ofTicking(endCoreInteractionHolder, this);
        }
        endCoreInteractionElement.setSize(
                DRAGON_INTERACTION_WIDTH,
                DRAGON_INTERACTION_HEIGHT
        );
    }

    @Override
    public void modifyRawEntityAttributeData(
            List<ClientboundUpdateAttributesPacket.AttributeSnapshot> data,
            ServerPlayer player,
            boolean initial
    ) {
        AnimatedEntity.super.modifyRawEntityAttributeData(data, player, initial);
        if (!(runtimeTower instanceof EndTower endTower)
                || endTower.state() != EndTowerState.PHANTOM) {
            return;
        }
        double renderScale = endTower.phantomScaleForMaxHealth(runtimeTower.currentMaxHealth());
        for (int index = 0; index < data.size(); index++) {
            ClientboundUpdateAttributesPacket.AttributeSnapshot snapshot = data.get(index);
            if (snapshot.attribute().equals(Attributes.SCALE)) {
                data.set(index, new ClientboundUpdateAttributesPacket.AttributeSnapshot(
                        snapshot.attribute(),
                        renderScale,
                        snapshot.modifiers()
                ));
                return;
            }
        }
        data.add(new ClientboundUpdateAttributesPacket.AttributeSnapshot(Attributes.SCALE, renderScale, List.of()));
    }

    private void discardEndCoreInteractionHitbox() {
        if (endCoreInteractionHolder != null) {
            endCoreInteractionHolder.destroy();
        }
        endCoreInteractionHolder = null;
        endCoreInteractionElement = null;
    }

    private void syncBlockDisplayVisual() {
        if (!usesBlockDisplayOverlayVisual()) {
            discardBlockDisplayVisual();
            return;
        }
        var blockState = BlockDisplayVisual.blockState(visual);
        if (blockState == null) {
            discardBlockDisplayVisual();
            return;
        }
        if (blockDisplayHolder == null || blockDisplayElement == null) {
            blockDisplayElement = new BlockDisplayElement(blockState);
            blockDisplayElement.setTeleportDuration(3);
            blockDisplayElement.setShadowRadius(0.5F);
            blockDisplayElement.setShadowStrength(1.0F);
            blockDisplayHolder = new ElementHolder();
            blockDisplayHolder.addElement(blockDisplayElement);
            EntityAttachment.ofTicking(blockDisplayHolder, this);
        } else if (!blockState.equals(blockDisplayElement.getBlockState())) {
            blockDisplayElement.setBlockState(blockState);
        }
        float scale = (float) visual.scale();
        blockDisplayElement.setTranslation(new Vector3f(-scale / 2.0F, 0.0F, -scale / 2.0F));
        blockDisplayElement.setScale(new Vector3f(scale, scale, scale));
        syncBlockDisplayTopVisual(scale);
    }

    private void syncBlockDisplayTopVisual(float scale) {
        var topBlockState = BlockDisplayVisual.topBlockState(visual);
        if (topBlockState == null) {
            if (blockDisplayTopElement != null) {
                blockDisplayHolder.removeElement(blockDisplayTopElement);
                blockDisplayTopElement = null;
            }
            return;
        }
        if (blockDisplayTopElement == null) {
            blockDisplayTopElement = new BlockDisplayElement(topBlockState);
            blockDisplayTopElement.setTeleportDuration(3);
            blockDisplayHolder.addElement(blockDisplayTopElement);
        } else if (!topBlockState.equals(blockDisplayTopElement.getBlockState())) {
            blockDisplayTopElement.setBlockState(topBlockState);
        }
        blockDisplayTopElement.setTranslation(new Vector3f(-scale / 2.0F, scale, -scale / 2.0F));
        blockDisplayTopElement.setScale(new Vector3f(scale, scale, scale));
    }

    private void discardBlockDisplayVisual() {
        if (blockDisplayHolder != null) {
            blockDisplayHolder.destroy();
        }
        blockDisplayHolder = null;
        blockDisplayElement = null;
        blockDisplayTopElement = null;
    }

    @SuppressWarnings("unchecked")
    private void syncMoobloomVisualEntity() {
        if (!(level() instanceof ServerLevel serverLevel) || !usesMoobloomOverlayVisual()) {
            if (moobloomVisualEntity != null) {
                discardMoobloomVisualEntity();
            }
            return;
        }
        boolean created = false;
        if (moobloomVisualEntity == null || moobloomVisualEntity.isRemoved() || moobloomVisualEntity.level() != serverLevel) {
            moobloomVisualEntity = new MoobloomEntity((EntityType<? extends MoobloomEntity>) polymerEntityType, serverLevel);
            resetMoobloomVisualSyncState();
            moobloomVisualEntity.setNoAi(true);
            moobloomVisualEntity.setNoGravity(true);
            moobloomVisualEntity.noPhysics = true;
            moobloomVisualEntity.setSilent(true);
            moobloomVisualEntity.setInvulnerable(true);
            moobloomVisualEntity.setPersistenceRequired();
            moobloomVisualEntity.addTag(SemionEntityTypes.RUNTIME_NO_SAVE_TAG);
            moobloomVisualEntity.setPos(getX(), getY(), getZ());
            serverLevel.addFreshEntity(moobloomVisualEntity);
            created = true;
        }
        if (!created && !moobloomVisualSyncDirty && !moobloomVisualPoseChanged()) {
            return;
        }
        applyMoobloomVisualVariant();
        syncMoobloomVisualName();
        syncMoobloomVisualRotation();
        syncMoobloomVisualPosition();
        moobloomVisualSyncDirty = false;
    }

    private void applyMoobloomVisualVariant() {
        if (moobloomVisualEntity == null) {
            return;
        }
        String variant = MoobloomVisual.variant(visual);
        if (variant == null || variant.isBlank()) {
            return;
        }
        if (Objects.equals(variant, syncedMoobloomVisualVariant)) {
            return;
        }
        moobloomVisualEntity.getEntityData().set(MoobloomAccessor.semiontd$dataVariant(), variant);
        syncedMoobloomVisualVariant = variant;
    }

    private void syncMoobloomVisualName() {
        Component customName = getCustomName();
        if (!Objects.equals(customName, syncedMoobloomVisualName)) {
            moobloomVisualEntity.setCustomName(customName);
            syncedMoobloomVisualName = customName;
        }
        boolean customNameVisible = isCustomNameVisible();
        if (customNameVisible != syncedMoobloomVisualNameVisible) {
            moobloomVisualEntity.setCustomNameVisible(customNameVisible);
            syncedMoobloomVisualNameVisible = customNameVisible;
        }
    }

    private void syncMoobloomVisualRotation() {
        float yRot = getYRot();
        float xRot = getXRot();
        float yHeadRot = getYHeadRot();
        float yBodyRot = this.yBodyRot;
        if (Float.compare(yRot, syncedMoobloomVisualYRot) == 0
                && Float.compare(xRot, syncedMoobloomVisualXRot) == 0
                && Float.compare(yHeadRot, syncedMoobloomVisualYHeadRot) == 0
                && Float.compare(yBodyRot, syncedMoobloomVisualYBodyRot) == 0) {
            return;
        }
        moobloomVisualEntity.setYRot(yRot);
        moobloomVisualEntity.setXRot(xRot);
        moobloomVisualEntity.setYHeadRot(yHeadRot);
        moobloomVisualEntity.setYBodyRot(yBodyRot);
        syncedMoobloomVisualYRot = yRot;
        syncedMoobloomVisualXRot = xRot;
        syncedMoobloomVisualYHeadRot = yHeadRot;
        syncedMoobloomVisualYBodyRot = yBodyRot;
    }

    private void syncMoobloomVisualPosition() {
        double x = getX();
        double y = getY();
        double z = getZ();
        if (sameMoobloomVisualPosition(x, y, z)) {
            return;
        }
        moobloomVisualEntity.teleportTo(x, y, z);
        syncedMoobloomVisualX = x;
        syncedMoobloomVisualY = y;
        syncedMoobloomVisualZ = z;
    }

    private boolean sameMoobloomVisualPosition(double x, double y, double z) {
        return Math.abs(x - syncedMoobloomVisualX) <= MOOBLOOM_VISUAL_POSITION_EPSILON
                && Math.abs(y - syncedMoobloomVisualY) <= MOOBLOOM_VISUAL_POSITION_EPSILON
                && Math.abs(z - syncedMoobloomVisualZ) <= MOOBLOOM_VISUAL_POSITION_EPSILON;
    }

    private boolean moobloomVisualPoseChanged() {
        return !sameMoobloomVisualPosition(getX(), getY(), getZ())
                || Float.compare(getYRot(), syncedMoobloomVisualYRot) != 0
                || Float.compare(getXRot(), syncedMoobloomVisualXRot) != 0
                || Float.compare(getYHeadRot(), syncedMoobloomVisualYHeadRot) != 0
                || Float.compare(yBodyRot, syncedMoobloomVisualYBodyRot) != 0;
    }

    private void discardMoobloomVisualEntity() {
        if (moobloomVisualEntity != null && !moobloomVisualEntity.isRemoved()) {
            moobloomVisualEntity.discard();
        }
        moobloomVisualEntity = null;
        resetMoobloomVisualSyncState();
    }

    private void resetMoobloomVisualSyncState() {
        syncedMoobloomVisualVariant = null;
        syncedMoobloomVisualName = null;
        syncedMoobloomVisualNameVisible = false;
        syncedMoobloomVisualX = Double.NaN;
        syncedMoobloomVisualY = Double.NaN;
        syncedMoobloomVisualZ = Double.NaN;
        syncedMoobloomVisualYRot = Float.NaN;
        syncedMoobloomVisualXRot = Float.NaN;
        syncedMoobloomVisualYHeadRot = Float.NaN;
        syncedMoobloomVisualYBodyRot = Float.NaN;
        moobloomVisualSyncDirty = true;
    }

    private void markMoobloomVisualSyncDirty() {
        moobloomVisualSyncDirty = true;
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
        double scale = visual == null ? EntityVisual.DEFAULT_SCALE : visual.scale();
        if (blockbenchModelId == null && polymerEntityType == EntityType.END_CRYSTAL) {
            scale = END_CRYSTAL_COLLISION_SCALE;
        } else if (usesMoobloomOverlayVisual()) {
            scale = MOOBLOOM_COLLISION_SCALE;
        }
        var scaleAttribute = getAttribute(Attributes.SCALE);
        scaleAttribute.setBaseValue(scale);
        if (runtimeTower instanceof EndTower endTower
                && endTower.state() == EndTowerState.PHANTOM) {
            getAttributes().getAttributesToSync().add(scaleAttribute);
        }
        refreshDimensions();
        if (holder != null) {
            holder.setScale(1.0F);
        }
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        if (usesOneBlockEndCoreHitbox()) {
            return EntityDimensions.fixed(END_CORE_HITBOX_WIDTH, END_CORE_HITBOX_HEIGHT);
        }
        if (usesBlockDisplayOverlayVisual()) {
            float scale = (float) visual.scale();
            return EntityDimensions.fixed(scale, scale);
        }
        return super.getDefaultDimensions(pose);
    }

    private void moveToward(Vec3 targetPosition, double speedModifier) {
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
        if (isBabyEndDragonBlockedByFriendlyTower(step)) {
            getNavigation().stop();
            getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0.0);
            setDeltaMovement(0.0, velocity.y, 0.0);
            playAnimation(SemionAnimationState.IDLE);
            return;
        }

        playAnimation(SemionAnimationState.WALK);
        getNavigation().moveTo(targetPosition.x, targetPosition.y, targetPosition.z, speedModifier);
        getMoveControl().setWantedPosition(targetPosition.x, targetPosition.y, targetPosition.z, speedModifier);
        getLookControl().setLookAt(targetPosition.x, targetPosition.y, targetPosition.z);
        move(MoverType.SELF, step);
        clampToFinalDefenseAreaIfNeeded();
        setDeltaMovement(0.0, velocity.y, 0.0);
    }

    private boolean isBabyEndDragonBlockedByFriendlyTower(Vec3 movement) {
        if (!(runtimeTower instanceof EndTower endTower)
                || !endTower.stopsBeforeFriendlyTowers()
                || movement == null
                || movement.lengthSqr() <= 0.0) {
            return false;
        }

        AABB nextPosition = getBoundingBox()
                .move(movement)
                .move(0.0, -1.0, 0.0);
        return !level().getEntitiesOfClass(
                SemionTowerEntity.class,
                nextPosition,
                this::isLivingFriendlyTower
        ).isEmpty();
    }

    private boolean isLivingFriendlyTower(SemionTowerEntity candidate) {
        return candidate != null
                && candidate != this
                && candidate.isAlive()
                && !candidate.isRemoved()
                && candidate.runtimeTower != null
                && candidate.runtimeTower.health() > 0.0
                && candidate.teamId == teamId
                && (candidate.laneId == laneId || finalDefense || candidate.finalDefense);
    }

    private void clampToFinalDefenseAreaIfNeeded() {
        if (!finalDefense || laneLayout == null || laneLayout.isInsideFinalDefenseTowerArea(position())) {
            return;
        }

        Vec3 clampedPosition = laneLayout.clampToFinalDefenseTowerArea(position());
        teleportTo(clampedPosition.x, clampedPosition.y, clampedPosition.z);
        getNavigation().stop();
    }

    private void syncEndCoreFlightPhysics() {
        boolean airborneEndCore = usesOneBlockEndCoreHitbox();
        setNoGravity(airborneEndCore);
        if (airborneEndCore) {
            Vec3 velocity = getDeltaMovement();
            setDeltaMovement(velocity.x, 0.0, velocity.z);
            fallDistance = 0.0F;
        }
    }

    private void applyLockedDragonRotation() {
        if (Float.isNaN(lockedDragonYaw)
                || !(runtimeTower instanceof EndTower endTower)
                || endTower.state() != EndTowerState.DRAGON) {
            return;
        }
        setYRot(lockedDragonYaw);
        setYHeadRot(lockedDragonYaw);
        yBodyRot = lockedDragonYaw;
        setXRot(lockedDragonPitch);
    }

    private static Vec3 towerAnchorPosition(Tower tower) {
        GridPosition position = tower.position();
        double airborneOffset = tower instanceof EndTower endTower
                && (endTower.state() == EndTowerState.PHANTOM || endTower.state() == EndTowerState.DRAGON)
                ? 1.0
                : 0.0;
        return new Vec3(position.x() + 0.5, position.y() + 1.0 + airborneOffset, position.z() + 0.5);
    }
}
