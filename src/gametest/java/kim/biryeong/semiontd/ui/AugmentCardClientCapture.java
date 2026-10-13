package kim.biryeong.semiontd.ui;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import kim.biryeong.semiontd.ui.augment.AugmentCardDialog;
import kim.biryeong.semiontd.ui.augment.AugmentCardFrames;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import org.lwjgl.sdl.SDLMouse;

final class AugmentCardClientCapture {
    private AugmentCardClientCapture() { }

    static void capture(ClientGameTestContext context) {
        context.getInput().resizeWindow(1600, 1000);
        context.waitFor(client -> client.getResourceManager().getResource(
                net.minecraft.resources.Identifier.parse("semion-td:font/augment_card_dialog.json")).isPresent(), 1200);
        for (int scale : new int[]{3}) {
            context.runOnClient(client -> {
                client.options.guiScale().set(scale);
                client.resizeGui();
            });
            for (String rarity : List.of("silver", "gold", "prismatic")) {
                open(context, rarity);
                context.getInput().setCursorPos(20, 20);
                screenshot(context, "augment-card-shared-" + rarity + "-scale" + scale);
                move(context, 2, AugmentCardFrames.WIDTH / 2, 144);
                context.waitTicks(3);
                screenshot(context, "augment-card-shared-" + rarity + "-tooltip-scale" + scale);
                if (rarity.equals("silver")) {
                    String[] rerollCommand = new String[1];
                    context.runOnClient(client -> {
                        var style = styleAt(client, cardWidget(client), AugmentCardFrames.WIDTH + AugmentCardDialog.GAP
                                + AugmentCardFrames.WIDTH / 2, (AugmentCardFrames.CARD_ROWS + 1) * 9 + 8);
                        rerollCommand[0] = ((net.minecraft.network.chat.ClickEvent.RunCommand) style.getClickEvent()).command();
                    });
                    move(context, 1, AugmentCardFrames.WIDTH / 2, (AugmentCardFrames.CARD_ROWS + 1) * 9 + 8);
                    context.getInput().pressMouse(SDLMouse.SDL_BUTTON_LEFT);
                    context.waitTicks(20);
                    command(context, "semioncapture checkreroll 4");
                    context.waitFor(client -> client.player.experienceLevel == 2104, 200);
                    geometry(context);
                    screenshot(context, "augment-card-shared-reroll-after-scale" + scale);
                    context.runOnClient(client -> client.getConnection().sendCommand(rerollCommand[0].substring(1)));
                    for (int outside : new int[]{0, 2}) {
                        move(context, outside, AugmentCardFrames.WIDTH / 2, (AugmentCardFrames.CARD_ROWS + 1) * 9 + 8);
                        context.getInput().pressMouse(SDLMouse.SDL_BUTTON_LEFT);
                    }
                    command(context, "semioncapture checkpreserved");
                    context.waitFor(client -> client.player.experienceLevel == 2141, 200);
                    move(context, 1, AugmentCardFrames.BUTTON_INSET, (AugmentCardFrames.CARD_ROWS + 1) * 9 + 8);
                    context.getInput().pressMouse(SDLMouse.SDL_BUTTON_LEFT);
                    context.waitTicks(20);
                    command(context, "semioncapture checkreroll 3");
                    context.waitFor(client -> client.player.experienceLevel == 2103, 200);
                    System.out.println("SEMION_NATIVE_SHARED_REROLL_LEFT_EDGE x=" + AugmentCardFrames.BUTTON_INSET);
                    closeAndReopen(context, scale);
                    command(context, "semioncapture lastroll");
                    context.waitFor(client -> client.player.experienceLevel == 2144 && client.gui.screen() instanceof DialogScreen<?>, 200);
                    move(context, 1, AugmentCardFrames.BUTTON_INSET + AugmentCardFrames.BUTTON_WIDTH - 1,
                            (AugmentCardFrames.CARD_ROWS + 1) * 9 + 8);
                    context.getInput().pressMouse(SDLMouse.SDL_BUTTON_LEFT);
                    context.waitTicks(20);
                    command(context, "semioncapture checkreroll 0");
                    context.waitFor(client -> client.player.experienceLevel == 2100, 200);
                    System.out.println("SEMION_NATIVE_SHARED_REROLL_RIGHT_EDGE x="
                            + (AugmentCardFrames.BUTTON_INSET + AugmentCardFrames.BUTTON_WIDTH - 1));
                    geometry(context);
                    context.getInput().setCursorPos(20, 20);
                    screenshot(context, "augment-card-shared-reroll-disabled-scale" + scale);
                    move(context, 1, AugmentCardFrames.WIDTH / 2, (AugmentCardFrames.CARD_ROWS + 1) * 9 + 8);
                    context.getInput().pressMouse(SDLMouse.SDL_BUTTON_LEFT);
                    command(context, "semioncapture checkdisabled");
                    context.waitFor(client -> client.player.experienceLevel == 2140, 200);
                    open(context, rarity);
                }
                String[] clickedCommand = new String[1];
                context.runOnClient(client -> {
                    var style = styleAt(client, cardWidget(client),
                            2 * (AugmentCardFrames.WIDTH + AugmentCardDialog.GAP) + AugmentCardFrames.WIDTH / 2, 144);
                    if (style == null || !(style.getClickEvent() instanceof net.minecraft.network.chat.ClickEvent.RunCommand action)) {
                        throw new AssertionError("The clicked native card needs its actual server command");
                    }
                    clickedCommand[0] = action.command();
                });
                move(context, 2, AugmentCardFrames.WIDTH / 2, 144);
                context.getInput().pressMouse(SDLMouse.SDL_BUTTON_LEFT);
                awaitClosed(context);
                context.runOnClient(client -> client.getConnection().sendCommand(clickedCommand[0].substring(1)));
                awaitClosed(context);
                System.out.println("SEMION_NATIVE_SELECTION_CALLBACK_REPLAY rarity=" + rarity);
                command(context, "semioncapture checkselection");
                context.waitFor(client -> client.player.experienceLevel == 2042, 200);
                System.out.println("SEMION_NATIVE_CARD_INPUT rarity=" + rarity + " scale=" + scale + " selected=true");
                command(context, "증강");
                context.waitFor(client -> client.gui.screen() instanceof DialogScreen<?>
                        && client.gui.screen().getTitle().getString().contains("증강 선택 기록"), 200);
                command(context, "semioncapture checkselectedreopen");
                context.waitFor(client -> client.player.experienceLevel == 2143, 200);
                System.out.println("SEMION_BARE_COMMAND_AFTER_SELECTION rarity=" + rarity + " history=true");
            }
            verifyMouseMatrix(context);
            command(context, "semioncapture dedicated");
            context.waitFor(client -> client.player.experienceLevel == 2142 && client.gui.screen() instanceof DialogScreen<?>, 200);
            context.waitTicks(5);
            geometry(context);
            context.getInput().setCursorPos(20, 20);
            context.runOnClient(client -> {
                String text = cardWidget(client).getMessage().getString();
                if (!text.contains("전용") || text.contains("미래기관 전용") || text.contains("일반 ·")) {
                    throw new AssertionError("The actual offer must show only the dedicated marker and no common category label");
                }
            });
            screenshot(context, "augment-card-shared-dedicated-scale" + scale);
            command(context, "semioncapture longtext");
            context.waitFor(client -> client.player.experienceLevel == 2145 && client.gui.screen() instanceof DialogScreen<?>, 200);
            context.waitTicks(5);
            geometry(context);
            context.getInput().setCursorPos(20, 20);
            screenshot(context, "augment-card-shared-long-description-scale" + scale);
            context.runOnClient(client -> {
                int count = 0;
                for (var card : kim.biryeong.semiontd.augment.AugmentCatalog.definitions()) {
                    String summary = kim.biryeong.semiontd.augment.AugmentService.offerSummary(card,
                            kim.biryeong.semiontd.augment.AugmentConfig.defaults());
                    var lines = client.font.split(net.minecraft.network.chat.Component.literal(summary), 90);
                    if (lines.size() > 7 || summary.contains("…") || lines.stream().anyMatch(line -> client.font.width(line) > 90)) {
                        throw new AssertionError("Actual client font overflows description: " + card.id() + " lines=" + lines.size());
                    }
                    if (client.font.split(net.minecraft.network.chat.Component.literal(card.displayName()), 90).size() > 2) {
                        throw new AssertionError("Actual client font overflows title: " + card.id());
                    }
                    count++;
                }
                System.out.println("SEMION_NATIVE_DESCRIPTION_FIT cards=" + count + " width=90 rows=7 lineHeight=" + client.font.lineHeight);
            });
            for (int page = 0; page < 5; page++) {
                command(context, "semioncapture iconpreview " + page);
                int expected = 2060 + page;
                context.waitFor(client -> client.player.experienceLevel == expected
                        && client.gui.screen() instanceof DialogScreen<?>, 200);
                context.waitTicks(5);
                geometry(context, false);
                context.getInput().setCursorPos(20, 20);
                screenshot(context, "augment-card-category-preview-" + (page + 1) + "-scale" + scale);
            }
        }
    }

