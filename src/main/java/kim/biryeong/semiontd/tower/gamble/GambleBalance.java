package kim.biryeong.semiontd.tower.gamble;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.TowerType;

public final class GambleBalance {
    public static final String GLOBAL_ID = "gamble_global";
    public static final double ODD_EVEN_WIN_SCORE = 70.0;
    public static final double ODD_EVEN_LOSS_SCORE = 40.0;
    public static final double MAX_HEALTH_PER_SCORE = 5.0;
    public static final double DAMAGE_PER_SCORE = 0.50;
    public static final double RANGE_PER_SCORE = 0.05;
    public static final double SPLASH_RADIUS_PER_SCORE = 0.025;
    public static final double BASE_SPLASH_RADIUS = 2.5;
    public static final double SPLASH_DAMAGE_RATIO = 0.60;
    public static final double ABILITY_REWARD_CHANCE = 0.25;
    public static final double LOSS_INSURANCE_REDUCTION = 0.20;
    public static final int TWO_DICE_COMPOUND_MIN_SUM = 10;
    public static final int SUPPORT_VFX_INTERVAL_TICKS = 40;
    public static final double SUPPORT_POSITIVE_RANGE_UNIT = 0.25;
    public static final double SUPPORT_POSITIVE_REGEN_UNIT = 2.5;
    public static final double SUPPORT_POSITIVE_DAMAGE_UNIT = 2.5;
    public static final double SUPPORT_POSITIVE_MAX_HEALTH_UNIT = 25.0;
    public static final double SUPPORT_NEGATIVE_RANGE_UNIT = 0.25;
    public static final double SUPPORT_NEGATIVE_HEALTH_LOSS_UNIT = 1.0;
    public static final double SUPPORT_NEGATIVE_DAMAGE_UNIT = 2.5;
    public static final double SUPPORT_NEGATIVE_MAX_HEALTH_UNIT = 25.0;
    public static final int MAX_SPECTATORS_PER_GAMBLER = 3;
    public static final double KING_PROMOTION_SCORE = 400.0;
    public static final double DARK_KING_PROMOTION_SCORE = -200.0;
    public static final double MAX_GAMBLE_SCORE = 500.0;
    public static final double KING_SPLASH_RADIUS_BONUS = 0.5;
    public static final double DARK_KING_SPLASH_RADIUS_BONUS = 0.75;

    private GambleBalance() {
    }

    public static double oddEvenWinScore() {
        return global("oddEvenWinScore", ODD_EVEN_WIN_SCORE);
    }

    public static double oddEvenLossScore() {
        return global("oddEvenLossScore", ODD_EVEN_LOSS_SCORE);
    }

    public static double abilityRewardChance() {
        return global("abilityRewardChance", ABILITY_REWARD_CHANCE);
    }

    public static double lossInsuranceReduction() {
        return global("lossInsuranceReduction", LOSS_INSURANCE_REDUCTION);
    }

    public static double baseSplashRadius() {
        return global("baseSplashRadius", BASE_SPLASH_RADIUS);
    }

    public static double splashDamageRatio() {
        return global("splashDamageRatio", SPLASH_DAMAGE_RATIO);
    }

    public static double kingPromotionScore() {
        return global("kingPromotionScore", KING_PROMOTION_SCORE);
    }

    public static double darkKingPromotionScore() {
        return -global("darkKingPromotionScoreMagnitude", Math.abs(DARK_KING_PROMOTION_SCORE));
    }

    public static double maxGambleScore() {
        return global("maxGambleScore", MAX_GAMBLE_SCORE);
    }

    public static double gamblerSplashRadius(TowerType type) {
        double fallback = 0.0;
        if (GambleTowers.isKing(type)) {
            fallback = KING_SPLASH_RADIUS_BONUS;
        } else if (GambleTowers.isDarkKing(type)) {
            fallback = DARK_KING_SPLASH_RADIUS_BONUS;
        }
        return baseSplashRadius() + Math.max(0.0,
                TowerBalanceRuntime.ability(type.id(), "splashRadiusBonus", fallback));
    }

    public static int supportVfxIntervalTicks() {
        return Math.max(1, TowerBalanceRuntime.abilityInt(
                GLOBAL_ID, "supportVfxIntervalTicks", SUPPORT_VFX_INTERVAL_TICKS
        ));
    }

