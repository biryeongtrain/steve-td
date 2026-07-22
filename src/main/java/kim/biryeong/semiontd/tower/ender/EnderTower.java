package kim.biryeong.semiontd.tower.ender;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

public final class EnderTower extends EntityBackedTower {
    public static final String CONFIG_ID = "ender_global";
    private static final double TRANSFER_PARTICLE_HEIGHT = 1.25;
    private static final double TRANSFER_COMPLETION_EPSILON = 1.0E-9;
    private static final TowerDataKey<EnderTowerState> STATE = TowerDataKey.of(
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "ender_tower_state"),
            EnderTowerState.class
    );
    private static final TowerDataKey<Double> TRANSFER_PROGRESS = TowerDataKey.of(
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "ender_transfer_progress"),
            Double.class
    );

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

    public EnderTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
        initializeState();
    }

    public EnderTower(
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

    public EnderTowerState state() {
        return getDataOrDefault(STATE, EnderTowerState.EGG);
    }

    @Override
    public EntityVisual visual() {
        if (!isCoreTower()) {
            return super.visual();
        }
        return switch (state()) {
            case EGG -> EnderTowers.DRAGON_EGG_VISUAL;
            case PHANTOM -> EnderTowers.PHANTOM_VISUAL;
            case DRAGON -> EnderTowers.DRAGON_VISUAL;
        };
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        if (!isCoreTower()) {
            return;
        }
        waveActive = true;
        if (isEgg()) {
            switchToPhantom(lane);
        } else if (lane != null) {
            onStateChanged(lane);
        }
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        waveActive = false;
        resetRoundTransferBonuses(lane);
        if (isCoreTower()) {
            setData(STATE, EnderTowerState.EGG);
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
        resetTransferProgress(lane);
        super.onRemoved(lane);
    }

    @Override
    public void onDeath(PlayerLane lane) {
        resetTransferProgress(lane);
        super.onDeath(lane);
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
        return isDragon() ? baseRange + Math.max(0.0, global("dragonAttackRangeBonus")) : baseRange;
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
        double absorbedDamage = damageAmount * (1.0 + (permanentDamageBonus + roundDamageBonus) / type().damage());
        return isDragon() ? absorbedDamage * (1.0 + Math.max(0.0, global("dragonDamageBonus"))) : absorbedDamage;
    }

    @Override
    public double modifyIncomingDamage(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount) {
        if (EnderTowers.isShulkerLine(type())) {
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
        if (!isCoreTower()) {
            ArrayList<String> lines = new ArrayList<>();
            lines.add("엔더 드래곤에게 힘 전달 대기 중");
            lines.add("힘 전달 진행률 " + percent(transferProgress()) + " (라운드 이월)");
            if (EnderTowers.isShulkerLine(type()) && shulkerDamageReduction() > 0.0) {
                lines.add("받는 피해 감소 " + percent(shulkerDamageReduction()));
            }
            return lines;
        }
        ArrayList<String> lines = new ArrayList<>();
        if (isEgg()) {
            lines.add("준비 중: 드래곤 알");
        } else if (isPhantom()) {
            lines.add("팬텀 단계");
            lines.add("드래곤 진화: 최대 체력 " + oneDecimal(currentMaxHealth()) + " / " + oneDecimal(dragonEvolutionMaxHealth()) + " 이상 필요");
        } else {
            lines.add("드래곤 진화 완료");
        }
        lines.add("누적 스택: 엔드 수정 " + absorbedEndCrystalCount + " / 셜커 " + absorbedShulkerCount);
        if (!isEgg()) {
            lines.add("이번 라운드 힘 전달: " + roundCompletedTransferCount);
            lines.add("이번 라운드: 체력 +" + oneDecimal(roundHealthBonus) + ", 공격력 +" + oneDecimal(roundDamageBonus));
        }
        lines.add("영구 누적: 체력 +" + oneDecimal(permanentHealthBonus)
                + ", 공격력 +" + oneDecimal(permanentDamageBonus)
                + " / +" + oneDecimal(permanentDamageBonusCap()));
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
        if (!(previousTower instanceof EnderTower enderTower) || !isCoreTower()) {
            return;
        }
        permanentHealthBonus = enderTower.permanentHealthBonus;
        permanentDamageBonus = enderTower.permanentDamageBonus;
        roundHealthContribution = enderTower.roundHealthContribution;
        roundDamageContribution = enderTower.roundDamageContribution;
        roundHealthBonus = enderTower.roundHealthBonus;
        roundDamageBonus = enderTower.roundDamageBonus;
        syncedPermanentHealthBonus = enderTower.syncedPermanentHealthBonus;
        syncedPermanentDamageBonus = enderTower.syncedPermanentDamageBonus;
        absorbedEndCrystalCount = enderTower.absorbedEndCrystalCount;
        absorbedShulkerCount = enderTower.absorbedShulkerCount;
        roundCompletedTransferCount = enderTower.roundCompletedTransferCount;
        waveActive = enderTower.waveActive;
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

    double transferProgress() {
        return Math.max(0.0, Math.min(1.0, getDataOrDefault(TRANSFER_PROGRESS, 0.0)));
    }

    private void absorbAlliedEnderTowers(PlayerLane lane) {
        if (lane == null) {
            return;
        }
        double excessHealthHealing = 0.0;
        boolean countsChanged = false;
        double progressPerTick = 1.0 / Math.max(1, globalTicks("absorptionDurationTicks"));
        for (Tower source : List.copyOf(lane.towers())) {
            if (!isEligibleAbsorptionTarget(source)) {
                continue;
            }
            double previousRatio = transferProgress(source);
            double nextRatio = Math.min(1.0, previousRatio + progressPerTick);
            if (1.0 - nextRatio < TRANSFER_COMPLETION_EPSILON) {
                nextRatio = 1.0;
            }
            excessHealthHealing += applyTransferProgress(source, nextRatio - previousRatio);
            source.setData(TRANSFER_PROGRESS, nextRatio);
            if (nextRatio < 1.0) {
                showTransferParticles(lane, source);
                continue;
            }
            if (!lane.killTower(source)) {
                continue;
            }
            source.removeData(TRANSFER_PROGRESS);
            roundCompletedTransferCount++;
            int tier = EnderTowers.absorptionTier(source.type());
            if (EnderTowers.isEndCrystalLine(source.type())) {
                absorbedEndCrystalCount += tier;
            } else if (EnderTowers.isShulkerLine(source.type())) {
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
        permanentDamageBonus = Math.min(permanentDamageBonusCap(), Math.max(0.0, permanentDamageBonus));
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
                && tower.ownerPlayer().equals(ownerPlayer())
                && tower.health() > 0.0
                && EnderTowers.isAbsorbableTower(tower.type());
    }

    private static double transferProgress(Tower tower) {
        return Math.max(0.0, Math.min(1.0, tower.getDataOrDefault(TRANSFER_PROGRESS, 0.0)));
    }

    private double applyTransferProgress(Tower source, double delta) {
        if (delta <= 0.0) {
            return 0.0;
        }
        boolean endCrystalLine = EnderTowers.isEndCrystalLine(source.type());
        boolean shulkerLine = EnderTowers.isShulkerLine(source.type());
        double sourceHealth = source.effectBaseMaxHealth();
        double sourceDamage = source.type().damage();
        double transferredRoundHealth = shulkerLine
                ? sourceHealth * Math.max(0.0, global("roundHealthRatio")) * delta
                : 0.0;
        double maximumRoundHealthBonus = type().maxHealth() * Math.max(0.0, global("roundStatBonusCapRatio"));
        double cappedHealthBefore = Math.min(maximumRoundHealthBonus, Math.max(0.0, roundHealthContribution));
        roundHealthContribution += transferredRoundHealth;
        double cappedHealthAfter = Math.min(maximumRoundHealthBonus, Math.max(0.0, roundHealthContribution));
        double appliedToMaxHealth = Math.max(0.0, cappedHealthAfter - cappedHealthBefore);
        if (endCrystalLine) {
            roundDamageContribution += sourceDamage * Math.max(0.0, global("roundDamageRatio")) * delta;
            permanentDamageBonus = Math.min(
                    permanentDamageBonusCap(),
                    permanentDamageBonus + sourceDamage * Math.max(0.0, global("permanentDamageRatio")) * delta
            );
        }
        if (shulkerLine) {
            permanentHealthBonus += sourceHealth * Math.max(0.0, global("permanentHealthRatio")) * delta;
        }
        return Math.max(0.0, transferredRoundHealth - appliedToMaxHealth);
    }

    private void resetTransferProgress(PlayerLane lane) {
        if (!isCoreTower()) {
            removeData(TRANSFER_PROGRESS);
            return;
        }
        if (lane == null) {
            return;
        }
        for (Tower tower : lane.towers()) {
            if (tower != this
                    && tower.ownerPlayer().equals(ownerPlayer())
                    && EnderTowers.isAbsorbableTower(tower.type())) {
                tower.removeData(TRANSFER_PROGRESS);
            }
        }
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
        setData(STATE, EnderTowerState.PHANTOM);
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
        setData(STATE, EnderTowerState.DRAGON);
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
        return isCoreTower() && state() == EnderTowerState.DRAGON;
    }

    private boolean isPhantom() {
        return isCoreTower() && state() == EnderTowerState.PHANTOM;
    }

    private boolean isHatched() {
        return isPhantom() || isDragon();
    }

    private boolean isEgg() {
        return isCoreTower() && state() == EnderTowerState.EGG;
    }

    private boolean isCoreTower() {
        return EnderTowers.isBaseEnderTower(type());
    }

    private void initializeState() {
        if (isCoreTower()) {
            setData(STATE, EnderTowerState.EGG);
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

    private double permanentDamageBonusCap() {
        return Math.max(0.0, global("permanentDamageBonusCap"));
    }
}
