package kim.biryeong.semiontd.augment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static kim.biryeong.semiontd.augment.AugmentCategory.*;
import static kim.biryeong.semiontd.augment.AugmentRarity.*;

/** Forty common cards, 124 job cards and nine fallback rewards; old stance IDs remain readable. */
public final class AugmentCatalog {
    public static final int OFFER_RULES_VERSION = 3;
    public static final List<Integer> MILESTONES = List.of(5, 15, 25);
    private static final Set<Integer> ALL_ROUNDS = Set.of(5, 15, 25);
    private static final Map<String, List<String>> STANCES = Map.of(
            "semiontd:tactical_designation_1", List.of("ASSAULT", "COVER"),
            "semiontd:tactical_designation_2", List.of("ASSAULT", "COVER"),
            "semiontd:tactical_designation_3", List.of("ASSAULT", "COVER"),
            "semiontd:engagement_plan", List.of("QUICK", "LONG"),
            "semiontd:biased_armor", List.of("PHYSICAL", "MAGIC"));
    private static final List<AugmentDefinition> BASE_DEFINITIONS = createDefinitions();
    private static final List<AugmentDefinition> DEFINITIONS = splitStances();

    private AugmentCatalog() {}

    public static String normalizeId(String id) {
        if (id == null || id.isBlank()) {throw new IllegalArgumentException("Augment ID is required.");}
        return id.contains(":") ? id : "semiontd:" + id;
    }

    public static List<AugmentDefinition> definitions() {return DEFINITIONS;}
    /** Split cards share the existing effect and balance keys, not player-selectable modes. */
    public static String effectId(String id) {
        String normalized = normalizeId(id);
        int suffix = normalized.lastIndexOf('_');
        String parent = normalized.substring(0, suffix < 0 ? normalized.length() : suffix);
        return STANCES.getOrDefault(parent, List.of()).stream()
                .anyMatch(mode -> normalized.equals(parent + "_" + mode.toLowerCase(java.util.Locale.ROOT))) ? parent : normalized;
    }
    public static String fixedMode(String id) {
        String normalized = normalizeId(id), effect = effectId(id);
        return effect.equals(normalized) ? "" : normalized.substring(effect.length() + 1).toUpperCase(java.util.Locale.ROOT);
    }
    public static boolean matchesSelection(String requestedId, String selectedId) {
        if (selectedId == null) {return false;}
        String requested = normalizeId(requestedId);
        return requested.equals(selectedId) || requested.equals(effectId(selectedId));
    }
    public static List<AugmentDefinition> normalDefinitions() {
        return DEFINITIONS.stream().filter(card -> !card.reserve()).toList();
    }
    public static List<AugmentDefinition> reserveDefinitions() {
        return DEFINITIONS.stream().filter(AugmentDefinition::reserve).toList();
    }
    public static Optional<AugmentDefinition> find(String id) {
        if (id == null || id.isBlank()) {return Optional.empty();}
        String normalized = normalizeId(id);
        // Legacy combined IDs remain readable, but only split cards enter offers and exports.
        return DEFINITIONS.stream().filter(card -> card.id().equals(normalized)).findFirst()
                .or(() -> BASE_DEFINITIONS.stream().filter(card -> card.id().equals(normalized)).findFirst());
    }

    private static List<AugmentDefinition> splitStances() {
        List<AugmentDefinition> result = new ArrayList<>();
        for (AugmentDefinition base : BASE_DEFINITIONS) {
            Set<String> conflicts = base.conflicts().stream().flatMap(id -> STANCES.containsKey(id)
                    ? STANCES.get(id).stream().map(mode -> id + "_" + mode.toLowerCase(java.util.Locale.ROOT))
                    : java.util.stream.Stream.of(id)).collect(java.util.stream.Collectors.toUnmodifiableSet());
            for (String mode : STANCES.getOrDefault(base.id(), List.of(""))) {
                String name = switch (mode) {
                    case "ASSAULT" -> base.displayName() + " (돌격)";
                    case "COVER" -> base.displayName() + " (엄호)";
                    case "QUICK" -> "속전";
                    case "LONG" -> "지구전";
                    case "PHYSICAL" -> "물리장갑";
                    case "MAGIC" -> "마법장갑";
                    default -> base.displayName();
                };
                result.add(new AugmentDefinition(base.id() + (mode.isEmpty() ? "" : "_" + mode.toLowerCase(java.util.Locale.ROOT)),
                        name, base.rarity(), base.category(), base.familyKey(), base.safe(), base.risky(), base.towerAugment(),
                        base.reserve(), base.milestoneRounds(), conflicts, base.description(), base.requiredJobId()));
            }
        }
        return List.copyOf(result);
    }

