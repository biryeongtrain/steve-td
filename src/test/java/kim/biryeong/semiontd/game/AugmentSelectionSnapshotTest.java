package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class AugmentSelectionSnapshotTest {
    @Test
    void rejectsImpossibleDecisionCombinations() {
        assertThrows(IllegalArgumentException.class,
                () -> new AugmentSelectionSnapshot(6, "SILVER", "card", "SELECTED", null));
        assertThrows(IllegalArgumentException.class,
                () -> new AugmentSelectionSnapshot(5, "SILVER", null, "SELECTED", null));
        assertThrows(IllegalArgumentException.class,
                () -> new AugmentSelectionSnapshot(15, "GOLD", "card", "SKIPPED", "TIMEOUT"));
        assertThrows(IllegalArgumentException.class,
                () -> new AugmentSelectionSnapshot(25, "PRISMATIC", null, "SKIPPED", null));
    }
}
