package kim.biryeong.semiontd.tower.frost;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.world.damagesource.DamageSource;

/** 아이스 브레이커와 얼어붙은 만두가 공유하는 동일 피해 광역 기본공격. */
public final class FrostSplashTower extends ProductionTower {
    private static final int ICE_BREAKER_T3_EXTRA_ATTACKS = 1;

    private int waveFamilyCount;
    private double chill;
    private boolean resolvingImmediateAttacks;

    public FrostSplashTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition position
    ) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public FrostSplashTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    public Optional<SemionMonsterEntity> selectAttackTarget(
            SemionTowerEntity towerEntity,
            List<SemionMonsterEntity> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        Comparator<SemionMonsterEntity> priority = Comparator
                .comparingDouble((SemionMonsterEntity target) -> target.runtimeMonster().targetPriorityScore());
        if (towerEntity != null) {
            priority = priority.thenComparingDouble(target -> -towerEntity.distanceToSqr(target));
        }
        if (FrostTowers.isFrozenDumpling(type()) && hasFrozenFoodThreshold(FrostBalance.thirdThreshold())) {
            Comparator<SemionMonsterEntity> incomePriority = Comparator
                    .comparingDouble((SemionMonsterEntity target) -> target.runtimeMonster().maxHealth())
                    .thenComparing(priority);
            return candidates.stream()
                    .filter(target -> target != null
                            && target.runtimeMonster() != null
                            && target.runtimeMonster().senderTeam().isPresent())
                    .max(incomePriority);
        }
        if (!FrostTowers.isIceBreaker(type())) {
            return Optional.empty();
        }
        return candidates.stream()
                .filter(target -> target != null
                        && target.runtimeMonster() != null
                        && FrostMonsterStates.isRefrigerated(target.runtimeMonster()))
                .max(priority);
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        super.onWaveStarted(lane, currentRound);
        if (!FrostTowers.isFrozenDumpling(type())) {
            return;
        }
        waveFamilyCount = lane == null ? 0 : (int) lane.towers().stream()
                .filter(tower -> ownerPlayer().equals(tower.ownerPlayer()))
                .map(Tower::type)
                .filter(FrostTowers::isFrozenDumpling)
                .count();
        onStateChanged(lane);
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        waveFamilyCount = 0;
        chill = 0.0;
        super.resetForRound(lane);
    }

    @Override
    public double modifyAttackDamage(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double damageAmount
    ) {
        double resolved = super.modifyAttackDamage(towerEntity, target, damageAmount);
        if (!FrostTowers.isFrozenDumpling(type())) {
            return resolved;
        }
        if (hasFrozenFoodThreshold(FrostBalance.firstThreshold())) {
            resolved += FrostBalance.frozenFoodDamageBonusAt3(type());
        }
        if (hasFrozenFoodThreshold(FrostBalance.thirdThreshold())
                && target != null
                && target.runtimeMonster() != null
                && target.runtimeMonster().senderTeam().isPresent()) {
            resolved *= 1.0 + FrostBalance.frozenFoodIncomeDamageBonusAt9(type());
        }
        return resolved;
    }

    @Override
    public double modifyResolvedOutgoingDamage(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double damageAmount
    ) {
        return FrostFullOperationService.fixedOutgoingDamage(
                ownerPlayer(), towerEntity.level().getGameTime(), damageAmount);
    }

    @Override
    public double modifyResolvedAttackDamage(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double damageAmount
    ) {
        return FrostFullOperationService.fixedOutgoingDamage(
                ownerPlayer(), towerEntity.level().getGameTime(), damageAmount);
    }

    @Override
    public double modifyFinalIncomingDamage(
            SemionTowerEntity towerEntity,
            DamageSource damageSource,
            double originalDamage,
            double normallyReducedDamage
    ) {
        return FrostFullOperationService.fixedIncomingDamage(
                ownerPlayer(), towerEntity.level().getGameTime(), originalDamage, normallyReducedDamage);
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
        super.onAttackResolved(
                towerEntity,
                target,
                attemptedDamage,
                resolvedOutgoingDamage,
                dealtDamage,
                killedTarget
        );
        if (towerEntity == null || target == null || resolvedOutgoingDamage <= 0.0) {
            return;
        }

        splash(towerEntity, target, resolvedOutgoingDamage);
        if (FrostTowers.isIceBreaker(type()) && dealtDamage > 0.0 && !killedTarget && target.isAlive()) {
            thaw(towerEntity, target);
        }
        if (isIceBreakerT3() && dealtDamage > 0.0 && !resolvingImmediateAttacks) {
            fireImmediateAttacks(towerEntity, ICE_BREAKER_T3_EXTRA_ATTACKS);
        }
    }

    @Override
    public List<String> runtimeDetailLines() {
        if (FrostTowers.isIceBreaker(type())) {
            return isIceBreakerT3()
                    ? List.of(
                            "동일 피해 광역 " + oneDecimal(FrostBalance.splashRadius(type())) + "칸",
                            "냉매 상태의 적 우선 공격",
                            "냉매 적중 시 해동 · 최대 체력 " + percent(FrostBalance.thawMaxHealthDamage()) + " 피해",
                            "공격 후 즉시 1회 추가 공격"
                    )
                    : List.of(
                            "동일 피해 광역 " + oneDecimal(FrostBalance.splashRadius(type())) + "칸",
                            "냉매 상태의 적 우선 공격",
                            "냉매 적중 시 해동 · 최대 체력 " + percent(FrostBalance.thawMaxHealthDamage()) + " 피해"
                    );
        }
        String family = waveFamilyCount <= 0
                ? "준비 중 · 다음 웨이브 시작 시 계열 수 고정"
                : "이번 웨이브 계열 " + waveFamilyCount + "기";
        return List.of(
                family,
                "공격력 +" + oneDecimal(activeFrozenFoodDamageBonus()),
                "동일 피해 광역 " + oneDecimal(effectiveSplashRadius()) + "칸",
                "방출 한기 " + String.format(Locale.ROOT, "%.0f%%", chill * 100.0)
        );
    }

    void onEmissionWaveHit(PlayerLane lane) {
        onEmissionWaveHit(lane, FrostBalance.chillPerHit());
    }

    void onEmissionWaveHit(PlayerLane lane, double amount) {
        if (!FrostTowers.isFrozenDumpling(type())) {
            return;
        }
        chill = Math.min(FrostBalance.chillThreshold(), chill + Math.max(0.0, amount));
        if (chill + 1.0E-9 < FrostBalance.chillThreshold()) {
            onStateChanged(lane);
            return;
        }

        chill = 0.0;
        fireImmediateAttacks(lane);
        FrostFullOperationService.recordSpecialActivation(
                lane, FrostFullOperationService.TriggerFamily.FROZEN_FOOD);
        onStateChanged(lane);
    }

    private void splash(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity primary,
            double resolvedOutgoingDamage
    ) {
        double radius = effectiveSplashRadius();
        if (radius <= 0.0) {
            return;
        }
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                AreaEffectIds.tower(this, "frost_splash"),
                towerEntity,
                primary,
                radius,
                AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)
        );
        TowerAreaDamage.applyResolved(
                this,
                towerEntity,
                request,
                ignored -> resolvedOutgoingDamage,
                true,
                (target, damage, killed) -> {
                    if (FrostTowers.isIceBreaker(type()) && damage > 0.0 && !killed && target.isAlive()) {
                        thaw(towerEntity, target);
                    }
                },
                type().primaryDamageType()
        );
    }

    private void thaw(SemionTowerEntity towerEntity, SemionMonsterEntity target) {
        FrostMonsterStates.ThawResult thaw = FrostMonsterStates.thaw(target);
        if (!thaw.thawed() || thaw.damage() <= 0.0) {
            return;
        }
        DamageResult result = damageResolvedTargetResult(towerEntity, target, thaw.damage(), DamageType.TRUE);
        FrostAugments.onThawed(this, towerEntity, target);
        TowerVfxService.showSecondaryAttack(towerEntity, target);
        if (result.killed()) {
            onKill(towerEntity, target, thaw.damage());
        }
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof FrostSplashTower previous) {
            waveFamilyCount = previous.waveFamilyCount;
            chill = previous.chill;
        }
    }

    private void fireImmediateAttacks(PlayerLane lane) {
        SemionTowerEntity source = runtimeEntity(lane).orElse(null);
        if (source == null) {
            return;
        }
        fireImmediateAttacks(source, FrostBalance.frozenFoodRefrigerantBonusAttacks());
    }

    private void fireImmediateAttacks(SemionTowerEntity source, int attackCount) {
        boolean previousImmediateState = resolvingImmediateAttacks;
        resolvingImmediateAttacks = true;
        try {
            int attacks = Math.max(0, attackCount);
            for (int index = 0; index < attacks; index++) {
                SemionMonsterEntity target = selectImmediateAttackTarget(source).orElse(null);
                if (target == null) {
                    return;
                }
                source.recordCurrentAttackTarget(target);
                source.faceAttackTarget(target);
                source.playAnimation(SemionAnimationState.ATTACK);
                double damageAmount = source.attackDamageAmount(target);
                float healthBeforeAttack = source.getHealth();
                DamageResult damageResult = source.damageTargetResult(target, damageAmount);
                source.recordAttack(
                        target,
                        damageAmount,
                        damageResult.outgoingDamage(),
                        damageResult.dealtDamage(),
                        damageResult.killed()
                );
                TowerVfxService.showAttack(
                        source,
                        target,
                        damageResult.killed(),
                        source.getHealth() > healthBeforeAttack + 0.01F
                );
            }
        } finally {
            resolvingImmediateAttacks = previousImmediateState;
        }
    }

    private Optional<SemionMonsterEntity> selectImmediateAttackTarget(SemionTowerEntity source) {
        double attackRangeSqr = source.attackRange() * source.attackRange();
        List<SemionMonsterEntity> candidates = source.level().getEntities(
                        source,
                        source.targetSearchBox(),
                        entity -> entity instanceof SemionMonsterEntity target
                                && source.isValidAttackTarget(target)
                                && source.distanceToSqr(target) <= attackRangeSqr
                ).stream()
                .map(SemionMonsterEntity.class::cast)
                .toList();
        Optional<SemionMonsterEntity> selected = selectAttackTarget(source, candidates);
        if (selected.isPresent()) {
            return selected;
        }
        Comparator<SemionMonsterEntity> normalPriority = Comparator
                .comparingDouble((SemionMonsterEntity target) -> target.runtimeMonster().targetPriorityScore())
                .thenComparingDouble(target -> -source.distanceToSqr(target));
        return candidates.stream().max(normalPriority);
    }

    private double effectiveSplashRadius() {
        double radius = FrostBalance.splashRadius(type());
        if (FrostTowers.isFrozenDumpling(type())
                && hasFrozenFoodThreshold(FrostBalance.secondThreshold())) {
            radius += FrostBalance.frozenFoodSplashRadiusBonusAt6();
        }
        return Math.max(0.0, radius);
    }

    private double activeFrozenFoodDamageBonus() {
        return hasFrozenFoodThreshold(FrostBalance.firstThreshold())
                ? FrostBalance.frozenFoodDamageBonusAt3(type())
                : 0.0;
    }

    private boolean isIceBreakerT3() {
        return FrostTowers.ICE_BREAKER_T3.id().equals(type().id());
    }

    private boolean hasFrozenFoodThreshold(int threshold) {
        return waveFamilyCount >= threshold;
    }

    int waveFamilyCount() {
        return waveFamilyCount;
    }

    double chill() {
        return chill;
    }

    double effectiveSplashRadiusForTest() {
        return effectiveSplashRadius();
    }
}
