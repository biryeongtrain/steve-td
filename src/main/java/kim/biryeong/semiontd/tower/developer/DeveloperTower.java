package kim.biryeong.semiontd.tower.developer;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

/**
 * Runtime tower for the 개발자 family.
 *
 * <p>Base stats here are ordinary — close to the live medians for their tier. Everything that makes
 * the family strong is layered on at query time from {@link DeveloperTowerData}: accumulated
 * patches, whichever of the 25 defects landed on this particular tower, and the permanent
 * optimisation trades. None of it is ever written back into the shared balance runtime, so one
 * player's patches cannot leak into another lane.
 *
 * <p>Two mechanics deliberately reuse a single existing lever. Instability stalls, 널 포인터 and
 * 긴급 점검 all express "this tower does nothing right now" by returning a zero attack range:
 * {@code TowerAttackMonsterGoal.tick} bails out on {@code attackRange() <= 0.0} and already handles
 * clearing the target, stopping navigation and dropping to the idle animation.
 */
public class DeveloperTower extends ProductionTower {
    /** Ticks the residual 오버킬 field keeps pulsing after a kill. */
    private static final int OVERKILL_DURATION_TICKS = 40;

    /** How long 지연 로딩 stays in its weak phase. */
    private static final int LAZY_LOADING_WARMUP_TICKS = 200;

    /** Window 은신 uses to decide the tower has been left alone. */
    private static final int STEALTH_QUIET_TICKS = 60;

    /** Extra ticks 캐시 미스 adds to the first attack after switching target. */
    private static final int CACHE_MISS_PENALTY_TICKS = 10;

    private boolean waveActive;
    private int waveTicks;
    private int currentRoundNumber;

    /** Rolled once per wave; a tower that failed the roll is dead weight for the whole wave. */
    private boolean nullPointerDown;

    /** Instability stall, also rolled per wave. */
    private int stallTicksRemaining;
    private boolean stallRolled;

    /** 첫 공격 헛방 consumes one swing at the start of every wave. */
    private boolean firstMissPending;

    /** 무한 루프 holds this target until it dies. */
    private UUID lockedTargetId;

    /** 캐시 미스 needs to know when the target changed. */
    private UUID lastTargetId;
    private int cacheMissTicks;

    /** 은신 pays out only while nothing has touched the tower. */
    private long lastDamagedGameTick = Long.MIN_VALUE;
    private long gameTick;

    /** 예외 처리 fires once and then halves this tower for the rest of the wave. */
    private boolean exceptionTriggeredThisWave;

    /** 가비지 컬렉션 fires once per wave when a patch can pay for the recovery. */
    private boolean garbageCollectionTriggeredThisWave;

    /** 좀비 프로세스 grace period once health hits zero. */
    private int zombieTicksRemaining;
    private boolean zombieConsumedThisRound;

    /** 버퍼 오버런 reads the candidate list captured during target selection. */
    private int lastCandidateCount;

    /** 오버킬 residual field. */
    private Vec3 overkillPosition;
    private int overkillTicksRemaining;

    /**
     * Kept so the residual field can still name a source after the target is gone.
     *
     * <p>{@link MonsterAreaEffectRequest} requires a non-null tower entity and {@code execute}
     * receives only the lane, so the reference has to be captured while the tower is alive.
     */
    private SemionTowerEntity spawnedEntity;

    private RandomSource random = RandomSource.create();

    public DeveloperTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public DeveloperTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    /**
     * Replaces the random source.
     *
     * <p>Bug rolls, 정확도 misses and instability stalls all draw from here. Tests and GameTests
     * need this deterministic, and retrofitting an injection point after the fact would mean
     * touching every call site.
     */
    public void useRandom(RandomSource source) {
        if (source != null) {
            this.random = source;
        }
    }

    public RandomSource random() {
        return random;
    }

    SemionTowerEntity spawnedEntity() {
        return spawnedEntity;
    }

