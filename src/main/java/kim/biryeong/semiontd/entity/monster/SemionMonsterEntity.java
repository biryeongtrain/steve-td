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
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
import kim.biryeong.semiontd.effect.TimedEffectSet;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.defender.LaneDefenseEntity;
import kim.biryeong.semiontd.entity.healing.HealingTarget;
import kim.biryeong.semiontd.entity.goal.NaturalWaveHealGoal;
import kim.biryeong.semiontd.entity.model.BilDeathVisual;
import kim.biryeong.semiontd.entity.model.SemionBilModelCache;
import kim.biryeong.semiontd.entity.monster.goal.AcquireLaneDefenseTargetGoal;
import kim.biryeong.semiontd.entity.monster.goal.LaneFollowGoal;
import kim.biryeong.semiontd.entity.monster.goal.MonsterAttackTargetGoal;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.summon.SummonRegistry;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.demonlord.DemonLordState;
import kim.biryeong.semiontd.tower.demonlord.DemonLordStates;
import kim.biryeong.semiontd.tower.legion.BeeStingPolicy;
import kim.biryeong.semiontd.trait.TraitEffects;
import kim.biryeong.semiontd.trait.TraitLoadout;
import kim.biryeong.semiontd.trait.TraitVfx;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;

public class SemionMonsterEntity extends PathfinderMob implements AnimatedEntity, HealingTarget, LaneDefenseEntity {
    private static final double DEFAULT_MELEE_RANGE = 2.5;
    private static final double DEFAULT_FOLLOW_RANGE = 5.0;
    private static final double DEFAULT_MOVEMENT_SPEED = 0.42;
    public static final double DEFENSE_SEARCH_HORIZONTAL_PADDING = 5.0;
    public static final double DEFENSE_TARGET_LEASH_RANGE = 8.0;
    /**
     * 이미 마왕을 노리던 몬스터가 표적을 놓지 않는 거리.
     *
     * <p>마왕 스킬(공포의 포효·악의 파동·하늘 부수기 등)은 몬스터를 6~7칸씩 날려 보냅니다. 붙잡는
     * 거리(8칸)로 놓아 버리면 날아간 몬스터가 마왕을 잊고 레인 앞쪽으로만 걸어 그대로 빠져나갑니다.
     * 그래서 한 번 잡은 마왕은 이 거리까지 계속 쫓아오게 합니다. 새로 붙잡는 거리는 그대로 8칸입니다.
     */
    public static final double DEMON_LORD_AGGRO_KEEP_RANGE = 16.0;
    private static final double DEFENSE_SEARCH_VERTICAL_PADDING = 3.0;
    private static final java.util.concurrent.atomic.AtomicInteger DOMINATED =
            new java.util.concurrent.atomic.AtomicInteger();

