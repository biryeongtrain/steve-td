package kim.biryeong.semiontd.tower.hero;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class HeroCompanionSkinInputGuiTest {
    @Test
    void validatesNamesWithVanillaRules() {
        assertTrue(HeroCompanionSkinInputGui.validPlayerName("Valid_Name16"));
        assertFalse(HeroCompanionSkinInputGui.validPlayerName(""));
        assertFalse(HeroCompanionSkinInputGui.validPlayerName("space name"));
        assertFalse(HeroCompanionSkinInputGui.validPlayerName("name-that-is-far-too-long"));
    }

    @Test
    void rejectsDuplicateAndStaleAsyncResults() {
        HeroCompanionSkinInputGui.SearchState state = new HeroCompanionSkinInputGui.SearchState();
        long first = state.begin();
        assertTrue(first >= 0);
        assertTrue(state.begin() < 0);
        assertTrue(state.accepts(first, "Player", "Player"));

        state.inputChanged();
        assertFalse(state.accepts(first, "Player", "Other"));
        long second = state.begin();
        assertTrue(second > first);
        state.finish(first);
        assertTrue(state.searching());
        assertTrue(state.accepts(second, "Other", " Other "));
        state.finish(second);
        assertFalse(state.searching());
    }
}
