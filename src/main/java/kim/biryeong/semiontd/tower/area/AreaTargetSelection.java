package kim.biryeong.semiontd.tower.area;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public final class AreaTargetSelection {
    private AreaTargetSelection() {
    }

    public static <T> List<T> sortedFirst(List<T> candidates, Comparator<? super T> order, int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Negative target limit: " + limit);
        }
        if (limit == 0 || candidates.isEmpty()) {
            return List.of();
        }
        if (candidates.size() == 1) {
            return List.of(candidates.getFirst());
        }
        if (limit == 1) {
            return List.of(first(candidates, order));
        }
        if (candidates.size() <= 16) {
            return small(candidates, order, Math.min(limit, candidates.size()));
        }
        if (limit >= candidates.size()) {
            return candidates.stream().sorted(order).toList();
        }
        return bounded(candidates, order, limit);
    }

    private static <T> T first(List<T> candidates, Comparator<? super T> order) {
        if (candidates instanceof java.util.RandomAccess) {
            T first = candidates.getFirst();
            for (int index = 1; index < candidates.size(); index++) {
                T candidate = candidates.get(index);
                if (order.compare(candidate, first) < 0) first = candidate;
            }
            return first;
        }
        var iterator = candidates.iterator();
        T first = iterator.next();
        while (iterator.hasNext()) {
            T candidate = iterator.next();
            if (order.compare(candidate, first) < 0) first = candidate;
        }
        return first;
    }

    private static <T> List<T> small(List<T> candidates, Comparator<? super T> order, int limit) {
        List<T> selected = new ArrayList<>(limit);
        for (T candidate : candidates) {
            int index = selected.size();
            while (index > 0 && order.compare(candidate, selected.get(index - 1)) < 0) {
                index--;
            }
            if (index >= limit) {
                continue;
            }
            if (selected.size() == limit) {
                selected.removeLast();
            }
            selected.add(index, candidate);
        }
        return List.copyOf(selected);
    }

    private static <T> List<T> bounded(List<T> candidates, Comparator<? super T> order, int limit) {
        Comparator<Ranked<T>> rankedOrder = (left, right) -> {
            int comparison = order.compare(left.target, right.target);
            return comparison != 0 ? comparison : Integer.compare(left.index, right.index);
        };
        PriorityQueue<Ranked<T>> selected = new PriorityQueue<>(limit, rankedOrder.reversed());
        int index = 0;
        for (T candidate : candidates) {
            if (selected.size() < limit) {
                selected.add(new Ranked<>(candidate, index));
            } else if (order.compare(candidate, selected.peek().target) < 0) {
                Ranked<T> replacement = selected.remove();
                replacement.target = candidate;
                replacement.index = index;
                selected.add(replacement);
            }
            index++;
        }
        List<Ranked<T>> ranked = new ArrayList<>(selected);
        ranked.sort(rankedOrder);
        List<T> result = new ArrayList<>(limit);
        for (Ranked<T> target : ranked) {
            result.add(target.target);
        }
        return List.copyOf(result);
    }

    private static final class Ranked<T> {
        private T target;
        private int index;

        private Ranked(T target, int index) {
            this.target = target;
            this.index = index;
        }
    }
}
