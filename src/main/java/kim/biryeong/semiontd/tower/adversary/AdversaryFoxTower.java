package kim.biryeong.semiontd.tower.adversary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Runtime combat implementation for one Adversary fox. */
public final class AdversaryFoxTower extends EntityBackedTower {
    private static final double LINE_HALF_WIDTH = 0.75;
    private static final TowerDataKey<UUID> FOX_ID = TowerDataKey.of(
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "adversary/fox_id"),
            UUID.class
    );

    private FoxForm form;
    private PlayerLane currentLane;
    private boolean normalEntityHealthSyncPending;
    private boolean unscaledEntityDamagePending;
    private double unscaledEntityDamageLogicalHealth;
    private double rivalHealingThisWave;

    private UUID goldenTargetId;
    private int goldenTargetHits;
    private int shieldCounterCooldownTicks;

    private UUID spyglassTargetId;
    private int spyglassTargetHits;
    private UUID echoTargetId;
    private int echoTargetHits;

    private UUID maceTargetId;
    private int maceTicksUntilStrike = -1;
    private int maceSuccessfulStrikes;
    private double maceFocusDamageTaken;

    private final List<PendingSculkBlast> pendingSculkBlasts = new ArrayList<>();

    public AdversaryFoxTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
        GridPosition position
    ) {
        super(type, ownerPlayer, teamId, laneId, position);
        initializeForm(type);
    }

    public AdversaryFoxTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
        initializeForm(type);
    }

    public FoxForm form() {
        return form;
    }

    @Override
    public DamageType primaryDamageType() {
        return form == FoxForm.SCULK_CORE ? DamageType.MAGIC : DamageType.PHYSICAL;
    }

    public UUID foxId() {
        return getDataOrDefault(FOX_ID, ownerPlayer());
    }

    /**
     * Applies a progression-selected form without replacing the logical tower entity.
     * Health ratio is kept across promotion and demotion, while all per-attack state is reset.
     */
    public void setForm(FoxForm nextForm, PlayerLane lane) {
        FoxForm resolved = nextForm == null ? FoxForm.BASE : nextForm;
        if (resolved == form) {
            return;
        }
        double previousMaximum = Math.max(1.0, currentMaxHealth());
        double healthRatio = Math.max(0.0, Math.min(1.0, health() / previousMaximum));
        form = resolved;
        resetTransientCombatState();

        syncMaxHealth(resolved.maxHealth(), false);
        SemionTowerEntity entity = towerEntity(lane);
        if (entity != null) {
            entity.refreshMaxHealthEffects(false);
        }
        super.syncHealth(currentMaxHealth() * healthRatio);
        super.onStateChanged(lane);
        equipHeldItem(towerEntity(lane));
    }

    @Override
    public double effectBaseMaxHealth() {
        return form.maxHealth();
    }

    @Override
    protected void refreshMaxHealthAfterTypeChange(PlayerLane lane) {
        SemionTowerEntity entity = towerEntity(lane);
        if (entity != null) {
            entity.refreshMaxHealthEffects(false);
        } else {
            syncMaxHealth(effectBaseMaxHealth(), false);
        }
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        currentLane = lane;
        AdversaryProgressStates.state(ownerPlayer()).registerFox(foxId(), form);
        super.onPlaced(lane);
    }

    @Override
    public void onSold(PlayerLane lane) {
        AdversaryProgressStates.state(ownerPlayer()).unregisterFox(foxId());
    }

    @Override
    public boolean meetsUpgradeRequirements(PlayerLane lane, TowerUpgradeOption option) {
        FoxForm target = option == null
                ? null
                : AdversaryTowers.foxForm(option.targetType()).orElse(null);
        return target != null && AdversaryProgressStates.state(ownerPlayer())
                .canEvolve(foxId(), form, target);
    }

    @Override
    public void onUpgradeCompleted(PlayerLane lane, Tower previousTower, TowerUpgradeOption option) {
        FoxForm previous = previousTower instanceof AdversaryFoxTower fox ? fox.form() : null;
        AdversaryProgressStates.state(ownerPlayer()).commitEvolution(foxId(), previous, form);
    }

    @Override
    protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) {
        equipHeldItem(entity);
    }

    @Override
    public void onStateChanged(PlayerLane lane) {
        currentLane = lane;
        super.onStateChanged(lane);
        equipHeldItem(towerEntity(lane));
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        currentLane = lane;
        resetTransientCombatState();
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        currentLane = lane;
        resetTransientCombatState();
        super.resetForRound(lane);
    }

    @Override
    public void tick(PlayerLane lane) {
        currentLane = lane;
        super.tick(lane);
        if (shieldCounterCooldownTicks > 0) {
            shieldCounterCooldownTicks--;
        }

        SemionTowerEntity entity = towerEntity(lane);
        if (entity == null || !entity.isAlive() || entity.isRemoved()) {
            return;
        }
        if (form == FoxForm.MACE_EXECUTIONER) {
            tickMace(entity);
        } else {
            resetMace();
        }
        tickSculkBlasts(entity);
        AdversaryTeamEffects.tick(this, entity);
        AdversaryVfx.showSupportPulse(entity, form);
    }

    @Override
    public Optional<SemionMonsterEntity> selectAttackTarget(
            SemionTowerEntity towerEntity,
            List<SemionMonsterEntity> candidates
    ) {
        if (towerEntity == null || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        List<SemionMonsterEntity> eligible = finalDefenseAttackableCandidates(
                towerEntity,
                candidates
        );
        List<SemionMonsterEntity> rivals = ownedRivals(eligible);
        if (!rivals.isEmpty()) {
            return selectHighestProgress(rivals);
        }
        return switch (form) {
            case TRACKER, FIREWORK_PIERCER -> selectHighestProgress(eligible);
            case BIG_GAME_TRACKER -> selectBigGameTarget(eligible)
                    .or(() -> selectHighestProgress(eligible));
            default -> Optional.empty();
        };
    }

    @Override
    public boolean supportsForcedAttackTargeting() {
        // This is deliberately always enabled so the fox can abandon a cached normal target
        // the moment one of its own rival proxies becomes attackable.
        return true;
    }

    @Override
    public Optional<SemionMonsterEntity> selectForcedAttackTarget(
            SemionTowerEntity towerEntity,
            List<SemionMonsterEntity> candidates
    ) {
        if (towerEntity == null || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        List<SemionMonsterEntity> eligible = finalDefenseAttackableCandidates(
                towerEntity,
                candidates
        );
        List<SemionMonsterEntity> rivals = ownedRivals(eligible);
        if (!rivals.isEmpty()) {
            return selectHighestProgress(rivals);
        }
        return form == FoxForm.BIG_GAME_TRACKER
                ? selectBigGameTarget(eligible)
                : Optional.empty();
    }

    @Override
    public double adjustAttackRange(double ignoredBaseRange) {
        return form.range();
    }

    @Override
    public int adjustAttackInterval(int ignoredBaseIntervalTicks) {
        return Math.max(1, form.attackIntervalTicks());
    }

    @Override
    public double modifyAttackDamage(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double damageAmount
    ) {
        if (form == FoxForm.MACE_EXECUTIONER || form == FoxForm.SCULK_CORE) {
            return 0.0;
        }
        double baseTypeDamage = Math.max(0.000_001, type().damage());
        double adjusted = damageAmount * form.damage() / baseTypeDamage;
        if (form == FoxForm.FIREWORK_PIERCER) {
            adjusted *= isIncome(target)
                    ? global("fireworkIncomeDamageMultiplier", AdversaryBalance.FIREWORK_INCOME_DAMAGE_MULTIPLIER)
                    : global("fireworkWaveDamageMultiplier", AdversaryBalance.FIREWORK_WAVE_DAMAGE_MULTIPLIER);
        } else if (form == FoxForm.BIG_GAME_TRACKER) {
            boolean income = isIncome(target);
            adjusted *= income
                    ? global("bigGameIncomeDamageMultiplier", AdversaryBalance.BIG_GAME_INCOME_DAMAGE_MULTIPLIER)
                    : global("bigGameWaveDamageMultiplier", AdversaryBalance.BIG_GAME_WAVE_DAMAGE_MULTIPLIER);
            if (income) {
                adjusted *= spyglassMultiplier(target);
            }
        } else if (form == FoxForm.ECHO_FOX) {
            adjusted *= echoMultiplier(target);
        }
        return adjusted;
    }

    @Override
    public double modifyResolvedAttackDamage(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double damageAmount
    ) {
        return damageAmount * (1.0 + postEvolutionDamageBonus());
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
        if (towerEntity == null || target == null) {
            return;
        }
        if (form == FoxForm.MACE_EXECUTIONER) {
            beginOrMaintainMaceChannel(towerEntity, target);
            return;
        }
        if (form == FoxForm.SCULK_CORE) {
            scheduleSculkBlast(towerEntity, target.position());
            return;
        }
        // Chained hits and streaks represent successful attacks, not blocked or invalid rays.
        if (dealtDamage <= 0.0) {
            return;
        }
        if (usesEvolvedSplash(form)) {
            applyNearbySecondaries(
                    towerEntity,
                    target,
                    attemptedDamage,
                    global("baseSplashRadius", AdversaryBalance.BASE_SPLASH_RADIUS),
                    globalInt("baseSplashExtraTargets", AdversaryBalance.BASE_SPLASH_EXTRA_TARGETS),
                    global("evolvedSplashDamageRatio", AdversaryBalance.EVOLVED_SPLASH_DAMAGE_RATIO)
            );
        }
        switch (form) {
            case BASE -> applyNearbySecondaries(
                    towerEntity,
                    target,
                    attemptedDamage,
                    global("baseSplashRadius", AdversaryBalance.BASE_SPLASH_RADIUS),
                    globalInt("baseSplashExtraTargets", AdversaryBalance.BASE_SPLASH_EXTRA_TARGETS),
                    global("baseSplashDamageRatio", AdversaryBalance.BASE_SPLASH_DAMAGE_RATIO)
            );
            case BREEZE -> applyAdditionalTargets(
                    towerEntity,
                    target,
                    attemptedDamage,
                    globalInt("breezeExtraTargets", AdversaryBalance.BREEZE_EXTRA_TARGETS),
                    global(
                            "breezeExtraTargetDamageRatio",
                            AdversaryBalance.BREEZE_EXTRA_TARGET_DAMAGE_RATIO
                    )
            );
            case GOLDEN_FANG -> handleGoldenAttack(
                    towerEntity,
                    target,
                    resolvedOutgoingDamage,
                    killedTarget
            );
            case FIREWORK_PIERCER -> handleFireworkAttack(
                    towerEntity,
                    target,
                    attemptedDamage
            );
            case BIG_GAME_TRACKER -> {
                if (isIncome(target)) {
                    updateSpyglassChain(target, killedTarget);
                } else {
                    resetSpyglassChain();
                }
            }
            case ECHO_FOX -> updateEchoChain(target, killedTarget);
            default -> {
            }
        }
    }

    @Override
    public void onKill(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double damageAmount
    ) {
        if (target == null || target.runtimeMonster() == null) {
            return;
        }
        recordRivalKill(target.runtimeMonster());
    }

    @Override
    public void onIgniteKill(SemionMonsterEntity target) {
        if (target != null && target.runtimeMonster() != null) {
            recordRivalKill(target.runtimeMonster());
        }
    }

    @Override
    protected boolean countsDamageInRoundStatistics(SemionMonsterEntity target) {
        return target == null
                || target.runtimeMonster() == null
                || !AdversaryRivalTower.isOwnedRival(target.runtimeMonster(), ownerPlayer());
    }

    @Override
    public double modifyIncomingDamage(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double damageAmount
    ) {
        double focusFireReduction = focusFireDamageReduction(focusFireAttackerCount(towerEntity, damageSource));
        double remainingDamage = damageAmount
                * Math.max(0.0, 1.0 - form.damageReduction())
                * Math.max(0.0, 1.0 - focusFireReduction);
        if (remainingDamage <= 0.0) {
            normalEntityHealthSyncPending = false;
            return 0.0;
        }
        if (towerEntity == null || towerEntity.runtimeTower() != this) {
            normalEntityHealthSyncPending = false;
            return remainingDamage;
        }

        double entityHealthCapacity = Math.max(1.0, towerEntity.getMaxHealth());
        double virtualHealth = Math.max(0.0, health() - entityHealthCapacity);
        double absorbedByVirtualHealth = Math.min(remainingDamage, virtualHealth);
        if (absorbedByVirtualHealth > 0.0) {
            double previousLogicalHealth = health();
            super.syncHealth(previousLogicalHealth - absorbedByVirtualHealth);
            recordDamageTaken(absorbedByVirtualHealth);
            handleReceivedDamage(
                    towerEntity,
                    damageSource,
                    absorbedByVirtualHealth,
                    previousLogicalHealth,
                    health()
            );
            remainingDamage -= absorbedByVirtualHealth;
            towerEntity.setHealth((float) Math.min(health(), entityHealthCapacity));
        }

        normalEntityHealthSyncPending = remainingDamage > 0.0;
        if (!normalEntityHealthSyncPending) {
            // SemionTowerEntity normally clears vanilla's hurt cooldown after applying
            // damage. A hit fully consumed by logical overflow never reaches that path.
            towerEntity.invulnerableTime = 0;
        }
        return remainingDamage;
    }

    @Override
    public void syncHealth(double nextHealth) {
        if (normalEntityHealthSyncPending) {
            normalEntityHealthSyncPending = false;
            super.syncHealth(nextHealth);
            return;
        }

        SemionTowerEntity entity = towerEntity(currentLane);
        double entityHealthCapacity = entity == null
                ? currentMaxHealth()
                : Math.max(1.0, entity.getMaxHealth());
        boolean mirrorsLiveEntity = entity != null
                && Math.abs(entity.getHealth() - nextHealth) <= 0.01;
        if (mirrorsLiveEntity
                && health() > entityHealthCapacity + 1.0E-9
                && nextHealth + 1.0E-9 < health()) {
            // hurtIgnoringReductions bypasses modifyIncomingDamage. Keep the logical
            // overflow alive until onDamaged can subtract the true damage exactly.
            unscaledEntityDamagePending = true;
            unscaledEntityDamageLogicalHealth = health();
            return;
        }
        super.syncHealth(nextHealth);
    }

    @Override
    public void onDamaged(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double damageAmount,
            double previousHealth,
            double currentHealth
    ) {
        double received = Math.max(0.0, previousHealth - currentHealth);
        if (unscaledEntityDamagePending) {
            double previousLogicalHealth = unscaledEntityDamageLogicalHealth;
            unscaledEntityDamagePending = false;
            unscaledEntityDamageLogicalHealth = 0.0;
            double logicalReceived = Math.min(
                    previousLogicalHealth,
                    Math.max(0.0, damageAmount)
            );
            if (logicalReceived > received) {
                recordDamageTaken(logicalReceived - received);
            }
            super.syncHealth(previousLogicalHealth - logicalReceived);
            towerEntity.setHealth((float) Math.min(health(), towerEntity.getMaxHealth()));
            previousHealth = previousLogicalHealth;
            currentHealth = health();
            received = logicalReceived;
        }
        handleReceivedDamage(towerEntity, damageSource, received, previousHealth, currentHealth);
    }

    private void handleReceivedDamage(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double received,
            double previousHealth,
            double currentHealth
    ) {
        if (received <= 0.0) {
            return;
        }
        resetEchoChain();
        if (form == FoxForm.MACE_EXECUTIONER && maceTicksUntilStrike >= 0) {
            maceFocusDamageTaken += received;
            if (maceFocusDamageTaken + 1.0E-9
                    >= currentMaxHealth() * global(
                            "maceBreakHealthRatio",
                            AdversaryBalance.MACE_FOCUS_BREAK_MAX_HEALTH_RATIO
                    )) {
                resetMace();
            }
        }
        if (form != FoxForm.SHIELD_BEARER
                || shieldCounterCooldownTicks > 0
                || !(damageSource.getEntity() instanceof SemionMonsterEntity attacker)
                || !towerEntity.isValidAttackTarget(attacker)) {
            return;
        }
        damageSecondary(
                towerEntity,
                attacker,
                specialAttackDamage(
                        towerEntity,
                        attacker,
                        global("shieldCounterDamage", AdversaryBalance.SHIELD_COUNTER_DAMAGE)
                ),
                DamageType.PHYSICAL,
                false,
                false
        );
        shieldCounterCooldownTicks = globalInt(
                "shieldCounterCooldownTicks",
                AdversaryBalance.SHIELD_COUNTER_COOLDOWN_TICKS
        );
    }

    @Override
    public boolean isDestroyed(PlayerLane lane) {
        SemionTowerEntity entity = towerEntity(lane);
        if (entity != null) {
            syncPosition(GridPosition.from(BlockPos.containing(
                    entity.getX(),
                    entity.getY() - entityAnchorYOffset(),
                    entity.getZ()
            )));
            if (!entity.isAlive()) {
                super.syncHealth(0.0);
                return true;
            }
            return health() <= 0.0;
        }
        if (entityWasUnloaded()) {
            return health() <= 0.0;
        }
        super.syncHealth(0.0);
        return true;
    }

    @Override
    public List<String> runtimeDetailLines() {
        List<String> lines = new ArrayList<>();
        AdversaryProgressState progress = AdversaryProgressStates.state(ownerPlayer());
        AdversaryProgressState.FoxProgressSnapshot foxProgress = progress.foxProgress(foxId())
                .orElseGet(() -> new AdversaryProgressState.FoxProgressSnapshot(
                        form,
                        form.route(),
                        form.isFinal() ? Optional.of(form) : Optional.empty(),
                        false
                ));
        int maxFoxes = globalInt("maxFoxTowers", AdversaryBalance.MAX_FOX_TOWERS);
        lines.add("<gold>여우</gold>: " + progress.foxCount() + "/" + maxFoxes);
        lines.add("<gold>현재 형태</gold>: " + form.displayName());
        foxProgress.lockedRoute().ifPresent(route -> lines.add(
                "<yellow>점유 계열</yellow>: " + FoxForm.intermediateFor(route).displayName()
        ));
        lines.add("<yellow>점수</yellow> (획득/사용/가능): " + scoreText(progress));
        String claimedRoutes = java.util.Arrays.stream(FoxRoute.values())
                .filter(route -> progress.routeOwner(route)
                        .filter(owner -> !owner.equals(foxId()))
                        .isPresent())
                .map(route -> FoxForm.intermediateFor(route).displayName())
                .collect(java.util.stream.Collectors.joining(", "));
        if (!claimedRoutes.isEmpty()) {
            lines.add("<red>다른 여우가 점유</red>: " + claimedRoutes);
        }
        if (form.isFinal()) {
            lines.add("<gold>최종 성장</gold>: 남은 점수 " + progress.postEvolutionBonusScore()
                    + " / 피해 +" + percent(postEvolutionDamageBonus()) + " (최대 "
                    + percent(global(
                    "postEvolutionDamageBonusCap",
                    AdversaryBalance.POST_EVOLUTION_DAMAGE_BONUS_CAP
            )) + ")");
        }
        lines.add("<green>숙적 처치 회복</green>: 일반 "
                + percent(global("baseRivalKillHealRatio", AdversaryBalance.BASE_RIVAL_KILL_HEAL_RATIO))
                + " / 강화 "
                + percent(global("enhancedRivalKillHealRatio", AdversaryBalance.ENHANCED_RIVAL_KILL_HEAL_RATIO))
                + " — 이번 웨이브 " + number(rivalHealingThisWave) + "/"
                + number(currentMaxHealth() * global(
                "rivalKillHealCapRatioPerWave",
                AdversaryBalance.RIVAL_KILL_HEAL_CAP_RATIO_PER_WAVE
        )));
        int focusFireAttackers = focusFireAttackerCount(towerEntity(currentLane), null);
        lines.add("<aqua>집중포화 방어</aqua>: " + focusFireAttackers + "기 / 피해 감소 "
                + percent(focusFireDamageReduction(focusFireAttackers)) + " (최대 "
                + percent(global(
                "focusFireDamageReductionCap",
                AdversaryBalance.FOCUS_FIRE_DAMAGE_REDUCTION_CAP
        )) + ")");
        if (usesEvolvedSplash(form)) {
            lines.add("기본 공격이 주변 적 최대 "
                    + globalInt("baseSplashExtraTargets", AdversaryBalance.BASE_SPLASH_EXTRA_TARGETS)
                    + "기에게 공격력의 "
                    + percent(global("evolvedSplashDamageRatio", AdversaryBalance.EVOLVED_SPLASH_DAMAGE_RATIO))
                    + "만큼 피해를 줍니다.");
        }
        if (form.isIntermediate() && !foxProgress.intermediateWaveCompleted()) {
            lines.add("<red>최종 전직 조건</red>: 이 형태로 웨이브 1회 완료");
        }
        for (FoxForm candidate : nextEvolutionCandidates(progress, foxProgress)) {
            lines.add("<light_purple>" + candidate.displayName() + "</light_purple>: "
                    + evolutionRequirementText(candidate, progress));
        }
        switch (form) {
            case BASE -> lines.add("기본 공격이 반경 "
                    + number(global("baseSplashRadius", AdversaryBalance.BASE_SPLASH_RADIUS))
                    + "블록 안의 다른 적 최대 "
                    + globalInt("baseSplashExtraTargets", AdversaryBalance.BASE_SPLASH_EXTRA_TARGETS)
                    + "기에게 공격력의 "
                    + percent(global("baseSplashDamageRatio", AdversaryBalance.BASE_SPLASH_DAMAGE_RATIO))
                    + "만큼 피해를 줍니다.");
            case BREEZE -> lines.add("기본 공격이 다른 적 "
                    + globalInt("breezeExtraTargets", AdversaryBalance.BREEZE_EXTRA_TARGETS)
                    + "기에게 공격력의 "
                    + percent(global(
                    "breezeExtraTargetDamageRatio",
                    AdversaryBalance.BREEZE_EXTRA_TARGET_DAMAGE_RATIO
            )) + "만큼 연쇄 마법 피해를 줍니다.");
            case GOLDEN_FANG -> {
                int every = globalInt(
                        "goldenExtraAttackEvery",
                        AdversaryBalance.GOLDEN_FANG_EXTRA_ATTACK_EVERY
                );
                lines.add("같은 적을 " + every + "번 공격할 때마다 공격력의 "
                        + percent(global(
                        "goldenExtraDamageRatio",
                        AdversaryBalance.GOLDEN_FANG_EXTRA_DAMAGE_RATIO
                )) + "만큼 추가 피해를 줍니다.");
                lines.add("연속 공격: " + goldenTargetHits + "/" + every);
            }
            case SHIELD_BEARER -> {
                lines.add("받는 피해 " + percent(form.damageReduction()) + " 감소");
                lines.add("반격 피해 "
                        + number(global("shieldCounterDamage", AdversaryBalance.SHIELD_COUNTER_DAMAGE))
                        + " / 재사용 대기시간 "
                        + globalInt(
                        "shieldCounterCooldownTicks",
                        AdversaryBalance.SHIELD_COUNTER_COOLDOWN_TICKS
                ) + "틱");
            }
            case BELL_KEEPER -> lines.add(number(globalInt(
                    "bellHealIntervalTicks",
                    AdversaryBalance.BELL_HEAL_INTERVAL_TICKS
            ) / 20.0) + "초마다 반경 "
                    + number(global("bellHealRadius", AdversaryBalance.BELL_HEAL_RADIUS))
                    + "블록 내 체력 비율이 가장 낮은 다른 여우 "
                    + globalInt("bellHealTargetCount", AdversaryBalance.BELL_HEAL_TARGET_COUNT)
                    + "기의 최대 체력을 "
                    + percent(global("bellHealMaxHealthRatio", AdversaryBalance.BELL_HEAL_MAX_HEALTH_RATIO))
                    + " 회복합니다.");
            case BEACON_KEEPER -> {
                lines.add("받는 피해 " + percent(form.damageReduction()) + " 감소");
                lines.add(number(globalInt(
                        "beaconHealIntervalTicks",
                        AdversaryBalance.BEACON_HEAL_INTERVAL_TICKS
                ) / 20.0) + "초마다 반경 "
                        + number(global("beaconHealRadius", AdversaryBalance.BEACON_HEAL_RADIUS))
                        + "블록 내 체력 비율이 가장 낮은 다른 여우 최대 "
                        + globalInt("beaconHealTargetCount", AdversaryBalance.BEACON_HEAL_TARGET_COUNT)
                        + "기의 최대 체력을 각각 "
                        + percent(global(
                        "beaconHealMaxHealthRatio",
                        AdversaryBalance.BEACON_HEAL_MAX_HEALTH_RATIO
                )) + " 회복합니다.");
            }
            case OMINOUS_HEXER -> {
                lines.add("받는 피해 " + percent(form.damageReduction()) + " 감소");
                lines.add(number(globalInt(
                        "bellHealIntervalTicks",
                        AdversaryBalance.BELL_HEAL_INTERVAL_TICKS
                ) / 20.0) + "초마다 반경 "
                        + number(global("bellHealRadius", AdversaryBalance.BELL_HEAL_RADIUS))
                        + "블록 내 체력 비율이 가장 낮은 다른 여우 "
                        + globalInt("bellHealTargetCount", AdversaryBalance.BELL_HEAL_TARGET_COUNT)
                        + "기의 최대 체력을 "
                        + percent(global(
                        "bellHealMaxHealthRatio",
                        AdversaryBalance.BELL_HEAL_MAX_HEALTH_RATIO
                )) + " 회복합니다.");
                lines.add("아군을 노리는 적: 공격력 -"
                        + percent(global(
                        "ominousMonsterDamageReduction",
                        AdversaryBalance.OMINOUS_MONSTER_DAMAGE_REDUCTION
                )) + " / 공격 속도 -"
                        + percent(global(
                        "ominousMonsterAttackSpeedReduction",
                        AdversaryBalance.OMINOUS_MONSTER_ATTACK_SPEED_REDUCTION
                )) + " / 타워에게 받는 피해 +"
                        + percent(global(
                        "ominousMonsterTowerDamageTakenBonus",
                        AdversaryBalance.OMINOUS_MONSTER_TOWER_DAMAGE_TAKEN_BONUS
                )));
                lines.add("숙적에게는 적용되지 않습니다.");
            }
            case TRACKER -> lines.add("라인에서 가장 앞선 적을 우선 공격합니다.");
            case FIREWORK_PIERCER -> {
                lines.add("웨이브 적에게 "
                        + number(global(
                    "fireworkWaveDamageMultiplier",
                    AdversaryBalance.FIREWORK_WAVE_DAMAGE_MULTIPLIER
                )) + "배, 인컴 적에게 "
                        + number(global(
                    "fireworkIncomeDamageMultiplier",
                    AdversaryBalance.FIREWORK_INCOME_DAMAGE_MULTIPLIER
                )) + "배의 피해를 줍니다.");
                lines.add("직선상의 적 최대 "
                        + globalInt("fireworkMaxTargets", AdversaryBalance.FIREWORK_MAX_TARGETS)
                        + "기를 관통하며 " + percentList(AdversaryBalance.fireworkTargetDamageRatios())
                        + "의 물리 피해를 줍니다.");
            }
            case BIG_GAME_TRACKER -> {
                int stages = AdversaryBalance.bigGameStreakMultipliers().length;
                lines.add("인컴 적에게 "
                        + number(global(
                        "bigGameIncomeDamageMultiplier",
                        AdversaryBalance.BIG_GAME_INCOME_DAMAGE_MULTIPLIER
                )) + "배, 웨이브 적에게 "
                        + number(global(
                        "bigGameWaveDamageMultiplier",
                        AdversaryBalance.BIG_GAME_WAVE_DAMAGE_MULTIPLIER
                )) + "배의 피해를 줍니다.");
                lines.add("같은 인컴 적을 계속 공격하면 피해가 "
                        + multiplierList(AdversaryBalance.bigGameStreakMultipliers())
                        + "로 증가합니다.");
                lines.add("조준 단계: " + Math.min(stages, spyglassTargetHits + 1) + "/" + stages);
            }
            case ECHO_FOX -> {
                lines.add("같은 적을 공격할 때마다 피해가 "
                        + percent(global(
                        "echoBonusPerHit",
                        AdversaryBalance.ECHO_STREAK_DAMAGE_BONUS_PER_HIT
                )) + " 증가합니다. 최대 "
                        + globalInt("echoMaxBonusStacks", AdversaryBalance.ECHO_MAX_STREAK_BONUS_STACKS)
                        + "중첩.");
                lines.add("피격되거나 대상을 바꾸면 중첩이 초기화됩니다.");
                lines.add("메아리 중첩: " + echoTargetHits + "/"
                        + globalInt("echoMaxBonusStacks", AdversaryBalance.ECHO_MAX_STREAK_BONUS_STACKS));
            }
            case MACE_EXECUTIONER -> {
                lines.add(globalInt("maceFocusTicks", AdversaryBalance.MACE_FOCUS_TICKS)
                        + "틱 동안 집중한 뒤 " + number(form.damage()) + "의 물리 피해를 줍니다.");
                lines.add("연속 적중 시 피해가 "
                        + multiplierList(AdversaryBalance.maceStreakMultipliers())
                        + "로 증가합니다.");
                lines.add("집중 중 최대 체력의 "
                        + percent(global(
                        "maceBreakHealthRatio",
                        AdversaryBalance.MACE_FOCUS_BREAK_MAX_HEALTH_RATIO
                )) + "만큼 피해를 받으면 공격이 취소됩니다.");
                lines.add("적중 시 주변 적 최대 "
                        + globalInt("maceSweepExtraTargets", AdversaryBalance.MACE_SWEEP_EXTRA_TARGETS)
                        + "기에게 공격력의 "
                        + percent(global("maceSweepDamageRatio", AdversaryBalance.MACE_SWEEP_DAMAGE_RATIO))
                        + "만큼 피해를 줍니다.");
                lines.add("집중: " + Math.max(0, maceTicksUntilStrike)
                        + "틱 / 연속 적중: " + maceSuccessfulStrikes + "/"
                        + (AdversaryBalance.maceStreakMultipliers().length - 1));
            }
            case SCULK_CORE -> {
                lines.add("조준한 위치에 "
                        + globalInt("sculkDelayTicks", AdversaryBalance.SCULK_DETONATION_DELAY_TICKS)
                        + "틱 뒤 폭발을 일으킵니다.");
                lines.add("반경 "
                        + number(global("sculkRadius", AdversaryBalance.SCULK_DETONATION_RADIUS))
                        + "블록 안의 적 최대 "
                        + globalInt("sculkMaxTargets", AdversaryBalance.SCULK_MAX_TARGETS)
                        + "기에게 " + number(form.damage()) + "의 마법 피해를 줍니다.");
                lines.add("폭발할 때 방어를 무시하고 최대 체력의 "
                        + percent(global(
                        "sculkSelfDamageRatio",
                        AdversaryBalance.SCULK_SELF_DAMAGE_MAX_HEALTH_RATIO
                )) + "를 잃지만 체력은 "
                        + percent(global(
                        "sculkSelfDamageFloorRatio",
                        AdversaryBalance.SCULK_SELF_DAMAGE_HEALTH_FLOOR_RATIO
                )) + " 아래로 내려가지 않습니다.");
                lines.add("대기 중인 폭발: " + pendingSculkBlasts.size() + "개");
            }
        }
        return List.copyOf(lines);
    }

    private List<FoxForm> nextEvolutionCandidates(
            AdversaryProgressState progress,
            AdversaryProgressState.FoxProgressSnapshot foxProgress
    ) {
        if (form == FoxForm.BASE) {
            return foxProgress.lockedRoute()
                    .map(route -> List.of(FoxForm.intermediateFor(route)))
                    .orElseGet(() -> List.of(
                            FoxForm.BREEZE,
                            FoxForm.BELL_KEEPER,
                            FoxForm.TRACKER,
                            FoxForm.ECHO_FOX
                    ).stream()
                            .filter(candidate -> progress.routeOwner(candidate.route().orElseThrow())
                                    .map(owner -> owner.equals(foxId()))
                                    .orElse(true))
                            .toList());
        }
        if (form.isIntermediate()) {
            return foxProgress.lockedFinalForm()
                    .map(List::of)
                    .orElseGet(() -> FoxForm.finalsFor(form.route().orElseThrow()));
        }
        return List.of();
    }

    private String evolutionRequirementText(
            FoxForm candidate,
            AdversaryProgressState progress
    ) {
        EvolutionRecipe recipe = candidate.recipe().orElse(null);
        if (recipe == null) {
            return "없음";
        }
        List<String> requirements = new ArrayList<>();
        Map<RivalKind, Integer> cost = progress.evolutionCost(form, candidate);
        for (RivalKind kind : RivalKind.values()) {
            int required = cost.getOrDefault(kind, 0);
            if (required > 0) {
                requirements.add(kind.displayName() + " " + progress.availableScore(kind) + "/" + required);
            }
        }
        return String.join(" + ", requirements);
    }

    private static String scoreText(AdversaryProgressState progress) {
        return java.util.Arrays.stream(RivalKind.values())
                .map(kind -> kind.displayName() + " " + progress.score(kind)
                        + "/" + progress.spentScore(kind)
                        + "/" + progress.availableScore(kind))
                .collect(java.util.stream.Collectors.joining(" | "));
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof AdversaryFoxTower previousFox) {
            double ratio = Math.max(0.0, Math.min(
                    1.0,
                    previousFox.health() / Math.max(1.0, previousFox.currentMaxHealth())
            ));
            syncMaxHealth(form.maxHealth(), false);
            syncHealth(currentMaxHealth() * ratio);
        }
        resetTransientCombatState();
    }

    private void initializeForm(TowerType type) {
        setData(FOX_ID, UUID.randomUUID());
        form = AdversaryTowers.foxForm(type).orElse(FoxForm.BASE);
        syncMaxHealth(form.maxHealth(), false);
        syncHealth(currentMaxHealth());
    }

    private void equipHeldItem(SemionTowerEntity entity) {
        if (entity != null) {
            entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(form.heldItem()));
        }
    }

    private SemionTowerEntity towerEntity(PlayerLane lane) {
        PlayerLane resolvedLane = lane == null ? currentLane : lane;
        if (resolvedLane == null || resolvedLane.arenaWorld() == null || entityId().isEmpty()) {
            return null;
        }
        return Optional.ofNullable(resolvedLane.arenaWorld().getEntity(entityId().getAsInt()))
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast)
                .orElse(null);
    }

    private List<SemionMonsterEntity> ownedRivals(List<SemionMonsterEntity> candidates) {
        return candidates.stream()
                .filter(candidate -> candidate != null && candidate.runtimeMonster() != null)
                .filter(candidate -> AdversaryRivalTower.isOwnedRival(
                        candidate.runtimeMonster(),
                        ownerPlayer()
                ))
                .toList();
    }

    private static List<SemionMonsterEntity> finalDefenseAttackableCandidates(
            SemionTowerEntity source,
            List<SemionMonsterEntity> candidates
    ) {
        if (!source.deployedAtFinalDefense()) {
            return candidates;
        }
        double rangeSqr = source.attackRange() * source.attackRange();
        return candidates.stream()
                .filter(candidate -> source.distanceToSqr(candidate) <= rangeSqr)
                .toList();
    }

    private static Optional<SemionMonsterEntity> selectHighestProgress(
            List<SemionMonsterEntity> candidates
    ) {
        return candidates.stream()
                .filter(candidate -> candidate != null && candidate.runtimeMonster() != null)
                .max(Comparator
                        .comparingDouble((SemionMonsterEntity candidate) ->
                                candidate.runtimeMonster().laneProgress())
                        .thenComparingDouble(candidate -> candidate.runtimeMonster().targetPriorityScore()));
    }

    private static Optional<SemionMonsterEntity> selectBigGameTarget(
            List<SemionMonsterEntity> candidates
    ) {
        return candidates.stream()
                .filter(AdversaryFoxTower::isIncome)
                .max(Comparator
                        .comparingDouble((SemionMonsterEntity candidate) ->
                                candidate.runtimeMonster().maxHealth())
                        .thenComparingDouble(candidate -> candidate.runtimeMonster().targetPriorityScore()));
    }

    private static boolean isIncome(SemionMonsterEntity target) {
        return target != null
                && target.runtimeMonster() != null
                && (target.runtimeMonster().ownerPlayer().isPresent()
                || target.runtimeMonster().senderTeam().isPresent());
    }

    private double spyglassMultiplier(SemionMonsterEntity target) {
        UUID targetId = target == null ? null : target.getUUID();
        double[] multipliers = AdversaryBalance.bigGameStreakMultipliers();
        if (targetId == null || !targetId.equals(spyglassTargetId)) {
            return multipliers[0];
        }
        return multipliers[Math.min(spyglassTargetHits, multipliers.length - 1)];
    }

    private void updateSpyglassChain(SemionMonsterEntity target, boolean killedTarget) {
        if (killedTarget || target == null) {
            resetSpyglassChain();
            return;
        }
        if (!target.getUUID().equals(spyglassTargetId)) {
            spyglassTargetId = target.getUUID();
            spyglassTargetHits = 0;
        }
        spyglassTargetHits = Math.min(
                AdversaryBalance.bigGameStreakMultipliers().length - 1,
                spyglassTargetHits + 1
        );
    }

    private void resetSpyglassChain() {
        spyglassTargetId = null;
        spyglassTargetHits = 0;
    }

    private double echoMultiplier(SemionMonsterEntity target) {
        UUID targetId = target == null ? null : target.getUUID();
        if (targetId == null || !targetId.equals(echoTargetId)) {
            return 1.0;
        }
        return 1.0 + Math.min(
                globalInt("echoMaxBonusStacks", AdversaryBalance.ECHO_MAX_STREAK_BONUS_STACKS),
                echoTargetHits
        ) * global("echoBonusPerHit", AdversaryBalance.ECHO_STREAK_DAMAGE_BONUS_PER_HIT);
    }

    private void updateEchoChain(SemionMonsterEntity target, boolean killedTarget) {
        if (killedTarget || target == null) {
            resetEchoChain();
            return;
        }
        if (!target.getUUID().equals(echoTargetId)) {
            echoTargetId = target.getUUID();
            echoTargetHits = 0;
        }
        echoTargetHits = Math.min(
                globalInt("echoMaxBonusStacks", AdversaryBalance.ECHO_MAX_STREAK_BONUS_STACKS),
                echoTargetHits + 1
        );
    }

    private void resetEchoChain() {
        echoTargetId = null;
        echoTargetHits = 0;
    }

    private void handleGoldenAttack(
            SemionTowerEntity source,
            SemionMonsterEntity target,
            double resolvedOutgoingDamage,
            boolean killedPrimary
    ) {
        if (!target.getUUID().equals(goldenTargetId)) {
            goldenTargetId = target.getUUID();
            goldenTargetHits = 0;
        }
        goldenTargetHits++;
        if (killedPrimary) {
            goldenTargetId = null;
            goldenTargetHits = 0;
            return;
        }
        int extraAttackEvery = Math.max(1, globalInt(
                "goldenExtraAttackEvery",
                AdversaryBalance.GOLDEN_FANG_EXTRA_ATTACK_EVERY
        ));
        if (goldenTargetHits < extraAttackEvery) {
            return;
        }
        goldenTargetHits = 0;
        damageSecondary(
                source,
                target,
                resolvedOutgoingDamage * global(
                        "goldenExtraDamageRatio",
                        AdversaryBalance.GOLDEN_FANG_EXTRA_DAMAGE_RATIO
                ),
                DamageType.PHYSICAL,
                true,
                false
        );
    }

    private void applyAdditionalTargets(
            SemionTowerEntity source,
            SemionMonsterEntity primary,
            double attemptedDamage,
            int cap,
            double ratio
    ) {
        attackableMonsters(source, source.position(), source.attackRange(), Set.of(primary.getUUID()))
                .stream()
                .sorted(Comparator.comparingDouble(source::distanceToSqr))
                .limit(Math.max(0, cap))
                .forEach(target -> damageSecondary(
                        source,
                        target,
                        attemptedDamage * ratio,
                        DamageType.MAGIC,
                        false,
                        false
                ));
    }

    private void applyNearbySecondaries(
            SemionTowerEntity source,
            SemionMonsterEntity primary,
            double attemptedDamage,
            double radius,
            int cap,
            double ratio
    ) {
        if (radius <= 0.0 || cap <= 0 || ratio <= 0.0) {
            return;
        }
        Set<UUID> selected = attackableMonsters(
                source,
                primary.position(),
                radius,
                Set.of(primary.getUUID())
        )
                .stream()
                .sorted(Comparator.comparingDouble(candidate ->
                        candidate.position().distanceToSqr(primary.position())))
                .limit(Math.max(0, cap))
                .map(SemionMonsterEntity::getUUID)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        if (selected.isEmpty()) {
            return;
        }
        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                AreaEffectIds.tower(this, "base_splash"),
                source,
                primary.position(),
                radius,
                Set.of(primary.getUUID()),
                target -> selected.contains(target.getUUID()),
                AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)
        );
        TowerAreaDamage.applyBasicAttackSplash(
                this,
                source,
                request,
                target -> attemptedDamage * ratio,
                true
        );
    }

    private void handleFireworkAttack(
            SemionTowerEntity source,
            SemionMonsterEntity primary,
            double attemptedDamage
    ) {
        if (isIncome(primary)) {
            return;
        }
        Vec3 origin = source.position();
        Vec3 direction = primary.position().subtract(origin);
        double lineLength = source.attackRange();
        if (direction.lengthSqr() <= 1.0E-9 || lineLength <= 0.0) {
            return;
        }
        Vec3 unit = direction.normalize();
        double[] ratios = AdversaryBalance.fireworkTargetDamageRatios();
        int maxTargets = Math.max(1, globalInt(
                "fireworkMaxTargets",
                AdversaryBalance.FIREWORK_MAX_TARGETS
        ));
        int secondaryCap = Math.min(ratios.length - 1, maxTargets - 1);
        List<LineCandidate> lineTargets = attackableMonsters(
                source,
                origin,
                lineLength + LINE_HALF_WIDTH,
                Set.of(primary.getUUID())
        ).stream()
                .filter(candidate -> !isIncome(candidate))
                .map(candidate -> lineCandidate(origin, unit, lineLength, candidate))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingDouble(LineCandidate::projection))
                .limit(Math.max(0, secondaryCap))
                .toList();

        for (int index = 0; index < lineTargets.size(); index++) {
            double ratio = ratios[index + 1];
            damageSecondary(
                    source,
                    lineTargets.get(index).monster(),
                    attemptedDamage * ratio,
                    DamageType.PHYSICAL,
                    false,
                    true
            );
        }
    }

    private static LineCandidate lineCandidate(
            Vec3 origin,
            Vec3 unit,
            double length,
            SemionMonsterEntity monster
    ) {
        Vec3 offset = monster.position().subtract(origin);
        double projection = offset.dot(unit);
        if (projection < 0.0 || projection > length) {
            return null;
        }
        double perpendicularSqr = Math.max(0.0, offset.lengthSqr() - projection * projection);
        if (perpendicularSqr > LINE_HALF_WIDTH * LINE_HALF_WIDTH) {
            return null;
        }
        return new LineCandidate(monster, projection);
    }

    private List<SemionMonsterEntity> attackableMonsters(
            SemionTowerEntity source,
            Vec3 center,
            double radius,
            Set<UUID> excluded
    ) {
        if (source == null || center == null || radius <= 0.0) {
            return List.of();
        }
        PlayerLane lane = currentLane;
        if (lane == null || lane.arenaWorld() != source.level()) {
            return List.of();
        }
        double radiusSqr = radius * radius;
        return List.copyOf(lane.activeMonsters()).stream()
                .filter(Monster::hasMinecraftEntity)
                .map(monster -> lane.arenaWorld().getEntity(monster.minecraftEntityId()))
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .filter(monster -> source.isValidAttackTarget(monster)
                        && monster.runtimeMonster() != null
                        && !excluded.contains(monster.getUUID())
                        && monster.position().distanceToSqr(center) <= radiusSqr)
                .toList();
    }

    private void beginOrMaintainMaceChannel(SemionTowerEntity source, SemionMonsterEntity target) {
        if (source == null || target == null) {
            return;
        }
        int focusTicks = globalInt("maceFocusTicks", AdversaryBalance.MACE_FOCUS_TICKS);
        if (maceTargetId == null) {
            maceTargetId = target.getUUID();
            maceTicksUntilStrike = focusTicks;
            maceSuccessfulStrikes = 0;
            maceFocusDamageTaken = 0.0;
            AdversaryVfx.showMaceFocus(source, target, focusTicks, focusTicks);
            return;
        }
        if (!maceTargetId.equals(target.getUUID())) {
            resetMace();
            maceTargetId = target.getUUID();
            maceTicksUntilStrike = focusTicks;
            maceFocusDamageTaken = 0.0;
            AdversaryVfx.showMaceFocus(source, target, focusTicks, focusTicks);
        } else if (maceTicksUntilStrike < 0) {
            // The ordinary zero-damage attack ray is the clock for every focus.
            maceTicksUntilStrike = focusTicks;
            maceFocusDamageTaken = 0.0;
            AdversaryVfx.showMaceFocus(source, target, focusTicks, focusTicks);
        }
    }

    private void tickMace(SemionTowerEntity source) {
        if (maceTargetId == null || !(source.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(level.getEntity(maceTargetId) instanceof SemionMonsterEntity target)
                || !source.isValidAttackTarget(target)
                || source.distanceToSqr(target) > source.attackRange() * source.attackRange()
                || (source.currentAttackTarget() != null
                && !maceTargetId.equals(source.currentAttackTarget().getUUID()))) {
            resetMace();
            return;
        }
        if (maceTicksUntilStrike < 0) {
            return;
        }
        if (maceTicksUntilStrike > 1) {
            maceTicksUntilStrike--;
            if (maceTicksUntilStrike % 10 == 0) {
                AdversaryVfx.showMaceFocus(
                        source,
                        target,
                        maceTicksUntilStrike,
                        globalInt("maceFocusTicks", AdversaryBalance.MACE_FOCUS_TICKS)
                );
            }
            return;
        }

        double[] multipliers = AdversaryBalance.maceStreakMultipliers();
        double multiplier = multipliers[Math.min(maceSuccessfulStrikes, multipliers.length - 1)];
        DamageResult result = damageSecondary(
                source,
                target,
                specialAttackDamage(
                        source,
                        target,
                        form.damage() * multiplier
                ),
                DamageType.PHYSICAL,
                false,
                false
        );
        if (result.dealtDamage() > 0.0) {
            applyMaceSweep(source, target, form.damage() * multiplier);
        }
        if (result.dealtDamage() <= 0.0 || result.killed()) {
            resetMace();
            return;
        }
        maceSuccessfulStrikes = Math.min(
                multipliers.length - 1,
                maceSuccessfulStrikes + 1
        );
        // Wait for the next ordinary attack ray before beginning a new focus.
        maceTicksUntilStrike = -1;
        maceFocusDamageTaken = 0.0;
    }

    private void applyMaceSweep(
            SemionTowerEntity source,
            SemionMonsterEntity primary,
            double strikeDamage
    ) {
        double radius = global("maceSweepRadius", AdversaryBalance.MACE_SWEEP_RADIUS);
        int cap = globalInt("maceSweepExtraTargets", AdversaryBalance.MACE_SWEEP_EXTRA_TARGETS);
        double ratio = global("maceSweepDamageRatio", AdversaryBalance.MACE_SWEEP_DAMAGE_RATIO);
        if (radius <= 0.0 || cap <= 0 || ratio <= 0.0) {
            return;
        }
        Set<UUID> selected = attackableMonsters(
                source,
                primary.position(),
                radius,
                Set.of(primary.getUUID())
        ).stream()
                .sorted(Comparator.comparingDouble(candidate ->
                        candidate.position().distanceToSqr(primary.position())))
                .limit(cap)
                .map(SemionMonsterEntity::getUUID)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        if (selected.isEmpty()) {
            return;
        }
        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                AreaEffectIds.tower(this, "mace_sweep"),
                source,
                primary.position(),
                radius,
                Set.of(primary.getUUID()),
                target -> selected.contains(target.getUUID()),
                AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)
        );
        TowerAreaDamage.apply(
                this,
                source,
                request,
                target -> specialAttackDamage(source, target, strikeDamage * ratio),
                true
        );
    }

    private void resetMace() {
        maceTargetId = null;
        maceTicksUntilStrike = -1;
        maceSuccessfulStrikes = 0;
        maceFocusDamageTaken = 0.0;
    }

    private void scheduleSculkBlast(SemionTowerEntity source, Vec3 center) {
        if (source == null || center == null || !pendingSculkBlasts.isEmpty()
                || !(source.level() instanceof ServerLevel level)) {
            return;
        }
        int delayTicks = globalInt("sculkDelayTicks", AdversaryBalance.SCULK_DETONATION_DELAY_TICKS);
        pendingSculkBlasts.add(new PendingSculkBlast(
                center,
                delayTicks
        ));
        AdversaryVfx.showSculkWarning(
                level,
                center,
                global("sculkRadius", AdversaryBalance.SCULK_DETONATION_RADIUS),
                delayTicks,
                delayTicks
        );
    }

    private void tickSculkBlasts(SemionTowerEntity source) {
        if (pendingSculkBlasts.isEmpty()) {
            return;
        }
        if (form != FoxForm.SCULK_CORE) {
            pendingSculkBlasts.clear();
            return;
        }
        for (int index = pendingSculkBlasts.size() - 1; index >= 0; index--) {
            PendingSculkBlast blast = pendingSculkBlasts.get(index);
            if (blast.remainingTicks() > 1) {
                PendingSculkBlast next = blast.tick();
                pendingSculkBlasts.set(index, next);
                if (next.remainingTicks() % 10 == 0 && source.level() instanceof ServerLevel level) {
                    AdversaryVfx.showSculkWarning(
                            level,
                            next.center(),
                            global("sculkRadius", AdversaryBalance.SCULK_DETONATION_RADIUS),
                            next.remainingTicks(),
                            globalInt("sculkDelayTicks", AdversaryBalance.SCULK_DETONATION_DELAY_TICKS)
                    );
                }
                continue;
            }
            detonateSculk(source, blast.center());
            pendingSculkBlasts.remove(index);
        }
    }

    private void detonateSculk(SemionTowerEntity source, Vec3 center) {
        double radius = global("sculkRadius", AdversaryBalance.SCULK_DETONATION_RADIUS);
        int maxTargets = globalInt("sculkMaxTargets", AdversaryBalance.SCULK_MAX_TARGETS);
        if (source.level() instanceof ServerLevel level) {
            AdversaryVfx.showSculkDetonation(level, center, radius);
        }
        if (radius > 0.0 && maxTargets > 0) {
            Set<UUID> selected = attackableMonsters(
                    source,
                    center,
                    radius,
                    Set.of()
            ).stream()
                    .sorted(Comparator.comparingDouble(candidate ->
                            candidate.position().distanceToSqr(center)))
                    .limit(maxTargets)
                    .map(SemionMonsterEntity::getUUID)
                    .collect(java.util.stream.Collectors.toCollection(HashSet::new));

            MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                    AreaEffectIds.tower(this, "sculk_blast"),
                    source,
                    center,
                    radius,
                    Set.of(),
                    target -> selected.contains(target.getUUID()),
                    AreaVfxSpec.none()
            );
            TowerAreaDamage.apply(
                    this,
                    source,
                    request,
                    target -> specialAttackDamage(
                            source,
                            target,
                            form.damage()
                    ),
                    true,
                    (target, damage, killed) -> {
                    },
                    DamageType.MAGIC
            );
        }
        // The core is dangerous even when the snapshot catches targets; "miss" explicitly
        // does not waive its normal recoil.
        double recoilDamage = sculkRecoilDamage(health(), currentMaxHealth());
        if (recoilDamage > 0.0) {
            source.hurtIgnoringReductions(source.damageSources().magic(), recoilDamage);
        }
    }

    static double sculkRecoilDamage(double currentHealth, double maximumHealth) {
        double safeMaximum = Math.max(0.0, maximumHealth);
        double floorHealth = safeMaximum * global(
                "sculkSelfDamageFloorRatio",
                AdversaryBalance.SCULK_SELF_DAMAGE_HEALTH_FLOOR_RATIO
        );
        double normalRecoil = safeMaximum * global(
                "sculkSelfDamageRatio",
                AdversaryBalance.SCULK_SELF_DAMAGE_MAX_HEALTH_RATIO
        );
        return Math.max(0.0, Math.min(normalRecoil, currentHealth - floorHealth));
    }

    static double rivalKillHealingAmount(
            double currentHealth,
            double maximumHealth,
            double healedThisWave,
            boolean enhanced
    ) {
        double safeMaximum = Math.max(0.0, maximumHealth);
        double ratio = enhanced
                ? global("enhancedRivalKillHealRatio", AdversaryBalance.ENHANCED_RIVAL_KILL_HEAL_RATIO)
                : global("baseRivalKillHealRatio", AdversaryBalance.BASE_RIVAL_KILL_HEAL_RATIO);
        double remainingWaveCap = safeMaximum * global(
                "rivalKillHealCapRatioPerWave",
                AdversaryBalance.RIVAL_KILL_HEAL_CAP_RATIO_PER_WAVE
        ) - Math.max(0.0, healedThisWave);
        return Math.max(0.0, Math.min(
                safeMaximum * ratio,
                Math.min(safeMaximum - Math.max(0.0, currentHealth), remainingWaveCap)
        ));
    }

    static double focusFireDamageReduction(int attackerCount) {
        int extraAttackers = Math.max(0, attackerCount - 1);
        return Math.min(
                global("focusFireDamageReductionCap", AdversaryBalance.FOCUS_FIRE_DAMAGE_REDUCTION_CAP),
                extraAttackers * global(
                        "focusFireDamageReductionPerExtraAttacker",
                        AdversaryBalance.FOCUS_FIRE_DAMAGE_REDUCTION_PER_EXTRA_ATTACKER
                )
        );
    }

    /**
     * Reproduces the generic pre-trait damage bonuses for attacks whose ordinary
     * basic hit is intentionally zero.  The returned value still goes through
     * {@link #damageTargetResult}, so traits, final multipliers and monster
     * defenses are each applied exactly once.
     */
    private double specialAttackDamage(
            SemionTowerEntity source,
            SemionMonsterEntity target,
            double baseDamage
    ) {
        double damage = baseDamage * (1.0
                + source.activeEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS));
        if (target != null && target.runtimeMonster() != null) {
            TimedEffectType specialization = target.runtimeMonster().senderTeam().isPresent()
                    ? TimedEffectType.TOWER_INCOME_DAMAGE_BONUS
                    : TimedEffectType.TOWER_WAVE_DAMAGE_BONUS;
            damage *= 1.0 + source.activeEffectMagnitude(specialization);
        }
        return modifyResolvedAttackDamage(source, target, damage);
    }

    /** Routes every non-primary attack through the same kill callback used by basic attacks. */
    private DamageResult damageSecondary(
            SemionTowerEntity source,
            SemionMonsterEntity target,
            double damage,
            DamageType damageType,
            boolean alreadyResolvedOutgoing,
            boolean basicAttackSecondary
    ) {
        if (source == null || target == null || !source.isValidAttackTarget(target) || damage <= 0.0) {
            return DamageResult.NONE;
        }
        DamageResult result;
        if (alreadyResolvedOutgoing) {
            result = damageResolvedTargetResult(source, target, damage, damageType);
        } else if (basicAttackSecondary && damageType == DamageType.PHYSICAL) {
            result = source.damageBasicAttackSecondaryTargetResult(target, damage);
        } else {
            result = damageTargetResult(source, target, damage, damageType);
        }
        if (result.dealtDamage() > 0.0) {
            switch (form) {
                case BREEZE, GOLDEN_FANG, SHIELD_BEARER, FIREWORK_PIERCER, MACE_EXECUTIONER ->
                        AdversaryVfx.showSecondaryAttack(form, source, target);
                default -> TowerVfxService.showSecondaryAttack(source, target);
            }
        }
        if (result.killed()) {
            onKill(source, target, damage);
        }
        return result;
    }

    private void recordRivalKill(Monster monster) {
        if (!AdversaryProgressStates.recordFoxKill(ownerPlayer(), monster, currentLane)) {
            return;
        }
        double amount = rivalKillHealingAmount(
                health(),
                currentMaxHealth(),
                rivalHealingThisWave,
                AdversaryRivalTower.isEnhancedProxy(monster)
        );
        if (amount <= 0.0) {
            return;
        }
        double before = health();
        SemionTowerEntity entity = towerEntity(currentLane);
        if (entity != null) {
            entity.healTarget(entity, amount);
        } else {
            syncHealth(before + amount);
            recordHealingDone(health() - before);
        }
        rivalHealingThisWave += Math.max(0.0, health() - before);
    }

    private double postEvolutionDamageBonus() {
        if (!form.isFinal()) {
            return 0.0;
        }
        int score = AdversaryProgressStates.state(ownerPlayer()).postEvolutionBonusScore();
        return Math.min(
                score * global(
                        "postEvolutionDamageBonusPerScore",
                        AdversaryBalance.POST_EVOLUTION_DAMAGE_BONUS_PER_SCORE
                ),
                global(
                        "postEvolutionDamageBonusCap",
                        AdversaryBalance.POST_EVOLUTION_DAMAGE_BONUS_CAP
                )
        );
    }

    private int focusFireAttackerCount(SemionTowerEntity towerEntity, DamageSource damageSource) {
        if (towerEntity == null || currentLane == null || currentLane.arenaWorld() != towerEntity.level()) {
            return 0;
        }
        // ponytail: one fox exists per lane; cache target counts only if profiling shows this hit-time scan is hot.
        int count = 0;
        boolean sourceCounted = false;
        Object damageSourceEntity = damageSource == null ? null : damageSource.getEntity();
        for (Monster monster : List.copyOf(currentLane.activeMonsters())) {
            if (!monster.hasMinecraftEntity()) {
                continue;
            }
            if (!(currentLane.arenaWorld().getEntity(monster.minecraftEntityId())
                    instanceof SemionMonsterEntity attacker)
                    || !attacker.isAlive()
                    || attacker.isRemoved()
                    || attacker.getTarget() != towerEntity) {
                continue;
            }
            count++;
            sourceCounted |= attacker == damageSourceEntity;
        }
        if (!sourceCounted
                && damageSourceEntity instanceof SemionMonsterEntity attacker
                && attacker.isAlive()
                && !attacker.isRemoved()
                && attacker.getTarget() == towerEntity) {
            count++;
        }
        return count;
    }

    private static boolean usesEvolvedSplash(FoxForm form) {
        return switch (form) {
            case GOLDEN_FANG, SHIELD_BEARER, BELL_KEEPER, BEACON_KEEPER,
                    OMINOUS_HEXER, TRACKER, BIG_GAME_TRACKER, ECHO_FOX -> true;
            default -> false;
        };
    }

    private void resetTransientCombatState() {
        normalEntityHealthSyncPending = false;
        unscaledEntityDamagePending = false;
        unscaledEntityDamageLogicalHealth = 0.0;
        rivalHealingThisWave = 0.0;
        goldenTargetId = null;
        goldenTargetHits = 0;
        shieldCounterCooldownTicks = 0;
        resetSpyglassChain();
        resetEchoChain();
        resetMace();
        pendingSculkBlasts.clear();
    }

    private static double global(String key, double fallback) {
        return AdversaryBalance.globalValue(key, fallback);
    }

    private static int globalInt(String key, int fallback) {
        return AdversaryBalance.globalInt(key, fallback);
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceFirst("\\.?0+$", "");
    }

    protected static String percent(double ratio) {
        return number(ratio * 100.0) + "%";
    }

    private static String multiplierList(double[] values) {
        return java.util.Arrays.stream(values)
                .mapToObj(value -> number(value) + "배")
                .collect(java.util.stream.Collectors.joining(" / "));
    }

    private static String percentList(double[] values) {
        return java.util.Arrays.stream(values)
                .mapToObj(AdversaryFoxTower::percent)
                .collect(java.util.stream.Collectors.joining(" / "));
    }

    private record LineCandidate(SemionMonsterEntity monster, double projection) {
    }

    private record PendingSculkBlast(Vec3 center, int remainingTicks) {
        PendingSculkBlast tick() {
            return new PendingSculkBlast(center, Math.max(0, remainingTicks - 1));
        }
    }
}
