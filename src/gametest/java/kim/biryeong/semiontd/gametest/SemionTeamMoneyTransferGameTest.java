package kim.biryeong.semiontd.gametest;

import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamMoneyTransferResult;
import kim.biryeong.semiontd.game.TeamMoneyTransferResultType;
import kim.biryeong.semiontd.map.GameArena;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;

public final class SemionTeamMoneyTransferGameTest {
    @GameTest
    public void commandTreeRegistersMoneyRequestAndKoreanAlias(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        if (dispatcher.getRoot().getChild("요청") == null) {
            throw new AssertionError("Expected /요청 alias to be registered");
        }
        if (dispatcher.getRoot().getChild("semiontd").getChild("money").getChild("request") == null) {
            throw new AssertionError("Expected /semiontd money request to be registered");
        }
        if (dispatcher.getRoot().getChild("semiontd").getChild("money").getChild("accept") == null) {
            throw new AssertionError("Expected /semiontd money accept to be registered");
        }
        context.succeed();
    }

    @GameTest
    public void commandDispatcherParsesKoreanRequestAlias(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        CommandSourceStack source = context.getLevel().getServer().createCommandSourceStack();
        if (dispatcher.parse("요청 30", source).getContext().getNodes().isEmpty()) {
            throw new AssertionError("Expected /요청 30 to parse");
        }
        context.succeed();
    }

    @GameTest
    public void sameTeamDiamondTransferWorksInGameTestRuntime(GameTestHelper context) {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(), new GameArena(Map.of()));
        game.teams().get(TeamId.BLUE).activate();
        UUID receiver = addPlayer(game, "receiver", TeamId.BLUE, 1, 200);
        UUID sender = addPlayer(game, "sender", TeamId.BLUE, 2, 200);

        TeamMoneyTransferResult request = game.requestTeamMoney(receiver, 30);
        TeamMoneyTransferResult accepted = game.acceptTeamMoneyRequest(sender, request.requestId().orElseThrow());

        if (request.type() != TeamMoneyTransferResultType.SUCCESS || accepted.type() != TeamMoneyTransferResultType.SUCCESS) {
            throw new AssertionError("Expected request and accept to succeed: " + request.type() + ", " + accepted.type());
        }
        if (game.players().get(sender).economy().diamond() != 170 || game.players().get(receiver).economy().diamond() != 230) {
            throw new AssertionError("Expected 30 diamonds to move from sender to receiver");
        }
        context.succeed();
    }

    private static UUID addPlayer(SemionGame game, String name, TeamId teamId, int laneId, long diamond) {
        UUID uuid = UUID.nameUUIDFromBytes((name + teamId + laneId).getBytes());
        PlayerEconomy economy = new PlayerEconomy(game.economyConfig());
        economy.overrideStartingValues(diamond, 0, 0, 0);
        game.players().put(uuid, new SemionPlayer(uuid, name, teamId, laneId, economy));
        game.teams().get(teamId).memberIds().add(uuid);
        return uuid;
    }
}
