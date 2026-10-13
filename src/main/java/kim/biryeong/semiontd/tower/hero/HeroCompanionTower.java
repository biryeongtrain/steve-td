package kim.biryeong.semiontd.tower.hero;

import static kim.biryeong.semiontd.tower.hero.HeroCompanionAbilityDefaults.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;

public final class HeroCompanionTower extends HeroPartyTower {
    private static final Identifier MAGE_SPLASH = Identifier.fromNamespaceAndPath("semion-td", "hero_party_mage_splash");

    private static final Identifier ROGUE_HASTE = Identifier.fromNamespaceAndPath("semion-td", "hero_party_rogue_haste");

    private int attackCount;
    private final HeroCompanionSupportController support = new HeroCompanionSupportController(this);
    private boolean executeAttackPending;

    public HeroCompanionTower(
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
    public void onPlaced(PlayerLane lane) {
        role().ifPresent(role -> HeroPartyStates.commitCompanion(ownerPlayer(), role));
        super.onPlaced(lane);
    }

    @Override
    public Optional<SemionMonsterEntity> selectAttackTarget(SemionTowerEntity towerEntity, List<SemionMonsterEntity> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        Comparator<SemionMonsterEntity> comparator = switch (role().orElse(HeroCompanionRole.KNIGHT)) {
            case ARCHER -> Comparator
                    .comparing((SemionMonsterEntity target) -> isBoss(target)).reversed()
                    .thenComparing(Comparator.comparingDouble((SemionMonsterEntity target) -> maxHealth(target)).reversed())
                    .thenComparingDouble(target -> target.distanceToSqr(towerEntity));
            case ROGUE -> Comparator
                    .comparingDouble(HeroCompanionTower::healthRatio)
                    .thenComparingDouble(target -> target.distanceToSqr(towerEntity));
            case MAGE -> Comparator
                    .comparingInt((SemionMonsterEntity target) -> nearbyCount(target, candidates)).reversed()
                    .thenComparingDouble(target -> target.distanceToSqr(towerEntity));
            default -> Comparator.comparingDouble(target -> target.distanceToSqr(towerEntity));
        };
        return candidates.stream().min(comparator);
    }

    @Override
    public double modifyResolvedAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        HeroCompanionRole role = role().orElse(null);
        executeAttackPending = false;
        if (role == HeroCompanionRole.ARCHER) {
            return damageAmount * archerTargetMultiplier(target);
        }
        if (role == HeroCompanionRole.ROGUE
                && healthRatio(target) <= value("executeThreshold", 0.30)) {
            executeAttackPending = true;
            return damageAmount * (1.0 + value("executeDamageBonus", ROGUE_EXECUTE[index()]));
        }
        return damageAmount;
    }

    @Override
    public double modifyIncomingDamage(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount) {
        double focusAdjusted = super.modifyIncomingDamage(towerEntity, damageSource, damageAmount);
        if (role().orElse(null) != HeroCompanionRole.KNIGHT) {
            return focusAdjusted;
        }
        double resolved = focusAdjusted * (1.0 - value("damageReduction", KNIGHT_REDUCTION[index()]));
        state().recordSpecial(
                HeroQuestKind.KNIGHT_GUARD,
                null,
                Math.max(0.0, focusAdjusted - resolved),
                onlineOwner(towerEntity)
        );
        return resolved;
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
        FakePlayerTowerVisuals.playAttack(this);
        HeroCompanionRole role = role().orElse(null);
        state().recordCompanionAttack(role, dealtDamage, killedTarget, isBoss(target), onlineOwner(towerEntity));
        if (role == HeroCompanionRole.ROGUE && executeAttackPending && dealtDamage > 0.0) {
            state().recordSpecial(HeroQuestKind.ROGUE_EXECUTE_HITS, null, 1.0, onlineOwner(towerEntity));
        }
        executeAttackPending = false;
        if (role == null || dealtDamage <= 0.0 || target == null || towerEntity == null) {
            return;
        }
        int attackNumber = ++attackCount;
        switch (role) {
            case KNIGHT -> applyKnightBash(towerEntity, target, attackNumber);
            case ARCHER -> applyArcherAbilities(towerEntity, target, attemptedDamage, attackNumber);
            case MAGE -> applyMageAbilities(towerEntity, target, attemptedDamage, attackNumber);
            case ROGUE -> applyRogueCombo(towerEntity, target, attemptedDamage, attackNumber);
            default -> {
            }
        }
    }

