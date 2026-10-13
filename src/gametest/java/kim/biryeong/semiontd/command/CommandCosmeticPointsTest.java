package kim.biryeong.semiontd.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.ProgressionConfig;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.gametest.RuntimePlayerFixture;
import kim.biryeong.semiontd.progression.ProgressionService;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class CommandCosmeticPointsTest {
    @GameTest
    public void giveAndTakeUseAllOnlinePlayersAndPersistAcrossReconnect(GameTestHelper context) throws Exception {
        var server = context.getLevel().getServer();
        Path directory = Files.createTempDirectory("semion-points-command-");
        Path profiles = directory.resolve("profiles.json");
        var manager = manager(profiles);
        var dispatcher = dispatcher(manager, directory);
        var output = new Output();
        var op = source(context, 2).withSource(output);
        UUID offline = UUID.randomUUID();
        manager.grantCosmeticCurrency(offline, "Offline", 42).orElseThrow();
        UUID reconnectId = UUID.randomUUID();
        try (var lobby = connect(context, GameType.ADVENTURE, reconnectId, "points-lobby");
                var spectator = connect(context, GameType.SPECTATOR, UUID.randomUUID(), "points-watch");
                var active = connect(context, GameType.SURVIVAL, UUID.randomUUID(), "points-active")) {
            var targets = CommandCosmeticPoints.onlineTargets(server.getPlayerList().getPlayers());
            context.assertTrue(targets.containsKey(lobby.player().getUUID())
                    && targets.containsKey(spectator.player().getUUID()) && targets.containsKey(active.player().getUUID()),
                    "All actual connected players must be included regardless of game mode or lobby membership");
            context.assertValueEqual(2, CommandCosmeticPoints.onlineTargets(
                    List.of(lobby.player(), spectator.player(), lobby.player())).size(), "Duplicate UUIDs receive only one share");
            context.assertValueEqual(targets.size(), dispatcher.execute("semiontd cosmetic points giveall 10", op), "Give returns affected target count");
            for (var target : targets.entrySet()) {
                context.assertValueEqual(10L, manager.profile(server, target.getKey(), target.getValue()).cosmeticCurrency(), "Every online target receives one share");
            }
            context.assertTrue(output.messages.stream().anyMatch(message -> message.contains("실제 지급 총량: " + targets.size() * 10L)), "Feedback includes actual total");
            context.assertValueEqual(targets.size(), dispatcher.execute("semiontd cosmetic points takeall 5", op), "Take returns affected target count");
            var reloaded = new ProgressionService(ProgressionConfig.defaultConfig(), profiles);
            for (var target : targets.entrySet()) {
                context.assertValueEqual(5L, reloaded.profile(server, target.getKey(), target.getValue()).cosmeticCurrency(), "All balances persist");
            }
            context.assertValueEqual(42L, reloaded.profile(server, offline, "Offline").cosmeticCurrency(), "Offline stored account is excluded");
            var playerList = new ArrayList<>(List.of(lobby.player()));
            var snapshot = CommandCosmeticPoints.onlineTargets(playerList);
            playerList.clear();
            context.assertValueEqual(1, snapshot.size(), "Snapshot is independent of future player-list changes");
        }
        try (var reconnected = connect(context, GameType.ADVENTURE, reconnectId, "points-lobby")) {
            context.assertValueEqual(5L, manager(profiles).profile(server, reconnected.player().getUUID(), "points-lobby").cosmeticCurrency(), "Reconnected player retains the saved balance");
        }
        context.succeed();
    }

    @GameTest
    public void commandFailuresDoNotPartiallyChangeBalances(GameTestHelper context) throws Exception {
        Path directory = Files.createTempDirectory("semion-points-failure-");
        Path profiles = directory.resolve("profiles.json");
        var manager = manager(profiles);
        var dispatcher = dispatcher(manager, directory);
        var output = new Output();
        var op = source(context, 2).withSource(output);
        try (var funded = connect(context, GameType.ADVENTURE, UUID.randomUUID(), "points-funded");
                var empty = connect(context, GameType.SPECTATOR, UUID.randomUUID(), "points-empty")) {
            manager.grantCosmeticCurrency(funded.player().getUUID(), "points-funded", Long.MAX_VALUE).orElseThrow();
            byte[] original = Files.readAllBytes(profiles);
            context.assertValueEqual(0, dispatcher.execute("semiontd cosmetic points takeall 1", op), "Any insufficient target rejects the whole removal");
            context.assertValueEqual(0, dispatcher.execute("semiontd cosmetic points giveall 1", op), "Any overflowing target rejects the whole grant");
            context.assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(profiles)), "Failed operations leave the persisted snapshot unchanged");
            context.assertTrue(output.messages.stream().filter(message -> message.contains("실제 반영 0명 / 0포인트")).count() == 2, "Both failures report zero actual changes");
        }
        context.succeed();
    }

    @GameTest
    public void singleTakePreservesOtherPlayersAndRejectsInvalidTargetsAndInsufficientFunds(GameTestHelper context) throws Exception {
        Path directory = Files.createTempDirectory("semion-points-single-");
        Path profiles = directory.resolve("profiles.json");
        var manager = manager(profiles);
        var dispatcher = dispatcher(manager, directory);
        var output = new Output();
        var op = source(context, 2).withSource(output);
        UUID id = UUID.randomUUID();
        try (var target = connect(context, GameType.ADVENTURE, id, "points-target");
                var other = connect(context, GameType.SPECTATOR, UUID.randomUUID(), "points-other")) {
            manager.grantCosmeticCurrency(other.player().getUUID(), "points-other", 80).orElseThrow();
            context.assertValueEqual(1, dispatcher.execute("semiontd cosmetic points give points-target 100", op), "Existing single give still works");
            context.assertValueEqual(1, dispatcher.execute("semiontd cosmetic points take points-target 35", op), "Single take removes requested amount");
            context.assertValueEqual(65L, manager.profile(context.getLevel().getServer(), id, "points-target").cosmeticCurrency(), "Single balance decreased exactly");
            context.assertValueEqual(80L, manager.profile(context.getLevel().getServer(), other.player().getUUID(), "points-other").cosmeticCurrency(), "Other online player remains unchanged");
            byte[] beforeFailure = Files.readAllBytes(profiles);
            context.assertValueEqual(0, dispatcher.execute("semiontd cosmetic points take points-target 66", op), "Insufficient funds reject removal");
            context.assertValueEqual(0, dispatcher.execute("semiontd cosmetic points take @a 1", op), "Single take rejects multiple targets");
            boolean missingRejected = false;
            try {
                dispatcher.execute("semiontd cosmetic points take @a[name=missing-test-id] 1", op);
            } catch (CommandSyntaxException expected) {
                missingRejected = true;
            }
            context.assertTrue(missingRejected, "Missing target must be rejected");
            context.assertTrue(java.util.Arrays.equals(beforeFailure, Files.readAllBytes(profiles)), "Rejected operations do not write partial changes");
            context.assertTrue(output.messages.stream().anyMatch(message -> message.contains("실제 반영 1명 / 35포인트")), "Success reports actual amount and count");
        }
        var reloaded = manager(profiles);
        try (var reconnected = connect(context, GameType.ADVENTURE, id, "points-target")) {
            context.assertValueEqual(65L, reloaded.profile(context.getLevel().getServer(), reconnected.player().getUUID(), "points-target").cosmeticCurrency(), "Single take persists across reconnect and store reload");
        }
        context.succeed();
    }

    @GameTest
    public void permissionAndNumericParsingCannotBypassLevelTwo(GameTestHelper context) throws Exception {
        var server = context.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var points = dispatcher.getRoot().getChild("semiontd").getChild("cosmetic").getChild("points");
        for (String name : List.of("giveall", "takeall", "take")) {
            String action = name.equals("take") ? "take @a[limit=1]" : name;
            for (int level : List.of(0, 1, 2)) {
                var commandSource = source(context, level);
                context.assertValueEqual(level >= 2, points.canUse(commandSource), "Points parent requires OP level two");
                context.assertValueEqual(level >= 2, points.getChild(name).canUse(commandSource), "Leaf cannot bypass OP level two");
                var parsed = dispatcher.parse("semiontd cosmetic points " + action + " 1", commandSource);
                context.assertValueEqual(level < 2, parsed.getReader().canRead(), "Only OP can parse full commands");
                if (level < 2) {
                    boolean rejected = false;
                    try {
                        dispatcher.execute(parsed);
                    } catch (CommandSyntaxException expected) {
                        rejected = true;
                    }
                    context.assertTrue(rejected, "Execution rejects unauthorized source");
                }
            }
            for (String amount : List.of("0", "-1", "9223372036854775808", "1.5", "")) {
                boolean rejected = false;
                try {
                    dispatcher.execute("semiontd cosmetic points " + action + " " + amount, source(context, 2));
                } catch (CommandSyntaxException expected) {
                    rejected = true;
                }
                context.assertTrue(rejected, "Invalid amount must not reach a mutation");
            }
        }
        context.assertValueEqual(0, CommandCosmeticPoints.onlineTargets(List.of()).size(), "Empty player list has no targets");
        context.succeed();
    }

    private static RuntimePlayerFixture connect(GameTestHelper context, GameType mode, UUID id, String name) {
        return RuntimePlayerFixture.connect(context, context.getLevel(), Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 2))), mode, id, name);
    }

    private static SemionGameManager manager(Path path) throws Exception {
        var manager = new SemionGameManager();
        var field = SemionGameManager.class.getDeclaredField("progressionService");
        field.setAccessible(true);
        field.set(manager, new ProgressionService(ProgressionConfig.defaultConfig(), path));
        return manager;
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher(SemionGameManager manager, Path directory) {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        SemionCommands.register(dispatcher, manager, null, null, null, null, directory);
        return dispatcher;
    }

    private static CommandSourceStack source(GameTestHelper context, int level) {
        return context.getLevel().getServer().createCommandSourceStack()
                .withPermission(LevelBasedPermissionSet.forLevel(PermissionLevel.byId(level)));
    }

    private static final class Output implements CommandSource {
        final List<String> messages = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
