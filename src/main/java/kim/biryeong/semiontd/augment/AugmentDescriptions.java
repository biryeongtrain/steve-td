package kim.biryeong.semiontd.augment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.regex.Pattern;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.augment.AugmentTowers;

/** The dialog and web catalog render the same configured numbers. */
public final class AugmentDescriptions {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)(?::(percent|seconds))?}");
    private AugmentDescriptions() {}

    public static String describe(AugmentDefinition card, AugmentConfig config) {
        String effect = AugmentCatalog.effectId(card.id());
        String id = effect.substring(effect.indexOf(':') + 1);
        String mode = AugmentCatalog.fixedMode(card.id());
        Map<String, Double> values = config.parametersFor(card.id());
        if (card.requiredJobId() != null) {
            return renderTemplate(card.description(), values);
        }
        if (id.startsWith("reserve_diamonds_")) {
            return "즉시 다이아 +" + n(values, "amount") + ".";
        }
        if (id.startsWith("reserve_income_")) {
            return "정기 인컴 +" + n(values, "amount") + ".";
        }
        if (id.startsWith("reserve_production_")) {
            return "기본 에메랄드 초당 생산 +" + n(values, "amount") + ". 생산 업그레이드 상한과 별개이며 R25 생산 배율을 적용합니다.";
        }
        String description = switch (id) {
            case "tactical_designation_1", "tactical_designation_2", "tactical_designation_3" ->
                    mode.equals("ASSAULT") ? "지정 타워 한 기의 최종 피해 +" + p(values, "damageBonus") + "."
                            : mode.equals("COVER") ? "지정 타워 한 기의 받는 피해 " + p(values, "damageReduction") + " 감소."
                            : "지정 타워 한 기의 모드를 고릅니다. 돌격: 최종 피해 +" + p(values, "damageBonus")
                            + ". 엄호: 받는 피해 " + p(values, "damageReduction") + " 감소.";
            case "triangle_formation" -> "웨이브 시작에 반경 " + n(values, "radius") + "블록 안의 다른 일반 영구 타워가 "
                    + n(values, "neighborCount") + "기 이상이면 최종 피해 +" + p(values, "damageBonus")
                    + ", 받는 피해 " + p(values, "damageReduction") + " 감소.";
            case "engagement_plan" -> mode.equals("QUICK")
                    ? "전투 시작 후 " + seconds(values, "transitionTicks") + "초 동안 일반 영구 타워의 최종 피해 +" + p(values, "quickDamageBonus") + "."
                    : mode.equals("LONG") ? "전투 시작 " + seconds(values, "transitionTicks") + "초 뒤부터 일반 영구 타워의 최종 피해 +"
                    + p(values, "longDamageBonus") + ", 받는 피해 " + p(values, "longDamageReduction") + " 감소."
                    : "속전: 전투 첫 " + seconds(values, "transitionTicks") + "초의 최종 피해 +" + p(values, "quickDamageBonus")
                    + ". 지구전: 그 이후 최종 피해 +" + p(values, "longDamageBonus") + ", 받는 피해 " + p(values, "longDamageReduction") + " 감소.";
            case "emergency_loan" -> "현재 정기 지급액의 " + n(values, "advanceMultiplier") + "배, 최대 "
                    + n(values, "advanceCap") + "다이아 대출. 받은 금액의 " + ratio(values.get("debtMultiplier"))
                    + "배를 다음 " + n(values, "repaymentCount") + "회 정기 지급에서 상환합니다.";
            case "additional_payload" -> "다음 유틸 인컴의 비용 ×" + n(values, "costMultiplier")
                    + ", 최대 체력 ×" + n(values, "healthMultiplier") + ", 회복과 보호막 ×" + n(values, "supportMultiplier")
                    + ".";
            case "twin_squadron" -> "웨이브 시작에 같은 종류와 티어의 일반 영구 공격 타워가 정확히 두 기이면 최종 피해 +"
                    + p(values, "damageBonus") + ".";
            case "overheat_core" -> "이번 준비 단계에 지정한 타워의 최종 피해 +" + p(values, "damageBonus")
                    + ". 사용 후 최종 피해 " + p(values, "penaltyPerStack")
                    + "가 영구 감소하며 최대 " + n(values, "maxStacks") + "회 중첩합니다.";
            case "frontline_specialization" -> "서로 다른 타워를 선봉과 포대로 지정합니다. 선봉: 최종 피해 -"
                    + p(values, "vanguardDamagePenalty") + ", 받는 피해 " + p(values, "vanguardDamageReduction")
                    + " 감소. 포대: 최종 피해 +" + p(values, "artilleryDamageBonus") + ", 받는 피해 ×"
                    + n(values, "artilleryIncomingMultiplier") + ". 한 기를 잃으면 두 효과 모두 해제됩니다.";
            case "forecast_offensive" -> "다음 인컴을 한 웨이브 늦춰 보내고, 능력치 "
                    + p(values, "echoRatio") + "의 복제본 한 기를 추가합니다. 비용은 즉시 결제하고 인컴은 출현할 때 증가합니다.";
            case "support_performance" -> "유틸 인컴 한 기가 서로 다른 유닛 " + n(values, "targetCount")
                    + "기를 회복하거나 보호막을 주면 정기 인컴 +" + n(values, "incomeBonus") + ". 준비 단계당 한 번, 경기 합계 최대 +"
                    + n(values, "matchIncomeCap") + ".";
            case "battlefield_mastery" -> "지정 타워가 적에게 웨이브 시작 최대 체력의 " + p(values, "damageThreshold")
                    + " 이상에 해당하는 체력 피해를 받고 생존하면 최종 피해와 최대 체력 +" + p(values, "bonusPerStack")
                    + ". 최대 " + n(values, "maxStacks") + "회 중첩. 대상을 바꾸거나 해제하면 중첩이 초기화됩니다.";
            case "biased_armor" -> "일반 영구 타워가 적의 직접 공격으로 받는 "
                    + (mode.equals("PHYSICAL") ? "물리" : mode.equals("MAGIC") ? "마법" : "선택 유형") + " 피해 ×" + n(values, "selectedMultiplier")
                    + ", " + (mode.equals("PHYSICAL") ? "마법" : mode.equals("MAGIC") ? "물리" : "반대 유형")
                    + " 피해 ×" + n(values, "oppositeMultiplier") + ".";
            case "cash_settlement" -> "다음 인컴의 영구 인컴 증가를 포기하고, 그 증가량의 "
                    + n(values, "diamondMultiplier") + "배를 즉시 다이아로 받습니다.";
            case "forbidden_blueprint" -> "장당 최대 " + n(values, "ticketValue") + "다이아를 지원하는 승급권 "
                    + n(values, "ticketCount") + "장을 받습니다. 사용할 때마다 이후 정기 지급액에 ×"
                    + n(values, "payoutMultiplier") + "를 적용합니다. 같은 타워에는 한 장만 쓰며, 이번 준비 종료 시 남은 이용권이 사라집니다.";
            case "low_pressure_high_yield" -> "다음 인컴의 몸체 능력치 ×" + n(values, "bodyMultiplier")
                    + ", 영구 인컴 증가량 +" + p(values, "bonusRatio") + ". 라운드 추가 인컴은 최대 "
                    + n(values, "roundBonusCap") + "입니다.";
            case "finishing_fire_1", "finishing_fire_2", "finishing_fire_3" ->
                    "체력이 절반 이하인 적에게 주 대상 기본 공격 피해 +" + p(values, "damageBonus") + ". 추가 공격과 범위 피해에는 적용하지 않습니다.";
            case "independent_position" -> "웨이브 시작에 반경 " + n(values, "radius")
                    + "블록 안에 이웃 타워가 없는 일반 영구 공격 타워는 최종 피해 +" + p(values, "damageBonus")
                    + ", 받는 피해 " + p(values, "damageReduction") + " 감소.";
            case "winning_barrage" -> "적격 적을 기본 공격으로 처치하면 다음 기본 공격 " + n(values, "charges")
                    + "회의 피해 +" + p(values, "damageBonus") + ". 다시 처치하면 남은 횟수를 " + n(values, "charges") + "회로 갱신합니다.";
            case "decisive_delivery" -> "다음 인컴의 영구 인컴 증가를 포기하고, 최대 체력 ×"
                    + n(values, "healthMultiplier") + ", 기본 공격 ×" + n(values, "attackMultiplier") + "로 보냅니다.";
            case "domino_fire" -> "주 대상 기본 공격 처치의 초과 피해 " + p(values, "overkillRatio")
                    + "를 반경 " + n(values, "radius") + "블록의 다른 적 한 기에게 전달합니다. 원래 피해의 "
                    + p(values, "damageCapRatio") + "가 상한이며, 전달 피해는 다른 증강을 발동하지 않습니다.";
            case "one_man_show" -> "지정한 주역 한 기의 최종 피해 +" + p(values, "damageBonus") + ", 최대 체력 +"
                    + p(values, "maxHealthBonus") + ". 나머지 일반 영구 타워의 최종 피해는 " + p(values, "otherDamagePenalty")
                    + " 감소합니다. 주역을 해제해도 나머지 타워의 피해 감소는 유지됩니다.";
            case "wartime_economy" -> "이후 정기 다이아 지급액 ×" + n(values, "payoutMultiplier")
                    + ". 일반 영구 타워의 최종 피해 +" + p(values, "damageBonus") + ", 최대 체력 +"
                    + p(values, "maxHealthBonus") + ".";
            case "folding_barricade_blueprint" -> offer(AugmentTowers.FOLDING_BARRICADE)
                    + "한 번에 받는 피해 최대 " + n(values, "damagePerHitCap") + " · 회복 불가. "
                    + "같은 거리에서 공격 가능한 다른 타워보다 먼저 공격받습니다. 직접 공격하지 않습니다.";
            case "pulse_relay_blueprint" -> offer(AugmentTowers.PULSE_RELAY)
                    + "서로 다른 두 공격 타워를 연결합니다. 한쪽이 기본 공격 " + n(values, "attacksPerCharge")
                    + "회를 명중시키면 반대편의 다음 기본 공격에 추가 피해 " + p(values, "chargedDamageRatio") + ".";
            case "barrier_core_call" -> offer(AugmentTowers.BARRIER_CORE)
                    + "연결 타워 최대 세 기가 받을 피해의 " + p(values, "redirectRatio")
                    + "를 대신 받습니다. 전투 중 회복, 판매와 재배치를 할 수 없습니다.";
            case "giant_hunter_call" -> offer(AugmentTowers.GIANT_HUNTER)
                    + n(values, "minimumRange") + "블록보다 가까운 적은 공격하지 못합니다. 기본 공격 시 추가 피해: 적 최대 체력의 "
                    + p(values, "maxHealthDamageRatio") + "(자연 웨이브 보스 " + p(values, "bossMaxHealthDamageRatio") + ").";
            case "emergency_bell_blueprint" -> offer(AugmentTowers.EMERGENCY_BELL)
                    + "체력 " + p(values, "healthThreshold") + " 이하의 아군을 최대 체력의 " + p(values, "healRatio")
                    + ", 최대 " + n(values, "healCap") + "만큼 회복합니다. 웨이브마다 서로 다른 " + n(values, "maxHeals") + "기에게 한 번씩 적용합니다.";
            case "capacitor_post_blueprint" -> offer(AugmentTowers.CAPACITOR_POST)
                    + "사거리 안에 적이 없는 동안 " + seconds(values, "chargeTicks") + "초마다 충전합니다. 최대 "
                    + n(values, "maxCharges") + "개를 모으며, 다음 기본 공격에 충전당 추가 피해 " + n(values, "chargeDamage") + ".";
            case "ambush_workshop_blueprint" -> offer(AugmentTowers.AMBUSH_WORKSHOP)
                    + "고른 방향 앞에 지뢰 세 개를 준비합니다. 반경 " + n(values, "triggerRadius")
                    + "블록에 적이 들어오면 반경 " + n(values, "damageRadius") + "블록의 최대 " + n(values, "mineTargets")
                    + "기에게 " + n(values, "mineDamage") + "피해를 줍니다. 다음 웨이브에 다시 장전합니다.";
            case "starlight_cocoon_call" -> offer(AugmentTowers.STARLIGHT_COCOON)
                    + "파괴 없이 " + n(values, "hatchWaves") + "웨이브를 끝내면 다음 준비 단계에 부화합니다. 부화 후 체력 "
                    + n(values, "hatchedHealth") + ", 사거리 " + n(values, "hatchedRange") + "블록, 공격력 "
                    + n(values, "hatchedDamage") + ", 공격 간격 " + seconds(values, "hatchedIntervalTicks") + "초.";
            case "ordnance_factory_call" -> offer(AugmentTowers.ORDNANCE_FACTORY)
                    + "인컴 구매에 쓴 " + n(values, "emeraldPerShell") + "에메랄드마다 다음 전투 포탄 한 발, 최대 "
                    + n(values, "maxShells") + "발. 포탄은 반경 " + n(values, "shellRadius") + "블록의 최대 "
                    + n(values, "shellTargets") + "기에게 " + n(values, "shellDamage") + "피해를 줍니다.";
            default -> throw new IllegalArgumentException("Missing augment description: " + card.id());
        };
        if (card.towerAugment()) {
            description += " R15/R25 자동 강화.";
        }
        return description;
    }

    static String renderTemplate(String template, Map<String, Double> values) {
        var matcher = PLACEHOLDER.matcher(template);
        StringBuilder description = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!values.containsKey(key)) {
                throw new IllegalArgumentException("Unknown augment description parameter: " + key);
            }
            String format = matcher.group(2);
            String rendered = "percent".equals(format) ? p(values, key)
                    : "seconds".equals(format) ? seconds(values, key) : n(values, key);
            matcher.appendReplacement(description, rendered);
        }
        matcher.appendTail(description);
        if (description.indexOf("{") >= 0 || description.indexOf("}") >= 0) {
            throw new IllegalArgumentException("Unknown augment description placeholder: " + template);
        }
        return description.toString();
    }

    private static String offer(TowerType base) {
        TowerType type = TowerBalanceRuntime.resolve(base);
        return (AugmentTowers.isFreeCall(type) ? "무료 배치권 한 장. " : "배치 비용 " + type.mineralCost() + "다이아. ")
                + "슬롯 " + AugmentTowers.slots(type) + "칸, 동시 설치 " + AugmentTowers.placementLimit(type)
                + "기, 체력 " + number(type.maxHealth()) + ". ";
    }

    private static String n(Map<String, Double> values, String key) {return number(values.get(key));}
    private static String p(Map<String, Double> values, String key) {return number(values.get(key) * 100) + "%";}
    private static String seconds(Map<String, Double> values, String key) {return number(values.get(key) / 20);}
    private static String ratio(double value) {return value == 4.0 / 3.0 ? "4/3" : number(value);}
    private static String number(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
