package kim.biryeong.semiontd.tower.hero;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaTowerTarget;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaTargetMode;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;

public final class HeroCompanionTower extends HeroPartyTower {
    private static final ResourceLocation MAGE_SPLASH = ResourceLocation.fromNamespaceAndPath("semion-td", "hero_party_mage_splash");
    private static final ResourceLocation KNIGHT_GUARD = ResourceLocation.fromNamespaceAndPath("semion-td", "hero_party_knight_guard");
    private static final ResourceLocation BARD_AURA = ResourceLocation.fromNamespaceAndPath("semion-td", "hero_party_bard_aura");
    private static final ResourceLocation BARD_ENCORE = ResourceLocation.fromNamespaceAndPath("semion-td", "hero_party_bard_encore");
    private static final ResourceLocation ROGUE_HASTE = ResourceLocation.fromNamespaceAndPath("semion-td", "hero_party_rogue_haste");
    private static final double[] KNIGHT_REDUCTION = {0.0, 0.07, 0.13, 0.20};
    private static final int[] KNIGHT_BASH_EVERY = {0, 4, 4, 3};
    private static final double[] KNIGHT_BASH_SLOW = {0.0, 0.25, 0.25, 0.35};
    private static final int[] KNIGHT_BASH_TICKS = {0, 40, 40, 60};
    private static final double[] KNIGHT_GUARD_RADIUS = {0.0, 0.0, 5.0, 6.0};
    private static final double[] KNIGHT_GUARD_REDUCTION = {0.0, 0.0, 0.08, 0.12};
    private static final int[] KNIGHT_GUARD_TICKS = {0, 0, 40, 40};
    private static final double[] ARCHER_BOSS_BONUS = {0.0, 0.12, 0.23, 0.35};
    private static final int[] ARCHER_PIERCE_EVERY = {0, 4, 4, 3};
    private static final double[] ARCHER_PIERCE_RATIO = {0.0, 0.60, 0.60, 0.75};
    private static final double[] ARCHER_MARK_BONUS = {0.0, 0.0, 0.12, 0.15};
    private static final int[] ARCHER_MARK_TICKS = {0, 0, 60, 80};
    private static final double[] MAGE_SPLASH_RATIO = {0.30, 0.40, 0.50, 0.60};
    private static final double[] MAGE_SPLASH_RADIUS = {2.0, 2.3, 2.6, 3.0};
    private static final double[] MAGE_SLOW = {0.0, 0.20, 0.20, 0.30};
    private static final int[] MAGE_SLOW_TICKS = {0, 40, 40, 60};
    private static final int[] MAGE_EMPOWERED_EVERY = {0, 0, 5, 4};
    private static final double[] MAGE_EMPOWERED_MULTIPLIER = {0.0, 0.0, 1.50, 1.75};
    private static final double[] MAGE_EMPOWERED_RADIUS = {0.0, 0.0, 0.50, 0.75};
    private static final double[] PRIEST_HEAL = {28.0, 42.0, 62.0, 90.0};
    private static final int[] PRIEST_INTERVAL = {40, 38, 34, 30};
    private static final double[] PRIEST_SECOND = {0.0, 0.0, 0.50, 1.0};
    private static final double[] PRIEST_GUARD = {0.0, 0.08, 0.10, 0.15};
    private static final int[] PRIEST_GUARD_TICKS = {0, 60, 60, 60};
    private static final double[] ROGUE_EXECUTE = {0.25, 0.35, 0.47, 0.60};
    private static final int[] ROGUE_COMBO_EVERY = {0, 4, 4, 3};
    private static final double[] ROGUE_COMBO_RATIO = {0.0, 0.40, 0.40, 0.60};
    private static final double[] ROGUE_HASTE_BONUS = {0.0, 0.0, 0.20, 0.30};
    private static final int[] ROGUE_HASTE_TICKS = {0, 0, 60, 80};
    private static final double[] BARD_SPEED = {0.08, 0.11, 0.14, 0.18};
    private static final double[] BARD_DAMAGE = {0.0, 0.03, 0.06, 0.10};
    private static final double[] BARD_RADIUS = {8.0, 9.0, 10.0, 12.0};
    private static final int[] BARD_ENCORE_EVERY = {0, 0, 5, 4};
    private static final double[] BARD_ENCORE_BONUS = {0.0, 0.0, 0.10, 0.15};
    private static final int[] BARD_ENCORE_TICKS = {0, 0, 40, 40};

