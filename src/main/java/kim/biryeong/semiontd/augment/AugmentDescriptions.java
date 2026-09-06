package kim.biryeong.semiontd.augment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.augment.AugmentTowers;

/** The dialog and web catalog render the same configured numbers. */
public final class AugmentDescriptions {
    private AugmentDescriptions() {}

    public static String describe(AugmentDefinition card, AugmentConfig config) {
        String id = card.id().substring(card.id().indexOf(':') + 1);
        Map<String, Double> values = config.parameters().get(card.id());
        if (id.startsWith("reserve_diamonds_")) {
            return "즉시 다이아 +" + n(values, "amount") + ". 후보가 부족할 때만 나오는 예비 보상입니다.";
        }
        if (id.startsWith("reserve_income_")) {
            return "정기 인컴 +" + n(values, "amount") + ". 기존 부채와 정기 지급 감소를 적용합니다.";
        }
        if (id.startsWith("reserve_production_")) {
            return "기본 에메랄드 초당 생산 +" + n(values, "amount") + ". 생산 업그레이드 상한과 별개이며 R25 생산 배율을 적용합니다.";
        }
        return switch (id) {
            case "tactical_designation_1", "tactical_designation_2", "tactical_designation_3" ->
                    "지정 타워 한 기의 모드를 고릅니다. 돌격: 최종 피해 +" + p(values, "damageBonus")
                            + ". 엄호: 받는 피해 " + p(values, "damageReduction") + " 감소. 준비 단계마다 한 번 변경할 수 있습니다.";
            case "triangle_formation" -> "웨이브 시작에 반경 " + n(values, "radius") + "블록 안의 다른 일반 영구 타워가 "
                    + n(values, "neighborCount") + "기 이상이면 최종 피해 +" + p(values, "damageBonus")
                    + ", 받는 피해 " + p(values, "damageReduction") + " 감소. 해당 웨이브 동안 유지합니다.";
            case "engagement_plan" -> "속전: 전투 첫 " + seconds(values, "transitionTicks") + "초의 최종 피해 +"
                    + p(values, "quickDamageBonus") + ". 지구전: 그 이후 최종 피해 +" + p(values, "longDamageBonus")
                    + ", 받는 피해 " + p(values, "longDamageReduction") + " 감소. 준비 단계마다 모드를 고릅니다.";
            case "emergency_loan" -> "현재 정기 지급액의 " + n(values, "advanceMultiplier") + "배, 최대 "
                    + n(values, "advanceCap") + "다이아를 즉시 받습니다. 총부채는 받은 금액 ×"
                    + ratio(values.get("debtMultiplier")) + "를 올림합니다. 다음 " + n(values, "repaymentCount")
                    + "회 지급에서 갚으며, 지급액이 부족하면 남은 부채를 이월합니다.";
            case "additional_payload" -> "준비 단계에 켜면 다음 유료 유틸 인컴의 비용 ×" + n(values, "costMultiplier")
                    + ", 최대 체력 ×" + n(values, "healthMultiplier") + ", 회복과 보호막 ×" + n(values, "supportMultiplier")
                    + ". 성공한 구매만 예약을 소비하며, 미사용 예약은 준비 종료 시 해제합니다.";
            case "twin_squadron" -> "같은 종류와 티어의 일반 영구 공격 타워가 정확히 두 기이면 최종 피해 +"
                    + p(values, "damageBonus") + ". 한 기이거나 세 기 이상이면 적용하지 않습니다. 웨이브 시작에 판정합니다.";
            case "overheat_core" -> "이번 준비 단계에 지정한 타워의 최종 피해 +" + p(values, "damageBonus")
                    + ". 웨이브 종료 후 열화 1스택을 얻고, 스택당 최종 피해 " + p(values, "penaltyPerStack")
                    + "가 영구 감소합니다. 최대 " + n(values, "maxStacks") + "스택이며, 매 웨이브 다시 예약해야 합니다.";
            case "frontline_specialization" -> "서로 다른 타워를 선봉과 포대로 지정합니다. 선봉: 최종 피해 -"
                    + p(values, "vanguardDamagePenalty") + ", 받는 피해 " + p(values, "vanguardDamageReduction")
                    + " 감소. 포대: 최종 피해 +" + p(values, "artilleryDamageBonus") + ", 받는 피해 ×"
                    + n(values, "artilleryIncomingMultiplier") + ". 한 기가 제거되면 그 웨이브에는 두 효과를 함께 끕니다.";
            case "forecast_offensive" -> "다음 유료 공격형 인컴을 지금 결제하고 다음 웨이브에 원본과 "
                    + p(values, "echoRatio") + " 메아리를 보냅니다. 원본 인컴 증가도 출현 때 반영합니다. 출현 전 경기 종료 시 환불하지 않습니다.";
            case "support_performance" -> "유료 유틸 한 기가 서로 다른 적격 유닛 " + n(values, "targetCount")
                    + "기를 유효 지원하면 영구 인컴 +" + n(values, "incomeBonus") + ". 준비 단계당 한 번, 경기 합계 최대 +"
                    + n(values, "matchIncomeCap") + ". 실제 회복과 새 보호막 적용을 세며, 초과 회복과 지속 시간 갱신은 세지 않습니다.";
            case "battlefield_mastery" -> "지정 타워가 적에게 웨이브 시작 최대 체력의 " + p(values, "damageThreshold")
                    + " 이상에 해당하는 체력 피해를 받고 생존하면 최종 피해와 최대 체력 +" + p(values, "bonusPerStack")
                    + "를 쌓습니다. 최대 " + n(values, "maxStacks") + "회이며, 대상을 바꿀 수 없고 제거하면 쌓은 효과를 잃습니다.";
            case "biased_armor" -> "적 몬스터의 직접 공격 중 선택한 물리 또는 마법 피해를 ×" + n(values, "selectedMultiplier")
                    + ", 반대 유형 피해를 ×" + n(values, "oppositeMultiplier") + "로 받습니다. 준비 단계마다 유형을 고릅니다.";
            case "cash_settlement" -> "다음 적격 유료 인컴의 영구 인컴 증가를 포기하고, 그 증가량의 "
                    + n(values, "diamondMultiplier") + "배를 즉시 다이아로 받습니다. 성공한 구매만 예약을 소비합니다.";
            case "forbidden_blueprint" -> "장당 최대 " + n(values, "ticketValue") + "다이아를 지원하는 승급권 "
                    + n(values, "ticketCount") + "장을 받습니다. 사용할 때마다 이후 정기 지급액에 ×"
                    + n(values, "payoutMultiplier") + "를 적용합니다. 같은 타워에는 한 장만 쓰며, 이번 준비 종료 시 남은 이용권이 사라집니다.";
            case "low_pressure_high_yield" -> "다음 적격 유료 인컴의 몸체 능력치 ×" + n(values, "bodyMultiplier")
                    + ", 영구 인컴 증가량 +" + p(values, "bonusRatio") + ". 라운드 추가 인컴은 최대 "
                    + n(values, "roundBonusCap") + "입니다.";
            case "finishing_fire_1", "finishing_fire_2", "finishing_fire_3" ->
                    "체력이 절반 이하인 적에게 주 대상 기본 공격 피해 +" + p(values, "damageBonus") + ". 추가 공격과 범위 피해에는 적용하지 않습니다.";
            case "independent_position" -> "웨이브 시작에 반경 " + n(values, "radius")
                    + "블록 안에 이웃 타워가 없는 일반 영구 공격 타워는 최종 피해 +" + p(values, "damageBonus")
                    + ", 받는 피해 " + p(values, "damageReduction") + " 감소.";
            case "winning_barrage" -> "적격 적을 기본 공격으로 처치하면 다음 기본 공격 " + n(values, "charges")
                    + "회의 피해 +" + p(values, "damageBonus") + ". 다시 처치하면 남은 횟수를 " + n(values, "charges") + "회로 갱신합니다.";
            case "decisive_delivery" -> "다음 유료 표준 공격형 인컴의 영구 인컴 증가를 포기하고, 최대 체력 ×"
                    + n(values, "healthMultiplier") + ", 기본 공격 ×" + n(values, "attackMultiplier") + "로 보냅니다.";
            case "domino_fire" -> "주 대상 기본 공격 처치의 초과 피해 " + p(values, "overkillRatio")
                    + "를 반경 " + n(values, "radius") + "블록의 다른 적 한 기에게 전달합니다. 원래 피해의 "
                    + p(values, "damageCapRatio") + "가 상한이며, 전달 피해는 다른 증강을 발동하지 않습니다.";
            case "one_man_show" -> "영구 주역 한 기의 최종 피해 +" + p(values, "damageBonus") + ", 최대 체력 +"
                    + p(values, "maxHealthBonus") + ". 나머지 일반 영구 타워의 최종 피해는 " + p(values, "otherDamagePenalty")
                    + " 감소합니다. 주역을 바꿀 수 없고, 주역이 제거돼도 대가는 남습니다.";
            case "wartime_economy" -> "이후 정기 다이아 지급액 ×" + n(values, "payoutMultiplier")
                    + ". 일반 영구 타워의 최종 피해 +" + p(values, "damageBonus") + ", 최대 체력 +"
                    + p(values, "maxHealthBonus") + ". 취소할 수 없습니다.";
            case "folding_barricade_blueprint" -> offer(AugmentTowers.FOLDING_BARRICADE)
                    + "같은 거리에서 공격 가능한 다른 타워보다 먼저 공격받습니다. 직접 공격하지 않습니다.";
            case "pulse_relay_blueprint" -> offer(AugmentTowers.PULSE_RELAY)
                    + "서로 다른 두 공격 타워를 연결합니다. 한쪽이 기본 공격 " + n(values, "attacksPerCharge")
                    + "회를 해결하면 반대편의 다음 기본 공격에 " + p(values, "chargedDamageRatio") + "의 추가 피해를 줍니다.";
            case "barrier_core_call" -> offer(AugmentTowers.BARRIER_CORE)
                    + "연결 타워 최대 세 기가 받을 피해의 " + p(values, "redirectRatio")
                    + "를 대신 받습니다. 전투 중 회복, 판매와 재배치를 할 수 없습니다.";
            case "giant_hunter_call" -> offer(AugmentTowers.GIANT_HUNTER)
                    + n(values, "minimumRange") + "블록보다 가까운 적은 공격하지 못합니다. 기본 공격에 적 최대 체력의 "
                    + p(values, "maxHealthDamageRatio") + "를 더하며, 자연 웨이브 보스에는 " + p(values, "bossMaxHealthDamageRatio") + "를 적용합니다.";
            case "emergency_bell_blueprint" -> offer(AugmentTowers.EMERGENCY_BELL)
                    + "체력 " + p(values, "healthThreshold") + " 이하의 아군을 최대 체력의 " + p(values, "healRatio")
                    + ", 최대 " + n(values, "healCap") + "만큼 회복합니다. 웨이브마다 서로 다른 " + n(values, "maxHeals") + "기에게 한 번씩 적용합니다.";
            case "capacitor_post_blueprint" -> offer(AugmentTowers.CAPACITOR_POST)
                    + "사거리 안에 적이 없는 동안 " + seconds(values, "chargeTicks") + "초마다 충전합니다. 최대 "
                    + n(values, "maxCharges") + "개를 모으며, 다음 기본 공격에 충전당 " + n(values, "chargeDamage") + "피해를 더합니다.";
            case "ambush_workshop_blueprint" -> offer(AugmentTowers.AMBUSH_WORKSHOP)
                    + "고른 방향 앞에 지뢰 세 개를 준비합니다. 반경 " + n(values, "triggerRadius")
                    + "블록에 적이 들어오면 반경 " + n(values, "damageRadius") + "블록의 최대 " + n(values, "mineTargets")
                    + "기에게 " + n(values, "mineDamage") + "피해를 줍니다. 다음 웨이브에 다시 장전합니다.";
            case "starlight_cocoon_call" -> offer(AugmentTowers.STARLIGHT_COCOON)
                    + "파괴 없이 " + n(values, "hatchWaves") + "웨이브를 끝내면 다음 준비 단계에 부화합니다. 부화 후 체력 "
                    + n(values, "hatchedHealth") + ", 사거리 " + n(values, "hatchedRange") + "블록, 공격력 "
                    + n(values, "hatchedDamage") + ", 공격 간격 " + seconds(values, "hatchedIntervalTicks") + "초.";
            case "ordnance_factory_call" -> offer(AugmentTowers.ORDNANCE_FACTORY)
                    + "적격 공격형 인컴 실결제 " + n(values, "emeraldPerShell") + "에메랄드마다 다음 전투 포탄 한 발, 최대 "
                    + n(values, "maxShells") + "발. 포탄은 반경 " + n(values, "shellRadius") + "블록의 최대 "
                    + n(values, "shellTargets") + "기에게 " + n(values, "shellDamage") + "피해를 줍니다.";
            default -> throw new IllegalArgumentException("Missing augment description: " + card.id());
        };
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
