package kim.biryeong.semiontd.tower.gamble;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

final class GambleSlotsTest {
    @Test
    void all216OutcomesHaveLowerExpectedEfficiencyAndPreserveJackpotCeilings() {
        int different = 0;
        int pairs = 0;
        int triples = 0;
        double total = 0.0;
        for (GambleSlots.Symbol a : GambleSlots.Symbol.values()) {
            for (GambleSlots.Symbol b : GambleSlots.Symbol.values()) {
                for (GambleSlots.Symbol c : GambleSlots.Symbol.values()) {
                    var result = GambleSlots.resolve(a, b, c);
                    assertTrue(result.score() > 0);
                    assertTrue(result.score() <= 300);
                    assertTrue(result.score() / result.statRewardCount() <= 150);
                    assertEquals(result.score(), GambleSlots.resolve(c, a, b).score());
                    total += result.score();
                    if (a == b && b == c) {
                        triples++;
                        assertEquals(2, result.statRewardCount());
                    } else if (a == b || a == c || b == c) {
                        pairs++;
                        assertEquals(1, result.statRewardCount());
                    } else {
                        different++;
                        assertEquals(25, result.score());
                    }
                }
            }
        }
        assertEquals(120, different);
        assertEquals(90, pairs);
        assertEquals(6, triples);
        assertEquals(55.5555555556, total / 216, 0.000001);
        assertEquals(0.9340659341, (total / 216 / 260) / (GambleRolls.expectedTwoDiceDelta() / 170), 0.000001);
        assertEquals(List.of("철 조각", "철", "구리", "금괴", "에메랄드", "다이아몬드"),
                java.util.Arrays.stream(GambleSlots.Symbol.values()).map(GambleSlots.Symbol::displayName).toList());
    }

}