    private static void awaitClosed(ClientGameTestContext context) {
        context.waitFor(client -> client.gui.screen() == null, 200);
        context.waitTicks(25);
        context.runOnClient(client -> {
            if (client.gui.screen() != null) throw new AssertionError("Confirmed selection must stay closed");
        });
        System.out.println("SEMION_NATIVE_SELECTION_CLOSED stable=true");
    }

    private static void verifyMouseMatrix(ClientGameTestContext context) {
        int[][] views = {{1600, 1000, 3}, {2560, 1369, 3}};
        int[][] points = {{0, 0}, {54, 36}, {54, 76}, {54, 120}, {106, 170}};
        for (var view : views) {
            context.getInput().resizeWindow(view[0], view[1]);
            context.runOnClient(client -> {
                client.options.guiScale().set(view[2]);
                client.resizeGui();
            });
            for (int slot = 0; slot < 3; slot++) {
                for (var point : points) {
                    open(context, "silver");
                    move(context, slot, point[0], point[1]);
                    context.getInput().pressMouse(SDLMouse.SDL_BUTTON_LEFT);
                    awaitClosed(context);
                    command(context, "semioncapture checkslotselection " + slot);
                    int expected = 2200 + slot;
                    context.waitFor(client -> client.player.experienceLevel == expected, 200);
                    System.out.println("SEMION_NATIVE_MOUSE_MATRIX scale=" + view[2] + " width=" + view[0]
                            + " height=" + view[1] + " slot=" + slot + " x=" + point[0] + " y=" + point[1]);
                }
            }
        }
        context.getInput().resizeWindow(1600, 1000);
        context.runOnClient(client -> {
            client.options.guiScale().set(3);
            client.resizeGui();
        });
    }

