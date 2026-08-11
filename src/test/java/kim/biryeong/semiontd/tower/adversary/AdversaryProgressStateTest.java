package kim.biryeong.semiontd.tower.adversary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdversaryProgressStateTest {
    @Test
    void enhancedRivalKillsCountTwoWithoutChangingPastScore() {
        AdversaryProgressState state = new AdversaryProgressState();
        UUID rival = UUID.nameUUIDFromBytes("enhanced-breeze".getBytes());

        state.registerRival(rival, RivalKind.BREEZE);
        state.recordRivalKill(rival, RivalKind.BREEZE, false);
        state.recordRivalKill(rival, RivalKind.BREEZE, true);

        assertEquals(3, state.score(RivalKind.BREEZE));
        assertEquals(3, state.contribution(rival));
    }

    @Test
    void evolutionAppliesOnlyOneStageAndRequiresAnIntermediateWave() {
        AdversaryProgressState state = new AdversaryProgressState();
        UUID rival = UUID.nameUUIDFromBytes("rapid-route".getBytes());

        state.reconcileRivals(List.of(new RivalContribution(rival, RivalKind.BREEZE, 50)));

        assertEquals(FoxForm.BREEZE, state.pendingForm().orElseThrow());
        assertEquals(FoxForm.BREEZE, state.applyPreparationTransition().orElseThrow().current());
        assertEquals(FoxForm.BREEZE, state.currentForm());
        assertTrue(state.applyPreparationTransition().isEmpty());

        state.recordCompletedWave();
        assertEquals(FoxForm.GOLDEN_FANG, state.pendingForm().orElseThrow());
        assertEquals(FoxForm.GOLDEN_FANG, state.applyPreparationTransition().orElseThrow().current());
    }

    @Test
    void sellingContributorsDemotesButKeepsRouteAndFinalLocks() {
        AdversaryProgressState state = new AdversaryProgressState();
        UUID foundation = UUID.nameUUIDFromBytes("breeze-foundation".getBytes());
        UUID excess = UUID.nameUUIDFromBytes("breeze-excess".getBytes());

        state.reconcileRivals(List.of(
                new RivalContribution(foundation, RivalKind.BREEZE, 12),
                new RivalContribution(excess, RivalKind.BREEZE, 38)
        ));
        state.applyPreparationTransition();
        state.recordCompletedWave();
        state.applyPreparationTransition();
        assertEquals(FoxForm.GOLDEN_FANG, state.currentForm());

        state.reconcileRivals(List.of(new RivalContribution(foundation, RivalKind.BREEZE, 12)));
        assertEquals(FoxForm.BREEZE, state.currentForm());
        assertEquals(FoxRoute.RAPID, state.lockedRoute().orElseThrow());
        assertEquals(FoxForm.GOLDEN_FANG, state.lockedFinalForm().orElseThrow());

        state.reconcileRivals(List.of());
        assertEquals(FoxForm.BASE, state.currentForm());
        assertEquals(FoxRoute.RAPID, state.lockedRoute().orElseThrow());
        assertEquals(FoxForm.GOLDEN_FANG, state.lockedFinalForm().orElseThrow());

        state.reconcileRivals(List.of(
                new RivalContribution(foundation, RivalKind.BREEZE, 12),
                new RivalContribution(excess, RivalKind.BREEZE, 38)
        ));
        assertEquals(FoxForm.BREEZE, state.pendingForm().orElseThrow());
        state.applyPreparationTransition();
        state.reconcileRivals(List.of(
                new RivalContribution(foundation, RivalKind.BREEZE, 12),
                new RivalContribution(excess, RivalKind.BREEZE, 38)
        ));
        assertEquals(FoxForm.GOLDEN_FANG, state.pendingForm().orElseThrow());
    }

    @Test
    void lastScoringKindBreaksACompletedFinalTie() {
        AdversaryProgressState state = new AdversaryProgressState();
        state.setCurrentForm(FoxForm.BREEZE);
        state.recordCompletedWave();
        state.noteScoringKind(RivalKind.POLAR_BEAR);

        state.reconcileRivals(List.of(
                new RivalContribution(UUID.randomUUID(), RivalKind.BREEZE, 50),
                new RivalContribution(UUID.randomUUID(), RivalKind.POLAR_BEAR, 20)
        ));

        assertEquals(FoxForm.SHIELD_BEARER, state.pendingForm().orElseThrow());
    }

    @Test
    void allPublishedRecipesExactlyMatchTheApprovedRequirements() {
        assertRecipe(FoxForm.BREEZE, false, Map.of(RivalKind.BREEZE, 12));
        assertRecipe(FoxForm.BELL_KEEPER, false, Map.of(RivalKind.PHANTOM, 14));
        assertRecipe(FoxForm.TRACKER, false, Map.of(RivalKind.CREEPER, 16));
        assertRecipe(FoxForm.ECHO_FOX, false, Map.of(RivalKind.POLAR_BEAR, 18));
        assertRecipe(FoxForm.GOLDEN_FANG, false, Map.of(RivalKind.BREEZE, 50));
        assertRecipe(FoxForm.SHIELD_BEARER, false, Map.of(
                RivalKind.BREEZE, 30,
                RivalKind.POLAR_BEAR, 20
        ));
        assertRecipe(FoxForm.BEACON_KEEPER, false, Map.of(
                RivalKind.PHANTOM, 50,
                RivalKind.POLAR_BEAR, 25
        ));
        assertRecipe(FoxForm.OMINOUS_HEXER, false, Map.of(
                RivalKind.PHANTOM, 50,
                RivalKind.CREEPER, 30
        ));
        assertRecipe(FoxForm.FIREWORK_PIERCER, false, Map.of(
                RivalKind.CREEPER, 60,
                RivalKind.BREEZE, 30
        ));
        assertRecipe(FoxForm.BIG_GAME_TRACKER, false, Map.of(
                RivalKind.CREEPER, 60,
                RivalKind.POLAR_BEAR, 30
        ));
        assertRecipe(FoxForm.MACE_EXECUTIONER, false, Map.of(
                RivalKind.POLAR_BEAR, 80,
                RivalKind.BREEZE, 40
        ));
        assertRecipe(FoxForm.SCULK_CORE, false, Map.of(
                RivalKind.POLAR_BEAR, 100,
                RivalKind.PHANTOM, 50,
                RivalKind.CREEPER, 40
        ));
    }

    @Test
    void finalTieBreakUsesTheLastUniquelyConnectedRivalAcrossAllRoutes() {
        assertFinalChoice(
                FoxForm.BREEZE,
                RivalKind.POLAR_BEAR,
                Map.of(RivalKind.BREEZE, 50, RivalKind.POLAR_BEAR, 20),
                FoxForm.SHIELD_BEARER
        );
        assertFinalChoice(
                FoxForm.BELL_KEEPER,
                RivalKind.CREEPER,
                Map.of(RivalKind.PHANTOM, 50, RivalKind.POLAR_BEAR, 25, RivalKind.CREEPER, 30),
                FoxForm.OMINOUS_HEXER
        );
        assertFinalChoice(
                FoxForm.TRACKER,
                RivalKind.POLAR_BEAR,
                Map.of(RivalKind.CREEPER, 60, RivalKind.BREEZE, 30, RivalKind.POLAR_BEAR, 30),
                FoxForm.BIG_GAME_TRACKER
        );
        assertFinalChoice(
                FoxForm.ECHO_FOX,
                RivalKind.PHANTOM,
                Map.of(
                        RivalKind.POLAR_BEAR, 100,
                        RivalKind.BREEZE, 40,
                        RivalKind.PHANTOM, 50,
                        RivalKind.CREEPER, 40
                ),
                FoxForm.SCULK_CORE
        );
    }

    @Test
    void sellingBeforePreparationCancelsAQueuedScoreEvolution() {
        AdversaryProgressState state = new AdversaryProgressState();
        UUID rival = UUID.nameUUIDFromBytes("queued-sale".getBytes());

        state.reconcileRivals(List.of(new RivalContribution(rival, RivalKind.BREEZE, 12)));
        assertEquals(FoxForm.BREEZE, state.pendingForm().orElseThrow());

        state.reconcileRivals(List.of());
        assertTrue(state.pendingForm().isEmpty());
        assertEquals(FoxForm.BASE, state.currentForm());
    }

    private static void assertRecipe(
            FoxForm form,
            boolean hidden,
            Map<RivalKind, Integer> expected
    ) {
        EvolutionRecipe recipe = form.recipe().orElseThrow();
        assertEquals(expected, recipe.requirements());
        assertEquals(hidden, recipe.hidden());
    }

    private static void assertFinalChoice(
            FoxForm intermediate,
            RivalKind lastKind,
            Map<RivalKind, Integer> scores,
            FoxForm expected
    ) {
        AdversaryProgressState state = new AdversaryProgressState();
        state.setCurrentForm(intermediate);
        state.recordCompletedWave();
        state.noteScoringKind(lastKind);
        state.reconcileRivals(contributions(scores));

        assertEquals(expected, state.pendingForm().orElseThrow());
    }

    private static List<RivalContribution> contributions(Map<RivalKind, Integer> scores) {
        EnumMap<RivalKind, Integer> ordered = new EnumMap<>(RivalKind.class);
        ordered.putAll(scores);
        List<RivalContribution> contributions = new ArrayList<>();
        ordered.forEach((kind, score) -> contributions.add(new RivalContribution(
                UUID.nameUUIDFromBytes((kind.name() + score).getBytes()),
                kind,
                score
        )));
        return contributions;
    }

}
