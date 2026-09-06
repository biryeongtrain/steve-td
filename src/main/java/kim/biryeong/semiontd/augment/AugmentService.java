package kim.biryeong.semiontd.augment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.AugmentTelemetrySnapshot;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.job.HeroPartyTowerJob;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.summon.SummonMonsterType;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.augment.AugmentTowerService;
import kim.biryeong.semiontd.tower.augment.AugmentTowers;
import kim.biryeong.semiontd.tower.TowerPlacementPositions;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.ui.SemionDialogService;
import kim.biryeong.semiontd.ui.SemionText;
import kim.biryeong.semiontd.ui.SemionTitleService;
import kim.biryeong.semiontd.ui.SemionLaneIndicatorService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Server-thread controller. Dialogs receive presentation data, never permission to apply effects. */
public final class AugmentService {
    /** Selection time added before the ordinary round preparation. */
    public static final int PREPARE_TICKS = 30 * 20;
    public static final int REVEAL_TICKS = 20;
    private static final String COMMAND = "/semiontd augment ";
    private final String sessionToken = UUID.randomUUID().toString();
    private final SemionDialogService dialogs = new SemionDialogService();
    private final Map<UUID, Long> revealedOffers = new HashMap<>();
    private final Set<UUID> lateRoundFive = new HashSet<>();
    private final Map<UUID, Integer> warnedSeconds = new HashMap<>();
    private final Map<GuiKey, GuiProgress> guiProgress = new HashMap<>();

    private record GuiKey(UUID playerId, int milestone) { }
    private record GuiContext(int milestone, long revision, Integer slot, String cardId, AugmentChoice choice) { }
    private static final class GuiProgress {
        long shownTick = -1;
        int inputs;
        int backs;
    }
    private static final class GuiRequestException extends IllegalArgumentException {
        private final String reason;

        GuiRequestException(String reason, String message) {
            super(message);
            this.reason = reason;
        }
    }

    public void onLateJoin(SemionPlayer player, int requestedRound) {
        if (requestedRound == 5) {
            lateRoundFive.add(player.uuid());
        }
    }

    public boolean onPrepare(SemionGame game, MinecraftServer server, long now) {
        revealedOffers.clear();
        warnedSeconds.clear();
        if (!game.augmentsEnabled()) {
            return false;
        }
        if (game.currentRound() == 1 && server != null) {
            String schedule = "증강 희귀도: R5 " + rarityLabel(game.augmentRarities().get(0)) + " · R15 "
                    + rarityLabel(game.augmentRarities().get(1)) + " · R25 " + rarityLabel(game.augmentRarities().get(2));
            for (SemionPlayer player : game.players().values()) {
                ServerPlayer online = server.getPlayerList().getPlayer(player.uuid());
                if (online != null) {
                    online.sendSystemMessage(SemionText.prefixedMini(schedule));
                }
            }
        }
        boolean opened = false;
        for (SemionPlayer player : game.players().values()) {
            if (!alive(game, player)) {
                continue;
            }
            int milestone = lateRoundFive.contains(player.uuid()) ? 5 : game.currentRound();
            if (!List.of(5, 15, 25).contains(milestone)
                    || player.augments().selections().stream().anyMatch(selection -> selection.milestoneRound() == milestone)
                    || !game.augmentConfig().publicPoolEnabled()) {
                continue;
            }
            var offer = player.augments().offer(milestone, game.currentRound(), now + PREPARE_TICKS,
                    definition -> isEligible(game, player, definition.id()));
            if (offer != null) {
                opened = true;
                lateRoundFive.remove(player.uuid());
                ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(player.uuid());
                if (online != null) {
                    SemionTitleService.showAugmentRarity(online, offer.rarity().markup(rarityName(offer.rarity()) + " 증강"));
                    online.sendSystemMessage(SemionText.prefixedMini("R" + milestone + " "
                            + rarityLabel(offer.rarity()) + " 증강 · 잠시 뒤 카드 세 장을 공개합니다."));
                    online.playNotifySound(switch (offer.rarity()) {
                        case SILVER -> SoundEvents.NOTE_BLOCK_PLING.value();
                        case GOLD -> SoundEvents.PLAYER_LEVELUP;
                        case PRISMATIC -> SoundEvents.AMETHYST_BLOCK_CHIME;
                    }, SoundSource.PLAYERS, 0.8F, 1.0F);
                }
            }
        }
        return opened;
    }

