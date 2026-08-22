package kim.biryeong.semiontd.tower.warlock;

import static kim.biryeong.semiontd.tower.warlock.WarlockConfig.Ability.*;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.LogarithmicScaling;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.world.damagesource.DamageSource;

public class WarlockTower extends EntityBackedTower {
    public static final String CONFIG_ID = WarlockTowers.CONFIG_ID;
    private final WarlockState state;
    private final WarlockSacrificeController sacrifices;
    private final WarlockCombat combat;
    private final WarlockStats stats;
    private PlayerLane currentLane;
    private int regenerationTicks;
    private int awakeningVfxTicks;

    public WarlockTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
        this.state = new WarlockState();
        this.sacrifices = new WarlockSacrificeController(WarlockConfig.RUNTIME, this.state);
        this.combat = new WarlockCombat(WarlockConfig.RUNTIME);
        this.stats = new WarlockStats(this.combat);
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
        this.state = new WarlockState();
        this.sacrifices = new WarlockSacrificeController(WarlockConfig.RUNTIME, this.state);
        this.combat = new WarlockCombat(WarlockConfig.RUNTIME);
        this.stats = new WarlockStats(this.combat);
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
    public double currentMaxHealth() {
        return applyTraitMaxHealth(maxHealth() * (1.0 + passiveHealthBonus())
                + effectiveHealthBonus());
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        return (damageAmount + effectiveDamageBonus() + awakeningDamageBonus()) * (1.0 + passiveDamageBonus());
    }

    @Override
    public double adjustMovementSpeed(double baseSpeed) {
        return baseSpeed * (1.0 + awakeningMovementSpeedBonus());
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
        if (is(WarlockTowers.RANGED_WARLOCK_TOWER)) {
            return Math.clamp(ability(RANGED_INCOME_DEBUFF_RESISTANCE), 0.0, 1.0);
        }
        if (is(WarlockTowers.MELEE_WARLOCK_TOWER)) {
            return Math.clamp(ability(MELEE_INCOME_DEBUFF_RESISTANCE), 0.0, 1.0);
        }
        return 0.0;
    }

    double awakeningDamageBonus() {
        if (!is(WarlockTowers.MELEE_WARLOCK_TOWER)
                || !state.awakenedThisRound()) {
            return 0.0;
        }
        return Math.max(0.0, ability(MELEE_AWAKENING_DAMAGE));
    }

    double awakeningMovementSpeedBonus() {
        if (!is(WarlockTowers.MELEE_WARLOCK_TOWER)
                || !state.awakenedThisRound()) {
            return 0.0;
        }
        return Math.max(0.0, ability(MELEE_AWAKENING_MOVE_SPEED));
    }

    public void onDamaged(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double damageAmount,
            double previousHealth,
            double currentHealth
    ) {
        if (is(WarlockTowers.BASE_WARLOCK_TOWER) && currentHealth <= 0.0) {
            sacrifices.sacrifice(
                    this,
                    towerEntity,
                    currentLane,
                    sacrificeRadius(BASE_RADIUS),
                    Comparator.comparingInt(Tower::aggroPriority)
            );
            return;
        }
        if (is(WarlockTowers.RANGED_WARLOCK_TOWER)) {
            double damagedHealthRatio = healthRatio(currentHealth);
            if (damagedHealthRatio <= ability(RANGED_THRESHOLD)) {
                sacrifices.sacrifice(
                        this,
                        towerEntity,
                        currentLane,
                        sacrificeRadius(SACRIFICE_RADIUS),
                        Comparator.comparingInt(Tower::aggroPriority)
                );
            }
            tryAwaken(currentLane, towerEntity);
            return;
        }
        if (is(WarlockTowers.MELEE_WARLOCK_TOWER)) {
            double damagedHealthRatio = healthRatio(currentHealth);
            if (damagedHealthRatio <= ability(MELEE_THRESHOLD)) {
                sacrifices.sacrifice(
                        this,
                        towerEntity,
                        currentLane,
                        sacrificeRadius(SACRIFICE_RADIUS),
                        Comparator.comparingInt(Tower::aggroPriority).reversed()
                );
            }
            tryAwaken(currentLane, towerEntity);
        }
    }

