package kim.biryeong.semiontd.tower.gamble;

import static kim.biryeong.semiontd.tower.gamble.GambleTowerStatsView.signed;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.visual.TowerEquipmentVisual;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.ui.SemionText;
import kim.biryeong.semiontd.ui.GambleRevealService;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class GamblerTower extends ProductionTower {
    static final TowerDataKey<GambleState> STATE = TowerDataKey.of(
            Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "gamble/state"), GambleState.class
    );

    private transient PlayerLane lane;
    private transient ArmorStand equipmentVisual;
    private double copiedHealthRatio = 1.0;
    private int jackpotCharges;
    private GambleReveal lastBetReveal;

    public GamblerTower(
            TowerType type, UUID ownerPlayer, TeamId teamId, int laneId,
            GridPosition originalPosition, GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        this.lane = lane;
        syncMaxHealth(state().resolvedValue(GambleStat.MAX_HEALTH, type().maxHealth()), false);
        syncHealth(currentMaxHealth() * copiedHealthRatio);
        copiedHealthRatio = 1.0;
        super.onPlaced(lane);
        syncEquipmentVisual();
    }

    @Override
    public void refreshType(TowerType type, PlayerLane lane) {
        if (type == null || !type().id().equals(type.id())) {
            return;
        }
        double healthRatio = health() / Math.max(1.0, currentMaxHealth());
        setData(STATE, state().rebalanced(type));
        super.refreshType(type, lane);
        syncHealth(currentMaxHealth() * healthRatio);
        promoteAfterBet(lane);
    }

    @Override
    protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) {
        GambleFacing.towardWave(entity, lane);
        entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(heldItem()));
        entity.setCustomName(Component.literal(type().displayName()));
        entity.setCustomNameVisible(true);
    }

    @Override
    public void onStateChanged(PlayerLane lane) {
        super.onStateChanged(lane);
        syncEquipmentVisual();
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        TowerEquipmentVisual.remove(equipmentVisual);
        equipmentVisual = null;
        super.onRemoved(lane);
    }

    @Override
    public void tick(PlayerLane lane) {
        this.lane = lane;
        super.tick(lane);
        runtimeEntity(lane).filter(entity -> entity.currentAttackTarget() == null)
                .ifPresent(entity -> GambleFacing.towardWave(entity, lane));
        syncEquipmentVisual();
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        this.lane = lane;
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        copiedHealthRatio = previousTower.health() / Math.max(1.0, previousTower.currentMaxHealth());
        if (previousTower instanceof GamblerTower previous) {
            jackpotCharges = previous.jackpotCharges;
        }
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        jackpotCharges = 0;
        super.resetForRound(lane);
    }

    @Override
    public double effectBaseMaxHealth() {
        return state().resolvedValue(GambleStat.MAX_HEALTH, type().maxHealth());
    }

    @Override
    protected void refreshMaxHealthAfterTypeChange(PlayerLane lane) {
        syncMaxHealth(effectBaseMaxHealth(), false);
    }

    @Override
    public double adjustAttackRange(double baseRange) {
        return state().resolvedValue(GambleStat.RANGE, baseRange);
    }

    @Override
    public double modifyAttackDamage(
            SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount
    ) {
        return Math.max(0.0, damageAmount + state().damageDelta()) + magicAttackDamage(towerEntity);
    }

    double magicAttackDamage(SemionTowerEntity source) {
        double bonus = source == null ? 0.0 : source.activeEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS);
        double flat = source == null ? 0.0 : source.activeEffectMagnitude(TimedEffectType.TOWER_FLAT_MAGIC_DAMAGE_BONUS);
        return Math.max(0.0, GambleBalance.baseMagicDamage(type()) * (1.0 + bonus) + flat + state().magicDamageDelta());
    }

    /** Target-independent damage shown in the stat panel, using the combat split and final modifiers. */
    public AttackDamage currentAttackDamage(SemionTowerEntity source) {
        double total = source == null
                ? modifyAttackDamage(null, null, type().damage() + permanentFlatDamageBonus())
                : resolveBasicAttackOutgoingDamage(source, null, source.attackDamageAmount(null));
        return splitAttackDamage(total, magicAttackShare(source));
    }

    public record AttackDamage(double physical, double magic) {
    }

    private static AttackDamage splitAttackDamage(double total, double magicShare) {
        return new AttackDamage(total * (1.0 - magicShare), total * magicShare);
    }

    private double magicAttackShare(SemionTowerEntity source) {
        // Mirror the shared pre-target physical modifiers for the split. Target, trait,
        // and final modifiers are applied once to the combined attack before splitting.
        double physical = (type().damage() + permanentFlatDamageBonus())
                * (1.0 + (source == null ? 0.0 : source.activeEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS)))
                + (source == null ? 0.0 : source.activeEffectMagnitude(TimedEffectType.TOWER_FLAT_DAMAGE_BONUS))
                - (source == null ? 0.0 : source.activeEffectMagnitude(TimedEffectType.TOWER_FLAT_DAMAGE_REDUCTION))
                + state().damageDelta();
        double magic = magicAttackDamage(source);
        double total = Math.max(0.0, physical) + magic;
        return total > 0.0 ? magic / total : 0.0;
    }

    @Override
    public DamageResult damageBasicAttackTargetResult(
            SemionTowerEntity source, SemionMonsterEntity target, double baseDamage
    ) {
        if (source == null || target == null || !Double.isFinite(baseDamage)) {
            return DamageResult.NONE;
        }
        return damageResolvedBasicAttackTargetResult(source, target,
                resolveBasicAttackOutgoingDamage(source, target, baseDamage));
    }

    @Override
    protected DamageResult damageResolvedBasicAttackTargetResult(
            SemionTowerEntity source, SemionMonsterEntity target, double outgoingDamage
    ) {
        return damageMixedTarget(source, target, outgoingDamage, magicAttackShare(source));
    }

    private DamageResult damageMixedTarget(
            SemionTowerEntity source, SemionMonsterEntity target, double resolvedDamage, double magicShare
    ) {
        AttackDamage components = splitAttackDamage(resolvedDamage, magicShare);
        DamageResult physical = damageResolvedTargetResult(source, target,
                components.physical(), DamageType.PHYSICAL);
        DamageResult magic = physical.killed() ? DamageResult.NONE
                : damageResolvedTargetResult(source, target, components.magic(), DamageType.MAGIC);
        return new DamageResult(physical.killed() || magic.killed(),
                physical.dealtDamage() + magic.dealtDamage(), resolvedDamage,
                physical.healthDamageAttempted() + magic.healthDamageAttempted(),
                Math.max(physical.healthBeforeHit(), magic.healthBeforeHit()));
    }

    @Override
    public void onAttackResolved(
            SemionTowerEntity source, SemionMonsterEntity target, double attemptedDamage,
            double resolvedOutgoingDamage, double dealtDamage, boolean killedTarget
    ) {
        applyBasicSplash(source, target, resolvedOutgoingDamage);
        if (AugmentCombat.allowsTriggers() && jackpotCharges > 0 && source != null && target != null
                && dealtDamage > 0.0 && augmentSnapshot().has("job_gamble_p")) {
            jackpotCharges--;
            double damage = source.attackDamageAmount(target)
                    * augmentSnapshot().parameter("job_gamble_p", "jackpotDamageRatio", 6.0);
            MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                    AreaEffectIds.tower(this, "jackpot"), source, target.position(),
                    augmentSnapshot().parameter("job_gamble_p", "jackpotRadius", 3.0),
                    Set.of(), null, AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH))
                    .nearestTargets((int) augmentSnapshot().parameter("job_gamble_p", "maxTargets", 12));
            double magicShare = magicAttackShare(source);
            AugmentCombat.runWithoutTriggers(() -> SemionTdApi.areaEffects().applyToMonsters(request, monster -> {
                double outgoing = resolveBasicAttackOutgoingDamage(source, monster, damage);
                DamageResult result = damageMixedTarget(source, monster, outgoing, magicShare);
                if (result.killed()) onKill(source, monster, outgoing);
                return result.killed() ? AreaEffectOutcome.KILLED
                        : result.dealtDamage() > 0.0 ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED;
            }));
        }
    }

    @Override
    public void onUpgradeApplied(PlayerLane lane, TowerUpgradeOption option) {
        GambleBet.fromUpgradeId(option.id()).ifPresent(bet -> resolveBet(lane, bet));
    }

    @Override
    public void onUpgradeCompleted(PlayerLane lane, Tower previousTower, TowerUpgradeOption option) {
        if (GambleBet.fromUpgradeId(option.id()).isPresent()) {
            promoteAfterBet(lane);
        }
    }

    @Override
    public boolean meetsUpgradeRequirements(PlayerLane lane, TowerUpgradeOption option) {
        return GambleBet.fromUpgradeId(option.id())
                .map(bet -> !state().atScoreCap()
                        && hasRequiredSupport(lane, bet)).orElse(true);
    }

    private boolean hasRequiredSupport(PlayerLane lane, GambleBet bet) {
        if (bet == GambleBet.ODD || bet == GambleBet.EVEN) {
            return true;
        }
        return lane != null && lane.towers().stream().anyMatch(tower ->
                ownerPlayer().equals(tower.ownerPlayer())
                        && (bet == GambleBet.TWO_DICE ? GambleTowers.isDice(tower.type())
                        : GambleTowers.isSpectator(tower.type())) && !tower.isDestroyed(lane));
    }

    @Override
    public boolean showsUnavailableUpgrade(PlayerLane lane, TowerUpgradeOption option) {
        return GambleBet.fromUpgradeId(option.id()).isPresent() && !state().atScoreCap();
    }

    @Override
    public boolean upgradeCostAddsToSaleValue(TowerUpgradeOption option) {
        return GambleBet.fromUpgradeId(option.id()).isEmpty();
    }

    @Override
    public List<String> upgradeTooltipLines(TowerUpgradeOption option) {
        return GambleTowerStatsView.upgradeTooltipLines(this, option);
    }

    @Override
    public List<String> runtimeDetailLines() {
        return GambleTowerStatsView.runtimeDetailLines(this);
    }

    GambleState state() {
        return getDataOrDefault(STATE, GambleState.EMPTY);
    }

    double gambleScore() {
        return state().cumulativeScore();
    }

    private void resolveBet(PlayerLane lane, GambleBet bet) {
        SemionTowerEntity source = GambleRoundEffects.towerEntity(this, lane).orElse(null);
        if (source == null || state().atScoreCap()) {
            return;
        }
        double healthRatio = health() / Math.max(1.0, currentMaxHealth());
        resolvePurchase(bet, source.getRandom());
        syncMaxHealth(effectBaseMaxHealth(), false);
        syncHealth(currentMaxHealth() * healthRatio);
        onStateChanged(lane);
        var player = source.level().getServer().getPlayerList().getPlayer(ownerPlayer());
        if (lastBetReveal != null) {
            GambleRevealService.start(player, lastBetReveal);
        }
    }

    int resolvePurchase(GambleBet bet, RandomSource random) {
        lastBetReveal = null;
        if (state().atScoreCap()) return 0;
        boolean allIn = bet != GambleBet.SLOTS && AugmentCombat.allowsTriggers()
                && augmentSnapshot().has("job_gamble_p");
        int maximum = allIn ? (int) augmentSnapshot().parameter("job_gamble_p", "maxAttempts", 3) : 1;
        ArrayList<String> results = new ArrayList<>();
        int attempts = 0;
        boolean succeeded = false;
        for (; attempts < maximum; attempts++) {
            succeeded = resolveAttempt(bet, random);
            results.add(state().lastResult());
            if (succeeded) {
                attempts++;
                if (allIn) {
                    jackpotCharges = Math.min(jackpotCharges + 1,
                            (int) augmentSnapshot().parameter("job_gamble_p", "maxCharges", 3));
                }
                break;
            }
        }
        if (allIn && !succeeded) {
            double loss = augmentSnapshot().parameter("job_gamble_p", "allFailedScoreLoss", 3);
            String penalty = "전부 실패, 점수 -" + oneDecimal(loss);
            setData(STATE, state().adjustScore(-loss, state().lastResult() + " · " + penalty));
            results.add(penalty);
        }
        if (attempts > 1) {
            String summary = String.join(" / ", results);
            setData(STATE, state().adjustScore(0, summary));
            lastBetReveal = new GambleReveal(lastBetReveal.kind(), lastBetReveal.outcomes(),
                    lastBetReveal.label(), attempts + "회 시도 · " + lastBetReveal.caption(), summary, succeeded);
        }
        return attempts;
    }

    int jackpotCharges() {
        return jackpotCharges;
    }

    private boolean resolveAttempt(GambleBet bet, RandomSource random) {
        double score;
        int rewardCount;
        String roll;
        List<Integer> revealOutcomes;
        if (bet == GambleBet.SLOTS) {
            GambleSlots.Symbol[] symbols = GambleSlots.Symbol.values();
            revealOutcomes = List.of(random.nextInt(symbols.length),
                    random.nextInt(symbols.length), random.nextInt(symbols.length));
            GambleSlots.Result result = GambleSlots.resolve(
                    symbols[revealOutcomes.get(0)], symbols[revealOutcomes.get(1)], symbols[revealOutcomes.get(2)]);
            score = result.score();
            rewardCount = result.statRewardCount();
            roll = result.display();
        } else {
            int first = random.nextInt(6) + 1;
            int second = bet == GambleBet.TWO_DICE ? random.nextInt(6) + 1 : 0;
            revealOutcomes = second == 0 ? List.of(first) : List.of(first, second);
            score = bet == GambleBet.TWO_DICE ? GambleRolls.twoDiceDelta(first, second)
                    : GambleRolls.oddEvenDelta(bet, first);
            rewardCount = bet == GambleBet.TWO_DICE ? GambleRolls.twoDiceStatRewardCount(first, second) : 1;
            roll = GambleRolls.formatResultRoll(bet, first, second);
        }
        GambleState before = state();
        boolean bottomKing = bet != GambleBet.SLOTS && AugmentCombat.allowsTriggers()
                && augmentSnapshot().has("job_gamble_g2");
        double settledScore = GambleRewards.settledScore(score, bottomKing
                ? augmentSnapshot().parameter("job_gamble_g2", "failureScoreMultiplier", 2.0) : 1.0);
        boolean reverseLoss = bottomKing && score < 0.0
                && random.nextDouble() < augmentSnapshot().parameter("job_gamble_g2", "statReversalChance", .2);
        GambleAbility ability = null;
        ArrayList<String> results = new ArrayList<>();
        if (bet != GambleBet.SLOTS && GambleRewards.awardsAbility(before, score, random.nextDouble())) {
            ability = GambleRewards.chooseMissing(
                    before, random.nextInt(GambleRewards.missingAbilities(before).size()));
            results.add(ability.displayName() + " 획득");
        }
        double statScore = GambleRewards.statRewardScore(score, ability);
        ArrayList<GambleState.StatChange> changes = new ArrayList<>();
        if (statScore != 0.0) {
            List<GambleStat> stats = rewardCount == 2
                    ? GambleRewards.chooseDistinctStats(
                            random.nextInt(GambleRewards.rollableStatCount()),
                            random.nextInt(GambleRewards.rollableStatCount() - 1))
                    : List.of(GambleRewards.chooseStat(random.nextInt(GambleRewards.rollableStatCount())));
            double scorePerStat = statScore / stats.size();
            for (GambleStat stat : stats) {
                double delta = GambleRewards.settledStatDelta(
                        before, GambleBalance.statDelta(stat, scorePerStat), reverseLoss);
                changes.add(new GambleState.StatChange(stat, delta, baseValue(stat)));
                results.add(stat.displayName() + " " + signed(delta));
            }
        }
        String rewardSummary = String.join(", ", results);
        setData(STATE, before.recordReward(changes, ability, settledScore,
                bet.displayName() + " " + roll + " → " + rewardSummary));
        lastBetReveal = new GambleReveal(
                bet == GambleBet.SLOTS ? GambleReveal.Kind.SLOTS : GambleReveal.Kind.DICE,
                revealOutcomes, bet.displayName(),
                (bet == GambleBet.SLOTS ? (rewardCount == 2 ? "잭팟!" : "강화") : roll) + " · " + signed(settledScore) + "점",
                rewardSummary, score > 0.0);
        return score > 0.0;
    }

    private double baseValue(GambleStat stat) {
        return switch (stat) {
            case MAX_HEALTH -> type().maxHealth();
            case DAMAGE -> type().damage();
            case MAGIC_DAMAGE -> GambleBalance.baseMagicDamage(type());
            case RANGE -> type().range();
            case SPLASH_RADIUS -> splashRadius();
        };
    }

    double splashRadius() {
        return GambleBalance.gamblerSplashRadius(type());
    }

    private net.minecraft.world.item.Item heldItem() {
        if (type().id().equals(GambleTowers.KING.id())) {
            return Items.DIAMOND;
        }
        if (type().id().equals(GambleTowers.DARK_KING.id())) {
            return Items.NETHERITE_INGOT;
        }
        return Items.GOLD_INGOT;
    }

    private void promoteAfterBet(PlayerLane lane) {
        TowerType targetType = GambleTowers.promotionTarget(type(), state().cumulativeScore());
        if (targetType == null || lane == null) {
            return;
        }
        Tower replacement = ProductionTowerCatalog.find(targetType.id())
                .map(entry -> entry.create(
                        ownerPlayer(), teamId(), laneId(), originalPosition(), position()))
                .orElse(null);
        if (!(replacement instanceof GamblerTower promoted)) {
            return;
        }
        promoted.copyFrom(this, 0L);
        if (!lane.replaceTower(this, promoted)) {
            return;
        }
        promoted.showPromotionResult(lane);
    }

    private void showPromotionResult(PlayerLane lane) {
        SemionTowerEntity source = GambleRoundEffects.towerEntity(this, lane).orElse(null);
        if (source == null) {
            return;
        }
        if (source.level() instanceof net.minecraft.server.level.ServerLevel level) {
            level.sendParticles(type().id().equals(GambleTowers.DARK_KING.id())
                            ? ParticleTypes.WITCH : ParticleTypes.HAPPY_VILLAGER,
                    source.getX(), source.getY() + 1.0, source.getZ(), 40, 0.55, 0.65, 0.55, 0.08);
        }
        if (source.level().getServer() != null) {
            var player = source.level().getServer().getPlayerList().getPlayer(ownerPlayer());
            if (player != null) {
                player.sendSystemMessage(SemionText.prefixedPlain(
                        "누적 도박 점수 " + signed(state().cumulativeScore()) + " 달성! "
                                + type().displayName() + "으로 전직했습니다."));
            }
        }
    }

    private void applyBasicSplash(
            SemionTowerEntity source, SemionMonsterEntity primary, double resolvedOutgoingDamage
    ) {
        double radius = splashRadius();
        double ratio = GambleBalance.splashDamageRatio();
        if (source == null || primary == null || radius <= 0.0 || ratio <= 0.0
                || resolvedOutgoingDamage <= 0.0) {
            return;
        }
        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                AreaEffectIds.tower(this, "basic_splash"),
                source,
                primary.position(),
                radius,
                Set.of(primary.getUUID()),
                null,
                AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)
        );
        double magicShare = magicAttackShare(source);
        SemionTdApi.areaEffects().applyToMonsters(request, target -> {
            DamageResult result = damageMixedTarget(source, target, resolvedOutgoingDamage * ratio, magicShare);
            if (result.killed()) {
                onKill(source, target, resolvedOutgoingDamage * ratio);
            }
            return result.killed() ? AreaEffectOutcome.KILLED
                    : result.dealtDamage() > 0.0 ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED;
        });
    }

    private void syncEquipmentVisual() {
        equipmentVisual = TowerEquipmentVisual.sync(
                equipmentVisual, GambleRoundEffects.towerEntity(this, lane).orElse(null)
        );
    }
}
