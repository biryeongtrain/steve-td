package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.map.GameArena;
import org.junit.jupiter.api.Test;

final class TeamMoneyTransferTest {
    @Test
    void requestExceedingRoundLimitFailsWithConfiguredMaximum() {
        SemionGame game = newGame(EconomyConfig.defaultConfig());
        UUID receiver = addPlayer(game, "receiver", TeamId.BLUE, 1, 200);

        TeamMoneyTransferResult result = game.requestTeamMoney(receiver, 31);

        assertEquals(TeamMoneyTransferResultType.AMOUNT_EXCEEDS_ROUND_LIMIT, result.type());
        assertEquals(30, result.maxAllowedAmount());
        assertTrue(result.requestId().isEmpty());
    }

    @Test
    void sameTeamSenderAcceptsRequestAndTransfersDiamondsOnce() {
        SemionGame game = newGame(EconomyConfig.defaultConfig());
        UUID receiver = addPlayer(game, "receiver", TeamId.BLUE, 1, 200);
        UUID sender = addPlayer(game, "sender", TeamId.BLUE, 2, 200);

        TeamMoneyTransferResult request = game.requestTeamMoney(receiver, 30);
        TeamMoneyTransferResult accepted = game.acceptTeamMoneyRequest(sender, request.requestId().orElseThrow());
        TeamMoneyTransferResult duplicate = game.acceptTeamMoneyRequest(sender, request.requestId().orElseThrow());

        assertEquals(TeamMoneyTransferResultType.SUCCESS, request.type());
        assertEquals(TeamMoneyTransferResultType.SUCCESS, accepted.type());
        assertEquals(170, game.players().get(sender).economy().diamond());
        assertEquals(230, game.players().get(receiver).economy().diamond());
        assertEquals(TeamMoneyTransferResultType.REQUEST_NOT_FOUND, duplicate.type());
    }

    @Test
    void senderMustBeDifferentSameTeamPlayer() {
        SemionGame game = newGame(EconomyConfig.defaultConfig());
        UUID receiver = addPlayer(game, "receiver", TeamId.BLUE, 1, 200);
        UUID enemy = addPlayer(game, "enemy", TeamId.RED, 1, 200);

        TeamMoneyTransferResult request = game.requestTeamMoney(receiver, 30);

        assertEquals(
                TeamMoneyTransferResultType.SELF_TRANSFER,
                game.acceptTeamMoneyRequest(receiver, request.requestId().orElseThrow()).type()
        );
        assertEquals(
                TeamMoneyTransferResultType.NOT_TEAMMATE,
                game.acceptTeamMoneyRequest(enemy, request.requestId().orElseThrow()).type()
        );
    }

    @Test
    void insufficientSenderDoesNotConsumeRequest() {
        SemionGame game = newGame(EconomyConfig.defaultConfig());
        UUID receiver = addPlayer(game, "receiver", TeamId.BLUE, 1, 200);
        UUID poorSender = addPlayer(game, "poor", TeamId.BLUE, 2, 5);
        UUID richSender = addPlayer(game, "rich", TeamId.BLUE, 3, 200);

        TeamMoneyTransferResult request = game.requestTeamMoney(receiver, 30);
        TeamMoneyTransferResult poorResult = game.acceptTeamMoneyRequest(poorSender, request.requestId().orElseThrow());
        TeamMoneyTransferResult richResult = game.acceptTeamMoneyRequest(richSender, request.requestId().orElseThrow());

        assertEquals(TeamMoneyTransferResultType.NOT_ENOUGH_DIAMOND, poorResult.type());
        assertEquals(5, game.players().get(poorSender).economy().diamond());
        assertEquals(TeamMoneyTransferResultType.SUCCESS, richResult.type());
        assertEquals(170, game.players().get(richSender).economy().diamond());
        assertEquals(230, game.players().get(receiver).economy().diamond());
    }

