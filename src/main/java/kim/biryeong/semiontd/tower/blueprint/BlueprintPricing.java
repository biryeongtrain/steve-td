package kim.biryeong.semiontd.tower.blueprint;

import java.util.Optional;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.DamageType;

/**
 * 설계도 가격과 한도.
 *
 * <p>가격은 "위력" P를 초선형으로 올린 값입니다.
 * <pre>
 *   공격  = (초당 피해 × 피해 유형 배율 / dpsUnit) × (사거리 / rangePivot)^rangeExponent × Π(1 + 공격 모듈 가중치 × 단계)
 *   생존  = 체력 / healthUnit × Π(1 + 생존 모듈 가중치 × 단계)
 *   유틸  = Σ 유틸 모듈 가중치 × 단계 × √(초당 맞힘) × (1 + utilityTargetBonus × (맞히는 대상 수 - 1))
 *   소환  = (기본 공격 위력 + 기본 생존 위력) × 하수인 능력치 비율 × 평균 동시 개체 수 × summon.powerWeight
 *   P     = 공격 + 생존 + 유틸 + Σ 지원 모듈 위력 × 단계 + 소환
 *   가격  = priceScale × P^priceExponent   (priceStep 단위로 반올림, 최소 minimumPrice)
 * </pre>
 * 기본 계수로는 기존 빌더 타워의 가격대별 중앙값(가격 50·130·260 → 초당 피해 8·16.7·26.7, 체력 88·140·200)의 위력을
 * 약 35·85·160에 삽니다. 지수가 1보다 커서 세게 만들수록 값이 가파르게 오릅니다.
 * 계수는 모두 {@link BlueprintTowers#CONFIG_ID} 아래 능력 값이라 설정에서 바꿀 수 있습니다.
 */
public final class BlueprintPricing {
    private BlueprintPricing() {
    }

    /** 이 능력치로 만든 타워의 설치 가격(다이아). */
    public static long price(BlueprintStats stats) {
        double power = power(stats);
        long price = roundedPrice(power);
        int summonLevel = stats.level(BlueprintModule.SUMMON);
        if (summonLevel > 0) {
            long minimumSurcharge = (long) value("summon.minimumPricePerLevel") * summonLevel;
            price = Math.max(price, roundedPrice(power - summonPower(stats)) + minimumSurcharge);
        }
        return price;
    }

    private static long roundedPrice(double power) {
        double raw = value("priceScale") * Math.pow(Math.max(0.0, power), value("priceExponent"));
        double step = Math.max(1.0, value("priceStep"));
        long rounded = Math.round(raw / step) * (long) step;
        return Math.max((long) value("minimumPrice"), rounded);
    }

    /** 가격 공식의 위력 P. 공격 쪽, 생존 쪽, 지원 모듈을 더합니다. */
    public static double power(BlueprintStats stats) {
        double offense = stats.damagePerSecond() * damageTypeMultiplier(stats.damageType()) / value("dpsUnit");
        double rangeFactor = Math.pow(Math.max(0.1, stats.range()) / value("rangePivot"), value("rangeExponent"));
        double defense = stats.maxHealth() / value("healthUnit");
        // 모듈은 서로 겹쳐 세지므로(다중 사격 화살마다 광역·연쇄가 터짐) 같은 쪽 모듈끼리는 곱합니다.
        double offenseMultiplier = 1.0;
        double defenseMultiplier = 1.0;
        double utility = 0.0;
        double support = 0.0;
        double hitRate = Math.sqrt(20.0 / Math.max(1, stats.attackIntervalTicks()));
        double targetFactor = 1.0 + value("utilityTargetBonus") * (targetsPerHit(stats) - 1.0);
        for (var module : stats.modules().entrySet()) {
            double weighted = module.getKey().priceWeight() * module.getValue();
            switch (module.getKey().kind()) {
                case OFFENSE -> offenseMultiplier *= 1.0 + weighted;
                case DEFENSE -> defenseMultiplier *= 1.0 + weighted;
                case UTILITY -> utility += weighted * hitRate * targetFactor;
                case SUPPORT -> support += weighted;
                case SUMMON -> { /* 하수인은 다른 모듈을 계승하지 않으므로 별도 계산합니다. */ }
            }
        }
        return offense * rangeFactor * offenseMultiplier + defense * defenseMultiplier + utility + support + summonPower(stats);
    }