    @Override
    protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) {
        super.configureEntityAfterSpawn(entity, lane);
        this.spawnedEntity = entity;
    }

    // ------------------------------------------------------------------ 슬롯과 어그로

    /**
     * Ability towers cost nothing to keep.
     *
     * <p>This is the single most important balance decision in the family. With ability towers on
     * the normal one-slot budget the builder spends three of its opening five slots on things that
     * cannot attack, which put it around 40% of everyone else's damage. Diamonds are the real cost
     * instead — the full ability set runs 620.
     */
    @Override
    public int slotWeight() {
        if (DeveloperTowers.isAbilityTower(type())) {
            return 0;
        }
        return 1 + (hasOptimization(DeveloperOptimization.SLOT) ? DeveloperOptimization.SLOT.extraSlotWeight() : 0);
    }

    @Override
    public int aggroPriority() {
        int base = super.aggroPriority();
        base += (int) Math.round(DeveloperTowerData.activeAmount(this, DeveloperPatch.AGGRO));
        if (hasBug(DeveloperBug.AGGRO_STORM)) {
            base += (int) Math.round(DeveloperBug.AGGRO_STORM.primary());
        }
        if (hasBug(DeveloperBug.STEALTH)) {
            // Stored positive because the balance file forbids negative values; 은신 lowers aggro.
            base -= (int) Math.round(DeveloperBug.STEALTH.primary());
        }
        if (hasOptimization(DeveloperOptimization.ATTACK)) {
            base += 40;
        }
        return clampAggro(base);
    }

    /** Ability towers are bookkeeping, not defence: a lane holding only them counts as fallen. */
    @Override
    public boolean countsForLaneDefense() {
        return !DeveloperTowers.isAbilityTower(type());
    }

    @Override
    public boolean canReceiveAllyHealing() {
        return !hasBug(DeveloperBug.SIGN_FLIP);
    }

    // ------------------------------------------------------------------ 사거리와 간격

    @Override
    public double adjustAttackRange(double baseRange) {
        if (DeveloperTowers.isAbilityTower(type())) {
            return 0.0;
        }
        if (isOffline()) {
            return 0.0;
        }
        double range = baseRange * (1.0 + DeveloperTowerData.activeAmount(this, DeveloperPatch.RANGE));
        if (hasOptimization(DeveloperOptimization.RANGE)) {
            range *= DeveloperOptimization.RANGE.costMultiplier();
        }
        if (hasOptimization(DeveloperOptimization.JUDGEMENT)) {
            range *= 1.0 + DeveloperOptimization.JUDGEMENT.gain();
        }
        if (hasBug(DeveloperBug.PRIMITIVE)) {
            range *= DeveloperBug.PRIMITIVE.primary();
        }
        return Math.max(0.0, range);
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        // Divide rather than subtract. Subtracting a sum that converges on 1.0 saturates — with the
        // shipped rates the accumulated value passes 100% and the interval would go negative, only
        // to be clamped to one tick for a sixteenfold damage spike. Dividing approaches a finite
        // ceiling instead, so stacking 연사 has diminishing returns rather than a cliff.
        double interval = baseIntervalTicks / (1.0 + DeveloperTowerData.activeAmount(this, DeveloperPatch.FIRE_RATE));
        if (hasOptimization(DeveloperOptimization.RANGE)) {
            interval /= 1.0 + DeveloperOptimization.RANGE.gain();
        }
        if (hasOptimization(DeveloperOptimization.FIRE_RATE)) {
            interval *= DeveloperOptimization.FIRE_RATE.costMultiplier();
        }
        if (hasBug(DeveloperBug.TIMEOUT)) {
            interval *= 1.0 + DeveloperBug.TIMEOUT.primary();
        }
        if (hasBug(DeveloperBug.MEMORY_LEAK)) {
            double perRound = DeveloperBug.MEMORY_LEAK.primary();
            double cap = DeveloperBug.MEMORY_LEAK.secondary();
            interval *= 1.0 + Math.min(cap, perRound * DeveloperTowerData.leakRounds(this));
        }
        interval += cacheMissTicks;
        return Math.max(minimumAttackIntervalTicks(), (int) Math.round(interval));
    }

    // ------------------------------------------------------------------ 체력

    /**
     * Max health including patches and permanent trades.
     *
     * <p>Overriding {@code effectBaseMaxHealth} rather than only calling {@code syncMaxHealth} keeps
     * timed effects rebasing against the patched value instead of the catalog value, so a shield
     * buff on a heavily patched tower is not silently reverted when it expires.
     */
    @Override
    public double effectBaseMaxHealth() {
        double base = type().maxHealth() * (1.0 + DeveloperTowerData.activeAmount(this, DeveloperPatch.HEALTH));
        if (hasOptimization(DeveloperOptimization.DURABILITY)) {
            base *= DeveloperOptimization.DURABILITY.costMultiplier();
        }
        if (hasOptimization(DeveloperOptimization.ATTACK)) {
            base *= 1.0 + DeveloperOptimization.ATTACK.gain();
        }
        if (hasOptimization(DeveloperOptimization.SLOT)) {
            base *= 1.0 + DeveloperOptimization.SLOT.gain();
        }
        return Math.max(1.0, base);
    }

    /** Call after anything changes the health multiplier so the entity picks the new value up. */
    public void resyncHealth(PlayerLane lane, boolean healIncrease) {
        syncMaxHealth(effectBaseMaxHealth(), healIncrease);
        onStateChanged(lane);
    }

    // ------------------------------------------------------------------ 피해

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        if (towerEntity != null) {
            spawnedEntity = towerEntity;
        }
        if (firstMissPending && hasBug(DeveloperBug.FIRST_MISS)) {
            firstMissPending = false;
            return 0.0;
        }
        if (hasOptimization(DeveloperOptimization.ACCURACY)
                && random.nextDouble() < DeveloperOptimization.ACCURACY.cost()) {
            return 0.0;
        }

        double damage = damageAmount * (1.0 + DeveloperTowerData.activeAmount(this, DeveloperPatch.ATTACK));
        damage *= optimizationDamageMultiplier();
        damage *= bugDamageMultiplier(towerEntity, target);
        damage *= situationalDamageMultiplier();
        return Math.max(0.0, damage);
    }

    private double optimizationDamageMultiplier() {
        double multiplier = 1.0;
        if (hasOptimization(DeveloperOptimization.DURABILITY)) {
            multiplier *= 1.0 + DeveloperOptimization.DURABILITY.gain();
        }
        if (hasOptimization(DeveloperOptimization.FIRE_RATE)) {
            multiplier *= 1.0 + DeveloperOptimization.FIRE_RATE.gain();
        }
        if (hasOptimization(DeveloperOptimization.ACCURACY)) {
            multiplier *= 1.0 + DeveloperOptimization.ACCURACY.gain();
        }
        if (hasOptimization(DeveloperOptimization.ATTACK)) {
            multiplier *= DeveloperOptimization.ATTACK.costMultiplier();
        }
        if (hasOptimization(DeveloperOptimization.SLOT)) {
            multiplier *= 1.0 + DeveloperOptimization.SLOT.gain();
        }
        return multiplier;
    }

    private double bugDamageMultiplier(SemionTowerEntity towerEntity, SemionMonsterEntity target) {
        double multiplier = 1.0;
        if (hasBug(DeveloperBug.PRIMITIVE)) {
            multiplier *= DeveloperBug.PRIMITIVE.secondary();
        }
        if (hasBug(DeveloperBug.TIMEOUT)) {
            multiplier *= 1.0 + DeveloperBug.TIMEOUT.secondary();
        }
        if (hasBug(DeveloperBug.FLOATING_POINT)) {
            double spread = DeveloperBug.FLOATING_POINT.primary();
            multiplier *= 1.0 + (random.nextDouble() * 2.0 - 1.0) * spread;
        }
        if (hasBug(DeveloperBug.BOUNDARY) && towerEntity != null && target != null) {
            double edge = adjustAttackRange(type().range()) * DeveloperBug.BOUNDARY.primary();
            if (towerEntity.distanceTo(target) >= edge) {
                multiplier *= DeveloperBug.BOUNDARY.secondary();
            }
        }
        if (hasBug(DeveloperBug.BUFFER_OVERRUN)) {
            if (lastCandidateCount >= 5) {
                multiplier *= 1.0 + DeveloperBug.BUFFER_OVERRUN.primary();
            } else if (lastCandidateCount <= 2) {
                multiplier *= 1.0 - DeveloperBug.BUFFER_OVERRUN.secondary();
            }
        }
        if (hasBug(DeveloperBug.HARDCODED) && target != null) {
            String latched = DeveloperTowerData.hardcodedType(this);
            String current = monsterId(target);
            if (!latched.isEmpty() && !current.isEmpty()) {
                multiplier *= latched.equals(current)
                        ? DeveloperBug.HARDCODED.primary()
                        : DeveloperBug.HARDCODED.secondary();
            }
        }
        if (hasBug(DeveloperBug.STEALTH)
                && (lastDamagedGameTick == Long.MIN_VALUE
                || gameTick - lastDamagedGameTick >= STEALTH_QUIET_TICKS)) {
            multiplier *= 1.0 + DeveloperBug.STEALTH.secondary();
        }
        if (hasBug(DeveloperBug.EXCEPTION_HANDLING) && exceptionTriggeredThisWave) {
            multiplier *= 1.0 - DeveloperBug.EXCEPTION_HANDLING.secondary();
        }
        if (hasBug(DeveloperBug.ZOMBIE_PROCESS) && zombieTicksRemaining > 0) {
            multiplier *= DeveloperBug.ZOMBIE_PROCESS.secondary();
        }
        if (hasBug(DeveloperBug.LAZY_LOADING)) {
            multiplier *= waveTicks < LAZY_LOADING_WARMUP_TICKS
                    ? DeveloperBug.LAZY_LOADING.primary()
                    : DeveloperBug.LAZY_LOADING.secondary();
        }
        return multiplier;
    }

    private double situationalDamageMultiplier() {
        return DeveloperTowerData.hasMaintenanceBonus(this, currentRoundNumber)
                ? 1.0 + DeveloperBalance.maintenanceDamageBonus()
                : 1.0;
    }

    /**
     * 정수 오버플로 lands here rather than in {@link #modifyAttackDamage} on purpose.
     *
     * <p>The cap has to be checked against the number that actually leaves the tower, after every
     * other multiplier. Checking earlier would let a later bonus push the total past the ceiling
     * without ever tripping it, which is exactly the interaction with 공속 포기 that makes this
     * defect interesting.
     */
    @Override
    public double modifyResolvedAttackDamage(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double damageAmount
    ) {
        if (!hasBug(DeveloperBug.INTEGER_OVERFLOW)) {
            return damageAmount;
        }
        double ceiling = type().damage() * DeveloperBug.INTEGER_OVERFLOW.primary();
        if (damageAmount <= ceiling) {
            return damageAmount;
        }
        return Math.max(0.0, type().damage() * DeveloperBug.INTEGER_OVERFLOW.secondary());
    }

    @Override
    public double modifyIncomingDamage(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double damageAmount
    ) {
        double damage = damageAmount;
        if (hasBug(DeveloperBug.SIGN_FLIP)) {
            damage *= 1.0 - DeveloperBug.SIGN_FLIP.primary();
        }
        if (hasBug(DeveloperBug.EXCEPTION_HANDLING) && !exceptionTriggeredThisWave) {
            double threshold = currentMaxHealth() * DeveloperBug.EXCEPTION_HANDLING.primary();
            if (damage >= threshold && threshold > 0.0) {
                exceptionTriggeredThisWave = true;
                damage = 1.0;
            }
        }
        if (waveActive
                && hasBug(DeveloperBug.GARBAGE_COLLECTION)
                && !garbageCollectionTriggeredThisWave
                && health() - damage <= currentMaxHealth() * DeveloperBug.GARBAGE_COLLECTION.primary()
                && DeveloperTowerData.dropOneActivePatch(this)) {
            garbageCollectionTriggeredThisWave = true;
            syncMaxHealth(effectBaseMaxHealth(), false);
            syncHealth(currentMaxHealth());
            if (towerEntity != null) {
                spawnedEntity = towerEntity;
                towerEntity.syncTowerState(this);
            }
            DeveloperVfx.show(this, AreaVfxStyles.PULSE, "garbage_collection");
            return 0.0;
        }
        return damage;
    }

    @Override
    public void onDamaged(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double damageAmount,
            double previousHealth,
            double currentHealth
    ) {
        super.onDamaged(towerEntity, damageSource, damageAmount, previousHealth, currentHealth);
        lastDamagedGameTick = gameTick;
        if (towerEntity != null) {
            spawnedEntity = towerEntity;
        }
    }

    // ------------------------------------------------------------------ 타겟 선정

    @Override
    public Optional<SemionMonsterEntity> selectAttackTarget(
            SemionTowerEntity towerEntity,
            List<SemionMonsterEntity> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            lastCandidateCount = 0;
            return Optional.empty();
        }
        lastCandidateCount = candidates.size();

        if (hasBug(DeveloperBug.INFINITE_LOOP) && lockedTargetId != null) {
            for (SemionMonsterEntity candidate : candidates) {
                if (lockedTargetId.equals(candidate.getUUID())) {
                    return Optional.of(candidate);
                }
            }
            lockedTargetId = null;
        }

        Optional<SemionMonsterEntity> chosen = chooseByBug(towerEntity, candidates);
        if (chosen.isEmpty() && hasOptimization(DeveloperOptimization.JUDGEMENT) && towerEntity != null) {
            chosen = candidates.stream().min(Comparator.comparingDouble(towerEntity::distanceToSqr));
        }
        chosen.ifPresent(target -> {
            if (hasBug(DeveloperBug.INFINITE_LOOP)) {
                lockedTargetId = target.getUUID();
            }
        });
        return chosen;
    }

    private Optional<SemionMonsterEntity> chooseByBug(
            SemionTowerEntity towerEntity,
            List<SemionMonsterEntity> candidates
    ) {
        if (hasBug(DeveloperBug.AGGRO_INVERSION)) {
            return candidates.stream().max(Comparator.comparingDouble(SemionMonsterEntity::getHealth));
        }
        if (hasBug(DeveloperBug.REVERSE_SORT) && towerEntity != null) {
            return candidates.stream().max(Comparator.comparingDouble(towerEntity::distanceToSqr));
        }
        if (hasBug(DeveloperBug.INFINITE_LOOP)) {
            return candidates.stream().findFirst();
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ 공격 후처리

    @Override
    public void onAttackResolved(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double attemptedDamage,
            double resolvedOutgoingDamage,
            double dealtDamage,
            boolean killedTarget
    ) {
        super.onAttackResolved(towerEntity, target, attemptedDamage, resolvedOutgoingDamage, dealtDamage, killedTarget);
        if (towerEntity != null) {
            spawnedEntity = towerEntity;
        }
        if (hasBug(DeveloperBug.HARDCODED) && target != null) {
            DeveloperTowerData.latchHardcodedType(this, monsterId(target));
        }
        if (hasBug(DeveloperBug.CACHE_MISS) && target != null) {
            UUID targetId = target.getUUID();
            cacheMissTicks = targetId.equals(lastTargetId) ? 0 : (int) DeveloperBug.CACHE_MISS.primary();
            lastTargetId = targetId;
        }
        if (killedTarget) {
            if (hasBug(DeveloperBug.INFINITE_LOOP)) {
                lockedTargetId = null;
            }
            if (hasBug(DeveloperBug.OVERKILL) && target != null) {
                overkillPosition = target.position();
                overkillTicksRemaining = (int) DeveloperBug.OVERKILL.primary();
            }
        }
    }

    /**
     * Drives the 오버킬 residual field.
     *
     * <p>{@code ProductionTower} inherits an empty {@code execute}, so overriding it costs nothing.
     * The residual is a countdown plus repeated one-shot area damage rather than a persistent
     * ground effect — {@link MonsterAreaEffectRequest} accepts an arbitrary centre, so the recorded
     * death position works directly and no new pipeline is needed.
     */
    @Override
    protected boolean execute(PlayerLane lane) {
        if (overkillTicksRemaining <= 0 || overkillPosition == null || spawnedEntity == null) {
            return false;
        }
        double radius = Math.max(0.5, DeveloperBug.OVERKILL.secondary() * 2.0);
        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                AreaEffectIds.tower(this, "overkill"),
                spawnedEntity,
                overkillPosition,
                radius,
                Set.of(),
                monster -> true,
                AreaVfxSpec.onTrigger(AreaVfxStyles.CORPSE_EXPLOSION)
        );
        double share = DeveloperBug.OVERKILL.secondary();
        TowerAreaDamage.apply(this, spawnedEntity, request, monster -> type().damage() * share, true);
        return true;
    }

    /**
     * Paces the residual field independently of the tower's own fire rate.
     *
     * <p>Without this the residual would inherit {@code attackIntervalTicks}, and a slow tower —
     * exactly the kind that benefits from 오버킬 — would only get one or two pulses out of it.
     */
    @Override
    protected int cooldownTicksAfterExecute(PlayerLane lane) {
        return overkillTicksRemaining > 0 ? 5 : super.cooldownTicksAfterExecute(lane);
    }

    // ------------------------------------------------------------------ 틱과 라운드

    @Override
    public void tick(PlayerLane lane) {
        gameTick++;
        if (waveActive) {
            waveTicks++;
        }
        if (stallTicksRemaining > 0) {
            stallTicksRemaining--;
            if (stallTicksRemaining == 0) {
                // The entity caches attackRange at sync time rather than asking every tick, so a
                // stall that simply runs out would leave the tower switched off for the rest of the
                // wave. Push a resync the moment it lifts.
                onStateChanged(lane);
            }
        }
        if (cacheMissTicks > 0) {
            cacheMissTicks--;
        }
        if (overkillTicksRemaining > 0) {
            overkillTicksRemaining--;
            if (overkillTicksRemaining == 0) {
                overkillPosition = null;
            }
        }
        if (zombieTicksRemaining > 0) {
            zombieTicksRemaining--;
        }
        super.tick(lane);
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        super.onWaveStarted(lane, currentRound);
        currentRoundNumber = currentRound;
        waveActive = true;
        waveTicks = 0;
        exceptionTriggeredThisWave = false;
        garbageCollectionTriggeredThisWave = false;
        zombieConsumedThisRound = false;
        firstMissPending = hasBug(DeveloperBug.FIRST_MISS);
        lockedTargetId = null;
        lastTargetId = null;
        cacheMissTicks = 0;

        // Reviewed patches queued last round take hold now, which is the whole point of the
        // 정식 패치 / 핫픽스 split.
        if (DeveloperTowerData.promotePendingPatches(this)) {
            resyncHealth(lane, true);
        }

        rollWaveFailures();
        onStateChanged(lane);
    }

    /**
     * Rolls the two "this tower is simply gone this wave" outcomes.
     *
     * <p>널 포인터 and an instability stall share the same expression — a zero attack range — but
     * differ in shape: the defect takes the whole wave, instability takes a few seconds. Rolling
     * both once at wave start keeps them cheap and, more importantly, deterministic per wave so a
     * player can read the outcome instead of being nibbled at random.
     */
    private void rollWaveFailures() {
        nullPointerDown = hasBug(DeveloperBug.NULL_POINTER)
                && random.nextDouble() < DeveloperBug.NULL_POINTER.primary();

        stallRolled = false;
        stallTicksRemaining = 0;
        int instability = DeveloperTowerData.instability(this);
        if (instability > 0) {
            double chance = Math.min(1.0, instability * DeveloperBalance.instabilityStallChance());
            if (random.nextDouble() < chance) {
                stallTicksRemaining = DeveloperBalance.instabilityStallTicks();
                stallRolled = true;
            }
        }
    }

    @Override
    public void onLaneCleared(PlayerLane lane) {
        super.onLaneCleared(lane);
        waveActive = false;
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        waveActive = false;
        waveTicks = 0;
        nullPointerDown = false;
        stallTicksRemaining = 0;
        overkillTicksRemaining = 0;
        overkillPosition = null;
        zombieTicksRemaining = 0;
        garbageCollectionTriggeredThisWave = false;

        if (hasBug(DeveloperBug.MEMORY_LEAK) && !DeveloperTowerData.underMaintenance(this, currentRoundNumber)) {
            DeveloperTowerData.advanceLeak(this);
        }
        super.resetForRound(lane);
    }

    /**
     * 좀비 프로세스 buys a few seconds at zero health.
     *
     * <p>Once per round only. Without that limit a tower on the wall would flicker in and out of
     * the grace period every time it was healed for a sliver and knocked down again.
     */
    @Override
    public boolean isDestroyed(PlayerLane lane) {
        boolean destroyed = super.isDestroyed(lane);
        if (!destroyed || !hasBug(DeveloperBug.ZOMBIE_PROCESS)) {
            return destroyed;
        }
        if (zombieTicksRemaining > 0) {
            return false;
        }
        if (!zombieConsumedThisRound) {
            zombieConsumedThisRound = true;
            zombieTicksRemaining = (int) DeveloperBug.ZOMBIE_PROCESS.primary();
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ 판매

    @Override
    public long sellRefundAmount() {
        if (hasBug(DeveloperBug.PRICE_TAG)) {
            return Math.round(paidMineralCost() * DeveloperBug.PRICE_TAG.primary());
        }
        return super.sellRefundAmount();
    }

    // ------------------------------------------------------------------ 상태 조회

    /** True while the tower is switched off for any reason. Expressed as a zero attack range. */
    public boolean isOffline() {
        return nullPointerDown
                || stallTicksRemaining > 0
                || DeveloperTowerData.underMaintenance(this, currentRoundNumber);
    }

    public boolean stalledByInstability() {
        return stallRolled;
    }

    public int currentRoundNumber() {
        return currentRoundNumber;
    }

    public boolean hasBug(DeveloperBug bug) {
        return DeveloperTowerData.hasBug(this, bug);
    }

    public boolean hasOptimization(DeveloperOptimization optimization) {
        return DeveloperTowerData.hasOptimization(this, optimization);
    }

    /**
     * Patch efficiency for this tower, including the 테스트 빌드 aura and 읽기 전용.
     *
     * <p>Read at application time, not at query time: the resolved amount is baked into the tower
     * data so a patch keeps the value it shipped with.
     */
    public double patchEfficiency(PlayerLane lane) {
        double scale = DeveloperBalance.patchScale(type());
        if (hasBug(DeveloperBug.READ_ONLY)) {
            scale *= 1.0 + DeveloperBug.READ_ONLY.primary();
        }
        scale *= 1.0 + testBuildAura(lane);
        return scale;
    }

    private double testBuildAura(PlayerLane lane) {
        if (lane == null) {
            return 0.0;
        }
        double radius = DeveloperBalance.testBuildAuraRadius();
        if (radius <= 0.0) {
            return 0.0;
        }
        double radiusSqr = radius * radius;
        double bonus = 0.0;
        for (var tower : lane.towers()) {
            if (tower == this || !DeveloperTowers.isTestBuild(tower.type())) {
                continue;
            }
            if (!ownerPlayer().equals(tower.ownerPlayer())) {
                continue;
            }
            if (distanceSqr(tower.position(), position()) <= radiusSqr) {
                bonus += DeveloperBalance.testBuildAuraBonus();
            }
        }
        return bonus;
    }

    private static double distanceSqr(GridPosition a, GridPosition b) {
        if (a == null || b == null) {
            return Double.MAX_VALUE;
        }
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static String monsterId(SemionMonsterEntity target) {
        if (target == null || target.runtimeMonster() == null) {
            return "";
        }
        String id = target.runtimeMonster().id();
        return id == null ? "" : id;
    }

    private static int clampAggro(int value) {
        return Math.max(-100, Math.min(100, value));
    }

    @Override
    public List<String> runtimeDetailLines() {
        return DeveloperTowerLines.describe(this);
    }
}
