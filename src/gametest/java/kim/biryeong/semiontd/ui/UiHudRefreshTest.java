package kim.biryeong.semiontd.ui;

import java.util.Map;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.map.GameArena;
import kim.biryeong.semiontd.tutorial.TutorialService.HighlightTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class UiHudRefreshTest {
    @GameTest
    public void refreshSummaryPreservesViewerSpecificLobbyComponents(GameTestHelper context) {
        var viewer = context.makeMockServerPlayerInLevel();
        var server = context.getLevel().getServer();
        var game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), new GameArena(Map.of()));
        for (MatchMode mode : MatchMode.values()) {
            for (boolean ready : new boolean[] {false, true, false}) {
                if (ready) {
                    game.markReady(viewer.getUUID());
                } else {
                    game.markNotReady(viewer.getUUID());
                }
                var lobby = UiLobbyRosterSummary.capture(server, game, mode);
                var original = SemionHudTextService.sidebarLinesFor(viewer, game, mode, server);
                var shared = SemionHudTextService.sidebarLinesFor(viewer, game, mode, server,
                        false, HighlightTarget.NONE, false, lobby);
                if (!original.equals(shared) || original.isEmpty()
                        || shared.stream().noneMatch(line -> line.getString().contains(ready ? "준비 완료" : "미준비"))) {
                    context.fail(net.minecraft.network.chat.Component.literal("Shared roster summary changed viewer readiness, lobby components or styles."));
                    return;
                }
            }
        }
        context.succeed();
    }
}
