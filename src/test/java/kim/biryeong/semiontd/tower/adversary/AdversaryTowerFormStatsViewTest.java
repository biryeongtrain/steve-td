package kim.biryeong.semiontd.tower.adversary;

import java.util.ArrayList;
import java.util.List;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdversaryTowerFormStatsViewTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void everyFormAppendsDescriptionsWithoutReplacingSharedProgressLines() {
        var state = new AdversaryTowerFormStatsView.State(2, 1, 3, -1, 2, 4);
        for (FoxForm form : FoxForm.values()) {
            List<String> lines = new ArrayList<>(List.of("progress"));
            AdversaryTowerFormStatsView.append(lines, form, state);
            assertEquals("progress", lines.getFirst(), form.name());
            assertTrue(lines.size() > 1, form.name());
            assertTrue(lines.stream().noneMatch(line -> line.contains("{") || line.contains("NaN")), form.name());
        }
    }

    @Test
    void countersAreCapturedAndDisplayedWithExistingBounds() {
        var state = new AdversaryTowerFormStatsView.State(2, 999, 3, -1, 2, 4);
        List<String> lines = new ArrayList<>();
        AdversaryTowerFormStatsView.append(lines, FoxForm.SCULK_CORE, state);
        assertEquals("대기 중인 폭발: 4개", lines.getLast());
        lines.clear();
        AdversaryTowerFormStatsView.append(lines, FoxForm.MACE_EXECUTIONER, state);
        assertTrue(lines.getLast().startsWith("집중: 0틱 / 연속 적중: 2/"));
        lines.clear();
        AdversaryTowerFormStatsView.append(lines, FoxForm.BIG_GAME_TRACKER, state);
        int stages = AdversaryBalance.bigGameStreakMultipliers().length;
        assertEquals("조준 단계: " + stages + "/" + stages, lines.getLast());
    }
}