    private static void closeAndReopen(ClientGameTestContext context, int scale) {
        double[] target = new double[2];
        context.runOnClient(client -> {
            var screen = client.gui.screen();
            var widgets = new ArrayList<net.minecraft.client.gui.components.AbstractWidget>();
            collectWidgets(screen, widgets);
            var close = widgets.stream().filter(widget -> widget.getMessage().getString().equals("닫기"))
                    .findFirst().orElseThrow(() -> new AssertionError("The live dialog has no close button"));
            target[0] = (close.getX() + close.getWidth() / 2.0) * client.getWindow().getScreenWidth() / screen.width;
            target[1] = (close.getY() + close.getHeight() / 2.0) * client.getWindow().getScreenHeight() / screen.height;
        });
        context.getInput().setCursorPos(target[0], target[1]);
        context.getInput().pressMouse(SDLMouse.SDL_BUTTON_LEFT);
        context.waitFor(client -> client.gui.screen() == null, 200);
        command(context, "semioncapture checkpreserved");
        context.waitFor(client -> client.player.experienceLevel == 2141, 200);
        command(context, "증강");
        context.waitFor(client -> client.gui.screen() instanceof DialogScreen<?>, 200);
        context.waitTicks(5);
        command(context, "semioncapture checkpreserved");
        geometry(context);
        context.getInput().setCursorPos(20, 20);
        screenshot(context, "augment-card-shared-reopened-scale" + scale);
        System.out.println("SEMION_NATIVE_CLOSE_REOPEN_INPUT scale=" + scale + " closed=true command=증강 sameOffer=true");
    }

