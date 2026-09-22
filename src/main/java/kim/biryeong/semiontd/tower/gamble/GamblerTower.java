package kim.biryeong.semiontd.tower.gamble;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
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
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
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
    private int jackpotCharges;
    private boolean lastPurchaseSucceeded;

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
        return damageAmount + state().damageDelta();
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
            AugmentCombat.runWithoutTriggers(() -> TowerAreaDamage.applyResolved(this, source, request,
                    monster -> resolveBasicAttackOutgoingDamage(source, monster, damage), true,
                    (monster, amount, killed) -> {}, primaryDamageType()));
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
        return GambleBet.fromUpgradeId(option.id()).isEmpty() || !state().atScoreCap();
    }

    @Override
    public boolean upgradeCostAddsToSaleValue(TowerUpgradeOption option) {
        return GambleBet.fromUpgradeId(option.id()).isEmpty();
    }

    @Override
    public List<String> upgradeTooltipLines(TowerUpgradeOption option) {
        boolean bottomKing = augmentSnapshot().has("job_gamble_g2");
        double lossDirection = bottomKing ? 1.0 : -1.0;
        ArrayList<String> lines = new ArrayList<>(GambleBet.fromUpgradeId(option.id()).map(bet -> switch (bet) {
            case ODD -> List.of(
                    "주사위 한 개를 굴려 홀수가 나오면 성공, 짝수가 나오면 실패합니다.",
                    "성공하면 " + statRewardSummary(GambleBalance.oddEvenWinScore()) + " 중 하나를 얻습니다.",
                    "실패하면 " + statRewardSummary(lossDirection * GambleBalance.oddEvenLossScore())
                            + " 중 하나가 적용됩니다.",
                    "손실 보험 보유 시 실패 수치는 " + statRewardSummary(lossDirection * GambleBalance.oddEvenLossScore()
                            * (1.0 - GambleBalance.lossInsuranceReduction())) + "로 완화됩니다.",
                    "비용은 판매 환불가에 포함되지 않습니다."
            );
            case EVEN -> List.of(
                    "주사위 한 개를 굴려 짝수가 나오면 성공, 홀수가 나오면 실패합니다.",
                    "성공하면 " + statRewardSummary(GambleBalance.oddEvenWinScore()) + " 중 하나를 얻습니다.",
                    "실패하면 " + statRewardSummary(lossDirection * GambleBalance.oddEvenLossScore())
                            + " 중 하나가 적용됩니다.",
                    "손실 보험 보유 시 실패 수치는 " + statRewardSummary(lossDirection * GambleBalance.oddEvenLossScore()
                            * (1.0 - GambleBalance.lossInsuranceReduction())) + "로 완화됩니다.",
                    "비용은 판매 환불가에 포함되지 않습니다."
            );
            case TWO_DICE -> List.of(
                    "주사위 두 개를 굴려 눈금의 합에 비례해 유닛을 업그레이드합니다.",
                    bottomKing ? "합이 2~5면 실패하지만 능력치는 원래 감소량만큼 증가합니다. 6~12면 성공합니다."
                            : "합이 2~5면 능력치가 크게 내려가고, 6~12면 크게 올라갑니다.",
                    "합이 " + GambleBalance.twoDiceCompoundMinSum()
                            + " 이상이면 보상을 서로 다른 능력치 두 개가 절반씩 나눠 받습니다.",
                    "가장 자주 나오는 합 7은 " + statRewardSummary(GambleBalance.twoDiceScore(7))
                            + " 중 하나를 줍니다.",
                    "같은 눈이 나오면 변화량이 두 배가 되며 비용은 판매 환불가에 포함되지 않습니다."
            );
        }).orElseGet(List::of));
        if (bottomKing) {
            lines.add("바닥의 왕: 실패마다 점수 손실 "
                    + oneDecimal(augmentSnapshot().parameter("job_gamble_g2", "failureScoreMultiplier", 2))
                    + "배, 능력치는 원래 감소할 양만큼 증가합니다.");
        }
        if (augmentSnapshot().has("job_gamble_p")) {
            lines.add("끝장을 보자: 한 번 결제하고 성공할 때까지 최대 "
                    + (int) augmentSnapshot().parameter("job_gamble_p", "maxAttempts", 3)
                    + "회 시도하며 매 시도를 정산합니다.");
        }
        return List.copyOf(lines);
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
        if (augmentSnapshot().has("job_gamble_p")) {
            lines.add("잭팟 폭발: " + jackpotCharges + "/"
                    + (int) augmentSnapshot().parameter("job_gamble_p", "maxCharges", 3));
        }
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
        double healthRatio = health() / Math.max(1.0, currentMaxHealth());
        int attempts = resolvePurchase(bet, source.getRandom());
        syncMaxHealth(effectBaseMaxHealth(), false);
        syncHealth(currentMaxHealth() * healthRatio);
        onStateChanged(lane);
        showBetResult(source, attempts + "회 시도 · " + state().lastResult(), lastPurchaseSucceeded);
    }

    int resolvePurchase(GambleBet bet, RandomSource random) {
        if (state().atScoreCap()) return 0;
        boolean allIn = AugmentCombat.allowsTriggers() && augmentSnapshot().has("job_gamble_p");
        int maximum = allIn ? (int) augmentSnapshot().parameter("job_gamble_p", "maxAttempts", 3) : 1;
        lastPurchaseSucceeded = false;
        for (int attempt = 1; attempt <= maximum; attempt++) {
            if (resolveAttempt(bet, random)) {
                lastPurchaseSucceeded = true;
                if (allIn) {
                    jackpotCharges = Math.min(jackpotCharges + 1,
                            (int) augmentSnapshot().parameter("job_gamble_p", "maxCharges", 3));
                }
                return attempt;
            }
        }
        if (allIn) {
            double loss = augmentSnapshot().parameter("job_gamble_p", "allFailedScoreLoss", 3);
            setData(STATE, state().adjustScore(-loss, state().lastResult() + " · 전부 실패, 점수 -" + oneDecimal(loss)));
        }
        return maximum;
    }

    int jackpotCharges() {
        return jackpotCharges;
    }

    private boolean resolveAttempt(GambleBet bet, RandomSource random) {
        int first = random.nextInt(6) + 1;
        int second = bet == GambleBet.TWO_DICE ? random.nextInt(6) + 1 : 0;
        double score = bet == GambleBet.TWO_DICE
                ? GambleRolls.twoDiceDelta(first, second)
                : GambleRolls.oddEvenDelta(bet, first);
        GambleState before = state();
        boolean bottomKing = AugmentCombat.allowsTriggers() && augmentSnapshot().has("job_gamble_g2");
        double settledScore = GambleRewards.settledScore(score, bottomKing
                ? augmentSnapshot().parameter("job_gamble_g2", "failureScoreMultiplier", 2.0) : 1.0);
        String roll = GambleRolls.formatResultRoll(bet, first, second);
        GambleState after;
        if (GambleRewards.awardsAbility(before, score, random.nextDouble())) {
            GambleAbility ability = GambleRewards.chooseMissing(
                    before, random.nextInt(GambleRewards.missingAbilities(before).size())
            );
            after = before.recordAbility(
                    ability, settledScore, bet.displayName() + " " + roll + " → " + ability.detailLine());
        } else {
            List<GambleStat> stats = bet == GambleBet.TWO_DICE
                    && GambleRolls.twoDiceStatRewardCount(first, second) == 2
                    ? GambleRewards.chooseDistinctStats(
                            random.nextInt(GambleRewards.rollableStatCount()),
                            random.nextInt(GambleRewards.rollableStatCount() - 1))
                    : List.of(GambleRewards.chooseStat(
                            random.nextInt(GambleRewards.rollableStatCount())));
            ArrayList<GambleState.StatChange> changes = new ArrayList<>(stats.size());
            ArrayList<String> results = new ArrayList<>(stats.size());
            double scorePerStat = score / stats.size();
            for (GambleStat stat : stats) {
                double delta = GambleRewards.settledStatDelta(
                        before, GambleBalance.statDelta(stat, scorePerStat), bottomKing);
                changes.add(new GambleState.StatChange(stat, delta, baseValue(stat)));
                results.add(stat.displayName() + " " + signed(delta));
            }
            String result = bet.displayName() + " " + roll + " → " + String.join(", ", results);
            after = before.recordStats(changes, settledScore, result);
        }
        setData(STATE, after);
        return score > 0.0;
    }

    private double baseValue(GambleStat stat) {
        return switch (stat) {
            case MAX_HEALTH -> type().maxHealth();
            case DAMAGE -> type().damage();
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
        TowerAreaDamage.applyResolved(this, source, request,
                ignored -> resolvedOutgoingDamage * ratio, true, (target, damage, killed) -> {});
    }

    private void showBetResult(SemionTowerEntity source, String result, boolean success) {
        if (source.level() instanceof net.minecraft.server.level.ServerLevel level) {
            level.sendParticles(success ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.WITCH,
                    source.getX(), source.getY() + 1.0, source.getZ(), 14, 0.35, 0.35, 0.35, 0.04);
        }
        if (source.getServer() != null) {
            var player = source.getServer().getPlayerList().getPlayer(ownerPlayer());
            if (player != null) {
                player.sendSystemMessage(SemionText.prefixedPlain("도박 결과: " + result));
            }
        }
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
                + "·사거리 " + signed(GambleBalance.statDelta(GambleStat.RANGE, score));
    }
}