    public static List<AugmentRarity> drawRarities(long seed, AugmentConfig config) {
        Random random = new Random(seed);
        int roll = random.nextInt(100);
        String chosen = null;
        for (var entry : new java.util.TreeMap<>(config.rarityWeights()).entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {chosen = entry.getKey(); break;}
        }
        if (chosen == null) {throw new IllegalArgumentException("Rarity weights must sum to 100.");}
        List<AugmentRarity> result = new ArrayList<>();
        for (char rarity : chosen.toCharArray()) {
            result.add(switch (rarity) {case 'S' -> SILVER; case 'G' -> GOLD; case 'P' -> PRISMATIC;
                default -> throw new IllegalArgumentException("Unknown rarity combination.");});
        }
        Collections.shuffle(result, random);
        return List.copyOf(result);
    }

    private static List<AugmentDefinition> createDefinitions() {
        List<AugmentDefinition> cards = new ArrayList<>();
        cards.add(card("tactical_designation_1", "전술 지명 I", SILVER, GENERAL, "TACTICAL_DESIGNATION", true,
                "지정 타워에 돌격(피해 증가) 또는 엄호(받는 피해 감소) 중 하나를 적용합니다."));
        cards.add(card("triangle_formation", "삼각진", SILVER, GENERAL, null, false,
                "가까이 모인 타워들이 더 강하게 공격하고 피해를 덜 받습니다."));
        cards.add(card("engagement_plan", "교전 계획", SILVER, GENERAL, null, true,
                "초반 공격을 강화하는 속전과, 시간이 지난 뒤 공격과 방어를 강화하는 지구전 중 하나를 고릅니다."));
        cards.add(card("emergency_loan", "비상 융자", SILVER, TRADE_OFF, null, false,
                "정기 수입 3회분을 최대 300다이아까지 즉시 받고, 원금의 4/3을 이후 정기 지급에서 갚습니다."));
        cards.add(card("folding_barricade_blueprint", "방벽", SILVER, TOWER, null, false,
                "160다이아와 한 슬롯으로 전용 방벽 한 기를 설치할 수 있습니다. 무료 지급이 아닙니다."));
        cards.add(card("additional_payload", "과다 투자", SILVER, INCOME, null, false,
                "유틸 인컴을 더 비싸게 보내는 대신 체력, 회복량과 보호막을 강화합니다."));
        cards.add(card("twin_squadron", "쌍둥이", SILVER, GENERAL, null, false,
                "같은 종류·같은 티어의 공격 타워가 정확히 두 기이면 두 타워가 함께 강화됩니다."));
        cards.add(card("tactical_designation_2", "전술 지명 II", GOLD, GENERAL, "TACTICAL_DESIGNATION", true,
                "지정 타워에 돌격(피해 증가) 또는 엄호(받는 피해 감소) 중 하나를 적용합니다."));
        cards.add(card("overheat_core", "오버히트", GOLD, TRADE_OFF, null, false,
                "이번 전투에 예약한 타워의 피해가 증가합니다. 종료 후 최종 피해를 영구적으로 낮추는 열화를 얻습니다."));
        cards.add(card("pulse_relay_blueprint", "결속 타워", GOLD, TOWER, null, false,
                "220다이아와 한 슬롯으로 중계기를 해금합니다. 연결 타워의 기본 공격 다섯 번마다 다음 공격을 강화합니다."));
        cards.add(card("frontline_specialization", "영혼 결속", GOLD, GAME_CHANGER, null, false,
                "타워 하나는 공격을 줄여 더 오래 버티고, 다른 하나는 방어를 줄여 더 강하게 공격합니다."));
        cards.add(card("forecast_offensive", "복제본 생성", GOLD, INCOME, null, false,
                "인컴을 미리 결제하고 다음 웨이브에 원본과 약한 복제본을 함께 보냅니다."));
        cards.add(card("support_performance", "인센티브", GOLD, INCOME, null, false,
                "유틸 인컴이 다른 유닛을 회복하거나 보호하면 정기 인컴을 추가로 얻습니다."));
        cards.add(card("battlefield_mastery", "고참병의 흉터", GOLD, GENERAL, null, false,
                "지정 타워가 적에게 충분한 체력 피해를 받고 생존하면 숙련을 쌓습니다. 대상을 바꾸거나 해제하면 숙련을 잃습니다."));
        cards.add(card("biased_armor", "편향 장갑", GOLD, TRADE_OFF, null, false,
                "물리 또는 마법 중 하나에 강해지는 대신, 반대 유형에는 더 큰 피해를 받습니다."));
        cards.add(card("cash_settlement", "일시불", GOLD, INCOME, null, false,
                "다음 인컴 구매에서 정기 인컴 증가를 포기하고 다이아를 즉시 받습니다."));
        cards.add(card("tactical_designation_3", "전술 지명 III", PRISMATIC, GENERAL, "TACTICAL_DESIGNATION", true,
                "지정 타워에 돌격(피해 증가) 또는 엄호(받는 피해 감소) 중 하나를 적용합니다."));
        cards.add(card("forbidden_blueprint", "금지된 설계도", PRISMATIC, TRADE_OFF, null, false,
                "승급 비용을 지원하는 이용권 두 장을 받습니다. 사용할 때마다 이후 정기 수입이 줄어듭니다."));
        cards.add(card("barrier_core_call", "수호자", PRISMATIC, TOWER, null, false,
                "타워 자리 두 칸을 쓰는 수호자를 받습니다. 연결한 타워의 피해 일부를 대신 받습니다."));
        cards.add(card("low_pressure_high_yield", "내실 다지기", PRISMATIC, INCOME, null, false,
                "약해진 인컴 유닛을 보내는 대신 정기 인컴을 더 많이 올립니다."));
        for (int i = 1; i <= 3; i++) {
            cards.add(card("finishing_fire_" + i, "마무리 사격 " + roman(i), AugmentRarity.values()[i - 1],
                    GENERAL, "FINISHING_FIRE", false,
                    "체력이 절반 이하인 적에게 기본 공격이 더 강해집니다. 범위 피해는 제외합니다."));
        }
        cards.add(card("independent_position", "혼자가 편해", SILVER, GENERAL, null, false,
                "다른 타워와 떨어져 배치한 공격 타워가 더 강하게 공격하고 피해를 덜 받습니다."));
        cards.add(card("winning_barrage", "칼날비", GOLD, GENERAL, null, false,
                "기본 공격으로 적을 처치하면 다음 세 번의 공격이 강화됩니다. 처치할 때마다 다시 충전합니다."));
        cards.add(card("decisive_delivery", "결전 납품", GOLD, INCOME, null, false,
                "정기 인컴 증가를 포기하고 체력과 공격력이 높은 인컴 유닛을 보냅니다."));
        cards.add(card("domino_fire", "도미노 사격", GOLD, GAME_CHANGER, null, false,
                "기본 공격으로 적을 처치하면 남은 피해 일부를 주변의 다른 적 한 기에게 전달합니다."));
        cards.add(card("one_man_show", "원맨쇼", PRISMATIC, TRADE_OFF, null, false,
                "주역 타워 하나의 공격과 체력을 크게 올립니다. 다른 타워의 공격은 약해지고 주역은 바꿀 수 없습니다."));
        cards.add(card("wartime_economy", "총동원령", PRISMATIC, INCOME, null, false,
                "이후 정기 수입을 줄이고 일반 타워 전체의 공격과 체력을 올립니다. 취소할 수 없습니다."));
        cards.add(card("giant_hunter_call", "공성 전차", PRISMATIC, TOWER, null, false,
                "한 슬롯의 무료 저격 타워 배치권. 3~9블록의 큰 적을 공격하며 가까운 적은 공격하지 못합니다."));
        cards.add(card("emergency_bell_blueprint", "수호천사", SILVER, TOWER, null, false,
                "120다이아·한 슬롯. 체력 40% 이하의 아군을 서로 다른 세 기까지 한 번씩 회복합니다."));
        cards.add(card("capacitor_post_blueprint", "전지 타워", GOLD, TOWER, null, false,
                "기당 140다이아·한 슬롯, 최대 두 기. 사거리 안에 적이 없을 때 충전해 다음 한 발에 방출합니다."));
        cards.add(card("ambush_workshop_blueprint", "지뢰 생성기", GOLD, TOWER, null, false,
                "220다이아·한 슬롯. 설치 방향 앞에 지뢰 세 개를 준비하고 매 웨이브 다시 장전합니다."));
        cards.add(card("starlight_cocoon_call", "별빛 고치 호출", PRISMATIC, TOWER, null, false,
                "두 슬롯의 무료 고치 배치권. 두 웨이브를 파괴 없이 끝내면 다음 준비 단계에 파수꾼으로 부화합니다."));
        cards.add(card("ordnance_factory_call", "인컴 대포", PRISMATIC, TOWER, null, false,
                "한 슬롯의 무료 공장 배치권. 인컴 구매에 쓴 100에메랄드마다 다음 전투 포탄 한 발, 최대 네 발."));
        cards.addAll(JobAugmentCatalog.definitions());
        for (AugmentRarity rarity : AugmentRarity.values()) {
            int tier = rarity.ordinal();
            String suffix = rarity.name().toLowerCase(java.util.Locale.ROOT);
            cards.add(reserve("reserve_diamonds_" + suffix, "예비 다이아 " + roman(tier + 1), rarity,
                    "RESERVE_DIAMONDS", "즉시 다이아 +" + new int[]{150, 300, 600}[tier] + "."));
            cards.add(reserve("reserve_income_" + suffix, "예비 인컴 " + roman(tier + 1), rarity,
                    "RESERVE_INCOME", "정기 인컴 +" + new int[]{15, 30, 60}[tier] + "."));
            cards.add(reserve("reserve_production_" + suffix, "예비 생산 " + roman(tier + 1), rarity,
                    "RESERVE_PRODUCTION", "기본 에메랄드 초당 생산 +" + new int[]{2, 3, 6}[tier] + "."));
        }
        return List.copyOf(cards);
    }

