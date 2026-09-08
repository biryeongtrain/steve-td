package kim.biryeong.semiontd.tower.gamble;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class GamblerTower extends ProductionTower {
    static final TowerDataKey<GambleState> STATE = TowerDataKey.of(
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "gamble/state"), GambleState.class
    );

    private transient PlayerLane lane;
    private transient ArmorStand equipmentVisual;
    private double copiedHealthRatio = 1.0;

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
        return damageMixedTarget(source, target,
                resolveBasicAttackOutgoingDamage(source, target, baseDamage), magicAttackShare(source));
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
                physical.dealtDamage() + magic.dealtDamage(), resolvedDamage);
    }

    @Override
    public void onAttackResolved(
            SemionTowerEntity source, SemionMonsterEntity target, double attemptedDamage,
            double resolvedOutgoingDamage, double dealtDamage, boolean killedTarget
    ) {
        applyBasicSplash(source, target, resolvedOutgoingDamage);
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
        return GambleBet.fromUpgradeId(option.id()).map(bet -> switch (bet) {
            case ODD -> List.of(
                    "주사위 한 개를 굴려 홀수가 나오면 능력치가 오르고, 짝수가 나오면 내려갑니다.",
                    "성공하면 " + statRewardSummary(GambleBalance.oddEvenWinScore()) + " 중 하나를 얻습니다.",
                    "실패하면 " + statRewardSummary(-GambleBalance.oddEvenLossScore())
                            + " 중 하나가 적용됩니다.",
                    "손실 보험 보유 시 실패 수치는 " + statRewardSummary(-GambleBalance.oddEvenLossScore()
                            * (1.0 - GambleBalance.lossInsuranceReduction())) + "로 완화됩니다.",
                    "비용은 판매 환불가에 포함되지 않습니다."
            );
            case EVEN -> List.of(
                    "주사위 한 개를 굴려 짝수가 나오면 능력치가 오르고, 홀수가 나오면 내려갑니다.",
                    "성공하면 " + statRewardSummary(GambleBalance.oddEvenWinScore()) + " 중 하나를 얻습니다.",
                    "실패하면 " + statRewardSummary(-GambleBalance.oddEvenLossScore())
                            + " 중 하나가 적용됩니다.",
                    "손실 보험 보유 시 실패 수치는 " + statRewardSummary(-GambleBalance.oddEvenLossScore()
                            * (1.0 - GambleBalance.lossInsuranceReduction())) + "로 완화됩니다.",
                    "비용은 판매 환불가에 포함되지 않습니다."
            );
            case TWO_DICE -> List.of(
                    "내 라인에 살아 있는 내 주사위 타워가 필요합니다 (단계·거리 무관).",
                    "주사위 두 개를 굴려 눈금의 합에 비례해 유닛을 업그레이드합니다.",
                    "합이 2~5면 능력치가 크게 내려가고, 6~12면 크게 올라갑니다.",
                    "합이 " + GambleBalance.twoDiceCompoundMinSum()
                            + " 이상이면 보상을 서로 다른 능력치 두 개가 절반씩 나눠 받습니다.",
                    "가장 자주 나오는 합 7은 " + statRewardSummary(GambleBalance.twoDiceScore(7))
                            + " 중 하나를 줍니다.",
                    "성공 시 " + oneDecimal(GambleBalance.abilityRewardChance() * 100) + "% 확률로 손실 보험을 얻으며, " + oneDecimal(GambleBalance.oddEvenWinScore())
                            + "점까지만 보험으로 바뀌고 나머지는 능력치로 지급됩니다.",
                    "같은 눈이 나오면 변화량이 두 배가 되며 비용은 판매 환불가에 포함되지 않습니다."
            );
            case SLOTS -> slotTooltipLines();
        }).orElseGet(List::of);
    }

    @Override
    public List<String> runtimeDetailLines() {
        GambleState state = state();
        ArrayList<String> lines = new ArrayList<>();
        lines.add("도박 횟수: " + state.totalBets());
        lines.add("누적 도박 점수: " + signed(state.cumulativeScore())
                + " / +" + oneDecimal(GambleBalance.maxGambleScore()));
        if (state.atScoreCap()) {
            lines.add("도박 상태: 종료 (최대 점수 도달)");
        }
        lines.add("최대 체력 변화: " + signed(state.maxHealthDelta()));
        lines.add("공격력 변화: " + signed(state.damageDelta()));
        lines.add("마법 공격력 변화: " + signed(state.magicDamageDelta()));
        lines.add("기본 공격 구성: 일반 " + oneDecimal(state.resolvedValue(GambleStat.DAMAGE, type().damage()))
                + " / 마법 " + oneDecimal(magicAttackDamage(null)));
        lines.add("사거리 변화: " + signed(state.rangeDelta()));
        lines.add("고정 공격 범위: " + oneDecimal(splashRadius()) + "칸");
        if (state.abilities().isEmpty()) {
            lines.add("보유 능력: 없음");
        } else {
            lines.add("보유 능력:");
            for (GambleAbility ability : GambleAbility.values()) {
                if (state.has(ability)) {
                    lines.add(ability.detailLine());
                }
            }
        }
        lines.add("최근 결과: " + state.lastResult());
        return List.copyOf(lines);
    }

    GambleState state() {
        return getDataOrDefault(STATE, GambleState.EMPTY);
    }

    double gambleScore() {
        return state().cumulativeScore();
    }

    private void resolveBet(PlayerLane lane, GambleBet bet) {
        if (state().atScoreCap()) {
            return;
        }
        SemionTowerEntity source = GambleRoundEffects.towerEntity(this, lane).orElse(null);
        if (source == null) {
            return;
        }
        double score;
        int rewardCount;
        String roll;
        List<Integer> revealOutcomes;
        if (bet == GambleBet.SLOTS) {
            GambleSlots.Symbol[] symbols = GambleSlots.Symbol.values();
            revealOutcomes = List.of(source.getRandom().nextInt(symbols.length),
                    source.getRandom().nextInt(symbols.length), source.getRandom().nextInt(symbols.length));
            GambleSlots.Result result = GambleSlots.resolve(
                    symbols[revealOutcomes.get(0)], symbols[revealOutcomes.get(1)], symbols[revealOutcomes.get(2)]);
            score = result.score();
            rewardCount = result.statRewardCount();
            roll = result.display();
        } else {
            int first = source.getRandom().nextInt(6) + 1;
            int second = bet == GambleBet.TWO_DICE ? source.getRandom().nextInt(6) + 1 : 0;
            revealOutcomes = second == 0 ? List.of(first) : List.of(first, second);
            score = bet == GambleBet.TWO_DICE ? GambleRolls.twoDiceDelta(first, second)
                    : GambleRolls.oddEvenDelta(bet, first);
            rewardCount = bet == GambleBet.TWO_DICE ? GambleRolls.twoDiceStatRewardCount(first, second) : 1;
            roll = GambleRolls.formatResultRoll(bet, first, second);
        }
        GambleState before = state();
        double healthRatio = health() / Math.max(1.0, currentMaxHealth());
        GambleAbility ability = null;
        ArrayList<String> results = new ArrayList<>();
        if (bet != GambleBet.SLOTS && GambleRewards.awardsAbility(before, score, source.getRandom().nextDouble())) {
            ability = GambleRewards.chooseMissing(
                    before, source.getRandom().nextInt(GambleRewards.missingAbilities(before).size())
            );
            results.add(ability.displayName() + " 획득");
        }
        double statScore = GambleRewards.statRewardScore(score, ability);
        ArrayList<GambleState.StatChange> changes = new ArrayList<>();
        if (statScore != 0.0) {
            List<GambleStat> stats = rewardCount == 2
                    ? GambleRewards.chooseDistinctStats(
                            source.getRandom().nextInt(GambleRewards.rollableStatCount()),
                            source.getRandom().nextInt(GambleRewards.rollableStatCount() - 1))
                    : List.of(GambleRewards.chooseStat(
                            source.getRandom().nextInt(GambleRewards.rollableStatCount())));
            double scorePerStat = statScore / stats.size();
            for (GambleStat stat : stats) {
                double delta = GambleRewards.insuredDelta(before, GambleBalance.statDelta(stat, scorePerStat));
                changes.add(new GambleState.StatChange(stat, delta, baseValue(stat)));
                results.add(stat.displayName() + " " + signed(delta));
            }
        }
        String rewardSummary = String.join(", ", results);
        GambleState after = before.recordReward(changes, ability, score,
                bet.displayName() + " " + roll + " → " + rewardSummary);
        setData(STATE, after);
        syncMaxHealth(effectBaseMaxHealth(), false);
        syncHealth(currentMaxHealth() * healthRatio);
        onStateChanged(lane);
        var player = source.getServer().getPlayerList().getPlayer(ownerPlayer());
        GambleRevealService.start(player, new GambleReveal(
                bet == GambleBet.SLOTS ? GambleReveal.Kind.SLOTS : GambleReveal.Kind.DICE,
                revealOutcomes, bet.displayName(),
                (bet == GambleBet.SLOTS ? (rewardCount == 2 ? "잭팟!" : "강화") : roll) + " · " + signed(score) + "점",
                rewardSummary, score > 0.0));
    }

    private static List<String> slotTooltipLines() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("내 라인에 살아 있는 내 슬롯머신 타워가 필요합니다 (단계·거리 무관).");
        lines.add("6종 심볼을 같은 확률로 세 칸에 뽑습니다. 순서와 관계없이 일치를 판정합니다.");
        lines.add("전부 다름 55.56% / 2개 일치 41.67% / 3개 일치 2.78%");
        GambleSlots.Symbol[] symbols = GambleSlots.Symbol.values();
        lines.add("전부 다르면 " + statRewardSummary(GambleSlots.resolve(
                symbols[0], symbols[1], symbols[2]).score()) + " 중 하나를 얻습니다.");
        for (GambleSlots.Symbol symbol : symbols) {
            GambleSlots.Symbol other = symbols[(symbol.ordinal() + 1) % symbols.length];
            lines.add(symbol.displayName() + ": 2개 +" + oneDecimal(GambleSlots.resolve(symbol, symbol, other).score())
                    + "점 / 3개 +" + oneDecimal(GambleSlots.resolve(symbol, symbol, symbol).score()) + "점");
        }
        lines.add("3개 일치는 서로 다른 능력치 두 개가 보상을 절반씩 나눠 받습니다.");
        lines.add("능력치 감소와 손실 보험 획득은 없으며 비용은 판매 환불가에 포함되지 않습니다.");
        return List.copyOf(lines);
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
        if (source.getServer() != null) {
            var player = source.getServer().getPlayerList().getPlayer(ownerPlayer());
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

    private static String signed(double value) {
        return (value >= 0.0 ? "+" : "") + oneDecimal(value);
    }

    private static String statRewardSummary(double score) {
        return "체력 " + signed(GambleBalance.statDelta(GambleStat.MAX_HEALTH, score))
                + "·공격력 " + signed(GambleBalance.statDelta(GambleStat.DAMAGE, score))
                + "·마법 공격력 " + signed(GambleBalance.statDelta(GambleStat.MAGIC_DAMAGE, score))
                + "·사거리 " + signed(GambleBalance.statDelta(GambleStat.RANGE, score));
    }
}
