package kim.biryeong.semiontd.tower.magicschool;

import java.util.Map;
import java.util.UUID;

final class MagicSchoolTestSetup {
    private MagicSchoolTestSetup() {}

    static void resetCurriculumRoundLimit(UUID owner) {
        try {
            var statesField = MagicSchoolCurriculum.class.getDeclaredField("STATES");
            statesField.setAccessible(true);
            Object state = ((Map<?, ?>) statesField.get(null)).get(owner);
            if (state == null) return;
            var roundField = state.getClass().getDeclaredField("lastUpgradeRound");
            roundField.setAccessible(true);
            roundField.setInt(state, -1);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot prepare curriculum fixture", exception);
        }
    }
}
