package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.map.GameArena;
import org.junit.jupiter.api.Test;

final class SemionGameLeaderCooldownTest {
    @Test
    void livingTeamCooldownTicksDownByRound() {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), new GameArena(Map.of()));
        SemionTeam redTeam = game.teams().get(TeamId.RED);
        redTeam.activate();
        UUID leader = UUID.nameUUIDFromBytes("cooldown-leader".getBytes());
        redTeam.setLeader(leader);
        redTeam.leaderTargeting().orElseThrow().use(TeamId.BLUE, 1, 3);

        game.tickLeaderCooldowns();
        game.tickLeaderCooldowns();
        game.tickLeaderCooldowns();

        assertEquals(0, redTeam.leaderTargeting().orElseThrow().cooldownRemainingRounds());
        assertTrue(redTeam.leaderTargeting().orElseThrow().canUse());
    }
}