    @Override
    public void onKill(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        if (role().orElse(null) != HeroCompanionRole.ROGUE || towerEntity == null) {
            return;
        }
        double bonus = value("killAttackSpeedBonus", ROGUE_HASTE_BONUS[index()]);
        int ticks = HeroPartyBalance.towerInt(type().id(), "killAttackSpeedDurationTicks", ROGUE_HASTE_TICKS[index()]);
        if (bonus <= 0.0 || ticks <= 0) {
            return;
        }
        towerEntity.refreshTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_BONUS, ROGUE_HASTE, bonus, ticks);
        TowerVfxService.showAreaEffect(
                towerEntity,
                AreaEffectIds.tower(this, "pursuit"),
                AreaVfxStyles.BUFF,
                towerEntity.position(),
                1.2,
                List.of(towerEntity.position()),
                1,
                1,
                0
        );
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        attackCount = 0;
        support.resetRound();
    }

    @Override
    public void tick(PlayerLane lane) {
        super.tick(lane);
        support.tick(lane);
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof HeroCompanionTower companion) {
            attackCount = companion.attackCount;
            support.copyFrom(companion.support);
        }
    }

    @Override
    public List<String> runtimeDetailLines() {
        ArrayList<String> lines = new ArrayList<>(super.runtimeDetailLines());
        HeroCompanionRole role = role().orElse(null);
        lines.add("동료: " + (role == null ? "알 수 없음" : role.displayName()) + " T" + tier());
        if (role == HeroCompanionRole.ARCHER) {
            lines.add("인컴/소환 피해: +" + Math.round(value(
                    "incomeDamageBonus", HeroPartyBalance.INCOME_DAMAGE_BONUS
            ) * 100.0) + "%");
        }
        if (role != null) {
            lines.addAll(HeroCompanionStatsView.abilities(role, tier(), type().id()));
        }
        return List.copyOf(lines);
    }

    @Override
    public List<String> upgradeTooltipLines(TowerUpgradeOption option) {
        HeroCompanionRole targetRole = option == null
                ? null
                : HeroPartyTowers.role(option.targetType()).orElse(null);
        int targetTier = option == null ? 0 : HeroPartyTowers.tier(option.targetType());
        if (targetRole == null || targetRole != role().orElse(null) || targetTier < 2 || targetTier > 4) {
            return List.of();
        }
        List<String> details = HeroCompanionStatsView.abilities(targetRole, targetTier, option.targetType().id());
        if (targetTier == 2 && !details.isEmpty()) {
            return List.of("<green>새 능력</green> " + details.get(0));
        }
        if (targetTier == 3 && details.size() >= 2) {
            return List.of("<green>새 능력</green> " + details.get(1));
        }
        return details.stream().map(line -> "<gold>능력 강화</gold> " + line).toList();
    }

    private void applyKnightBash(SemionTowerEntity source, SemionMonsterEntity target, int attackNumber) {
        int every = HeroPartyBalance.towerInt(type().id(), "shieldBashEvery", KNIGHT_BASH_EVERY[index()]);
        double slow = value("shieldBashSlow", KNIGHT_BASH_SLOW[index()]);
        int ticks = HeroPartyBalance.towerInt(type().id(), "shieldBashDurationTicks", KNIGHT_BASH_TICKS[index()]);
        if (every <= 0 || attackNumber % every != 0 || slow <= 0.0 || ticks <= 0 || !target.isAlive()) {
            return;
        }
        target.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, slow, ticks);
        target.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION, slow, ticks);
        TowerVfxService.showAreaEffect(
                source,
                AreaEffectIds.tower(this, "shield_bash"),
                AreaVfxStyles.DEBUFF,
                target.position(),
                0.8,
                List.of(target.position()),
                1,
                1,
                0
        );
    }

    private void applyArcherAbilities(
            SemionTowerEntity source,
            SemionMonsterEntity primary,
            double attemptedDamage,
            int attackNumber
    ) {
        int every = HeroPartyBalance.towerInt(type().id(), "pierceEvery", ARCHER_PIERCE_EVERY[index()]);
        if (every <= 0 || attackNumber % every != 0) {
            return;
        }
        double ratio = value("pierceDamageRatio", ARCHER_PIERCE_RATIO[index()]);
        SemionMonsterEntity secondary = nearestExtraTarget(source, primary);
        if (secondary != null && ratio > 0.0) {
            double primaryMultiplier = archerTargetMultiplier(primary);
            double secondaryDamage = attemptedDamage / Math.max(0.01, primaryMultiplier)
                    * ratio * archerTargetMultiplier(secondary);
            DamageResult result = damageBasicAttackTargetResult(
                    source, secondary, secondaryDamage, primaryDamageType()
            );
            state().recordCompanionAttack(
                    HeroCompanionRole.ARCHER,
                    result.dealtDamage(),
                    result.killed(),
                    isBoss(secondary),
                    onlineOwner(source)
            );
            if (result.killed()) {
                onKill(source, secondary, secondaryDamage);
            }
            TowerVfxService.showSecondaryAttack(source, secondary);
        }
        double mark = value("markDamageBonus", ARCHER_MARK_BONUS[index()]);
        int markTicks = HeroPartyBalance.towerInt(type().id(), "markDurationTicks", ARCHER_MARK_TICKS[index()]);
        if (mark <= 0.0 || markTicks <= 0 || !primary.isAlive()) {
            return;
        }
        primary.applyTimedEffect(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS, mark, markTicks);
        TowerVfxService.showAreaEffect(
                source,
                AreaEffectIds.tower(this, "weakness_mark"),
                AreaVfxStyles.DEBUFF,
                primary.position(),
                0.8,
                List.of(primary.position()),
                1,
                1,
                0
        );
    }

    private void applyMageAbilities(
            SemionTowerEntity source,
            SemionMonsterEntity primary,
            double attemptedDamage,
            int attackNumber
    ) {
        double slow = value("splashSlow", MAGE_SLOW[index()]);
        int slowTicks = HeroPartyBalance.towerInt(type().id(), "splashSlowDurationTicks", MAGE_SLOW_TICKS[index()]);
        if (slow > 0.0 && slowTicks > 0 && primary.isAlive()) {
            primary.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, slow, slowTicks);
        }

        int empoweredEvery = HeroPartyBalance.towerInt(
                type().id(), "empoweredEvery", MAGE_EMPOWERED_EVERY[index()]
        );
        boolean empowered = empoweredEvery > 0 && attackNumber % empoweredEvery == 0;
        double ratio = value("splashDamageRatio", MAGE_SPLASH_RATIO[index()]);
        double radius = value("splashRadius", MAGE_SPLASH_RADIUS[index()]);
        if (empowered) {
            ratio *= value("empoweredSplashMultiplier", MAGE_EMPOWERED_MULTIPLIER[index()]);
            radius += value("empoweredRadiusBonus", MAGE_EMPOWERED_RADIUS[index()]);
        }
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                MAGE_SPLASH,
                source,
                primary,
                radius,
                AreaVfxSpec.onTrigger(empowered ? AreaVfxStyles.PULSE : AreaVfxStyles.SPLASH)
        );
        double splashDamage = attemptedDamage * ratio;
        TowerAreaDamage.applyResolved(
                this,
                source,
                request,
                secondary -> resolveBasicAttackOutgoingDamage(source, secondary, splashDamage),
                true,
                (secondary, damage, killed) -> {
                    if (slow > 0.0 && slowTicks > 0 && secondary.isAlive()) {
                        secondary.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, slow, slowTicks);
                    }
                    state().recordCompanionAttack(HeroCompanionRole.MAGE, damage, killed, false, onlineOwner(source));
                    state().recordSpecial(HeroQuestKind.MAGE_SPLASH_HITS, null, 1.0, onlineOwner(source));
                },
                DamageType.MAGIC
        );
    }

    private void applyRogueCombo(
            SemionTowerEntity source,
            SemionMonsterEntity target,
            double attemptedDamage,
            int attackNumber
    ) {
        int every = HeroPartyBalance.towerInt(type().id(), "comboEvery", ROGUE_COMBO_EVERY[index()]);
        double ratio = value("comboDamageRatio", ROGUE_COMBO_RATIO[index()]);
        if (every <= 0 || attackNumber % every != 0 || ratio <= 0.0 || !target.isAlive()) {
            return;
        }
        double comboDamage = attemptedDamage * ratio;
        DamageResult result = damageBasicAttackTargetResult(source, target, comboDamage, primaryDamageType());
        state().recordCompanionAttack(
                HeroCompanionRole.ROGUE,
                result.dealtDamage(),
                result.killed(),
                false,
                onlineOwner(source)
        );
        if (result.killed()) {
            onKill(source, target, comboDamage);
        }
        TowerVfxService.showSecondaryAttack(source, target);
    }

    private SemionMonsterEntity nearestExtraTarget(SemionTowerEntity source, SemionMonsterEntity primary) {
        double rangeSqr = source.attackRange() * source.attackRange();
        return source.level().getEntities(
                        source,
                        source.targetSearchBox(),
                        entity -> entity instanceof SemionMonsterEntity monster
                                && monster.isAlive()
                                && monster != primary
                                && monster.runtimeMonster() != null
                                && source.defendsLane(monster.runtimeMonster().targetLaneId())
                                && source.distanceToSqr(monster) <= rangeSqr
                ).stream()
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .min(Comparator.comparingDouble(target -> target.distanceToSqr(source)))
                .orElse(null);
    }

    private double archerTargetMultiplier(SemionMonsterEntity target) {
        double bonus = isBoss(target) ? value("bossDamageBonus", ARCHER_BOSS_BONUS[index()]) : 0.0;
        if (isIncomeTarget(target)) {
            bonus += value("incomeDamageBonus", HeroPartyBalance.INCOME_DAMAGE_BONUS);
        }
        return 1.0 + bonus;
    }

    Optional<HeroCompanionRole> role() {
        return HeroPartyTowers.role(type());
    }

    int tier() {
        return Math.max(1, HeroPartyTowers.tier(type()));
    }

    int index() {
        return Math.max(0, Math.min(3, tier() - 1));
    }

    double value(String key, double fallback) {
        return HeroPartyBalance.tower(type().id(), key, fallback);
    }

    protected static String percent(double value) {
        return Math.round(value * 100.0) + "%";
    }

    private static int nearbyCount(SemionMonsterEntity target, List<SemionMonsterEntity> candidates) {
        if (target == null) {
            return 0;
        }
        return (int) candidates.stream().filter(other -> other != null && other.distanceToSqr(target) <= 9.0).count();
    }

    private static double maxHealth(SemionMonsterEntity target) {
        return target == null || target.runtimeMonster() == null ? 0.0 : target.runtimeMonster().maxHealth();
    }

    private static double healthRatio(SemionMonsterEntity target) {
        if (target == null || target.runtimeMonster() == null) {
            return 1.0;
        }
        return target.runtimeMonster().health() / Math.max(1.0, target.runtimeMonster().maxHealth());
    }

    private static boolean isBoss(SemionMonsterEntity target) {
        return target != null
                && target.runtimeMonster() != null
                && target.runtimeMonster().id().toLowerCase(java.util.Locale.ROOT).contains("boss");
    }
}
