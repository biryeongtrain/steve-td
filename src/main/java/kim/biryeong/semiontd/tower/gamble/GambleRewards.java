package kim.biryeong.semiontd.tower.gamble;

import java.util.Arrays;
import java.util.List;

public final class GambleRewards {
    private static final GambleStat[] ROLLABLE_STATS = {
            GambleStat.MAX_HEALTH,
            GambleStat.DAMAGE,
            GambleStat.RANGE,
            GambleStat.MAGIC_DAMAGE
    };

    private GambleRewards() {
    }

    public static List<GambleAbility> missingAbilities(GambleState state) {
        GambleState current = state == null ? GambleState.EMPTY : state;
        return Arrays.stream(GambleAbility.values()).filter(ability -> !current.has(ability)).toList();
    }

    public static boolean awardsAbility(GambleState state, double score, double chanceRoll) {
        return score > 0.0
                && !missingAbilities(state).isEmpty()
                && Double.isFinite(chanceRoll)
                && chanceRoll >= 0.0
                && chanceRoll < GambleBalance.abilityRewardChance();
    }

    public static GambleAbility chooseMissing(GambleState state, int index) {
        List<GambleAbility> missing = missingAbilities(state);
        if (missing.isEmpty()) {
            throw new IllegalStateException("No unowned gamble ability remains.");
        }
        return missing.get(Math.floorMod(index, missing.size()));
    }

    static double statRewardScore(double score, GambleAbility ability) {
        return ability == null ? score : Math.max(0.0, score - GambleBalance.oddEvenWinScore());
    }

    public static GambleStat chooseStat(int index) {
        return ROLLABLE_STATS[Math.floorMod(index, ROLLABLE_STATS.length)];
    }

    public static List<GambleStat> chooseDistinctStats(int firstIndex, int secondOffset) {
        GambleStat first = chooseStat(firstIndex);
        int normalizedFirst = Math.floorMod(firstIndex, ROLLABLE_STATS.length);
        int normalizedOffset = 1 + Math.floorMod(secondOffset, ROLLABLE_STATS.length - 1);
        GambleStat second = ROLLABLE_STATS[(normalizedFirst + normalizedOffset) % ROLLABLE_STATS.length];
        return List.of(first, second);
    }

    public static int rollableStatCount() {
        return ROLLABLE_STATS.length;
    }

    public static double insuredDelta(GambleState state, double delta) {
        if (delta >= 0.0 || state == null || !state.has(GambleAbility.LOSS_INSURANCE)) {
            return delta;
        }
        return delta * Math.max(0.0, 1.0 - GambleBalance.lossInsuranceReduction());
    }
}
