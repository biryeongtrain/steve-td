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
        cards.forEach(card -> best.put(card, PokerHand.HIGH_CARD));
        if (cards.size() >= 5) {
            boolean pathMostlyX = pathMostlyX(lane);
            Map<RowKey, List<QueenCardTower>> rows = new HashMap<>();
            for (QueenCardTower card : cards) {
                GridPosition position = card.originalPosition();
                RowKey key = new RowKey(pathMostlyX ? position.x() : position.z(), position.y());
                rows.computeIfAbsent(key, ignored -> new ArrayList<>()).add(card);
            }
            rows.values().forEach(row -> evaluateRow(row, pathMostlyX, best));
        }
        best.forEach(QueenCardTower::applyPokerSnapshot);
    }

    private static void evaluateRow(List<QueenCardTower> row, boolean pathMostlyX, Map<QueenCardTower, PokerHand> best) {
        row.sort(Comparator.comparingInt(card -> perpendicular(card.originalPosition(), pathMostlyX)));
        for (int start = 0; start + 5 <= row.size(); start++) {
            List<QueenCardTower> window = row.subList(start, start + 5);
            int first = perpendicular(window.getFirst().originalPosition(), pathMostlyX);
            int last = perpendicular(window.getLast().originalPosition(), pathMostlyX);
            if (last - first != 4) continue;
            PokerHand hand = PokerHand.evaluate(window.stream().map(card -> card.card().orElseThrow()).toList());
            for (QueenCardTower card : window) {
                if (hand.ordinal() > best.get(card).ordinal()) best.put(card, hand);
            }
        }
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