    private void tryAwaken(PlayerLane lane, SemionTowerEntity towerEntity) {
        if (towerEntity == null
                || !WarlockAwakeningProgress.unlocked(ownerPlayer())
                || (!is(WarlockTowers.RANGED_WARLOCK_TOWER)
                && !is(WarlockTowers.MELEE_WARLOCK_TOWER))) {
            return;
        }
        if (state.awakenedThisRound()) {
            return;
        }
        syncHealth(towerEntity.getHealth());
        if (!meetsAwakeningConditions(
                WarlockAwakeningProgress.unlocked(ownerPlayer()),
                currentHealthRatio(),
                ability(AWAKENING_THRESHOLD),
                onlyCoreTowerAlive(lane)
        )) {
            return;
        }
        if (!state.awaken()) {
            return;
        }
        regenerationTicks = 0;
        awakeningVfxTicks = 0;
        towerEntity.setGlowingTag(true);
        TowerVfxService.showWarlockAwakening(towerEntity);
        if (is(WarlockTowers.RANGED_WARLOCK_TOWER)) {
            heal(
                    towerEntity,
                    ability(RANGED_AWAKENING_HEAL)
            );
        } else if (is(WarlockTowers.MELEE_WARLOCK_TOWER)) {
            heal(
                    towerEntity,
                    ability(MELEE_AWAKENING_HEAL)
            );
        }
        onStateChanged(lane);
    }

    double regenerationPerSecond() {
        if (!is(WarlockTowers.RANGED_WARLOCK_TOWER)
                || !state.awakenedThisRound()) {
            return 0.0;
        }
        return Math.max(
                0.0,
                ability(RANGED_AWAKENING_REGENERATION)
        );
    }

    private void tickRegeneration(PlayerLane lane) {
        double amount = regenerationPerSecond();
        if (health() <= 0.0 || amount <= 0.0) {
            regenerationTicks = 0;
            return;
        }
        if (health() >= currentMaxHealth()) {
            regenerationTicks = 0;
            return;
        }
        int intervalTicks = Math.max(
                1,
                abilityInt(
                        RANGED_AWAKENING_REGENERATION_TICKS
                )
        );
        regenerationTicks++;
        if (regenerationTicks < intervalTicks) {
            return;
        }
        regenerationTicks %= intervalTicks;
        double before = health();
        syncHealth(before + amount);
        recordHealingDone(health() - before);
        onStateChanged(lane);
    }

    private boolean onlyCoreTowerAlive(PlayerLane lane) {
        if (lane == null
                || health() <= 0.0
                || !lane.towers().contains(this)) {
            return false;
        }
        return lane.towers().stream()
                .filter(tower -> tower.health() > 0.0)
                .noneMatch(tower -> tower != this);
    }

    boolean onlyCoreTowerAlive() {
        return onlyCoreTowerAlive(currentLane);
    }

    double currentHealthRatio() {
        return healthRatio(health());
    }

    static boolean meetsAwakeningConditions(
            boolean awakeningUnlocked,
            double currentHealthRatio,
            double healthThreshold,
            boolean onlyCoreAlive
    ) {
        if (!Double.isFinite(currentHealthRatio)
                || !Double.isFinite(healthThreshold)) {
            return false;
        }
        return awakeningUnlocked
                && onlyCoreAlive
                && currentHealthRatio > 0.0
                && currentHealthRatio <= Math.max(0.0, healthThreshold);
    }

    private int abilityInt(WarlockConfig.Ability key) {
        return WarlockConfig.RUNTIME.integer(key);
    }