    private EntityType<?> polymerEntityType = net.minecraft.world.entity.EntityTypes.ZOMBIE;
    private Monster runtimeMonster;
    private LaneRegionLayout laneLayout;
    private String blockbenchModelId;
    private EntityDimensions runtimeDimensions = MonsterDimensions.DEFAULT.toEntityDimensions();
    private SemionAnimationState animationState = SemionAnimationState.IDLE;
    /** 공격·치유처럼 한 번 도는 동작이 끝나는 틱. 그 전에는 걷기·대기로 바뀌어도 멈추지 않습니다. */
    private int oneShotEndTick;
    private boolean deathVisualShown;
    private final List<Goal> summonAbilityGoals = new ArrayList<>();
    private NaturalWaveHealGoal waveAbilityGoal;
    private final TimedEffectSet timedEffects = new TimedEffectSet();
    private final kim.biryeong.semiontd.tower.magicschool.MagicSchoolMonsterSpells schoolSpells =
            new kim.biryeong.semiontd.tower.magicschool.MagicSchoolMonsterSpells(this);
    private IgniteState ignite;
    private final Map<Tower, BeePoisonState> beePoisons = new IdentityHashMap<>();
    private LivingEntityHolder<SemionMonsterEntity> holder;
    private EntityAttachment holderAttachment;
    /** 공격 방식(선딜·특수 타격). 없으면 공격을 트는 틱에 바로 한 번 때립니다. */
    private MonsterAttackStyle attackStyle;
    private LivingEntity pendingHitTarget;
    private int pendingHitTick = -1;
    /** 은신 유닛: 공격 중이 아니면 타워가 고를 수 없고 모델도 숨습니다. */
    private boolean stealthCapable;
    private int revealedUntilTick;
    private boolean stealthVisualApplied;
    private float visibleScale = 1.0F;
    /** 타워·마왕 어그로를 모두 무시하고 레인 끝의 보스만 노립니다. */
    private boolean ignoresDefenses;

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
        showDeathVisual();
        super.die(damageSource);
        if (runtimeMonster != null) {
            runtimeMonster.syncHealth(0.0);
        }
    }

    // ------------------------------------------------------------------ 지배(정원사)

    /** 지배당해 편을 바꾼 동안, 지키는 레인 id. -1이면 지배당하지 않았습니다. */
    private int dominatedLaneId = -1;
    private boolean dominatedCounted;

    public boolean isDominated() {
        return dominatedLaneId >= 0;
    }

    /**
     * 정원사에게 지배당해 그 레인 편이 됩니다({@code laneId}, -1이면 풀림). 지배당한 동안에는 원래 편 몹이 공격 대상으로
     * 고를 수 있고(방어 대상처럼 어그로를 끕니다), 아군 타워는 노리지 않습니다.
     */
    public void setDominatedFor(int laneId) {
        dominatedLaneId = laneId;
        countDominated(laneId >= 0 && !isRemoved());
        if (laneId >= 0) {
            setTarget(null);
        }
    }

    public static boolean anyDominated() {
        return DOMINATED.get() > 0;
    }

    private void countDominated(boolean dominated) {
        if (dominated != dominatedCounted) {
            dominatedCounted = dominated;
            DOMINATED.addAndGet(dominated ? 1 : -1);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        countDominated(false);
        super.remove(reason);
    }

    @Override
    public boolean defendsLane(int laneId) {
        return dominatedLaneId >= 0 && dominatedLaneId == laneId;
    }

    @Override
    public int aggroPriority() {
        return 60;
    }

    @Override
    public boolean drawsAggro() {
        return dominatedLaneId >= 0;
    }

    @Override
    protected void actuallyHurt(ServerLevel serverLevel, DamageSource damageSource, float amount) {
        if (damageSource.getEntity() instanceof ServerPlayer) {
            return;
        }
        // 지배당한 몹이 원래 편에게 맞으면 바닐라 체력이 아니라 런타임 체력이 깎입니다.
        if (isDominated() && damageSource.getEntity() instanceof SemionMonsterEntity && runtimeMonster != null) {
            applyRuntimeDamage(damageSource, amount, DamageType.PHYSICAL);
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
                : Component.literal(monster.displayName()));
        setCustomNameVisible(true);
        refreshSupportProgressName();
        setPolymerEntityType(monster.entityTypeId());
        syncAttributesFromRuntimeMonster();
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(followRangeFor(monster));
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(DEFAULT_MOVEMENT_SPEED);
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        setHealth((float) monster.health());
        installBilModel(blockbenchModelId);
        installSummonAbilityGoals();
        installWaveAbilityGoals();
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
        applyPermanentStatScale(factor, factor, minimumVisualScale);
    }

    public void applyPermanentStatScale(double factor, double healthFactor, double minimumVisualScale) {
        if (runtimeMonster == null) {
            return;
        }
        runtimeMonster.syncHealth(Math.min(runtimeMonster.health(), getHealth()));
        runtimeMonster.applyPermanentStatScale(factor, healthFactor, minimumVisualScale);
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
        tickPendingHit();
        tickStealthVisual();
        tickIgnite();
        tickBeePoisons();
        schoolSpells.tick();
        timedEffects.tick();
        if (runtimeMonster != null) {
            runtimeMonster.expireShields(level().getGameTime());
            if (tickCount % 20 == 0) {
                refreshSupportProgressName();
            }
        }

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
        if (runtimeMonster == null) {
            return true;
        }
        if (ignoresDefenses && (target instanceof ServerPlayer || target instanceof LaneDefenseEntity)) {
            return false;
        }
        double targetSearchRange = defenseTargetSearchRange();
        double leashRangeSqr = targetSearchRange * targetSearchRange;
        if (target instanceof ServerPlayer player) {
            DemonLordState state = DemonLordStates.get(player.getUUID());
            double keepRange = Math.max(DEMON_LORD_AGGRO_KEEP_RANGE, targetSearchRange);
            return state != null
                    && state.inCombat()
                    && state.canFight(runtimeMonster)
                    && (!onGround() || distanceToSqr(target) <= keepRange * keepRange);
        }
        if (!(target instanceof LaneDefenseEntity defenseEntity)) {
            return true;
        }
        return defenseEntity.defendsLane(runtimeMonster.targetLaneId()) && distanceToSqr(target) <= leashRangeSqr;
    }

    public void setAttackStyle(MonsterAttackStyle attackStyle) {
        this.attackStyle = attackStyle;
    }

    public MonsterAttackStyle attackStyle() {
        return attackStyle;
    }

    /**
     * 공격 한 번을 시작합니다. 공격 방식이 있으면 애니메이션에서 무기가 닿는 틱까지 기다렸다 때리고,
     * 없으면 예전처럼 바로 때립니다. 은신 유닛은 공격하는 동안 드러납니다.
     */
    public void startAttack(LivingEntity target) {
        if (isDisarmed() || schoolSpells.controlled()) return;
        playAnimation(SemionAnimationState.ATTACK);
        if (attackStyle == null) {
            MonsterAttackStyle.strike(this, target, attackDamageAmount());
            return;
        }
        if (pendingHitTick >= 0) {
            // 공격 속도가 올라 앞 공격의 타격 틱보다 먼저 다음 공격이 시작되면, 앞 타격을 지금 넣고 넘어갑니다.
            pendingHitTick = tickCount;
            tickPendingHit();
        }
        int delay = Math.max(0, attackStyle.hitDelayTicks());
        revealFor(delay + STEALTH_REVEAL_AFTER_ATTACK_TICKS);
        if (delay == 0) {
            attackStyle.hit(this, target);
            return;
        }
        pendingHitTarget = target;
        pendingHitTick = tickCount + delay;
    }

    public boolean hasPendingHit() {
        return pendingHitTick >= 0;
    }

    private void tickPendingHit() {
        if (pendingHitTick < 0 || tickCount < pendingHitTick) {
            return;
        }
        LivingEntity target = pendingHitTarget;
        pendingHitTarget = null;
        pendingHitTick = -1;
        // 휘두르는 도중 기절하면 헛손질입니다. 대상이 죽었으면 방식에 따라 주변만 맞을 수 있습니다.
        if (isAlive() && !isStunned() && !isDisarmed() && !schoolSpells.controlled() && attackStyle != null) {
            attackStyle.hit(this, target);
        }
    }

    /** 공격을 마친 은신 유닛이 다시 숨기까지의 틱. */
    public static final int STEALTH_REVEAL_AFTER_ATTACK_TICKS = 20;

    public void setStealthCapable(boolean stealthCapable) {
        this.stealthCapable = stealthCapable;
    }

    /** 은신 중이면 타워가 이 몬스터를 공격 대상으로 고를 수 없습니다(범위 공격에는 맞습니다). */
    /**
     * 한 플레이어에게만 보내는 발광 표시 패킷. Blockbench 모델이면 모델을 이루는 디스플레이마다 발광과 발광 색을,
     * 바닐라 모습이면 이 엔티티의 발광 비트를 바꿉니다(바닐라 모습의 색은 팀 색이라 호출하는 쪽이 팀 패킷을 따로 보냅니다).
     * 끌 때는 지금 값으로 되돌려, 원래 켜져 있던 발광이나 투명은 그대로 둡니다.
     */
    public List<net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket> markGlowPackets(boolean on, int color) {
        List<net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket> packets = new ArrayList<>();
        if (holder != null) {
            for (var element : holder.getElements()) {
                if (element instanceof eu.pb4.polymer.virtualentity.api.elements.DisplayElement display) {
                    byte flags = display.getSyncedData().get(eu.pb4.polymer.virtualentity.api.data.EntityData.FLAGS);
                    packets.add(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(display.getEntityId(), List.of(
                            net.minecraft.network.syncher.SynchedEntityData.DataValue.create(
                                    eu.pb4.polymer.virtualentity.api.data.EntityData.FLAGS,
                                    on ? (byte) (flags | 0x40) : flags),
                            net.minecraft.network.syncher.SynchedEntityData.DataValue.create(
                                    eu.pb4.polymer.virtualentity.api.data.DisplayEntityData.GLOW_COLOR_OVERRIDE,
                                    on ? color : display.getGlowColorOverride()))));
                }
            }
        } else {
            byte flags = entityData.get(DATA_SHARED_FLAGS_ID);
            packets.add(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(getId(), List.of(
                    net.minecraft.network.syncher.SynchedEntityData.DataValue.create(
                            DATA_SHARED_FLAGS_ID, on ? (byte) (flags | 0x40) : flags))));
        }
        return packets;
    }

    /** Blockbench 모델로 보이는지. 아니면 바닐라 모습(발광 색이 팀 색)입니다. */
    public boolean usesModelVisual() {
        return holder != null;
    }

    public boolean isStealthed() {
        return stealthCapable && isAlive() && tickCount >= revealedUntilTick;
    }

    public void revealFor(int ticks) {
        revealedUntilTick = Math.max(revealedUntilTick, tickCount + Math.max(0, ticks));
    }

    private void tickStealthVisual() {
        if (!stealthCapable || holder == null) {
            return;
        }
        boolean hidden = isStealthed();
        if (hidden == stealthVisualApplied) {
            return;
        }
        stealthVisualApplied = hidden;
        if (hidden) {
            visibleScale = holder.getScale();
            // 디스플레이는 투명도를 못 바꾸므로 모델을 아주 작게 줄여 숨깁니다. 이름표도 같이 감춥니다.
            holder.setScale(0.001F);
            setCustomNameVisible(false);
        } else {
            holder.setScale(visibleScale);
            setCustomNameVisible(true);
        }
        if (level() instanceof ServerLevel serverLevel) {
            kim.biryeong.semiontd.summon.invasion.InvasionVfx.stealthPuff(serverLevel, position(), hidden);
        }
    }

    public void setIgnoresDefenses(boolean ignoresDefenses) {
        this.ignoresDefenses = ignoresDefenses;
    }

    public boolean ignoresDefenses() {
        return ignoresDefenses;
    }

    /** 이 몬스터가 레인을 지키는 대상(타워·마왕)을 때릴 수 있는지. 사거리 제한 없이 범위 공격에 씁니다. */
    public boolean canDamageDefense(LivingEntity target) {
        if (runtimeMonster == null || target == null || !target.isAlive() || target.isRemoved() || ignoresDefenses) {
            return false;
        }
        if (target instanceof ServerPlayer player) {
            DemonLordState state = DemonLordStates.get(player.getUUID());
            return state != null && state.inCombat() && state.canFight(runtimeMonster);
        }
        return target instanceof LaneDefenseEntity defenseEntity && defenseEntity.defendsLane(runtimeMonster.targetLaneId());
    }

    public double defenseTargetSearchRange() {
        return Math.max(DEFENSE_TARGET_LEASH_RANGE, attackRange());
    }

    public void rewindLanePath(Vec3 destination, double progress) {
        getNavigation().stop();
        for (var goal : goalSelector.getAvailableGoals()) {
            if (goal.getGoal() instanceof LaneFollowGoal) {goal.stop();}
        }
        getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, 0);
        setDeltaMovement(Vec3.ZERO);
        teleportTo(destination.x, destination.y, destination.z);
        if (runtimeMonster != null) {runtimeMonster.syncLaneProgress(progress);}
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

    private void refreshSupportProgressName() {
        if (runtimeMonster == null || runtimeMonster.origin() != MonsterOrigin.NORMAL_PAID) {
            return;
        }
        var progress = kim.biryeong.semiontd.augment.AugmentEconomyService.supportProgress(runtimeMonster);
        if (progress.isEmpty()) {
            return;
        }
        String sender = runtimeMonster.senderName().orElse(null);
        TeamId team = runtimeMonster.senderTeam().orElse(null);
        var name = sender != null && team != null
                ? Component.literal(sender).withStyle(teamColor(team))
                : Component.literal(runtimeMonster.displayName());
        setCustomName(name.append(Component.literal(" · " + progress.get())));
    }

    public boolean applyRuntimeDamage(DamageSource damageSource, double amount, DamageType damageType) {
        return applySemionDamageResult(damageSource, amount, damageType).killed();
    }

    public AppliedDamageResult applySemionDamageResult(DamageSource damageSource, double amount, DamageType damageType) {
        if (damageType == DamageType.MAGIC) {
            amount *= 1.0 + activeTimedEffectMagnitude(TimedEffectType.MONSTER_LUMOS)
                    + activeTimedEffectMagnitude(TimedEffectType.MONSTER_CRUCIO_VULNERABILITY);
        }
        if (!Double.isFinite(amount) || amount <= 0.0) {
            return new AppliedDamageResult(false, 0.0, 0.0, 0.0);
        }
        if (runtimeMonster == null) {
            double previous = getHealth();
            hurt(damageSource, (float) amount);
            return new AppliedDamageResult(isRemoved() || !isAlive() || getHealth() <= 0.0F,
                    amount, Math.max(0.0, previous - getHealth()), 0.0);
        }

        runtimeMonster.syncHealth(Math.min(runtimeMonster.health(), getHealth()));
        runtimeMonster.expireShields(level().getGameTime());
        Monster.DamageResult result = runtimeMonster.damageResult(amount, damageType,
                activeTimedEffectMagnitude(TimedEffectType.MONSTER_ARMOR_REDUCTION));
        double appliedDamage = result.appliedDamage();
        if (appliedDamage <= 0.0) {
            return new AppliedDamageResult(runtimeMonster.isRemoved(), result.healthDamageAttempted(), 0.0, result.absorbedDamage());
        }

        hurt(damageSource, (float) appliedDamage);
        if (runtimeMonster.health() <= 0.0) {
            showDeathVisual();
            discard();
            return new AppliedDamageResult(true, result.healthDamageAttempted(), appliedDamage, result.absorbedDamage());
        }
        setHealth((float) runtimeMonster.health());
        return new AppliedDamageResult(false, result.healthDamageAttempted(), appliedDamage, result.absorbedDamage());
    }

    public record AppliedDamageResult(boolean killed, double healthDamageAttempted, double appliedDamage, double absorbedDamage) {
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
        if (runtimeMonster == null || !Double.isFinite(amount) || amount <= 0 || !runtimeMonster.isAlive()
                || runtimeMonster.health() <= 0.0 || !isAlive() || isRemoved()) {
            return false;
        }
        double before = runtimeMonster.health();
        runtimeMonster.heal(amount * Math.max(0, 1 - activeTimedEffectMagnitude(TimedEffectType.MONSTER_HEAL_REDUCTION)));
        if (runtimeMonster.health() <= before) {
            return false;
        }
        setHealth((float) runtimeMonster.health());
        return true;
    }

    @Override
    public boolean healTarget(HealingTarget target, double amount) {
        if (target == null) {
            return false;
        }
        if (runtimeMonster != null) {
            amount *= kim.biryeong.semiontd.augment.AugmentEconomyService.supportMultiplier(runtimeMonster);
        }
        double effective = target.receiveHealingAmount(amount);
        if (runtimeMonster != null) {
            runtimeMonster.supportMetrics().recordHealing(amount, effective);
            if (target instanceof SemionMonsterEntity monsterTarget && monsterTarget.runtimeMonster() != null) {
                kim.biryeong.semiontd.augment.AugmentEconomyService.recordSupport(runtimeMonster,
                        monsterTarget.runtimeMonster(), effective);
            }
        }
        return effective > 0.0;
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
        boolean oneShot = isOneShot(animationState);
        if (holder == null && animationState == SemionAnimationState.ATTACK && polymerEntityType == net.minecraft.world.entity.EntityTypes.CREAKING) {
            // 바닐라 모습의 크리킹은 엔티티 이벤트 4로 팔 휘두르기 동작을 봅니다.
            level().broadcastEntityEvent(this, (byte) 4);
        }
        if (holder != null && (this.animationState != animationState || oneShot)) {
            boolean oneShotRunning = tickCount < oneShotEndTick;
            for (SemionAnimationState state : SemionAnimationState.values()) {
                // 공격 직후 쿨다운 동안 대기·걷기로 돌아와도, 돌고 있는 공격·치유 동작은 끝까지 두어 위에 겹쳐 보이게 합니다.
                // 예전에는 바로 다음 틱에 멈춰서 공격 모션이 한 틱만 보였습니다.
                if (state == animationState || (isOneShot(state) && !oneShot && oneShotRunning)) {
                    continue;
                }
                holder.getAnimator().pauseAnimation(state.animationId());
            }
            holder.getAnimator().playAnimation(animationState.animationId(), oneShot ? 10 : 1, true);
            if (oneShot) {
                de.tomalbrc.bil.core.model.Animation animation = holder.getModel().animations().get(animationState.animationId());
                oneShotEndTick = tickCount + (animation == null ? 0 : animation.duration());
            }
        }
        this.animationState = animationState;
    }

    private static boolean isOneShot(SemionAnimationState state) {
        return state == SemionAnimationState.ATTACK || state == SemionAnimationState.HEAL;
    }

    /** 죽은 자리에 모델만 남겨 사망 애니메이션을 한 번 보여 줍니다(모델에 death가 있을 때). */
    public void showDeathVisual() {
        if (deathVisualShown || holder == null || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        deathVisualShown = true;
        BilDeathVisual.spawn(serverLevel, position(), yBodyRot, holder.getModel(), holder.getScale());
    }

    public double attackRange() {
        if (runtimeMonster == null) {
            return DEFAULT_MELEE_RANGE;
        }
        // 사거리 감소는 여기 한 곳만 지나갑니다. 공격 판정도 추적 상자도 이 값을 다시 물어보므로,
        // 원거리 몹을 밀어낸 뒤 다시 붙을 때까지 실제로 못 때리게 됩니다.
        double reduction = timedEffects.magnitude(TimedEffectType.MONSTER_ATTACK_RANGE_REDUCTION);
        return Math.max(0.0, runtimeMonster.attackRange() * Math.max(0.0, 1.0 - reduction));
    }

    public int attackIntervalTicks() {
        int baseInterval = runtimeMonster == null
                ? WaveMonsterEntry.DEFAULT_ATTACK_INTERVAL_TICKS
                : runtimeMonster.attackIntervalTicks();
        double speedBonus = timedEffects.magnitude(TimedEffectType.MONSTER_ATTACK_SPEED_BONUS);
        double speedReduction = activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION);
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

    public boolean isStunned() {
        return timedEffects.magnitude(TimedEffectType.MONSTER_STUN) > 0.0;
    }

    public boolean isDisarmed() {
        return timedEffects.magnitude(TimedEffectType.MONSTER_DISARM) > 0;
    }

    public kim.biryeong.semiontd.tower.magicschool.MagicSchoolMonsterSpells schoolSpells() {
        return schoolSpells;
    }

    public boolean isRooted() {
        return timedEffects.magnitude(TimedEffectType.MONSTER_ROOT) > 0.0;
    }

    @Override
    public void travel(Vec3 movementInput) {
        // Keep AI timers and forced motion (including Sky Breaker's lift) running.
        if (isRooted()) {
            setDeltaMovement(0, getDeltaMovement().y, 0);
            getNavigation().stop();
        }
        super.travel(isStunned() || isRooted() ? Vec3.ZERO : movementInput);
    }

    @Override
    public void setJumping(boolean jumping) {
        super.setJumping(jumping && !isStunned() && !isRooted());
    }

    public boolean applyTimedEffect(TimedEffectType type, Identifier sourceId, double magnitude, int durationTicks) {
        return timedEffects.apply(type, sourceId, magnitude, durationTicks);
    }

    public boolean refreshTimedEffect(TimedEffectType type, Identifier sourceId, double magnitude, int durationTicks) {
        return timedEffects.refresh(type, sourceId, magnitude, durationTicks);
    }

    public double activeTimedEffectMagnitude(TimedEffectType type) {
        double magnitude = timedEffects.magnitude(type);
        if (type == TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION) {
            double queen = timedEffects.persistentMagnitude(type,
                    kim.biryeong.semiontd.tower.queen.QueenShrink.SHRINK_DEBUFF_SOURCE)
                    + timedEffects.magnitude(type, kim.biryeong.semiontd.tower.queen.QueenShrink.GIANT_DEBUFF_SOURCE);
            magnitude -= Math.max(0.0, queen - 0.70);
        }
        return magnitude;
    }

    public void removeTimedEffect(TimedEffectType type) {
        timedEffects.remove(type);
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
                    ticksUntilDamage,
                    !AugmentCombat.allowsTriggers() || sourceTower != null && sourceTower.isTemporaryCopy()
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
                    ticksUntilDamage,
                    ignite.suppressAugmentTriggers()
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
                sting,
                !AugmentCombat.allowsTriggers() || sourceTower.isTemporaryCopy()
                        || previous != null && previous.suppressAugmentTriggers()
        ));
    }

    public boolean hasTimedEffectSource(TimedEffectType type, Identifier sourceId) {
        return timedEffects.hasSource(type, sourceId);
    }

    public boolean setPersistentEffect(TimedEffectType type, Identifier sourceId, double magnitude) {
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
                ticksUntilDamage,
                ignite.suppressAugmentTriggers()
        );
    }

    private void applyIgniteDamage(IgniteState state) {
        if (state.suppressAugmentTriggers() && AugmentCombat.allowsTriggers()) {
            AugmentCombat.runWithoutTriggers(() -> applyIgniteDamage(state));
            return;
        }
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
                    AugmentCombat.recordKillOrigin(state.sourceTower(), runtimeMonster);
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
                        result.state().orElseThrow(),
                        poison.suppressAugmentTriggers()
                ));
            }
        }
    }

    private void applyBeePoisonDamage(BeePoisonState poison, double outgoingDamage) {
        if (poison.suppressAugmentTriggers() && AugmentCombat.allowsTriggers()) {
            AugmentCombat.runWithoutTriggers(() -> applyBeePoisonDamage(poison, outgoingDamage));
            return;
        }
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
                AugmentCombat.recordKillOrigin(poison.sourceTower(), runtimeMonster);
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
            int ticksUntilDamage,
            boolean suppressAugmentTriggers
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
            BeeStingPolicy.State sting,
            boolean suppressAugmentTriggers
    ) {
    }

    @Override
    public void knockback(double strength, double x, double z,
            net.minecraft.world.damagesource.DamageSource source, float damage, boolean comesFromEffect) {
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

    private void installWaveAbilityGoals() {
        if (waveAbilityGoal != null) {
            goalSelector.removeGoal(waveAbilityGoal);
            waveAbilityGoal = null;
        }
        if (runtimeMonster.origin() == MonsterOrigin.NATURAL_WAVE && runtimeMonster.waveHealing() != null) {
            waveAbilityGoal = new NaturalWaveHealGoal(this);
            waveAbilityGoal.updateName();
            goalSelector.addGoal(3, waveAbilityGoal);
        }
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
            // 새 모델에는 아직 아무 애니메이션도 돌지 않습니다. playAnimation은 같은 상태면 건너뛰므로, 지금 상태(처음엔 idle)를
            // 여기서 바로 틀어야 가만히 선 몹이 기본 자세(팔을 늘어뜨리고 무기를 눕힌 모습)로 굳지 않습니다.
            holder.getAnimator().playAnimation(animationState.animationId(), 1, true);
        });
    }

    private void setPolymerEntityType(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isBlank()) {
            polymerEntityType = net.minecraft.world.entity.EntityTypes.ZOMBIE;
            return;
        }

        Identifier id = Identifier.tryParse(entityTypeId);
        if (id == null) {
            polymerEntityType = net.minecraft.world.entity.EntityTypes.ZOMBIE;
            return;
        }

        polymerEntityType = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(net.minecraft.world.entity.EntityTypes.ZOMBIE);
    }
}
