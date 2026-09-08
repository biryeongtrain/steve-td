package kim.biryeong.semiontd.tower.gamble;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class GamblePokerTest {
    @Test
    void exhaustsEveryThreeCardCombinationWithTheAgreedProbabilitiesAndScores() {
        Map<GamblePoker.Kind, Integer> counts = new EnumMap<>(GamblePoker.Kind.class);
        int destroyed = 0;
        int weak = 0;
        int totalScore = 0;
        double totalHealth = 0;
        for (int a = 0; a < 52; a++) {
            for (int b = a + 1; b < 52; b++) {
                for (int c = b + 1; c < 52; c++) {
                    var hand = GamblePoker.evaluate(a, b, c);
                    counts.merge(hand.kind(), 1, Integer::sum);
                    destroyed += hand.destroyed() ? 1 : 0;
                    weak += hand.weak() ? 1 : 0;
                    totalScore += hand.score();
                    totalHealth += hand.destroyed() ? 0 : 180 + hand.healthBonus(1000, 170.0 / 3, 620, 1170);
                    assertEquals(hand.score(), GamblePoker.evaluate(c, a, b).score());
                }
            }
        }
        assertEquals(Map.of(GamblePoker.Kind.HIGH_CARD, 16440, GamblePoker.Kind.PAIR, 3744,
                GamblePoker.Kind.FLUSH, 1096, GamblePoker.Kind.STRAIGHT, 720,
                GamblePoker.Kind.THREE_OF_A_KIND, 52, GamblePoker.Kind.STRAIGHT_FLUSH, 48), counts);
        assertEquals(1800, destroyed);
        assertEquals(4920, weak);
        assertEquals(588992, totalScore);
        assertEquals(728.786975, totalHealth / 22100, 0.000001);

        assertEquals(GamblePoker.Kind.STRAIGHT, GamblePoker.evaluate(12, 13, 27).kind()); // A,2,3
        assertEquals(GamblePoker.Kind.STRAIGHT, GamblePoker.evaluate(10, 24, 38).kind()); // Q,K,A
        assertEquals(GamblePoker.Kind.HIGH_CARD, GamblePoker.evaluate(11, 25, 26).kind()); // K,A,2
        assertEquals(37, GamblePoker.evaluate(0, 13, 28).score());
        assertEquals(49, GamblePoker.evaluate(12, 25, 28).score());
        assertThrows(IllegalArgumentException.class, () -> GamblePoker.evaluate(0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> GamblePoker.evaluate(-1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> GamblePoker.evaluate(0, 1, 52));
            assertEquals(3, GamblePoker.draw(bound -> bound - 1).cards().stream().distinct().count());
    }

    @Test
    void specialAbilitiesRequireBothTheHandAndEnoughWeightedScore() {
        var flush = GamblePoker.evaluate(0, 3, 7);
        var straight = GamblePoker.evaluate(0, 14, 28);
        var triple = GamblePoker.evaluate(0, 13, 26);
        assertFalse(flush.qualifiesForDebuffs(999, 50000));
        assertTrue(flush.qualifiesForDebuffs(1000, 50000));
        assertFalse(straight.qualifiesForDebuffs(909, 50000));
        assertTrue(straight.qualifiesForDebuffs(910, 50000));
        assertFalse(triple.qualifiesForDebuffs(833, 50000));
        assertTrue(triple.qualifiesForDebuffs(834, 50000));
        assertFalse(GamblePoker.evaluate(12, 25, 28).qualifiesForDebuffs(1000, 50000));
        assertEquals(60000, triple.weightedScore(1000));
        assertEquals(620, GamblePoker.evaluate(10, 13, 28).healthBonus(1000, 170.0 / 3, 620, 1170));
        assertEquals(1170, triple.healthBonus(1000, 170.0 / 3, 620, 1170));
        assertEquals(234, triple.healthBonus(200, 170.0 / 3, 620, 1170));
        assertTrue(GamblePoker.validBet(200));
        assertTrue(GamblePoker.validBet(1000));
        assertFalse(GamblePoker.validBet(199));
        assertFalse(GamblePoker.validBet(1001));
        assertThrows(IllegalArgumentException.class, () -> triple.weightedScore(Long.MAX_VALUE));
    }

    @Test
    void qualifyingHandsFixDebuffCountAndRandomizeEffectsWithoutDuplicates() {
        var subsets = new java.util.HashSet<java.util.List<GamblePoker.DeathDebuff>>();
        var hands = java.util.List.of(GamblePoker.evaluate(0, 3, 7), GamblePoker.evaluate(0, 14, 28),
                GamblePoker.evaluate(0, 13, 26), GamblePoker.evaluate(0, 1, 2));
        for (int handIndex = 0; handIndex < hands.size(); handIndex++) {
            int count = Math.min(handIndex + 1, 3);
            for (int first = 0; first < 3; first++) {
                for (int second = 0; second < 2; second++) {
                    int[] draws = {first, second, 0};
                    int[] index = {0};
                    var result = GamblePoker.drawDebuffs(hands.get(handIndex), 1000, 50000,
                            bound -> draws[index[0]++]);
                    assertEquals(count, result.size());
                    assertEquals(result.size(), result.stream().distinct().count());
                    subsets.add(result);
                }
            }
        }
        assertEquals(7, subsets.size());
        assertEquals(java.util.List.of(), GamblePoker.drawDebuffs(GamblePoker.evaluate(0, 3, 7), 999, 50000,
                bound -> { throw new AssertionError("Ineligible bets must not roll debuffs"); }));
    }

}