    boolean awakenedThisRound() {
        return state.awakenedThisRound();
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
        combat.resolveAttack(
                this,
                towerEntity,
                target,
                attemptedDamage,
                resolvedOutgoingDamage,
                dealtDamage
        );
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        if (is(WarlockTowers.RANGED_WARLOCK_TOWER)) {
            return Math.max(
                    combat.minimumAttackIntervalTicks(),
                    (int) Math.ceil(baseIntervalTicks - state.roundIntervalReduction())
            );
        }
        if (is(WarlockTowers.MELEE_WARLOCK_TOWER)) {
            return Math.max(
                    combat.minimumAttackIntervalTicks(),
                    baseIntervalTicks - combat.meleeAttackIntervalReduction(this)
            );
        }
        if (baseIntervalTicks <= 0) {
            return baseIntervalTicks;
        }
        return Math.max(combat.minimumAttackIntervalTicks(), baseIntervalTicks);
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
        tickRegeneration(lane);
        tickAwakeningVfx(lane);
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        currentLane = lane;
        setAwakeningGlow(lane, false);
        awakeningVfxTicks = 0;
        regenerationTicks = 0;
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
        double nextHealth = Math.min(currentMaxHealth(), health() + amount);
        syncHealth(nextHealth);
        towerEntity.setHealth((float) nextHealth);
    }

    void refreshAfterSacrifice(PlayerLane lane, SemionTowerEntity towerEntity, double healAmount) {
        onStateChanged(lane);
        heal(towerEntity, healAmount);
        onStateChanged(lane);
    }

    private void tickAwakeningVfx(PlayerLane lane) {
        if (!state.awakenedThisRound()) {
            awakeningVfxTicks = 0;
            return;
        }
        if (lane == null || lane.arenaWorld() == null) {
            return;
        }
        awakeningVfxTicks++;
        entityId().ifPresent(id -> {
            var entity = lane.arenaWorld().getEntity(id);
            if (!(entity instanceof SemionTowerEntity towerEntity) || !towerEntity.isAlive()) {
                return;
            }
            if (awakeningVfxTicks % 2 == 0) {
                TowerVfxService.showWarlockAwakeningAura(towerEntity);
            }
            if (awakeningVfxTicks % 10 == 0) {
                TowerVfxService.showWarlockAwakeningSparkBurst(towerEntity);
            }
        });
    }

    private void setAwakeningGlow(PlayerLane lane, boolean glowing) {
        if (lane == null || lane.arenaWorld() == null) {
            return;
        }

        entityId().ifPresent(id -> {
            var entity = lane.arenaWorld().getEntity(id);
            if (entity instanceof SemionTowerEntity towerEntity) {
                towerEntity.setGlowingTag(glowing);
            }
        });
    }

    private double passiveHealthBonus() {
        return sacrifices.passiveHealthBonus(this, currentLane);
    }

    private double passiveDamageBonus() {
        return sacrifices.passiveDamageBonus(this, currentLane);
    }

