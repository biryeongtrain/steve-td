package kim.biryeong.semiontd.ui;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import org.lwjgl.sdl.SDLMouse;

final class CardDialogClientCapture {
    private CardDialogClientCapture() { }

    static void capture(ClientGameTestContext context) {
        context.getInput().resizeWindow(1600, 1000);
        context.waitFor(client -> client.getResourceManager().getResource(
                net.minecraft.resources.Identifier.parse("semion-td:font/card_dialog_poc.json")).isPresent(), 1200);
        for (int scale : new int[]{2, 3}) {
            context.runOnClient(client -> {
                client.options.guiScale().set(scale);
                client.resizeGui();
            });
            for (int card = 0; card < 3; card++) {
                open(context);
                if (card == 0) screenshot(context, "card-dialog-scale" + scale);
                int attempts = 0;
                for (int row = 0; row < 12; row++) {
                    for (int offsetX : new int[]{1, 47, 94}) {
                        for (int offsetY : new int[]{0, 4, 8}) {
                            move(context, card * 108 + offsetX, row * 9 + offsetY);
                            context.getInput().pressMouse(SDLMouse.SDL_BUTTON_LEFT);
                            int expected = 3000 + ++attempts;
                            context.waitFor(client -> client.player.experienceLevel == expected, 100);
                        }
                    }
                }
                verify(context, attempts, 1, 0, attempts - 1, card);
                System.out.println("SEMION_CARD_DIALOG_HIT_GRID scale=" + scale + " card=" + card + " points=" + attempts);
            }
            open(context);
            for (int row = 0; row < 12; row++) {
                for (int gap : new int[]{101, 209}) {
                    move(context, gap, row * 9 + 4);
                    context.getInput().pressMouse(SDLMouse.SDL_BUTTON_LEFT);
                }
            }
            verify(context, 0, 0, 0, 0, -1);
            open(context);
            move(context, 47, 40);
            context.getInput().holdMouse(SDLMouse.SDL_BUTTON_LEFT);
            context.waitTicks(1);
            move(context, 155, 70);
            context.waitTicks(1);
            context.getInput().releaseMouse(SDLMouse.SDL_BUTTON_LEFT);
            context.waitTicks(2);
            verify(context, 1, 1, 0, 0, 0);
            open(context);
            ClickEvent.Custom[] stale = new ClickEvent.Custom[1];
            context.runOnClient(client -> stale[0] = firstCardAction(cardWidget(client)));
            clickReroll(context);
            context.waitFor(client -> client.player.experienceLevel == 3001, 100);
            context.waitTicks(3);
            screenshot(context, "card-dialog-rerolled-scale" + scale);
            context.runOnClient(client -> client.getConnection().send(
                    new ServerboundCustomClickActionPacket(stale[0].id(), stale[0].payload())));
            context.waitFor(client -> client.player.experienceLevel == 3002, 100);
            verify(context, 2, 0, 1, 1, -1);
            System.out.println("SEMION_CARD_DIALOG_INPUT_COMPLETE scale=" + scale + " gap=true drag=true reroll=true stale=true");
        }
    }

    private static void open(ClientGameTestContext context) {
        context.runOnClient(client -> client.getConnection().sendCommand("semioncardpoc open"));
        context.waitFor(client -> client.player.experienceLevel == 3000
                && client.gui.screen() instanceof DialogScreen<?>, 400);
        context.waitTicks(2);
        context.runOnClient(client -> {
            var widget = cardWidget(client);
            var rows = client.font.split(widget.getMessage(), widget.getWidth() - 2 * widget.getPadding());
            if (rows.size() != 12) throw new AssertionError("The actual card body must retain all twelve bitmap rows: " + rows.size());
            for (var row : rows) {
                if (client.font.width(row) != 312) throw new AssertionError("Unexpected actual bitmap row advance " + client.font.width(row));
            }
            if (widget.getHeight() != 108 + 2 * widget.getPadding()) throw new AssertionError("Actual card widget row height does not match all twelve click rows: " + widget.getHeight());
            int textTop = widget.getY() + widget.getPadding();
            if (widget.getX() < 0 || widget.getX() + widget.getWidth() > client.gui.screen().width
                    || textTop < 0 || textTop + 108 > client.gui.screen().height) {
                throw new AssertionError("The card body clips outside the actual GUI viewport");
            }
            System.out.println("SEMION_CARD_DIALOG_GEOMETRY scale=" + client.options.guiScale().get()
                    + " x=" + widget.getX() + " y=" + widget.getY() + " width=" + widget.getWidth()
                    + " height=" + widget.getHeight() + " padding=" + widget.getPadding());
        });
    }

