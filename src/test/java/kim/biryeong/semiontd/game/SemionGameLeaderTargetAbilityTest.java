package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.map.GameArena;
import org.junit.jupiter.api.Test;

final class SemionGameLeaderTargetAbilityTest {
    @Test
    void teamLeaderCanTargetLivingEnemyTeam() {
        Fixture fixture = fixture();

        LeaderTargetResult result = fixture.game.setLeaderTarget(fixture.redLeader, TeamId.BLUE);

        assertEquals(LeaderTargetResult.SUCCESS, result);
        assertEquals(TeamId.BLUE, fixture.redTeam.leaderTargeting().orElseThrow().targetTeamId().orElseThrow());
        assertEquals(3, fixture.redTeam.leaderTargeting().orElseThrow().cooldownRemainingRounds());
    }

    @Test
    void normalTeamMemberCannotTargetTeam() {
        Fixture fixture = fixture();

        assertEquals(LeaderTargetResult.NOT_TEAM_LEADER, fixture.game.setLeaderTarget(fixture.redMember, TeamId.BLUE));
    }

    @Test
    void leaderCannotTargetOwnTeam() {
        Fixture fixture = fixture();

        assertEquals(LeaderTargetResult.TARGET_SELF_TEAM, fixture.game.setLeaderTarget(fixture.redLeader, TeamId.RED));
    }

    @Test
    void leaderCannotTargetEliminatedTeam() {
        Fixture fixture = fixture();
        fixture.blueTeam.eliminate();

        assertEquals(LeaderTargetResult.TARGET_TEAM_NOT_ALIVE, fixture.game.setLeaderTarget(fixture.redLeader, TeamId.BLUE));
    }

    @Test
    void leaderCannotReuseDuringCooldown() {
        Fixture fixture = fixture();
        assertEquals(LeaderTargetResult.SUCCESS, fixture.game.setLeaderTarget(fixture.redLeader, TeamId.BLUE));

        assertEquals(LeaderTargetResult.COOLDOWN_ACTIVE, fixture.game.setLeaderTarget(fixture.redLeader, TeamId.GREEN));
    }

    @Test
    void commandFailureMessagesAreKorean() {
        assertEquals("팀장만 타깃을 지정할 수 있습니다.", LeaderTargetResult.NOT_TEAM_LEADER.message());
        assertEquals("아직 팀장 능력 쿨타임입니다.", LeaderTargetResult.COOLDOWN_ACTIVE.message());
    }

    private static Fixture fixture() {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), new GameArena(Map.of()));
        SemionTeam redTeam = game.teams().get(TeamId.RED);
        SemionTeam blueTeam = game.teams().get(TeamId.BLUE);
        SemionTeam greenTeam = game.teams().get(TeamId.GREEN);
        redTeam.activate();
        blueTeam.activate();
        greenTeam.activate();
        UUID redLeader = UUID.nameUUIDFromBytes("red-leader-target".getBytes());
        UUID redMember = UUID.nameUUIDFromBytes("red-member-target".getBytes());
        game.players().put(redLeader, player(redLeader, TeamId.RED, 1));
        game.players().put(redMember, player(redMember, TeamId.RED, 2));
        redTeam.setLeader(redLeader);
        return new Fixture(game, redTeam, blueTeam, redLeader, redMember);
    }

    private static SemionPlayer player(UUID uuid, TeamId teamId, int laneId) {
        return new SemionPlayer(uuid, uuid.toString(), teamId, laneId, new PlayerEconomy(EconomyConfig.defaultConfig()));
    }

    private record Fixture(SemionGame game, SemionTeam redTeam, SemionTeam blueTeam, UUID redLeader, UUID redMember) {
    }
}