    double damageReduction() {
        return sacrifices.damageReduction(this);
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
            if (!(entity instanceof SemionTowerEntity towerEntity) || !towerEntity.isAlive()) {
                return;
            }
            tryAwaken(lane, towerEntity);
        });
    }

    boolean is(TowerType towerType) {
        return type().id().equals(towerType.id());
    }

    int totalSacrificeCount() {
        return state.totalSacrificeCount();
    }

    int roundSacrificeCount() {
        return state.roundSacrificeCount();
    }

    double rawDamageBonus() {
        return state.permanentDamageBonus() + state.roundDamageBonus();
    }

    double effectiveDamageBonus() {
        return scaledDamageBonus(type(), rawDamageBonus());
    }

    static double scaledDamageBonus(TowerType type, double rawDamageBonus) {
        if (!isLogScaled(type)) {
            return finiteNonNegative(rawDamageBonus);
        }
        return LogarithmicScaling.logarithmicBonus(
                rawDamageBonus,
                WarlockConfig.RUNTIME.value(damageThreshold(type)),
                WarlockConfig.RUNTIME.value(damageScale(type))
        );
    }

    double rawHealthBonus() {
        return state.permanentHealthBonus() + state.roundHealthBonus();
    }

    double effectiveHealthBonus() {
        return scaledHealthBonus(type(), rawHealthBonus());
    }

    static double scaledHealthBonus(TowerType type, double rawHealthBonus) {
        if (!isLogScaled(type)) {
            return finiteNonNegative(rawHealthBonus);
        }
        return LogarithmicScaling.logarithmicBonus(
                rawHealthBonus,
                WarlockConfig.RUNTIME.value(healthThreshold(type)),
                WarlockConfig.RUNTIME.value(healthScale(type))
        );
    }

    private static boolean isLogScaled(TowerType type) {
        return type != null && (type.id().equals(WarlockTowers.RANGED_WARLOCK_TOWER.id())
                || type.id().equals(WarlockTowers.MELEE_WARLOCK_TOWER.id()));
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static WarlockConfig.Ability damageThreshold(TowerType type) {
        if (type.id().equals(WarlockTowers.RANGED_WARLOCK_TOWER.id())) {
            return RANGED_DAMAGE_THRESHOLD;
        }
        if (type.id().equals(WarlockTowers.MELEE_WARLOCK_TOWER.id())) {
            return MELEE_DAMAGE_THRESHOLD;
        }
        throw new IllegalArgumentException("Damage logarithmic scaling is not configured for: " + type.id());
    }

    private static WarlockConfig.Ability damageScale(TowerType type) {
        if (type.id().equals(WarlockTowers.RANGED_WARLOCK_TOWER.id())) {
            return RANGED_DAMAGE_SCALE;
        }
        if (type.id().equals(WarlockTowers.MELEE_WARLOCK_TOWER.id())) {
            return MELEE_DAMAGE_SCALE;
        }
        throw new IllegalArgumentException("Damage logarithmic scaling is not configured for: " + type.id());
    }

    private static WarlockConfig.Ability healthThreshold(TowerType type) {
        if (type.id().equals(WarlockTowers.RANGED_WARLOCK_TOWER.id())) {
            return RANGED_HEALTH_THRESHOLD;
        }
        if (type.id().equals(WarlockTowers.MELEE_WARLOCK_TOWER.id())) {
            return MELEE_HEALTH_THRESHOLD;
        }
        throw new IllegalArgumentException("Health logarithmic scaling is not configured for: " + type.id());
    }

    private static WarlockConfig.Ability healthScale(TowerType type) {
        if (type.id().equals(WarlockTowers.RANGED_WARLOCK_TOWER.id())) {
            return RANGED_HEALTH_SCALE;
        }
        if (type.id().equals(WarlockTowers.MELEE_WARLOCK_TOWER.id())) {
            return MELEE_HEALTH_SCALE;
        }
        throw new IllegalArgumentException("Health logarithmic scaling is not configured for: " + type.id());
    }

    double additionalHealth() {
        return Math.max(0.0, currentMaxHealth() - applyTraitMaxHealth(maxHealth()));
    }

    int attackIntervalReduction() {
        return Math.max(0, type().attackIntervalTicks() - adjustAttackInterval(type().attackIntervalTicks()));
    }

    int maximumAttackIntervalReduction() {
        int maximumByMinimumInterval = Math.max(
                0,
                type().attackIntervalTicks() - combat.minimumAttackIntervalTicks()
        );
        return Math.min(maximumByMinimumInterval, combat.maximumAttackIntervalReduction());
    }

    double maximumDamageReduction() {
        return sacrifices.maximumDamageReduction(this);
    }

    private double sacrificeRadius(WarlockConfig.Ability key) {
        double radius = ability(key);
        return radius <= 0.0 ? Double.MAX_VALUE : radius;
    }

    private double ability(WarlockConfig.Ability key) {
        return WarlockConfig.RUNTIME.value(key);
    }

    private double healthRatio(double currentHealth) {
        double maxHealth = currentMaxHealth();
        return maxHealth <= 0.0 ? 0.0 : currentHealth / maxHealth;
    }

}