    private int attackCount;
    private int supportCooldown;
    private int supportPulseCount;
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
        supportPulseCount = 0;
    }

    @Override
    public void tick(PlayerLane lane) {
        super.tick(lane);
        HeroCompanionRole role = role().orElse(null);
        if (role != HeroCompanionRole.KNIGHT
                && role != HeroCompanionRole.PRIEST
                && role != HeroCompanionRole.BARD) {
            return;
        }
        if (supportCooldown > 0) {
            supportCooldown--;
            return;
        }
        if (role == HeroCompanionRole.KNIGHT) {
            applyKnightGuard(lane);
            supportCooldown = 20;
        } else if (role == HeroCompanionRole.PRIEST) {
            healParty(lane);
            supportCooldown = Math.max(1, HeroPartyBalance.towerInt(type().id(), "healIntervalTicks", PRIEST_INTERVAL[index()]));
        } else {
            applyBardAura(lane);
            supportPulseCount++;
            int encoreEvery = HeroPartyBalance.towerInt(type().id(), "encoreEveryPulses", BARD_ENCORE_EVERY[index()]);
            if (encoreEvery > 0 && supportPulseCount % encoreEvery == 0) {
                applyBardEncore(lane);
            }
            supportCooldown = 20;
        }
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof HeroCompanionTower companion) {
            attackCount = companion.attackCount;
            supportCooldown = companion.supportCooldown;
            supportPulseCount = companion.supportPulseCount;
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
            lines.addAll(abilityDetailLines(role, tier(), type().id()));
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
        List<String> details = abilityDetailLines(targetRole, targetTier, option.targetType().id());
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

    private void healParty(PlayerLane lane) {
        List<Tower> wounded = lane.towers().stream()
                .filter(tower -> tower.ownerPlayer().equals(ownerPlayer()))
                .filter(tower -> HeroPartyTowers.isHeroPartyTower(tower.type()))
                .filter(tower -> tower.health() > 0.0 && tower.health() < tower.currentMaxHealth())
                .sorted(Comparator.comparingDouble(tower -> tower.health() / Math.max(1.0, tower.currentMaxHealth())))
                .toList();
        if (wounded.isEmpty()) {
            return;
        }
        double heal = value("healAmount", PRIEST_HEAL[index()]);
        double reduction = value("healGuardReduction", PRIEST_GUARD[index()]);
        int reductionTicks = HeroPartyBalance.towerInt(
                type().id(), "healGuardDurationTicks", PRIEST_GUARD_TICKS[index()]
        );
        recordPriestHealing(lane, healTower(lane, wounded.get(0), heal, reduction, reductionTicks));
        double secondRatio = value("secondTargetRatio", PRIEST_SECOND[index()]);
        if (wounded.size() > 1 && secondRatio > 0.0) {
            recordPriestHealing(lane, healTower(
                    lane, wounded.get(1), heal * secondRatio, reduction, reductionTicks
            ));
        }
    }

    private double healTower(PlayerLane lane, Tower target, double amount, double reduction, int reductionTicks) {
        SemionTowerEntity entity = towerEntity(lane, target);
        if (entity != null) {
            double healed = healPartyMember(entity, amount);
            if (healed > 0.0 && reduction > 0.0 && reductionTicks > 0) {
                entity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION, reduction, reductionTicks);
                SemionTowerEntity source = towerEntity(lane, this);
                if (source != null) {
                    TowerVfxService.showAreaEffect(
                            source,
                            AreaEffectIds.tower(this, "priest_guard"),
                            AreaVfxStyles.BUFF,
                            entity.position(),
                            0.8,
                            List.of(entity.position()),
                            1,
                            1,
                            0
                    );
                }
            }
            return healed;
        }
        double previous = target.health();
        target.syncHealth(Math.min(target.currentMaxHealth(), target.health()
                + amount * HeroPartyBalance.partyHealingMultiplier(state().adventurePoints())));
        return Math.max(0.0, target.health() - previous);
    }

    private void recordPriestHealing(PlayerLane lane, double healed) {
        SemionTowerEntity source = towerEntity(lane, this);
        state().recordSpecial(HeroQuestKind.PRIEST_HEALING, null, healed, onlineOwner(source));
    }

    private void applyKnightGuard(PlayerLane lane) {
        double radius = value("guardRadius", KNIGHT_GUARD_RADIUS[index()]);
        double reduction = value("guardDamageReduction", KNIGHT_GUARD_REDUCTION[index()]);
        int ticks = HeroPartyBalance.towerInt(type().id(), "guardDurationTicks", KNIGHT_GUARD_TICKS[index()]);
        SemionTowerEntity source = towerEntity(lane, this);
        if (source == null || radius <= 0.0 || reduction <= 0.0 || ticks <= 0) {
            return;
        }
        TowerAreaEffectRequest request = TowerAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, "guard_formation"),
                source,
                radius,
                TowerAreaTargetMode.REGISTERED,
                AreaVfxSpec.onChange(AreaVfxStyles.BUFF)
        ).withFilter(target -> target.tower().ownerPlayer().equals(ownerPlayer())
                && HeroPartyTowers.isHeroPartyTower(target.tower().type()));
        SemionTdApi.areaEffects().applyToTowers(request, target -> {
            HeroCompanionTower provider = strongestProvider(lane, target.tower(), HeroCompanionRole.KNIGHT, false);
            if (provider != this) {
                return AreaEffectOutcome.UNCHANGED;
            }
            SemionTowerEntity entity = target.entity().orElse(null);
            if (entity == null) {
                return AreaEffectOutcome.UNCHANGED;
            }
            return entity.refreshTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION, KNIGHT_GUARD, reduction, ticks)
                    ? AreaEffectOutcome.APPLIED
                    : AreaEffectOutcome.UNCHANGED;
        });
    }

    private void applyBardAura(PlayerLane lane) {
        double radius = value("auraRadius", BARD_RADIUS[index()]);
        SemionTowerEntity source = towerEntity(lane, this);
        if (source == null || radius <= 0.0) {
            return;
        }
        TowerAreaEffectRequest request = new TowerAreaEffectRequest(
                AreaEffectIds.tower(this, "battle_song"),
                source,
                source.position(),
                radius,
                TowerAreaTargetMode.REGISTERED,
                true,
                target -> target.tower().ownerPlayer().equals(ownerPlayer())
                        && HeroPartyTowers.isHeroPartyTower(target.tower().type()),
                AreaVfxSpec.onChange(AreaVfxStyles.BUFF)
        );
        SemionTdApi.areaEffects().applyToTowers(request, target -> applyStrongestBardAura(lane, target));
    }

    private AreaEffectOutcome applyStrongestBardAura(PlayerLane lane, AreaTowerTarget target) {
        HeroCompanionTower provider = strongestProvider(lane, target.tower(), HeroCompanionRole.BARD, true);
        SemionTowerEntity entity = target.entity().orElse(null);
        if (provider == null || entity == null || provider != this) {
            return AreaEffectOutcome.UNCHANGED;
        }
        int providerIndex = provider.index();
        double speed = provider.value("attackSpeedBonus", BARD_SPEED[providerIndex]);
        double damage = provider.value("damageBonus", BARD_DAMAGE[providerIndex]);
        boolean changed = entity.refreshTimedEffect(
                TimedEffectType.TOWER_ATTACK_SPEED_BONUS, BARD_AURA, speed, 40
        );
        if (damage > 0.0) {
            changed |= entity.refreshTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, BARD_AURA, damage, 40);
        }
        state().recordSpecial(HeroQuestKind.BARD_AURA_SUPPORT, null, 1.0, onlineOwner(entity));
        return changed ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED;
    }

    private void applyBardEncore(PlayerLane lane) {
        double radius = value("auraRadius", BARD_RADIUS[index()]);
        double damage = value("encoreDamageBonus", BARD_ENCORE_BONUS[index()]);
        double speed = value("encoreAttackSpeedBonus", BARD_ENCORE_BONUS[index()]);
        int ticks = HeroPartyBalance.towerInt(type().id(), "encoreDurationTicks", BARD_ENCORE_TICKS[index()]);
        SemionTowerEntity source = towerEntity(lane, this);
        if (source == null || radius <= 0.0 || damage <= 0.0 || speed <= 0.0 || ticks <= 0) {
            return;
        }
        TowerAreaEffectRequest request = new TowerAreaEffectRequest(
                AreaEffectIds.tower(this, "encore"),
                source,
                source.position(),
                radius,
                TowerAreaTargetMode.REGISTERED,
                true,
                target -> target.tower().ownerPlayer().equals(ownerPlayer())
                        && HeroPartyTowers.isHeroPartyTower(target.tower().type()),
                AreaVfxSpec.onTrigger(AreaVfxStyles.PULSE)
        );
        SemionTdApi.areaEffects().applyToTowers(request, target -> {
            HeroCompanionTower provider = strongestProvider(lane, target.tower(), HeroCompanionRole.BARD, true);
            SemionTowerEntity entity = target.entity().orElse(null);
            if (provider != this || entity == null) {
                return AreaEffectOutcome.UNCHANGED;
            }
            boolean changed = entity.refreshTimedEffect(
                    TimedEffectType.TOWER_ATTACK_SPEED_BONUS, BARD_ENCORE, speed, ticks
            );
            changed |= entity.refreshTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, BARD_ENCORE, damage, ticks);
            return changed ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED;
        });
    }

    private double gridDistanceSqr(Tower tower) {
        double dx = tower.position().x() - position().x();
        double dy = tower.position().y() - position().y();
        double dz = tower.position().z() - position().z();
        return dx * dx + dy * dy + dz * dz;
    }

    private HeroCompanionTower strongestProvider(
            PlayerLane lane,
            Tower target,
            HeroCompanionRole providerRole,
            boolean includeSelf
    ) {
        return lane.towers().stream()
                .filter(HeroCompanionTower.class::isInstance)
                .map(HeroCompanionTower.class::cast)
                .filter(provider -> provider.ownerPlayer().equals(ownerPlayer()))
                .filter(provider -> provider.role().orElse(null) == providerRole)
                .filter(provider -> includeSelf || provider != target)
                .filter(provider -> provider.covers(target, providerRole))
                .max(Comparator.comparingInt(HeroCompanionTower::tier))
                .orElse(null);
    }

    private boolean covers(Tower target, HeroCompanionRole providerRole) {
        double radius = providerRole == HeroCompanionRole.KNIGHT
                ? value("guardRadius", KNIGHT_GUARD_RADIUS[index()])
                : value("auraRadius", BARD_RADIUS[index()]);
        return radius > 0.0 && gridDistanceSqr(target) <= radius * radius;
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

    private Optional<HeroCompanionRole> role() {
        return HeroPartyTowers.role(type());
    }

    private int tier() {
        return Math.max(1, HeroPartyTowers.tier(type()));
    }

    private int index() {
        return Math.max(0, Math.min(3, tier() - 1));
    }

    private double value(String key, double fallback) {
        return HeroPartyBalance.tower(type().id(), key, fallback);
    }

    private static List<String> abilityDetailLines(HeroCompanionRole role, int tier, String configId) {
        int index = Math.max(0, Math.min(3, tier - 1));
        if (tier < 2) {
            return List.of();
        }
        String first = switch (role) {
            case KNIGHT -> HeroPartyTowers.firstAbilityName(role) + ": "
                    + configuredInt(configId, "shieldBashEvery", KNIGHT_BASH_EVERY[index])
                    + "번째 공격, 이동/공격 속도 -"
                    + percent(configured(configId, "shieldBashSlow", KNIGHT_BASH_SLOW[index]))
                    + " (" + seconds(configuredInt(configId, "shieldBashDurationTicks", KNIGHT_BASH_TICKS[index])) + ")";
            case ARCHER -> HeroPartyTowers.firstAbilityName(role) + ": "
                    + configuredInt(configId, "pierceEvery", ARCHER_PIERCE_EVERY[index])
                    + "번째 공격이 다른 적에게 "
                    + percent(configured(configId, "pierceDamageRatio", ARCHER_PIERCE_RATIO[index])) + " 피해";
            case MAGE -> HeroPartyTowers.firstAbilityName(role) + ": 폭발 대상 이동 속도 -"
                    + percent(configured(configId, "splashSlow", MAGE_SLOW[index]))
                    + " (" + seconds(configuredInt(configId, "splashSlowDurationTicks", MAGE_SLOW_TICKS[index])) + ")";
            case PRIEST -> HeroPartyTowers.firstAbilityName(role) + ": 치유 대상이 받는 피해 -"
                    + percent(configured(configId, "healGuardReduction", PRIEST_GUARD[index]))
                    + " (" + seconds(configuredInt(configId, "healGuardDurationTicks", PRIEST_GUARD_TICKS[index])) + ")";
            case ROGUE -> HeroPartyTowers.firstAbilityName(role) + ": "
                    + configuredInt(configId, "comboEvery", ROGUE_COMBO_EVERY[index])
                    + "번째 공격에 "
                    + percent(configured(configId, "comboDamageRatio", ROGUE_COMBO_RATIO[index])) + " 추가타";
            case BARD -> HeroPartyTowers.firstAbilityName(role) + ": 주변 파티원 공격력 +"
                    + percent(configured(configId, "damageBonus", BARD_DAMAGE[index]))
                    + ", 공격 속도 +" + percent(configured(configId, "attackSpeedBonus", BARD_SPEED[index]));
        };
        if (tier < 3) {
            return List.of(first);
        }
        String second = switch (role) {
            case KNIGHT -> HeroPartyTowers.secondAbilityName(role) + ": 반경 "
                    + number(configured(configId, "guardRadius", KNIGHT_GUARD_RADIUS[index]))
                    + ", 파티원이 받는 피해 -"
                    + percent(configured(configId, "guardDamageReduction", KNIGHT_GUARD_REDUCTION[index]));
            case ARCHER -> HeroPartyTowers.secondAbilityName(role) + ": 대상이 받는 타워 피해 +"
                    + percent(configured(configId, "markDamageBonus", ARCHER_MARK_BONUS[index]))
                    + " (" + seconds(configuredInt(configId, "markDurationTicks", ARCHER_MARK_TICKS[index])) + ")";
            case MAGE -> HeroPartyTowers.secondAbilityName(role) + ": "
                    + configuredInt(configId, "empoweredEvery", MAGE_EMPOWERED_EVERY[index])
                    + "번째 공격, 폭발 "
                    + number(configured(configId, "empoweredSplashMultiplier", MAGE_EMPOWERED_MULTIPLIER[index]))
                    + "배 / 반경 +"
                    + number(configured(configId, "empoweredRadiusBonus", MAGE_EMPOWERED_RADIUS[index]));
            case PRIEST -> HeroPartyTowers.secondAbilityName(role) + ": 두 번째 파티원을 "
                    + percent(configured(configId, "secondTargetRatio", PRIEST_SECOND[index])) + "만큼 치유";
            case ROGUE -> HeroPartyTowers.secondAbilityName(role) + ": 처치 시 공격 속도 +"
                    + percent(configured(configId, "killAttackSpeedBonus", ROGUE_HASTE_BONUS[index]))
                    + " (" + seconds(configuredInt(
                            configId, "killAttackSpeedDurationTicks", ROGUE_HASTE_TICKS[index]
                    )) + ")";
            case BARD -> HeroPartyTowers.secondAbilityName(role) + ": "
                    + configuredInt(configId, "encoreEveryPulses", BARD_ENCORE_EVERY[index])
                    + "번째 노래마다 공격력/공격 속도 +"
                    + percent(configured(configId, "encoreDamageBonus", BARD_ENCORE_BONUS[index]))
                    + " (" + seconds(configuredInt(configId, "encoreDurationTicks", BARD_ENCORE_TICKS[index])) + ")";
        };
        return List.of(first, second);
    }

    private static double configured(String configId, String key, double fallback) {
        return HeroPartyBalance.tower(configId, key, fallback);
    }

    private static int configuredInt(String configId, String key, int fallback) {
        return HeroPartyBalance.towerInt(configId, key, fallback);
    }

    protected static String percent(double value) {
        return Math.round(value * 100.0) + "%";
    }

    private static String seconds(int ticks) {
        return number(ticks / 20.0) + "초";
    }

    private static String number(double value) {
        long rounded = Math.round(value);
        return Math.abs(value - rounded) < 0.000_001 ? Long.toString(rounded) : oneDecimal(value);
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