    private static void verify(ClientGameTestContext context, int attempts, int selections, int rerolls, int rejected, int card) {
        context.runOnClient(client -> client.getConnection().sendCommand("semioncardpoc verify " + attempts
                + " " + selections + " " + rerolls + " " + rejected + " " + card));
        context.waitFor(client -> client.player.experienceLevel == 8000, 200);
    }

    private static void move(ClientGameTestContext context, int x, int y) {
        double[] target = new double[2];
        context.runOnClient(client -> {
            var widget = cardWidget(client);
            var screen = client.gui.screen();
            target[0] = (widget.getX() + (widget.getWidth() - 312) / 2.0 + x + .5)
                    * client.getWindow().getScreenWidth() / screen.width;
            target[1] = (widget.getY() + widget.getPadding() + y + .5)
                    * client.getWindow().getScreenHeight() / screen.height;
        });
        context.getInput().setCursorPos(target[0], target[1]);
        context.runOnClient(client -> {
            var widget = cardWidget(client);
            int expectedX = widget.getX() + (widget.getWidth() - 312) / 2 + x;
            int expectedY = widget.getY() + widget.getPadding() + y;
            int actualX = (int) Math.floor(client.mouseHandler.getScaledXPos(client.getWindow()));
            int actualY = (int) Math.floor(client.mouseHandler.getScaledYPos(client.getWindow()));
            if (actualX != expectedX || actualY != expectedY) {
                throw new AssertionError("SDL cursor missed requested GUI pixel: actual=" + actualX + "," + actualY
                        + " expected=" + expectedX + "," + expectedY);
            }
        });
    }

    private static void clickReroll(ClientGameTestContext context) {
        double[] target = new double[2];
        context.runOnClient(client -> {
            var dialog = (DialogScreen<?>) client.gui.screen();
            try {
                var field = DialogScreen.class.getDeclaredField("layout");
                field.setAccessible(true);
                var widgets = new ArrayList<net.minecraft.client.gui.components.AbstractWidget>();
                ((Layout) field.get(dialog)).visitWidgets(widgets::add);
                var bodyField = DialogScreen.class.getDeclaredField("bodyScroll");
                bodyField.setAccessible(true);
                var contentField = ScrollableLayout.class.getDeclaredField("content");
                contentField.setAccessible(true);
                ((Layout) contentField.get(bodyField.get(dialog))).visitWidgets(widgets::add);
                var button = widgets.stream().filter(widget -> widget.getMessage().getString().contains("Reroll"))
                        .findFirst().orElseThrow(() -> new AssertionError("Native footer reroll button missing"));
                target[0] = (button.getX() + button.getWidth() / 2.0) * client.getWindow().getScreenWidth() / dialog.width;
                target[1] = (button.getY() + button.getHeight() / 2.0) * client.getWindow().getScreenHeight() / dialog.height;
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Cannot inspect actual native footer layout", exception);
            }
        });
        context.getInput().setCursorPos(target[0], target[1]);
        context.getInput().pressMouse(SDLMouse.SDL_BUTTON_LEFT);
    }

    private static FocusableTextWidget cardWidget(Minecraft client) {
        if (!(client.gui.screen() instanceof DialogScreen<?> dialog)) throw new AssertionError("Native card dialog is not open");
        try {
            var bodyField = DialogScreen.class.getDeclaredField("bodyScroll");
            bodyField.setAccessible(true);
            var contentField = ScrollableLayout.class.getDeclaredField("content");
            contentField.setAccessible(true);
            var widgets = new ArrayList<net.minecraft.client.gui.components.AbstractWidget>();
            ((Layout) contentField.get(bodyField.get(dialog))).visitWidgets(widgets::add);
            return widgets.stream().filter(FocusableTextWidget.class::isInstance).map(FocusableTextWidget.class::cast)
                    .filter(widget -> firstCardAction(widget) != null).findFirst()
                    .orElseThrow(() -> new AssertionError("Actual bitmap-card text widget missing"));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot inspect actual native dialog body", exception);
        }
    }

    private static ClickEvent.Custom firstCardAction(FocusableTextWidget widget) {
        return widget.getMessage().visit((style, text) -> style.getClickEvent() instanceof ClickEvent.Custom custom
                && custom.id().getPath().contains("/card/") ? Optional.of(custom) : Optional.<ClickEvent.Custom>empty(),
                net.minecraft.network.chat.Style.EMPTY).orElse(null);
    }

    private static void screenshot(ClientGameTestContext context, String name) {
        context.getInput().setCursorPos(20, 20);
        context.runOnClient(client -> client.gui.toastManager().clear());
        var file = context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1600, 1000).disableCounterPrefix());
        if (!Files.isRegularFile(file)) throw new AssertionError("Actual Dialog framebuffer missing");
        System.out.println("SEMION_CAPTURE_SCREENSHOT=" + file.toAbsolutePath());
    }
}
