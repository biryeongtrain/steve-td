package kim.biryeong.semiontd.augment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static kim.biryeong.semiontd.augment.AugmentCategory.*;
import static kim.biryeong.semiontd.augment.AugmentRarity.*;

/** The 35 approved normal cards and nine fallback-only rewards. */
public final class AugmentCatalog {
    public static final int OFFER_RULES_VERSION = 1;
    public static final List<Integer> MILESTONES = List.of(5, 15, 25);
    private static final Set<Integer> ALL_ROUNDS = Set.of(5, 15, 25);
    private static final List<AugmentDefinition> DEFINITIONS = createDefinitions();

    private AugmentCatalog() {}

    public static String normalizeId(String id) {
        if (id == null || id.isBlank()) {throw new IllegalArgumentException("Augment ID is required.");}
        return id.contains(":") ? id : "semiontd:" + id;
    }

    public static List<AugmentDefinition> definitions() {return DEFINITIONS;}
    public static List<AugmentDefinition> normalDefinitions() {
        return DEFINITIONS.stream().filter(card -> !card.reserve()).toList();
    }
    public static List<AugmentDefinition> reserveDefinitions() {
        return DEFINITIONS.stream().filter(AugmentDefinition::reserve).toList();
    }
    public static Optional<AugmentDefinition> find(String id) {
        if (id == null || id.isBlank()) {return Optional.empty();}
        String normalized = normalizeId(id);
        return DEFINITIONS.stream().filter(card -> card.id().equals(normalized)).findFirst();
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
        cards.add(card("triangle_formation", "삼각 진형", SILVER, GENERAL, null, false,
                "반경 4블록에 이웃 영구 타워가 두 기 이상이면 피해 +8%, 받는 피해 8% 감소."));
        cards.add(card("engagement_plan", "교전 계획", SILVER, GENERAL, null, true,
                "선봉은 전투 첫 160틱 피해 +18%, 장기전은 이후 피해 +10%와 받는 피해 10% 감소."));
        cards.add(card("emergency_loan", "비상 융자", SILVER, TRADE_OFF, null, false,
                "정기 수입 3회분을 최대 300다이아까지 즉시 받고, 원금의 4/3을 이후 정기 지급에서 갚습니다."));
        cards.add(card("folding_barricade_blueprint", "접이식 방벽 설계도", SILVER, TOWER, null, false,
                "160다이아와 한 슬롯으로 전용 방벽 한 기를 설치할 수 있습니다. 무료 지급이 아닙니다."));
        cards.add(card("additional_payload", "추가 적재", SILVER, INCOME, null, false,
                "유틸 인컴 가격 +25%, 최대 체력과 지원량 +35%."));
        cards.add(card("twin_squadron", "쌍둥이 편대", SILVER, GENERAL, null, false,
                "같은 종류·같은 티어의 공격 타워가 정확히 두 기이면 두 타워가 함께 강화됩니다."));
        cards.add(card("tactical_designation_2", "전술 지명 II", GOLD, GENERAL, "TACTICAL_DESIGNATION", true,
                "지정 타워에 돌격(피해 증가) 또는 엄호(받는 피해 감소) 중 하나를 적용합니다."));
        cards.add(card("overheat_core", "과열 코어", GOLD, TRADE_OFF, null, false,
                "이번 전투에 예약한 타워의 피해가 증가합니다. 종료 후 최종 피해를 영구적으로 낮추는 열화를 얻습니다."));
        cards.add(card("pulse_relay_blueprint", "박동 중계기 설계도", GOLD, TOWER, null, false,
                "220다이아와 한 슬롯으로 중계기를 해금합니다. 연결 타워의 기본 공격 다섯 번마다 다음 공격을 강화합니다."));
        cards.add(card("frontline_specialization", "전열 분업", GOLD, GAME_CHANGER, null, false,
                "선봉은 피해 -25%·받는 피해 30% 감소, 포대는 피해 +30%·받는 피해 ×1.25."));
        cards.add(card("forecast_offensive", "예고 공세", GOLD, INCOME, null, false,
                "준비 단계에서 다음 적격 인컴 구매에 예고 계약을 적용하면 다음 웨이브에 30% 메아리를 보냅니다."));
        cards.add(card("support_performance", "지원 실적", GOLD, INCOME, null, false,
                "유틸 인컴이 다른 적격 몬스터 세 기를 유효 지원하면 영구 인컴 +2. 경기 합계 최대 +8."));
        cards.add(card("battlefield_mastery", "전장 숙련", GOLD, GENERAL, null, false,
                "영구 지정 타워가 적에게 충분한 실제 체력 피해를 받고 생존하면 피해와 최대 체력의 숙련을 쌓습니다."));
        cards.add(card("biased_armor", "편향 장갑", GOLD, TRADE_OFF, null, false,
                "선택한 물리·마법 피해는 ×0.75, 반대 유형은 ×1.35로 받습니다."));
        cards.add(card("cash_settlement", "현금 결제", GOLD, INCOME, null, false,
                "다음 적격 인컴 구매의 영구 인컴 증가를 포기하고 그 증가량의 세 배를 즉시 다이아로 받습니다."));
        cards.add(card("tactical_designation_3", "전술 지명 III", PRISMATIC, GENERAL, "TACTICAL_DESIGNATION", true,
                "지정 타워에 돌격(피해 증가) 또는 엄호(받는 피해 감소) 중 하나를 적용합니다."));
        cards.add(card("forbidden_blueprint", "금지된 설계도", PRISMATIC, TRADE_OFF, null, false,
                "승급권 두 장을 받습니다. 장당 최대 300다이아를 지원하지만 사용마다 이후 정기 지급에 ×0.90을 적용합니다."));
        cards.add(card("barrier_core_call", "방벽 핵심 호출", PRISMATIC, TOWER, null, false,
                "두 슬롯을 쓰는 방벽 핵심의 무료 배치권 한 장. 연결 타워가 받을 피해 25%를 대신 받습니다."));
        cards.add(card("low_pressure_high_yield", "저압 고수익", PRISMATIC, INCOME, null, false,
                "다음 적격 인컴의 몸체 능력치를 30% 낮추고 영구 인컴을 25% 더 받습니다. 라운드 보너스 최대 12."));
        for (int i = 1; i <= 3; i++) {
            cards.add(card("finishing_fire_" + i, "마무리 사격 " + roman(i), AugmentRarity.values()[i - 1],
                    GENERAL, "FINISHING_FIRE", false,
                    "체력이 절반 이하인 적에게 주 대상 기본 공격 피해 +" + new int[]{20, 35, 55}[i - 1] + "%."));
        }
        cards.add(card("independent_position", "독립 진지", SILVER, GENERAL, null, false,
                "웨이브 시작에 반경 4블록 안에 이웃 타워가 없는 공격 타워는 피해 +12%, 받는 피해 8% 감소."));
        cards.add(card("winning_barrage", "연승 포화", GOLD, GENERAL, null, false,
                "적격 적을 기본 공격으로 처치하면 다음 기본 공격 세 회의 피해 +30%. 다시 처치하면 세 회로 갱신."));
        cards.add(card("decisive_delivery", "결전 납품", GOLD, INCOME, null, false,
                "다음 표준 공격형 인컴의 영구 인컴 증가를 포기하고 체력 +60%, 기본 공격 +40%로 보냅니다."));
        cards.add(card("domino_fire", "도미노 사격", GOLD, GAME_CHANGER, null, false,
                "주 대상 기본 공격 처치의 초과 피해 60%를 반경 4블록의 다른 적 한 기에게 전달합니다. 원래 피해의 50% 상한."));
        cards.add(card("one_man_show", "원맨쇼", PRISMATIC, TRADE_OFF, null, false,
                "영구 주역 한 기는 피해 +100%, 최대 체력 +30%. 나머지 일반 타워의 피해는 영구적으로 20% 감소."));
        cards.add(card("wartime_economy", "전시 경제", PRISMATIC, INCOME, null, false,
                "이후 정기 다이아 지급 ×0.65. 일반 영구 타워 전체의 피해 +35%, 최대 체력 +20%. 취소할 수 없습니다."));
        cards.add(card("giant_hunter_call", "거인 사냥꾼 호출", PRISMATIC, TOWER, null, false,
                "한 슬롯의 무료 저격 타워 배치권. 3~9블록의 큰 적을 공격하며 가까운 적은 공격하지 못합니다."));
        cards.add(card("emergency_bell_blueprint", "응급 종탑 설계도", SILVER, TOWER, null, false,
                "120다이아·한 슬롯. 체력 40% 이하의 아군을 서로 다른 세 기까지 한 번씩 회복합니다."));
        cards.add(card("capacitor_post_blueprint", "축전 초소 설계도", GOLD, TOWER, null, false,
                "기당 140다이아·한 슬롯, 최대 두 기. 사거리 안에 적이 없을 때 충전해 다음 한 발에 방출합니다."));
        cards.add(card("ambush_workshop_blueprint", "매복 작업장 설계도", GOLD, TOWER, null, false,
                "220다이아·한 슬롯. 설치 방향 앞에 지뢰 세 개를 준비하고 매 웨이브 다시 장전합니다."));
        cards.add(card("starlight_cocoon_call", "별빛 고치 호출", PRISMATIC, TOWER, null, false,
                "두 슬롯의 무료 고치 배치권. 두 웨이브를 파괴 없이 끝내면 다음 준비 단계에 파수꾼으로 부화합니다."));
        cards.add(card("ordnance_factory_call", "군수공장 호출", PRISMATIC, TOWER, null, false,
                "한 슬롯의 무료 공장 배치권. 적격 공격형 인컴 실결제 100에메랄드마다 다음 전투 포탄 한 발, 최대 네 발."));
        for (AugmentRarity rarity : AugmentRarity.values()) {
            int tier = rarity.ordinal();
            String suffix = rarity.name().toLowerCase(java.util.Locale.ROOT);
            cards.add(reserve("reserve_diamonds_" + suffix, "예비 다이아 " + roman(tier + 1), rarity,
                    "RESERVE_DIAMONDS", "즉시 다이아 +" + new int[]{60, 120, 240}[tier] + "."));
            cards.add(reserve("reserve_income_" + suffix, "예비 인컴 " + roman(tier + 1), rarity,
                    "RESERVE_INCOME", "정기 인컴 +" + new int[]{10, 20, 40}[tier] + "."));
            cards.add(reserve("reserve_production_" + suffix, "예비 생산 " + roman(tier + 1), rarity,
                    "RESERVE_PRODUCTION", "기본 에메랄드 초당 생산 +" + new int[]{1, 2, 3}[tier] + "."));
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
