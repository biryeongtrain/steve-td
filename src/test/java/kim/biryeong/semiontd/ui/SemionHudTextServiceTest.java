package kim.biryeong.semiontd.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.GameArena;
import org.junit.jupiter.api.Test;

final class SemionHudTextServiceTest {
    @Test
    void activePlayerEconomyActionbarMarkupKeepsExistingElements() {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), new GameArena(Map.of()));
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        PlayerEconomy economy = new PlayerEconomy(EconomyConfig.defaultConfig());
        economy.overrideStartingValues(123, 45, 67, 8);
        SemionPlayer player = new SemionPlayer(playerId, "player", TeamId.RED, 1, economy);
        game.players().put(playerId, player);

        String actionbar = SemionHudTextService.actionbarMarkupFor(player, game);

        assertTrue(actionbar.contains("◆ 다이아 123"));
        assertTrue(actionbar.contains("⬢ 에메랄드 45"));
        assertTrue(actionbar.contains("+ 수입 67"));
        assertTrue(actionbar.contains("에메랄드/s 8"));
        assertTrue(actionbar.contains("▣ 타워"));
    }
}
