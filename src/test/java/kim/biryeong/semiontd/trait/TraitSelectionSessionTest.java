package kim.biryeong.semiontd.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class TraitSelectionSessionTest {
    private static final Identifier TEST_TRAIT =
            Identifier.fromNamespaceAndPath("semion-td", "selection_session_test");
    private static final UUID RED = UUID.nameUUIDFromBytes("trait-session-red".getBytes());
    private static final UUID BLUE = UUID.nameUUIDFromBytes("trait-session-blue".getBytes());
    private static final UUID SPECTATOR = UUID.nameUUIDFromBytes("trait-session-spectator".getBytes());

    @BeforeAll
    static void registerTestTrait() {
        BuiltInTraits.register();
        TraitRegistry.registerIfAbsent(new SemionTrait(
                TEST_TRAIT,
                Component.literal("선택 세션 테스트"),
                List.of()
        ) {
        });
    }

    @Test
    void activeParticipantsAllNeedBothSlotsBeforeComplete() {
        BuiltInTraits.register();
        TraitSelectionSession session = new TraitSelectionSession(plan(), Map.of(), 90);

        assertFalse(session.complete());
        assertEquals(TraitSelectionSession.SelectionResult.SELECTED,
                session.select(RED, TraitSlot.PRIMARY, TEST_TRAIT));
        assertEquals(TraitSelectionSession.SelectionResult.SELECTED,
                session.select(RED, TraitSlot.SECONDARY, BuiltInTraits.NONE_ID));
        assertFalse(session.complete());
        assertEquals(TraitSelectionSession.SelectionResult.SELECTED,
                session.select(BLUE, TraitSlot.PRIMARY, BuiltInTraits.NONE_ID));
        assertEquals(TraitSelectionSession.SelectionResult.SELECTED,
                session.select(BLUE, TraitSlot.SECONDARY, BuiltInTraits.NONE_ID));

        assertTrue(session.complete());
    }

    @Test
    void spectatorsCannotSelectAndDoNotBlockCompletion() {
        BuiltInTraits.register();
        TraitSelectionSession session = new TraitSelectionSession(plan(), Map.of(), 90);

        assertEquals(TraitSelectionSession.SelectionResult.NOT_PARTICIPANT,
                session.select(SPECTATOR, TraitSlot.PRIMARY, BuiltInTraits.NONE_ID));
        completeNone(session, RED);
        completeNone(session, BLUE);

        assertTrue(session.complete());
    }

    @Test
    void duplicateNonNoneTraitsAreRejected() {
        BuiltInTraits.register();
        TraitSelectionSession session = new TraitSelectionSession(plan(), Map.of(), 90);

        assertEquals(TraitSelectionSession.SelectionResult.SELECTED,
                session.select(RED, TraitSlot.PRIMARY, TEST_TRAIT));
        assertEquals(TraitSelectionSession.SelectionResult.DUPLICATE_TRAIT,
                session.select(RED, TraitSlot.SECONDARY, TEST_TRAIT));
    }

    @Test
    void snapshotFillsMissingSlotsWithNoneOnTimeout() {
        BuiltInTraits.register();
        TraitSelectionSession session = new TraitSelectionSession(plan(), Map.of(), 1);
        session.select(RED, TraitSlot.PRIMARY, TEST_TRAIT);

        assertTrue(session.tick());
        TraitSelectionSnapshot snapshot = session.snapshot();

        assertEquals(TEST_TRAIT, snapshot.loadoutOrDefault(RED).primaryTraitId());
        assertEquals(BuiltInTraits.NONE_ID, snapshot.loadoutOrDefault(RED).secondaryTraitId());
        assertEquals(TraitLoadout.none(), snapshot.loadoutOrDefault(BLUE));
    }

    private static void completeNone(TraitSelectionSession session, UUID playerId) {
        session.select(playerId, TraitSlot.PRIMARY, BuiltInTraits.NONE_ID);
        session.select(playerId, TraitSlot.SECONDARY, BuiltInTraits.NONE_ID);
    }

    private static ParticipantSelectionPlan plan() {
        return new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(RED, "red", TeamId.RED, 1),
                        new AssignedParticipant(BLUE, "blue", TeamId.BLUE, 1)
                ),
                Set.of(SPECTATOR),
                2
        );
    }
}
