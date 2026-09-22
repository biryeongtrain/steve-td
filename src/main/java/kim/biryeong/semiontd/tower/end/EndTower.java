package kim.biryeong.semiontd.tower.end;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;

public final class EndTower extends EntityBackedTower {
    public static final String CONFIG_ID = EndTowers.CONFIG_ID;
    private static final TowerDataKey<EndTowerState> STATE = TowerDataKey.of(ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "end_tower_state"), EndTowerState.class);
    private final EndConfig config;
    private final EndTransferController transfers;
    private final EndCombat combat;
    private final EndEvolutionController evolution;
    private final EndStatsAssembler stats;
    private boolean waveActive;
    private int regenerationTicks;
    private final EndAugments augments = new EndAugments();
    private FrozenCombat frozenCombat;
    private int augmentWave = Integer.MIN_VALUE;

    public EndTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        this(type, ownerPlayer, teamId, laneId, position, position);
    }

    public EndTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
        EndConfig config = EndConfig.RUNTIME;
        this.config = config;
        this.transfers = new EndTransferController(config);
        this.combat = new EndCombat(config);
        this.evolution = new EndEvolutionController(config, type.maxHealth());
        this.stats = new EndStatsAssembler(config, this.combat, this.evolution);
        initializeState();
    }

    public EndTowerState state() {
        return getDataOrDefault(STATE, EndTowerState.EGG);
    }

    @Override
    public EntityVisual visual() {
        if (!isCoreTower()) {
            return super.visual();
        }
        return switch (state()) {
            case EGG -> EndTowers.DRAGON_EGG_VISUAL;
            case PHANTOM -> EndTowers.PHANTOM_VISUAL;
            case DRAGON -> EndTowers.DRAGON_VISUAL;
        };
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        boolean newWave = !waveActive || augmentWave != currentRound;
        waveActive = true;
        if (frozenCombat != null) {return;}
        if (newWave) {
            augments.reset();
            augmentWave = currentRound;
        }
        if (!isCoreTower()) {
            return;
        }
        regenerationTicks = 0;
        if (transfers.rollbackIncomplete()) {
            refreshTransferStats(lane);
        }
        if (state() == EndTowerState.EGG) {
            hatch(lane);
        } else if (lane != null) {
            onStateChanged(lane);
        }
        if (newWave && lane != null && augmentSnapshot().has(EndAugments.TWIN)) {
            lane.addTower(createAugmentTwin(lane));
        }
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        waveActive = false;
        regenerationTicks = 0;
        augments.reset();
        clearTransferLifecycleState();
        resetRoundTransferBonuses(lane);
        if (isCoreTower()) {
            setData(STATE, EndTowerState.EGG);
            syncMaxHealth(effectBaseMaxHealth(), false);
            evolution.synchronize(type().maxHealth());
        }
        super.resetForRound(lane);
    }

    void resetRoundTransferBonuses(PlayerLane lane) {
        transfers.resetRound();
        refreshTransferStats(lane);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        if (lane == null || !lane.towers().contains(this)) {augments.reset();}
        clearTransferLifecycleState();
        super.onRemoved(lane);
    }

    @Override
    public void onDeath(PlayerLane lane) {
        augments.cancelBurst();
        clearTransferLifecycleState();
        super.onDeath(lane);
    }

    @Override
    public void refreshType(TowerType type, PlayerLane lane) {
        if (frozenCombat != null) {return;}
        if (type == null || !type().id().equals(type.id())) {
            return;
        }
        if (isCoreTower() && transfers.rollbackIncomplete()) {
            refreshTransferStats(lane);
        }
        super.refreshType(type, lane);
        reconcileEvolutionState(lane);
    }

    @Override
    protected void refreshMaxHealthAfterTypeChange(PlayerLane lane) {
        if (!isCoreTower()) {
            super.refreshMaxHealthAfterTypeChange(lane);
            return;
        }
        if (!state().hatched()) {
            super.refreshMaxHealthAfterTypeChange(lane);
            evolution.synchronize(type().maxHealth());
            return;
        }
        Optional<SemionTowerEntity> entity = runtimeEntity(lane);
        if (entity.isPresent()) {
            entity.get().refreshMaxHealthEffects(false);
        } else {
            syncMaxHealth(effectBaseMaxHealth(), false);
        }
        evolution.synchronize(previewHatchedMaxHealth());
    }

    @Override
    public void tick(PlayerLane lane) {
        if (waveActive && frozenCombat == null) {augments.tickMines(this, lane);}
        if (isDestroyed(lane)) {
            return;
        }
        if (waveActive && isCoreTower() && state().hatched() && frozenCombat == null) {
            EndTransferController.TickResult result = transfers.tick(this, lane);
            for (Tower source : result.particleSources()) {
                EndVfx.transfer(lane, this, source);
            }
            if (result.statsChanged()) {
                refreshTransferStats(lane);
            }
            reconcileEvolutionState(lane);
            healTransferredHealth(lane, result.completionHealing());
            healTransferredHealth(lane, result.transferHealing());
            if (result.countsChanged()) {
                runtimeEntity(lane).ifPresent(SemionTowerEntity::refreshCombatStats);
            }
            augments.tick(this, lane);
        }
        if (waveActive && isCoreTower() && state().hatched()) {tickRegeneration(lane);}
        super.tick(lane);
    }

    @Override
    public double effectBaseMaxHealth() {
        if (frozenCombat != null) {return frozenCombat.maxHealth();}
        return isCoreTower() && state().hatched() ? previewHatchedMaxHealth() : super.effectBaseMaxHealth();
    }

    @Override
    protected double builderCurrentMaxHealth() {
        return frozenCombat == null ? super.builderCurrentMaxHealth() : frozenCombat.maxHealth();
    }

    public double previewHatchedMaxHealth() {
        return previewHatchedMaxHealth(transfers.progressionSnapshot());
    }

    public double previewHatchedAttackDamage() {
        if (frozenCombat != null) {return frozenCombat.attackDamage();}
        return type().damage() + progressionStats().totalDamageBonus();
    }

    public int previewHatchedAttackIntervalTicks() {
        if (frozenCombat != null) {return frozenCombat.intervalTicks();}
        return combat.attackInterval(type(), transfers.progressionSnapshot().stacks());
    }

    public double previewHatchedAttackRange() {
        if (frozenCombat != null) {return frozenCombat.range();}
        return combat.attackRange(type(), state(), transfers.progressionSnapshot().stacks());
    }

    @Override
    public double adjustAttackRange(double baseRange) {
        if (frozenCombat != null) {return frozenCombat.range();}
        EndTowerState state = state();
        if (isCoreTower() && state == EndTowerState.EGG) {
            return 0.0;
        }
        return baseRange
                + combat.attackRangeBonus(transfers.progressionSnapshot().stacks())
                + combat.dragonRangeBonus(state);
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        if (frozenCombat != null) {return frozenCombat.intervalTicks();}
        if (!isCoreTower() || !state().hatched()) {
            return baseIntervalTicks;
        }
        return combat.adjustAttackInterval(baseIntervalTicks, transfers.progressionSnapshot().stacks());
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        if (frozenCombat != null) {
            return type().damage() <= 0.0 ? frozenCombat.attackDamage()
                    : damageAmount * frozenCombat.attackDamage() / type().damage();
        }
        return isCoreTower() && state().hatched()
                ? combat.modifyAttackDamage(type(), progressionStats().totalDamageBonus(), damageAmount)
                : damageAmount;
    }

    @Override
    public double finalDamageBonus() {
        return combat.finalDamageBonus(state());
    }

    @Override
    public double modifyIncomingDamage(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount) {
        if (EndTowers.isShulkerLine(type())) {
            return damageAmount * Math.max(0.0, 1.0 - combat.shulkerDamageReduction(type()));
        }
        if (!isCoreTower() || !state().hatched()) {
            return damageAmount;
        }
        return damageAmount * Math.max(
                0.0,
                1.0 - combat.damageReduction(transfers.progressionSnapshot().stacks())
        );
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
        if (!isCoreTower() || !state().hatched() || towerEntity == null || target == null) {
            return;
        }
        List<SemionMonsterEntity> secondaries = combat.resolveAttack(
                this,
                towerEntity,
                target,
                attemptedDamage,
                resolvedOutgoingDamage,
                dealtDamage,
                transfers.progressionSnapshot().stacks()
        );
        if (frozenCombat == null && dealtDamage > 0.0 && AugmentCombat.allowsTriggers()) {
            augments.onAttack(this, towerEntity, target, secondaries, attemptedDamage);
        }
    }

    @Override
    public List<String> runtimeDetailLines() {
        List<String> lines = new java.util.ArrayList<>(stats.create(this, waveActive, transfers.progressionSnapshot()));
        if (frozenCombat != null) {lines.add("쌍둥이 용: 생성 당시 형태와 능력치 고정");}
        else {lines.addAll(augments.details(this));}
        return List.copyOf(lines);
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (!(previousTower instanceof EndTower endTower)) {
            return;
        }
        waveActive = endTower.waveActive;
        augmentWave = endTower.augmentWave;
        if (!isCoreTower()) {
            EndTransferController.clearProgress(this);
            return;
        }
        transfers.copyCommittedFrom(endTower.transfers);
        regenerationTicks = endTower.regenerationTicks;
    }

    public EndTransferStats transferStats() {
        return progressionStats();
    }

    private void refreshTransferStats(PlayerLane lane) {
        Optional<SemionTowerEntity> entity = runtimeEntity(lane);
        if (entity.isPresent()) {entity.get().refreshMaxHealthEffects(false);}
        else {syncMaxHealth(effectBaseMaxHealth(), false);}
        evolution.synchronize(state().hatched() ? previewHatchedMaxHealth() : type().maxHealth());
    }

    private void healTransferredHealth(PlayerLane lane, double amount) {
        if (amount <= 0.0) {return;}
        Optional<SemionTowerEntity> entity = runtimeEntity(lane);
        if (entity.isPresent()) {healTarget(entity.get(), amount);}
        else {
            double before = health();
            syncHealth(before + amount);
            recordHealingDone(health() - before);
        }
    }

    private void hatch(PlayerLane lane) {
        if (!isCoreTower()) {return;}
        EndTowerState nextState = evolution.hatch(state());
        if (state() == nextState) {return;}
        setData(STATE, nextState);
        Optional<SemionTowerEntity> entity = runtimeEntity(lane);
        if (entity.isPresent()) {entity.get().refreshMaxHealthEffects();}
        else {syncMaxHealth(effectBaseMaxHealth(), true);}
        evolution.synchronize(previewHatchedMaxHealth());
        if (lane != null) {onStateChanged(lane);}
    }

    private void reconcileEvolutionState(PlayerLane lane) {
        if (!isCoreTower() || !state().hatched()) {return;}
        EndTowerState nextState = evolution.reconcile(
                state(),
                previewEvolutionMaxHealth(transfers.progressionSnapshot())
        );
        if (state() == nextState) {return;}
        setData(STATE, nextState);
        if (lane != null) {onStateChanged(lane);}
    }

    public double splashRadius() {
        return isCoreTower()
                ? combat.splashRadius(state(), transfers.progressionSnapshot().stacks())
                : 0.0;
    }

    private void tickRegeneration(PlayerLane lane) {
        double healing = combat.regenerationPerSecond(transfers.progressionSnapshot().stacks());
        if (healing <= 0.0) {regenerationTicks = 0;return;}
        int intervalTicks = combat.regenerationTicks();
        regenerationTicks++;
        if (regenerationTicks < intervalTicks) {return;}
        regenerationTicks %= intervalTicks;
        healTransferredHealth(lane, healing);
    }

    public boolean stopsBeforeFriendlyTowers() {
        return isCoreTower() && state() == EndTowerState.PHANTOM;
    }

    boolean isCoreTower() {
        return EndTowers.isBaseEndTower(type());
    }

    @Override
    protected double entityAnchorYOffset() {
        return isCoreTower() && state().hatched() ? 2.0 : 1.0;
    }

    private void initializeState() {
        if (isCoreTower()) {setData(STATE, EndTowerState.EGG);}
    }

    public double phantomScaleForMaxHealth(double maxHealth) {
        return evolution.phantomScale(maxHealth);
    }

    double previewEvolutionMaxHealth(EndTransferSnapshot progression) {
        double projectedBaseMaxHealth = evolution.projectedBaseMaxHealth(
                maxHealth(),
                previewHatchedMaxHealth(progression)
        );
        return applyTraitMaxHealth(projectedBaseMaxHealth);
    }

    private double previewHatchedMaxHealth(EndTransferSnapshot progression) {
        if (frozenCombat != null) {return frozenCombat.maxHealth();}
        return evolution.progressionMaxHealth(type(), progression);
    }

    double transferDurationMultiplier() {
        return augmentSnapshot().has(EndAugments.GROWTH)
                ? augmentSnapshot().parameter(EndAugments.GROWTH, "durationMultiplier", .50) : 1.0;
    }

    void onTransferCompleted(PlayerLane lane, Tower source, double sourceMaxHealth) {
        if (frozenCombat == null && AugmentCombat.allowsTriggers()) {
            augments.onTransferCompleted(this, source, sourceMaxHealth);
        }
    }

    EndTower createAugmentTwin(PlayerLane lane) {
        double ratio = augmentSnapshot().parameter(EndAugments.TWIN, "statRatio", .50);
        SemionTowerEntity entity = runtimeEntity(lane).orElse(null);
        EndTower twin = new EndTower(type(), ownerPlayer(), teamId(), laneId(), position());
        twin.setData(STATE, state());
        twin.transfers.copyCommittedFrom(transfers);
        twin.frozenCombat = new FrozenCombat(currentMaxHealth() * ratio,
                (entity == null ? previewHatchedAttackDamage() : entity.attackDamageAmount(null)) * ratio,
                entity == null ? previewHatchedAttackRange() : entity.attackRange(),
                entity == null ? previewHatchedAttackIntervalTicks() : entity.attackIntervalTicks());
        twin.waveActive = true;
        twin.markTemporaryCopy(logicalId());
        twin.syncMaxHealth(twin.frozenCombat.maxHealth(), true);
        return twin;
    }

    record FrozenCombat(double maxHealth, double attackDamage, double range, int intervalTicks) {}

    private EndTransferStats progressionStats() {
        return transfers.progressionSnapshot().resolve(
                config.healthScaling(),
                config.damageScaling()
        );
    }

    private void clearTransferLifecycleState() {
        transfers.rollbackIncomplete();
        EndTransferController.clearProgress(this);
    }
}
