package kim.biryeong.semiontd.ui;

import java.nio.file.Files;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.lwjgl.sdl.SDLMouse;

final class AugmentHudClientCapture {
    private AugmentHudClientCapture() { }

    static void capture(ClientGameTestContext context) {
        context.getInput().resizeWindow(1600, 1000);
        for (String rarity : List.of("silver", "gold", "prismatic")) {
            context.runOnClient(client -> {
                client.options.guiScale().set(2);
                client.resizeGui();
                client.getConnection().sendCommand("semioncapture " + rarity);
            });
            waitOffer(context);
            context.getInput().setCursorPos(20, 20);
            for (int frame = 0; frame < 6; frame++) {
                screenshot(context, "augment-hud-" + rarity + "-reveal-" + frame);
                context.waitTicks(1);
            }
            context.waitTicks(20);
            screenshot(context, "augment-hud-" + rarity + "-scale2");
            hoverSlot(context, 6);
            context.waitTicks(3);
            screenshot(context, "augment-hud-" + rarity + "-tooltip-scale2");
            if (rarity.equals("silver")) {
                clickSlot(context, 49);
                context.waitTicks(10);
                context.runOnClient(client -> client.getConnection().sendCommand("semioncapture checkreroll"));
                context.waitFor(client -> client.player.experienceLevel == 2041, 200);
                waitOffer(context);
                context.waitTicks(20);
                context.getInput().setCursorPos(20, 20);
                screenshot(context, "augment-hud-silver-rerolled-scale2");
                context.runOnClient(client -> client.getConnection().sendCommand("semioncapture silver"));
                context.waitFor(client -> client.player.experienceLevel == 2040, 200);
                waitOffer(context);
                context.waitTicks(20);
            }
            if (rarity.equals("prismatic")) {
                context.runOnClient(client -> {
                    client.options.guiScale().set(3);
                    client.resizeGui();
                });
                context.getInput().setCursorPos(20, 20);
                context.waitTicks(3);
                screenshot(context, "augment-hud-prismatic-scale3");
            }
            clickSlot(context, 6);
            context.waitTicks(10);
            context.runOnClient(client -> client.getConnection().sendCommand("semioncapture checkselection"));
            context.waitFor(client -> client.player.experienceLevel == 2042, 200);
            System.out.println("SEMION_HUD_MOUSE_SELECTION=" + rarity + " serverConfirmed=true");
        }
    }

    private static void waitOffer(ClientGameTestContext context) {
        context.waitFor(client -> client.gui.screen() instanceof ContainerScreen screen
                && screen.getMenu().getRowCount() == 6
                && !screen.getMenu().getSlot(6).getItem().isEmpty()
                && !screen.getMenu().getSlot(49).getItem().isEmpty()
                && !screen.getTitle().getString().isEmpty(), 1200);
    }

    private static void hoverSlot(ClientGameTestContext context, int index) {
        double[] position = new double[2];
        context.runOnClient(client -> {
            if (!(client.gui.screen() instanceof ContainerScreen screen)) {
                throw new AssertionError("The actual offer container must be open before mouse input");
            }
            try {
                var left = AbstractContainerScreen.class.getDeclaredField("leftPos");
                var top = AbstractContainerScreen.class.getDeclaredField("topPos");
                left.setAccessible(true);
                top.setAccessible(true);
                var slot = screen.getMenu().getSlot(index);
                position[0] = (left.getInt(screen) + slot.x + 8.0) * client.getWindow().getScreenWidth() / screen.width;
                position[1] = (top.getInt(screen) + slot.y + 8.0) * client.getWindow().getScreenHeight() / screen.height;
                System.out.println("SEMION_HUD_MOUSE_TARGET slot=" + index + " x=" + position[0] + " y=" + position[1]
                        + " guiScale=" + client.options.guiScale().get());
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Cannot inspect the actual offer slot geometry", exception);
            }
        });
        context.getInput().setCursorPos(position[0], position[1]);
    }

    private static void clickSlot(ClientGameTestContext context, int index) {
        hoverSlot(context, index);
        context.getInput().pressMouse(SDLMouse.SDL_BUTTON_LEFT);
    }

    private static void screenshot(ClientGameTestContext context, String name) {
        context.runOnClient(client -> {
            client.gui.toastManager().clear();
            if (!(client.gui.screen() instanceof ContainerScreen screen)) throw new AssertionError("Offer closed before " + name);
            System.out.println("SEMION_HUD_FRAME=" + name + " titleHash=" + screen.getTitle().getString().hashCode()
                    + " rows=" + screen.getMenu().getRowCount() + " guiScale=" + client.options.guiScale().get());
        });
        var file = context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1600, 1000).disableCounterPrefix());
        if (!Files.isRegularFile(file)) throw new AssertionError("Actual HUD framebuffer screenshot missing");
        System.out.println("SEMION_CAPTURE_SCREENSHOT=" + file.toAbsolutePath());
    }
}
