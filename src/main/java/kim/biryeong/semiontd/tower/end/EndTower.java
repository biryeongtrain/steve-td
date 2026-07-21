package kim.biryeong.semiontd.tower.end;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

public final class EndTower extends EntityBackedTower {
    public static final String CONFIG_ID = "ender_global";
    private static final double TRANSFER_PARTICLE_HEIGHT = 1.25;
    private static final TowerDataKey<EndTowerState> STATE = TowerDataKey.of(
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "ender_tower_state"),
            EndTowerState.class
    );

    private final Map<Tower, AbsorptionProgress> absorptionProgress = new IdentityHashMap<>();
    private final Set<Tower> completedTransferSources = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean waveActive;
    private double permanentHealthBonus;
    private double permanentDamageBonus;
    private double roundHealthContribution;
    private double roundDamageContribution;
    private double roundHealthBonus;
    private double roundDamageBonus;
    private double syncedPermanentHealthBonus;
    private double syncedPermanentDamageBonus;
    private int absorbedEndCrystalCount;
    private int absorbedShulkerCount;
    private int roundCompletedTransferCount;

    public EndTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
        initializeState();
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
        if (!isCoreTower()) {
            return;
        }
        waveActive = true;
        absorptionProgress.clear();
        completedTransferSources.clear();
        if (isEgg()) {
            switchToPhantom(lane);
        } else if (lane != null) {
            onStateChanged(lane);
        }
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        waveActive = false;
        absorptionProgress.clear();
        resetRoundTransferBonuses(lane);
        if (isCoreTower()) {
            setData(STATE, EndTowerState.EGG);
            syncMaxHealth(effectBaseMaxHealth(), false);
        }
        super.resetForRound(lane);
    }

    void resetRoundTransferBonuses(PlayerLane lane) {
        roundHealthContribution = 0.0;
        roundDamageContribution = 0.0;
        roundCompletedTransferCount = 0;
        refreshAbsorbedStats(lane);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        super.onRemoved(lane);
    }

    @Override
    public void refreshType(TowerType type, PlayerLane lane) {
        super.refreshType(type, lane);
        if (!isHatched()) {
            return;
        }
        if (refreshAbsorbedStats(lane)) {
            return;
        }
        Optional<SemionTowerEntity> entity = towerEntity(lane);
        if (entity.isPresent()) {
            entity.get().refreshMaxHealthEffects();
        } else {
            syncMaxHealth(effectBaseMaxHealth(), false);
        }
        onStateChanged(lane);
    }

    @Override
    public void tick(PlayerLane lane) {
        if (isDestroyed(lane)) {
            return;
        }
        if (waveActive && isHatched()) {
            absorbAlliedEnderTowers(lane);
            evolveToDragonIfReady(lane);
        }
        super.tick(lane);
    }

    @Override
    public double effectBaseMaxHealth() {
        return isHatched() ? type().maxHealth() + permanentHealthBonus + roundHealthBonus : super.effectBaseMaxHealth();
    }

    @Override
    public double adjustAttackRange(double baseRange) {
        if (isEgg()) {
            return 0.0;
        }
        return baseRange;
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        if (!isHatched()) {
            return baseIntervalTicks;
        }
        int minimumInterval = Math.max(1, globalInt("minimumAttackIntervalTicks"));
        return Math.max(minimumInterval, baseIntervalTicks - attackIntervalReduction());
    }

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        if (!isHatched() || type().damage() <= 0.0) {
            return damageAmount;
        }
        return damageAmount * (1.0 + (permanentDamageBonus + roundDamageBonus) / type().damage());
    }

    @Override
    public double modifyIncomingDamage(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount) {
        if (EndTowers.isShulkerLine(type())) {
            return damageAmount * Math.max(0.0, 1.0 - shulkerDamageReduction());
        }
        if (!isHatched()) {
            return damageAmount;
        }
        return damageAmount * Math.max(0.0, 1.0 - damageReduction());
    }

    @Override
    public void onAttack(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount, boolean killedTarget) {
        if (!isHatched() || towerEntity == null || target == null) {
            return;
        }
        applySplashDamage(towerEntity, target, damageAmount);
        heal(towerEntity, damageAmount * lifeStealRatio());
    }

    @Override
    public List<String> runtimeDetailLines() {
        if (isEgg()) {
            return List.of("준비 중: 드래곤 알");
        }
        if (!isCoreTower()) {
            ArrayList<String> lines = new ArrayList<>();
            lines.add("엔더 드래곤에게 힘 전달 대기 중");
            if (EndTowers.isShulkerLine(type()) && shulkerDamageReduction() > 0.0) {
                lines.add("받는 피해 감소 " + percent(shulkerDamageReduction()));
            }
            return lines;
        }
        ArrayList<String> lines = new ArrayList<>();
        if (isPhantom()) {
            lines.add("팬텀 단계");
            lines.add("드래곤 진화: 최대 체력 " + oneDecimal(currentMaxHealth()) + " / " + oneDecimal(dragonEvolutionMaxHealth()) + " 이상 필요");
        } else {
            lines.add("드래곤 진화 완료");
        }
        lines.add("누적 스택: 엔드 수정 " + absorbedEndCrystalCount + " / 셜커 " + absorbedShulkerCount);
        lines.add("이번 라운드 힘 전달: " + roundCompletedTransferCount);
        lines.add("이번 라운드: 체력 +" + oneDecimal(roundHealthBonus) + ", 공격력 +" + oneDecimal(roundDamageBonus));
        lines.add("영구 누적: 체력 +" + oneDecimal(permanentHealthBonus) + ", 공격력 +" + oneDecimal(permanentDamageBonus));
        if (splashRadius() > 0.0) {
            lines.add("광역 공격 반경 " + oneDecimal(splashRadius()));
        }
        if (attackIntervalReduction() > 0) {
            lines.add("공격 주기 -" + attackIntervalReduction() + "틱");
        }
        if (lifeStealRatio() > 0.0) {
            lines.add("생명력 흡수 " + percent(lifeStealRatio()));
        }
        if (damageReduction() > 0.0) {
            lines.add("받는 피해 감소 " + percent(damageReduction()));
        }
        return lines;
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (!(previousTower instanceof EndTower endTower) || !isCoreTower()) {
            return;
        }
        permanentHealthBonus = endTower.permanentHealthBonus;
        permanentDamageBonus = endTower.permanentDamageBonus;
        roundHealthContribution = endTower.roundHealthContribution;
        roundDamageContribution = endTower.roundDamageContribution;
        roundHealthBonus = endTower.roundHealthBonus;
        roundDamageBonus = endTower.roundDamageBonus;
        syncedPermanentHealthBonus = endTower.syncedPermanentHealthBonus;
        syncedPermanentDamageBonus = endTower.syncedPermanentDamageBonus;
        absorbedEndCrystalCount = endTower.absorbedEndCrystalCount;
        absorbedShulkerCount = endTower.absorbedShulkerCount;
        roundCompletedTransferCount = endTower.roundCompletedTransferCount;
        completedTransferSources.addAll(endTower.completedTransferSources);
        waveActive = endTower.waveActive;
    }

    public int absorbedEndCrystalCount() {
        return absorbedEndCrystalCount;
    }

    public int absorbedShulkerCount() {
        return absorbedShulkerCount;
    }

    public int roundCompletedTransferCount() {
        return roundCompletedTransferCount;
    }

    public double absorbedHealthBonus() {
        return permanentHealthBonus + roundHealthBonus;
    }

    public double absorbedDamageBonus() {
        return permanentDamageBonus + roundDamageBonus;
    }

    public double permanentHealthBonus() {
        return permanentHealthBonus;
    }

    public double permanentDamageBonus() {
        return permanentDamageBonus;
    }

    public double roundHealthBonus() {
        return roundHealthBonus;
    }

    public double roundDamageBonus() {
        return roundDamageBonus;
    }

    private void absorbAlliedEnderTowers(PlayerLane lane) {
        if (lane == null) {
            return;
        }
        for (Tower tower : List.copyOf(lane.towers())) {
            if (isEligibleAbsorptionTarget(tower)) {
                absorptionProgress.computeIfAbsent(tower, this::newAbsorptionProgress);
            }
        }

        List<Tower> completed = new ArrayList<>();
        double excessHealthHealing = 0.0;
        for (Map.Entry<Tower, AbsorptionProgress> entry : List.copyOf(absorptionProgress.entrySet())) {
            Tower source = entry.getKey();
            if (!lane.towers().contains(source) || source.health() <= 0.0) {
                absorptionProgress.remove(source);
                continue;
            }
            AbsorptionProgress progress = entry.getValue();
            progress.elapsedTicks++;
            excessHealthHealing += applyTransferProgress(progress);
            if (progress.elapsedTicks >= progress.durationTicks) {
                completed.add(source);
            } else {
                showTransferParticles(lane, source);
            }
        }

        boolean countsChanged = false;
        for (Tower source : completed) {
            AbsorptionProgress progress = absorptionProgress.remove(source);
            if (progress == null
                    || !lane.towers().contains(source)
                    || source.health() <= 0.0
                    || !lane.killTower(source)) {
                continue;
            }
            completedTransferSources.add(source);
            roundCompletedTransferCount++;
            int tier = EndTowers.absorptionTier(source.type());
            if (EndTowers.isEndCrystalLine(source.type())) {
                absorbedEndCrystalCount += tier;
            } else if (EndTowers.isShulkerLine(source.type())) {
                absorbedShulkerCount += tier;
            }
            countsChanged = true;
        }
        boolean statsChanged = refreshAbsorbedStats(lane);
        healExcessTransferredHealth(lane, excessHealthHealing);
        if (countsChanged && !statsChanged) {
            onStateChanged(lane);
        }
    }

    private boolean refreshAbsorbedStats(PlayerLane lane) {
        double capRatio = Math.max(0.0, global("roundStatBonusCapRatio"));
        double maximumHealthBonus = type().maxHealth() * capRatio;
        double maximumDamageBonus = type().damage() * capRatio;
        double nextRoundHealthBonus = Math.min(maximumHealthBonus, Math.max(0.0, roundHealthContribution));
        double nextRoundDamageBonus = Math.min(maximumDamageBonus, Math.max(0.0, roundDamageContribution));
        if (Math.abs(nextRoundHealthBonus - roundHealthBonus) < 1.0E-9
                && Math.abs(nextRoundDamageBonus - roundDamageBonus) < 1.0E-9
                && Math.abs(permanentHealthBonus - syncedPermanentHealthBonus) < 1.0E-9
                && Math.abs(permanentDamageBonus - syncedPermanentDamageBonus) < 1.0E-9) {
            return false;
        }
        roundHealthBonus = nextRoundHealthBonus;
        roundDamageBonus = nextRoundDamageBonus;
        syncedPermanentHealthBonus = permanentHealthBonus;
        syncedPermanentDamageBonus = permanentDamageBonus;
        Optional<SemionTowerEntity> entity = towerEntity(lane);
        if (entity.isPresent()) {
            entity.get().refreshMaxHealthEffects();
        } else {
            syncMaxHealth(effectBaseMaxHealth(), true);
        }
        onStateChanged(lane);
        return true;
    }

    private boolean isEligibleAbsorptionTarget(Tower tower) {
        return tower != null
                && tower != this
                && !completedTransferSources.contains(tower)
                && tower.ownerPlayer().equals(ownerPlayer())
                && tower.health() > 0.0
                && EndTowers.isAbsorbableTower(tower.type());
    }

    private AbsorptionProgress newAbsorptionProgress(Tower tower) {
        int durationTicks = Math.max(1, globalTicks("absorptionDurationTicks"));
        boolean endCrystalLine = EndTowers.isEndCrystalLine(tower.type());
        boolean shulkerLine = EndTowers.isShulkerLine(tower.type());
        double sourceHealth = tower.currentMaxHealth();
        double sourceDamage = tower.modifyAttackDamage(null, null, tower.type().damage());
        return new AbsorptionProgress(
                durationTicks,
                shulkerLine
                        ? sourceHealth * Math.max(0.0, global("roundHealthRatio"))
                        : 0.0,
                endCrystalLine
                        ? sourceDamage * Math.max(0.0, global("roundDamageRatio"))
                        : 0.0,
                shulkerLine
                        ? sourceHealth * Math.max(0.0, global("permanentHealthRatio"))
                        : 0.0,
                endCrystalLine
                        ? sourceDamage * Math.max(0.0, global("permanentDamageRatio"))
                        : 0.0
        );
    }

    private double applyTransferProgress(AbsorptionProgress progress) {
        double ratio = Math.min(1.0, progress.elapsedTicks / (double) progress.durationTicks);
        double delta = Math.max(0.0, ratio - progress.appliedRatio);
        if (delta <= 0.0) {
            return 0.0;
        }
        progress.appliedRatio = ratio;
        double transferredRoundHealth = progress.roundHealthBonus * delta;
        double maximumRoundHealthBonus = type().maxHealth() * Math.max(0.0, global("roundStatBonusCapRatio"));
        double cappedHealthBefore = Math.min(maximumRoundHealthBonus, Math.max(0.0, roundHealthContribution));
        roundHealthContribution += transferredRoundHealth;
        double cappedHealthAfter = Math.min(maximumRoundHealthBonus, Math.max(0.0, roundHealthContribution));
        double appliedToMaxHealth = Math.max(0.0, cappedHealthAfter - cappedHealthBefore);
        roundDamageContribution += progress.roundDamageBonus * delta;
        permanentHealthBonus += progress.permanentHealthBonus * delta;
        permanentDamageBonus += progress.permanentDamageBonus * delta;
        return Math.max(0.0, transferredRoundHealth - appliedToMaxHealth);
    }

    private void healExcessTransferredHealth(PlayerLane lane, double amount) {
        if (amount <= 0.0) {
            return;
        }
        Optional<SemionTowerEntity> entity = towerEntity(lane);
        if (entity.isPresent()) {
            entity.get().receiveHealing(amount);
        } else {
            syncHealth(health() + amount);
        }
    }

    private void switchToPhantom(PlayerLane lane) {
        if (!isEgg()) {
            return;
        }
        setData(STATE, EndTowerState.PHANTOM);
        Optional<SemionTowerEntity> entity = towerEntity(lane);
        if (entity.isPresent()) {
            entity.get().refreshMaxHealthEffects();
        } else {
            syncMaxHealth(effectBaseMaxHealth(), true);
        }
        if (lane != null) {
            onStateChanged(lane);
        }
    }

    private void evolveToDragonIfReady(PlayerLane lane) {
        if (!isPhantom() || currentMaxHealth() < dragonEvolutionMaxHealth()) {
            return;
        }
        setData(STATE, EndTowerState.DRAGON);
        if (lane != null) {
            onStateChanged(lane);
        }
    }

    private void showTransferParticles(PlayerLane lane, Tower source) {
        ServerLevel level = lane.arenaWorld();
        if (level == null) {
            return;
        }
        Vec3 targetPosition = transferParticlePosition(this);
        Vec3 sourceOffset = transferParticlePosition(source).subtract(targetPosition);
        level.sendParticles(
                ParticleTypes.ENCHANT,
                targetPosition.x,
                targetPosition.y,
                targetPosition.z,
                0,
                sourceOffset.x,
                sourceOffset.y,
                sourceOffset.z,
                1.0
        );
    }

    private static Vec3 transferParticlePosition(Tower tower) {
        return new Vec3(
                tower.position().x() + 0.5,
                tower.position().y() + TRANSFER_PARTICLE_HEIGHT,
                tower.position().z() + 0.5
        );
    }

    public double splashRadius() {
        int every = Math.max(1, globalInt("endCrystalSplashEvery"));
        double value = absorbedEndCrystalCount / every * Math.max(0.0, global("splashRadiusPerStep"));
        return Math.min(Math.max(0.0, global("splashRadiusCap")), value);
    }

    private void applySplashDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        double radius = splashRadius();
        double damageRatio = Math.max(0.0, global("splashDamageRatio"));
        if (radius <= 0.0 || damageAmount <= 0.0 || damageRatio <= 0.0) {
            return;
        }
        ResourceLocation vfxStyle = isDragon() ? AreaVfxStyles.DRAGON_BREATH : AreaVfxStyles.SPLASH;
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(AreaEffectIds.tower(this, "splash"), towerEntity, target, radius, AreaVfxSpec.onTrigger(vfxStyle));
        TowerAreaDamage.apply(this, towerEntity, request, ignored -> damageAmount * damageRatio, true, (ignored, splashDamage, killed) -> heal(towerEntity, splashDamage * lifeStealRatio()));
    }

    private int attackIntervalReduction() {
        int every = Math.max(1, globalInt("endCrystalAttackIntervalEvery"));
        int permanentReduction = absorbedEndCrystalCount / every
                * Math.max(0, globalInt("attackIntervalReductionPerStep"));
        return Math.min(
                Math.max(0, globalInt("maxAttackIntervalReductionTicks")),
                permanentReduction
        );
    }

    private double lifeStealRatio() {
        int every = Math.max(1, globalInt("shulkerLifeStealEvery"));
        double value = absorbedShulkerCount / every * Math.max(0.0, global("lifeStealPerStep"));
        return Math.min(Math.max(0.0, global("lifeStealCap")), value);
    }

    private double damageReduction() {
        int every = Math.max(1, globalInt("shulkerReductionEvery"));
        double value = absorbedShulkerCount / every * Math.max(0.0, global("damageReductionPerStep"));
        return Math.min(Math.max(0.0, global("damageReductionCap")), value);
    }

    private double shulkerDamageReduction() {
        return Math.max(0.0, Math.min(1.0, TowerBalanceRuntime.ability(type().id(), "damageReduction")));
    }

    private void heal(SemionTowerEntity towerEntity, double amount) {
        if (amount > 0.0) {
            towerEntity.receiveHealing(amount);
        }
    }

    private Optional<SemionTowerEntity> towerEntity(PlayerLane lane) {
        if (lane == null || entityId().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(lane.arenaWorld().getEntity(entityId().getAsInt()))
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast);
    }

    private boolean isDragon() {
        return isCoreTower() && state() == EndTowerState.DRAGON;
    }

    private boolean isPhantom() {
        return isCoreTower() && state() == EndTowerState.PHANTOM;
    }

    private boolean isHatched() {
        return isPhantom() || isDragon();
    }

    private boolean isEgg() {
        return isCoreTower() && state() == EndTowerState.EGG;
    }

    private boolean isCoreTower() {
        return EndTowers.isBaseEnderTower(type());
    }

    private void initializeState() {
        if (isCoreTower()) {
            setData(STATE, EndTowerState.EGG);
        }
    }

    private double global(String key) {
        return TowerBalanceRuntime.ability(CONFIG_ID, key);
    }

    private int globalInt(String key) {
        return TowerBalanceRuntime.abilityInt(CONFIG_ID, key);
    }

    private int globalTicks(String key) {
        return TowerBalanceRuntime.abilityTicks(CONFIG_ID, key);
    }

    private double dragonEvolutionMaxHealth() {
        return Math.max(0.0, global("dragonEvolutionMaxHealth"));
    }

    private static final class AbsorptionProgress {
        private final int durationTicks;
        private final double roundHealthBonus;
        private final double roundDamageBonus;
        private final double permanentHealthBonus;
        private final double permanentDamageBonus;
        private int elapsedTicks;
        private double appliedRatio;

        private AbsorptionProgress(
                int durationTicks,
                double roundHealthBonus,
                double roundDamageBonus,
                double permanentHealthBonus,
                double permanentDamageBonus
        ) {
            this.durationTicks = durationTicks;
            this.roundHealthBonus = roundHealthBonus;
            this.roundDamageBonus = roundDamageBonus;
            this.permanentHealthBonus = permanentHealthBonus;
            this.permanentDamageBonus = permanentDamageBonus;
        }
    }

}
