package kim.biryeong.semiontd.entity.goal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

final class EntityGoalTargetSelection {
    private EntityGoalTargetSelection() {}

    static <T> List<T> first(List<T> candidates, Comparator<? super T> order, int limit) {
        if (limit <= 0 || candidates.isEmpty()) {
            return List.of();
        }
        if (limit >= candidates.size() || candidates.size() <= 8) {
            List<T> sorted = new ArrayList<>(candidates);
            sorted.sort(order);
            return sorted.subList(0, Math.min(limit, sorted.size()));
        }
        Comparator<Candidate<T>> ranked = (left, right) -> {
            int compared = order.compare(left.value, right.value);
            return compared != 0 ? compared : Integer.compare(left.index, right.index);
        };
        PriorityQueue<Candidate<T>> selected = new PriorityQueue<>(limit, ranked.reversed());
        int index = 0;
        for (T value : candidates) {
            if (selected.size() < limit) {
                selected.add(new Candidate<>(value, index));
            } else if (order.compare(value, selected.peek().value) < 0) {
                Candidate<T> replaced = selected.remove();
                replaced.value = value;
                replaced.index = index;
                selected.add(replaced);
            }
            index++;
        }
        List<Candidate<T>> sorted = new ArrayList<>(selected);
        sorted.sort(ranked);
        List<T> result = new ArrayList<>(sorted.size());
        for (Candidate<T> candidate : sorted) {
            result.add(candidate.value);
        }
        return result;
    }

    private static final class Candidate<T> {
        private T value;
        private int index;

        private Candidate(T value, int index) {
            this.value = value;
            this.index = index;
        }
    }
}
