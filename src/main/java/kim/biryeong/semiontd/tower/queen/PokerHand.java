package kim.biryeong.semiontd.tower.queen;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public enum PokerHand {
    HIGH_CARD("하이 카드", 0.00),
    ONE_PAIR("원 페어", 0.10),
    TWO_PAIR("투 페어", 0.15),
    THREE_OF_A_KIND("트리플", 0.20),
    STRAIGHT("스트레이트", 0.25),
    FLUSH("플러시", 0.30),
    FULL_HOUSE("풀하우스", 0.40),
    FOUR_OF_A_KIND("포카드", 0.50),
    STRAIGHT_FLUSH("스트레이트 플러시", 0.65),
    ROYAL_FLUSH("로열 플러시", 0.80),
    FIVE_OF_A_KIND("파이브 카드", 1.00);

    private final String displayName;
    private final double defaultBonus;

    PokerHand(String displayName, double defaultBonus) {
        this.displayName = displayName;
        this.defaultBonus = defaultBonus;
    }

    public String displayName() {return displayName;}
    public double defaultBonus() {return defaultBonus;}

    public static PokerHand evaluate(List<QueenCard> cards) {
        if (cards == null || cards.size() != 5) return HIGH_CARD;
        Map<Integer, Integer> counts = new HashMap<>();
        Set<QueenCard.Suit> suits = new HashSet<>();
        Set<Integer> ranks = new HashSet<>();
        for (QueenCard card : cards) {
            counts.merge(card.rank(), 1, Integer::sum);
            suits.add(card.suit());
            ranks.add(card.rank());
        }
        boolean flush = suits.size() == 1;
        boolean straight = isStraight(ranks);
        boolean royal = ranks.equals(Set.of(1, 10, 11, 12, 13));
        int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        long pairs = counts.values().stream().filter(value -> value == 2).count();
        boolean triple = counts.containsValue(3);

        if (max == 5) return FIVE_OF_A_KIND;
        if (flush && royal) return ROYAL_FLUSH;
        if (flush && straight) return STRAIGHT_FLUSH;
        if (max == 4) return FOUR_OF_A_KIND;
        if (triple && pairs == 1) return FULL_HOUSE;
        if (flush) return FLUSH;
        if (straight) return STRAIGHT;
        if (triple) return THREE_OF_A_KIND;
        if (pairs == 2) return TWO_PAIR;
        if (pairs == 1) return ONE_PAIR;
        return HIGH_CARD;
    }

    private static boolean isStraight(Set<Integer> ranks) {
        if (ranks.size() != 5) return false;
        if (ranks.equals(Set.of(1, 2, 3, 4, 5)) || ranks.equals(Set.of(1, 10, 11, 12, 13))) return true;
        int min = ranks.stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = ranks.stream().mapToInt(Integer::intValue).max().orElse(0);
        return max - min == 4;
    }
}
