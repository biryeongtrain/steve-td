package kim.biryeong.semiontd.tower.gamble;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GambleAugmentsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void insuranceAddsOnlyHalfOppositeCombatEffectsAndPreservesBadRoll() {
        for (var type : List.of(GambleTowers.DICE_T1, GambleTowers.SPECTATOR_T1)) {
            for (int face = 1; face <= 2; face++) {
                var original = GambleSupportRolls.roll(type, face, RandomSource.create(13));
                var opposite = GambleSupportRolls.roll(type, 7 - face, RandomSource.create(13));
                var insured = GambleSupportRolls.roll(type, face, RandomSource.create(13), .5);
                assertEquals(original, insured.subList(0, original.size()));
                assertEquals(original.size() + opposite.size(), insured.size());
                for (int i = 0; i < opposite.size(); i++) {
                    assertEquals(opposite.get(i).type(), insured.get(original.size() + i).type());
                    assertEquals(opposite.get(i).magnitude() * .5,
                            insured.get(original.size() + i).magnitude(), 1e-9);
                }
            }
        }
    }

    @Test
    void doubleDiceUsesTwoActualLegalRollsAndOnlyTheHighest() {
        for (int minimum = 1; minimum <= 6; minimum++) {
            for (int seed = 0; seed < 30; seed++) {
                RandomSource expected = RandomSource.create(seed);
                int first = minimum + expected.nextInt(7 - minimum);
                int second = minimum + expected.nextInt(7 - minimum);
                assertEquals(Math.max(first, second), GambleSupportRolls.rollFace(minimum, RandomSource.create(seed), true));
                assertEquals(first, GambleSupportRolls.rollFace(minimum, RandomSource.create(seed), false));
            }
        }
    }

    @Test
    void bottomKingDoublesOnlyScoreLossAndReversesTheInsuredStatLoss() {
        GambleState insured = GambleState.EMPTY.recordAbility(GambleAbility.LOSS_INSURANCE, 0, "insurance");
        assertEquals(-80, GambleRewards.settledScore(-40, 2), 1e-9);
        assertEquals(70, GambleRewards.settledScore(70, 2), 1e-9);
        assertEquals(20, GambleRewards.settledStatDelta(GambleState.EMPTY, -20, true), 1e-9);
        assertEquals(-20, GambleRewards.settledStatDelta(GambleState.EMPTY, -20, false), 1e-9);
        assertEquals(20 * (1 - GambleBalance.lossInsuranceReduction()),
                GambleRewards.settledStatDelta(insured, -20, true), 1e-9);
    }

    @Test
    void onePurchaseSettlesEveryAttemptStopsOnSuccessAndChargesAllFailurePenaltyOnce() {
        Set<Integer> successfulAttempts = new HashSet<>();
        boolean sawAllFailure = false;
        for (int seed = 0; seed < 200; seed++) {
            GamblerTower tower = tower("job_gamble_p", "job_gamble_g2");
            int attempts = tower.resolvePurchase(GambleBet.ODD, RandomSource.create(seed));
            assertEquals(attempts, tower.state().totalBets());
            assertTrue(attempts >= 1 && attempts <= 3);
            if (tower.jackpotCharges() == 1) {
                successfulAttempts.add(attempts);
                assertEquals(70 - 80 * (attempts - 1), tower.gambleScore(), 1e-9);
            } else {
                sawAllFailure = true;
                assertEquals(3, attempts);
                assertEquals(-243, tower.gambleScore(), 1e-9);
                assertTrue(tower.state().damageDelta() > 0 || tower.state().maxHealthDelta() > 0
                        || tower.state().rangeDelta() > 0);
            }
        }
        assertEquals(Set.of(1, 2, 3), successfulAttempts);
        assertTrue(sawAllFailure);
    }

    @Test
    void jackpotStorageCapsAtThreeSurvivesPromotionAndClearsBetweenRounds() {
        GamblerTower tower = tower("job_gamble_p");
        for (int seed = 0; seed < 100 && tower.jackpotCharges() < 3; seed++) {
            tower.resolvePurchase(GambleBet.ODD, RandomSource.create(seed));
        }
        assertEquals(3, tower.jackpotCharges());
        tower.resolvePurchase(GambleBet.ODD, RandomSource.create(4));
        assertEquals(3, tower.jackpotCharges());
        GamblerTower promoted = tower();
        promoted.copyFrom(tower, 0);
        assertEquals(3, promoted.jackpotCharges());
        promoted.resetForRound(null);
        assertEquals(0, promoted.jackpotCharges());
    }

    private static GamblerTower tower(String... cards) {
        GridPosition position = new GridPosition(0, 0, 0);
        GamblerTower tower = new GamblerTower(GambleTowers.GAMBLER, UUID.randomUUID(), TeamId.RED, 1, position, position);
        tower.syncAugments(new AugmentSnapshot(AugmentConfig.defaults(), Arrays.stream(cards).map(id ->
                new PlayerAugmentState.Selection(5, AugmentRarity.GOLD, id,
                        PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())).toList()), null);
        return tower;
    }
}
