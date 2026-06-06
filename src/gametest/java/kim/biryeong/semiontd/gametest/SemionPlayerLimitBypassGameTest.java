package kim.biryeong.semiontd.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.SemionPlayerLimitBypassService;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;

public final class SemionPlayerLimitBypassGameTest {
    @GameTest
    public void playerLimitBypassCommandTreeIsOpOnlyAndHasCrudSubcommands(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        var semiontd = dispatcher.getRoot().getChild("semiontd");
        if (semiontd == null) {
            throw new AssertionError("Expected /semiontd to be registered");
        }
        var playerLimit = semiontd.getChild("playerlimit");
        if (playerLimit == null) {
            throw new AssertionError("Expected /semiontd playerlimit to be registered");
        }
        if (playerLimit.getChild("add") == null) {
            throw new AssertionError("Expected /semiontd playerlimit add to be registered");
        }
        if (playerLimit.getChild("remove") == null) {
            throw new AssertionError("Expected /semiontd playerlimit remove to be registered");
        }
        if (playerLimit.getChild("list") == null) {
            throw new AssertionError("Expected /semiontd playerlimit list to be registered");
        }

        CommandSourceStack nonOp = context.getLevel().getServer().createCommandSourceStack().withPermission(0);
        CommandSourceStack op = context.getLevel().getServer().createCommandSourceStack().withPermission(2);
        if (playerLimit.canUse(nonOp)) {
            throw new AssertionError("Expected /semiontd playerlimit to require OP permission");
        }
        if (!playerLimit.canUse(op)) {
            throw new AssertionError("Expected OP source to use /semiontd playerlimit");
        }
        context.succeed();
    }

    @GameTest
    public void playerLimitBypassRuntimeListAllowsAndRevokesCapacityBypass(GameTestHelper context) {
        SemionGameManager gameManager = new SemionGameManager();
        UUID playerId = UUID.nameUUIDFromBytes("capacity-bypass-runtime".getBytes());
        GameProfile profile = new GameProfile(playerId, "BypassPlayer");
        SemionGameManager previousManager = SemionPlayerLimitBypassService.configure(gameManager);
        try {
            if (gameManager.canBypassPlayerLimit(playerId)) {
                throw new AssertionError("Expected player to start without capacity bypass");
            }
            if (SemionPlayerLimitBypassService.canBypassPlayerLimit(profile)) {
                throw new AssertionError("Expected service to start without capacity bypass");
            }
            if (!gameManager.addPlayerLimitBypass(playerId, profile.getName())) {
                throw new AssertionError("Expected first add to report a new capacity bypass");
            }
            if (!gameManager.canBypassPlayerLimit(playerId)) {
                throw new AssertionError("Expected manager to allow configured capacity bypass");
            }
            if (!SemionPlayerLimitBypassService.canBypassPlayerLimit(profile)) {
                throw new AssertionError("Expected service to allow configured capacity bypass");
            }
            if (!"BypassPlayer".equals(gameManager.playerLimitBypasses().get(playerId))) {
                throw new AssertionError("Expected runtime list to expose the configured display name");
            }
            if (!gameManager.removePlayerLimitBypass(playerId)) {
                throw new AssertionError("Expected remove to revoke an existing capacity bypass");
            }
            if (gameManager.canBypassPlayerLimit(playerId)) {
                throw new AssertionError("Expected removed player to lose capacity bypass");
            }
            context.succeed();
        } finally {
            SemionPlayerLimitBypassService.configure(previousManager);
        }
    }
}
