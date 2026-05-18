package kim.biryeong.semiontd.tower;

import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import java.util.UUID;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import net.minecraft.world.damagesource.DamageSource;

public abstract class Tower {
    private final TowerType type;
    private final UUID ownerPlayer;
    private final TeamId teamId;
    private final int laneId;
    private final GridPosition originalPosition;
    private GridPosition currentPosition;
    private final double maxHealth;
    private double health;
    private long paidMineralCost;
    private int placedRound;
    private boolean waveStartedAfterPlacement;
    private int cooldownTicks;
    private int level = 1;
    private boolean deployedAtFinalDefense;

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

    public void markWaveStarted(int currentRound) {
        if (placedRound > 0 && currentRound >= placedRound) {
            waveStartedAfterPlacement = true;
        }
    }

    public long sellRefundAmount() {
        double rate = waveStartedAfterPlacement ? 0.5 : 1.0;
        return Math.round(paidMineralCost * rate);
    }

    public void onPlaced(PlayerLane lane) {
    }

    public void onRemoved(PlayerLane lane) {
    }

    public void onStateChanged(PlayerLane lane) {
    }

    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        return damageAmount;
    }

    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
    }

    public void onKill(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
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

    public int adjustAttackInterval(int baseIntervalTicks) {
        return baseIntervalTicks;
    }

    public ProductionTowerBehavior productionBehavior() {
        return null;
    }

    public int productionMechanicStacks() {
        return 0;
    }

    public boolean isDestroyed(PlayerLane lane) {
        return health <= 0.0;
    }

    public void resetForRound(PlayerLane lane) {
        cooldownTicks = 0;
        currentPosition = originalPosition;
        health = maxHealth;
        deployedAtFinalDefense = false;
        onStateChanged(lane);
    }

    public void moveToFinalDefense(PlayerLane lane, GridPosition position) {
        currentPosition = position;
        deployedAtFinalDefense = true;
        onStateChanged(lane);
    }

    public void syncHealth(double health) {
        this.health = Math.max(0.0, Math.min(maxHealth, health));
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
            cooldownTicks = type.attackIntervalTicks();
        }
    }

    protected abstract boolean execute(PlayerLane lane);
}
