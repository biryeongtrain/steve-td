package kim.biryeong.semiontd.entity.goal;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class EntityGoalTargetSelectionTest {
    private record Target(int identity, double priority, double distance) {}

    @Test
    void boundedSelectionMatchesStableFullOrderingAcrossSizesAndTies() {
        Random random = new Random(2603);
        List<Comparator<Target>> orders = List.of(
                Comparator.comparingDouble(Target::distance),
                Comparator.comparingDouble(Target::priority).reversed().thenComparingDouble(Target::distance),
                Comparator.comparingDouble(Target::priority).thenComparingDouble(Target::distance)
                        .thenComparingInt(Target::identity));
        for (int size : new int[] {0, 1, 2, 3, 6, 7, 8, 9, 32, 128, 512, 4096}) {
            List<Target> input = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                input.add(new Target(i, random.nextInt(7), random.nextInt(13)));
            }
            Collections.shuffle(input, random);
            List<Target> unchanged = List.copyOf(input);
            for (Comparator<Target> order : orders) {
                for (int limit : new int[] {-4, -1, 0, 1, 3, 6, 8, size, size + 1}) {
                    List<Target> expected = input.stream().sorted(order).limit(Math.max(0, limit)).toList();
                    assertEquals(expected, EntityGoalTargetSelection.first(input, order, limit),
                            "size=" + size + " limit=" + limit);
                    assertEquals(unchanged, input, "Selection must not reorder the query result.");
                }
            }
        }
    }

    @Test
    void singleBestKeepsFirstIdentityWhenEveryCandidateTies() {
        for (int size : new int[] {1, 2, 8, 9, 32, 512}) {
            Target first = new Target(0, 3, 4);
            List<Target> input = new ArrayList<>();
            input.add(first);
            for (int i = 1; i < size; i++) {
                input.add(i % 3 == 0 ? first : new Target(i, 3, 4));
            }
            List<Target> selected = EntityGoalTargetSelection.first(input,
                    Comparator.comparingDouble(Target::distance), 1);
            assertEquals(1, selected.size());
            assertSame(first, selected.getFirst());
        }
    }

    @Test
    void singleBestUsesLinearComparisonsForSequentialLists() {
        List<Integer> input = new LinkedList<>();
        for (int value = 512; value > 0; value--) {
            input.add(value);
        }
        AtomicInteger comparisons = new AtomicInteger();
        Comparator<Integer> order = (left, right) -> {
            comparisons.incrementAndGet();
            return Integer.compare(left, right);
        };
        assertEquals(List.of(1), EntityGoalTargetSelection.first(input, order, 1));
        assertEquals(input.size() - 1, comparisons.get());
        assertEquals(512, input.getFirst());
    }

    @Test
    void singleBestPreservesNullableValuesAndIndependentMutableResults() {
        for (int size : new int[] {1, 2, 8, 9, 128}) {
            List<Integer> input = new ArrayList<>();
            input.add(null);
            for (int i = 1; i < size; i++) {
                input.add(i % 3 == 0 ? null : i);
            }
            List<Integer> unchanged = new ArrayList<>(input);
            for (Comparator<Integer> order : List.of(Comparator.nullsFirst(Integer::compare),
                    Comparator.nullsLast(Integer::compare))) {
                List<Integer> expected = new ArrayList<>(input);
                expected.sort(order);
                List<Integer> actual = EntityGoalTargetSelection.first(input, order, 1);
                assertEquals(expected.subList(0, 1), actual);
                actual.set(0, 42);
                actual.add(null);
                actual.remove(0);
                actual.clear();
                assertTrue(actual.isEmpty());
                assertEquals(unchanged, input);
            }
        }
    }

    @Test
    void nullComparatorRetainsExistingNaturalOrderingAndFailureBoundaries() {
        for (int size : new int[] {1, 2, 8}) {
            List<Integer> input = new ArrayList<>();
            for (int value = size; value > 0; value--) {
                input.add(value);
            }
            List<Integer> expected = new ArrayList<>(input);
            expected.sort(null);
            List<Integer> actual = EntityGoalTargetSelection.first(input, null, 1);
            assertEquals(expected.subList(0, 1), actual);
            actual.add(100);
            assertEquals(size, input.size());
        }
        Object incomparable = new Object();
        assertSame(incomparable, EntityGoalTargetSelection.first(List.of(incomparable), null, 1).getFirst());
        assertThrows(ClassCastException.class, () -> EntityGoalTargetSelection.first(
                List.of(new Object(), new Object()), null, 1));
        assertThrows(NullPointerException.class, () -> EntityGoalTargetSelection.first(
                List.of(9, 8, 7, 6, 5, 4, 3, 2, 1), null, 1));
        assertEquals(List.of(), EntityGoalTargetSelection.first(List.of(1), null, -1));
        assertEquals(List.of(), EntityGoalTargetSelection.first(List.of(), null, 1));
    }

    @Test
    void singleBestObservesChangedPriorityOnEachInvocation() {
        List<Integer> input = List.of(0, 1, 2, 3);
        int[] priorities = {0, 1, 2, 3};
        Comparator<Integer> order = Comparator.comparingInt(index -> priorities[index]);
        List<Integer> first = EntityGoalTargetSelection.first(input, order, 1);
        priorities[0] = 10;
        priorities[3] = -1;
        assertEquals(List.of(3), EntityGoalTargetSelection.first(input, order, 1));
        assertEquals(List.of(0), first);
    }

    @Test
    void equalPriorityKeepsEncounterOrderAndRepeatedReferences() {
        Target repeated = new Target(1, 0, 0);
        List<Target> input = List.of(repeated, new Target(2, 0, 0), repeated, new Target(3, 0, 0));
        List<Target> actual = EntityGoalTargetSelection.first(input, Comparator.comparingDouble(Target::distance), 3);
        assertEquals(input.subList(0, 3), actual);
        assertSame(repeated, actual.getFirst());
        assertSame(repeated, actual.getLast());
    }

    @Test
    void floatingPointOrderMatchesJavaComparatorIncludingSignedZeroAndNan() {
        List<Double> input = List.of(Double.NaN, 0.0, -0.0, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, 3.0, Double.NaN, -1.0);
        for (int limit = 0; limit <= input.size(); limit++) {
            assertEquals(input.stream().sorted(Double::compare).limit(limit).toList(),
                    EntityGoalTargetSelection.first(input, Double::compare, limit));
        }
    }

    @Test
    void eachInvocationObservesCurrentPriorityAndDoesNotCacheTargets() {
        List<Integer> input = List.of(0, 1, 2, 3);
        int[] priority = {0, 1, 2, 3};
        Comparator<Integer> order = Comparator.comparingInt(index -> priority[index]);
        List<Integer> first = EntityGoalTargetSelection.first(input, order, 2);
        priority[0] = 10;
        priority[3] = -1;
        assertEquals(List.of(3, 1), EntityGoalTargetSelection.first(input, order, 2));
        assertEquals(List.of(0, 1), first);
    }
}