    public void tick(SemionGame game, MinecraftServer server, long now) {
        if (!game.augmentsEnabled() || game.phase() != RoundPhase.PREPARE_AND_SUMMON || server == null) {
            return;
        }
        for (SemionPlayer player : game.players().values()) {
            if (!alive(game, player)) {
                continue;
            }
            if (now % 20 == 0) {
                showPreparedMinePositions(game, player);
            }
            var offer = player.augments().currentOffer().orElse(null);
            if (offer == null) {
                continue;
            }
            if (now >= offer.deadlineTickExclusive()) {
                expireOffer(game, player, now);
                continue;
            }
            ServerPlayer online = server.getPlayerList().getPlayer(player.uuid());
            if (online == null || now < inputAllowedTick(offer.deadlineTickExclusive())) {
                continue;
            }
            if (!revealedOffers.containsKey(player.uuid())) {
                revealedOffers.put(player.uuid(), offer.revision());
                showOffer(game, online, player);
            }
            int remaining = (int) Math.max(0, (offer.deadlineTickExclusive() - now + 19) / 20);
            int warning = remaining <= 5 ? 5 : remaining <= 10 ? 10 : 0;
            if (warning > 0 && warnedSeconds.getOrDefault(player.uuid(), Integer.MAX_VALUE) > warning) {
                warnedSeconds.put(player.uuid(), warning);
                online.sendSystemMessage(SemionText.prefixedPlain("증강 선택 마감까지 " + remaining + "초입니다. /증강"));
                online.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.7F, 1.2F);
            }
        }
    }

    public void expire(SemionGame game) {
        for (SemionPlayer player : game.players().values()) {
            expireOffer(game, player, game.currentTick());
            player.augments().clearConfigurationDraft();
        }
    }

    public void reopen(SemionGame game, ServerPlayer online) {
        SemionPlayer player = game.players().get(online.getUUID());
        if (!game.augmentsEnabled() || !alive(game, player)) {
            return;
        }
        expireOffer(game, player, game.currentTick());
        recordGui(game, player, guiContext(player), "RESTORED", "RECONNECT", "SUCCESS", null);
        if (player.augments().currentOffer().isPresent()) {
            if (player.augments().currentOffer().orElseThrow().draft() == null) {
                showOffer(game, online, player);
            } else {
                showDraft(game, online, player, false, "");
            }
        } else if (game.phase() == RoundPhase.PREPARE_AND_SUMMON && player.augments().configurationDraft().isPresent()) {
            showDraft(game, online, player, true, "");
        }
    }

    static long inputAllowedTick(long deadlineTickExclusive) {
        return deadlineTickExclusive - PREPARE_TICKS + REVEAL_TICKS;
    }

    private static String rarityName(AugmentRarity rarity) {
        return switch (rarity) {
            case SILVER -> "실버";
            case GOLD -> "골드";
            case PRISMATIC -> "프리즘";
        };
    }

    private static String rarityLabel(AugmentRarity rarity) {
        return rarity.markup(rarityName(rarity));
    }

    private static String cardLabel(AugmentDefinition card) {
        return card.rarity().markup(card.displayName());
    }

    static String selectionCountLabel(PlayerAugmentState state) {
        return "선택한 증강 " + state.selections().stream()
                .filter(selection -> selection.outcome() == PlayerAugmentState.Outcome.SELECTED).count()
                + "/" + AugmentCatalog.MILESTONES.size();
    }

    public record Button(String label, String command, String description) { }
    public record CardLine(String category, String text) { }
    public record Screen(String title, String body, List<CardLine> cards, List<Button> buttons, int columns) {
        public Screen {
            cards = List.copyOf(cards);
            buttons = List.copyOf(buttons);
        }
    }

    static String shortId(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    public static int targetCount(String id) {
        return switch (shortId(id)) {
            case "tactical_designation_1", "tactical_designation_2", "tactical_designation_3",
                    "overheat_core", "battlefield_mastery", "one_man_show" -> 1;
            case "frontline_specialization" -> 2;
            default -> 0;
        };
    }

    public static List<String> modes(String id) {
        return switch (shortId(id)) {
            case "tactical_designation_1", "tactical_designation_2", "tactical_designation_3" -> List.of("ASSAULT", "COVER");
            case "engagement_plan" -> List.of("QUICK", "LONG");
            case "biased_armor" -> List.of("PHYSICAL", "MAGIC");
            default -> List.of();
        };
    }

    public static boolean configurable(String id) {
        return targetCount(id) > 0 && !List.of("battlefield_mastery", "one_man_show").contains(shortId(id))
                || !modes(id).isEmpty();
    }

    public static String modeName(String mode) {
        return switch (mode == null ? "" : mode) {
            case "ASSAULT" -> "돌격";
            case "COVER" -> "엄호";
            case "QUICK" -> "속전";
            case "LONG" -> "지구전";
            case "PHYSICAL" -> "물리 편향";
            case "MAGIC" -> "마법 편향";
            default -> "설정 없음";
        };
    }

    private static boolean alive(SemionGame game, SemionPlayer player) {
        return player != null && game.teams().containsKey(player.teamId())
                && !game.teams().get(player.teamId()).eliminated();
    }

    private static List<Tower> towers(SemionGame game, SemionPlayer player) {
        return game.playerLane(player.uuid()).map(PlayerLane::towers).orElse(List.of());
    }

    public boolean isEligible(SemionGame game, SemionPlayer player, String cardId) {
        if (!alive(game, player)) {
            return false;
        }
        String id = shortId(cardId);
        if (id.startsWith("reserve_")) {
            return AugmentCatalog.find(cardId).map(AugmentDefinition::reserve).orElse(false);
        }
        return switch (id) {
            case "tactical_designation_1", "tactical_designation_2", "tactical_designation_3",
                    "overheat_core", "battlefield_mastery", "one_man_show" -> !eligibleTargets(game, player, cardId).isEmpty();
            case "frontline_specialization" -> eligibleTargets(game, player, cardId).size() >= 2;
            case "finishing_fire_1", "finishing_fire_2", "finishing_fire_3", "winning_barrage", "domino_fire" ->
                    towers(game, player).stream().anyMatch(AugmentCombat::isNormalAttacker);
            case "independent_position" -> game.playerLane(player.uuid()).map(AugmentCombat::independentEligibleCount).orElse(0) > 0;
            case "twin_squadron" -> canUseTwins(game, player);
            case "additional_payload", "support_performance" -> hasAffordableSummon(game, player, "UTILITY");
            case "forecast_offensive" -> hasAffordableSummon(game, player, "ATTACK");
            case "cash_settlement" -> hasAffordableSummon(game, player, "INCOME");
            case "low_pressure_high_yield", "decisive_delivery" -> hasAffordableSummon(game, player, "STANDARD");
            case "forbidden_blueprint" -> AugmentEconomyService.eligibleForCard(game, player, cardId);
            case "wartime_economy" -> player.economy().income() > 0;
            case "folding_barricade_blueprint", "pulse_relay_blueprint", "barrier_core_call", "giant_hunter_call",
                    "emergency_bell_blueprint", "capacitor_post_blueprint", "ambush_workshop_blueprint", "starlight_cocoon_call" ->
                    AugmentTowerService.canSelect(game, player, cardId);
            case "ordnance_factory_call" -> AugmentTowerService.canSelect(game, player, cardId)
                    && hasAffordableSummon(game, player, "STANDARD");
            case "triangle_formation", "engagement_plan", "emergency_loan", "biased_armor" -> true;
            default -> false;
        };
    }

    public List<Tower> eligibleTargets(SemionGame game, SemionPlayer player, String cardId) {
        String id = shortId(cardId);
        if (targetCount(cardId) == 0) {
            return List.of();
        }
        AugmentSnapshot snapshot = player.augments().snapshot();
        AugmentChoice roles = snapshot.choice("frontline_specialization");
        Set<UUID> designated = new HashSet<>();
        for (int tier = 1; tier <= 3; tier++) {
            UUID target = snapshot.choice("tactical_designation_" + tier).primaryTargetId();
            if (target != null) {
                designated.add(target);
            }
        }
        return towers(game, player).stream().filter(tower -> player.uuid().equals(tower.ownerPlayer()))
                .filter(tower -> switch (id) {
                    case "overheat_core" -> AugmentCombat.isOverheatEligible(tower);
                    case "battlefield_mastery" -> AugmentCombat.isMasteryEligible(tower);
                    case "one_man_show" -> AugmentCombat.isNormalAttacker(tower);
                    case "frontline_specialization" -> AugmentCombat.isNormalAttacker(tower) && !designated.contains(tower.logicalId());
                    default -> AugmentCombat.isNormalPermanent(tower)
                            && !tower.logicalId().equals(roles.primaryTargetId()) && !tower.logicalId().equals(roles.secondaryTargetId());
                }).sorted(towerOrder()).toList();
    }

    public static boolean hasAffordableSummon(SemionGame game, SemionPlayer player, String kind) {
        if (game.summonsAreFree()) {
            return false;
        }
        JobContext context = new JobContext(game, player);
        var job = player.job().orElse(JobRegistry.defaultJob());
        return game.summonShop().all().stream().filter(type -> job.canUseSummon(context, type))
                .filter(type -> {
                    long cost = Math.max(0, job.modifySummonGasCost(context, type, type.gasCost()));
                    long income = Math.max(0, job.modifySummonIncomeGain(context, type, type.incomeGain()));
                    return cost > 0 && player.economy().emerald() >= cost && switch (kind) {
                        case "UTILITY" -> AugmentEconomyService.isUtility(type);
                        case "ATTACK" -> AugmentEconomyService.isAttackEligible(type);
                        case "STANDARD" -> income > 0 && AugmentEconomyService.isStandardAttack(type);
                        case "INCOME" -> income > 0;
                        default -> false;
                    };
                }).findAny().isPresent();
    }

    private static boolean canUseTwins(SemionGame game, SemionPlayer player) {
        var job = player.job().orElse(JobRegistry.defaultJob());
        if (job instanceof HeroPartyTowerJob) {
            return false; // Each companion role is limited to one tower, including after upgrades.
        }
        return ProductionTowerCatalog.all().stream().filter(entry -> job.includesTowerInCatalog(entry.type()))
                .filter(entry -> entry.type().damage() > 0 && entry.type().range() > 0)
                .map(entry -> entry.create(player.uuid(), player.teamId(), player.laneId(), new kim.biryeong.semiontd.game.GridPosition(0, 0, 0)))
                .anyMatch(AugmentCombat::isNormalPermanentType);
    }

    private void showOffer(SemionGame game, ServerPlayer online, SemionPlayer player) {
        var offer = player.augments().currentOffer().orElse(null);
        if (offer == null) {
            showHistory(game, online, player);
            return;
        }
        if (game.currentTick() < inputAllowedTick(offer.deadlineTickExclusive())) {
            error(online, "카드를 공개하고 있습니다. 잠시 뒤 다시 열어 주세요.");
            return;
        }
        List<CardLine> cards = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();
        for (int slot = 0; slot < offer.cardIds().size(); slot++) {
            AugmentDefinition card = AugmentCatalog.find(offer.cardIds().get(slot)).orElseThrow();
            cards.add(new CardLine(card.category().name(), card.rarity().markup("[" + (slot + 1) + "] " + card.displayName())
                    + " [" + rarityLabel(card.rarity()) + " · " + categoryName(card.category()) + "]\n"
                    + offerSummary(card, game.augmentConfig())));
            buttons.add(new Button((slot + 1) + "번 카드 검토",
                    COMMAND + "draft " + offer.revision() + " " + slot + " " + requestId(),
                    card.displayName() + "의 전체 효과와 현재 조건을 확인합니다. 아직 선택하지 않습니다."));
        }
        buttons.add(button("리롤", "ui reroll"));
        buttons.add(button("선택 기록", "ui history"));
        buttons.add(button("도움말", "ui help"));
        buttons.add(button("건너뛰기", "ui skip"));
        render(online, new Screen("R" + offer.milestoneRound() + " 증강 선택 · " + rarityLabel(offer.rarity()),
                selectionCountLabel(player.augments()) + "\n선택 마감까지 " + Math.max(0, (offer.deadlineTickExclusive() - game.currentTick() + 19) / 20)
                        + "초 · 에메랄드 자동 생산 중단\n이후 일반 준비 25초 · 리롤 1회 · 닫은 뒤 /증강",
                cards, buttons, 3));
        recordShown(game, player, offer);
    }

    private static String categoryName(AugmentCategory category) {
        return switch (category) {
            case GENERAL -> "범용";
            case TRADE_OFF -> "대가형";
            case TOWER -> "전용 타워";
            case GAME_CHANGER -> "게임 체인저";
            case INCOME -> "인컴";
        };
    }

    /** The offer shows the decision; the draft retains all configured conditions and live costs. */
    public static String offerSummary(AugmentDefinition card, AugmentConfig config) {
        String id = shortId(card.id());
        Map<String, Double> values = config.parameters().get(card.id());
        java.util.function.Function<String, String> n = key -> number(values.get(key));
        java.util.function.Function<String, String> p = key -> number(values.get(key) * 100) + "%";
        if (id.startsWith("reserve_diamonds_")) return "즉시 다이아 +" + n.apply("amount") + ". 후보 부족 시 예비 보상.";
        if (id.startsWith("reserve_income_")) return "정기 인컴 +" + n.apply("amount") + ". 기존 지급 감소·부채 적용.";
        if (id.startsWith("reserve_production_")) return "기본 에메랄드 생산 +" + n.apply("amount") + "/초. 업그레이드 상한과 별개.";
        String summary = switch (id) {
            case "tactical_designation_1", "tactical_designation_2", "tactical_designation_3" ->
                    "지정 1기: 피해 +" + p.apply("damageBonus") + " 또는 받는 피해 -" + p.apply("damageReduction");
            case "triangle_formation" -> "이웃 " + n.apply("neighborCount") + "기 이상: 피해 +" + p.apply("damageBonus") + ", 받는 피해 -" + p.apply("damageReduction");
            case "engagement_plan" -> "초반 피해 +" + p.apply("quickDamageBonus") + " / 후반 피해 +" + p.apply("longDamageBonus") + "·받는 피해 -" + p.apply("longDamageReduction");
            case "emergency_loan" -> "최대 " + n.apply("advanceCap") + "다이아 대출. " + n.apply("repaymentCount") + "회에 원금×"
                    + (values.get("debtMultiplier") == 4.0 / 3.0 ? "4/3" : n.apply("debtMultiplier")) + " 상환.";
            case "additional_payload" -> "유틸 비용 ×" + n.apply("costMultiplier") + ", 체력 ×" + n.apply("healthMultiplier") + "·회복/보호막 ×" + n.apply("supportMultiplier");
            case "twin_squadron" -> "같은 종류·티어가 정확히 2기: 피해 +" + p.apply("damageBonus");
            case "overheat_core" -> "지정 1기 피해 +" + p.apply("damageBonus") + ". 사용마다 영구 피해 -" + p.apply("penaltyPerStack") + " 누적.";
            case "frontline_specialization" -> "선봉 받는 피해 -" + p.apply("vanguardDamageReduction") + "·피해 -" + p.apply("vanguardDamagePenalty")
                    + " / 포대 피해 +" + p.apply("artilleryDamageBonus") + "·받는 피해 ×" + n.apply("artilleryIncomingMultiplier");
            case "forecast_offensive" -> "공격 인컴을 1웨이브 늦춰 +" + p.apply("echoRatio") + " 메아리. 비용은 지금, 인컴 증가는 출현 때.";
            case "support_performance" -> "유틸 1기가 " + n.apply("targetCount") + "기 지원: 인컴 +" + n.apply("incomeBonus") + ". 경기 최대 +" + n.apply("matchIncomeCap");
            case "battlefield_mastery" -> "지정 1기 피격·생존마다 피해/체력 +" + p.apply("bonusPerStack") + ". 대상 변경 불가·제거 시 초기화.";
            case "biased_armor" -> "선택 유형 받는 피해 ×" + n.apply("selectedMultiplier") + ", 반대 유형 ×" + n.apply("oppositeMultiplier");
            case "cash_settlement" -> "다음 인컴 증가를 포기하고 그 " + n.apply("diamondMultiplier") + "배를 즉시 다이아로 받음.";
            case "forbidden_blueprint" -> n.apply("ticketValue") + "다이아 승급권 " + n.apply("ticketCount") + "장. 사용마다 영구 정기 지급 ×" + n.apply("payoutMultiplier");
            case "low_pressure_high_yield" -> "계약 인컴 몸체 ×" + n.apply("bodyMultiplier") + ", 인컴 증가 +" + p.apply("bonusRatio") + ". 라운드 추가 상한 " + n.apply("roundBonusCap");
            case "finishing_fire_1", "finishing_fire_2", "finishing_fire_3" -> "체력 절반 이하 적: 주 대상 기본 공격 피해 +" + p.apply("damageBonus");
            case "independent_position" -> "고립된 타워: 피해 +" + p.apply("damageBonus") + ", 받는 피해 -" + p.apply("damageReduction");
            case "winning_barrage" -> "기본 공격 처치 후 " + n.apply("charges") + "회 피해 +" + p.apply("damageBonus") + ". 재처치하면 충전 갱신.";
            case "decisive_delivery" -> "다음 인컴 증가 포기: 표준 공격 인컴 체력 ×" + n.apply("healthMultiplier") + "·공격 ×" + n.apply("attackMultiplier");
            case "domino_fire" -> "기본 공격 처치의 초과 피해 " + p.apply("overkillRatio") + "를 인접 적 1기에게 전달.";
            case "one_man_show" -> "주역 피해 +" + p.apply("damageBonus") + "·체력 +" + p.apply("maxHealthBonus") + ", 나머지 피해 -" + p.apply("otherDamagePenalty") + ". 주역 변경 불가.";
            case "wartime_economy" -> "피해 +" + p.apply("damageBonus") + "·체력 +" + p.apply("maxHealthBonus") + ". 정기 지급은 영구 ×" + n.apply("payoutMultiplier");
            case "folding_barricade_blueprint" -> "먼저 공격받는 방벽. 직접 공격 불가.";
            case "pulse_relay_blueprint" -> "연결한 두 타워가 서로 다음 공격 +" + p.apply("chargedDamageRatio") + " 충전.";
            case "barrier_core_call" -> "연결 3기의 피해 " + p.apply("redirectRatio") + " 대신 받음. 전투 회복 불가.";
            case "giant_hunter_call" -> "적 최대 체력의 " + p.apply("maxHealthDamageRatio") + " 추가 피해. " + n.apply("minimumRange") + "블록 안 공격 불가.";
            case "emergency_bell_blueprint" -> "위급한 아군 " + n.apply("maxHeals") + "기를 응급 회복. 직접 공격 불가.";
            case "capacitor_post_blueprint" -> "범위 내 적이 없으면 최대 " + n.apply("maxCharges") + "충전. 다음 공격에 충전당 +" + n.apply("chargeDamage") + "피해.";
            case "ambush_workshop_blueprint" -> "지상 지뢰 3개·각 " + n.apply("mineDamage") + "피해. 적당 1회, 직접 공격 불가.";
            case "starlight_cocoon_call" -> n.apply("hatchWaves") + "웨이브 생존 후 파수꾼 부화. 부화 전 공격 불가.";
            case "ordnance_factory_call" -> "공격 인컴 " + n.apply("emeraldPerShell") + "에메랄드마다 포탄. 웨이브 최대 " + n.apply("maxShells") + "발.";
            default -> throw new IllegalArgumentException("Missing compact augment description: " + card.id());
        };
        if (card.towerAugment()) {
            TowerType tower = AugmentTowers.all().stream().filter(type -> card.id().equals(AugmentTowers.augmentId(type)))
                    .map(kim.biryeong.semiontd.config.TowerBalanceRuntime::resolve).findFirst().orElseThrow();
            return (AugmentTowers.isFreeCall(tower) ? "무료" : tower.mineralCost() + "다이아") + "·" + AugmentTowers.slots(tower) + "칸: " + summary;
        }
        return summary;
    }

    private String preview(SemionGame game, SemionPlayer player, AugmentDefinition card, AugmentChoice choice) {
        StringBuilder body = new StringBuilder(AugmentDescriptions.describe(card, game.augmentConfig()));
        List<Tower> targets = eligibleTargets(game, player, card.id());
        if (targetCount(card.id()) > 0) {
            body.append("\n선택 가능 ").append(targets.size()).append("기");
        }
        for (UUID targetId : java.util.Arrays.asList(choice.primaryTargetId(), choice.secondaryTargetId())) {
            if (targetId == null) {
                continue;
            }
            Tower tower = towers(game, player).stream().filter(candidate -> candidate.logicalId().equals(targetId)).findFirst().orElse(null);
            body.append("\n대상: ").append(tower == null ? "제거됨 · 새 대상을 고르세요." : towerLabel(tower));
            if (tower != null) {
                body.append(" · 현재 체력 ").append(number(tower.health())).append("/").append(number(tower.currentMaxHealth()));
            }
        }
        if (!choice.mode().isEmpty()) {
            body.append("\n모드: ").append(modeName(choice.mode()));
        }
        long income = player.economy().income();
        switch (shortId(card.id())) {
            case "emergency_loan" -> {
                long advance = Math.min((long) parameter(game, card.id(), "advanceCap", 300),
                        (long) Math.floor(income * parameter(game, card.id(), "advanceMultiplier", 3)));
                long debt = (long) Math.ceil(advance * parameter(game, card.id(), "debtMultiplier", 4.0 / 3.0));
                long times = Math.max(1, (long) parameter(game, card.id(), "repaymentCount", 4));
                body.append("\n즉시 +").append(advance).append("다이아 / 총부채 ").append(debt)
                        .append(" / 다음 차감 ").append((debt + times - 1) / times).append("\n지급액이 부족하면 상환이 연장됩니다.");
            }
            case "forbidden_blueprint" -> body.append("\n이번 준비 종료 시 이용권 소멸. 현재 보유 ")
                    .append(player.economy().diamond()).append("다이아\n이용권당 최대 ")
                    .append(number(parameter(game, card.id(), "ticketValue", 300))).append("다이아 지원 · 사용마다 정기 지급 ×")
                    .append(number(parameter(game, card.id(), "payoutMultiplier", 0.9)));
            case "wartime_economy" -> body.append("\n현재 정기 수입 기준 ").append(income).append(" → ")
                    .append((long) Math.floor(income * parameter(game, card.id(), "payoutMultiplier", 0.65)))
                    .append("다이아. 경기 종료까지 되돌릴 수 없습니다.");
            case "overheat_core" -> {
                Tower target = targets.stream().filter(tower -> tower.logicalId().equals(choice.primaryTargetId())).findFirst().orElse(null);
                if (target != null) {
                    int maxStacks = (int) parameter(game, card.id(), "maxStacks", 5);
                    body.append("\n현재 열화 ").append(AugmentCombat.heatStacks(target)).append('/').append(maxStacks)
                            .append(" → 이번 사용 뒤 ").append(AugmentCombat.heatStacks(target) + 1).append('/').append(maxStacks)
                            .append("\n미지정 시 이번 웨이브 보너스와 추가 열화 없음.");
                }
            }
            case "one_man_show" -> body.append("\n주역은 바꿀 수 없습니다. 승급과 기존 스택은 유지합니다.\n주역을 제거해도 다른 일반 타워의 피해 감소는 남습니다.");
            case "battlefield_mastery" -> body.append("\n대상 변경 불가. 판매·제거하면 지정과 숙련을 모두 잃습니다.");
            case "triangle_formation" -> body.append("\n현재 배치 적용 예상 ")
                    .append(game.playerLane(player.uuid()).map(AugmentCombat::triangleEligibleCount).orElse(0)).append("기");
            case "independent_position" -> body.append("\n현재 독립 배치 ")
                    .append(game.playerLane(player.uuid()).map(AugmentCombat::independentEligibleCount).orElse(0)).append("기");
            case "biased_armor" -> body.append('\n').append(directDpsPreview(game, player));
            default -> { }
        }
        if (!player.augments().hasSelected(card.id()) && !isEligible(game, player, card.id())) {
            body.append("\n현재 조건을 충족하지 못합니다. 대상·슬롯·자원을 확인하세요.");
        }
        return MiniMessage.miniMessage().escapeTags(body.toString());
    }

    private static double parameter(SemionGame game, String card, String key, double fallback) {
        return game.augmentConfig().parameter(card, key, fallback);
    }

    private static String directDpsPreview(SemionGame game, SemionPlayer player) {
        if (game.phase() != RoundPhase.PREPARE_AND_SUMMON) {
            return "직접 DPS 비중은 준비 단계에 표시합니다.";
        }
        double physical = game.upcomingWaveEntries(player.uuid()).stream()
                .mapToDouble(entry -> entry.count() * entry.attackDamage() * 20.0 / entry.attackIntervalTicks()).sum();
        var lane = game.playerLane(player.uuid()).orElse(null);
        if (lane != null) {
            physical += lane.queuedSummonDirectDps(DamageType.PHYSICAL);
        }
        double magic = lane == null ? 0 : lane.queuedSummonDirectDps(DamageType.MAGIC);
        double total = physical + magic;
        if (total <= 0) {
            return "다음 웨이브: 현재 확인 가능한 물리·마법 기본 공격 없음.";
        }
        return "다음 웨이브 직접 DPS: 물리 " + number(physical / total * 100) + "% / 마법 " + number(magic / total * 100)
                + "%\n현재 자연 웨이브·구매된 이번 웨이브 인컴의 기본 공격 기준. 고정 피해·능력·방어력·공격 시간은 제외합니다.";
    }

    private static String number(double value) {
        return value == (long) value ? Long.toString((long) value) : String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String remainingAtOpen(SemionGame game, long deadline) {
        return "화면을 연 시점: 선택 마감까지 " + Math.max(0, (deadline - game.currentTick() + 19) / 20)
                + "초 · 에메랄드 자동 생산 중단\n선택 시간 후 일반 준비 25초";
    }

    private static String towerLabel(Tower tower) {
        return tower.type().displayName() + " T" + ProductionTowerCatalog.entry(tower.type()).map(ProductionTowerCatalog.CatalogEntry::tier).orElse(0)
                + " · X " + tower.originalPosition().x() + "/Z " + tower.originalPosition().z();
    }

    public int handle(SemionGame game, ServerPlayer online, String input, boolean administrator) {
        SemionPlayer player = game.players().get(online.getUUID());
        if (!game.augmentsEnabled() || !alive(game, player)) {
            error(online, "증강은 시즌 3 NORMAL의 생존 참가자만 사용할 수 있습니다.");
            return 0;
        }
        String[] args = input.trim().split("\\s+");
        GuiContext requestContext = guiContext(player);
        String route = "UNKNOWN";
        try {
            if (args[0].equals("session")) {
                if (args.length < 3 || !sessionToken.equals(args[1])) {
                    recordGui(game, player, requestContext, "REJECTED", "SESSION", "REJECTED", "SESSION_MISMATCH");
                    error(online, "이전 경기의 증강 화면입니다. /증강으로 현재 경기를 다시 여세요.");
                    return 0;
                }
                args = java.util.Arrays.copyOfRange(args, 2, args.length);
            } else if (!args[0].equals("ui") && !args[0].equals("view") && !(administrator && args[0].equals("force"))) {
                recordGui(game, player, requestContext, "REJECTED", "SESSION", "REJECTED", "SESSION_REQUIRED");
                error(online, "현재 경기의 증강 버튼으로 입력해 주세요. /증강");
                return 0;
            }
            route = args[0];
            if (route.startsWith("configure")) {
                requestContext = configurationContext(player);
            }
            if (args[0].equals("ui")) {
                requireArity(args, 1, 2, 3);
                if (args.length == 3 && !args[2].equals("back")) {
                    throw new GuiRequestException("MALFORMED_INPUT", "알 수 없는 증강 화면 이동입니다.");
                }
                if (requestContext != null && args.length == 3) {
                    progress(player, requestContext).backs++;
                }
                recordGui(game, player, requestContext, "REOPENED", args.length == 1 ? "ui current" : "ui " + args[1], "SUCCESS", null);
                openView(game, online, player, args.length == 1 ? "current" : args[1]);
                return 1;
            }
            if (args[0].equals("view")) {
                requireArity(args, 3);
                if (!List.of("offer", "configuration").contains(args[1])
                        || !List.of("target", "secondary", "mode", "confirm").contains(args[2])) {
                    throw new IllegalArgumentException("알 수 없는 증강 화면입니다.");
                }
                requestContext = args[1].equals("configuration") ? configurationContext(player) : guiContext(player);
                if (requestContext != null) {
                    progress(player, requestContext).backs++;
                }
                recordGui(game, player, requestContext, "REOPENED", "view " + args[1] + " " + args[2], "SUCCESS", null);
                showDraft(game, online, player, args[1].equals("configuration"), args[2]);
                return 1;
            }
            boolean cardInput = List.of("draft", "target", "mode", "confirm", "reroll", "skip", "configure",
                    "configure-target", "configure-mode", "configure-confirm").contains(route);
            if (cardInput && requestContext != null) {
                progress(player, requestContext).inputs++;
            }
            if (game.phase() != RoundPhase.PREPARE_AND_SUMMON) {
                recordGui(game, player, requestContext, "REJECTED", route, "REJECTED", "WRONG_PHASE");
                error(online, "준비 단계가 끝났습니다. /증강에서 선택 기록을 확인하세요.");
                return 0;
            }
            if (args[0].equals("buy")) {
                requireArity(args, 5);
                if (Long.parseLong(args[1]) != player.economyAugments().revision()) {
                    error(online, "구매 조건이 갱신되었습니다. 현재 금액으로 다시 확인하세요.");
                    reopenPurchase(game, online, args[2]);
                    return 0;
                }
                var type = game.summonShop().find(args[2]).orElseThrow(() -> new IllegalArgumentException("알 수 없는 인컴입니다."));
                AugmentEconomyService.Contract override = purchaseOverride(args[3]);
                var plan = AugmentEconomyService.previewPurchase(game, player, type, override).orElse(null);
                if (plan == null || plan.emeraldCost() != Long.parseLong(args[4])) {
                    error(online, "구매 가격 또는 계약이 달라졌습니다. 다시 확인하세요.");
                    reopenPurchase(game, online, args[2]);
                    return 0;
                }
                var result = game.summonMonster(player.uuid(), type.id(), override);
                if (result.type() != kim.biryeong.semiontd.summon.SummonResultType.SUCCESS) {
                    error(online, result.type() == kim.biryeong.semiontd.summon.SummonResultType.AUGMENT_CONTRACT_UNAVAILABLE
                            ? "선택한 계약을 현재 구매에 적용할 수 없습니다. 계약과 유닛을 다시 확인하세요."
                            : "소환하지 못했습니다. 보유 에메랄드, 구매 권한과 상대 레인을 확인하세요. 계약은 유지됩니다.");
                    reopenPurchase(game, online, args[2]);
                    return 0;
                }
                online.sendSystemMessage(SemionText.prefixedPlain(type.displayName() + " 구매 완료 · " + purchaseSummary(plan)));
                dialogs.showSummonShop(online, game);
                return 1;
            }
            if (args[0].equals("upgrade") || args[0].equals("upgrade-confirm")) {
                requireArity(args, args[0].equals("upgrade") ? 3 : 5);
                int offset = args[0].equals("upgrade") ? 0 : 1;
                UUID targetId = UUID.fromString(args[1 + offset]);
                Tower tower = towers(game, player).stream().filter(candidate -> candidate.logicalId().equals(targetId))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("승급 대상이 제거되었습니다. 타워를 다시 고르세요."));
                var upgrade = ProductionTowerCatalog.upgrade(tower.type(), args[2 + offset])
                        .orElseThrow(() -> new IllegalArgumentException("현재 타워에 없는 승급입니다."));
                var quote = AugmentEconomyService.quoteUpgrade(player, UUID.randomUUID(), game.currentRound(), tower.logicalId(),
                        ProductionTowerService.eligibleTicketUpgrade(game, player, tower, upgrade), upgrade.mineralCost(), true).orElse(null);
                if (quote == null) {
                    error(online, "이 타워에는 승급권을 쓸 수 없습니다. 남은 권리, 사용한 대상과 자원을 확인하세요.");
                    return 0;
                }
                if (args[0].equals("upgrade-confirm")) {
                    if (Long.parseLong(args[1]) != quote.revision() || Long.parseLong(args[4]) != quote.cost()) {
                        error(online, "승급 비용이나 이용권이 갱신되었습니다. 새 금액을 확인하세요.");
                        showUpgradeQuote(game, online, player, tower, upgrade.id(), quote);
                        return 0;
                    }
                    var result = ProductionTowerService.upgradeTower(game, player.uuid(), tower.managementPosition(), upgrade.id(), true);
                    if (result != kim.biryeong.semiontd.game.TowerUpgradeResult.SUCCESS) {
                        error(online, "승급하지 못했습니다. 타워와 자원을 확인하세요. 이용권은 소비되지 않았습니다.");
                        return 0;
                    }
                    online.sendSystemMessage(SemionText.prefixedPlain("승급 완료 · " + quote.cost() + "다이아 결제 · 이용권 1장 사용"));
                    dialogs.showTowerControl(online, game, null);
                    return 1;
                }
                showUpgradeQuote(game, online, player, tower, upgrade.id(), quote);
                return 1;
            }
            if (args[0].equals("force")) {
                requireArity(args, 5);
                if (!administrator) {
                    error(online, "관리자만 시험 제안을 만들 수 있습니다.");
                    return 0;
                }
                int milestone = Integer.parseInt(args[1]);
                List<String> cards = List.of(args[2], args[3], args[4]).stream().map(AugmentCatalog::normalizeId).toList();
                if (cards.stream().anyMatch(card -> !isEligible(game, player, card))) {
                    error(online, "현재 보드에서 사용할 수 없는 카드가 있습니다. 시험 보드를 먼저 준비하세요.");
                    return 0;
                }
                long deadline = game.currentTick() + PREPARE_TICKS;
                if (player.augments().currentOffer().isPresent()
                        || player.augments().selections().stream().anyMatch(selection -> selection.milestoneRound() == milestone)) {
                    error(online, "이미 제안하거나 완료한 마일스톤을 덮어쓸 수 없습니다.");
                    return 0;
                }
                player.augments().forceOffer(milestone, game.currentRound(), deadline, cards);
                game.extendAugmentPreparation(deadline);
                revealedOffers.remove(player.uuid());
                warnedSeconds.remove(player.uuid());
                online.sendSystemMessage(SemionText.prefixedPlain("시험 제안을 저장했습니다. 잠시 뒤 공개합니다."));
                return 1;
            }
            if (args[0].equals("contract") || args[0].equals("payload")) {
                requireArity(args, 3);
                if (args[0].equals("contract") && args[2].equals("LOW_PRESSURE")) {
                    throw new IllegalArgumentException("저압 계약은 유닛의 구매 확인 화면에서 선택하세요.");
                }
                long expected = Long.parseLong(args[1]);
                if (expected != player.economyAugments().revision()) {
                    error(online, "인컴 계약이 갱신되었습니다. 현재 설정을 확인하세요.");
                    showContracts(game, online, player);
                    return 0;
                }
                boolean changed = args[0].equals("payload")
                        ? AugmentEconomyService.setAdditionalPayload(player, game.currentRound(), parseToggle(args[2]))
                        : AugmentEconomyService.setContract(player, game.currentRound(), AugmentEconomyService.Contract.valueOf(args[2]));
                if (!changed) {
                    error(online, "이번 준비 단계에 사용할 수 없는 계약입니다. 보유 카드와 사용 횟수를 확인하세요.");
                }
                showContracts(game, online, player);
                return changed ? 1 : 0;
            }
            PlayerAugmentState state = player.augments();
            var offer = state.currentOffer().orElse(null);
            boolean configuration = args[0].startsWith("configure");
            if (!configuration && (offer == null || game.currentTick() < inputAllowedTick(offer.deadlineTickExclusive())
                    || game.currentTick() >= offer.deadlineTickExclusive())) {
                recordGui(game, player, requestContext, "REJECTED", route, "REJECTED", offer == null ? "NO_OPEN_OFFER"
                        : game.currentTick() >= offer.deadlineTickExclusive() ? "EXPIRED" : "REVEAL_LOCKED");
                    error(online, "현재 변경할 수 있는 제안이 없습니다. 공개 시간과 선택 마감을 확인하세요.");
                if (offer == null) {
                    showHistory(game, online, player);
                }
                return 0;
            }
            var eligible = (java.util.function.Predicate<AugmentDefinition>) card -> isEligible(game, player, card.id());
            PlayerAugmentState.ActionResult result;
            switch (args[0]) {
                case "draft" -> {
                    requireArity(args, 4);
                    int slot = Integer.parseInt(args[2]);
                    if (slot < 0 || slot >= offer.cardIds().size()) {
                        throw new IllegalArgumentException("카드 칸은 0~2만 가능합니다.");
                    }
                    long requestedRevision = Long.parseLong(args[1]);
                    requestContext = new GuiContext(offer.milestoneRound(), requestedRevision, slot,
                            requestedRevision == offer.revision() ? offer.cardIds().get(slot) : null, AugmentChoice.none());
                    recordGui(game, player, requestContext, "CARD_INPUT", route, "REQUESTED", null);
                    AugmentChoice choice = offer.draft() != null && offer.draft().cardId().equals(offer.cardIds().get(slot))
                            ? offer.draft().choice() : AugmentChoice.none();
                    result = state.draft(offer.milestoneRound(), slot, Long.parseLong(args[1]), offer.draftRevision(),
                            choice, checkedRequest(args[3]), game.currentTick(), eligible);
                }
                case "reroll" -> {
                    requireArity(args, 4);
                    result = state.reroll(offer.milestoneRound(), Integer.parseInt(args[2]), Long.parseLong(args[1]),
                            checkedRequest(args[3]), game.currentTick(), eligible);
                }
                case "target", "mode" -> {
                    requireArity(args, args[0].equals("target") ? new int[]{4, 5} : new int[]{4});
                    if (offer.draft() == null) {
                        throw new IllegalArgumentException("먼저 검토할 카드를 고르세요.");
                    }
                    AugmentDefinition card = AugmentCatalog.find(offer.draft().cardId()).orElseThrow();
                    AugmentChoice choice = editedChoice(game, player, card, offer.draft().choice(), args[0], args[3],
                            args.length == 5 ? Integer.parseInt(args[4]) : 0);
                    result = state.draft(offer.milestoneRound(), offer.cardIds().indexOf(card.id()), Long.parseLong(args[1]),
                            Long.parseLong(args[2]), choice, UUID.randomUUID(), game.currentTick(), eligible);
                }
                case "confirm" -> {
                    requireArity(args, 4);
                    result = state.confirm(offer.milestoneRound(), Long.parseLong(args[1]), Long.parseLong(args[2]),
                            checkedRequest(args[3]), game.currentTick(), eligible,
                            (card, choice) -> commitSelection(game, player, card, choice));
                }
                case "skip" -> {
                    requireArity(args, 3);
                    result = state.skip(offer.milestoneRound(), Long.parseLong(args[1]), checkedRequest(args[2]),
                            game.currentTick(), PlayerAugmentState.SkipReason.EXPLICIT);
                }
                case "configure" -> {
                    requireArity(args, 3);
                    var selection = selectionAt(player, Integer.parseInt(args[1]));
                    if (!configurable(selection.augmentId())) {
                        throw new IllegalArgumentException("이 카드는 대상을 다시 지정할 수 없습니다.");
                    }
                    AugmentChoice choice = state.configurationDraft()
                            .filter(draft -> draft.selectedMilestone() == selection.milestoneRound())
                            .map(PlayerAugmentState.ConfigurationDraft::choice).orElse(selection.choice());
                    result = state.draftConfiguration(selection.milestoneRound(), game.currentRound(), state.configurationRevision(),
                            choice, checkedRequest(args[2]), eligible);
                }
                case "configure-target", "configure-mode" -> {
                    requireArity(args, args[0].equals("configure-target") ? new int[]{3, 4} : new int[]{3});
                    var draft = state.configurationDraft().orElseThrow(() -> new IllegalArgumentException("먼저 설정할 카드를 고르세요."));
                    var selected = state.selections().stream().filter(selection -> selection.milestoneRound() == draft.selectedMilestone()).findFirst().orElseThrow();
                    AugmentDefinition card = AugmentCatalog.find(selected.augmentId()).orElseThrow();
                    AugmentChoice choice = editedChoice(game, player, card, draft.choice(), args[0].substring("configure-".length()), args[2],
                            args.length == 4 ? Integer.parseInt(args[3]) : 0);
                    result = state.draftConfiguration(draft.selectedMilestone(), game.currentRound(), Long.parseLong(args[1]),
                            choice, UUID.randomUUID(), eligible);
                }
                case "configure-confirm" -> {
                    requireArity(args, 3);
                    result = state.confirmConfiguration(game.currentRound(), Long.parseLong(args[1]), checkedRequest(args[2]), eligible,
                            (card, choice) -> validChoice(game, player, card, choice));
                }
                default -> throw new IllegalArgumentException("알 수 없는 증강 입력입니다. /증강으로 다시 여세요.");
            }
            GuiContext resultingContext = configuration ? configurationContext(player) : guiContext(player);
            if (resultingContext == null) {
                resultingContext = requestContext;
            } else if (requestContext == null && cardInput) {
                progress(player, resultingContext).inputs++;
            }
            if (args[0].equals("reroll") && result.successful()) {
                var current = state.currentOffer().orElseThrow();
                int slot = Integer.parseInt(args[2]);
                resultingContext = new GuiContext(current.milestoneRound(), current.revision(), slot,
                        current.cardIds().get(slot), AugmentChoice.none());
            }
            if (!result.successful()) {
                recordGui(game, player, requestContext == null ? resultingContext : requestContext,
                        "REJECTED", route, result.status().name(), result.status().name());
                error(online, "선택 상태 또는 조건이 달라졌습니다. 최신 카드·대상·비용을 확인하세요.");
            } else {
                String event = switch (args[0]) {
                    case "confirm" -> "CONFIRMED";
                    case "skip" -> "SKIPPED";
                    case "reroll" -> "REROLL";
                    default -> configuration ? "FOLLOW_UP" : "DRAFT";
                };
                if (result.status() == PlayerAugmentState.Status.UNCHANGED) {
                    event = "REOPENED";
                }
                recordGui(game, player, resultingContext, event, route, result.status().name(), args[0].equals("skip") ? "EXPLICIT" : null);
            }
            game.playerLane(player.uuid()).ifPresent(lane -> lane.assignAugmentSnapshot(state.snapshot()));
            if (args[0].equals("confirm") && result.successful()) {
                announceSelection(game, online, player);
            }
            if ((args[0].equals("confirm") || args[0].equals("configure-confirm")) && result.successful()) {
                announceSettings(game, online.getServer(), player, true);
            }
            if (args[0].equals("reroll")) {
                showOffer(game, online, player);
            } else if (configuration && state.configurationDraft().isPresent()) {
                showDraft(game, online, player, true, "");
            } else if (state.currentOffer().isPresent() && state.currentOffer().get().draft() != null) {
                showDraft(game, online, player, false, "");
            } else {
                openView(game, online, player, "current");
            }
            return result.successful() ? 1 : 0;
        } catch (IllegalArgumentException exception) {
            recordGui(game, player, requestContext, "REJECTED", route, "INVALID_REQUEST",
                    exception instanceof GuiRequestException invalid ? invalid.reason
                            : exception instanceof NumberFormatException ? "MALFORMED_NUMBER" : "INVALID_ARGUMENT");
            error(online, exception instanceof NumberFormatException ? "입력 형식이 잘못되었습니다. /증강에서 다시 선택하세요." : exception.getMessage());
            return 0;
        }
    }

    private static void requireArity(String[] args, int... permitted) {
        for (int count : permitted) {
            if (args.length == count) {
                return;
            }
        }
        throw new GuiRequestException("MALFORMED_INPUT", "입력 형식이 잘못되었습니다. /증강에서 다시 선택하세요.");
    }

    private static UUID checkedRequest(String value) {
        return UUID.fromString(value);
    }

    private static boolean parseToggle(String value) {
        if (!value.equals("true") && !value.equals("false")) {
            throw new IllegalArgumentException("계약 토글 값이 잘못되었습니다.");
        }
        return Boolean.parseBoolean(value);
    }

    private static AugmentEconomyService.Contract purchaseOverride(String value) {
        return switch (value) {
            case "current" -> null;
            case "normal" -> AugmentEconomyService.Contract.NONE;
            case "low" -> AugmentEconomyService.Contract.LOW_PRESSURE;
            default -> throw new IllegalArgumentException("알 수 없는 구매 계약입니다.");
        };
    }

    public boolean showSummonPurchase(SemionGame game, ServerPlayer online, String summonId) {
        SemionPlayer player = game.players().get(online.getUUID());
        if (!game.augmentsEnabled() || !alive(game, player) || game.phase() != RoundPhase.PREPARE_AND_SUMMON) {
            return false;
        }
        boolean lowPressure = player.augments().hasSelected("low_pressure_high_yield");
        if (!lowPressure && AugmentEconomyService.contract(player) == AugmentEconomyService.Contract.NONE
                && !AugmentEconomyService.payloadArmed(player)) {
            return false;
        }
        var type = game.summonShop().find(summonId).orElse(null);
        if (type == null) {
            return false;
        }
        var current = AugmentEconomyService.previewPurchase(game, player, type).orElse(null);
        if (current == null) {
            error(online, "이 인컴을 구매할 수 없습니다. 빌더의 소환 조건을 확인하세요.");
            return true;
        }
        List<Button> buttons = new ArrayList<>();
        StringBuilder body = new StringBuilder(MiniMessage.miniMessage().escapeTags(type.displayName())).append("\n현재 계약: ").append(contractName(current.contract()))
                .append("\n").append(purchaseSummary(current));
        if (player.economy().emerald() >= current.emeraldCost()) {
            buttons.add(button(current.contract() == AugmentEconomyService.Contract.NONE ? "일반 구매" : contractName(current.contract()) + " 구매",
                    "buy " + current.revision() + " " + type.id() + " current " + current.emeraldCost()));
        } else {
            body.append("\n에메랄드가 부족합니다.");
        }
        if (lowPressure && AugmentEconomyService.isStandardAttack(type)) {
            var low = AugmentEconomyService.previewPurchase(game, player, type, AugmentEconomyService.Contract.LOW_PRESSURE).orElse(null);
            if (low != null) {
                body.append("\n\n저압 계약\n").append(purchaseSummary(low));
                if (player.economy().emerald() >= low.emeraldCost()) {
                    buttons.add(button("저압 계약 구매", "buy " + low.revision() + " " + type.id() + " low " + low.emeraldCost()));
                }
            } else {
                body.append("\n다른 가치 계약을 먼저 꺼야 저압 계약을 쓸 수 있습니다.");
            }
        }
        if (current.contract() == AugmentEconomyService.Contract.NONE && AugmentEconomyService.contract(player) != AugmentEconomyService.Contract.NONE) {
            body.append("\n현재 계약은 이 유닛에 적용되지 않으며 다음 적격 구매까지 유지됩니다.");
        }
        buttons.add(button("인컴 계약 변경", "ui contracts"));
        buttons.add(new Button("소환 상점으로", "/semiontd summonui", "구매하지 않고 돌아갑니다."));
        show(online, "인컴 구매 확인", body.toString(), buttons, 2);
        return true;
    }

    private void reopenPurchase(SemionGame game, ServerPlayer online, String summonId) {
        if (!showSummonPurchase(game, online, summonId)) {
            dialogs.showSummonShop(online, game);
        }
    }

    public static String purchaseSummary(AugmentEconomyService.PurchasePlan plan) {
        return "지금 비용 " + plan.emeraldCost() + "에메랄드 · R" + plan.scheduledRound() + " 출현\n"
                + "영구 인컴 +" + plan.incomeGain() + (plan.deferredIncome() > 0 ? " · 출현 때 +" + plan.deferredIncome() : "")
                + " · 즉시 다이아 +" + plan.instantDiamond() + "\n"
                + "체력 ×" + number(plan.healthMultiplier()) + " · 기본 공격 ×" + number(plan.attackMultiplier())
                + " · 지원 ×" + number(plan.supportMultiplier());
    }

    private void showUpgradeQuote(SemionGame game, ServerPlayer online, SemionPlayer player, Tower tower, String upgradeId,
                                   AugmentEconomyService.UpgradePlan quote) {
        long nextPayout = AugmentEconomyService.previewPayoutAfterTicket(player, player.economy().income());
        show(online, "금지된 설계도 · 승급 확인", MiniMessage.miniMessage().escapeTags(towerLabel(tower)) + "\n지금 결제 " + quote.cost() + "다이아 · 이용권 지원 " + quote.discount()
                        + "다이아\n남은 이용권 " + player.economyAugments().remainingTickets() + " → " + (player.economyAugments().remainingTickets() - 1)
                        + "\n현재 수입 기준 다음 정기 지급 " + nextPayout + "다이아\n사용하면 경기 종료까지 정기 지급이 감소합니다. 이번 준비 종료 시 미사용 권리는 사라집니다.",
                List.of(button("이용권 1장으로 승급 확정", "upgrade-confirm " + quote.revision() + " " + tower.logicalId() + " " + upgradeId + " " + quote.cost()),
                        new Button("타워로 돌아가기", "/semiontd tower ui", "아무것도 소비하지 않고 돌아갑니다.")), 2);
    }

    public static Optional<Button> upgradeAction(SemionGame game, SemionPlayer player, Tower tower,
                                                   kim.biryeong.semiontd.tower.TowerUpgradeOption option) {
        if (!game.augmentsEnabled() || player == null || game.phase() != RoundPhase.PREPARE_AND_SUMMON) {
            return Optional.empty();
        }
        return AugmentEconomyService.quoteUpgrade(player, new UUID(0, 0), game.currentRound(), tower.logicalId(),
                        ProductionTowerService.eligibleTicketUpgrade(game, player, tower, option), option.mineralCost(), true)
                .map(quote -> game.augmentService().scope(button(option.displayName() + " · 이용권 검토 (" + quote.cost() + "다이아)",
                        "upgrade " + tower.logicalId() + " " + option.id())));
    }

    public static String placementPreview(SemionGame game, ServerPlayer online, TowerType type) {
        if (!AugmentTowers.isAugment(type)) {
            return "";
        }
        PlayerLane lane = game.playerLane(online.getUUID()).orElse(null);
        if (lane == null) {
            return "담당 레인이 없습니다.";
        }
        StringBuilder text = new StringBuilder(type.displayName()).append(" · 비용 ").append(type.mineralCost())
                .append("다이아 · 슬롯 ").append(AugmentTowers.slots(type)).append(" · 설치 한도 ").append(AugmentTowers.placementLimit(type))
                .append("기\n체력 ").append(number(type.maxHealth())).append(" · 범위 ").append(number(type.range())).append("블록\n");
        if (AugmentTowers.isFreeCall(type)) {
            text.append("무료 배치권은 성공할 때만 소비합니다. 판매·수동 재배치·승급은 불가합니다.\n");
        } else {
            text.append("설계도는 무료 타워가 아닙니다. 정상 판매는 가능하며 승급은 불가합니다.\n");
        }
        if (AugmentTowers.is(type, AugmentTowers.AMBUSH_WORKSHOP)) {
            var position = TowerPlacementPositions.resolveGrid(lane, online.blockPosition());
            if (position.isEmpty()) {
                text.append("지뢰 미리보기: 설치 가능한 레인 지점 위에서 다시 여세요.\n");
            } else {
                var mines = AugmentTowerService.minePreview(lane, position.get());
                if (mines.isEmpty()) {
                    text.append(AugmentTowerService.placementProblem(lane, type)).append('\n');
                } else {
                    SemionLaneIndicatorService.showPlacementPoints(online, mines);
                    text.append("현재 자리의 지뢰 지점: ").append(mines.stream()
                            .map(point -> "X " + number(point.x) + "/Z " + number(point.z))
                            .collect(java.util.stream.Collectors.joining(" → "))).append('\n');
                }
            }
        }
        return text.toString();
    }

    private static void showPreparedMinePositions(SemionGame game, SemionPlayer player) {
        var lane = game.playerLane(player.uuid()).orElse(null);
        if (lane == null) {
            return;
        }
        for (Tower tower : lane.towers()) {
            if (!AugmentTowers.is(tower.type(), AugmentTowers.AMBUSH_WORKSHOP) || !(tower instanceof EntityBackedTower entityBacked)) {
                continue;
            }
            entityBacked.runtimeEntity(lane).filter(net.minecraft.world.entity.Entity::isAlive).ifPresent(source -> {
                for (var point : AugmentTowerService.minePreview(lane, tower.position())) {
                    kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService.showAreaEffect(source,
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("semiontd", "augment_mine_preview"),
                            kim.biryeong.semiontd.api.area.AreaVfxStyles.PULSE, point,
                            parameter(game, "ambush_workshop_blueprint", "triggerRadius", 1.25), List.of(), 0, 0, 0);
                }
            });
        }
    }

    private AugmentChoice editedChoice(SemionGame game, SemionPlayer player, AugmentDefinition card,
                                        AugmentChoice previous, String operation, String value, int targetSlot) {
        if (operation.equals("mode")) {
            if (!modes(card.id()).contains(value)) {
                throw new GuiRequestException("INVALID_MODE", "이 카드에 없는 모드입니다.");
            }
            return new AugmentChoice(previous.primaryTargetId(), previous.secondaryTargetId(), value);
        }
        UUID target = UUID.fromString(value);
        if (targetSlot < 0 || targetSlot >= targetCount(card.id())
                || eligibleTargets(game, player, card.id()).stream().noneMatch(tower -> tower.logicalId().equals(target))) {
            throw new GuiRequestException("TARGET_UNAVAILABLE", "선택한 타워는 현재 대상이 될 수 없습니다. 새 대상을 고르세요.");
        }
        if (targetSlot == 1 && (previous.primaryTargetId() == null || target.equals(previous.primaryTargetId()))) {
            throw new GuiRequestException("ROLE_TARGET_COLLISION", "선봉과 포대는 서로 다른 타워여야 합니다.");
        }
        return targetSlot == 0 ? new AugmentChoice(target, null, previous.mode())
                : new AugmentChoice(previous.primaryTargetId(), target, previous.mode());
    }

    private boolean commitSelection(SemionGame game, SemionPlayer player, AugmentDefinition card, AugmentChoice choice) {
        if (!isEligible(game, player, card.id()) || !validChoice(game, player, card, choice)) {
            return false;
        }
        AugmentEconomyService.onSelected(player, card.id(), game.currentRound(),
                game.augmentConfig().parameters().getOrDefault(card.id(), Map.of()));
        return true;
    }

    public static String hudHint(SemionGame game, SemionPlayer player) {
        if (!game.augmentsEnabled() || game.phase() != RoundPhase.PREPARE_AND_SUMMON || !alive(game, player)) {
            return "";
        }
        var offer = player.augments().currentOffer().orElse(null);
        if (offer != null && game.currentTick() < offer.deadlineTickExclusive()) {
            long seconds = Math.max(0, (offer.deadlineTickExclusive() - game.currentTick() + 19) / 20);
            return "R" + offer.milestoneRound() + " " + rarityLabel(offer.rarity()) + " 증강 미선택 · " + seconds + "초 · /증강";
        }
        List<String> settings = new ArrayList<>();
        for (var selection : player.augments().selections()) {
            if (selection.outcome() != PlayerAugmentState.Outcome.SELECTED || !configurable(selection.augmentId())) {
                continue;
            }
            var card = AugmentCatalog.find(selection.augmentId()).orElseThrow();
            AugmentChoice choice = selection.choice();
            boolean targetMissing = targetCount(card.id()) > 0 && (choice.primaryTargetId() == null
                    || towers(game, player).stream().noneMatch(tower -> tower.logicalId().equals(choice.primaryTargetId()))
                    || targetCount(card.id()) == 2 && (choice.secondaryTargetId() == null
                    || towers(game, player).stream().noneMatch(tower -> tower.logicalId().equals(choice.secondaryTargetId()))));
            String state = shortId(card.id()).equals("overheat_core") && choice.primaryTargetId() == null ? "이번 웨이브 미사용"
                    : targetMissing ? "설정 필요" : modes(card.id()).isEmpty() ? "지정 유지" : modeName(choice.mode()) + " 유지";
            settings.add(card.displayName() + ": " + state);
        }
        return settings.isEmpty() ? "" : String.join(" · ", settings) + " · /증강 설정";
    }

    private void openView(SemionGame game, ServerPlayer online, SemionPlayer player, String view) {
        switch (view) {
            case "current", "offer" -> showOffer(game, online, player);
            case "history" -> showHistory(game, online, player);
            case "reroll" -> showReroll(game, online, player);
            case "skip" -> {
                var offer = player.augments().currentOffer().orElse(null);
                if (offer == null) {
                    showHistory(game, online, player);
                    return;
                }
                show(online, "증강 건너뛰기", "R" + offer.milestoneRound() + " " + rarityLabel(offer.rarity())
                                + " 증강을 받지 않고 진행합니다.\n이번 선택 기회는 되돌릴 수 없습니다.",
                        List.of(button("증강 없이 진행", "skip " + offer.revision() + " " + requestId()),
                                button("카드로 돌아가기", "ui offer back")), 2);
            }
            case "contracts" -> showContracts(game, online, player);
            case "help" -> show(online, "증강 도움말",
                    "R5·R15·R25에 같은 등급의 세 장 중 하나를 고릅니다.\n경기마다 등급 순서는 모두 같고 카드 후보는 개인마다 다릅니다.\n"
                            + "첫 클릭은 검토입니다. 마지막 확인 전에는 효과와 대가가 적용되지 않습니다.\n"
                            + "경기당 리롤 한 번으로 한 칸만 바꿀 수 있습니다.\n닫아도 초안이 유지됩니다. 30초 선택 시간 안에 확정하지 않으면 건너뜁니다.\n"
                            + "선택 시간에는 전원의 에메랄드 자동 생산을 멈춥니다. 이후 일반 준비 25초 동안 다시 생산합니다.\n"
                            + "전용 타워 증강은 경기당 한 장만 고를 수 있습니다.\n후보가 부족하면 즉시 다이아·정기 인컴·생산 보너스로 빈 칸을 채웁니다.",
                    List.of(button("돌아가기", "ui current back")), 1);
            default -> throw new IllegalArgumentException("알 수 없는 증강 화면입니다.");
        }
    }

    private void showReroll(SemionGame game, ServerPlayer online, SemionPlayer player) {
        var state = player.augments();
        var offer = state.currentOffer().orElse(null);
        if (offer == null) {
            showHistory(game, online, player);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        StringBuilder body = new StringBuilder("이번에 사용하면 이후에는 리롤이 남지 않습니다.\n다른 두 장은 그대로 유지됩니다.\n");
        for (int slot = 0; slot < offer.cardIds().size(); slot++) {
            var card = AugmentCatalog.find(offer.cardIds().get(slot)).orElseThrow();
            body.append("\n").append(slot + 1).append("번: ").append(cardLabel(card));
            if (state.canReroll(offer.milestoneRound(), slot, candidate -> isEligible(game, player, candidate.id()))) {
                buttons.add(button((slot + 1) + "번만 리롤 · 1회 즉시 소비", "reroll " + offer.revision() + " " + slot + " " + requestId()));
            } else {
                body.append(" · 교체 불가: 리롤을 사용했거나 유효한 대체 카드가 없습니다.");
            }
        }
        buttons.add(button("카드로 돌아가기", "ui offer back"));
        show(online, "카드 한 칸 리롤", body.toString(), buttons, 3);
    }

    private void showHistory(SemionGame game, ServerPlayer online, SemionPlayer player) {
        List<Button> buttons = new ArrayList<>();
        StringBuilder body = new StringBuilder(selectionCountLabel(player.augments())).append("\n\n");
        for (int index = 0; index < AugmentCatalog.MILESTONES.size(); index++) {
            int milestone = AugmentCatalog.MILESTONES.get(index);
            var selection = player.augments().selections().stream().filter(value -> value.milestoneRound() == milestone).findFirst().orElse(null);
            body.append("R").append(milestone).append(" [")
                    .append(index < game.augmentRarities().size() ? rarityLabel(game.augmentRarities().get(index)) : "미정").append("] ");
            if (selection == null) {
                body.append("아직 선택하지 않음\n");
                continue;
            }
            if (selection.outcome() != PlayerAugmentState.Outcome.SELECTED) {
                body.append(selection.skipReason() == PlayerAugmentState.SkipReason.TIMEOUT ? "시간 초과로 건너뜀" : "직접 건너뜀").append('\n');
                continue;
            }
            AugmentDefinition card = AugmentCatalog.find(selection.augmentId()).orElseThrow();
            body.append(cardLabel(card)).append('\n');
            body.append(preview(game, player, card, selection.choice())).append("\n\n");
            if (configurable(card.id()) && game.phase() == RoundPhase.PREPARE_AND_SUMMON
                    && player.augments().lastConfiguredRound(milestone) < game.currentRound()) {
                buttons.add(button(card.displayName() + " 설정 변경", "configure " + index + " " + requestId()));
            }
        }
        var economy = player.economyAugments();
        body.append("남은 부채 ").append(economy.debt()).append(" · 승급권 ").append(economy.remainingTickets())
                .append(" · 지원 실적 +").append(economy.supportIncome()).append("/+")
                .append(number(parameter(game, "support_performance", "matchIncomeCap", 8))).append('\n');
        appendForecasts(body, game, player);
        body.append("설계도·호출 타워는 기존 타워 설치 화면에서 배치합니다.");
        buttons.add(button("인컴 계약", "ui contracts"));
        if (player.augments().currentOffer().isPresent()) {
            buttons.add(button("제안 카드로", "ui offer back"));
        }
        show(online, "증강 선택 기록 · 설정", body.toString(), buttons, 2);
    }

    private static PlayerAugmentState.Selection selectionAt(SemionPlayer player, int slot) {
        if (slot < 0 || slot >= AugmentCatalog.MILESTONES.size()) {
            throw new IllegalArgumentException("선택 기록 칸이 잘못되었습니다.");
        }
        return player.augments().selections().stream()
                .filter(selection -> selection.milestoneRound() == AugmentCatalog.MILESTONES.get(slot)
                        && selection.outcome() == PlayerAugmentState.Outcome.SELECTED)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("이 칸에는 선택한 증강이 없습니다."));
    }

    private void showDraft(SemionGame game, ServerPlayer online, SemionPlayer player, boolean configuration, String requestedStep) {
        var state = player.augments();
        var offer = state.currentOffer().orElse(null);
        var configurationDraft = state.configurationDraft().orElse(null);
        if (configuration && configurationDraft == null || !configuration && (offer == null || offer.draft() == null)) {
            openView(game, online, player, "current");
            return;
        }
        String cardId = configuration ? state.selections().stream()
                .filter(selection -> selection.milestoneRound() == configurationDraft.selectedMilestone())
                .map(PlayerAugmentState.Selection::augmentId).findFirst().orElseThrow() : offer.draft().cardId();
        AugmentDefinition card = AugmentCatalog.find(cardId).orElseThrow();
        AugmentChoice choice = configuration ? configurationDraft.choice() : offer.draft().choice();
        String context = configuration ? "configuration" : "offer";
        String prefix = configuration ? "configure-" : "";
        String revisions = configuration ? Long.toString(configurationDraft.revision()) : offer.revision() + " " + offer.draftRevision();
        int count = targetCount(cardId);
        String step = requestedStep;
        if (step.isBlank()) {
            step = count > 0 && choice.primaryTargetId() == null ? "target"
                    : count == 2 && choice.secondaryTargetId() == null ? "secondary"
                    : !modes(cardId).isEmpty() && choice.mode().isEmpty() ? "mode" : "confirm";
        }
        List<Button> buttons = new ArrayList<>();
        String body = selectionCountLabel(state) + "\n" + (configuration ? "설정 변경은 준비 단계마다 한 번만 확정할 수 있습니다."
                : remainingAtOpen(game, offer.deadlineTickExclusive())) + "\n" + preview(game, player, card, choice);
        if (step.equals("target") || step.equals("secondary")) {
            int targetSlot = step.equals("secondary") ? 1 : 0;
            if (targetSlot >= count) {
                throw new IllegalArgumentException("이 카드에는 선택할 타워 대상이 없습니다.");
            }
            List<Tower> targets = eligibleTargets(game, player, cardId);
            body += "\n" + (count == 2 ? targetSlot == 0 ? "선봉" : "포대" : "대상") + "을 고르세요.\n"
                    + "선택 가능 " + targets.size() + "기 · 제외 " + (towers(game, player).size() - targets.size())
                    + "기: 일반 영구 타워·역할 충돌·카드별 조건을 검사합니다.";
            for (int index = 0; index < targets.size(); index++) {
                Tower tower = targets.get(index);
                if (targetSlot == 1 && tower.logicalId().equals(choice.primaryTargetId())) {
                    continue;
                }
                buttons.add(button("#" + (index + 1) + " · " + towerLabel(tower), prefix + "target " + revisions
                        + " " + tower.logicalId() + " " + targetSlot));
            }
            if (targetSlot == 1) {
                buttons.add(button("선봉 다시 고르기", "view " + context + " target"));
            }
        } else if (step.equals("mode")) {
            for (String mode : modes(cardId)) {
                buttons.add(button(modeName(mode), prefix + "mode " + revisions + " " + mode));
            }
            if (count > 0) {
                buttons.add(button("대상 다시 고르기", "view " + context + " target"));
            }
        } else if (step.equals("confirm")) {
            if (validChoice(game, player, card, choice) && isEligible(game, player, cardId)) {
                buttons.add(button(card.displayName() + (configuration ? " 설정 확정" : " 확정"), prefix + "confirm " + revisions + " " + requestId()));
            } else {
                body += "\n대상·모드·비용이 유효하지 않습니다. 아래에서 다시 고르세요.";
            }
            if (!modes(cardId).isEmpty()) {
                buttons.add(button("모드 다시 고르기", "view " + context + " mode"));
            }
            if (count > 0) {
                buttons.add(button("대상 다시 고르기", "view " + context + " target"));
            }
        }
        buttons.add(button(configuration ? "선택 기록으로" : "다른 카드 보기", configuration ? "ui history back" : "ui offer back"));
        show(online, cardLabel(card) + " · " + (step.equals("confirm") ? "결과 확인" : "설정"), body, buttons, 2);
        if (!configuration) {
            recordShown(game, player, offer);
        }
    }

    private void showContracts(SemionGame game, ServerPlayer online, SemionPlayer player) {
        if (game.phase() != RoundPhase.PREPARE_AND_SUMMON) {
            show(online, "인컴 계약", "인컴 계약은 준비 단계에서만 적용합니다. 전투 중 예약 소환에는 적용하지 않습니다.",
                    List.of(new Button("소환 상점으로", "/semiontd summonui", "소환 상점을 엽니다.")), 1);
            return;
        }
        var state = player.economyAugments();
        StringBuilder body = new StringBuilder("다음 적격 구매에만 적용합니다. 실패하거나 부적격인 구매 뒤에는 유지됩니다.\n준비 종료 시 미사용 계약은 해제됩니다.\n");
        body.append("몸체 보정: 추가 적재 ").append(AugmentEconomyService.payloadArmed(player) ? "켜짐" : "꺼짐")
                .append("\n가치 계약: ").append(contractName(AugmentEconomyService.contract(player))).append('\n');
        List<Button> buttons = new ArrayList<>();
        if (player.augments().hasSelected("additional_payload")) {
            buttons.add(button(AugmentEconomyService.payloadArmed(player) ? "추가 적재 끄기" : "추가 적재 켜기",
                    "payload " + state.revision() + " " + !AugmentEconomyService.payloadArmed(player)));
            body.append("추가 적재: 유틸 인컴에 적용. 가치 계약 하나와 함께 사용할 수 있습니다.\n");
        }
        buttons.add(button("계약 없음", "contract " + state.revision() + " NONE"));
        for (var contract : List.of(AugmentEconomyService.Contract.FORECAST, AugmentEconomyService.Contract.CASH, AugmentEconomyService.Contract.DECISIVE)) {
            if (player.augments().hasSelected(contract.card)) {
                buttons.add(button(contractName(contract), "contract " + state.revision() + " " + contract.name()));
                body.append(contractName(contract)).append(": ").append(contract == AugmentEconomyService.Contract.CASH ? "정상 인컴 증가가 있는 유료 인컴"
                        : contract == AugmentEconomyService.Contract.FORECAST ? "공격형 인컴" : "고유 능력 없는 표준 공격형 인컴").append('\n');
            }
        }
        if (player.augments().hasSelected("low_pressure_high_yield")) {
            body.append("저압 계약은 적격 유닛의 구매 확인 화면에서 고릅니다. 다른 가치 계약이 켜져 있으면 먼저 꺼 주세요.\n");
        }
        appendForecasts(body, game, player);
        buttons.add(new Button("소환 상점으로", "/semiontd summonui", "소환 상점을 엽니다."));
        show(online, "인컴 계약", body.toString(), buttons, 2);
    }

    private static void appendForecasts(StringBuilder body, SemionGame game, SemionPlayer player) {
        for (var forecast : player.economyAugments().pendingForecasts()) {
            String name = game.summonShop().find(forecast.summonId()).map(SummonMonsterType::displayName).orElse(forecast.summonId());
            body.append("\n예약: R").append(forecast.round()).append(' ').append(MiniMessage.miniMessage().escapeTags(name))
                    .append(" +").append(number(parameter(game, "forecast_offensive", "echoRatio", 0.3) * 100))
                    .append("% 메아리 · ").append(forecast.targetTeam().name()).append(" 레인 ")
                    .append(forecast.targetLaneId()).append(" · 결제 ").append(forecast.paidEmerald())
                    .append("에메랄드 · 출현 시 인컴 +").append(forecast.amount()).append('\n');
        }
    }

    public static String contractName(AugmentEconomyService.Contract contract) {
        return switch (contract) {
            case NONE -> "없음";
            case FORECAST -> "예고 공세";
            case CASH -> "현금 결제";
            case LOW_PRESSURE -> "저압 계약";
            case DECISIVE -> "결전 납품";
        };
    }

    private static void announceSelection(SemionGame game, ServerPlayer online, SemionPlayer player) {
        var selection = player.augments().selections().stream().filter(value -> value.outcome() == PlayerAugmentState.Outcome.SELECTED)
                .max(Comparator.comparingInt(PlayerAugmentState.Selection::milestoneRound)).orElse(null);
        if (selection == null) {
            return;
        }
        var card = AugmentCatalog.find(selection.augmentId()).orElseThrow();
        Component message = SemionText.prefixed(Component.literal(player.name() + " · ")
                .append(SemionText.mini(rarityLabel(card.rarity()) + " 증강 " + cardLabel(card)))
                .append(Component.literal(" 확정")));
        for (ServerPlayer viewer : online.getServer().getPlayerList().getPlayers()) {
            if (game.isActiveParticipant(viewer.getUUID()) || game.isMatchSpectator(viewer.getUUID())) {
                viewer.sendSystemMessage(message);
            }
        }
    }

    public void announceWaveSettings(SemionGame game, MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (SemionPlayer player : game.players().values()) {
            if (alive(game, player)) {
                announceSettings(game, server, player, false);
            }
        }
    }

    private static void announceSettings(SemionGame game, MinecraftServer server, SemionPlayer player, boolean teammatesOnly) {
        List<String> settings = new ArrayList<>();
        for (var selection : player.augments().selections()) {
            if (selection.outcome() != PlayerAugmentState.Outcome.SELECTED || targetCount(selection.augmentId()) == 0 && modes(selection.augmentId()).isEmpty()) {
                continue;
            }
            StringBuilder text = new StringBuilder(AugmentCatalog.find(selection.augmentId()).orElseThrow().displayName());
            for (UUID id : java.util.Arrays.asList(selection.choice().primaryTargetId(), selection.choice().secondaryTargetId())) {
                if (id != null) {
                    text.append(" · ").append(towers(game, player).stream().filter(tower -> tower.logicalId().equals(id))
                            .map(AugmentService::towerLabel).findFirst().orElse("대상 없음"));
                }
            }
            if (!selection.choice().mode().isEmpty()) {
                text.append(" · ").append(modeName(selection.choice().mode()));
            }
            settings.add(text.toString());
        }
        if (settings.isEmpty()) {
            return;
        }
        Component message = SemionText.prefixedPlain(player.name() + (teammatesOnly ? " 준비 설정: " : " R" + game.currentRound() + " 전투 설정: ")
                + String.join(" / ", settings));
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            SemionPlayer participant = game.players().get(viewer.getUUID());
            if (teammatesOnly ? participant != null && participant.teamId() == player.teamId()
                    : game.isActiveParticipant(viewer.getUUID()) || game.isMatchSpectator(viewer.getUUID())) {
                viewer.sendSystemMessage(message);
            }
        }
    }

    private boolean validChoice(SemionGame game, SemionPlayer player, AugmentDefinition definition, AugmentChoice choice) {
        int count = targetCount(definition.id());
        Set<UUID> eligible = eligibleTargets(game, player, definition.id()).stream()
                .map(Tower::logicalId).collect(java.util.stream.Collectors.toSet());
        if (count >= 1 && !eligible.contains(choice.primaryTargetId())) {
            return false;
        }
        if (count == 2 && (!eligible.contains(choice.secondaryTargetId())
                || choice.primaryTargetId().equals(choice.secondaryTargetId()))) {
            return false;
        }
        if (count == 0 && choice.primaryTargetId() != null || count < 2 && choice.secondaryTargetId() != null) {
            return false;
        }
        List<String> modes = modes(definition.id());
        return modes.isEmpty() ? choice.mode().isEmpty() : modes.contains(choice.mode());
    }

    private static Comparator<Tower> towerOrder() {
        return Comparator.comparingInt((Tower tower) -> tower.originalPosition().x())
                .thenComparingInt(tower -> tower.originalPosition().z())
                .thenComparingInt(tower -> tower.originalPosition().y())
                .thenComparing(Tower::logicalId);
    }

    private static Button button(String label, String command) {
        return new Button(label, COMMAND + command, label);
    }

    private static String requestId() {
        return UUID.randomUUID().toString();
    }

    private void show(ServerPlayer player, String title, String body, List<Button> buttons, int columns) {
        render(player, new Screen(title, body, List.of(), buttons, columns));
    }

    private Button scope(Button button) {
        if (!button.command().startsWith(COMMAND) || button.command().startsWith(COMMAND + "session ")) {
            return button;
        }
        return new Button(button.label(), COMMAND + "session " + sessionToken + " " + button.command().substring(COMMAND.length()), button.description());
    }

    private void render(ServerPlayer player, Screen screen) {
        dialogs.showAugment(player, new Screen(screen.title(), screen.body(), screen.cards(),
                screen.buttons().stream().map(this::scope).toList(), screen.columns()));
    }

    private static void error(ServerPlayer player, String message) {
        player.sendSystemMessage(SemionText.prefixedError(message));
    }

    private GuiContext guiContext(SemionPlayer player) {
        var state = player.augments();
        var offer = state.currentOffer().orElse(null);
        if (offer != null) {
            return offerContext(offer);
        }
        return configurationContext(player);
    }

    private GuiContext configurationContext(SemionPlayer player) {
        var state = player.augments();
        var draft = state.configurationDraft().orElse(null);
        if (draft == null) {
            return null;
        }
        return state.selections().stream().filter(selection -> selection.milestoneRound() == draft.selectedMilestone())
                .map(selection -> new GuiContext(draft.selectedMilestone(), draft.revision(), null, selection.augmentId(), draft.choice()))
                .findFirst().orElse(null);
    }

    private static GuiContext offerContext(PlayerAugmentState.Offer offer) {
        var draft = offer.draft();
        return new GuiContext(offer.milestoneRound(), offer.revision(), draft == null ? null : offer.cardIds().indexOf(draft.cardId()),
                draft == null ? null : draft.cardId(), draft == null ? AugmentChoice.none() : draft.choice());
    }

    private GuiProgress progress(SemionPlayer player, GuiContext context) {
        return guiProgress.computeIfAbsent(new GuiKey(player.uuid(), context.milestone()), ignored -> new GuiProgress());
    }

    private void recordShown(SemionGame game, SemionPlayer player, PlayerAugmentState.Offer offer) {
        GuiContext context = offerContext(offer);
        GuiProgress progress = progress(player, context);
        if (progress.shownTick < 0) {
            progress.shownTick = game.currentTick();
            recordGui(game, player, context, "SHOWN", "DIALOG", "SUCCESS", null);
        }
    }

    private void recordGui(SemionGame game, SemionPlayer player, GuiContext context,
                           String event, String route, String result, String reason) {
        if (context == null) {
            return;
        }
        GuiProgress progress = progress(player, context);
        List<Integer> targetRefs = java.util.stream.Stream.of(context.choice().primaryTargetId(), context.choice().secondaryTargetId())
                .filter(java.util.Objects::nonNull).map(player.augmentTelemetry()::towerRef).toList();
        player.augmentTelemetry().recordGui(new AugmentTelemetrySnapshot.GuiEvent(context.milestone(), game.currentTick(), event,
                context.revision(), context.slot(), context.cardId(), route, result, reason,
                context.choice().mode().isEmpty() ? null : context.choice().mode(), targetRefs,
                progress.shownTick < 0 ? null : game.currentTick() - progress.shownTick, progress.inputs, progress.backs));
    }

    private void expireOffer(SemionGame game, SemionPlayer player, long now) {
        var offer = player.augments().currentOffer().orElse(null);
        player.augments().expire(now);
        if (offer != null && now >= offer.deadlineTickExclusive() && player.augments().currentOffer().isEmpty()) {
            recordGui(game, player, offerContext(offer), "SKIPPED", "TIMEOUT", "SUCCESS", "TIMEOUT");
        }
    }
}
