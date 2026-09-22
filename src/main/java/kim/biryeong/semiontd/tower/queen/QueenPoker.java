package kim.biryeong.semiontd.tower.queen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import net.minecraft.world.phys.Vec3;

final class QueenPoker {
    private QueenPoker() {}

    static void snapshot(PlayerLane lane, UUID ownerPlayer) {
        List<QueenCardTower> cards = lane.towers().stream()
                .filter(QueenCardTower.class::isInstance).map(QueenCardTower.class::cast)
                .filter(card -> card.ownerPlayer().equals(ownerPlayer) && card.card().isPresent())
                .toList();
        Map<QueenCardTower, PokerHand> best = new HashMap<>();
        Map<QueenCardTower, QueenCard> jokerCards = new HashMap<>();
        cards.forEach(card -> best.put(card, PokerHand.HIGH_CARD));
        if (!cards.isEmpty()) {
            boolean pathMostlyX = pathMostlyX(lane);
            Map<RowKey, List<QueenCardTower>> rows = new HashMap<>();
            for (QueenCardTower card : cards) {
                GridPosition position = card.originalPosition();
                RowKey key = new RowKey(pathMostlyX ? position.x() : position.z(), position.y());
                rows.computeIfAbsent(key, ignored -> new ArrayList<>()).add(card);
            }
            double healthBonus = 0.0;
            for (List<QueenCardTower> row : rows.values()) {
                boolean jokerPresent = row.stream().anyMatch(QueenCardTower::isJoker);
                row.forEach(card -> card.applyJokerRowBonus(jokerPresent));
                healthBonus += evaluateRow(row, pathMostlyX, best, jokerCards);
            }
            QueenStates.state(ownerPlayer).addPokerHealthBonus(healthBonus);
        }
        jokerCards.forEach(QueenCardTower::assignCard);
        best.forEach(QueenCardTower::applyPokerSnapshot);
    }

    private static double evaluateRow(List<QueenCardTower> row, boolean pathMostlyX,
                                     Map<QueenCardTower, PokerHand> best, Map<QueenCardTower, QueenCard> jokerCards) {
        double healthBonus = 0.0;
        row.sort(Comparator.comparingInt(card -> perpendicular(card.originalPosition(), pathMostlyX)));
        for (int start = 0; start + 5 <= row.size(); start++) {
            List<QueenCardTower> window = row.subList(start, start + 5);
            int first = perpendicular(window.getFirst().originalPosition(), pathMostlyX);
            int last = perpendicular(window.getLast().originalPosition(), pathMostlyX);
            if (last - first != 4) continue;
            JokerHand result = bestWithJokers(window.stream().map(card -> card.card().orElseThrow()).toList(),
                    window.stream().map(QueenCardTower::isJoker).toList());
            PokerHand hand = result.hand();
            healthBonus += QueenBalance.handBonus(hand);
            for (int index = 0; index < window.size(); index++) {
                QueenCardTower card = window.get(index);
                if (hand.ordinal() > best.get(card).ordinal()) {
                    best.put(card, hand);
                    if (card.isJoker()) jokerCards.put(card, result.cards().get(index));
                }
            }
        }
        return healthBonus;
    }

    record JokerHand(PokerHand hand, List<QueenCard> cards) {}

    static JokerHand bestWithJokers(List<QueenCard> cards, List<Boolean> jokers) {
        if (cards.size() != 5 || jokers.size() != 5) return new JokerHand(PokerHand.HIGH_CARD, cards);
        return assignJokerRanks(new ArrayList<>(cards), jokers, 0,
                new JokerHand(PokerHand.evaluate(cards), List.copyOf(cards)));
    }

    private static JokerHand assignJokerRanks(List<QueenCard> cards, List<Boolean> jokers, int index, JokerHand best) {
        if (best.hand() == PokerHand.FIVE_OF_A_KIND) return best;
        if (index == cards.size()) {
            PokerHand hand = PokerHand.evaluate(cards);
            if (hand.ordinal() > best.hand().ordinal()) best = new JokerHand(hand, List.copyOf(cards));
            // Only flushes depend on suits. Other hands retain each joker's current suit.
            List<QueenCard.Suit> fixedSuits = java.util.stream.IntStream.range(0, cards.size())
                    .filter(i -> !jokers.get(i)).mapToObj(i -> cards.get(i).suit()).distinct().toList();
            if (fixedSuits.size() == 1) {
                List<QueenCard> flushCards = new ArrayList<>(cards);
                for (int i = 0; i < cards.size(); i++) {
                    if (jokers.get(i)) flushCards.set(i, new QueenCard(fixedSuits.getFirst(), cards.get(i).rank()));
                }
                PokerHand flushHand = PokerHand.evaluate(flushCards);
                if (flushHand.ordinal() > best.hand().ordinal()) best = new JokerHand(flushHand, List.copyOf(flushCards));
            }
            return best;
        }
        if (!jokers.get(index)) return assignJokerRanks(cards, jokers, index + 1, best);
        QueenCard previous = cards.get(index);
        best = assignJokerRanks(cards, jokers, index + 1, best);
        for (int rank = 1; rank <= 13; rank++) {
            if (rank == previous.rank()) continue;
            cards.set(index, new QueenCard(previous.suit(), rank));
            best = assignJokerRanks(cards, jokers, index + 1, best);
        }
        cards.set(index, previous);
        return best;
    }

    private static int perpendicular(GridPosition position, boolean pathMostlyX) {
        return pathMostlyX ? position.z() : position.x();
    }

    private static boolean pathMostlyX(PlayerLane lane) {
        Vec3 start = lane.laneLayout().spawn();
        List<Vec3> path = lane.laneLayout().pathPoints();
        Vec3 next = path.size() > 1 ? path.get(1) : lane.laneLayout().bossPosition();
        return Math.abs(next.x - start.x) >= Math.abs(next.z - start.z);
    }

    private record RowKey(int longitudinal, int y) {}
}