    private static void collectWidgets(net.minecraft.client.gui.components.events.GuiEventListener element,
                                       List<net.minecraft.client.gui.components.AbstractWidget> widgets) {
        if (element instanceof net.minecraft.client.gui.components.AbstractWidget widget) widgets.add(widget);
        if (element instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container) {
            for (var child : container.children()) collectWidgets(child, widgets);
        }
    }

    private static void open(ClientGameTestContext context, String rarity) {
        command(context, "semioncapture " + rarity);
        context.waitFor(client -> client.player.experienceLevel == 2040 && client.gui.screen() instanceof DialogScreen<?>, 1200);
        context.waitTicks(20);
        geometry(context);
    }

    private static void command(ClientGameTestContext context, String command) {
        context.waitTicks(40);
        context.runOnClient(client -> client.getConnection().sendCommand(command));
    }

    private static void geometry(ClientGameTestContext context) {
        geometry(context, true);
    }

    private static void geometry(ClientGameTestContext context, boolean checkInput) {
        context.runOnClient(client -> {
            var widget = cardWidget(client);
            var lines = client.font.split(widget.getMessage(), widget.getWidth() - widget.getPadding() * 2);
            if (lines.size() != AugmentCardDialog.TOTAL_ROWS) throw new AssertionError("Card rows unexpectedly wrapped: " + lines.size());
            for (var line : lines) if (client.font.width(line) != AugmentCardDialog.CONTENT_WIDTH) {
                throw new AssertionError("Actual card text and frame advance mismatch: " + client.font.width(line));
            }
            if (widget.getX() < 0 || widget.getX() + widget.getWidth() > client.gui.screen().width
                    || widget.getY() < 0 || widget.getY() + widget.getHeight() > client.gui.screen().height) {
                throw new AssertionError("Native augment card body clips outside viewport");
            }
            System.out.println("SEMION_NATIVE_CARD_GEOMETRY scale=" + client.options.guiScale().get() + " x=" + widget.getX()
                    + " y=" + widget.getY() + " width=" + widget.getWidth() + " height=" + widget.getHeight());
            if (checkInput) hitRegions(client, widget);
        });
    }

    private static void move(ClientGameTestContext context, int slot, int x, int y) {
        double[] target = new double[2];
        context.runOnClient(client -> {
            var widget = cardWidget(client);
            var screen = client.gui.screen();
            target[0] = (widget.getX() + widget.getPadding() + slot * (AugmentCardDialog.CARD_WIDTH + AugmentCardDialog.GAP) + x + .5)
                    * client.getWindow().getScreenWidth() / screen.width;
            target[1] = (widget.getY() + widget.getPadding() + y + .5) * client.getWindow().getScreenHeight() / screen.height;
        });
        context.getInput().setCursorPos(target[0], target[1]);
        context.runOnClient(client -> {
            var widget = cardWidget(client);
            int expectedX = widget.getX() + widget.getPadding() + slot * (AugmentCardDialog.CARD_WIDTH + AugmentCardDialog.GAP) + x;
            int expectedY = widget.getY() + widget.getPadding() + y;
            if ((int) Math.floor(client.mouseHandler.getScaledXPos(client.getWindow())) != expectedX
                    || (int) Math.floor(client.mouseHandler.getScaledYPos(client.getWindow())) != expectedY) {
                throw new AssertionError("Actual SDL cursor did not land on the requested native card pixel");
            }
        });
    }