    private static double summonPower(BlueprintStats stats) {
        int level = stats.level(BlueprintModule.SUMMON);
        if (level <= 0) {
            return 0.0;
        }
        BlueprintModule summon = BlueprintModule.SUMMON;
        double offense = stats.damagePerSecond() * damageTypeMultiplier(stats.damageType()) / value("dpsUnit");
        double rangeFactor = Math.pow(Math.max(0.1, stats.range()) / value("rangePivot"), value("rangeExponent"));
        double basePower = offense * rangeFactor + stats.maxHealth() / value("healthUnit");
        double averageCount = Math.min(summon.value("count", level),
                summon.value("durationTicks", level) / summon.value("intervalTicks", level));
        return basePower * summon.value("statRatio", level) * averageCount * summon.priceWeight();
    }

    /** 한 번 공격에 효과를 거는 대상 수(기본 1 + 다중 사격 추가 대상 + 직선이 보통 더 맞히는 수). */
    public static double targetsPerHit(BlueprintStats stats) {
        double targets = 1.0;
        int multishot = stats.level(BlueprintModule.MULTISHOT);
        if (multishot > 0) {
            targets += BlueprintModule.MULTISHOT.value("extraTargets", multishot);
        }
        if (stats.level(BlueprintModule.LINE) > 0) {
            targets *= 1.0 + value("line.expectedExtraTargets");
        }
        return targets;
    }

    /** 한 설계도에 붙일 수 있는 모듈 수. */
    public static int maxModules() {
        return Math.max(0, (int) value("maxModules"));
    }

    /** 가격이 높을수록 타워 수(인구)를 더 차지합니다. */
    public static int slotCost(long price) {
        if (price >= (long) value("threeSlotPrice")) {
            return 3;
        }
        return price >= (long) value("twoSlotPrice") ? 2 : 1;
    }

    /** 한 사람이 한 경기에 만들 수 있는 설계도 수. */
    public static int maxBlueprints() {
        return Math.max(1, (int) value("maxBlueprintsPerPlayer"));
    }

    /** 한도를 벗어난 첫 항목을 설명합니다. 문제가 없으면 비어 있습니다. */
    public static Optional<String> validate(BlueprintStats stats) {
        if (outside(stats.maxHealth(), "minHealth", "maxHealth")) {
            return Optional.of(range("체력", "minHealth", "maxHealth"));
        }
        if (outside(stats.damage(), "minDamage", "maxDamage")) {
            return Optional.of(range("공격력", "minDamage", "maxDamage"));
        }
        if (outside(stats.attackIntervalTicks(), "minAttackIntervalTicks", "maxAttackIntervalTicks")) {
            return Optional.of(range("공격 간격(틱)", "minAttackIntervalTicks", "maxAttackIntervalTicks"));
        }
        if (outside(stats.range(), "minRange", "maxRange")) {
            return Optional.of(range("사거리", "minRange", "maxRange"));
        }
        if (outside(stats.aggroPriority(), "minAggroPriority", "maxAggroPriority")) {
            return Optional.of(range("어그로", "minAggroPriority", "maxAggroPriority"));
        }
        if (stats.modules().size() > maxModules()) {
            return Optional.of("모듈은 " + maxModules() + "개까지 붙일 수 있습니다.");
        }
        for (var module : stats.modules().entrySet()) {
            if (module.getValue() < 1 || module.getValue() > BlueprintModule.MAX_LEVEL) {
                return Optional.of(module.getKey().displayName() + " 단계는 1~" + BlueprintModule.MAX_LEVEL + "이어야 합니다.");
            }
        }
        return Optional.empty();
    }

    public static double damageTypeMultiplier(DamageType type) {
        return switch (type) {
            case PHYSICAL -> 1.0;
            case MAGIC -> value("magicDamageMultiplier");
            case TRUE -> value("trueDamageMultiplier");
        };
    }

    public static double value(String key) {
        return TowerBalanceRuntime.ability(BlueprintTowers.CONFIG_ID, key);
    }

    private static boolean outside(double actual, String minKey, String maxKey) {
        return actual < value(minKey) - 1.0e-9 || actual > value(maxKey) + 1.0e-9;
    }

    private static String range(String label, String minKey, String maxKey) {
        return label + "은(는) " + trim(value(minKey)) + " ~ " + trim(value(maxKey)) + " 사이여야 합니다.";
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
