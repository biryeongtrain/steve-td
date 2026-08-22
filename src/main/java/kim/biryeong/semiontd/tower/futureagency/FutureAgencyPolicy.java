package kim.biryeong.semiontd.tower.futureagency;

import java.util.Arrays;
import java.util.Optional;

public enum FutureAgencyPolicy {
    AGENCY_TACTICS("agency_tactics", "기관 전술 교리", "모든 요원 피해 +10%", 3, 0.10),
    COMPOSITE_ARMOR("composite_armor", "복합 방탄복 지급", "모든 요원 최대 HP +10%", 3, 0.10),
    REACTION_TRAINING("reaction_training", "반응속도 훈련", "모든 요원 공속 +7%", 3, 0.07),
    FORWARD_DEPLOYMENT("forward_deployment", "전진 배치 프로토콜", "요원 이동속도 +10%", 3, 0.10),
    EVAC_MEDICS("evac_medics", "긴급 철수 의료반", "생존 이월 시 최대 HP 8% 회복", 3, 0.08),
    WAVE_ANALYSIS("wave_analysis", "웨이브 전술분석실", "자연 웨이브 피해 +8%", 3, 0.08),
    INCOME_INTERCEPTION("income_interception", "인컴 요격실", "인컴 피해 +8%", 3, 0.08),
    RANGED_ARMOR("ranged_armor", "원거리 방호복", "원거리 피해 -10%", 2, 0.10),
    MELEE_TRAINING("melee_training", "근접 대응훈련", "근접 피해 -10%", 2, 0.10),
    CENTRAL_BATTLE("central_battle", "중앙 결전 계획", "구원 후 중앙 방어 피해 +15%", 1, 0.15),
    ANOMALY_DEPARTMENT("anomaly_department", "특이괴물대책과 창설", "워든 피해 -25%", 1, 0.25),
    TANK_DEPARTMENT("tank_department", "중장갑 대책과", "TANK 피해 +15%", 1, 0.15),
    SIEGE_DEPARTMENT("siege_department", "공성체 대책과", "SIEGE 피해 +15%", 1, 0.15),
    SWARM_DEPARTMENT("swarm_department", "군집체 대책과", "SWARM·RUSH 피해 +15%", 1, 0.15),
    PROFESSIONAL_AGENTS("professional_agents", "전문요원훈련과 창설", "모든 요원 피해 감소 8%", 1, 0.08),
    PRECISION_FIRE("precision_fire", "정밀사격 교범", "전투 요원 피해 +15%", 3, 0.15),
    FAST_RELOAD("fast_reload", "고속 재장전", "전투 요원 공속 +12%", 3, 0.12),
    LONG_RANGE_OPTICS("long_range_optics", "장거리 광학장비", "전투 요원 사거리 +1", 2, 1.0),
    EXECUTION_AUTHORITY("execution_authority", "처형 승인", "체력 30% 이하 피해 +20%", 1, 0.20),
    HIGH_VALUE_TARGET("high_value_target", "고가치 표적 지정", "최대 HP 500 이상 피해 +15%", 1, 0.15),
    AREA_SUPPRESSION("area_suppression", "광역 제압탄", "제압 반경 +0.5", 3, 0.50),
    RESTRAINT_ROUNDS("restraint_rounds", "강화 구속탄", "제압 이동·공격속도 감소 +5%p", 3, 0.05),
    MULTI_TARGET("multi_target", "다중 표적 교리", "제압 대상 +2", 3, 2.0),
    DISPERSION_WARHEAD("dispersion_warhead", "확산 탄두", "제압 범위 피해 +7.5%p", 2, 0.075),
    DENSE_CONTROL("dense_control", "밀집 진압 절차", "주변 적당 피해 +1.5%, 최대 +30%", 1, 0.015),
    CERAMIC_PLATES("ceramic_plates", "세라믹 장갑판", "방호 요원 최대 HP +15%", 3, 0.15),
    SHOCK_ABSORPTION("shock_absorption", "충격 흡수복", "방호 피해 감소 +5%p", 3, 0.05),
    FORCED_TAUNT("forced_taunt", "강제 유인 신호기", "방호 어그로 +25", 2, 25.0),
    ESCORT_FORMATION("escort_formation", "호위 대형", "주변 요원 피해 감소 8%", 1, 0.08),
    LAST_BARRIER("last_barrier", "최후 방벽", "체력 35% 이하 피해 감소 +15%p", 1, 0.15);