    private static void hitRegions(Minecraft client, FocusableTextWidget widget) {
        for (int row = 0; row < AugmentCardDialog.TOTAL_ROWS; row++) {
            for (int slot = 0; slot < 3; slot++) {
                for (int x = 0; x < AugmentCardFrames.WIDTH - 1; x++) {
                    var style = styleAt(client, widget, slot * (AugmentCardDialog.CARD_WIDTH + AugmentCardDialog.GAP) + x, row * 9 + 4);
                    var click = style == null ? null : style.getClickEvent();
                    if (row < AugmentCardFrames.CARD_ROWS) {
                        if (!(click instanceof net.minecraft.network.chat.ClickEvent.RunCommand command)
                                || !command.command().matches(".* draft [0-9]+ " + slot + " .*")) {
                            throw new AssertionError("Bitmap/text card hit region lost its slot command: row=" + row + " slot=" + slot + " x=" + x);
                        }
                    } else if (row == AugmentCardFrames.CARD_ROWS && click != null) {
                        throw new AssertionError("The visual gap below a card must not activate it");
                    } else if (click instanceof net.minecraft.network.chat.ClickEvent.RunCommand command
                            && (slot != 1 || !command.command().matches(".* reroll [0-9]+ [0-9a-f-]+"))) {
                        throw new AssertionError("Only the centered shared reroll may be clickable");
                    }
                }
            }
            if (row > AugmentCardFrames.CARD_ROWS) {
                for (int slot = 0; slot < 3; slot++) {
                    int origin = slot * (AugmentCardDialog.CARD_WIDTH + AugmentCardDialog.GAP);
                    var center = styleAt(client, widget, origin + AugmentCardFrames.WIDTH / 2, row * 9 + 4);
                    var expected = center == null ? null : center.getClickEvent();
                    for (int x = 0; x < AugmentCardFrames.WIDTH; x++) {
                        var style = styleAt(client, widget, origin + x, row * 9 + 4);
                        var click = style == null ? null : style.getClickEvent();
                        boolean inside = slot == 1 && x >= AugmentCardFrames.BUTTON_INSET
                                && x < AugmentCardFrames.BUTTON_INSET + AugmentCardFrames.BUTTON_WIDTH;
                        if (!java.util.Objects.equals(inside ? expected : null, click)) {
                            throw new AssertionError("Centered reroll hit width mismatch: slot=" + slot + " x=" + x);
                        }
                    }
                }
            }
            for (int gap : new int[]{AugmentCardDialog.CARD_WIDTH + AugmentCardDialog.GAP / 2,
                    AugmentCardDialog.CARD_WIDTH * 2 + AugmentCardDialog.GAP * 3 / 2}) {
                var style = styleAt(client, widget, gap, row * 9 + 4);
                if (style != null && style.getClickEvent() != null) throw new AssertionError("Card-column gap is clickable");
            }
        }
        System.out.println("SEMION_NATIVE_CARD_HIT_REGIONS scale=" + client.options.guiScale().get()
                + " rows=" + AugmentCardDialog.TOTAL_ROWS + " overlay=true gaps=true rerollWidth=" + AugmentCardFrames.BUTTON_WIDTH + " centered=true");
    }

    private static net.minecraft.network.chat.Style styleAt(Minecraft client, FocusableTextWidget widget, int x, int y) {
        var finder = new net.minecraft.client.gui.ActiveTextCollector.ClickableStyleFinder(client.font,
                widget.getX() + widget.getPadding() + x, widget.getY() + widget.getPadding() + y);
        widget.visitLines(finder);
        return finder.result();
    }

    private static FocusableTextWidget cardWidget(Minecraft client) {
        if (!(client.gui.screen() instanceof DialogScreen<?> dialog)) throw new AssertionError("Native augment dialog is not open");
        try {
            var body = DialogScreen.class.getDeclaredField("bodyScroll");
            body.setAccessible(true);
            var content = ScrollableLayout.class.getDeclaredField("content");
            content.setAccessible(true);
            var widgets = new ArrayList<net.minecraft.client.gui.components.AbstractWidget>();
            ((Layout) content.get(body.get(dialog))).visitWidgets(widgets::add);
            return widgets.stream().filter(FocusableTextWidget.class::isInstance).map(FocusableTextWidget.class::cast)
                    .filter(widget -> widget.getMessage().getString().codePoints().anyMatch(AugmentCardFrames::isCardGlyph))
                    .findFirst().orElseThrow(() -> new AssertionError("Actual card frame text widget missing"));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot inspect native card layout", exception);
        }
    }

    private static void screenshot(ClientGameTestContext context, String name) {
        String requested = System.getProperty("semiontd.capture.screenshot");
        if (requested != null && !requested.equals(name)) return;
        context.runOnClient(client -> client.gui.toastManager().clear());
        var file = context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1600, 1000).disableCounterPrefix());
        if (!Files.isRegularFile(file)) throw new AssertionError("Actual native augment framebuffer missing");
        System.out.println("SEMION_CAPTURE_SCREENSHOT=" + file.toAbsolutePath());
    }
}
