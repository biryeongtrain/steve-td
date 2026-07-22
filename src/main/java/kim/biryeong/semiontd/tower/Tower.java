package kim.biryeong.semiontd.tower;

import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
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
    }

    public TowerType type() {
        return type;
    }

    public EntityVisual visual() {
        return type.visual();
    }

    public void refreshType(TowerType type, PlayerLane lane) {
        if (type == null || !this.type.id().equals(type.id())) {
            return;
        }
        this.type = type;
        this.maxHealth = type.maxHealth();
        syncHealth(health);
        onStateChanged(lane);
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
        this.maxHealth = Math.max(1.0, maxHealth);
        double nextMaxHealth = currentMaxHealth();
        if (healIncrease && nextMaxHealth > previousMaxHealth) {
            health = Math.min(nextMaxHealth, health + (nextMaxHealth - previousMaxHealth));
            return;
        }
        syncHealth(health);
    }

    public void syncEffectMaxHealth(double maxHealth, double traitMaxHealthBonus) {
        double previousMaxHealth = currentMaxHealth();
        this.maxHealth = Math.max(1.0, maxHealth);
        this.traitMaxHealthBonus = Math.max(0.0, traitMaxHealthBonus);
        double nextMaxHealth = currentMaxHealth();
        if (nextMaxHealth > previousMaxHealth) {
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
        if (placedRound > 0 && currentRound >= placedRound) {
            waveStartedAfterPlacement = true;
        }
    }

    public void onWaveStarted(PlayerLane lane, int currentRound) {
    }

    public long sellRefundAmount() {
        double rate = TraitEffects.sellRefundRate(traitLoadout, waveStartedAfterPlacement);
        return Math.round(paidMineralCost * rate);
    }

    public void attachToLane(PlayerLane lane, TraitLoadout traitLoadout) {
        this.traitLoadout = traitLoadout == null ? TraitLoadout.none() : traitLoadout;
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

    public double finalDamageBonus() {
        return 0.0;
    }

    public double incomeDebuffResistance() {
        return 0.0;
    }

    public boolean damageTarget(SemionTowerEntity towerEntity, SemionMonsterEntity target, double baseDamage) {
        Monster runtimeMonster = target.runtimeMonster();
        double traitDamage = towerEntity.applyTraitOutgoingDamage(runtimeMonster, baseDamage);
        double damageAmount = target.towerDamageTaken(traitDamage);
        if (damageAmount <= 0.0) {
            return false;
        }
        double previousHealth = runtimeMonster == null ? 0.0 : runtimeMonster.health();
        boolean killed = target.applyRuntimeDamage(
                towerEntity.damageSources().mobAttack(towerEntity),
                damageAmount,
                DamageType.PHYSICAL
        );
        if (runtimeMonster != null && runtimeMonster.health() < previousHealth) {
            runtimeMonster.recordLastHit(ownerPlayer, KillSourceKind.TOWER);
        }
        return killed;
    }

    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
    }

    public void onKill(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
    }

    public void onNearbyMonsterDeath(PlayerLane lane, Monster monster, Vec3 deathPosition) {
    }

    public void onNearbyTowerDeath(PlayerLane lane, Tower destroyedTower) {
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

    protected final double applyTraitMaxHealth(double baseMaxHealth) {
        return baseMaxHealth * (1.0 + traitMaxHealthBonus);
    }

    public int adjustAttackInterval(int baseIntervalTicks) {
        return baseIntervalTicks;
    }

    public double adjustAttackRange(double baseRange) {
        return baseRange;
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
}