    private static String roman(int tier) {return new String[]{"I", "II", "III"}[tier - 1];}

    private static AugmentDefinition reserve(String id, String name, AugmentRarity rarity, String family, String text) {
        return new AugmentDefinition(id, name, rarity, INCOME, family, true, false, false, true,
                ALL_ROUNDS, Set.of(), text);
    }

    private static AugmentDefinition card(String id, String name, AugmentRarity rarity, AugmentCategory category,
                                          String family, boolean safe, String description) {
        Set<Integer> rounds = switch (id) {
            case "emergency_loan", "overheat_core", "forbidden_blueprint", "starlight_cocoon_call" -> Set.of(5, 15);
            case "ordnance_factory_call" -> Set.of(15, 25);
            default -> ALL_ROUNDS;
        };
        Set<String> conflicts = new java.util.HashSet<>();
        Set<String> futureDiamond = Set.of("emergency_loan", "forbidden_blueprint", "cash_settlement", "wartime_economy");
        Set<String> tactical = Set.of("tactical_designation_1", "tactical_designation_2", "tactical_designation_3");
        if (futureDiamond.contains(id)) {conflicts.addAll(futureDiamond); conflicts.remove(id);}
        if (tactical.contains(id)) {conflicts.addAll(Set.of("overheat_core", "one_man_show"));}
        if (id.equals("overheat_core")) {conflicts.addAll(tactical); conflicts.add("one_man_show");}
        if (id.equals("one_man_show")) {conflicts.addAll(tactical); conflicts.addAll(Set.of("overheat_core", "frontline_specialization"));}
        if (id.equals("frontline_specialization")) {conflicts.add("one_man_show");}
        if (id.equals("forecast_offensive")) {conflicts.add("low_pressure_high_yield");}
        if (id.equals("low_pressure_high_yield")) {conflicts.add("forecast_offensive");}
        return new AugmentDefinition(id, name, rarity, category, family == null ? id.toUpperCase(java.util.Locale.ROOT) : family,
                safe, category == TRADE_OFF, category == TOWER, false, rounds, conflicts, description);
    }
}
