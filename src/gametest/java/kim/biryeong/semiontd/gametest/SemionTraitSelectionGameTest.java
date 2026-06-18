package kim.biryeong.semiontd.gametest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.game.AssignedParticipant;
import kim.biryeong.semiontd.game.MatchMode;
import kim.biryeong.semiontd.game.ParticipantSelectionPlan;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.trait.BuiltInTraits;
import kim.biryeong.semiontd.trait.TraitLoadout;
import kim.biryeong.semiontd.trait.TraitSelectionSession;
import kim.biryeong.semiontd.trait.TraitSlot;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public final class SemionTraitSelectionGameTest {
    @GameTest
    public void traitSelectionIsBypassedWhileFeatureDisabled(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        UUID redId = playerId("trait-disabled-red");
        UUID blueId = playerId("trait-disabled-blue");
        EconomyConfig economy = EconomyConfig.defaultConfig();
        SemionGame game = syntheticGame(context, economy);
        SemionGameManager manager = managerWithActiveGame(game);
        ParticipantSelectionPlan plan = twoPlayerPlan(redId, "trait-disabled-red", blueId, "trait-disabled-blue");

        game.selectTrait(redId, TraitSlot.PRIMARY, BuiltInTraits.STARTER_MINERAL_TRAINING_ID);
        if (!assertEquals(context, SemionGameManager.StartCountdownResult.SCHEDULED, manager.scheduleStart(server, plan), "Manager should schedule the normal start countdown.")) {
            return;
        }
        if (!assertTrue(context, !manager.traitsEnabled(), "Trait feature should be disabled.")) {
            return;
        }
        if (!assertTrue(context, !manager.traitSelectionActive(), "Disabled traits should not open a trait-selection phase.")) {
            return;
        }
        if (!assertTrue(context, manager.startCountdownActive(), "Normal start countdown should begin immediately.")) {
            return;
        }
        if (!assertEquals(context, TraitLoadout.none(), manager.traitLoadoutOrDefault(redId), "Disabled traits should expose no pending loadout during countdown.")) {
            return;
        }
        if (!assertEquals(context, TraitSelectionSession.SelectionResult.DISABLED, manager.selectTrait(server, redId, TraitSlot.SECONDARY, BuiltInTraits.NONE_ID), "Trait selection requests should be rejected while disabled.")) {
            return;
        }

        for (int i = 0; i < SemionGameManager.START_COUNTDOWN_TICKS; i++) {
            manager.tick(server);
        }
        if (!assertTrue(context, game.rosterLocked(), "Game should start after the normal countdown without waiting for trait selection.")) {
            return;
        }
        if (!assertEquals(context, TraitLoadout.none(), game.players().get(redId).traitLoadout(), "Disabled traits should not apply preselected trait loadouts.")) {
            return;
        }
        if (!assertEquals(context, economy.startingDiamond(), game.players().get(redId).economy().diamond(), "Disabled traits should not change starting diamond.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void traitCommandsRemainParseableWhileDisabled(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        if (!assertTrue(context, dispatcher.getRoot().getChild("특성") != null, "Expected /특성 alias to remain registered.")) {
            return;
        }
        var source = context.getLevel().getServer().createCommandSourceStack();
        for (String command : List.of(
                "특성",
                "semiontd trait",
                "semiontd trait current",
                "semiontd trait list",
                "semiontd trait ui primary",
                "semiontd trait ui secondary",
                "특성 ui primary",
                "semiontd trait select primary none",
                "semiontd trait select secondary none"
        )) {
            var parsed = dispatcher.parse(command, source);
            if (!assertTrue(context, !parsed.getContext().getNodes().isEmpty() && !parsed.getReader().canRead(), "Expected disabled trait command to parse completely: /" + command)) {
                return;
            }
        }
        context.succeed();
    }

    @GameTest
    public void traitCommandTreeRegistersKoreanAlias(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        if (!assertTrue(context, dispatcher.getRoot().getChild("특성") != null, "Expected /특성 alias to be registered.")) {
            return;
        }
        var source = context.getLevel().getServer().createCommandSourceStack();
        for (String command : List.of(
                "특성",
                "semiontd trait",
                "semiontd trait current",
                "semiontd trait list",
                "semiontd trait ui primary",
                "semiontd trait ui secondary",
                "특성 ui primary",
                "semiontd trait select primary none",
                "semiontd trait select secondary none"
        )) {
            var parsed = dispatcher.parse(command, source);
            if (!assertTrue(context, !parsed.getContext().getNodes().isEmpty() && !parsed.getReader().canRead(), "Expected command to parse completely: /" + command)) {
                return;
            }
        }
        context.succeed();
    }

    private static SemionGame syntheticGame(GameTestHelper context, EconomyConfig economyConfig) {
        return new SemionGame(economyConfig, WaveConfig.defaultConfig(), SyntheticArenaFactory.create(
                context.getLevel(),
                context.absolutePos(BlockPos.ZERO)
        ));
    }

    private static SemionGameManager managerWithActiveGame(SemionGame game) {
        SemionGameManager manager = new SemionGameManager();
        try {
            var field = SemionGameManager.class.getDeclaredField("activeGame");
            field.setAccessible(true);
            field.set(manager, game);
            return manager;
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to set active game for trait selection test.", exception);
        }
    }

    private static ParticipantSelectionPlan twoPlayerPlan(UUID redId, String redName, UUID blueId, String blueName) {
        return new ParticipantSelectionPlan(
                MatchMode.NORMAL,
                List.of(
                        new AssignedParticipant(redId, redName, TeamId.RED, 1),
                        new AssignedParticipant(blueId, blueName, TeamId.BLUE, 1)
                ),
                Set.of(),
                2
        );
    }

    private static UUID playerId(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean assertTrue(GameTestHelper context, boolean condition, String message) {
        if (!condition) {
            context.fail(Component.literal(message));
            return false;
        }
        return true;
    }

    private static boolean assertEquals(GameTestHelper context, Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            context.fail(Component.literal(message + " expected=" + expected + " actual=" + actual));
            return false;
        }
        return true;
    }
}