    private final String id;
    private final String displayName;
    private final String description;
    private final int maxStacks;
    private final double defaultValue;

    FutureAgencyPolicy(String id, String displayName, String description, int maxStacks, double defaultValue) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.maxStacks = maxStacks;
        this.defaultValue = defaultValue;
    }

    public String id() {return id;}
    public String upgradeId() {return "policy_" + id;}
    public String displayName() {return displayName;}
    public String description() {return description;}
    public int maxStacks() {return maxStacks;}
    public double defaultValue() {return defaultValue;}

    public String configuredDescription() {
        double value = FutureAgencyBalance.policy(this);
        return switch (this) {
            case AGENCY_TACTICS -> "모든 요원 피해 +" + percent(value);
            case COMPOSITE_ARMOR -> "모든 요원 최대 HP +" + percent(value);
            case REACTION_TRAINING -> "모든 요원 공속 +" + percent(value);
            case FORWARD_DEPLOYMENT -> "요원 이동속도 +" + percent(value);
            case EVAC_MEDICS -> "생존 이월 시 최대 HP의 " + percent(value) + " 회복";
            case WAVE_ANALYSIS -> "자연 웨이브 피해 +" + percent(value);
            case INCOME_INTERCEPTION -> "실제 인컴 피해 +" + percent(value);
            case RANGED_ARMOR -> "원거리 공격 피해 -" + percent(value);
            case MELEE_TRAINING -> "근접 공격 피해 -" + percent(value);
            case CENTRAL_BATTLE -> "구원 후 중앙 방어 피해 +" + percent(value);
            case ANOMALY_DEPARTMENT -> "워든에게 받는 피해 -" + percent(value);
            case TANK_DEPARTMENT -> "TANK 대상 피해 +" + percent(value);
            case SIEGE_DEPARTMENT -> "SIEGE 대상 피해 +" + percent(value);
            case SWARM_DEPARTMENT -> "SWARM·RUSH 대상 피해 +" + percent(value);
            case PROFESSIONAL_AGENTS -> "모든 요원이 받는 피해 -" + percent(value);
            case PRECISION_FIRE -> "전투 요원 피해 +" + percent(value);
            case FAST_RELOAD -> "전투 요원 공속 +" + percent(value);
            case LONG_RANGE_OPTICS -> "전투 요원 사거리 +" + number(value);
            case EXECUTION_AUTHORITY -> "체력 30% 이하 대상 피해 +" + percent(value);
            case HIGH_VALUE_TARGET -> "최대 HP 500 이상 대상 피해 +" + percent(value);
            case AREA_SUPPRESSION -> "제압 반경 +" + number(value);
            case RESTRAINT_ROUNDS -> "제압 이동·공격속도 감소율 +" + percent(value) + "p";
            case MULTI_TARGET -> "제압 최대 대상 +" + number(value);
            case DISPERSION_WARHEAD -> "제압 범위 피해 비율 +" + percent(value) + "p";
            case DENSE_CONTROL -> "주변 적 1기당 피해 +" + percent(value)
                    + ", 최대 +" + percent(FutureAgencyBalance.suppressionDenseCap());
            case CERAMIC_PLATES -> "방호 요원 최대 HP +" + percent(value);
            case SHOCK_ABSORPTION -> "방호 요원 피해 감소 +" + percent(value) + "p";
            case FORCED_TAUNT -> "방호 요원 어그로 +" + number(value);
            case ESCORT_FORMATION -> "주변 다른 요원이 받는 피해 -" + percent(value);
            case LAST_BARRIER -> "방호 요원 체력 35% 이하 피해 감소 +" + percent(value) + "p";
        };
    }

    public static Optional<FutureAgencyPolicy> fromUpgradeId(String id) {
        return Arrays.stream(values()).filter(value -> value.upgradeId().equals(id)).findFirst();
    }

    private static String percent(double value) {
        return Math.round(value * 1000.0) / 10.0 + "%";
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value))
                : Double.toString(Math.round(value * 100.0) / 100.0);
    }
}
