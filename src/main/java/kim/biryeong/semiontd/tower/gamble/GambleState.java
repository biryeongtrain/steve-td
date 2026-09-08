package kim.biryeong.semiontd.tower.gamble;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import kim.biryeong.semiontd.tower.TowerType;

public record GambleState(
        double maxHealthDelta,
        double damageDelta,
        double magicDamageDelta,
        double rangeDelta,
        double splashRadiusDelta,
        double cumulativeScore,
        Set<GambleAbility> abilities,
        int totalBets,
        String lastResult
) {
    public static final GambleState EMPTY = new GambleState(
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Set.of(), 0, "도박 전");

    public GambleState {
        maxHealthDelta = capPositiveDelta(GambleStat.MAX_HEALTH, maxHealthDelta);
        damageDelta = capPositiveDelta(GambleStat.DAMAGE, damageDelta);
        magicDamageDelta = capPositiveDelta(GambleStat.MAGIC_DAMAGE, magicDamageDelta);
        rangeDelta = capPositiveDelta(GambleStat.RANGE, rangeDelta);
        splashRadiusDelta = capPositiveDelta(GambleStat.SPLASH_RADIUS, splashRadiusDelta);
        cumulativeScore = capScore(cumulativeScore);
        EnumSet<GambleAbility> copied = abilities == null || abilities.isEmpty()
                ? EnumSet.noneOf(GambleAbility.class)
                : EnumSet.copyOf(abilities);
        abilities = Collections.unmodifiableSet(copied);
        totalBets = Math.max(0, totalBets);
        lastResult = lastResult == null || lastResult.isBlank() ? "도박 전" : lastResult;
    }

    public double resolvedValue(GambleStat stat, double baseValue) {
        double safeBase = Math.max(0.0, baseValue);
        return Math.max(safeBase * 0.20, safeBase + delta(stat));
    }

    public double delta(GambleStat stat) {
        return switch (stat) {
            case MAX_HEALTH -> maxHealthDelta;
            case DAMAGE -> damageDelta;
            case MAGIC_DAMAGE -> magicDamageDelta;
            case RANGE -> rangeDelta;
            case SPLASH_RADIUS -> splashRadiusDelta;
        };
    }

    public boolean has(GambleAbility ability) {
        return abilities.contains(ability);
    }

    public boolean atScoreCap() {
        return cumulativeScore >= GambleBalance.maxGambleScore();
    }

    public GambleState rebalanced(TowerType type) {
        double maxHealthBase = type == null ? 0.0 : type.maxHealth();
        double damageBase = type == null ? 0.0 : type.damage();
        double rangeBase = type == null ? 0.0 : type.range();
        double splashRadiusBase = type == null ? 0.0 : GambleBalance.gamblerSplashRadius(type);
        return new GambleState(
                clampConfiguredDelta(GambleStat.MAX_HEALTH, maxHealthDelta, maxHealthBase),
                clampConfiguredDelta(GambleStat.DAMAGE, damageDelta, damageBase),
                clampConfiguredDelta(GambleStat.MAGIC_DAMAGE, magicDamageDelta,
                        type == null ? 0.0 : GambleBalance.baseMagicDamage(type)),
                clampConfiguredDelta(GambleStat.RANGE, rangeDelta, rangeBase),
                clampConfiguredDelta(GambleStat.SPLASH_RADIUS, splashRadiusDelta, splashRadiusBase),
                cumulativeScore,
                abilities,
                totalBets,
                lastResult
        );
    }

    public GambleState recordStat(
            GambleStat stat, double amount, double baseValue, double score, String result
    ) {
        return recordStats(List.of(new StatChange(stat, amount, baseValue)), score, result);
    }

    public GambleState recordStats(List<StatChange> changes, double score, String result) {
        return recordReward(changes, null, score, result);
    }

    public GambleState recordReward(List<StatChange> changes, GambleAbility ability, double score, String result) {
        double health = maxHealthDelta;
        double damage = damageDelta;
        double magicDamage = magicDamageDelta;
        double range = rangeDelta;
        double splashRadius = splashRadiusDelta;
        for (StatChange change : changes == null ? List.<StatChange>of() : changes) {
            if (change == null || change.stat() == null) {
                continue;
            }
            double minimumDelta = -Math.max(0.0, change.baseValue()) * 0.80;
            double maximumDelta = Math.max(0.0,
                    GambleBalance.statDelta(change.stat(), GambleBalance.maxGambleScore()));
            switch (change.stat()) {
                case MAX_HEALTH -> health = clampDelta(
                        health + change.amount(), minimumDelta, maximumDelta);
                case DAMAGE -> damage = clampDelta(
                        damage + change.amount(), minimumDelta, maximumDelta);
                case MAGIC_DAMAGE -> magicDamage = clampDelta(
                        magicDamage + change.amount(), minimumDelta, maximumDelta);
                case RANGE -> range = clampDelta(
                        range + change.amount(), minimumDelta, maximumDelta);
                case SPLASH_RADIUS -> splashRadius = clampDelta(
                        splashRadius + change.amount(), minimumDelta, maximumDelta);
            }
        }
        EnumSet<GambleAbility> updated = abilities.isEmpty()
                ? EnumSet.noneOf(GambleAbility.class)
                : EnumSet.copyOf(abilities);
        if (ability != null) {
            updated.add(ability);
        }
        return new GambleState(health, damage, magicDamage, range, splashRadius,
                cumulativeScore + sanitizeDelta(score), updated, totalBets + 1, result);
    }

    public GambleState recordAbility(GambleAbility ability, double score, String result) {
        return recordReward(List.of(), ability, score, result);
    }

    private static double sanitizeDelta(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double capScore(double value) {
        return Math.min(GambleBalance.maxGambleScore(), sanitizeDelta(value));
    }

    private static double capPositiveDelta(GambleStat stat, double value) {
        return Math.min(GambleBalance.statDelta(stat, GambleBalance.maxGambleScore()),
                sanitizeDelta(value));
    }

    private static double clampConfiguredDelta(GambleStat stat, double value, double baseValue) {
        double minimum = -Math.max(0.0, baseValue) * 0.80;
        double maximum = Math.max(0.0,
                GambleBalance.statDelta(stat, GambleBalance.maxGambleScore()));
        return clampDelta(value, minimum, maximum);
    }

    private static double clampDelta(double value, double minimum, double maximum) {
        return Math.min(maximum, Math.max(minimum, sanitizeDelta(value)));
    }

    public record StatChange(GambleStat stat, double amount, double baseValue) {
    }
}
