package kim.biryeong.semiontd.tower;

import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.trait.TraitEffects;
import kim.biryeong.semiontd.trait.TraitLoadout;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

public abstract class Tower {
    private TowerType type;
    private final UUID ownerPlayer;
    private final TeamId teamId;
    private final int laneId;
    private final GridPosition originalPosition;
    private GridPosition currentPosition;
    private double maxHealth;
    private double health;
    private long paidMineralCost;
    private int placedRound;
    private int currentRound = 1;
    private TowerType roundCombatType;
    private double roundPhysicalDamageDealt;
    private double roundMagicDamageDealt;
    private double roundDamageTaken;
    private boolean waveStartedAfterPlacement;
    private int cooldownTicks;
    private int level = 1;
    private boolean deployedAtFinalDefense;
    private boolean deathNotifiedThisRound;
    private final Map<TowerDataKey<?>, Object> data = new HashMap<>();
    private TraitLoadout traitLoadout = TraitLoadout.none();
    private double traitMaxHealthBonus;

    protected Tower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        this(type, ownerPlayer, teamId, laneId, position, position);
    }

    protected Tower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        this.type = type;
        this.ownerPlayer = ownerPlayer;
        this.teamId = teamId;
        this.laneId = laneId;
        this.originalPosition = originalPosition;
        this.currentPosition = currentPosition;
        this.maxHealth = type.maxHealth();
        this.health = type.maxHealth();
        this.paidMineralCost = type.mineralCost();
        this.roundCombatType = type;
    }

    public TowerType type() {
        return type;
    }

    public DamageType primaryDamageType() {
        return type.primaryDamageType();
    }

    public EntityVisual visual() {
        return type.visual();
    }

    public void refreshType(TowerType type, PlayerLane lane) {
        if (type == null || !this.type.id().equals(type.id())) {
            return;
        }
        double previousHealth = health;
        this.type = type;
        refreshMaxHealthAfterTypeChange(lane);
        syncHealth(previousHealth);
        onStateChanged(lane);
    }

    protected void refreshMaxHealthAfterTypeChange(PlayerLane lane) {
        this.maxHealth = type.maxHealth();
    }

    public UUID ownerPlayer() {
        return ownerPlayer;
    }

    public TeamId teamId() {
        return teamId;
    }

    public int laneId() {
        return laneId;
    }

    public GridPosition position() {
        return currentPosition;
    }

    public GridPosition originalPosition() {
        return originalPosition;
    }

    public int level() {
        return level;
    }

    public double maxHealth() {
        return maxHealth;
    }

    public double currentMaxHealth() {
        return applyTraitMaxHealth(maxHealth);
    }

    public double effectBaseMaxHealth() {
        return type.maxHealth();
    }

    public void syncMaxHealth(double maxHealth, boolean healIncrease) {
        double previousMaxHealth = currentMaxHealth();
        this.maxHealth = finitePositive(maxHealth, 1.0);
        double nextMaxHealth = currentMaxHealth();
        if (healIncrease && nextMaxHealth > previousMaxHealth) {
            health = Math.min(nextMaxHealth, health + (nextMaxHealth - previousMaxHealth));
            return;
        }
        syncHealth(health);
    }

    public void syncEffectMaxHealth(double maxHealth, double traitMaxHealthBonus) {
        syncEffectMaxHealth(maxHealth, traitMaxHealthBonus, true);
    }

    public void syncEffectMaxHealth(
            double maxHealth,
            double traitMaxHealthBonus,
            boolean healIncrease
    ) {
        double previousMaxHealth = currentMaxHealth();
        this.maxHealth = finitePositive(maxHealth, 1.0);
        this.traitMaxHealthBonus = finiteNonNegative(traitMaxHealthBonus);
        double nextMaxHealth = currentMaxHealth();
        if (healIncrease && nextMaxHealth > previousMaxHealth) {
            health = Math.min(nextMaxHealth, health + (nextMaxHealth - previousMaxHealth));
        } else {
            syncHealth(health);
        }
    }

    public double health() {
        return health;
    }

    public int aggroPriority() {
        return type.aggroPriority();
    }

    public boolean deployedAtFinalDefense() {
        return deployedAtFinalDefense;
    }

    public long paidMineralCost() {
        return paidMineralCost;
    }

    public int placedRound() {
        return placedRound;
    }

    public boolean waveStartedAfterPlacement() {
        return waveStartedAfterPlacement;
    }

    public void recordPlacementEconomy(long paidMineralCost, int currentRound) {
        this.paidMineralCost = Math.max(0, paidMineralCost);
        this.placedRound = currentRound;
        this.waveStartedAfterPlacement = false;
    }

    public final void copyFrom(Tower previousTower, long extraPaidMineralCost) {
        if (previousTower == null) {
            return;
        }
        inheritSaleState(previousTower, extraPaidMineralCost);
        copyDataFrom(previousTower);
        copyRuntimeStateFrom(previousTower);
    }

    public void inheritSaleState(Tower previousTower, long extraPaidMineralCost) {
        if (previousTower == null) {
            return;
        }
        this.paidMineralCost = Math.max(0, previousTower.paidMineralCost() + Math.max(0, extraPaidMineralCost));
        this.placedRound = previousTower.placedRound();
        this.currentRound = previousTower.currentRound();
        this.roundCombatType = previousTower.roundCombatType();
        this.roundPhysicalDamageDealt = previousTower.roundPhysicalDamageDealt();
        this.roundMagicDamageDealt = previousTower.roundMagicDamageDealt();
        this.roundDamageTaken = previousTower.roundDamageTaken();
        this.waveStartedAfterPlacement = previousTower.waveStartedAfterPlacement();
    }

    protected void copyRuntimeStateFrom(Tower previousTower) {
    }

    public <T> void setData(TowerDataKey<T> key, T value) {
        if (key == null) {
            return;
        }
        if (value == null) {
            removeData(key);
            return;
        }
        if (!key.type().isInstance(value)) {
            throw new IllegalArgumentException("Tower data " + key.id() + " requires " + key.type().getName());
        }
        data.put(key, value);
    }

    public boolean hasData(TowerDataKey<?> key) {
        return key != null && data.containsKey(key);
    }

    public <T> Optional<T> getData(TowerDataKey<T> key) {
        if (key == null) {
            return Optional.empty();
        }
        Object value = data.get(key);
        return value == null ? Optional.empty() : Optional.of(key.cast(value));
    }

    public <T> T getDataOrDefault(TowerDataKey<T> key, T fallback) {
        return getData(key).orElse(fallback);
    }

    public void removeData(TowerDataKey<?> key) {
        if (key != null) {
            data.remove(key);
        }
    }

    private void copyDataFrom(Tower previousTower) {
        data.clear();
        data.putAll(previousTower.data);
    }

    public void markWaveStarted(int currentRound) {
        this.currentRound = Math.max(1, currentRound);
        this.roundCombatType = type;
        this.roundPhysicalDamageDealt = 0.0;
        this.roundMagicDamageDealt = 0.0;
        this.roundDamageTaken = 0.0;
        if (placedRound > 0 && currentRound >= placedRound) {
            waveStartedAfterPlacement = true;
        }
    }

    public int currentRound() {
        return currentRound;
    }

    public TowerType roundCombatType() {
        return roundCombatType;
    }

    public double roundDamageDealt() {
        return roundPhysicalDamageDealt + roundMagicDamageDealt;
    }

    public double roundPhysicalDamageDealt() {
        return roundPhysicalDamageDealt;
    }

    public double roundMagicDamageDealt() {
        return roundMagicDamageDealt;
    }

    public double roundDamageTaken() {
        return roundDamageTaken;
    }

    public void recordDamageDealt(double amount) {
        recordDamageDealt(amount, DamageType.PHYSICAL);
    }

    public void recordDamageDealt(double amount, DamageType damageType) {
        if (Double.isFinite(amount) && amount > 0.0) {
            if (damageType == DamageType.MAGIC) {
                roundMagicDamageDealt += amount;
            } else {
                roundPhysicalDamageDealt += amount;
            }
        }
    }

    public void recordDamageDealt(SemionMonsterEntity target, double amount, DamageType damageType) {
        if (countsDamageInRoundStatistics(target)) {
            recordDamageDealt(amount, damageType);
        }
    }

    protected boolean countsDamageInRoundStatistics(SemionMonsterEntity target) {
        return true;
    }

    public void recordDamageTaken(double amount) {
        if (Double.isFinite(amount) && amount > 0.0) {
            roundDamageTaken += amount;
        }
    }

    public void onWaveStarted(PlayerLane lane, int currentRound) {
    }

    public long sellRefundAmount() {
        double rate = TraitEffects.sellRefundRate(traitLoadout, waveStartedAfterPlacement);
        return Math.round(paidMineralCost * rate);
    }

    public void attachToLane(PlayerLane lane, TraitLoadout traitLoadout) {
        this.traitLoadout = receivesTraitEffects() && traitLoadout != null
                ? traitLoadout
                : TraitLoadout.none();
    }

    public boolean receivesTraitEffects() {
        return true;
    }

    public TraitLoadout traitLoadout() {
        return traitLoadout;
    }

    public void detachFromLane(PlayerLane lane) {
        traitLoadout = TraitLoadout.none();
        traitMaxHealthBonus = 0.0;
        syncHealth(health);
    }

    public void onPlaced(PlayerLane lane) {
    }

    public void onRemoved(PlayerLane lane) {
    }

    public void onDeath(PlayerLane lane) {
    }

    public final boolean notifyDeath(PlayerLane lane) {
        if (deathNotifiedThisRound) {
            return false;
        }
        deathNotifiedThisRound = true;
        onDeath(lane);
        return true;
    }

    public void onStateChanged(PlayerLane lane) {
    }

    public boolean meetsUpgradeRequirements(PlayerLane lane, TowerUpgradeOption option) {
        return true;
    }

    public void onUpgradeCompleted(PlayerLane lane, Tower previousTower, TowerUpgradeOption option) {
    }

    public void onSold(PlayerLane lane) {
    }

    public Optional<SemionMonsterEntity> selectAttackTarget(SemionTowerEntity towerEntity, List<SemionMonsterEntity> candidates) {
        return Optional.empty();
    }

    public boolean supportsForcedAttackTargeting() {
        return false;
    }

    public Optional<SemionMonsterEntity> selectForcedAttackTarget(SemionTowerEntity towerEntity, List<SemionMonsterEntity> candidates) {
        return Optional.empty();
    }

    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        return damageAmount;
    }

    public double modifyResolvedAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        return damageAmount;
    }

    public double finalDamageBonus() {
        return 0.0;
    }

    public double incomeDebuffResistance() {
        return 0.0;
    }

    public boolean damageTarget(SemionTowerEntity towerEntity, SemionMonsterEntity target, double baseDamage) {
        return damageTargetResult(towerEntity, target, baseDamage).killed();
    }

    public boolean damageTarget(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double baseDamage,
            DamageType damageType
    ) {
        return damageTargetResult(towerEntity, target, baseDamage, damageType).killed();
    }

    public DamageResult damageTargetResult(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double baseDamage
    ) {
        return damageTargetResult(towerEntity, target, baseDamage, DamageType.PHYSICAL);
    }

    public DamageResult damageTargetResult(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double baseDamage,
            DamageType damageType
    ) {
        if (towerEntity == null || target == null || !Double.isFinite(baseDamage)) {
            return DamageResult.NONE;
        }
        double outgoingDamage = resolveOutgoingDamage(towerEntity, target, baseDamage);
        return damageResolvedTargetResult(towerEntity, target, outgoingDamage, damageType);
    }

    public double resolveOutgoingDamage(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double baseDamage
    ) {
        if (towerEntity == null || !Double.isFinite(baseDamage)) {
            return 0.0;
        }
        double damageWithTraits =
                towerEntity.applyTraitOutgoingDamageBeforeTowerFinalAgainst(target, baseDamage);
        double resolvedOutgoingDamage = applyOutgoingDamageStages(
                damageWithTraits,
                damage -> modifyOutgoingDamage(towerEntity, target, damage),
                towerEntity::applyTowerFinalDamageBonus
        );
        return modifyResolvedOutgoingDamage(towerEntity, target, resolvedOutgoingDamage);
    }

    static double applyOutgoingDamageStages(
            double damageWithTraits,
            DoubleUnaryOperator outgoingModifier,
            DoubleUnaryOperator finalDamageModifier
    ) {
        double modifiedDamage = outgoingModifier.applyAsDouble(damageWithTraits);
        return finalDamageModifier.applyAsDouble(modifiedDamage);
    }

    public DamageResult damageResolvedTargetResult(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double outgoingDamage
    ) {
        return damageResolvedTargetResult(towerEntity, target, outgoingDamage, DamageType.PHYSICAL);
    }

    public DamageResult damageResolvedTargetResult(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double outgoingDamage,
            DamageType damageType
    ) {
        if (towerEntity == null
                || target == null
                || !Double.isFinite(outgoingDamage)
                || outgoingDamage <= 0.0) {
            return DamageResult.NONE;
        }
        Monster runtimeMonster = target.runtimeMonster();
        double damageAmount = modifyAppliedDamage(
                towerEntity,
                target,
                target.towerDamageTaken(outgoingDamage)
        );
        if (!Double.isFinite(damageAmount) || damageAmount <= 0.0) {
            return DamageResult.NONE;
        }
        double previousHealth = runtimeMonster == null ? target.getHealth() : runtimeMonster.health();
        DamageType resolvedDamageType = damageType == null ? DamageType.PHYSICAL : damageType;
        boolean killed = target.applyRuntimeDamage(
                towerEntity.damageSources().mobAttack(towerEntity),
                damageAmount,
                resolvedDamageType
        );
        double currentHealth = runtimeMonster == null ? target.getHealth() : runtimeMonster.health();
        double dealtDamage = Math.max(0.0, previousHealth - currentHealth);
        recordDamageDealt(target, dealtDamage, resolvedDamageType);
        if (dealtDamage > 0.0 && resolvedDamageType == DamageType.MAGIC) {
            TowerVfxService.showMagicHit(towerEntity, target);
        }
        if (runtimeMonster != null && dealtDamage > 0.0) {
            runtimeMonster.recordLastHit(ownerPlayer, KillSourceKind.TOWER);
        }
        return new DamageResult(killed, dealtDamage, outgoingDamage);
    }

    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
    }

    public void onAttackResolved(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double attemptedDamage,
            double resolvedOutgoingDamage,
            double dealtDamage,
            boolean killedTarget
    ) {
        onAttack(towerEntity, target, attemptedDamage, killedTarget);
    }

    public void onKill(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
    }

    public void onIgniteKill(SemionMonsterEntity target) {
    }

    public void onNearbyMonsterDeath(PlayerLane lane, Monster monster, Vec3 deathPosition) {
    }

    public void onNearbyTowerDeath(PlayerLane lane, Tower destroyedTower) {
    }

    public double modifyOutgoingDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {return damageAmount;}

    public double modifyResolvedOutgoingDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        return damageAmount;
    }

    public double modifyAppliedDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        return damageAmount;
    }

    protected boolean isWithinDeathStackRange(Vec3 deathPosition) {
        if (deathPosition == null) {
            return false;
        }
        double x = position().x() + 0.5;
        double y = position().y() + 1.0;
        double z = position().z() + 0.5;
        double radius = Math.max(0.0, type.range());
        return deathPosition.distanceToSqr(x, y, z) <= radius * radius;
    }

    protected boolean isWithinDeathStackRange(GridPosition deathPosition) {
        if (deathPosition == null) {
            return false;
        }
        return isWithinDeathStackRange(new Vec3(deathPosition.x() + 0.5, deathPosition.y() + 1.0, deathPosition.z() + 0.5));
    }

    public double modifyIncomingDamage(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount) {
        return damageAmount;
    }

    public double modifyIncomingDamageIgnoringReductions(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double damageAmount
    ) {
        return damageAmount;
    }

    public void onDamaged(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double damageAmount,
            double previousHealth,
            double currentHealth
    ) {
    }

    public void onTimedEffectApplied(
            SemionTowerEntity towerEntity,
            TimedEffectType type,
            double magnitude,
            int durationTicks
    ) {
    }

    public List<String> runtimeDetailLines() {
        return List.of();
    }

    protected static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    protected static String percent(double value) {
        return oneDecimal(value * 100.0) + "%";
    }

    protected static String percentInteger(double value) {return Math.round(value * 100.0) + "%";}

    protected final double applyTraitMaxHealth(double baseMaxHealth) {
        return baseMaxHealth * (1.0 + traitMaxHealthBonus);
    }

    public int adjustAttackInterval(int baseIntervalTicks) {
        return baseIntervalTicks;
    }

    public int minimumAttackIntervalTicks() {
        return 1;
    }

    public double adjustAttackRange(double baseRange) {
        return baseRange;
    }

    public double adjustMovementSpeed(double baseSpeed) {return baseSpeed;}

    /**
     * Rooted towers hold their tile instead of walking toward an out-of-range target inside the lane.
     */
    public boolean canChaseTargets() {
        return true;
    }

    /**
     * Fixture towers take no damage at all, including from effects that ignore damage reduction.
     */
    public boolean invulnerable() {
        return false;
    }

    /**
     * Towers that do not draw aggro are skipped entirely when monsters look for something to attack.
     */
    public boolean drawsAggro() {
        return true;
    }

    public boolean isDestroyed(PlayerLane lane) {
        return health <= 0.0;
    }

    public void resetForRound(PlayerLane lane) {
        cooldownTicks = 0;
        currentPosition = originalPosition;
        health = currentMaxHealth();
        deployedAtFinalDefense = false;
        deathNotifiedThisRound = false;
        onStateChanged(lane);
    }
    
    public void finishRoundReset(PlayerLane lane) {
    }

    public void moveToFinalDefense(PlayerLane lane, GridPosition position) {
        currentPosition = position;
        deployedAtFinalDefense = true;
        onStateChanged(lane);
    }

    public void syncHealth(double health) {
        this.health = Math.max(0.0, Math.min(currentMaxHealth(), health));
    }

    public void syncPosition(GridPosition position) {
        this.currentPosition = position;
    }

    public void tick(PlayerLane lane) {
        if (health <= 0.0) {
            return;
        }
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        if (execute(lane)) {
            cooldownTicks = cooldownTicksAfterExecute(lane);
        }
    }

    protected int cooldownTicksAfterExecute(PlayerLane lane) {
        return type.attackIntervalTicks();
    }

    protected abstract boolean execute(PlayerLane lane);

    private static double finitePositive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 0.0;
    }

    public record DamageResult(boolean killed, double dealtDamage, double outgoingDamage) {
        public static final DamageResult NONE = new DamageResult(false, 0.0, 0.0);

        public DamageResult {
            dealtDamage = Double.isFinite(dealtDamage) && dealtDamage > 0.0 ? dealtDamage : 0.0;
            outgoingDamage = Double.isFinite(outgoingDamage) && outgoingDamage > 0.0 ? outgoingDamage : 0.0;
        }
    }
}
