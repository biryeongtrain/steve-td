package kim.biryeong.semiontd.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class UiHudDamageRankingTest {
    @Test
    void boundedRankingMatchesStableFullSortAcrossSizesAndInputOrders() {
        Random random = new Random(820_301);
        for (int size : new int[] {0, 1, 4, 5, 6, 32, 128, 555, 4096}) {
            List<UiHudDamageRanking.Summary> summaries = new ArrayList<>();
            for (int index = 0; index < size; index++) {
                summaries.add(new UiHudDamageRanking.Summary("type_" + index, "name_" + random.nextInt(7),
                        random.nextInt(20), random.nextInt(5), random.nextInt(20)));
            }
            for (int permutation = 0; permutation < 8; permutation++) {
                Collections.shuffle(summaries, random);
                List<UiHudDamageRanking.Summary> original = List.copyOf(summaries);
                for (boolean dealt : new boolean[] {true, false}) {
                    assertEquals(sortedTop(summaries, dealt), UiHudDamageRanking.top(summaries, dealt));
                }
                assertEquals(original, summaries);
            }
        }
    }

    @Test
    void tiesKeepNameIdAndEncounterOrderAndDamageTypesStayIndependent() {
        var first = new UiHudDamageRanking.Summary("same", "same", 4, 6, 1);
        var second = new UiHudDamageRanking.Summary("same", "same", 6, 4, 2);
        var byName = new UiHudDamageRanking.Summary("z", "a", 10, 0, 3);
        var byId = new UiHudDamageRanking.Summary("a", "same", 10, 0, 4);
        var highestTaken = new UiHudDamageRanking.Summary("lowest", "lowest", 1, 0, 100);
        var input = List.of(first, highestTaken, second, byName, byId);
        assertEquals(List.of(byName, byId, first, second, highestTaken), UiHudDamageRanking.top(input, true));
        assertEquals(List.of(highestTaken, byId, byName, second, first), UiHudDamageRanking.top(input, false));
    }

    @Test
    void filteringPreservesPositiveInfinityAndExcludesNanZeroAndNegativeValues() {
        var positive = new UiHudDamageRanking.Summary("positive", "positive", Double.POSITIVE_INFINITY, 0, 1);
        var nan = new UiHudDamageRanking.Summary("nan", "nan", Double.NaN, 1, Double.NaN);
        var zero = new UiHudDamageRanking.Summary("zero", "zero", -0.0, 0, -0.0);
        var negative = new UiHudDamageRanking.Summary("negative", "negative", -1, 0, -1);
        assertEquals(List.of(positive), UiHudDamageRanking.top(List.of(nan, zero, negative, positive), true));
        assertEquals(List.of(positive), UiHudDamageRanking.top(List.of(nan, zero, negative, positive), false));
        assertTrue(UiHudDamageRanking.top(List.of(nan, zero, negative), true).isEmpty());
    }

    private static List<UiHudDamageRanking.Summary> sortedTop(List<UiHudDamageRanking.Summary> summaries, boolean dealt) {
        return summaries.stream()
                .filter(summary -> (dealt ? summary.dealt() : summary.taken()) > 0.0)
                .sorted(Comparator.comparingDouble((UiHudDamageRanking.Summary summary) -> dealt ? summary.dealt() : summary.taken())
                        .reversed().thenComparing(UiHudDamageRanking.Summary::displayName)
                        .thenComparing(UiHudDamageRanking.Summary::id))
                .limit(5).toList();
    }
}