    @Test
    void receiverCooldownBlocksUntilConfiguredRoundGap() throws Exception {
        SemionGame game = newGame(EconomyConfig.defaultConfig());
        UUID receiver = addPlayer(game, "receiver", TeamId.BLUE, 1, 200);
        UUID firstSender = addPlayer(game, "first", TeamId.BLUE, 2, 200);
        UUID secondSender = addPlayer(game, "second", TeamId.BLUE, 3, 200);

        TeamMoneyTransferResult first = game.requestTeamMoney(receiver, 30);
        assertEquals(TeamMoneyTransferResultType.SUCCESS, game.acceptTeamMoneyRequest(firstSender, first.requestId().orElseThrow()).type());

        TeamMoneyTransferResult immediate = game.requestTeamMoney(receiver, 30);
        setCurrentRound(game, 3);
        TeamMoneyTransferResult beforeGap = game.requestTeamMoney(receiver, 30);
        setCurrentRound(game, 4);
        TeamMoneyTransferResult afterGap = game.requestTeamMoney(receiver, 30);
        TeamMoneyTransferResult accepted = game.acceptTeamMoneyRequest(secondSender, afterGap.requestId().orElseThrow());

        assertEquals(TeamMoneyTransferResultType.RECEIVE_COOLDOWN_ACTIVE, immediate.type());
        assertEquals(3, immediate.remainingCooldownRounds());
        assertEquals(TeamMoneyTransferResultType.RECEIVE_COOLDOWN_ACTIVE, beforeGap.type());
        assertEquals(1, beforeGap.remainingCooldownRounds());
        assertEquals(TeamMoneyTransferResultType.SUCCESS, afterGap.type());
        assertEquals(TeamMoneyTransferResultType.SUCCESS, accepted.type());
    }

    @Test
    void requesterCannotSpamOpenRequestsInSameRound() {
        SemionGame game = newGame(EconomyConfig.defaultConfig());
        UUID receiver = addPlayer(game, "receiver", TeamId.BLUE, 1, 200);
        UUID sender = addPlayer(game, "sender", TeamId.BLUE, 2, 200);

        TeamMoneyTransferResult first = game.requestTeamMoney(receiver, 10);
        TeamMoneyTransferResult second = game.requestTeamMoney(receiver, 20);

        assertEquals(TeamMoneyTransferResultType.SUCCESS, first.type());
        assertEquals(TeamMoneyTransferResultType.REQUEST_ALREADY_OPEN, second.type());
        assertEquals(TeamMoneyTransferResultType.SUCCESS, game.acceptTeamMoneyRequest(sender, first.requestId().orElseThrow()).type());
        assertEquals(190, game.players().get(sender).economy().diamond());
        assertEquals(210, game.players().get(receiver).economy().diamond());
    }

    @Test
    void openRequestExpiresAfterRoundChanges() throws Exception {
        SemionGame game = newGame(EconomyConfig.defaultConfig());
        UUID receiver = addPlayer(game, "receiver", TeamId.BLUE, 1, 200);
        UUID sender = addPlayer(game, "sender", TeamId.BLUE, 2, 200);

        TeamMoneyTransferResult request = game.requestTeamMoney(receiver, 10);
        setCurrentRound(game, 2);

        assertEquals(
                TeamMoneyTransferResultType.REQUEST_EXPIRED,
                game.acceptTeamMoneyRequest(sender, request.requestId().orElseThrow()).type()
        );
        assertEquals(200, game.players().get(sender).economy().diamond());
        assertEquals(200, game.players().get(receiver).economy().diamond());
    }

    @Test
    void requestsAreRejectedAfterMatchEnds() throws Exception {
        SemionGame game = newGame(EconomyConfig.defaultConfig());
        UUID receiver = addPlayer(game, "receiver", TeamId.BLUE, 1, 200);

        setPhase(game, RoundPhase.ENDED);

        assertEquals(TeamMoneyTransferResultType.MATCH_ENDED, game.requestTeamMoney(receiver, 10).type());
    }

    private static SemionGame newGame(EconomyConfig economyConfig) {
        SemionGame game = new SemionGame(economyConfig, WaveConfig.defaultConfig(), new GameArena(Map.of()));
        game.teams().get(TeamId.BLUE).activate();
        game.teams().get(TeamId.RED).activate();
        return game;
    }

    private static UUID addPlayer(SemionGame game, String name, TeamId teamId, int laneId, long diamond) {
        UUID uuid = UUID.nameUUIDFromBytes((name + teamId + laneId).getBytes());
        PlayerEconomy economy = new PlayerEconomy(game.economyConfig());
        economy.overrideStartingValues(diamond, 0, 0, 0);
        game.players().put(uuid, new SemionPlayer(uuid, name, teamId, laneId, economy));
        game.teams().get(teamId).memberIds().add(uuid);
        return uuid;
    }

    private static void setCurrentRound(SemionGame game, int round) throws Exception {
        Field field = SemionGame.class.getDeclaredField("currentRound");
        field.setAccessible(true);
        field.setInt(game, round);
    }

    private static void setPhase(SemionGame game, RoundPhase phase) throws Exception {
        Field field = SemionGame.class.getDeclaredField("phase");
        field.setAccessible(true);
        field.set(game, phase);
    }
}
