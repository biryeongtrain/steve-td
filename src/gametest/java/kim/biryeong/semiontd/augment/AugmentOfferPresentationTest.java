package kim.biryeong.semiontd.augment;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class AugmentOfferPresentationTest extends AugmentControllerFixture {
    @GameTest
    public void cardScopeLabelsShowOnlyDedicatedMarker(GameTestHelper context) {
        for (var card : AugmentCatalog.definitions()) {
            String label = kim.biryeong.semiontd.ui.augment.AugmentCardDialog.categoryLabel(card);
            require(!label.contains("실버") && !label.contains("골드") && !label.contains("프리즘"), "Rarity words must not label cards.");
            if (card.requiredJobId() == null) {
                require(label.isEmpty(), "Common cards have no upper label.");
            } else {
                require(label.equals("전용"), "Dedicated cards show only the dedicated marker, without a job name.");

            }
        }
        context.succeed();
    }

    @GameTest
    public void bareAugmentAliasRequiresNoSubcommandOrOperatorPermission(GameTestHelper context) {
        var server = context.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var command = dispatcher.getRoot().getChild("증강");
        require(command != null && command.getCommand() != null, "The bare augment alias must have an executable action.");
        for (int level = 0; level <= 2; level++) {
            var source = server.createCommandSourceStack().withPermission(
                    net.minecraft.server.permissions.LevelBasedPermissionSet.forLevel(
                            net.minecraft.server.permissions.PermissionLevel.byId(level)));
            require(command.canUse(source), "Players do not need operator permission to reopen augments.");
            var parsed = dispatcher.parse("증강", source);
            require(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty()
                            && parsed.getContext().getCommand() != null,
                    "The exact bare command must parse without a subcommand.");
        }
        context.succeed();
    }

    private static final String COMMAND = "/semiontd augment ";

    @GameTest
    public void nativeDialogBodyKeepsCardIdentityNormalFontCounterAndStaleGuardsWithoutTooltips(GameTestHelper context) {
        for (String schedule : List.of("SSS", "GGG", "PPP")) {
            var online = context.makeMockServerPlayerInLevel();
            var game = prepare(context, online, schedule);
            try {
                String rarity = schedule.equals("SSS") ? "silver" : schedule.equals("GGG") ? "gold" : "prismatic";
                force(game, online, "reserve_diamonds_" + rarity + " reserve_income_" + rarity + " reserve_production_" + rarity);
                advance(game, online, 20);
                var player = game.players().get(online.getUUID());
                var state = player.augments();
                for (int slot = 0; slot < 1; slot++) {
                    var offer = state.currentOffer().orElseThrow();
                    var screen = game.augmentService().offerScreen(game, player, offer, true);
                    var dialog = kim.biryeong.semiontd.ui.augment.AugmentCardDialog.dialog(screen);
                    require(dialog.common().body().size() == 1
                                    && dialog.common().body().getFirst() instanceof net.minecraft.server.dialog.body.PlainMessage,
                            "The production dialog exposes cards through a clickable text body.");
                    require(dialog.common().title().getStyle().getHoverEvent() == null,
                            "The augment title has no hover tooltip.");
                    require(dialog.actions().stream().allMatch(action -> action.button().tooltip().isEmpty()),
                            "Navigation controls have no hover tooltips.");
                    for (int count : List.of(5, 0)) {
                        var counted = new AugmentService.Screen(screen.title(), screen.body(), screen.cards(),
                                screen.buttons(), screen.columns(), count, count > 0);
                        var found = new java.util.concurrent.atomic.AtomicInteger();
                        kim.biryeong.semiontd.ui.augment.AugmentCardDialog.body(counted).visit((style, text) -> {
                            require(style.getHoverEvent() == null, "All augment body spans are tooltip-free.");
                            if (text.equals(Integer.toString(count))) {
                                require(style.getFont().equals(net.minecraft.network.chat.Style.EMPTY.getFont()),
                                        "The remaining count uses the normal UI font.");
                                require(style.getClickEvent() == null,
                                        "The ordinary-font counter cannot override its aligned reroll hit region.");
                                found.incrementAndGet();
                            }
                            return java.util.Optional.empty();
                        }, net.minecraft.network.chat.Style.EMPTY);
                        require(found.get() == 1, "The remaining count appears exactly once as normal text.");
                    }
                    var styles = new java.util.HashMap<String, net.minecraft.network.chat.Style>();
                    kim.biryeong.semiontd.ui.augment.AugmentCardDialog.body(screen).visit((style, text) -> {
                        if (style.getClickEvent() instanceof net.minecraft.network.chat.ClickEvent.RunCommand click) {
                            styles.put(click.command(), style);
                        }
                        return java.util.Optional.empty();
                    }, net.minecraft.network.chat.Style.EMPTY);
                    for (int cardSlot = 0; cardSlot < 3; cardSlot++) {
                        String command = screen.buttons().get(cardSlot).command();
                        require(styles.containsKey(command) && command.contains(" " + offer.cardIds().get(cardSlot) + " "),
                                "Every card slice carries the current card identity and session-scoped action.");
                        require(styles.get(command).getHoverEvent() == null,
                                "Card controls must not expose hover tooltips.");
                    }
                    String reroll = screen.buttons().get(3).command();
                    require(screen.canReroll() && styles.containsKey(reroll), "The shared reroll is clickable in the body.");
                    require(game.augmentService().handle(game, online, reroll.substring(COMMAND.length()), false) == 1,
                            "The real body command replaces all three cards.");
                    var after = state.currentOffer().orElseThrow();
                    for (int other = 0; other < 3; other++) {
                        require(!offer.cardIds().contains(after.cardIds().get(other)),
                                "A body reroll changes every card and cannot retain an old card.");
                    }
                    require(state.rerollsRemaining(slot) == 4, "Each independently used slot is charged once.");
                    require(game.augmentService().handle(game, online, reroll.substring(COMMAND.length()), false) == 0,
                            "Repeated native callbacks cannot charge again.");
                    require(game.augmentService().handle(game, online, screen.buttons().get(slot).command().substring(COMMAND.length()), false) == 0,
                            "The prior card action is stale after replacement.");
                }
                require(state.rerollsRemainingBySlot().equals(List.of(4, 4, 4)), "The single body control has one shared balance.");
                var current = state.currentOffer().orElseThrow();
                var disabled = game.augmentService().offerScreen(game, player, current, false);
                var commands = new java.util.HashSet<String>();
                kim.biryeong.semiontd.ui.augment.AugmentCardDialog.body(disabled).visit((style, text) -> {
                    if (style.getClickEvent() instanceof net.minecraft.network.chat.ClickEvent.RunCommand click) commands.add(click.command());
                    return java.util.Optional.empty();
                }, net.minecraft.network.chat.Style.EMPTY);
                require(!commands.contains(disabled.buttons().get(3).command()),
                        "Disabled reroll glyphs have no executable action.");
                int selectedSlot = java.util.stream.IntStream.range(0, 3)
                        .filter(slot -> AugmentService.modes(current.cardIds().get(slot)).isEmpty()).findFirst().orElseThrow();
                String select = disabled.buttons().get(selectedSlot).command();
                require(commands.contains(select) && game.augmentService().handle(game, online, select.substring(COMMAND.length()), false) == 1,
                        "Disabling reroll does not disable card selection.");
                require(game.augmentService().handle(game, online, select.substring(COMMAND.length()), false) == 0
                                && state.selections().size() == 1 && state.rerollsRemainingBySlot().equals(List.of(4, 4, 4)),
                        "Selection grants exactly once without spending another reroll.");
            } finally {game.close();}
        }
        context.succeed();
    }

    @GameTest
    public void cardInteractionLayersKeepAllThreeIdentitiesAndVisualsCannotOverrideThem(GameTestHelper context) {
        for (int selectedSlot = 0; selectedSlot < 3; selectedSlot++) {
            var online = context.makeMockServerPlayerInLevel();
            var game = prepare(context, online);
            var originalConnection = online.connection;
            var packets = collectPackets(online);
            try {
                addTarget(game, online);
                force(game, online, "finishing_fire_1 tactical_designation_1_cover beneficial_effect_1");
                advance(game, online, 20);
                var player = game.players().get(online.getUUID());
                var state = player.augments();
                var offer = state.currentOffer().orElseThrow();
                var screen = game.augmentService().offerScreen(game, player, offer, true);
                int[] actionCharacters = {0};
                kim.biryeong.semiontd.ui.augment.AugmentCardDialog.body(screen).visit((style, text) -> {
                    if (style.getClickEvent() != null) {
                        require(style.getFont() instanceof net.minecraft.network.chat.FontDescription.Resource font
                                        && kim.biryeong.semiontd.ui.rp.SemionUiFont.usesFont(font.id()),
                                "Only card-aligned spacing regions carry commands; artwork and labels cannot override them.");
                        text.codePoints().forEach(codePoint -> {
                            require(kim.biryeong.semiontd.ui.rp.SemionUiFont.preciseAdvance(codePoint) > 0,
                                    "An interactive span never moves the cursor backwards.");
                            actionCharacters[0]++;
                        });
                    }
                    return java.util.Optional.empty();
                }, net.minecraft.network.chat.Style.EMPTY);
                require(actionCharacters[0] > 0, "The full cards remain interactive.");
                String selectedId = offer.cardIds().get(selectedSlot);
                String command = screen.buttons().get(selectedSlot).command();
                packets.clear();
                require(game.augmentService().handle(game, online, command.substring(COMMAND.length()), false) == 1,
                        "Each card region dispatches its live selection action.");
                require(state.selections().size() == 1 && state.selections().getFirst().augmentId().equals(selectedId)
                                && state.currentOffer().isEmpty() && state.rerollsRemaining() == 5,
                        "The selected card identity is preserved for every slot without spending a reroll.");
                require(clearPackets(packets) == 1 && showPackets(packets) == 0,
                        "A committed selection closes the dialog without reopening it.");
                packets.clear();
                require(game.augmentService().handle(game, online, command.substring(COMMAND.length()), false) == 0,
                        "Replaying a card cannot grant another augment.");
                advance(game, online, 25);
                require(clearPackets(packets) == 1 && showPackets(packets) == 0 && state.selections().size() == 1,
                        "Duplicate callbacks and scheduled updates cannot reopen the dialog or grant twice.");
            } finally {
                online.connection = originalConnection;
                game.close();
            }
        }
        context.succeed();
    }

    @GameTest
    public void dialogClosePreservesOfferAndRerollFailuresWhileExpiryRejectsLateSelection(GameTestHelper context) {
        var online = context.makeMockServerPlayerInLevel();
        var game = prepare(context, online);
        var originalConnection = online.connection;
        var packets = collectPackets(online);
        try {
            force(game, online, "beneficial_effect_1 reserve_income_silver reserve_production_silver");
            advance(game, online, 20);
            var player = game.players().get(online.getUUID());
            var state = player.augments();
            var offer = state.currentOffer().orElseThrow();
            var screen = game.augmentService().offerScreen(game, player, offer, true);
            String staleCard = screen.buttons().getFirst().command().substring(COMMAND.length());
            packets.clear();
            require(handle(game, online, "ui close") == 1, "Explicit close succeeds.");
            require(clearPackets(packets) == 1 && state.currentOffer().isPresent()
                            && state.selections().isEmpty() && state.rerollsRemaining() == 5,
                    "Closing a dialog consumes neither selection nor reroll.");
            packets.clear();
            require(game.augmentService().handle(game, online,
                    screen.buttons().get(3).command().substring(COMMAND.length()), false) == 1,
                    "Reroll succeeds through its actual command.");
            require(clearPackets(packets) == 0 && showPackets(packets) > 0
                            && state.selections().isEmpty() && state.rerollsRemaining() == 4,
                    "Reroll keeps the offer dialog open without selecting.");
            packets.clear();
            require(game.augmentService().handle(game, online, staleCard, false) == 0,
                    "An old card command is rejected after reroll.");
            require(clearPackets(packets) == 0 && state.currentOffer().isPresent()
                            && state.selections().isEmpty() && state.rerollsRemaining() == 4,
                    "Stale input cannot close an active offer or spend another action.");
            var current = state.currentOffer().orElseThrow();
            String lateCard = game.augmentService().offerScreen(game, player, current, true)
                    .buttons().getFirst().command().substring(COMMAND.length());
            setField(game, "tickCounter", current.deadlineTickExclusive());
            packets.clear();
            require(game.augmentService().handle(game, online, lateCard, false) == 0,
                    "An expired click is rejected rather than acknowledged as a successful selection.");
            require(clearPackets(packets) > 0 && showPackets(packets) == 0
                            && state.currentOffer().isEmpty() && state.selections().size() == 1
                            && state.rerollsRemaining() == 4,
                    "The existing timeout policy resolves the offer once and leaves the dialog closed.");
        } finally {
            online.connection = originalConnection;
            game.close();
        }
        context.succeed();
    }
}
