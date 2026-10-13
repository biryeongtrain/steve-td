package kim.biryeong.semiontd.trait;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.TraitBalanceRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class BuiltInTraits {
    public static final Identifier NONE_ID = id("none");
    public static final Identifier MOBILIZATION_GRANT_ID = id("mobilization_grant");
    public static final Identifier CLEAN_LANE_BONUS_ID = id("clean_lane_bonus");
    public static final Identifier RAPID_DEPLOYMENT_ID = id("rapid_deployment");
    public static final Identifier BERSERK_SUMMONS_ID = id("berserk_summons");
    public static final Identifier INTERCEPTION_DOCTRINE_ID = id("interception_doctrine");
    public static final Identifier OPENING_SALVO_ID = id("opening_salvo");
    public static final Identifier WAVEBREAKER_DOCTRINE_ID = id("wavebreaker_doctrine");
    public static final Identifier FORTITUDE_ID = id("fortitude");
    public static final Identifier DOUBLE_EDGED_SWORD_ID = id("double_edged_sword");
    public static final Identifier STRENGTH_IN_NUMBERS_ID = id("strength_in_numbers");
    public static final Identifier DIVERSITY_ID = id("diversity");
    public static final Identifier SUPPLY_DEPOT_ID = id("supply_depot");
    public static final Identifier TRANSCENDENCE_ID = id("transcendence");
    public static final Identifier WEEKLY_HOLIDAY_PAY_ID = id("weekly_holiday_pay");
    public static final Identifier RUTHLESS_ID = id("ruthless");
    public static final Identifier IGNITE_ID = id("ignite");
    public static final Identifier GIANT_SLAYER_ID = id("giant_slayer");
    public static final Identifier FINISHING_BLOW_ID = id("finishing_blow");
    public static final Identifier PERFORMANCE_BONUS_ID = id("performance_bonus");

    private static boolean registered;

    private BuiltInTraits() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }

        register(NONE_ID, 0, "선택 안 함",
                "특성을 사용하지 않습니다.");
        register(MOBILIZATION_GRANT_ID, 1, "금수저",
                "경기 시작 시 다이아를 추가로 받습니다.");
        register(CLEAN_LANE_BONUS_ID, 1, "완막 보너스",
                "누수 없이 라운드를 막으면 다음 준비 단계에 다이아를 받습니다.");
        register(RAPID_DEPLOYMENT_ID, 1, "신속 배치",
                "한 번 이상 웨이브를 치른 타워의 판매 환수율을 높입니다.");
        register(BERSERK_SUMMONS_ID, 1, "광폭화",
                "내가 보낸 인컴 유닛의 공격력과 공격 속도를 높입니다.");
        register(INTERCEPTION_DOCTRINE_ID, 1, "인컴 학살자",
                "내 타워가 상대 인컴 유닛에게 입히는 피해를 높입니다.");
        register(OPENING_SALVO_ID, 1, "스피드러너",
                "웨이브 시작 시 살아 있던 내 타워의 공격 속도를 높입니다.");
        register(WAVEBREAKER_DOCTRINE_ID, 1, "웨이브 학살자",
                "내 타워가 기본 웨이브 몬스터에게 입히는 피해를 높입니다.");
        register(FORTITUDE_ID, 1, "완강함",
                "내 모든 타워의 최대 체력을 높입니다.",
                "하이퍼 캐리형의 핵심 전투체는 별도 수치를 사용합니다.");
        register(DOUBLE_EDGED_SWORD_ID, 1, "양날의 검",
                "내 타워의 최종 피해가 증가하지만 받는 최종 피해도 증가합니다.");
        register(STRENGTH_IN_NUMBERS_ID, 1, "무리의 힘",
                "살아 있는 같은 종류 타워 수에 따라 입히는 피해가 증가합니다.",
                "공격하는 타워 자신도 수에 포함합니다.");
        register(DIVERSITY_ID, 1, "다양성",
                "살아 있는 서로 다른 타워 종류 수에 따라 모든 타워의 피해가 증가합니다.");
        register(SUPPLY_DEPOT_ID, 1, "보급고",
                "최대 타워 수가 증가합니다.");
        register(TRANSCENDENCE_ID, 1, "초월",
                "방어 시작 후 일정 시간이 지나면 살아 있는 내 타워의 피해가 방어 종료까지 증가합니다.");
        register(WEEKLY_HOLIDAY_PAY_ID, 1, "주휴수당",
                "경기 시작 후 3분마다 현재 수입에 비례한 다이아를 받습니다.");
        register(RUTHLESS_ID, 1, "비열",
                "디버프가 걸린 적에게 입히는 피해가 증가합니다.");
        register(IGNITE_ID, 1, "점화",
                "기본 공격이 적을 4초간 점화해 1초마다 마법 피해를 줍니다.",
                "점화는 디버프로 취급되며 반복 공격 시 더 강한 점화를 유지합니다.",
                "마법 피해를 주는 능력에는 점화가 적용되지 않습니다.");
        register(GIANT_SLAYER_ID, 1, "거인학살자",
                "현재 체력이 일정 수치 이상인 적에게 입히는 피해가 증가합니다.");
        register(FINISHING_BLOW_ID, 1, "마무리타격",
                "현재 체력이 최대 체력의 일정 비율 이하인 적에게 입히는 피해가 증가합니다.");
        register(PERFORMANCE_BONUS_ID, 1, "성과급",
                "2라운드부터 팀 전체의 현재 수입 합계에 비례한 에메랄드를 받습니다.",
                "자신의 수입도 합산하며 라운드별 에메랄드 상한이 적용됩니다.");

        registered = true;
    }

    private static void register(
            Identifier id,
            int version,
            String displayName,
            String... description
    ) {
        List<Component> lines = Arrays.stream(description)
                .map(Component::literal)
                .map(Component.class::cast)
                .toList();
        TraitRegistry.registerIfAbsent(new SemionTrait(
                id,
                version,
                Component.literal(displayName),
                lines
        ) {
            @Override
            public Component effectSummary(TraitSlot slot) {
                return BuiltInTraits.effectSummary(id, slot);
            }
        });
    }

    private static Component effectSummary(Identifier id, TraitSlot slot) {
        double scale = slot.effectScale();
        String summary = switch (id.getPath()) {
            case "none" -> "효과 없음";
            case "mobilization_grant" -> "시작 다이아 "
                    + signedNumber(Math.round(value(id, "startingDiamond") * scale));
            case "clean_lane_bonus" -> "현재 인컴의 "
                    + percentage(value(id, "incomeRatio") * scale);
            case "rapid_deployment" -> "판매 환수율 "
                    + percentage(TraitEffects.sellRefundRate(slot));
            case "berserk_summons" -> "공격력 "
                    + signedPercentage(value(id, "attackDamageBonus") * scale)
                    + " · 공속 "
                    + signedPercentage(value(id, "attackSpeedBonus") * scale);
            case "interception_doctrine" -> "인컴 대상 피해 "
                    + signedPercentage(value(id, "damageBonus") * scale);
            case "opening_salvo" -> number(TraitEffects.openingAttackSpeedDurationSeconds())
                    + "초간 공속 "
                    + signedPercentage(value(id, "attackSpeedBonus") * scale);
            case "wavebreaker_doctrine" -> "웨이브 대상 피해 "
                    + signedPercentage(value(id, "damageBonus") * scale);
            case "fortitude" -> "최대 체력 "
                    + signedPercentage(value(id, "maxHealthBonus") * scale)
                    + " · 하이퍼 캐리형 "
                    + signedPercentage(value(id, "CoreMaxHealthBonus") * scale);
            case "double_edged_sword" -> "입히는 피해 "
                    + signedPercentage(value(id, "outgoingDamageBonus") * scale)
                    + " · 받는 피해 "
                    + signedPercentage(value(id, "incomingDamageBonus") * scale);
            case "strength_in_numbers" -> "같은 종류 타워당 "
                    + signedPercentage(value(id, "damageBonusPerTower") * scale);
            case "diversity" -> "서로 다른 종류당 "
                    + signedPercentage(value(id, "damageBonusPerType") * scale);
            case "supply_depot" -> "최대 타워 수 "
                    + signedNumber(Math.round(value(id, "towerLimitBonus") * scale));
            case "transcendence" -> number(value(id, "activationDelaySeconds"))
                    + "초 후 피해 "
                    + signedPercentage(value(id, "damageBonus") * scale);
            case "weekly_holiday_pay" -> number(value(id, "intervalSeconds"))
                    + "초마다 다이아 "
                    + signedNumber(Math.round(value(id, "flatDiamond") * scale))
                    + " + 수입 "
                    + percentage(value(id, "incomeRatio") * scale);
            case "ruthless" -> "디버프 대상 피해 "
                    + signedPercentage(value(id, "damageBonus") * scale);
            case "ignite" -> number(value(id, "durationSeconds"))
                    + "초간 매초 "
                    + number(value(id, "flatDamagePerTick") * scale)
                    + " + 공격력×라운드×"
                    + percentage(value(id, "attackDamageRatioPerRound") * scale)
                    + " 마법 피해 (마법 피해 능력 제외)";
            case "giant_slayer" -> "현재 체력 "
                    + number(value(id, "currentHealthThreshold"))
                    + " 이상 피해 "
                    + signedPercentage(value(id, "damageBonus") * scale);
            case "finishing_blow" -> "체력 "
                    + percentage(value(id, "healthRatioThreshold"))
                    + " 이하 피해 "
                    + signedPercentage(value(id, "damageBonus") * scale);
            case "performance_bonus" -> number(value(id, "firstPayoutRound"))
                    + "라운드부터 팀 수입의 "
                    + percentage(value(id, "teamIncomeRatio") * scale);
            default -> "";
        };
        return Component.literal(summary);
    }

    private static double value(Identifier id, String key) {
        return TraitBalanceRuntime.value(id, key);
    }

    private static String signedNumber(long value) {
        return value >= 0 ? "+" + value : Long.toString(value);
    }

    private static String signedPercentage(double ratio) {
        return ratio >= 0.0 ? "+" + percentage(ratio) : "-" + percentage(-ratio);
    }

    private static String percentage(double ratio) {
        return number(ratio * 100.0) + "%";
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, path);
    }
}
