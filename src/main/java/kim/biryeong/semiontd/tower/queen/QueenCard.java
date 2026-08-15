package kim.biryeong.semiontd.tower.queen;

import java.util.concurrent.ThreadLocalRandom;

public record QueenCard(Suit suit, int rank) {
    public QueenCard {
        if (suit == null || rank < 1 || rank > 13) {
            throw new IllegalArgumentException("A card needs a suit and rank from A through K.");
        }
    }

    public static QueenCard random() {
        Suit[] suits = Suit.values();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new QueenCard(suits[random.nextInt(suits.length)], random.nextInt(1, 14));
    }

    public String label() {
        return rankLabel() + suit.symbol();
    }

    public String rankLabel() {
        return switch (rank) {
            case 1 -> "A";
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            default -> Integer.toString(rank);
        };
    }

    public enum Suit {
        HEART("♥", "하트"),
        DIAMOND("♦", "다이아"),
        CLUB("♣", "클로버"),
        SPADE("♠", "스페이드");

        private final String symbol;
        private final String displayName;

        Suit(String symbol, String displayName) {
            this.symbol = symbol;
            this.displayName = displayName;
        }

        public String symbol() {return symbol;}
        public String displayName() {return displayName;}
    }
}
