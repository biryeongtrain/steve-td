package kim.biryeong.semiontd.buildguide;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.GameArena;
import org.junit.jupiter.api.Test;

final class BuildGuideServiceTest {
    @Test
    void lastRecordingSurvivesDelayedFinishUntilNextMatchStarts() {
        BuildGuideService service = new BuildGuideService(null);
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        SemionGame finishedGame = gameWithPlayer(playerId, "player");

        service.startMatch(finishedGame);
        service.recordSummon(finishedGame, playerId, "zombie", 10, 1, TeamId.BLUE, 1, 2);
        service.finishMatch(finishedGame, 3);

        // SemionGameManager finishes build recording as soon as the match enters ENDED,
        // then runs the normal delayed match-result finish path later. The second call must
        // not erase the already captured last-match build before the next match starts.
        service.finishMatch(finishedGame, 3);

        Optional<BuildGuide> published = service.publishLastRecording(playerId, "마지막 경기 빌드");
        assertTrue(published.isPresent());
        assertTrue(published.get().actions().stream().anyMatch(action -> action.type() == BuildActionType.SUMMON));

        service.startMatch(gameWithPlayer(playerId, "player"));

        assertTrue(service.publishLastRecording(playerId, "너무 늦은 저장").isEmpty());
    }

    private static SemionGame gameWithPlayer(UUID playerId, String playerName) {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), new GameArena(Map.of()));
        game.players().put(playerId, new SemionPlayer(
                playerId,
                playerName,
                TeamId.RED,
                1,
                new PlayerEconomy(EconomyConfig.defaultConfig())
        ));
        return game;
    }
}
