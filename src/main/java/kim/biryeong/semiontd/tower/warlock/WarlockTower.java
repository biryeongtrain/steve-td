package kim.biryeong.semiontd.tower.warlock;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.damagesource.DamageSource;

public class WarlockTower extends EntityBackedTower {
    public static final String CONFIG_ID = WarlockTowers.CONFIG_ID;

    private final WarlockConfig config;
    private final WarlockPath path;
    private final WarlockState state;
    private final WarlockSacrificeController sacrifice;
    private final WarlockCombat combat;
    private final WarlockAwakeningController awakening;
    private final WarlockStatsAssembler stats;
    private PlayerLane currentLane;

    public WarlockTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        this(type, ownerPlayer, teamId, laneId, position, position);
    }

    public WarlockTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
        this.config = WarlockConfig.RUNTIME;
        this.path = WarlockPath.fromCore(type);
        this.state = new WarlockState();
        this.sacrifice = new WarlockSacrificeController(config, state);
        this.combat = new WarlockCombat(config);
        this.awakening = new WarlockAwakeningController(config, state, path, ownerPlayer);
        this.stats = new WarlockStatsAssembler(config, combat);
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        currentLane = lane;
        super.onPlaced(lane);
        refreshWarlockCoreStats(lane);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        super.onRemoved(lane);
        if (currentLane == lane) {
            currentLane = null;
        }
    }

    @Override
    protected double builderCurrentMaxHealth() {
        return applyTraitMaxHealth(maxHealth() * (1.0 + passiveHealthBonus()) + effectiveHealthBonus());
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        return (damageAmount + effectiveDamageBonus() + awakening.attackDamageBonus()) * (1.0 + passiveDamageBonus());
    }

    @Override
    public double adjustMovementSpeed(double baseSpeed) {
        return baseSpeed * (1.0 + awakening.movementSpeedBonus());
    }

    @Override
    public double modifyIncomingDamage(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount) {
        if (damageAmount <= 0.0) {
            return damageAmount;
        }
        return damageAmount * Math.max(0.0, 1.0 - damageReduction());
    }

    @Override
    public double incomeDebuffResistance() {
        return config.path(path).incomeDebuffResistance();
    }

    public void onDamaged(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double damageAmount,
            double previousHealth,
            double currentHealth
    ) {
        WarlockRules.AbsorptionRule absorption = config.path(path).absorption();
        if (path == WarlockPath.BASE) {
            if (currentHealth <= 0.0) {
                sacrifice.absorbNearest(this, towerEntity, currentLane, Comparator.comparingInt(Tower::aggroPriority));
            }
            return;
        }

        double damagedHealthRatio = healthRatio(currentHealth);
        if (damagedHealthRatio <= absorption.triggerHealthRatio()) {
            Comparator<Tower> priority = Comparator.comparingInt(Tower::aggroPriority);
            sacrifice.absorbNearest(
                    this,
                    towerEntity,
                    currentLane,
                    path == WarlockPath.MELEE ? priority.reversed() : priority
            );
        }
        awakening.tryActivate(this, currentLane, towerEntity);
    }

    @Override
    public void onAttackResolved(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double attemptedDamage,
            double resolvedOutgoingDamage,
            double dealtDamage,
            boolean killedTarget
    ) {
        combat.resolveAttack(this, towerEntity, target, attemptedDamage, resolvedOutgoingDamage, dealtDamage);
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        return switch (path) {
            case RANGED -> Math.max(
                    combat.minimumAttackIntervalTicks(),
                    (int) Math.ceil(baseIntervalTicks - state.roundIntervalReduction())
            );
            case MELEE -> Math.max(
                    combat.minimumAttackIntervalTicks(),
                    baseIntervalTicks - combat.meleeAttackIntervalReduction(progressionSnapshot().roundSacrificeCount())
            );
            case BASE -> baseIntervalTicks <= 0
                    ? baseIntervalTicks
                    : Math.max(combat.minimumAttackIntervalTicks(), baseIntervalTicks);
        };
    }

    @Override
    public int minimumAttackIntervalTicks() {
        return combat.minimumAttackIntervalTicks();
    }

    @Override
    public List<String> runtimeDetailLines() {
        return stats.create(this);
    }

    @Override
    public void tick(PlayerLane lane) {
        currentLane = lane;
        super.tick(lane);
        awakening.tick(this, lane);
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        currentLane = lane;
        awakening.resetRound(this, lane);
        state.resetRound();
        super.resetForRound(lane);
        refreshWarlockCoreStats(lane);
    }

    @Override
    public void finishRoundReset(PlayerLane lane) {
        currentLane = lane;
        syncHealth(currentMaxHealth());
        onStateChanged(lane);
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof WarlockTower warlockTower) {
            state.copyFrom(warlockTower.state);
        }
    }

    void heal(SemionTowerEntity towerEntity, double amount) {
        if (towerEntity == null || amount <= 0.0) {
            return;
        }
        double before = health();
        double nextHealth = Math.min(currentMaxHealth(), before + amount);
        syncHealth(nextHealth);
        towerEntity.setHealth((float) nextHealth);
        recordHealingDone(health() - before);
    }

    void syncFromEntityHealth(double currentHealth) {
        syncHealth(currentHealth);
    }

    void applyRegeneration(PlayerLane lane, double amount) {
        if (amount <= 0.0) {
            return;
        }
        double before = health();
        syncHealth(before + amount);
        recordHealingDone(health() - before);
        onStateChanged(lane);
    }

    void refreshAfterSacrifice(PlayerLane lane, SemionTowerEntity towerEntity, double healAmount) {
        onStateChanged(lane);
        heal(towerEntity, healAmount);
        onStateChanged(lane);
    }

    private double passiveHealthBonus() {
        return sacrifice.passiveHealthBonus(this, currentLane);
    }

    private double passiveDamageBonus() {
        return sacrifice.passiveDamageBonus(this, currentLane);
    }

    double damageReduction() {
        return sacrifice.damageReduction(path);
    }

    double splashRadius() {
        return combat.splashRadius(this);
    }

    public static void refreshWarlockCoreStats(PlayerLane lane) {
        if (lane == null) {
            return;
        }
        for (Tower tower : lane.towers()) {
            if (tower instanceof WarlockTower warlockTower) {
                warlockTower.syncHealth(warlockTower.health());
                warlockTower.onStateChanged(lane);
            }
        }
    }

    public static void onAwakeningUnlocked(PlayerLane lane, UUID ownerPlayer) {
        if (lane == null || ownerPlayer == null) {
            return;
        }
        for (Tower tower : lane.towers()) {
            if (tower instanceof WarlockTower warlockTower
                    && ownerPlayer.equals(warlockTower.ownerPlayer())) {
                warlockTower.tryAwakenFromCurrentState(lane);
            }
        }
        refreshWarlockCoreStats(lane);
    }

    private void tryAwakenFromCurrentState(PlayerLane lane) {
        currentLane = lane;
        if (lane.arenaWorld() == null) {
            return;
        }
        entityId().ifPresent(id -> {
            var entity = lane.arenaWorld().getEntity(id);
            if (entity instanceof SemionTowerEntity towerEntity && towerEntity.isAlive()) {
                awakening.tryActivate(this, lane, towerEntity);
            }
        });
    }

    WarlockPath path() {
        return path;
    }

    WarlockProgressionSnapshot progressionSnapshot() {
        return WarlockProgressionSnapshot.from(state, ownerPlayer());
    }

    double rawDamageBonus() {
        return state.permanentDamageBonus() + state.roundDamageBonus();
    }

    double effectiveDamageBonus() {
        return config.path(path).damageScaling().value(rawDamageBonus());
    }

    double rawHealthBonus() {
        return state.permanentHealthBonus() + state.roundHealthBonus();
    }

    double effectiveHealthBonus() {
        return config.path(path).healthScaling().value(rawHealthBonus());
    }

    double additionalHealth() {
        return Math.max(0.0, currentMaxHealth() - applyTraitMaxHealth(maxHealth()));
    }

    int attackIntervalReduction() {
        return Math.max(0, type().attackIntervalTicks() - adjustAttackInterval(type().attackIntervalTicks()));
    }

    int maximumAttackIntervalReduction() {
        int maximumByMinimumInterval = Math.max(0, type().attackIntervalTicks() - combat.minimumAttackIntervalTicks());
        return Math.min(maximumByMinimumInterval, combat.maximumAttackIntervalReduction());
    }

    double maximumDamageReduction() {
        return sacrifice.maximumDamageReduction(path);
    }

    double currentHealthRatio() {
        return healthRatio(health());
    }

    boolean isLastSurvivingTower() {
        return isLastSurvivingTower(currentLane);
    }

    boolean isLastSurvivingTower(PlayerLane lane) {
        if (lane == null || health() <= 0.0 || !lane.towers().contains(this)) {
            return false;
        }
        return lane.towers().stream()
                .filter(tower -> tower.health() > 0.0)
                .noneMatch(tower -> tower != this);
    }

    boolean awakenedThisRound() {
        return awakening.awakenedThisRound();
    }

    double awakeningHealthThreshold() {
        return config.awakening(path).healthThreshold();
    }

    double regenerationPerSecond() {
        return awakening.regenerationPerSecond();
    }

    double awakeningDamageBonus() {
        return awakening.attackDamageBonus();
    }

    double awakeningMovementSpeedBonus() {
        return awakening.movementSpeedBonus();
    }

    private double healthRatio(double currentHealth) {
        double maxHealth = currentMaxHealth();
        return maxHealth <= 0.0 ? 0.0 : currentHealth / maxHealth;
    }
}
