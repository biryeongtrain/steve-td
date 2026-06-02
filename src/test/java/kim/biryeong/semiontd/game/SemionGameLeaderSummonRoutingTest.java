package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.map.GameArena;
import org.junit.jupiter.api.Test;

final class SemionGameLeaderSummonRoutingTest {
    @Test
    void targetTeamForSummonUsesLeaderTarget() {
        Fixture fixture = fixture();
        fixture.redTeam.leaderTargeting().orElseThrow().use(TeamId.BLUE, 1, 3);

        Optional<SemionTeam> targetTeam = fixture.game.targetTeamForSummon(TeamId.RED);

        assertEquals(TeamId.BLUE, targetTeam.orElseThrow().id());
    }

    @Test
    void invalidLeaderTargetIsClearedAndFallsBackToLivingEnemy() {
        Fixture fixture = fixture();
        fixture.redTeam.leaderTargeting().orElseThrow().use(TeamId.BLUE, 1, 3);
        fixture.blueTeam.eliminate();

        Optional<SemionTeam> targetTeam = fixture.game.targetTeamForSummon(TeamId.RED);

        assertTrue(targetTeam.isPresent());
        assertNotEquals(TeamId.BLUE, targetTeam.orElseThrow().id());
        assertEquals(Optional.empty(), fixture.redTeam.leaderTargeting().orElseThrow().targetTeamId());
    }

    private static Fixture fixture() {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), new GameArena(Map.of()));
        SemionTeam redTeam = game.teams().get(TeamId.RED);
        SemionTeam blueTeam = game.teams().get(TeamId.BLUE);
        SemionTeam greenTeam = game.teams().get(TeamId.GREEN);
        redTeam.activate();
        blueTeam.activate();
        greenTeam.activate();
        UUID redLeader = UUID.nameUUIDFromBytes("red-leader-routing".getBytes());
        game.players().put(redLeader, new SemionPlayer(redLeader, "red", TeamId.RED, 1, new PlayerEconomy(EconomyConfig.defaultConfig())));
        redTeam.setLeader(redLeader);
        return new Fixture(game, redTeam, blueTeam);
    }

    private record Fixture(SemionGame game, SemionTeam redTeam, SemionTeam blueTeam) {
    }
}
