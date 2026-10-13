package kim.biryeong.semiontd.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.GameArena;
import org.junit.jupiter.api.Test;

final class CommandGameStatusTest {
    @Test
    void noGameAndEmptyRosterRetainTheCommandMessages() {
        SemionGameManager manager = new SemionGameManager();
        SemionGame game = emptyGame();

        assertEquals(List.of("상태 activeGame=false, phase=NONE, matchMode=NORMAL, lobbyLoaded=false, arenaLoaded=false"),
                SemionCommands.statusLines(manager));
        assertEquals(List.of("참가자 없음", "관전자 없음"), SemionCommands.playerStatusLines(game));
        assertEquals(List.of("활성 라인 없음"), SemionCommands.laneStatusLines(game));
    }

    @Test
    void playerReportsSortByNameAndReflectRosterChangesOnEveryRequest() {
        SemionGame game = emptyGame();
        UUID zulu = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID alpha = UUID.fromString("00000000-0000-0000-0000-000000000002");
        game.players().put(zulu, new SemionPlayer(zulu, "Zulu", TeamId.RED, 1,
                new PlayerEconomy(EconomyConfig.defaultConfig())));
        game.players().put(alpha, new SemionPlayer(alpha, "Alpha", TeamId.BLUE, 2,
                new PlayerEconomy(EconomyConfig.defaultConfig())));

        assertEquals(List.of(
                "참가자 Alpha uuid=" + alpha + ", team=BLUE, lane=2, eliminated=false",
                "참가자 Zulu uuid=" + zulu + ", team=RED, lane=1, eliminated=false",
                "관전자 없음"
        ), SemionCommands.playerStatusLines(game));

        game.players().remove(alpha);
        assertEquals(List.of(
                "참가자 Zulu uuid=" + zulu + ", team=RED, lane=1, eliminated=false",
                "관전자 없음"
        ), SemionCommands.playerStatusLines(game));
    }

    private static SemionGame emptyGame() {
        return new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), new GameArena(Map.of()));
    }
}
