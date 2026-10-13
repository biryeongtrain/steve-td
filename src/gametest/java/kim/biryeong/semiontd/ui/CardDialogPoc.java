package kim.biryeong.semiontd.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerPlayer;

public final class CardDialogPoc {
    public static final Identifier FONT = Identifier.fromNamespaceAndPath("semion-td", "card_dialog_poc");
    public static final int CARD_ADVANCE = 96;
    public static final int GAP = 12;
    public static final int ROWS = 12;
    public static final int ROW_HEIGHT = 9;
    public static final int BODY_WIDTH = 312;
    private static final int GLYPH_BASE = 0xE800;
    private static final char SPACE = '\uE900';
    private static final Map<UUID, State> STATES = new HashMap<>();

    private CardDialogPoc() { }

    private static final class State {
        String token = UUID.randomUUID().toString();
        int attempts;
        int selections;
        int rerolls;
        int rejected;
        int selectedCard = -1;
    }

    public static void register() {
        if (!enabled()) return;
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(CardDialogPoc::resources);
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            dispatcher.register(Commands.literal("semioncardpoc")
                    .then(Commands.literal("open").executes(context -> {
                        var player = context.getSource().getPlayerOrException();
                        requireCapture(player);
                        if (!PolymerResourcePackUtils.hasMainPack(player)) throw new IllegalStateException("Capture pack is required");
                        var state = new State();
                        STATES.put(player.getUUID(), state);
                        show(player, state);
                        player.setExperienceLevels(3000);
                        System.out.println("SEMION_CARD_POC_OPEN token=" + state.token);
                        return 1;
                    }))
                    .then(Commands.literal("verify")
                            .then(Commands.argument("attempts", IntegerArgumentType.integer(0))
                            .then(Commands.argument("selections", IntegerArgumentType.integer(0))
                            .then(Commands.argument("rerolls", IntegerArgumentType.integer(0))
                            .then(Commands.argument("rejected", IntegerArgumentType.integer(0))
                            .then(Commands.argument("selectedCard", IntegerArgumentType.integer(-1, 2)).executes(context -> {
                                var player = context.getSource().getPlayerOrException();
                                requireCapture(player);
                                var state = STATES.get(player.getUUID());
                                if (state == null || state.attempts != IntegerArgumentType.getInteger(context, "attempts")
                                        || state.selections != IntegerArgumentType.getInteger(context, "selections")
                                        || state.rerolls != IntegerArgumentType.getInteger(context, "rerolls")
                                        || state.rejected != IntegerArgumentType.getInteger(context, "rejected")
                                        || state.selectedCard != IntegerArgumentType.getInteger(context, "selectedCard")) {
                                    throw new IllegalStateException("Card dialog callback counters differ from the expected mouse input");
                                }
                                report(state, "verified");
                                player.setExperienceLevels(8000);
                                return 1;
                            }))))))));
        });
    }

    public static boolean enabled() {
        return Boolean.getBoolean("semiontd.capture") && "card-dialog".equals(System.getProperty("semiontd.capture.mode"));
    }

    public static boolean accepts(Identifier id) {
        return enabled() && id.getNamespace().equals("semion-td") && id.getPath().startsWith("card_dialog_poc/");
    }

    public static void handle(ServerPlayer player, ServerboundCustomClickActionPacket packet) {
        requireCapture(player);
        var state = STATES.get(player.getUUID());
        if (state == null) return;
        state.attempts++;
        String prefix = "card_dialog_poc/" + state.token + "/";
        String path = packet.id().getPath();
        String result = "rejected";
        if (!packet.payload().isEmpty() || !path.startsWith(prefix) || state.selections != 0) {
            state.rejected++;
        } else if (path.equals(prefix + "reroll") && state.rerolls < 5) {
            state.rerolls++;
            state.token = UUID.randomUUID().toString();
            show(player, state);
            result = "rerolled";
        } else if (path.matches(java.util.regex.Pattern.quote(prefix) + "card/[0-2]")) {
            state.selections++;
            state.selectedCard = path.charAt(path.length() - 1) - '0';
            result = "selected";
        } else {
            state.rejected++;
        }
        player.setExperienceLevels(3000 + state.attempts);
        report(state, result);
    }

    private static void report(State state, String result) {
        System.out.println("SEMION_CARD_POC_CALLBACK result=" + result + " attempts=" + state.attempts
                + " selections=" + state.selections + " rerolls=" + state.rerolls
                + " rejected=" + state.rejected + " selectedCard=" + state.selectedCard);
    }

    private static void requireCapture(ServerPlayer player) {
        if (!enabled() || !player.getGameProfile().name().equals("SemionCapture")) {
            throw new IllegalStateException("Isolated capture account required");
        }
    }

    private static ClickEvent.Custom click(State state, String action) {
        return new ClickEvent.Custom(Identifier.fromNamespaceAndPath("semion-td", "card_dialog_poc/" + state.token + "/" + action), Optional.empty());
    }

    private static void show(ServerPlayer player, State state) {
        var body = Component.empty();
        Style font = Style.EMPTY.withFont(new FontDescription.Resource(FONT)).withColor(0xFFFFFF)
                .withShadowColor(0).withBold(false).withItalic(false).withUnderlined(false);
        for (int row = 0; row < ROWS; row++) {
            if (row > 0) body.append(Component.literal("\n"));
            for (int card = 0; card < 3; card++) {
                if (card > 0) body.append(Component.literal(Character.toString(SPACE)).setStyle(font));
                body.append(Component.literal(Character.toString(GLYPH_BASE + row * 3 + card)).setStyle(font
                        .withClickEvent(click(state, "card/" + card))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Card " + (card + 1)
                                + " - generated test frame; original icon atlas unavailable")))));
            }
        }
        var common = new CommonDialogData(Component.literal("Card Dialog PoC"), Optional.empty(), true, false,
                DialogAction.NONE, List.of(new PlainMessage(body, BODY_WIDTH + 8),
                new PlainMessage(Component.literal("Original icon atlas unavailable - test frames only"), BODY_WIDTH)), List.of());
        var reroll = new ActionButton(new CommonButtonData(Component.literal("Reroll " + (5 - state.rerolls) + "/5"),
                Optional.of(Component.literal("Replace callback token; previous cards must become stale")), 120),
                Optional.of(new StaticAction(click(state, "reroll"))));
        player.connection.send(new ClientboundShowDialogPacket(Holder.direct(
                new MultiActionDialog(common, List.of(reroll), Optional.empty(), 1))));
    }

    private static void resources(ResourcePackBuilder builder) {
        var atlas = new BufferedImage(95 * 3, ROW_HEIGHT * ROWS, BufferedImage.TYPE_INT_ARGB);
        var graphics = atlas.createGraphics();
        try {
            int[] colors = {0xBBCAD9, 0xE8BF54, 0xBBA2FA};
            for (int card = 0; card < 3; card++) {
                int x = card * 95;
                graphics.setColor(new Color(0x101A2D));
                graphics.fillRect(x, 0, 95, ROW_HEIGHT * ROWS);
                graphics.setColor(new Color(colors[card]));
                graphics.drawRect(x, 0, 94, ROW_HEIGHT * ROWS - 1);
                graphics.drawRect(x + 2, 2, 90, ROW_HEIGHT * ROWS - 5);
                graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
                graphics.drawString("CARD " + (card + 1), x + 18, 21);
                graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 9));
                graphics.drawString("TEST FRAME", x + 15, 46);
                graphics.drawString("NO ICON ATLAS", x + 9, 63);
                graphics.drawString("CLICK ANYWHERE", x + 6, 87);
                for (int row = 1; row < ROWS; row++) {
                    graphics.drawLine(x, row * ROW_HEIGHT, x + 4, row * ROW_HEIGHT);
                    graphics.drawLine(x + 90, row * ROW_HEIGHT, x + 94, row * ROW_HEIGHT);
                }
            }
        } finally {
            graphics.dispose();
        }
        try (var bytes = new ByteArrayOutputStream()) {
            ImageIO.write(atlas, "PNG", bytes);
            builder.addData("assets/semion-td/textures/font/card_dialog_poc.png", bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to build card dialog test frames", exception);
        }
        var providers = new JsonArray();
        var bitmap = new JsonObject();
        bitmap.addProperty("type", "bitmap");
        bitmap.addProperty("file", "semion-td:font/card_dialog_poc.png");
        bitmap.addProperty("ascent", 7);
        bitmap.addProperty("height", ROW_HEIGHT);
        var chars = new JsonArray();
        for (int row = 0; row < ROWS; row++) {
            chars.add(new String(new char[]{(char) (GLYPH_BASE + row * 3), (char) (GLYPH_BASE + row * 3 + 1), (char) (GLYPH_BASE + row * 3 + 2)}));
        }
        bitmap.add("chars", chars);
        providers.add(bitmap);
        var space = new JsonObject();
        space.addProperty("type", "space");
        var advances = new JsonObject();
        advances.addProperty(Character.toString(SPACE), GAP);
        space.add("advances", advances);
        providers.add(space);
        var font = new JsonObject();
        font.add("providers", providers);
        builder.addData("assets/semion-td/font/card_dialog_poc.json", font.toString().getBytes(StandardCharsets.UTF_8));
    }
}