    public static double twoDiceScore(int sum) {
        if (sum < 2 || sum > 12) {
            throw new IllegalArgumentException("Two-dice sum must be between 2 and 12: " + sum);
        }
        if (sum <= 5) {
            double[] defaults = {0.0, 0.0, 70.0, 50.0, 30.0, 10.0};
            return -global("twoDiceLoss" + sum, defaults[sum]);
        }
        double[] defaults = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 20.0, 40.0, 50.0, 60.0, 90.0, 120.0, 150.0};
        return global("twoDiceGain" + sum, defaults[sum]);
    }

    public static int twoDiceCompoundMinSum() {
        return Math.max(2, Math.min(12, TowerBalanceRuntime.abilityInt(
                GLOBAL_ID, "twoDiceCompoundMinSum", TWO_DICE_COMPOUND_MIN_SUM)));
    }

    public static double statDelta(GambleStat stat, double score) {
        double perScore = switch (stat) {
            case MAX_HEALTH -> global("maxHealthPerScore", MAX_HEALTH_PER_SCORE);
            case DAMAGE, MAGIC_DAMAGE -> global("damagePerScore", DAMAGE_PER_SCORE);
            case RANGE -> global("rangePerScore", RANGE_PER_SCORE);
            case SPLASH_RADIUS -> global("splashRadiusPerScore", SPLASH_RADIUS_PER_SCORE);
        };
        return score * perScore;
    }

    public static int minimumRoll(TowerType type) {
        return Math.max(1, Math.min(6, TowerBalanceRuntime.abilityInt(type.id(), "minimumRoll", 1)));
    }

    public static double baseMagicDamage(TowerType type) {
        double fallback = GambleTowers.isKing(type) ? 20.0 : GambleTowers.isDarkKing(type) ? 22.0 : 5.0;
        return TowerBalanceRuntime.ability(type.id(), "baseMagicDamage", fallback);
    }

    public static double supportPowerMultiplier(TowerType type) {
        return Math.max(0.0, TowerBalanceRuntime.ability(type.id(), "supportPowerMultiplier", 1.0));
    }

    public static long spectatorJackpotDiamondReward(TowerType type) {
        if (!GambleTowers.isSpectator(type)) {
            return 0L;
        }
        return Math.max(0L, Math.round(TowerBalanceRuntime.ability(
                type.id(), "jackpotDiamondReward", 0.0
        )));
    }

    public static int maxSpectatorsPerGambler() {
        return Math.max(1, TowerBalanceRuntime.abilityInt(
                GLOBAL_ID, "maxSpectatorsPerGambler", MAX_SPECTATORS_PER_GAMBLER));
    }

    public static double supportUnit(GambleSupportStat stat, boolean positive) {
        if (stat == null) {
            return 0.0;
        }
        return switch (stat) {
            case RANGE -> positive
                    ? global("supportPositiveRangeUnit", SUPPORT_POSITIVE_RANGE_UNIT)
                    : global("supportNegativeRangeUnit", SUPPORT_NEGATIVE_RANGE_UNIT);
            case REGENERATION -> positive
                    ? global("supportPositiveRegenUnit", SUPPORT_POSITIVE_REGEN_UNIT)
                    : global("supportNegativeHealthLossUnit", SUPPORT_NEGATIVE_HEALTH_LOSS_UNIT);
            case DAMAGE -> positive
                    ? global("supportPositiveDamageUnit", SUPPORT_POSITIVE_DAMAGE_UNIT)
                    : global("supportNegativeDamageUnit", SUPPORT_NEGATIVE_DAMAGE_UNIT);
            case MAGIC_DAMAGE -> positive ? global("supportPositiveDamageUnit", SUPPORT_POSITIVE_DAMAGE_UNIT) : 0.0;
            case MAX_HEALTH -> positive
                    ? global("supportPositiveMaxHealthUnit", SUPPORT_POSITIVE_MAX_HEALTH_UNIT)
                    : global("supportNegativeMaxHealthUnit", SUPPORT_NEGATIVE_MAX_HEALTH_UNIT);
        };
    }

    private static double global(String key, double fallback) {
        return TowerBalanceRuntime.ability(GLOBAL_ID, key, fallback);
    }
}
