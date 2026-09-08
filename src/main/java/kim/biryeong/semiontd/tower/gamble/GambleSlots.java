package kim.biryeong.semiontd.tower.gamble;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;

/** Three independent, equally weighted reels; every result awards positive score. */
public final class GambleSlots {
    public static final double DIFFERENT_SCORE = 25.0;
    public static final double MAX_SCORE = 300.0;

    private GambleSlots() {
    }

    public enum Symbol {
        IRON_NUGGET("철 조각", "IronNugget", 60, 150),
        IRON("철", "Iron", 70, 180),
        COPPER("구리", "Copper", 80, 210),
        GOLD("금괴", "Gold", 90, 240),
        EMERALD("에메랄드", "Emerald", 100, 270),
        DIAMOND("다이아몬드", "Diamond", 110, 300);

        private final String displayName;
        private final String configSuffix;
        private final double pairScore;
        private final double tripleScore;

        Symbol(String displayName, String configSuffix, double pairScore, double tripleScore) {
            this.displayName = displayName;
            this.configSuffix = configSuffix;
            this.pairScore = pairScore;
            this.tripleScore = tripleScore;
        }

        public String displayName() { return displayName; }
        public String pairKey() { return "slotPair" + configSuffix; }
        public String tripleKey() { return "slotTriple" + configSuffix; }
        public double defaultPairScore() { return pairScore; }
        public double defaultTripleScore() { return tripleScore; }
    }

    public static Result resolve(Symbol first, Symbol second, Symbol third) {
        if (first == null || second == null || third == null) {
            throw new IllegalArgumentException("Slot reels require three symbols.");
        }
        boolean triple = first == second && second == third;
        Symbol pair = first == second || first == third ? first : second == third ? second : null;
        double score = triple
                ? value(first.tripleKey(), first.defaultTripleScore())
                : pair != null ? value(pair.pairKey(), pair.defaultPairScore())
                : value("slotDifferentScore", DIFFERENT_SCORE);
        String reels = "[" + first.displayName() + " | " + second.displayName() + " | "
                + third.displayName() + "] " + (triple ? "잭팟!" : pair != null ? "2개 일치!" : "소액 강화");
        return new Result(score, triple ? 2 : 1, reels);
    }

    private static double value(String key, double fallback) {
        return TowerBalanceRuntime.ability(GambleBalance.GLOBAL_ID, key, fallback);
    }

    public record Result(double score, int statRewardCount, String display) {
    }
}
