package kim.biryeong.semiontd.tower.area;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AreaTargetSelectionTest {
    private record Candidate(int score, int identity) {
    }

    @Test
    void boundedSelectionMatchesStableSortIncludingRepeatedIdentities() {
        Random random = new Random(26031010L);
        Comparator<Candidate> order = Comparator.comparingInt(Candidate::score);
        for (int size : new int[] {0, 1, 2, 8, 15, 16, 17, 32, 128, 512}) {
            List<Candidate> candidates = new ArrayList<>();
            for (int index = 0; index < size; index++) {
                candidates.add(index > 0 && index % 3 == 0
                        ? candidates.get(random.nextInt(index)) : new Candidate(random.nextInt(8), index));
            }
            for (int limit : new int[] {0, 1, 2, 3, 8, 32, 128, 512, Integer.MAX_VALUE}) {
                List<Candidate> expected = candidates.stream().sorted(order).limit(limit).toList();
                List<Candidate> actual = AreaTargetSelection.sortedFirst(candidates, order, limit);
                assertEquals(expected.size(), actual.size());
                for (int index = 0; index < expected.size(); index++) {
                    assertSame(expected.get(index), actual.get(index), "Encounter order and duplicate slots must survive");
                }
                assertEquals(expected, AreaTargetSelection.sortedFirst(new java.util.LinkedList<>(candidates), order, limit));
            }
        }
    }

    @Test
    void selectionPreservesDoubleOrderingAndInput() {
        List<Double> candidates = List.of(Double.NaN, 0.0, -0.0, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, 3.0, -2.0, Double.NaN);
        List<Double> expanded = new ArrayList<>();
        for (int repeat = 0; repeat < 5; repeat++) {
            expanded.addAll(candidates);
        }
        for (List<Double> input : List.of(candidates, expanded)) {
            for (int limit = 0; limit <= input.size() + 1; limit++) {
                assertEquals(input.stream().sorted(Double::compare).limit(limit).toList(),
                        AreaTargetSelection.sortedFirst(input, Double::compare, limit));
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> AreaTargetSelection.sortedFirst(candidates, Double::compare, -1));
        assertThrows(UnsupportedOperationException.class,
                () -> AreaTargetSelection.sortedFirst(candidates, Double::compare, 3).add(4.0));
        assertEquals(Double.NaN, candidates.getFirst());
    }
}
