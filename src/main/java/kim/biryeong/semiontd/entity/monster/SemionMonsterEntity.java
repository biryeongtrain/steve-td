package kim.biryeong.semiontd.entity.monster;

import de.tomalbrc.bil.api.AnimatedEntity;
import de.tomalbrc.bil.api.AnimatedEntityHolder;
import de.tomalbrc.bil.core.holder.entity.living.LivingEntityHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
import kim.biryeong.semiontd.effect.TimedEffectSet;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.defender.LaneDefenseEntity;
import kim.biryeong.semiontd.entity.healing.HealingTarget;
import kim.biryeong.semiontd.entity.model.SemionBilModelCache;
import kim.biryeong.semiontd.entity.monster.goal.AcquireLaneDefenseTargetGoal;
import kim.biryeong.semiontd.entity.monster.goal.LaneFollowGoal;
import kim.biryeong.semiontd.entity.monster.goal.MonsterAttackTargetGoal;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.summon.SummonRegistry;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.legion.BeeStingPolicy;
import kim.biryeong.semiontd.trait.TraitEffects;
import kim.biryeong.semiontd.trait.TraitLoadout;
import kim.biryeong.semiontd.trait.TraitVfx;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
    private static final double DEFAULT_FOLLOW_RANGE = 5.0;
    private static final double DEFAULT_MOVEMENT_SPEED = 0.42;
    public static final double DEFENSE_SEARCH_HORIZONTAL_PADDING = 5.0;
    public static final double DEFENSE_TARGET_LEASH_RANGE = 8.0;
    private static final double DEFENSE_SEARCH_VERTICAL_PADDING = 3.0;

    private EntityType<?> polymerEntityType = EntityType.ZOMBIE;
    private Monster runtimeMonster;
    private LaneRegionLayout laneLayout;
    private String blockbenchModelId;
    private EntityDimensions runtimeDimensions = MonsterDimensions.DEFAULT.toEntityDimensions();
    private SemionAnimationState animationState = SemionAnimationState.IDLE;
    private final List<Goal> summonAbilityGoals = new ArrayList<>();
    private final TimedEffectSet timedEffects = new TimedEffectSet();
    private IgniteState ignite;
    private final Map<Tower, BeePoisonState> beePoisons = new IdentityHashMap<>();
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

    @Override
    protected void actuallyHurt(ServerLevel serverLevel, DamageSource damageSource, float amount) {
        if (damageSource.getEntity() instanceof ServerPlayer) {
            return;
        }
        super.actuallyHurt(serverLevel, damageSource, amount);
    }

    public void configureFrom(Monster monster, LaneRegionLayout laneLayout) {
        this.runtimeMonster = monster;
        this.laneLayout = laneLayout;
        this.blockbenchModelId = monster.blockbenchModelId().orElse(null);
        this.runtimeDimensions = monster.dimensions().toEntityDimensions();
        refreshDimensions();
        String senderName = monster.senderName().orElse(null);
        TeamId senderTeam = monster.senderTeam().orElse(null);
        setCustomName(senderName != null && senderTeam != null
                ? Component.literal(senderName).withStyle(teamColor(senderTeam))
                : Component.literal(monster.id()));
        setCustomNameVisible(true);
        setPolymerEntityType(monster.entityTypeId());
        syncAttributesFromRuntimeMonster();
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(followRangeFor(monster));
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(DEFAULT_MOVEMENT_SPEED);
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        setHealth((float) monster.health());
        installBilModel(blockbenchModelId);
        installSummonAbilityGoals();
        playAnimation(SemionAnimationState.IDLE);
    }

    private static ChatFormatting teamColor(TeamId teamId) {
        return switch (teamId) {
            case RED -> ChatFormatting.RED;
            case BLUE -> ChatFormatting.BLUE;
            case GREEN -> ChatFormatting.GREEN;
            case YELLOW -> ChatFormatting.YELLOW;
            case PURPLE -> ChatFormatting.LIGHT_PURPLE;
            case AQUA -> ChatFormatting.AQUA;
        };
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        double monsterScale = runtimeMonster == null ? 1.0 : runtimeMonster.visualScale();
        return runtimeDimensions.scale((float) (getAgeScale() * monsterScale));
    }

    /** Applies a persistent max-health, attack-damage, and entity-size reduction. */
    public void applyPermanentStatScale(double factor, double minimumVisualScale) {
        if (runtimeMonster == null) {
            return;
        }
        runtimeMonster.syncHealth(Math.min(runtimeMonster.health(), getHealth()));
        runtimeMonster.applyPermanentStatScale(factor, minimumVisualScale);
        syncAttributesFromRuntimeMonster();
        setHealth((float) runtimeMonster.health());
        refreshDimensions();
    }

    @Override
    public void aiStep() {
        if (isAlive() && level() instanceof ServerLevel serverLevel && getY() < serverLevel.getMinY()) {
            setHealth(0.0F);
            die(serverLevel.damageSources().fellOutOfWorld());
            return;
        }
        super.aiStep();
        tickIgnite();
        tickBeePoisons();
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

    public boolean canTargetDefense(LivingEntity target) {
        if (!(target instanceof LaneDefenseEntity defenseEntity) || runtimeMonster == null) {
            return true;
        }
        double targetSearchRange = defenseTargetSearchRange();
        double leashRangeSqr = targetSearchRange * targetSearchRange;
        return defenseEntity.defendsLane(runtimeMonster.targetLaneId()) && distanceToSqr(target) <= leashRangeSqr;
    }

    public double defenseTargetSearchRange() {
        return Math.max(DEFENSE_TARGET_LEASH_RANGE, attackRange());
    }

    public int nextPathPointIndex() {
        if (laneLayout == null) {
            return 0;
        }

        List<Vec3> points = pathPoints();
        if (points.isEmpty()) {
            return 0;
        }

        double currentProgress = laneLayout.progressAt(position());
        for (int i = 0; i < points.size(); i++) {
            if (laneLayout.progressAt(points.get(i)) + 0.0001 >= currentProgress) {
                return i;
            }
        }
        return points.size();
    }

    public Monster runtimeMonster() {
        return runtimeMonster;
    }

    public boolean applyRuntimeDamage(DamageSource damageSource, double amount, DamageType damageType) {
        if (amount <= 0.0) {
            return false;
        }
        if (runtimeMonster == null) {
            hurt(damageSource, (float) amount);
            return isRemoved() || !isAlive() || getHealth() <= 0.0F;
        }

        runtimeMonster.syncHealth(Math.min(runtimeMonster.health(), getHealth()));
        double previousHealth = runtimeMonster.health();
        runtimeMonster.damage(amount, damageType);
        double appliedDamage = previousHealth - runtimeMonster.health();
        if (appliedDamage <= 0.0) {
            return runtimeMonster.isRemoved();
        }

        hurt(damageSource, (float) appliedDamage);
        if (runtimeMonster.health() <= 0.0) {
            discard();
            return true;
        }
        setHealth((float) runtimeMonster.health());
        return false;
    }

    public void syncAttributesFromRuntimeMonster() {
        if (runtimeMonster == null) {
            return;
        }
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(runtimeMonster.maxHealth());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(runtimeMonster.attackDamage());
        var scale = getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(runtimeMonster.visualScale());
        }
    }

    @Override
    public boolean isHealingAlly(HealingTarget other) {
        if (!(other instanceof SemionMonsterEntity monsterEntity) || monsterEntity == this
                || runtimeMonster == null || monsterEntity.runtimeMonster == null) {
            return false;
        }
        return runtimeMonster.targetTeam() == monsterEntity.runtimeMonster.targetTeam()
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
        return runtimeMonster.attackRange();
    }

    public int attackIntervalTicks() {
        int baseInterval = runtimeMonster == null
                ? WaveMonsterEntry.DEFAULT_ATTACK_INTERVAL_TICKS
                : runtimeMonster.attackIntervalTicks();
        double speedBonus = timedEffects.magnitude(TimedEffectType.MONSTER_ATTACK_SPEED_BONUS);
        double speedReduction = timedEffects.magnitude(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION);
        return Math.max(1, (int) Math.ceil(baseInterval / Math.max(0.01, 1.0 + speedBonus - speedReduction)));
    }

    public double attackDamageAmount() {
        double baseDamage = getAttributeValue(Attributes.ATTACK_DAMAGE);
        double multiplier = 1.0
                + timedEffects.magnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS)
                - timedEffects.magnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION);
        return baseDamage * Math.max(0.0, multiplier);
    }

    public void applyTimedEffect(TimedEffectType type, double magnitude, int durationTicks) {
        timedEffects.apply(type, magnitude, durationTicks);
    }

    public boolean applyTimedEffect(TimedEffectType type, ResourceLocation sourceId, double magnitude, int durationTicks) {
        return timedEffects.apply(type, sourceId, magnitude, durationTicks);
    }

    public double activeTimedEffectMagnitude(TimedEffectType type) {
        return timedEffects.magnitude(type);
    }

    public int activeTimedEffectTicks(TimedEffectType type) {
        return timedEffects.remainingTicks(type);
    }

    public boolean hasDebuff() {
        if (ignite != null) {
            return true;
        }
        for (TimedEffectType type : TimedEffectType.values()) {
            if (type.isMonsterDebuff() && timedEffects.magnitude(type) > 0.0) {
                return true;
            }
        }
        return false;
    }

    public void applyIgnite(
            UUID sourcePlayer,
            Tower sourceTower,
            TraitLoadout sourceLoadout,
            double damagePerTick,
            double additiveTraitBonus,
            double finalTraitMultiplier,
            int durationTicks,
            int tickIntervalTicks
    ) {
        if (sourcePlayer == null || damagePerTick <= 0.0 || durationTicks <= 0 || tickIntervalTicks <= 0 || !isAlive()) {
            return;
        }
        int ticksUntilDamage = ignite == null
                ? tickIntervalTicks
                : Math.max(1, Math.min(ignite.ticksUntilDamage(), tickIntervalTicks));
        if (ignite == null || damagePerTick > ignite.damagePerTick()) {
            ignite = new IgniteState(
                    sourcePlayer,
                    sourceTower,
                    sourceLoadout == null ? TraitLoadout.none() : sourceLoadout,
                    damagePerTick,
                    additiveTraitBonus,
                    finalTraitMultiplier,
                    durationTicks,
                    ticksUntilDamage
            );
        } else {
            ignite = new IgniteState(
                    ignite.sourcePlayer(),
                    ignite.sourceTower(),
                    ignite.sourceLoadout(),
                    ignite.damagePerTick(),
                    ignite.additiveTraitBonus(),
                    ignite.finalTraitMultiplier(),
                    durationTicks,
                    ticksUntilDamage
            );
        }
        timedEffects.apply(TimedEffectType.MONSTER_IGNITED, 1.0, durationTicks);
        TraitVfx.showIgniteApplied(this);
    }

    public void applyBeePoison(
            UUID sourcePlayer,
            Tower sourceTower,
            double damagePerStack,
            int maxStacks,
            int durationTicks,
            int tickIntervalTicks
    ) {
        if (sourcePlayer == null || sourceTower == null || damagePerStack <= 0.0
                || durationTicks <= 0 || tickIntervalTicks <= 0 || !isAlive()) {
            return;
        }
        BeePoisonState previous = beePoisons.get(sourceTower);
        BeeStingPolicy.State sting = BeeStingPolicy.applySting(
                previous == null ? null : previous.sting(),
                maxStacks,
                durationTicks,
                tickIntervalTicks
        );
        beePoisons.put(sourceTower, new BeePoisonState(
                sourcePlayer,
                sourceTower,
                Math.max(0.0, damagePerStack),
                Math.max(1, tickIntervalTicks),
                sting
        ));
    }

    public boolean hasTimedEffectSource(TimedEffectType type, ResourceLocation sourceId) {
        return timedEffects.hasSource(type, sourceId);
    }

    public boolean setPersistentEffect(TimedEffectType type, ResourceLocation sourceId, double magnitude) {
        return timedEffects.setPersistent(type, sourceId, magnitude);
    }

    public double movementSpeedMultiplier() {
        double baseMultiplier = runtimeMonster == null ? 1.0 : runtimeMonster.movementSpeedMultiplier();
        double speedBonus = timedEffects.magnitude(TimedEffectType.MONSTER_MOVE_SPEED_BONUS);
        double speedReduction = timedEffects.magnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION);
        return Math.max(0.01, baseMultiplier * (1.0 + speedBonus - speedReduction));
    }

    public double towerDamageTaken(double baseDamage) {
        double damageReduction = timedEffects.magnitude(TimedEffectType.MONSTER_DAMAGE_REDUCTION);
        double damageTakenBonus = timedEffects.magnitude(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS);
        return Math.max(0.0, baseDamage) * Math.max(0.0, 1.0 - damageReduction + damageTakenBonus);
    }

    private void tickIgnite() {
        if (ignite == null) {
            return;
        }
        if (runtimeMonster == null || isRemoved() || !isAlive()) {
            ignite = null;
            return;
        }

        int remainingTicks = ignite.remainingTicks() - 1;
        int ticksUntilDamage = ignite.ticksUntilDamage() - 1;
        if (tickCount % 5 == 0) {
            TraitVfx.showIgniteActive(this);
        }
        if (ticksUntilDamage <= 0) {
            applyIgniteDamage(ignite);
            ticksUntilDamage = Math.max(1, TraitEffects.igniteTickIntervalTicks());
        }
        if (remainingTicks <= 0 || isRemoved() || !isAlive()) {
            ignite = null;
            return;
        }
        ignite = new IgniteState(
                ignite.sourcePlayer(),
                ignite.sourceTower(),
                ignite.sourceLoadout(),
                ignite.damagePerTick(),
                ignite.additiveTraitBonus(),
                ignite.finalTraitMultiplier(),
                remainingTicks,
                ticksUntilDamage
        );
    }

    private void applyIgniteDamage(IgniteState state) {
        TraitVfx.showIgniteTick(this);
        double conditionalBonus = TraitEffects.conditionalTargetDamageBonus(
                state.sourceLoadout(),
                runtimeMonster,
                hasDebuff()
        );
        double traitDamage = state.damagePerTick()
                * Math.max(0.0, 1.0 + state.additiveTraitBonus() + conditionalBonus)
                * state.finalTraitMultiplier();
        double damageAmount = towerDamageTaken(traitDamage);
        if (damageAmount <= 0.0) {
            return;
        }
        double previousHealth = runtimeMonster.health();
        boolean killed = applyRuntimeDamage(damageSources().onFire(), damageAmount, DamageType.MAGIC);
        double dealtDamage = Math.max(0.0, previousHealth - runtimeMonster.health());
        if (dealtDamage > 0.0) {
            if (state.sourceTower() != null) {
                state.sourceTower().recordDamageDealt(this, dealtDamage, DamageType.MAGIC);
                if (killed) {
                    state.sourceTower().recordKill();
                    state.sourceTower().onIgniteKill(this);
                }
            }
            runtimeMonster.recordLastHit(state.sourcePlayer(), KillSourceKind.TOWER);
        }
    }

    private void tickBeePoisons() {
        if (beePoisons.isEmpty()) {
            return;
        }
        if (runtimeMonster == null || isRemoved() || !isAlive()) {
            beePoisons.clear();
            return;
        }
        Iterator<Map.Entry<Tower, BeePoisonState>> iterator = beePoisons.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Tower, BeePoisonState> entry = iterator.next();
            BeePoisonState poison = entry.getValue();
            BeeStingPolicy.TickResult result = BeeStingPolicy.tick(
                    poison.sting(),
                    poison.damagePerStack(),
                    poison.tickIntervalTicks()
            );
            if (result.damage() > 0.0) {
                applyBeePoisonDamage(poison, result.damage());
            }
            if (result.state().isEmpty() || isRemoved() || !isAlive()) {
                iterator.remove();
            } else {
                entry.setValue(new BeePoisonState(
                        poison.sourcePlayer(),
                        poison.sourceTower(),
                        poison.damagePerStack(),
                        poison.tickIntervalTicks(),
                        result.state().orElseThrow()
                ));
            }
        }
    }

    private void applyBeePoisonDamage(BeePoisonState poison, double outgoingDamage) {
        double damageAmount = towerDamageTaken(outgoingDamage);
        if (damageAmount <= 0.0) {
            return;
        }
        double previousHealth = runtimeMonster.health();
        boolean killed = applyRuntimeDamage(damageSources().onFire(), damageAmount, DamageType.MAGIC);
        double dealtDamage = Math.max(0.0, previousHealth - runtimeMonster.health());
        if (dealtDamage > 0.0) {
            poison.sourceTower().recordDamageDealt(this, dealtDamage, DamageType.MAGIC);
            if (killed) {
                poison.sourceTower().recordKill();
            }
            runtimeMonster.recordLastHit(poison.sourcePlayer(), KillSourceKind.TOWER);
        }
    }

    private double followRangeFor(Monster monster) {
        return Math.max(DEFAULT_FOLLOW_RANGE, monster.attackRange() + 2.0);
    }

    private record IgniteState(
            UUID sourcePlayer,
            Tower sourceTower,
            TraitLoadout sourceLoadout,
            double damagePerTick,
            double additiveTraitBonus,
            double finalTraitMultiplier,
            int remainingTicks,
            int ticksUntilDamage
    ) {
        private IgniteState {
            damagePerTick = Math.max(0.0, damagePerTick);
            additiveTraitBonus = Math.max(0.0, additiveTraitBonus);
            finalTraitMultiplier = Math.max(0.0, finalTraitMultiplier);
            remainingTicks = Math.max(0, remainingTicks);
            ticksUntilDamage = Math.max(1, ticksUntilDamage);
        }
    }

    private record BeePoisonState(
            UUID sourcePlayer,
            Tower sourceTower,
            double damagePerStack,
            int tickIntervalTicks,
            BeeStingPolicy.State sting
    ) {
    }

    @Override
    public void knockback(double strength, double x, double z) {
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
